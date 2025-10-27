package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.util.JsonUtil
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
    apiKey: String,
    apiBase: String,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
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
        JsonUtil.fromJson(apiKey, AWSAuth::class.java)
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
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)), logStreams = logStreams)
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