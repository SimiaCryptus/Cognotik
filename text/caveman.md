# Project Specification: Caveman Prompting Module

## 1. Overview

### 1.1 Purpose

This specification defines a standalone, open-source, JVM/Kotlin-native software module that performs **deterministic prompt compression** — colloquially termed "caveman prompting." The module transforms arbitrary natural-language text into a semantically-dense, grammar-minimal, content-word-maximal representation suitable for use as a preprocessed prompt for large language models (LLMs).

The defining constraint of this project is that the entire transformation pipeline must be achievable **without neural networks**, relying instead on classical information retrieval (IR) techniques: tokenization, stemming/lemmatization, stopword elimination, part-of-speech filtering, keyword salience extraction, and rule-based grammar reconstruction.

### 1.2 Motivating Insight

Large language models internally perform operations that are functionally similar to classical IR preprocessing: they attend heavily to content-bearing tokens and are comparatively insensitive to function words, inflectional variance, and surface syntax. This suggests that a mechanical, IR-based transform — stripping stopwords, collapsing morphology, and preserving content words — can serve as an effective, reproducible "prompt canonicalizer."

Unlike neural paraphrasing or semantic compression, this transform is:

- **Deterministic** — identical input always yields identical output.
- **Auditable** — every transformation step is traceable and explainable.
- **Reproducible** — no model versioning, sampling, or drift concerns.
- **Lightweight** — no GPU, no model weights, no inference latency beyond simple text processing.

### 1.3 Non-Goals

The module explicitly does **not** attempt to provide:

- Semantic compression or simplification (e.g., "explain like I'm 5" rewriting).
- Word-sense disambiguation (e.g., distinguishing "bank" as financial institution vs. riverbank).
- Pragmatic intent classification (e.g., distinguishing "summarize" vs. "rewrite" vs. "analyze" requests).
- Multi-sentence discourse restructuring or coherence modeling.
- Domain-specific paraphrasing or style transfer.
- Any machine learning model requiring training, fine-tuning, or inference via neural architectures.

These are acknowledged limitations of the classical IR approach and are considered out of scope by design, not oversight.

---

## 2. Design Principles

1. **No Neural Networks.** All transformations must be implementable using rule-based, statistical, or classical linguistic-analysis techniques.
2. **Determinism.** Given the same input and configuration, the module must always produce the same output.
3. **Composability.** The transformation must be expressible as a pipeline of discrete, independently testable stages.
4. **Extensibility.** Users must be able to customize stopword lists, domain-term preservation dictionaries, grammar templates, and pipeline stage ordering without modifying core module code.
5. **Portability.** The module must be usable as a standalone library, independent of any specific host application, while remaining suitable for integration as a preprocessing layer within a larger system (e.g., a workspace or prompt-orchestration tool).
6. **Minimal Dependency Footprint.** Prefer well-established, actively maintained, open-source JVM libraries. Avoid unnecessary transitive dependencies.
7. **Language Extensibility.** While the initial specification targets English, the architecture must not preclude future support for additional languages via alternate analyzer chains.

---

## 3. Functional Requirements

### 3.1 Input

- The module accepts arbitrary free-form natural language text as input (single sentence, multi-sentence paragraph, or short document).
- Input is assumed to be plain text (no markup, though the design should tolerate incidental punctuation without failure).

### 3.2 Output

- The module produces a compressed textual representation consisting of:
    - A reduced set of content-bearing tokens (nouns, verbs, and other salience-marked terms).
    - Optionally, a minimal rule-based grammatical scaffold ("caveman grammar") wrapping the token set to preserve rudimentary intent signaling (e.g., request vs. question vs. imperative).

### 3.3 Core Transformation Stages

The transformation is defined as an ordered pipeline. Each stage is optional and independently configurable, but the following represents the canonical default ordering:

