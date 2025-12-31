package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ConcurrentHashMap
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
    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(GroqChatClient::class.java)
        private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val APPLICATION_JSON = "application/json"

        fun toGroq(chatRequest: ModelSchema.ChatRequest): ModelSchema.GroqChatRequest = ModelSchema.GroqChatRequest(
            messages = chatRequest.messages.map { message ->
                ModelSchema.GroqChatMessage(
                    role = message.role,
                    content = message.content?.joinToString("\n") { it.text ?: "" } ?: "",
                )
            },
            model = chatRequest.model,
            max_tokens = chatRequest.max_tokens,
            temperature = chatRequest.temperature,
        )
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GroqModel(
        val id: String,
        val `object`: String,
        val created: Long,
        val owned_by: String,
        val active: Boolean,
        val context_window: Int,
        val public_apps: Boolean
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class GroqModelsResponse(
        val `object`: String,
        val data: List<GroqModel>
    )

    override fun getModels(): List<ChatModel>? {
        // Check cache first
        modelsCache[apiBase]?.let { cachedModels ->
            //log.debug("Returning cached models for apiBase: $apiBase")
            return cachedModels
        }

        return try {
            log.info("Fetching available models from Groq API")
            val result = get("$apiBase/models")
            checkError(result)
            log.debug("Groq models response: $result")
            val response = JsonUtil.objectMapper().readValue(result, GroqModelsResponse::class.java)
            val models = response.data.filter { it.active }.mapNotNull { groqModel ->
                // Try to find existing ChatModel definition first
                ChatModel.values().values.find { it.modelName == groqModel.id }
                    ?: run {
                        // Create a basic ChatModel for unknown models
                        log.debug("Creating basic ChatModel for unknown Groq model: ${groqModel.id}")
                        ChatModel(
                            name = groqModel.id,
                            modelName = groqModel.id,
                            maxTotalTokens = groqModel.context_window,
                            maxOutTokens = minOf(groqModel.context_window, 8192), // Conservative default
                            provider = APIProvider.Groq,
                            inputTokenPricePerK = 0.0, // Unknown pricing
                            outputTokenPricePerK = 0.0 // Unknown pricing
                        )
                    }
            }
            // Cache the result
            modelsCache[apiBase] = models
            models
        } catch (e: Exception) {
            log.warn("Failed to fetch models from Groq API: ${e.message}")
            null
        }
    }

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
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        log.info("Starting Groq chat with model: ${model.modelName}")

        return withReliability {
            withPerformanceLogging {
                val groqRequest = toGroq(chatRequest)
                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(groqRequest)

                val result = post("$apiBase/openai/chat/completions", json, APIProvider.Groq)
                checkError(result)
                val response = JsonUtil.objectMapper().readValue(result, ModelSchema.ChatResponse::class.java)

                if (response.usage != null && model is ChatModel) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)), logStreams = logStreams)
                }

                response
            }
        }
    }
}