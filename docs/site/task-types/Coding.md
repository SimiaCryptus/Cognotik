# Coding Task

**Generate, execute, and iteratively repair code from natural-language and chat context — with an in-UI Run/Retry loop.**

`Side-Effect Safe (opt-in Destructive)` `Iterative` `Runtime-Backed` `Auto-Fix Capable`

`CodingTask<T : CodeRuntime>` wraps `CodeAgent` to turn a chat transcript into compilable/executable code against a
pluggable `CodeRuntime`, render the result in the session UI, and offer the user a "▶ Run" button and a feedback box
to request revisions. If `autoFix` is enabled, failures are automatically fed back into the LLM as a follow-up
`CodeRequest` until the code executes cleanly (bounded by the underlying `CodeAgent` retry policy).

---

## Reality Check

**Input (execution configuration passed to `start()`):**

```json
{
  "codeRequest": {
    "messages": [
      { "content": "You have access to symbols: dataFrame, outputDir", "role": "system" },
      { "content": "Load dataFrame, drop null rows, and write result to outputDir/clean.csv", "role": "user" }
    ]
  },
  "temperature": 0.1,
  "details": "Use only the Kotlin stdlib and the provided CodeRuntime symbols.",
  "retryable": true,
  "autoFix": false
}
```

**Rendered Output (session UI):**

