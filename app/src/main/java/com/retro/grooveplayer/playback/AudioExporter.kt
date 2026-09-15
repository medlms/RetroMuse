package com.retro.grooveplayer.playback

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.retro.grooveplayer.data.Song
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Renders a processed copy of a track to a shareable .m4a file.
 *
 * The live effects chain is playback-only, which means a slowed + reverb or karaoke
 * version exists solely inside the app. Exporting turns the app into something people
 * can post from, which is the only realistic organic-growth loop it has.
 *
 * Pipeline: MediaExtractor -> MediaCodec decode to PCM -> vocal separation / 8D ->
 * reverb -> resample for speed & pitch -> MediaCodec AAC encode -> MediaMuxer.
 */
object AudioExporter {

    data class Progress(val fraction: Float, val done: Boolean, val outputUri: Uri?, val error: String?)

    /**
     * Output container.
     *
     * MP3 is deliberately absent: Android ships an MP3 decoder but no encoder, so it
     * would need a bundled native library like LAME.
     */
    enum class Format(val label: String, val extension: String, val mimeType: String) {
        M4A("M4A / AAC", "m4a", "audio/mp4"),
        WAV("WAV (lossless)", "wav", "audio/wav")
    }

    private const val TIMEOUT_US = 10_000L

    /**
     * @param speed playback rate multiplier; values below 1 slow the track down.
     * @param semitones additional pitch shift, applied on top of [speed].
     */
    suspend fun export(
        context: Context,
        song: Song,
        presetLabel: String,
        outputName: String,
        speed: Float,
        semitones: Int,
        reverbPercent: Int,
        vocalMode: VocalProcessor.Mode,
        spatial: Boolean,
        format: Format = Format.M4A,
        bitrate: Int = 192_000,
        /** Clip bounds in milliseconds. endMs <= 0 means render to the end. */
        startMs: Long = 0L,
        endMs: Long = 0L,
        onProgress: (Float) -> Unit
    ): Progress {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var tempFile: File? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(context, Uri.parse(song.uri), null)

            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return Progress(0f, true, null, "No audio track found in this file.")

            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) {
                inputFormat.getLong(MediaFormat.KEY_DURATION)
            } else {
                song.duration * 1000L
            }

            decoder = MediaCodec.createDecoderByType(
                inputFormat.getString(MediaFormat.KEY_MIME) ?: "audio/mpeg"
            ).apply {
                configure(inputFormat, null, null, 0)
                start()
            }

            // Clip bounds. Seeking to the previous sync frame keeps the decoder happy;
            // anything decoded before the requested start is discarded downstream.
            val startUs = (startMs * 1000L).coerceIn(0L, durationUs)
            val endUs = if (endMs > 0L) (endMs * 1000L).coerceIn(startUs, durationUs) else durationUs
            if (startUs > 0L) {
                extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            }
            val renderUs = (endUs - startUs).coerceAtLeast(1L)

            // Resampling by this ratio changes speed and pitch together, which is
            // exactly what "slowed" and "nightcore" mean.
            val pitchFactor = Math.pow(2.0, semitones / 12.0).toFloat()
            val rate = (speed * pitchFactor).coerceIn(0.5f, 2.0f)

            val dsp = ExportDsp(sampleRate, reverbPercent, vocalMode, spatial, rate)

            // WAV skips the encoder entirely: PCM is written straight out.
            if (format == Format.WAV) {
                tempFile = File.createTempFile("retromuse_export", ".wav", context.cacheDir)
                val error = renderWav(
                    extractor, decoder, channelCount, sampleRate, dsp,
                    startUs, endUs, renderUs, tempFile, onProgress
                )
                if (error != null) return Progress(0f, true, null, error)
                val uri = publish(context, tempFile, song, outputName, presetLabel, format)
                return Progress(1f, true, uri, null)
            }

            val outFormat = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(
                    MediaFormat.KEY_AAC_PROFILE,
                    android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC
                )
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            tempFile = File.createTempFile("retromuse_export", ".m4a", context.cacheDir)
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val result = runPipeline(
                extractor, decoder, encoder, muxer, channelCount, dsp,
                startUs, endUs, renderUs, onProgress
            )
            if (result != null) return Progress(0f, true, null, result)

