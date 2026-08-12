# OpenAI Models

Chat models available through OpenAI, accessible in Cognotik using your own API key (BYOK).

> ⚠️ **Deprecated / Unverified**: This provider integration is marked as deprecated in Cognotik. The maintainer
> has lost API access to OpenAI, so model IDs and pricing below are sourced from public documentation only and
> have not been independently verified. Corrections are welcome via PR. Consider using Anthropic models instead.

## Overview

OpenAI provides text-based chat models (GPT-4, GPT-4.1, GPT-4.5, GPT-5.x, and the "O" reasoning-focused series).
All models listed below support text input and text output only, as defined in Cognotik.

## Available Models

| Model Name       | Context Window | Capabilities | Pricing (per 1M tokens, Input / Output) |
|------------------|-----------------|--------------|-------------------------------------------|
| `gpt-4-turbo`    | 128,000         | Text         | $10.00 / $30.00 |
| `gpt-4o`         | 128,000         | Text         | $2.50 / $10.00 |
| `gpt-4.5-preview-2025-02-27` | 128,000 | Text     | $75.00 / $150.00 |
| `gpt-4o-mini`    | 128,000         | Text         | $0.15 / $0.60 |
| `o1-preview`     | 131,072         | Text (reasoning) | $0.50 / $1.50 |
| `o1`             | 131,072         | Text (reasoning) | $15.00 / $60.00 |
| `o1-mini`        | 131,072         | Text (reasoning) | $1.10 / $4.40 |
| `o3-mini`        | 131,072         | Text (reasoning) | $1.10 / $4.40 |
| `o4-mini`        | 200,000         | Text (reasoning) | $1.10 / $4.40 |
| `o3`             | 200,000         | Text (reasoning) | — / $10.00 |
| `gpt-5.2`        | 128,000         | Text         | $1,750.00 / $14,000.00 |
| `gpt-5.2-pro`    | 128,000         | Text         | $21,000.00 / $168,000.00 |
| `gpt-5-mini`     | 128,000         | Text         | $250.00 / $2,000.00 |
| `gpt-5.4`        | 1,048,576 (max output 128,000) | Text | $2,500.00 / $15,000.00 |
| `gpt-5.4-mini`   | 400,000 (max output 128,000)   | Text | $750.00 / $4,500.00 |
| `gpt-5.4-nano`   | 400,000 (max output 128,000)   | Text | $200.00 / $1,250.00 |
| `gpt-4.1-2025-04-14`       | 1,048,576 | Text | $2,000.00 / $8,000.00 |
| `gpt-4.1-mini-2025-04-14`  | 1,048,576 | Text | $400.00 / $1,600.00 |
| `gpt-4.1-nano-2025-04-14`  | 1,048,576 | Text | $100.00 / $400.00 |

> **Note on GPT-5.2 / GPT-5.2 Pro pricing:** The source values defined in Cognotik for these models appear to be
> denominated per 1,000 tokens rather than per 1M tokens elsewhere in the file (e.g. `1.75 / 1000` scaled up to
> the per-1M convention used in this table gives the very high figures shown above). This discrepancy exists in
> the underlying definitions and has not been corrected here — verify actual OpenAI pricing before use.

> — indicates a value not defined in the source configuration (e.g. `o3` has no input token price defined).

Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates with OpenAI
before relying on them for budgeting.

## Usage Example

```json
{
  "provider": "OpenAI",
  "model": "gpt-4o-mini"
}
```

```kotlin
val model = OpenAIModels.GPT4oMini
```

## Related Links

- [OpenAI API Pricing](https://openai.com/api/pricing/)
- [OpenAI Models Documentation](https://platform.openai.com/docs/models)