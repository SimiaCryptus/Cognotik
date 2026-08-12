# FileModification

**Modify existing files or create new files with AI-assisted patch generation and diff review.**

`Category: File` · `Side-Effect Safe*` (manual mode) / `Destructive` (auto-apply) · `Chat Model Required`

*Side-effect safety depends on `autoFix` setting — auto-apply mode writes directly to disk without confirmation.

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "FileModification",
  "task_description": "Add null-check guards to the parseConfig function and extract the validation logic into a separate helper method.",
  "related_files": [
    "src/main/kotlin/com/example/config/ConfigParser.kt"
  ],
  "includeGitDiff": true,
  "task_dependencies": []
}
```

**Rendered Output (UI)**

The task streams a "Generating modifications..." status message, then renders the LLM's markdown response inline in
the session UI. Embedded diff/patch blocks are instrumented by `DiffInstrumentor`, which turns each proposed file
change into an interactive diff viewer with per-file apply links (`fileIndex/{session}/{path}`). If `autoFix` is
disabled, the task appends an "Accept" button footer that blocks (via semaphore) until the user approves; if enabled,
changes are applied immediately and the task auto-completes. A parallel system file transcript (`.transcript` file)
logs the full context, raw AI response, and a bullet list of "Modifications Applied" with links to each updated file.

---

## Documentation

### Configuration

| Field Name | Type | Required/Optional | Description |
|---|---|---|---|
| `main_file` / `related_files` | `String` / `List<String>` | Required (at least one) | File(s) to be examined/modified; validated so at least one of `main_file` or `related_files` is non-blank. |
| `modifications` | `Any?` | Optional | Specific modifications to be made to the files. |
| `includeGitDiff` | `Boolean` | Optional (default `false`) | Whether to include the `git diff HEAD` output for each input file as additional LLM context. |
| `task_description` | `String?` | Optional | Free-text description of the goal driving the modification/creation. |
| `task_dependencies` | `List<String>?` | Optional | Names of prerequisite tasks whose `main_file` outputs are pulled in as dependency context. |
| `state` | `TaskState?` | Optional | Inherited task lifecycle state. |

### Dependencies

- Consumes output of other `FileModificationTaskExecutionConfigData` tasks listed in `task_dependencies`, pulling
  their `main_file` content into the prompt as dependency context.
- Relies on `TaskOrchestrator.executionState.tasksByDescription` to resolve those dependencies at runtime.
- Uses `ChatAgent`, `PatchProcessor`, and `DiffInstrumentor` from the core text/patch subsystem for prompt
  construction and diff rendering — not other Task classes directly, but shared infrastructure services.

### Token Usage

**Medium–High** — the prompt includes full file contents for all related/main files (optionally with git diffs
appended), plus dependency file contents and the task description. Output volume scales with the number/size of
files modified, since the LLM must emit full patch/diff blocks for each change.

---

## Config & Process

### Type Configuration

- `model` — the chat model instance used to service the modification request (falls back to `defaultSmart` if unset).
- Standard `TaskTypeConfig` fields inherited from the task-type registration (name: `FileModification`, category: `File`).

### Runtime Configuration

- `related_files` / `main_file` — target file set.
- `modifications` — optional structured/free-form modification spec.
- `includeGitDiff` — toggles git-diff enrichment of context.
- `task_description`, `task_dependencies` — drive prompt content and dependency resolution.
- `orchestrationConfig.autoFix` — controls whether patches are applied automatically or require manual accept.
- `orchestrationConfig.temperature` — passed through to the `ChatAgent`.

### Lifecycle

1. **Initialization** — `validate()` on the execution config ensures at least one file path is provided in
   `main_file` or `related_files`. The task opens a system file transcript stream and resolves the default file
   target via `getDefaultFile()` (single-file shortcut logic based on distinct path counts).
2. **Execution** —
   - Builds dependency context by resolving `task_dependencies` against the orchestrator's task map and reading
     referenced files (from in-memory `codeFiles` cache or disk).
   - Builds file context via `getInputFileWithDiff()`, optionally appending `git diff HEAD` output per file.
   - Constructs a `ChatAgent` with a system prompt from `getSystemPrompt()` (patch-format instructions from the
     configured `PatchProcessor`).
   - Sends dependency context, file context, and task description to the LLM and captures the markdown response.
   - Renders the response through `DiffInstrumentor`, which parses embedded patches, resolves target paths via
     `resolveToRelativePath`/`prefilterFilename`, and either auto-applies (`shouldAutoApply = { autoFix }`) or waits
     for manual acceptance.
3. **Error Handling** — Any exception during context building, LLM invocation, or patch application is caught at
   the top level: logged via `task.error(e)` (UI) and `log.error(...)` (application log), written to the transcript
   as a fenced stack trace block, then rethrown. The transcript stream is always flushed in a `finally` block
   regardless of outcome. Retry is handled externally via the `Retryable` wrapper around the whole process closure,
   allowing the user to re-trigger generation without restarting the task.

---

## Integration

### Registering the Task

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ...other config...
)

val task = FileModificationTask(
    orchestrationConfig,
    FileModificationTask.FileModificationTaskExecutionConfigData(
        related_files = listOf("src/main/kotlin/com/example/config/ConfigParser.kt"),
        task_description = "Add null-check guards to parseConfig and extract validation logic.",
        includeGitDiff = true,
        task_dependencies = emptyList()
    )
)
```

### Prompt Segment (Planner-Facing)