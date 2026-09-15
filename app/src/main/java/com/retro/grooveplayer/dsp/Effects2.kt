package com.retro.grooveplayer.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * Second half of the rack. Split from Effects.kt purely to keep each file readable;
 * everything here implements the same [AudioEffect] contract.
 */

// --------------------------------------------------------------------------------
// Dynamics
// --------------------------------------------------------------------------------

/** Plain full-band compressor, for glue rather than surgical control. */
class Compressor : AudioEffect {
    override var enabled = false

    @Volatile var thresholdDb = -18f
    @Volatile var ratio = 3f
    @Volatile var makeupDb = 0f

    private val follower = EnvelopeFollower(10f, 120f)

    /** Gain reduction in dB, negative, for the UI meter. */
    val reduction = Meter()

    override fun prepare(sampleRate: Int) {
        follower.prepare(sampleRate)
        reduction.prepare(sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val level = follower.process(max(abs(frame.l), abs(frame.r)))
        val reductionDb = GainComputer.gainDb(Db.fromGain(level), thresholdDb, ratio, 6f)
        reduction.update(-reductionDb / 24f)
        val gain = Db.toGain(reductionDb + makeupDb)
        frame.l *= gain
        frame.r *= gain
    }

    override fun reset() {
        follower.reset()
        reduction.reset()
    }
}

/**
 * De-esser: a dynamic notch that only ducks the sibilance band, keyed off the energy
 * in that band rather than the whole signal. Distinct from the resonance suppressor,
 * which hunts wherever a peak happens to be.
 */
class DeEsser : AudioEffect {
    override var enabled = false

    @Volatile var frequency = 6500f
    @Volatile var amount = 0.5f

    private val detector = Biquad()
    private val notch = Biquad()
    private val follower = EnvelopeFollower(1.5f, 60f)
    private var sampleRate = 44100

    val reduction = Meter()

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        detector.setBandPass(frequency, 2.5f, sampleRate)
        follower.prepare(sampleRate)
        reduction.prepare(sampleRate)
    }

    fun updateFrequency() {
        detector.setBandPass(frequency, 2.5f, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val probe = detector.processLeft((frame.l + frame.r) * 0.5f)
        val level = follower.process(probe)
        val levelDb = Db.fromGain(level)
        val threshold = -32f
        if (levelDb <= threshold) return

        val cutDb = -((levelDb - threshold) * amount).coerceIn(0f, 14f)
        reduction.update(-cutDb / 14f)
        notch.setPeaking(frequency, 2.5f, cutDb, sampleRate)
        frame.l = notch.processLeft(frame.l)
        frame.r = notch.processRight(frame.r)
    }

    override fun reset() {
        detector.reset(); notch.reset(); follower.reset(); reduction.reset()
    }
}

// --------------------------------------------------------------------------------
// Harmonic
// --------------------------------------------------------------------------------

/**
 * Exciter: saturation applied only to the top band, then mixed back under the dry
 * signal. Adds air and perceived detail without the whole mix sounding distorted.
 */
class Exciter : AudioEffect {
    override var enabled = false

    @Volatile var frequency = 4000f
    @Volatile var amount = 0.4f

    private val split = LinkwitzRileyCrossover()
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        split.setFrequency(frequency, sampleRate)
    }

    fun updateFrequency() {
        split.setFrequency(frequency, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val highL = split.highLeft(frame.l)
        val highR = split.highRight(frame.r)
        frame.l += Shapers.tube(highL, 4f) * amount * 0.6f
        frame.r += Shapers.tube(highR, 4f) * amount * 0.6f
    }

    override fun reset() = split.reset()
}

/**
 * Sub-bass generator: rectifying the low band produces an octave-down component,
 * which is filtered and blended under the original. The trick behind bass-boosted
 * edits sounding big on phone speakers.
 */
class SubBass : AudioEffect {
    override var enabled = false

    @Volatile var amount = 0.4f

    private val split = LinkwitzRileyCrossover()
    private val subFilter = Biquad()
    private var flip = 1f
    private var lastSign = 1f

