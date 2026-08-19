package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.node.ObjectNode
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
    request.setHeader("Content-Type", "application/json")
    request.setHeader("Accept", "application/json")
    request.setHeader("Authorization", "Bearer ${apiKey.decrypt}")
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
      val rawJson = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(sanitizedRequest)
      val json = sanitizeContentForXAI(rawJson)
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

  /**
   * The xAI chat/completions endpoint expects OpenAI-style tagged content
   * parts (e.g. {"type": "text", "text": "..."}). Our internal ChatRequest
   * model may serialize text-only parts without an explicit "type" field
   * (e.g. {"text": "..."}), which xAI's untagged-enum deserializer rejects
   * with "data did not match any variant of untagged enum Content".
   * This patches the outgoing JSON to add the missing discriminator.
   */
  private fun sanitizeContentForXAI(json: String): String {
    val mapper = JsonUtil.objectMapper()
    val root = mapper.readTree(json)
    val messages = root.get("messages")
    if (messages != null && messages.isArray) {
      messages.forEach { message ->
        val content = message.get("content")
        if (content != null && content.isArray) {
          content.forEach { part ->
            if (part is ObjectNode && !part.has("type")) {
              when {
                part.has("text") -> part.put("type", "text")
                part.has("image_url") -> part.put("type", "image_url")
              }
            }
          }
        }
      }
    }
    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
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