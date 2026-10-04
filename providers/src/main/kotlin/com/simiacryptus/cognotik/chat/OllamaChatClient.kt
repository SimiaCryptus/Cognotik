package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.exceptions.AIServiceException
import com.simiacryptus.cognotik.exceptions.ErrorUtil
import com.simiacryptus.cognotik.exceptions.InvalidModelException
import com.simiacryptus.cognotik.exceptions.RequestOverloadException
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.ModelSchema.*
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService

class OllamaChatClient(
  apiKey: SecureString,
  apiBase: String,
  workPool: ExecutorService,
  scheduledPool: ListeningScheduledExecutorService,
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
  session: Session,
) : ChatClientBase(
  CoreProviders.Ollama,
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
  scheduledPool = scheduledPool,
  logLevel = logLevel,
  logStreams = logStreams,
  session = session,
) {

  override fun authorize(
    request: HttpRequest,
  ) {
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    // Ollama typically doesn't require authorization headers
  }

  override fun chat(
    chatRequest: ChatRequest,
    model: ChatModel,
    logStreams: MutableList<BufferedOutputStream>,
    usageHandler: UsageListener
  ): ChatResponse {
    validateChatRequest(chatRequest, model)
    return withPerformanceLogging {
      // Convert OpenAI format to Ollama format
      // Ollama expects content as a string, not an array
      val ollamaMessages = chatRequest.messages.map { message ->
        OllamaMessage(
          role = message.role.toString(),
          content = when (val content = message.content) {
            is List<*> -> content.joinToString("\n") {
              when (it) {
                is ContentPart -> it.text ?: ""
                else -> it.toString()
              }
            }

            else -> ""
          }
        )
      }

      val ollamaRequest = OllamaChatRequest(
        model = chatRequest.model ?: model.modelId,
        messages = ollamaMessages,
        stream = false,
        options = OllamaOptions(
          temperature = chatRequest.temperature,
          //top_p = chatRequest.top_p,
          max_tokens = chatRequest.max_tokens
        )
      )

      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(ollamaRequest)

      val rawResponse = post("${apiBase}/api/chat", json, model = model.modelId)

      checkOllamaError(rawResponse, model, chatRequest.model ?: model.modelId)

      val ollamaResponse = JsonUtil.objectMapper().readValue(rawResponse, OllamaChatResponse::class.java)

      // Convert Ollama response to OpenAI format
      val response = ChatResponse(
        id = "ollama-${System.currentTimeMillis()}",
        `object` = "chat.completion",
        created = System.currentTimeMillis() / 1000,
        model = ollamaResponse.model,
        choices = listOf(
          ChatChoice(
            index = 0,
            message = ollamaResponse.message.let { message ->
              ChatMessageResponse(
                role = Role.values().firstOrNull { it.name.equals(message.role, ignoreCase = true) }
                  ?: Role.valueOf("assistant"),
                content = message.content,
              )
            },
            finish_reason = if (ollamaResponse.done) "stop" else "length"
          )
        ),
        usage = Usage(
          prompt_tokens = ollamaResponse.prompt_eval_count?.toLong() ?: 0L,
          completion_tokens = ollamaResponse.eval_count?.toLong() ?: 0L,
        )
      )

      if (response.usage != null) {
        usageHandler.onUsage(model, response.usage!!)
      }


      response
    }
  }

  override fun getModels(): List<ChatModel> {
    return try {
      val rawResponse = get("${apiBase}/api/tags")
      val modelsResponse = JsonUtil.objectMapper().readValue(rawResponse, OllamaModelsResponse::class.java)

      modelsResponse.models.map { ollamaModel ->
        ChatModel(
          name = ollamaModel.name,
          modelId = ollamaModel.name,
          maxTotalTokens = 4096, // Default, could be model-specific
          maxOutTokens = 4096,
          provider = CoreProviders.Ollama,
          outputTokenPricePerK = 0.0, // Ollama is typically free/local
          inputModalities = setOf(ChatMessageModality.TEXT),
          outputModalities = setOf(ChatMessageModality.TEXT)
        )
      }
    } catch (e: Exception) {
      log(Level.WARN, "Failed to fetch Ollama models: ${e.message}", logStreams)
      emptyList()
    }
  }

  /**
   * Converts Ollama error payloads (`{"error": "..."}` or plain text) into typed
   * Cognotik exceptions so retry logic and metrics classify them correctly.
   */
  private fun checkOllamaError(rawResponse: String, model: LLMModel, modelName: String?) {
    val jsonResponse = try {
      JsonUtil.objectMapper().readTree(rawResponse)
    } catch (e: com.fasterxml.jackson.core.JsonProcessingException) {
      if (rawResponse.contains("error", ignoreCase = true) ||
        rawResponse.contains("not found", ignoreCase = true) ||
        rawResponse.contains("invalid", ignoreCase = true)
      ) {
        throw mapOllamaError(rawResponse.trim(), modelName)
      }
      throw IOException("Invalid JSON response: $rawResponse\nChat Model: ${model.modelId}", e)
    }
    val error = jsonResponse?.get("error") ?: return
    if (error.isTextual) {
      throw mapOllamaError(error.asText(), modelName)
    }
    // OpenAI-style error object; delegate to the shared pattern matcher
    ErrorUtil.checkError(rawResponse, model)
  }

  private fun mapOllamaError(message: String, modelName: String?): IOException {
    val lower = message.lowercase()
    return when {
      lower.contains("model") && (lower.contains("not found") || lower.contains("does not exist")) ->
        InvalidModelException(modelName)

      lower.contains("overloaded") || lower.contains("server busy") || lower.contains("too many requests") ->
        RequestOverloadException("Ollama API error: $message")

      lower.contains("context length") || lower.contains("too long") ->
        AIServiceException("Ollama API error: $message", isFatal = true)

      else -> AIServiceException("Ollama API error: $message", isFatal = false)
    }
  }


  private fun validateChatRequest(chatRequest: ChatRequest, model: LLMModel) {
    require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
    require(model.modelId.isNotBlank() == true) { "Model name cannot be blank" }
    require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class OllamaMessage(
    val role: String,
    val content: String
  )


  @JsonIgnoreProperties(ignoreUnknown = true)
  data class OllamaOptions(
    val temperature: Double? = null,
    val top_p: Double? = null,
    val max_tokens: Int? = null
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class OllamaChatResponse(
    val model: String,
    val message: OllamaMessage,
    val done: Boolean,
    @JsonProperty("prompt_eval_count") val prompt_eval_count: Int? = null,
    @JsonProperty("eval_count") val eval_count: Int? = null
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class OllamaModelsResponse(
    val models: List<OllamaModel>
  )

  @JsonIgnoreProperties(ignoreUnknown = true)
  data class OllamaModel(
    val name: String,
    val size: Long? = null,
    val digest: String? = null,
    val modified_at: String? = null
  )
}