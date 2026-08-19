# Cognotik / Sourcegraph Deep Search: Alignment Report

*Prepared for Andrew Charneski — July 2026*

---

## Executive Summary

Cognotik is a strongly-typed, extensible agentic AI platform whose core architectural patterns — iterative planning loops, self-describing tool plugins, structured-output agents, and session-based streaming UI — are a high-fidelity conceptual match for the way Sourcegraph Deep Search operates internally. The alignment is not superficial: the two systems solve the same hard problems (tool selection, reasoning state management, iterative evidence accumulation, multi-audience output) with nearly identical structural solutions, arrived at independently.

---

## 1. Iterative Agentic Loop

**Deep Search** runs a loop: select tools, invoke them, observe results, update reasoning state, repeat until confident.

**Cognotik** implements this identically in [`AdaptivePlanningMode.startAutoPlanChat`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/AdaptivePlanningMode.kt?L73-L272):

```kotlin
while (iteration++ < config.maxIterations && continueLoop) {
    val nextTask = getNextTask(userMessage, currentThinkingStatus, task)
    // ... execute tasks in parallel via pool.submit
    val updatedStatus = config.cognitiveStrategy.update(currentThinkingStatus, completedTasks, ...)
    reasoningState.set(updatedStatus)
}
```

Config parameters `maxIterations` and `maxTasksPerIteration` ([`AdaptivePlanningConfig`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/AdaptivePlanningMode.kt?L31-L37)) mirror Deep Search's iteration and parallelism controls exactly.

---

## 2. Self-Describing Tool Registry

**Deep Search** tools advertise their name, parameters, and purpose to the planning agent via JSON schemas.

**Cognotik** does the same via two mechanisms:

- [`AbstractTask.promptSegment()`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/AbstractTask.kt?L98) — every task produces a natural-language description of itself.
- [`TaskContextYamlDescriber`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/AdaptivePlanningMode.kt?L355-L360) — generates a typed schema from `@Description`-annotated Kotlin data classes, fed verbatim into the planner prompt.

The `getNextTask` method ([lines 301–421](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/AdaptivePlanningMode.kt?L301-L421)) constructs a prompt containing all registered task types' `promptSegment()` output and their full execution config schemas, then uses a `ParsedAgent<Tasks>` to have the LLM return a typed task selection — structurally identical to how Deep Search's planning agent chooses which tools to call.

---

## 3. Structured Reasoning State

**Deep Search** maintains an evolving research state: hypotheses, evidence, open questions, confidence.

**Cognotik** has [`AdaptivePlanningMode.ReasoningState`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/AdaptivePlanningMode.kt?L507-L526):

```kotlin
data class ReasoningState(
    var initialPrompt: String?,
    var confidence: Double?,
    var iteration: Int,
    val goals: Goals?,         // short-term + long-term, prioritized
    val knowledge: Knowledge?, // facts, hypotheses, openQuestions
    val executionContext: ExecutionContext?
)
```

The `knowledge.hypotheses` and `knowledge.openQuestions` fields ([lines 570–574](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/AdaptivePlanningMode.kt?L570-L574)) are structurally identical to what a Deep Search state would carry. The `confidence` field at line 512 is literally the same concept as Deep Search's confidence threshold for termination.

This state is managed by a pluggable [`CognitiveSchemaStrategy`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/CognitiveSchemaStrategy.kt) — an abstract class with `initialize`, `update`, `formatState`, and `getTaskSelectionGuidance` hooks. Five concrete strategies already exist (`ProjectManager`, `ScientificMethod`, `AgileDeveloper`, `CriticalAuditor`, `CreativeWriter`). A `ResearchInvestigator` strategy for code search would be a sixth.

---

## 4. Typed Structured-Output Agents

**Deep Search** uses typed tool schemas to ensure the planner returns parseable, validated tool calls.

**Cognotik** has [`ParsedAgent<T>`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/core/src/main/kotlin/com/simiacryptus/cognotik/agents/ParsedAgent.kt) — a generic agent that takes a result class, generates its schema via `TypeDescriber`, sends it to the LLM, and deserializes the output back to `T`. This is used for both task selection (`ParsedAgent<Tasks>`) and state initialization (`ParsedAgent<ReasoningState>`). The mechanism is functionally equivalent to function-calling / structured outputs in OpenAI terms.

---

## 5. Parallel Task Execution with Dependency Graphs

**Deep Search's** evaluator tool handles aggregation; individual tools run independently per iteration.

