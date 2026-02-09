package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object GroqModels {

    val Llama33_70bVersatile = ChatModel(
        name = "Llama33_70bVersatile",
        modelId = "llama-3.3-70b-versatile",
        maxTotalTokens = 131072,
        maxOutTokens = 32768,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Gemma2_9b = ChatModel(
        name = "Gemma2_9b",
        modelId = "gemma2-9b-it",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )

    val Llama33_70bSpecDec = ChatModel(
        name = "Llama33_70bSpecDec",
        modelId = "llama-3.3-70b-specdec",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.99
    )

    val Llama31_8bInstant = ChatModel(
        name = "Llama31_8bInstant",
        modelId = "llama-3.1-8b-instant",
        maxTotalTokens = 131072,
        maxOutTokens = 131072,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.05,
        outputTokenPricePerK = 0.08
    )

    val Llama32_1bPreview = ChatModel(
        name = "Llama32_1bPreview",
        modelId = "llama-3.2-1b-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.04,
        outputTokenPricePerK = 0.04
    )

    val Llama32_3bPreview = ChatModel(
        name = "Llama32_3bPreview",
        modelId = "llama-3.2-3b-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.06,
        outputTokenPricePerK = 0.06
    )

    val LlamaGuard38b = ChatModel(
        name = "LlamaGuard38b",
        modelId = "llama-guard-3-8b",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )

    val Llama370b8192 = ChatModel(
        name = "Llama370b8192",
        modelId = "llama3-70b-8192",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Llama38b8192 = ChatModel(
        name = "Llama38b8192",
        modelId = "llama3-8b-8192",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.05,
        outputTokenPricePerK = 0.08
    )

    val Qwen25_32b = ChatModel(
        name = "Qwen25_32b",
        modelId = "qwen-2.5-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val Qwen25Coder32b = ChatModel(
        name = "Qwen25Coder32b",
        modelId = "qwen-2.5-coder-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val QwenQwq32b = ChatModel(
        name = "QwenQwq32b",
        modelId = "qwen-qwq-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )
    val MistralSaba24b = ChatModel(
        name = "MistralSaba24b",
        modelId = "mistral-saba-24b",
        maxTotalTokens = 32000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.25,
        outputTokenPricePerK = 0.25
    )

    val DeepseekQwen32b = ChatModel(
        name = "DeepseekQwen32b",
        modelId = "deepseek-r1-distill-qwen-32b",
        maxTotalTokens = 128000,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.30,
        outputTokenPricePerK = 0.30
    )

    val DeepseekLlama70b = ChatModel(
        name = "DeepseekLlama70b",
        modelId = "deepseek-r1-distill-llama-70b",
        maxTotalTokens = 131072,
        maxOutTokens = 131072,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.59,
        outputTokenPricePerK = 0.79
    )

    val Llama32_11bVision = ChatModel(
        name = "Llama32_11bVision",
        modelId = "llama-3.2-11b-vision-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )

    val Llama32_90bVision = ChatModel(
        name = "Llama32_90bVision",
        modelId = "llama-3.2-90b-vision-preview",
        maxTotalTokens = 128000,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.70,
        outputTokenPricePerK = 0.70
    )
    val LlamaPromptGuard2_22m = ChatModel(
        name = "LlamaPromptGuard2_22m",
        modelId = "meta-llama/llama-prompt-guard-2-22m",
        maxTotalTokens = 512,
        maxOutTokens = 512,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.03,
        outputTokenPricePerK = 0.03
    )
    val LlamaPromptGuard2_86m = ChatModel(
        name = "LlamaPromptGuard2_86m",
        modelId = "meta-llama/llama-prompt-guard-2-86m",
        maxTotalTokens = 512,
        maxOutTokens = 512,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.04,
        outputTokenPricePerK = 0.04
    )
    val KimiK2Instruct = ChatModel(
        name = "KimiK2Instruct",
        modelId = "moonshotai/kimi-k2-instruct-0905",
        maxTotalTokens = 262144,
        maxOutTokens = 16384,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 1.00,
        outputTokenPricePerK = 3.00
    )
    val GptOss120b = ChatModel(
        name = "GptOss120b",
        modelId = "openai/gpt-oss-120b",
        maxTotalTokens = 131072,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.15,
        outputTokenPricePerK = 0.60
    )
    val GptOss20b = ChatModel(
        name = "GptOss20b",
        modelId = "openai/gpt-oss-20b",
        maxTotalTokens = 131072,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.075,
        outputTokenPricePerK = 0.30
    )
    val PlayAiTts = ChatModel(
        name = "PlayAiTts",
        modelId = "playai-tts",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )
    val PlayAiTtsArabic = ChatModel(
        name = "PlayAiTtsArabic",
        modelId = "playai-tts-arabic",
        maxTotalTokens = 8192,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )
    val Qwen3_32b = ChatModel(
        name = "Qwen3_32b",
        modelId = "qwen/qwen3-32b",
        maxTotalTokens = 131072,
        maxOutTokens = 40960,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.29,
        outputTokenPricePerK = 0.59
    )

    val Llama4Scout17b = ChatModel(
        name = "Llama4Scout17b",
        modelId = "meta-llama/llama-4-scout-17b-16e-instruct",
        maxTotalTokens = 131072,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.11,
        outputTokenPricePerK = 0.34
    )
    val Llama4Maverick17b = ChatModel(
        name = "Llama4Maverick17b",
        modelId = "meta-llama/llama-4-maverick-17b-128e-instruct",
        maxTotalTokens = 131072,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.60
    )
    val Allam2_7b = ChatModel(
        name = "Allam2_7b",
        modelId = "allam-2-7b",
        maxTotalTokens = 4096,
        maxOutTokens = 4096,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.10,
        outputTokenPricePerK = 0.10
    )
    val LlamaGuard4_12b = ChatModel(
        name = "LlamaGuard4_12b",
        modelId = "meta-llama/llama-guard-4-12b",
        maxTotalTokens = 131072,
        maxOutTokens = 1024,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.20,
        outputTokenPricePerK = 0.20
    )
    val GptOssSafeguard20b = ChatModel(
        name = "GptOssSafeguard20b",
        modelId = "openai/gpt-oss-safeguard-20b",
        maxTotalTokens = 131072,
        maxOutTokens = 65536,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.075,
        outputTokenPricePerK = 0.30
    )
    val Compound = ChatModel(
        name = "Compound",
        modelId = "groq/compound",
        maxTotalTokens = 131072,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.0,
        outputTokenPricePerK = 0.0
    )
    val CompoundMini = ChatModel(
        name = "CompoundMini",
        modelId = "groq/compound-mini",
        maxTotalTokens = 131072,
        maxOutTokens = 8192,
        provider = APIProvider.Companion.Groq,
        inputTokenPricePerK = 0.0,
        outputTokenPricePerK = 0.0
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
        "LlamaPromptGuard2_22m" to LlamaPromptGuard2_22m,
        "LlamaPromptGuard2_86m" to LlamaPromptGuard2_86m,
        "KimiK2Instruct" to KimiK2Instruct,
        "GptOss120b" to GptOss120b,
        "GptOss20b" to GptOss20b,
        "PlayAiTts" to PlayAiTts,
        "PlayAiTtsArabic" to PlayAiTtsArabic,
        "Qwen3_32b" to Qwen3_32b,
        "Llama4Scout17b" to Llama4Scout17b,
        "Llama4Maverick17b" to Llama4Maverick17b,
        "Allam2_7b" to Allam2_7b,
        "LlamaGuard4_12b" to LlamaGuard4_12b,
        "GptOssSafeguard20b" to GptOssSafeguard20b,
        "Compound" to Compound,
        "CompoundMini" to CompoundMini
    )
}