package com.retro.grooveplayer.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Unit coverage for the DSP primitives.
 *
 * These are pure functions of their inputs, so they can be verified without a device -
 * which matters because a wrong filter coefficient is silent until someone hears it.
 */
class DspTest {

    private val sampleRate = 44100

    private fun sine(freq: Float, samples: Int, rate: Int = sampleRate) =
        FloatArray(samples) { sin(2.0 * PI * freq * it / rate).toFloat() }

    private fun rms(values: FloatArray, skip: Int = 0): Float {
        var sum = 0.0
        for (i in skip until values.size) sum += values[i].toDouble() * values[i]
        return sqrt(sum / (values.size - skip)).toFloat()
    }

    // --- Biquad -----------------------------------------------------------------

    @Test
    fun `lowpass passes low frequencies and rejects high ones`() {
        val low = sine(200f, 8192)
        val high = sine(15000f, 8192)

        val filterLow = Biquad().apply { setLowPass(1000f, 0.707f, sampleRate) }
        val filterHigh = Biquad().apply { setLowPass(1000f, 0.707f, sampleRate) }

        val outLow = FloatArray(low.size) { filterLow.processLeft(low[it]) }
        val outHigh = FloatArray(high.size) { filterHigh.processLeft(high[it]) }

        // Skip the settling transient before measuring.
        assertTrue("low band should survive", rms(outLow, 1024) > 0.6f)
        assertTrue("high band should be attenuated", rms(outHigh, 1024) < 0.05f)
    }

    @Test
    fun `highpass rejects low frequencies`() {
        val low = sine(50f, 8192)
        val filter = Biquad().apply { setHighPass(1000f, 0.707f, sampleRate) }
        val out = FloatArray(low.size) { filter.processLeft(low[it]) }
        assertTrue(rms(out, 1024) < 0.05f)
    }

    @Test
    fun `peaking filter boosts at its centre frequency`() {
        val tone = sine(1000f, 8192)
        val flat = Biquad().apply { setPeaking(1000f, 1f, 0f, sampleRate) }
        val boosted = Biquad().apply { setPeaking(1000f, 1f, 12f, sampleRate) }

        val flatOut = FloatArray(tone.size) { flat.processLeft(tone[it]) }
        val boostOut = FloatArray(tone.size) { boosted.processLeft(tone[it]) }

        val ratio = rms(boostOut, 2048) / rms(flatOut, 2048)
        // +12 dB is a linear gain of about 4.
        assertEquals(4.0f, ratio, 0.4f)
    }

    @Test
    fun `unity peaking filter leaves the signal untouched`() {
        val tone = sine(440f, 4096)
        val filter = Biquad().apply { setPeaking(1000f, 1f, 0f, sampleRate) }
        val out = FloatArray(tone.size) { filter.processLeft(tone[it]) }
        for (i in 2048 until tone.size) {
            assertEquals(tone[i], out[i], 0.001f)
        }
    }

    // --- Gain computer ----------------------------------------------------------

    @Test
    fun `compressor leaves signals below threshold alone`() {
        val gain = GainComputer.gainDb(inputDb = -30f, thresholdDb = -20f, ratio = 4f, kneeDb = 0f)
        assertEquals(0f, gain, 0.0001f)
    }

    @Test
    fun `compressor applies the expected ratio above threshold`() {
        // 10 dB over threshold at 4:1 should come out 2.5 dB over, so -7.5 dB of gain.
        val gain = GainComputer.gainDb(inputDb = -10f, thresholdDb = -20f, ratio = 4f, kneeDb = 0f)
        assertEquals(-7.5f, gain, 0.01f)
    }

    @Test
    fun `infinite ratio behaves as a limiter`() {
        val gain = GainComputer.gainDb(inputDb = 0f, thresholdDb = -6f, ratio = 1000f, kneeDb = 0f)
        assertEquals(-6f, gain, 0.05f)
    }

    // --- Decibel helpers --------------------------------------------------------

    @Test
    fun `decibel conversions round trip`() {
        for (db in listOf(-24f, -12f, -6f, 0f, 6f)) {
            assertEquals(db, Db.fromGain(Db.toGain(db)), 0.01f)
        }
        assertEquals(1f, Db.toGain(0f), 0.0001f)
        assertEquals(0.5f, Db.toGain(-6.0206f), 0.001f)
    }

    // --- Limiter ----------------------------------------------------------------

