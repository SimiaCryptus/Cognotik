# SingleFix

**Analyze a log file and patch the errors it reveals — no command execution, one deterministic pass.**

`Side-Effect Safe` · `Execution Category` · `Patch-Based`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "SingleFix",
  "task_description": "Fix errors reported in the latest build log",
  "logFile": "build/logs/last-build.log",
  "task_dependencies": []
}
```

**Rendered Output**

The session UI shows a new sub-task block containing:

- A link: `Writing transcript to <a href='SingleFixTask_full_report_20240521120000.md'>...</a>`
- A `▶ Run SingleFix` button (only if `autoFix` is disabled in the `OrchestrationConfig`) — clicking it kicks off
  execution asynchronously.
- Once running: a live `PatchApp` session controller UI (tabbed display) driving the patch-generation workflow —
  diff previews, per-file patch application status, and any fuzzy-match resolution UI from `PatchProcessors.Fuzzy`.
- On completion: `### Success\nLog analysis and fix generation completed.`
- On failure: an inline error block with the exception name and full stack trace embedded in a fenced code block,
  plus the same error appended to the markdown transcript file.

---

## Documentation

### Configuration

| Field Name          | Required/Optional | Type           | Description                                                |
|----------------------|--------------------|----------------|--------------------------------------------------------------|
| `logFile`            | Required           | `String?`      | Path (relative to task root) to the log file containing errors to analyze and fix. |
| `task_description`   | Optional           | `String?`      | Inherited from `TaskExecutionConfig` — human-readable description of this task instance. |
| `task_dependencies`  | Optional           | `List<String>?`| Inherited from `TaskExecutionConfig` — IDs of tasks that must complete before this one runs. |
| `state`              | Optional           | `TaskState?`   | Inherited from `TaskExecutionConfig` — orchestrator-managed execution state. |

### Dependencies

- **`PatchApp`** (`com.simiacryptus.cognotik.autofix.PatchApp`) — SingleFixTask constructs an anonymous `PatchApp`
  subclass to drive the actual patch-generation/application session; this is the real workhorse.
- **`PatchProcessors.Fuzzy`** — default patch-matching processor used unless overridden via
  `orchestrationConfig.processor`.
- No explicit dependency on other `TaskType` orchestration steps is present in code, though `task_dependencies` can
  be used by the orchestrator to sequence this task after log-producing tasks (e.g. a build/test task).

### Token Usage Estimate

**Medium** — The task feeds the entire log file content plus a project file summary (file paths + byte sizes) into
the model as context for patch generation. For large logs or large codebases, this can grow, but there's no
iterative retry loop inflating the size (`OutputResult(1, ...)` is only used once per run to seed the fix logic).

---

## Config & Process

### Type Configuration (`SingleFixTaskTypeConfig`)

- `name`: defaults to `"SingleFix"`.
- `model`: optional `ApiChatModel` override; if unset, falls back to `defaultSmart` (via
  `typeConfig.model?.let { it.instance(...) } ?: defaultSmart`).

### Runtime Configuration (`SingleFixTaskExecutionConfigData`)

- `logFile`: required path to the error log.
- `task_description`, `task_dependencies`, `state`: standard orchestrator bookkeeping fields.
- Internally, a `Settings` object is built with a **dummy command** entry (`executable = File("dummy")`) purely to
  populate `workingDirectory` correctly for `PatchApp`, plus `autoFix` and `includeLineNumbers = false`.

### Lifecycle Walkthrough

1. **Initialization**
    - `validate()` checks `logFile` is non-blank; delegates remaining field validation to
      `ValidatedObject.validateFields(this)`.
    - A `Retryable` wrapper and a `Semaphore(0)` are set up so the outer `run()` call blocks until the async work
      completes (or fails permanently).
    - If `orchestrationConfig.autoFix` is `false`, execution is gated behind a manual "▶ Run SingleFix" button;
      otherwise it runs immediately.

2. **Execution**
    - A transcript file (`*_full_report_<timestamp>.md`) is opened for writing.
    - The child model client is resolved (`typeConfig.model` or `defaultSmart`), scoped via `getChildClient(subTask)`.
    - The log file is resolved relative to `agent.root` and existence-checked; throws `IllegalArgumentException` if
      missing.
    - Builds an anonymous `PatchApp` instance where:
        - `codeFiles()` walks the repo (`FileSelectionUtils.filteredWalk`), filtering files `< 512KB`.
        - `projectSummary()` lists relative paths + byte sizes for all existing code files.
        - `output()` **always returns exit code `1`** with the raw log file text — deliberately forcing `PatchApp`'s
          internal "fix" branch to trigger, since no command is actually executed.
        - `searchFiles()` does a case-insensitive substring search across the working directory for supplied terms.
    - `newSessionController(subTask).start()` launches the interactive patch session asynchronously; this task's
      `run()` does not itself iterate on patch results — the `PatchApp` controller owns that loop.
    - On successful kickoff, `resultFn` is immediately called with a success message, the semaphore is released, and
      `subTask.complete()` is invoked — note this reports success once the *session starts*, not once patches are
      confirmed applied.

3. **Error Handling**
    - Any `Throwable` during setup (missing log file, model resolution failure, etc.) is caught:
        - Logged via `subTask.error(e)` and SLF4J `log.error(...)`.
        - Stack trace appended to the markdown transcript.
        - The semaphore is only released (unblocking `run()`) **if `autoFix` is enabled** — otherwise the task
          remains pending until manual intervention/re-run.
    - The transcript `FileOutputStream` is always closed in a `finally` block.
    - The outer `run()` wraps `semaphore.acquire()` in its own try/catch, logging (but not rethrowing) any
      interruption, and unconditionally calls `task.complete()` at the end.

---

## Integration

### Registering in an `OrchestrationConfig`

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings ...
    autoFix = true,
    processor = PatchProcessors.Fuzzy,
    workingDir = "backend",
)

val planTask = SingleFixTask.SingleFixTaskExecutionConfigData(
    logFile = "build/logs/last-build.log",
    task_description = "Fix compile errors from latest CI run"
)

val task = SingleFixTask(orchestrationConfig, planTask)
```

### Prompt Segment

The fragment injected into the orchestrator's planning/execution prompt is generated directly from the config:

```
Analyze the log file '${executionConfig?.logFile}' and fix any errors found.
```

For the example configuration above, this resolves to:

```
Analyze the log file 'build/logs/last-build.log' and fix any errors found.
```

The deeper patch-generation prompt (project summary + log contents + code file listing) is constructed internally
by `PatchApp` itself, not by `SingleFixTask` — this task's own prompt surface is intentionally minimal, deferring
all patch-specific prompting to the shared `PatchApp` implementation.