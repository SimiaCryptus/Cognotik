package com.simiacryptus.jopenai.chat

import com.fasterxml.jackson.core.JsonProcessingException
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.jopenai.chat.AnthropicChatClient.Companion.fromAnthropicResponse
import com.simiacryptus.jopenai.chat.AnthropicChatClient.Companion.mapToAnthropicChatRequest
import com.simiacryptus.jopenai.chat.AwsChatClient.Companion.awsCredentials
import com.simiacryptus.jopenai.chat.AwsChatClient.Companion.fromAWS
import com.simiacryptus.jopenai.chat.AwsChatClient.Companion.toAWS
import com.simiacryptus.jopenai.chat.DeepSeekChatClient.Companion.toDeepSeek
import com.simiacryptus.jopenai.chat.GoogleChatClient.Companion.fromGemini
import com.simiacryptus.jopenai.chat.GoogleChatClient.Companion.toGeminiChatRequest
import com.simiacryptus.jopenai.chat.GroqChatClient.Companion.toGroq
import com.simiacryptus.jopenai.chat.ModelsLabChatClient.Companion.fromModelsLab
import com.simiacryptus.jopenai.chat.ModelsLabChatClient.Companion.toModelsLab
import com.simiacryptus.jopenai.exceptions.ModerationException
import com.simiacryptus.jopenai.models.*
import com.simiacryptus.jopenai.models.ApiModel.*
import com.simiacryptus.jopenai.models.chat.ChatModelType
import com.simiacryptus.jopenai.models.chat.LLMModel
import com.simiacryptus.jopenai.util.ClientUtil.allowedCharset
import com.simiacryptus.jopenai.util.ClientUtil.checkError
import com.simiacryptus.text.TextCompressor
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.StringUtil.restrictCharacterSet
import com.simiacryptus.util.runWithPermit
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore


