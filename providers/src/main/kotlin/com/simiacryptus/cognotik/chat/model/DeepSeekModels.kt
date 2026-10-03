package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ChatMessageModality
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes

@Suppress("unused")
object DeepSeekModels {

  // Pricing is quoted per 1M tokens in the DeepSeek docs; convert to per-1k.
// Prices below reflect peak rates (off-peak rates are half of peak rates).
// deepseek-flash:
//   cache hit (Cached) input: $0.006 / 1M
//   cache miss (Prompt) input: $0.30  / 1M
//   output (Completion):       $1.20  / 1M
  private val flashPricing = mapOf(
   TokenTypes.Prompt to 0.3 / 1000.0,
   TokenTypes.Cached to 0.006 / 1000.0,
   TokenTypes.Completion to 1.2 / 1000.0,
   TokenTypes.Thinking to 1.2 / 1000.0,
  )

  // deepseek-v4-pro:
//   cache hit (Cached) input: $0.044 / 1M
//   cache miss (Prompt) input: $1.32  / 1M
//   output (Completion):       $3.96  / 1M
  private val proPricing = mapOf(
   TokenTypes.Prompt to 1.32 / 1000.0,
   TokenTypes.Cached to 0.044 / 1000.0,
   TokenTypes.Completion to 3.96 / 1000.0,
   TokenTypes.Thinking to 3.96 / 1000.0,
  )

  // deepseek-flash: DeepSeek-V4.1-Flash, 1M context, 384K max output,
  // supports both thinking and non-thinking modes (thinking is default).
  // Concurrency limit: 2500. Supports JSON output, tool calls, responses API,
  // Anthropic API, chat prefix completion (beta), FIM completion (beta, non-thinking only),
  // and Vision.
  val DeepSeekFlash by lazy {
    ChatModel(
      name = "DeepSeekFlash",
      modelId = "deepseek-flash",
      maxTotalTokens = 1_000_000,
      maxOutTokens = 384_000,
      provider = CoreProviders.DeepSeek,
      tokenPricingPerK = flashPricing,
      supportsReasoning = true,
      inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )
  }

  // Retired legacy name for deepseek-flash; served by DeepSeek-V4.1-Flash.
  val DeepSeekV4Flash by lazy {
    ChatModel(
      name = "DeepSeekV4Flash",
      modelId = "deepseek-v4-flash",
      maxTotalTokens = 1_000_000,
      maxOutTokens = 384_000,
      provider = CoreProviders.DeepSeek,
      tokenPricingPerK = flashPricing,
      supportsReasoning = true,
      deprecated = true,
      inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )
  }

  // supports both thinking and non-thinking modes (thinking is default).
  // Concurrency limit: 500. Supports JSON output, tool calls, responses API,
  // Anthropic API, and chat prefix completion (beta).

  val DeepSeekV4Pro by lazy {
    ChatModel(
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
  }

  // Deprecated aliases (removal scheduled 2026/07/24). deepseek-chat maps to the
  // non-thinking mode and deepseek-reasoner to the thinking mode of deepseek-v4-flash.
  val DeepSeekChat by lazy {
    ChatModel(
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
  }

  val DeepSeekReasoner by lazy {
    ChatModel(
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
  }

  val values by lazy {
    mapOf(
      "DeepSeekFlash" to DeepSeekFlash,
      "DeepSeekV4Flash" to DeepSeekV4Flash,
      "DeepSeekV4Pro" to DeepSeekV4Pro,
      "DeepSeekChat" to DeepSeekChat,
      "DeepSeekReasoner" to DeepSeekReasoner,
    )
  }

}