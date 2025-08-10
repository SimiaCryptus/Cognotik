package com.simiacryptus.jopenai.chat.model

import com.simiacryptus.jopenai.models.APIProvider

object MistralModels {

    val Mistral7B = ChatModelType(
        name = "Mistral7B",
        modelName = "open-mistral-7b",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )

    val Mixtral8x7B = ChatModelType(
        name = "Mixtral8x7B",
        modelName = "open-mixtral-8x7b",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Mixtral8x22B = ChatModelType(
        name = "Mixtral8x22B",
        modelName = "open-mixtral-8x22b",
        maxTotalTokens = 65536,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val MistralSmall = ChatModelType(
        name = "MistralSmall",
        modelName = "mistral-small-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val MistralMedium = ChatModelType(
        name = "MistralMedium",
        modelName = "mistral-medium-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val MistralLarge = ChatModelType(
        name = "MistralLarge",
        modelName = "mistral-large-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val MistralNemo = ChatModelType(
        name = "MistralNemo",
        modelName = "open-mistral-nemo",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val Codestral = ChatModelType(
        name = "Codestral",
        modelName = "codestral-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val CodestralMamba = ChatModelType(
        name = "CodestralMamba",
        modelName = "open-codestral-mamba",
        maxTotalTokens = 128 * 1024 - 1,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0005,

        outputTokenPricePerK = 0.0015

    )
    val values = mapOf(
        "Mistral7B" to Mistral7B,
        "Mixtral8x7B" to Mixtral8x7B,
        "Mixtral8x22B" to Mixtral8x22B,
        "MistralSmall" to MistralSmall,
        "MistralMedium" to MistralMedium,
        "MistralLarge" to MistralLarge,
        "MistralNemo" to MistralNemo,
        "Codestral" to Codestral,
        "CodestralMamba" to CodestralMamba,
    )

}