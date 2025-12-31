package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class DeepSeekChatClient(
    apiKey: String,
    workPool: ExecutorService,
    apiBase: String = "https://api.deepseek.com",
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : SingleProviderChatClient(
    APIProvider.DeepSeek,
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
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        val deepSeekRequest = toDeepSeek(chatRequest)
        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(deepSeekRequest)
        val result = post("$apiBase/chat/completions", json, APIProvider.DeepSeek)
        checkError(result)
        val response = JsonUtil.objectMapper().readValue(result, ModelSchema.ChatResponse::class.java)
        if (response.usage != null && model is ChatModel) {
            onUsage(model, response.usage.copy(cost = model.pricing(response.usage)), logStreams = logStreams)
        }
        return response
    }

    companion object {
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val APPLICATION_JSON = "application/json"


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