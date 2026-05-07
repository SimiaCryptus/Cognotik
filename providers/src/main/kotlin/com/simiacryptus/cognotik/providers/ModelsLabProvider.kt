package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.ModelsLabChatClient
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class ModelsLabProvider : APIProvider("ModelsLab", "https://modelslab.com/api/v6") {

  override fun getChatClient(
      key: SecureString,
      workPool: ExecutorService,
      logLevel: Level,
      logStreams: MutableList<BufferedOutputStream>,
      scheduledPool: ListeningScheduledExecutorService
  ) = ModelsLabChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
  )
}