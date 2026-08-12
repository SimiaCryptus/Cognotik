# AutoFix Task

**Execute build/test commands and iteratively patch code until they pass.**

`Side-Effect Safe` (with approval gate) `Destructive` (auto-fix mode) `No Vision Required`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "AutoFix",
  "task_description": "Run the test suite and fix any failures",
  "commands": [
    {
      "executable": "./gradlew",
      "arguments": ["test", "--continue"],
      "working_dir": "."
    }
  ],
  "task_dependencies": []
}
```

**Rendered Output**

The task renders a live transcript panel with two tabs:

- **Work Details** — a running markdown log: numbered list of commands to be executed with their resolved working
  directories, followed by streaming progress from the underlying `CmdPatchApp` patch-fix loop.
- **Final Output** — on completion, either:
    - `## Result: Success` with truncated command stdout (max 5KB, middle-truncated) and, if not in auto-fix mode, an
      **"Accept & Continue"** button; or
    - `## Result: Failed` with the exit code, truncated output, and an **"Ignore Error"** button to force completion.

If a run fails with an unhandled exception, a collapsible `<details>` block containing the full stack trace is
appended instead.

---

## Documentation

### Configuration

| Field Name         | Type                          | Required/Optional | Description |
|---------------------|-------------------------------|--------------------|--------------|
| `commands`          | `List<CommandWithWorkingDir>` | Optional           | The commands to execute, each with its own working directory. |
| `commands[].executable` | `String`                  | Required           | Executable to run, as a relative path or simple command name. Must not invoke a shell or use shell operators (`&&`, `\|`, `>`). |
| `commands[].arguments`  | `List<String>`            | Optional           | Arguments for the command. No shell quoting/expansion support. |
| `commands[].working_dir`| `String?`                 | Optional           | Working directory relative to the project root; `null` means project root. |
| `task_description`  | `String?`                     | Optional           | Description of what this task should accomplish. |
| `task_dependencies`  | `List<String>?`               | Optional           | IDs of tasks that must complete before this one runs. |

**Validation:** each `CommandWithWorkingDir` requires a non-blank `executable`; otherwise validation fails with
`"command must not be empty"`.

### Dependencies

- Delegates actual execution and patch-fix looping to **`CmdPatchApp`** (`PatchApp.Settings`), which handles running
  the process, capturing output, and — when `orchestrationConfig.autoFix` is enabled — invoking the model to patch
  files and retry.
- Resolves executables via `resolveTool(this.root)`, throwing `IllegalArgumentException` if a command alias can't be
  found.
- Uses `orchestrationConfig.workingDir` as a fallback working directory when a command doesn't specify one.

### Token Usage

**Medium** — the task itself emits no direct LLM prompt beyond configuration parsing; the bulk of token usage comes
from the delegated `CmdPatchApp` patch-generation loop, which sends file contents and command output to the model on
each fix iteration. Iteration count is bounded by the auto-fix retry logic in `CmdPatchApp`, not by `AutoFixTask`.

---

## Config & Process

### Type Configuration

`AutoFixTaskTypeConfig`:

- `model: ApiChatModel?` — optional override model; falls back to `defaultSmart` if unset.
- `promptTemplate: String` — the prompt segment describing the AutoFix capability and listing available executables
  (`{executables}` placeholder), injected into the orchestrator's planning prompt.

### Runtime Configuration

`AutoFixTaskExecutionConfigData`:

- `commands: MutableList<CommandWithWorkingDir>` — list of commands to run, each resolved against `agent.root`.
- `task_description`, `task_dependencies`, `state` — standard `TaskExecutionConfig` fields.

### Lifecycle

**Initialization**
- A new UI subtask (`SessionTask`) and transcript file are created.
- Model clients are resolved: `typeConfig.model` (or `defaultSmart`) for fixing, and `defaultFast` for auxiliary
  calls, both scoped via `getChildClient(subTask)`.
- Command list is rendered into the transcript, including resolved working directories.

**Execution**
- If `orchestrationConfig.autoFix` is true, execution starts immediately; otherwise a **"▶ Run AutoFix"** button gates
  execution behind user interaction.
- Each configured command is mapped into a `PatchApp.CommandSettings` (executable resolved via `resolveTool`,
  arguments joined, working directory created with `mkdirs()`).
- `CmdPatchApp.newSessionController(...).start()` runs the command(s), applying patches and retrying automatically
  when `autoFix` is enabled.
- On completion, exit code and truncated output are written to the transcript; success/failure branches render
  distinct UI affordances (auto-continue, "Accept & Continue", or "Ignore Error").

**Error Handling**
- Any uncaught `Throwable` during execution is logged via the "Triple Log Rule": `subTask.error(e)` (UI),
  `log.error(...)` (SLF4J), and a stack-trace block written to the transcript.
- `resultFn` is always invoked exactly once per run (success, failure-ignored, or exception) before releasing the
  `Semaphore` and calling `subTask.complete()` / closing the transcript stream — ensuring the orchestrator never
  blocks indefinitely on this task.

---

## Integration

### Registering the Task

```kotlin
val orchestrationConfig = OrchestrationConfig(
  // ...
  autoFix = true,
  workingDir = "modules/app",
  // task type registry typically includes AutoFixTask.AutoFix by default;
  // custom wiring example:
  taskTypes = listOf(
    AutoFixTask.AutoFix,
    // ...other task types
  )
)

val executionConfig = AutoFixTask.AutoFixTaskExecutionConfigData(
  commands = mutableListOf(
    AutoFixTask.CommandWithWorkingDir(
      executable = "./gradlew",
      arguments = mutableListOf("test", "--continue"),
      working_dir = "."
    )
  ),
  task_description = "Run the test suite and fix any failures"
)
```

### Prompt Segment

The following template is injected into the planning prompt (with `{executables}` populated with the available
tool list at runtime):

```text
SelfHealing - Run a command and automatically fix any issues that arise
  * Specify the commands to be executed along with their working directories
  * Each command's working directory should be specified relative to the root directory
  * Provide the commands and their arguments in the 'commands' field
  * Each command should be a list of strings
  * Available commands:
  {executables}
```