package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.OllamaChatClient
import com.simiacryptus.cognotik.embedding.EmbeddingModel
import com.simiacryptus.cognotik.embedding.OllamaEmbeddingClient
import com.simiacryptus.cognotik.embedding.OllamaEmbeddingModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class OllamaProvider : APIProvider("Ollama", "http://localhost:11434") {

  override fun getChatClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ) = OllamaChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    scheduledPool = scheduledPool,
    logLevel = logLevel,
    logStreams = logStreams
  )

  override fun getEmbeddingModels(key: SecureString, baseUrl: String): List<EmbeddingModel> {
    return OllamaEmbeddingModels.values.values.toList()
  }

  override fun getEmbeddingClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ) = OllamaEmbeddingClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
  )
}