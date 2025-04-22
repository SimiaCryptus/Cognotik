package com.simiacryptus.jopenai.models
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

@Suppress("unused")
enum class AudioModels(
    override val modelName: String,
    val type : AudioModelType,
) : OpenAIModel {
    GPT4oTranscribe("gpt-4o-transcribe", AudioModelType.Transcription),
    GPT4oMiniTranscribe("gpt-4o-mini-transcribe", AudioModelType.Transcription),
    Whisper("whisper-1", AudioModelType.Transcription),
    TTS("tts-1", AudioModelType.TextToSpeech),
    TTS_HD("tts-1-hd", AudioModelType.TextToSpeech),
    GPT4oMiniTTS("gpt-4o-mini-tts", AudioModelType.TextToSpeech)
    ;

    private val _api = AtomicReference<OpenAIModel?>(null)
    private val log = LoggerFactory.getLogger(AudioModels::class.java)

    enum class AudioModelType {
        Transcription,
        TextToSpeech,
    }
    
    fun pricing(length: Int): Double = when (this) {
        Whisper -> 0.006 * length // seconds
        TTS -> (15.0 / 1000000) * length // characters ($15 per 1M characters)
        TTS_HD -> (30.0 / 1000000) * length // characters ($30 per 1M characters)
        GPT4oTranscribe -> 0.006 * length // minutes ($0.006 per minute)
        GPT4oMiniTranscribe -> 0.003 * length // minutes ($0.003 per minute)
        GPT4oMiniTTS -> (0.60 / 1000000) * length // characters
    }
    .also { log.info("Calculated price: {}", it) }
    companion object {
        @OptIn(ExperimentalStdlibApi::class)
        fun find(modelName: String?): AudioModels? {
            return entries.find { it.modelName == modelName }
        }
    }
}