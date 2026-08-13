# Anthropic Models

Claude models from Anthropic, available through Cognotik via Bring-Your-Own-Key (BYOK) chat access.

## Overview

Anthropic's Claude family is exposed in Cognotik as text-in/text-out chat models (`ChatMessageModality.TEXT` for
both input and output). Newer "Sonnet 5", "Opus 4.6+" and "Fable/Mythos 5" models additionally support extended
**Thinking** (reasoning) tokens and disable sampling temperature control in favor of Anthropic's native reasoning
controls. Prompt caching is supported across the line via dedicated `Cached`, `CacheWrite5m`, and `CacheWrite1h`
token-pricing tiers.

## Available Models

| Model Name | Model ID | Context Window | Max Output | Capabilities | Status |
|---|---|---|---|---|---|
| `ClaudeHaiku3` | `claude-3-haiku-20240307` | 200,000 | 4,096 | Text | Legacy |
| `Claude35Haiku` | `claude-3-5-haiku-latest` | 200,000 | 8,192 | Text, Prompt Caching | Legacy |
| `Claude45Haiku` | `claude-haiku-4-5-20251001` | 200,000 | 64,000 | Text, Prompt Caching | Active |
| `Claude37Sonnet` | `claude-3-7-sonnet-20250219` | 200,000 | 64,000 | Text, Prompt Caching | Legacy |
| `Claude4Sonnet` | `claude-sonnet-4-20250514` | 200,000 | 64,000 | Text, Prompt Caching | Legacy |
| `Claude45Sonnet` | `claude-sonnet-4-5-20250620` | 200,000 | 64,000 | Text, Prompt Caching | Active |
| `Claude46Sonnet` | `claude-sonnet-4-6` | 1,000,000 | 64,000 | Text, Prompt Caching | Active |
| `Claude5Sonnet` | `claude-sonnet-5` | 1,000,000 | 64,000 | Text, Prompt Caching, Thinking | Active |
| `Claude4Opus` | `claude-opus-4-20250514` | 200,000 | 32,000 | Text, Prompt Caching | Legacy |
| `Claude41Opus` | `claude-opus-4-1-20250618` | 200,000 | 32,000 | Text, Prompt Caching | Legacy |
| `Claude45Opus` | `claude-opus-4-5` | 200,000 | 128,000 | Text, Prompt Caching | Legacy |
| `Claude46Opus` | `claude-opus-4-6` | 1,000,000 | 128,000 | Text, Prompt Caching, Thinking | Active |
| `Claude47Opus` | `claude-opus-4-7` | 1,000,000 | 128,000 | Text, Prompt Caching, Thinking | Active |
| `Claude48Opus` | `claude-opus-4-8` | 1,000,000 | 128,000 | Text, Prompt Caching, Thinking | Active |
| `Claude5Opus` | `claude-opus-5` | 1,000,000 | 128,000 | Text, Prompt Caching, Thinking | Active |
| `ClaudeFable5` | `claude-fable-5` | 1,000,000 | 128,000 | Text, Prompt Caching, Thinking | Legacy / Preview |
| `ClaudeMythos5` | `claude-mythos-5` | 1,000,000 | 128,000 | Text, Prompt Caching, Thinking | Legacy / Preview |

## Pricing

All prices are per 1M tokens, derived from Cognotik's configured per-1K rates.

| Model Name | Input | Output | Cache Write (5m) | Cache Write (1h) | Cached (read) | Thinking |
|---|---|---|---|---|---|---|
| `ClaudeHaiku3` | $0.25 | $1.25 | — | — | — | — |
| `Claude35Haiku` | $0.80 | $4.00 | $1.00 | $1.60 | $0.08 | — |
| `Claude45Haiku` | $1.00 | $5.00 | $1.25 | $2.00 | $0.10 | — |
| `Claude37Sonnet` | $3.00 | $15.00 | $3.75 | $6.00 | $0.30 | — |
| `Claude4Sonnet` | $3.00 | $15.00 | $3.75 | $6.00 | $0.30 | — |
| `Claude45Sonnet` | $3.00 | $15.00 | $3.75 | $6.00 | $0.30 | — |
| `Claude46Sonnet` | $3.00 | $15.00 | $3.75 | $6.00 | $0.30 | — |
| `Claude5Sonnet` | $2.00* | $10.00* | $2.50 | $4.00 | $0.20 | $10.00 |
| `Claude4Opus` | $15.00 | $75.00 | $18.75 | $30.00 | $1.50 | — |
| `Claude41Opus` | $15.00 | $75.00 | $18.75 | $30.00 | $1.50 | — |
| `Claude45Opus` | $5.00 | $25.00 | $6.25 | $10.00 | $0.50 | — |
| `Claude46Opus` | $5.00 | $25.00 | $6.25 | $10.00 | $0.50 | $25.00 |
| `Claude47Opus` | $5.00 | $25.00 | $6.25 | $10.00 | $0.50 | $25.00 |
| `Claude48Opus` | $5.00 | $25.00 | $6.25 | $10.00 | $0.50 | $25.00 |
| `Claude5Opus` | $5.00 | $25.00 | $6.25 | $10.00 | $0.50 | $25.00 |
| `ClaudeFable5` | $10.00 | $50.00 | $12.50 | $20.00 | $1.00 | $50.00 |
| `ClaudeMythos5` | $10.00 | $50.00 | $12.50 | $20.00 | $1.00 | $50.00 |

\* `Claude5Sonnet` introductory pricing ($2/$10 per 1M) is in effect through 2026-08-31; standard pricing
($3/$15 per 1M) applies afterwards, per Cognotik's model definition.

> Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates with the
> provider before relying on them for budgeting.

## Usage Example

```kotlin
import com.simiacryptus.cognotik.chat.model.AnthropicModels

val model = AnthropicModels.Claude45Sonnet
// model.modelId == "claude-sonnet-4-5-20250620"
```

```json
{
  "provider": "Anthropic",
  "model": "claude-sonnet-4-5-20250620"
}
```

## Related Links

- [Anthropic Model Overview](https://docs.anthropic.com/en/docs/about-claude/models)
- [Anthropic Pricing](https://www.anthropic.com/pricing)