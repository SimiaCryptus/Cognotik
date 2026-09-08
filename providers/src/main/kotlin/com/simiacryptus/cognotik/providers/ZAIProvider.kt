package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.ZAIChatClient
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.chat.model.ZAIModels
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

/**
 * xAI (Grok) API provider. Mirrors the structure of [OpenAIProvider];
 * the xAI endpoint is OpenAI-compatible.
 */
class ZAIProvider : APIProvider("z.ai", DEFAULT_BASE) {

  override fun getChatClient(
      key: SecureString,
      workPool: ExecutorService,
      logLevel: Level,
      logStreams: MutableList<BufferedOutputStream>,
      scheduledPool: ListeningScheduledExecutorService,
      session: Session
  ) = ZAIChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool,
    session = session,
  )

  override fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel> {
    return ZAIModels.values.values.toList()
  }

  companion object {
    const val DEFAULT_BASE = "https://api.z.ai/api/paas/v4"
  }
}