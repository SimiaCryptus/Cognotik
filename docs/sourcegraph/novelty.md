Here is a structured inventory of the notable innovations in
the [Cognotik](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik) project, with dates derived
from the commit history. Note that the repo's visible history begins at **2025-04-22** (initial "wip" commits) with the
first tagged release `2.0.0` on **2025-04-27**, though several innovations originate from an earlier codebase
(`SkyeNet`) that was consolidated here.

---

## Notable Innovations in Cognotik

### 1. Typed Agent Abstraction (`BaseAgent<I,R>`, `ParsedAgent<T>`, `ProxyAgent<T>`)

**First seen in this repo:** 2025-04-27 (`2.0.0`)

The agent layer wraps all LLM interaction in strongly-typed generics. The standout inventions are:

- **[
  `ParsedAgent<T>`](r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ParsedAgent.kt)** —
  Forces the LLM to emit JSON that is automatically deserialized into a Kotlin/Java object of type `T`. Eliminates
  free-form text parsing.
- **[
  `ProxyAgent<T>`](r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ProxyAgent.kt)** —
  Uses `java.lang.reflect.Proxy` to implement any JVM interface via LLM at runtime. Methods are dispatched to the model
  with their signatures and return types automatically described and deserialized. Formally introduced in the current
  module form in **2026-07-05** (`2.1.17`), but conceptually present since initial commits.

---

### 2. `TypeDescriber` / Reflection-to-Schema System

**First seen in this repo:** 2025-04-27 (`2.0.0`)

[
`TypeDescriber`](r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/describe/TypeDescriber.kt)
uses JVM reflection to convert arbitrary Kotlin/Java class structures (including generics, polymorphism, and methods)
into token-efficient YAML/JSON/TypeScript schemas that LLMs can read. This is the bridge that enables `ParsedAgent` and
`ProxyAgent` to communicate structured types to models without hand-writing schemas. Multiple implementations exist
(`YamlDescriber`, `JsonDescriber`, `TypescriptDescriber`).

---

### 3. `DynamicEnum` — Runtime-Extensible Enum Pattern

**First seen in this repo:** 2025-04-22 (`wip`) → **2026-07-05** (moved to `core/` in `2.1.17`)

[
`DynamicEnum`](r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/util/DynamicEnum.kt)
is a registry-based alternative to Java enums with Jackson serialization support. Every major extension point (API
providers, cognitive modes, task types, code runtimes) is a `DynamicEnum`, meaning new strategies can be registered at
runtime without changing core code. This gives the system **multiplicative scaling**: adding one new provider or task
type automatically multiplies capability across all existing combinations.

---

### 4. `Discussable<T>` — Human-in-the-Loop Iterative Refinement

**First seen in this repo:** 2025-04-23 (`wip`), first named in `1.0.66` / `1.0.67`

[
`Discussable<T>`](r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/ui/Discussable.kt)
is a UI component that wraps an AI response in an interactive tabbed display. The user can accept, reject, or revise;
each revision cycle is appended as a new tab with a full conversation history. A semaphore gate optionally blocks the
pipeline until the user accepts. This makes human approval a first-class, composable primitive rather than an
afterthought.

---

### 5. Cognitive Modes — Pluggable Planning Strategies

**First seen in this repo:** 2025-04-22 (initial commits), substantially expanded in **2026-07-05** (`2.1.17`)

The [
`CognitiveMode`](r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveMode.kt)
abstraction defines swappable "thinking styles" for the agent. Notable modes, each registered as a `DynamicEnum`:

| Mode                      | Innovation                                                              | Status                       |
|---------------------------|-------------------------------------------------------------------------|------------------------------|
| **Waterfall**             | Full upfront JSON plan → sequential execution                           | Core                         |
| **Adaptive Planning**     | Think-Act-Reflect loop with evolving reasoning state                    | Core                         |
| **Hierarchical Planning** | Goal tree with dependency management and real-time visualization        | Core                         |
| **GoalOrientedMode**      | Goal-decomposition with sub-goal tracking                               | Core (refactored 2025-06-15) |
| **Council**               | Multi-persona voting on task selection to reduce hallucination          | Experimental (2026-07-05)    |
| **Protocol**              | State-machine with a separate "Referee" agent for pass/fail transitions | Experimental (2026-07-05)    |
| **Parallel**              | CrossJoin/Zip batch processing over variable lists                      | Experimental (2026-07-05)    |
| **Coding**                | REPL-style Groovy execution loop                                        | Core                         |

---

### 6. `FuzzyPatchMatcher` — Multi-Phase Fuzzy Code Patching

**Formally extracted as a standalone class:** 2026-07-05 (`2.1.17`)

