package com.simiacryptus.jopenai.models

object PerplexityModels {

    val SonarSmallChat128k = run {
        val maxTotalTokens = 128 * 1024
        ChatModel(
            name = "SonarSmallChat128k",
            modelName = "llama-3.1-sonar-small-128k-chat",
            maxTotalTokens = maxTotalTokens,
            maxOutTokens = maxTotalTokens,
            provider = APIProvider.Perplexity,
            inputTokenPricePerK = 0.0005,
            outputTokenPricePerK = 0.0015
        )
    }

    val SonarSmallOnline128k = run {
        val maxTotalTokens = 128 * 1024
        ChatModel(
            name = "SonarSmallOnline128k",
            modelName = "llama-3.1-sonar-small-128k-online",
            maxTotalTokens = maxTotalTokens,
            maxOutTokens = maxTotalTokens,
            provider = APIProvider.Perplexity,
            inputTokenPricePerK = 0.0005,
            outputTokenPricePerK = 0.0015
        )
    }

    val SonarLargeChat128k = run {
        val maxTotalTokens = 128 * 1024
        ChatModel(
            name = "SonarLargeChat128k",
            modelName = "llama-3.1-sonar-large-128k-chat",
            maxTotalTokens = maxTotalTokens,
            maxOutTokens = maxTotalTokens,
            provider = APIProvider.Perplexity,
            inputTokenPricePerK = 0.0005,
            outputTokenPricePerK = 0.0015
        )
    }

    val SonarLargeOnline128k = run {
        val maxTotalTokens = 128 * 1024
        ChatModel(
            name = "SonarLargeOnline128k",
            modelName = "llama-3.1-sonar-large-128k-online",
            maxTotalTokens = maxTotalTokens,
            maxOutTokens = maxTotalTokens,
            provider = APIProvider.Perplexity,
            inputTokenPricePerK = 0.0005,
            outputTokenPricePerK = 0.0015
        )
    }
    val values = mapOf(
        "SonarSmallChat128k" to SonarSmallChat128k,
        "SonarSmallOnline128k" to SonarSmallOnline128k,
        "SonarLargeChat128k" to SonarLargeChat128k,
        "SonarLargeOnline128k" to SonarLargeOnline128k,
    )

}