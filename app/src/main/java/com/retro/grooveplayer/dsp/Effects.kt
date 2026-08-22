package com.retro.grooveplayer.dsp

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * One processing unit in the rack. Parameters are written from the UI thread and read
 * on the audio thread, so anything the UI touches is marked @Volatile.
 */
interface AudioEffect {
    var enabled: Boolean
    fun prepare(sampleRate: Int)
    fun process(frame: AudioFrame)
    fun reset()
}

// --------------------------------------------------------------------------------
// Spectral / frequency
// --------------------------------------------------------------------------------

/**
 * Parametric EQ with an optional dynamic mode.
 *
 * A static band applies fixed gain. In dynamic mode the band only acts once the
 * energy in that region crosses a threshold, which is how you tame an occasional
 * boomy note without dulling the whole track.
 */
class ParametricEq(private val bandCount: Int = 5) : AudioEffect {
    override var enabled = false

    class Band(
        @Volatile var freq: Float,
        @Volatile var q: Float,
        @Volatile var gainDb: Float,
        @Volatile var dynamic: Boolean = false,
        @Volatile var thresholdDb: Float = -18f
    )

    val bands = Array(bandCount) { i ->
        Band(freq = 80f * 3f.pow(i.toFloat()), q = 1.0f, gainDb = 0f)
    }

    private val filters = Array(bandCount) { Biquad() }
    private val detectors = Array(bandCount) { Biquad() }
    private val followers = Array(bandCount) { EnvelopeFollower(15f, 120f) }
    private var sampleRate = 44100
    private var dirty = true

    /** Call after changing any band parameter. */
    fun invalidate() {
        dirty = true
    }

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        followers.forEach { it.prepare(sampleRate) }
        dirty = true
    }

    private fun rebuild() {
        for (i in 0 until bandCount) {
            val b = bands[i]
            filters[i].setPeaking(b.freq, b.q, b.gainDb, sampleRate)
            detectors[i].setBandPass(b.freq, b.q, sampleRate)
        }
        dirty = false
    }

    override fun process(frame: AudioFrame) {
        if (dirty) rebuild()
        for (i in 0 until bandCount) {
            val b = bands[i]
            if (b.gainDb == 0f) continue
            if (b.dynamic) {
                // Detect energy in this band only, then scale the applied gain by how
                // far it exceeds the threshold.
                val probe = detectors[i].processLeft(frame.l)
                val env = followers[i].process(probe)
                val envDb = Db.fromGain(env)
                val amount = ((envDb - b.thresholdDb) / 12f).coerceIn(0f, 1f)
                if (amount <= 0f) continue
                filters[i].setPeaking(b.freq, b.q, b.gainDb * amount, sampleRate)
            }
            frame.l = filters[i].processLeft(frame.l)
            frame.r = filters[i].processRight(frame.r)
        }
    }

    override fun reset() {
        filters.forEach { it.reset() }
        detectors.forEach { it.reset() }
        followers.forEach { it.reset() }
    }
}

/**
 * Spectral resonance suppression.
 *
 * A bank of narrow band detectors watches for frequencies that ring far louder than
 * the local average, then notches only those, only while they ring. This is what
 * smooths harsh vocal top end and cymbal glare without dulling everything.
 */
class ResonanceSuppressor(private val nodeCount: Int = 12) : AudioEffect {
    override var enabled = false

    /** How aggressively resonant peaks are pulled down, 0..1. */
    @Volatile var amount = 0.5f

