package com.simiacryptus.cognotik.chat

import com.google.common.util.concurrent.ListeningScheduledExecutorService
  import com.simiacryptus.cognotik.CoreProviders
  import com.simiacryptus.cognotik.chat.model.AnthropicModels
  import com.simiacryptus.cognotik.chat.model.ChatModel
  import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
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
) : ChatClientBase(
  CoreProviders.Anthropic,
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
  logLevel = logLevel,
  logStreams = logStreams,
  scheduledPool = scheduledPool
) {
  override fun authorize(request: HttpRequest) {
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
          .filter { it.name == modelInfo.id || it.modelId == modelInfo.id }
        when {
          models.size == 1 -> models.first()
          else -> {
            log.debug("Unknown Anthropic model: ${modelInfo.id}")
            ChatModel(
              name = modelInfo.display_name,
              modelId = modelInfo.id,
              provider = CoreProviders.Anthropic,
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
    require(!apiBase.isBlank())
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


  @Suppress("OVERRIDE_DEPRECATION") // Depreciated only for external users
  override fun chat(
    chatRequest: ModelSchema.ChatRequest,
    model: ChatModel,
    logStreams: MutableList<BufferedOutputStream>,
    usageHandler: ((model: LLMModel, usage: ModelSchema.Usage) -> Unit)?
  ): ModelSchema.ChatResponse {
    validateChatRequest(chatRequest, model)
    return withPerformanceLogging {
      val anthropicChatRequest = try {
        val chatMessages = chatRequest.messages
        require(chatMessages.isNotEmpty()) { "Messages cannot be empty" }
        require(model.modelId?.isNotBlank() == true) { "Model name cannot be blank" }
        val max_tokens = chatRequest.max_tokens ?: model.maxOutTokens
        val temperature = if(model.supportsTemperature) chatRequest.temperature else null
        val model = chatRequest.model ?: model.modelId
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
                val thisRole =
                  remainingMessages.firstOrNull()?.role
                val toConsolidate =
                  remainingMessages.takeWhile { it.role == thisRole }
                    .toTypedArray<ModelSchema.ChatMessage>()
                remainingMessages.removeAll(toConsolidate)
                 val contentBlocks = mutableListOf<AnthropicContentInput>()
                 for (msg in toConsolidate) {
                   msg.content?.forEach { part ->
                     when {
                       part.image_url != null -> {
                         val imageUrl = part.image_url
                         if (imageUrl != null && imageUrl.startsWith("data:")) {
                           // data URI: data:<media_type>;base64,<data>
                           val withoutScheme = imageUrl.removePrefix("data:")
                           val semicolonIdx = withoutScheme.indexOf(';')
                           val mediaType = if (semicolonIdx >= 0) withoutScheme.substring(0, semicolonIdx) else "image/jpeg"
                           val base64Data = withoutScheme.substringAfter("base64,")
                           contentBlocks += AnthropicImageContentBlock(
                             source = AnthropicImageSource(
                               type = "base64",
                               media_type = mediaType,
                               data = base64Data
                             )
                           )
                         } else if (imageUrl != null) {
                           contentBlocks += AnthropicImageContentBlock(
                             source = AnthropicImageSource(
                               type = "url",
                               url = imageUrl
                             )
                           )
                         }
                       }
                       !part.text.isNullOrBlank() -> {
                         contentBlocks += AnthropicTextContentBlock(text = part.text ?: "")
                       }
                     }
                   }
                 }
                 // If no structured blocks were produced, fall back to plain text
                 if (contentBlocks.isEmpty()) {
                   val plainText = toConsolidate.joinToString("\n\n") {
                     it.content?.joinToString("\n") { it.text.orEmpty() }.orEmpty()
                   }
                   if (plainText.isNotBlank()) {
                     contentBlocks += AnthropicTextContentBlock(text = plainText)
                   }
                 }
                 if (contentBlocks.isNotEmpty()) {
                   alternatingMessages += AnthropicMessage(
                     role = thisRole.toString(),
                     content = contentBlocks
                   )
                 }
              }
              alternatingMessages
            }
          }
          .filter { !it.content.isNullOrEmpty() }
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
      val rawResponse =
        post("${apiBase}/messages", json)
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
          val response = JsonUtil.objectMapper()
            .readValue(
              rawResponse,
              AnthropicResponse::class.java
            )
          val chatResponse = ModelSchema.ChatResponse(
            id = response.id, choices = listOf(
              ModelSchema.ChatChoice(
                message = ModelSchema.ChatMessageResponse(
                  content = response.content?.joinToString(
                    "\n"
                  ) { it.text ?: "" },
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
      val response = JsonUtil.objectMapper().readValue(
        responseJson,
        ModelSchema.ChatResponse::class.java
      )
      if (response.usage != null) {
        usageHandler?.invoke(
          model,
          response.usage?.copy(cost = model.pricing(response.usage!!))!!,
        )
      }
      response
    }
  }

  private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
    require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
    require(model.modelId?.isNotBlank() == true) { "Model name cannot be blank" }
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
       val role: String? = null,
       val content: List<AnthropicContentInput>? = null
    )
     @com.fasterxml.jackson.annotation.JsonTypeInfo(
       use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
       include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY,
       property = "type"
     )
     @com.fasterxml.jackson.annotation.JsonSubTypes(
       com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = AnthropicTextContentBlock::class, name = "text"),
       com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = AnthropicImageContentBlock::class, name = "image")
     )
     sealed class AnthropicContentInput
     data class AnthropicTextContentBlock(
       val text: String
     ) : AnthropicContentInput()
     data class AnthropicImageContentBlock(
       val source: AnthropicImageSource
     ) : AnthropicContentInput()
     data class AnthropicImageSource(
       val type: String,                  // "base64" or "url"
       val media_type: String? = null,    // e.g. "image/jpeg" — required for base64
       val data: String? = null,          // base64-encoded bytes — required for base64
       val url: String? = null            // required for url type
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