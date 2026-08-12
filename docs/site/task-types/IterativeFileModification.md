# IterativeFileModification

**Multi-phase file modification with planning and iterative implementation.**

`Destructive` · `File Category` · `Multi-Agent` · `Structured Output`

Analyzes a modification goal, decomposes it into a discrete ordered plan of changes, then implements each change iteratively — applying diffs to real files with optional per-change human approval gates.

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "IterativeFileModification",
  "task_description": "Refactor the UserService class to extract validation logic into a separate validator, add null-safety checks, and update all call sites.",
  "related_files": [
    "src/main/kotlin/com/example/UserService.kt",
    "src/main/kotlin/com/example/UserController.kt"
  ],
  "max_changes": 6,
  "approve_each_change": true,
  "task_dependencies": []
}
```

**Rendered Output (UI)**

The task renders as a tabbed panel (`TabbedDisplay`) with:

- **"Planning Phase" tab** — markdown summary of the modification plan: a numbered list of changes (`## 1. Extract validation logic`, `## 2. Add null-safety checks`, ...), each with target files and a truncated description.
- **One "Change N: <title>" tab per planned change** — the raw LLM implementation response rendered as markdown, with inline diff-application links (via `DiffInstrumentor`) that let the user click to apply a patch to a specific file, plus an "Accept" footer button if `approve_each_change` is true.
- **"Implementation Phase" tab** — a completion banner once all changes are processed.
- Final summary block listing modified files as `Change N - <file link> Updated` bullet points.
- A downloadable transcript file capturing every planning/implementation prompt and response in full detail.

---

## Documentation

### Configuration

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `task_description` | Required (inherited) | `String?` | The overall modification goal driving the planning phase. |
| `related_files` | Optional | `List<String>?` | Additional files to include as context; combined with `main_file` for validation. |
| `max_changes` | Optional (default `10`) | `Int` | Maximum number of change items the planning agent may generate. Must be between 1 and 50. |
| `approve_each_change` | Optional (default `false`) | `Boolean` | Whether to pause and require explicit user approval between each implemented change (skipped when `orchestrationConfig.autoFix` is true, and always skipped on the final change). |
| `task_dependencies` | Optional (inherited) | `List<String>?` | IDs of upstream tasks whose output feeds into this task's context (`getPriorCode`). |
| `main_file` | Inherited from `FileTaskExecutionConfig` | `String` | Primary target file; used as fallback target when a planned change specifies none. |

**Type Configuration** (`IterativeFileModificationTypeConfig`, set at orchestration level, not per-invocation):

| Field | Type | Description |
|---|---|---|
| `planningModel` | `ApiChatModel?` | Model used for the planning phase (falls back to `defaultSmart`). |
| `implementationModel` | `ApiChatModel?` | Model used for the implementation phase (falls back to `defaultSmart`). |
| `planningPrompt` | `String?` | Overrides the default planning system prompt. |
| `implementationPrompt` | `String?` | Overrides the default implementation system prompt. |

### Dependencies

- Extends `AbstractFileTask`, using shared file-context helpers (`getInputFileCode`, `getPriorCode`, `resolveToRelativePath`, `prefilterFilename`).
- Uses `ParsedAgent` (structured `ModificationPlan` parsing) for the planning phase and `ChatAgent` for the implementation phase.
- Integrates with `DiffInstrumentor` / `SessionRenderer` for interactive diff application — the same mechanism used by other patch-producing file tasks, implying consistency with tasks like `SingleFileModification` or similar diff-based tools.
- No hard task-type dependency, but `task_dependencies` can wire in upstream tasks' output via `getPriorCode`.

### Token Usage: **High**

Two-phase, multi-agent process: one structured planning call (which includes full file contents + dependency context), followed by *N* implementation calls (up to `max_changes`, each re-sending current file contents and prior-change summaries). Cost scales linearly with plan size and file size.

---

## Config & Process

### Type Configuration vs. Runtime Configuration

- **Type Configuration** (`IterativeFileModificationTypeConfig`) is set once per orchestration setup — model selection and prompt overrides for both phases.
- **Runtime Configuration** (`IterativeFileModificationTaskExecutionConfigData`) is set per task invocation — the goal, file scope, change cap, and approval mode.

### Lifecycle

1. **Initialization**
    - `validate()` checks that at least one file is referenced (`main_file` or `related_files`) and that `max_changes` is within `[1, 50]`.
    - A transcript file stream and `TabbedDisplay` are opened for structured output capture.

2. **Execution — Planning Phase**
    - Builds file context (`getInputFileCode`) and dependency context (`getPriorCode`).
    - Invokes a `ParsedAgent` with the planning prompt (custom or default) to produce a `ModificationPlan`.
    - Re-indexes returned changes sequentially and back-fills empty `targetFiles` with `main_file`.
    - Falls back to regex-based text parsing (`parsePlannedChanges`) if structured parsing yields no changes.
    - If the plan is empty, the task completes early with a "no changes identified" result.

3. **Execution — Implementation Phase**
    - Iterates over each `PlannedChange`, re-reading current file contents each time (accounting for prior iterations' edits).
    - Sends a `ChatAgent` request (implementation prompt + patch-format instructions) with change details, current file state, and previously implemented change summaries.
    - Renders the response through `DiffInstrumentor`, which offers click-to-apply diffs, auto-applies them if `orchestrationConfig.autoFix` is set, and records completion notes.
    - If `approve_each_change` is true and not on the last change and not in autoFix mode, blocks on a `Semaphore` until the user clicks "Accept".

4. **Error Handling**
    - The entire `run()` body is wrapped in try/catch: any `Throwable` is logged, reported via `task.error(e)`, written to the transcript as a fenced stack trace, then rethrown.
    - The transcript stream is always closed in a `finally` block, regardless of success or failure.
    - Individual transcript-write failures during diff application are caught and logged (`log.warn`) without aborting the task.

---

## Integration

### Registering the Task

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other config ...
    taskSettings = mapOf(
        IterativeFileModificationTask.IterativeFileModification.name to
            IterativeFileModificationTask.IterativeFileModificationTypeConfig(
                planningModel = ApiChatModel.GPT4O,
                implementationModel = ApiChatModel.GPT4O,
                planningPrompt = null,      // use default
                implementationPrompt = null // use default
            )
    )
)

val executionConfig = IterativeFileModificationTask.IterativeFileModificationTaskExecutionConfigData(
    related_files = listOf("src/main/kotlin/com/example/UserService.kt"),
    max_changes = 6,
    approve_each_change = true,
    task_description = "Extract validation logic and add null-safety checks",
    task_dependencies = emptyList()
)
```

### Prompt Segment (Planner-Facing Description)