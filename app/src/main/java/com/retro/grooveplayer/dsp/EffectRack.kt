package com.retro.grooveplayer.dsp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * User-facing rack parameters.
 *
 * Kept separate from the processing units so live playback and offline export can each
 * own an independent [EffectRack] - with its own filter state and sample rate - while
 * reading exactly the same settings. That is what makes an exported file sound like
 * what was previewed.
 *
 * Backed by Compose state so the UI recomposes, and read on the audio thread each
 * buffer via [EffectRack.sync].
 */
object RackSettings {

    // Spectral
    var eqEnabled by mutableStateOf(false)
    var eqBandGains by mutableStateOf(listOf(0f, 0f, 0f, 0f, 0f))
    var eqDynamic by mutableStateOf(false)

    var resonanceEnabled by mutableStateOf(false)
    var resonanceAmount by mutableStateOf(0.5f)

    // Dynamics
    var multibandEnabled by mutableStateOf(false)
    var multibandAmount by mutableStateOf(0.5f)

    var transientEnabled by mutableStateOf(false)
    var transientAttack by mutableStateOf(0f)
    var transientSustain by mutableStateOf(0f)

    var pumpEnabled by mutableStateOf(false)
    var pumpBpm by mutableStateOf(120f)
    var pumpDepth by mutableStateOf(0.6f)

    var gateEnabled by mutableStateOf(false)
    var gateThresholdDb by mutableStateOf(-45f)
    var gateStutterHz by mutableStateOf(0f)

    // Harmonic
    var saturationEnabled by mutableStateOf(false)
    var saturationCharacter by mutableStateOf(Saturator.Character.TAPE)
    var saturationDrive by mutableStateOf(2f)

    var bitcrushEnabled by mutableStateOf(false)
    var bitcrushBits by mutableStateOf(12f)
    var bitcrushDownsample by mutableStateOf(1f)

    // Modulation / time
    var modulationEnabled by mutableStateOf(false)
    var modulationType by mutableStateOf(Modulation.Type.CHORUS)
    var modulationRate by mutableStateOf(0.5f)
    var modulationDepth by mutableStateOf(0.5f)

    var wowFlutterEnabled by mutableStateOf(false)
    var wowFlutterAmount by mutableStateOf(0.3f)

    var delayEnabled by mutableStateOf(false)
    var delayTimeMs by mutableStateOf(375f)
    var delayFeedback by mutableStateOf(0.4f)
    var delayMix by mutableStateOf(0.3f)
    var delayPingPong by mutableStateOf(true)

    var reverbEnabled by mutableStateOf(false)
    var reverbSize by mutableStateOf(0.6f)
    var reverbDamping by mutableStateOf(0.4f)
    var reverbMix by mutableStateOf(0.3f)

    // Spatial
    var imagerEnabled by mutableStateOf(false)
    var stereoWidth by mutableStateOf(1f)
    var bassMonoHz by mutableStateOf(120f)
    var haasMs by mutableStateOf(0f)

    // Pitch / timbre
    var formantEnabled by mutableStateOf(false)
    var formantSemitones by mutableStateOf(0f)

    var doublerEnabled by mutableStateOf(false)
    var doublerAmount by mutableStateOf(0.5f)

    // Texture
    var dustEnabled by mutableStateOf(false)
    var dustAmount by mutableStateOf(0.3f)

    // Master
    var limiterEnabled by mutableStateOf(true)
    var limiterCeilingDb by mutableStateOf(-0.3f)
    var outputGainDb by mutableStateOf(0f)

    /** Bumped on every change so the audio thread knows to re-read cheaply. */
    var revision by mutableStateOf(0)
        private set

    fun touch() {
        revision++
    }

    /** True if anything other than the always-on limiter is active. */
    val anyEnabled: Boolean
        get() = eqEnabled || resonanceEnabled || multibandEnabled || transientEnabled ||
            pumpEnabled || gateEnabled || saturationEnabled || bitcrushEnabled ||
            modulationEnabled || wowFlutterEnabled || delayEnabled || reverbEnabled ||
            imagerEnabled || formantEnabled || doublerEnabled || dustEnabled

    fun resetAll() {
        eqEnabled = false; eqBandGains = listOf(0f, 0f, 0f, 0f, 0f); eqDynamic = false
        resonanceEnabled = false
        multibandEnabled = false
        transientEnabled = false; transientAttack = 0f; transientSustain = 0f
        pumpEnabled = false
        gateEnabled = false; gateStutterHz = 0f
        saturationEnabled = false
        bitcrushEnabled = false
        modulationEnabled = false
        wowFlutterEnabled = false
        delayEnabled = false
        reverbEnabled = false
        imagerEnabled = false; stereoWidth = 1f; haasMs = 0f
        formantEnabled = false; formantSemitones = 0f
        doublerEnabled = false
        dustEnabled = false
        outputGainDb = 0f
        touch()
    }
}

/**
 * The processing chain itself, in signal order.
 *
 * Order matters: corrective work first, then character, then space, then the limiter
 * last so nothing after it can push the signal back over the ceiling.
 */
class EffectRack {

    private val eq = ParametricEq(5)
    private val resonance = ResonanceSuppressor()
    private val multiband = MultibandCompressor()
    private val transient = TransientShaper()
    private val formant = FormantShifter()
    private val doubler = VocalDoubler()
    private val saturator = Saturator()
    private val bitcrusher = BitCrusher()
    private val wowFlutter = WowFlutter()
    private val modulation = Modulation()
    private val delay = PingPongDelay()
    private val reverb = AlgorithmicReverb()
    private val imager = StereoImager()
    private val pump = SidechainPump()
    private val gate = Gate()
    private val dust = VinylDust()
    private val limiter = BrickwallLimiter()

