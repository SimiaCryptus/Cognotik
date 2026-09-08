package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ChatMessageModality
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes

/**
 * Models exposed by z.ai (Zhipu AI) via its OpenAI-compatible endpoint:
 * https://api.z.ai/api/paas/v4/chat/completions
 *
 * Pricing below is approximate (per z.ai's published pricing page as of this
 * writing, see https://docs.z.ai/guides/llm/pricing) and converted from
 * per-1M-token rates to per-1K-token rates used by ChatModel. Verify against
 * the pricing page before relying on these figures for billing, as prices
 * (and promotions) are subject to change.
 */
@Suppress("unused")
object ZAIModels {

  // glm-4.6: 200K context, thinking-capable flagship model.
  private val glm46Pricing = mapOf(
    TokenTypes.Prompt to 0.60 / 1000.0,
    TokenTypes.Cached to 0.11 / 1000.0,
    TokenTypes.Completion to 2.20 / 1000.0,
    TokenTypes.Thinking to 2.20 / 1000.0,
  )

  // glm-4.5: 128K context, thinking-capable.
  private val glm45Pricing = mapOf(
    TokenTypes.Prompt to 0.60 / 1000.0,
    TokenTypes.Cached to 0.11 / 1000.0,
    TokenTypes.Completion to 2.20 / 1000.0,
    TokenTypes.Thinking to 2.20 / 1000.0,
  )

  // glm-4.5-air: smaller/cheaper variant of glm-4.5.
  private val glm45AirPricing = mapOf(
    TokenTypes.Prompt to 0.20 / 1000.0,
    TokenTypes.Cached to 0.03 / 1000.0,
    TokenTypes.Completion to 1.10 / 1000.0,
    TokenTypes.Thinking to 1.10 / 1000.0,
  )

  // glm-4.5-flash: low-cost/free-tier model.
  private val glm45FlashPricing = mapOf(
    TokenTypes.Prompt to 0.0,
    TokenTypes.Cached to 0.0,
    TokenTypes.Completion to 0.0,
    TokenTypes.Thinking to 0.0,
  )

  // glm-5.3-flash: latest flash model, currently at a 50% promotional
  // discount per z.ai's pricing page (promotion ends 2026-09-09 24:00 UTC+8).
  // Prices below reflect the discounted (current) rate: $0.075 / $0.015 / $0.25
  // per 1M tokens (list price: $0.15 / $0.03 / $0.50).
  private val glm53FlashPricing = mapOf(
    TokenTypes.Prompt to 0.075 / 1000.0,
    TokenTypes.Cached to 0.015 / 1000.0,
    TokenTypes.Completion to 0.25 / 1000.0,
    TokenTypes.Thinking to 0.25 / 1000.0,
  )

  // glm-5.3: latest flagship model.
  private val glm53Pricing = mapOf(
    TokenTypes.Prompt to 1.4 / 1000.0,
    TokenTypes.Cached to 0.26 / 1000.0,
    TokenTypes.Completion to 4.4 / 1000.0,
    TokenTypes.Thinking to 4.4 / 1000.0,
  )

  // glm-5.2: same pricing tier as glm-5.3.
  private val glm52Pricing = mapOf(
    TokenTypes.Prompt to 1.4 / 1000.0,
    TokenTypes.Cached to 0.26 / 1000.0,
    TokenTypes.Completion to 4.4 / 1000.0,
    TokenTypes.Thinking to 4.4 / 1000.0,
  )

  // glm-5.1: text model, same rate as 5.2/5.3.
  private val glm51Pricing = mapOf(
    TokenTypes.Prompt to 1.4 / 1000.0,
    TokenTypes.Cached to 0.26 / 1000.0,
    TokenTypes.Completion to 4.4 / 1000.0,
    TokenTypes.Thinking to 4.4 / 1000.0,
  )

  // glm-5: text model.
  private val glm5Pricing = mapOf(
    TokenTypes.Prompt to 1.0 / 1000.0,
    TokenTypes.Cached to 0.2 / 1000.0,
    TokenTypes.Completion to 3.2 / 1000.0,
    TokenTypes.Thinking to 3.2 / 1000.0,
  )

  // glm-4.7: text model, same rate as glm-4.6/glm-4.5.
  private val glm47Pricing = mapOf(
    TokenTypes.Prompt to 0.60 / 1000.0,
    TokenTypes.Cached to 0.11 / 1000.0,
    TokenTypes.Completion to 2.20 / 1000.0,
    TokenTypes.Thinking to 2.20 / 1000.0,
  )

