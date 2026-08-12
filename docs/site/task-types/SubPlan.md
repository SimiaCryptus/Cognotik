# SubPlan

**Recursive planning task that spawns a nested cognitive-mode session to solve a sub-goal, then aggregates and summarizes its results.**

`Execution` · `Side-Effect Safe` (delegates side effects to child tasks) · `Recursive`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "SubPlan",
  "task_description": "Investigate and fix flaky integration tests in the auth module",
  "planning_goal": "Diagnose the root cause of intermittent failures in AuthIntegrationTest and propose a fix",
  "context": [
    "CI logs show failures ~15% of the time",
    "Tests were passing before the last dependency bump"
  ],
  "task_dependencies": ["Task_1_LogAnalysis"],
  "state": null
}
```

**Rendered Output (UI)**

The task panel renders progressively as markdown:

1. A header block: `# Sub-Planning Task` with **Goal**, **Cognitive Mode**, and optional **Purpose**, separated by an `---` rule.
2. If `orchestrationConfig.autoFix` is `false`, a **"▶ Run Sub-Plan"** button (`btn btn-primary`) is shown and execution pauses until clicked.
3. Once running, the nested cognitive-mode instance streams its own UI into the same task panel (sub-plan chatter, task cards, etc.), all mirrored into a transcript file.
4. On completion, a `# Sub-Planning Summary` (or `# Sub-Planning Results` if short) section appears, with the LLM-generated summary followed by a collapsible `<details>` block containing the full concatenated raw results.
5. If not `autoFix`, an "Accept" footer button appears before the result is passed back to the parent orchestrator.

---

## Documentation

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `planning_goal` | Optional* | `String?` | The goal or objective for the sub-planning task. |
| `context` | Optional | `List<String>?` | Context information to provide to the sub-planner. |
| `task_description` | Optional* | `String?` | Inherited from `TaskExecutionConfig`; used as fallback goal if `planning_goal` is blank. |
| `task_dependencies` | Optional | `List<String>?` | Inherited; IDs of tasks that must complete first. |
| `state` | Optional | `TaskState?` | Inherited; current lifecycle state of the task. |

\* At least one of `planning_goal` or `task_description` **must** be non-blank — enforced in `validate()`.

**Type Configuration fields** (`SubPlanTaskTypeConfig`, set at task-type registration, not per-invocation):

| Field Name | Type | Description |
|---|---|---|
| `cognitiveSettings` | `CognitiveModeConfig?` | Overrides the default cognitive strategy used for the sub-plan. |
| `taskSettings` | `MutableMap<String, TaskTypeConfig>` | Task-type configurations available within the sub-plan. |
| `purpose` | `String` | Supplemental description appended to the planning goal and shown in the UI. |
| `summaryPrompt` | `String` | Prompt template (with `{goal}` placeholder) used to summarize results. |

### Dependencies

- Instantiates a nested `CognitiveModeType.getImpl(...)` (from `com.simiacryptus.cognotik.plan.cognitive`) — effectively runs an entire independent `TaskOrchestrator`/cognitive session inside this task.
- Uses `ChatAgent` (`com.simiacryptus.cognotik.agents.ChatAgent`) to generate the final summary via `defaultSmart` or a configured model.
- Relies on `agent.executionState` (via `getPriorCode`) to inject prior task results as context.

### Token Usage Estimate

**High** — this task can transitively invoke an entire sub-plan (multiple LLM calls across nested tasks) plus an additional summarization call over potentially large aggregated results (only skipped if combined output < 5000 chars).

---

## Config & Process

### Type Configuration vs. Runtime Configuration

- **Type Configuration** (`SubPlanTaskTypeConfig`): `cognitiveSettings`, `taskSettings`, `purpose`, `summaryPrompt`, plus inherited `model`/`name`. Set once when registering the `SubPlan` task type in an `OrchestrationConfig`.
- **Runtime Configuration** (`SubPlanTaskExecutionConfigData`): `planning_goal` and `context`, supplied per task instance by the planning LLM (plus inherited `task_description`, `task_dependencies`, `state`).

### Lifecycle Walkthrough

**Initialization**
- Resolves `typeConfig`; throws if missing.
- Determines the effective `cognitiveMode`: type-level override, falling back to `orchestrationConfig.cognitiveSettings`; throws `IllegalArgumentException` if neither is set.
- Builds a `subConfig` — a copy of the parent `OrchestrationConfig` with `taskSettings` and `cognitiveSettings` swapped for the type config's values.
- Resolves the planning goal from `planning_goal` or `task_description` (throws if both blank — though `validate()` should already have caught this), and appends `purpose` if present.
- Instantiates and `initialize()`s the cognitive-mode implementation against the sub-config.
- Writes a transcript file and renders a "Sub-Planning Task" header with goal/mode/purpose.

**Execution**
- If `orchestrationConfig.autoFix` is true, execution runs immediately on the UI thread pool.
- Otherwise, a "Run Sub-Plan" button gates execution, and a `Semaphore` blocks the calling thread until the user clicks and the sub-plan completes.
- `runExecution()`:
  - Builds context messages from `execution.context`, prior task results (`getPriorCode`), and incoming `messages`.
  - Calls `cognitiveInstance.handleUserMessage(...)`, streaming into `task` and the transcript.
  - Retrieves `cognitiveInstance.contextData()` as the raw results list.
  - Calls `createSummary(...)`: returns results directly (formatted) if short (<5000 chars combined), otherwise invokes a `ChatAgent` built from `summaryPrompt` to summarize, wrapping full results in a collapsible `<details>` block.

**Error Handling**
- All paths (`autoFix` and interactive) wrap execution in try/catch.
- `handleError` calls `task.error(e)`, logs via SLF4J, writes the stack trace into the transcript under `## Error`, and reports the error message through `resultFn`.
- In the interactive branch, the semaphore is released on error to avoid deadlocking the calling thread.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
  // ... other settings ...
  taskSettings = mapOf(
    SubPlanTask.SubPlan.name to SubPlanTask.SubPlanTaskTypeConfig(
      cognitiveSettings = CognitiveModeConfig(type = SomeCognitiveModeType),
      taskSettings = mapOf(
        // task types available to the sub-plan
      ),
      purpose = "Handle deep investigative sub-goals that require their own planning loop",
      summaryPrompt = """
        Create a comprehensive summary of the sub-planning results below.

        Original Goal: {goal}

        - Highlight key findings and accomplishments
        - Identify any issues or blockers
        - Provide actionable next steps
      """.trimIndent()
    )
  )
)
```

### Prompt Segment (injected into planning LLM)

```text
SubPlanningTask - Create and execute sub-plans using recursive planning with configurable cognitive modes.
** Purpose: <typeConfig.purpose, if set>
** This SubPlanningTask can run the following tasks types: <comma-separated task_type list>
** Specify a planning goal or objective
** Optionally provide context information
** Can override the cognitive mode for the sub-plan
** Supports multiple levels of recursion up to configured depth
** Results are aggregated and optionally summarized
```