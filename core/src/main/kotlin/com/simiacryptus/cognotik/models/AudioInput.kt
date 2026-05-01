package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Path
import java.util.*
import kotlin.io.path.extension

data class AudioInput(
  var data: String = "",
  var format: String = "wav",
  var sampleRate: Int = 24000,
  var channels: Int = 1,
  var bitsPerSample: Int = 16,
) {
  var audioBytes: ByteArray
    @JsonIgnore
    get() = Base64.getDecoder().decode(data)
    @JsonIgnore
    set(value) {
      data = Base64.getEncoder().encodeToString(value)
    }

  fun convert(targetFormat: String): AudioInput = when {
    format == targetFormat -> this
    format.uppercase() == "L16" && targetFormat.lowercase() == "wav" -> {
      val pcmBytes = audioBytes
      val wavBytes = pcmToWav(
        pcm = pcmBytes,
        sampleRate = sampleRate,
        channels = channels,
        bitsPerSample = bitsPerSample
      )
      AudioInput(
        format = "wav",
        sampleRate = sampleRate,
        channels = channels,
        bitsPerSample = bitsPerSample
      ).also { it.audioBytes = wavBytes }
    }

    else -> throw UnsupportedOperationException("Conversion from $format to $targetFormat is not supported")
  }

  fun writeAudio(
    outputPath: Path
  ) {
    require(data.isNotEmpty()) { "Audio data is empty; nothing to write." }
    val targetExt = outputPath.extension.lowercase()
    if (targetExt.isNotEmpty() && !format.lowercase().startsWith(targetExt)) {
      return convert(targetExt).writeAudio(outputPath)
    }
    val outFile = outputPath.toFile()
    outFile.parentFile?.let { parent ->
      if (!parent.exists() && !parent.mkdirs() && !parent.exists()) {
        throw IOException("Failed to create parent directory: ${parent.absolutePath}")
      }
    }
    outFile.writeBytes(audioBytes)
  }

  companion object {
    /**
     * Wraps raw L16 PCM audio data in a WAV (RIFF) container.
     *
     * WAV header layout (PCM, 44 bytes):
     *  - "RIFF" (4 bytes)
     *  - ChunkSize (4 bytes, little-endian) = 36 + dataSize
     *  - "WAVE" (4 bytes)
     *  - "fmt " (4 bytes)
     *  - Subchunk1Size (4 bytes, LE) = 16 for PCM
     *  - AudioFormat (2 bytes, LE) = 1 for PCM
     *  - NumChannels (2 bytes, LE)
     *  - SampleRate (4 bytes, LE)
     *  - ByteRate (4 bytes, LE) = SampleRate * NumChannels * BitsPerSample/8
     *  - BlockAlign (2 bytes, LE) = NumChannels * BitsPerSample/8
     *  - BitsPerSample (2 bytes, LE)
     *  - "data" (4 bytes)
     *  - Subchunk2Size (4 bytes, LE) = dataSize
     *
     * Note: L16 is conventionally big-endian on the wire (e.g., RTP payload),
     * but most local L16 buffers (and what OpenAI/similar APIs return) are
     * already little-endian PCM. We assume little-endian here, which is what
     * WAV requires for the sample data.
     */
    fun pcmToWav(
      pcm: ByteArray,
      sampleRate: Int,
      channels: Int,
      bitsPerSample: Int
    ): ByteArray {
      val dataSize = pcm.size
      val byteRate = sampleRate * channels * bitsPerSample / 8
      val blockAlign = (channels * bitsPerSample / 8).toShort()
      val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
      header.put("RIFF".toByteArray(Charsets.US_ASCII))
      header.putInt(36 + dataSize)
      header.put("WAVE".toByteArray(Charsets.US_ASCII))
      header.put("fmt ".toByteArray(Charsets.US_ASCII))
      header.putInt(16)
      header.putShort(1.toShort())
      header.putShort(channels.toShort())
      header.putInt(sampleRate)
      header.putInt(byteRate)
      header.putShort(blockAlign)
      header.putShort(bitsPerSample.toShort())
      header.put("data".toByteArray(Charsets.US_ASCII))
      header.putInt(dataSize)
      val out = ByteArray(44 + dataSize)
      System.arraycopy(header.array(), 0, out, 0, 44)
      System.arraycopy(pcm, 0, out, 44, dataSize)
      return out
    }
  }


}


