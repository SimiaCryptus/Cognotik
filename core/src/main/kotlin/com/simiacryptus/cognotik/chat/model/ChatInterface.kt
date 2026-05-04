package com.simiacryptus.cognotik.chat.model

import com.fasterxml.jackson.annotation.JsonIgnore
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
  logStreams: MutableList<BufferedOutputStream>,
  private val key: SecureString,
  private val base: String,
  private val logLevel: Level,
  val temperature: Double,
  val audio: MutableMap<String, String> = mutableMapOf(),
  val provider: APIProvider,
  val modelType: ChatModel,
  private val workPool: ExecutorService,
  private val scheduledPool: ListeningScheduledExecutorService,
  private val onUsage: (model: LLMModel, tokens: ModelSchema.Usage) -> Unit,
) {
  val logStreams: MutableList<BufferedOutputStream> = logStreams.toMutableList()
    get() = when {
      !ENABLE_LOGS -> mutableListOf()
      else -> field
    }


  init {
    //require(key != null) { "API key must be provided" }
    require(base.isNotBlank()) { "Base URL must be provided" }
    require(temperature in 0.0..2.0) { "Temperature must be in range [0.0, 2.0]" }
  }

  fun chat(chatRequest: ChatRequest): ModelSchema.ChatResponse = provider.getChatClient(
    key = key,
    base = base,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool,
  ).apply {
    onUsageListeners.add { model, usage -> onUsage(model, usage) }
  }.chat(
    chatRequest = chatRequest,
    model = modelType,
    logStreams = logStreams
  )

  @JsonIgnore
  fun getChildClient(): ChatInterface = ChatInterface(
    logStreams = this.logStreams.toTypedArray().toMutableList(),
    key = this.key,
    base = this.base,
    logLevel = this.logLevel,
    temperature = this.temperature,
    provider = this.provider,
    modelType = this.modelType,
    workPool = this.workPool,
    scheduledPool = this.scheduledPool,
    onUsage = this.onUsage,
  )

  companion object {
    val log = org.slf4j.LoggerFactory.getLogger(ChatInterface::class.java)
    var ENABLE_LOGS = false
  }
}