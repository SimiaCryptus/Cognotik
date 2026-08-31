# SessionTask
**Real-time streaming UI primitive for session-scoped task output.**
`SessionTask` is the low-level rendering/IO surface every agent task writes through — buffered HTML fragments,
spinners, file links, and nested sub-tasks — pushed over a `SocketManager` to the browser as they're produced.
`Side-Effect Safe` (in-memory buffer) · `Filesystem Write` (via `saveFile`/`newLogStream`) · `No Model Requirement`
---
## Reality Check
**Input (construction / usage, not a JSON execution config — `SessionTask` is instantiated by `SocketManager`,
not driven by a serialized config):**
```kotlin
val task = ui.newTask(cancelable = true)
task.header("Refactor Plan", level = 2)
task.add("Analyzing 14 files...", markdown = true)
task.image(diagramBufferedImage)
task.complete("Done — 14 files updated.")
```
**Rendered output (what the user sees in the UI):**
A single scrollable message block identified by `messageID`, containing, in order:
- An `<h2>` header ("Refactor Plan") with markdown rendering applied.
- A markdown-rendered status line with an animated Bootstrap spinner appended while work is in-flight.
- An inline `<img>` tag pointing to a `fileIndex/{sessionId}/images/{uuid}.png` link generated via `saveFile`.
- A final "completion-message"-classed `<div>` replacing the spinner once `complete()` is called.
- Optionally, nested collapsible `<div class="expandable-guide">` blocks (from `expandable`/`expanded`) and
dismissible `<div class="hideable-message">` blocks with a close-button href-link wired through
`ui.linkTriggers`.
---
## Documentation
### Constructor Parameters
| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `messageID` | Optional (defaults to `Session.randomId(11)`) | `String` | Unique DOM id used as the `message-id` attribute on the placeholder `<div>` and as the socket message key. |
| `buffer` | Optional (defaults to empty `MutableList<StringBuilder>`) | `MutableList<StringBuilder>` | Accumulates every appended HTML fragment; `currentText` joins the non-blank entries for each `send()`. |
| `spinner` | Optional (defaults to companion `spinner` HTML constant) | `String` | Bootstrap spinner markup appended after content while `showSpinner = true`. |
| `ui` | Required | `SocketManager` | The session's socket manager; used for `send()`, `linkTriggers`, `sessionId`, `dataStorage`, and file resolution. |
### Key Methods (config-like inputs consumed at call time)
| Method | Notable Parameters | Description |
|---|---|---|
| `append` | `htmlToAppend: String`, `showSpinner: Boolean` | Low-level primitive: wraps content in a `<div>`, pushes to buffer, sends. |
| `add` | `message`, `showSpinner`, `tag`, `additionalClasses`, `markdown` | Standard message emission with optional markdown rendering (`response-message` class). |
| `hideable` | same as `add` | Dismissible message with a close-button href-link. |
| `echo` | `message`, `showSpinner`, `tag` | User-message styling, markdown forced on. |
| `header` | `message`, `level` (0–6) | Emits `h1`–`h6` or `div` with `response-header` class. |
| `expandable` / `expanded` | `title`, `content`, `markdown` | Collapsible/expanded `expandable-guide` block. |
| `verbose` | `message`, `tag` (default `pre`) | Hidden-by-default diagnostic output (`verbose` class). |
| `error` | `e: Throwable`, `tag` | Renders `ValidationError` / `FailedToImplementException` / generic stack traces as a hideable error block. |
| `complete` | `message`, `tag`, `additionalClasses` | Final message; suppresses the spinner (`completion-message` class). |
| `image` | `image: BufferedImage` | Saves PNG via `saveFile` and embeds an `<img>`. |
| `saveFile` | `relativePath: String`, `data: ByteArray` | Writes bytes under the session's user directory; rejects path traversal (`..`) and blank paths. |
| `newLogStream` | `name: String` | Creates a timestamped Markdown log file under `.logs/` with a stack-trace header, returns a `BufferedOutputStream`. |
| `newSession` / `linkedTask` | `session`, `appname`, `label`, `renderFn` | Spawns a child `SocketManager`/`SessionTask` linked as a sub-application, registered in `SessionProxyServer` and `ApplicationServer.appInfoMap`. |
| `hrefLink` | `linkText`, `classname`, `id`, `handler: Consumer<Unit>` | Registers a click handler in `ui.linkTriggers` and returns an `<a>` tag. |
| `newTask` | `showSpinner: Boolean` | Creates a nested `SessionTask`, embedding its placeholder in the current buffer. |
### Dependencies
- `SocketManager` — required collaborator for all output; owns `sessionId`, `dataStorage`, `linkTriggers`, and file
resolution (`resolveSystemFile`/`resolveUserFile`).
- `SessionProxyServer` — used by `newSession`/`linkedTask` to register parent/child session relationships and agent
handlers.
- `ApplicationServer.appInfoMap` — populated when spawning a linked sub-session/app via `linkedTask`.
- `ChatInterface.getChildClient(task)` extension — attaches a `newLogStream()` to a chat client's `logStreams`,
coupling `SessionTask` to LLM client logging.
- Not itself an orchestrated `Task` subtype with an execution config — it is the shared **output substrate** that
every concrete `*Task` implementation (Plan, CodingAgent, etc.) uses internally to render progress.
### Token Usage Estimate
`Low` — `SessionTask` performs no LLM calls itself; all "usage" is local string buffering and file I/O. Any token
cost is incurred by the caller (e.g. a `ChatInterface` client wired through `getChildClient`).
---
## Config & Process
### Type Configuration (fixed at construction)
- `ui: SocketManager` — the transport/session context; immutable for the task's lifetime.
- `spinner: String` — the loading indicator markup; rarely overridden.
- `messageID: String` — the DOM anchor; generated once, referenced by `placeholder`.
### Runtime Configuration (varies per call)
- Every `append`/`add`/`header`/`expandable`/`error`/`complete` call supplies its own `showSpinner`, `tag`,
`additionalClasses`, and `markdown` flags — these are per-invocation, not fixed task state.
- `buffer` grows monotonically across the task's lifetime as `append` is called; `currentText` is recomputed from
the full buffer on every `send()`.
### Lifecycle
1. **Initialization** — `SessionTask` is constructed (typically via `SocketManager.newTask()`), producing a
`placeholder` `<div message-id="...">` that gets embedded into the parent task's buffer.
2. **Execution** — Callers repeatedly invoke `append`/`add`/`header`/`expandable`/`verbose`/`image` as work
progresses; each call re-renders `currentText` (all buffered fragments joined) plus an optional spinner and
pushes it through `ui.send(html)`. `saveFile` validates paths (rejects blank/`..`) and lazily creates the
session's user directory tree before writing.
3. **Error Handling** — `error(e)` differentiates `ValidatedObject.ValidationError`, `FailedToImplementException`
(rendering prefix + attempted code), and generic `Throwable` (full stack trace via `stackTraceTxt`), always
rendered as a dismissible, markdown-formatted block with `showSpinner = false` by default so failures don't get
masked by a perpetual loading indicator. There is no automatic rollback/retry — `SessionTask` only reports state;
retry logic lives in the calling task.
4. **Completion** — `complete(message)` emits the final markdown block tagged `completion-message` with
`showSpinner = false`, terminating the visual "in progress" state for that message.
---
## Integration
### Usage in an orchestrating task
```kotlin
class ExampleTask(
planTask: PlanningTask.PlanTask,
private val root: PlanCoordinator,
private val plan: PlanUtil.TaskBreakdownWithPrompt
) : AbstractTask<PlanningTask.PlanTask>(planTask, root, plan) {
override fun run(
agent: PlanCoordinator,
messages: List<String>,
task: SessionTask,          // <-- injected SessionTask instance
api: API,
resultFn: (String) -> Unit
) {
task.header("Example Task", level = 3)
val subTask = task.newTask(showSpinner = true)
try {
subTask.add("Working...", markdown = true)
// ... do work, call an LLM client via getChildClient(subTask) ...
subTask.complete("Finished successfully.")
} catch (e: Throwable) {
subTask.error(e)
throw e
}
}
}
```
### Prompt segment
`SessionTask` itself injects no LLM prompt content — it has no prompt template. The only LLM-adjacent hook is the
`ChatInterface.getChildClient(task)` extension, which attaches a diagnostic log stream (not a prompt) to the
underlying client:
```kotlin
fun ChatInterface.getChildClient(task: SessionTask) =
getChildClient(task.sessionId).apply { logStreams += task.newLogStream() }
```