An expandable **"Code"** panel showing the generated snippet in a syntax-highlighted fenced block
(`​```kotlin ... ```` `), matching `codeAgent.language`. Below it, a reply-style feedback row containing:

* A **"▶ Run"** link (only if the current user is authorized to execute — `canPlay`), which replaces the row with a
  transient "Running..." header, executes the code against `codeRuntime`, and renders a two-tab `TabbedDisplay`
  (**Result** / **Output**) with the return value and stdout, respectively.
* A free-text feedback input; submitting it appends the assistant's code and the user's feedback to the message
  history and re-invokes generation (`feedback()` → `start()`), replacing the panel with a "Revising..." header.
* A transcript link block ("Writing CodeAgent_<timestamp>.md to ... html ... pdf") pointing to an auto-saved
  Markdown/HTML/PDF transcript of the generated code and, on execution, the result/output.

On error (compile failure, `FailedToImplementException`, `ValidatedObject.ValidationError`, or runtime exception),
the panel is replaced with a rendered error block and — if `autoFix` is on — the error text is fed back into a new
`displayCode` call automatically.

---

## Documentation Tab

### Configuration

| Field Name | Type | Required/Optional | Description |
|---|---|---|---|
| `dataStorage` | `StorageInterface` | Required | Backing store used to resolve/write transcript files and session artifacts. |
| `session` | `Session` | Required | The session this task's UI elements and transcripts belong to. |
| `user` | `User` | Required | User the task runs on behalf of; used for authorization checks (`canPlay`) and passed into `CodeAgent`. |
| `ui` | `SocketManager` | Required | WebSocket-backed UI manager used to create new tasks, text inputs, and href-links. |
| `codeRuntime` | `T : CodeRuntime` | Required | The pluggable execution environment (language/sandbox) the generated code runs against. |
| `symbols` | `Map<String, Any>` | Required | Named objects exposed to the generated code (e.g. dataframes, output paths); merged per-invocation with `{"task": task}`. |
| `temperature` | `Double` | Optional (default `0.1`) | Sampling temperature forwarded to `CodeAgent`/model calls. |
| `details` | `String?` | Optional (default `null`) | Extra natural-language instructions/constraints appended to the code-generation prompt context. |
| `model` | `ChatInterface` | Required | Primary (and fallback) model used by `CodeAgent` for generation. |
| `mainTask` | `SessionTask` | Required (constructor param) | Parent task under which sub-tasks and placeholders are created. |
| `retryable` | `Boolean` | Optional (default `true`) | If true, wraps execution in a `Retryable` UI block allowing the user to re-run generation from scratch. |
| `autoFix` | `Boolean` | Optional (default `false`) | If true, execution runs automatically after generation and failures are fed back into the LLM without user interaction. |
| `describer` | `TypeDescriber` | Optional (default `AbbrevWhitelistYamlDescriber("com.simiacryptus")`) | Controls how `symbols` types are described to the model for code generation. |
| `codeRequest.messages` | `List<Pair<String, ModelSchema.Role>>` | Required | Chat transcript (system/user/assistant turns) driving code generation; last `user` message may itself be a literal ```` ``` ````-fenced code block to skip generation. |

### Dependencies

* **`CodeAgent`** — does the actual generation (`answer`) and execution (`CodeResult.result`); `CodingTask` is a thin
  UI/orchestration wrapper around it.
* **`CodeRuntime`** — the generic execution backend; `CodingTask<T : CodeRuntime>` is parameterized over it, so it
  composes with any runtime implementation (e.g. shell, JVM sandbox, notebook kernel).
* No direct references to other `*Task` classes were found; this task is typically driven by higher-level
  orchestration tasks that assemble a `CodeRequest` transcript and call `start()`.

### Token Usage

**Medium** — prompts include the full chat history, `details`, and describer output for all `symbols` (can grow with
many/complex symbol types); responses are typically a single code block plus optional rendered commentary, and
feedback loops re-send the growing transcript on each revision.

---

## Config & Process Tab

### Type Configuration

* `T : CodeRuntime` — the generic runtime type bound at construction; fixes which execution backend the task targets
  for its lifetime.
* `describer: TypeDescriber` — fixed at construction, controls symbol-type serialization for every request made
  through this instance.

### Runtime Configuration

* `codeRequest: CodeRequest` — passed per-call to `start()`; carries the evolving message history.
* `retryable` / `autoFix` — behavioral toggles read at `start()` time to choose between the `Retryable`-wrapped UI
  path and the plain `ui.pool.submit` async path, and whether to auto-execute vs. wait for user feedback.
* `symbols(task)` — recomputed per sub-task (base `symbols` + `{"task": task}`) so generated code can reference the
  current `SessionTask` instance.

### Lifecycle

1. **Initialization** — `start()` creates a sub-task placeholder on `mainTask`, then dispatches either into a
   `Retryable` block (if `retryable`) or a plain thread-pool submission, each showing an "Running..." status.
2. **Execution**
    * `displayCode()` extracts the last `user` message; if it's a literal fenced code block, it's used verbatim via
      `CodeResultImpl` (bypassing generation); otherwise `codeAgent.answer(codeRequest)` generates code.
    * `displayCodeAndFeedback()` renders the code, writes it to a transcript file, and either:
        * calls `execute()` immediately if `autoFix && canPlay`, or
        * calls `displayFeedback()` to present the Run button + feedback box.
    * `execute()` runs the code via `CodeAgent.CodeResult.result`, writes Result/Output to the transcript and a
      `TabbedDisplay`, then loops back into `displayFeedback()` with the execution summary appended as an assistant
      message.
3. **Error Handling**
    * Any throwable in `start`, `displayCode`, `displayCodeAndFeedback`, `feedback`, or the outer `execute` wrapper is
      logged, shown via `task.error(e)`, and appended to the transcript as a `## Error` Markdown block.
    * The inner `execute(task, response)` failure path distinguishes `ValidatedObject.ValidationError` and
      `FailedToImplementException` for friendlier messages, then re-invokes `displayCode()` with the error appended
      as a `system` message — enabling either manual user correction or, when driven by `autoFix`, further automated
      iteration.

---

## Integration Tab

### Registering / invoking in an orchestration config

```kotlin
val codingTask = CodingTask(
    dataStorage = dataStorage,
    session = session,
    user = user,
    ui = socketManager,
    codeRuntime = myShellRuntime,          // any CodeRuntime implementation
    symbols = mapOf(
        "outputDir" to outputDirectory,
        "dataFrame" to loadedDataFrame,
    ),
    temperature = 0.1,
    details = "Use only the Kotlin stdlib and the provided CodeRuntime symbols.",
    model = chatModel,
    mainTask = parentSessionTask,
    retryable = true,
    autoFix = false,
)

codingTask.start(
    codeRequest = CodeRequest(
        messages = listOf(
            "You have access to symbols: dataFrame, outputDir" to ModelSchema.Role.system,
            "Load dataFrame, drop null rows, and write result to outputDir/clean.csv" to ModelSchema.Role.user,
        )
    )
)
```

### Prompt segment injected into the LLM

`CodingTask` does not itself template the prompt text — it forwards the raw `codeRequest.messages` transcript
directly to `CodeAgent`, along with `symbols(task)` (described via `describer`) and `details`. A representative
composed turn sent downstream looks like:

```text
[system] You have access to symbols: dataFrame, outputDir
[system] <describer-generated YAML type description of dataFrame/outputDir>
[system] Use only the Kotlin stdlib and the provided CodeRuntime symbols.
[user]   Load dataFrame, drop null rows, and write result to outputDir/clean.csv
```

On feedback iterations, the prior assistant code and the user's follow-up feedback are appended verbatim:

```text
[assistant] <previously generated code>
[user]      <free-text feedback from the Run/feedback UI>
```