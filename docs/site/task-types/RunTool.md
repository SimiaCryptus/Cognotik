# RunTool

**Execute external CLI tools with custom arguments — compilers, linters, search utilities, or arbitrary scripts, run under human or auto-approved control.**

`Execution` · `Side-Effect Safe (Gated)` · `Destructive (Auto-Fix Mode)`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "RunTool",
  "tool": "ripgrep",
  "args": ["-n", "TODO", "src/"],
  "workingDir": "project-root",
  "task_description": "Search the codebase for outstanding TODO markers",
  "task_dependencies": []
}
```

**Rendered Output (UI)**

The task renders a tabbed panel (`TabbedDisplay`) with up to three tabs:

- **Context** — collapsible `<details>` block showing prior code/context inherited from upstream tasks (only shown
  if non-blank).
- **Command** — a syntax-highlighted bash code block showing the fully resolved executable path and arguments, e.g.
  ```bash
  /abs/path/to/ripgrep -n TODO src/
  ```
- **Output** — appears only after execution. Shows a status line (`Executing process...` → `**Execution Complete**
  (Exit Code: 0)`), followed by a `#### Output` section with the raw stdout/stderr captured in a fenced code block.

If not running in auto-fix mode, an **"▶ Run Tool"** button is rendered above an "Approval Required" notice; clicking
it triggers execution and appends an **Accept** footer button to finalize the task. A full transcript (command,
output, and any error stack traces) is streamed to a companion transcript file.

---

## Documentation

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `tool` | Required | `String?` | The tool to run |
| `args` | Optional | `List<String>?` | The arguments to pass to the tool |
| `workingDir` | Optional | `String?` | The relative file path of the working directory |
| `task_description` | Optional | `String?` | A description of the task's purpose |
| `task_dependencies` | Optional | `List<String>?` | List of task IDs this task depends on |
| `state` | Optional | `TaskState?` | The current state of the task |

### Dependencies

No direct dependencies on other Task types are wired in code. `RunToolTask` does consult
`agent.executionState` via `getPriorCode(...)` to surface prior-task code as context, implying loose coupling
to whatever upstream tasks populate the shared execution state.

### Token Usage Estimate

**Low.** RunTool does not construct any LLM prompt for its own execution — it is a pure process-execution wrapper.
The `promptSegment()` only contributes a short static description to the planning model's context. Token cost is
essentially fixed and minimal; the bulk of "usage" is UI rendering of process output, not model tokens.

---

## Config & Process

### Type Configuration (`RunToolTaskTypeConfig`)

- `task_type: String` — defaults to `"RunTool"`.
- `model: ApiChatModel?` — optional model override (unused directly by this task's execution, since it performs no
  LLM calls itself).
- `name: String?` — defaults to `"RunTool"`.

### Runtime Configuration (`RunToolTaskExecutionConfigData`)

- `tool`, `args`, `workingDir` — define what gets executed and where.
- `task_description`, `task_dependencies`, `state` — inherited orchestration bookkeeping fields from
  `TaskExecutionConfig`.

### Lifecycle Walkthrough

**Initialization**
- A transcript file stream is opened via `task.newUserFileStream(transcriptFile())`.
- Prior code/context is pulled from `agent.executionState` and, if non-blank, rendered into a "Context" tab and
  logged to the transcript.
- The `tool` field is validated as non-null (`IllegalArgumentException` if missing); `args` defaults to an empty
  list if unset.
- `workingDir` resolves to the configured relative path or falls back to `orchestrationConfig.absoluteWorkingDir`
  (or `"."`).
- The tool name is resolved to an actual executable via `tool.resolveTool(...)`, searched relative to the data
  storage root or on the system `PATH`. Failure to resolve throws `IllegalArgumentException`.
- The full command (`executable + args`) is displayed in the "Command" tab and logged to the transcript before any
  execution occurs.

**Execution**
- Two paths depending on `orchestrationConfig.autoFix`:
  - **Auto-fix enabled:** the tool runs immediately via `ProcessBuilder`, with stdout/stderr merged
    (`redirectErrorStream(true)`), captured, and the task completes automatically.
  - **Auto-fix disabled (default):** an "Approval Required" prompt is shown with a "▶ Run Tool" button. Execution is
    deferred until the user clicks it. A `Semaphore` blocks the task thread until either execution completes and the
    user clicks "Accept," or an error occurs.
- Regardless of path, execution result is formatted as a markdown summary: `### Tool execution successful` or
  `### Tool execution failed (Exit Code: N)`, including the tool name and full output, and passed to `resultFn(...)`.

**Error Handling**
- Any exception during setup or execution is caught at the top level: the task UI displays the error
  (`task.error(e)`), it's logged via SLF4J, the stack trace is written to the transcript, and the exception is
  rethrown to propagate failure to the orchestrator.
- Errors inside the "Run Tool" button handler are caught separately and reported to the task UI/transcript without
  releasing the semaphore-based Accept flow (execution simply fails to reach the Accept button).
- The transcript stream is always closed in a `finally` block, regardless of success or failure.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings ...
    autoFix = false, // require manual approval before running tools
    absoluteWorkingDir = "/path/to/project/root"
)

val runToolConfig = RunToolTask.RunToolTaskExecutionConfigData(
    tool = "gradle",
    args = listOf("build", "--warning-mode=all"),
    workingDir = ".",
    task_description = "Build the project and surface any warnings"
)

val runToolTask = RunToolTask(orchestrationConfig, runToolConfig)
```

### Prompt Segment (Injected into Planning LLM)

```
RunTool - Execute external CLI tools with custom arguments.
* **Use when:** You need to run compilers, linters, search tools, or custom scripts.
* **Inputs:** Specify the `tool` name and a list of `args`.
```

This segment is contributed by `promptSegment()` and is used solely to inform the planning/orchestration model of
this task's availability and calling convention — RunTool itself makes no LLM calls during execution.