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
          it.modelId == baseModelId || it.modelId == model.name
        } ?: run {
          // If not found in predefined models, create a dynamic one
          log.debug("Creating basic ChatModel for unknown Gemini model: ${baseModelId}")
          ChatModel(
            name = model.displayName ?: baseModelId,
            modelId = baseModelId,
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
      "${apiBase}/v1beta/models/${model.modelId}:generateContent?key=${apiKey.decrypt}",
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