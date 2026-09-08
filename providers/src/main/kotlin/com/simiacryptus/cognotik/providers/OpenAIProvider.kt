package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.platform.model.AudioModels
import com.simiacryptus.cognotik.chat.OpenAIChatClient
import com.simiacryptus.cognotik.platform.model.EmbeddingModel
import com.simiacryptus.cognotik.embedding.OpenAIEmbeddingClient
import com.simiacryptus.cognotik.embedding.OpenAIEmbeddingModels
import com.simiacryptus.cognotik.platform.model.ImageClientInterface
import com.simiacryptus.cognotik.platform.model.ImageModel
import com.simiacryptus.cognotik.image.OpenAIImageClient
import com.simiacryptus.cognotik.image.OpenAIImageModels
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class OpenAIProvider : APIProvider("OpenAI", "https://api.openai.com/v1") {

  override fun getChatClient(
      key: SecureString,
      workPool: ExecutorService,
      logLevel: Level,
      logStreams: MutableList<BufferedOutputStream>,
      scheduledPool: ListeningScheduledExecutorService,
      session: Session
  ) = OpenAIChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    scheduledPool = scheduledPool,
    session = session,
  )


  override fun getEmbeddingModels(key: SecureString, baseUrl: String): List<EmbeddingModel> {
    return OpenAIEmbeddingModels.values.values.toList()
  }

  override fun getEmbeddingClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ) = OpenAIEmbeddingClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
  )

  override fun getImageModels(key: SecureString, baseUrl: String): List<ImageModel> {
    return OpenAIImageModels.values.values.toList()
  }

  override fun getImageClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ): ImageClientInterface = OpenAIImageClient(
    key = key.decrypt!!,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
  )

  override fun getTranscriptionModels(
    key: SecureString,
    baseUrl: String
  ): List<AudioModels> {
    return listOf(
      AudioModels(modelId = "gpt-4o-transcribe", provider = this),
      AudioModels(modelId = "gpt-4o-mini-transcribe", provider = this),
      AudioModels(modelId = "whisper-1", provider = this)
    )
  }
}