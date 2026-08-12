# Cognotik Core

*Turn free-form LLM output into safe, validated file changes — deterministically.*

## Overview

`com.cognotik:core` is the foundation library behind every code-editing capability in Cognotik. It takes the raw,
often sloppy text an LLM returns — markdown fences, half-formed diffs, whole-file rewrites — and turns it into
concrete, validated file mutations you can trust to write to disk.

No LLM client dependency, no cloud calls. Core operates purely on strings, so it works identically whether the model
response came from OpenAI, a local model, or a test fixture.

If you're building anything that lets an AI model edit code — an agent, an autonomous PR bot, an IDE plugin — this is
the layer that stands between "the model said so" and "the file actually changed."

## Key Features

* **Flexible response parsing** — extracts file-scoped code blocks and diffs from messy markdown, explicit
  `<<<FILE>>>`/`<<<DIFF>>>` markers, or bare fenced blocks, with smart filename detection and path normalization.
* **Multiple patch engines, one interface** — pick the strategy that matches your risk tolerance:
    * `Fuzzy` — the balanced default, tolerant of drifted context and re-indentation.
    * `Strict` — exact-context only, fails loudly instead of guessing.
    * `Lenient` — maximum tolerance for weaker models (pair with human review).
    * `Thermodynamic` — energy-based alignment for small, noisy hunks.
    * `Python` — indentation-preserving matching for Python/YAML.
    * `FullReplacement` — whole-file rewrites for small files or large edits.
    * `DataMerge` — structured deep-merge for JSON/YAML/XML/TOML/properties config.
* **Built-in grammar validation** — Kotlin syntax checking (ANTLR-based) and a lightweight bracket/quote balance
  checker for everything else, so a corrupting patch never gets silently accepted.
* **Safe-by-default results** — every applied patch is diffed against the *original* file's validation errors, so
  pre-existing breakage never blocks a good patch, and new breakage is always visible.
* **LLM-friendly JSON parsing** — a permissive Jackson configuration (`JsonUtil`) built for tolerating the quirks of
  model-generated JSON/YAML.
* **Trimmable dependency graph** — exclude Jackson dataformats or ANTLR if you only need the core parsing/patching
  path.

## Example

Pick an engine, tell the model how to format its answer, then apply the response — three lines of glue:

```kotlin
import com.simiacryptus.cognotik.diff.PatchProcessors

val processor = PatchProcessors.Fuzzy                     // 1. pick a dialect + engine
val prompt = processor.patchFormatPrompt                   // 2. describe it to the model

val result = processor.apply(originalCode, modelResponse, filename = "src/Main.kt")
if (result.isValid) {
    file.writeText(result.newCode)
} else {
    result.errors.forEach { logger.warn(it.message) }
}
```

Every result tells you exactly what happened:

```kotlin
when {
    result.newCode == originalCode -> logger.warn("Patch was a no-op")
    !result.isValid -> logger.warn("Rejected: ${result.errors}")
    else -> file.writeText(result.newCode)
}
```

## Integration & Dependencies

Core has no dependency on any LLM client — it's a pure text-processing library, which makes it the natural foundation
layer for other Cognotik modules that need to apply model output to real files (agents, coding assistants, CI
automation).

Runtime dependencies are intentionally scoped:

* **Required:** Kotlin stdlib, Commons Text (Levenshtein), SLF4J, Jackson (databind/kotlin/JSR-310), Guava,
  Commons IO, Kotlin Coroutines.
* **Optional, only if used:** Jackson dataformat modules (YAML/XML/TOML/properties) for `DataMergeProcessor`; ANTLR +
  the Cognotik `antlr` module for Kotlin grammar validation.
* **Bring your own:** an SLF4J binding (Logback is `compileOnly`).

```kotlin
dependencies {
    implementation("com.cognotik:core:<version>")
}
```

Need a minimal footprint? Exclude what you don't use:

```kotlin
implementation("com.cognotik:core:<version>") {
    exclude(group = "com.fasterxml.jackson.dataformat") // disables DataMergeProcessor
    exclude(group = "org.antlr")                        // disables KotlinGrammarValidator
}
```