    private val chain: List<AudioEffect> = listOf(
        eq, resonance, multiband, transient,
        formant, doubler,
        saturator, bitcrusher, wowFlutter,
        modulation, delay, reverb,
        imager, pump, gate, dust,
        limiter
    )

    private var sampleRate = 44100
    private var lastRevision = -1
    private var outputGain = 1f

    // Five EQ bands spanning the usual corrective points.
    private val eqFrequencies = floatArrayOf(80f, 250f, 1000f, 4000f, 10000f)

    fun prepare(sampleRate: Int) {
        this.sampleRate = sampleRate
        for (i in eqFrequencies.indices) {
            eq.bands[i].freq = eqFrequencies[i]
            eq.bands[i].q = 1.0f
        }
        chain.forEach {
            it.prepare(sampleRate)
            it.reset()
        }
        lastRevision = -1
    }

    fun reset() {
        chain.forEach { it.reset() }
    }

    /**
     * Copies settings across. Called once per buffer rather than per sample, so the
     * audio thread does almost no work here.
     */
    fun sync(force: Boolean = false) {
        val revision = RackSettings.revision
        if (!force && revision == lastRevision) return
        lastRevision = revision

        eq.enabled = RackSettings.eqEnabled
        val gains = RackSettings.eqBandGains
        for (i in eqFrequencies.indices) {
            eq.bands[i].gainDb = gains.getOrElse(i) { 0f }
            eq.bands[i].dynamic = RackSettings.eqDynamic
        }
        eq.invalidate()

        resonance.enabled = RackSettings.resonanceEnabled
        resonance.amount = RackSettings.resonanceAmount

        multiband.enabled = RackSettings.multibandEnabled
        val strength = RackSettings.multibandAmount
        multiband.low.thresholdDb = -12f - strength * 18f
        multiband.low.ratio = 1.5f + strength * 4f
        multiband.low.makeupDb = strength * 3f
        multiband.mid.thresholdDb = -12f - strength * 16f
        multiband.mid.ratio = 1.5f + strength * 3f
        multiband.mid.makeupDb = strength * 2.5f
        multiband.high.thresholdDb = -12f - strength * 14f
        multiband.high.ratio = 1.5f + strength * 3f
        multiband.high.makeupDb = strength * 2f

        transient.enabled = RackSettings.transientEnabled
        transient.attackAmount = RackSettings.transientAttack
        transient.sustainAmount = RackSettings.transientSustain

        formant.enabled = RackSettings.formantEnabled
        formant.semitones = RackSettings.formantSemitones

        doubler.enabled = RackSettings.doublerEnabled
        doubler.amount = RackSettings.doublerAmount

        saturator.enabled = RackSettings.saturationEnabled
        saturator.character = RackSettings.saturationCharacter
        saturator.drive = RackSettings.saturationDrive

        bitcrusher.enabled = RackSettings.bitcrushEnabled
        bitcrusher.bits = RackSettings.bitcrushBits
        bitcrusher.downsample = RackSettings.bitcrushDownsample

        wowFlutter.enabled = RackSettings.wowFlutterEnabled
        wowFlutter.amount = RackSettings.wowFlutterAmount

        modulation.enabled = RackSettings.modulationEnabled
        modulation.type = RackSettings.modulationType
        modulation.rateHz = RackSettings.modulationRate
        modulation.depth = RackSettings.modulationDepth
        modulation.updateRate()

        delay.enabled = RackSettings.delayEnabled
        delay.timeMs = RackSettings.delayTimeMs
        delay.feedback = RackSettings.delayFeedback
        delay.mix = RackSettings.delayMix
        delay.pingPong = RackSettings.delayPingPong

        reverb.enabled = RackSettings.reverbEnabled
        reverb.roomSize = RackSettings.reverbSize
        reverb.damping = RackSettings.reverbDamping
        reverb.mix = RackSettings.reverbMix

        imager.enabled = RackSettings.imagerEnabled
        imager.width = RackSettings.stereoWidth
        imager.bassMonoHz = RackSettings.bassMonoHz
        imager.haasMs = RackSettings.haasMs
        imager.updateBassFrequency()

        pump.enabled = RackSettings.pumpEnabled
        pump.bpm = RackSettings.pumpBpm
        pump.depth = RackSettings.pumpDepth
        pump.updateRate()

        gate.enabled = RackSettings.gateEnabled
        gate.thresholdDb = RackSettings.gateThresholdDb
        gate.stutterHz = RackSettings.gateStutterHz
        gate.updateRate()

        dust.enabled = RackSettings.dustEnabled
        dust.amount = RackSettings.dustAmount

        limiter.enabled = RackSettings.limiterEnabled
        limiter.ceilingDb = RackSettings.limiterCeilingDb

        outputGain = Db.toGain(RackSettings.outputGainDb)
    }

    /** Runs one stereo frame through every enabled unit. */
    fun process(frame: AudioFrame) {
        if (outputGain != 1f) {
            frame.l *= outputGain
            frame.r *= outputGain
        }
        for (effect in chain) {
            if (effect.enabled) effect.process(frame)
        }
    }
}
