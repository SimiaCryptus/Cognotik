package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.ChatMessageModality
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GroqModels
import com.simiacryptus.cognotik.chat.model.price
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

class GroqChatClient(
  apiKey: SecureString,
  workPool: ExecutorService,
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
  apiBase: String,
  scheduledPool: ListeningScheduledExecutorService,
  session: Session,
) : ChatClientBase(
  CoreProviders.Groq,
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
  logLevel = logLevel,
  logStreams = logStreams,
  scheduledPool = scheduledPool,
  session = session,
) {

  companion object {
    private val log = getLogger(GroqChatClient::class.java)
    private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_ACCEPT = "Accept"
    const val HEADER_AUTHORIZATION = "Authorization"
    const val APPLICATION_JSON = "application/json"

    fun toGroq(chatRequest: ModelSchema.ChatRequest): ModelSchema.GroqChatRequest = ModelSchema.GroqChatRequest(
      messages = chatRequest.messages.map { message ->
        ModelSchema.GroqChatMessage(
          role = message.role,
          content = message.content?.joinToString("\n") { it.text ?: "" } ?: "",
        )
      },
      model = chatRequest.model,
      max_tokens = chatRequest.max_tokens,
      temperature = chatRequest.temperature,
    )
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class GroqModel(
    val id: String,
    val `object`: String,
    val created: Long,
    val owned_by: String,
    val active: Boolean,
    val context_window: Int,
    val public_apps: Boolean
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class GroqModelsResponse(
    val `object`: String,
    val data: List<GroqModel>
  )

  override fun getModels(): List<ChatModel> {
    // Check cache first
    modelsCache[apiBase]?.let { cachedModels ->
      //log.debug("Returning cached models for apiBase: $apiBase")
      return cachedModels
    }

    return try {
      log.info("Fetching available models from Groq API")
      val result = get("$apiBase/models")
      checkError(result)
      log.debug("Groq models response: $result")
      val response = JsonUtil.objectMapper().readValue(result, GroqModelsResponse::class.java)
      val models = response.data.filter { it.active }.mapNotNull { groqModel ->
        val knownModels = GroqModels.values.values
          .filter { it.modelId == groqModel.id }
        if (knownModels.isNotEmpty()) {
          knownModels.first()
        } else if (groqModel.id.startsWith("groq") || groqModel.id.startsWith("o1") || groqModel.id.startsWith("o3")) {
          ChatModel(
            name = groqModel.id,
            modelId = groqModel.id,
            maxTotalTokens = groqModel.context_window,
            provider = CoreProviders.Groq,
            outputTokenPricePerK = 0.0, // Groq doesn't publicly list token pricing as of now
            inputModalities = setOf(ChatMessageModality.TEXT),
            outputModalities = setOf(ChatMessageModality.TEXT)
          )
        } else {
          null
        }
      }
      // Cache the result
      modelsCache[apiBase] = models
      models
    } catch (e: Exception) {
      log.warn("Failed to fetch models from Groq API: ${e.message}")
      emptyList()
    }
  }

  override fun authorize(
    request: HttpRequest,
  ) {
    request.addHeader(HEADER_CONTENT_TYPE, APPLICATION_JSON)
    request.addHeader(HEADER_ACCEPT, APPLICATION_JSON)
    request.addHeader(HEADER_AUTHORIZATION, "Bearer ${apiKey.decrypt}")
  }

  override fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    log.info("Starting Groq chat with model: ${model.modelId}")
    return withPerformanceLogging {
      val groqRequest = toGroq(chatRequest)
      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(groqRequest)
      val result =
        post("${apiBase}/chat/completions", json)
      checkError(result)
      val response = JsonUtil.objectMapper().readValue(
        result,
        ModelSchema.ChatResponse::class.java
      )

      if (response.usage != null) {
        usageHandler.onUsage(model, response.usage?.copy(cost = response.usage!!.price(model).cost)!!)
      }

      response
    }
  }
}