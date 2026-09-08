# DialecticalReasoning

**Category:** Reasoning &nbsp;|&nbsp; `Side-Effect Safe` &nbsp;`No Vision Required` &nbsp;`Multi-Stage LLM Pipeline`

Resolve contradictions between two opposing positions through a structured thesis → antithesis → contradictions → iterative synthesis → final integration pipeline.

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "DialecticalReasoning",
  "thesis": "Microservices architecture enables independent scaling and deployment of components",
  "antithesis": "Monolithic architecture reduces operational complexity and simplifies data consistency",
  "context": "Backend architecture decision for a mid-sized SaaS platform",
  "synthesis_levels": 3,
  "preserve_strengths": true,
  "related_files": ["docs/architecture/*.md"],
  "task_dependencies": []
}
```

**Rendered Output (UI)**

A tabbed display (`TabbedDisplay`) is created with one tab per pipeline stage:

* **Overview** — running summary header with context, synthesis level count, preserve-strengths flag, live progress checklist (✅ markers appended as each stage completes), and a final completion banner with total elapsed time.
* **Context** *(if related files/prior task output exist)* — expandable sections for "Prior Task Results," "Related Files," "Input Files."
* **Thesis** — statement + full LLM analysis (claims, strengths, coherence, limitations), rendered as Markdown.
* **Antithesis** — statement + full LLM analysis, including how it contradicts the thesis.
* **Contradictions** — exploration of direct contradictions, tensions, overlaps, and root causes.
* **Synthesis L1…Ln** — one tab per synthesis level, each showing the generated synthesis statement and its rationale.
* **Final Integration** — summary of the dialectical journey, practical implications, and recommendations.

All stage output is also streamed to a session transcript file (Markdown with `##` headers and completion timestamps), and a compact summary (thesis, antithesis, levels, truncated final synthesis) is returned via `resultFn`.

---

## Documentation Tab

### Configuration Fields

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `thesis` | Required | `String` | The thesis statement or position to analyze (max 5000 chars). |
| `antithesis` | Required | `String` | The antithesis statement or opposing position (max 5000 chars). Must differ from `thesis`. |
| `context` | Optional | `String` | Context or domain for the dialectical analysis (max 10000 chars; defaults to "general domain"). |
| `synthesis_levels` | Optional | `Int` | Number of synthesis iterations to run, coerced to range 1–5 (default 3). |
| `preserve_strengths` | Optional | `Boolean` | Whether synthesis prompts instruct the model to retain strengths of both sides (default `true`). |
| `related_files` | Optional | `List<String>` | File paths/glob patterns (e.g. `**/*.kt`) supplying additional context. |
| `task_dependencies` | Optional | `List<String>` | IDs of prerequisite tasks in the orchestration graph. |
| `state` | Optional | `TaskState` | Current task lifecycle state (default `Pending`). |

### Dependencies

No hard dependencies on other `Task` classes. The task pulls contextual input generically via `getPriorCode(agent.executionState)` (results from upstream tasks in the dependency graph) and `getRelatedFilesContent()` / `getInputFileCode()` (via `FileSelectionUtils` glob matching against `related_files`).

### Token Usage Estimate

**High** — the pipeline issues `4 + synthesis_levels` sequential LLM calls (thesis, antithesis, contradictions, N synthesis rounds, final integration), each with cumulative prior-stage context (analyses are truncated to ~1000 chars when reused, but the full pipeline can still consume substantial tokens at max `synthesis_levels = 5`).

---

## Config & Process Tab

### Type Configuration (`DialecticalReasoningTypeConfig`)

| Field | Default | Purpose |
|---|---|---|
| `thesis_analysis_prompt` | "You are analyzing a thesis statement..." | System framing for thesis analysis step |
| `antithesis_analysis_prompt` | "You are analyzing an antithesis statement..." | System framing for antithesis analysis step |
| `contradictions_prompt` | "You are exploring the contradictions and tensions..." | System framing for contradiction exploration |
| `synthesis_prompt` | "You are generating a dialectical synthesis..." | System framing for Level-1 synthesis |
| `integration_prompt` | "You are providing a final integration..." | System framing for final integration |
| `analysis_temperature` | `0.5` | Temperature for thesis/antithesis steps |
| `synthesis_temperature` | `0.7` | Temperature for synthesis steps |
| `contradictions_temperature` | `0.6` | Temperature for contradictions step |
| `integration_temperature` | `0.6` | Temperature for final integration step |

### Runtime Configuration

See the field table above (`thesis`, `antithesis`, `context`, `synthesis_levels`, `preserve_strengths`, `related_files`, `task_dependencies`, `state`) — set per invocation, not per task-type.

### Lifecycle Walkthrough

**Initialization**
* `validate()` on `DialecticalReasoningTaskExecutionConfigData` enforces: non-blank thesis/antithesis, thesis ≠ antithesis, length caps (5000/10000 chars), and clamps `synthesis_levels` into `[1, 5]`.
* A `TabbedDisplay` and transcript file (`transcriptFile()`) are created; execution is dispatched onto `task.pool`.

**Execution**
1. Re-checks that `thesis`/`antithesis` are non-blank at runtime (defensive re-validation); on failure writes a "CONFIGURATION ERROR" to both UI and transcript and returns early.
2. Assembles combined context from prior task output, related files, and input files.
3. Sequentially runs: Thesis Analysis → Antithesis Analysis → Contradictions & Tensions → Synthesis Level 1..N (each level built on the previous synthesis text) → Final Integration.
4. Each stage uses a dedicated `ChatAgent` with its own prompt and temperature, logs timing via SLF4J, writes results to the transcript, updates its own tab, and appends a checklist entry to the Overview tab.
5. On completion, writes a summary block to Overview/transcript and calls `task.complete()` plus `resultFn(...)` with a condensed Markdown summary.

**Error Handling**
* Follows the "Triple Log Rule": any exception is logged via SLF4J (`log.error`), surfaced to the UI (`task.error(e)` and an Overview error banner), and written to the transcript inside a collapsible `<details>` stack-trace block.
* The transcript stream is always closed in a `finally` block regardless of success/failure.
* No rollback is needed since the task performs no destructive file/state mutations — it is purely additive (transcript + UI + return string).

---

## Integration Tab

### Registering in an `OrchestrationConfig`

```kotlin
val executionConfig = DialecticalReasoningTask.DialecticalReasoningTaskExecutionConfigData(
  thesis = "Microservices enable independent scaling",
  antithesis = "Monoliths simplify operational complexity",
  context = "Backend architecture decision",
  synthesis_levels = 3,
  preserve_strengths = true,
  related_files = listOf("docs/architecture/*.md")
)

val task = DialecticalReasoningTask(
  orchestrationConfig = orchestrationConfig,
  planTask = executionConfig
)

// Register the task type so the orchestrator/UI can discover it
orchestrationConfig.registerTaskType(DialecticalReasoningTask.DialecticalReasoning)
```

### Prompt Segment (Planner-Facing)

This is the text injected into the orchestration planner's prompt so it knows when/how to use this task type: