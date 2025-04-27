package com.simiacryptus.jopenai.audio

import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.models.AudioModels
import org.slf4j.LoggerFactory
import java.util.*

open class TranscriptionProcessor(
    var client: OpenAIClient,
    private var audioBuffer: Queue<AudioPacket>,
    var model: AudioModels = AudioModels.Whisper,
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
                    |Audio duration: ${recordAudio.samples.size / recordAudio.audioFormat.sampleRate.toFloat()}s
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