# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/AudioModels.kt

```
package com.simiacryptus.cognotik.audio

import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

@Suppress("unused")
class AudioModels(
    override val modelName: String,
    val type: AudioModelType = AudioModelType.Transcription,
    override val provider: APIProvider = APIProvider.OpenAI,
) : AIModel {

    private val _api = AtomicReference<AIModel?>(null)

    enum class AudioModelType {
        Transcription,
        TextToSpeech,
    }

    companion object {
        private val log = LoggerFactory.getLogger(AudioModels::class.java)
    }

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/AudioPacket.kt

```
package com.simiacryptus.cognotik.audio

import com.simiacryptus.cognotik.util.LoggerFactory
import edu.emory.mathcs.jtransforms.fft.FloatFFT_1D
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioPacket

        if (createdOn != other.createdOn) return false
        if (!samples.contentEquals(other.samples)) return false
        if (audioFormat != other.audioFormat) return false
        if (log != other.log) return false
        if (duration != other.duration) return false
        if (rms != other.rms) return false
        if (size != other.size) return false
        if (spectralEntropy != other.spectralEntropy) return false
        if (iec61672 != other.iec61672) return false
        if (spectralCentroid != other.spectralCentroid) return false
        if (spectralFlatness != other.spectralFlatness) return false
        if (zeroCrossings != other.zeroCrossings) return false
        if (aWeighting != other.aWeighting) return false
        if (!fft.contentEquals(other.fft)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = createdOn.hashCode()
        result = 31 * result + samples.contentHashCode()
        result = 31 * result + audioFormat.hashCode()
        result = 31 * result + log.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + rms.hashCode()
        result = 31 * result + size
        result = 31 * result + spectralEntropy.hashCode()
        result = 31 * result + iec61672.hashCode()
        result = 31 * result + spectralCentroid.hashCode()
        result = 31 * result + spectralFlatness.hashCode()
        result = 31 * result + zeroCrossings
        result = 31 * result + aWeighting.hashCode()
        result = 31 * result + fft.contentHashCode()
        return result
    }

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/AudioRecorder.kt

```
package com.simiacryptus.cognotik.audio

import com.simiacryptus.cognotik.util.LoggerFactory
import org.apache.commons.io.input.buffer.CircularByteBuffer
import java.util.*
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

open class AudioRecorder(
    val audioBuffer: Queue<AudioPacket> = LinkedList(),
    val msPerPacket: Long = 100,
    val continueFn: () -> Boolean = { true },
    val selectedMicLine: String? = null,
    val audioFormat: AudioFormat = AudioFormat(16000f, 16, 1, true, false),
) {
    private var totalBytesRecorded: Long = 0L
    private var startTimeNanos: Long = System.nanoTime()

    fun run() {
        val targetDataLine = openMic()
        val audioFormat = targetDataLine.format
        val packetLength = (audioFormat.frameRate * audioFormat.frameSize * (msPerPacket / 1000.0)).toInt()
        try {
            log.info("Audio recording started with packet length: {} bytes", packetLength)
            val buffer = ByteArray(packetLength)
            val circularBuffer = CircularByteBuffer(packetLength * 2)
            startTimeNanos = System.nanoTime()
            while (continueFn()) {
                try {
                    var bytesRead = 0
                    val endTime = System.currentTimeMillis() + msPerPacket
                    while (bytesRead != -1 && System.currentTimeMillis() < endTime) {
                        bytesRead = targetDataLine.read(buffer, 0, buffer.size)
                        circularBuffer.add(buffer, 0, bytesRead)
                        totalBytesRecorded += bytesRead
                        while (circularBuffer.currentNumberOfBytes >= packetLength) {
                            val array = ByteArray(packetLength)
                            circularBuffer.read(array, 0, packetLength)
                            val chunkStartNanos = startTimeNanos +
                                    ((totalBytesRecorded.toDouble() * 1_000_000_000.0) /
                                            (audioFormat.frameRate * audioFormat.frameSize)).toLong()
                            val chunkStartMillis = chunkStartNanos / 1_000_000
                            audioBuffer.add(
                                AudioPacket(
                                    AudioPacket.convertRaw(array, audioFormat),
                                    audioFormat,
                                    chunkStartMillis
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error during audio recording", e)
                }
            }
            log.info("Audio recording stopped, final buffer size: {}", audioBuffer.size)
        } finally {
            targetDataLine.close()
            log.info("Microphone line closed")
        }
    }

    open fun openMic(): TargetDataLine {
        val mixerInfo = AudioSystem.getMixerInfo()
        val micLine = (selectedMicLine ?: "Microphone").let { selectedMicLine ->
            mixerInfo.firstOrNull { it.toString().contains(selectedMicLine, true) }
        }
            ?: throw IllegalStateException("No microphone line found; available lines: ${mixerInfo.joinToString { it.name }}, selected: $selectedMicLine")
        try {
            val targetLineInfo = AudioSystem.getMixer(micLine).lineInfo
            log.info(" Audio Mixer Target Line: $targetLineInfo (${targetLineInfo.javaClass.canonicalName})")
            if (targetLineInfo is DataLine.Info) {
                targetLineInfo.formats.forEach { format ->
                    log.info("  Audio Mixer Target Line Format: $format; Channels: ${format.channels}; Sample Rate: ${format.sampleRate}")
                }
            }
        } catch (e: Exception) {
            log.error("Error getting audio mixer target line", e)
        }
        return AudioSystem.getTargetDataLine(audioFormat, micLine).apply {
            open(audioFormat)
            start()
            log.info("Microphone line opened with format: {}", audioFormat)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AudioRecorder::class.java)

    }

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/DictationManager.kt

```
package com.simiacryptus.cognotik.audio

