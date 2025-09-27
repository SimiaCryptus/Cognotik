package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.util.JsonUtil
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class GroqChatClient(
    apiKey: String,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    apiBase: String,
    scheduledPool: ListeningScheduledExecutorService,
) : SingleProviderChatClient(
    APIProvider.Groq,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
) {
    override fun authorize(
        request: HttpRequest,
        apiProvider: APIProvider
    ) {
        request.addHeader(HEADER_CONTENT_TYPE, APPLICATION_JSON)
        request.addHeader(HEADER_ACCEPT, APPLICATION_JSON)
        request.addHeader(HEADER_AUTHORIZATION, "Bearer $apiKey")
        require(null == budget || budget!!.toDouble() > 0.0) { "Budget Exceeded" }
    }

    override fun chat(
        chatRequest: ApiModel.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ApiModel.ChatResponse {
        log.info("Starting Groq chat with model: ${model.modelName}")

        return withReliability {
            withPerformanceLogging {
                val groqRequest = toGroq(chatRequest)
                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(groqRequest)

                val result = post("$apiBase/openai/chat/completions", json, APIProvider.Groq)
                checkError(result)
                val response = JsonUtil.objectMapper().readValue(result, ApiModel.ChatResponse::class.java)

                if (response.usage != null && model is ChatModel) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)), logStreams = logStreams)
                }

                response
            }
        }
    }

    companion object {
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val APPLICATION_JSON = "application/json"

        fun toGroq(chatRequest: ApiModel.ChatRequest): ApiModel.GroqChatRequest = ApiModel.GroqChatRequest(
            messages = chatRequest.messages.map { message ->
                ApiModel.GroqChatMessage(
                    role = message.role,
                    content = message.content?.joinToString("\n") { it.text ?: "" } ?: "",
                )
            },
            model = chatRequest.model,
            max_tokens = chatRequest.max_tokens,
            temperature = chatRequest.temperature,
        )
    }
}