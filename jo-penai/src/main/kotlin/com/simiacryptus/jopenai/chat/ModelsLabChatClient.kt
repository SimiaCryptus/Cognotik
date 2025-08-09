package com.simiacryptus.jopenai.chat

import com.simiacryptus.jopenai.HttpClientManager
import com.simiacryptus.jopenai.models.*
import com.simiacryptus.jopenai.models.chat.ChatModelType
import com.simiacryptus.jopenai.models.chat.ModelsLabDataModel
import com.simiacryptus.jopenai.models.chat.LLMModel
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.runWithPermit
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Semaphore

class ModelsLabChatClient(
    apiKey: String,
    apiBase: String = APIProvider.ModelsLab.base!!,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream> = mutableListOf(),
) : SingleProviderChatClient(
    APIProvider.ModelsLab,
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
        request.addHeader("Content-Type", "application/json")
        request.addHeader("Accept", "application/json")
        // ModelsLab uses API key in the request body, not headers
    }

    override fun chat(
        chatRequest: ApiModel.ChatRequest,
        model: LLMModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ApiModel.ChatResponse {
        return modelsLabThrottle.runWithPermit {
            val modelsLabRequest = toModelsLab(chatRequest)
            val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
                .writeValueAsString(modelsLabRequest)

            val rawResponse = post("$apiBase/llm/chat", json, APIProvider.ModelsLab)
            val responseJson = fromModelsLab(rawResponse, this)

            val response: ApiModel.ChatResponse =
                JsonUtil.objectMapper().readValue(responseJson, ApiModel.ChatResponse::class.java)
            if (response.usage != null && model is ChatModelType) {
                onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
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

        fun fromModelsLab(rawResponse: String, client: ChatClientInterface): String {
            val response = JsonUtil.objectMapper().readValue(rawResponse, ModelsLabDataModel.ChatResponse::class.java)
            return when (response.status) {
                "success" -> {
                    JsonUtil.toJson(
                        ApiModel.ChatResponse(
                            id = response.chat_id, choices = listOf(
                                ApiModel.ChatChoice(
                                    message = ApiModel.ChatMessageResponse(content = response.message), index = 0
                                )
                            ), usage = response.meta?.let {
                                ApiModel.Usage(
                                    prompt_tokens = it.max_new_tokens?.toLong() ?: 0,
                                    completion_tokens = 0,

                                    total_tokens = it.max_new_tokens?.toLong() ?: 0
                                )
                            })
                    )
                }

                "processing" -> {
                    val seconds = response?.eta ?: 1
                    ProvidersChatClient.Companion.log.info("Chat response is still processing; waiting ${seconds}s and trying again.")
                    Thread.sleep(seconds * 1000L)
                    val key = "" /*client.apiKeyMap[APIProvider.ModelsLab]*/
                    val url = "" /*"${client.apiBaseMap}/llm/get_queued_response"*/
                    val postCheck = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        mapOf(
                            "chat_id" to (response.meta?.chat_id ?: response.chat_id),
                            "key" to key
                        )
                    )
                    fromModelsLab(
                        (client as ChatClientBase).post(
                            url,
                            postCheck,
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

        fun fromModelsLab(rawResponse: String, client: ModelsLabChatClient): String {
            val response = JsonUtil.objectMapper().readValue(rawResponse, ModelsLabDataModel.ChatResponse::class.java)
            return when (response.status) {
                "success" -> {
                    JsonUtil.toJson(
                        ApiModel.ChatResponse(
                            id = response.chat_id, choices = listOf(
                                ApiModel.ChatChoice(
                                    message = ApiModel.ChatMessageResponse(content = response.message), index = 0
                                )
                            ), usage = response.meta?.let {
                                ApiModel.Usage(
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
                    val postCheck = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        mapOf(
                            "chat_id" to (response.meta?.chat_id ?: response.chat_id),
                            "key" to client.apiKey
                        )
                    )
                    fromModelsLab(
                        client.post(
                            "${client.apiBase}/llm/get_queued_response",
                            postCheck,
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

        fun toModelsLab(chatRequest: ApiModel.ChatRequest) = modelslab_chatRequest_prototype.copy(
            model_id = chatRequest.model,
            system_prompt = chatRequest.messages.filter { it.role == ApiModel.Role.system }.joinToString("\n") {
                it.content?.joinToString("\n") { it.text ?: "" } ?: ""
            },
            prompt = chatRequest.messages.filter { it.role != ApiModel.Role.system }.joinToString("\n") {
                it.content?.joinToString("\n") { it.text ?: "" } ?: ""
            },
            temperature = chatRequest.temperature,
        )

    }
}