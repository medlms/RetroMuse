package com.retro.grooveplayer.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import com.retro.grooveplayer.dsp.AudioFrame
import com.retro.grooveplayer.dsp.EffectRack
import com.retro.grooveplayer.dsp.RackSettings
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp

/**
 * Real-time mid/side separation.
 *
 * Both modes work on the mid (centre) and side (stereo difference) signals:
 *   mid  = (L + R) / 2   -> what is panned centre: lead vocal, kick, bass, snare
 *   side = (L - R) / 2   -> what is panned wide: guitars, keys, reverb tails, backing
 *
 * ISOLATE_INSTRUMENTAL keeps the side signal but folds the low end of the mid back in,
 * so the bass and kick survive instead of being cancelled along with the vocal.
 *
 * ISOLATE_VOCAL band-passes the mid into the vocal range and ducks it whenever the side
 * signal is louder, which suppresses wide instrumentation during non-vocal passages.
 */
class VocalProcessor : BaseAudioProcessor() {
    enum class Mode { OFF, ISOLATE_INSTRUMENTAL, ISOLATE_VOCAL }

    // Written from the UI thread, read from the audio thread.
    @Volatile
    var mode = Mode.OFF

    /** 8D audio: slowly rotates the image between the ears. Combines with any mode. */
    @Volatile
    var spatialAudio = false

    private var panPhase = 0.0
    private var panIncrement = 0.0

    /** Full effect rack for live playback. Export builds its own instance. */
    private val rack = EffectRack()
    private val frame = AudioFrame()

    // One-pole filter coefficients, derived from the stream sample rate in onConfigure.
    private var bassCoeff = 0f      // ~200 Hz, keeps the low end for karaoke mode
    private var lowCutCoeff = 0f    // ~150 Hz, removes rumble below the voice
    private var highCutCoeff = 0f   // ~7 kHz, removes cymbals/air above the voice
    private var attackCoeff = 0f
    private var releaseCoeff = 0f

