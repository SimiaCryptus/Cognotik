package com.simiacryptus.jopenai

import com.fasterxml.jackson.core.JsonProcessingException
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.simiacryptus.jopenai.exceptions.ModerationException
import com.simiacryptus.jopenai.models.*
import com.simiacryptus.jopenai.models.ApiModel.*
import com.simiacryptus.jopenai.util.ClientUtil.allowedCharset
import com.simiacryptus.jopenai.util.ClientUtil.checkError
import com.simiacryptus.jopenai.util.ClientUtil.defaultApiProvider
import com.simiacryptus.jopenai.util.ClientUtil.keyMap
import com.simiacryptus.text.TextCompressor
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.StringUtil
import com.simiacryptus.util.runWithPermit
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore

open class ChatClient(
    protected var key: Map<APIProvider, String> = keyMap.mapKeys { APIProvider.valueOf(it.key) },
    protected val apiBase: Map<APIProvider, String> = APIProvider.values().associate { it to (it.base ?: "") },
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService = HttpClientManager.scheduledPool,
    workPool: ExecutorService = HttpClientManager.workPool,
    var reasoningEffort: ReasoningEffort = ReasoningEffort.Low,
    var textCompressor: TextCompressor? = TextCompressor(
        minLength = 256,
        minOccurrences = 5
    ),
) : HttpClientManager(
    logLevel = logLevel, logStreams = logStreams, scheduledPool = scheduledPool, workPool = workPool
) {

    enum class ReasoningEffort {
        Low, Medium, High
    }

    open var session: Any? = null
    open var user: Any? = null
    var budget: Number? = null

    private inner class ChildClient(
        key: Map<APIProvider, String> = this@ChatClient.key, apiBase: Map<APIProvider, String> = this@ChatClient.apiBase
    ) : ChatClient(
        key = key, apiBase = apiBase, logLevel = Level.INFO
    ) {
        init {
            session = this@ChatClient.session
            user = this@ChatClient.user
        }

        override fun log(level: Level, msg: String) {
            super.log(level, msg)
            this@ChatClient.log(level, msg)
        }
    }

    open fun getChildClient(): ChatClient = ChildClient()

    protected open fun onUsage(model: OpenAIModel?, tokens: Usage) {
        log.debug(
            "Usage recorded for session: {}, user: {}, model: {}, tokens: {}", session, user, model, tokens
        )
        if (null != budget) budget = budget!!.toDouble() - (tokens.cost ?: 0.0)
    }

    fun moderate(text: String) = withReliability {
        when (defaultApiProvider) {
            APIProvider.Groq -> return@withReliability
            APIProvider.ModelsLab -> return@withReliability
        }
        withPerformanceLogging {
            val body: String = try {
                JsonUtil.objectMapper().writeValueAsString(
                    mapOf(
                        "input" to StringUtil.restrictCharacterSet(text, allowedCharset)
                    )
                )
            } catch (e: JsonProcessingException) {
                throw RuntimeException(e)
            }
            val result: String = try {
                this.post("${apiBase[defaultApiProvider]}/moderations", body, defaultApiProvider)
            } catch (e: IOException) {
                throw RuntimeException(e)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            val jsonObject = Gson().fromJson(
                result, JsonObject::class.java
            ) ?: return@withPerformanceLogging
            if (jsonObject.has("error")) {
                val errorObject = jsonObject.getAsJsonObject("error")
                throw RuntimeException(IOException(errorObject["message"].asString))
            }
            val moderationResult = jsonObject.getAsJsonArray("results")[0].asJsonObject
            if (moderationResult["flagged"].asBoolean) {
                val categoriesObj = moderationResult["categories"].asJsonObject
                throw RuntimeException(
                    ModerationException(
                        "Moderation flagged this request due to " + categoriesObj.keySet().stream()
                            .filter { c: String? ->
                                categoriesObj[c].asBoolean
                            }.reduce { a: String, b: String -> "$a, $b" }.orElse("???")
                    )
                )
            }
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun post(
        url: String,
        json: String,
        apiProvider: APIProvider,
        requestID: String = UUID.randomUUID().toString()
    ): String {
        val request = HttpPost(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        authorize(request, apiProvider)
        request.entity = StringEntity(json, Charsets.UTF_8, false)
        return post(request, requestID = requestID)
    }

    private fun post(request: HttpPost, requestID: String = UUID.randomUUID().toString()): String = withClient<String> {
        log(
            level = Level.DEBUG,
            msg = String.format(
                "POST %s\nID:%s\nPrefix:\n\t%s\n%s\n",
                request.uri,
                requestID,
                EntityUtils.toString(request.entity).lineSequence().map {
                    when {
                        it.isBlank() -> {
                            when {
                                it.length < "\t".length -> "\t"
                                else -> it
                            }
                        }

                        else -> "\t" + it
                    }
                }.joinToString("\n"),
                captureCallerStack().lineSequence().map {
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
            )
        )
        EntityUtils.toString(it.execute(request).entity)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatClient::class.java)

        // common header names
        private const val HEADER_CONTENT_TYPE = "Content-Type"
        private const val HEADER_ACCEPT = "Accept"
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val HEADER_API_KEY = "x-api-key"
        private const val HEADER_ANTHROPIC_VERSION = "anthropic-version"

        // common header values
        private const val APPLICATION_JSON = "application/json"
        private const val ANTHROPIC_API_VERSION = "2023-06-01"

        var modelsLabThrottle = Semaphore(1)
        var modelslab_chatRequest_prototype = ModelsLabDataModel.ChatRequest(
            max_new_tokens = 1000,
            no_repeat_ngram_size = 5,
        )
    }

    @Throws(IOException::class)
    protected open fun authorize(request: HttpRequest, apiProvider: APIProvider) {
        log.debug("Authorizing request for session: {}, user: {}, apiProvider: {}", session, user, apiProvider)
        request.addHeader(HEADER_CONTENT_TYPE, APPLICATION_JSON)
        request.addHeader(HEADER_ACCEPT, APPLICATION_JSON)


        require(null == budget || budget!!.toDouble() > 0.0) { "Budget Exceeded" }
        when (apiProvider) {
            APIProvider.Google -> {
//        request.addHeader("X-goog-api-key", "${key.get(apiProvider)}")
            }

            APIProvider.Anthropic -> {
                request.addHeader(HEADER_API_KEY, "${key.get(apiProvider)}")
                request.addHeader(HEADER_ANTHROPIC_VERSION, ANTHROPIC_API_VERSION)
            }

            else -> request.addHeader(HEADER_AUTHORIZATION, "Bearer ${key.get(apiProvider)}")
        }
    }

    open fun chat(
        chatRequest: ChatRequest, model: TextModel
    ): ChatResponse {
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
            chatRequest = chatRequest.copy(reasoning_effort = this@ChatClient.reasoningEffort.name.lowercase())
        }
        val requestID = UUID.randomUUID().toString()
        log.info("Chat request ID: $requestID with ${chatRequest.messages.size} messages")
        if (chatRequest.messages.isEmpty()) {
            throw RuntimeException("No messages provided")
        }
        return withReliability {
            withPerformanceLogging {
                val apiProvider = model.provider
                val result = when {
                    apiProvider == APIProvider.DeepSeek -> {
                        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                            .writeValueAsString(toDeepSeek(chatRequest))
                        post("${apiBase[apiProvider]}/v1/chat/completions", json, apiProvider, requestID = requestID)
                    }

                    apiProvider == APIProvider.Google -> {
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
                        fromGemini(
                            post(
                                "${apiBase[apiProvider]}/v1beta/${model.modelName}:generateContent?key=${key[apiProvider]?.trim()}",
                                json,
                                apiProvider,
                                requestID
                            )
                        )
                    }

                    apiProvider == APIProvider.Anthropic -> {
                        val anthropicChatRequest = mapToAnthropicChatRequest(chatRequest, model)
                        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                            .writeValueAsString(anthropicChatRequest)
                        val request = HttpPost("${apiBase[apiProvider]}/messages")
                        request.addHeader("Content-Type", "application/json")
                        request.addHeader("Accept", "application/json")
                        request.addHeader("x-api-key", "${key.get(apiProvider)}")
                        request.addHeader("anthropic-version", "2023-06-01")
                        request.entity = StringEntity(json, Charsets.UTF_8, false)
                        val rawResponse = post(request)
                        fromAnthropicResponse(rawResponse)
                    }

                    apiProvider == APIProvider.Perplexity -> {
                        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                            .writeValueAsString(chatRequest.copy(stop = null))
                        post("${apiBase[apiProvider]}/chat/completions", json, apiProvider, requestID)
                    }

                    apiProvider == APIProvider.Mistral -> {
                        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                            .writeValueAsString(toGroq(chatRequest))
                        post("${apiBase[apiProvider]}/chat/completions", json, apiProvider, requestID)
                    }

                    apiProvider == APIProvider.Groq -> {
                        val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                            .writeValueAsString(toGroq(chatRequest))
                        post("${apiBase[apiProvider]}/chat/completions", json, apiProvider, requestID)
                    }

                    apiProvider == APIProvider.ModelsLab -> {
                        modelsLabThrottle.runWithPermit {
                            val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                                .writeValueAsString(toModelsLab(chatRequest))
                            fromModelsLab(post("${apiBase[apiProvider]}/llm/chat", json, apiProvider, requestID))
                        }
                    }

                    apiProvider == APIProvider.AWS -> {
                        val awsAuth = JsonUtil.fromJson<AWSAuth>(key[apiProvider]!!, AWSAuth::class.java)
                        val invokeModelRequest = toAWS(model, chatRequest)
                        val bedrockRuntimeClient =
                            BedrockRuntimeClient.builder().credentialsProvider(awsCredentials(awsAuth))
                                .region(Region.of(awsAuth.region)).build()
                        val invokeModelResponse = bedrockRuntimeClient.invokeModel(invokeModelRequest)
                        val responseBody = invokeModelResponse.body().asString(Charsets.UTF_8)
                        fromAWS(responseBody, model.modelName)
                    }

                    else -> {
                        val json =
                            JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(chatRequest)
                        post("${apiBase[apiProvider]}/chat/completions", json, apiProvider, requestID)
                    }
                }
                checkError(result)
                val response = JsonUtil.objectMapper().readValue(result, ChatResponse::class.java)
                if (response.usage != null) {
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

    private fun toDeepSeek(chatRequest: ChatRequest): Map<String, Any> {
        return mapOf(
            "model" to (chatRequest.model ?: throw RuntimeException("Model not specified")),
            "messages" to chatRequest.messages.map { message ->
                mapOf(
                    "role" to message.role.toString(),
                    "content" to (message.content?.joinToString("\n") { it.text ?: "" } ?: ""))
            },
            "temperature" to chatRequest.temperature,
            "max_tokens" to (chatRequest.max_tokens ?: 2048)
        )
    }

    private fun awsCredentials(awsAuth: AWSAuth): AwsCredentialsProviderChain? =
        AwsCredentialsProviderChain.builder().credentialsProviders(
            InstanceProfileCredentialsProvider.create(),
            ProfileCredentialsProvider.create(awsAuth.profile),
        ).build()

    private fun fromGemini(responseBody: String): String {
        val fromJson = JsonUtil.fromJson<GenerateContentResponse>(responseBody, GenerateContentResponse::class.java)
        return JsonUtil.toJson(
            ChatResponse(
                choices = fromJson.candidates?.mapIndexed { index, candidate ->
                    ChatChoice(
                        message = ChatMessageResponse(
                            content = candidate.content?.parts?.joinToString("\n") { it.text ?: "" }), index = index
                    )
                } ?: emptyList(),
            ))
    }

    private fun toGeminiChatRequest(chatRequest: ChatRequest, model: TextModel): GenerateContentRequest {
        return GenerateContentRequest(
            contents = collectRoleSequences(chatRequest.messages.filter {
                when (it.role) {
//                    Role.system -> false
                    else -> true
                }
            }.map {
                Content(
                    role = when (it.role) {
                        Role.user -> "user"
                        Role.system -> "user"
                        Role.assistant -> "model"
                        else -> throw RuntimeException("Unsupported role: ${it.role}")
                    }, parts = it.content?.map {
                        Part(
                            text = it.text
                        )
                    })
            }).map { collectTextParts(it) },
            generationConfig = GenerationConfig(temperature = chatRequest.temperature.toFloat())
        )
    }

    private fun collectTextParts(it: Content): Content {
        var text = ""
        val partsList = it.parts?.toMutableList() ?: mutableListOf()
        val newParts = mutableListOf<Part>()
        while (partsList.isNotEmpty()) {
            val parts = partsList.takeWhile { it.text != null }
            text = parts.joinToString("\n") { it.text ?: "" }
            partsList.removeAll(parts)
            newParts.add(Part(text = text))
            // Copy all non-text parts
            val nonTextParts = partsList.takeWhile { it.text == null }
            newParts.addAll(nonTextParts)
            partsList.removeAll(nonTextParts)
        }
        return Content(parts = newParts)
    }

    private fun collectRoleSequences(map: List<Content>): List<Content> {
        val alternatingMessages = mutableListOf<Content>()
        val messagesCopy = map.toMutableList()
        while (messagesCopy.isNotEmpty()) {
            val thisRole = messagesCopy.firstOrNull()?.role
            val toConsolidate = messagesCopy.takeWhile { it.role == thisRole }.toTypedArray()
            messagesCopy.removeAll(toConsolidate)
            val consolidatedMessage = toConsolidate.reduceOrNull { acc, chatMessage ->
                Content(
                    role = acc.role, parts = acc.parts?.plus(chatMessage.parts ?: emptyList()) ?: chatMessage.parts
                )
            }
            alternatingMessages.add(consolidatedMessage ?: Content())
        }
        return alternatingMessages

    }

    private data class GenerateContentRequest(
        val model: String? = null,
        val contents: List<Content>? = null,
        val system_instruction: Content? = null,
        val safetySettings: List<SafetySetting>? = null,
        val generationConfig: GenerationConfig? = null
    )

    private data class Content(
        val role: String? = null, val parts: List<Part>? = null
    )

    private data class Part(
        val inlineData: Blob? = null, val text: String? = null
    )

    private data class Blob(
        val mimeType: String? = null, val data: String? = null
    )

    private data class SafetySetting(
        val threshold: String? = null, val category: String? = null
    )

    private data class GenerationConfig(
        val temperature: Float? = null,
        val candidateCount: Int? = null,
        val topK: Int? = null,
        val maxOutputTokens: Int? = null,
        val topP: Float? = null,
        val stopSequences: List<String>? = null
    )

    private data class GenerateContentResponse(
        val candidates: List<Candidate>? = null
    )

    private data class Candidate(
        val content: Content? = null, // Reuse or adjust your existing Content class
        val finishReason: String? = null, val index: Int? = null, val safetyRatings: List<SafetyRating>? = null
    )

    private data class SafetyRating(
        val category: String? = null, val probability: String? = null
    )

    private fun mapToAnthropicChatRequest(chatRequest: ChatRequest, model: TextModel): AnthropicChatRequest {
        return AnthropicChatRequest(
            model = chatRequest.model,
            system = chatRequest.messages.firstOrNull { it.role == Role.system }?.content?.joinToString("\n\n") {
                it.text ?: ""
            },
            messages = alternateAnthropicRoles(chatRequest.messages.filter { it.role != Role.system }),
            max_tokens = chatRequest.max_tokens ?: model.maxOutTokens,
            temperature = chatRequest.temperature,
        )
    }

    private fun alternateAnthropicRoles(messages: List<ChatMessage>): List<AnthropicMessage> {
        val alternatingMessages = mutableListOf<AnthropicMessage>()
        val remainingMessages = messages.toMutableList()
        while (remainingMessages.isNotEmpty()) {
            val thisRole = remainingMessages.firstOrNull()?.role
            val toConsolidate = remainingMessages.takeWhile { it.role == thisRole }.toTypedArray()
            remainingMessages.removeAll(toConsolidate)
            alternatingMessages += AnthropicMessage(
                role = thisRole.toString(),
                content = toConsolidate.joinToString("\n\n") { it.content?.joinToString("\n") { it.text ?: "" } ?: "" })
        }
        return alternatingMessages
    }

    private data class AnthropicChatRequest(
        val model: String? = null,
        val system: String? = null,
        val messages: List<AnthropicMessage>? = null,
        val max_tokens: Int? = null,
        val temperature: Double? = null,
        val top_p: Double? = null,
        val top_k: Int? = null
    )

    private data class AnthropicMessage(
        val role: String? = null, val content: String? = null
    )

    private data class AnthropicResponse(
        val id: String? = null,
        val type: String? = null,
        val role: String? = null,
        val content: List<AnthropicContentBlock>? = null,
        val model: String? = null,
        val stop_reason: String? = null,
        val stop_sequence: String? = null,
        val usage: AnthropicUsage? = null
    )

    private data class AnthropicContentBlock(
        val type: String? = null, val text: String? = null
    )

    private data class AnthropicUsage(
        val input_tokens: Int? = null, val output_tokens: Int? = null
    )

    private data class AWSAuth(
        val profile: String = "default",
        val region: String = Region.US_WEST_2.id(),
    )

    private fun toAWS(model: TextModel, chatRequest: ChatRequest) =
        InvokeModelRequest.builder().modelId(model.modelName).accept("application/json").contentType("application/json")
            .body(SdkBytes.fromString(JsonUtil.toJson(awsBody(model, chatRequest)), Charsets.UTF_8)).build()

    private fun awsBody(
        model: TextModel, chatRequest: ChatRequest
    ): Map<String, Any> = when {
        model.modelName.contains("llama") -> {
            mapOf(
                "prompt" to toSimplePrompt(chatRequest),
                "max_gen_len" to model.maxOutTokens,
                "temperature" to chatRequest.temperature,
//        "top_p" to 0.9,
            )
        }

        //mistral
        model.modelName.contains("mistral") -> {
            mapOf(
                "prompt" to toSimplePrompt(chatRequest),
                "max_tokens" to model.maxOutTokens,
                "temperature" to chatRequest.temperature,
//        "top_p" to 0.9,
//        "top_k" to 50,
            )
        }

        model.modelName.contains("titan") -> {
            mapOf(
                "inputText" to toSimplePrompt(chatRequest), "textGenerationConfig" to mapOf(
                    "maxTokenCount" to model.maxTotalTokens,
                    "stopSequences" to emptyList<String>(),
                    "temperature" to chatRequest.temperature,
//          "topP" to 0.9,
                )
            )
        }

        model.modelName.contains("cohere") -> {
            mapOf(
                "prompt" to toSimplePrompt(chatRequest),
                "max_tokens" to model.maxTotalTokens,
                "temperature" to chatRequest.temperature,
//        "p" to 1,
//        "k" to 0,
            )
        }

        model.modelName.contains("ai21") -> {
            mapOf(
                "prompt" to toSimplePrompt(chatRequest),
                "maxTokens" to model.maxTotalTokens,
                "temperature" to chatRequest.temperature,
//        "topP" to 0.9,
                "stopSequences" to emptyList<String>(),
                "countPenalty" to mapOf("scale" to 0),
                "presencePenalty" to mapOf("scale" to 0),
                "frequencyPenalty" to mapOf("scale" to 0),
            )
        }

        model.modelName.contains("anthropic") -> {
            val alternatingMessages = alternateMessagesRoles(chatRequest.messages)
            mapOf(
                "anthropic_version" to anthropic_version(model),
                "max_tokens" to model.maxOutTokens,
                "temperature" to chatRequest.temperature,
                "messages" to alternatingMessages.filter {
                    when (it.role) {
                        Role.system -> false
                        else -> true
                    }
                }.map {
                    mapOf(
                        "role" to it.role.toString(), "content" to it.content?.map {
                            mapOf(
                                "type" to "text", "text" to it.text
                            )
                        })
                },
                "system" to toSimplePrompt(chatRequest) { it.role == Role.system },
            ).filterValues { it != null }
        }


        else -> throw RuntimeException("Unsupported model: $model")
    }

    private fun anthropic_version(model: TextModel) = when {
        else -> "bedrock-2023-05-31"
//    else -> null
    }

    private fun alternateMessagesRoles(messages: List<ChatMessage>): List<ChatMessage> {
        val alternatingMessages = mutableListOf<ChatMessage>()
        val messagesCopy = messages.toMutableList()
        var isFirst = true
        while (messagesCopy.isNotEmpty()) {
            val thisRole = messagesCopy.firstOrNull()?.role
            val consolidatedMessage = takeAll(messagesCopy, thisRole)
            if (isFirst) {
                isFirst = false
                if ((consolidatedMessage?.role ?: "") != "user") {
                    val chatMessage = takeAll(messagesCopy, Role.user)
                    alternatingMessages.add(
                        concat(
                            (consolidatedMessage ?: ChatMessage()).copy(role = Role.user), chatMessage ?: ChatMessage()
                        )
                    )
                    continue
                }
            }
            alternatingMessages.add(consolidatedMessage ?: ChatMessage())
        }
        return alternatingMessages
    }

    private fun takeAll(
        messagesCopy: MutableList<ChatMessage>, thisRole: Role?
    ): ChatMessage? {
        val toConsolidate = messagesCopy.takeWhile { it.role == thisRole }.toTypedArray()
        messagesCopy.removeAll(toConsolidate)
        val consolidatedMessage = toConsolidate.reduceOrNull { acc, chatMessage ->
            concat(acc, chatMessage)
        }
        return consolidatedMessage
    }

    private fun concat(
        acc: ChatMessage, chatMessage: ChatMessage
    ) = ChatMessage(
        role = acc.role, content = listOf(
            ContentPart(
                type = "text",
                text = (acc.content?.plus(chatMessage.content ?: emptyList())
                    ?: chatMessage.content)?.joinToString("\n") { it.text ?: "" })
        )
    )

    private fun toSimplePrompt(
        chatRequest: ChatRequest, filterFn: (ChatMessage) -> Boolean = { true }
    ) = if (chatRequest.messages.filter(filterFn).map { it.role }.distinct().size <= 1) {
        chatRequest.messages.filter(filterFn).joinToString("\n\n") {
            it.content?.joinToString("\n") { it.text ?: "" } ?: ""
        }
    } else {
        chatRequest.messages.filter(filterFn).joinToString("\n\n") {
            "${it.role}: \n" + it.content?.joinToString("\n") { "\t" + (it.text ?: "") }
        }
    }

    private fun fromAWS(responseBody: String, model: String): String {
        return when {
            model.contains("llama") -> {
                val fromJson = JsonUtil.fromJson<AwsResponseLlama2>(responseBody, AwsResponseLlama2::class.java)
                JsonUtil.toJson(
                    ChatResponse(
                        choices = listOf(
                            ChatChoice(
                                message = ChatMessageResponse(
                                    content = fromJson.generation ?: ""
                                ), index = 0
                            )
                        ), usage = Usage(
                            prompt_tokens = fromJson.prompt_token_count?.toLong() ?: 0,
                            completion_tokens = fromJson.generation_token_count?.toLong() ?: 0,
                            total_tokens = (fromJson.prompt_token_count?.toLong()
                                ?: 0) + (fromJson.generation_token_count?.toLong() ?: 0)
                        )
                    )
                )
            }

            model.contains("mistral") -> {
                val fromJson = JsonUtil.fromJson<AwsResponseMistral>(responseBody, AwsResponseMistral::class.java)
                JsonUtil.toJson(
                    ChatResponse(
                        choices = listOf(
                            ChatChoice(
                                message = ChatMessageResponse(
                                    content = fromJson.outputs.first().text ?: ""
                                ), index = 0
                            )
                        )
                    )
                )
            }

            model.contains("titan") -> {
                val fromJson = JsonUtil.fromJson<AwsResponseTitan>(responseBody, AwsResponseTitan::class.java)
                JsonUtil.toJson(
                    ChatResponse(
                        choices = listOf(
                            ChatChoice(
                                message = ChatMessageResponse(
                                    content = fromJson.results.first().outputText ?: ""
                                ), index = 0
                            )
                        )
                    )
                )
            }

            model.contains("cohere") -> {
                val fromJson = JsonUtil.fromJson<AwsResponseCohere>(responseBody, AwsResponseCohere::class.java)
                JsonUtil.toJson(
                    ChatResponse(
                        choices = listOf(
                            ChatChoice(
                                message = ChatMessageResponse(
                                    content = fromJson.generations.first().text ?: ""
                                ), index = 0
                            )
                        )
                    )
                )
            }

            model.contains("ai21") -> {
                val fromJson = JsonUtil.objectMapper().readValue(responseBody, Ai21ChatResponse::class.java)
                return JsonUtil.toJson(
                    ChatResponse(
                        choices = fromJson.completions?.mapIndexed { index, completion ->
                            ChatChoice(
                                message = ChatMessageResponse(
                                    content = completion.data?.text ?: ""
                                ), index = index
                            )
                        } ?: emptyList(),
                    ))
            }

            model.contains("anthropic") -> {
                val fromJson = JsonUtil.fromJson<AwsResponseAnthropic>(responseBody, AwsResponseAnthropic::class.java)
                JsonUtil.toJson(
                    ChatResponse(
                        choices = listOf(
                            ChatChoice(
                                message = ChatMessageResponse(
                                    content = fromJson.content?.firstOrNull()?.text ?: ""
                                ), index = 0
                            )
                        ), usage = Usage(
                            prompt_tokens = fromJson.usage?.input_tokens?.toLong() ?: 0,
                            completion_tokens = fromJson.usage?.output_tokens?.toLong() ?: 0,
                            total_tokens = (fromJson.usage?.input_tokens?.toLong()
                                ?: 0) + (fromJson.usage?.output_tokens ?: 0)
                        )
                    )
                )
            }

            else -> throw RuntimeException("Unsupported model: $model")
        }
    }

    private data class AwsResponseAnthropic(
        val id: String? = null,
        val type: String? = null,
        val role: String? = null,
        val content: List<AwsResponseAnthropicContent>? = null,
        val model: String? = null,
        val stop_reason: String? = null,
        val stop_sequence: String? = null,
        val usage: AwsResponseAnthropicUsage? = null
    )

    private data class AwsResponseAnthropicContent(
        val type: String? = null, val text: String? = null
    )

    private data class AwsResponseAnthropicUsage(
        val input_tokens: Int? = null, val output_tokens: Int? = null
    )

    private data class Ai21ChatResponse(
        val id: Int? = null, val prompt: Ai21Prompt? = null, val completions: List<Ai21Completion>? = null
    )

    private data class Ai21Completion(
        val data: Ai21Data? = null, val finishReason: Ai21FinishReason? = null
    )

    private data class Ai21FinishReason(
        val reason: String? = null
    )

    private data class Ai21Data(
        val text: String? = null, val tokens: List<Ai21Token>? = null
    )

    private data class Ai21Prompt(
        val text: String? = null, val tokens: List<Ai21Token>? = null
    )

    private data class Ai21Token(
        val generatedToken: Ai21GeneratedToken? = null,
        val topTokens: List<Ai21TopToken>? = null,
        val textRange: Ai21TextRange? = null
    )

    private data class Ai21GeneratedToken(
        val token: String? = null, val logprob: Double? = null, val raw_logprob: Double? = null
    )

    private data class Ai21TopToken(
        val token: String? = null, val logprob: Double? = null, val raw_logprob: Double? = null
    )

    private data class Ai21TextRange(
        val start: Int? = null, val end: Int? = null
    )

    private data class AwsResponseCohere(
        val generations: List<AwsResponseCohereGeneration>
    )

    private data class AwsResponseCohereGeneration(
        val text: String? = null
    )

    private data class AwsResponseMistral(
        val outputs: List<AwsResponseMistralOutput>
    )

    private data class AwsResponseMistralOutput(
        val text: String? = null, val stop_reason: String? = null
    )

    private data class AwsResponseTitan(
        val inputTextTokenCount: Int? = null, val results: List<AwsResponseTitanResult>
    )

    private data class AwsResponseTitanResult(
        val tokenCount: Int? = null, val outputText: String? = null, val completionReason: String? = null
    )

    private data class AwsResponseLlama2(
        val generation: String? = null,
        val prompt_token_count: Int? = null,
        val generation_token_count: Int? = null,
        val stop_reason: String? = null
    )

    private fun fromAnthropicResponse(rawResponse: String): String {
        try {
            val errorCheck = JsonUtil.objectMapper().readTree(rawResponse)
            if (errorCheck.has("type") && errorCheck.get("type").asText() == "error") {
                throw RuntimeException("Error response received: $rawResponse")
            }
            val response = JsonUtil.objectMapper().readValue(rawResponse, AnthropicResponse::class.java)
            return JsonUtil.toJson(
                ChatResponse(
                    id = response.id, choices = listOf(
                        ChatChoice(
                            message = ChatMessageResponse(
                                content = response.content?.joinToString("\n") { it.text ?: "" }), index = 0
                        )
                    ), usage = Usage(
                        prompt_tokens = response.usage?.input_tokens?.toLong() ?: 0,
                        completion_tokens = response.usage?.output_tokens?.toLong() ?: 0,
                        total_tokens = (response.usage?.input_tokens?.toLong() ?: 0) + (response.usage?.output_tokens
                            ?: 0),
                    )
                )
            )
        } catch (e: Exception) {
            throw RuntimeException("Error parsing Anthropic response: $rawResponse", e)
        }
    }

    private fun fromModelsLab(rawResponse: String): String {
        val response = JsonUtil.objectMapper().readValue(rawResponse, ModelsLabDataModel.ChatResponse::class.java)
        return when (response.status) {
            "success" -> {
                JsonUtil.toJson(
                    ChatResponse(
                        id = response.chat_id, choices = listOf(
                            ChatChoice(
                                message = ChatMessageResponse(content = response.message), index = 0
                            )
                        ), usage = response.meta?.let {
                            Usage(
                                prompt_tokens = it.max_new_tokens?.toLong() ?: 0,
                                completion_tokens = 0, // Assuming no direct mapping; adjust as needed.
                                total_tokens = it.max_new_tokens?.toLong() ?: 0
                            )
                        })
                )
            }

            "processing" -> {
                val seconds = response?.eta ?: 1
                log.info("Chat response is still processing; waiting ${seconds}s and trying again.")
                Thread.sleep(seconds * 1000L)
                val postCheck = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                    mapOf(
                        "chat_id" to (response.meta?.chat_id ?: response.chat_id), "key" to key[APIProvider.ModelsLab]
                    )
                )
                fromModelsLab(
                    post(
                        "${apiBase[defaultApiProvider]}/llm/get_queued_response",
                        postCheck,
                        defaultApiProvider,
                    )
                )
            }

            "error" -> {
                throw RuntimeException("Error in chat request: ${response.message}\n$rawResponse")
            }

            "failed" -> {
                throw RuntimeException("Chat request failed: ${response.message}\n$rawResponse")
            }

            else -> throw RuntimeException("Unknown status: ${response.status}\n${response.message}\n$rawResponse")
        }
    }

    private fun toModelsLab(chatRequest: ChatRequest) = modelslab_chatRequest_prototype.copy(
        key = key[APIProvider.ModelsLab],
        model_id = chatRequest.model,
        system_prompt = chatRequest.messages.filter { it.role == Role.system }.joinToString("\n") {
            it.content?.joinToString("\n") { it.text ?: "" } ?: ""
        },
        prompt = chatRequest.messages.filter { it.role != Role.system }.joinToString("\n") {
            it.content?.joinToString("\n") { it.text ?: "" } ?: ""
        },
        temperature = chatRequest.temperature,
    )

    private fun toGroq(chatRequest: ChatRequest): GroqChatRequest = GroqChatRequest(
        messages = chatRequest.messages.map { message ->
            GroqChatMessage(
                role = message.role,
                content = message.content?.joinToString("\n") { it.text ?: "" } ?: "",
            )
        },
        model = chatRequest.model,
        max_tokens = chatRequest.max_tokens,
        temperature = chatRequest.temperature,
    )


}