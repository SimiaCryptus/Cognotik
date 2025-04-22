package com.simiacryptus.jopenai.audio

import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.AudioModels
import org.slf4j.LoggerFactory
import java.util.*
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.swing.JOptionPane

open class DictationManager {
    companion object : DictationManager() {
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
    var transcriptionModel: AudioModels = AudioModels.Whisper

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
                    client = OpenAIClient(),
                    audioBuffer = processedBuffer,
                    model = transcriptionModel,
                    continueFn = { isRecording },
                    prompt = "",
                    onTranscriptionUpdate = onTranscriptionUpdate
                ).apply {
                    run()
                }
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