    @Test
    fun `brickwall limiter keeps output under the ceiling`() {
        val limiter = BrickwallLimiter().apply {
            enabled = true
            ceilingDb = -1f
            prepare(sampleRate)
        }
        val ceiling = Db.toGain(-1f)
        val frame = AudioFrame()
        var worst = 0f

        // Deliberately slam it with a signal well over full scale.
        val loud = sine(220f, 8192)
        for (sample in loud) {
            frame.set(sample * 4f, sample * 4f)
            limiter.process(frame)
            worst = maxOf(worst, abs(frame.l), abs(frame.r))
        }
        assertTrue("peaked at $worst, ceiling $ceiling", worst <= ceiling + 0.001f)
    }

    // --- Effect contract --------------------------------------------------------

    @Test
    fun `disabled rack is bit transparent`() {
        RackSettings.resetAll()
        RackSettings.limiterEnabled = false
        RackSettings.dryWet = 1f

        val rack = EffectRack()
        rack.prepare(sampleRate)
        rack.sync(force = true)

        val tone = sine(440f, 2048)
        val frame = AudioFrame()
        for (sample in tone) {
            frame.set(sample, sample)
            rack.process(frame)
            assertEquals(sample, frame.l, 0.0001f)
            assertEquals(sample, frame.r, 0.0001f)
        }
    }

    @Test
    fun `master bypass returns the input untouched`() {
        RackSettings.resetAll()
        RackSettings.saturationEnabled = true
        RackSettings.saturationDrive = 8f
        RackSettings.masterBypass = true

        val rack = EffectRack()
        rack.prepare(sampleRate)
        rack.sync(force = true)

        val frame = AudioFrame()
        frame.set(0.5f, -0.5f)
        rack.process(frame)
        assertEquals(0.5f, frame.l, 0.0001f)
        assertEquals(-0.5f, frame.r, 0.0001f)

        RackSettings.resetAll()
    }

    @Test
    fun `stereo imager at zero width collapses to mono`() {
        val imager = StereoImager().apply {
            enabled = true
            width = 0f
            bassMonoHz = 0f
            haasMs = 0f
            prepare(sampleRate)
        }
        val frame = AudioFrame()
        frame.set(1f, -1f)
        imager.process(frame)
        assertEquals(frame.l, frame.r, 0.0001f)
    }

    // --- Settings persistence ---------------------------------------------------

    @Test
    fun `rack settings survive a snapshot round trip`() {
        RackSettings.resetAll()
        RackSettings.reverbEnabled = true
        RackSettings.reverbMix = 0.42f
        RackSettings.saturationDrive = 6.5f
        RackSettings.saturationCharacter = Saturator.Character.TUBE
        RackSettings.eqBandGains = listOf(1f, -2f, 3f, -4f, 5f)

        val snapshot = RackSettings.toMap()
        RackSettings.resetAll()
        RackSettings.fromMap(snapshot)

        assertTrue(RackSettings.reverbEnabled)
        assertEquals(0.42f, RackSettings.reverbMix, 0.0001f)
        assertEquals(6.5f, RackSettings.saturationDrive, 0.0001f)
        assertEquals(Saturator.Character.TUBE, RackSettings.saturationCharacter)
        assertEquals(listOf(1f, -2f, 3f, -4f, 5f), RackSettings.eqBandGains)

        RackSettings.resetAll()
    }

    // --- FFT --------------------------------------------------------------------

    @Test
    fun `fft round trips back to the original signal`() {
        val n = 256
        val original = FloatArray(n) { sin(2.0 * PI * 5 * it / n).toFloat() }
        val re = original.copyOf()
        val im = FloatArray(n)

        Fft.transform(re, im)
        Fft.transform(re, im, inverse = true)

        for (i in 0 until n) {
            assertEquals(original[i], re[i], 0.001f)
        }
    }

    @Test
    fun `fft finds the bin of a pure tone`() {
        val n = 512
        val bin = 16
        val re = FloatArray(n) { sin(2.0 * PI * bin * it / n).toFloat() }
        val im = FloatArray(n)
        Fft.transform(re, im)

        var strongest = 0
        var best = 0f
        for (k in 1 until n / 2) {
            val magnitude = kotlin.math.hypot(re[k], im[k])
            if (magnitude > best) {
                best = magnitude
                strongest = k
            }
        }
        assertEquals(bin, strongest)
    }
}