    override fun prepare(sampleRate: Int) {
        split.setFrequency(120f, sampleRate)
        subFilter.setLowPass(90f, 0.7f, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val lowL = split.lowLeft(frame.l)
        val lowR = split.lowRight(frame.r)
        val mono = (lowL + lowR) * 0.5f

        // Flip polarity on every zero crossing to halve the fundamental frequency.
        val sign = if (mono >= 0f) 1f else -1f
        if (sign != lastSign) {
            lastSign = sign
            if (sign > 0f) flip = -flip
        }
        val octaveDown = subFilter.processLeft(abs(mono) * flip)

        frame.l += octaveDown * amount * 1.5f
        frame.r += octaveDown * amount * 1.5f
    }

    override fun reset() {
        split.reset(); subFilter.reset(); flip = 1f; lastSign = 1f
    }
}

/** Ring modulator: multiplies by a sine, producing metallic sum and difference tones. */
class RingModulator : AudioEffect {
    override var enabled = false

    @Volatile var frequency = 200f
    @Volatile var mix = 0.5f

    private val osc = Lfo()
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        osc.setRate(frequency, sampleRate)
    }

    fun updateFrequency() = osc.setRate(frequency, sampleRate)

    override fun process(frame: AudioFrame) {
        val carrier = osc.next()
        frame.l = frame.l * (1f - mix) + frame.l * carrier * mix
        frame.r = frame.r * (1f - mix) + frame.r * carrier * mix
    }

    override fun reset() = osc.reset()
}

// --------------------------------------------------------------------------------
// Filter / modulation
// --------------------------------------------------------------------------------

/** Auto-wah: a resonant bandpass whose cutoff follows the signal envelope. */
class AutoWah : AudioEffect {
    override var enabled = false

    @Volatile var sensitivity = 0.5f
    @Volatile var resonance = 4f

    private val follower = EnvelopeFollower(8f, 120f)
    private val filter = Biquad()
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        follower.prepare(sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val env = follower.process(max(abs(frame.l), abs(frame.r)))
        // Map the envelope onto a musical sweep range.
        val cutoff = (300f + env * 6000f * (0.5f + sensitivity * 2f)).coerceIn(200f, 6000f)
        filter.setBandPass(cutoff, resonance, sampleRate)
        frame.l = filter.processLeft(frame.l) * 1.6f
        frame.r = filter.processRight(frame.r) * 1.6f
    }

    override fun reset() {
        follower.reset(); filter.reset()
    }
}

/** Auto-pan: equal-power sweep between the ears, independent of the 8D mode. */
class AutoPan : AudioEffect {
    override var enabled = false

    @Volatile var rateHz = 0.4f
    @Volatile var depth = 0.8f

    private val lfo = Lfo()
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        lfo.setRate(rateHz, sampleRate)
    }

    fun updateRate() = lfo.setRate(rateHz, sampleRate)

    override fun process(frame: AudioFrame) {
        val pan = lfo.next() * depth
        val angle = (pan + 1f) * 0.25f * Math.PI.toFloat()
        val gainL = cos(angle)
        val gainR = sin(angle)
        val mono = (frame.l + frame.r) * 0.5f
        frame.l = frame.l * (1f - depth) + mono * gainL * 1.414f * depth
        frame.r = frame.r * (1f - depth) + mono * gainR * 1.414f * depth
    }

    override fun reset() = lfo.reset()
}

// --------------------------------------------------------------------------------
// Pitch
// --------------------------------------------------------------------------------

/**
 * Standalone pitch shift, independent of tempo.
 *
 * The player's speed control moves pitch and tempo together because it resamples.
 * This shifts pitch alone, so a track can be transposed without being slowed down.
 */
class PitchShift : AudioEffect {
    override var enabled = false

    @Volatile var semitones = 0f

    private val left = GranularPitchShifter()
    private val right = GranularPitchShifter()

    override fun prepare(sampleRate: Int) {
        left.prepare(sampleRate)
        right.prepare(sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val ratio = 2f.pow(semitones / 12f).coerceIn(0.5f, 2f)
        left.setRatio(ratio)
        right.setRatio(ratio)
        frame.l = left.process(frame.l)
        frame.r = right.process(frame.r)
    }

    override fun reset() {
        left.reset(); right.reset()
    }
}

/**
 * Scale snap, the autotune effect.
 *
 * Pitch is detected by autocorrelation over a short window, snapped to the nearest
 * note in the chosen scale, and the difference applied through the granular shifter.
 * Correction is deliberately not instantaneous - [strength] controls how hard it
 * pulls, since a hard snap is the robotic sound and a soft one is a light polish.
 */
class ScaleSnap : AudioEffect {
    override var enabled = false

