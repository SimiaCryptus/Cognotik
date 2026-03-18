---
documents: ../core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/GroqModels.kt
specifies: ../core/src/main/kotlin/com/simiacryptus/cognotik/chat/model/GroqModels.kt
related:
  - https://console.groq.com/docs/models
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

## Available Models

### Llama Models

| Name                 | Model ID                  | Context Window | Max Output | Input $/1K | Output $/1K |
|----------------------|---------------------------|----------------|------------|------------|-------------|
| Llama33_70bVersatile | `llama-3.3-70b-versatile` | 131,072        | 32,768     | $0.59      | $0.79       |
| Llama33_70bSpecDec   | `llama-3.3-70b-specdec`   | 8,192          | 8,192      | $0.59      | $0.99       |
| Llama31_8bInstant    | `llama-3.1-8b-instant`    | 131,072        | 131,072    | $0.05      | $0.08       |
| Llama32_1bPreview    | `llama-3.2-1b-preview`    | 128,000        | 8,192      | $0.04      | $0.04       |
| Llama32_3bPreview    | `llama-3.2-3b-preview`    | 128,000        | 8,192      | $0.06      | $0.06       |
| Llama370b8192        | `llama3-70b-8192`         | 8,192          | 8,192      | $0.59      | $0.79       |
| Llama38b8192         | `llama3-8b-8192`          | 8,192          | 8,192      | $0.05      | $0.08       |

### Llama 4 Models

| Name              | Model ID                                        | Context Window | Max Output | Input $/1K | Output $/1K |
|-------------------|-------------------------------------------------|----------------|------------|------------|-------------|
| Llama4Scout17b    | `meta-llama/llama-4-scout-17b-16e-instruct`     | 131,072        | 8,192      | $0.11      | $0.34       |
| Llama4Maverick17b | `meta-llama/llama-4-maverick-17b-128e-instruct` | 131,072        | 8,192      | $0.20      | $0.60       |

### Vision Models

| Name              | Model ID                       | Context Window | Max Output | Input $/1K | Output $/1K |
|-------------------|--------------------------------|----------------|------------|------------|-------------|
| Llama32_11bVision | `llama-3.2-11b-vision-preview` | 128,000        | 8,192      | $0.10      | $0.10       |
| Llama32_90bVision | `llama-3.2-90b-vision-preview` | 128,000        | 8,192      | $0.70      | $0.70       |

### Safety / Guard Models

| Name                  | Model ID                              | Context Window | Max Output | Input $/1K | Output $/1K |
|-----------------------|---------------------------------------|----------------|------------|------------|-------------|
| LlamaGuard38b         | `llama-guard-3-8b`                    | 8,192          | 8,192      | $0.20      | $0.20       |
| LlamaGuard4_12b       | `meta-llama/llama-guard-4-12b`        | 131,072        | 1,024      | $0.20      | $0.20       |
| LlamaPromptGuard2_22m | `meta-llama/llama-prompt-guard-2-22m` | 512            | 512        | $0.03      | $0.03       |
| LlamaPromptGuard2_86m | `meta-llama/llama-prompt-guard-2-86m` | 512            | 512        | $0.04      | $0.04       |
| GptOssSafeguard20b    | `openai/gpt-oss-safeguard-20b`        | 131,072        | 65,536     | $0.075     | $0.30       |

### Qwen Models

| Name           | Model ID             | Context Window | Max Output | Input $/1K | Output $/1K |
|----------------|----------------------|----------------|------------|------------|-------------|
| Qwen25_32b     | `qwen-2.5-32b`       | 128,000        | 16,384     | $0.30      | $0.30       |
| Qwen25Coder32b | `qwen-2.5-coder-32b` | 128,000        | 16,384     | $0.30      | $0.30       |
| QwenQwq32b     | `qwen-qwq-32b`       | 128,000        | 16,384     | $0.30      | $0.30       |
| Qwen3_32b      | `qwen/qwen3-32b`     | 131,072        | 40,960     | $0.29      | $0.59       |

### Deepseek Models

| Name             | Model ID                        | Context Window | Max Output | Input $/1K | Output $/1K |
|------------------|---------------------------------|----------------|------------|------------|-------------|
| DeepseekQwen32b  | `deepseek-r1-distill-qwen-32b`  | 128,000        | 16,384     | $0.30      | $0.30       |
| DeepseekLlama70b | `deepseek-r1-distill-llama-70b` | 131,072        | 131,072    | $0.59      | $0.79       |

### Mistral Models

| Name           | Model ID           | Context Window | Max Output | Input $/1K | Output $/1K |
|----------------|--------------------|----------------|------------|------------|-------------|
| MistralSaba24b | `mistral-saba-24b` | 32,000         | 16,384     | $0.25      | $0.25       |

### Google Models

| Name      | Model ID       | Context Window | Max Output | Input $/1K | Output $/1K |
|-----------|----------------|----------------|------------|------------|-------------|
| Gemma2_9b | `gemma2-9b-it` | 8,192          | 8,192      | $0.20      | $0.20       |

### Other Models

| Name           | Model ID                           | Context Window | Max Output | Input $/1K | Output $/1K |
|----------------|------------------------------------|----------------|------------|------------|-------------|
| KimiK2Instruct | `moonshotai/kimi-k2-instruct-0905` | 262,144        | 16,384     | $1.00      | $3.00       |
| GptOss120b     | `openai/gpt-oss-120b`              | 131,072        | 65,536     | $0.15      | $0.60       |
| GptOss20b      | `openai/gpt-oss-20b`               | 131,072        | 65,536     | $0.075     | $0.30       |
| Allam2_7b      | `allam-2-7b`                       | 4,096          | 4,096      | $0.10      | $0.10       |

### Text-to-Speech Models

| Name            | Model ID            | Context Window | Max Output | Input $/1K | Output $/1K |
|-----------------|---------------------|----------------|------------|------------|-------------|
| PlayAiTts       | `playai-tts`        | 8,192          | 8,192      | $0.10      | $0.10       |
| PlayAiTtsArabic | `playai-tts-arabic` | 8,192          | 8,192      | $0.10      | $0.10       |

### Compound (Agentic) Models

| Name         | Model ID             | Context Window | Max Output | Input $/1K | Output $/1K |
|--------------|----------------------|----------------|------------|------------|-------------|
| Compound     | `groq/compound`      | 131,072        | 8,192      | $0.00      | $0.00       |
| CompoundMini | `groq/compound-mini` | 131,072        | 8,192      | $0.00      | $0.00       |

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

- **Speculative Decoding**: `Llama33_70bSpecDec` uses speculative decoding for faster inference but has a smaller
  context window (8,192) and higher output token cost compared to the versatile variant.
- **Compound Models**: `Compound` and `CompoundMini` are Groq's agentic compound models with zero listed token costs —
  pricing may be handled differently or bundled.
- **Guard Models**: Several safety-focused models (`LlamaGuard`, `LlamaPromptGuard`, `GptOssSafeguard`) are available
  for content moderation and prompt injection detection. The prompt guard models have very small context windows (512
  tokens).
- **Vision Support**: `Llama32_11bVision` and `Llama32_90bVision` support multimodal (image + text) inputs.
- **Largest Context**: `KimiK2Instruct` offers the largest context window at 262,144 tokens but is also the most
  expensive model in the catalog.
- **TTS Models**: `PlayAiTts` and `PlayAiTtsArabic` are text-to-speech models exposed through the chat model interface.