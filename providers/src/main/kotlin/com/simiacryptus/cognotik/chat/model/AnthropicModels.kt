package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.models.ModelSchema.TokenTypes

object AnthropicModels {
    @JvmStatic
    val ClaudeHaiku3 = ChatModel(
        name = "ClaudeHaiku3",
        modelId = "claude-3-haiku-20240307",
        maxTotalTokens = 200000,
        maxOutTokens = 4096,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 0.25 / 1000.0,
            TokenTypes.Completion to 1.25 / 1000.0,
        ),
        deprecated = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @JvmStatic
    val Claude35Haiku = ChatModel(
        name = "Claude35Haiku",
        modelId = "claude-3-5-haiku-latest",
        maxTotalTokens = 200000,
        maxOutTokens = 8192,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 0.80 / 1000.0,
            TokenTypes.Completion to 4.0 / 1000.0,
            TokenTypes.CacheWrite5m to 1.0 / 1000.0,
            TokenTypes.CacheWrite1h to 1.60 / 1000.0,
            TokenTypes.Cached to 0.08 / 1000.0,
        ),
        deprecated = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @JvmStatic
    val Claude45Haiku = ChatModel(
        name = "Claude Haiku 4.5",
        modelId = "claude-haiku-4-5-20251001",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 1.0 / 1000.0,
            TokenTypes.Completion to 5.0 / 1000.0,
            TokenTypes.CacheWrite5m to 1.25 / 1000.0,
            TokenTypes.CacheWrite1h to 2.0 / 1000.0,
            TokenTypes.Cached to 0.10 / 1000.0,
        ),
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @Deprecated("Model deprecated by Anthropic", replaceWith = ReplaceWith("Claude4Sonnet"))

