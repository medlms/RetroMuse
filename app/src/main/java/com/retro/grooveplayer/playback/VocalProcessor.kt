package com.retro.grooveplayer.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
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
        // Safe check: we only support PCM 16-bit. If the input format is not PCM 16-bit,
        // we return AudioFormat.NOT_SET so that we deactivate ourselves safely instead of throwing exceptions.
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            pendingInputFormat = AudioFormat.NOT_SET
            return AudioFormat.NOT_SET
        }
        pendingInputFormat = inputAudioFormat
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        return pendingInputFormat != AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val remaining = inputBuffer.remaining()
        
        if (buffer.capacity() < remaining) {
            buffer = ByteBuffer.allocate(remaining).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }

        // Process 16-bit PCM stereo sample by sample
        if (inputFormat != AudioFormat.NOT_SET && inputFormat.channelCount == 2) {
            // Ensure we have at least 4 bytes (2 channels * 2 bytes/sample) to read a stereo frame
            while (inputBuffer.remaining() >= 4) {
                val left = inputBuffer.short
                val right = inputBuffer.short

                when (mode) {
                    Mode.ISOLATE_INSTRUMENTAL -> {
                        // Out-Of-Phase Stereo (OOPS): L - R cancels center vocals
                        val diff = ((left - right) / 2).toInt().coerceIn(-32768, 32767).toShort()
                        buffer.putShort(diff)
                        buffer.putShort(diff)
                    }
                    Mode.ISOLATE_VOCAL -> {
                        // Mono sum extraction: (L + R) / 2 isolates centered vocals
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
            // Put any leftover trailing bytes directly into the buffer to avoid underflow exceptions
            if (inputBuffer.hasRemaining()) {
                buffer.put(inputBuffer)
            }
        } else {
            // Mono track or unsupported layout: pass through unchanged
            buffer.put(inputBuffer)
        }

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
