package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders

/**
 * Model catalog for xAI (Grok).
 * API is OpenAI-compatible: https://api.x.ai/v1
 * Prices are USD per 1K tokens (published per-million prices / 1000).
 */
object XAIModels {

  @JvmStatic
  val Grok4 = ChatModel(
    name = "Grok4",
    modelId = "grok-4-0709",
    maxTotalTokens = 256000,
    maxOutTokens = 64000,
    provider = CoreProviders.XAI,
    inputTokenPricePerK = 3.0 / 1000.0,
    outputTokenPricePerK = 15.0 / 1000.0,
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
    inputTokenPricePerK = 0.20 / 1000.0,
    outputTokenPricePerK = 0.50 / 1000.0,
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
    inputTokenPricePerK = 0.20 / 1000.0,
    outputTokenPricePerK = 0.50 / 1000.0,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val GrokCodeFast1 = ChatModel(
    name = "GrokCodeFast1",
    modelId = "grok-code-fast-1",
    maxTotalTokens = 256000,
    maxOutTokens = 32000,
    provider = CoreProviders.XAI,
    inputTokenPricePerK = 0.20 / 1000.0,
    outputTokenPricePerK = 1.50 / 1000.0,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok3 = ChatModel(
    name = "Grok3",
    modelId = "grok-3",
    maxTotalTokens = 131072,
    maxOutTokens = 32768,
    provider = CoreProviders.XAI,
    inputTokenPricePerK = 3.0 / 1000.0,
    outputTokenPricePerK = 15.0 / 1000.0,
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok3Mini = ChatModel(
    name = "Grok3Mini",
    modelId = "grok-3-mini",
    maxTotalTokens = 131072,
    maxOutTokens = 32768,
    provider = CoreProviders.XAI,
    inputTokenPricePerK = 0.30 / 1000.0,
    outputTokenPricePerK = 0.50 / 1000.0,
    supportsReasoning = true,
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Grok2Vision = ChatModel(
    name = "Grok2Vision",
    modelId = "grok-2-vision-1212",
    maxTotalTokens = 32768,
    maxOutTokens = 8192,
    provider = CoreProviders.XAI,
    inputTokenPricePerK = 2.0 / 1000.0,
    outputTokenPricePerK = 10.0 / 1000.0,
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val values = mapOf(
    "Grok4" to Grok4,
    "Grok4FastReasoning" to Grok4FastReasoning,
    "Grok4FastNonReasoning" to Grok4FastNonReasoning,
    "GrokCodeFast1" to GrokCodeFast1,
    "Grok3" to Grok3,
    "Grok3Mini" to Grok3Mini,
    "Grok2Vision" to Grok2Vision,
  )
}