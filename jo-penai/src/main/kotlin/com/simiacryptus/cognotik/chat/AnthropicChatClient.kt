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

class AnthropicChatClient(
    apiKey: String,
    workPool: ExecutorService,
    apiBase: String,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>
) : SingleProviderChatClient(
    APIProvider.Anthropic,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
) {
    override fun authorize(
        request: HttpRequest,
        apiProvider: APIProvider
    ) {
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        request.addHeader("x-api-key", apiKey)
        request.addHeader("anthropic-version", "2023-06-01")
    }

    override fun chat(
        chatRequest: ApiModel.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ApiModel.ChatResponse {
        validateChatRequest(chatRequest, model)

        return withReliability {
            withPerformanceLogging {
                val anthropicChatRequest = try {
                    mapToAnthropicChatRequest(chatRequest, model)
                } catch (e: Exception) {
                    log.error("Failed to map chat request to Anthropic format", e)
                    throw RuntimeException("Failed to map chat request to Anthropic format: ${e.message}", e)
                }

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(anthropicChatRequest)

                val rawResponse = post("${apiBase}/messages", json, APIProvider.Anthropic)
                checkError(rawResponse)
                val responseJson = try {
                    fromAnthropicResponse(rawResponse)
                } catch (e: Exception) {
                    log.error("Failed to parse Anthropic response: $rawResponse", e)
                    throw RuntimeException("Failed to parse Anthropic response: ${e.message}", e)
                }

                val response = JsonUtil.objectMapper().readValue(responseJson, ApiModel.ChatResponse::class.java)

                if (response.usage != null && model is ChatModel) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)), logStreams = logStreams)
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


    companion object {

        fun mapToAnthropicChatRequest(chatRequest: ApiModel.ChatRequest, model: LLMModel): AnthropicChatRequest {
            require(chatRequest.messages.isNotEmpty()) { "Messages cannot be empty" }
            require(model.modelName.isNotBlank()) { "Model name cannot be blank" }

            return AnthropicChatRequest(
                model = chatRequest.model ?: model.modelName,
                system = chatRequest.messages.firstOrNull {
                    it.role == ApiModel.Role.system
                }?.content?.joinToString("\n\n") { it.text.orEmpty() },
                messages = alternateAnthropicRoles(chatRequest.messages.filter {
                    it.role != ApiModel.Role.system
                }).filter { !it.content.isNullOrBlank() },
                max_tokens = chatRequest.max_tokens ?: model.maxOutTokens,
                temperature = chatRequest.temperature,
            )
        }

        fun alternateAnthropicRoles(messages: List<ApiModel.ChatMessage>): List<AnthropicMessage> {
            if (messages.isEmpty()) return emptyList()

            val alternatingMessages = mutableListOf<AnthropicMessage>()
            val remainingMessages = messages.toMutableList()
            while (remainingMessages.isNotEmpty()) {
                val thisRole = remainingMessages.firstOrNull()?.role
                val toConsolidate = remainingMessages.takeWhile { it.role == thisRole }.toTypedArray()
                remainingMessages.removeAll(toConsolidate)
                alternatingMessages += AnthropicMessage(
                    role = thisRole.toString(),
                    content = toConsolidate.joinToString("\n\n") {
                        it.content?.joinToString("\n") { it.text.orEmpty() }.orEmpty()
                    })
            }
            return alternatingMessages
        }

        data class AnthropicChatRequest(
            val model: String? = null,
            val system: String? = null,
            val messages: List<AnthropicMessage>? = null,
            val max_tokens: Int? = null,
            val temperature: Double? = null,
            val top_p: Double? = null,
            val top_k: Int? = null
        )

        data class AnthropicMessage(
            val role: String? = null, val content: String? = null
        )

        data class AnthropicResponse(
            val id: String? = null,
            val type: String? = null,
            val role: String? = null,
            val content: List<AnthropicContentBlock>? = null,
            val model: String? = null,
            val stop_reason: String? = null,
            val stop_sequence: String? = null,
            val usage: AnthropicUsage? = null
        )

        data class AnthropicContentBlock(
            val type: String? = null, val text: String? = null
        )

        data class AnthropicUsage(
            val input_tokens: Int? = null, val output_tokens: Int? = null
        )

        fun fromAnthropicResponse(rawResponse: String): String {
            require(rawResponse.isNotBlank()) { "Response cannot be blank" }

            try {
                val errorCheck = JsonUtil.objectMapper().readTree(rawResponse)
                if (errorCheck.has("type") && errorCheck.get("type").asText() == "error") {
                    val errorMessage = if (errorCheck.has("message")) {
                        errorCheck.get("message").asText()
                    } else if (errorCheck.has("error") && errorCheck.get("error").has("message")) {
                        errorCheck.get("error").get("message").asText()
                    } else {
                        "Unknown error: ${errorCheck}"
                    }
                    throw RuntimeException("Anthropic API error: $errorMessage")
                }

                val response = JsonUtil.objectMapper().readValue(rawResponse, AnthropicResponse::class.java)
                return JsonUtil.toJson(
                    ApiModel.ChatResponse(
                        id = response.id, choices = listOf(
                            ApiModel.ChatChoice(
                                message = ApiModel.ChatMessageResponse(
                                    content = response.content?.joinToString("\n") { it.text ?: "" }), index = 0
                            )
                        ), usage = ApiModel.Usage(
                            prompt_tokens = response.usage?.input_tokens?.toLong() ?: 0,
                            completion_tokens = response.usage?.output_tokens?.toLong() ?: 0,
                            total_tokens = (response.usage?.input_tokens?.toLong()
                                ?: 0) + (response.usage?.output_tokens
                                ?: 0),
                        )
                    )
                )
            } catch (e: Exception) {
                log.error("Failed to parse Anthropic response", e)
                throw RuntimeException("Error parsing Anthropic response", e)
            }
        }

    }
}