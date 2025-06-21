package com.simiacryptus.jopenai.models.chat

import com.simiacryptus.jopenai.models.APIProvider

object AnthropicModels {
    val Claude4Opus = ChatModelType(
        name = "Claude4Opus",
        modelName = "claude-opus-4-20250514",
        maxTotalTokens = 200000,
        maxOutTokens = 32000,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 30.0 / 1000.0,
        outputTokenPricePerK = 120.0 / 1000.0,
    )
    val Claude4Sonnet = ChatModelType(
        name = "Claude4Sonnet",
        modelName = "claude-sonnet-4-20250514",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 6.0 / 1000.0,
        outputTokenPricePerK = 24.0 / 1000.0,
    )


    val Claude35Sonnet = ChatModelType(
        name = "Claude35Sonnet",
        modelName = "claude-3-5-sonnet-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 3.75 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )
    val Claude37Sonnet = ChatModelType(
        name = "Claude37Sonnet",
        modelName = "claude-3-7-sonnet-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 3.75 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )
    val Claude35Haiku = ChatModelType(
        name = "Claude35Haiku",
        modelName = "claude-3-5-haiku-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 1.0 / 1000.0,
        outputTokenPricePerK = 4.0 / 1000.0,
    )
    val Claude3Opus = ChatModelType(
        name = "Claude3Opus",
        modelName = "claude-3-opus-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 18.75 / 1000.0,
        outputTokenPricePerK = 75.0 / 1000.0,
    )
    val Claude3Sonnet = ChatModelType(
        name = "Claude3Sonnet",
        modelName = "claude-3-sonnet-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 3.0 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0
    )
    val Claude3Haiku = ChatModelType(
        name = "Claude3Haiku",
        modelName = "claude-3-haiku-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.Anthropic,
        inputTokenPricePerK = 0.25 / 1000.0,
        outputTokenPricePerK = 1.25 / 1000.0
    )
    val values = mapOf(
        "Claude4Opus" to Claude4Opus,
        "Claude4Sonnet" to Claude4Sonnet,
        "Claude3Opus" to Claude3Opus,
        "Claude35Sonnet" to Claude35Sonnet,
        "Claude37Sonnet" to Claude37Sonnet,
        "Claude35Haiku" to Claude35Haiku,
        "Claude3Sonnet" to Claude3Sonnet,
        "Claude3Haiku" to Claude3Haiku,
    )

}