package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.AnthropicChatClient
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class AnthropicProvider : APIProvider("Anthropic", "https://api.anthropic.com/v1") {

  override fun getChatClient(
      key: SecureString,
      workPool: ExecutorService,
      logLevel: Level,
      logStreams: MutableList<BufferedOutputStream>,
      scheduledPool: ListeningScheduledExecutorService,
      session: Session
  ) = AnthropicChatClient(
      apiKey = key,
      apiBase = base,
      workPool = workPool,
      logLevel = logLevel,
      logStreams = logStreams,
      scheduledPool = scheduledPool,
      session = session,
  )
}