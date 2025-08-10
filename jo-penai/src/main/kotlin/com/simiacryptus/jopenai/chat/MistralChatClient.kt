package com.simiacryptus.jopenai.chat

import com.fasterxml.jackson.annotation.JsonProperty

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
    val role: ApiModel.Role,
    val content: String
)


class MistralChatClient(
    apiKey: String,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    apiBase: String
) : SingleProviderChatClient(
    APIProvider.Mistral,
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
        require(null == budget || budget!!.toDouble() > 0.0) { "Budget Exceeded" }
    }

    override fun chat(
        chatRequest: ApiModel.ChatRequest,
        model: LLMModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ApiModel.ChatResponse {
        log.info("Starting Mistral chat with model: ${model.modelName}")

        return withReliability {
            withPerformanceLogging {
                val mistralRequest = toMistral(chatRequest)
                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(mistralRequest)

                val result = post("$apiBase/v1/chat/completions", json, APIProvider.Mistral)
                checkError(result)
                val response = JsonUtil.objectMapper().readValue(result, ApiModel.ChatResponse::class.java)

                if (response.usage != null && model is ChatModelType) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
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

        fun toMistral(chatRequest: ApiModel.ChatRequest): MistralChatRequest = MistralChatRequest(
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
            stop = chatRequest.stop?.map { if (it.isNullOrEmpty()) "" else it.toString() },
            //top_p = chatRequest.top_p,
            //random_seed = chatRequest.seed
        )
    }
}