package com.simiacryptus.jopenai.models.chat

import com.simiacryptus.jopenai.models.APIProvider

object GroqModels {

    val Llama33_70bVersatile = ChatModelType(
        name = "Llama33_70bVersatile",
        modelName = "llama-3.3-70b-versatile",
        maxTotalTokens = 128000,
        maxOutTokens = 32768,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Gemma2_9b = ChatModelType(
        name = "Gemma2_9b",
        modelName = "gemma2-9b-it",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )

    val Llama33_70bSpecDec = ChatModelType(
        name = "Llama33_70bSpecDec",
        modelName = "llama-3.3-70b-specdec",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.99
    )

    val Llama31_8bInstant = ChatModelType(
        name = "Llama31_8bInstant",
        modelName = "llama-3.1-8b-instant",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.05,
        outputTokenPricePerK = 0.08
    )

    val Llama32_1bPreview = ChatModelType(
        name = "Llama32_1bPreview",
        modelName = "llama-3.2-1b-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.04,
        outputTokenPricePerK = 0.04
    )

    val Llama32_3bPreview = ChatModelType(
        name = "Llama32_3bPreview",
        modelName = "llama-3.2-3b-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.06,
        outputTokenPricePerK = 0.06
    )

    val LlamaGuard38b = ChatModelType(
        name = "LlamaGuard38b",
        modelName = "llama-guard-3-8b",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )

    val Llama370b8192 = ChatModelType(
        name = "Llama370b8192",
        modelName = "llama3-70b-8192",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Llama38b8192 = ChatModelType(
        name = "Llama38b8192",
        modelName = "llama3-8b-8192",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.05,
        outputTokenPricePerK = 0.08
    )

    val Qwen25_32b = ChatModelType(
        name = "Qwen25_32b",
        modelName = "qwen-2.5-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val Qwen25Coder32b = ChatModelType(
        name = "Qwen25Coder32b",
        modelName = "qwen-2.5-coder-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val QwenQwq32b = ChatModelType(
        name = "QwenQwq32b",
        modelName = "qwen-qwq-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val MistralSaba24b = ChatModelType(
        name = "MistralSaba24b",
        modelName = "mistral-saba-24b",
        maxTotalTokens = 32000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.25,
        outputTokenPricePerK = 0.25
    )

    val DeepseekQwen32b = ChatModelType(
        name = "DeepseekQwen32b",
        modelName = "deepseek-r1-distill-qwen-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )

    val DeepseekLlama70b = ChatModelType(
        name = "DeepseekLlama70b",
        modelName = "deepseek-r1-distill-llama-70b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Llama32_11bVision = ChatModelType(
        name = "Llama32_11bVision",
        modelName = "llama-3.2-11b-vision-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )

    val Llama32_90bVision = ChatModelType(
        name = "Llama32_90bVision",
        modelName = "llama-3.2-90b-vision-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.70,
        outputTokenPricePerK = 0.70
    )
    val Llama4Scout17b = ChatModelType(
        name = "Llama4Scout17b",
        modelName = "meta-llama/llama-4-scout-17b-16e-instruct",
        maxTotalTokens = 131072,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )
    val Llama4Maverick17b = ChatModelType(
        name = "Llama4Maverick17b",
        modelName = "meta-llama/llama-4-maverick-17b-128e-instruct",
        maxTotalTokens = 131072,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )
    val Allam2_7b = ChatModelType(
        name = "Allam2_7b",
        modelName = "allam-2-7b",
        maxTotalTokens = 4096,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )

    val values = mapOf(
        "Llama33_70bVersatile" to Llama33_70bVersatile,
        "Gemma2_9b" to Gemma2_9b,
        "Llama31_8bInstant" to Llama31_8bInstant,
        "Llama32_1bPreview" to Llama32_1bPreview,
        "Llama32_3bPreview" to Llama32_3bPreview,
        "Llama33_70bSpecDec" to Llama33_70bSpecDec,
        "LlamaGuard38b" to LlamaGuard38b,
        "Llama370b8192" to Llama370b8192,
        "Llama38b8192" to Llama38b8192,
        "Qwen25_32b" to Qwen25_32b,
        "Qwen25Coder32b" to Qwen25Coder32b,
        "QwenQwq32b" to QwenQwq32b,
        "MistralSaba24b" to MistralSaba24b,
        "DeepseekQwen32b" to DeepseekQwen32b,
        "DeepseekLlama70b" to DeepseekLlama70b,
        "Llama32_11bVision" to Llama32_11bVision,
        "Llama32_90bVision" to Llama32_90bVision,
        "Llama4Scout17b" to Llama4Scout17b,
        "Llama4Maverick17b" to Llama4Maverick17b,
        "Allam2_7b" to Allam2_7b
    )
}