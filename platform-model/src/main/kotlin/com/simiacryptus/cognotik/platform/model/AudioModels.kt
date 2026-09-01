package com.simiacryptus.cognotik.platform.model

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference

@Suppress("unused")
class AudioModels(
  override val modelId: String,
  val type: AudioModelType = AudioModelType.Transcription,
  override val provider: APIProvider,
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