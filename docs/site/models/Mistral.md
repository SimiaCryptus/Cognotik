# Mistral Models

Mistral AI provides a range of chat-capable large language models, from lightweight edge-optimized "Ministral" models to
frontier-class multimodal "Large"/"Medium" models, plus specialist code and reasoning models — all available through
Cognotik using your own Mistral API key (BYOK).

## Overview

Mistral models are integrated into Cognotik as text-based chat completion models, with several supporting vision (image)
input in addition to text. The catalog spans:

- **Featured Models** — the latest flagship general-purpose offering.
- **Frontier Generalist Models** — general chat/instruct models, including small (`Ministral`), medium, and large sizes,
  plus reasoning-focused "Magistral" models.
- **Frontier Specialist Models** — code-focused models (`Codestral`, `Devstral`, `Leanstral`).
- **Other Models** — creative writing and multilingual open-weight models.
- **Legacy / Deprecated Models** — older versions retained for backward compatibility.

## Available Models

| Model Name                    | Context Window | Capabilities            | Pricing (Input / Output per 1M tokens) |
|-------------------------------|----------------|-------------------------|----------------------------------------|
| `MistralMedium3_5`            | 131,072        | Text, Vision            | $1.50 / $4.50                          |
| `MistralSmall4`               | 131,072        | Text                    | $0.10 / $0.30                          |
| `MistralLarge3`               | 131,072        | Text, Vision            | $2.00 / $6.00                          |
| `MistralMedium3_1`            | 131,072        | Text, Vision            | $1.50 / $4.50                          |
| `MistralSmall3_2` (Legacy)    | 131,072        | Text                    | $0.10 / $0.30                          |
| `Ministral3_14B`              | 32,768         | Text, Vision            | $0.09 / $0.09                          |
| `Ministral3_8B`               | 32,768         | Text, Vision            | $0.09 / $0.09                          |
| `Ministral3_3B`               | 32,768         | Text, Vision            | $0.04 / $0.04                          |
| `MagistralMedium1_2`          | 131,072        | Text, Vision, Reasoning | $2.00 / $6.00                          |
| `MagistralSmall1_2`           | 131,072        | Text, Reasoning         | $0.10 / $0.30                          |
| `Leanstral`                   | 131,072        | Text, Code (Lean 4)     | $0.10 / $0.30                          |
| `Codestral`                   | 262,144        | Text, Code              | $0.30 / $0.90                          |
| `Devstral2`                   | 131,072        | Text, Code Agent        | $2.00 / $6.00                          |
| `DevstralSmall2`              | 131,072        | Text, Code Agent        | $0.10 / $0.30                          |
| `MistralSmallCreative`        | 131,072        | Text, Creative Writing  | $0.10 / $0.30                          |
| `MistralMedium3` (Legacy)     | 131,072        | Text, Vision            | $1.50 / $4.50                          |
| `DevstralMedium1_0` (Legacy)  | 131,072        | Text, Code Agent        | $2.00 / $6.00                          |
| `MistralNemo`                 | 131,071        | Text, Multilingual      | $0.15 / $0.15                          |
| `MagistralMedium1_1` (Legacy) | 131,072        | Text, Reasoning         | $2.00 / $6.00                          |
| `MagistralSmall1_1` (Legacy)  | 131,072        | Text, Reasoning         | $0.10 / $0.30                          |

> Note: `MistralSmall`, `MistralMedium`, `MistralLarge`, `MagistralMedium`, and `MagistralSmall` also exist as
> backward-compatible aliases pointing to the current recommended model in each family.

Pricing shown reflects the values defined in Cognotik at the time of writing; verify current rates with the provider
before relying on them for budgeting.

## Usage Example

```kotlin
val model = MistralModels.MistralMedium3_5 // modelId = "mistral-medium-2604"
```

Or by referencing the model id string directly in configuration:

```json
{
  "provider": "Mistral",
  "model": "mistral-medium-2604"
}
```

## Related Links

- [Mistral Models Overview](https://docs.mistral.ai/models/overview)
- [Mistral Pricing](https://mistral.ai/pricing)