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