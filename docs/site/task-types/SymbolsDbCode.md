# SymbolsDbCode

**Execute code snippets with predefined symbols, wired to a project-wide symbol graph.**

`Category: Execution` · `Side-Effect Safe*` · `Code Execution` · `No Vision Required`

> \*Safety depends on the underlying `codeRuntime` and the code the LLM chooses to execute; this task itself performs
> no destructive operations beyond what the generated code does.

`SymbolsDbCode` extends `RunCodeTask`, injecting a `symbols_db` object (a `SymbolGraphService`) into the execution
scope so generated code can query symbol relationships (definitions, dependencies) loaded from a JSON symbol graph
file on disk.

---

## Reality Check

**Input configuration** (`SymbolsDbCodeTaskExecutionConfigData`):

```json
{
  "task_type": "SymbolsDbCodeTask",
  "goal": "Find all callers of `UserService.authenticate` and summarize call sites",
  "workingDir": "src/main/kotlin",
  "task_description": "Use symbols_db to locate `authenticate` and list its dependents",
  "task_dependencies": [],
  "state": null
}
```

**Rendered output** (in the session UI):

* A header block: `### Initializing Symbols Database`
* An expandable `<details>` panel titled **Configuration Details** listing the symbol file path, runtime
  (e.g. `GroovyRuntime`), and working directory.
* Below that, the standard `RunCodeTask` flow renders: the generated code snippet, an execution approval prompt,
  then the captured stdout/result (often markdown-formatted symbol lists or dependency trees) written to a
  transcript file (`transcriptFile()`), with any error rendered as a `## Error` section containing a stack trace
  in a fenced code block.

---

## Documentation Tab

### Configuration Table

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `goal` | Optional | `String?` | The high-level goal or objective for the code execution. |
| `workingDir` | Optional | `String?` | The working directory where the code will be executed. |
| `task_description` | Optional | `String?` | A detailed description of the specific task to be performed. |
| `task_dependencies` | Optional | `List<String>?` | A list of task IDs that must be completed before this task starts. |
| `state` | Optional | `TaskState?` | The current execution state of the task. |

Type-level configuration (`SymbolsDbCodeTaskTypeConfig`):

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `codeRuntime` | Optional (default `GroovyRuntime`) | `CodeRuntimes?` | The code runtime to use for execution (e.g., Groovy, Kotlin). |
| `symbolFile` | Optional (default `"symbol_graph.json"`) | `String` | The relative path to the symbol graph JSON file. |
| `promptTemplate` | Optional | `String` | The prompt template used to describe the symbols database to the LLM. |

### Dependencies

* Extends `RunCodeTask` (execution, approval, and transcript machinery inherited from that base task).
* Uses `SymbolGraphService` (from `com.simiacryptus.cognotik.apps`) as the sole injected symbol.
* No direct orchestration wiring to other `TaskType`s is present in code; it is a leaf execution task.

### Token Usage

**Medium** — prompt includes the base `RunCodeTask` prompt segment plus a short symbols-db usage blurb; output
volume depends on how much symbol/dependency data the generated code prints, which can grow with large codebases.

---

## Config & Process Tab

### Type Configuration vs. Runtime Configuration

* **Type Configuration** (`SymbolsDbCodeTaskTypeConfig`) — set once per task-type instance: `codeRuntime`,
  `symbolFile`, `promptTemplate`. These control *how* the symbol graph is loaded and described to the LLM, not
  what the task should accomplish.
* **Runtime Configuration** (`SymbolsDbCodeTaskExecutionConfigData`) — set per invocation: `goal`, `workingDir`,
  `task_description`, `task_dependencies`, `state`. These define *what* this particular execution should do.

### Lifecycle Walkthrough

1. **Initialization:** `symbols()` resolves `typeConfig.symbolFile` relative to `root`, logs the resolved path, and
   loads it into a fresh `SymbolGraphService` via `.load(file)` only if the file exists on disk (silent no-op
   otherwise — no error thrown for a missing symbol file).
2. **Execution:** `run()` logs the goal, emits an "Initializing Symbols Database" markdown header to the UI,
   writes a `<details>` block with symbol file / runtime / working dir to the transcript, then delegates to
   `super.run(...)` (the `RunCodeTask` execution pipeline: prompt construction via `promptSegment()`, code
   generation, user-approved execution using injected `symbols_db`, and result capture).
3. **Error Handling:** The entire `run()` body is wrapped in try/catch. On exception, it calls `task.error(e)`
   (UI-visible error), logs via SLF4J, and appends a `## Error` section with the full stack trace to the
   transcript before rethrowing — no automatic retry or rollback is performed; the transcript stream is always
   closed in a `finally` block.

---

## Integration Tab

### Registering in an OrchestrationConfig

```kotlin
val config = OrchestrationConfig(
    // ... other settings
    taskSettings = mapOf(
        SymbolsDbCodeTask.SymbolsDbCode.name to SymbolsDbCodeTask.SymbolsDbCodeTaskTypeConfig(
            codeRuntime = CodeRuntimes.GroovyRuntime,
            symbolFile = "build/symbol_graph.json",
            promptTemplate = "You have access to `symbols_db` (SymbolGraphService) loaded from '{file}'."
        )
    )
)
```

### Prompt Segment (as constructed in code)

```text
${basePrompt from RunCodeTask}

### Symbols Database Access
* You have access to a `symbols_db` object (SymbolGraphService) loaded from the project symbol graph.
* Use `symbols_db.findSymbol("name")` to locate code elements.
* Use `symbols_db.getDependencies("name")` to analyze relationships.
```

If `promptTemplate` is customized, the `{file}` placeholder is substituted with `typeConfig.symbolFile` before
being spliced into the second bullet above.