    // Filter and envelope state.
    private var bassState = 0f
    private var lowCutState = 0f
    private var highCutState = 0f
    private var midEnv = 0f
    private var sideEnv = 0f
    private var duckGain = 1f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Support both 16-bit signed PCM and 32-bit float PCM.
        // If the format is neither, return NOT_SET to deactivate safely.
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            return AudioFormat.NOT_SET
        }

        val sampleRate = if (inputAudioFormat.sampleRate > 0) inputAudioFormat.sampleRate else 44100
        bassCoeff = onePole(200f, sampleRate)
        lowCutCoeff = onePole(150f, sampleRate)
        highCutCoeff = onePole(7000f, sampleRate)
        attackCoeff = onePole(50f, sampleRate)    // ~20 ms attack
        releaseCoeff = onePole(5f, sampleRate)    // ~200 ms release
        // One full rotation every ~8 seconds is the pacing the "8D audio" edits use.
        panIncrement = 2.0 * Math.PI / (sampleRate * 8.0)
        rack.prepare(sampleRate)
        rack.sync(force = true)
        resetState()

        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun onReset() {
        resetState()
    }

    private fun resetState() {
        rack.reset()
        bassState = 0f
        lowCutState = 0f
        highCutState = 0f
        midEnv = 0f
        sideEnv = 0f
        duckGain = 1f
    }

    private fun onePole(cutoffHz: Float, sampleRate: Int): Float =
        1f - exp(-2.0 * Math.PI * cutoffHz / sampleRate).toFloat()

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Snapshot the settings so they cannot change midway through this buffer.
        val activeMode = mode
        val spatial = spatialAudio
        // Pull rack parameter changes once per buffer, not per sample.
        rack.sync()
        val rackActive = RackSettings.anyEnabled || RackSettings.limiterEnabled

        // Obtain a formatted output buffer from BaseAudioProcessor
        val outputBuffer = replaceOutputBuffer(remaining)

        // Ensure buffers use native byte ordering
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val isStereo = inputAudioFormat.channelCount == 2

        if ((activeMode == Mode.OFF && !spatial && !rackActive) || !isStereo) {
            // Nothing to do, but still tap the signal so the visualiser has real data to
            // draw. Read through a duplicate so the passthrough copy below still sees
            // the whole buffer.
            tapForLevels(inputBuffer.duplicate().order(ByteOrder.nativeOrder()), isFloat, isStereo)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        if (isFloat) {
            // Stereo float frame size is 8 bytes (2 channels * 4 bytes/sample)
            while (inputBuffer.remaining() >= 8) {
                processInto(frame, inputBuffer.float, inputBuffer.float, activeMode, spatial)
                rack.process(frame)
                AudioLevels.feed((frame.l + frame.r) * 0.5f)
                outputBuffer.putFloat(frame.l)
                outputBuffer.putFloat(frame.r)
            }
        } else {
            // Stereo 16-bit frame size is 4 bytes (2 channels * 2 bytes/sample)
            while (inputBuffer.remaining() >= 4) {
                processInto(
                    frame,
                    inputBuffer.short / 32768f,
                    inputBuffer.short / 32768f,
                    activeMode,
                    spatial
                )
                rack.process(frame)
                AudioLevels.feed((frame.l + frame.r) * 0.5f)
                outputBuffer.putShort(toPcm16(frame.l))
                outputBuffer.putShort(toPcm16(frame.r))
            }
        }

        // Write any trailing partial frame bytes directly to output
        if (inputBuffer.hasRemaining()) {
            outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }

    /** Feeds the visualiser when the buffer is being passed through unmodified. */
    private fun tapForLevels(buffer: ByteBuffer, isFloat: Boolean, isStereo: Boolean) {
        val step = if (isStereo) 2 else 1
        if (isFloat) {
            while (buffer.remaining() >= 4 * step) {
                var sum = 0f
                for (c in 0 until step) sum += buffer.float
                AudioLevels.feed(sum / step)
            }
        } else {
            while (buffer.remaining() >= 2 * step) {
                var sum = 0f
                for (c in 0 until step) sum += buffer.short / 32768f
                AudioLevels.feed(sum / step)
            }
        }
    }

    private fun toPcm16(value: Float): Short =
        (value * 32767f).toInt().coerceIn(-32768, 32767).toShort()

    /**
     * Runs one stereo frame through separation and then the 8D panner, writing the
     * result into [target]. Isolation collapses to mono first, which the panner then
     * places in the field - that is exactly how the "8D audio" edits are made.
     *
     * Writes in place rather than returning a Pair: at 44.1 kHz a Pair per frame would
     * allocate 88200 objects a second on the audio thread.
     */
    fun processInto(
        target: AudioFrame,
        left: Float,
        right: Float,
        activeMode: Mode,
        spatial: Boolean
    ) {
        var outL: Float
        var outR: Float

        if (activeMode == Mode.OFF) {
            outL = left
            outR = right
        } else {
            val mono = process(left, right, activeMode)
            outL = mono
            outR = mono
        }

        if (spatial) {
            panPhase += panIncrement
            if (panPhase > 2 * Math.PI) panPhase -= 2 * Math.PI
            // Equal-power pan keeps perceived loudness steady through the sweep.
            val pan = kotlin.math.sin(panPhase).toFloat()          // -1 (left) .. 1 (right)
            val angle = (pan + 1f) * 0.25f * Math.PI.toFloat()      // 0 .. PI/2
            val gainL = kotlin.math.cos(angle)
            val gainR = kotlin.math.sin(angle)
            val mid = (outL + outR) * 0.5f
            outL = mid * gainL * 1.414f
            outR = mid * gainR * 1.414f
        }

        // No hard clamp here - the rack's brickwall limiter handles the ceiling, and
        // clamping mid-chain squares off peaks into audible crackle.
        target.l = outL
        target.r = outR
    }

    /** Processes one stereo frame down to a single mono sample in [-1, 1]. */
    private fun process(left: Float, right: Float, activeMode: Mode): Float {
        val mid = (left + right) * 0.5f
        val side = (left - right) * 0.5f

        return when (activeMode) {
            Mode.ISOLATE_INSTRUMENTAL -> {
                // Centre-cancelled signal, with the centred low end folded back in so the
                // track keeps its bass and kick.
                bassState += bassCoeff * (mid - bassState)
                (side + bassState).coerceIn(-1f, 1f)
            }

            Mode.ISOLATE_VOCAL -> {
                // Band-pass the centre into the vocal range.
                lowCutState += lowCutCoeff * (mid - lowCutState)
                val aboveLowCut = mid - lowCutState
                highCutState += highCutCoeff * (aboveLowCut - highCutState)
                val voiceBand = highCutState

                // Follow the mid and side levels and duck whenever the sides dominate,
                // which is where the wide instrumentation lives.
                midEnv = follow(midEnv, abs(voiceBand))
                sideEnv = follow(sideEnv, abs(side))
                val target = midEnv / (midEnv + 1.5f * sideEnv + 1e-6f)
                duckGain += 0.05f * (target - duckGain)

                // Make up the level lost to band-passing.
                (voiceBand * duckGain * 1.8f).coerceIn(-1f, 1f)
            }

            Mode.OFF -> mid
        }
    }

    private fun follow(env: Float, value: Float): Float {
        val coeff = if (value > env) attackCoeff else releaseCoeff
        return env + coeff * (value - env)
    }
}
