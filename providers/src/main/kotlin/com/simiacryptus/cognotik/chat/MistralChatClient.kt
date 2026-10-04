package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.exceptions.InvalidModelException
import com.simiacryptus.cognotik.exceptions.InvalidValueException
import com.simiacryptus.cognotik.exceptions.RequestOverloadException
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UsageListener
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService

data class MistralChatRequest(
  val messages: List<MistralChatMessage>,
  val model: String,
  @JsonProperty("max_tokens") val max_tokens: Int? = null,
  val temperature: Double? = null,
  val stream: Boolean? = null,
  val stop: List<String>? = null,
  @JsonProperty("top_p") val top_p: Double? = null,
  @JsonProperty("random_seed") val random_seed: Int? = null
)

data class MistralChatMessage(
  val role: ModelSchema.Role,
  val content: String
)


class MistralChatClient(
  apiKey: SecureString,
  workPool: ExecutorService,
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
  apiBase: String,
  scheduledPool: ListeningScheduledExecutorService,
  session: Session,
) : ChatClientBase(
  CoreProviders.Mistral,
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

  override fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    log.info("Starting Mistral chat with model: ${model.modelId}")
    return withPerformanceLogging {
      val mistralRequest = toMistral(chatRequest)
      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(mistralRequest)

      val result =
        post("${apiBase}/chat/completions", json, model = model.modelId)
      // OpenAI-style error bodies -> typed exceptions
      checkError(result, model)
      // Mistral-specific error bodies ({"object":"error",...} / {"detail":...})
      checkMistralError(result)
      val response = JsonUtil.objectMapper().readValue(
        result,
        ModelSchema.ChatResponse::class.java
      )

      if (response.usage != null) {
        usageHandler.onUsage(model, response.usage!!)
      }

      response
    }
  }

  companion object {
    private val log = getLogger(MistralChatClient::class.java)
    const val HEADER_CONTENT_TYPE = "Content-Type"
    const val HEADER_ACCEPT = "Accept"
    const val HEADER_AUTHORIZATION = "Authorization"
    const val APPLICATION_JSON = "application/json"

    /**
     * Detects Mistral-native error payloads that [checkError] does not recognize
     * and maps them onto Cognotik's typed exceptions.
     */
    fun checkMistralError(result: String) {
      val node = try {
        JsonUtil.objectMapper().readTree(result)
      } catch (e: Exception) {
        throw IOException("Invalid JSON response: $result", e)
      }
      if (node == null || !node.isObject) return
      val isError = node.path("object").asText("") == "error" ||
          (node.has("detail") && !node.has("choices"))
      if (!isError) return
      val message = when {
        node.hasNonNull("message") -> node.get("message").let { if (it.isTextual) it.asText() else it.toString() }
        node.hasNonNull("detail") -> node.get("detail").let { if (it.isTextual) it.asText() else it.toString() }
        else -> result
      }
      val lower = message.lowercase()
      throw when {
        lower.contains("rate limit") || lower.contains("capacity") || lower.contains("overloaded") ->
          RequestOverloadException(message)

        lower.contains("invalid model") || (lower.contains("model") && lower.contains("does not exist")) ->
          InvalidModelException(
            Regex(
              """model[:\s`']+([\w.\-]+)""",
              RegexOption.IGNORE_CASE
            ).find(message)?.groupValues?.get(1)
          )

        lower.contains("too large for model") || lower.contains("context length") -> {
          val nums = Regex("""\d+""").findAll(message).map { it.value.toInt() }.toList()
          if (nums.size >= 2) com.simiacryptus.cognotik.exceptions.ModelMaxException(nums[1], nums[0], nums[0], 0)
          else IOException(message)
        }

        else -> IOException(message)
      }
    }


    fun toMistral(chatRequest: ModelSchema.ChatRequest): MistralChatRequest = MistralChatRequest(
      messages = chatRequest.messages.map { message ->
        MistralChatMessage(
          role = message.role ?: throw InvalidValueException("role", "null"),
          content = message.content?.joinToString("\n") { it.text ?: "" } ?: "",
        )
      },
      model = chatRequest.model ?: throw InvalidModelException(null),
      max_tokens = chatRequest.max_tokens,
      temperature = chatRequest.temperature,
      stream = false,
      stop = chatRequest.stop?.map { if (it.isEmpty()) "" else it.toString() },
      //top_p = chatRequest.top_p,
      //random_seed = chatRequest.seed
    )
  }
}