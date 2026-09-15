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

    // Master
    var masterBypass by mutableStateOf(false)
    var dryWet by mutableStateOf(1f)
    var limiterEnabled by mutableStateOf(true)
    var limiterCeilingDb by mutableStateOf(-0.3f)
    var outputGainDb by mutableStateOf(0f)

    // Spectral
    var eqEnabled by mutableStateOf(false)
    var eqBandGains by mutableStateOf(listOf(0f, 0f, 0f, 0f, 0f))
    var eqDynamic by mutableStateOf(false)
    var eqStereoMode by mutableStateOf(StereoMode.STEREO)

    var resonanceEnabled by mutableStateOf(false)
    var resonanceAmount by mutableStateOf(0.5f)

    var deEsserEnabled by mutableStateOf(false)
    var deEsserFrequency by mutableStateOf(6500f)
    var deEsserAmount by mutableStateOf(0.5f)

    // Dynamics
    var compressorEnabled by mutableStateOf(false)
    var compressorThresholdDb by mutableStateOf(-18f)
    var compressorRatio by mutableStateOf(3f)
    var compressorMakeupDb by mutableStateOf(0f)

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
    var saturationStereoMode by mutableStateOf(StereoMode.STEREO)

    var exciterEnabled by mutableStateOf(false)
    var exciterFrequency by mutableStateOf(4000f)
    var exciterAmount by mutableStateOf(0.4f)

    var subBassEnabled by mutableStateOf(false)
    var subBassAmount by mutableStateOf(0.4f)

    var ringModEnabled by mutableStateOf(false)
    var ringModFrequency by mutableStateOf(200f)
    var ringModMix by mutableStateOf(0.5f)

    var bitcrushEnabled by mutableStateOf(false)
    var bitcrushBits by mutableStateOf(12f)
    var bitcrushDownsample by mutableStateOf(1f)

    // Filter / modulation
    var autoWahEnabled by mutableStateOf(false)
    var autoWahSensitivity by mutableStateOf(0.5f)
    var autoWahResonance by mutableStateOf(4f)

    var modulationEnabled by mutableStateOf(false)
    var modulationType by mutableStateOf(Modulation.Type.CHORUS)
    var modulationRate by mutableStateOf(0.5f)
    var modulationDepth by mutableStateOf(0.5f)

    var autoPanEnabled by mutableStateOf(false)
    var autoPanRate by mutableStateOf(0.4f)
    var autoPanDepth by mutableStateOf(0.8f)

    var wowFlutterEnabled by mutableStateOf(false)
    var wowFlutterAmount by mutableStateOf(0.3f)

    // Time / space
    var delayEnabled by mutableStateOf(false)
    var delayTimeMs by mutableStateOf(375f)
    var delayFeedback by mutableStateOf(0.4f)
    var delayMix by mutableStateOf(0.3f)
    var delayPingPong by mutableStateOf(true)

    var reverbEnabled by mutableStateOf(false)
    var reverbSize by mutableStateOf(0.6f)
    var reverbDamping by mutableStateOf(0.4f)
    var reverbMix by mutableStateOf(0.3f)

    var convolutionEnabled by mutableStateOf(false)
    var convolutionSpace by mutableStateOf(ConvolutionReverb.Space.HALL)
    var convolutionMix by mutableStateOf(0.3f)

    var imagerEnabled by mutableStateOf(false)
    var stereoWidth by mutableStateOf(1f)
    var bassMonoHz by mutableStateOf(120f)
    var haasMs by mutableStateOf(0f)

    // Pitch / voice
    var pitchShiftEnabled by mutableStateOf(false)
    var pitchShiftSemitones by mutableStateOf(0f)

    var scaleSnapEnabled by mutableStateOf(false)
    var scaleSnapStrength by mutableStateOf(0.8f)
    var scaleSnapRoot by mutableStateOf(0)
    var scaleSnapScale by mutableStateOf("Major")

    var formantEnabled by mutableStateOf(false)
    var formantSemitones by mutableStateOf(0f)

    var doublerEnabled by mutableStateOf(false)
    var doublerAmount by mutableStateOf(0.5f)

    // Transport-style
    var tapeStopEnabled by mutableStateOf(false)
    var tapeStopDurationMs by mutableStateOf(1200f)
    var tapeStopEngaged by mutableStateOf(false)

    var reverseEnabled by mutableStateOf(false)
    var reverseSliceMs by mutableStateOf(500f)

    // Texture
    var dustEnabled by mutableStateOf(false)
    var dustAmount by mutableStateOf(0.3f)

    /** Bumped on every change so the audio thread knows to re-read cheaply. */
    var revision by mutableStateOf(0)
        private set

    /** Set by [EffectRack] each buffer so the UI can draw meters. */
    var meterOutput by mutableStateOf(0f)
    var meterLimiter by mutableStateOf(0f)
    var meterCompressor by mutableStateOf(0f)
    var meterDeEsser by mutableStateOf(0f)
    var meterMultiband by mutableStateOf(Triple(0f, 0f, 0f))

    fun touch() {
        revision++
        onChanged?.invoke()
    }

    /** Set by PlaybackManager so every change is persisted. */
    var onChanged: (() -> Unit)? = null

    val anyEnabled: Boolean
        get() = eqEnabled || resonanceEnabled || deEsserEnabled || compressorEnabled ||
            multibandEnabled || transientEnabled || pumpEnabled || gateEnabled ||
            saturationEnabled || exciterEnabled || subBassEnabled || ringModEnabled ||
            bitcrushEnabled || autoWahEnabled || modulationEnabled || autoPanEnabled ||
            wowFlutterEnabled || delayEnabled || reverbEnabled || convolutionEnabled ||
            imagerEnabled || pitchShiftEnabled || scaleSnapEnabled || formantEnabled ||
            doublerEnabled || tapeStopEnabled || reverseEnabled || dustEnabled

    fun scaleSteps(): IntArray = when (scaleSnapScale) {
        "Minor" -> ScaleSnap.MINOR
        "Pentatonic" -> ScaleSnap.PENTATONIC
        "Chromatic" -> ScaleSnap.CHROMATIC
        else -> ScaleSnap.MAJOR
    }

    fun resetAll() {
        masterBypass = false; dryWet = 1f
        eqEnabled = false; eqBandGains = listOf(0f, 0f, 0f, 0f, 0f); eqDynamic = false
        eqStereoMode = StereoMode.STEREO
        resonanceEnabled = false
        deEsserEnabled = false
        compressorEnabled = false
        multibandEnabled = false
        transientEnabled = false; transientAttack = 0f; transientSustain = 0f
        pumpEnabled = false
        gateEnabled = false; gateStutterHz = 0f
        saturationEnabled = false; saturationStereoMode = StereoMode.STEREO
        exciterEnabled = false
        subBassEnabled = false
        ringModEnabled = false
        bitcrushEnabled = false
        autoWahEnabled = false
        modulationEnabled = false
        autoPanEnabled = false
        wowFlutterEnabled = false
        delayEnabled = false
        reverbEnabled = false
        convolutionEnabled = false
        imagerEnabled = false; stereoWidth = 1f; haasMs = 0f
        pitchShiftEnabled = false; pitchShiftSemitones = 0f
        scaleSnapEnabled = false
        formantEnabled = false; formantSemitones = 0f
        doublerEnabled = false
        tapeStopEnabled = false; tapeStopEngaged = false
        reverseEnabled = false
        dustEnabled = false
        outputGainDb = 0f
        touch()
    }

    /** Flat snapshot for persistence and user presets. */
    fun toMap(): Map<String, String> = mapOf(
        "dryWet" to dryWet.toString(),
        "limiterEnabled" to limiterEnabled.toString(),
        "limiterCeilingDb" to limiterCeilingDb.toString(),
        "outputGainDb" to outputGainDb.toString(),
        "eqEnabled" to eqEnabled.toString(),
        "eqBandGains" to eqBandGains.joinToString(","),
        "eqDynamic" to eqDynamic.toString(),
        "eqStereoMode" to eqStereoMode.name,
        "resonanceEnabled" to resonanceEnabled.toString(),
        "resonanceAmount" to resonanceAmount.toString(),
        "deEsserEnabled" to deEsserEnabled.toString(),
        "deEsserFrequency" to deEsserFrequency.toString(),
        "deEsserAmount" to deEsserAmount.toString(),
        "compressorEnabled" to compressorEnabled.toString(),
        "compressorThresholdDb" to compressorThresholdDb.toString(),
        "compressorRatio" to compressorRatio.toString(),
        "compressorMakeupDb" to compressorMakeupDb.toString(),
        "multibandEnabled" to multibandEnabled.toString(),
        "multibandAmount" to multibandAmount.toString(),
        "transientEnabled" to transientEnabled.toString(),
        "transientAttack" to transientAttack.toString(),
        "transientSustain" to transientSustain.toString(),
        "pumpEnabled" to pumpEnabled.toString(),
        "pumpBpm" to pumpBpm.toString(),
        "pumpDepth" to pumpDepth.toString(),
        "gateEnabled" to gateEnabled.toString(),
        "gateThresholdDb" to gateThresholdDb.toString(),
        "gateStutterHz" to gateStutterHz.toString(),
        "saturationEnabled" to saturationEnabled.toString(),
        "saturationCharacter" to saturationCharacter.name,
        "saturationDrive" to saturationDrive.toString(),
        "saturationStereoMode" to saturationStereoMode.name,
        "exciterEnabled" to exciterEnabled.toString(),
        "exciterFrequency" to exciterFrequency.toString(),
        "exciterAmount" to exciterAmount.toString(),
        "subBassEnabled" to subBassEnabled.toString(),
        "subBassAmount" to subBassAmount.toString(),
        "ringModEnabled" to ringModEnabled.toString(),
        "ringModFrequency" to ringModFrequency.toString(),
        "ringModMix" to ringModMix.toString(),
        "bitcrushEnabled" to bitcrushEnabled.toString(),
        "bitcrushBits" to bitcrushBits.toString(),
        "bitcrushDownsample" to bitcrushDownsample.toString(),
        "autoWahEnabled" to autoWahEnabled.toString(),
        "autoWahSensitivity" to autoWahSensitivity.toString(),
        "autoWahResonance" to autoWahResonance.toString(),
        "modulationEnabled" to modulationEnabled.toString(),
        "modulationType" to modulationType.name,
        "modulationRate" to modulationRate.toString(),
        "modulationDepth" to modulationDepth.toString(),
        "autoPanEnabled" to autoPanEnabled.toString(),
        "autoPanRate" to autoPanRate.toString(),
        "autoPanDepth" to autoPanDepth.toString(),
        "wowFlutterEnabled" to wowFlutterEnabled.toString(),
        "wowFlutterAmount" to wowFlutterAmount.toString(),
        "delayEnabled" to delayEnabled.toString(),
        "delayTimeMs" to delayTimeMs.toString(),
        "delayFeedback" to delayFeedback.toString(),
        "delayMix" to delayMix.toString(),
        "delayPingPong" to delayPingPong.toString(),
        "reverbEnabled" to reverbEnabled.toString(),
        "reverbSize" to reverbSize.toString(),
        "reverbDamping" to reverbDamping.toString(),
        "reverbMix" to reverbMix.toString(),
        "convolutionEnabled" to convolutionEnabled.toString(),
        "convolutionSpace" to convolutionSpace.name,
        "convolutionMix" to convolutionMix.toString(),
        "imagerEnabled" to imagerEnabled.toString(),
        "stereoWidth" to stereoWidth.toString(),
        "bassMonoHz" to bassMonoHz.toString(),
        "haasMs" to haasMs.toString(),
        "pitchShiftEnabled" to pitchShiftEnabled.toString(),
        "pitchShiftSemitones" to pitchShiftSemitones.toString(),
        "scaleSnapEnabled" to scaleSnapEnabled.toString(),
        "scaleSnapStrength" to scaleSnapStrength.toString(),
        "scaleSnapRoot" to scaleSnapRoot.toString(),
        "scaleSnapScale" to scaleSnapScale,
        "formantEnabled" to formantEnabled.toString(),
        "formantSemitones" to formantSemitones.toString(),
        "doublerEnabled" to doublerEnabled.toString(),
        "doublerAmount" to doublerAmount.toString(),
        "tapeStopEnabled" to tapeStopEnabled.toString(),
        "tapeStopDurationMs" to tapeStopDurationMs.toString(),
        "reverseEnabled" to reverseEnabled.toString(),
        "reverseSliceMs" to reverseSliceMs.toString(),
        "dustEnabled" to dustEnabled.toString(),
        "dustAmount" to dustAmount.toString()
    )

    fun fromMap(map: Map<String, String>) {
        fun f(key: String, fallback: Float) = map[key]?.toFloatOrNull() ?: fallback
        fun b(key: String, fallback: Boolean) = map[key]?.toBooleanStrictOrNull() ?: fallback
        fun i(key: String, fallback: Int) = map[key]?.toIntOrNull() ?: fallback

        dryWet = f("dryWet", 1f)
        limiterEnabled = b("limiterEnabled", true)
        limiterCeilingDb = f("limiterCeilingDb", -0.3f)
        outputGainDb = f("outputGainDb", 0f)

        eqEnabled = b("eqEnabled", false)
        map["eqBandGains"]?.split(",")?.mapNotNull { it.toFloatOrNull() }?.let {
            if (it.size == 5) eqBandGains = it
        }
        eqDynamic = b("eqDynamic", false)
        eqStereoMode = runCatching { StereoMode.valueOf(map["eqStereoMode"] ?: "") }
            .getOrDefault(StereoMode.STEREO)

        resonanceEnabled = b("resonanceEnabled", false)
        resonanceAmount = f("resonanceAmount", 0.5f)
        deEsserEnabled = b("deEsserEnabled", false)
        deEsserFrequency = f("deEsserFrequency", 6500f)
        deEsserAmount = f("deEsserAmount", 0.5f)
        compressorEnabled = b("compressorEnabled", false)
        compressorThresholdDb = f("compressorThresholdDb", -18f)
        compressorRatio = f("compressorRatio", 3f)
        compressorMakeupDb = f("compressorMakeupDb", 0f)
        multibandEnabled = b("multibandEnabled", false)
        multibandAmount = f("multibandAmount", 0.5f)
        transientEnabled = b("transientEnabled", false)
        transientAttack = f("transientAttack", 0f)
        transientSustain = f("transientSustain", 0f)
        pumpEnabled = b("pumpEnabled", false)
        pumpBpm = f("pumpBpm", 120f)
        pumpDepth = f("pumpDepth", 0.6f)
        gateEnabled = b("gateEnabled", false)
        gateThresholdDb = f("gateThresholdDb", -45f)
        gateStutterHz = f("gateStutterHz", 0f)

        saturationEnabled = b("saturationEnabled", false)
        saturationCharacter = runCatching { Saturator.Character.valueOf(map["saturationCharacter"] ?: "") }
            .getOrDefault(Saturator.Character.TAPE)
        saturationDrive = f("saturationDrive", 2f)
        saturationStereoMode = runCatching { StereoMode.valueOf(map["saturationStereoMode"] ?: "") }
            .getOrDefault(StereoMode.STEREO)
        exciterEnabled = b("exciterEnabled", false)
        exciterFrequency = f("exciterFrequency", 4000f)
        exciterAmount = f("exciterAmount", 0.4f)
        subBassEnabled = b("subBassEnabled", false)
        subBassAmount = f("subBassAmount", 0.4f)
        ringModEnabled = b("ringModEnabled", false)
        ringModFrequency = f("ringModFrequency", 200f)
        ringModMix = f("ringModMix", 0.5f)
        bitcrushEnabled = b("bitcrushEnabled", false)
        bitcrushBits = f("bitcrushBits", 12f)
        bitcrushDownsample = f("bitcrushDownsample", 1f)

        autoWahEnabled = b("autoWahEnabled", false)
        autoWahSensitivity = f("autoWahSensitivity", 0.5f)
        autoWahResonance = f("autoWahResonance", 4f)
        modulationEnabled = b("modulationEnabled", false)
        modulationType = runCatching { Modulation.Type.valueOf(map["modulationType"] ?: "") }
            .getOrDefault(Modulation.Type.CHORUS)
        modulationRate = f("modulationRate", 0.5f)
        modulationDepth = f("modulationDepth", 0.5f)
        autoPanEnabled = b("autoPanEnabled", false)
        autoPanRate = f("autoPanRate", 0.4f)
        autoPanDepth = f("autoPanDepth", 0.8f)
        wowFlutterEnabled = b("wowFlutterEnabled", false)
        wowFlutterAmount = f("wowFlutterAmount", 0.3f)

        delayEnabled = b("delayEnabled", false)
        delayTimeMs = f("delayTimeMs", 375f)
        delayFeedback = f("delayFeedback", 0.4f)
        delayMix = f("delayMix", 0.3f)
        delayPingPong = b("delayPingPong", true)
        reverbEnabled = b("reverbEnabled", false)
        reverbSize = f("reverbSize", 0.6f)
        reverbDamping = f("reverbDamping", 0.4f)
        reverbMix = f("reverbMix", 0.3f)
        convolutionEnabled = b("convolutionEnabled", false)
        convolutionSpace = runCatching { ConvolutionReverb.Space.valueOf(map["convolutionSpace"] ?: "") }
            .getOrDefault(ConvolutionReverb.Space.HALL)
        convolutionMix = f("convolutionMix", 0.3f)
        imagerEnabled = b("imagerEnabled", false)
        stereoWidth = f("stereoWidth", 1f)
        bassMonoHz = f("bassMonoHz", 120f)
        haasMs = f("haasMs", 0f)

        pitchShiftEnabled = b("pitchShiftEnabled", false)
        pitchShiftSemitones = f("pitchShiftSemitones", 0f)
        scaleSnapEnabled = b("scaleSnapEnabled", false)
        scaleSnapStrength = f("scaleSnapStrength", 0.8f)
        scaleSnapRoot = i("scaleSnapRoot", 0)
        scaleSnapScale = map["scaleSnapScale"] ?: "Major"
        formantEnabled = b("formantEnabled", false)
        formantSemitones = f("formantSemitones", 0f)
        doublerEnabled = b("doublerEnabled", false)
        doublerAmount = f("doublerAmount", 0.5f)

        tapeStopEnabled = b("tapeStopEnabled", false)
        tapeStopDurationMs = f("tapeStopDurationMs", 1200f)
        tapeStopEngaged = false
        reverseEnabled = b("reverseEnabled", false)
        reverseSliceMs = f("reverseSliceMs", 500f)
        dustEnabled = b("dustEnabled", false)
        dustAmount = f("dustAmount", 0.3f)

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
    private val deEsser = DeEsser()
    private val compressor = Compressor()
    private val multiband = MultibandCompressor()
    private val transient = TransientShaper()
    private val pitchShift = PitchShift()
    private val scaleSnap = ScaleSnap()
    private val formant = FormantShifter()
    private val doubler = VocalDoubler()
    private val saturator = Saturator()
    private val exciter = Exciter()
    private val subBass = SubBass()
    private val ringMod = RingModulator()
    private val bitcrusher = BitCrusher()
    private val autoWah = AutoWah()
    private val wowFlutter = WowFlutter()
    private val modulation = Modulation()
    private val delay = PingPongDelay()
    private val reverb = AlgorithmicReverb()
    private val convolution = ConvolutionReverb()
    private val imager = StereoImager()
    private val autoPan = AutoPan()
    private val pump = SidechainPump()
    private val gate = Gate()
    private val reverse = Reverse()
    private val tapeStop = TapeStop()
    private val dust = VinylDust()
    private val limiter = BrickwallLimiter()

    private val chain: List<AudioEffect> = listOf(
        // Corrective
        eq, resonance, deEsser, compressor, multiband, transient,
        // Pitch and voice
        pitchShift, scaleSnap, formant, doubler,
        // Character
        saturator, exciter, subBass, ringMod, bitcrusher, autoWah, wowFlutter,
        // Movement and space
        modulation, delay, reverb, convolution, imager, autoPan,
        // Rhythmic and transport
        pump, gate, reverse, tapeStop,
        // Texture, then master
        dust, limiter
    )

    private var sampleRate = 44100
    private var lastRevision = -1
    private var outputGain = 1f
    private var dryWet = 1f
    private var bypass = false

    private val dryFrame = AudioFrame()
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

    fun reset() = chain.forEach { it.reset() }

    /** Copies meter values out for the UI. Called from the UI, not the audio thread. */
    fun publishMeters() {
        RackSettings.meterOutput = limiter.outputLevel.peak
        RackSettings.meterLimiter = limiter.reduction.peak
        RackSettings.meterCompressor = compressor.reduction.peak
        RackSettings.meterDeEsser = deEsser.reduction.peak
        RackSettings.meterMultiband = Triple(
            multiband.reductionLow.peak,
            multiband.reductionMid.peak,
            multiband.reductionHigh.peak
        )
    }

    /**
     * Copies settings across. Called once per buffer rather than per sample, so the
     * audio thread does almost no work here.
     */
    fun sync(force: Boolean = false) {
        val revision = RackSettings.revision
        if (!force && revision == lastRevision) return
        lastRevision = revision

        bypass = RackSettings.masterBypass
        dryWet = RackSettings.dryWet

        eq.enabled = RackSettings.eqEnabled
        val gains = RackSettings.eqBandGains
        for (i in eqFrequencies.indices) {
            eq.bands[i].gainDb = gains.getOrElse(i) { 0f }
            eq.bands[i].dynamic = RackSettings.eqDynamic
        }
        eq.stereoMode = RackSettings.eqStereoMode
        eq.invalidate()

        resonance.enabled = RackSettings.resonanceEnabled
        resonance.amount = RackSettings.resonanceAmount

        deEsser.enabled = RackSettings.deEsserEnabled
        deEsser.frequency = RackSettings.deEsserFrequency
        deEsser.amount = RackSettings.deEsserAmount
        deEsser.updateFrequency()

        compressor.enabled = RackSettings.compressorEnabled
        compressor.thresholdDb = RackSettings.compressorThresholdDb
        compressor.ratio = RackSettings.compressorRatio
        compressor.makeupDb = RackSettings.compressorMakeupDb

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

        pitchShift.enabled = RackSettings.pitchShiftEnabled
        pitchShift.semitones = RackSettings.pitchShiftSemitones

        scaleSnap.enabled = RackSettings.scaleSnapEnabled
        scaleSnap.strength = RackSettings.scaleSnapStrength
        scaleSnap.root = RackSettings.scaleSnapRoot
        scaleSnap.scale = RackSettings.scaleSteps()

        formant.enabled = RackSettings.formantEnabled
        formant.semitones = RackSettings.formantSemitones

        doubler.enabled = RackSettings.doublerEnabled
        doubler.amount = RackSettings.doublerAmount

        saturator.enabled = RackSettings.saturationEnabled
        saturator.character = RackSettings.saturationCharacter
        saturator.drive = RackSettings.saturationDrive
        saturator.stereoMode = RackSettings.saturationStereoMode

        exciter.enabled = RackSettings.exciterEnabled
        exciter.frequency = RackSettings.exciterFrequency
        exciter.amount = RackSettings.exciterAmount
        exciter.updateFrequency()

        subBass.enabled = RackSettings.subBassEnabled
        subBass.amount = RackSettings.subBassAmount

        ringMod.enabled = RackSettings.ringModEnabled
        ringMod.frequency = RackSettings.ringModFrequency
        ringMod.mix = RackSettings.ringModMix
        ringMod.updateFrequency()

        bitcrusher.enabled = RackSettings.bitcrushEnabled
        bitcrusher.bits = RackSettings.bitcrushBits
        bitcrusher.downsample = RackSettings.bitcrushDownsample

        autoWah.enabled = RackSettings.autoWahEnabled
        autoWah.sensitivity = RackSettings.autoWahSensitivity
        autoWah.resonance = RackSettings.autoWahResonance

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

        convolution.enabled = RackSettings.convolutionEnabled
        convolution.space = RackSettings.convolutionSpace
        convolution.mix = RackSettings.convolutionMix

        imager.enabled = RackSettings.imagerEnabled
        imager.width = RackSettings.stereoWidth
        imager.bassMonoHz = RackSettings.bassMonoHz
        imager.haasMs = RackSettings.haasMs
        imager.updateBassFrequency()

        autoPan.enabled = RackSettings.autoPanEnabled
        autoPan.rateHz = RackSettings.autoPanRate
        autoPan.depth = RackSettings.autoPanDepth
        autoPan.updateRate()

        pump.enabled = RackSettings.pumpEnabled
        pump.bpm = RackSettings.pumpBpm
        pump.depth = RackSettings.pumpDepth
        pump.updateRate()

        gate.enabled = RackSettings.gateEnabled
        gate.thresholdDb = RackSettings.gateThresholdDb
        gate.stutterHz = RackSettings.gateStutterHz
        gate.updateRate()

        reverse.enabled = RackSettings.reverseEnabled
        reverse.sliceMs = RackSettings.reverseSliceMs

        tapeStop.enabled = RackSettings.tapeStopEnabled
        tapeStop.durationMs = RackSettings.tapeStopDurationMs
        tapeStop.engaged = RackSettings.tapeStopEngaged

        dust.enabled = RackSettings.dustEnabled
        dust.amount = RackSettings.dustAmount

        limiter.enabled = RackSettings.limiterEnabled
        limiter.ceilingDb = RackSettings.limiterCeilingDb

        outputGain = Db.toGain(RackSettings.outputGainDb)
    }

    /** Runs one stereo frame through every enabled unit. */
    fun process(frame: AudioFrame) {
        if (bypass) return

        // Keep the untouched signal so the global dry/wet can blend it back in.
        val blending = dryWet < 0.999f
        if (blending) {
            dryFrame.l = frame.l
            dryFrame.r = frame.r
        }

        if (outputGain != 1f) {
            frame.l *= outputGain
            frame.r *= outputGain
        }
        for (effect in chain) {
            if (effect.enabled) effect.process(frame)
        }

        if (blending) {
            frame.l = dryFrame.l * (1f - dryWet) + frame.l * dryWet
            frame.r = dryFrame.r * (1f - dryWet) + frame.r * dryWet
        }
    }
}