1. **Tokenization** — Split input text into discrete word tokens, handling standard punctuation, casing, and whitespace boundaries.
2. **Case Normalization** — Lowercase all tokens to eliminate case-based variance.
3. **Stopword Removal** — Eliminate function words (articles, prepositions, auxiliary verbs, conjunctions, etc.) using a configurable stopword list.
4. **Domain-Term Preservation Marking** — Identify tokens belonging to a user-supplied domain dictionary (e.g., technical acronyms, proper nouns, jargon) and mark them as exempt from subsequent stemming.
5. **Stemming / Lemmatization** — Reduce remaining (non-exempt) tokens to their morphological root or stem form, collapsing inflectional variants (plurals, verb tenses, etc.) into a single canonical form.
6. **Part-of-Speech Filtering (Optional)** — Restrict the retained token set to specific grammatical categories (typically nouns and verbs), discarding adjectives, adverbs, and other modifiers unless explicitly retained by configuration.
7. **Keyword Salience Extraction (Optional)** — Apply a statistical salience-ranking technique to further reduce the token set to only the highest-value keywords, particularly useful for longer or multi-sentence inputs.
8. **Grammar Reconstruction (Optional)** — Apply rule-based templates to wrap the resulting token sequence in a minimal grammatical scaffold reflecting the inferred intent of the original input (e.g., imperative request, question, description).

### 3.4 Domain-Term Preservation

The module must support a user-extensible dictionary of domain-specific terms (e.g., "CRDT," "HNSW," "Raft," "Paxos," "Kubernetes") that are:

- Never stemmed or altered morphologically.
- Never removed by stopword filtering, even if the term coincidentally matches a common word pattern.
- Case-preserved or case-normalized according to configuration.

This dictionary must be organized as a set of loadable, swappable term lists so that different subject-matter domains (distributed systems, robotics, physics, etc.) can be composed or layered.

### 3.5 Caveman Grammar Layer

The grammar reconstruction stage is a purely rule-based, non-statistical component responsible for:

- Detecting coarse input intent using shallow heuristics (e.g., presence of a question mark, presence of an initial interrogative word, presence of an imperative verb).
- Selecting an appropriate minimal grammatical template based on detected intent (e.g., "me want X," "explain X," "X cause problem why").
- Collapsing multi-sentence input into a single flattened token sequence prior to template application.
- Optionally reordering tokens by salience score (highest-salience terms first) when a salience-scoring stage has been applied upstream.

This layer must be fully rule-based and must not depend on any statistical or learned model.

---

## 4. Architectural Design

### 4.1 High-Level Structure

The module is organized into four conceptual layers:

1. **Analysis Layer** — Responsible for tokenization, normalization, stopword removal, stemming, and (optionally) POS filtering. This layer wraps classical IR analyzer chains.
2. **Salience Layer** — Responsible for optional statistical keyword extraction/ranking, used to further compress longer inputs.
3. **Grammar Layer** — Responsible for rule-based reconstruction of a minimal grammatical scaffold around the compressed token set.
4. **Orchestration Layer** — Responsible for composing the above layers into a configurable, ordered pipeline, exposing a single top-level entry point to consuming applications.

### 4.2 Component Responsibilities

- **Analyzer Component**: Encapsulates the token stream pipeline (tokenizer + filters). Must be swappable to support alternate languages or alternate underlying analysis libraries.
- **Stopword Provider**: Supplies a configurable, mergeable set of stopword lists (default English list plus optional user-supplied additions/removals).
- **Domain Term Registry**: Supplies a configurable, mergeable set of domain-preserved terms, associated with an exemption behavior for stemming and stopword filtering.
- **POS Filter Component**: Optional component that classifies tokens by part of speech and filters according to a configurable allow-list of grammatical categories.
- **Salience Extractor Component**: Optional component that scores and ranks tokens or token spans by statistical salience (e.g., frequency-based or graph-based ranking techniques), retaining only the top-N or above-threshold results.
- **Grammar Template Engine**: Rule-based component that classifies coarse input intent and selects/applies an appropriate output template.
- **Pipeline Orchestrator**: Public-facing entry point that accepts raw input text and a configuration object, invokes each enabled stage in order, and returns the final compressed output (and optionally, intermediate stage results for debugging/inspection).

### 4.3 Configurability

All stages must be independently toggleable via a configuration object, including:

- Enable/disable stopword removal, and specify custom stopword list(s).
- Enable/disable domain-term preservation, and specify custom domain dictionary/dictionaries.
- Enable/disable stemming, and select among alternate stemming algorithms (e.g., Porter-style vs. alternate morphological reduction) if multiple are supported.
- Enable/disable POS filtering, and specify the allowed grammatical category set.
- Enable/disable salience extraction, and specify the retention threshold or top-N cutoff.
- Enable/disable grammar reconstruction, and specify or extend the template set used for intent-based scaffolding.