    private val frequencies = FloatArray(nodeCount)
    private val detectors = Array(nodeCount) { Biquad() }
    private val notches = Array(nodeCount) { Biquad() }
    private val fast = Array(nodeCount) { EnvelopeFollower(2f, 40f) }
    private val slow = Array(nodeCount) { EnvelopeFollower(200f, 800f) }
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        // Log-spaced nodes across the range where harshness actually lives.
        val lo = 1200.0
        val hi = 12000.0
        for (i in 0 until nodeCount) {
            val t = i.toDouble() / (nodeCount - 1)
            frequencies[i] = (lo * (hi / lo).pow(t)).toFloat()
            detectors[i].setBandPass(frequencies[i], 6f, sampleRate)
            fast[i].prepare(sampleRate)
            slow[i].prepare(sampleRate)
        }
    }

    override fun process(frame: AudioFrame) {
        val mono = (frame.l + frame.r) * 0.5f
        for (i in 0 until nodeCount) {
            val probe = detectors[i].processLeft(mono)
            val peak = fast[i].process(probe)
            val average = slow[i].process(probe)
            if (average < 1e-6f) continue

            // How far this node sticks out above its own running average.
            val excessDb = Db.fromGain(peak / average)
            if (excessDb <= 3f) continue

            val cutDb = -((excessDb - 3f) * amount).coerceIn(0f, 12f)
            notches[i].setPeaking(frequencies[i], 6f, cutDb, sampleRate)
            frame.l = notches[i].processLeft(frame.l)
            frame.r = notches[i].processRight(frame.r)
        }
    }

    override fun reset() {
        detectors.forEach { it.reset() }
        notches.forEach { it.reset() }
        fast.forEach { it.reset() }
        slow.forEach { it.reset() }
    }
}

// --------------------------------------------------------------------------------
// Dynamics
// --------------------------------------------------------------------------------

/** Three-band compressor split by two Linkwitz-Riley crossovers. */
class MultibandCompressor : AudioEffect {
    override var enabled = false

    @Volatile var lowCrossover = 250f
    @Volatile var highCrossover = 3000f

    class BandSettings(
        @Volatile var thresholdDb: Float = -18f,
        @Volatile var ratio: Float = 3f,
        @Volatile var makeupDb: Float = 0f
    )

    val low = BandSettings()
    val mid = BandSettings()
    val high = BandSettings()

    private val splitLow = LinkwitzRileyCrossover()
    private val splitHigh = LinkwitzRileyCrossover()
    private val envLow = EnvelopeFollower(10f, 150f)
    private val envMid = EnvelopeFollower(8f, 120f)
    private val envHigh = EnvelopeFollower(4f, 80f)

    override fun prepare(sampleRate: Int) {
        splitLow.setFrequency(lowCrossover, sampleRate)
        splitHigh.setFrequency(highCrossover, sampleRate)
        envLow.prepare(sampleRate)
        envMid.prepare(sampleRate)
        envHigh.prepare(sampleRate)
    }

    private fun compress(
        sample: Float,
        env: EnvelopeFollower,
        settings: BandSettings,
        detector: Float
    ): Float {
        val level = env.process(detector)
        val reduction = GainComputer.gainDb(Db.fromGain(level), settings.thresholdDb, settings.ratio, 6f)
        return sample * Db.toGain(reduction + settings.makeupDb)
    }

    override fun process(frame: AudioFrame) {
        val lowL = splitLow.lowLeft(frame.l)
        val lowR = splitLow.lowRight(frame.r)
        val restL = splitLow.highLeft(frame.l)
        val restR = splitLow.highRight(frame.r)

        val midL = splitHigh.lowLeft(restL)
        val midR = splitHigh.lowRight(restR)
        val highL = splitHigh.highLeft(restL)
        val highR = splitHigh.highRight(restR)

        val lowDetector = max(abs(lowL), abs(lowR))
        val midDetector = max(abs(midL), abs(midR))
        val highDetector = max(abs(highL), abs(highR))

        // Each band shares one detector so the stereo image is not pulled apart.
        val lowGain = Db.toGain(
            GainComputer.gainDb(Db.fromGain(envLow.process(lowDetector)), low.thresholdDb, low.ratio, 6f) + low.makeupDb
        )
        val midGain = Db.toGain(
            GainComputer.gainDb(Db.fromGain(envMid.process(midDetector)), mid.thresholdDb, mid.ratio, 6f) + mid.makeupDb
        )
        val highGain = Db.toGain(
            GainComputer.gainDb(Db.fromGain(envHigh.process(highDetector)), high.thresholdDb, high.ratio, 6f) + high.makeupDb
        )

        frame.l = lowL * lowGain + midL * midGain + highL * highGain
        frame.r = lowR * lowGain + midR * midGain + highR * highGain
    }

    override fun reset() {
        splitLow.reset(); splitHigh.reset()
        envLow.reset(); envMid.reset(); envHigh.reset()
    }
}

/**
 * Brickwall limiter with lookahead.
 *
 * This replaces the hard coerceIn clamps that used to end the chain. Clamping squares
 * off peaks, which is audible as crackle; a limiter rides the gain down smoothly
 * before the peak arrives.
 */