import com.simiacryptus.cognotik.TranscriptionClient
import com.simiacryptus.cognotik.util.LoggerFactory
import java.util.*
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.swing.JOptionPane

abstract class DictationManager {
    companion object {
        private val log = LoggerFactory.getLogger(DictationManager::class.java)
    }

    val availableMicLines: List<String>
        get() {
            return AudioSystem.getMixerInfo().filter {
                val mixer = AudioSystem.getMixer(it)
                !mixer.targetLineInfo.isNullOrEmpty()
            }.map { it.toString() }.toList()
        }

    var selectedMicLine: String? = null
    var transcriptionProcessor: TranscriptionProcessor? = null
    var transcriptionModel: AudioModels? = null

    var audioFormat: AudioFormat = AudioFormat(16000f, 16, 1, true, false)
        set(value) {
            field = value
        }
    private var isRecording = false
    private var recordingStartTime: Long = 0
    private val audioBuffer: Queue<AudioPacket> = LinkedList()
    private val processedBuffer: Queue<AudioPacket> = LinkedList()
    private var recorder: Thread? = null
    private var processor: Thread? = null
    private var windowBuffer: Thread? = null

    val discriminator = TrainedSilenceDiscriminator(
        inputBuffer = audioBuffer,
        outputBuffer = processedBuffer,
        onPacket = { handlePacket(it) },
        continueFn = { isRecording },
    )

    var handlePacket: (AudioPacket) -> Unit = {
        log.trace("Packet received: ${it.samples.size} samples")
    }

    var onException: (java.lang.Exception) -> Unit = { log.error("Error during recording", it) }
    var msPerPacket: Long = 100

    @Suppress("LongParameterList")
    fun startRecording() {
        try {
            isRecording = true
            audioBuffer.clear()
            processedBuffer.clear()
            recordingStartTime = System.currentTimeMillis()
            recorder = Thread {
                try {
                    AudioRecorder(
                        audioBuffer = audioBuffer,
                        msPerPacket = msPerPacket,
                        continueFn = { isRecording },
                        selectedMicLine = this.selectedMicLine,
                        audioFormat = audioFormat
                    ).run()
                } catch (e: Exception) {
                    onException(e)
                }
            }.apply { start() }
            windowBuffer = Thread {
                log.info("Starting WindowBuffer processing loop.")
                while (discriminator.let { it.shouldContinue(it.inputBuffer) } == true) {
                    discriminator.let { it.poll(it.inputBuffer) }
                }
                log.info("WindowBuffer processing loop ended.")
            }.apply {
                start()
            }
            processor = Thread {
                transcriptionProcessor = TranscriptionProcessor(
                    client = transcriptionClient(),
                    audioBuffer = processedBuffer,
                    model = transcriptionModel!!,
                    continueFn = { isRecording },
                    prompt = "",
                    onTranscriptionUpdate = onTranscriptionUpdate
                ).apply { run() }
            }.apply { start() }
        } catch (e: Exception) {
            JOptionPane.showMessageDialog(
                null,
                "Failed to start recording: ${e.message}",
                "Speech-to-Text Error",
                JOptionPane.ERROR_MESSAGE
            )
            stopRecording()
        }
    }

