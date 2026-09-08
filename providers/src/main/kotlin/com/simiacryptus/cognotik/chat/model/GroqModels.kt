package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.CoreProviders
import com.simiacryptus.cognotik.platform.model.ChatMessageModality
import com.simiacryptus.cognotik.platform.model.ChatModel

object GroqModels {

    // ==================== Production Models ====================

    val Llama31_8bInstant = ChatModel(
      name = "Llama31_8bInstant",
      modelId = "llama-3.1-8b-instant",
      maxTotalTokens = 131072,
      maxOutTokens = 131072,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.05 / 1000.0,
      outputTokenPricePerK = 0.08 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )

    val Llama33_70bVersatile = ChatModel(
      name = "Llama33_70bVersatile",
      modelId = "llama-3.3-70b-versatile",
      maxTotalTokens = 131072,
      maxOutTokens = 32768,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.59 / 1000.0,
      outputTokenPricePerK = 0.79 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )

    val GptOss120b = ChatModel(
      name = "GptOss120b",
      modelId = "openai/gpt-oss-120b",
      maxTotalTokens = 131072,
      maxOutTokens = 65536,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.15 / 1000.0,
      outputTokenPricePerK = 0.60 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )


    val GptOss20b = ChatModel(
      name = "GptOss20b",
      modelId = "openai/gpt-oss-20b",
      maxTotalTokens = 131072,
      maxOutTokens = 65536,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.075 / 1000.0,
      outputTokenPricePerK = 0.30 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )
    val WhisperLargeV3 = ChatModel(
      name = "WhisperLargeV3",
      modelId = "whisper-large-v3",
      maxTotalTokens = 0,
      maxOutTokens = 0,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.111 / 1000.0,
      outputTokenPricePerK = 0.0 / 1000.0,
      inputModalities = setOf(ChatMessageModality.AUDIO),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )
    val WhisperLargeV3Turbo = ChatModel(
      name = "WhisperLargeV3Turbo",
      modelId = "whisper-large-v3-turbo",
      maxTotalTokens = 0,
      maxOutTokens = 0,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.04 / 1000.0,
      outputTokenPricePerK = 0.0 / 1000.0,
      inputModalities = setOf(ChatMessageModality.AUDIO),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )


    // ==================== Production Systems ====================

    val Compound = ChatModel(
      name = "Compound",
      modelId = "groq/compound",
      maxTotalTokens = 131072,
      maxOutTokens = 8192,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.0,
      outputTokenPricePerK = 0.0,
      inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )

    val CompoundMini = ChatModel(
      name = "CompoundMini",
      modelId = "groq/compound-mini",
      maxTotalTokens = 131072,
      maxOutTokens = 8192,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.0,
      outputTokenPricePerK = 0.0,
      inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )

    // ==================== Preview Models ====================

    val OrpheusArabicSaudi = ChatModel(
      name = "OrpheusArabicSaudi",
      modelId = "canopylabs/orpheus-arabic-saudi",
      maxTotalTokens = 4000,
      maxOutTokens = 50000,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 40.00,
      outputTokenPricePerK = 40.00,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO)
    )

    val OrpheusV1English = ChatModel(
      name = "OrpheusV1English",
      modelId = "canopylabs/orpheus-v1-english",
      maxTotalTokens = 4000,
      maxOutTokens = 50000,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 22.00,
      outputTokenPricePerK = 22.00,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.AUDIO)
    )

    val Llama4Scout17b = ChatModel(
      name = "Llama4Scout17b",
      modelId = "meta-llama/llama-4-scout-17b-16e-instruct",
      maxTotalTokens = 131072,
      maxOutTokens = 8192,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.11 / 1000.0,
      outputTokenPricePerK = 0.34 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )

    val LlamaPromptGuard2_22m = ChatModel(
      name = "LlamaPromptGuard2_22m",
      modelId = "meta-llama/llama-prompt-guard-2-22m",
      maxTotalTokens = 512,
      maxOutTokens = 512,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.03 / 1000.0,
      outputTokenPricePerK = 0.03 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )
    val LlamaPromptGuard2_86m = ChatModel(
      name = "LlamaPromptGuard2_86m",
      modelId = "meta-llama/llama-prompt-guard-2-86m",
      maxTotalTokens = 512,
      maxOutTokens = 512,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.04 / 1000.0,
      outputTokenPricePerK = 0.04 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )
    val GptOssSafeguard20b = ChatModel(
      name = "GptOssSafeguard20b",
      modelId = "openai/gpt-oss-safeguard-20b",
      maxTotalTokens = 131072,
      maxOutTokens = 65536,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.075 / 1000.0,
      outputTokenPricePerK = 0.30 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT, ChatMessageModality.IMAGE),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )
    val Qwen3_32b = ChatModel(
      name = "Qwen3_32b",
      modelId = "qwen/qwen3-32b",
      maxTotalTokens = 131072,
      maxOutTokens = 40960,
      provider = CoreProviders.Groq,
      inputTokenPricePerK = 0.29 / 1000.0,
      outputTokenPricePerK = 0.59 / 1000.0,
      inputModalities = setOf(ChatMessageModality.TEXT),
      outputModalities = setOf(ChatMessageModality.TEXT)
    )


    val values = mapOf(
        // Production Models
        "Llama31_8bInstant" to Llama31_8bInstant,
        "Llama33_70bVersatile" to Llama33_70bVersatile,
        "GptOss120b" to GptOss120b,
        "GptOss20b" to GptOss20b,
        "WhisperLargeV3" to WhisperLargeV3,
        "WhisperLargeV3Turbo" to WhisperLargeV3Turbo,
        // Production Systems
        "Compound" to Compound,
        "CompoundMini" to CompoundMini,
        // Preview Models
        "OrpheusArabicSaudi" to OrpheusArabicSaudi,
        "OrpheusV1English" to OrpheusV1English,
        "Llama4Scout17b" to Llama4Scout17b,
        "LlamaPromptGuard2_22m" to LlamaPromptGuard2_22m,
        "LlamaPromptGuard2_86m" to LlamaPromptGuard2_86m,
        "GptOssSafeguard20b" to GptOssSafeguard20b,
        "Qwen3_32b" to Qwen3_32b,
    )
}