class BrickwallLimiter : AudioEffect {
    override var enabled = true

    /** Output ceiling in dB, always at or below 0. */
    @Volatile var ceilingDb = -0.3f
    @Volatile var releaseMs = 60f

    private var lookaheadSamples = 64
    private var delayL = FloatArray(1)
    private var delayR = FloatArray(1)
    private var writeIndex = 0
    private var gain = 1f
    private var releaseCoeff = 0.999f

    override fun prepare(sampleRate: Int) {
        lookaheadSamples = (sampleRate * 0.0015f).toInt().coerceAtLeast(8)
        delayL = FloatArray(lookaheadSamples)
        delayR = FloatArray(lookaheadSamples)
        writeIndex = 0
        gain = 1f
        releaseCoeff = kotlin.math.exp(
            -1.0 / ((releaseMs / 1000.0) * sampleRate)
        ).toFloat()
    }

    override fun process(frame: AudioFrame) {
        val ceiling = Db.toGain(ceilingDb)

        val delayedL = delayL[writeIndex]
        val delayedR = delayR[writeIndex]
        delayL[writeIndex] = frame.l
        delayR[writeIndex] = frame.r
        writeIndex = (writeIndex + 1) % lookaheadSamples

        // Target gain is set by the loudest sample in the lookahead window.
        val peak = max(abs(frame.l), abs(frame.r))
        val target = if (peak > ceiling) ceiling / peak else 1f

        gain = if (target < gain) target else target + releaseCoeff * (gain - target)

        frame.l = (delayedL * gain).coerceIn(-ceiling, ceiling)
        frame.r = (delayedR * gain).coerceIn(-ceiling, ceiling)
    }

    override fun reset() {
        delayL.fill(0f)
        delayR.fill(0f)
        writeIndex = 0
        gain = 1f
    }
}

/**
 * Transient shaper: separates attack from sustain using two envelope followers at
 * different speeds, then rebalances them independently of overall level.
 */
class TransientShaper : AudioEffect {
    override var enabled = false

    /** -1 softens the attack, +1 sharpens it. */
    @Volatile var attackAmount = 0f

    /** -1 tightens the tail, +1 lengthens it. */
    @Volatile var sustainAmount = 0f

    private val fast = EnvelopeFollower(0.5f, 40f)
    private val slow = EnvelopeFollower(25f, 400f)

    override fun prepare(sampleRate: Int) {
        fast.prepare(sampleRate)
        slow.prepare(sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val detector = max(abs(frame.l), abs(frame.r))
        val f = fast.process(detector)
        val s = slow.process(detector)
        if (s < 1e-7f) return

        // Fast rising above slow means a transient; the reverse means a decaying tail.
        val difference = Db.fromGain(f / s)
        val attackGainDb = (difference.coerceAtLeast(0f)) * attackAmount * 0.8f
        val sustainGainDb = (-difference.coerceAtMost(0f)) * sustainAmount * 0.5f

        val gain = Db.toGain((attackGainDb + sustainGainDb).coerceIn(-18f, 18f))
        frame.l *= gain
        frame.r *= gain
    }

    override fun reset() {
        fast.reset()
        slow.reset()
    }
}

/**
 * Sidechain-style pumping. Without a separate kick track to key from, the duck is
 * driven by a tempo-locked LFO, which is how the effect is produced in edits anyway.
 */
class SidechainPump : AudioEffect {
    override var enabled = false

    @Volatile var bpm = 120f
    @Volatile var depth = 0.6f

    private val lfo = Lfo()
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        lfo.setRate(bpm / 60f, sampleRate)
    }

    fun updateRate() {
        lfo.setRate(bpm / 60f, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        // Ramp restarts each beat; gain recovers across the beat like a release curve.
        val phase = lfo.nextRamp()
        val duck = 1f - depth * (1f - phase).pow(2f)
        frame.l *= duck
        frame.r *= duck
    }

    override fun reset() {
        lfo.reset()
    }
}

/** Hard gate with a rhythmic option, for stutter and "empty gap" effects. */
class Gate : AudioEffect {
    override var enabled = false

    @Volatile var thresholdDb = -45f
    @Volatile var stutterHz = 0f

    private val follower = EnvelopeFollower(1f, 60f)
    private val lfo = Lfo()
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        follower.prepare(sampleRate)
        lfo.setRate(stutterHz, sampleRate)
    }

