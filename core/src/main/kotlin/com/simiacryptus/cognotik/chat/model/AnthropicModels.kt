package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object AnthropicModels {
    val Claude35Sonnet = ChatModel(
        name = "Claude35Sonnet",
        modelName = "claude-3-5-sonnet-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 8192,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 3.0 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )
    val Claude35Haiku = ChatModel(
        name = "Claude35Haiku",
        modelName = "claude-3-5-haiku-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 8192,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 0.80 / 1000.0,
        outputTokenPricePerK = 4.0 / 1000.0,
    )
    val Claude37Sonnet = ChatModel(
        name = "Claude37Sonnet",
        modelName = "claude-3-7-sonnet-20250219",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 3.0 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )
    val Claude45Sonnet = ChatModel(
        name = "Claude45Sonnet",
        modelName = "claude-sonnet-4-5",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 3.0 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )
    val Claude45Haiku = ChatModel(
        name = "Claude45Haiku",
        modelName = "claude-haiku-4-5",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 1.0 / 1000.0,
        outputTokenPricePerK = 5.0 / 1000.0,
    )
    val Claude46Opus = ChatModel(
        name = "Claude46Opus",
        modelName = "claude-opus-4-6",
        maxTotalTokens = 200000,
        maxOutTokens = 128000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 5.0 / 1000.0,
        outputTokenPricePerK = 25.0 / 1000.0,
    )


    val values = mapOf(
        "Claude35Sonnet" to Claude35Sonnet,
        "Claude35Haiku" to Claude35Haiku,
        "Claude37Sonnet" to Claude37Sonnet,
        "Claude45Sonnet" to Claude45Sonnet,
        "Claude45Haiku" to Claude45Haiku,
        "Claude46Opus" to Claude46Opus,
    )

}