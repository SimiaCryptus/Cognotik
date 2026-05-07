package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.common.util.concurrent.MoreExecutors
import com.simiacryptus.cognotik.chat.AnthropicChatClient
import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class AnthropicProvider : APIProvider("Anthropic", "https://api.anthropic.com/v1") {

  override fun getChatClient(
    key: SecureString,
    base: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService
  ) = AnthropicChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
  )
}