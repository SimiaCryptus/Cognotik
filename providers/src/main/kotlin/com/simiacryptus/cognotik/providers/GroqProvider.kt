package com.simiacryptus.cognotik.providers

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.platform.model.AudioModels
import com.simiacryptus.cognotik.chat.GroqChatClient
import com.simiacryptus.cognotik.platform.model.APIProvider
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class GroqProvider : APIProvider("Groq", "https://api.groq.com/openai/v1") {

  override fun getChatClient(
      key: SecureString,
      workPool: ExecutorService,
      logLevel: Level,
      logStreams: MutableList<BufferedOutputStream>,
      scheduledPool: ListeningScheduledExecutorService,
      session: Session
  ) = GroqChatClient(
    apiKey = key,
    apiBase = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool,
      session = session,
  )

  override fun getTranscriptionModels(
    key: SecureString,
    baseUrl: String
  ): List<AudioModels> {
    return listOf(
      AudioModels(modelId = "whisper-large-v3", provider = this),
      AudioModels(modelId = "whisper-large-v3-turbo", provider = this),
    )
  }
}