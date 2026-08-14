package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.ChatClientBase
import com.simiacryptus.cognotik.chat.QwenChatClient
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.QwenModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import java.util.concurrent.ExecutorService

/**
 * Alibaba Cloud Qwen (DashScope) API provider, using the OpenAI compatible-mode endpoint.
 * Use [MAINLAND_BASE] instead of [DEFAULT_BASE] for accounts hosted in mainland China.
 */
class QwenProvider : APIProvider(
  name = "Qwen",
  base = DEFAULT_BASE,
) {

  override val models: List<ChatModel>
    get() = QwenModels.values.values.toList()

  override fun createChatClient(
    apiKey: SecureString,
    apiBase: String,
    workPool: ExecutorService,
    scheduledPool: ListeningScheduledExecutorService,
    session: Session,
  ): ChatClientBase = QwenChatClient(
    apiKey = apiKey,
    apiBase = apiBase.ifBlank { DEFAULT_BASE },
    workPool = workPool,
    scheduledPool = scheduledPool,
    session = session,
  )

  companion object {
    const val DEFAULT_BASE = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
    const val MAINLAND_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"
  }
}