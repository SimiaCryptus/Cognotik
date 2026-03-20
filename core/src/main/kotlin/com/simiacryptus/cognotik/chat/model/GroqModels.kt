package com.simiacryptus.cognotik.chat.model

import com.simiacryptus.cognotik.models.APIProvider

object GroqModels {

  // ==================== Production Models ====================

  val Llama31_8bInstant = ChatModel(
    name = "Llama31_8bInstant",
    modelId = "llama-3.1-8b-instant",
    maxTotalTokens = 131072,
    maxOutTokens = 131072,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.05,
    outputTokenPricePerK = 0.08
  )

  val Llama33_70bVersatile = ChatModel(
    name = "Llama33_70bVersatile",
    modelId = "llama-3.3-70b-versatile",
    maxTotalTokens = 131072,
    maxOutTokens = 32768,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.59,
    outputTokenPricePerK = 0.79
  )

  val GptOss120b = ChatModel(
    name = "GptOss120b",
    modelId = "openai/gpt-oss-120b",
    maxTotalTokens = 131072,
    maxOutTokens = 65536,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.15,
    outputTokenPricePerK = 0.60
  )

  val GptOss20b = ChatModel(
    name = "GptOss20b",
    modelId = "openai/gpt-oss-20b",
    maxTotalTokens = 131072,
    maxOutTokens = 65536,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.075,
    outputTokenPricePerK = 0.30
  )

  // ==================== Production Systems ====================

  val Compound = ChatModel(
    name = "Compound",
    modelId = "groq/compound",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.0,
    outputTokenPricePerK = 0.0
  )

  val CompoundMini = ChatModel(
    name = "CompoundMini",
    modelId = "groq/compound-mini",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.0,
    outputTokenPricePerK = 0.0
  )

  // ==================== Preview Models ====================

  val OrpheusArabicSaudi = ChatModel(
    name = "OrpheusArabicSaudi",
    modelId = "canopylabs/orpheus-arabic-saudi",
    maxTotalTokens = 4000,
    maxOutTokens = 50000,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.0,
    outputTokenPricePerK = 0.0
  )

  val OrpheusV1English = ChatModel(
    name = "OrpheusV1English",
    modelId = "canopylabs/orpheus-v1-english",
    maxTotalTokens = 4000,
    maxOutTokens = 50000,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.0,
    outputTokenPricePerK = 0.0
  )

  val Llama4Scout17b = ChatModel(
    name = "Llama4Scout17b",
    modelId = "meta-llama/llama-4-scout-17b-16e-instruct",
    maxTotalTokens = 131072,
    maxOutTokens = 8192,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.11,
    outputTokenPricePerK = 0.34
  )

  val LlamaPromptGuard2_22m = ChatModel(
    name = "LlamaPromptGuard2_22m",
    modelId = "meta-llama/llama-prompt-guard-2-22m",
    maxTotalTokens = 512,
    maxOutTokens = 512,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.03,
    outputTokenPricePerK = 0.03
  )
  val LlamaPromptGuard2_86m = ChatModel(
    name = "LlamaPromptGuard2_86m",
    modelId = "meta-llama/llama-prompt-guard-2-86m",
    maxTotalTokens = 512,
    maxOutTokens = 512,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.04,
    outputTokenPricePerK = 0.04
  )
  val KimiK2Instruct = ChatModel(
    name = "KimiK2Instruct",
    modelId = "moonshotai/kimi-k2-instruct-0905",
    maxTotalTokens = 262144,
    maxOutTokens = 16384,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 1.00,
    outputTokenPricePerK = 3.00
  )
  val GptOssSafeguard20b = ChatModel(
    name = "GptOssSafeguard20b",
    modelId = "openai/gpt-oss-safeguard-20b",
    maxTotalTokens = 131072,
    maxOutTokens = 65536,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.075,
    outputTokenPricePerK = 0.30
  )

  val Qwen3_32b = ChatModel(
    name = "Qwen3_32b",
    modelId = "qwen/qwen3-32b",
    maxTotalTokens = 131072,
    maxOutTokens = 40960,
    provider = APIProvider.Groq,
    inputTokenPricePerK = 0.29,
    outputTokenPricePerK = 0.59
  )


  val values = mapOf(
    // Production Models
    "Llama31_8bInstant" to Llama31_8bInstant,
    "Llama33_70bVersatile" to Llama33_70bVersatile,
    "GptOss120b" to GptOss120b,
    "GptOss20b" to GptOss20b,
    // Production Systems
    "Compound" to Compound,
    "CompoundMini" to CompoundMini,
    // Preview Models
    "OrpheusArabicSaudi" to OrpheusArabicSaudi,
    "OrpheusV1English" to OrpheusV1English,
    "Llama4Scout17b" to Llama4Scout17b,
    "LlamaPromptGuard2_22m" to LlamaPromptGuard2_22m,
    "LlamaPromptGuard2_86m" to LlamaPromptGuard2_86m,
    "KimiK2Instruct" to KimiK2Instruct,
    "GptOssSafeguard20b" to GptOssSafeguard20b,
    "Qwen3_32b" to Qwen3_32b
  )
}