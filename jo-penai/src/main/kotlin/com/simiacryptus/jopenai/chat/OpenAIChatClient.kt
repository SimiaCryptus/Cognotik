package com.simiacryptus.jopenai.chat

import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.chat.model.ChatModelType
import com.simiacryptus.jopenai.models.LLMModel
import com.simiacryptus.jopenai.util.ClientUtil.checkError
import com.simiacryptus.util.JsonUtil
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class OpenAIChatClient(
    apiKey: String,
    apiBase: String,
    workPool: ExecutorService,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>
) : SingleProviderChatClient(
    APIProvider.OpenAI,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool
) {
    override fun authorize(
        request: HttpRequest,
        apiProvider: APIProvider
    ) {
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        request.addHeader("Authorization", "Bearer $apiKey")
    }

    override fun chat(
        chatRequest: ApiModel.ChatRequest,
        model: LLMModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ApiModel.ChatResponse {
        validateChatRequest(chatRequest, model)

        return withReliability {
            withPerformanceLogging {

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(chatRequest)

                val rawResponse = post("${apiBase}/v1/chat/completions", json, APIProvider.OpenAI)
                checkError(rawResponse)

                val response = JsonUtil.objectMapper().readValue(rawResponse, ApiModel.ChatResponse::class.java)

                if (response.usage != null && model is ChatModelType) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
                }

                response
            }
        }
    }
    private fun validateChatRequest(chatRequest: ApiModel.ChatRequest, model: LLMModel) {
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
        require(model.modelName.isNotBlank()) { "Model name cannot be blank" }
        require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
    }















}