package com.simiacryptus.cognotik.chat.model

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

  @JvmStatic
  val values = mapOf(
    "Grok46" to Grok46,
    "Grok4" to Grok4,
    "Grok4FastReasoning" to Grok4FastReasoning,
    "Grok4FastNonReasoning" to Grok4FastNonReasoning,
    "Grok3Mini" to Grok3Mini,
  )
}