    @JvmStatic
    val Claude37Sonnet = ChatModel(
        name = "Claude37Sonnet",
        modelId = "claude-3-7-sonnet-20250219",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 3.0 / 1000.0,
            TokenTypes.Completion to 15.0 / 1000.0,
            TokenTypes.CacheWrite5m to 3.75 / 1000.0,
            TokenTypes.CacheWrite1h to 6.0 / 1000.0,
            TokenTypes.Cached to 0.30 / 1000.0,
        ),
        deprecated = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @JvmStatic
    val Claude4Sonnet = ChatModel(
        name = "Claude4Sonnet",
        modelId = "claude-sonnet-4-20250514",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 3.0 / 1000.0,
            TokenTypes.Completion to 15.0 / 1000.0,
            TokenTypes.CacheWrite5m to 3.75 / 1000.0,
            TokenTypes.CacheWrite1h to 6.0 / 1000.0,
            TokenTypes.Cached to 0.30 / 1000.0,
        ),
        deprecated = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )


    @JvmStatic
    val Claude45Sonnet = ChatModel(
        name = "Claude45Sonnet",
        modelId = "claude-sonnet-4-5-20250620",
        maxTotalTokens = 200000,
        maxOutTokens = 64000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 3.0 / 1000.0,
            TokenTypes.Completion to 15.0 / 1000.0,
            TokenTypes.CacheWrite5m to 3.75 / 1000.0,
            TokenTypes.CacheWrite1h to 6.0 / 1000.0,
            TokenTypes.Cached to 0.30 / 1000.0,
        ),
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @JvmStatic
    val Claude46Sonnet = ChatModel(
        name = "Claude Sonnet 4.6",
        modelId = "claude-sonnet-4-6",
        maxTotalTokens = 1000000,
        maxOutTokens = 64000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 3.0 / 1000.0,
            TokenTypes.Completion to 15.0 / 1000.0,
            TokenTypes.CacheWrite5m to 3.75 / 1000.0,
            TokenTypes.CacheWrite1h to 6.0 / 1000.0,
            TokenTypes.Cached to 0.30 / 1000.0,
        ),
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @JvmStatic
    val Claude4Opus = ChatModel(
        name = "Claude4Opus",
        modelId = "claude-opus-4-20250514",
        maxTotalTokens = 200000,
        maxOutTokens = 32000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 15.0 / 1000.0,
            TokenTypes.Completion to 75.0 / 1000.0,
            TokenTypes.CacheWrite5m to 18.75 / 1000.0,
            TokenTypes.CacheWrite1h to 30.0 / 1000.0,
            TokenTypes.Cached to 1.50 / 1000.0,
        ),
        deprecated = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @JvmStatic
    val Claude41Opus = ChatModel(
        name = "Claude41Opus",
        modelId = "claude-opus-4-1-20250618",
        maxTotalTokens = 200000,
        maxOutTokens = 32000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 15.0 / 1000.0,
            TokenTypes.Completion to 75.0 / 1000.0,
            TokenTypes.CacheWrite5m to 18.75 / 1000.0,
            TokenTypes.CacheWrite1h to 30.0 / 1000.0,
            TokenTypes.Cached to 1.50 / 1000.0,
        ),
        deprecated = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @JvmStatic
    val Claude45Opus = ChatModel(
        name = "Claude Opus 4.5",
        modelId = "claude-opus-4-5",
        maxTotalTokens = 200000,
        maxOutTokens = 128000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 5.0 / 1000.0,
            TokenTypes.Completion to 25.0 / 1000.0,
            TokenTypes.CacheWrite5m to 6.25 / 1000.0,
            TokenTypes.CacheWrite1h to 10.0 / 1000.0,
            TokenTypes.Cached to 0.50 / 1000.0,
        ),
        deprecated = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT),
    )

    @JvmStatic
    val Claude46Opus = ChatModel(
        name = "Claude Opus 4.6",
        modelId = "claude-opus-4-6",
        maxTotalTokens = 1000000,
        maxOutTokens = 128000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 5.0 / 1000.0,
            TokenTypes.Completion to 25.0 / 1000.0,
            TokenTypes.CacheWrite5m to 6.25 / 1000.0,
            TokenTypes.CacheWrite1h to 10.0 / 1000.0,
            TokenTypes.Cached to 0.50 / 1000.0,
            TokenTypes.Thinking to 25.0 / 1000.0,
        ),
        supportsTemperature = false,
        supportsReasoning = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT)
    )

    @JvmStatic
    val Claude47Opus = ChatModel(
        name = "Claude Opus 4.7",
        modelId = "claude-opus-4-7",
        maxTotalTokens = 1000000,
        maxOutTokens = 128000,
        provider = CoreProviders.Anthropic,
        tokenPricingPerK = mapOf(
            TokenTypes.Prompt to 5.0 / 1000.0,
            TokenTypes.Completion to 25.0 / 1000.0,
            TokenTypes.CacheWrite5m to 6.25 / 1000.0,
            TokenTypes.CacheWrite1h to 10.0 / 1000.0,
            TokenTypes.Cached to 0.50 / 1000.0,
            TokenTypes.Thinking to 25.0 / 1000.0,
        ),
        supportsTemperature = false,
        supportsReasoning = true,
        inputModalities = setOf(ChatMessageModality.TEXT),
        outputModalities = setOf(ChatMessageModality.TEXT)
    )
     @JvmStatic
     val Claude48Opus = ChatModel(
         name = "Claude Opus 4.8",
         modelId = "claude-opus-4-8",
         maxTotalTokens = 1000000,
         maxOutTokens = 128000,
         provider = CoreProviders.Anthropic,
         tokenPricingPerK = mapOf(
             TokenTypes.Prompt to 5.0 / 1000.0,
             TokenTypes.Completion to 25.0 / 1000.0,
             TokenTypes.CacheWrite5m to 6.25 / 1000.0,
             TokenTypes.CacheWrite1h to 10.0 / 1000.0,
             TokenTypes.Cached to 0.50 / 1000.0,
         ),
         inputModalities = setOf(ChatMessageModality.TEXT),
         outputModalities = setOf(ChatMessageModality.TEXT),
     )
     @JvmStatic
     val ClaudeFable5 = ChatModel(
         name = "Claude Fable 5",
         modelId = "claude-fable-5",
         maxTotalTokens = 1000000,
         maxOutTokens = 128000,
         provider = CoreProviders.Anthropic,
         tokenPricingPerK = mapOf(
             TokenTypes.Prompt to 10.0 / 1000.0,
             TokenTypes.Completion to 50.0 / 1000.0,
             TokenTypes.CacheWrite5m to 12.50 / 1000.0,
             TokenTypes.CacheWrite1h to 20.0 / 1000.0,
             TokenTypes.Cached to 1.0 / 1000.0,
         ),
         inputModalities = setOf(ChatMessageModality.TEXT),
         outputModalities = setOf(ChatMessageModality.TEXT),
     )
     @JvmStatic
     val ClaudeMythos5 = ChatModel(
         name = "Claude Mythos 5",
         modelId = "claude-mythos-5",
         maxTotalTokens = 1000000,
         maxOutTokens = 128000,
         provider = CoreProviders.Anthropic,
         tokenPricingPerK = mapOf(
             TokenTypes.Prompt to 10.0 / 1000.0,
             TokenTypes.Completion to 50.0 / 1000.0,
             TokenTypes.CacheWrite5m to 12.50 / 1000.0,
             TokenTypes.CacheWrite1h to 20.0 / 1000.0,
             TokenTypes.Cached to 1.0 / 1000.0,
         ),
         inputModalities = setOf(ChatMessageModality.TEXT),
         outputModalities = setOf(ChatMessageModality.TEXT),
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
        "Claude47Opus" to Claude47Opus,
         "Claude48Opus" to Claude48Opus,
         "ClaudeFable5" to ClaudeFable5,
         "ClaudeMythos5" to ClaudeMythos5,
    )

}