# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/AnthropicChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class AnthropicChatClient(
    apiKey: SecureString,
    workPool: ExecutorService,
    apiBase: String,
    logLevel: Level,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService,
) : SingleProviderChatClient(
    APIProvider.Anthropic,
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
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        request.addHeader("x-api-key", apiKey.decrypt)
        request.addHeader("anthropic-version", "2023-06-01")
    }

    override fun getModels(): List<ChatModel>? {
        // Check cache first
        modelsCache[apiBase]?.let { return it }

        return try {
            val modelsResponse = fetchAllModels()
            val models = modelsResponse.mapNotNull { modelInfo ->
                val models = AnthropicModels.values.values
                    .filter { it.name == modelInfo.id || it.modelName == modelInfo.id }
                when {
                    models.size == 1 -> models.first()
                    else -> {
                        log.debug("Unknown Anthropic model: ${modelInfo.id}")
                        ChatModel(
                            name = modelInfo.display_name,
                            modelName = modelInfo.id,
                            provider = APIProvider.Anthropic,
                            maxTotalTokens = 200000,
                            maxOutTokens = 64000,
                            inputTokenPricePerK = 0.0, // TODO: Set actual pricing if known
                            outputTokenPricePerK = 0.0 // TODO: Set actual pricing if known
                        )
                    }
                }
            }
            // Cache the result
            modelsCache[apiBase] = models
            models
        } catch (e: Exception) {
            log.error("Failed to fetch Anthropic models", e)
            null
        }
    }


    private fun fetchAllModels(): List<ModelInfo> {
        val allModels = mutableListOf<ModelInfo>()
        var hasMore = true
        var afterId: String? = null
        val limit = 100 // Use a larger limit to reduce API calls
        while (hasMore) {
            val queryParams = mutableListOf<String>()
            queryParams.add("limit=$limit")
            afterId?.let { queryParams.add("after_id=${URLEncoder.encode(it, "UTF-8")}") }
            val queryString = if (queryParams.isNotEmpty()) "?${queryParams.joinToString("&")}" else ""
            val response = get("${apiBase}/models$queryString")
            checkError(response)
            log.debug("Anthropic models response: $response")
            val listResponse = JsonUtil.objectMapper().readValue(response, ListModelsResponse::class.java)
            allModels.addAll(listResponse.data)
            hasMore = listResponse.has_more
            afterId = listResponse.last_id
        }
        return allModels
    }


    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        validateChatRequest(chatRequest, model)
        return withReliability {
            withPerformanceLogging {
                val anthropicChatRequest = try {
                    val chatMessages = chatRequest.messages
                    require(chatMessages.isNotEmpty()) { "Messages cannot be empty" }
                    require(model.modelName?.isNotBlank() == true) { "Model name cannot be blank" }
                    val max_tokens = chatRequest.max_tokens ?: model.maxOutTokens
                    val model = chatRequest.model ?: model.modelName
                    val temperature = chatRequest.temperature
                    val system = chatMessages
                        .firstOrNull { it.role == ModelSchema.Role.system }
                        ?.content
                        ?.joinToString("\n\n") { it.text.orEmpty() }
                    val messages = chatMessages
                        .filter { it.role != ModelSchema.Role.system }
                        .let {
                            if (it.isEmpty()) emptyList()
                            else {
                                val alternatingMessages = mutableListOf<AnthropicMessage>()
                                val remainingMessages = it.toMutableList()
                                while (remainingMessages.isNotEmpty()) {
                                    val thisRole = remainingMessages.firstOrNull()?.role
                                    val toConsolidate = remainingMessages.takeWhile { it.role == thisRole }
                                        .toTypedArray<ModelSchema.ChatMessage>()
                                    remainingMessages.removeAll(toConsolidate)
                                    alternatingMessages += AnthropicMessage(
                                        role = thisRole.toString(),
                                        content = toConsolidate.joinToString("\n\n") {
                                            it.content?.joinToString("\n") { it.text.orEmpty() }.orEmpty()
                                        })
                                }
                                alternatingMessages
                            }
                        }
                        .filter { !it.content.isNullOrBlank() }
                    AnthropicChatRequest(
                        model = model,
                        system = system,
                        messages = messages,
                        max_tokens = max_tokens,
                        temperature = temperature,
                    )
                } catch (e: Exception) {
                    log.error("Failed to map chat request to Anthropic format", e)
                    throw RuntimeException("Failed to map chat request to Anthropic format: ${e.message}", e)
                }
                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(anthropicChatRequest)
                val rawResponse = post("${apiBase}/messages", json, APIProvider.Anthropic)
                checkError(rawResponse)
                val responseJson = try {
                    require(rawResponse.isNotBlank()) { "Response cannot be blank" }
                    try {
                        val errorCheck = JsonUtil.objectMapper().readTree(rawResponse)
                        if (errorCheck.has("type") && errorCheck.get("type").asText() == "error") {
                            val errorMessage = if (errorCheck.has("message")) {
                                errorCheck.get("message").asText()
                            } else if (errorCheck.has("error") && errorCheck.get("error").has("message")) {
                                errorCheck.get("error").get("message").asText()
                            } else {
                                "Unknown error: $errorCheck"
                            }
                            throw RuntimeException("Anthropic API error: $errorMessage")
                        }
                        val response = JsonUtil.objectMapper().readValue(rawResponse, AnthropicResponse::class.java)
                        val chatResponse = ModelSchema.ChatResponse(
                            id = response.id, choices = listOf(
                                ModelSchema.ChatChoice(
                                    message = ModelSchema.ChatMessageResponse(
                                        content = response.content?.joinToString("\n") { it.text ?: "" },
                                    ), index = 0
                                )
                            ), usage = ModelSchema.Usage(
                                prompt_tokens = response.usage?.input_tokens?.toLong() ?: 0,
                                completion_tokens = response.usage?.output_tokens?.toLong() ?: 0,
                                total_tokens = (response.usage?.input_tokens?.toLong()
                                    ?: 0) + (response.usage?.output_tokens
                                    ?: 0),
                            )
                        )
                        JsonUtil.toJson(chatResponse)
                    } catch (e: Exception) {
                        log.error("Failed to parse Anthropic response", e)
                        throw RuntimeException("Error parsing Anthropic response", e)
                    }
                } catch (e: Exception) {
                    log.error("Failed to parse Anthropic response: $rawResponse", e)
                    throw RuntimeException("Failed to parse Anthropic response: ${e.message}", e)
                }
                val response = JsonUtil.objectMapper().readValue(responseJson, ModelSchema.ChatResponse::class.java)
                if (response.usage != null) {
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
                }
                response
            }
        }
    }

    private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
        require(model.modelName?.isNotBlank() == true) { "Model name cannot be blank" }
        require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
    }


    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(AnthropicChatClient::class.java)
        private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

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

        data class ModelInfo(
            val id: String,
            val type: String = "model",
            val display_name: String,
            val created_at: String
        )

        data class ListModelsResponse(
            val data: List<ModelInfo>,
            val first_id: String?,
            val last_id: String?,
            val has_more: Boolean
        )


    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/AwsChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrock.BedrockClient
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import java.io.BufferedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class AwsChatClient(
    apiKey: SecureString,
    apiBase: String,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : SingleProviderChatClient(
    provider = APIProvider.AWS,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
) {
    private val awsAuth: AWSAuth by lazy {
        JsonUtil.fromJson(apiKey.decrypt, AWSAuth::class.java)
    }
    private val bedrockClient: BedrockRuntimeClient by lazy {
        BedrockRuntimeClient.builder().credentialsProvider(awsCredentials(awsAuth)).region(Region.of(awsAuth.region))
            .build()
    }
    private val bedrockManagementClient: BedrockClient by lazy {
        BedrockClient.builder()
            .credentialsProvider(awsCredentials(awsAuth))
            .region(Region.of(awsAuth.region))
            .build()
    }

    override fun getModels(): List<ChatModel>? {
        // Check cache first
        val cacheKey = "${awsAuth.region}:${awsAuth.profile}"
        modelsCache[cacheKey]?.let { return it }

        return try {
            log.info("Fetching available models from AWS Bedrock in region: ${awsAuth.region}")

            val request = ListFoundationModelsRequest.builder().build()
            val response = bedrockManagementClient.listFoundationModels(request)

            val models = response.modelSummaries()?.mapNotNull { modelSummary ->
                try {
                    mapAwsModelToChatModel(modelSummary)
                } catch (e: Exception) {
                    log.warn("Failed to map AWS model ${modelSummary.modelId()}: ${e.message}")
                    null
                }
            } ?: emptyList()

            log.info("Found ${models.size} available models in AWS Bedrock")

            // Cache the result
            models.takeIf { it.isNotEmpty() }?.let {
                modelsCache[cacheKey] = it
            }

            models
        } catch (e: Exception) {
            log.error("Failed to fetch models from AWS Bedrock: ${e.message}", e)
            // Return a default list of known AWS models as fallback
            getDefaultAwsModels()
        }
    }

    private fun mapAwsModelToChatModel(modelSummary: FoundationModelSummary): ChatModel? {
        val modelId = modelSummary.modelId() ?: return null
        val (maxTokens, maxOutTokens, inputPrice, outputPrice) = getModelSpecifications(modelId)
        return ChatModel(
            name = modelSummary.modelName() ?: modelId,
            modelName = modelId,
            maxTotalTokens = maxTokens,
            maxOutTokens = maxOutTokens,
            provider = APIProvider.AWS,
            inputTokenPricePerK = inputPrice,
            outputTokenPricePerK = outputPrice
        )
    }

    private fun getModelSpecifications(modelId: String): ModelSpecs {
        return when {
            // Anthropic Claude models
            modelId.contains("claude-3-opus") -> ModelSpecs(200000, 4096, 0.015, 0.075)
            modelId.contains("claude-3-sonnet") -> ModelSpecs(200000, 4096, 0.003, 0.015)
            modelId.contains("claude-3-haiku") -> ModelSpecs(200000, 4096, 0.00025, 0.00125)
            modelId.contains("claude-2.1") -> ModelSpecs(200000, 4096, 0.008, 0.024)
            modelId.contains("claude-2") -> ModelSpecs(100000, 4096, 0.008, 0.024)
            modelId.contains("claude-instant") -> ModelSpecs(100000, 4096, 0.0008, 0.0024)

            // Meta Llama models
            modelId.contains("llama3-70b") -> ModelSpecs(8192, 2048, 0.00265, 0.0035)
            modelId.contains("llama3-8b") -> ModelSpecs(8192, 2048, 0.0003, 0.0006)
            modelId.contains("llama2-70b") -> ModelSpecs(4096, 2048, 0.00195, 0.00256)
            modelId.contains("llama2-13b") -> ModelSpecs(4096, 2048, 0.00075, 0.001)

            // Mistral models
            modelId.contains("mistral-large") -> ModelSpecs(32000, 8192, 0.008, 0.024)
            modelId.contains("mixtral-8x7b") -> ModelSpecs(32000, 4096, 0.00045, 0.0007)
            modelId.contains("mistral-7b") -> ModelSpecs(32000, 4096, 0.00015, 0.0002)

            // Amazon Titan models
            modelId.contains("titan-text-express") -> ModelSpecs(8192, 8192, 0.0002, 0.0006)
            modelId.contains("titan-text-lite") -> ModelSpecs(4096, 4096, 0.00015, 0.0002)
            modelId.contains("titan-text-premier") -> ModelSpecs(32000, 3072, 0.0005, 0.0015)

            // Cohere models
            modelId.contains("command-r-plus") -> ModelSpecs(128000, 4096, 0.003, 0.015)
            modelId.contains("command-r") -> ModelSpecs(128000, 4096, 0.0005, 0.0015)
            modelId.contains("command-text") -> ModelSpecs(4096, 4096, 0.0015, 0.002)
            modelId.contains("command-light") -> ModelSpecs(4096, 4096, 0.0003, 0.0006)

            // AI21 models
            modelId.contains("j2-ultra") -> ModelSpecs(8192, 8192, 0.0125, 0.0125)
            modelId.contains("j2-mid") -> ModelSpecs(8192, 8192, 0.0125, 0.0125)

            // Default values for unknown models
            else -> ModelSpecs(4096, 2048, 0.001, 0.002)
        }
    }

    private fun getDefaultAwsModels(): List<ChatModel> {
        // Return a list of commonly available AWS Bedrock models as fallback
        return listOf(
            ChatModel(
                name = "Claude 3 Sonnet",
                modelName = "anthropic.claude-3-sonnet-20240229-v1:0",
                maxTotalTokens = 200000,
                maxOutTokens = 4096,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.003,
                outputTokenPricePerK = 0.015
            ),
            ChatModel(
                name = "Claude 3 Haiku",
                modelName = "anthropic.claude-3-haiku-20240307-v1:0",
                maxTotalTokens = 200000,
                maxOutTokens = 4096,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.00025,
                outputTokenPricePerK = 0.00125
            ),
            ChatModel(
                name = "Llama 3 70B Instruct",
                modelName = "meta.llama3-70b-instruct-v1:0",
                maxTotalTokens = 8192,
                maxOutTokens = 2048,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.00265,
                outputTokenPricePerK = 0.0035
            ),
            ChatModel(
                name = "Mistral 7B Instruct",
                modelName = "mistral.mistral-7b-instruct-v0:2",
                maxTotalTokens = 32000,
                maxOutTokens = 4096,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.00015,
                outputTokenPricePerK = 0.0002
            ),
            ChatModel(
                name = "Amazon Titan Text Express",
                modelName = "amazon.titan-text-express-v1",
                maxTotalTokens = 8192,
                maxOutTokens = 8192,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.0002,
                outputTokenPricePerK = 0.0006
            )
        )
    }

    override fun authorize(
        request: HttpRequest, apiProvider: APIProvider
    ) {
        // AWS Bedrock uses SDK authentication, not HTTP headers
        // This method is not used for AWS authentication
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        validateChatRequest(chatRequest, model)

        log.info("Starting AWS Bedrock chat with model: ${model.modelName}")

        return withReliability {
            withPerformanceLogging {
                val invokeModelRequest = try {
                    toAWS(model, chatRequest)
                } catch (e: Exception) {
                    log.error("Failed to create AWS request for model: ${model.modelName}", e)
                    throw RuntimeException("Failed to create AWS request", e)
                }

                val invokeModelResponse = try {
                    bedrockClient.invokeModel(invokeModelRequest)
                } catch (e: Exception) {
                    log.error("Failed to invoke AWS Bedrock model: ${model.modelName}", e)
                    throw RuntimeException("Failed to invoke AWS Bedrock model", e)
                }

                val responseBody = try {
                    invokeModelResponse.body()?.asString(Charsets.UTF_8)
                        ?: throw RuntimeException("Empty response body from AWS Bedrock")
                } catch (e: Exception) {
                    log.error("Failed to read AWS response body", e)
                    throw RuntimeException("Failed to read AWS response body", e)
                }

                log.debug("AWS Bedrock response: $responseBody")

                val result = try {
                    fromAWS(responseBody, model.modelName ?: "")
                } catch (e: Exception) {
                    log.error("Failed to parse AWS response for model: ${model.modelName}", e)
                    throw RuntimeException("Failed to parse AWS response", e)
                }

                val response = JsonUtil.objectMapper().readValue(result, ModelSchema.ChatResponse::class.java)

                if (response.usage != null && model is ChatModel) {
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
                }

                log.info("AWS Bedrock chat completed successfully")
                response
            }
        }
    }

    private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
        require(model.modelName?.isNotBlank() == true) { "Model name cannot be blank" }
        require(awsAuth.region.isNotBlank()) { "AWS region must be specified" }
    }


    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(AwsChatClient::class.java)
        private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

        private data class ModelSpecs(
            val maxTotalTokens: Int,
            val maxOutTokens: Int,
            val inputTokenPricePerK: Double,
            val outputTokenPricePerK: Double
        )

        fun awsCredentials(awsAuth: AWSAuth): AwsCredentialsProviderChain =
            AwsCredentialsProviderChain.builder().credentialsProviders(
                InstanceProfileCredentialsProvider.create(),
                ProfileCredentialsProvider.create(awsAuth.profile),
            ).build()

        data class AWSAuth(
            val profile: String = "default",
            val region: String = Region.US_WEST_2.id(),
        )

        fun toAWS(model: LLMModel, chatRequest: ModelSchema.ChatRequest) =
            InvokeModelRequest.builder().modelId(model.modelName).accept("application/json")
                .contentType("application/json")
                .body(SdkBytes.fromString(JsonUtil.toJson(awsBody(model, chatRequest)), Charsets.UTF_8)).build()

        fun awsBody(
            model: LLMModel, chatRequest: ModelSchema.ChatRequest
        ): Map<String, Any> = when {
            model.modelName?.contains("llama") == true -> {
                mapOf(
                    "prompt" to toSimplePrompt(chatRequest),
                    "max_gen_len" to model.maxOutTokens,
                    "temperature" to chatRequest.temperature,

                    )
            }

            model.modelName?.contains("mistral") == true -> {
                mapOf(
                    "prompt" to toSimplePrompt(chatRequest),
                    "max_tokens" to model.maxOutTokens,
                    "temperature" to chatRequest.temperature,
                )
            }

            model.modelName?.contains("titan") == true -> {
                mapOf(
                    "inputText" to toSimplePrompt(chatRequest), "textGenerationConfig" to mapOf(
                        "maxTokenCount" to model.maxTotalTokens,
                        "stopSequences" to emptyList<String>(),
                        "temperature" to chatRequest.temperature,
                    )
                )
            }

            model.modelName?.contains("cohere") == true -> {
                mapOf(
                    "prompt" to toSimplePrompt(chatRequest),
                    "max_tokens" to model.maxTotalTokens,
                    "temperature" to chatRequest.temperature,
                )
            }

            model.modelName?.contains("ai21") == true -> {
                mapOf(
                    "prompt" to toSimplePrompt(chatRequest),
                    "maxTokens" to model.maxTotalTokens,
                    "temperature" to chatRequest.temperature,
                    "stopSequences" to emptyList<String>(),
                    "countPenalty" to mapOf("scale" to 0),
                    "presencePenalty" to mapOf("scale" to 0),
                    "frequencyPenalty" to mapOf("scale" to 0),
                )
            }

            model.modelName?.contains("anthropic") == true -> {
                val alternatingMessages = alternateMessagesRoles(chatRequest.messages)
                mapOf(
                    "anthropic_version" to anthropic_version(model),
                    "max_tokens" to model.maxOutTokens,
                    "temperature" to chatRequest.temperature,
                    "messages" to alternatingMessages.filter {
                        when (it.role) {
                            ModelSchema.Role.system -> false
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
                    "system" to toSimplePrompt(chatRequest) { it.role == ModelSchema.Role.system },
                )
            }

            else -> throw RuntimeException("Unsupported model: $model")
        }

        fun anthropic_version(model: LLMModel) = when {
            else -> "bedrock-2023-05-31"

        }

        fun alternateMessagesRoles(messages: List<ModelSchema.ChatMessage>): List<ModelSchema.ChatMessage> {
            val alternatingMessages = mutableListOf<ModelSchema.ChatMessage>()
            val messagesCopy = messages.toMutableList()
            var isFirst = true
            while (messagesCopy.isNotEmpty()) {
                val thisRole = messagesCopy.firstOrNull()?.role
                val consolidatedMessage = takeAll(messagesCopy, thisRole)
                if (isFirst) {
                    isFirst = false
                    if ((consolidatedMessage?.role ?: "") != "user") {
                        val chatMessage = takeAll(messagesCopy, ModelSchema.Role.user)
                        alternatingMessages.add(
                            concat(
                                (consolidatedMessage ?: ModelSchema.ChatMessage()).copy(role = ModelSchema.Role.user),
                                chatMessage ?: ModelSchema.ChatMessage()
                            )
                        )
                        continue
                    }
                }
                alternatingMessages.add(consolidatedMessage ?: ModelSchema.ChatMessage())
            }
            return alternatingMessages
        }

        fun takeAll(
            messagesCopy: MutableList<ModelSchema.ChatMessage>, thisRole: ModelSchema.Role?
        ): ModelSchema.ChatMessage? {
            val toConsolidate = messagesCopy.takeWhile { it.role == thisRole }.toTypedArray()
            messagesCopy.removeAll(toConsolidate)
            val consolidatedMessage = toConsolidate.reduceOrNull { acc, chatMessage ->
                concat(acc, chatMessage)
            }
            return consolidatedMessage
        }

        fun concat(
            acc: ModelSchema.ChatMessage, chatMessage: ModelSchema.ChatMessage
        ) = ModelSchema.ChatMessage(
            role = acc.role, content = listOf(
                ModelSchema.ContentPart(
                    text = (acc.content?.plus(chatMessage.content ?: emptyList()) ?: chatMessage.content)?.joinToString(
                        "\n"
                    ) { it.text ?: "" })
            )
        )

        fun toSimplePrompt(
            chatRequest: ModelSchema.ChatRequest, filterFn: (ModelSchema.ChatMessage) -> Boolean = { true }
        ) = if (chatRequest.messages.filter(filterFn).map { it.role }.distinct().size <= 1) {
            chatRequest.messages.filter(filterFn).joinToString("\n\n") {
                it.content?.joinToString("\n") { it.text ?: "" } ?: ""
            }
        } else {
            chatRequest.messages.filter(filterFn).joinToString("\n\n") {
                "${it.role}: \n" + it.content?.joinToString("\n") { "\t" + (it.text ?: "") }
            }
        }

        fun fromAWS(responseBody: String, model: String): String {
            require(responseBody.isNotBlank()) { "Response body cannot be blank" }
            require(model.isNotBlank()) { "Model name cannot be blank" }

            return when {
                model.contains("llama") -> {
                    val fromJson = try {
                        JsonUtil.fromJson<AwsResponseLlama2>(responseBody, AwsResponseLlama2::class.java)
                    } catch (e: Exception) {
                        throw RuntimeException("Failed to parse Llama response", e)
                    }
                    JsonUtil.toJson(
                        ModelSchema.ChatResponse(
                            choices = listOf(
                                ModelSchema.ChatChoice(
                                    message = ModelSchema.ChatMessageResponse(
                                        content = fromJson.generation ?: "",
                                    ), index = 0
                                )
                            ), usage = ModelSchema.Usage(
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
                        ModelSchema.ChatResponse(
                            choices = listOf(
                                ModelSchema.ChatChoice(
                                    message = ModelSchema.ChatMessageResponse(
                                        content = fromJson.outputs?.firstOrNull()?.text ?: "",
                                    ), index = 0
                                )
                            )
                        )
                    )
                }

                model.contains("titan") -> {
                    val fromJson = JsonUtil.fromJson<AwsResponseTitan>(responseBody, AwsResponseTitan::class.java)
                    JsonUtil.toJson(
                        ModelSchema.ChatResponse(
                            choices = listOf(
                                ModelSchema.ChatChoice(
                                    message = ModelSchema.ChatMessageResponse(
                                        content = fromJson.results?.firstOrNull()?.outputText ?: "",
                                    ), index = 0
                                )
                            )
                        )
                    )
                }

                model.contains("cohere") -> {
                    val fromJson = JsonUtil.fromJson<AwsResponseCohere>(responseBody, AwsResponseCohere::class.java)
                    JsonUtil.toJson(
                        ModelSchema.ChatResponse(
                            choices = listOf(
                                ModelSchema.ChatChoice(
                                    message = ModelSchema.ChatMessageResponse(
                                        content = fromJson.generations?.firstOrNull()?.text ?: "",
                                    ), index = 0
                                )
                            )
                        )
                    )
                }

                model.contains("ai21") -> {
                    val fromJson = JsonUtil.objectMapper().readValue(responseBody, Ai21ChatResponse::class.java)
                    return JsonUtil.toJson(
                        ModelSchema.ChatResponse(
                            choices = fromJson.completions?.mapIndexed { index, completion ->
                                ModelSchema.ChatChoice(
                                    message = ModelSchema.ChatMessageResponse(
                                        content = completion.data?.text ?: "",
                                    ), index = index
                                )
                            } ?: emptyList(),
                        ))
                }

                model.contains("anthropic") -> {
                    val fromJson = try {
                        JsonUtil.fromJson<AwsResponseAnthropic>(responseBody, AwsResponseAnthropic::class.java)
                    } catch (e: Exception) {
                        throw RuntimeException("Failed to parse Anthropic response", e)
                    }
                    JsonUtil.toJson(
                        ModelSchema.ChatResponse(
                            choices = listOf(
                                ModelSchema.ChatChoice(
                                    message = ModelSchema.ChatMessageResponse(
                                        content = fromJson.content?.firstOrNull()?.text ?: "",
                                    ), index = 0
                                )
                            ), usage = ModelSchema.Usage(
                                prompt_tokens = fromJson.usage?.input_tokens?.toLong() ?: 0,
                                completion_tokens = fromJson.usage?.output_tokens?.toLong() ?: 0,
                                total_tokens = (fromJson.usage?.input_tokens?.toLong()
                                    ?: 0) + (fromJson.usage?.output_tokens?.toLong() ?: 0)
                            )
                        )
                    )
                }

                else -> throw IllegalArgumentException("Unsupported AWS model: $model")
            }
        }

        data class AwsResponseAnthropic(
            val id: String? = null,
            val type: String? = null,
            val role: String? = null,
            val content: List<AwsResponseAnthropicContent>? = null,
            val model: String? = null,
            val stop_reason: String? = null,
            val stop_sequence: String? = null,
            val usage: AwsResponseAnthropicUsage? = null
        )

        data class AwsResponseAnthropicContent(
            val type: String? = null, val text: String? = null
        )

        data class AwsResponseAnthropicUsage(
            val input_tokens: Int? = null, val output_tokens: Int? = null
        )

        data class Ai21ChatResponse(
            val id: Int? = null, val prompt: Ai21Prompt? = null, val completions: List<Ai21Completion>? = null
        )

        data class Ai21Completion(
            val data: Ai21Data? = null, val finishReason: Ai21FinishReason? = null
        )

        data class Ai21FinishReason(
            val reason: String? = null
        )

        data class Ai21Data(
            val text: String? = null, val tokens: List<Ai21Token>? = null
        )

        data class Ai21Prompt(
            val text: String? = null, val tokens: List<Ai21Token>? = null
        )

        data class Ai21Token(
            val generatedToken: Ai21GeneratedToken? = null,
            val topTokens: List<Ai21TopToken>? = null,
            val textRange: Ai21TextRange? = null
        )

        data class Ai21GeneratedToken(
            val token: String? = null, val logprob: Double? = null, val raw_logprob: Double? = null
        )

        data class Ai21TopToken(
            val token: String? = null, val logprob: Double? = null, val raw_logprob: Double? = null
        )

        data class Ai21TextRange(
            val start: Int? = null, val end: Int? = null
        )

        data class AwsResponseCohere(
            val generations: List<AwsResponseCohereGeneration>?
        )

        data class AwsResponseCohereGeneration(
            val text: String? = null
        )

        data class AwsResponseMistral(
            val outputs: List<AwsResponseMistralOutput>?
        )

        data class AwsResponseMistralOutput(
            val text: String? = null, val stop_reason: String? = null
        )

        data class AwsResponseTitan(
            val inputTextTokenCount: Int? = null, val results: List<AwsResponseTitanResult>?
        )

        data class AwsResponseTitanResult(
            val tokenCount: Int? = null, val outputText: String? = null, val completionReason: String? = null
        )

        data class AwsResponseLlama2(
            val generation: String? = null,
            val prompt_token_count: Int? = null,
            val generation_token_count: Int? = null,
            val stop_reason: String? = null
        )

    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/ChatClientBase.kt

```
package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.HttpClientManager
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.toJson
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.HttpEntity
import org.apache.hc.core5.http.HttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.ExecutorService

abstract class SingleProviderChatClient(
    protected val provider: APIProvider,
    val apiKey: SecureString,
    val apiBase: String = provider.base,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : ChatClientBase(
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
) {
    protected fun get(url: String) = withClient { client ->
        client.execute(HttpGet(url).let {
            provider.authorize(
                request = it,
                key = apiKey.decrypt,
                apiBase = apiBase
            )
            it
        }).use { response ->
            val responseBody = response.entity?.content?.bufferedReader()?.readText() ?: ""
            log(Level.DEBUG, "GET $url -> ${response.code}: $responseBody", logStreams)
            responseBody
        }
    }
}

abstract class ChatClientBase(
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : HttpClientManager(
    logLevel = logLevel, logStreams = logStreams, workPool = workPool, scheduledPool = scheduledPool
), ChatClientInterface {

    var session: Any? = null
    var user: Any? = null
    override var budget: Number? = null

    @Throws(IOException::class, InterruptedException::class)
    fun post(
        url: String,
        json: String,
        apiProvider: APIProvider,
        requestID: String = UUID.randomUUID().toString(),
        logStreams: MutableList<BufferedOutputStream> = this.logStreams
    ): String {
        validatePostRequest(url, json)
        val request = HttpPost(url)
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        authorize(request, apiProvider)
        request.entity = StringEntity(json, Charsets.UTF_8, false)
        return post(request, requestID = requestID, logStreams = logStreams)
    }

    private fun validatePostRequest(url: String, json: String) {
        require(url.isNotBlank()) { "URL cannot be blank" }
        require(json.isNotBlank()) { "JSON payload cannot be blank" }
        require(url.startsWith("http")) { "URL must be a valid HTTP/HTTPS URL: $url" }
    }

    abstract fun authorize(request: HttpRequest, apiProvider: APIProvider)

    fun post(
        request: HttpPost,
        requestID: String = UUID.randomUUID().toString(),
        logStreams: MutableList<BufferedOutputStream> = this.logStreams
    ): String = try {
        withClient { client ->
            log(
                level = Level.DEBUG,
                msg = String.format(
                    "<details><summary>POST %s\nID:%s</summary>\nPrefix:\n\n```json\n%s\n```\n\n```\n%s\n```\n</details>",
                    request.uri,
                    requestID,
                    request.entity.formatEntityForLogging(),
                    captureCallerStack().indent("  ")
                ),
                logStreams
            )
            val response =
                innerPost(client, request) ?: throw IOException("Empty response from POST request to ${request.uri}")
            log(
                level = Level.DEBUG,
                msg = String.format(
                    "<details><summary>POST %s\nID:%s</summary>\nResponse:\n\n```\n%s\n```\n</details>",
                    request.uri,
                    requestID,
                    response.let {
                        try {
                            fromJson<Map<String, Any>>(it, Map::class.java).toJson()
                        } catch (e: Exception) {
                            it
                        }
                    }.indent("  ")
                ),
                logStreams
            )
            response
        }
    } catch (e: Exception) {
        log(
            Level.ERROR,
            "Error during POST request to ${request.uri}\nID:$requestID\nRequest Entity:\n${request.entity.formatEntityForLogging()}",
            logStreams
        )
        log.error("Failed to execute POST request to ${request.uri}", e)
        throw e
    }

    protected open fun innerPost(
        client: CloseableHttpClient,
        request: HttpPost
    ): String? {
        val response = client.execute(request)
        val entity = response.entity
        return if (entity != null) {
            val responseBody = EntityUtils.toString(entity)
            if (responseBody.isBlank()) {
                throw IOException("Empty response body")
            }
            responseBody
        } else {
            throw IOException("Empty response entity")
        }
    }

    override fun onUsage(
        model: LLMModel,
        tokens: Usage,
        logStreams: MutableList<BufferedOutputStream>
    ) {
        log(
            Level.DEBUG,
            "Usage recorded for session: %s, user: %s, model: %s, tokens: %s".format(session, user, model, tokens),
            logStreams
        )
        budget?.let { currentBudget ->
            val cost = tokens.cost ?: 0.0
            budget = (currentBudget.toDouble() - cost).coerceAtLeast(0.0)
            if (budget!!.toDouble() <= 0.0) {
                log(Level.WARN, "Budget exhausted for session: $session, user: $user", logStreams)
            } else {
                log(Level.DEBUG, "Remaining budget for session: $session, user: $user is $budget", logStreams)
            }
        }
        super.onUsage(model, tokens, logStreams)
    }

    companion object {
        private val log = LoggerFactory.getLogger(ChatClientBase::class.java)
    }
}

fun HttpEntity?.formatEntityForLogging() = try {
    EntityUtils.toString(this)?.indent("  ").orEmpty()
} catch (e: Exception) {
    "[Unable to format entity for logging]: $e"
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/ChatClientInterface.kt

```
package com.simiacryptus.cognotik.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

interface ChatClientInterface {
    var budget: Number?
    val logStreams: MutableList<BufferedOutputStream>
    val workPool: ExecutorService
    val onUsageListeners: MutableList<(model: LLMModel, tokens: ModelSchema.Usage) -> Unit>
    fun getModels(): List<ChatModel>? = null

    /**
     * Sends a chat request to the configured model and returns the response
     * @param chatRequest The chat request containing messages and parameters
     * @param model The text model to use for the chat
     * @return The chat response from the model
     * @throws IllegalArgumentException if the request is invalid
     * @throws RuntimeException if the API call fails
     */
    @Deprecated("Use chat with messages parameter instead via preauthenticated chat models")
    fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<BufferedOutputStream> = this.logStreams
    ): ModelSchema.ChatResponse

    /**
     * Moderates the given text for policy violations
     * @param text The text to moderate
     * @throws ModerationException if the text violates policies
     */
    fun moderate(text: String) {}

}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/DeepSeekChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class DeepSeekChatClient(
    apiKey: SecureString,
    workPool: ExecutorService,
    apiBase: String = "https://api.deepseek.com",
    logLevel: Level = Level.DEBUG,
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
        request.addHeader(HEADER_AUTHORIZATION, "Bearer ${apiKey.decrypt}")
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
            onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
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
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/GeminiChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class GeminiChatClient(
    apiKey: SecureString,
    apiBase: String,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService,
) : SingleProviderChatClient(
    APIProvider.Gemini,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
) {

    override fun getModels(): List<ChatModel>? {
        // Check cache first
        modelsCache[apiBase]?.let { return it }

        return try {
            val responseBody = get("${apiBase}/v1beta/models?key=${apiKey.decrypt}")
            checkError(responseBody)
            log.debug("Fetched models from Google API: $responseBody")
            val listResponse = JsonUtil.fromJson<ModelsListResponse>(responseBody, ModelsListResponse::class.java)
            val models = listResponse.models?.mapNotNull { model ->
                // Map Google API model to our ChatModel
                val baseModelId = model.name?.removePrefix("models/") ?: return@mapNotNull null
                // Try to find a matching model in our predefined GoogleModels
                GeminiModels.values.values.find {
                    it.modelName == baseModelId || it.modelName == model.name
                } ?: run {
                    // If not found in predefined models, create a dynamic one
                    log.debug("Creating basic ChatModel for unknown Gemini model: ${baseModelId}")
                    ChatModel(
                        name = model.displayName ?: baseModelId,
                        modelName = baseModelId,
                        maxTotalTokens = model.inputTokenLimit ?: 1048576,
                        maxOutTokens = model.outputTokenLimit ?: 8192,
                        provider = APIProvider.Gemini,
                        inputTokenPricePerK = 0.0, // Default pricing - would need to be configured
                        outputTokenPricePerK = 0.0
                    )
                }
            }
            // Cache the result
            models?.let { modelsCache[apiBase] = it }
            models
        } catch (e: Exception) {
            log.warn("Failed to fetch models from Google API: ${e.message}")
            null
        }
    }

    override fun authorize(
        request: HttpRequest,
        apiProvider: APIProvider
    ) {
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        // Google API uses API key as query parameter, not in headers
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        val geminiChatRequest = toGeminiChatRequest(chatRequest, model)
        val json = JsonUtil.objectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(geminiChatRequest)

        val responseBody = post(
            "${apiBase}/v1beta/models/${model.modelName}:generateContent?key=${apiKey.decrypt}",
            json,
            APIProvider.Gemini
        )
        checkError(responseBody)

        val responseJson = fromGemini(responseBody)
        val response = JsonUtil.objectMapper()
            .readValue(responseJson, ModelSchema.ChatResponse::class.java)
        if (response.usage != null && model is ChatModel) {
            onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
        }
        return response
    }

    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(GeminiChatClient::class.java)
        private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

        data class ModelsListResponse(
            val models: List<ModelInfo>? = null,
            val nextPageToken: String? = null
        )

        data class ModelInfo(
            val name: String? = null,
            val baseModelId: String? = null,
            val version: String? = null,
            val displayName: String? = null,
            val description: String? = null,
            val inputTokenLimit: Int? = null,
            val outputTokenLimit: Int? = null,
            val supportedGenerationMethods: List<String>? = null,
            val thinking: Boolean? = null,
            val temperature: Double? = null,
            val maxTemperature: Double? = null,
            val topP: Double? = null,
            val topK: Int? = null
        )

        fun fromGemini(responseBody: String): String {
            val fromJson = JsonUtil.fromJson<GenerateContentResponse>(responseBody, GenerateContentResponse::class.java)
            return JsonUtil.toJson(
                ModelSchema.ChatResponse(
                    choices = fromJson.candidates?.mapIndexed { index, candidate ->
                        ModelSchema.ChatChoice(
                            message = ModelSchema.ChatMessageResponse(
                                content = candidate.content?.parts?.joinToString("\n") { it.text ?: "" },
                            ), index = index
                        )
                    } ?: emptyList(),
                    usage = ModelSchema.Usage(
                        prompt_tokens = (fromJson.usageMetadata?.promptTokenCount ?: 0).toLong(),
                        completion_tokens = (fromJson.usageMetadata?.candidatesTokenCount ?: 0).toLong(),
                        total_tokens = (fromJson.usageMetadata?.totalTokenCount ?: 0).toLong()
                    )
                ))
        }

        fun toGeminiChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel): GenerateContentRequest {
            return GenerateContentRequest(
                contents = collectRoleSequences(chatRequest.messages.filter {
                    when (it.role) {

                        else -> true
                    }
                }.map {
                    Content(
                        role = when (it.role) {
                            ModelSchema.Role.user -> "user"
                            ModelSchema.Role.system -> "user"
                            ModelSchema.Role.assistant -> "model"
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

        fun collectTextParts(it: Content): Content {
            var text = ""
            val partsList = it.parts?.toMutableList() ?: mutableListOf()
            val newParts = mutableListOf<Part>()
            while (partsList.isNotEmpty()) {
                val parts = partsList.takeWhile { it.text != null }
                if (parts.isNotEmpty()) {
                    text = parts.joinToString("\n") { it.text ?: "" }
                    newParts.add(Part(text = text))
                }
                partsList.removeAll(parts)

                val nonTextParts = partsList.takeWhile { it.text == null }
                newParts.addAll(nonTextParts)
                partsList.removeAll(nonTextParts)
            }
            return it.copy(parts = newParts)
        }

        fun collectRoleSequences(map: List<Content>): List<Content> {
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

        data class GenerateContentRequest(
            val model: String? = null,
            val contents: List<Content>? = null,
            val system_instruction: Content? = null,
            val safetySettings: List<SafetySetting>? = null,
            val generationConfig: GenerationConfig? = null
        )

        data class GenerateContentResponse(
            val candidates: List<Candidate>? = null,
            val usageMetadata: UsageMetadata? = null,
            val modelVersion: String? = null,
            val responseId: String? = null
        )

        data class Candidate(
            val content: Content? = null,

            val finishReason: String? = null, val index: Int? = null, val safetyRatings: List<SafetyRating>? = null
        )

        data class UsageMetadata(
            val promptTokenCount: Int? = null,
            val candidatesTokenCount: Int? = null,
            val totalTokenCount: Int? = null,
            val promptTokensDetails: List<TokensDetail>? = null
        )

        data class TokensDetail(
            val modality: String? = null,
            val tokenCount: Int? = null
        )


        data class SafetyRating(
            val category: String? = null, val probability: String? = null
        )

        data class Content(
            val role: String? = null, val parts: List<Part>? = null
        )

        data class Part(
            val inlineData: Blob? = null, val text: String? = null
        )

        data class Blob(
            val mimeType: String? = null, val data: String? = null
        )

        data class SafetySetting(
            val threshold: String? = null, val category: String? = null
        )

        data class GenerationConfig(
            val temperature: Float? = null,
            val candidateCount: Int? = null,
            val topK: Int? = null,
            val maxOutputTokens: Int? = null,
            val topP: Float? = null,
            val stopSequences: List<String>? = null
        )

    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/GeminiSdkChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.google.genai.Client
import com.google.genai.types.*
import com.google.genai.types.Content.builder
import com.google.genai.types.Part.fromText
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SecureString
import okio.ByteString.Companion.decodeBase64
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import kotlin.jvm.optionals.getOrNull

/**
 * Gemini Chat Client using the official Google Gen AI Java SDK
 */
class GeminiSdkChatClient(
    apiKey: SecureString,
    val apiBase: String = APIProvider.Gemini.base,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream>,
    scheduledPool: ListeningScheduledExecutorService,
    private val useVertexAI: Boolean = false,
    private val project: String? = null,
    private val location: String? = null,
) : ChatClientBase(
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams,
    scheduledPool = scheduledPool
), ChatClientInterface {

    private val client: Client = buildClient(apiKey, useVertexAI, project, location)

    private fun buildClient(
        apiKey: SecureString,
        useVertexAI: Boolean,
        project: String?,
        location: String?
    ): Client {
        val builder = Client.builder()

        if (useVertexAI) {
            builder.vertexAI(true)
            if (project != null && location != null) {
                builder.project(project).location(location)
            } else {
                builder.apiKey(apiKey.decrypt)
            }
        } else {
            builder.apiKey(apiKey.decrypt)
        }

        return builder.build()
    }

    override fun getModels(): List<ChatModel>? {
        // Check cache first
        modelsCache[apiBase]?.let { return it }
        val models = try {
            client.models.list(
                ListModelsConfig.builder().build()
            ).mapNotNull {
                val model = it.name().get()
                val baseModelId = model.removePrefix("models/")
                GeminiModels.values.values.find {
                    it.modelName == baseModelId || it.modelName == model
                } ?: run {
                    // If not found in predefined models, create a dynamic one
                    log.debug("Creating basic ChatModel for unknown Gemini model: ${baseModelId}")
                    ChatModel(
                        name = model,
                        modelName = baseModelId,
                        maxTotalTokens = it.inputTokenLimit().get() + it.outputTokenLimit().get(),
                        maxOutTokens = it.outputTokenLimit().get(),
                        provider = APIProvider.Gemini,
                        inputTokenPricePerK = 0.0, // Default pricing - would need to be configured
                        outputTokenPricePerK = 0.0
                    )
                }
            }.toList()
        } catch (e: Exception) {
            log.warn("Failed to fetch models: ${e.message}")
            null
        }
        // Cache the result
        models?.let { modelsCache[apiBase] = it }
        return models
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        val requestID = UUID.randomUUID().toString()
        try {
            val config = buildGenerateContentConfig(chatRequest)
            val contents: List<Content> = convertToGeminiContents(chatRequest.messages)
            val sysInstruct = config?.systemInstruction()?.getOrNull()?.text()?.indent("  ")
            val contentStr = contents.joinToString("\n\n") { it.toMarkdown() }
            val toJson = toJson(config).indent("  ")
            val msg =
                "\n<details>\n<summary>Sending request to Gemini SDK for model: ${model.modelName} (${requestID})</summary>\n\n```json\n$toJson\n```\n\nSystem Prompt:\n```\n${sysInstruct}\n```\n\n$contentStr\n</details>"
            log(
                msg,
                logStreams
            )
            val response = client.models.generateContent(model.modelName, contents, config)
            // Log response
            log(
                "\n<details>\n<summary>Gemini SDK Response (${requestID})</summary>\n\n${
                    response.candidates().orElse(emptyList()).joinToString("\n\n") { candidate ->
                        candidate.content().orElse(null)?.toMarkdown() ?: "\n\n**No content**\n\n"
                    }
                }\n</details>",
                logStreams
            )
            val chatResponse = convertFromGeminiResponse(response)
            if (chatResponse.usage != null) {
                onUsage(
                    model,
                    chatResponse.usage?.copy(cost = model.pricing(chatResponse.usage!!))!!,
                    logStreams = logStreams
                )
            } else {
                log("No usage data returned from Gemini SDK for request ${requestID}", logStreams)
            }
            return chatResponse
        } catch (e: Exception) {
            log.error("Error during Gemini SDK chat request", e)
            throw e
        }
    }

    private fun Content.toMarkdown(): CharSequence {
        val sb = StringBuilder()
        this.role().getOrNull()?.let { role ->
            sb.append("**Role:** ").append(role).append("\n\n")
        }
        this.parts().orElse(emptyList()).forEach { part ->
            part.text().getOrNull()?.let { text ->
                sb
                    .append("\n```text\n")
                    .append(text.indent("    "))
                    .append("\n```\n")
            }
            part.inlineData().getOrNull()?.let { inlineData ->
                when (inlineData.mimeType().getOrNull()) {
                    "image/png", "image/jpeg", "image/jpg", "image/gif" -> {
                        val imageBytes = inlineData.data().getOrNull()
                        if (imageBytes != null) {
                            /*Resize to no more than 256 px wide*/
                            val maxWidth = 256
                            var sourceImage = javax.imageio.ImageIO.read(imageBytes.inputStream())
                            if (sourceImage.width > maxWidth) {
                                val aspectRatio = sourceImage.height.toDouble() / sourceImage.width.toDouble()
                                val newHeight = (maxWidth * aspectRatio).toInt()
                                val resizedImage = java.awt.image.BufferedImage(maxWidth, newHeight, sourceImage.type)
                                val g2d = resizedImage.createGraphics()
                                g2d.drawImage(sourceImage, 0, 0, maxWidth, newHeight, null)
                                g2d.dispose()
                                sourceImage = resizedImage
                            }
                            val logBytes = java.io.ByteArrayOutputStream()
                            javax.imageio.ImageIO.write(
                                sourceImage,
                                inlineData.mimeType().getOrNull()!!.substringAfter("image/"),
                                logBytes
                            )
                            val imageBytes = logBytes.toByteArray()
                            sb.append(
                                "<img src=\"data:${
                                    inlineData.mimeType().getOrNull()
                                };base64,${imageBytes.base64()}\" alt=\"image\" width=\"${sourceImage.width}\" height=\"${sourceImage.height}\" />\n"
                            )
                        }
                    }

                    else -> {
                        sb.append("`[Unsupported inline data of type ${inlineData.mimeType().getOrNull()}]`\n")
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun buildGenerateContentConfig(chatRequest: ModelSchema.ChatRequest): GenerateContentConfig? {
        val builder = GenerateContentConfig.builder()
        chatRequest.temperature.let { builder.temperature(it.toFloat()) }
        chatRequest.max_tokens?.let { builder.maxOutputTokens(it) }
        val systemMessages = chatRequest.messages.filter { it.role == ModelSchema.Role.system }
        if (systemMessages.isNotEmpty()) {
            builder.systemInstruction(systemMessages.reduceOrNull { acc, message ->
                ModelSchema.ChatMessage(
                    role = ModelSchema.Role.system,
                    content = (acc.content ?: emptyList()) + (message.content ?: emptyList())
                )
            }?.let { reduceOrNull ->
                builder()
                    .role("system")
                    .parts(reduceOrNull.content?.map { it.part() } ?: listOf(fromText("")))
                    .build()
            })
        }
        return builder.build()
    }

    private fun convertToGeminiContents(messages: List<ModelSchema.ChatMessage>) = messages
        .filter { it.role != ModelSchema.Role.system }
        .mapNotNull { it.toContent() }

    private fun ModelSchema.ChatMessage.toContent() = builder()
        .role(
            when (this.role) {
                ModelSchema.Role.system -> "user" // Gemini does not have a system role, treat as user
                ModelSchema.Role.user -> "user"
                ModelSchema.Role.assistant -> "model"
                else -> "user"
            }
        )
        .parts(content?.flatMap { it.parts() } ?: listOf(fromText("")))
        .build()

    fun ModelSchema.ContentPart.part(): Part? = when {
        image_url != null -> {
            // Handle image URLs
            val imageUrl = image_url
            if (imageUrl?.startsWith("data:") == true) {
                // Base64 encoded image
                val parts = imageUrl.split(",")
                val mimeType = parts[0].substringAfter("data:").substringBefore(";")
                val data = parts[1]
                Part.fromBytes(data.decodeBase64()?.toByteArray(), mimeType)
            } else if (imageUrl?.startsWith("gs://") == true) {
                // GCS URI
                Part.fromUri(imageUrl, "image/jpeg")
            } else {
                // Regular URL - convert to text description
                Part.fromUri(imageUrl, "image/jpeg")
            }
        }

        text != null -> fromText(text)

        else -> fromText("")
    }

    fun ModelSchema.ContentPart.parts(): List<Part> = when {
        image_url != null && text != null -> listOfNotNull(
            copy(text = null).part(),
            copy(image_url = null).part()
        )

        else -> listOfNotNull(
            this.part()
        )
    }

    private fun convertFromGeminiResponse(response: GenerateContentResponse): ModelSchema.ChatResponse {
        val choices = response.candidates().orElse(emptyList()).mapIndexed { index, candidate ->
            val content = candidate.content().orElse(null)
            val text = content?.parts()?.orElse(emptyList())
                ?.mapNotNull { it.text().getOrNull() }?.joinToString("\n")?.let {
                    when (it) {
                        "" -> null
                        else -> it
                    }
                }

            val chatMessageResponse = ModelSchema.ChatMessageResponse(
                content = text,
            )
            content?.parts()?.orElse(emptyList())?.forEach { part ->
                part.inlineData()?.getOrNull()?.apply {
                    when (mimeType().getOrNull()) {
                        "image/png", "image/jpeg", "image/jpg", "image/gif" -> {
                            chatMessageResponse.image_data = this.data().getOrNull()
                            chatMessageResponse.image_mime_type = this.mimeType().getOrNull()
                        }
                    }
                }
            }
            ModelSchema.ChatChoice(
                message = chatMessageResponse,
                index = index,
                finish_reason = candidate.finishReason().orElse(null)?.toString()
            )
        }

        val usage = response.usageMetadata().orElse(null)?.let { metadata ->
            ModelSchema.Usage(
                prompt_tokens = metadata.promptTokenCount().orElse(0).toLong(),
                completion_tokens = metadata.candidatesTokenCount().orElse(0).toLong(),
                total_tokens = metadata.totalTokenCount().orElse(0).toLong()
            )
        }

        return ModelSchema.ChatResponse(
            choices = choices,
            usage = usage
        )
    }

    override fun authorize(request: HttpRequest, apiProvider: APIProvider) {
        TODO("Not yet implemented")
    }

    companion object {
        private val log = LoggerFactory.getLogger(GeminiSdkChatClient::class.java)
        private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()
    }
}


private fun ByteArray.base64() = java.util.Base64.getEncoder().encodeToString(this)

```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/GroqChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class GroqChatClient(
    apiKey: SecureString,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
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
        request.addHeader(HEADER_AUTHORIZATION, "Bearer ${apiKey.decrypt}")
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
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
                }

                response
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/MistralChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
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
    val role: ModelSchema.Role,
    val content: String
)


class MistralChatClient(
    apiKey: SecureString,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    apiBase: String,
    scheduledPool: ListeningScheduledExecutorService,
) : SingleProviderChatClient(
    APIProvider.Mistral,
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
        request.addHeader(HEADER_AUTHORIZATION, "Bearer ${apiKey.decrypt}")
        require(null == budget || budget!!.toDouble() > 0.0) { "Budget Exceeded" }
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        log.info("Starting Mistral chat with model: ${model.modelName}")

        return withReliability {
            withPerformanceLogging {
                val mistralRequest = toMistral(chatRequest)
                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(mistralRequest)

                val result = post("$apiBase/chat/completions", json, APIProvider.Mistral)
                checkError(result)
                val response = JsonUtil.objectMapper().readValue(result, ModelSchema.ChatResponse::class.java)

                if (response.usage != null && model is ChatModel) {
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
                }

                response
            }
        }
    }

    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(MistralChatClient::class.java)
        const val HEADER_CONTENT_TYPE = "Content-Type"
        const val HEADER_ACCEPT = "Accept"
        const val HEADER_AUTHORIZATION = "Authorization"
        const val APPLICATION_JSON = "application/json"

        fun toMistral(chatRequest: ModelSchema.ChatRequest): MistralChatRequest = MistralChatRequest(
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
            stop = chatRequest.stop?.map { if (it.isEmpty()) "" else it.toString() },
            //top_p = chatRequest.top_p,
            //random_seed = chatRequest.seed
        )
    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/ModelsLabChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.ModelsLabDataModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import com.simiacryptus.cognotik.util.runWithPermit
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore

class ModelsLabChatClient(
    apiKey: SecureString,
    apiBase: String = APIProvider.ModelsLab.base!!,
    workPool: ExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
    scheduledPool: ListeningScheduledExecutorService,
) : SingleProviderChatClient(
    APIProvider.ModelsLab,
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
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        // ModelsLab uses API key in the request body, not headers
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        return modelsLabThrottle.runWithPermit {
            val modelsLabRequest = toModelsLab(chatRequest)
            val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(modelsLabRequest)

            val rawResponse = post("$apiBase/llm/chat", json, APIProvider.ModelsLab)
            val responseJson = fromModelsLab(rawResponse, this)

            val response: ModelSchema.ChatResponse =
                JsonUtil.objectMapper().readValue(responseJson, ModelSchema.ChatResponse::class.java)
            if (response.usage != null && model is ChatModel) {
                onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
            }
            response
        }
    }

    companion object {
        private val modelsLabThrottle = Semaphore(1)
        private val modelslab_chatRequest_prototype = ModelsLabDataModel.ChatRequest(
            max_new_tokens = 1000,
            no_repeat_ngram_size = 5,
        )

        fun fromModelsLab(rawResponse: String, client: ModelsLabChatClient): String {
            val response = JsonUtil.objectMapper().readValue(rawResponse, ModelsLabDataModel.ChatResponse::class.java)
            return when (response.status) {
                "success" -> {
                    JsonUtil.toJson(
                        ModelSchema.ChatResponse(
                            id = response.chat_id, choices = listOf(
                                ModelSchema.ChatChoice(
                                    message = ModelSchema.ChatMessageResponse(content = response.message), index = 0
                                )
                            ), usage = response.meta?.let {
                                ModelSchema.Usage(
                                    prompt_tokens = it.max_new_tokens?.toLong() ?: 0,
                                    completion_tokens = 0,
                                    total_tokens = it.max_new_tokens?.toLong() ?: 0
                                )
                            })
                    )
                }

                "processing" -> {
                    val seconds = response?.eta ?: 1
                    client.log(Level.INFO, "Chat response is still processing; waiting ${seconds}s and trying again.")
                    Thread.sleep(seconds * 1000L)
                    fromModelsLab(
                        client.post(
                            "${client.apiBase}/llm/get_queued_response",
                            JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                                mapOf(
                                    "chat_id" to (response.meta?.chat_id ?: response.chat_id),
                                    "key" to client.apiKey.decrypt
                                )
                            ),
                            APIProvider.ModelsLab,
                        ),
                        client
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

        fun toModelsLab(chatRequest: ModelSchema.ChatRequest) = modelslab_chatRequest_prototype.copy(
            model_id = chatRequest.model,
            system_prompt = chatRequest.messages.filter { it.role == ModelSchema.Role.system }.joinToString("\n") {
                it.content?.joinToString("\n") { it.text ?: "" } ?: ""
            },
            prompt = chatRequest.messages.filter { it.role != ModelSchema.Role.system }.joinToString("\n") {
                it.content?.joinToString("\n") { it.text ?: "" } ?: ""
            },
            temperature = chatRequest.temperature,
        )

    }
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/OllamaChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.*
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class OllamaChatClient(
    apiKey: SecureString,
    apiBase: String,
    workPool: ExecutorService,
    scheduledPool: ListeningScheduledExecutorService,
    logLevel: Level = Level.DEBUG,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
) : SingleProviderChatClient(
    APIProvider.Ollama,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    scheduledPool = scheduledPool,
    logLevel = logLevel,
    logStreams = logStreams
) {

    override fun authorize(
        request: HttpRequest,
        apiProvider: APIProvider
    ) {
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        // Ollama typically doesn't require authorization headers
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        validateChatRequest(chatRequest, model)

        return withReliability {
            withPerformanceLogging {
                // Convert OpenAI format to Ollama format
                // Ollama expects content as a string, not an array
                val ollamaMessages = chatRequest.messages.map { message ->
                    OllamaMessage(
                        role = message.role.toString(),
                        content = when (val content = message.content) {
                            is String -> content
                            is List<*> -> content.joinToString("\n") {
                                when (it) {
                                    is ModelSchema.ContentPart -> it.text ?: ""
                                    else -> it.toString()
                                }
                            }

                            else -> ""
                        }
                    )
                }

                val ollamaRequest = OllamaChatRequest(
                    model = chatRequest.model ?: model.modelName!!,
                    messages = ollamaMessages,
                    stream = false,
                    options = OllamaOptions(
                        temperature = chatRequest.temperature,
                        //top_p = chatRequest.top_p,
                        max_tokens = chatRequest.max_tokens
                    )
                )

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(ollamaRequest)

                val rawResponse = post("${apiBase}/api/chat", json, APIProvider.Ollama)

                // Check if response is an error by trying to parse it as JSON
                // Ollama returns plain text errors or JSON responses
                try {
                    val jsonResponse = JsonUtil.objectMapper().readTree(rawResponse)
                    if (jsonResponse.has("error")) {
                        throw RuntimeException("Ollama API error: ${jsonResponse.get("error").asText()}")
                    }
                } catch (e: com.fasterxml.jackson.core.JsonParseException) {
                    // If it's not valid JSON, treat it as an error message
                    if (rawResponse.contains("error", ignoreCase = true) ||
                        rawResponse.contains("not found", ignoreCase = true) ||
                        rawResponse.contains("invalid", ignoreCase = true)
                    ) {
                        throw RuntimeException("Ollama API error: $rawResponse")
                    }
                    // If it's not JSON and doesn't look like an error, re-throw the parse exception
                    throw RuntimeException("Invalid JSON response from Ollama: $rawResponse", e)
                }

                val ollamaResponse = JsonUtil.objectMapper().readValue(rawResponse, OllamaChatResponse::class.java)

                // Convert Ollama response to OpenAI format
                val response = ModelSchema.ChatResponse(
                    id = "ollama-${System.currentTimeMillis()}",
                    `object` = "chat.completion",
                    created = System.currentTimeMillis() / 1000,
                    model = ollamaResponse.model,
                    choices = listOf(
                        ChatChoice(
                            index = 0,
                            message = ollamaResponse.message.let { message ->
                                ChatMessageResponse(
                                    role = message.role.let { Role.valueOf(it) },
                                    content = message.content,
                                )
                            },
                            finish_reason = if (ollamaResponse.done) "stop" else "length"
                        )
                    ),
                    usage = ModelSchema.Usage(
                        prompt_tokens = ollamaResponse.prompt_eval_count?.toLong() ?: 0L,
                        completion_tokens = ollamaResponse.eval_count?.toLong() ?: 0L,
                        total_tokens = ((ollamaResponse.prompt_eval_count ?: 0) + (ollamaResponse.eval_count
                            ?: 0)).toLong()
                    )
                )

                if (response.usage != null && model is ChatModel) {
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
                }

                response
            }
        }
    }

    override fun getModels(): List<ChatModel>? {
        return try {
            val rawResponse = get("${apiBase}/api/tags")
            val modelsResponse = JsonUtil.objectMapper().readValue(rawResponse, OllamaModelsResponse::class.java)

            modelsResponse.models.map { ollamaModel ->
                ChatModel(
                    name = ollamaModel.name,
                    modelName = ollamaModel.name,
                    maxTotalTokens = 4096, // Default, could be model-specific
                    maxOutTokens = 4096,
                    provider = APIProvider.Ollama,
                    inputTokenPricePerK = 0.0, // Ollama is typically free/local
                    outputTokenPricePerK = 0.0
                )
            }
        } catch (e: Exception) {
            log(Level.WARN, "Failed to fetch Ollama models: ${e.message}", logStreams)
            null
        }
    }

    private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
        require(model.modelName?.isNotBlank() == true) { "Model name cannot be blank" }
        require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OllamaChatRequest(
        val model: String,
        val messages: List<OllamaMessage>,
        val stream: Boolean = false,
        val options: OllamaOptions? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OllamaMessage(
        val role: String,
        val content: String
    )


    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OllamaOptions(
        val temperature: Double? = null,
        val top_p: Double? = null,
        val max_tokens: Int? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OllamaChatResponse(
        val model: String,
        val message: OllamaMessage,
        val done: Boolean,
        @JsonProperty("prompt_eval_count") val prompt_eval_count: Int? = null,
        @JsonProperty("eval_count") val eval_count: Int? = null
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OllamaModelsResponse(
        val models: List<OllamaModel>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class OllamaModel(
        val name: String,
        val size: Long? = null,
        val digest: String? = null,
        val modified_at: String? = null
    )
}
```

# /home/andrew/code/Cognotik/core/src/main/kotlin/com/simiacryptus/cognotik/chat/OpenAIChatClient.kt

```
package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.chat.model.OpenAIModels
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class OpenAIChatClient(
    apiKey: SecureString,
    apiBase: String,
    workPool: ExecutorService,
    scheduledPool: ListeningScheduledExecutorService,
) : SingleProviderChatClient(
    APIProvider.OpenAI,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    scheduledPool = scheduledPool
) {
    override fun authorize(
        request: HttpRequest,
        apiProvider: APIProvider
    ) {
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        request.addHeader("Authorization", "Bearer ${apiKey.decrypt}")
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        validateChatRequest(chatRequest, model)

        return withReliability {
            withPerformanceLogging {

                val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(chatRequest)

                val rawResponse = post("${apiBase}/chat/completions", json, APIProvider.OpenAI)
                checkError(rawResponse)

                val response = JsonUtil.objectMapper().readValue(rawResponse, ModelSchema.ChatResponse::class.java)

                if (response.usage != null && model is ChatModel) {
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
                }

                response
            }
        }
    }

    private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
        require(model.modelName?.isNotBlank() == true) { "Model name cannot be blank" }
        require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
    }

    override fun getModels(): List<ChatModel>? {
        modelsCache[apiBase]?.let { return it }
        return try {
            val modelsResponse = fetchModels()
            val models = modelsResponse.mapNotNull { modelInfo ->
                val knownModels = OpenAIModels.values.values
                    .filter { it.modelName == modelInfo.id }
                if (knownModels.isNotEmpty()) {
                    knownModels.first()
                } else if (modelInfo.id.startsWith("gpt") || modelInfo.id.startsWith("o1") || modelInfo.id.startsWith("o3")) {
                    ChatModel(
                        name = modelInfo.id,
                        modelName = modelInfo.id,
                        provider = APIProvider.OpenAI,
                        maxTotalTokens = 128000,
                        inputTokenPricePerK = 0.0,
                        outputTokenPricePerK = 0.0
                    )
                } else {
                    null
                }
            }
            modelsCache[apiBase] = models
            models
        } catch (e: Exception) {
            log.error("Failed to fetch OpenAI models", e)
            null
        }
    }

    private fun fetchModels(): List<OpenAIModelInfo> {
        val response = get("${apiBase}/models")
        checkError(response)
        val listResponse = JsonUtil.objectMapper().readValue(response, OpenAIListModelsResponse::class.java)
        return listResponse.data
    }

    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(OpenAIChatClient::class.java)
        private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

        data class OpenAIModelInfo(
            val id: String,
            val `object`: String,
            val created: Long,
            val owned_by: String
        )

        data class OpenAIListModelsResponse(
            val `object`: String,
            val data: List<OpenAIModelInfo>
        )
    }


}
```

# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `com.simiacryptus.cognotik.chat` package, summarizing the chat client implementations and their shared infrastructure.

### core/src/main/kotlin/com/simiacryptus/cognotik/chat/README.md
```markdown
# Chat Client Implementations

This package provides a comprehensive set of chat client implementations for various Large Language Model (LLM) providers. It includes a robust base infrastructure for handling authentication, request/response mapping, usage tracking, and reliability.

## Core Infrastructure

The chat clients are built upon a hierarchical structure that ensures consistency and reduces code duplication:

*   **`ChatClientInterface`**: The primary interface defining the contract for all chat clients. It includes methods for sending chat requests (`chat`), retrieving available models (`getModels`), and performing content moderation (`moderate`).
*   **`ChatClientBase`**: An abstract base class that integrates with `HttpClientManager`. it provides:
    *   **Usage Tracking**: Automatically records token usage and calculates costs.
    *   **Budget Management**: Monitors and enforces session or user-level budgets.
    *   **Logging**: Detailed logging of requests and responses, including formatted JSON and caller stack traces.
    *   **Reliability**: Hooks for performance logging and reliability wrappers.
*   **`SingleProviderChatClient`**: A specialized base class for providers that follow standard HTTP patterns, simplifying the implementation of `GET` and `POST` operations with provider-specific authorization.

## Provider Implementations

The following provider-specific clients are implemented:

| Client | Provider | Description |
| :--- | :--- | :--- |
| `AnthropicChatClient` | Anthropic | Supports Claude models via the Anthropic Messages API. Handles message consolidation and system prompt mapping. |
| `AwsChatClient` | AWS Bedrock | Integrates with AWS Bedrock using the AWS SDK. Supports a wide range of models including Anthropic Claude, Meta Llama, Mistral, Amazon Titan, and Cohere. |
| `DeepSeekChatClient` | DeepSeek | Implementation for the DeepSeek API, supporting their high-performance reasoning and chat models. |
| `GeminiChatClient` | Google Gemini | REST-based implementation for Google's Gemini API. |
| `GeminiSdkChatClient` | Google Gemini | Implementation using the official Google Gen AI Java SDK, supporting advanced features like image input and Vertex AI integration. |
| `GroqChatClient` | Groq | High-speed inference client for models hosted on Groq's LPU platform. |
| `MistralChatClient` | Mistral AI | Client for Mistral's native API, supporting models like Mistral Large and Mixtral. |
| `ModelsLabChatClient` | ModelsLab | Supports various open-source models via the ModelsLab (formerly Stable Diffusion API) infrastructure, including long-polling for queued responses. |
| `OllamaChatClient` | Ollama | Enables interaction with locally hosted models running via Ollama. |
| `OpenAIChatClient` | OpenAI | Standard implementation for OpenAI's GPT-4, GPT-4o, and o1/o3 series models. |

## Key Features

### Reliability and Performance
Clients utilize `withReliability` and `withPerformanceLogging` blocks to ensure robust execution and provide insights into API latency and success rates.

### Model Discovery
Most clients implement `getModels()`, which dynamically fetches available models from the provider's API and maps them to internal `ChatModel` definitions, often including pricing and context window metadata.

### Message Mapping
The clients handle the complexities of mapping the internal `ModelSchema.ChatRequest` format to provider-specific formats. This includes:
*   Consolidating consecutive messages with the same role.
*   Handling system prompts (either as a separate field or a specific message role).
*   Converting multi-modal content (like images) for supported providers (e.g., Gemini).

### Usage and Budgeting
Every successful chat completion triggers `onUsage`, which:
1.  Updates token counts (prompt, completion, total).
2.  Calculates cost based on the specific model's pricing.
3.  Deducts from the available budget if configured.
4.  Notifies registered listeners for downstream tracking or billing.
```

### Summary of Changes
- Created a new `README.md` in the `com.simiacryptus.cognotik.chat` package directory.
- Documented the core architecture (`ChatClientInterface`, `ChatClientBase`, `SingleProviderChatClient`).
- Provided a summary table of all 10 provider-specific implementations found in the source code.
- Highlighted shared features such as reliability, model discovery, message mapping, and budget management.
- Ensured the documentation reflects the specific logic found in the provided Kotlin files (e.g., AWS Bedrock's multi-model support, ModelsLab's queue handling, and Gemini's SDK vs. REST options).
</details>

                - <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/chat/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/chat/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/core/src/main/kotlin/com/simiacryptus/cognotik/chat/README.md'>core/src/main/kotlin/com/simiacryptus/cognotik/chat/README.md</a> Updated