    fun updateRate() {
        lfo.setRate(stutterHz, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        if (stutterHz > 0f) {
            val phase = lfo.nextRamp()
            if (phase > 0.5f) {
                frame.l = 0f
                frame.r = 0f
                return
            }
        }
        val level = follower.process(max(abs(frame.l), abs(frame.r)))
        if (Db.fromGain(level) < thresholdDb) {
            frame.l = 0f
            frame.r = 0f
        }
    }

    override fun reset() {
        follower.reset()
        lfo.reset()
    }
}

// --------------------------------------------------------------------------------
// Harmonic / non-linear
// --------------------------------------------------------------------------------

class Saturator : AudioEffect {
    override var enabled = false

    enum class Character { TAPE, TUBE, TRANSISTOR }

    @Volatile var character = Character.TAPE
    @Volatile var drive = 2f
    @Volatile var mix = 1f

    override fun prepare(sampleRate: Int) = Unit

    override fun process(frame: AudioFrame) {
        val dryL = frame.l
        val dryR = frame.r
        val wetL: Float
        val wetR: Float
        when (character) {
            Character.TAPE -> {
                wetL = Shapers.tape(dryL, drive); wetR = Shapers.tape(dryR, drive)
            }
            Character.TUBE -> {
                wetL = Shapers.tube(dryL, drive); wetR = Shapers.tube(dryR, drive)
            }
            Character.TRANSISTOR -> {
                wetL = Shapers.soft(dryL, drive); wetR = Shapers.soft(dryR, drive)
            }
        }
        frame.l = dryL * (1f - mix) + wetL * mix
        frame.r = dryR * (1f - mix) + wetR * mix
    }

    override fun reset() = Unit
}

/** Bit depth and sample-rate reduction, for lo-fi grit. */
class BitCrusher : AudioEffect {
    override var enabled = false

    @Volatile var bits = 12f
    @Volatile var downsample = 1f

    private var holdL = 0f
    private var holdR = 0f
    private var counter = 0f

    override fun prepare(sampleRate: Int) = Unit

    override fun process(frame: AudioFrame) {
        counter += 1f
        if (counter >= downsample) {
            counter = 0f
            val levels = 2f.pow(bits.coerceIn(2f, 24f))
            holdL = kotlin.math.round(frame.l * levels) / levels
            holdR = kotlin.math.round(frame.r * levels) / levels
        }
        frame.l = holdL
        frame.r = holdR
    }

    override fun reset() {
        holdL = 0f; holdR = 0f; counter = 0f
    }
}

// --------------------------------------------------------------------------------
// Modulation / time
// --------------------------------------------------------------------------------

/** Chorus, flanger, phaser and tremolo share one unit since they share machinery. */
class Modulation : AudioEffect {
    override var enabled = false

    enum class Type { CHORUS, FLANGER, PHASER, TREMOLO }

    @Volatile var type = Type.CHORUS
    @Volatile var rateHz = 0.5f
    @Volatile var depth = 0.5f
    @Volatile var feedback = 0.3f

    private var sampleRate = 44100
    private val lfoL = Lfo()
    private val lfoR = Lfo()
    private var delayL = DelayLine(4096)
    private var delayR = DelayLine(4096)
    private val allpassL = Array(4) { Biquad() }
    private val allpassR = Array(4) { Biquad() }
    private var feedbackL = 0f
    private var feedbackR = 0f

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        delayL = DelayLine((sampleRate * 0.05f).toInt())
        delayR = DelayLine((sampleRate * 0.05f).toInt())
        lfoL.setRate(rateHz, sampleRate)
        lfoR.setRate(rateHz, sampleRate)
        // Offset the right channel so modulation is not mono.
        repeat((sampleRate / 8)) { lfoR.next() }
    }

