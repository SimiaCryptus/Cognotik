package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrock.BedrockClient
import software.amazon.awssdk.services.bedrock.model.CustomModelSummary
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary
import software.amazon.awssdk.services.bedrock.model.ListCustomModelsRequest
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient
import software.amazon.awssdk.services.bedrockruntime.model.*
import java.io.BufferedOutputStream
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

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
        JsonUtil.fromJson(apiKey.decrypt!!, AWSAuth::class.java)
    }
    private val bedrockClient: BedrockRuntimeAsyncClient by lazy {
        BedrockRuntimeAsyncClient.builder()
            .credentialsProvider(awsCredentials(awsAuth))
            .region(Region.of(awsAuth.region))
            .overrideConfiguration { config ->
                config.apiCallTimeout(Duration.ofMinutes(10))
                config.apiCallAttemptTimeout(Duration.ofMinutes(5))
                config.advancedOptions()
            }
            .build()
    }
    private val bedrockManagementClient: BedrockClient by lazy {
        BedrockClient.builder()
            .credentialsProvider(awsCredentials(awsAuth))
            .region(Region.of(awsAuth.region))
            .overrideConfiguration { config ->
                config.apiCallTimeout(Duration.ofMinutes(5))
                config.apiCallAttemptTimeout(Duration.ofMinutes(3))
            }
            .build()
    }

    override fun getModels(): List<ChatModel>? {
        // Check cache first
        val cacheKey = "${awsAuth.region}:${awsAuth.profile}"
        modelsCache[cacheKey]?.let {
            log.debug("Returning ${it.size} cached models for region=${awsAuth.region}, profile=${awsAuth.profile}")
            return it
        }

        return try {
            log.info("Fetching available models from AWS Bedrock in region: ${awsAuth.region}")

            val response = try {
                log.debug("Listing foundation models from AWS Bedrock...")
                val listFoundationModels = bedrockManagementClient.listFoundationModels(
                    ListFoundationModelsRequest.builder().build()
                )
                val summaries = listFoundationModels.modelSummaries()?.filterNotNull() ?: emptyList()
                log.debug("Found ${summaries.size} foundation models from AWS Bedrock")
                summaries.mapNotNull { modelSummary ->
                    try {
                        mapAwsModelToChatModel(modelSummary)
                    } catch (e: Exception) {
                        log.warn("Failed to map AWS model ${modelSummary.modelId()}: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to list foundation models from AWS Bedrock: ${e.message}", e)
                emptyList()
            }
            val response2 = try {
                log.debug("Listing custom models from AWS Bedrock...")
                val listCustomModels = bedrockManagementClient.listCustomModels(
                    ListCustomModelsRequest.builder().build()
                )
                val summaries = listCustomModels.modelSummaries()?.filterNotNull() ?: emptyList()
                log.debug("Found ${summaries.size} custom models from AWS Bedrock")
                summaries.mapNotNull { modelSummary ->
                    try {
                        mapAwsModelToChatModel(modelSummary)
                    } catch (e: Exception) {
                        log.warn("Failed to map AWS custom model ${modelSummary.modelArn()}: ${e.message}")
                        null
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to list custom models from AWS Bedrock: ${e.message}", e)
                emptyList()
            }
            log.debug("Processing ${awsAuth.models.size} configured model mappings")

            val modelsCfg = awsAuth.models.mapNotNull { (modelName, modelId) ->
                try {
                    getModelSpecifications(modelId).let { specs ->
                        log.debug("Mapped configured model: name=$modelName, id=$modelId, maxTokens=${specs.maxTotalTokens}")
                        ChatModel(
                            name = modelName,
                            modelId = modelId,
                            maxTotalTokens = specs.maxTotalTokens,
                            maxOutTokens = specs.maxOutTokens,
                            provider = APIProvider.AWS,
                            inputTokenPricePerK = specs.inputTokenPricePerK,
                            outputTokenPricePerK = specs.outputTokenPricePerK
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to add additional AWS model $modelId: ${e.message}")
                    null
                }
            }
            val models = response + response2 + modelsCfg

            log.info("Found ${models.size} available models in AWS Bedrock (${response.size} foundation, ${response2.size} custom, ${modelsCfg.size} configured)")

            // Cache the result
            models.takeIf { it.isNotEmpty() }?.let {
                log.debug("Caching ${it.size} models for key=$cacheKey")
                modelsCache[cacheKey] = it
            }

            models
        } catch (e: Exception) {
            log.error("Failed to fetch models from AWS Bedrock: ${e.message}", e)
            log.warn("Returning default fallback list of AWS Bedrock models")
            getDefaultAwsModels()
        }
    }

    private fun mapAwsModelToChatModel(modelSummary: FoundationModelSummary): ChatModel? {
        val modelId = modelSummary.modelId() ?: run {
            log.warn("Skipping foundation model with null modelId: ${modelSummary.modelName()}")
            return null
        }
        log.debug("Mapping foundation model: id=$modelId, name=${modelSummary.modelName()}")
        val (maxTokens, maxOutTokens, inputPrice, outputPrice) = getModelSpecifications(
            modelId
        )
        return ChatModel(
            name = modelSummary.modelName() ?: modelId,
            modelId = modelId,
            maxTotalTokens = maxTokens,
            maxOutTokens = maxOutTokens,
            provider = APIProvider.AWS,
            inputTokenPricePerK = inputPrice,
            outputTokenPricePerK = outputPrice
        )
    }

    private fun mapAwsModelToChatModel(modelSummary: CustomModelSummary): ChatModel? {
        log.debug("Mapping custom model: arn=${modelSummary.modelArn()}, name=${modelSummary.modelName()}")
        return mapAwsModelToChatModel(
            FoundationModelSummary.builder().modelId(modelSummary.modelArn()).modelName(modelSummary.modelName()).build()
        )
    }

    private fun getModelSpecifications(modelId: String): ModelSpecs {
        val modelId = modelId.lowercase()
        val specs = when {
            // Anthropic Claude models
            modelId.contains("opus") -> ModelSpecs(200000, 64000, 0.015, 0.075)
            modelId.contains("sonnet") -> ModelSpecs(200000, 64000, 0.003, 0.015)
            modelId.contains("haiku") -> ModelSpecs(200000, 64000, 0.00025, 0.00125)

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
            else -> {
                log.debug("Using default model specifications for unknown model: $modelId")
                ModelSpecs(200000, 64000, 0.001, 0.002)
            }
        }
        log.trace("Model specs for $modelId: maxTotal=${specs.maxTotalTokens}, maxOut=${specs.maxOutTokens}, inputPrice=${specs.inputTokenPricePerK}, outputPrice=${specs.outputTokenPricePerK}")
        return specs
    }

    private fun getDefaultAwsModels(): List<ChatModel> {
        // Return a list of commonly available AWS Bedrock models as fallback
        return listOf(
            ChatModel(
                name = "Claude 3 Sonnet",
                modelId = "anthropic.sonnet-20240229-v1:0",
                maxTotalTokens = 200000,
                maxOutTokens = 4096,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.003,
                outputTokenPricePerK = 0.015
            ),
            ChatModel(
                name = "Claude 3 Haiku",
                modelId = "anthropic.haiku-20240307-v1:0",
                maxTotalTokens = 200000,
                maxOutTokens = 4096,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.00025,
                outputTokenPricePerK = 0.00125
            ),
            ChatModel(
                name = "Llama 3 70B Instruct",
                modelId = "meta.llama3-70b-instruct-v1:0",
                maxTotalTokens = 8192,
                maxOutTokens = 2048,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.00265,
                outputTokenPricePerK = 0.0035
            ),
            ChatModel(
                name = "Mistral 7B Instruct",
                modelId = "mistral.mistral-7b-instruct-v0:2",
                maxTotalTokens = 32000,
                maxOutTokens = 4096,
                provider = APIProvider.AWS,
                inputTokenPricePerK = 0.00015,
                outputTokenPricePerK = 0.0002
            ),
            ChatModel(
                name = "Amazon Titan Text Express",
                modelId = "amazon.titan-text-express-v1",
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
        log.debug("authorize() called but AWS Bedrock uses SDK authentication, not HTTP headers - skipping")
    }

    override fun chat(
        chatRequest: ModelSchema.ChatRequest,
        model: ChatModel,
        logStreams: MutableList<BufferedOutputStream>
    ): ModelSchema.ChatResponse {
        validateChatRequest(chatRequest, model)

        val messageCount = chatRequest.messages.size
        val totalContentLength = chatRequest.messages.sumOf { msg ->
            msg.content?.sumOf { it.text?.length ?: 0 } ?: 0
        }
        log.info("Starting AWS Bedrock chat with model: ${model.modelId}, messages=$messageCount, contentLength=$totalContentLength, temperature=${chatRequest.temperature}")

        return withReliability {
            withPerformanceLogging {
                val converseRequest = try {
                    logStreams.debug("Building AWS Converse request for model: ${model.modelId}")
                    toConverseRequest(model, chatRequest, logStreams, awsAuth.flattenChat ?: false)
                } catch (e: Exception) {
                    log.error("Failed to create AWS request for model: ${model.modelId}", e)
                    logStreams.debug("Error details: ${e.message}")
                    throw RuntimeException("Failed to create AWS request", e)
                }

                val converseResponse = try {
                    logStreams.debug("Invoking AWS Bedrock converse for model: ${model.modelId}")
                    val startTime = System.currentTimeMillis()
                    val responseFuture = bedrockClient.converse(converseRequest)
                    val response = responseFuture.get(10, TimeUnit.MINUTES)
                    val elapsed = System.currentTimeMillis() - startTime
                    logStreams.debug("AWS Bedrock converse completed in ${elapsed}ms for model: ${model.modelId}, stopReason=${response.stopReason()}")
                    response
                } catch (e: Exception) {
                    log.error("Failed to invoke AWS Bedrock model: ${model.modelId}", e)
                    throw RuntimeException("Failed to invoke AWS Bedrock model", e.cause ?: e)
                }

                val response = fromConverseResponse(converseResponse)

                if (response.usage != null) {
                    log.debug("Usage for model ${model.modelId}: prompt_tokens=${response.usage?.prompt_tokens}, completion_tokens=${response.usage?.completion_tokens}, total_tokens=${response.usage?.total_tokens}")
                    onUsage(model, response.usage?.copy(cost = model.pricing(response.usage!!))!!, logStreams = logStreams)
                }

                log.info("AWS Bedrock chat completed successfully for model: ${model.modelId}, choices=${response.choices?.size ?: 0}")
                response
            }
        }
    }

    private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
        log.debug("Validating chat request: messages=${chatRequest.messages.size}, model=${model.modelId}, region=${awsAuth.region}")
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
        require(model.modelId?.isNotBlank() == true) { "Model name cannot be blank" }
        require(awsAuth.region.isNotBlank()) { "AWS region must be specified" }
    }


    companion object {
        private val log = LoggerFactory.getLogger(AwsChatClient::class.java)
        private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()

        private data class ModelSpecs(
            val maxTotalTokens: Int,
            val maxOutTokens: Int,
            val inputTokenPricePerK: Double,
            val outputTokenPricePerK: Double
        )

        fun awsCredentials(awsAuth: AWSAuth): AwsCredentialsProviderChain =
            AwsCredentialsProviderChain.builder().credentialsProviders(
                ProfileCredentialsProvider.create(awsAuth.profile),
            ).build()

        data class AWSAuth(
            val profile: String = "default",
            val region: String = Region.US_WEST_2.id(),
            val models: Map<String,String> = emptyMap(),
            val s3OutputUri: String? = null,
            val flattenChat: Boolean? = null,
        )

        fun toConverseRequest(
            model: LLMModel,
            chatRequest: ModelSchema.ChatRequest,
            logStreams: MutableList<BufferedOutputStream>,
            flattenChat: Boolean = false
        ): ConverseRequest {
            log.debug("Creating AWS ConverseRequest: modelId=${model.modelId}")
            if (flattenChat) {
                log.debug("Flattening chat messages into a single user message")
                val flattenedText = chatRequest.messages.joinToString("\n\n") { msg ->
                    val roleLabel = when (msg.role) {
                        ModelSchema.Role.system -> "System"
                        ModelSchema.Role.assistant -> "Assistant"
                        ModelSchema.Role.user -> "User"
                        else -> msg.role?.name ?: "Unknown"
                    }
                    val text = msg.content?.joinToString("\n") { it.text ?: "" } ?: ""
                    "$roleLabel: $text"
                }

                val messages = listOf(
                    Message.builder()
                        .role(ConversationRole.USER)
                        .content(listOf(ContentBlock.fromText(flattenedText)))
                        .build()
                )

                val inferenceConfig = InferenceConfiguration.builder()
                    .maxTokens(model.maxOutTokens)
                    .temperature(chatRequest.temperature.toFloat())
                    .build()

                logStreams.debug("Flattened converse request: 1 message, flattenedLength=${flattenedText.length}, maxTokens=${model.maxOutTokens}, temperature=${chatRequest.temperature}")

                return ConverseRequest.builder()
                    .modelId(model.modelId)
                    .messages(messages)
                    .inferenceConfig(inferenceConfig)
                    .build()
            } else {
                val systemMessages = chatRequest.messages.filter { it.role == ModelSchema.Role.system }
                val nonSystemMessages = alternateMessagesRoles(chatRequest.messages).filter {
                    it.role != ModelSchema.Role.system
                }

                val systemContent = systemMessages.flatMap { msg ->
                    msg.content?.mapNotNull { part ->
                        part.text?.takeIf { it.isNotEmpty() }?.let { SystemContentBlock.fromText(it) }
                    } ?: emptyList()
                }

                val messages = nonSystemMessages.map { msg ->
                    val contentBlocks = msg.content?.mapNotNull { part ->
                        part.text?.takeIf { it.isNotEmpty() }?.let { ContentBlock.fromText(it) }
                    } ?: emptyList()
                    val role = when (msg.role) {
                        ModelSchema.Role.assistant -> ConversationRole.ASSISTANT
                        else -> ConversationRole.USER
                    }
                    Message.builder()
                        .role(role)
                        .content(contentBlocks)
                        .build()
                }

                val inferenceConfig = InferenceConfiguration.builder()
                    .maxTokens(model.maxOutTokens)
                    .temperature(chatRequest.temperature.toFloat())
                    .build()

                logStreams.debug("Converse request: ${messages.size} messages, ${systemContent.size} system blocks, maxTokens=${model.maxOutTokens}, temperature=${chatRequest.temperature}")

                val builder = ConverseRequest.builder()
                    .modelId(model.modelId)
                    .messages(messages)
                    .inferenceConfig(inferenceConfig)

                if (systemContent.isNotEmpty()) {
                    builder.system(systemContent)
                }

                return builder.build()
            }
        }

        fun fromConverseResponse(response: ConverseResponse): ModelSchema.ChatResponse {
            log.debug("Parsing converse response: stopReason=${response.stopReason()}")
            val outputMessage = response.output()?.message()
            val contentText = outputMessage?.content()?.joinToString("") { block ->
                block.text() ?: ""
            } ?: ""

            val usage = response.usage()
            val promptTokens = usage?.inputTokens()?.toLong() ?: 0
            val completionTokens = usage?.outputTokens()?.toLong() ?: 0
            val totalTokens = usage?.totalTokens()?.toLong() ?: (promptTokens + completionTokens)

            log.debug("Converse response: contentLength=${contentText.length}, promptTokens=$promptTokens, completionTokens=$completionTokens, totalTokens=$totalTokens")

            return ModelSchema.ChatResponse(
                choices = listOf(
                    ModelSchema.ChatChoice(
                        message = ModelSchema.ChatMessageResponse(
                            content = contentText,
                        ),
                        index = 0
                    )
                ),
                usage = ModelSchema.Usage(
                    prompt_tokens = promptTokens,
                    completion_tokens = completionTokens,
                    total_tokens = totalTokens
                )
            )
        }

        fun alternateMessagesRoles(messages: List<ModelSchema.ChatMessage>): List<ModelSchema.ChatMessage> {
            log.debug("Alternating message roles for ${messages.size} messages")
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
            log.debug("Alternated messages: ${messages.size} -> ${alternatingMessages.size} messages")
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

    }
}

private fun MutableList<BufferedOutputStream>.debug(string: String) {
    this.forEach { stream ->
        stream.write("[DEBUG] $string\n".toByteArray())
        stream.flush()
    }
}