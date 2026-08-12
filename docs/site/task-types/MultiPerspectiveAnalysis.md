# MultiPerspectiveAnalysis

**Analyze a subject from multiple independent viewpoints, then synthesize the results into a unified conclusion.**

`Side-Effect Safe` `Social` `LLM-Powered` `Multi-Agent`

MultiPerspectiveAnalysis fans out a single analysis subject to N independent `ChatAgent` calls — one per configured
perspective — then optionally runs a synthesis pass that reconciles agreements, conflicts, and consensus level
against a configurable threshold.

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "MultiPerspectiveAnalysis",
  "analysis_subject": "Should we migrate our REST API to GraphQL?",
  "perspectives": ["technical", "business", "user experience", "security"],
  "related_files": ["src/main/**/*.kt", "docs/api/**/*.md"],
  "synthesize": true,
  "consensus_threshold": 0.7,
  "task_dependencies": []
}
```

**Rendered Output (UI)**

A `TabbedDisplay` is created with one tab per perspective plus an `Overview` tab and (if `synthesize=true`) a
`Synthesis` tab:

- **Overview** — subject, perspective list, and a "🔄 Starting analysis..." status line rendered as Markdown.
- **technical / business / user experience / security** — each tab fills in as its `ChatAgent` call completes,
  showing a rendered Markdown analysis specific to that viewpoint.
- **Synthesis** — appears last, showing the reconciled conclusion, noted agreements/conflicts, and an assessment
  of consensus relative to `consensus_threshold`.

A detailed transcript file (`*.details.md`) captures every perspective's raw output plus the synthesis, and a final
transcript file (`*.md`) records just the synthesis section. The task's overall result string returned via
`resultFn` concatenates all perspective sections and (if enabled) the synthesis, in Markdown.

---

## Documentation

### Configuration Table

| Field Name             | Required/Optional | Type            | Description                                                                 |
|-------------------------|--------------------|------------------|-------------------------------------------------------------------------------|
| `analysis_subject`      | Required           | `String`         | The topic or problem to analyze from multiple viewpoints.                    |
| `perspectives`          | Required           | `List<String>`   | List of perspectives to consider (e.g., technical, business, ethical, user). |
| `related_files`         | Optional           | `List<String>`   | Files or glob patterns (e.g. `**/*.kt`) used as input context for analysis.   |
| `synthesize`            | Optional           | `Boolean`        | Whether to synthesize perspectives into a unified conclusion. Default `true`. |
| `consensus_threshold`   | Optional           | `Double`         | Minimum confidence threshold for perspective agreement (0.0–1.0). Default `0.7`. |
| `task_dependencies`     | Optional           | `List<String>`   | IDs of tasks that must complete before this one runs (inherited base field). |

Validation rules enforced in `validate()`:
- `analysis_subject` must not be null/blank.
- `perspectives` must not be null/empty.
- `consensus_threshold` must be in `[0.0, 1.0]`.
- `related_files`, if present, cannot contain blank entries.

### Dependencies

No hard dependency on other `TaskType`s is wired into this class — it consumes prior results generically via
`getPriorCode(agent.executionState)` and file context via `getInputFileContent(...)`, so it can follow any upstream
task in an orchestration graph, but does not require one.

### Token Usage Estimate

**High** — Token cost scales with `perspectives.size` (one full `ChatAgent` call per perspective, each including
full `contextFiles` and prior task results) plus one additional synthesis call that re-ingests all perspective
outputs verbatim. Large `related_files` globs or many perspectives will multiply cost significantly.

---

## Config & Process

### Type Configuration

- `TaskType` registration (`MultiPerspectiveAnalysis` companion object) is fixed: category `"Social"`,
  `taskClass = MultiPerspectiveAnalysisTask::class.java`, uses the shared `TaskTypeConfig` for task-type-level
  settings (no task-type-specific settings class).

### Runtime Configuration

- `MultiPerspectiveAnalysisTaskExecutionConfigData` — set per task instance: `analysis_subject`, `perspectives`,
  `related_files`, `synthesize`, `consensus_threshold`, `task_dependencies`, `state`.

### Lifecycle

1. **Initialization**
   - Task construction opens two output streams: a detailed transcript (`*.details.md`) and a final transcript
     (`*.md`).
   - Work is submitted to `task.ui.pool` asynchronously; the outer `run()` returns immediately after submission
     (wrapped in its own try/catch for pool-submission failures).

2. **Execution**
   - `config.validate()` is invoked; any validation failure throws `IllegalArgumentException` and is caught by the
     inner handler.
   - A smart model client is fetched via `defaultSmart.getChildClient(task)`.
   - Context is assembled once: `getInputFileContent(...)` (note: `related_files` is currently concatenated with
     itself in the source) and `getPriorCode(agent.executionState)`.
   - For each perspective (in order), a new `ChatAgent` is created with a fixed system prompt
     ("You are an expert analyst providing perspective-specific insights.") and a perspective-specific user prompt;
     the result is stored in `perspectiveResults` and written to the detailed transcript and its own UI tab.
   - If `synthesize == true`, a second `ChatAgent` call combines all perspective results into a synthesis, written
     to both transcript streams and its own UI tab.
   - The final result string is built by concatenating all perspective sections (and the synthesis, if generated)
     and passed to `resultFn`.

3. **Error Handling**
   - Any exception inside the pooled execution is caught, logged via SLF4J, reported to the UI via `task.error(e)`,
     written as a stack trace block to both transcript streams, and surfaced to the orchestrator via
     `resultFn("Error: ${e.message}")`.
   - A `finally` block guarantees both transcript streams are closed and `task.complete()` is called regardless of
     success or failure.
   - No retry or rollback logic is present — failures are terminal for this task instance.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings ...
    availableTaskTypes = listOf(
        MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis,
        // ... other task types ...
    )
)

val task = MultiPerspectiveAnalysisTask(
    orchestrationConfig,
    MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysisTaskExecutionConfigData(
        analysis_subject = "Should we migrate our REST API to GraphQL?",
        perspectives = listOf("technical", "business", "user experience", "security"),
        related_files = listOf("src/main/**/*.kt"),
        synthesize = true,
        consensus_threshold = 0.7
    )
)
```

### Prompt Segments (as injected into the LLM)

Per-perspective analysis prompt:

```text
You are analyzing the following subject from the **$perspective perspective**.

## Subject to Analyze:
$subject

## Context:
$contextFiles

## Previous Task Results:
$priorCode

## Instructions:
1. Analyze the subject specifically from the $perspective perspective
2. Identify key considerations, risks, and opportunities
3. Provide specific recommendations or insights
4. Rate your confidence in this analysis (0.0-1.0)

Provide a thorough analysis from the $perspective viewpoint.
```

System prompt for each perspective's `ChatAgent`:

```text
You are an expert analyst providing perspective-specific insights.
```

Synthesis prompt (only when `synthesize=true`):

```text
You are synthesizing multiple perspective analyses into a unified conclusion.

## Subject:
$subject

## Perspective Analyses:
$perspectiveResults (each rendered as "### $p Perspective:\n$a")

## Synthesis Instructions:
1. Identify common themes and agreements
2. Highlight conflicts or tensions
3. Assess overall consensus level (target threshold: $consensus_threshold)
4. Provide a balanced, unified recommendation

Provide a comprehensive synthesis.
```

System prompt for the synthesis `ChatAgent`:

```text
You are an expert at synthesizing multiple viewpoints into coherent conclusions.
```