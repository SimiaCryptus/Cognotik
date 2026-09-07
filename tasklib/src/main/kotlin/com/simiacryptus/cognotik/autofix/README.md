# `com.simiacryptus.cognotik.autofix`

**Magic Code Fixer** — an agentic "run → observe → diagnose → patch → repeat" loop.

This package implements the *self‑healing build* capability of Cognotik. It runs one or more external commands (builds,
tests, linters, scripts), captures their output, uses an LLM to parse that output into structured errors, gathers the
relevant source files, asks an LLM to produce patches, and renders those patches into the web UI as apply‑able diffs —
optionally applying them automatically and re‑running the command until the build is green or the retry budget is
exhausted.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Package Contents](#package-contents)
3. [Architecture](#architecture)
4. [Execution Flow](#execution-flow)
5. [The Iteration Loop & `SessionController`](#the-iteration-loop--sessioncontroller)
6. [Data Model Reference](#data-model-reference)
7. [Task Types](#task-types)
8. [Configuration Reference](#configuration-reference)
9. [UI Anatomy](#ui-anatomy)
10. [Transcripts & Logging](#transcripts--logging)
11. [Extending `PatchApp`](#extending-patchapp)
12. [Threading & Concurrency](#threading--concurrency)
13. [Limits, Truncation & Guardrails](#limits-truncation--guardrails)
14. [Troubleshooting](#troubleshooting)
15. [Known Limitations / Roadmap](#known-limitations--roadmap)

---

## Quick Start

### As a plan task (`AutoFix`)

Emit a plan node of type `AutoFix` with a list of commands. Each command is an executable plus arguments plus an
optional working directory relative to the project root.

```json
{
  "task_type": "AutoFix",
  "task_description": "Build the project and fix any compilation errors",
  "task_dependencies": [
    "1"
  ],
  "commands": [
    {
      "executable": "gradlew",
      "arguments": [
        "compileKotlin",
        "--console=plain"
      ],
      "working_dir": null
    }
  ]
}
```

### As a plan task (`SingleFix`)

When you already have a captured build log and do **not** want to execute anything:

```json
{
  "task_type": "SingleFix",
  "task_description": "Fix the errors recorded in build.log",
  "logFile": "build/reports/build.log"
}
```

### Programmatically

```kotlin
CmdPatchApp(
  root = projectRoot,                       // java.nio.file.Path
  settings = PatchApp.Settings(
    commands = listOf(
      PatchApp.CommandSettings(
        executable = File("/usr/bin/make"),
        arguments = "-j8 all",
        workingDirectory = projectRoot.toFile(),
      )
    ),
    autoFix = true,
    maxRetries = 3,
  ),
  files = sourceFiles,                      // Array<File>? used to build the file index
  model = smartModel,
  fastModel = fastModel,
  processor = PatchProcessors.Fuzzy,
).newSessionController(
  task = sessionTask,
  onComplete = { result -> println("Exit code = ${result.exitCode}") }
).start()
```

---

## Package Contents

| File               | Type                                          | Responsibility                                                                                                                                                                                                               |
|--------------------|-----------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PatchApp.kt`      | `abstract class PatchApp : ApplicationServer` | The engine. Error parsing, file selection, code summarization, patch generation, fix history, and the `SessionController` iteration state machine.                                                                           |
| `CmdPatchApp.kt`   | `class CmdPatchApp : PatchApp`                | Concrete implementation that *executes external processes* and streams their output into the UI.                                                                                                                             |
| `AutoFixTask.kt`   | `class AutoFixTask : AbstractTask`            | Plan/orchestration binding for `CmdPatchApp`. Registers the `AutoFix` task type, builds `Settings` from plan JSON, writes a markdown transcript, and blocks the orchestrator until the user (or auto‑fix) resolves the task. |
| `SingleFixTask.kt` | `class SingleFixTask : AbstractTask`          | Plan/orchestration binding for a *log‑file‑only* `PatchApp`. Registers the `SingleFix` task type. Runs no processes.                                                                                                         |
| `README.md`        | docs                                          | This file.                                                                                                                                                                                                                   |

Also defined here:

* `fun File.findAbsolute(vararg files: File?): File` — resolve a possibly‑relative file against a list of candidate base
  directories, returning the first that exists (or `this` unchanged).
* `private fun String.truncateMiddle(maxLen: Int): String` (in `AutoFixTask.kt`) — head/tail truncation used when
  embedding command output into transcripts.

---

## Architecture

```text
┌──────────────────────────────────────────────────────────────────────┐
│ Plan Orchestrator (TaskOrchestrator)                                 │
└───────────────┬──────────────────────────────────┬───────────────────┘
                │                                  │
        AutoFixTask                          SingleFixTask
        (TaskType "AutoFix")                 (TaskType "SingleFix")
                │                                  │
                │ builds Settings                  │ builds Settings
                ▼                                  ▼
          CmdPatchApp  ─────────────►  PatchApp (abstract)  ◄───── anonymous
          (runs processes)             │                            subclass
                                       │                          (reads log)
                                       ▼
                              newSessionController(task)
                                       │
                                       ▼
                      ┌──────── SessionController ────────┐
                      │  state machine + control panel UI │
                      │  retries, history, summary        │
                      └───────────────┬───────────────────┘
                                      │  executeIteration(task, model, i)
                                      ▼
                ┌─────────────────────────────────────────────┐
                │ 1. output(...)         -> OutputResult      │  ← subclass hook
                │ 2. parse errors (LLM)  -> ParsedErrors      │
                │ 3. fixAllErrors(...)   -> per-error fibers  │
                │ 4. fix(...)            -> patch + history   │
                │ 5. DiffInstrumentor    -> apply-able diffs  │
                └─────────────────────────────────────────────┘
```

`PatchApp` extends `ApplicationServer` (`applicationName = "Magic Code Fixer"`, `path = "/fixCmd"`,
`showMenubar = false`) so that it can render its own session UI, but in the plan‑task flows it is used purely as a
controller attached to an existing `SessionTask`.

---

## Execution Flow

### 1. Task entry (`AutoFixTask.run`)

* Creates a `Semaphore(0)` — the orchestration thread blocks on `semaphore.acquire()` so the plan does not advance until
  the fix session terminates.
* Wraps the body in `Retryable`, so the whole block can be re‑run from the UI.
* Opens a markdown transcript via `subTask.newUserFileStream(transcriptFile())`.
* Resolves the smart model (`typeConfig.model ?: defaultSmart`) and a `fastModel` (`defaultFast`), both bound to the
  sub‑task via `getChildClient(subTask)` for per‑task token accounting.
* Maps each `CommandWithWorkingDir` to a `PatchApp.CommandSettings`:
* `executable` is resolved through `String.resolveTool(root)` — throws
  `IllegalArgumentException("Command not found: …")` if the tool is not on the resolved path.
* `arguments` are re‑joined into a single space‑delimited string.
* `working_dir` is resolved against `agent.root` and `mkdirs()`‑ed.
* Constructs `CmdPatchApp` and calls `.newSessionController(task, onComplete).start()`.
* If `orchestrationConfig.autoFix` is **false**, the whole execution is gated behind a
  `▶ Run AutoFix` button.

### 2. Command execution (`CmdPatchApp.output`)

For each `CommandSettings`, in order:

* Builds the OS command list, with special handling by extension:
* `*.ps1` → `powershell.exe -ExecutionPolicy Bypass -File <script> …args`
* `*.bat` / `*.cmd` → `cmd.exe /c <script> …args`
* otherwise → `[executable] + args`
* Inherits the full `System.getenv()` environment.
* Creates a **tab** in the surrounding `TabbedDisplay`, keyed by the full command string.
* Starts the process, prints working directory / command / models / start time, and adds a **Stop** link that calls
  `process.destroy()`.
* Spawns two daemon reader threads (`stdout`, `stderr`) that append into a shared `StringBuilder`
  and flush to the UI at most every 15 seconds.
* Polls `process.waitFor(15, SECONDS)` in a loop up to a **5 minute** wall‑clock timeout, logging PID / liveness / JVM
  heap each interval and updating the UI with a `[Process Status]` line.
* On timeout: destroys the process and throws `RuntimeException("Process timed out after 5 minutes")`.
* On non‑zero exit: **returns immediately** with `OutputResult(exitCode, cleanedOutput)` — later commands in the list
  are *not* run.
* On success of all commands: returns `OutputResult(0, "All commands completed successfully")`.
* Any throwable is reported to the task and converted to `OutputResult(1, "Error executing command: …")`.

Output is post‑processed by `outputString`: ANSI escape sequences (`\x1B[…`) are stripped and the text is truncated to ~
64 KB (head 32 KB + tail 32 KB) via `truncate`.

### 3. Error parsing (`PatchApp.parsedErrorsParsedResponse`)

A `ParsedAgent<ParsedErrors>` is invoked with:

* `model` = the smart model, `parsingModel` = the fast model.
* An **example instance**: on the first iteration a hand‑written canonical `ParsedErrors`; on later iterations
  `recentErrors()` — the highest‑severity instance of each previously seen message. This biases the parser toward a
  stable, comparable error taxonomy across iterations.
* A prompt containing the project root, `projectSummary()` (path + byte size of every code file), and instructions to
  produce, per error: fix files, related files, search queries, code locations, severity/complexity 1‑10, and a
  distilled detail summary.
* The user message is `promptPrefix` + the fenced command output.

If `OutputResult.errors` was already populated by the subclass, parsing is skipped entirely and the pre‑parsed value is
wrapped in a synthetic `ParsedResponse`.

### 4. Fix generation (`fixAllErrors` → `fix`)

* Warnings are dropped when at least one real error exists and `settings.ignoreWarnings` is true.
* Errors are grouped by `message`; each group is submitted to `task.pool` and processed **in parallel**, then joined
  via `.onEach { it.get() }`.
* Each group gets a `linkedTask` sub‑session with a live `Status: …` buffer.
* `ResearchNotes.searchQueries` are executed as a filtered walk + glob match + case‑insensitive substring match (files <
  1 MB only).
* `fix(...)` then:

1. Collects candidate paths from `fixFiles ∪ relatedFiles ∪ locations[].file ∪ searchResults`, cleaning each with
   `cleanFilePath` (everything before the first space) and normalizing away the project root prefix.
2. `prunePaths(paths, 50 * 1024)` — sorts by descending size and greedily packs files until the 50 KB budget is
   exhausted.
3. `codeSummary(paths, error)` — renders each file as a fenced block, annotating the reported error lines with
   `/* Error: … */`, optionally prefixing line numbers (`includeLineNumbers`) and optionally appending
   `git diff HEAD -- <path>`
   (`includeGitDiffs`, 30 s timeout).
4. Builds a **history context** from `fixHistory` — prior patches for the same file are shown, with patches for the
   *same error message* flagged as `⚠️ PRIOR FAILED FIXES` and an explicit instruction not to regenerate them.
5. Calls a `ChatAgent` whose system prompt embeds the code summary plus
   `processor.patchFormatPrompt`, and whose user message contains the error message, details,
   `additionalInstructions`, and the history context.
6. Strips any `/* Error … */` markers from the response, records a `FixAttempt` per file, and hands the response to
   `DiffInstrumentor(processor, SessionRenderer(task))`.

* `DiffInstrumentor.instrument` converts fenced diffs/files into apply‑able UI actions;
  `shouldAutoApply` returns `true` for every path when `autoFix` is enabled.

### 5. Loop or finish

`executeIteration` returns the original `OutputResult`. The `SessionController` records an
`IterationRecord`, updates the UI, and either retries or completes.

---

## The Iteration Loop & `SessionController`

`SessionController` is a small, self‑contained state machine that owns the retry policy and the entire control‑panel UI.
It is created via:

```kotlin
patchApp.newSessionController(task, onComplete = { result -> … }).start()
```

### States (`RunState`)

| State             | Meaning                                                    | Badge                                 |
|-------------------|------------------------------------------------------------|---------------------------------------|
| `IDLE`            | Constructed, not yet started                               | ⏳ Initializing…                      |
| `RUNNING_COMMAND` | `output(...)` in progress                                  | ⚙️ Running command (Iteration *n*)…   |
| `RUNNING_FIX`     | Entered when a status message contains "fix" or "Applying" | 🔧 Applying fixes…                    |
| `SUCCESS`         | Exit code 0                                                | ✅ Build succeeded!                   |
| `FAILED_RETRYING` | Non‑zero exit, retries remain, auto‑retry on               | 🔄 Failed — auto‑retrying (*k* left)… |
| `FAILED_DONE`     | Non‑zero exit, no retries left or auto‑retry off           | ❌ Failed — manual retry available    |

### Retry policy

* `retriesRemaining` starts at `settings.maxRetries` **only when `settings.autoFix` is true**, otherwise `0`.
* `autoRetryEnabled` mirrors `settings.autoFix` and can be toggled at runtime.
* Each failed iteration decrements the counter and recurses into `runIteration()` **on the same thread**, so a long
  chain of retries is a nested call chain rather than a flat loop.
* `onComplete(result)` is invoked exactly once per terminal state (`SUCCESS`, `FAILED_DONE`, or an internal exception
  which reports `OutputResult(exitCode = -1, …)`).

### Controls exposed in the UI

| Control                              | Behaviour                                                                                       |
|--------------------------------------|-------------------------------------------------------------------------------------------------|
| `▶ Run` / `▶ Run Again` / `🔄 Retry` | Guarded by `isRunning.compareAndSet(false, true)`; resets the retry budget if auto‑retry is on. |
| `⏸ Disable Auto-Retry`               | Sets `autoRetryEnabled = false` and `retriesRemaining = 0`.                                     |
| `▶ Enable Auto-Retry (N max)`        | Re‑arms the budget if currently in a terminal, non‑running state.                               |
| `⏹ Stop After Current`               | Zeroes the budget and disables auto‑retry; the in‑flight iteration finishes normally.           |

### Derived analytics

* **History timeline** — one pill per iteration (`✅ #1: clean`, `❌ #2: 3 errors (14:02:11)`), tooltip lists the first 60
  chars of each error message.
* **Error trend** — compares the last two iterations: resolved / reduced / unchanged / increased.
* **Persistent errors** — any message appearing in ≥ 2 iterations is surfaced in an amber warning box (top 3 by
  occurrence count). This is the primary signal that the model is stuck in a loop.

---

## Data Model Reference

### `PatchApp.Settings`

| Field                    | Type                    | Default | Description                                                                 |
|--------------------------|-------------------------|---------|-----------------------------------------------------------------------------|
| `commands`               | `List<CommandSettings>` | `[]`    | Commands to run, in order. Execution stops at the first non‑zero exit.      |
| `autoFix`                | `Boolean`               | `false` | Auto‑apply generated patches **and** enable the retry loop.                 |
| `maxRetries`             | `Int`                   | `3`     | Maximum additional iterations after the first.                              |
| `ignoreWarnings`         | `Boolean`               | `true`  | Skip `isWarning == true` entries when real errors exist.                    |
| `includeGitDiffs`        | `Boolean`               | `false` | Append `git diff HEAD -- <file>` to each file in the code summary.          |
| `includeLineNumbers`     | `Boolean`               | `false` | Prefix `N: ` to every line of the code summary.                             |
| `workingDirectory`       | `File?` (derived)       | —       | Getter returns the first command's dir; setter writes it to *all* commands. |
| `additionalInstructions` | `String` (derived)      | `""`    | Same fan‑out semantics; injected into both the parse and fix prompts.       |

### `PatchApp.CommandSettings`

| Field                    | Type     | Notes                                                                     |
|--------------------------|----------|---------------------------------------------------------------------------|
| `executable`             | `File`   | Fully resolved executable. `AutoFixTask` resolves this via `resolveTool`. |
| `arguments`              | `String` | Space‑delimited; split on whitespace at exec time. **No shell quoting.**  |
| `workingDirectory`       | `File?`  | Created with `mkdirs()` by `AutoFixTask`.                                 |
| `additionalInstructions` | `String` | Free‑text guidance appended to prompts.                                   |

### `PatchApp.OutputResult`

```kotlin
data class OutputResult(
  val exitCode: Int,
  val output: String,
  val errors: ParsedErrors? = null   // if non-null, LLM error-parsing is skipped
)
```

### Error taxonomy

```kotlin
data class ParsedErrors(val errors: List<ParsedError>? = null)

data class ParsedError(
  val message: String?,              // grouping key
  val details: String?,              // distilled excerpt of the raw output
  val severity: Int? = 0,            // 1..10
  val complexity: Int? = 0,          // 1..10
  val isWarning: Boolean? = false,
  val locations: List<CodeLocation>?,
  val research: ResearchNotes?
)

data class CodeLocation(val file: String?, val lines: List<Int>?)

data class ResearchNotes(
  val fixFiles: List<String>?,       // files to modify
  val relatedFiles: List<String>?,   // context files
  val searchQueries: List<SearchQuery>?
)

data class SearchQuery(val pattern: String?, val fileGlob: String?)
```

### Bookkeeping types

```kotlin
data class FixAttempt(error: String, patch: String, timestamp: Long, iteration: Int)
data class ParsedErrorRecord(errors: ParsedErrors?, timestamp: Long, iteration: Int)
data class IterationRecord(
  iteration: Int, exitCode: Int, errorCount: Int,
  timestamp: Long, errorSummaries: List<String>, fixApplied: Boolean
)
```

* `fixHistory: Map<filePath, List<FixAttempt>>` — feeds the anti‑repetition prompt section.
* `previousParsedErrorsRecords: List<ParsedErrorRecord>` — feeds `recentErrors()` and the
  "previous occurrences" verbose block.

---

## Task Types

### `AutoFix`

| Property               | Value                                        |
|------------------------|----------------------------------------------|
| `name`                 | `AutoFix`                                    |
| `category`             | `Execution`                                  |
| `taskClass`            | `AutoFixTask`                                |
| `executionConfigClass` | `AutoFixTask.AutoFixTaskExecutionConfigData` |
| `taskSettingsClass`    | `AutoFixTask.AutoFixTaskTypeConfig`          |

**Execution config**

```kotlin
class AutoFixTaskExecutionConfigData(
  var commands: MutableList<CommandWithWorkingDir>?,
  task_description: String?,
  task_dependencies: List<String>?,
  state: TaskState?
)

data class CommandWithWorkingDir(
  var executable: String = "",         // relative path or simple name; NOT a shell line
  var arguments: MutableList<String>,  // no shell features: no &&, |, >, quoting
  var working_dir: String? = null      // relative to project root; null = root
)
```

`CommandWithWorkingDir.validate()` rejects a blank `executable`.

> ⚠️ **No shell.** Do not pass `&&`, `|`, `>`, globs, or quoted strings. If you need shell
> semantics, commit a script (`.sh`, `.ps1`, `.bat`) and invoke that instead.

**Type config** exposes `promptTemplate`, whose default advertises the task to the planner and contains an
`{executables}` placeholder that the platform substitutes with the allow‑listed tools.

### `SingleFix`

| Property               | Value                                            |
|------------------------|--------------------------------------------------|
| `name`                 | `SingleFix`                                      |
| `category`             | `Execution`                                      |
| `executionConfigClass` | `SingleFixTask.SingleFixTaskExecutionConfigData` |

Takes a single `logFile` (relative to the project root) and constructs an anonymous `PatchApp`
whose `output()` **always returns `OutputResult(1, logFile.readText())`** — guaranteeing that the fix pipeline runs
exactly once over the pre‑existing log. `codeFiles()` walks the whole root (files < 512 KB) rather than relying on a
supplied file array, and a dummy `CommandSettings`
(`File("dummy")`) exists only so that `Settings.workingDirectory` resolves correctly.

`SingleFixTask` uses `orchestrationConfig.processor ?: PatchProcessors.Fuzzy` as its patch processor default.

---

## Configuration Reference

Sourced from `OrchestrationConfig` at task run time:

| Key          | Consumed by | Effect                                                                                |
|--------------|-------------|---------------------------------------------------------------------------------------|
| `autoFix`    | both tasks  | Skips the `▶ Run` gate, auto‑applies patches, arms the retry budget.                  |
| `workingDir` | both tasks  | Default working directory when a command omits `working_dir`.                         |
| `processor`  | both tasks  | The `PatchProcessor` that defines `patchFormatPrompt` and diff application semantics. |
| `user`       | both tasks  | Used to materialize the configured `ApiChatModel` (`model.instance(user)`).           |

Model selection:

* **Smart model** — `typeConfig.model?.instance(user) ?: defaultSmart`; used for error parsing and patch generation.
* **Fast model** — `defaultFast`; used as `ParsedAgent.parsingModel` (structured extraction only).

Both are wrapped with `getChildClient(subTask)` so usage is attributed to the correct UI node.

---

## UI Anatomy

`SessionController` reserves three sequential buffers on the host `SessionTask`:

```text
┌─ controlPanelBuffer ────────────────────────────────────────────┐
│  ⚙️ Running command (Iteration 2)…            Iteration 2 / 4 max │
│  History: ✅ #1: clean  ❌ #2: 3 errors (14:02:11)                │
│  Errors reduced: 7 → 3 (↓4)                                     │
│  ⚠️ Persistent Errors …                                          │
│  [▶ Run] [⏸ Disable Auto-Retry] [⏹ Stop After Current]          │
└─────────────────────────────────────────────────────────────────┘
┌─ summaryBuffer ─────────────────────────────────────────────────┐
│  ✅ Build Successful — completed in 3 iterations.                │
└─────────────────────────────────────────────────────────────────┘
┌─ iterationAreaBuffer ───────────────────────────────────────────┐
│  Iteration Details                                              │
│  ▾ ❌ Iteration 2 — 3 errors        (newest first, latest open)  │
│      Command Output   [tabs per command]                        │
│      Fix Details      ▸ Error Analysis Details                  │
│                       Fix: cannot find symbol… (linked task)    │
│  ▸ ✅ Iteration 1 — success                                      │
└─────────────────────────────────────────────────────────────────┘
```

Inside each iteration, `executeIteration` creates two detached child tasks:

* **Command Output** — receives the `TabbedDisplay` produced by `output(...)`, one tab per command, with live streaming,
  a `Stop` link, and periodic `[Process Status]` diagnostics.
* **Fix Details** — a progress header (`Processing N error(s)…` → `✅ Finished processing N error
group(s)`), a collapsible **Error Analysis Details** section with `Text` / `JSON` /
  `Process Details` tabs, and one `linkedTask` per error group containing the JSON error, search results, the
  instrumented diff, and verbose provenance blocks.

---

## Transcripts & Logging

### Markdown transcript

`AutoFixTask` streams a markdown transcript to a user file (`transcriptFile()`), structured as two
`markdown="1"` divs:

* `#work-details` — task header, an enumerated command list with resolved working directories.
* `#final-output` — `## Result: Success` or `## Result: Failed`, the exit code, and the command output truncated to 5 KB
  via `truncateMiddle`. On a crash it instead contains `## Error` with a collapsible stack trace.

`SingleFixTask` writes a simpler transcript via `createTranscript(...)`, which also injects an
`<a href=…>` link into the UI so the user can open the report.

### The Triple Log Rule

Terminal/error paths deliberately log to **three** sinks:

1. `subTask.error(e)` — surfaced in the UI.
2. `log.error(...)` — SLF4J for operators.
3. `transcript?.write(...)` — durable markdown artifact.

Follow this rule when adding new terminal branches. Also ensure every terminal branch calls
`resultFn(...)`, `semaphore.release()`, `subTask.complete()`, and closes the transcript — otherwise the orchestrator
thread will block forever.

---

## Extending `PatchApp`

Subclass `PatchApp` and implement four members:

```kotlin
 abstract fun codeFiles(): Set<Path>
abstract fun projectSummary(): String
abstract fun output(task: SessionTask, settings: Settings, tabs: TabbedDisplay): OutputResult
abstract fun searchFiles(searchStrings: List<String>): Set<Path>
```

| Member             | Contract                                                                                                                                                      |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `codeFiles()`      | Project‑root‑relative paths of indexable source files. Filter aggressively (`CmdPatchApp` uses < 512 KB).                                                     |
| `projectSummary()` | A newline‑delimited `* <path> - <bytes> bytes` listing injected into the parse prompt.                                                                        |
| `output(...)`      | Produce the raw signal to diagnose. Return `exitCode == 0` to declare success and terminate the loop. Populate `OutputResult.errors` to bypass LLM parsing.   |
| `searchFiles(...)` | Substring search across the project. (Note: `fixAllErrors` currently performs its own glob+substring walk; `searchFiles` is available for subclass/tool use.) |

Optional overrides:

* `codeSummary(paths, error)` — customize how source is presented to the fixer.
* `promptPrefix` (constructor param) — defaults to
  `"The following command was run and produced an error:"`.
* `updateStatus: (String) -> Unit` — a mutable hook the controller rebinds each iteration. Messages containing `"fix"`or
  `"Applying"` (case‑insensitive) transition the badge to `RUNNING_FIX`.

Two reference implementations ship with the package: `CmdPatchApp` (process execution) and the anonymous log‑file
subclass inside `SingleFixTask`.

---

## Threading & Concurrency

| Boundary                   | Mechanism                                                                                                                  |
|----------------------------|----------------------------------------------------------------------------------------------------------------------------|
| Orchestrator ↔ fix session | `Semaphore(0)`; the plan thread blocks in `acquire()` until a terminal branch releases.                                    |
| Task body                  | `subTask.pool.submit { … }` — the fix session runs off the request thread.                                              |
| Iteration                  | `Thread { … }.start()` inside `runIteration()`; retries recurse on that thread.                                            |
| Process I/O                | Two `Thread`s per command (`stdout`, `stderr`) appending to a shared `StringBuilder`.                                      |
| Error groups               | `task.pool.submit` per group, joined with `.toTypedArray().onEach { it.get() }`.                                        |
| Shared state               | `AtomicInteger` / `AtomicBoolean` / `AtomicReference` for controller state; `synchronized(task)` around UI output flushes. |

> ⚠️ `buffer: StringBuilder` in `CmdPatchApp.output` is written by two reader threads without
> synchronization on append. `fixHistory`, `previousParsedErrorsRecords`, and `iterationHistory`
> are plain mutable collections touched from pool threads. Treat these as known hazards when
> modifying the code.

---

## Limits, Truncation & Guardrails

| Limit                              | Value                   | Location                        |
|------------------------------------|-------------------------|---------------------------------|
| Process timeout                    | 5 minutes               | `CmdPatchApp.output`            |
| Liveness poll interval             | 15 s                    | `CmdPatchApp.output`            |
| UI output refresh interval         | 15 s                    | `readStream`                    |
| Console output kept                | 32 KB head + 32 KB tail | `CmdPatchApp.truncate(kb = 32)` |
| Transcript output kept             | 5 KB (head + tail)      | `String.truncateMiddle`         |
| Indexable code file size           | < 512 KB                | `codeFiles()`                   |
| File size in code summary          | < 256 KB                | `codeSummary`                   |
| Search candidate file size         | < 1 MB                  | `fixAllErrors`                  |
| Total prompt file budget           | 50 KB                   | `prunePaths(paths, 50 * 1024)`  |
| `git diff` timeout                 | 30 s                    | `codeSummary`                   |
| Persistent‑error warning threshold | ≥ 2 iterations          | `renderControlPanel`            |
| Persistent errors displayed        | top 3                   | `renderControlPanel`            |

Other guardrails:

* ANSI escape sequences are stripped from captured output.
* Directories beginning with `.` and `.gitignore`‑excluded paths are skipped during file discovery.
* `/* Error: … */` annotations injected into the code summary are stripped from the model's response before patching.
* `cleanFilePath` truncates a reported path at the first space, tolerating models that append notes like
  `src/Foo.kt (line 12)`.

---

## Troubleshooting

| Symptom                                          | Likely cause                                                                                        | Remedy                                                                                                                       |
|--------------------------------------------------|-----------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------|
| `IllegalArgumentException: Command not found: X` | `resolveTool` could not locate `X` under the project root or the tool allow‑list.                   | Use a repo‑relative script path, or register the executable with the platform.                                               |
| Plan hangs forever on an AutoFix node            | A terminal branch failed to `semaphore.release()`.                                                  | Check the UI for an un‑clicked **Accept & Continue** / **Ignore Error** link; these are required when `autoFix` is off.      |
| `Process timed out after 5 minutes`              | Long build, or the process is waiting on stdin.                                                     | Shorten the command, or pass non‑interactive flags (`--console=plain`, `-B`, `--no-daemon`).                                 |
| Shell operators appear to be ignored             | `ProcessBuilder` is used directly; there is no shell.                                               | Move the pipeline into a script file.                                                                                        |
| Same error persists across every iteration       | The model is regenerating an identical patch, or the error is not actually in the summarized files. | Watch the **Persistent Errors** panel; add `additionalInstructions`, enable `includeGitDiffs`, or narrow `searchQueries`.    |
| Patches target the wrong file                    | `cleanFilePath` / root‑prefix normalization mismatch.                                               | Ensure reported paths are project‑root‑relative; check the "Files identified for modification" verbose block.                |
| Only the first command ever runs                 | By design — execution stops at the first non‑zero exit.                                             | Split into separate `AutoFix` tasks, or wrap in a script.                                                                    |
| Empty **Error Analysis Details** JSON            | The parser returned zero errors for a non‑zero exit.                                                | Inspect the `Process Details` tab; the output may be truncated past the error, or written to a file rather than the console. |

---

## Known Limitations / Roadmap

* **Retry recursion.** `runIteration()` recurses rather than loops; deep retry chains grow the stack. Converting to a
  `while` loop is a safe refactor.
* **Unsynchronized shared state.** See [Threading & Concurrency](#threading--concurrency).
* **Fail‑fast command list.** Later commands never run after a failure, which can mask independent problems.
* **`searchFiles` is effectively dead code** in the main path — `fixAllErrors` inlines its own walk. Consider unifying.
* **No verification of applied patches** beyond re‑running the command; there is no per‑patch compile check or rollback.
* **`SingleFixTask` releases its semaphore immediately** after `start()`, so the plan advances while the fix session is
  still running asynchronously (documented in‑code as intentional).
* **Fixed 5‑minute timeout** — not currently configurable via `Settings`.
* **Hard‑coded prompts** — the fix prompt is assembled inline in `PatchApp.fix`; only
  `AutoFixTaskTypeConfig.promptTemplate` (the *planner‑facing* description) is user‑configurable.