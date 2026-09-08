package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.chat.model.ModelsLabDataModel
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ModelSchema
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UsageListener
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.runWithPermit
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore

class ModelsLabChatClient(
  apiKey: SecureString,
  apiBase: String = CoreProviders.ModelsLab.base!!,
  workPool: ExecutorService,
  logLevel: Level = Level.DEBUG,
  logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
  scheduledPool: ListeningScheduledExecutorService,
  session: Session,
) : ChatClientBase(
  CoreProviders.ModelsLab,
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
    // ModelsLab uses API key in the request body, not headers
  }

  override fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<java.io.BufferedOutputStream>,
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    return modelsLabThrottle.runWithPermit {
      val modelsLabRequest = toModelsLab(chatRequest)
      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(modelsLabRequest)

      val rawResponse = post("$apiBase/llm/chat", json)
      val responseJson = fromModelsLab(rawResponse, this)

      val response: ModelSchema.ChatResponse =
        JsonUtil.objectMapper().readValue(responseJson, ModelSchema.ChatResponse::class.java)
      if (response.usage != null) {
        usageHandler.onUsage(model, response.usage!!)
      }
      response
    }
  }

  companion object {
    private val modelsLabThrottle = Semaphore(1)
    private val modelslab_chatRequest_prototype = ModelsLabDataModel.ChatRequest(
      max_new_tokens = 1000,
      no_repeat_ngram_size = 5,
    )

    fun fromModelsLab(rawResponse: String, client: ModelsLabChatClient): String {
      val response = JsonUtil.objectMapper().readValue(rawResponse, ModelsLabDataModel.ChatResponse::class.java)
      return when (response.status) {
        "success" -> {
          JsonUtil.toJson(
            ModelSchema.ChatResponse(
              id = response.chat_id, choices = listOf(
                ModelSchema.ChatChoice(
                  message = ModelSchema.ChatMessageResponse(content = response.message), index = 0
                )
              ), usage = response.meta?.let {
                ModelSchema.Usage(
                  prompt_tokens = it.max_new_tokens?.toLong() ?: 0,
                  completion_tokens = 0,
                )
              })
          )
        }

        "processing" -> {
          val seconds = response?.eta ?: 1
          client.log(Level.INFO, "Chat response is still processing; waiting ${seconds}s and trying again.")
          Thread.sleep(seconds * 1000L)
          fromModelsLab(
            client.post(
              "${client.apiBase}/llm/get_queued_response",
              JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                mapOf(
                  "chat_id" to (response.meta?.chat_id ?: response.chat_id),
                  "key" to client.apiKey.decrypt
                )
              ),
            ),
            client
          )
        }

        "error" -> {
          throw RuntimeException("Error in chat request: ${response.message}\n$rawResponse")
        }

        "failed" -> {
          throw RuntimeException("Chat request failed: ${response.message}\n$rawResponse")
        }

        else -> throw RuntimeException("Unknown status: ${response.status}\n${response.message}\n$rawResponse")
      }
    }

    fun toModelsLab(chatRequest: ModelSchema.ChatRequest) = modelslab_chatRequest_prototype.copy(
      model_id = chatRequest.model,
      system_prompt = chatRequest.messages.filter { it.role == ModelSchema.Role.system }.joinToString("\n") {
        it.content?.joinToString("\n") { it.text ?: "" } ?: ""
      },
      prompt = chatRequest.messages.filter { it.role != ModelSchema.Role.system }.joinToString("\n") {
        it.content?.joinToString("\n") { it.text ?: "" } ?: ""
      },
      temperature = chatRequest.temperature,
    )

  }
}