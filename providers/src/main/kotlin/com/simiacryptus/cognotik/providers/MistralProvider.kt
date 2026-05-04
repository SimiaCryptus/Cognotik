package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.MistralChatClient
import com.simiacryptus.cognotik.chat.model.MistralModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MistralProvider : APIProvider("Mistral", "https://api.mistral.ai/v1") {

  override fun getChatModels(key: SecureString, baseUrl: String) = getChatClient(
    key = key,
    base = baseUrl,
    workPool = MoreExecutors.newDirectExecutorService(),
    scheduledPool = MoreExecutors.listeningDecorator(Executors.newScheduledThreadPool(1)),
    logLevel = Level.DEBUG,
    logStreams = mutableListOf()
  ).getModels() ?: MistralModels.values.values.toList()

  override fun getChatClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ) = MistralChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
  )
}