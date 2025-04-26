package com.simiacryptus.jopenai.models

import org.slf4j.LoggerFactory

object ModelsLabModels {
    private val log = LoggerFactory.getLogger(ModelsLabModels::class.java)

    val Zephyr7bBeta = ChatModel(
        name = "Zephyr7bBeta",
        modelName = "zephyr-7b-beta",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val DialoGPTLarge = ChatModel(
        name = "DialoGPTLarge",
        modelName = "DialoGPT-large",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val YarnMistral7b128k = ChatModel(
        name = "YarnMistral7b128k",
        modelName = "Yarn-Mistral-7b-128k",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Pygmalion13b = ChatModel(
        name = "Pygmalion13b",
        modelName = "pygmalion-1.3b",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Opt67b = ChatModel(
        name = "Opt67b",
        modelName = "opt-6.7b",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val MistralLite = ChatModel(
        name = "MistralLite",
        modelName = "MistralLite",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Openchat35 = ChatModel(
        name = "Openchat35",
        modelName = "openchat_3.5",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val NeuralChat7bV3 = ChatModel(
        name = "NeuralChat7bV3",
        modelName = "neural-chat-7b-v3",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val OpenHermes25Mistral7B = ChatModel(
        name = "OpenHermes25Mistral7B",
        modelName = "OpenHermes-2.5-Mistral-7B",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Dolphin221Mistral7b = ChatModel(
        name = "Dolphin221Mistral7b",
        modelName = "dolphin-2.2.1-mistral-7b",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Mistral7BOpenOrca = ChatModel(
        name = "Mistral7BOpenOrca",
        modelName = "Mistral-7B-OpenOrca",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )

    val DeepseekCoder67bInstruct = ChatModel(
        name = "DeepseekCoder67bInstruct",
        modelName = "deepseek-coder-6.7b-instruct",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Phi15 = ChatModel(
        name = "Phi15",
        modelName = "phi-1_5",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
    )
    val Zephyr7bAlpha = ChatModel(
        name = "Zephyr7bAlpha",
        modelName = "zephyr-7b-alpha",
        maxTotalTokens = 16384,
        provider = APIProvider.ModelsLab,
        inputTokenPricePerK = 0.0005,
        outputTokenPricePerK = 0.0015
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