---
transforms:
  - ../../../(tasklib/.*)/(.*?)Mode.kt -> ../cognitive-mode/$2.md
  - ../../../(stdtools/.*)/(.*?)Mode.kt -> ../cognitive-mode/$2.md
  - ../../../(webui/.*)/(.*?)Mode.kt -> ../cognitive-mode/$2.md
---

# Task: Cognitive Mode Feature Page

You are transforming a **Cognitive Mode** implementation (a `*Mode.kt` class) into a public-facing feature page for
the Cognotik website, explaining the mode to a non-code-reading visitor deciding which AI workflow suits their
problem.

## Goal

Produce a page that answers, in this order:

1. **What is this mode?** — a one-sentence definition in plain language.
2. **When should I use it?** — a "Best for..." callout describing the ideal use case(s).
3. **How does it work?** — a numbered, step-by-step breakdown of the mode's internal loop/algorithm, translated from
   code logic into human-readable process steps (e.g. "Nominate tasks → Vote → Execute").
4. **What do I see?** — describe the UI/UX artifacts the user will observe (transcripts, visual trees, voting
   results, thinking-status panels, etc.), inferred from the code's emitted events/UI hooks.
5. **Quick Reference** — a short comparison note situating this mode relative to sibling modes (e.g. "Unlike
   Waterfall Mode, X Mode adapts its plan after every step.").

## Style Guide

* **Tone & voice:** Match the style of `docs/cognitive/cognitive_modes.md` — friendly, direct, benefit-first
  language, structured with bold lead-ins ("**How it works:**", "**Best for:**", "**Key Feature:**").
* **Format:** Valid Markdown only.
    * H1: Mode display name (derived from the class name, humanized — e.g. `AdaptivePlanningMode` → "Adaptive
      Planning Mode").
    * Use a `**Best for:**` line immediately under the title.
    * Use `**How it works:**` followed by a numbered list for the algorithm/loop.
    * Use bullet lists for features, benefits, and comparisons.
* **No source code dumps.** Summarize logic conceptually; do not paste raw Kotlin. Small illustrative pseudocode is
  acceptable only if it clarifies a genuinely novel mechanic.

## Source-to-Output Mapping

| Source Signal (from `*Mode.kt`)                                | Output Section        |
|--------------------------------------------------------------------|------------------------|
| Class-level KDoc / comments describing purpose                     | Definition / Best For |
| Main loop structure (think/act/reflect, state machine, voting)     | How It Works           |
| Emitted UI events, tabs, status objects                            | What You See           |
| Config/parameters exposed (e.g. max parallel tasks, retries)       | Key Feature callouts   |
| References to other Mode classes or shared interfaces              | Quick Reference        |

## Constraints

* Infer user-visible behavior from code, but describe it functionally — never mention internal class/function names.
* If the mode's purpose is ambiguous from the code alone, cross-reference `docs/cognitive/cognitive_modes.md` for
  terminology and framing consistency, but do not simply copy its text — write a page specific to this mode's
  implementation.
* Keep the page skimmable: prefer bold lead-in phrases and short lists over long paragraphs.