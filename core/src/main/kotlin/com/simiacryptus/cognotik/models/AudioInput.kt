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
   /**
    * Concatenates two [AudioInput] instances into a single [AudioInput].
    *
    * If the formats differ, [other] is converted to this instance's format first.
    * The sample rate, channel count, and bits-per-sample must match after conversion.
    *
    * For WAV inputs, the RIFF/WAVE header of [other] is stripped and a new WAV
    * header is generated reflecting the combined PCM data size. For other
    * (raw) formats, the audio bytes are simply concatenated.
    */
   operator fun plus(other: AudioInput): AudioInput {
     val rhs = if (this.format == other.format) other else other.convert(this.format)
     require(this.sampleRate == rhs.sampleRate) {
       "Sample rates do not match: ${this.sampleRate} vs ${rhs.sampleRate}"
     }
     require(this.channels == rhs.channels) {
       "Channel counts do not match: ${this.channels} vs ${rhs.channels}"
     }
     require(this.bitsPerSample == rhs.bitsPerSample) {
       "Bits-per-sample do not match: ${this.bitsPerSample} vs ${rhs.bitsPerSample}"
     }
     val combinedBytes: ByteArray = when (format.lowercase()) {
       "wav" -> {
         val lhsPcm = stripWavHeader(this.audioBytes)
         val rhsPcm = stripWavHeader(rhs.audioBytes)
         val merged = ByteArray(lhsPcm.size + rhsPcm.size)
         System.arraycopy(lhsPcm, 0, merged, 0, lhsPcm.size)
         System.arraycopy(rhsPcm, 0, merged, lhsPcm.size, rhsPcm.size)
         pcmToWav(merged, sampleRate, channels, bitsPerSample)
       }
       else -> {
         val a = this.audioBytes
         val b = rhs.audioBytes
         val merged = ByteArray(a.size + b.size)
         System.arraycopy(a, 0, merged, 0, a.size)
         System.arraycopy(b, 0, merged, a.size, b.size)
         merged
       }
     }
     return AudioInput(
       format = this.format,
       sampleRate = this.sampleRate,
       channels = this.channels,
       bitsPerSample = this.bitsPerSample
     ).also { it.audioBytes = combinedBytes }
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
     /**
      * Returns the PCM payload of a WAV byte array by locating the "data"
      * sub-chunk and returning its contents. Falls back to skipping the
      * standard 44-byte header if parsing fails or the input does not appear
      * to be a WAV (RIFF/WAVE) container.
      */
     internal fun stripWavHeader(wav: ByteArray): ByteArray {
       if (wav.size < 44) return wav
       val isRiff = wav[0] == 'R'.code.toByte() && wav[1] == 'I'.code.toByte() &&
           wav[2] == 'F'.code.toByte() && wav[3] == 'F'.code.toByte()
       val isWave = wav[8] == 'W'.code.toByte() && wav[9] == 'A'.code.toByte() &&
           wav[10] == 'V'.code.toByte() && wav[11] == 'E'.code.toByte()
       if (!isRiff || !isWave) return wav
       val buf = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
       var pos = 12
       while (pos + 8 <= wav.size) {
         val id = String(wav, pos, 4, Charsets.US_ASCII)
         val size = buf.getInt(pos + 4)
         val dataStart = pos + 8
         if (id == "data") {
           val end = (dataStart + size).coerceAtMost(wav.size)
           return wav.copyOfRange(dataStart, end)
         }
         pos = dataStart + size + (size and 1)
       }
       return wav.copyOfRange(44, wav.size)
     }
  }


}