    fun updateRate() {
        lfoL.setRate(rateHz, sampleRate)
        lfoR.setRate(rateHz, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val modL = lfoL.next()
        val modR = lfoR.next()

        when (type) {
            Type.TREMOLO -> {
                frame.l *= 1f - depth * (0.5f + 0.5f * modL)
                frame.r *= 1f - depth * (0.5f + 0.5f * modR)
            }
            Type.PHASER -> {
                // Sweeping allpass stages create moving notches.
                val freq = 400f + 1600f * (0.5f + 0.5f * modL) * depth
                var l = frame.l + feedbackL * feedback
                var r = frame.r + feedbackR * feedback
                for (i in allpassL.indices) {
                    allpassL[i].setAllPass(freq * (1f + i * 0.35f), 0.7f, sampleRate)
                    allpassR[i].setAllPass(freq * (1f + i * 0.35f), 0.7f, sampleRate)
                    l = allpassL[i].processLeft(l)
                    r = allpassR[i].processRight(r)
                }
                feedbackL = l
                feedbackR = r
                frame.l = (frame.l + l) * 0.5f
                frame.r = (frame.r + r) * 0.5f
            }
            Type.FLANGER, Type.CHORUS -> {
                // Flanger sweeps a very short delay with feedback; chorus uses a longer
                // delay and no feedback, which is the only real difference between them.
                val baseMs = if (type == Type.FLANGER) 2f else 18f
                val swingMs = if (type == Type.FLANGER) 1.8f else 6f
                val fb = if (type == Type.FLANGER) feedback else 0f

                val dL = (baseMs + swingMs * modL * depth) * sampleRate / 1000f
                val dR = (baseMs + swingMs * modR * depth) * sampleRate / 1000f

                val wetL = delayL.read(dL)
                val wetR = delayR.read(dR)
                delayL.write(frame.l + wetL * fb)
                delayR.write(frame.r + wetR * fb)

                frame.l = frame.l * 0.7f + wetL * 0.7f
                frame.r = frame.r * 0.7f + wetR * 0.7f
            }
        }
    }

    override fun reset() {
        delayL.reset(); delayR.reset()
        allpassL.forEach { it.reset() }
        allpassR.forEach { it.reset() }
        feedbackL = 0f; feedbackR = 0f
        lfoL.reset(); lfoR.reset()
    }
}

/** Tape wow and flutter: slow and fast random pitch drift via a modulated delay. */
class WowFlutter : AudioEffect {
    override var enabled = false

    @Volatile var amount = 0.3f

    private var delayL = DelayLine(4096)
    private var delayR = DelayLine(4096)
    private val wow = Lfo()
    private val flutter = Lfo()
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        delayL = DelayLine((sampleRate * 0.05f).toInt())
        delayR = DelayLine((sampleRate * 0.05f).toInt())
        wow.setRate(0.7f, sampleRate)
        flutter.setRate(6.3f, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val drift = wow.next() * 3.5f + flutter.next() * 0.7f
        val base = 12f * sampleRate / 1000f
        val offset = drift * amount * sampleRate / 1000f

        delayL.write(frame.l)
        delayR.write(frame.r)
        frame.l = delayL.read(base + offset)
        frame.r = delayR.read(base + offset)
    }

    override fun reset() {
        delayL.reset(); delayR.reset()
        wow.reset(); flutter.reset()
    }
}

/** Stereo / ping-pong delay with tempo-style subdivisions. */
class PingPongDelay : AudioEffect {
    override var enabled = false

    @Volatile var timeMs = 375f
    @Volatile var feedback = 0.4f
    @Volatile var mix = 0.3f
    @Volatile var pingPong = true

    private var delayL = DelayLine(4096)
    private var delayR = DelayLine(4096)
    private var sampleRate = 44100
    private val dampL = Biquad()
    private val dampR = Biquad()

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        val maxSamples = (sampleRate * 2.5f).toInt()
        delayL = DelayLine(maxSamples)
        delayR = DelayLine(maxSamples)
        // Rolling off the repeats stops them turning into harsh noise.
        dampL.setLowPass(6000f, 0.7f, sampleRate)
        dampR.setLowPass(6000f, 0.7f, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        val d = (timeMs * sampleRate / 1000f).coerceAtLeast(1f)
        val wetL = dampL.processLeft(delayL.read(d))
        val wetR = dampR.processRight(delayR.read(d))

        if (pingPong) {
            // Each repeat crosses to the opposite channel.
            delayL.write(frame.l + wetR * feedback)
            delayR.write(frame.r + wetL * feedback)
        } else {
            delayL.write(frame.l + wetL * feedback)
            delayR.write(frame.r + wetR * feedback)
        }

        frame.l = frame.l * (1f - mix) + wetL * mix
        frame.r = frame.r * (1f - mix) + wetR * mix
    }

