package com.simiacryptus.cognotik.chat.model

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ChatRequest
import com.simiacryptus.cognotik.util.SecureString
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class ChatInterface(
  val logStreams: MutableList<BufferedOutputStream>,
  private val key: SecureString,
  private val base: String,
  private val logLevel: Level,
  private val temperature: Double,
  val provider: APIProvider,
  val modelType: ChatModel,
  private val workPool: ExecutorService,
  private val scheduledPool: ListeningScheduledExecutorService,
  private val onUsage: (model: LLMModel, tokens: ModelSchema.Usage) -> Unit,
) {
  init {
    //require(key != null) { "API key must be provided" }
    require(base.isNotBlank()) { "Base URL must be provided" }
    require(temperature in 0.0..2.0) { "Temperature must be in range [0.0, 2.0]" }
  }

  fun chat(
    messages: List<ChatMessage>,
  ) = provider.getChatClient(
    key = key,
    base = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool,
  ).apply {
    onUsageListeners.add { model, usage -> onUsage(model, usage) }
  }.chat(
    chatRequest = ChatRequest(
      model = modelType.modelId,
      messages = messages,
      temperature = temperature,
    ),
    model = modelType,
    logStreams = logStreams
  )

}