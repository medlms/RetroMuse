package com.retro.grooveplayer.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Reusable stereo frame.
 *
 * The chain runs per sample, so returning a Pair would allocate tens of thousands of
 * objects a second and churn the GC on the audio thread. Everything mutates in place.
 */
class AudioFrame {
    @JvmField var l = 0f
    @JvmField var r = 0f

    fun set(left: Float, right: Float) {
        l = left
        r = right
    }
}

object Db {
    fun toGain(db: Float): Float = 10f.pow(db / 20f)
    fun fromGain(gain: Float): Float = if (gain > 1e-9f) 20f * log10(gain) else -180f
}

/**
 * Transposed direct-form II biquad, stereo. Coefficients follow the RBJ audio
 * cookbook, the standard formulas behind every parametric EQ.
 */
class Biquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f

    private var z1L = 0f
    private var z2L = 0f
    private var z1R = 0f
    private var z2R = 0f

    fun reset() {
        z1L = 0f; z2L = 0f; z1R = 0f; z2R = 0f
    }

    private fun normalise(b0n: Float, b1n: Float, b2n: Float, a0n: Float, a1n: Float, a2n: Float) {
        val inv = 1f / a0n
        b0 = b0n * inv; b1 = b1n * inv; b2 = b2n * inv
        a1 = a1n * inv; a2 = a2n * inv
    }

    private fun omega(freq: Float, sampleRate: Int): Float =
        2f * PI.toFloat() * (freq / sampleRate).coerceIn(1e-5f, 0.49f)

    fun setPeaking(freq: Float, q: Float, gainDb: Float, sampleRate: Int) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = omega(freq, sampleRate)
        val cosw = cos(w0)
        val alpha = sin(w0) / (2f * q.coerceAtLeast(0.05f))
        normalise(
            1f + alpha * a, -2f * cosw, 1f - alpha * a,
            1f + alpha / a, -2f * cosw, 1f - alpha / a
        )
    }

    fun setLowShelf(freq: Float, q: Float, gainDb: Float, sampleRate: Int) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = omega(freq, sampleRate)
        val cosw = cos(w0)
        val alpha = sin(w0) / (2f * q.coerceAtLeast(0.05f))
        val k = 2f * sqrt(a) * alpha
        normalise(
            a * ((a + 1f) - (a - 1f) * cosw + k),
            2f * a * ((a - 1f) - (a + 1f) * cosw),
            a * ((a + 1f) - (a - 1f) * cosw - k),
            (a + 1f) + (a - 1f) * cosw + k,
            -2f * ((a - 1f) + (a + 1f) * cosw),
            (a + 1f) + (a - 1f) * cosw - k
        )
    }

    fun setHighShelf(freq: Float, q: Float, gainDb: Float, sampleRate: Int) {
        val a = 10f.pow(gainDb / 40f)
        val w0 = omega(freq, sampleRate)
        val cosw = cos(w0)
        val alpha = sin(w0) / (2f * q.coerceAtLeast(0.05f))
        val k = 2f * sqrt(a) * alpha
        normalise(
            a * ((a + 1f) + (a - 1f) * cosw + k),
            -2f * a * ((a - 1f) + (a + 1f) * cosw),
            a * ((a + 1f) + (a - 1f) * cosw - k),
            (a + 1f) - (a - 1f) * cosw + k,
            2f * ((a - 1f) - (a + 1f) * cosw),
            (a + 1f) - (a - 1f) * cosw - k
        )
    }

    fun setHighPass(freq: Float, q: Float, sampleRate: Int) {
        val w0 = omega(freq, sampleRate)
        val cosw = cos(w0)
        val alpha = sin(w0) / (2f * q.coerceAtLeast(0.05f))
        normalise(
            (1f + cosw) / 2f, -(1f + cosw), (1f + cosw) / 2f,
            1f + alpha, -2f * cosw, 1f - alpha
        )
    }

    fun setLowPass(freq: Float, q: Float, sampleRate: Int) {
        val w0 = omega(freq, sampleRate)
        val cosw = cos(w0)
        val alpha = sin(w0) / (2f * q.coerceAtLeast(0.05f))
        normalise(
            (1f - cosw) / 2f, 1f - cosw, (1f - cosw) / 2f,
            1f + alpha, -2f * cosw, 1f - alpha
        )
    }

    fun setBandPass(freq: Float, q: Float, sampleRate: Int) {
        val w0 = omega(freq, sampleRate)
        val cosw = cos(w0)
        val alpha = sin(w0) / (2f * q.coerceAtLeast(0.05f))
        normalise(alpha, 0f, -alpha, 1f + alpha, -2f * cosw, 1f - alpha)
    }

    fun setAllPass(freq: Float, q: Float, sampleRate: Int) {
        val w0 = omega(freq, sampleRate)
        val cosw = cos(w0)
        val alpha = sin(w0) / (2f * q.coerceAtLeast(0.05f))
        normalise(
            1f - alpha, -2f * cosw, 1f + alpha,
            1f + alpha, -2f * cosw, 1f - alpha
        )
    }

    fun processLeft(x: Float): Float {
        val y = b0 * x + z1L
        z1L = b1 * x - a1 * y + z2L
        z2L = b2 * x - a2 * y
        return y
    }

    fun processRight(x: Float): Float {
        val y = b0 * x + z1R
        z1R = b1 * x - a1 * y + z2R
        z2R = b2 * x - a2 * y
        return y
    }
}