    /** 0 = untouched, 1 = fully quantised. */
    @Volatile var strength = 0.8f

    /** Semitone offsets of the key, relative to C. */
    @Volatile var scale = MAJOR

    /** Root note, 0 = C. */
    @Volatile var root = 0

    private val shifter = GranularPitchShifter()
    private var sampleRate = 44100

    private val window = FloatArray(WINDOW)
    private var windowIndex = 0
    private var currentRatio = 1f

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        shifter.prepare(sampleRate)
        window.fill(0f)
        windowIndex = 0
        currentRatio = 1f
    }

    override fun process(frame: AudioFrame) {
        val mono = (frame.l + frame.r) * 0.5f

        window[windowIndex] = mono
        windowIndex++
        if (windowIndex >= WINDOW) {
            windowIndex = 0
            updateRatio()
        }

        shifter.setRatio(currentRatio)
        val shifted = shifter.process(mono)
        // Keep the original stereo balance, applying correction to the centre.
        val side = (frame.l - frame.r) * 0.5f
        frame.l = shifted + side
        frame.r = shifted - side
    }

    private fun updateRatio() {
        val detected = detectPitchHz() ?: return
        if (detected < 60f || detected > 1200f) return

        // Convert to semitones from A4, snap to the scale, take the difference.
        val midi = 69.0 + 12.0 * (kotlin.math.ln(detected / 440.0) / kotlin.math.ln(2.0))
        val nearest = snapToScale(midi)
        val correction = (nearest - midi) * strength
        val target = 2f.pow((correction / 12.0).toFloat()).coerceIn(0.75f, 1.33f)
        // Glide rather than jump, so sustained notes do not warble.
        currentRatio += (target - currentRatio) * 0.25f
    }

    private fun snapToScale(midi: Double): Double {
        val octave = kotlin.math.floor(midi / 12.0)
        val within = midi - octave * 12.0
        var best = within
        var bestDistance = Double.MAX_VALUE
        for (step in scale) {
            val candidate = ((step + root) % 12).toDouble()
            for (shift in -12..12 step 12) {
                val option = candidate + shift
                val distance = kotlin.math.abs(option - within)
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = option
                }
            }
        }
        return octave * 12.0 + best
    }

    /** Autocorrelation pitch detection: cheap, and adequate for monophonic vocals. */
    private fun detectPitchHz(): Float? {
        var rms = 0f
        for (v in window) rms += v * v
        rms = kotlin.math.sqrt(rms / WINDOW)
        if (rms < 0.005f) return null

        val minLag = sampleRate / 1200
        val maxLag = (sampleRate / 60).coerceAtMost(WINDOW - 1)
        if (maxLag <= minLag) return null

        var bestLag = -1
        var bestScore = 0f
        for (lag in minLag..maxLag) {
            var sum = 0f
            var i = 0
            while (i + lag < WINDOW) {
                sum += window[i] * window[i + lag]
                i++
            }
            val score = sum / (WINDOW - lag)
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestLag <= 0 || bestScore < 0.01f) return null
        return sampleRate.toFloat() / bestLag
    }

    override fun reset() {
        shifter.reset()
        window.fill(0f)
        windowIndex = 0
        currentRatio = 1f
    }

    companion object {
        private const val WINDOW = 1024
        val MAJOR = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        val MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        val PENTATONIC = intArrayOf(0, 2, 4, 7, 9)
        val CHROMATIC = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
    }
}

// --------------------------------------------------------------------------------
// Transport-style effects
// --------------------------------------------------------------------------------

/**
 * Tape stop / turntable brake. Triggered rather than continuous: the read rate ramps
 * to zero over [durationMs], then holds silent until released.
 */
class TapeStop : AudioEffect {
    override var enabled = false

    @Volatile var durationMs = 1200f

    /** Set true to start braking, false to spin back up. */
    @Volatile var engaged = false

