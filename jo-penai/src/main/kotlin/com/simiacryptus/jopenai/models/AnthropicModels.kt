package com.simiacryptus.jopenai.models

object AnthropicModels {
    
    val Claude35Sonnet = ChatModel(
        name = "Claude35Sonnet",
        modelName = "claude-3-5-sonnet-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 3.75 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )
    val Claude37Sonnet = ChatModel(
        name = "Claude37Sonnet",
        modelName = "claude-3-7-sonnet-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 3.75 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0,
    )
    val Claude35Haiku = ChatModel(
        name = "Claude3Haiku",
        modelName = "claude-3-5-haiku-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 1.0 / 1000.0,
        outputTokenPricePerK = 4.0 / 1000.0,
    )
    val Claude3Opus = ChatModel(
        name = "Claude3Opus",
        modelName = "claude-3-opus-20240229",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 18.75 / 1000.0,
        outputTokenPricePerK = 75.0 / 1000.0,
    )
    val Claude3Sonnet = ChatModel(
        name = "Claude3Sonnet",
        modelName = "claude-3-sonnet-20240229",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 3.0 / 1000.0,
        outputTokenPricePerK = 15.0 / 1000.0
    )
    val Claude3Haiku = ChatModel(
        name = "Claude3Haiku",
        modelName = "claude-3-haiku-20240307",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = APIProvider.Anthropic,
        inputTokenPricePerK = 0.25 / 1000.0,
        outputTokenPricePerK = 1.25 / 1000.0
    )
    val values = mapOf(
        "Claude3Opus" to Claude3Opus,
        "Claude35Sonnet" to Claude35Sonnet,
        "Claude37Sonnet" to Claude37Sonnet,
        "Claude35Haiku" to Claude35Haiku,
        "Claude3Sonnet" to Claude3Sonnet,
        "Claude3Haiku" to Claude3Haiku,
    )

}