### 4.4 Extensibility Points

The specification requires the following explicit extension points:

- **Custom Analyzer Chains** — Ability to substitute or extend the tokenization/normalization/stemming chain for support of additional languages or alternate morphological rules.
- **Custom Stopword Sets** — Ability to add, remove, or wholly replace the default stopword list.
- **Custom Domain Dictionaries** — Ability to layer multiple domain-term dictionaries (e.g., "distributed systems" + "robotics") simultaneously.
- **Custom POS Category Allow-Lists** — Ability to redefine which grammatical categories are retained.
- **Custom Salience Algorithms** — Ability to substitute alternate statistical ranking techniques for keyword extraction.
- **Custom Grammar Templates** — Ability to define new intent-classification rules and corresponding output scaffolds, including support for locale- or persona-specific "flavors" of the caveman grammar layer.

### 4.5 Multilingual Considerations

While the initial specification targets English-language input, the architecture must not hard-code English-specific assumptions into the orchestration or grammar layers. Language-specific behavior (tokenization rules, stopword lists, stemming algorithms) must be isolated entirely within the Analysis Layer's swappable analyzer chain, such that additional language support can be added by supplying an alternate analyzer configuration without modifying the Salience, Grammar, or Orchestration layers.

---

## 5. Data Flow Specification

1. **Input Reception**: Raw text string is received by the Pipeline Orchestrator along with a configuration object.
2. **Tokenization & Normalization**: Text is split into tokens; case is normalized.
3. **Stopword Filtering**: Function words are removed per the active stopword set(s).
4. **Domain-Term Marking**: Tokens matching the active domain dictionary are flagged as exempt from stemming.
5. **Stemming**: Non-exempt tokens are reduced to root/stem form.
6. **POS Filtering (if enabled)**: Tokens are classified by grammatical category and filtered to the allowed set.
7. **Salience Extraction (if enabled)**: Remaining tokens are scored and ranked; only top-N or above-threshold tokens are retained.
8. **Grammar Reconstruction (if enabled)**: The final token sequence is classified by coarse intent and wrapped in an appropriate rule-based template.
9. **Output**: The final compressed text (and optionally, a structured trace of intermediate stage outputs) is returned to the caller.

Each stage in this flow must be individually inspectable — the orchestrator should support returning intermediate results (post-tokenization, post-stopword-removal, post-stemming, etc.) for diagnostic and debugging purposes, in addition to the final compressed output.

---

## 6. External Dependencies (Conceptual)

The specification identifies the following categories of open-source, JVM-compatible dependencies as appropriate building blocks. Selection of specific library versions is an implementation concern outside the scope of this document; however, the following capabilities are required from the dependency set:

- **General-purpose IR analysis toolkit**: Provides tokenization, case normalization, stopword filtering, and stemming/lemmatization primitives, along with a mechanism for marking specific tokens as exempt from stemming (domain-term preservation).
- **Natural language POS tagging toolkit**: Provides part-of-speech classification for the optional POS Filter Component.
- **Keyword salience extraction toolkit**: Provides statistical keyword ranking/extraction for the optional Salience Extractor Component.
- **Multilingual analysis extensions (optional)**: Provides alternate tokenization/segmentation strategies for non-English input, should multilingual support be pursued.

No dependency on any neural network runtime, embedding model, or LLM inference library is permitted within the core module. The module must function entirely offline, with no network calls required for its core transformation logic.

---

## 7. Module Composition

The module should be organized as a small set of cleanly separated logical units, each with a single, well-defined responsibility:

- A unit responsible for the core analysis pipeline (tokenization, normalization, stopwording, stemming).
- A unit responsible for domain-term registry management.
- A unit responsible for stopword set management.
- A unit responsible for optional POS-based filtering.
- A unit responsible for optional salience-based keyword extraction.
- A unit responsible for rule-based grammar reconstruction.
- A unit responsible for overall pipeline orchestration and public API exposure.

This decomposition ensures each concern (linguistic analysis, domain knowledge, salience ranking, grammar templating, orchestration) can be developed, tested, and extended independently.

