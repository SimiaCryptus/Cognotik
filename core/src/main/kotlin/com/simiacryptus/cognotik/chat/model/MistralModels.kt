package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object MistralModels {

    // Frontier Generalist Models

    val MistralLarge3 = ChatModel(
        name = "MistralLarge3",
        modelName = "mistral-large-latest",
        maxTotalTokens = 131072,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.002,
        outputTokenPricePerK = 0.006
    )

    val MistralMedium3_1 = ChatModel(
        name = "MistralMedium3_1",
        modelName = "mistral-medium-latest",
        maxTotalTokens = 131072,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0015,
        outputTokenPricePerK = 0.0045
    )

    val MistralSmall3_2 = ChatModel(
        name = "MistralSmall3_2",
        modelName = "mistral-small-latest",
        maxTotalTokens = 131072,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0003
    )

    val Ministral3_14B = ChatModel(
        name = "Ministral3_14B",
        modelName = "ministral-14b-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.00009,
        outputTokenPricePerK = 0.00009
    )

    val Ministral3_8B = ChatModel(
        name = "Ministral3_8B",
        modelName = "ministral-8b-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.00009,
        outputTokenPricePerK = 0.00009
    )

    val Ministral3_3B = ChatModel(
        name = "Ministral3_3B",
        modelName = "ministral-3b-latest",
        maxTotalTokens = 32768,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.00004,
        outputTokenPricePerK = 0.00004
    )

    val MagistralMedium = ChatModel(
        name = "MagistralMedium",
        modelName = "magistral-medium-latest",
        maxTotalTokens = 131072,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.002,
        outputTokenPricePerK = 0.006
    )
    val MagistralSmall = ChatModel(
        name = "MagistralSmall",
        modelName = "magistral-small-latest",
        maxTotalTokens = 131072,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0003
    )
    // Specialist Models

    val Codestral = ChatModel(
        name = "Codestral",
        modelName = "codestral-latest",
        maxTotalTokens = 262144,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0003,
        outputTokenPricePerK = 0.0009
    )

    val Devstral2 = ChatModel(
        name = "Devstral2",
        modelName = "devstral-large-latest",
        maxTotalTokens = 131072,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.002,
        outputTokenPricePerK = 0.006
    )
    val DevstralSmall2 = ChatModel(
        name = "DevstralSmall2",
        modelName = "devstral-small-latest",
        maxTotalTokens = 131072,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.0001,
        outputTokenPricePerK = 0.0003
    )
    // Other Models
    val MistralNemo = ChatModel(
        name = "MistralNemo",
        modelName = "open-mistral-nemo",
        maxTotalTokens = 131071,
        provider = APIProvider.Companion.Mistral,
        inputTokenPricePerK = 0.00015,
        outputTokenPricePerK = 0.00015
    )

    val values = mapOf(
        // Frontier Generalist
        "MistralLarge3" to MistralLarge3,
        "MistralMedium3_1" to MistralMedium3_1,
        "MistralSmall3_2" to MistralSmall3_2,
        "Ministral3_14B" to Ministral3_14B,
        "Ministral3_8B" to Ministral3_8B,
        "Ministral3_3B" to Ministral3_3B,
        "MagistralMedium" to MagistralMedium,
        "MagistralSmall" to MagistralSmall,
        // Specialist
        "Codestral" to Codestral,
        "Devstral2" to Devstral2,
        "DevstralSmall2" to DevstralSmall2,
        // Other
        "MistralNemo" to MistralNemo,
        // Legacy aliases for backward compatibility
        "MistralSmall" to MistralSmall3_2,
        "MistralMedium" to MistralMedium3_1,
        "MistralLarge" to MistralLarge3,
    )

}