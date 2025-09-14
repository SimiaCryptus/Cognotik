package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object AnthropicModels {
    val Claude41Opus = ChatModel(
        name = "Claude41Opus",
        modelName = "claude-opus-4-1-20250805",
        maxTotalTokens = 200000,
        maxOutTokens = 32000,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 30.0 / 1000.0,
        outputTokenPricePerK = 120.0 / 1000.0,
    )
    val Claude4Sonnet = ChatModel(
        name = "Claude4Sonnet",
        modelName = "claude-sonnet-4-20250514",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 6.0 / 1000.0,
        outputTokenPricePerK = 24.0 / 1000.0,
    )

    val Claude35Haiku = ChatModel(
        name = "Claude35Haiku",
        modelName = "claude-3-5-haiku-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 1.0 / 1000.0,
        outputTokenPricePerK = 4.0 / 1000.0,
    )
    val values = mapOf(
        "Claude41Opus" to Claude41Opus,
        "Claude4Sonnet" to Claude4Sonnet,
        "Claude35Haiku" to Claude35Haiku,
    )

}