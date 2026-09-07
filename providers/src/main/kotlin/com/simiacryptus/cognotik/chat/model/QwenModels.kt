package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ChatMessageModality
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.ModelSchema.TokenTypes

/**
 * Model catalog for Alibaba Cloud Qwen (DashScope "compatible-mode" OpenAI API).
 * International base: https://dashscope-intl.aliyuncs.com/compatible-mode/v1
 * Mainland base:      https://dashscope.aliyuncs.com/compatible-mode/v1
 * Prices are USD per 1K tokens (published per-million prices / 1000).
 */
object QwenModels {


  @JvmStatic
  val QwenMax = ChatModel(
    name = "QwenMax",
    modelId = "qwen3.8-max",
    maxTotalTokens = 32768,
    maxOutTokens = 8192,
    provider = CoreProviders.Qwen,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 1.60 / 1000.0,
      TokenTypes.Completion to 6.40 / 1000.0,
      TokenTypes.Cached to 0.32 / 1000.0,
    ),
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val QwenPlus = ChatModel(
    name = "QwenPlus",
    modelId = "qwen3.7-plus",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = CoreProviders.Qwen,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.40 / 1000.0,
      TokenTypes.Completion to 1.20 / 1000.0,
      TokenTypes.Thinking to 1.20 / 1000.0,
      TokenTypes.Cached to 0.08 / 1000.0,
    ),
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val QwenFlash = ChatModel(
    name = "QwenFlash",
    modelId = "qwen3.7-flash",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = CoreProviders.Qwen,
    tokenPricingPerK = mapOf(
      TokenTypes.Prompt to 0.40 / 1000.0,
      TokenTypes.Completion to 1.20 / 1000.0,
      TokenTypes.Thinking to 1.20 / 1000.0,
      TokenTypes.Cached to 0.08 / 1000.0,
    ),
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val values = mapOf(
    "QwenMax" to QwenMax,
    "QwenPlus" to QwenPlus,
    "QwenFlash" to QwenFlash,
  )
}