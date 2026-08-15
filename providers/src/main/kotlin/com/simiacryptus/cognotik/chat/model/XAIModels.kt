package com.simiacryptus.cognotik.chat.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.models.ModelSchema.TokenTypes

/**
 * Model catalog for xAI (Grok).
 * API is OpenAI-compatible: https://api.x.ai/v1
 * Prices are USD per 1K tokens (published per-million prices / 1000).
 */
object XAIModels {

  @JvmStatic
  val Grok46 = ChatModel(
    name = "Grok46",
    modelId = "grok-4.6",
    maxTotalTokens = 500000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 2.00 / 1000.0,
      TokenTypes.Cached to 0.50 / 1000.0,
      TokenTypes.Completion to 6.00 / 1000.0,
      TokenTypes.Thinking to 6.00 / 1000.0,
    ),
    supportsTemperature = false,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok45 = ChatModel(
    name = "Grok45",
    modelId = "grok-4.5",
    maxTotalTokens = 500000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 2.00 / 1000.0,
      TokenTypes.Cached to 0.30 / 1000.0,
      TokenTypes.Completion to 6.00 / 1000.0,
      TokenTypes.Thinking to 6.00 / 1000.0,
    ),
    supportsTemperature = false,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok43 = ChatModel(
    name = "Grok43",
    modelId = "grok-4.3",
    maxTotalTokens = 1000000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 1.25 / 1000.0,
      TokenTypes.Cached to 0.20 / 1000.0,
      TokenTypes.Completion to 2.50 / 1000.0,
      TokenTypes.Thinking to 2.50 / 1000.0,
    ),
    supportsTemperature = false,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok420Reasoning = ChatModel(
    name = "Grok420Reasoning",
    modelId = "grok-4.20-0309-reasoning",
    maxTotalTokens = 1000000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 1.25 / 1000.0,
      TokenTypes.Cached to 0.20 / 1000.0,
      TokenTypes.Completion to 2.50 / 1000.0,
      TokenTypes.Thinking to 2.50 / 1000.0,
    ),
    supportsTemperature = false,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok420NonReasoning = ChatModel(
    name = "Grok420NonReasoning",
    modelId = "grok-4.20-0309-non-reasoning",
    maxTotalTokens = 1000000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 1.25 / 1000.0,
      TokenTypes.Cached to 0.20 / 1000.0,
      TokenTypes.Completion to 2.50 / 1000.0,
    ),
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val GrokBuild01 = ChatModel(
    name = "GrokBuild01",
    modelId = "grok-build-0.1",
    maxTotalTokens = 256000,
    maxOutTokens = 32000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 1.00 / 1000.0,
      TokenTypes.Cached to 0.20 / 1000.0,
      TokenTypes.Completion to 2.00 / 1000.0,
    ),
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok420MultiAgent = ChatModel(
    name = "Grok420MultiAgent",
    modelId = "grok-4.20-multi-agent-0309",
    maxTotalTokens = 1000000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 1.25 / 1000.0,
      TokenTypes.Cached to 0.20 / 1000.0,
      TokenTypes.Completion to 2.50 / 1000.0,
      TokenTypes.Thinking to 2.50 / 1000.0,
    ),
    supportsTemperature = false,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok4 = ChatModel(
    name = "Grok4",
    modelId = "grok-4-0709",
    maxTotalTokens = 256000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 3.0 / 1000.0,
      TokenTypes.Completion to 15.0 / 1000.0,
      TokenTypes.Thinking to 15.0 / 1000.0,
    ),
    supportsTemperature = false,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok4FastReasoning = ChatModel(
    name = "Grok4FastReasoning",
    modelId = "grok-4-fast-reasoning",
    maxTotalTokens = 2000000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.20 / 1000.0,
      TokenTypes.Completion to 0.50 / 1000.0,
      TokenTypes.Thinking to 0.50 / 1000.0,
    ),
    supportsTemperature = false,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok4FastNonReasoning = ChatModel(
    name = "Grok4FastNonReasoning",
    modelId = "grok-4-fast-non-reasoning",
    maxTotalTokens = 2000000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.20 / 1000.0,
      TokenTypes.Completion to 0.50 / 1000.0,
    ),
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok3Mini = ChatModel(
    name = "Grok3Mini",
    modelId = "grok-3-mini",
    maxTotalTokens = 131072,
    maxOutTokens = 32768,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.30 / 1000.0,
      TokenTypes.Completion to 0.50 / 1000.0,
      TokenTypes.Thinking to 0.50 / 1000.0,
    ),
    supportsReasoning = true,
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  /**
   * Image generation/editing model. Accessed via the /images/generations
   * and /images/edits endpoints (not /chat/completions).
   */
  @JvmStatic
  val GrokImagineImage2 = ChatModel(
    name = "GrokImagineImage2",
    modelId = "grok-imagine-image-2.0",
    maxTotalTokens = 4096,
    maxOutTokens = 4096,
    provider = CoreProviders.XAI,
    tokenPricingPerK = mapOf(),
    supportsTemperature = false,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.IMAGE),
  )


  @JvmStatic
  val values = mapOf(
    "Grok46" to Grok46,
    "Grok45" to Grok45,
    "Grok43" to Grok43,
    "Grok420Reasoning" to Grok420Reasoning,
    "Grok420NonReasoning" to Grok420NonReasoning,
    "GrokBuild01" to GrokBuild01,
    "Grok420MultiAgent" to Grok420MultiAgent,
    "Grok4" to Grok4,
    "Grok4FastReasoning" to Grok4FastReasoning,
    "Grok4FastNonReasoning" to Grok4FastNonReasoning,
    "Grok3Mini" to Grok3Mini,
    "GrokImagineImage2" to GrokImagineImage2,
  )
}

/** Reference to a source image for editing, as expected by the xAI /images/edits endpoint. */
data class XAIImageRef(
  val url: String,
  val type: String = "image_url",
)

data class XAIImageGenerationRequest(
  val model: String,
  val prompt: String,
  val n: Int? = null,
  val response_format: String? = null,
)

data class XAIImageEditRequest(
  val model: String,
  val prompt: String,
  val image: XAIImageRef,
  val n: Int? = null,
  val response_format: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class XAIImageData(
  val url: String? = null,
  val b64_json: String? = null,
  val revised_prompt: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class XAIImageResponse(
  val created: Long? = null,
  val data: List<XAIImageData> = emptyList(),
)