[
`FuzzyPatchMatcher`](r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/diff/FuzzyPatchMatcher.kt)
is a novel algorithm for applying AI-generated code patches that may not exactly match the current source. Key
innovations:

- **Bidirectional linked-list line structure** for context-aware matching
- **Three-phase matching**: Unique-line anchoring → adjacent propagation → recursive subsequence linking
- **Snippet patching**: Handles AI outputs that provide updated code blocks *without* explicit `+`/`-` diff markers
- **Move detection**: Distinguishes relocated code from deletions
- **Adaptive Levenshtein thresholds** scaled by line length with structural type-checking (headers only match headers,
  etc.)

Pre-configured variants: `Strict`, `Lenient`, `Fuzzy`, `Python` (indentation-preserving), `FullReplacement`.

---

### 7. `ThermodynamicPatchMatcher` — Physics-Based Code Alignment

**Introduced:** 2026-07-05 (`2.1.17`)

[
`ThermodynamicPatchMatcher`](r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/diff/ThermodynamicPatchMatcher.kt)
models code patch alignment as a molecular DNA-binding problem. Lines are matched using binding energy (ΔG),
cooperativity bonuses for adjacent matches, entropy penalties for gaps, and a temperature parameter for stringency —
adapting Needleman-Wunsch dynamic programming to code patching.

---

### 8. Server-Driven UI with WebSocket Replay (`SessionTask` / `SocketManager`)

**First seen:** 2025-04-27 (`2.0.0`)

The [
`SessionTask`](r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/webui/session/SessionTask.kt)
system generates all UI server-side and pushes it to the browser over WebSockets. Each task is an addressable DOM block
that can be updated, tabbed (`TabbedDisplay`), or made interactive (buttons, file uploads, `Discussable`). The socket
manager replays the full UI history on reconnect via `lastMessageTime`, so page refreshes never lose state.

---

### 9. DocProcessor — Docs-as-Specification Pipeline

**Formally documented:** 2026-07-15 (`arch docs` commit)

The [DocProcessor](r/github.com/SimiaCryptus/Cognotik/-/blob/docs/architecture/architecture_overview.md?L303) is a
frontmatter-driven pipeline where documentation files carry YAML frontmatter specifying which source files they document
and which output files they generate. The AI uses this to keep docs and source code in sync bidirectionally —
documentation becomes a specification that can regenerate or validate its corresponding code.

---

### 10. Self-Describing Task Architecture

**Present since:** 2025-04-27 (`2.0.0`)

Each [`TaskType`](r/github.com/SimiaCryptus/Cognotik/-/blob/docs/taskplanning.md) advertises its own capabilities to the
planner via `promptSegment()` and `@Description` annotations on its config class, which are converted to JSON schema via
the `TypeDescriber`. The planner LLM reads these self-descriptions to select and configure tasks it has never explicitly
seen before — a form of self-documenting, discoverable tool registration.

---

### 11. Graph-Based Domain Models

**First seen:** 2025-02-08 (`graph models` commit) → removed/refactored in `2.0.0`

An early experiment (`DomainGraphModels`) used graph structures to represent infrastructure and software components,
enabling AI-assisted generation of structured system designs from natural language. The concept informed the later
`GraphOrderedPlanMode`.

---

### Summary Timeline

| Date       | Innovation                                                                                                                                                             |
|------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 2025-02-08 | Graph domain models (experimental)                                                                                                                                     |
| 2025-04-22 | `DynamicEnum` (initial), `Discussable` (initial)                                                                                                                       |
| 2025-04-27 | `2.0.0`: Core agent stack (`ParsedAgent`, `ProxyAgent`, `TypeDescriber`), `SessionTask` server-driven UI, Cognitive Modes framework, Self-describing task architecture |
| 2025-06-15 | `GoalOrientedMode` refactored; "narrations" / session transcript system expanded                                                                                       |
| 2026-02-08 | Graph models revisited                                                                                                                                                 |
| 2026-07-05 | `2.1.17`: `FuzzyPatchMatcher`, `ThermodynamicPatchMatcher`, `Council`/`Protocol`/`Parallel` modes, `ProxyAgent` & `DynamicEnum` moved to `core/`                       |
| 2026-07-15 | Architecture docs + `DocProcessor` pipeline formally specified                                                                                                         |

Now I have a complete picture. Here are the three missed innovations, properly documented:

---

## Additions to the Innovation Inventory

### A. `PromptOptimization` — Genetic Algorithm for Prompt Evolution

