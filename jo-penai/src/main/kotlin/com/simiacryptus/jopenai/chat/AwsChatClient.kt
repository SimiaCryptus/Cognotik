package com.simiacryptus.jopenai.chat

import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.chat.ChatModelType
import com.simiacryptus.jopenai.models.chat.LLMModel
import com.simiacryptus.util.JsonUtil
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class AwsChatClient(
    apiKey: String,
    apiBase: String,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
) : SingleProviderChatClient(
    provider = APIProvider.AWS,
    apiKey = apiKey,
    apiBase = apiBase,
    workPool = workPool,
    logLevel = logLevel,
    logStreams = logStreams
) {
    private val awsAuth: AWSAuth by lazy {
        JsonUtil.fromJson(apiKey, AWSAuth::class.java)
    }
    private val bedrockClient: BedrockRuntimeClient by lazy {
        BedrockRuntimeClient.builder().credentialsProvider(awsCredentials(awsAuth)).region(Region.of(awsAuth.region))
            .build()
    }

    override fun authorize(
        request: HttpRequest, apiProvider: APIProvider
    ) {
        // AWS Bedrock uses SDK authentication, not HTTP headers
        // This method is not used for AWS authentication
    }

    override fun chat(
        chatRequest: ApiModel.ChatRequest, model: LLMModel
    ): ApiModel.ChatResponse {
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
                    fromAWS(responseBody, model.modelName)
                } catch (e: Exception) {
                    log.error("Failed to parse AWS response for model: ${model.modelName}", e)
                    throw RuntimeException("Failed to parse AWS response", e)
                }

                val response = JsonUtil.objectMapper().readValue(result, ApiModel.ChatResponse::class.java)

                if (response.usage != null && model is ChatModelType) {
                    onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
                }

                log.info("AWS Bedrock chat completed successfully")
                response
            }
        }
    }
    private fun validateChatRequest(chatRequest: ApiModel.ChatRequest, model: LLMModel) {
        require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
        require(model.modelName.isNotBlank()) { "Model name cannot be blank" }
        require(awsAuth.region.isNotBlank()) { "AWS region must be specified" }
    }


    companion object {
        fun awsCredentials(awsAuth: AWSAuth): AwsCredentialsProviderChain =
            AwsCredentialsProviderChain.builder().credentialsProviders(
                InstanceProfileCredentialsProvider.create(),
                ProfileCredentialsProvider.create(awsAuth.profile),
            ).build()

        data class AWSAuth(
            val profile: String = "default",
            val region: String = Region.US_WEST_2.id(),
        )

        fun toAWS(model: LLMModel, chatRequest: ApiModel.ChatRequest) =
            InvokeModelRequest.builder().modelId(model.modelName).accept("application/json")
                .contentType("application/json")
                .body(SdkBytes.fromString(JsonUtil.toJson(awsBody(model, chatRequest)), Charsets.UTF_8)).build()

        fun awsBody(
            model: LLMModel, chatRequest: ApiModel.ChatRequest
        ): Map<String, Any> = when {
            model.modelName.contains("llama") -> {
                mapOf(
                    "prompt" to toSimplePrompt(chatRequest),
                    "max_gen_len" to model.maxOutTokens,
                    "temperature" to chatRequest.temperature,

                    )
            }

            model.modelName.contains("mistral") -> {
                mapOf(
                    "prompt" to toSimplePrompt(chatRequest),
                    "max_tokens" to model.maxOutTokens,
                    "temperature" to chatRequest.temperature,
                )
            }

            model.modelName.contains("titan") -> {
                mapOf(
                    "inputText" to toSimplePrompt(chatRequest), "textGenerationConfig" to mapOf(
                        "maxTokenCount" to model.maxTotalTokens,
                        "stopSequences" to emptyList<String>(),
                        "temperature" to chatRequest.temperature,
                    )
                )
            }

            model.modelName.contains("cohere") -> {
                mapOf(
                    "prompt" to toSimplePrompt(chatRequest),
                    "max_tokens" to model.maxTotalTokens,
                    "temperature" to chatRequest.temperature,
                )
            }

            model.modelName.contains("ai21") -> {
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

            model.modelName.contains("anthropic") -> {
                val alternatingMessages = alternateMessagesRoles(chatRequest.messages)
                mapOf(
                    "anthropic_version" to anthropic_version(model),
                    "max_tokens" to model.maxOutTokens,
                    "temperature" to chatRequest.temperature,
                    "messages" to alternatingMessages.filter {
                        when (it.role) {
                            ApiModel.Role.system -> false
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
                    "system" to toSimplePrompt(chatRequest) { it.role == ApiModel.Role.system },
                )
            }

            else -> throw RuntimeException("Unsupported model: $model")
        }

        fun anthropic_version(model: LLMModel) = when {
            else -> "bedrock-2023-05-31"

        }

        fun alternateMessagesRoles(messages: List<ApiModel.ChatMessage>): List<ApiModel.ChatMessage> {
            val alternatingMessages = mutableListOf<ApiModel.ChatMessage>()
            val messagesCopy = messages.toMutableList()
            var isFirst = true
            while (messagesCopy.isNotEmpty()) {
                val thisRole = messagesCopy.firstOrNull()?.role
                val consolidatedMessage = takeAll(messagesCopy, thisRole)
                if (isFirst) {
                    isFirst = false
                    if ((consolidatedMessage?.role ?: "") != "user") {
                        val chatMessage = takeAll(messagesCopy, ApiModel.Role.user)
                        alternatingMessages.add(
                            concat(
                                (consolidatedMessage ?: ApiModel.ChatMessage()).copy(role = ApiModel.Role.user),
                                chatMessage ?: ApiModel.ChatMessage()
                            )
                        )
                        continue
                    }
                }
                alternatingMessages.add(consolidatedMessage ?: ApiModel.ChatMessage())
            }
            return alternatingMessages
        }

        fun takeAll(
            messagesCopy: MutableList<ApiModel.ChatMessage>, thisRole: ApiModel.Role?
        ): ApiModel.ChatMessage? {
            val toConsolidate = messagesCopy.takeWhile { it.role == thisRole }.toTypedArray()
            messagesCopy.removeAll(toConsolidate)
            val consolidatedMessage = toConsolidate.reduceOrNull { acc, chatMessage ->
                concat(acc, chatMessage)
            }
            return consolidatedMessage
        }

        fun concat(
            acc: ApiModel.ChatMessage, chatMessage: ApiModel.ChatMessage
        ) = ApiModel.ChatMessage(
            role = acc.role, content = listOf(
                ApiModel.ContentPart(
                    type = "text",
                    text = (acc.content?.plus(chatMessage.content ?: emptyList()) ?: chatMessage.content)?.joinToString(
                        "\n"
                    ) { it.text ?: "" })
            )
        )

        fun toSimplePrompt(
            chatRequest: ApiModel.ChatRequest, filterFn: (ApiModel.ChatMessage) -> Boolean = { true }
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
                        ApiModel.ChatResponse(
                            choices = listOf(
                                ApiModel.ChatChoice(
                                    message = ApiModel.ChatMessageResponse(
                                        content = fromJson.generation ?: ""
                                    ), index = 0
                                )
                            ), usage = ApiModel.Usage(
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
                        ApiModel.ChatResponse(
                            choices = listOf(
                                ApiModel.ChatChoice(
                                    message = ApiModel.ChatMessageResponse(
                                        content = fromJson.outputs?.firstOrNull()?.text ?: ""
                                    ), index = 0
                                )
                            )
                        )
                    )
                }

                model.contains("titan") -> {
                    val fromJson = JsonUtil.fromJson<AwsResponseTitan>(responseBody, AwsResponseTitan::class.java)
                    JsonUtil.toJson(
                        ApiModel.ChatResponse(
                            choices = listOf(
                                ApiModel.ChatChoice(
                                    message = ApiModel.ChatMessageResponse(
                                        content = fromJson.results?.firstOrNull()?.outputText ?: ""
                                    ), index = 0
                                )
                            )
                        )
                    )
                }

                model.contains("cohere") -> {
                    val fromJson = JsonUtil.fromJson<AwsResponseCohere>(responseBody, AwsResponseCohere::class.java)
                    JsonUtil.toJson(
                        ApiModel.ChatResponse(
                            choices = listOf(
                                ApiModel.ChatChoice(
                                    message = ApiModel.ChatMessageResponse(
                                        content = fromJson.generations?.firstOrNull()?.text ?: ""
                                    ), index = 0
                                )
                            )
                        )
                    )
                }

                model.contains("ai21") -> {
                    val fromJson = JsonUtil.objectMapper().readValue(responseBody, Ai21ChatResponse::class.java)
                    return JsonUtil.toJson(
                        ApiModel.ChatResponse(
                            choices = fromJson.completions?.mapIndexed { index, completion ->
                                ApiModel.ChatChoice(
                                    message = ApiModel.ChatMessageResponse(
                                        content = completion.data?.text ?: ""
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
                        ApiModel.ChatResponse(
                            choices = listOf(
                                ApiModel.ChatChoice(
                                    message = ApiModel.ChatMessageResponse(
                                        content = fromJson.content?.firstOrNull()?.text ?: ""
                                    ), index = 0
                                )
                            ), usage = ApiModel.Usage(
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