**Cognotik's** [`TaskOrchestrator.executePlan`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/TaskOrchestrator.kt?L55-L88) executes a full DAG of tasks respecting `task_dependencies`, renders a live Mermaid dependency graph into the UI, and tracks futures per task. Within `AdaptivePlanningMode`, tasks selected per iteration are submitted in parallel to `ui.pool` ([line 184](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/AdaptivePlanningMode.kt?L184)).

---

## 6. Multi-Audience Output Discipline

**Deep Search** separates output for the user (rendered UI), the reasoning agent (concise Markdown summary via `resultFn`), and the audit trail.

**Cognotik** formalizes this as the ["Four Audiences" IO discipline](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/docs/architecture/architecture_overview.md?L258-L271):

| Audience | Channel | Format |
|---|---|---|
| User | `task.ui` | HTML / Markdown |
| Auditor | `task.transcript()` | Permanent Markdown |
| Developer | SLF4J | Single-line text |
| LLM | `resultFn()` | Concise Markdown |

The `resultFn: (String) -> Unit` parameter in [`AbstractTask.run`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/AbstractTask.kt?L100-L106) is exactly the LLM-audience channel — returning a token-efficient summary of what the task did, back to the planner context. This is structurally identical to how Deep Search tools return results to the reasoning loop.

---

## 7. Plugin / Extension Architecture

**Deep Search** is a closed system. **Cognotik** is explicitly designed for extension.

New task types are registered via [`TaskType.registerTaskType`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/TaskType.kt?L24-L47) and discovered at runtime through `ServiceLoader`-based JAR plugins ([`PluginManager`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/webui/src/main/kotlin/com/simiacryptus/cognotik/platform/PluginManager.kt)). This means a `sourcegraph-tasks` plugin JAR could register all ten Deep Search tool analogs without touching the core platform — exactly the separation Sourcegraph would want from an integration partner.

The existing [`CrawlerAgentTask`](https://acharneski-fe.sourcegraph.app/r/github.com/SimiaCryptus/Cognotik/-/blob/tasklib/src/main/kotlin/com/simiacryptus/cognotik/crawl/CrawlerAgentTask.kt) demonstrates this pattern at production quality: it manages concurrent HTTP fetching, queue state, robots.txt respect, and pluggable processing strategies, all within the `AbstractTask` contract. It is the closest analog to what `KeywordSearchTask` or `CommitSearchTask` would look like.

---

## 8. Gap Analysis

| Dimension | Deep Search | Cognotik | Gap |
|---|---|---|---|
| Iterative agentic loop | Yes | Yes (`AdaptivePlanningMode`) | None |
| Self-describing tools | Yes (JSON schema) | Yes (`promptSegment` + `TypeDescriber`) | Negligible — different serialization format, same concept |
| Structured reasoning state | Yes | Yes (`ReasoningState` + `CognitiveSchemaStrategy`) | None |
| Parallel tool execution | Yes | Yes (`pool.submit` per iteration) | None |
| Multi-audience output | Yes | Yes (Four Audiences pattern) | None |
| Token budget management | Yes | Yes (`maxTaskHistoryChars`, `OrchestrationConfig.budget`) | Cognotik's budget tracking is less granular than per-tool token counting |
| Code-specific search primitives | Yes (10 built-in tools) | No (would need new `AbstractTask` subclasses) | **Primary implementation gap** — addressable in ~4 weeks |
| Lua evaluator sandbox | Yes | No | Significant — Cognotik has `CodeAgent` for Kotlin/Groovy execution but no sandboxed Lua |
| Answer synthesis / citation | Yes | Partial (`FileModificationTask` can write Markdown; no dedicated citation formatter) | Moderate — needs `AnswerSynthesisTask` |
| Human-in-the-loop approval | Optional | Yes (built-in `Discussable` / `Retryable` wrappers) | Cognotik is *more* capable here |
| Multi-model provider support | Single provider | 8+ providers (OpenAI, Anthropic, Gemini, AWS, Groq, Ollama…) | Cognotik exceeds Deep Search |

---

## 9. Alignment Score Summary

The architectural alignment is genuine and deep. The two systems share:

- The same **cognitive loop structure** (initialize state → select tasks → execute in parallel → update state → repeat)
- The same **tool abstraction contract** (self-describing, typed config, single `resultFn` output)
- The same **output discipline** (separate channels for user, LLM context, and audit)
- The same **extensibility philosophy** (runtime-registered, schema-driven tools)

The gaps are primarily at the **leaf level** — Cognotik lacks Sourcegraph-specific search primitives and a Lua sandbox. These are implementation work, not architectural rethinking. The planning engine, reasoning state machine, agent layer, and web platform are already a match.

From an evaluation standpoint, Cognotik demonstrates that the author understands and can independently re-derive the architecture of a production agentic research system. That is the meaningful signal.

