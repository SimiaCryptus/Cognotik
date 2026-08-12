# DeepSeek Models

DeepSeek provides high-context chat models with reasoning ("thinking") support, accessible through Cognotik using your own DeepSeek API key.

## Overview

DeepSeek's model lineup available in Cognotik centers on large-context chat completion, including support for extended reasoning ("thinking") tokens and prompt caching for reduced input costs. The current generation is `deepseek-v4-flash` and `deepseek-v4-pro`, with `deepseek-chat` and `deepseek-reasoner` retained as deprecated aliases.

## Available Models

| Model Name | Context Window | Max Output Tokens | Capabilities | Status |
|---|---|---|---|---|
| `DeepSeekV4Flash` (`deepseek-v4-flash`) | 1,000,000 | 384,000 | Text, Reasoning/Thinking, Prompt Caching | Current |
| `DeepSeekV4Pro` (`deepseek-v4-pro`) | 1,000,000 | 384,000 | Text, Reasoning/Thinking, Prompt Caching | Current |
| `DeepSeekChat` (`deepseek-chat`) | 1,000,000 | 384,000 | Text | Legacy (removal scheduled 2026/07/24) |
| `DeepSeekReasoner` (`deepseek-reasoner`) | 1,000,000 | 384,000 | Text, Reasoning/Thinking | Legacy (removal scheduled 2026/07/24) |

## Pricing

Pricing values are per 1M tokens.

| Model Name | Input (Prompt) | Cached Input | Output (Completion) | Thinking |
|---|---|---|---|---|
| `deepseek-v4-flash` | $0.14 | $0.0028 | $0.28 | $0.28 |
| `deepseek-v4-pro` | $0.435 | $0.003625 | $0.87 | $0.87 |
| `deepseek-chat` | $0.14 | $0.0028 | $0.28 | — |
| `deepseek-reasoner` | $0.14 | $0.0028 | $0.28 | $0.28 |

> Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates with the provider before relying on them for budgeting.

## Usage Example

```kotlin
val model = DeepSeekModels.DeepSeekV4Flash

val chatClient = DeepSeekProvider().getChatClient(
    key = apiKey,
    workPool = workPool,
    logLevel = Level.INFO,
    logStreams = mutableListOf(),
    scheduledPool = scheduledPool,
    session = session,
)
```

Or by model id string:

```json
{
  "provider": "DeepSeek",
  "model": "deepseek-v4-flash"
}
```

## Related Links

- [DeepSeek Pricing](https://api-docs.deepseek.com/quick_start/pricing)
- [DeepSeek API Docs](https://api-docs.deepseek.com/)