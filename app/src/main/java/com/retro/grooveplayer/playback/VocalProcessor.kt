package com.retro.grooveplayer.playback

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

class VocalProcessor : BaseAudioProcessor() {
    enum class Mode { OFF, ISOLATE_INSTRUMENTAL, ISOLATE_VOCAL }

    var mode = Mode.OFF

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // We only support PCM 16-bit. If the input format is not PCM 16-bit,
        // we return AudioFormat.NOT_SET to safely deactivate this processor.
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            return AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Obtain a formatted output buffer from BaseAudioProcessor
        val outputBuffer = replaceOutputBuffer(remaining)

        // Process 16-bit PCM stereo sample by sample
        if (inputAudioFormat.channelCount == 2) {
            // Ensure we have at least 4 bytes (2 channels * 2 bytes/sample) to read a stereo frame
            while (inputBuffer.remaining() >= 4) {
                val left = inputBuffer.short
                val right = inputBuffer.short

                when (mode) {
                    Mode.ISOLATE_INSTRUMENTAL -> {
                        // Out-Of-Phase Stereo (OOPS): L - R cancels center vocals
                        val diff = ((left - right) / 2).toInt().coerceIn(-32768, 32767).toShort()
                        outputBuffer.putShort(diff)
                        outputBuffer.putShort(diff)
                    }
                    Mode.ISOLATE_VOCAL -> {
                        // Mono sum extraction: (L + R) / 2 isolates centered vocals
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
            // Put any leftover trailing bytes directly to output to avoid frame fragmentation crashes
            if (inputBuffer.hasRemaining()) {
                outputBuffer.put(inputBuffer)
            }
        } else {
            // Mono track: pass through unchanged
            outputBuffer.put(inputBuffer)
        }

        outputBuffer.flip()
    }
}
