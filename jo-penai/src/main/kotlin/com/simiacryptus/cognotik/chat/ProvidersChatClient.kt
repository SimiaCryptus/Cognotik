package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.core.JsonProcessingException
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.cognotik.exceptions.ModerationException
import com.simiacryptus.cognotik.models.*
import com.simiacryptus.cognotik.models.ApiModel.*
import com.simiacryptus.cognotik.util.ClientUtil.allowedCharset
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.StringUtil.restrictCharacterSet
import org.apache.hc.core5.http.HttpRequest
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService

open class ProvidersChatClient(
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    val apiKeyMap: Map<APIProvider, String> = emptyMap(),
    val apiBaseMap: Map<APIProvider, String> = emptyMap(),
) : ChatClientBase(
    logLevel = logLevel,
    logStreams = logStreams,
    workPool = workPool,
), ChatClientInterface {

    init {
        require(apiKeyMap.size == apiBaseMap.size) {
            "API Key and Base URL maps must have the same size: ${apiKeyMap.size} != ${apiBaseMap.size}"
        }
        require(apiKeyMap.keys.toSet() == (apiBaseMap.keys.toSet())) {
            "API Key map must contain all API providers in API Base URL map: ${apiKeyMap.keys} != ${apiBaseMap.keys}"
        }
        require(apiBaseMap.isNotEmpty()) {
            "API base URLs must not be empty"
        }
    }

    companion object {
        val log = LoggerFactory.getLogger(ProvidersChatClient::class.java)
    }

    enum class ReasoningEffort {
        Low, Medium, High
    }

    private fun validateChatRequest(chatRequest: ChatRequest) {
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain at least one message" }
        require(!chatRequest.model.isNullOrBlank()) { "Model must be specified" }
    }

    override fun moderate(text: String): Unit = withReliability {
        validateModerationRequest(text)
        withPerformanceLogging {
            val body: String = try {
                JsonUtil.objectMapper().writeValueAsString(
                    mapOf(
                        "input" to restrictCharacterSet(text, allowedCharset)
                    )
                )
            } catch (e: JsonProcessingException) {
                log.error("Failed to serialize moderation request", e)
                throw RuntimeException("Failed to serialize moderation request", e)
            }

            val apiBase = apiBaseMap[APIProvider.OpenAI] ?: return@withPerformanceLogging

            val result: String = try {
                this.post("$apiBase/moderations", body, APIProvider.OpenAI)
            } catch (e: IOException) {
                log.error("Failed to execute moderation request", e)
                throw RuntimeException("Failed to execute moderation request", e)
            } catch (e: InterruptedException) {
                log.error("Moderation request was interrupted", e)
                throw RuntimeException("Moderation request was interrupted", e)
            }

            val jsonObject = try {
                Gson().fromJson(result, JsonObject::class.java)
                    ?: throw RuntimeException("Empty moderation response")
            } catch (e: Exception) {
                log.error("Failed to parse moderation response: $result", e)
                throw RuntimeException("Failed to parse moderation response", e)
            }

            if (jsonObject.has("error")) {
                val errorObject = jsonObject.getAsJsonObject("error")
                log.error("Moderation API error: ${errorObject["message"]?.asString}")
                throw RuntimeException("Moderation API error: ${errorObject["message"].asString}")
            }

            val moderationResult = jsonObject.getAsJsonArray("results")[0].asJsonObject
            if (moderationResult["flagged"].asBoolean) {
                val categoriesObj = moderationResult["categories"].asJsonObject
                val flaggedCategories = categoriesObj.keySet().filter { categoriesObj[it].asBoolean }
                log.warn("Content moderation flagged for categories: $flaggedCategories")
                throw RuntimeException(
                    ModerationException(
                        "Moderation flagged this request due to ${flaggedCategories.joinToString(", ")}"
                    )
                )
            }
        }
    }

    private fun validateModerationRequest(text: String) {
        require(text.isNotBlank()) { "Text to moderate cannot be blank" }
        require(text.length <= 32768) { "Text too long for moderation (max 32768 characters)" }
    }

    @Throws(IOException::class)
    override fun authorize(request: HttpRequest, apiProvider: APIProvider) {
        throw UnsupportedOperationException("Use specific client implementations for authorization")
    }

    override fun chat(
        chatRequest: ChatRequest, model: LLMModel,
        logStreams: MutableList<BufferedOutputStream>
    ): ChatResponse {
        validateChatRequest(chatRequest)
        return getProviderClient(model.provider).chat(chatRequest, model, logStreams)
    }

    private fun getProviderClient(apiProvider: APIProvider): ChatClientInterface {
        val apiKey = apiKeyMap[apiProvider]
            ?: throw IllegalStateException("API key not found for provider: $apiProvider")
        val apiBase = apiBaseMap[apiProvider]
            ?: throw IllegalStateException("API base not found for provider: $apiProvider")
        return when (apiProvider) {
            APIProvider.OpenAI -> OpenAIChatClient(apiKey, apiBase, workPool, logLevel, logStreams)
            APIProvider.DeepSeek -> DeepSeekChatClient(apiKey, workPool, apiBase, logLevel, logStreams)
            APIProvider.Google -> GoogleChatClient(apiKey, apiBase, workPool, logLevel, logStreams)
            APIProvider.Anthropic -> AnthropicChatClient(apiKey, workPool, apiBase, logLevel, logStreams)
            APIProvider.Mistral -> MistralChatClient(apiKey, workPool, logLevel, logStreams, apiBase)
            APIProvider.Groq -> GroqChatClient(apiKey, workPool, logLevel, logStreams, apiBase)
            APIProvider.ModelsLab -> ModelsLabChatClient(apiKey, apiBase, workPool, logLevel, logStreams)
            APIProvider.AWS -> AwsChatClient(apiKey, apiBase, workPool, logLevel, logStreams)
            else -> throw IllegalArgumentException("Unsupported API provider: $apiProvider")
        }.also { client ->
            client.session = this.session
            client.user = this.user
            client.budget = this.budget
        }
    }
}