  // glm-4.7-flashx: low-cost variant of glm-4.7.
  private val glm47FlashXPricing = mapOf(
    TokenTypes.Prompt to 0.07 / 1000.0,
    TokenTypes.Cached to 0.01 / 1000.0,
    TokenTypes.Completion to 0.40 / 1000.0,
    TokenTypes.Thinking to 0.40 / 1000.0,
  )

  // glm-4.7-flash: free-tier model.
  private val glm47FlashPricing = mapOf(
    TokenTypes.Prompt to 0.0,
    TokenTypes.Cached to 0.0,
    TokenTypes.Completion to 0.0,
    TokenTypes.Thinking to 0.0,
  )

  // glm-4.5-x: higher-throughput variant of glm-4.5.
  private val glm45XPricing = mapOf(
    TokenTypes.Prompt to 2.20 / 1000.0,
    TokenTypes.Cached to 0.45 / 1000.0,
    TokenTypes.Completion to 8.90 / 1000.0,
    TokenTypes.Thinking to 8.90 / 1000.0,
  )

  // glm-4.5-airx: higher-throughput variant of glm-4.5-air.
  private val glm45AirXPricing = mapOf(
    TokenTypes.Prompt to 1.10 / 1000.0,
    TokenTypes.Cached to 0.22 / 1000.0,
    TokenTypes.Completion to 4.50 / 1000.0,
    TokenTypes.Thinking to 4.50 / 1000.0,
  )

  // glm-4-32b-0414-128k: smaller model, no distinct cached-input rate published.
  private val glm432B0414Pricing = mapOf(
    TokenTypes.Prompt to 0.10 / 1000.0,
    TokenTypes.Cached to 0.10 / 1000.0,
    TokenTypes.Completion to 0.10 / 1000.0,
    TokenTypes.Thinking to 0.10 / 1000.0,
  )


  val GLM46 = ChatModel(
    name = "GLM46",
    modelId = "glm-4.6",
    maxTotalTokens = 200_000,
    maxOutTokens = 128_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm46Pricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM53Flash = ChatModel(
    name = "GLM53Flash",
    modelId = "glm-5.3-flash",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm53FlashPricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM53 = ChatModel(
    name = "GLM53",
    modelId = "glm-5.3",
    maxTotalTokens = 200_000,
    maxOutTokens = 128_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm53Pricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM52 = ChatModel(
    name = "GLM52",
    modelId = "glm-5.2",
    maxTotalTokens = 200_000,
    maxOutTokens = 128_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm52Pricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM51 = ChatModel(
    name = "GLM51",
    modelId = "glm-5.1",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm51Pricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM5 = ChatModel(
    name = "GLM5",
    modelId = "glm-5",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm5Pricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM47 = ChatModel(
    name = "GLM47",
    modelId = "glm-4.7",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm47Pricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM47FlashX = ChatModel(
    name = "GLM47FlashX",
    modelId = "glm-4.7-flashx",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm47FlashXPricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM47Flash = ChatModel(
    name = "GLM47Flash",
    modelId = "glm-4.7-flash",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm47FlashPricing,
    supportsReasoning = false,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM45X = ChatModel(
    name = "GLM45X",
    modelId = "glm-4.5-x",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm45XPricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM45AirX = ChatModel(
    name = "GLM45AirX",
    modelId = "glm-4.5-airx",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm45AirXPricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val GLM432B0414 = ChatModel(
    name = "GLM432B0414",
    modelId = "glm-4-32b-0414-128k",
    maxTotalTokens = 128_000,
    maxOutTokens = 32_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm432B0414Pricing,
    supportsReasoning = false,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )


  val GLM45 = ChatModel(
    name = "GLM45",
    modelId = "glm-4.5",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm45Pricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  val GLM45Air = ChatModel(
    name = "GLM45Air",
    modelId = "glm-4.5-air",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm45AirPricing,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  val GLM45Flash = ChatModel(
    name = "GLM45Flash",
    modelId = "glm-4.5-flash",
    maxTotalTokens = 128_000,
    maxOutTokens = 96_000,
    provider = CoreProviders.ZAI,
    tokenPricingPerK = glm45FlashPricing,
    supportsReasoning = false,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT)
  )

  val values = mapOf(
    "GLM46" to GLM46,
    "GLM53Flash" to GLM53Flash,
    "GLM53" to GLM53,
    "GLM52" to GLM52,
    "GLM51" to GLM51,
    "GLM5" to GLM5,
    "GLM47" to GLM47,
    "GLM47FlashX" to GLM47FlashX,
    "GLM47Flash" to GLM47Flash,
    "GLM45X" to GLM45X,
    "GLM45AirX" to GLM45AirX,
    "GLM432B0414" to GLM432B0414,
    "GLM45" to GLM45,
    "GLM45Air" to GLM45Air,
    "GLM45Flash" to GLM45Flash,
  )
}