/**
 * Fourth-order Linkwitz-Riley crossover: two cascaded Butterworth sections, which sum
 * back to a flat response. This is how multiband processors split bands.
 */
class LinkwitzRileyCrossover {
    private val lp1 = Biquad()
    private val lp2 = Biquad()
    private val hp1 = Biquad()
    private val hp2 = Biquad()

    fun setFrequency(freq: Float, sampleRate: Int) {
        val q = 0.7071068f
        lp1.setLowPass(freq, q, sampleRate)
        lp2.setLowPass(freq, q, sampleRate)
        hp1.setHighPass(freq, q, sampleRate)
        hp2.setHighPass(freq, q, sampleRate)
    }

    fun reset() {
        lp1.reset(); lp2.reset(); hp1.reset(); hp2.reset()
    }

    fun lowLeft(x: Float) = lp2.processLeft(lp1.processLeft(x))
    fun lowRight(x: Float) = lp2.processRight(lp1.processRight(x))
    fun highLeft(x: Float) = hp2.processLeft(hp1.processLeft(x))
    fun highRight(x: Float) = hp2.processRight(hp1.processRight(x))
}

/** Peak envelope follower with independent attack and release times. */
class EnvelopeFollower(var attack: Float = 10f, var release: Float = 100f) {
    private var attackCoeff = 0f
    private var releaseCoeff = 0f
    private var envelope = 0f

    fun prepare(sampleRate: Int) {
        attackCoeff = timeToCoeff(attack, sampleRate)
        releaseCoeff = timeToCoeff(release, sampleRate)
    }

    private fun timeToCoeff(ms: Float, sampleRate: Int): Float =
        exp(-1.0 / ((ms.coerceAtLeast(0.01f) / 1000.0) * sampleRate)).toFloat()

    fun process(input: Float): Float {
        val rectified = abs(input)
        val coeff = if (rectified > envelope) attackCoeff else releaseCoeff
        envelope = rectified + coeff * (envelope - rectified)
        return envelope
    }

    fun reset() {
        envelope = 0f
    }
}

/** Fractional-delay ring buffer: the backbone of delay, chorus, flanger and reverb. */
class DelayLine(maxSamples: Int) {
    private val buffer = FloatArray(maxSamples.coerceAtLeast(4))
    private var writeIndex = 0

    fun reset() {
        buffer.fill(0f)
        writeIndex = 0
    }

    fun write(value: Float) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % buffer.size
    }

    /** Linear-interpolated read, [delaySamples] behind the write head. */
    fun read(delaySamples: Float): Float {
        val d = delaySamples.coerceIn(1f, (buffer.size - 2).toFloat())
        var pos = writeIndex - d
        if (pos < 0) pos += buffer.size
        val i = pos.toInt()
        val frac = pos - i
        val a = buffer[i % buffer.size]
        val b = buffer[(i + 1) % buffer.size]
        return a + (b - a) * frac
    }
}

/** Low-frequency oscillator for modulation effects. */
class Lfo {
    private var phase = 0.0
    private var increment = 0.0

    fun setRate(hz: Float, sampleRate: Int) {
        increment = 2.0 * PI * hz.coerceAtLeast(0.001f) / sampleRate
    }

    fun reset() {
        phase = 0.0
    }

    /** Advances and returns a sine in -1..1. */
    fun next(): Float {
        phase += increment
        if (phase > 2 * PI) phase -= 2 * PI
        return sin(phase).toFloat()
    }

    /** Advances and returns a unipolar ramp in 0..1, for pumping envelopes. */
    fun nextRamp(): Float {
        phase += increment
        if (phase > 2 * PI) phase -= 2 * PI
        return (phase / (2 * PI)).toFloat()
    }
}

/** Static compressor gain computer with a soft knee, shared by every dynamics unit. */
object GainComputer {
    fun gainDb(inputDb: Float, thresholdDb: Float, ratio: Float, kneeDb: Float): Float {
        val over = inputDb - thresholdDb
        val safeRatio = ratio.coerceAtLeast(1f)
        return when {
            kneeDb <= 0f -> if (over <= 0f) 0f else over * (1f / safeRatio - 1f)
            over <= -kneeDb / 2f -> 0f
            over >= kneeDb / 2f -> over * (1f / safeRatio - 1f)
            else -> {
                val x = over + kneeDb / 2f
                (1f / safeRatio - 1f) * x * x / (2f * kneeDb)
            }
        }
    }
}

/** Shared non-linear shapers. */
object Shapers {
    /** Smooth symmetric soft clip: odd harmonics, transistor-like. */
    fun soft(x: Float, drive: Float): Float {
        val d = drive.coerceAtLeast(0.1f)
        return tanh(x * d) / tanh(d)
    }

    /** Asymmetric shaping generates even harmonics: the tube character. */
    fun tube(x: Float, drive: Float): Float {
        val d = drive.coerceAtLeast(0.1f)
        val v = x * d
        val shaped = if (v >= 0f) tanh(v) else tanh(v * 0.7f) * 0.85f
        return shaped / tanh(d)
    }

    /** Tape-style saturation: gentle peak compression with a soft knee. */
    fun tape(x: Float, drive: Float): Float {
        val d = drive.coerceAtLeast(0.1f)
        val v = x * d
        return (v / (1f + abs(v))) * (1f + 1f / d) * 0.5f
    }
}
