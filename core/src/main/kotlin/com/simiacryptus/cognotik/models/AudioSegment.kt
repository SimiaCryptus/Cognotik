package com.simiacryptus.cognotik.models

import com.fasterxml.jackson.annotation.JsonIgnore
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.extension

data class AudioSegment(
  var data: String = "",
  var format: String = "wav",
  var sampleRate: Int = 24000,
  var channels: Int = 1,
  var bitsPerSample: Int = 16,
) {
  val durationSeconds: Double
    get() {
      val bytesPerSample = bitsPerSample / 8
      val totalSamples = audioBytes.size / (channels * bytesPerSample)
      return totalSamples.toDouble() / sampleRate
    }

  var audioBytes: ByteArray
    @JsonIgnore get() = Base64.getDecoder().decode(data)
    @JsonIgnore set(value) {
      data = Base64.getEncoder().encodeToString(value)
    }

  fun convert(targetFormat: String): AudioSegment = when {
    format == targetFormat -> this
    format.uppercase() == "L16" && targetFormat.lowercase() == "wav" -> {
      val pcmBytes = audioBytes
      val wavBytes = pcmToWav(
        pcm = pcmBytes, sampleRate = sampleRate, channels = channels, bitsPerSample = bitsPerSample
      )
      AudioSegment(
        format = "wav", sampleRate = sampleRate, channels = channels, bitsPerSample = bitsPerSample
      ).also { it.audioBytes = wavBytes }
    }
    format.lowercase() == "wav" && targetFormat.uppercase() == "L16" -> {
      val pcmBytes = stripWavHeader(audioBytes)
      AudioSegment(
        format = "L16", sampleRate = sampleRate, channels = channels, bitsPerSample = bitsPerSample
      ).also { it.audioBytes = pcmBytes }
    }

    format.uppercase() == "L16" && targetFormat.uppercase() == "L16" -> this

    else -> convertViaFfmpeg(targetFormat)
  }

  operator fun plus(other: AudioSegment): AudioSegment {
    val (lhs, rhs) = when {
      this.format.equals(other.format, ignoreCase = true) -> Pair(this, other)
      this.format.lowercase() == "wav" -> Pair(this, other.convert(this.format))
      other.format.lowercase() == "wav" -> Pair(this.convert(other.format), other)
      else -> Pair(this.convert("wav"), other.convert("wav"))
    }
    require(lhs.sampleRate == rhs.sampleRate) {
      "Sample rates do not match: ${lhs.sampleRate} vs ${rhs.sampleRate}"
    }
    require(lhs.channels == rhs.channels) {
      "Channel counts do not match: ${lhs.channels} vs ${rhs.channels}"
    }
    require(lhs.bitsPerSample == rhs.bitsPerSample) {
      "Bits-per-sample do not match: ${lhs.bitsPerSample} vs ${rhs.bitsPerSample}"
    }
    val combinedBytes: ByteArray = when (lhs.format.lowercase()) {
      "wav" -> {
        val lhsPcm = stripWavHeader(lhs.audioBytes)
        val rhsPcm = stripWavHeader(rhs.audioBytes)
        val merged = ByteArray(lhsPcm.size + rhsPcm.size)
        System.arraycopy(lhsPcm, 0, merged, 0, lhsPcm.size)
        System.arraycopy(rhsPcm, 0, merged, lhsPcm.size, rhsPcm.size)
        pcmToWav(merged, sampleRate, channels, bitsPerSample)
      }

      "mp3" -> {
        val inputFile1 = Files.createTempFile("audio_mp3_1_", ".mp3").toFile()
        val inputFile2 = Files.createTempFile("audio_mp3_2_", ".mp3").toFile()
        val listFile = Files.createTempFile("audio_mp3_list_", ".txt").toFile()
        val outputFile = Files.createTempFile("audio_mp3_out_", ".mp3").toFile()
        try {
          inputFile1.writeBytes(lhs.audioBytes)
          inputFile2.writeBytes(rhs.audioBytes)
          listFile.writeText("file '${inputFile1.absolutePath}'\nfile '${inputFile2.absolutePath}'\n")
          val cmd = listOf(
            "ffmpeg", "-y", "-f", "concat", "-safe", "0",
            "-i", listFile.absolutePath,
            "-c", "copy",
            outputFile.absolutePath
          )
          val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
          val output = process.inputStream.readBytes()
          val exitCode = process.waitFor()
          if (exitCode != 0) {
            throw IOException(
              "FFmpeg MP3 concatenation failed (exit $exitCode):\n${String(output)}"
            )
          }
          outputFile.readBytes()
        } finally {
          inputFile1.delete()
          inputFile2.delete()
          listFile.delete()
          outputFile.delete()
        }
      }


      else -> {
        val a = lhs.audioBytes
        val b = rhs.audioBytes
        val merged = ByteArray(a.size + b.size)
        System.arraycopy(a, 0, merged, 0, a.size)
        System.arraycopy(b, 0, merged, a.size, b.size)
        merged
      }
    }
    return AudioSegment(
      format = lhs.format, sampleRate = lhs.sampleRate, channels = lhs.channels, bitsPerSample = lhs.bitsPerSample
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
    private fun AudioSegment.convertViaFfmpeg(targetFormat: String): AudioSegment {
      val inputExt = when (format.lowercase()) {
        "l16" -> "raw"
        else -> format.lowercase()
      }
      val outputExt = when (targetFormat.lowercase()) {
        "l16" -> "raw"
        else -> targetFormat.lowercase()
      }
      val inputFile = Files.createTempFile("audio_in_", ".$inputExt").toFile()
      val outputFile = Files.createTempFile("audio_out_", ".$outputExt").toFile()
      try {
        inputFile.writeBytes(audioBytes)
        val cmd = mutableListOf("ffmpeg", "-y", "-i", inputFile.absolutePath)
        if (inputExt == "raw") {
          cmd.addAll(listOf("-f", "s${bitsPerSample}le", "-ar", sampleRate.toString(), "-ac", channels.toString()))
        }
        if (outputExt == "raw") {
          cmd.addAll(listOf("-f", "s${bitsPerSample}le", "-ar", sampleRate.toString(), "-ac", channels.toString()))
        } else {
          cmd.addAll(listOf("-ar", sampleRate.toString(), "-ac", channels.toString()))
        }
        cmd.add(outputFile.absolutePath)
        val process = ProcessBuilder(cmd)
          .redirectErrorStream(true)
          .start()
        val output = process.inputStream.readBytes()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
          throw IOException(
            "FFmpeg conversion from $format to $targetFormat failed (exit $exitCode):\n${String(output)}"
          )
        }
        val convertedBytes = outputFile.readBytes()
        return AudioSegment(
          format = targetFormat,
          sampleRate = sampleRate,
          channels = channels,
          bitsPerSample = bitsPerSample,
        ).also { it.audioBytes = convertedBytes }
      } finally {
        inputFile.delete()
        outputFile.delete()
      }
    }


    fun pcmToWav(
      pcm: ByteArray, sampleRate: Int, channels: Int, bitsPerSample: Int
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

    @JvmStatic
    fun stripWavHeader(wav: ByteArray): ByteArray {
      if (wav.size < 44) return wav
      val isRiff =
        wav[0] == 'R'.code.toByte() && wav[1] == 'I'.code.toByte() && wav[2] == 'F'.code.toByte() && wav[3] == 'F'.code.toByte()
      val isWave =
        wav[8] == 'W'.code.toByte() && wav[9] == 'A'.code.toByte() && wav[10] == 'V'.code.toByte() && wav[11] == 'E'.code.toByte()
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

    @JvmStatic
    @JvmOverloads
    fun silence(
      durationSeconds: Double,
      format: String = "wav",
      sampleRate: Int = 24000,
      channels: Int = 1,
      bitsPerSample: Int = 16,
    ): AudioSegment {
      require(durationSeconds >= 0.0) { "durationSeconds must be non-negative: $durationSeconds" }
      require(sampleRate > 0) { "sampleRate must be positive: $sampleRate" }
      require(channels > 0) { "channels must be positive: $channels" }
      require(bitsPerSample > 0 && bitsPerSample % 8 == 0) {
        "bitsPerSample must be a positive multiple of 8: $bitsPerSample"
      }
      val bytesPerSample = bitsPerSample / 8
      val totalSamples = Math.round(durationSeconds * sampleRate).toInt()
      val pcmSize = totalSamples * channels * bytesPerSample
      val pcm = ByteArray(pcmSize) // zero-filled by default
      val bytes = when (format.lowercase()) {
        "wav" -> pcmToWav(pcm, sampleRate, channels, bitsPerSample)
        else -> pcm
      }
      return AudioSegment(
        format = format,
        sampleRate = sampleRate,
        channels = channels,
        bitsPerSample = bitsPerSample,
      ).also { it.audioBytes = bytes }
    }
  }
}