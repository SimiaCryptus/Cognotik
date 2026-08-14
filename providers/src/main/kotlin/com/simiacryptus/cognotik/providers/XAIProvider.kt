package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.ChatClientBase
import com.simiacryptus.cognotik.chat.XAIChatClient
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.XAIModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import java.util.concurrent.ExecutorService

/**
 * xAI (Grok) API provider. Mirrors the structure of [OpenAIProvider];
 * the xAI endpoint is OpenAI-compatible.
 */
class XAIProvider : APIProvider(
  name = "xAI",
  base = DEFAULT_BASE,
) {

  override val models: List<ChatModel>
    get() = XAIModels.values.values.toList()

  override fun createChatClient(
    apiKey: SecureString,
    apiBase: String,
    workPool: ExecutorService,
    scheduledPool: ListeningScheduledExecutorService,
    session: Session,
  ): ChatClientBase = XAIChatClient(
    apiKey = apiKey,
    apiBase = apiBase.ifBlank { DEFAULT_BASE },
    workPool = workPool,
    scheduledPool = scheduledPool,
    session = session,
  )

  companion object {
    const val DEFAULT_BASE = "https://api.x.ai/v1"
  }
}