open class ProvidersChatClient(
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    var reasoningEffort: ReasoningEffort = ReasoningEffort.Low,
    var textCompressor: TextCompressor? = TextCompressor(
        minLength = 25600,
        minOccurrences = 50
    ),
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

        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val HEADER_API_KEY = "x-api-key"
        const val HEADER_ANTHROPIC_VERSION = "anthropic-version"

        const val APPLICATION_JSON = "application/json"
        const val ANTHROPIC_API_VERSION = "2023-06-01"

        var modelsLabThrottle = Semaphore(1)
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
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
        val apiKey = apiKeyMap[apiProvider]
            ?: throw IllegalStateException("API key not found for provider: $apiProvider")
        require(apiKey.isNotBlank()) { "API key cannot be blank for provider: $apiProvider" }
        val apiBase = apiBaseMap[apiProvider]
            ?: throw IllegalStateException("API base not found for provider: $apiProvider")
        require(apiBase.isNotBlank()) { "API base cannot be blank for provider: $apiProvider" }

        log.debug("Authorizing request for session: {}, user: {}, apiProvider: {}", session, user, apiProvider)
        request.addHeader(HEADER_CONTENT_TYPE, APPLICATION_JSON)
        request.addHeader(HEADER_ACCEPT, APPLICATION_JSON)

        require(null == budget || budget!!.toDouble() > 0.0) { "Budget Exceeded" }

        when (apiProvider) {
            APIProvider.Google -> {
                // Google uses API key in query parameters
            }

            APIProvider.Anthropic -> {
                request.addHeader(HEADER_API_KEY, apiKey)
                request.addHeader(HEADER_ANTHROPIC_VERSION, ANTHROPIC_API_VERSION)
            }

            else -> request.addHeader(HEADER_AUTHORIZATION, "Bearer $apiKey")
        }
    }

    override fun chat(
        chatRequest: ChatRequest, model: LLMModel
    ): ChatResponse {
        validateChatRequest(chatRequest)

        val apiProvider = model.provider
        val apiKey = apiKeyMap[apiProvider]
            ?: throw IllegalStateException("API key not found for provider: $apiProvider")
        val apiBase = apiBaseMap[apiProvider]
            ?: throw IllegalStateException("API base not found for provider: $apiProvider")

        var chatRequest = chatRequest
        if (textCompressor != null) {
            chatRequest = chatRequest.copy(
                messages = chatRequest.messages.map {
                    it.let {
                        it.copy(
                            content = it.content?.map {
                                val compress = textCompressor!!.compress(it.text ?: "")
                                if (compress != it.text) {
                                    log.debug("Compressed message from ${it.text} to $compress")
                                }
                                it.copy(text = compress)
                            } ?: emptyList()
                        )
                    }
                }
            )
        }

        log.info("Starting chat with model: ${model.modelName}")

        if (!model.hasTemperature) {
            chatRequest = chatRequest.copy(
                messages = chatRequest.messages.map { message ->
                    if (message.role == Role.system) {
                        message.copy(role = Role.user)
                    } else {
                        message
                    }
                }, temperature = 1.0, stop = null
            )
            log.debug("Adjusted chat request for model: ${model.modelName}")
        }

        if (model.hasReasoningEffort && chatRequest.reasoning_effort == null) {
            chatRequest = chatRequest.copy(reasoning_effort = this@ProvidersChatClient.reasoningEffort.name.lowercase())
        }

        val requestID = UUID.randomUUID().toString()
        log.info("Chat request ID: $requestID with ${chatRequest.messages.size} messages")

        return withReliability {
            withPerformanceLogging {
                val result = when (apiProvider) {
                    APIProvider.DeepSeek -> handleDeepSeekChat(chatRequest, apiBase, requestID)
                    APIProvider.Google -> handleGoogleChat(chatRequest, model, apiBase, apiKey, requestID)
                    APIProvider.Anthropic -> handleAnthropicChat(chatRequest, model, apiBase, apiKey)
                    APIProvider.Perplexity -> handlePerplexityChat(chatRequest, apiBase, apiProvider, requestID)
                    APIProvider.Mistral -> handleMistralChat(chatRequest, apiBase, apiProvider, requestID)
                    APIProvider.Groq -> handleGroqChat(chatRequest, apiBase, apiProvider, requestID)
                    APIProvider.ModelsLab -> handleModelsLabChat(chatRequest, apiBase, apiProvider, requestID)
                    APIProvider.AWS -> handleAwsChat(chatRequest, model, apiKey)
                    else -> handleGenericChat(chatRequest, apiBase, apiProvider, requestID)
                }
                checkError(result)
                val response = JsonUtil.objectMapper().readValue(result, ChatResponse::class.java)
                if (response.usage != null && model is ChatModelType) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
                }
                log(
                    level = Level.DEBUG,
                    msg = String.format(
                        "Chat Completion %s:\n\t%s",
                        requestID,
                        response.choices.firstOrNull()?.message?.content?.trim { it <= ' ' }?.let { trim ->
                            trim.lineSequence().map {
                                when {
                                    it.isBlank() -> {
                                        when {
                                            it.length < "\t".length -> "\t"
                                            else -> it
                                        }
                                    }

                                    else -> "\t" + it
                                }
                            }.joinToString("\n")
                        } ?: JsonUtil.toJson(response)))
                response
            }
        }
    }

    private fun handleDeepSeekChat(chatRequest: ChatRequest, apiBase: String, requestID: String): String {
        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(toDeepSeek(chatRequest))
        return post("$apiBase/v1/chat/completions", json, APIProvider.DeepSeek, requestID = requestID)
    }

    private fun handleGoogleChat(
        chatRequest: ChatRequest,
        model: LLMModel,
        apiBase: String,
        apiKey: String,
        requestID: String
    ): String {
        val geminiChatRequest = toGeminiChatRequest(
            chatRequest
                .copy(messages = chatRequest.messages.map {
                    it.copy(
                        role = when (it.role) {
                            Role.system -> Role.user
                            else -> it.role
                        }
                    )
                }), model
        )
        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(geminiChatRequest)
        return fromGemini(
            post(
                "$apiBase/v1beta/models/${model.modelName}:generateContent?key=$apiKey",
                json,
                APIProvider.Google,
                requestID
            )
        )
    }

    private fun handleAnthropicChat(
        chatRequest: ChatRequest,
        model: LLMModel,
        apiBase: String,
        apiKey: String
    ): String {
        val anthropicChatRequest = mapToAnthropicChatRequest(chatRequest, model)
        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(anthropicChatRequest)
        val request = HttpPost("$apiBase/messages")
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        request.addHeader("x-api-key", apiKey)
        request.addHeader("anthropic-version", "2023-06-01")
        request.entity = StringEntity(json, Charsets.UTF_8, false)
        val rawResponse = post(request)
        return fromAnthropicResponse(rawResponse)
    }

    private fun handlePerplexityChat(
        chatRequest: ChatRequest,
        apiBase: String,
        apiProvider: APIProvider,
        requestID: String
    ): String {
        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(chatRequest.copy(stop = null))
        return post("$apiBase/chat/completions", json, apiProvider, requestID)
    }

    private fun handleMistralChat(
        chatRequest: ChatRequest,
        apiBase: String,
        apiProvider: APIProvider,
        requestID: String
    ): String {
        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(toGroq(chatRequest))
        return post("$apiBase/v1/chat/completions", json, apiProvider, requestID)
    }

    private fun handleGroqChat(
        chatRequest: ChatRequest,
        apiBase: String,
        apiProvider: APIProvider,
        requestID: String
    ): String {
        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
            .writeValueAsString(toGroq(chatRequest))
        return post("$apiBase/openai/v1/chat/completions", json, apiProvider, requestID)
    }

    private fun handleModelsLabChat(
        chatRequest: ChatRequest,
        apiBase: String,
        apiProvider: APIProvider,
        requestID: String
    ): String {
        return modelsLabThrottle.runWithPermit {
            modelsLabThrottle.runWithPermit {
                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(toModelsLab(chatRequest))
                fromModelsLab(post("$apiBase/llm/chat", json, apiProvider, requestID), this)
            }
        }
    }

    private fun handleAwsChat(chatRequest: ChatRequest, model: LLMModel, apiKey: String): String {
        val awsAuth = JsonUtil.fromJson<AwsChatClient.Companion.AWSAuth>(
            apiKey,
            AwsChatClient.Companion.AWSAuth::class.java
        )
        val invokeModelRequest = toAWS(model, chatRequest)
        val bedrockRuntimeClient =
            BedrockRuntimeClient.builder().credentialsProvider(awsCredentials(awsAuth))
                .region(Region.of(awsAuth.region)).build()
        val invokeModelResponse = bedrockRuntimeClient.invokeModel(invokeModelRequest)
        val responseBody = invokeModelResponse.body().asString(Charsets.UTF_8)
        return fromAWS(responseBody, model.modelName)
    }

    private fun handleGenericChat(
        chatRequest: ChatRequest,
        apiBase: String,
        apiProvider: APIProvider,
        requestID: String
    ): String {
        val json =
            JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(chatRequest)
        return post("$apiBase/chat/completions", json, apiProvider, requestID)
    }


}