            val uri = publish(context, tempFile, song, outputName, presetLabel, format)
            return Progress(1f, true, uri, null)
        } catch (e: Exception) {
            e.printStackTrace()
            return Progress(0f, true, null, e.message ?: "Export failed.")
        } finally {
            try { decoder?.stop(); decoder?.release() } catch (e: Exception) { e.printStackTrace() }
            try { encoder?.stop(); encoder?.release() } catch (e: Exception) { e.printStackTrace() }
            try { muxer?.stop(); muxer?.release() } catch (e: Exception) { e.printStackTrace() }
            try { extractor?.release() } catch (e: Exception) { e.printStackTrace() }
            try { tempFile?.delete() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun runPipeline(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        encoder: MediaCodec,
        muxer: MediaMuxer,
        channelCount: Int,
        dsp: ExportDsp,
        startUs: Long,
        endUs: Long,
        renderUs: Long,
        onProgress: (Float) -> Unit
    ): String? {
        val decoderInfo = MediaCodec.BufferInfo()
        val encoderInfo = MediaCodec.BufferInfo()

        var muxerTrack = -1
        var muxerStarted = false
        var sawInputEOS = false
        var sawDecodeEOS = false
        var sawEncodeEOS = false
        var encoderInputDone = false
        var presentationUs = 0L
        val pending = ArrayList<Short>(8192)

        while (!sawEncodeEOS) {
            // 1. Feed compressed data to the decoder.
            if (!sawInputEOS) {
                val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = decoder.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    // Stop feeding once past the clip's end point.
                    if (size < 0 || extractor.sampleTime > endUs) {
                        decoder.queueInputBuffer(
                            inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        sawInputEOS = true
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            // 2. Drain decoded PCM, run the effects, buffer the result.
            if (!sawDecodeEOS) {
                val outIndex = decoder.dequeueOutputBuffer(decoderInfo, TIMEOUT_US)
                if (outIndex >= 0) {
                    // Frames before the clip start are decoded (the seek lands on a
                    // sync frame) but discarded, so the clip begins exactly on time.
                    val inClip = decoderInfo.presentationTimeUs >= startUs &&
                        decoderInfo.presentationTimeUs <= endUs
                    if (decoderInfo.size > 0 && inClip) {
                        val buffer = decoder.getOutputBuffer(outIndex)!!
                        buffer.position(decoderInfo.offset)
                        buffer.limit(decoderInfo.offset + decoderInfo.size)
                        dsp.process(buffer, channelCount, pending)
                        onProgress(
                            ((decoderInfo.presentationTimeUs - startUs).toFloat() /
                                renderUs.coerceAtLeast(1)).coerceIn(0f, 0.99f)
                        )
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (decoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawDecodeEOS = true
                    }
                }
            }

            // 3. Feed processed PCM into the encoder.
            if (!encoderInputDone) {
                val inIndex = encoder.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = encoder.getInputBuffer(inIndex)!!
                    buffer.clear()
                    val capacityShorts = buffer.capacity() / 2
                    val count = minOf(capacityShorts, pending.size)

                    if (count == 0 && sawDecodeEOS) {
                        encoder.queueInputBuffer(
                            inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        )
                        encoderInputDone = true
                    } else if (count > 0) {
                        val shorts = buffer.asShortBuffer()
                        for (i in 0 until count) shorts.put(pending[i])
                        repeat(count) { pending.removeAt(0) }
                        val bytes = count * 2
                        encoder.queueInputBuffer(inIndex, 0, bytes, presentationUs, 0)
                        // Two channels per frame.
                        presentationUs += (count / 2) * 1_000_000L / dsp.sampleRate
                    } else {
                        encoder.queueInputBuffer(inIndex, 0, 0, presentationUs, 0)
                    }
                }
            }

            // 4. Drain the encoder into the muxer.
            val encIndex = encoder.dequeueOutputBuffer(encoderInfo, TIMEOUT_US)
            when {
                encIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (!muxerStarted) {
                        muxerTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                }
                encIndex >= 0 -> {
                    val buffer = encoder.getOutputBuffer(encIndex)!!
                    if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        encoderInfo.size = 0
                    }
                    if (encoderInfo.size > 0 && muxerStarted) {
                        buffer.position(encoderInfo.offset)
                        buffer.limit(encoderInfo.offset + encoderInfo.size)
                        muxer.writeSampleData(muxerTrack, buffer, encoderInfo)
                    }
                    encoder.releaseOutputBuffer(encIndex, false)
                    if (encoderInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawEncodeEOS = true
                    }
                }
            }
        }
        return null
    }

    /**
     * WAV path: decode, process, and write PCM straight to a RIFF file. No encoder is
     * involved, which is what makes it lossless and what makes it big.
     */
    private fun renderWav(
        extractor: MediaExtractor,
        decoder: MediaCodec,
        channelCount: Int,
        sampleRate: Int,
        dsp: ExportDsp,
        startUs: Long,
        endUs: Long,
        renderUs: Long,
        target: File,
        onProgress: (Float) -> Unit
    ): String? {
        val info = MediaCodec.BufferInfo()
        val pending = ArrayList<Short>(8192)
        var sawInputEOS = false
        var sawDecodeEOS = false
        var totalSamples = 0L

        java.io.RandomAccessFile(target, "rw").use { out ->
            // Placeholder header; sizes are patched in once the length is known.
            out.write(ByteArray(44))

            while (!sawDecodeEOS) {
                if (!sawInputEOS) {
                    val inIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buffer = decoder.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0 || extractor.sampleTime > endUs) {
                            decoder.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            sawInputEOS = true
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIndex >= 0) {
                    val inClip = info.presentationTimeUs >= startUs && info.presentationTimeUs <= endUs
                    if (info.size > 0 && inClip) {
                        val buffer = decoder.getOutputBuffer(outIndex)!!
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        dsp.process(buffer, channelCount, pending)

                        if (pending.isNotEmpty()) {
                            val bytes = ByteArray(pending.size * 2)
                            for (i in pending.indices) {
                                val v = pending[i].toInt()
                                bytes[i * 2] = (v and 0xFF).toByte()
                                bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                            }
                            out.write(bytes)
                            totalSamples += pending.size
                            pending.clear()
                        }

                        onProgress(
                            ((info.presentationTimeUs - startUs).toFloat() /
                                renderUs.coerceAtLeast(1)).coerceIn(0f, 0.99f)
                        )
                    }
                    decoder.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        sawDecodeEOS = true
                    }
                }
            }

            if (totalSamples == 0L) return "Nothing was rendered - the clip may be empty."

            val dataBytes = (totalSamples * 2).toInt()
            out.seek(0)
            out.write(wavHeader(dataBytes, sampleRate, 2))
        }
        return null
    }

    /** Standard 44-byte RIFF/WAVE header for 16-bit PCM. */
    private fun wavHeader(dataBytes: Int, sampleRate: Int, channels: Int): ByteArray {
        val byteRate = sampleRate * channels * 2
        val buffer = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)               // PCM chunk size
        buffer.putShort(1)              // format = PCM
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort((channels * 2).toShort())  // block align
        buffer.putShort(16)             // bits per sample
        buffer.put("data".toByteArray())
        buffer.putInt(dataBytes)
        return buffer.array()
    }

    /** The name offered in the export dialog before the user edits it. */
    fun defaultName(song: Song, presetLabel: String): String =
        if (presetLabel.isBlank() || presetLabel == "Original") song.name
        else "${song.name} ($presetLabel)"

    /**
     * Strips characters that are illegal in a file name, and guarantees something
     * usable if the user clears the field entirely.
     */
    fun sanitiseName(raw: String, fallback: String): String {
        val cleaned = raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(120)
        return cleaned.ifBlank { fallback }
    }

    /** Writes the finished file into the user's Music folder via MediaStore. */
    private fun publish(
        context: Context,
        source: File,
        song: Song,
        outputName: String,
        presetLabel: String,
        format: Format
    ): Uri? {
        val safeName = sanitiseName(outputName, defaultName(song, presetLabel))
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeName.${format.extension}")
            put(MediaStore.Audio.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Audio.Media.TITLE, safeName)
            put(MediaStore.Audio.Media.ARTIST, song.artist)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/RetroMuse")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null

        resolver.openOutputStream(uri)?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}

/**
 * Offline mirror of the live chain. Separation reuses [VocalProcessor] so exported
 * files sound like what was previewed; reverb and resampling are added here because
 * the live versions are native effects that only exist on the output mix.
 */
private class ExportDsp(
    val sampleRate: Int,
    reverbPercent: Int,
    private val vocalMode: VocalProcessor.Mode,
    private val spatial: Boolean,
    private val rate: Float
) {
    private val separator = VocalProcessor().also {
        it.mode = vocalMode
        it.spatialAudio = spatial
    }
    private val reverb = SchroederReverb(sampleRate, reverbPercent / 100f)

    // Its own rack instance, reading the same RackSettings the live chain uses, so the
    // saved file matches what was previewed.
    private val rack = com.retro.grooveplayer.dsp.EffectRack().also {
        it.prepare(sampleRate)
        it.sync(force = true)
    }
    private val frame = com.retro.grooveplayer.dsp.AudioFrame()

    // Fractional read position for linear-interpolation resampling.
    private var position = 0.0
    private val bufferL = ArrayList<Float>(16384)
    private val bufferR = ArrayList<Float>(16384)

    init {
        // Configure the separator for this stream so its filters are tuned correctly.
        separator.configure(
            androidx.media3.common.audio.AudioProcessor.AudioFormat(
                sampleRate, 2, androidx.media3.common.C.ENCODING_PCM_16BIT
            )
        )
    }

    fun process(input: ByteBuffer, channelCount: Int, out: ArrayList<Short>) {
        val shorts = input.order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()

        while (shorts.remaining() >= channelCount) {
            val l = shorts.get() / 32768f
            val r = if (channelCount > 1) shorts.get() / 32768f else l
            separator.processInto(frame, l, r, vocalMode, spatial)
            if (reverb.enabled) {
                frame.l = reverb.processLeft(frame.l)
                frame.r = reverb.processRight(frame.r)
            }
            rack.process(frame)
            bufferL.add(frame.l)
            bufferR.add(frame.r)
        }

        // Resample the buffered frames to apply speed/pitch.
        while (position + 1 < bufferL.size) {
            val i = position.toInt()
            val frac = (position - i).toFloat()
            val l = bufferL[i] + (bufferL[i + 1] - bufferL[i]) * frac
            val r = bufferR[i] + (bufferR[i + 1] - bufferR[i]) * frac
            out.add((l.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
            out.add((r.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
            position += rate
        }

        // Drop consumed frames, keeping one for interpolation continuity.
        val consumed = position.toInt()
        if (consumed > 1) {
            repeat(consumed - 1) {
                bufferL.removeAt(0)
                bufferR.removeAt(0)
            }
            position -= (consumed - 1)
        }
    }
}

/** Small Schroeder reverb: four parallel combs into two allpass sections. */
private class SchroederReverb(sampleRate: Int, private val mix: Float) {
    val enabled = mix > 0.01f

    private val combDelays = intArrayOf(1557, 1617, 1491, 1422).map {
        (it * sampleRate / 44100).coerceAtLeast(1)
    }
    private val allpassDelays = intArrayOf(225, 556).map {
        (it * sampleRate / 44100).coerceAtLeast(1)
    }

    private val combsL = combDelays.map { FloatArray(it) }
    private val combsR = combDelays.map { FloatArray(it + 23) }
    private val allpassL = allpassDelays.map { FloatArray(it) }
    private val allpassR = allpassDelays.map { FloatArray(it + 11) }

    private val combIdxL = IntArray(combDelays.size)
    private val combIdxR = IntArray(combDelays.size)
    private val apIdxL = IntArray(allpassDelays.size)
    private val apIdxR = IntArray(allpassDelays.size)

    private val feedback = 0.78f

    fun processLeft(input: Float) = run(input, combsL, combIdxL, allpassL, apIdxL)
    fun processRight(input: Float) = run(input, combsR, combIdxR, allpassR, apIdxR)

    private fun run(
        input: Float,
        combs: List<FloatArray>,
        combIdx: IntArray,
        allpasses: List<FloatArray>,
        apIdx: IntArray
    ): Float {
        var wet = 0f
        for (c in combs.indices) {
            val line = combs[c]
            val i = combIdx[c]
            val delayed = line[i]
            line[i] = input + delayed * feedback
            combIdx[c] = (i + 1) % line.size
            wet += delayed
        }
        wet /= combs.size

        for (a in allpasses.indices) {
            val line = allpasses[a]
            val i = apIdx[a]
            val delayed = line[i]
            val out = -wet + delayed
            line[i] = wet + delayed * 0.5f
            apIdx[a] = (i + 1) % line.size
            wet = out
        }

        return (input * (1f - mix * 0.6f) + wet * mix * 0.6f).coerceIn(-1f, 1f)
    }
}
