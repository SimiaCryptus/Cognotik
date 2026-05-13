package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
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
    logStreams: MutableList<java.io.BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    log.info("Starting Mistral chat with model: ${model.modelId}")
    return withPerformanceLogging {
      val mistralRequest = toMistral(chatRequest)
      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(mistralRequest)

      val result =
        post("${apiBase}/chat/completions", json)
      checkError(result)
      val response = JsonUtil.objectMapper().readValue(
        result,
        ModelSchema.ChatResponse::class.java
      )

      if (response.usage != null) {
        usageHandler.onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!)
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

    fun toMistral(chatRequest: ModelSchema.ChatRequest): MistralChatRequest = MistralChatRequest(
      messages = chatRequest.messages.map { message ->
        MistralChatMessage(
          role = message.role!!,
          content = message.content?.joinToString("\n") { it.text ?: "" } ?: "",
        )
      },
      model = chatRequest.model!!,
      max_tokens = chatRequest.max_tokens,
      temperature = chatRequest.temperature,
      stream = false,
      stop = chatRequest.stop?.map { if (it.isEmpty()) "" else it.toString() },
      //top_p = chatRequest.top_p,
      //random_seed = chatRequest.seed
    )
  }
}