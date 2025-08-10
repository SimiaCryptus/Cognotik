package com.simiacryptus.jopenai.chat

import com.simiacryptus.jopenai.models.APIProvider
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.chat.model.ChatModelType
import com.simiacryptus.jopenai.models.LLMModel
import com.simiacryptus.jopenai.util.ClientUtil.checkError
import com.simiacryptus.util.JsonUtil
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.util.concurrent.ExecutorService

class GoogleChatClient(
    apiKey: String,
    apiBase: String,
    workPool: ExecutorService,
    logLevel: Level = Level.INFO,
    logStreams: MutableList<BufferedOutputStream>,
) : SingleProviderChatClient(
    APIProvider.Google,
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
        // Google API uses API key as query parameter, not in headers
    }

    override fun chat(
        chatRequest: ApiModel.ChatRequest,
        model: LLMModel,
        logStreams: MutableList<java.io.BufferedOutputStream>
    ): ApiModel.ChatResponse {
        val geminiChatRequest = toGeminiChatRequest(chatRequest, model)
        val json = JsonUtil.objectMapper()
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(geminiChatRequest)

        val responseBody = post(
            "${apiBase}/v1beta/models/${model.modelName}:generateContent?key=$apiKey",
            json,
            APIProvider.Google
        )
        checkError(responseBody)

        val responseJson = fromGemini(responseBody)
        val response = JsonUtil.objectMapper()
            .readValue(responseJson, ApiModel.ChatResponse::class.java)
        if (response.usage != null && model is ChatModelType) {
            onUsage(model, response.usage.copy(cost = model.pricing(response.usage)))
        }
        return response
    }

    companion object {

        fun fromGemini(responseBody: String): String {
            val fromJson = JsonUtil.fromJson<GenerateContentResponse>(responseBody, GenerateContentResponse::class.java)
            return JsonUtil.toJson(
                ApiModel.ChatResponse(
                    choices = fromJson.candidates?.mapIndexed { index, candidate ->
                        ApiModel.ChatChoice(
                            message = ApiModel.ChatMessageResponse(
                                content = candidate.content?.parts?.joinToString("\n") { it.text ?: "" }), index = index
                        )
                    } ?: emptyList(),
                ))
        }

        fun toGeminiChatRequest(chatRequest: ApiModel.ChatRequest, model: LLMModel): GenerateContentRequest {
            return GenerateContentRequest(
                contents = collectRoleSequences(chatRequest.messages.filter {
                    when (it.role) {

                        else -> true
                    }
                }.map {
                    Content(
                        role = when (it.role) {
                            ApiModel.Role.user -> "user"
                            ApiModel.Role.system -> "user"
                            ApiModel.Role.assistant -> "model"
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
            val candidates: List<Candidate>? = null
        )

        data class Candidate(
            val content: Content? = null,

            val finishReason: String? = null, val index: Int? = null, val safetyRatings: List<SafetyRating>? = null
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