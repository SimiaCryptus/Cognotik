package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.ChatMessageModality
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.OpenAIModels
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory.getLogger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class OpenAIChatClient(
  apiKey: SecureString,
  apiBase: String,
  workPool: ExecutorService,
  scheduledPool: ListeningScheduledExecutorService,
  session: Session,
) : ChatClientBase(
  CoreProviders.OpenAI,
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
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
    logStreams: MutableList<java.io.BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    validateChatRequest(chatRequest, model)
    return withPerformanceLogging {

      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(chatRequest)

      val rawResponse =
        post("${apiBase}/chat/completions", json)
      checkError(rawResponse)
      val response = JsonUtil.objectMapper().readValue(
        rawResponse,
        ModelSchema.ChatResponse::class.java
      )

      if (response.usage != null) {
        usageHandler.onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!)
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
      val modelsResponse = fetchModels()
      val models = modelsResponse.mapNotNull { modelInfo ->
        val knownModels = OpenAIModels.values.values
          .filter { it.modelId == modelInfo.id }
        if (knownModels.isNotEmpty()) {
          knownModels.first()
        } else if (modelInfo.id.startsWith("gpt") || modelInfo.id.startsWith("o1") || modelInfo.id.startsWith("o3")) {
          ChatModel(
            name = modelInfo.id,
            modelId = modelInfo.id,
            maxTotalTokens = 128000,
            provider = CoreProviders.OpenAI,
            outputTokenPricePerK = 0.0,
            inputModalities = setOf(ChatMessageModality.TEXT),
            outputModalities = setOf(ChatMessageModality.TEXT)
          )
        } else {
          null
        }
      }
      modelsCache[apiBase] = models
      models
    } catch (e: Exception) {
      log.error("Failed to fetch OpenAI models", e)
      emptyList()
    }
  }

  private fun fetchModels(): List<OpenAIModelInfo> {
    val response = get("${apiBase}/models")
    checkError(response)
    val listResponse = JsonUtil.objectMapper().readValue(response, OpenAIListModelsResponse::class.java)
    return listResponse.data
  }

  companion object {
    private val log = getLogger(OpenAIChatClient::class.java)
    private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

    data class OpenAIModelInfo(
      val id: String,
      val `object`: String,
      val created: Long,
      val owned_by: String
    )

    data class OpenAIListModelsResponse(
      val `object`: String,
      val data: List<OpenAIModelInfo>
    )
  }


}