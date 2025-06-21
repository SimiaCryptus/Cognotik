package com.simiacryptus.jopenai.chat

import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.chat.ChatModelType
import com.simiacryptus.jopenai.models.chat.LLMModel
import com.simiacryptus.jopenai.util.ClientUtil.checkError
import com.simiacryptus.util.JsonUtil
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class DeepSeekChatClient(
    apiKey: String,
    workPool: ExecutorService,
    apiBase: String = "https://api.deepseek.com",
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf()
) : SingleProviderChatClient(
    APIProvider.DeepSeek,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams
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
        chatRequest: ApiModel.ChatRequest,
        model: LLMModel
    ): ApiModel.ChatResponse {
        val deepSeekRequest = toDeepSeek(chatRequest)
        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(deepSeekRequest)
        val result = post("$apiBase/v1/chat/completions", json, APIProvider.DeepSeek)
        checkError(result)
        val response = JsonUtil.objectMapper().readValue(result, ApiModel.ChatResponse::class.java)
        if (response.usage != null && model is ChatModelType) {
            onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
        }
        return response
    }

    companion object {
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val APPLICATION_JSON = "application/json"


        fun toDeepSeek(chatRequest: ApiModel.ChatRequest): Map<String, Any> {
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