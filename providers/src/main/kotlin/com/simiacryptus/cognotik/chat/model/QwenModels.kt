package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders

/**
 * Model catalog for Alibaba Cloud Qwen (DashScope "compatible-mode" OpenAI API).
 * International base: https://dashscope-intl.aliyuncs.com/compatible-mode/v1
 * Mainland base:      https://dashscope.aliyuncs.com/compatible-mode/v1
 * Prices are USD per 1K tokens (published per-million prices / 1000).
 */
object QwenModels {

  @JvmStatic
  val Qwen3Max = ChatModel(
    name = "Qwen3Max",
    modelId = "qwen3-max",
    maxTotalTokens = 262144,
    maxOutTokens = 65536,
    provider = CoreProviders.Qwen,
    inputTokenPricePerK = 1.20 / 1000.0,
    outputTokenPricePerK = 6.00 / 1000.0,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val QwenMax = ChatModel(
    name = "QwenMax",
    modelId = "qwen-max",
    maxTotalTokens = 32768,
    maxOutTokens = 8192,
    provider = CoreProviders.Qwen,
    inputTokenPricePerK = 1.60 / 1000.0,
    outputTokenPricePerK = 6.40 / 1000.0,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val QwenPlus = ChatModel(
    name = "QwenPlus",
    modelId = "qwen-plus",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = CoreProviders.Qwen,
    inputTokenPricePerK = 0.40 / 1000.0,
    outputTokenPricePerK = 1.20 / 1000.0,
    supportsReasoning = true,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val QwenTurbo = ChatModel(
    name = "QwenTurbo",
    modelId = "qwen-turbo",
    maxTotalTokens = 1000000,
    maxOutTokens = 8192,
    provider = CoreProviders.Qwen,
    inputTokenPricePerK = 0.05 / 1000.0,
    outputTokenPricePerK = 0.20 / 1000.0,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Qwen3CoderPlus = ChatModel(
    name = "Qwen3CoderPlus",
    modelId = "qwen3-coder-plus",
    maxTotalTokens = 1000000,
    maxOutTokens = 65536,
    provider = CoreProviders.Qwen,
    inputTokenPricePerK = 1.00 / 1000.0,
    outputTokenPricePerK = 5.00 / 1000.0,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val Qwen3CoderFlash = ChatModel(
    name = "Qwen3CoderFlash",
    modelId = "qwen3-coder-flash",
    maxTotalTokens = 1000000,
    maxOutTokens = 65536,
    provider = CoreProviders.Qwen,
    inputTokenPricePerK = 0.30 / 1000.0,
    outputTokenPricePerK = 1.50 / 1000.0,
    inputModalities = setOf(ChatMessageModality.TEXT),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val QwenVlMax = ChatModel(
    name = "QwenVlMax",
    modelId = "qwen-vl-max",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = CoreProviders.Qwen,
    inputTokenPricePerK = 0.80 / 1000.0,
    outputTokenPricePerK = 3.20 / 1000.0,
    inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
    outputModalities = setOf(ChatMessageModality.TEXT),
  )

  @JvmStatic
  val values = mapOf(
    "Qwen3Max" to Qwen3Max,
    "QwenMax" to QwenMax,
    "QwenPlus" to QwenPlus,
    "QwenTurbo" to QwenTurbo,
    "Qwen3CoderPlus" to Qwen3CoderPlus,
    "Qwen3CoderFlash" to Qwen3CoderFlash,
    "QwenVlMax" to QwenVlMax,
  )
}