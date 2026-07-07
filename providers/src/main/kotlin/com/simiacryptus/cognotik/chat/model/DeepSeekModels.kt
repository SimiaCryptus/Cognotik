package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.models.ModelSchema.TokenTypes

@Suppress("unused")
object DeepSeekModels {

  // Pricing is quoted per 1M tokens in the DeepSeek docs; convert to per-1k.
  // deepseek-v4-flash:
  //   cache hit (Cached) input: $0.0028 / 1M
  //   cache miss (Prompt) input: $0.14   / 1M
  //   output (Completion):       $0.28   / 1M
  private val flashPricing = mapOf(
    TokenTypes.Prompt to 0.14 / 1000.0,
    TokenTypes.Cached to 0.0028 / 1000.0,
    TokenTypes.Completion to 0.28 / 1000.0,
    TokenTypes.Thinking to 0.28 / 1000.0,
  )

  // deepseek-v4-pro:
  //   cache hit (Cached) input: $0.003625 / 1M
  //   cache miss (Prompt) input: $0.435   / 1M
  //   output (Completion):       $0.87    / 1M
  private val proPricing = mapOf(
    TokenTypes.Prompt to 0.435 / 1000.0,
    TokenTypes.Cached to 0.003625 / 1000.0,
    TokenTypes.Completion to 0.87 / 1000.0,
    TokenTypes.Thinking to 0.87 / 1000.0,
  )

  val DeepSeekV4Flash = ChatModel(
    name = "DeepSeekV4Flash",
    modelId = "deepseek-v4-flash",
    maxTotalTokens = 1_000_000,
    maxOutTokens = 384_000,
    provider = CoreProviders.DeepSeek,
    tokenPricingPerK = flashPricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  val DeepSeekV4Pro = ChatModel(
    name = "DeepSeekV4Pro",
    modelId = "deepseek-v4-pro",
    maxTotalTokens = 1_000_000,
    maxOutTokens = 384_000,
    provider = CoreProviders.DeepSeek,
    tokenPricingPerK = proPricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  // Deprecated aliases (removal scheduled 2026/07/24). deepseek-chat maps to the
  // non-thinking mode and deepseek-reasoner to the thinking mode of deepseek-v4-flash.
  val DeepSeekChat = ChatModel(
    name = "DeepSeekChat",
    modelId = "deepseek-chat",
    maxTotalTokens = 1_000_000,
    maxOutTokens = 384_000,
    provider = CoreProviders.DeepSeek,
    tokenPricingPerK = flashPricing,
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  val DeepSeekReasoner = ChatModel(
    name = "DeepSeekReasoner",
    modelId = "deepseek-reasoner",
    maxTotalTokens = 1_000_000,
    maxOutTokens = 384_000,
    provider = CoreProviders.DeepSeek,
    tokenPricingPerK = flashPricing,
    supportsReasoning = true,
    deprecated = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  val values = mapOf(
    "DeepSeekV4Flash" to DeepSeekV4Flash,
    "DeepSeekV4Pro" to DeepSeekV4Pro,
    "DeepSeekChat" to DeepSeekChat,
    "DeepSeekReasoner" to DeepSeekReasoner,
  )

}