    private var line = DelayLine(96000)
    private var sampleRate = 44100
    private var speed = 1f
    private var readOffset = 0f

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        line = DelayLine(sampleRate * 2)
        speed = 1f
        readOffset = 1f
    }

    override fun process(frame: AudioFrame) {
        line.write((frame.l + frame.r) * 0.5f)

        val step = 1000f / (durationMs.coerceAtLeast(50f) * sampleRate / 1000f)
        speed = if (engaged) {
            (speed - step).coerceAtLeast(0f)
        } else {
            (speed + step * 2f).coerceAtMost(1f)
        }

        if (speed >= 1f) return

        // As speed falls the read head lags further behind, dropping pitch with it.
        readOffset += (1f - speed)
        if (readOffset > sampleRate * 1.5f) readOffset = sampleRate * 1.5f
        val out = line.read(readOffset.coerceAtLeast(1f)) * speed
        frame.l = out
        frame.r = out
    }

    override fun reset() {
        line.reset(); speed = 1f; readOffset = 1f
    }
}

/**
 * Reverse: buffers a slice and plays it backwards on a loop. Because playback is
 * still moving forward underneath, this reads as a stutter-reverse rather than the
 * whole track running in reverse.
 */
class Reverse : AudioEffect {
    override var enabled = false

    @Volatile var sliceMs = 500f

    private var bufferL = FloatArray(1)
    private var bufferR = FloatArray(1)
    private var writeIndex = 0
    private var sliceSamples = 22050
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        bufferL = FloatArray(sampleRate * 2)
        bufferR = FloatArray(sampleRate * 2)
        writeIndex = 0
    }

    override fun process(frame: AudioFrame) {
        sliceSamples = (sliceMs * sampleRate / 1000f).toInt().coerceIn(1000, bufferL.size)

        bufferL[writeIndex] = frame.l
        bufferR[writeIndex] = frame.r

        // Read mirrored within the current slice.
        val positionInSlice = writeIndex % sliceSamples
        val sliceStart = writeIndex - positionInSlice
        val mirrored = sliceStart + (sliceSamples - 1 - positionInSlice)
        val readIndex = ((mirrored % bufferL.size) + bufferL.size) % bufferL.size

        frame.l = bufferL[readIndex]
        frame.r = bufferR[readIndex]

        writeIndex = (writeIndex + 1) % bufferL.size
    }

    override fun reset() {
        bufferL.fill(0f); bufferR.fill(0f); writeIndex = 0
    }
}

// --------------------------------------------------------------------------------
// Convolution
// --------------------------------------------------------------------------------

/**
 * Convolution reverb using uniform partitioned overlap-add.
 *
 * Impulse responses are synthesised rather than shipped as audio assets - decaying
 * filtered noise with early reflections, which is what a real IR mostly is. Runs on
 * the mono sum and re-spreads afterwards to halve the cost; this is still the most
 * expensive unit in the rack, so it is off by default.
 */
class ConvolutionReverb : AudioEffect {
    override var enabled = false

    enum class Space { ROOM, HALL, PLATE, CATHEDRAL }

    @Volatile var space = Space.HALL
    @Volatile var mix = 0.3f

    private var sampleRate = 44100
    private var builtFor: Space? = null

    private var partitions = 0
    private var irRe: Array<FloatArray> = emptyArray()
    private var irIm: Array<FloatArray> = emptyArray()
    private var historyRe: Array<FloatArray> = emptyArray()
    private var historyIm: Array<FloatArray> = emptyArray()
    private var historyIndex = 0

    private val inputBlock = FloatArray(BLOCK)
    private var inputFill = 0
    private val overlap = FloatArray(BLOCK)
    private val output = FloatArray(BLOCK)
    private var outputRead = BLOCK