---

## 8. Quality Attributes

### 8.1 Determinism & Reproducibility

Given identical input text and identical configuration, the module must produce byte-identical output on every invocation, across platforms and JVM versions, with no reliance on random seeding, timestamp-based behavior, or external state.

### 8.2 Explainability

Every stage of the pipeline must produce output that can be independently inspected and attributed to a specific transformation rule (e.g., "this token was removed because it matched the stopword list," "this token was preserved because it matched the domain dictionary"). This traceability is a first-class design requirement, not an incidental debugging feature.

### 8.3 Performance Characteristics

The module is expected to operate at IR-preprocessing speeds (i.e., suitable for real-time or near-real-time invocation as a prompt preprocessing step), without introducing the latency profile associated with neural inference.

### 8.4 Portability

The module must have no hard dependency on any specific host application, UI framework, or orchestration system. It must be consumable as an independent library by any JVM-based or Kotlin-based application.

---

## 9. Validation Strategy (Specification Only)

The correctness of the module should be validated against the following categories of behavior, without prescribing specific test implementations:

1. **Stopword correctness** — Verify that known function words are consistently removed and known content words are consistently retained.
2. **Domain-term preservation correctness** — Verify that terms present in an active domain dictionary are never stemmed or removed, regardless of their surface similarity to stopwords or common morphological patterns.
3. **Stemming correctness** — Verify that known inflectional variants collapse to expected canonical stems.
4. **POS filtering correctness** — Verify that, when enabled, only tokens matching the configured grammatical category allow-list are retained.
5. **Salience extraction correctness** — Verify that, when enabled, retained tokens correspond to the highest-ranked terms under the configured scoring technique.
6. **Grammar reconstruction correctness** — Verify that coarse intent classification (question vs. imperative vs. descriptive) consistently selects the expected template, and that template application is deterministic.
7. **End-to-end determinism** — Verify that repeated invocations with identical input and configuration yield identical output across all pipeline configurations.
8. **Configuration isolation** — Verify that disabling any individual stage does not affect the correctness of other stages' behavior.

---

## 10. Known Limitations

This specification explicitly acknowledges the following inherent limitations of a classical-IR-only approach, which must be documented alongside the module for consuming developers:

- The module cannot perform true semantic compression; it can only reduce surface-level linguistic redundancy (function words, inflectional morphology).
- The module cannot disambiguate polysemous terms; a token's meaning is not resolved, only its surface form is normalized.
- The module cannot infer pragmatic intent beyond coarse heuristic classification (e.g., question vs. imperative); nuanced intent (e.g., "summarize" vs. "critique") is not distinguished without explicit rule authoring.
- The module's effectiveness is bounded by the quality and completeness of its configured stopword lists, domain dictionaries, and grammar templates; poor configuration will degrade output quality.
- Multi-sentence or long-document input compression may lose discourse-level coherence, since the design intentionally collapses structure in favor of token-level salience.

---

## 11. Future Extension Considerations

The following are noted as potential future directions consistent with the architecture, without committing to their inclusion in the current specification:

- Support for additional languages via alternate analyzer chains (e.g., CJK segmentation).
- Support for user-selectable "grammar flavors" (e.g., alternate rule-based scaffolds beyond the baseline "caveman" template, such as terse-imperative or telegraphic-English styles).
- Support for layered/composable domain dictionaries spanning multiple technical fields simultaneously.
- Support for alternate statistical salience-ranking techniques beyond the initial baseline, selectable via configuration.
- Integration guidance for use as a preprocessing layer within larger prompt-orchestration or workspace-based systems, while preserving the module's standalone, independently distributable nature.

---

## 12. Summary

This specification defines a deterministic, explainable, neural-network-free text compression module intended to transform natural-language input into a content-word-dense, minimally-scaffolded representation suitable for use as an LLM prompt. The design is organized around a configurable, staged pipeline — tokenization, stopword removal, domain-term preservation, stemming, optional part-of-speech filtering, optional salience-based keyword extraction, and rule-based grammar reconstruction — implemented atop classical, well-established open-source information retrieval components. The architecture prioritizes determinism, explainability, extensibility, and portability, while explicitly acknowledging the semantic and pragmatic limitations inherent to a purely classical (non-neural) approach.
