package com.simiacryptus.cognotik.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.util.JsonUtil
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
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ApiModel.ChatResponse {
        validateChatRequest(chatRequest, model)

        return withReliability {
            withPerformanceLogging {

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(chatRequest)

                val rawResponse = post("${apiBase}/chat/completions", json, APIProvider.OpenAI)
                checkError(rawResponse)

                val response = JsonUtil.objectMapper().readValue(rawResponse, ApiModel.ChatResponse::class.java)

                if (response.usage != null && model is ChatModel) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)), logStreams = logStreams)
                }

                response
            }
        }
    }
    private fun validateChatRequest(chatRequest: ApiModel.ChatRequest, model: LLMModel) {
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
        require(model.modelName?.isNotBlank() == true) { "Model name cannot be blank" }
        require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
    }















}