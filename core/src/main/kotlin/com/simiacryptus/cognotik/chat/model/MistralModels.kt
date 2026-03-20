package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object MistralModels {

  // Frontier Generalist Models
  val MistralSmall4 = ChatModel(
    name = "MistralSmall4",
    modelId = "mistral-small-2603",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0003
  )


  val MistralLarge3 = ChatModel(
    name = "MistralLarge3",
    modelId = "mistral-large-latest",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.002,
    outputTokenPricePerK = 0.006
  )

  val MistralMedium3_1 = ChatModel(
    name = "MistralMedium3_1",
    modelId = "mistral-medium-latest",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.0015,
    outputTokenPricePerK = 0.0045
  )

  val MistralSmall3_2 = ChatModel(
    name = "MistralSmall3_2",
    modelId = "mistral-small-latest",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0003
  )

  val Ministral3_14B = ChatModel(
    name = "Ministral3_14B",
    modelId = "ministral-14b-latest",
    maxTotalTokens = 32768,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.00009,
    outputTokenPricePerK = 0.00009
  )

  val Ministral3_8B = ChatModel(
    name = "Ministral3_8B",
    modelId = "ministral-8b-latest",
    maxTotalTokens = 32768,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.00009,
    outputTokenPricePerK = 0.00009
  )

  val Ministral3_3B = ChatModel(
    name = "Ministral3_3B",
    modelId = "ministral-3b-latest",
    maxTotalTokens = 32768,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.00004,
    outputTokenPricePerK = 0.00004
  )

  val MagistralMedium1_2 = ChatModel(
    name = "MagistralMedium1_2",
    modelId = "magistral-medium-latest",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.002,
    outputTokenPricePerK = 0.006
  )

  val MagistralSmall1_2 = ChatModel(
    name = "MagistralSmall1_2",
    modelId = "magistral-small-latest",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0003
  )

  // Specialist Models
  val Leanstral = ChatModel(
    name = "Leanstral",
    modelId = "leanstral-2603",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0003
  )


  val Codestral = ChatModel(
    name = "Codestral",
    modelId = "codestral-latest",
    maxTotalTokens = 262144,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.0003,
    outputTokenPricePerK = 0.0009
  )

  val Devstral2 = ChatModel(
    name = "Devstral2",
    modelId = "devstral-large-latest",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.002,
    outputTokenPricePerK = 0.006
  )

  val DevstralSmall2 = ChatModel(
    name = "DevstralSmall2",
    modelId = "devstral-small-latest",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0003
  )

  // Other Models
  val MistralSmallCreative = ChatModel(
    name = "MistralSmallCreative",
    modelId = "mistral-small-creative-2512",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.0001,
    outputTokenPricePerK = 0.0003
  )
  val DevstralMedium1_0 = ChatModel(
    name = "DevstralMedium1_0",
    modelId = "devstral-medium-2507",
    maxTotalTokens = 131072,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.002,
    outputTokenPricePerK = 0.006
  )

  val MistralNemo = ChatModel(
    name = "MistralNemo",
    modelId = "open-mistral-nemo",
    maxTotalTokens = 131071,
    provider = APIProvider.Companion.Mistral,
    inputTokenPricePerK = 0.00015,
    outputTokenPricePerK = 0.00015
  )

  val values = mapOf(
    // Frontier Generalist
    "MistralSmall4" to MistralSmall4,
    "MistralLarge3" to MistralLarge3,
    "MistralMedium3_1" to MistralMedium3_1,
    "MistralSmall3_2" to MistralSmall3_2,
    "Ministral3_14B" to Ministral3_14B,
    "Ministral3_8B" to Ministral3_8B,
    "Ministral3_3B" to Ministral3_3B,
    "MagistralMedium1_2" to MagistralMedium1_2,
    "MagistralSmall1_2" to MagistralSmall1_2,
    // Specialist
    "Leanstral" to Leanstral,
    "Codestral" to Codestral,
    "Devstral2" to Devstral2,
    "DevstralSmall2" to DevstralSmall2,
    // Other
    "MistralSmallCreative" to MistralSmallCreative,
    "DevstralMedium1_0" to DevstralMedium1_0,
    "MistralNemo" to MistralNemo,
    // Legacy aliases for backward compatibility
    "MistralSmall" to MistralSmall3_2,
    "MistralMedium" to MistralMedium3_1,
    "MistralLarge" to MistralLarge3,
    "MagistralMedium" to MagistralMedium1_2,
    "MagistralSmall" to MagistralSmall1_2,
  )

}