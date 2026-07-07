package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.ChatMessageModality
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.DeepSeekModels
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
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

class DeepSeekChatClient(
  apiKey: SecureString,
  workPool: ExecutorService,
  apiBase: String = "https://api.deepseek.com",
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
  scheduledPool: ListeningScheduledExecutorService,
  session: Session,
) : ChatClientBase(
  CoreProviders.DeepSeek,
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
    request.addHeader(HEADER_CONTENT_TYPE, APPLICATION_JSON)
    request.addHeader(HEADER_ACCEPT, APPLICATION_JSON)
    request.addHeader(HEADER_AUTHORIZATION, "Bearer ${apiKey.decrypt}")
  }

  override fun getModels(): List<ChatModel> {
    // Check cache first
    modelsCache[apiBase]?.let { return it }

    return try {
      val modelsResponse = fetchAllModels()
      val models = modelsResponse.map { modelInfo ->
        val known = DeepSeekModels.values.values
          .firstOrNull { it.name == modelInfo.id || it.modelId == modelInfo.id }
        when {
          known != null -> known
          else -> {
            log.debug("Unknown DeepSeek model: ${modelInfo.id}")
            ChatModel(
              name = modelInfo.id,
              modelId = modelInfo.id,
              maxTotalTokens = 1_000_000,
              maxOutTokens = 384_000,
              provider = CoreProviders.DeepSeek,
              outputTokenPricePerK = 0.0, // TODO: Set actual pricing if known
              inputModalities = setOf(ChatMessageModality.TEXT),
              outputModalities = setOf(ChatMessageModality.TEXT)
            )
          }
        }
      }
      // Cache the result
      modelsCache[apiBase] = models
      models
    } catch (e: Exception) {
      log.error("Failed to fetch DeepSeek models", e)
      // Fall back to the statically-known models
      DeepSeekModels.values.values.toList()
    }
  }

  private fun fetchAllModels(): List<ModelInfo> {
    require(!apiBase.isBlank())
    val response = get("$apiBase/models")
    checkError(response)
    log.debug("DeepSeek models response: $response")
    val listResponse = JsonUtil.objectMapper().readValue(response, ListModelsResponse::class.java)
    return listResponse.data
  }

  override fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<java.io.BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    val deepSeekRequest = toDeepSeek(chatRequest)
    val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
      .writeValueAsString(deepSeekRequest)
    val result = post("$apiBase/chat/completions", json)
    checkError(result)
    val response = JsonUtil.objectMapper().readValue(result, ModelSchema.ChatResponse::class.java)
    if (response.usage != null && model is ChatModel) {
      usageHandler.onUsage(model, response.usage!!)
    }
    return response
  }

  companion object {
    private val log = getLogger(DeepSeekChatClient::class.java)
    private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_ACCEPT = "Accept"
    const val HEADER_AUTHORIZATION = "Authorization"
    const val APPLICATION_JSON = "application/json"

    data class ModelInfo(
      val id: String,
      val `object`: String? = null,
      val owned_by: String? = null
    )

    data class ListModelsResponse(
      val `object`: String? = null,
      val data: List<ModelInfo> = emptyList()
    )

    fun toDeepSeek(chatRequest: ModelSchema.ChatRequest): Map<String, Any> {
      val request = mutableMapOf<String, Any>(
        "model" to (chatRequest.model ?: throw RuntimeException("Model not specified")),
        "messages" to chatRequest.messages.map { message ->
          mapOf(
            "role" to message.role.toString(),
            "content" to (message.content?.joinToString("\n") { it.text ?: "" } ?: "")
          )
        },
        "stream" to false
      )
      chatRequest.temperature?.let { request["temperature"] = it }
      chatRequest.max_tokens?.let { request["max_tokens"] = it }
//            chatRequest.top_p?.let { request["top_p"] = it }
//            chatRequest.frequency_penalty?.let { request["frequency_penalty"] = it }
//            chatRequest.presence_penalty?.let { request["presence_penalty"] = it }
      chatRequest.stop?.let { request["stop"] = it }
      return request
    }
  }
}