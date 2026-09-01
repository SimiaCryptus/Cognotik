package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.XAIChatClient
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.chat.model.XAIModels
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
class XAIProvider : APIProvider("xAI", DEFAULT_BASE) {

  override fun getChatClient(
      key: SecureString,
      workPool: ExecutorService,
      logLevel: Level,
      logStreams: MutableList<BufferedOutputStream>,
      scheduledPool: ListeningScheduledExecutorService,
      session: Session
  ) = XAIChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool,
    session = session,
  )

  override fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel> {
    return XAIModels.values.values.toList()
  }

  companion object {
    const val DEFAULT_BASE = "https://api.x.ai/v1"
  }
}