    private val fftRe = FloatArray(FFT_SIZE)
    private val fftIm = FloatArray(FFT_SIZE)
    private val accRe = FloatArray(FFT_SIZE)
    private val accIm = FloatArray(FFT_SIZE)

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        builtFor = null
        inputFill = 0
        outputRead = BLOCK
        overlap.fill(0f)
    }

    fun ensureBuilt() {
        if (builtFor == space) return
        buildImpulse(space)
        builtFor = space
    }

    private fun buildImpulse(space: Space) {
        val (seconds, decay, brightness) = when (space) {
            Space.ROOM -> Triple(0.35f, 5.5f, 0.55f)
            Space.HALL -> Triple(0.9f, 3.2f, 0.4f)
            Space.PLATE -> Triple(0.6f, 4.0f, 0.75f)
            Space.CATHEDRAL -> Triple(1.6f, 2.0f, 0.3f)
        }

        val length = (seconds * sampleRate).toInt()
        val ir = FloatArray(length)
        val random = kotlin.random.Random(space.ordinal * 7919)
        val tone = Biquad().apply { setLowPass(1500f + brightness * 9000f, 0.7f, sampleRate) }

        for (i in ir.indices) {
            val t = i.toFloat() / sampleRate
            val envelope = kotlin.math.exp(-decay * t)
            ir[i] = tone.processLeft((random.nextFloat() * 2f - 1f) * envelope)
        }
        // Early reflections give the space a recognisable size.
        val reflections = intArrayOf(7, 13, 19, 29, 43, 61)
        for ((n, ms) in reflections.withIndex()) {
            val index = (ms * sampleRate / 1000f).toInt()
            if (index < ir.size) ir[index] += 0.45f / (n + 1)
        }

        // Normalise so switching spaces does not jump in level.
        var energy = 0f
        for (v in ir) energy += v * v
        val norm = if (energy > 0f) 0.35f / kotlin.math.sqrt(energy / ir.size) / ir.size.toFloat().pow(0.5f) else 1f
        for (i in ir.indices) ir[i] *= norm

        partitions = (ir.size + BLOCK - 1) / BLOCK
        irRe = Array(partitions) { FloatArray(FFT_SIZE) }
        irIm = Array(partitions) { FloatArray(FFT_SIZE) }
        historyRe = Array(partitions) { FloatArray(FFT_SIZE) }
        historyIm = Array(partitions) { FloatArray(FFT_SIZE) }
        historyIndex = 0

        for (p in 0 until partitions) {
            val re = irRe[p]
            val im = irIm[p]
            for (i in 0 until BLOCK) {
                val index = p * BLOCK + i
                re[i] = if (index < ir.size) ir[index] else 0f
            }
            Fft.transform(re, im)
        }
    }

    override fun process(frame: AudioFrame) {
        ensureBuilt()
        if (partitions == 0) return

        val dryL = frame.l
        val dryR = frame.r
        val mono = (dryL + dryR) * 0.5f

        inputBlock[inputFill] = mono
        inputFill++

        if (inputFill >= BLOCK) {
            inputFill = 0
            processBlock()
            outputRead = 0
        }

        val wet = if (outputRead < BLOCK) output[outputRead++] else 0f
        // Slight decorrelation so the tail is not dead centre.
        frame.l = dryL * (1f - mix) + wet * mix
        frame.r = dryR * (1f - mix) + wet * mix * 0.95f
    }

    private fun processBlock() {
        // Forward transform of this block, zero-padded to double length.
        for (i in 0 until BLOCK) {
            fftRe[i] = inputBlock[i]
            fftIm[i] = 0f
        }
        for (i in BLOCK until FFT_SIZE) {
            fftRe[i] = 0f
            fftIm[i] = 0f
        }
        Fft.transform(fftRe, fftIm)

        historyRe[historyIndex] = fftRe.copyOf()
        historyIm[historyIndex] = fftIm.copyOf()

        accRe.fill(0f)
        accIm.fill(0f)
        for (p in 0 until partitions) {
            val h = (historyIndex - p + partitions) % partitions
            val xr = historyRe[h]
            val xi = historyIm[h]
            val hr = irRe[p]
            val hi = irIm[p]
            for (k in 0 until FFT_SIZE) {
                accRe[k] += xr[k] * hr[k] - xi[k] * hi[k]
                accIm[k] += xr[k] * hi[k] + xi[k] * hr[k]
            }
        }

        Fft.transform(accRe, accIm, inverse = true)

        for (i in 0 until BLOCK) {
            output[i] = accRe[i] + overlap[i]
            overlap[i] = accRe[i + BLOCK]
        }

        historyIndex = (historyIndex + 1) % partitions
    }

    override fun reset() {
        inputFill = 0
        outputRead = BLOCK
        overlap.fill(0f)
        output.fill(0f)
        historyRe.forEach { it.fill(0f) }
        historyIm.forEach { it.fill(0f) }
    }

    companion object {
        private const val BLOCK = 256
        private const val FFT_SIZE = 512
    }
}
