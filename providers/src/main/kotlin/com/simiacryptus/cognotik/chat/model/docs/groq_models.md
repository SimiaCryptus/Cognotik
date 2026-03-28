---
documents: ../core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/GroqModels.kt
specifies: ../core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/GroqModels.kt
related:
  - https://console.groq.com/docs/models
  - https://console.groq.com/docs/pricing
---

# Groq Models

The `GroqModels` object defines all available chat models accessible through the Groq API provider. Groq is known for
its high-speed inference engine (LPU), offering extremely fast token generation across a variety of open-source and
proprietary models.

## Overview

All models in this file are instances of `ChatModel` configured with `APIProvider.Companion.Groq` as their provider.
Each model specifies:

- **name** – Internal identifier used for lookup
- **modelId** – The Groq API model identifier
- **maxTotalTokens** – Maximum combined input + output token context window
- **maxOutTokens** – Maximum number of output tokens
- **inputTokenPricePerK** – Cost per 1,000 input tokens (USD)
- **outputTokenPricePerK** – Cost per 1,000 output tokens (USD)

## Production Models

Production models are intended for use in production environments. They meet or exceed Groq's high standards for speed,
quality, and reliability.

| Name                 | Model ID                  | Speed (T/s) | Context Window | Max Output | Input $/1K | Output $/1K |
|----------------------|---------------------------|-------------|----------------|------------|------------|-------------|
| Llama31_8bInstant    | `llama-3.1-8b-instant`    | 560         | 131,072        | 131,072    | $0.05      | $0.08       |
| Llama33_70bVersatile | `llama-3.3-70b-versatile` | 280         | 131,072        | 32,768     | $0.59      | $0.79       |
| GptOss120b           | `openai/gpt-oss-120b`     | 500         | 131,072        | 65,536     | $0.15      | $0.60       |
| GptOss20b            | `openai/gpt-oss-20b`      | 1000        | 131,072        | 65,536     | $0.075     | $0.30       |

> **Note:** Whisper models (`whisper-large-v3`, `whisper-large-v3-turbo`) are speech-to-text models listed on the Groq
> models page but are not included in this file as they are not chat models.

## Production Systems

Systems are a collection of models and tools that work together to answer a user query. These support agentic
capabilities including web search and code execution.

| Name         | Model ID             | Speed (T/s) | Context Window | Max Output | Input $/1K | Output $/1K |
|--------------|----------------------|-------------|----------------|------------|------------|-------------|
| Compound     | `groq/compound`      | 450         | 131,072        | 8,192      | $0.00      | $0.00       |
| CompoundMini | `groq/compound-mini` | 450         | 131,072        | 8,192      | $0.00      | $0.00       |

> **Note:** Compound models have zero listed token costs — pricing may be handled differently or bundled.

## Preview Models

Preview models are intended for evaluation purposes only and should not be used in production environments as they may
be discontinued at short notice.

| Name                  | Model ID                                    | Speed (T/s) | Context Window | Max Output | Input $/1K | Output $/1K |
|-----------------------|---------------------------------------------|-------------|----------------|------------|------------|-------------|
| OrpheusArabicSaudi    | `canopylabs/orpheus-arabic-saudi`           | -           | 4,000          | 50,000     | $0.00*     | $0.00*      |
| OrpheusV1English      | `canopylabs/orpheus-v1-english`             | -           | 4,000          | 50,000     | $0.00*     | $0.00*      |
| Llama4Scout17b        | `meta-llama/llama-4-scout-17b-16e-instruct` | 750         | 131,072        | 8,192      | $0.11      | $0.34       |
| LlamaPromptGuard2_22m | `meta-llama/llama-prompt-guard-2-22m`       | -           | 512            | 512        | $0.03      | $0.03       |
| LlamaPromptGuard2_86m | `meta-llama/llama-prompt-guard-2-86m`       | -           | 512            | 512        | $0.04      | $0.04       |
| KimiK2Instruct        | `moonshotai/kimi-k2-instruct-0905`          | 200         | 262,144        | 16,384     | $1.00      | $3.00       |
| GptOssSafeguard20b    | `openai/gpt-oss-safeguard-20b`              | 1000        | 131,072        | 65,536     | $0.075     | $0.30       |
| Qwen3_32b             | `qwen/qwen3-32b`                            | 400         | 131,072        | 40,960     | $0.29      | $0.59       |

> \* Orpheus TTS models are priced per 1M characters ($40.00 for Arabic Saudi, $22.00 for English) rather than per
> token. Token pricing is set to $0.00 in the model definition.

## Model Lookup

All models are registered in the `values` map, keyed by their `name` property. This allows programmatic lookup:

```kotlin
val model = GroqModels.values["Llama33_70bVersatile"]
```

Or direct reference:

```kotlin
val model = GroqModels.Llama33_70bVersatile
```

## Notes

- **Compound Models**: `Compound` and `CompoundMini` are Groq's agentic compound systems with zero listed token costs —
  pricing may be handled differently or bundled. They support web search, code execution, and other built-in tools.
- **Guard Models**: Several safety-focused models (`LlamaPromptGuard2_22m`, `LlamaPromptGuard2_86m`,
  `GptOssSafeguard20b`) are available for content moderation and prompt injection detection. The prompt guard models
  have very small context windows (512 tokens).
- **Largest Context**: `KimiK2Instruct` offers the largest context window at 262,144 tokens but is also the most
  expensive model in the catalog.
- **Orpheus TTS Models**: `OrpheusArabicSaudi` and `OrpheusV1English` are text-to-speech models from Canopy Labs. They
  use character-based pricing rather than token-based pricing.
- **Featured Models**: Groq highlights `Compound` and `GPT-OSS 120B` as featured models on their documentation page.