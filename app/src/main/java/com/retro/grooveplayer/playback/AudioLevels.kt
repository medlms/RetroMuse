package com.retro.grooveplayer.playback

import kotlinx.coroutines.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Real spectrum data for the player's bars.
 *
 * The bars used to be Random.nextFloat(), animating identically regardless of what was
 * playing. The samples are tapped from [VocalProcessor], which already sits in the
 * audio pipeline and sees every decoded frame - so this needs no RECORD_AUDIO
 * permission, unlike the platform Visualizer effect.
 */
object AudioLevels {
    const val BAR_COUNT = 28
    private const val FFT_SIZE = 512

    /** Normalised 0..1 magnitude per bar, read by the UI each frame. */
    val bars = FloatArray(BAR_COUNT)

    // Single-producer ring buffer written from the audio thread, read by the analyser.
    private val ring = FloatArray(FFT_SIZE * 2)
    @Volatile private var writeIndex = 0

    private val window = FloatArray(FFT_SIZE) { i ->
        0.5f * (1f - cos(2.0 * PI * i / (FFT_SIZE - 1)).toFloat())
    }
    private val real = FloatArray(FFT_SIZE)
    private val imag = FloatArray(FFT_SIZE)

    // Log-spaced bucket edges over the lower half of the spectrum.
    private val edges = IntArray(BAR_COUNT + 1) { i ->
        val bins = FFT_SIZE / 2
        val t = i.toFloat() / BAR_COUNT
        (1.0 * Math.pow(bins.toDouble(), t.toDouble())).roundToInt().coerceIn(1, bins)
    }

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Called from the audio thread for every output frame. Must stay allocation-free. */
    fun feed(sample: Float) {
        val i = writeIndex
        ring[i] = sample
        writeIndex = if (i + 1 >= ring.size) 0 else i + 1
    }

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            while (isActive) {
                analyse()
                delay(45)
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        bars.fill(0f)
    }

    private fun analyse() {
        // Copy the most recent FFT_SIZE samples out of the ring.
        val end = writeIndex
        for (i in 0 until FFT_SIZE) {
            var idx = end - FFT_SIZE + i
            if (idx < 0) idx += ring.size
            real[i] = ring[idx] * window[i]
            imag[i] = 0f
        }

        fft(real, imag)

        for (b in 0 until BAR_COUNT) {
            val from = edges[b]
            val to = edges[b + 1].coerceAtLeast(edges[b] + 1)
            var peak = 0f
            for (k in from until to) {
                val magnitude = hypot(real[k], imag[k])
                if (magnitude > peak) peak = magnitude
            }
            // Convert to dB and normalise into a range that looks right on screen.
            val db = if (peak > 1e-6f) 20f * log10(peak) else -90f
            val normalised = ((db + 60f) / 60f).coerceIn(0f, 1f)
            // Fast attack, slow decay - how a level meter is expected to move.
            bars[b] = if (normalised > bars[b]) normalised else bars[b] * 0.80f
        }
    }

    /** In-place iterative radix-2 Cooley-Tukey FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wRe = cos(ang).toFloat()
            val wIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * curRe - im[i + k + len / 2] * curIm
                    val vIm = re[i + k + len / 2] * curIm + im[i + k + len / 2] * curRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