**Introduced:** 2026-07-05 (`2.1.17`)
**Source:** [`core/src/main/kotlin/com/simiacryptus/cognotik/embedding/PromptOptimization.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/embedding/PromptOptimization.kt)

This class applies a genetic algorithm directly to **system prompt text**, using LLMs as the mutation and crossover
operators. The loop:

1. **Seed population** — start with one or more candidate prompts
2. **Evaluate** — run each prompt against a `TestCase` suite; score responses by embedding-space distance to expected
   outputs using `Expectation.score()`
3. **Select** — keep the top `selectionSize ≈ ln(N)` survivors
4. **Regenerate** — fill population back to `populationSize` by either:
    - **Mutating** (single parent): randomly apply one of
      `{Rephrase, Randomize, Summarize, Expand, Reorder, Remove Duplicate}` via a `ProxyAgent<GeneticApi>`
    - **Recombining** (two parents): crossover two prompts via the same `GeneticApi`
5. Repeat for `N` generations

A notable detail: `compressibility(strA, strB)` uses GZIP compression ratio as a diversity metric to detect semantically
near-duplicate candidates.

---

### B. `GeneticOptimizationTask` — Full-Pipeline Genetic Optimization as a Planner Task

**Introduced:** 2026-07-05 (`2.1.17`)
**Source:** [`experiment/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/GeneticOptimizationTask.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/experiment/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/reasoning/GeneticOptimizationTask.kt)

A full `TaskType` that exposes genetic text evolution to the planner as a first-class reasoning step. Extends
`PromptOptimization`'s concept to arbitrary text (prompts, documentation, marketing copy, etc.) with:

- Configurable `mutation_strategies` (rephrase, simplify, elaborate, restructure)
- `enable_crossover` toggle
- Per-criterion `evaluation_weights` map (e.g., `{clarity: 0.4, conciseness: 0.3, impact: 0.3}`)
- `diversity_weight` that uses the GZIP compressibility metric to penalize homogeneous populations
- Fitness progression tracked and rendered across generations in the session UI

---

### C. `DecisionTreeTask` & `EntropyReductionTreeTask` — LLM-Driven Symbolic Decision Trees

**Introduced:** 2026-07-05 (`2.1.17`)
**Sources:**

- [`experiment/.../data/DecisionTreeTask.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/experiment/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/data/DecisionTreeTask.kt)
- [`experiment/.../data/EntropyReductionTreeTask.kt`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/experiment/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/data/EntropyReductionTreeTask.kt)

A novel hybrid: **the LLM proposes splitting rules; information gain validates them.** The standard decision-tree build
loop is intact (entropy, information gain, depth limit), but instead of a numerical search over feature thresholds, at
each node it:

1. Samples 10 records from the current partition and sends them to a `ChatAgent` with a domain-aware prompt
2. Gets back `N` candidate rules in the form `FIELD OPERATOR VALUE` (supporting `==`, `!=`, `>`, `<`, `contains`,
   `matches` regex)
3. Evaluates all candidate rules against the data and picks the one maximizing information gain
4. Recurses until max depth, purity > 95%, or n < 5

The result is both **a human-readable tree** and **auto-generated executable Kotlin code** for the classifier.

`EntropyReductionTreeTask` extends this to **unsupervised** partitioning — no single target column; the tree minimizes
aggregate entropy across all selected fields simultaneously to find natural data clusters.

---

### D. ANTLR-Based Auto-Validating Parser for AI-Generated Code

**Introduced:** 2026-07-05 (`2.1.17`) — `antlr/` module + [
`KotlinGrammarValidator`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/util/KotlinGrammarValidator.kt)
wired into `FileValidators`

The [`antlr/`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/tree/antlr) module packages a
full Kotlin ANTLR4 grammar ([
`KotlinLexer.g4`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/antlr/src/main/antlr/KotlinLexer.g4) + [
`KotlinParser.g4`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/antlr/src/main/antlr/KotlinParser.g4))
compiled and published as a library artifact. `KotlinGrammarValidator` uses the generated parser to **syntax-validate
every AI-generated code patch before it is applied** — new errors introduced by a patch are surfaced to the UI;
pre-existing errors in the original file are filtered out. This closes the loop on AI code generation: generate →
patch → parse → report errors → retry. The `IntelliJPsiValidator` provides the same contract inside the IDE plugin using
IntelliJ's own PSI engine.

---

### Updated Timeline Addendum

| Date                  | Innovation                                                                                                                                                                                                                                                                                                                |
|-----------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 2026-07-05 (`2.1.17`) | `PromptOptimization` genetic prompt evolution; `GeneticOptimizationTask` planner task; `DecisionTreeTask` (LLM rule proposal + info-gain validation + Kotlin codegen); `EntropyReductionTreeTask` (unsupervised entropy clustering); ANTLR Kotlin grammar module + `KotlinGrammarValidator` auto-validation of AI patches |

