---
transforms:
  - ../../../providers/src/main/kotlin/com/simiacryptus/cognotik/chat/model/(.*?)Models\.kt -> ../models/$1.md
  - ../../../providers/src/main/kotlin/com/simiacryptus/cognotik/providers/(.*?)Provider\.kt -> ../models/$1.md
---

# Task: Supported Model Provider Page

You are transforming a **model provider definition file** (e.g. `AnthropicModels.kt`, `OpenAIModels.kt`,
`GeminiModels.kt`) into a public "Supported Models" page for the Cognotik website. The audience is a prospective
user deciding whether their preferred AI provider/model is supported, and what it will cost them to use it through
Cognotik's Bring-Your-Own-Key (BYOK) model.

## Goal

Produce a page that answers:

1. **Which provider is this?** — H1 title using the provider's display name (e.g. "Anthropic Models").
2. **What can I use it for?** — a short intro naming the provider and the general capability class (chat, vision,
   audio/TTS, embeddings, etc.) as evidenced by the model list and any `TokenTypes` present in the file.
3. **Which models are available?** — a Markdown table enumerating every model constant defined in the file, with
   columns: `Model Name`, `Context Window` (if available), `Capabilities` (vision/tools/thinking/etc. if
   discoverable from flags), and `Pricing` (per-million-token input/output, and any `Cached`/`Thinking` token-type
   rates if defined).
4. **How do I use it?** — a short Kotlin/JSON snippet showing how to reference one of these models when configuring
   Cognotik (model id string as it would be passed to the platform).
5. **Where to learn more** — a "Related Links" list surfaced from the file's `related:` frontmatter (official pricing
   pages, provider model docs).

## Style Guide

* **Tone:** Factual and current — this page functions like a pricing/spec sheet, not hype copy. Prefer precise
  numbers over vague claims ("supports 200K token context" rather than "huge context window").
* **Format:** Valid Markdown:
    * H1 provider title, one-sentence subtitle.
    * `##` sections: Overview, Available Models, Pricing, Usage Example, Related Links.
    * Pricing/model tables must use Markdown table syntax with monospace model identifiers (backticks).
    * All monetary values expressed per 1M tokens unless the source uses a different unit — state the unit
      explicitly in a table footnote if it differs.
* **Freshness caveat:** Since pricing changes frequently, include a brief note such as: *"Pricing shown reflects the
  values defined in Cognotik at the time of writing; verify current rates with the provider before relying on
  them for budgeting."*

## Source-to-Output Mapping

| Source Signal (from `*Models.kt`)                                | Output Section         |
|------------------------------------------------------------------|-------------------------|
| Provider name (file/class name, package)                         | H1 Title                |
| Each model constant/object definition                            | Row in Available Models table |
| Context window / max token fields                                | Context Window column   |
| Pricing map entries (Input/Output/`TokenTypes.Cached`/`Thinking`) | Pricing table/column    |
| Capability flags (vision, tool-use, function-calling, etc.)      | Capabilities column     |
| `related:` frontmatter URLs                                      | Related Links section   |

## Constraints

* Never invent a model, price, or context window that is not present in the source file — if a value is missing,
  write `—` rather than guessing.
* Do not include internal-only or deprecated/experimental models unless they are clearly still exposed for use;
  if uncertain, list them but mark with a `Legacy`/`Preview` badge instead of omitting them silently.
* Keep provider pages structurally consistent with one another so the "Models" section of the site feels like a
  unified catalog rather than one-off pages.