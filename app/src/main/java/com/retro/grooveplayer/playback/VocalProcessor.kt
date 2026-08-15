package com.retro.grooveplayer.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VocalProcessor : AudioProcessor {
    enum class Mode { OFF, ISOLATE_INSTRUMENTAL, ISOLATE_VOCAL }

    var mode = Mode.OFF

    private var pendingInputFormat = AudioFormat.NOT_SET
    private var inputFormat = AudioFormat.NOT_SET
    private var outputBuffer = AudioProcessor.EMPTY_BUFFER
    private var buffer = ByteBuffer.allocate(0)

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        pendingInputFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        // Keep the processor always active to allow seamless, real-time live switching without clicks/reflushing
        return pendingInputFormat != AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val limit = inputBuffer.limit()
        val remaining = inputBuffer.remaining()
        
        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocate(remaining).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        // Process 16-bit PCM stereo sample by sample
        if (inputFormat.channelCount == 2) {
            while (inputBuffer.hasRemaining()) {
                val left = inputBuffer.short
                val right = inputBuffer.short

                when (mode) {
                    Mode.ISOLATE_INSTRUMENTAL -> {
                        // Out-Of-Phase Stereo (OOPS) / Center Channel Subtraction
                        // Subtracting left and right removes centered vocals (monophonic parts), leaving instrumentals
                        val diff = ((left - right) / 2).toInt().coerceIn(-32768, 32767).toShort()
                        buffer.putShort(diff) // Left channel output
                        buffer.putShort(diff) // Right channel output
                    }
                    Mode.ISOLATE_VOCAL -> {
                        // Center Channel Extraction
                        // Taking the average (L + R) / 2 cancels side signals (reverb, stereo effects) and keeps center (vocals)
                        val sum = ((left + right) / 2).toInt().coerceIn(-32768, 32767).toShort()
                        buffer.putShort(sum)
                        buffer.putShort(sum)
                    }
                    Mode.OFF -> {
                        buffer.putShort(left)
                        buffer.putShort(right)
                    }
                }
            }
        } else {
            // Mono track: pass through unchanged
            buffer.put(inputBuffer)
        }

        inputBuffer.limit(limit)
        buffer.flip()
        outputBuffer = buffer
    }

    override fun queueEndOfStream() {}

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = outputBuffer == AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputFormat = pendingInputFormat
    }

    override fun reset() {
        flush()
        buffer = ByteBuffer.allocate(0)
        inputFormat = AudioFormat.NOT_SET
        pendingInputFormat = AudioFormat.NOT_SET
    }
}
