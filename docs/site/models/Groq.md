---
title: Groq Models
related:
  - https://console.groq.com/docs/models
  - https://groq.com/pricing
---

# Groq Models

Groq provides ultra-low-latency inference for a range of open-weight chat models, safety/guard models, compound
agentic systems, transcription (Whisper), and text-to-speech (Orpheus) models.

## Overview

Groq hosts text chat models (including vision-capable variants), audio transcription models, text-to-speech
models, and "compound" agentic systems that can browse/execute tools. Capabilities vary by model — see the table
below for input/output modalities per model.

## Available Models

| Model Name | Context Window | Max Output Tokens | Capabilities | Pricing (Input / Output per 1M tokens) |
|---|---|---|---|---|
| `llama-3.1-8b-instant` | 131,072 | 131,072 | Text | $0.05 / $0.08 |
| `llama-3.3-70b-versatile` | 131,072 | 32,768 | Text | $0.59 / $0.79 |
| `openai/gpt-oss-120b` | 131,072 | 65,536 | Text, Vision (input) | $0.15 / $0.60 |
| `openai/gpt-oss-20b` | 131,072 | 65,536 | Text, Vision (input) | $0.075 / $0.30 |
| `whisper-large-v3` | — | — | Audio → Text (transcription) | $0.111 / $0.00 |
| `whisper-large-v3-turbo` | — | — | Audio → Text (transcription) | $0.04 / $0.00 |
| `groq/compound` | 131,072 | 8,192 | Text, Vision (input), Agentic/Compound | $0.00 / $0.00 |
| `groq/compound-mini` | 131,072 | 8,192 | Text, Vision (input), Agentic/Compound | $0.00 / $0.00 |
| `canopylabs/orpheus-arabic-saudi` (Preview) | 4,000 | 50,000 | Text → Audio (TTS) | $40.00 / $40.00 |
| `canopylabs/orpheus-v1-english` (Preview) | 4,000 | 50,000 | Text → Audio (TTS) | $22.00 / $22.00 |
| `meta-llama/llama-4-scout-17b-16e-instruct` (Preview) | 131,072 | 8,192 | Text, Vision (input) | $0.11 / $0.34 |
| `meta-llama/llama-prompt-guard-2-22m` (Preview) | 512 | 512 | Text (safety/guard) | $0.03 / $0.03 |
| `meta-llama/llama-prompt-guard-2-86m` (Preview) | 512 | 512 | Text (safety/guard) | $0.04 / $0.04 |
| `openai/gpt-oss-safeguard-20b` (Preview) | 131,072 | 65,536 | Text, Vision (input), Safety | $0.075 / $0.30 |
| `qwen/qwen3-32b` (Preview) | 131,072 | 40,960 | Text | $0.29 / $0.59 |

> Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates with Groq
> before relying on them for budgeting. Note: Orpheus TTS pricing units may differ from standard per-token text
> pricing (rate as defined in source); confirm against Groq's official pricing page.

## Usage Example

```kotlin
val model = GroqModels.Llama33_70bVersatile

// Or by model id string:
val modelId = "llama-3.3-70b-versatile"
```

```json
{
  "provider": "Groq",
  "model": "openai/gpt-oss-120b"
}
```

## Related Links

- [Groq Model Documentation](https://console.groq.com/docs/models)
- [Groq Pricing](https://groq.com/pricing)