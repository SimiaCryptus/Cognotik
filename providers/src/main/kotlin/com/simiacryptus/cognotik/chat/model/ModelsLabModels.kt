package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders

object ModelsLabModels {

  val Zephyr7bBeta = ChatModel(
      name = "Zephyr7bBeta",
      modelId = "zephyr-7b-beta",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val DialoGPTLarge = ChatModel(
      name = "DialoGPTLarge",
      modelId = "DialoGPT-large",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val YarnMistral7b128k = ChatModel(
      name = "YarnMistral7b128k",
      modelId = "Yarn-Mistral-7b-128k",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val Pygmalion13b = ChatModel(
      name = "Pygmalion13b",
      modelId = "pygmalion-1.3b",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val Opt67b = ChatModel(
      name = "Opt67b",
      modelId = "opt-6.7b",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val MistralLite = ChatModel(
      name = "MistralLite",
      modelId = "MistralLite",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val Openchat35 = ChatModel(
      name = "Openchat35",
      modelId = "openchat_3.5",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val NeuralChat7bV3 = ChatModel(
      name = "NeuralChat7bV3",
      modelId = "neural-chat-7b-v3",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val OpenHermes25Mistral7B = ChatModel(
      name = "OpenHermes25Mistral7B",
      modelId = "OpenHermes-2.5-Mistral-7B",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val Dolphin221Mistral7b = ChatModel(
      name = "Dolphin221Mistral7b",
      modelId = "dolphin-2.2.1-mistral-7b",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val Mistral7BOpenOrca = ChatModel(
      name = "Mistral7BOpenOrca",
      modelId = "Mistral-7B-OpenOrca",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )

  val DeepseekCoder67bInstruct = ChatModel(
      name = "DeepseekCoder67bInstruct",
      modelId = "deepseek-coder-6.7b-instruct",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val Phi15 = ChatModel(
      name = "Phi15",
      modelId = "phi-1_5",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val Zephyr7bAlpha = ChatModel(
      name = "Zephyr7bAlpha",
      modelId = "zephyr-7b-alpha",
      maxTotalTokens = 16384,
      provider = CoreProviders.ModelsLab,
      inputTokenPricePerK = 0.0005,
      outputTokenPricePerK = 0.0015,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
  )
  val values = mapOf(
    "Zephyr7bBeta" to Zephyr7bBeta,
    "DialoGPTLarge" to DialoGPTLarge,
    "YarnMistral7b128k" to YarnMistral7b128k,
    "Pygmalion13b" to Pygmalion13b,
    "Opt67b" to Opt67b,
    "MistralLite" to MistralLite,
    "Openchat35" to Openchat35,
    "NeuralChat7bV3" to NeuralChat7bV3,
    "OpenHermes25Mistral7B" to OpenHermes25Mistral7B,
    "Dolphin221Mistral7b" to Dolphin221Mistral7b,
    "Mistral7BOpenOrca" to Mistral7BOpenOrca,
    "DeepseekCoder67bInstruct" to DeepseekCoder67bInstruct,
    "Phi15" to Phi15,
    "Zephyr7bAlpha" to Zephyr7bAlpha,
  )

}