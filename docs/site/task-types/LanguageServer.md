# LanguageServer

**Query code intelligence directly from real Language Server Protocol implementations — definitions, references, hover, and diagnostics, with zero mutation of source files.**

`Side-Effect Safe` · `File` Category · `Requires External LSP Binary`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "LanguageServer",
  "task_description": "Find where 'processRequest' is defined",
  "action": "definition",
  "file": "src/main/kotlin/com/example/RequestHandler.kt",
  "line": 42,
  "character": 17,
  "task_dependencies": []
}
```

**Rendered Output**

The UI panel shows a collapsible header `LSP Execution: definition` (level-3), with a live status line that updates
in place through the lifecycle: `Starting LSP for .kt...` → `Initializing Server...` → `Opening Document...` →
`Executing definition...` → `Shutting down...` → finally `<b>LSP Action Completed</b>`.

Below the status line, the final result block renders as plain text:

```
LSP Action 'definition' completed.
Result:
- file:///project/src/main/kotlin/com/example/RequestHandler.kt:39:4
```

A full JSON-RPC transcript (every `-->` request and `<--` response, plus notifications) is written to a
downloadable transcript file attached to the task, giving a complete audit trail of the LSP session.

If `orchestrationConfig.autoFix` is `false`, the panel instead shows a "Ready to run LSP action..." message with a
**Run LSP Action** button that must be clicked before execution proceeds (human-in-the-loop gate).

---

## Documentation

### Configuration Table

| Field Name | Type | Description |
|---|---|---|
| `action` (Optional) | `String?` | The LSP operation to perform. Supported: `diagnostics`, `definition`, `references`, `hover`. |
| `file` (Optional*) | `String?` | The relative path of the file to analyze. *Effectively required — task throws `IllegalArgumentException` if null. |
| `line` (Optional) | `Int?` | The line number (0-indexed) for position-based requests (`definition`, `hover`, `references`). Required when those actions are used. |
| `character` (Optional) | `Int?` | The character offset (0-indexed) for position-based requests. Required alongside `line` for position-based actions. |
| `task_description` (Optional) | `String?` | Inherited from `TaskExecutionConfig` — human-readable description of the task's purpose. |
| `task_dependencies` (Optional) | `List<String>?` | Inherited from `TaskExecutionConfig` — IDs of tasks that must complete first. |

### Dependencies

No hard-coded dependencies on other `Task` classes were found in the source. The task is self-contained and
communicates only with an external Language Server process spawned via `ProcessBuilder`. It can be used standalone
or downstream of file-editing tasks (e.g. to verify diagnostics after a `Patch`/`FileEdit`-style task runs), though
this wiring is not enforced in code.

### Token Usage Estimate

**Low.** This task does not invoke an LLM directly for its core logic — it drives an external LSP binary via
JSON-RPC over stdio. The `promptSegment()` contributes a short, fixed-size block to the orchestrator's system
prompt (a few lines listing supported actions/extensions), and the task's own `run()` does not call any chat model.
Token cost is effectively limited to whatever downstream task consumes the LSP result summary.

---

## Config & Process

### Type Configuration

`LanguageServerTaskTypeConfig` extends `TaskTypeConfig` with:
- `task_type` — defaults to `LanguageServer.name`
- `model` — optional `ApiChatModel`, unused by the task's own logic but available for orchestrator-level policies
- `name` — defaults to `task_type`

### Runtime Configuration

`LanguageServerTaskExecutionConfigData` extends `TaskExecutionConfig` and carries the actual per-invocation
parameters: `action`, `file`, `line`, `character`, plus inherited `task_description`, `task_dependencies`, and
`state`.

### Lifecycle Walkthrough

**Initialization**
- Resolves `filePath` and `action` from the execution config; throws `IllegalArgumentException` immediately if
  either is missing.
- Resolves the target `File` against `root` and verifies it exists.
- Looks up an LSP command line via `serverCommands[extension]`; throws if the extension is unsupported.

**Execution**
- Spawns the language server process (`ProcessBuilder(command).directory(root.toFile()).start()`), wrapping
  failures in a `RuntimeException` with a helpful "ensure it is installed and on your PATH" message.
- Wraps the process's stdin/stdout in a minimal internal `LspClient` (JSON-RPC over `Content-Length`-framed
  messages), streaming every request/response/notification to the transcript file.
- Performs the standard LSP handshake: `initialize` request → `textDocument/didOpen` notification.
- Dispatches on `action`:
  - `diagnostics` — sleeps 2s to let async `publishDiagnostics` notifications arrive, then returns a note pointing
    to the transcript (diagnostics are not deterministically captured in this one-shot client).
  - `definition` / `references` — validates `line`/`character` are present, sends the corresponding
    `textDocument/*` request, formats the returned location(s) as relative paths with `line:character`.
  - `hover` — validates position, sends `textDocument/hover`, returns the raw `contents` node as text.
  - Unknown action — throws `IllegalArgumentException`.
- Sends `shutdown` request and `exit` notification to cleanly terminate the server.
- If `orchestrationConfig.autoFix` is `false`, execution is gated behind a UI button click and a `Semaphore` blocks
  the calling thread until the user acts.

**Error Handling**
- All LSP protocol errors returned in a JSON-RPC `error` field are converted into a `RuntimeException`.
- Any exception during the LSP session is logged (`log.error`), written to the transcript, reported via
  `task.error(e)`, and rethrown.
- A `finally` block guarantees the spawned process is forcibly destroyed (`process.destroyForcibly()`) if still
  alive, preventing orphaned LSP processes even on failure.
- The outer `run()` catches any remaining exception, logs a warning, calls `task.error(e)`, and reports
  `"Error: ${e.message}"` via `resultFn` rather than propagating further, so a single failed LSP call does not crash
  the orchestrator.
- The transcript stream is always closed in an outer `finally`.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val orchestrationConfig = OrchestrationConfig(
    // ... other settings
    autoFix = true, // set false to require manual "Run LSP Action" confirmation
    availableTaskTypes = listOf(
        LanguageServerTask.LanguageServer,
        // ...other task types
    )
)
```

### Prompt Segment (injected into orchestrator LLM prompt)

```
LanguageServer - Query code intelligence (LSP)
  * Use to find definitions, references, or check for syntax errors (diagnostics).
  * Supported extensions: py, js, ts, kt, java, c, cpp, go, rs, sh, tex, yaml, dockerfile
  * Actions: 'diagnostics' (file-wide), 'definition' (specific pos), 'references' (specific pos), 'hover' (specific pos).
```

Note: the supported-extensions list is generated dynamically from `serverCommands.keys`, so it will always reflect
the current mapping in code (currently: `pylsp`, `typescript-language-server`, `kotlin-language-server`, `jdtls`,
`clangd`, `gopls`, `rust-analyzer`, `bash-language-server`, `texlab`, `yaml-language-server`,
`docker-langserver`).