    override fun reset() {
        delayL.reset(); delayR.reset()
        dampL.reset(); dampR.reset()
    }
}

/**
 * Freeverb-style algorithmic reverb: eight damped comb filters per channel into four
 * allpass sections. Considerably richer than a plain Schroeder network.
 */
class AlgorithmicReverb : AudioEffect {
    override var enabled = false

    @Volatile var roomSize = 0.6f
    @Volatile var damping = 0.4f
    @Volatile var width = 1f
    @Volatile var mix = 0.3f

    private val combTuning = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allpassTuning = intArrayOf(556, 441, 341, 225)
    private val stereoSpread = 23

    private lateinit var combL: Array<FloatArray>
    private lateinit var combR: Array<FloatArray>
    private lateinit var apL: Array<FloatArray>
    private lateinit var apR: Array<FloatArray>
    private lateinit var combIdxL: IntArray
    private lateinit var combIdxR: IntArray
    private lateinit var apIdxL: IntArray
    private lateinit var apIdxR: IntArray
    private lateinit var filterStoreL: FloatArray
    private lateinit var filterStoreR: FloatArray

    override fun prepare(sampleRate: Int) {
        val scale = sampleRate / 44100f
        combL = Array(combTuning.size) { FloatArray((combTuning[it] * scale).toInt().coerceAtLeast(1)) }
        combR = Array(combTuning.size) { FloatArray(((combTuning[it] + stereoSpread) * scale).toInt().coerceAtLeast(1)) }
        apL = Array(allpassTuning.size) { FloatArray((allpassTuning[it] * scale).toInt().coerceAtLeast(1)) }
        apR = Array(allpassTuning.size) { FloatArray(((allpassTuning[it] + stereoSpread) * scale).toInt().coerceAtLeast(1)) }
        combIdxL = IntArray(combTuning.size)
        combIdxR = IntArray(combTuning.size)
        apIdxL = IntArray(allpassTuning.size)
        apIdxR = IntArray(allpassTuning.size)
        filterStoreL = FloatArray(combTuning.size)
        filterStoreR = FloatArray(combTuning.size)
    }

    override fun process(frame: AudioFrame) {
        val input = (frame.l + frame.r) * 0.015f
        val feedback = 0.7f + roomSize * 0.28f
        val damp1 = damping * 0.4f
        val damp2 = 1f - damp1

        var outL = 0f
        var outR = 0f

        for (i in combL.indices) {
            val lineL = combL[i]
            val iL = combIdxL[i]
            val yL = lineL[iL]
            filterStoreL[i] = yL * damp2 + filterStoreL[i] * damp1
            lineL[iL] = input + filterStoreL[i] * feedback
            combIdxL[i] = (iL + 1) % lineL.size
            outL += yL

            val lineR = combR[i]
            val iR = combIdxR[i]
            val yR = lineR[iR]
            filterStoreR[i] = yR * damp2 + filterStoreR[i] * damp1
            lineR[iR] = input + filterStoreR[i] * feedback
            combIdxR[i] = (iR + 1) % lineR.size
            outR += yR
        }

        for (i in apL.indices) {
            val lineL = apL[i]
            val iL = apIdxL[i]
            val bufL = lineL[iL]
            lineL[iL] = outL + bufL * 0.5f
            apIdxL[i] = (iL + 1) % lineL.size
            outL = bufL - outL

            val lineR = apR[i]
            val iR = apIdxR[i]
            val bufR = lineR[iR]
            lineR[iR] = outR + bufR * 0.5f
            apIdxR[i] = (iR + 1) % lineR.size
            outR = bufR - outR
        }

        val wet1 = mix * (width / 2f + 0.5f)
        val wet2 = mix * ((1f - width) / 2f)
        val dryL = frame.l
        val dryR = frame.r
        frame.l = dryL * (1f - mix) + outL * wet1 + outR * wet2
        frame.r = dryR * (1f - mix) + outR * wet1 + outL * wet2
    }

    override fun reset() {
        if (!::combL.isInitialized) return
        combL.forEach { it.fill(0f) }
        combR.forEach { it.fill(0f) }
        apL.forEach { it.fill(0f) }
        apR.forEach { it.fill(0f) }
        filterStoreL.fill(0f)
        filterStoreR.fill(0f)
    }
}

// --------------------------------------------------------------------------------
// Spatial
// --------------------------------------------------------------------------------

/**
 * Mid/side width control with optional Haas widening and a bass mono-maker.
 *
 * Collapsing low frequencies to mono is standard practice: wide bass smears on
 * speakers and wastes headroom.
 */
class StereoImager : AudioEffect {
    override var enabled = false

