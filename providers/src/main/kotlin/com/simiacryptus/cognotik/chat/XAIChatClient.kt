package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.ChatMessageModality
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.XAIModels
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

/**
 * xAI (Grok) client. The xAI REST API is OpenAI-compatible, so the request/response
 * payloads are passed through unchanged.
 */
class XAIChatClient(
  apiKey: SecureString,
  apiBase: String,
  workPool: ExecutorService,
  scheduledPool: ListeningScheduledExecutorService,
  session: Session,
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
) : ChatClientBase(
  CoreProviders.XAI,
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
  logLevel = logLevel,
  logStreams = logStreams,
  scheduledPool = scheduledPool,
  session = session,
) {

  override fun authorize(
    request: HttpRequest,
  ) {
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    request.addHeader("Authorization", "Bearer ${apiKey.decrypt}")
  }

  override fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    validateChatRequest(chatRequest, model)
    return withPerformanceLogging {
      val sanitizedRequest = if (model.supportsTemperature) chatRequest else chatRequest.copy(temperature = 0.0)
      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(sanitizedRequest)
      val rawResponse = post("${apiBase}/chat/completions", json)
      checkError(rawResponse)
      val response = JsonUtil.objectMapper().readValue(
        rawResponse,
        ModelSchema.ChatResponse::class.java
      )
      if (response.usage != null) {
        usageHandler.onUsage(model, response.usage!!)
      }
      response
    }
  }

  private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
    require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
    require(model.modelId?.isNotBlank() == true) { "Model name cannot be blank" }
    require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
  }

  override fun getModels(): List<ChatModel> {
    modelsCache[apiBase]?.let { return it }
    return try {
      val response = get("${apiBase}/models")
      checkError(response)
      log.debug("xAI models response: $response")
      val listResponse = JsonUtil.objectMapper().readValue(response, XAIListModelsResponse::class.java)
      val models = listResponse.data.mapNotNull { modelInfo ->
        val known = XAIModels.values.values.filter { it.modelId == modelInfo.id }
        when {
          known.isNotEmpty() -> known.first()
          modelInfo.id.startsWith("grok") -> ChatModel(
            name = modelInfo.id,
            modelId = modelInfo.id,
            maxTotalTokens = 131072,
            maxOutTokens = 32768,
            provider = CoreProviders.XAI,
            outputTokenPricePerK = 0.0,
            inputModalities = setOf(ChatMessageModality.TEXT),
            outputModalities = setOf(ChatMessageModality.TEXT)
          )

          else -> null
        }
      }
      modelsCache[apiBase] = models
      models
    } catch (e: Exception) {
      log.error("Failed to fetch xAI models", e)
      emptyList()
    }
  }

  companion object {
    private val log = getLogger(XAIChatClient::class.java)
    private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class XAIModelInfo(
      val id: String,
      val `object`: String? = null,
      val created: Long? = null,
      val owned_by: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class XAIListModelsResponse(
      val `object`: String? = null,
      val data: List<XAIModelInfo> = emptyList()
    )
  }
}