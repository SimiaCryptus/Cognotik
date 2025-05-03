package com.simiacryptus.jopenai.audio

import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import kotlin.math.*

data class AudioPacket(
    val samples: FloatArray,
    val audioFormat: AudioFormat,
    val createdOn: Long = System.currentTimeMillis()
) {
    private val log = LoggerFactory.getLogger(AudioPacket::class.java)
    val duration: Double by lazy { samples.size.toDouble() / audioFormat.sampleRate }
    private val fft: FloatArray by lazy { fft(samples) }
    val rms: Double by lazy { rms(samples).toDouble() }
    val size: Int by lazy { samples.size }
    val spectralEntropy: Double by lazy { spectralEntropy(fft) }
    val iec61672 get() = aWeighting
    val spectralCentroid: Double by lazy { spectralCentroid(fft, audioFormat.sampleRate.toDouble()) }
    val spectralFlatness: Double by lazy { spectralFlatness(fft) }

    fun frequencyBandPower(minFreq: Double, maxFreq: Double): Double {
        val minIndex = (minFreq * fft.size / audioFormat.sampleRate).toInt().coerceIn(0, fft.size - 1)
        val maxIndex = (maxFreq * fft.size / audioFormat.sampleRate).toInt().coerceIn(0, fft.size - 1)
        return if (minIndex >= maxIndex) {
            0.0
        } else {
            val bandPower = (minIndex until maxIndex).sumOf { i ->
                when {
                    i == 0 -> fft[0].pow(2).toDouble()
                    i == fft.size / 2 -> fft[1].pow(2).toDouble()
                    else -> {
                        val real = fft[i]
                        val imag = fft[fft.size - i]
                        (real.pow(2) + imag.pow(2)).toDouble()
                    }
                }
            }
            bandPower / (maxIndex - minIndex)

        }
    }

    @Suppress("unused")
    val zeroCrossings: Int by lazy {
        log.trace("Calculating zero crossings")
        samples.toList().windowed(2).count { (a, b) -> a > 0 && b < 0 || a < 0 && b > 0 }
    }

    /**
     * Calculates the spectral centroid of the FFT result.
     *
     * @param fft The FFT result.
     * @param sampleRate The sample rate of the audio.
     * @return The spectral centroid in Hz.
     */
    private fun spectralCentroid(fft: FloatArray, sampleRate: Double): Double {
        log.trace("Calculating spectral centroid")
        val magnitudes = fft.map { (it * it).toDouble() }.toDoubleArray()
        val frequencies = magnitudes.indices.map { it * sampleRate / fft.size }.toDoubleArray()
        val sumMagnitudes = magnitudes.sum()
        if (sumMagnitudes == 0.0) return 0.0
        val centroid = magnitudes.zip(frequencies) { mag, freq -> mag * freq }.sum() / sumMagnitudes
        return centroid
    }

    /**
     * Calculates the spectral flatness of the FFT result.
     *
     * @param fft The FFT result.
     * @return The spectral flatness (0.0 to 1.0).
     */
    private fun spectralFlatness(fft: FloatArray): Double {
        log.trace("Calculating spectral flatness")
        val magnitudes = fft.map { it.absoluteValue.toDouble() + 1e-12 }

        val logMean = magnitudes.map { log10(it) }.average()
        val mean = magnitudes.average()
        return 10.0.pow(logMean) / mean
    }

    val aWeighting: Double by lazy {
        log.trace("Calculating A-weighting based on IEC 61672")
        val aWeightingFilter = aWeightingFilter(fft, audioFormat.sampleRate.toInt())
        val weightedPower = aWeightingFilter.map { it * it }.average()
        weightedPower
    }

    @Suppress("unused")
    fun spectrumWindowPower(minFrequency: Double, maxFrequency: Double): Double {
        log.trace("Calculating spectrum window power for frequencies between {} and {}", minFrequency, maxFrequency)
        val minIndex = (samples.size * minFrequency / audioFormat.sampleRate).toInt()
        val maxIndex = (samples.size * maxFrequency / audioFormat.sampleRate).toInt()
        return fft.sliceArray(minIndex until maxIndex).map { it * it }.average()
    }

    private fun aWeightingFilter(
        fft: FloatArray,
        sampleRate: Int
    ): FloatArray {
        log.trace("Applying A-weighting filter")
        val aWeightingFilter = FloatArray(fft.size) { 0f }

        val a0 = 12200.0f.pow(2)
        val a1 = 20.6f.pow(2)
        val a2 = 107.7f.pow(2)
        val a3 = 737.9f.pow(2)
        for (i in fft.indices) {
            val frequency = i * sampleRate.toFloat() / fft.size
            val numerator = a0 * frequency.pow(4)
            val denominator = (frequency.pow(2) + a1) *
                    sqrt((frequency.pow(2) + a2) * (frequency.pow(2) + a3)) *
                    (frequency.pow(2) + a0)

            val aWeight = if (denominator != 0f) numerator / denominator else 0f
            aWeightingFilter[i] = fft[i] * aWeight
        }
        return aWeightingFilter
    }

    operator fun plus(packet: AudioPacket): AudioPacket {
        log.trace("Combining audio packets")
        return AudioPacket(this.samples + packet.samples, audioFormat, createdOn.coerceAtMost(packet.createdOn))
    }

    override fun toString(): String {
        return "AudioPacket(createdOn=$createdOn, audioFormat=$audioFormat)"
    }

    companion object {
        private val log = LoggerFactory.getLogger(AudioPacket::class.java)

        fun convertRawToWav(audio: ByteArray, audioFormat: AudioFormat): ByteArray? {
            Companion.log.trace("Converting raw audio to WAV format")

            AudioInputStream(
                ByteArrayInputStream(audio),
                audioFormat,
                audio.size.toLong()
            ).use { audioInputStream ->

                val wavBuffer = ByteArrayOutputStream()

                AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, wavBuffer)

                return wavBuffer.toByteArray()
            }
        }

        fun convertRaw(audio: ByteArray, audioFormat: AudioFormat): FloatArray {
            Companion.log.trace("Converting raw audio bytes to float array")

            val byteArrayInputStream = ByteArrayInputStream(audio)

            val audioInputStream =
                AudioInputStream(byteArrayInputStream, audioFormat, audio.size.toLong())

            val audioFloatInputStream =
                AudioSystem.getAudioInputStream(AudioFormat.Encoding.PCM_SIGNED, audioInputStream)

            val samples = audioFloatInputStream.readAllBytes()

            val sum = (samples.indices step 2).map { i ->

                val r = samples[i].toInt()
                val l = samples[i + 1].toInt()
                val sample = ((r and 0xff) or ((l and 0xff) shl 8)).toDouble() / 32768.0

                (sample * sample).toFloat()
            }.toTypedArray()
            return sum.toFloatArray()
        }

        /**
         * Calculates the spectral entropy of the given float array representing audio samples.
         *
         * @param floats The audio samples.
         * @return The spectral entropy value.
         */
        fun spectralEntropy(floats: FloatArray): Double {
            log.trace("Calculating spectral entropy")

            val fftResult = fft(floats)
            val fftSize = fftResult.size / 2

            val powerSpectrum = FloatArray(fftSize + 1) { i ->
                when (i) {
                    0 -> fftResult[0].pow(2)

                    fftSize -> fftResult[1].pow(2)

                    else -> {
                        val real = fftResult[i]
                        val imag = fftResult[fftResult.size - i]
                        real.pow(2) + imag.pow(2)
                    }
                }
            }

            val sum = powerSpectrum.sum().toDouble()
            if (sum == 0.0) return 0.0


            val entropy = powerSpectrum.map { it.toDouble() / sum }
                .filter { it > 0.0 }
                .sumOf { -it * ln(it) }

            return entropy
        }

        fun rms(samples: FloatArray): Float = sqrt(samples.map { it * it }.sum() / samples.size)

        fun convertFloatsToRaw(audio: FloatArray): ByteArray {
            log.trace("Converting float array to raw audio bytes")
            val byteArray = ByteArray(audio.size * 2)

            for (i in audio.indices) {

                val sample = (audio[i] * 32768.0).toInt()

                val r = (sample and 0xff).toByte()
                val l = ((sample shr 8) and 0xff).toByte()

                byteArray[i * 2] = r
                byteArray[i * 2 + 1] = l
            }

            return byteArray
        }

        fun fft(input: FloatArray): FloatArray {
            log.trace("Performing FFT")
            val output = input.copyOf(input.size)
            val fft = FloatFFT_1D(output.size)
            fft.realForward(output)
            return output
        }

        fun empty(): AudioPacket {
            return AudioPacket(floatArrayOf(), AudioFormat(0f, 0, 0, true, false))
        }
    }

}