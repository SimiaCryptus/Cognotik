---
transforms:
  - ../../../text/src/main/kotlin/com/simiacryptus/cognotik/text/patch/FullReplacementProcessor.kt -> ../patchers/FullReplacement.md
  - ../../../text/src/main/kotlin/com/simiacryptus/cognotik/text/patch/DataMergeProcessor.kt -> ../patchers/DataMerge.md
  - ../../../text/src/main/kotlin/com/simiacryptus/cognotik/text/patch/FuzzyPatchMatcher.kt -> ../patchers/FuzzyPatch.md
  - ../../../text/src/main/kotlin/com/simiacryptus/cognotik/text/patch/PythonPatcher.kt -> ../patchers/PythonPatcher.md
  - ../../../text/src/main/kotlin/com/simiacryptus/cognotik/text/patch/ThermodynamicPatchMatcher.kt -> ../patchers/ThermodynamicPatch.md
---

# Task: Patcher Algorithm Feature Page

You are transforming a **Patcher** implementation (a Kotlin class implementing Cognotik's `PatchProcessor`
interface) into a public-facing feature page for the "Patchers" section of the Cognotik website. Patchers are the
components responsible for turning an LLM's proposed code/text edits into a real, applied change — generating diffs
and/or applying them back to source files. The audience is a developer trying to understand *which patch strategy
Cognotik uses when*, and why that matters for reliability when an AI is editing their code.

## Goal

Produce a page that answers, in this order:

1. **What is this patcher?** — a one-sentence elevator pitch naming the strategy (e.g. "matches edits using
   Levenshtein-distance fuzzy line matching" or "replaces the entire file rather than diffing it").
2. **When should Cognotik use it?** — a `**Best for:**` callout describing the ideal scenario (s): file size, edit type
   (small tweak vs. full rewrite), language/format sensitivity, or robustness needs (e.g. tolerating whitespace drift in
   LLM output).
3. **How does it work?** — a numbered, step-by-step breakdown of the class's core algorithm as described in its
   KDoc/comments, translated into plain-language process steps (e.g. "Link exact matches → expand to neighbors →
   recursively match remaining blocks → detect moved lines → emit diff").
4. **Key Features** — a bullet list of the patcher's distinguishing capabilities and tunable behaviors (constructor
   parameters, thresholds, fallback strategies), described by effect rather than by parameter name alone (e.g.
   "Configurable fuzzy-match strictness via `levenshteinThresholdDivisor`" is fine to include, but explain what
   raising/lowering it does).
5. **Example** — a small, concrete before/after example: an original snippet, the patch/diff format this class produces
   or consumes, and the resulting output. Use the class's documented format (unified-diff-style `+`/`-`/`
   ` lines, full-file replacement, JSON merge, etc.) exactly as implemented — do not invent a different format.
6. **Quick Reference** — a short comparison note situating this patcher relative to the other patchers in this family
   (Full Replacement, Data Merge, Fuzzy Patch, Python Patcher, Thermodynamic Patch), e.g. "Unlike Fuzzy Patch, Full
   Replacement never attempts partial matching — it's the fallback when structural diffing isn't worth the risk."

## Style Guide

* **Tone & voice:** Confident and technical, matching `docs/cognitive/cognitive_modes.md` in structure — bold lead-ins (
  "**Best for:**", "**How it works:**", "**Key Feature:**") — but grounded in verifiable algorithmic detail rather than
  marketing language, since this audience cares about correctness guarantees.
* **Format:** Valid Markdown only.
    * A single `#` H1 title: the patcher's display name (derived from the class name, humanized — e.g.
      `FuzzyPatchMatcher` → "Fuzzy Patch Matcher").
    * A short italic tagline directly under the title.
    * A `**Best for:**` line near the top.
    * A `## How It Works` section with a numbered list.
    * A `## Key Features` section with bullet points.
    * A `## Example` section with fenced code blocks (use ` ```diff ` for unified-diff output, ` ```text ` or the
      appropriate language hint otherwise).
    * A `## Quick Reference` section comparing it to sibling patchers.
* **No raw source dumps.** Summarize algorithms conceptually from the KDoc and control flow; do not paste implementation
  code. Small illustrative pseudocode or diff snippets are fine when they clarify the patch format.

## Source-to-Output Mapping

| Source Signal (from `*.kt`)                                                                                 | Output Section              |
|-------------------------------------------------------------------------------------------------------------|-----------------------------|
| Class-level KDoc describing purpose/strategy                                                                | Title tagline / Best For    |
| `label` property                                                                                            | Title / tagline             |
| Documented algorithm steps in `generatePatch`/`applyPatch` KDoc                                             | How It Works                |
| Constructor parameters and their documented effects (thresholds, flags)                                     | Key Features                |
| Diff/patch format produced or expected (e.g. `+`/`-`/`  ` line prefixes, JSON shape, full-text replacement) | Example                     |
| Fallback logic, edge-case handling (blank source/patch, snippet mode)                                       | Key Features / How It Works |
| References to related patcher classes or interfaces                                                         | Quick Reference             |

## Constraints

* Never invent a diff/patch format, parameter, or fallback behavior that is not present or documented in the source
  file — if a detail is unclear from the code and its comments, omit it rather than guessing.
* Do not describe internal-only helper methods or private implementation details unless they materially affect a user's
  understanding of *what the patcher will and won't reliably do* to their code.
* Keep the page skimmable and short enough to read in under two minutes — this documents one strategy, not the whole
  patching subsystem.