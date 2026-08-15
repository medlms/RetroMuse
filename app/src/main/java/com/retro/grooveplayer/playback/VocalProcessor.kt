package com.retro.grooveplayer.playback

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VocalProcessor : BaseAudioProcessor() {
    enum class Mode { OFF, ISOLATE_INSTRUMENTAL, ISOLATE_VOCAL }

    var mode = Mode.OFF

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // Support both 16-bit signed PCM and 32-bit float PCM.
        // If the format is neither, return NOT_SET to deactivate safely.
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Obtain a formatted output buffer from BaseAudioProcessor
        val outputBuffer = replaceOutputBuffer(remaining)

        // Ensure buffers use native byte ordering
        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        if (inputAudioFormat.encoding == androidx.media3.common.C.ENCODING_PCM_FLOAT) {
            // Process 32-bit Float PCM
            if (inputAudioFormat.channelCount == 2) {
                // Stereo float frame size is 8 bytes (2 channels * 4 bytes/sample)
                while (inputBuffer.remaining() >= 8) {
                    val left = inputBuffer.float
                    val right = inputBuffer.float

                    when (mode) {
                        Mode.ISOLATE_INSTRUMENTAL -> {
                            // Out-Of-Phase Stereo (OOPS) center cancellation for floats (scaled to prevent clipping)
                            val diff = ((left - right) / 2.0f).coerceIn(-1.0f, 1.0f)
                            outputBuffer.putFloat(diff)
                            outputBuffer.putFloat(diff)
                        }
                        Mode.ISOLATE_VOCAL -> {
                            // Mono sum extraction for floats (scaled to prevent clipping)
                            val sum = ((left + right) / 2.0f).coerceIn(-1.0f, 1.0f)
                            outputBuffer.putFloat(sum)
                            outputBuffer.putFloat(sum)
                        }
                        Mode.OFF -> {
                            outputBuffer.putFloat(left)
                            outputBuffer.putFloat(right)
                        }
                    }
                }
                // Write any trailing partial frame bytes directly to output
                if (inputBuffer.hasRemaining()) {
                    outputBuffer.put(inputBuffer)
                }
            } else {
                // Mono: pass through
                outputBuffer.put(inputBuffer)
            }
        } else {
            // Process 16-bit PCM
            if (inputAudioFormat.channelCount == 2) {
                // Stereo 16-bit frame size is 4 bytes (2 channels * 2 bytes/sample)
                while (inputBuffer.remaining() >= 4) {
                    val left = inputBuffer.short
                    val right = inputBuffer.short

                    when (mode) {
                        Mode.ISOLATE_INSTRUMENTAL -> {
                            // Out-Of-Phase Stereo (OOPS) center cancellation for 16-bit shorts (scaled to prevent clipping)
                            val diff = ((left - right) / 2).toInt().coerceIn(-32768, 32767).toShort()
                            outputBuffer.putShort(diff)
                            outputBuffer.putShort(diff)
                        }
                        Mode.ISOLATE_VOCAL -> {
                            // Mono sum extraction for 16-bit shorts (scaled to prevent clipping)
                            val sum = ((left + right) / 2).toInt().coerceIn(-32768, 32767).toShort()
                            outputBuffer.putShort(sum)
                            outputBuffer.putShort(sum)
                        }
                        Mode.OFF -> {
                            outputBuffer.putShort(left)
                            outputBuffer.putShort(right)
                        }
                    }
                }
                // Write any trailing partial frame bytes directly to output
                if (inputBuffer.hasRemaining()) {
                    outputBuffer.put(inputBuffer)
                }
            } else {
                // Mono: pass through
                outputBuffer.put(inputBuffer)
            }
        }

        outputBuffer.flip()
    }
}
