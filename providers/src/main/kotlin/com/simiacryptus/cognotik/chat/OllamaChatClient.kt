package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
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
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<java.io.BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    validateChatRequest(chatRequest, model)
    return withPerformanceLogging {
      // Convert OpenAI format to Ollama format
      // Ollama expects content as a string, not an array
      val ollamaMessages = chatRequest.messages.map { message ->
        OllamaMessage(
          role = message.role.toString(),
          content = when (val content = message.content) {
            is String -> content
            is List<*> -> content.joinToString("\n") {
              when (it) {
                is ModelSchema.ContentPart -> it.text ?: ""
                else -> it.toString()
              }
            }

            else -> ""
          }
        )
      }

      val ollamaRequest = OllamaChatRequest(
        model = chatRequest.model ?: model.modelId!!,
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

      val rawResponse = post("${apiBase}/api/chat", json)

      // Check if response is an error by trying to parse it as JSON
      // Ollama returns plain text errors or JSON responses
      try {
        val jsonResponse = JsonUtil.objectMapper().readTree(rawResponse)
        if (jsonResponse.has("error")) {
          throw RuntimeException("Ollama API error: ${jsonResponse.get("error").asText()}")
        }
      } catch (e: com.fasterxml.jackson.core.JsonParseException) {
        // If it's not valid JSON, treat it as an error message
        if (rawResponse.contains("error", ignoreCase = true) ||
          rawResponse.contains("not found", ignoreCase = true) ||
          rawResponse.contains("invalid", ignoreCase = true)
        ) {
          throw RuntimeException("Ollama API error: $rawResponse")
        }
        // If it's not JSON and doesn't look like an error, re-throw the parse exception
        throw RuntimeException("Invalid JSON response from Ollama: $rawResponse", e)
      }

      val ollamaResponse = JsonUtil.objectMapper().readValue(rawResponse, OllamaChatResponse::class.java)

      // Convert Ollama response to OpenAI format
      val response = ModelSchema.ChatResponse(
        id = "ollama-${System.currentTimeMillis()}",
        `object` = "chat.completion",
        created = System.currentTimeMillis() / 1000,
        model = ollamaResponse.model,
        choices = listOf(
          ChatChoice(
            index = 0,
            message = ollamaResponse.message.let { message ->
              ChatMessageResponse(
                role = message.role.let { Role.valueOf(it) },
                content = message.content,
              )
            },
            finish_reason = if (ollamaResponse.done) "stop" else "length"
          )
        ),
        usage = ModelSchema.Usage(
          prompt_tokens = ollamaResponse.prompt_eval_count?.toLong() ?: 0L,
          completion_tokens = ollamaResponse.eval_count?.toLong() ?: 0L,
          total_tokens = ((ollamaResponse.prompt_eval_count ?: 0) + (ollamaResponse.eval_count
            ?: 0)).toLong()
        )
      )

      if (response.usage != null) {
        usageHandler.onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!)
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
          inputTokenPricePerK = 0.0, // Ollama is typically free/local
          outputTokenPricePerK = 0.0
        )
      }
    } catch (e: Exception) {
      log(Level.WARN, "Failed to fetch Ollama models: ${e.message}", logStreams)
      emptyList()
    }
  }

  private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
    require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
    require(model.modelId?.isNotBlank() == true) { "Model name cannot be blank" }
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