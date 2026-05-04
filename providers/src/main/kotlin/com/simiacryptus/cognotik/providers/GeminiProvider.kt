package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.GeminiSdkChatClient
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.image.GeminiImageClient
import com.simiacryptus.cognotik.image.GeminiImageModels
import com.simiacryptus.cognotik.image.ImageClientInterface
import com.simiacryptus.cognotik.image.ImageModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GeminiProvider : APIProvider("Gemini", "https://generativelanguage.googleapis.com") {
  override fun authorize(
    request: HttpRequest,
    key: String,
    apiBase: String
  ) {
  }

  override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
    key = key,
    base = baseUrl,
    workPool = MoreExecutors.newDirectExecutorService(),
    scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
    logLevel = Level.DEBUG,
    logStreams = mutableListOf()
  ).getModels() ?: GeminiModels.values.values.toList()

  override fun getChatClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ) = GeminiSdkChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
  )

  override fun getImageModels(key: SecureString, baseUrl: String): List<ImageModel> {
    return GeminiImageModels.values.values.toList()
  }

  override fun getImageClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ): ImageClientInterface = GeminiImageClient(
    apiKey = key,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
  )

}