package com.simiacryptus.cognotik.chat

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import com.google.common.util.concurrent.ListeningScheduledExecutorService
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.chat.model.AnthropicModels
import com.simiacryptus.cognotik.exceptions.*
import com.simiacryptus.cognotik.exceptions.ErrorUtil.checkError
import com.simiacryptus.cognotik.platform.model.*
import com.simiacryptus.cognotik.platform.model.ChatModel.ReasoningLevel
import com.simiacryptus.cognotik.platform.model.mcp.MCPContent
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.SecureString
import org.apache.hc.core5.http.HttpRequest
import org.slf4j.LoggerFactory.getLogger
import org.slf4j.event.Level
import java.io.BufferedOutputStream
import java.io.IOException
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
  session: Session,
) : ChatClientBase(
  CoreProviders.Anthropic,
  apiKey = apiKey,
  apiBase = apiBase,
  workPool = workPool,
  logLevel = logLevel,
  logStreams = logStreams,
  scheduledPool = scheduledPool,
  session = session,
) {
  override fun authorize(request: HttpRequest) {
    request.addHeader("Content-Type", "application/json")
    request.addHeader("Accept", "application/json")
    request.addHeader("x-api-key", apiKey.decrypt)
    request.addHeader("anthropic-version", "2023-06-01")
  }

  override fun getModels(): List<ChatModel> {
    // Check cache first
    modelsCache[apiBase]?.let { return it }

    return try {
      val modelsResponse = fetchAllModels()
      val models = modelsResponse.flatMap { modelInfo ->
        val models = AnthropicModels.values.values
          .filter { it.name == modelInfo.id || it.modelId == modelInfo.id }
        when {
          models.size == 1 -> models.first()
          else -> {
            log.debug("Unknown Anthropic model: ${modelInfo.id}")
            ChatModel(
              name = modelInfo.display_name,
              modelId = modelInfo.id,
              maxTotalTokens = 200000,
              maxOutTokens = 64000,
              provider = CoreProviders.Anthropic,
              outputTokenPricePerK = 0.0, // TODO: Set actual pricing if known
              inputModalities = setOf(ChatMessageModality.TEXT),
              outputModalities = setOf(ChatMessageModality.TEXT), // TODO: Set actual pricing if known
              supportsTools = true, // all current Anthropic chat models support tool use
            )
          }
        }.let { chatModel -> ReasoningVariants.expand(chatModel) }
      }.distinctBy { it.modelId }
      // Cache the result
      modelsCache[apiBase] = models
      models
    } catch (e: Exception) {
      log.error("Failed to fetch Anthropic models", e)
      emptyList()
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
      checkAnthropicError(response, null)
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
    usageHandler: UsageListener
  ): ModelSchema.ChatResponse {
    validateChatRequest(chatRequest, model)
    check(chatRequest.toolProviders.isNullOrEmpty() || !chatRequest.tools.isNullOrEmpty()) {
      TOOL_PROVIDERS_WITHOUT_TOOLS
    }
    return withPerformanceLogging {
      val anthropicChatRequest = try {
        buildRequest(chatRequest, model)
      } catch (e: Exception) {
        log.error("Failed to map chat request to Anthropic format", e)
        // An invalid request will not succeed on retry
        throw NonRetryableException("Failed to map chat request to Anthropic format: ${e.message}", e)
      }
      val json = JsonUtil.objectMapper().writerWithDefaultPrettyPrinter()
        .writeValueAsString(anthropicChatRequest)
      val rawResponse =
        post("${apiBase}/messages", json, model = model.modelId)
      checkAnthropicError(rawResponse, model)
      checkError(rawResponse, model)
      val response = try {
        require(rawResponse.isNotBlank()) { "Response cannot be blank" }
        toChatResponse(JsonUtil.objectMapper().readValue(rawResponse, AnthropicResponse::class.java))
      } catch (e: IOException) {
        // Typed provider errors (AIServiceException etc.) and parser errors propagate unchanged
        throw e
      } catch (e: NonRetryableException) {
        throw e
      } catch (e: Exception) {
        log.error("Failed to parse Anthropic response: $rawResponse", e)
        throw RuntimeException("Failed to parse Anthropic response: ${e.message}", e)
      }
      response.usage?.let { usage ->
        usageHandler.onUsage(
          model, usage, ModelSchema.UsageData(
            input_text = anthropicChatRequest.messages?.joinToString("\n\n") { msg ->
              msg.content?.joinToString("\n") { part -> describeBlock(part) }.orEmpty()
            }.orEmpty(),
            output_text = response.choices.joinToString("\n\n") { it.message?.content.orEmpty() }
          ))
      }
      response
    }
  }

  private fun buildRequest(chatRequest: ModelSchema.ChatRequest, model: ChatModel): AnthropicChatRequest {
    val chatMessages = chatRequest.messages
    require(chatMessages.isNotEmpty()) { "Messages cannot be empty" }
    require(model.modelId.isNotBlank()) { "Model name cannot be blank" }
    val rawModelId = chatRequest.model ?: model.modelId
    val reasoningLevel = model.reasoningLevel ?: reasoningLevelFromSuffix(rawModelId)
    val maxTokens = chatRequest.max_tokens ?: model.maxOutTokens
    var temperature: Double? = if (model.supportsTemperature) chatRequest.temperature else null
    val system = chatMessages
      .firstOrNull { it.role == ModelSchema.Role.system }
      ?.content
      ?.joinToString("\n\n") { it.text.orEmpty() }
    val messages = toAnthropicMessages(chatMessages.filter { it.role != ModelSchema.Role.system })
      .filter { !it.content.isNullOrEmpty() }
    // Adaptive thinking (implies interleaved thinking between tool calls on current models)
    val thinking = reasoningLevel?.let { ThinkingConfig() }
    val outputConfig = reasoningLevel?.let {
      OutputConfig(
        effort = when (it) {
          ReasoningLevel.Low -> EffortLevel.low
          ReasoningLevel.Medium -> EffortLevel.medium
          ReasoningLevel.High -> EffortLevel.high
          ReasoningLevel.X -> EffortLevel.max
        }
      )
    }
    if (thinking != null) temperature = 1.0
    val tools = chatRequest.tools?.takeIf { it.isNotEmpty() }?.map {
      AnthropicTool(name = it.name, description = it.description.ifBlank { null }, input_schema = it.inputSchema)
    }
    val toolChoice = if (tools != null) anthropicToolChoice(chatRequest.tool_choice, thinking != null) else null
    return AnthropicChatRequest(
      model = stemModelId(rawModelId),
      system = system,
      messages = messages,
      max_tokens = maxTokens,
      temperature = temperature,
      thinking = thinking,
      output_config = outputConfig,
      tools = tools,
      tool_choice = toolChoice,
    )
  }

  /**
   * Consolidates consecutive same-role messages into alternating Anthropic messages,
   * preserving block order (thinking must precede tool_use) and placing tool_result
   * blocks first within a message, as the API requires.
   */
  private fun toAnthropicMessages(chatMessages: List<ModelSchema.ChatMessage>): List<AnthropicMessage> {
    val alternating = mutableListOf<AnthropicMessage>()
    val remaining = chatMessages.toMutableList()
    while (remaining.isNotEmpty()) {
      val thisRole = remaining.first().role
      val toConsolidate = remaining.takeWhile { it.role == thisRole }
      repeat(toConsolidate.size) { remaining.removeAt(0) }
      val blocks = mutableListOf<AnthropicContentInput>()
      for (msg in toConsolidate) {
        msg.content?.forEach { part -> blocks += toAnthropicBlocks(part) }
      }
      // If no structured blocks were produced, fall back to plain text (cannot contain tool blocks)
      if (blocks.isEmpty()) {
        val plainText = toConsolidate.joinToString("\n\n") {
          it.content?.joinToString("\n") { p -> p.text.orEmpty() }.orEmpty()
        }
        if (plainText.isNotBlank()) blocks += AnthropicTextContentBlock(text = plainText)
      }
      val ordered = blocks.filterIsInstance<AnthropicToolResultBlock>() + blocks.filter { it !is AnthropicToolResultBlock }
      if (ordered.isNotEmpty()) {
        alternating += AnthropicMessage(role = thisRole.toString(), content = ordered)
      }
    }
    return alternating
  }

  private fun toAnthropicBlocks(part: ModelSchema.ContentPart): List<AnthropicContentInput> {
    val out = mutableListOf<AnthropicContentInput>()
    val thinking = part.thinking
    if (thinking != null) {
      val origin = thinking.provider
      if (origin == null || origin == PROVIDER_TAG) {
        val redacted = thinking.redacted
        val signature = thinking.signature
        when {
          redacted != null -> out += AnthropicRedactedThinkingBlock(data = redacted)
          signature != null -> out += AnthropicThinkingBlock(thinking = thinking.text.orEmpty(), signature = signature)
          else -> log.debug("Dropping unsigned thinking block")
        }
      } else {
        log.debug("Dropping thinking block produced by provider {}", origin)
      }
    }
    part.tool_use?.let { out += AnthropicToolUseBlock(id = it.id, name = it.name, input = it.input) }
    part.tool_result?.let { result ->
      out += AnthropicToolResultBlock(
        tool_use_id = result.tool_use_id,
        content = listOf(AnthropicTextContentBlock(text = toolResultText(result))),
        is_error = if (result.is_error) true else null,
      )
    }
    val imageUrl = part.image_url
    when {
      imageUrl != null && imageUrl.startsWith("data:") -> {
        // data URI: data:<media_type>;base64,<data>
        val withoutScheme = imageUrl.removePrefix("data:")
        val semicolonIdx = withoutScheme.indexOf(';')
        val mediaType = if (semicolonIdx >= 0) withoutScheme.substring(0, semicolonIdx) else "image/jpeg"
        out += AnthropicImageContentBlock(
          source = AnthropicImageSource(type = "base64", media_type = mediaType, data = withoutScheme.substringAfter("base64,"))
        )
      }

      imageUrl != null -> out += AnthropicImageContentBlock(source = AnthropicImageSource(type = "url", url = imageUrl))

      !part.text.isNullOrBlank() -> out += AnthropicTextContentBlock(text = part.text ?: "")
    }
    return out
  }

  private fun toolResultText(result: ModelSchema.ToolResultBlock): String {
    val text = result.content.joinToString("\n") { (it as? MCPContent.Text)?.text ?: "[${it.javaClass.simpleName}]" }
    return text.ifBlank { result.structured?.toString() ?: "(no output)" }
  }

  private fun toChatResponse(response: AnthropicResponse): ModelSchema.ChatResponse {
    val blocks = toNeutralBlocks(response.content)
    return ModelSchema.ChatResponse(
      id = response.id,
      choices = listOf(
        ModelSchema.ChatChoice(
          message = ModelSchema.ChatMessageResponse(
            content = blocks.mapNotNull { it.text }.joinToString("\n"),
            blocks = blocks,
          ),
          index = 0,
          finish_reason = if (response.stop_reason == "tool_use") "tool_calls" else response.stop_reason,
        )
      ),
      usage = ModelSchema.Usage(
        prompt_tokens = response.usage?.input_tokens?.toLong() ?: 0,
        completion_tokens = response.usage?.output_tokens?.toLong() ?: 0,
      )
    )
  }

  private fun toNeutralBlocks(content: List<AnthropicContentBlock>?): List<ModelSchema.ContentPart> =
    content.orEmpty().mapNotNull { block ->
      when (block.type) {
        "text" -> ModelSchema.ContentPart(text = block.text.orEmpty())
        "thinking" -> ModelSchema.ContentPart(
          thinking = ModelSchema.ThinkingBlock(text = block.thinking, signature = block.signature, provider = PROVIDER_TAG)
        )

        "redacted_thinking" -> ModelSchema.ContentPart(
          thinking = ModelSchema.ThinkingBlock(redacted = block.data, provider = PROVIDER_TAG)
        )

        "tool_use" -> ModelSchema.ContentPart(
          tool_use = ModelSchema.ToolUseBlock(
            id = block.id ?: "toolu_missing_${System.nanoTime()}",
            name = block.name.orEmpty(),
            input = block.input ?: JsonUtil.objectMapper().createObjectNode(),
          )
        )

        else -> {
          log.warn("Ignoring unsupported Anthropic content block type: {}", block.type)
          null
        }
      }
    }

  private fun describeBlock(part: AnthropicContentInput): String = when (part) {
    is AnthropicTextContentBlock -> part.text
    is AnthropicImageContentBlock -> "[Image: ${part.source.type}]"
    is AnthropicToolUseBlock -> "[tool_use ${part.name}: ${part.input}]"
    is AnthropicToolResultBlock -> "[tool_result ${part.tool_use_id}: ${part.content.joinToString(" ") { describeBlock(it) }}]"
    is AnthropicThinkingBlock -> "[thinking]"
    is AnthropicRedactedThinkingBlock -> "[redacted thinking]"
  }

  /**
   * Maps Anthropic-native error bodies ({"type":"error","error":{"type":...,"message":...}})
   * to Cognotik typed exceptions, since ErrorUtil.checkError only knows OpenAI-style messages.
   * Returns normally if the body is not an Anthropic error.
   */
  private fun checkAnthropicError(body: String, model: LLMModel?) {
    val node = try {
      JsonUtil.objectMapper().readTree(body)
    } catch (e: Exception) {
      return // let checkError report invalid JSON
    } ?: return
    if (node.path("type").asText() != "error") return
    val err = node.path("error")
    val errType = err.path("type").asText("")
    val message = err.path("message").asText(node.path("message").asText("Unknown Anthropic error"))
    when (errType) {
      "overloaded_error", "api_error" -> throw RequestOverloadException("Anthropic: $message")
      "rate_limit_error" -> throw RateLimitException("anthropic", 0, 60)
      "not_found_error" ->
        if (message.contains("model", ignoreCase = true)) throw InvalidModelException(model?.modelId)

      "invalid_request_error" -> {
        Regex("""prompt is too long: (\d+) tokens > (\d+) maximum""").find(message)?.let { m ->
          val prompt = m.groupValues[1].toInt()
          val max = m.groupValues[2].toInt()
          throw ModelMaxException(max, prompt, prompt, 0)
        }
        if (message.contains("credit balance", ignoreCase = true) ||
          message.contains("billing", ignoreCase = true)
        ) throw QuotaException()
      }
    }
    throw IOException("Anthropic API error ($errType): $message")
  }


  private fun validateChatRequest(chatRequest: ModelSchema.ChatRequest, model: LLMModel) {
    require(chatRequest.messages.isNotEmpty()) { "Chat request must contain messages" }
    require(model.modelId.isNotBlank()) { "Model name cannot be blank" }
    require(chatRequest.model?.isNotBlank() == true) { "Chat request model must be specified" }
  }


  companion object {
    private val log = getLogger(AnthropicChatClient::class.java)
    private val modelsCache = ConcurrentHashMap<String, List<ChatModel>>()
    /** Origin tag for thinking blocks; thinking from other providers is never sent to Anthropic. */
    const val PROVIDER_TAG = "anthropic"
    private const val TOOL_PROVIDERS_WITHOUT_TOOLS =
      "ChatRequest.toolProviders is set but tools is empty: tool requests must go through ChatInterface " +
          "(McpToolLoop); provider clients do not execute tools"

    /** Returns the reasoning level encoded as a suffix on the model id, if any. */
    fun reasoningLevelFromSuffix(modelId: String): ReasoningLevel? =
      ReasoningVariants.levelFromModelId(modelId)

    /** Strips a reasoning level suffix (e.g. "-high") from the model id, if present. */
    fun stemModelId(modelId: String): String = ReasoningVariants.stem(modelId)

    /** With thinking enabled only auto/none are accepted, so forced choices are downgraded. */
    private fun anthropicToolChoice(choice: String?, thinkingEnabled: Boolean): AnthropicToolChoice? =
      when (val c = choice?.trim()?.takeIf { it.isNotEmpty() }) {
        null, "auto" -> null
        "none" -> AnthropicToolChoice(type = "none")
        else -> if (thinkingEnabled) {
          log.warn("tool_choice '{}' is not supported with extended thinking; using auto", c)
          null
        } else if (c == "any") {
          AnthropicToolChoice(type = "any")
        } else {
          AnthropicToolChoice(type = "tool", name = c)
        }
      }

    data class AnthropicChatRequest(
      val model: String? = null,
      val system: String? = null,
      val messages: List<AnthropicMessage>? = null,
      val max_tokens: Int? = null,
      val temperature: Double? = null,
      val top_p: Double? = null,
      val top_k: Int? = null,
      val thinking: ThinkingConfig? = null,
      val output_config: OutputConfig? = null,
      val tools: List<AnthropicTool>? = null,
      val tool_choice: AnthropicToolChoice? = null,
    )

    data class AnthropicTool(
      val name: String,
      val description: String? = null,
      val input_schema: JsonNode,
    )

    data class AnthropicToolChoice(
      val type: String,
      val name: String? = null,
    )

    data class OutputConfig(
      val effort: EffortLevel
    )

    enum class EffortLevel {
      low,
      medium,
      high,
      xhigh,
      max
    }

    data class ThinkingConfig(
      val `type`: ThinkingConfigType = ThinkingConfigType.adaptive,
      val budget_tokens: Int? = null
    )

    enum class ThinkingConfigType {
      adaptive,
      disabled,
      enabled
    }

    data class AnthropicMessage(
      val role: String? = null,
      val content: List<AnthropicContentInput>? = null
    )

    @JsonTypeInfo(
      use = JsonTypeInfo.Id.NAME,
      include = JsonTypeInfo.As.PROPERTY,
      property = "type"
    )
    @JsonSubTypes(
      JsonSubTypes.Type(value = AnthropicTextContentBlock::class, name = "text"),
      JsonSubTypes.Type(value = AnthropicImageContentBlock::class, name = "image"),
      JsonSubTypes.Type(value = AnthropicToolUseBlock::class, name = "tool_use"),
      JsonSubTypes.Type(value = AnthropicToolResultBlock::class, name = "tool_result"),
      JsonSubTypes.Type(value = AnthropicThinkingBlock::class, name = "thinking"),
      JsonSubTypes.Type(value = AnthropicRedactedThinkingBlock::class, name = "redacted_thinking"),
    )
    sealed class AnthropicContentInput
    data class AnthropicTextContentBlock(
      val text: String
    ) : AnthropicContentInput()

    data class AnthropicImageContentBlock(
      val source: AnthropicImageSource
    ) : AnthropicContentInput()

    data class AnthropicToolUseBlock(
      val id: String,
      val name: String,
      val input: JsonNode,
    ) : AnthropicContentInput()

    data class AnthropicToolResultBlock(
      val tool_use_id: String,
      val content: List<AnthropicContentInput>,
      val is_error: Boolean? = null,
    ) : AnthropicContentInput()

    /** Must be replayed exactly as received (signature is opaque). */
    data class AnthropicThinkingBlock(
      val thinking: String,
      val signature: String,
    ) : AnthropicContentInput()

    data class AnthropicRedactedThinkingBlock(
      val data: String,
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

    /** Tolerant response block: covers text, thinking, redacted_thinking and tool_use. */
    data class AnthropicContentBlock(
      val type: String? = null,
      val text: String? = null,
      val id: String? = null,
      val name: String? = null,
      val input: JsonNode? = null,
      val thinking: String? = null,
      val signature: String? = null,
      val data: String? = null,
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