    abstract fun transcriptionClient(): TranscriptionClient

    var onTranscriptionUpdate: (TranscriptionProcessor.TranscriptionResult) -> Unit = {
        log.info("Transcription: ${it.text}")
    }

    fun stopRecording() {
        isRecording = false
        recorder?.join()
        windowBuffer?.join()
        processor?.join()
        recorder = null
        recordingStartTime = 0
        windowBuffer = null
        processor = null
        log.info("Recording stopped")
    }

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/PercentileTool.kt

```
package com.simiacryptus.cognotik.audio

import kotlin.math.absoluteValue
import kotlin.math.ln
import kotlin.math.log
import kotlin.math.max

class PercentileTool(
    val memorySize: Int = 10000
) {
    companion object {

        /**
         * Compute the KL-divergence between two sorted lists of values.
         */
        fun computeKLDivergence(a: DoubleArray, b: DoubleArray): Double {
            var klDiv = 0.0
            val maxValue = max(a.lastOrNull() ?: 0.0, b.lastOrNull() ?: 0.0)
            if (maxValue == 0.0) return 0.0
            val aList = a.map { it / maxValue }.toMutableList()
            val bList = b.map { it / maxValue }.toMutableList()
            while (aList.isNotEmpty() && bList.isNotEmpty()) {
                val aV = aList.first()
                val bV = bList.first()
                when {
                    aV < bV -> {
                        klDiv += if (aV > 0.0 && bV > 0.0) aV * ln(aV / bV) else 0.0
                        aList.removeAt(0)
                    }

                    else -> {
                        klDiv += if (aV > 0.0 && bV > 0.0) bV * ln(bV / aV) else 0.0
                        bList.removeAt(0)
                    }
                }
            }
            return klDiv / (a.size + b.size)
        }
    }

    internal var memory = ArrayList<Double>()
        private set

    /**
     * Add a value to the internal memory in a threadsafe manner.
     * Ensures that the list remains sorted and does not exceed memorySize.
     */
    @Synchronized

    fun add(value: Double) {

        val index = memory.binarySearch(value).let { if (it < 0) -it - 1 else it }
        memory.add(index, value)

        if (memory.size > memorySize) {
            val newSize = memorySize / 2
            val newMemory = ArrayList<Double>(newSize)
            for (i in 0 until memory.size step 2) {
                newMemory.add(memory[i])
            }
            memory = newMemory
        }
    }

    @Synchronized

    fun getValueOfPercentile(percentile: Double): Double {
        if (memory.isEmpty()) return 0.0
        val index = (percentile * memory.size).toInt().coerceIn(0, memory.size - 1)
        return memory[index]
    }

    fun getPercentileOfValue(value: Double): Double {
        if (memory.isEmpty()) return 0.0
        val idx = memory.binarySearch(value).let { if (it < 0) -it - 1 else it }
        return idx.toDouble() / memory.size
    }

    fun getDensityOfValue(value: Double): Double {
        if (memory.isEmpty()) return 0.0
        if (value < memory.first() || value > memory.last()) return 0.0
        val index = memory.binarySearch(value).let { if (it < 0) -it - 1 else it }.coerceIn(0, memory.size - 1)
        val neighborIndex = (if (index >= memory.size - 1) memory.size - 2 else index + 1).coerceIn(0, memory.size - 1)
        return 1.0 / ((memory[neighborIndex] - memory[index]).absoluteValue * memory.size.toDouble())
    }

    fun getDistanceFromBounds(value: Double): Double {
        if (memory.isEmpty()) return 0.0
        if (value < memory.first()) return memory.first() - value
        if (value > memory.last()) return value - memory.last()
        return 0.0
    }

    fun findEntropyThreshold5(percentileBias: Double = 0.0): Double {
        if (memory.size < 2) return 0.0
        if (memory.first() == memory.last()) {
            return memory.first()
        }

        var bestThreshold = memory[0]
        var bestIndex = -1
        var maxJS = Double.NEGATIVE_INFINITY
        val min = memory.first()
        val max = memory.last()

        fun jensenShannonDivergence(p: Double, q: Double): Double {

            if (p <= 0.0 || p >= 1.0 || q <= 0.0 || q >= 1.0) return Double.NEGATIVE_INFINITY
            val m = (p + q) / 2
            val p1 = 1 - p
            val q1 = 1 - q
            val m1 = (p1 + q1) / 2
            return 0.5 * (
                    p * log(p / m, 2.0) +
                            p1 * log(p1 / m1, 2.0)
                    ) + 0.5 * (
                    q * log(q / m, 2.0) +
                            q1 * log(q1 / m1, 2.0)
                    )
        }

        for (i in 1 until memory.size) {
            if (memory[i] == memory[i - 1]) continue
            val threshold = (memory[i - 1] + memory[i]) / 2
            if (threshold <= min || threshold >= max) continue

            val fractionOfValues = i.toDouble() / memory.size
            val fractionOfRange = (threshold - min) / (max - min)

            if (fractionOfValues <= 0.0 || fractionOfRange <= 0.0) continue
            if (fractionOfValues >= 1.0 || fractionOfRange >= 1.0) break

            val js = jensenShannonDivergence(fractionOfValues, fractionOfRange)

            if (js > maxJS) {
                maxJS = js
                bestThreshold = threshold
                bestIndex = i
            }
        }

        if (percentileBias != 0.0 && bestIndex != -1) {
            val percentileIndex = (bestIndex + (percentileBias * memory.size)).toInt().coerceIn(0, memory.size - 1)
            if (percentileIndex != bestIndex) {
                bestThreshold = memory[percentileIndex]
            }
        }

        return bestThreshold
    }

    fun isEmpty(): Boolean {
        return memory.isEmpty()
    }

    fun clear() {
        memory.clear()
    }

    fun computeKLDivergence(other: PercentileTool): Double {
        return computeKLDivergence(memory.toDoubleArray(), other.memory.toDoubleArray())
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/SilenceDiscriminator.kt

```
package com.simiacryptus.cognotik.audio

import com.simiacryptus.cognotik.util.EventDispatcher
import com.simiacryptus.cognotik.util.LoggerFactory
import java.util.*

enum class AudioState {
    TALKING,
    QUIET
}

abstract class SilenceDiscriminator(
    val inputBuffer: Queue<AudioPacket>,
    val outputBuffer: Queue<AudioPacket>,
    val onPacket: (AudioPacket) -> Unit,
    val continueFn: () -> Boolean,
) {
    var requiredQuietWindowsForTransition = 5
    var requiredTalkWindowsForTransition = 3
    var currentState = AudioState.QUIET
    var minTalkTime = 2.0
    val onModeChanged = EventDispatcher()
    val talkTime: Double
        @Synchronized
        get() = outputPacketBuffer.sumOf { it.duration }

    private var consecutiveQuietWindows = 0
    private var consecutiveTalkWindows = 0
    private var outputPacketBuffer = ArrayList<AudioPacket>()
    private var lastOutputBuffer: ArrayList<AudioPacket>? = null

    fun run() {
        while (shouldContinue(inputBuffer)) {
            try {
                poll(inputBuffer)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                log.warn("Polling thread was interrupted.", e)
                break
            }
        }
    }

    fun poll(audioPackets: Queue<AudioPacket>) {
        val audioPacket = audioPackets.poll()
        if (null == audioPacket) {
            Thread.sleep(10)

        } else {
            processPacket(audioPacket)
        }
    }

    fun shouldContinue(audioPackets: Queue<AudioPacket>): Boolean = this.continueFn() || audioPackets.isNotEmpty()

    protected open fun processPacket(packet: AudioPacket) {
        when (currentState) {
            AudioState.QUIET -> {
                if (!isQuiet(packet)) {
                    consecutiveQuietWindows = 0
                    if (consecutiveTalkWindows++ >= requiredTalkWindowsForTransition) {
                        log.debug("State transition: QUIET -> TALKING")
                        currentState = AudioState.TALKING
                        onModeChanged.notifyListeners()
                    }
                } else {
                    consecutiveTalkWindows = 0
                    outputPacketBuffer.clear()
                }
                synchronized(this.outputPacketBuffer) {
                    this.outputPacketBuffer.add(packet)
                }
            }

            AudioState.TALKING -> {
                synchronized(this.outputPacketBuffer) {
                    this.outputPacketBuffer.add(packet)
                }
                if (isQuiet(packet)) {
                    consecutiveTalkWindows = 0
                    if (consecutiveQuietWindows++ >= requiredQuietWindowsForTransition) {
                        log.debug("State transition: TALKING -> QUIET")
                        currentState = AudioState.QUIET
                        onModeChanged.notifyListeners()
                        val reduced = flushOutput()
                        if (reduced.duration > minTalkTime) {
                            log.debug("Outputting packet size: ${reduced.duration}.")
                            outputBuffer.add(reduced)
                        } else {
                            log.debug("Ignoring packet size: ${reduced.duration}.")
                        }
                    }
                } else {
                    consecutiveQuietWindows = 0
                }
            }
        }
        onPacket(packet)
    }

    protected abstract fun isQuiet(vararg packets: AudioPacket): Boolean

    private fun flushOutput(): AudioPacket {
        val reduced = if (outputPacketBuffer.isNotEmpty()) {
            outputPacketBuffer.reduce { a, b -> a + b }
        } else {
            AudioPacket.empty()
        }
        lastOutputBuffer = outputPacketBuffer
        outputPacketBuffer = ArrayList()
        log.debug("Flushing output buffer: ${reduced.duration}")
        return reduced
    }

    fun clearMemory() {
        lastOutputBuffer = outputPacketBuffer
        outputPacketBuffer = ArrayList()
    }

    companion object {
        private val log = LoggerFactory.getLogger(SilenceDiscriminator::class.java)
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/TrainedSilenceDiscriminator.kt

```
package com.simiacryptus.cognotik.audio

import com.simiacryptus.cognotik.util.LoggerFactory
import java.util.*
import kotlin.math.ln

open class TrainedSilenceDiscriminator(
    inputBuffer: Queue<AudioPacket>,
    outputBuffer: Queue<AudioPacket>,
    onPacket: (AudioPacket) -> Unit,
    continueFn: () -> Boolean,
    var isVerbose: Boolean = false,
    val frequencyBands: List<Pair<Double, Double>> = listOf(
        85.0 to 255.0,

        256.0 to 2000.0,

        2001.0 to 8000.0

    )
) : SilenceDiscriminator(
    inputBuffer = inputBuffer,
    outputBuffer = outputBuffer,
    onPacket = onPacket,
    continueFn = continueFn
) {

    init {
        onModeChanged.addListener {

            if (isTraining != null && silence.rmsPercentileTool.memory.size > 1 && speech.rmsPercentileTool.memory.size > 1) {
                logKL()
            }
        }
    }

    protected open fun newPercentile() = PercentileTool(10000)
    protected open fun PercentileTool.getCurrentRMSThreshold() = findEntropyThreshold5(0.0)
    inner class Statistics {
        val rmsPercentileTool by lazy { newPercentile() }
        val iec61672PercentileTool by lazy { newPercentile() }
        val spectralEntropyPercentileTool by lazy { newPercentile() }
        val spectralCentroidPercentileTool by lazy { newPercentile() }
        val spectralFlatnessPercentileTool by lazy { newPercentile() }

        val frequencyBandTools by lazy { frequencyBands.map { newPercentile() } }

        fun isQuiet(vararg packets: AudioPacket): Double {
            return packets.map { packet ->
                (listOf(
                    packet.rms < rmsPercentileTool.getCurrentRMSThreshold(),
                    packet.aWeighting < iec61672PercentileTool.getCurrentRMSThreshold(),
                    packet.spectralEntropy < spectralEntropyPercentileTool.getCurrentRMSThreshold(),
                ) + frequencyBands.mapIndexed { index, (low, high) ->
                    packet.frequencyBandPower(low, high) < frequencyBandTools[index].getCurrentRMSThreshold()
                }).map { if (it) 1.0 else 0.0 }.average()
            }.average()
        }

        fun isEmpty() =
            rmsPercentileTool.isEmpty() ||
                    iec61672PercentileTool.isEmpty() ||
                    spectralEntropyPercentileTool.isEmpty() ||
                    spectralCentroidPercentileTool.isEmpty() ||
                    spectralFlatnessPercentileTool.isEmpty() ||
                    frequencyBandTools.any { it.isEmpty() }
    }

    private val silence = Statistics()
    private val speech = Statistics()
    private val unknown = Statistics()
    var isTraining: Boolean? = false
        set(value) {
            if (field != value) {
                field = value
                if (silence.rmsPercentileTool.memory.size > 1 && speech.rmsPercentileTool.memory.size > 1) {
                    logKL()
                }
                if (value == null) {

                }
            }
        }

    private fun logKL() {
        log.info("KL-Divergence Update:")
        log.info("RMS KL-Divergence: ${silence.rmsPercentileTool.computeKLDivergence(speech.rmsPercentileTool)}")
        log.info("IEC61672 KL-Divergence: ${silence.iec61672PercentileTool.computeKLDivergence(speech.iec61672PercentileTool)}")
        log.info(
            "Spectral Entropy KL-Divergence: ${
                silence.spectralEntropyPercentileTool.computeKLDivergence(
                    speech.spectralEntropyPercentileTool
                )
            }"
        )
        log.info(
            "Spectral Centroid KL-Divergence: ${
                silence.spectralCentroidPercentileTool.computeKLDivergence(
                    speech.spectralCentroidPercentileTool
                )
            }"
        )
        log.info(
            "Spectral Flatness KL-Divergence: ${
                silence.spectralFlatnessPercentileTool.computeKLDivergence(
                    speech.spectralFlatnessPercentileTool
                )
            }"
        )
        frequencyBands.forEachIndexed { index, (low, high) ->
            log.info(
                "Frequency Band ${index + 1} (${low}-${high}Hz) KL-Divergence: " +
                        "${silence.frequencyBandTools[index].computeKLDivergence(speech.frequencyBandTools[index])}"
            )
        }
    }

    var bias: Double = -0.1

    override fun processPacket(packet: AudioPacket) {
        when (isTraining) {
            true -> speech
            false -> silence
            else -> unknown
        }.let { statistics ->
            statistics.rmsPercentileTool.add(packet.rms)
            statistics.iec61672PercentileTool.add(packet.aWeighting)
            statistics.spectralEntropyPercentileTool.add(packet.spectralEntropy)
            statistics.spectralCentroidPercentileTool.add(packet.spectralCentroid)
            statistics.spectralFlatnessPercentileTool.add(packet.spectralFlatness)
            frequencyBands.forEachIndexed { index, (low, high) ->
                statistics.frequencyBandTools[index].add(packet.frequencyBandPower(low, high))
            }
        }
        logPacket(packet)
        super.processPacket(packet)
    }

    override fun isQuiet(vararg packets: AudioPacket): Boolean {
        if (silence.isEmpty() || speech.isEmpty()) return unknown.isQuiet(*packets) < 0.5
        if (isTraining != null) return !isTraining!!
        return packets.map { computeQuietMetrics(it).average() > bias }.all { it }
    }

    protected open fun compare(v: Double, a: PercentileTool, b: PercentileTool): Double {
        val vB = b.getDensityOfValue(v)
        val vA = a.getDensityOfValue(v)
        return when {
            vB == 0.0 || vA == 0.0 -> {
                val pA = a.getPercentileOfValue(v)
                val pB = b.getPercentileOfValue(v)
                when {
                    pA == 1.0 && pB == 0.0 -> {
                        val dA = a.getDistanceFromBounds(v)
                        val dB = b.getDistanceFromBounds(v)
                        if (dA < dB) 1.0 else -1.0
                    }

                    pA == 1.0 -> -1.0
                    pB == 0.0 -> 1.0
                    else -> (pA - pB)
                }
            }

            else -> ln(vA / vB)
        }
    }

    private fun computeQuietMetrics(packet: AudioPacket) = listOf(
        compare(packet.rms, silence.rmsPercentileTool, speech.rmsPercentileTool),
        compare(packet.aWeighting, silence.iec61672PercentileTool, speech.iec61672PercentileTool),
        compare(packet.spectralEntropy, silence.spectralEntropyPercentileTool, speech.spectralEntropyPercentileTool),


        ) + frequencyBands.mapIndexed { index, (low, high) ->
        compare(
            packet.frequencyBandPower(low, high),
            silence.frequencyBandTools[index],
            speech.frequencyBandTools[index]
        )
    }

    private fun logPacket(packet: AudioPacket) {
        if (isVerbose) log.debug(
            "Audio metrics comparison: ${
                listOf(
                    "RMS: ${
                        compare(
                            silence.rmsPercentileTool.getPercentileOfValue(packet.rms),
                            speech.rmsPercentileTool.getPercentileOfValue(packet.rms)
                        )
                    }",
                    "IEC61672: ${
                        compare(
                            silence.iec61672PercentileTool.getPercentileOfValue(packet.aWeighting),
                            speech.iec61672PercentileTool.getPercentileOfValue(packet.aWeighting)
                        )
                    }",
                    "Spectral Entropy: ${
                        compare(
                            silence.spectralEntropyPercentileTool.getPercentileOfValue(packet.spectralEntropy),
                            speech.spectralEntropyPercentileTool.getPercentileOfValue(packet.spectralEntropy)
                        )
                    }",
                    "Spectral Centroid: ${
                        compare(
                            speech.spectralCentroidPercentileTool.getPercentileOfValue(packet.spectralCentroid),
                            silence.spectralCentroidPercentileTool.getPercentileOfValue(packet.spectralCentroid)
                        )
                    }",
                    "Spectral Flatness: ${
                        compare(
                            speech.spectralFlatnessPercentileTool.getPercentileOfValue(packet.spectralFlatness),
                            silence.spectralFlatnessPercentileTool.getPercentileOfValue(packet.spectralFlatness)
                        )
                    }",
                    "isTalk: ${isTraining}",
                ).joinToString(" ")
            }"
        )
    }

    fun reset() {
        silence.clear()
        speech.clear()
    }

    private fun Statistics.clear() {
        rmsPercentileTool.clear()
        iec61672PercentileTool.clear()
        spectralEntropyPercentileTool.clear()
        spectralCentroidPercentileTool.clear()
        spectralFlatnessPercentileTool.clear()
        frequencyBandTools.forEach { it.clear() }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TrainedSilenceDiscriminator::class.java)
        fun compare(a: Double, b: Double) = when {
            a > b -> "${a.format("%.2f")} > ${b.format("%.2f")}"
            a < b -> "${a.format("%.2f")} < ${b.format("%.2f")}"
            else -> "${a.format("%.2f")} = ${b.format("%.2f")}"
        }
    }
}

private fun Number.format(s: String) = String.format(s, this)
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/TranscriptionProcessor.kt

```
package com.simiacryptus.cognotik.audio

import com.simiacryptus.cognotik.TranscriptionClient
import com.simiacryptus.cognotik.util.LoggerFactory
import java.util.*

open class TranscriptionProcessor(
    var client: TranscriptionClient,
    private var audioBuffer: Queue<AudioPacket>,
    var model: AudioModels,
    var continueFn: () -> Boolean,
    var prompt: String = "",
    var onTranscriptionUpdate: (TranscriptionResult) -> Unit,
    var verbose: Boolean = false
) {
    data class TranscriptionResult(
        val text: String,
        val prompt: String,
        val packet: AudioPacket,
        val processingTime: Long
    ) {
        override fun toString(): String {
            return "TranscriptionResult(text='$text', prompt='$prompt', processingTime=$processingTime)"
        }
    }

    private val log = LoggerFactory.getLogger(TranscriptionProcessor::class.java)
    fun run() {
        log.debug("TranscriptionProcessor started.")
        while (this.continueFn() || audioBuffer.isNotEmpty()) {
            val recordAudio = audioBuffer.poll()
            if (null == recordAudio) {
                Thread.sleep(1)
            } else {
                val startTime = System.currentTimeMillis()
                val text = client.transcription(
                    AudioPacket.convertRawToWav(
                        AudioPacket.convertFloatsToRaw(recordAudio.samples),
                        recordAudio.audioFormat
                    )!!,
                    prompt,
                    model
                )
                val processingTime = System.currentTimeMillis() - startTime
                val transcriptionResult = TranscriptionResult(text, prompt, recordAudio, processingTime)
                prompt = updatePrompt(text)
                onTranscriptionUpdate(transcriptionResult)
                if (verbose) log.debug(
                    """Transcription details:
                    |Text: ${transcriptionResult.text}
                    |Prompt: ${transcriptionResult.prompt}
                    |Processing time: ${transcriptionResult.processingTime}ms
                    |Audio duration: ${recordAudio.samples.size / recordAudio.audioFormat.sampleRate}s
                    |""".trimMargin()
                )
            }
        }
        log.info("TranscriptionProcessor finished.")
    }

    protected open fun updatePrompt(text: String): String {

        return prompt
    }
}
```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `com.simiacryptus.cognotik.audio` package, documenting the audio processing, silence discrimination, and transcription pipeline.

### /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/README.md
```markdown
# Audio Processing & Transcription Module

This package provides a comprehensive suite of tools for real-time audio capture, digital signal processing (DSP), adaptive silence discrimination, and AI-powered transcription.

## Core Components

### 1. Data Representation: `AudioPacket`
The `AudioPacket` is the fundamental unit of audio data. It wraps a `FloatArray` of samples and provides extensive DSP metrics:
- **RMS (Root Mean Square):** Measures the average power/volume.
- **Spectral Analysis:** Includes Spectral Entropy, Spectral Centroid, and Spectral Flatness.
- **A-Weighting (IEC 61672):** Frequency weighting that mimics human hearing sensitivity.
- **Frequency Band Power:** Ability to calculate power within specific frequency ranges (e.g., 85Hz-255Hz for human voice fundamentals).
- **FFT Integration:** Uses `FloatFFT_1D` for frequency domain transformations.
- **Format Conversion:** Utilities to convert between raw bytes, WAV format, and float arrays.

### 2. Audio Capture: `AudioRecorder`
Handles the interface with system hardware:
- **Microphone Selection:** Supports targeting specific mixer lines by name.
- **Buffering:** Uses a circular buffer to manage continuous audio streams.
- **Packetization:** Slices the incoming stream into uniform time-based packets (default 100ms) for downstream processing.

### 3. Silence Discrimination (VAD)
The module implements a sophisticated Voice Activity Detection (VAD) system:
- **`SilenceDiscriminator`:** A state machine that transitions between `TALKING` and `QUIET` states based on configurable thresholds and window counts.
- **`TrainedSilenceDiscriminator`:** An adaptive implementation that "learns" the characteristics of silence vs. speech. It uses multiple metrics (RMS, A-Weighting, Entropy, and specific frequency bands) to build statistical models.
- **`PercentileTool`:** A statistical utility used by the discriminator to track value distributions, calculate KL-Divergence between speech/silence profiles, and determine optimal entropy-based thresholds.

### 4. Transcription: `TranscriptionProcessor`
Orchestrates the interaction with AI models:
- **Client Integration:** Interfaces with `TranscriptionClient` (e.g., OpenAI Whisper).
- **Context Management:** Supports prompt injection and updates to maintain transcription continuity.
- **Asynchronous Execution:** Processes audio packets in a dedicated thread to prevent UI or capture lag.

### 5. Orchestration: `DictationManager`
An abstract base class that ties all components together into a functional pipeline:
1. **Capture:** Starts the `AudioRecorder`.
2. **Filter:** Passes packets through the `TrainedSilenceDiscriminator`.
3. **Process:** Sends identified speech segments to the `TranscriptionProcessor`.
4. **Lifecycle:** Manages thread startup/shutdown and error handling.

## Usage Patterns

### Implementing a Dictation Service
To create a specific dictation tool, extend `DictationManager` and provide a `TranscriptionClient`:

```kotlin
class MyDictationManager : DictationManager() {
    override fun transcriptionClient() = MyAIClient()
}

val manager = MyDictationManager()
manager.selectedMicLine = "Built-in Microphone"
manager.onTranscriptionUpdate = { result ->
    println("Heard: ${result.text}")
}
manager.startRecording()
```

### Statistical Learning
The `TrainedSilenceDiscriminator` can be toggled into training modes:
- `isTraining = false`: Collects statistics for background noise (silence).
- `isTraining = true`: Collects statistics for active speech.
- `isTraining = null`: Uses the learned distributions to perform real-time discrimination using a log-likelihood ratio comparison.

## Technical Specifications
- **Default Sample Rate:** 16,000 Hz (optimized for speech recognition).
- **Bit Depth:** 16-bit Signed PCM.
- **Channels:** Mono.
- **FFT Implementation:** `edu.emory.mathcs.jtransforms.fft.FloatFFT_1D`.
```

### Summary of Changes
- **New File Created**: `README.md` in the `com.simiacryptus.cognotik.audio` package directory.
- **Content**:
    - Documented the `AudioPacket` class and its DSP capabilities (RMS, FFT, Spectral metrics).
    - Explained the `AudioRecorder` and its role in hardware interfacing.
    - Detailed the Voice Activity Detection (VAD) logic, specifically the adaptive `TrainedSilenceDiscriminator` and the statistical `PercentileTool`.
    - Described the `TranscriptionProcessor` and how it handles AI model communication.
    - Provided a high-level overview of `DictationManager` as the pipeline orchestrator.
    - Included a code snippet for implementation and explained the training logic for silence detection.
- **Rationale**: This provides a central entry point for developers to understand how the audio subsystem works, how the adaptive silence detection is implemented, and how to extend the dictation capabilities.
</details>

                - <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/README.md'>/home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/README.md'>/home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/audio/README.md</a> Updated