    /** 0 = mono, 1 = unchanged, 2 = double width. */
    @Volatile var width = 1f

    /** Frequencies below this stay mono. 0 disables. */
    @Volatile var bassMonoHz = 120f

    /** Haas delay on one channel, in milliseconds. 0 disables. */
    @Volatile var haasMs = 0f

    private val bassSplit = LinkwitzRileyCrossover()
    private var haasLine = DelayLine(4096)
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        bassSplit.setFrequency(bassMonoHz.coerceAtLeast(20f), sampleRate)
        haasLine = DelayLine((sampleRate * 0.05f).toInt())
    }

    fun updateBassFrequency() {
        bassSplit.setFrequency(bassMonoHz.coerceAtLeast(20f), sampleRate)
    }

    override fun process(frame: AudioFrame) {
        var l = frame.l
        var r = frame.r

        var monoLowL = 0f
        var monoLowR = 0f
        if (bassMonoHz > 20f) {
            monoLowL = bassSplit.lowLeft(l)
            monoLowR = bassSplit.lowRight(r)
            l = bassSplit.highLeft(l)
            r = bassSplit.highRight(r)
        }

        if (haasMs > 0f) {
            haasLine.write(r)
            r = haasLine.read(haasMs * sampleRate / 1000f)
        }

        val mid = (l + r) * 0.5f
        val side = (l - r) * 0.5f * width
        l = mid + side
        r = mid - side

        if (bassMonoHz > 20f) {
            val lowMono = (monoLowL + monoLowR) * 0.5f
            l += lowMono
            r += lowMono
        }

        frame.l = l
        frame.r = r
    }

    override fun reset() {
        bassSplit.reset()
        haasLine.reset()
    }
}

// --------------------------------------------------------------------------------
// Pitch / timbre
// --------------------------------------------------------------------------------

/**
 * Granular pitch shifter: two crossfading delay taps sweeping at a constant rate.
 * Shifts pitch without changing tempo. Quality is that of a classic cheap shifter -
 * some warble on sustained tones - but it needs no FFT and runs in real time.
 */
class GranularPitchShifter(private val grainMs: Float = 60f) {
    private var line = DelayLine(8192)
    private var sampleRate = 44100
    private var grainSamples = 2646f
    private var phase = 0f
    private var ratio = 1f

    fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        grainSamples = grainMs * sampleRate / 1000f
        line = DelayLine((grainSamples * 3f).toInt())
        phase = 0f
    }

    fun setRatio(value: Float) {
        ratio = value.coerceIn(0.5f, 2f)
    }

    fun reset() {
        line.reset()
        phase = 0f
    }

    fun process(input: Float): Float {
        line.write(input)

        // The read pointer drifts at (1 - ratio); two taps half a grain apart are
        // crossfaded so the wrap-around discontinuity is masked.
        phase += (1f - ratio)
        if (phase >= grainSamples) phase -= grainSamples
        if (phase < 0f) phase += grainSamples

        val tap1 = phase + 1f
        val tap2 = phase + grainSamples * 0.5f + 1f

        val fade = phase / grainSamples
        val gain1 = kotlin.math.sin(fade * Math.PI.toFloat())
        val gain2 = kotlin.math.sin(((fade + 0.5f) % 1f) * Math.PI.toFloat())

        val a = line.read(tap1)
        val b = line.read(if (tap2 >= grainSamples * 2f) tap2 - grainSamples else tap2)
        val sum = gain1 + gain2
        return if (sum > 1e-6f) (a * gain1 + b * gain2) / sum else a
    }
}

/**
 * Formant shifting, independent of pitch.
 *
 * Resampling alone moves pitch and formants together, which is why a pitched-up voice
 * sounds like a chipmunk. Here the signal is resampled by the formant ratio and then
 * pitch-shifted back by its inverse, so the vocal tract resonances move while the
 * fundamental stays put.
 */
class FormantShifter : AudioEffect {
    override var enabled = false

    /** Semitones of formant shift; negative deepens, positive thins. */
    @Volatile var semitones = 0f

