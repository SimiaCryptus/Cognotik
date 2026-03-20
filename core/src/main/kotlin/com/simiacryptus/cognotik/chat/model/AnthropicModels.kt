package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object AnthropicModels {
  @JvmStatic
  val ClaudeHaiku3 = ChatModel(
    name = "ClaudeHaiku3",
    modelId = "claude-3-haiku-20240307",
    maxTotalTokens = 200000,
    maxOutTokens = 4096,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 0.25 / 1000.0,
    outputTokenPricePerK = 1.25 / 1000.0,
  )

  @JvmStatic
  val Claude35Haiku = ChatModel(
    name = "Claude35Haiku",
    modelId = "claude-3-5-haiku-latest",
    maxTotalTokens = 200000,
    maxOutTokens = 8192,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 0.80 / 1000.0,
    outputTokenPricePerK = 4.0 / 1000.0,
  )

  @JvmStatic
  val Claude45Haiku = ChatModel(
    name = "Claude45Haiku",
    modelId = "claude-haiku-4-5-20251001",
    maxTotalTokens = 200000,
    maxOutTokens = 64000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 1.0 / 1000.0,
    outputTokenPricePerK = 5.0 / 1000.0,
  )

  @Deprecated("Model deprecated by Anthropic", replaceWith = ReplaceWith("Claude4Sonnet"))

  @JvmStatic
  val Claude37Sonnet = ChatModel(
    name = "Claude37Sonnet",
    modelId = "claude-3-7-sonnet-20250219",
    maxTotalTokens = 200000,
    maxOutTokens = 64000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 3.0 / 1000.0,
    outputTokenPricePerK = 15.0 / 1000.0,
  )

  @JvmStatic
  val Claude4Sonnet = ChatModel(
    name = "Claude4Sonnet",
    modelId = "claude-sonnet-4-20250514",
    maxTotalTokens = 200000,
    maxOutTokens = 64000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 3.0 / 1000.0,
    outputTokenPricePerK = 15.0 / 1000.0,
  )


  @JvmStatic
  val Claude45Sonnet = ChatModel(
    name = "Claude45Sonnet",
    modelId = "claude-sonnet-4-5-20250620",
    maxTotalTokens = 200000,
    maxOutTokens = 64000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 3.0 / 1000.0,
    outputTokenPricePerK = 15.0 / 1000.0,
  )

  @JvmStatic
  val Claude46Sonnet = ChatModel(
    name = "Claude46Sonnet",
    modelId = "claude-sonnet-4-6",
    maxTotalTokens = 1000000,
    maxOutTokens = 64000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 3.0 / 1000.0,
    outputTokenPricePerK = 15.0 / 1000.0,
  )

  @JvmStatic
  val Claude4Opus = ChatModel(
    name = "Claude4Opus",
    modelId = "claude-opus-4-20250514",
    maxTotalTokens = 200000,
    maxOutTokens = 32000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 15.0 / 1000.0,
    outputTokenPricePerK = 75.0 / 1000.0,
  )

  @JvmStatic
  val Claude41Opus = ChatModel(
    name = "Claude41Opus",
    modelId = "claude-opus-4-1-20250618",
    maxTotalTokens = 200000,
    maxOutTokens = 32000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 15.0 / 1000.0,
    outputTokenPricePerK = 75.0 / 1000.0,
  )

  @JvmStatic
  val Claude45Opus = ChatModel(
    name = "Claude45Opus",
    modelId = "claude-opus-4-5",
    maxTotalTokens = 200000,
    maxOutTokens = 128000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 5.0 / 1000.0,
    outputTokenPricePerK = 25.0 / 1000.0,
  )

  @JvmStatic
  val Claude46Opus = ChatModel(
    name = "Claude46Opus",
    modelId = "claude-opus-4-6",
    maxTotalTokens = 1000000,
    maxOutTokens = 128000,
    provider = APIProvider.Anthropic,
    inputTokenPricePerK = 5.0 / 1000.0,
    outputTokenPricePerK = 25.0 / 1000.0,
  )

  @JvmStatic
  val values = mapOf(
    "ClaudeHaiku3" to ClaudeHaiku3,
    "Claude35Haiku" to Claude35Haiku,
    "Claude45Haiku" to Claude45Haiku,
    "Claude37Sonnet" to Claude37Sonnet,
    "Claude4Sonnet" to Claude4Sonnet,
    "Claude45Sonnet" to Claude45Sonnet,
    "Claude46Sonnet" to Claude46Sonnet,
    "Claude4Opus" to Claude4Opus,
    "Claude41Opus" to Claude41Opus,
    "Claude45Opus" to Claude45Opus,
    "Claude46Opus" to Claude46Opus,
  )

}