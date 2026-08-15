package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.QwenChatClient
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.QwenModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

/**
 * Alibaba Cloud Qwen (DashScope) API provider, using the OpenAI compatible-mode endpoint.
 * Use [MAINLAND_BASE] instead of [DEFAULT_BASE] for accounts hosted in mainland China.
 */
class QwenProvider : APIProvider("Qwen", DEFAULT_BASE) {

  override fun getChatClient(
      key: SecureString,
      workPool: ExecutorService,
      logLevel: Level,
      logStreams: MutableList<BufferedOutputStream>,
      scheduledPool: ListeningScheduledExecutorService,
      session: Session
  ) = QwenChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool,
    session = session,
  )

  override fun getChatModels(key: SecureString, baseUrl: String): List<ChatModel> {
    return QwenModels.values.values.toList()
  }

  companion object {
    const val DEFAULT_BASE = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"
    const val MAINLAND_BASE = "https://dashscope.aliyuncs.com/compatible-mode/v1"
  }
}