    private val shifterL = GranularPitchShifter()
    private val shifterR = GranularPitchShifter()
    private var resampleL = DelayLine(8192)
    private var resampleR = DelayLine(8192)
    private var readPos = 0f
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        shifterL.prepare(sampleRate)
        shifterR.prepare(sampleRate)
        resampleL = DelayLine(8192)
        resampleR = DelayLine(8192)
        readPos = 0f
    }

    override fun process(frame: AudioFrame) {
        val ratio = 2f.pow(semitones / 12f).coerceIn(0.5f, 2f)
        shifterL.setRatio(1f / ratio)
        shifterR.setRatio(1f / ratio)

        // Stage 1: resample (moves pitch and formants together).
        resampleL.write(frame.l)
        resampleR.write(frame.r)
        readPos += ratio
        if (readPos > 4096f) readPos -= 4096f
        val stagedL = resampleL.read((readPos % 2048f) + 1f)
        val stagedR = resampleR.read((readPos % 2048f) + 1f)

        // Stage 2: shift the pitch back, leaving the formants displaced.
        frame.l = shifterL.process(stagedL)
        frame.r = shifterR.process(stagedR)
    }

    override fun reset() {
        shifterL.reset(); shifterR.reset()
        resampleL.reset(); resampleR.reset()
        readPos = 0f
    }
}

/** Doubling: a short detuned delay that makes one take sound like two. */
class VocalDoubler : AudioEffect {
    override var enabled = false

    @Volatile var amount = 0.5f

    private var delayL = DelayLine(4096)
    private var delayR = DelayLine(4096)
    private val driftL = Lfo()
    private val driftR = Lfo()
    private var sampleRate = 44100

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        delayL = DelayLine((sampleRate * 0.1f).toInt())
        delayR = DelayLine((sampleRate * 0.1f).toInt())
        driftL.setRate(0.31f, sampleRate)
        driftR.setRate(0.43f, sampleRate)
    }

    override fun process(frame: AudioFrame) {
        delayL.write(frame.l)
        delayR.write(frame.r)
        val dL = (22f + driftL.next() * 3f) * sampleRate / 1000f
        val dR = (28f + driftR.next() * 3f) * sampleRate / 1000f
        // The double is panned opposite the source, which is what widens the image.
        frame.l += delayR.read(dR) * amount * 0.8f
        frame.r += delayL.read(dL) * amount * 0.8f
    }

    override fun reset() {
        delayL.reset(); delayR.reset()
        driftL.reset(); driftR.reset()
    }
}

// --------------------------------------------------------------------------------
// Texture
// --------------------------------------------------------------------------------

/**
 * Vinyl dust: surface hiss, random crackle and low rumble, generated rather than
 * sampled so it never loops audibly.
 */
class VinylDust : AudioEffect {
    override var enabled = false

    @Volatile var amount = 0.3f

    private val hissFilter = Biquad()
    private val rumbleFilter = Biquad()
    private var crackleEnv = 0f
    private var crackleSign = 1f
    private var sampleRate = 44100
    private var crackleDecay = 0.999f

    override fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        hissFilter.setHighPass(2000f, 0.7f, sampleRate)
        rumbleFilter.setLowPass(70f, 0.7f, sampleRate)
        crackleDecay = kotlin.math.exp(-1.0 / (0.002 * sampleRate)).toFloat()
    }

    override fun process(frame: AudioFrame) {
        val noise = Random.nextFloat() * 2f - 1f

        // Continuous surface hiss.
        val hiss = hissFilter.processLeft(noise) * 0.02f

        // Occasional pops, Poisson-ish: a low per-sample probability of a new impulse.
        if (Random.nextFloat() < 0.0004f) {
            crackleEnv = 1f
            crackleSign = if (Random.nextBoolean()) 1f else -1f
        }
        crackleEnv *= crackleDecay
        val crackle = crackleEnv * crackleSign * 0.35f

        // Turntable rumble.
        val rumble = rumbleFilter.processLeft(Random.nextFloat() * 2f - 1f) * 0.03f

        val dust = (hiss + crackle + rumble) * amount
        frame.l += dust
        frame.r += dust * 0.92f
    }

    override fun reset() {
        hissFilter.reset()
        rumbleFilter.reset()
        crackleEnv = 0f
    }
}
