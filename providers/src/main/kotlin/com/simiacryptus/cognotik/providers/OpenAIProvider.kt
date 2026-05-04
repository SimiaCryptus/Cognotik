package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.audio.AudioModels
import com.simiacryptus.cognotik.chat.OpenAIChatClient
import com.simiacryptus.cognotik.chat.model.OpenAIModels
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.embedding.OpenAIEmbeddingClient
import com.simiacryptus.cognotik.embedding.OpenAIEmbeddingModels
import com.simiacryptus.cognotik.image.ImageClientInterface
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.image.OpenAIImageClient
import com.simiacryptus.cognotik.image.OpenAIImageModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OpenAIProvider : APIProvider("OpenAI", "https://api.openai.com/v1") {

  override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
    key = key,
    base = baseUrl,
    workPool = MoreExecutors.newDirectExecutorService(),
    scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
    logLevel = Level.DEBUG,
    logStreams = mutableListOf()
  ).getModels() ?: OpenAIModels.values.values.toList()

  override fun getChatClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ) = OpenAIChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    scheduledPool = scheduledPool
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