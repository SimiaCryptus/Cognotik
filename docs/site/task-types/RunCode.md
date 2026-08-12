# RunCode

**Execute code snippets in an interactive runtime, with optional auto-fix and interactive review.**

`Side-Effect Possible` · `Execution` · `Interactive / Auto-Fix Modes`

RunCode drives a `CodeAgent`/`CodingTask` loop that generates code in a configurable interpreter (Groovy by default),
executes it against the local workspace, and either presents the result for user approval/feedback or auto-applies
it depending on `OrchestrationConfig.autoFix`.

## Reality Check

**Input Configuration**

```json
{
  "task_type": "RunCode",
  "goal": "Read all CSV files in ./data and produce a summary report of row counts per file",
  "workingDir": "data",
  "task_description": "Aggregate row counts across CSV files",
  "task_dependencies": []
}
```

**Rendered Output**

The UI renders a `TabbedDisplay` with three tabs:

* **Code** — the generated source, fenced in the runtime's language (e.g. ```groovy).
* **Result** — the raw `resultValue` from execution, in a plain code block.
* **Output** — captured console/stdout output from the run.

Below the tabs, a transcript file (`transcriptFile()`) accumulates a running Markdown log of each execution attempt,
including request messages and results in collapsible `<details>` blocks. In interactive mode, the user sees
**Play**, **Continue**, and a feedback text box; in auto-fix mode, the first generated solution is executed
automatically and the task completes without user interaction.

## Documentation Tab

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `goal` | Optional | `String?` | The task or goal to be accomplished |
| `workingDir` | Optional | `String?` | The relative file path of the working directory |
| `task_description` | Optional | `String?` | A detailed description of the task's purpose |
| `task_dependencies` | Optional | `List<String>?` | List of task IDs that must complete before this task starts |
| `state` | Optional | `TaskState?` | The execution state/history of the task |

**Type-level configuration** (`RunCodeTaskTypeConfig`):

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `codeRuntime` | Optional | `CodeRuntimes?` | Which interpreter runtime to use (defaults to `GroovyRuntime`) |
| `model` | Optional | `ApiChatModel?` | Model used for code generation (defaults to `defaultSmart`) |

### Dependencies

RunCode has no hard dependency on other task types. It integrates with `CodingTask`/`CodeAgent` (from
`com.simiacryptus.cognotik.agents`) for code generation and `CodeRuntimes` for execution — these are supporting
infrastructure classes rather than orchestrated tasks.

### Token Usage

**Medium** — a single code-generation prompt plus iterative feedback rounds in interactive mode; auto-fix mode
limits itself to one execution attempt per invocation (`autoRunCounter`).

## Config & Process Tab

### Type Configuration (per task-type, shared across instances)

* `codeRuntime`: interpreter selection (Groovy, Kotlin, etc.) — chosen once for all `RunCode` instances of this type.
* `model`: the LLM used to generate code.

### Runtime Configuration (per task instance)

* `goal`, `workingDir`, `task_description`, `task_dependencies`, `state` — set per plan step.

### Lifecycle

1. **Initialization**
    * Resolves `typeConfig` and instantiates a child model client scoped to the task.
    * Opens a transcript file stream for logging.
    * Selects the runtime (`typeConfig.codeRuntime` or falls back to `GroovyRuntime`, noting Kotlin has issues
      running embedded in IntelliJ).
    * Builds a `CodingTask` (subclassing to override `displayFeedback` and `execute`) with working directory resolved
      from `OrchestrationConfig.absoluteWorkingDir`.

2. **Execution**
    * Sends the accumulated `messages` plus the `goal` to the coding agent via `codeRequest`.
    * On response, `displayFeedback` renders the Code/Result/Output tabs and writes to the transcript.
    * **Auto-fix path:** if `orchestrationConfig.autoFix` is true, the first response is executed immediately
      (guarded by `autoRunCounter` to prevent repeated auto-runs), and the task completes without blocking.
    * **Interactive path:** presents Play/Continue buttons and a feedback textbox; execution blocks on a `Semaphore`
      until the user clicks Continue or an auto-fix execution finishes.
    * `execute()` override captures the post-run result and output, releasing the semaphore in auto-fix mode.

3. **Error Handling**
    * The entire run is wrapped in try/catch; any `Throwable` triggers `task.error(e)`, a logged error, and an
      appended `## Error` section in the transcript with the stack trace, then the exception is rethrown.
    * `finally` always writes a "Task Completed" marker, flushes and closes the transcript, and calls
      `task.complete()`.

## Integration Tab

### Registering in OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings ...
).apply {
    taskSettings[RunCodeTask.RunCode.name] = RunCodeTask.RunCodeTaskTypeConfig(
        codeRuntime = CodeRuntimes.GroovyRuntime,
        model = ApiChatModel.GPT4o // example model
    )
}
```

### Prompt Segment (injected into planning LLM)

```
RunCode - Use a Groovy interpreter to solve and complete the user's request.
  * Useful for data processing, file system operations, or complex calculations.
  * Provide a clear 'goal' for the code to achieve.
  * The interpreter has access to the local workspace.
  * Results and console output will be returned to the context.
```