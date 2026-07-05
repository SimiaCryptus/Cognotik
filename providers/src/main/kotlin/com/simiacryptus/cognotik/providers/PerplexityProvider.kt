package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.OpenAIChatClient
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class PerplexityProvider : APIProvider("Perplexity", "https://api.perplexity.ai") {

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
}