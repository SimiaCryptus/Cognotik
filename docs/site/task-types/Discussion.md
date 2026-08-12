# Discussion

**Direct LLM Q&A with optional file context — no side effects, no file mutation.**

`Side-Effect Safe` `File` `Interactive` `Auto-Fix Compatible`

---

## Reality Check

**Input Configuration**

```json
{
  "task_type": "Discussion",
  "inquiry_questions": [
    "What are the tradeoffs between coroutine-based and thread-pool-based concurrency in this module?",
    "Are there any race conditions in the current SessionTask implementation?"
  ],
  "inquiry_goal": "Understand concurrency risk before refactoring SessionTask",
  "related_files": [
    "**/SessionTask.kt",
    "**/AbstractTask.kt"
  ],
  "task_description": "Assess concurrency safety of SessionTask",
  "task_dependencies": [],
  "state": "pending"
}
```

**Rendered Output**

The UI streams a sequence of markdown status blocks in the session task panel:

```
### Initializing Discussion
Gathering context and preparing inquiry...

Opening interactive discussion...
```

Followed by a rendered markdown response (headings, bullet lists, code blocks) containing the LLM's analysis. If
`autoFix` is disabled, an interactive `Discussable` widget appears beneath the response allowing the user to submit
follow-up messages, which stream additional "Revision Response" markdown blocks. A parallel transcript file
(`transcriptFile()`) records the full mermaid flow diagram plus every request/response pair in collapsible
`<details>` blocks, viewable as a task artifact.

---

## Documentation

### Configuration

| Field Name          | Required/Optional | Type           | Description                                                                 |
|----------------------|--------------------|----------------|-------------------------------------------------------------------------------|
| `inquiry_questions`  | Optional           | `List<String>` | The specific questions or topics to be addressed in the inquiry              |
| `inquiry_goal`       | Optional           | `String`       | The goal or purpose of the inquiry                                          |
| `related_files`      | Optional           | `List<String>` | File paths/glob patterns (e.g. `**/*.kt`) used as optional input context     |
| `task_description`   | Optional           | `String`       | A description of the task                                                   |
| `task_dependencies`  | Optional           | `List<String>` | List of task IDs this task depends on                                       |
| `state`              | Optional           | `TaskState`    | The current state of the task                                               |

### Dependencies

None enforced structurally — `Discussion` does not reference or invoke other `TaskType` implementations. It may
declare `task_dependencies` generically like any orchestrated task, but has no hard-coded coupling to other task
classes (unlike file-writing tasks that chain into review/validation tasks).

### Token Usage

**Medium** — Prompt size scales with `related_files` glob matches (each file's full text or extracted document
content is inlined) plus the discussion transcript growing with each revision round. No output truncation logic is
present, so large file sets or long interactive threads increase cost linearly.

---

## Config & Process

### Type Configuration

`DiscussionTaskTypeConfig` — inherits `TaskTypeConfig`:
* `task_type: String? = "Discussion"`
* `name: String?`

No task-type-specific settings beyond the base fields (e.g. model override is inherited via `typeConfig.model`).

### Runtime Configuration

`DiscussionTaskExecutionConfigData` — inherits `TaskExecutionConfig`:
* `inquiry_questions`, `inquiry_goal`, `related_files` (task-specific)
* `task_description`, `task_dependencies`, `state` (inherited base fields)

### Lifecycle

**Initialization**
* A `ChatAgent` named `"Insight"` is constructed with a fixed system prompt oriented toward requirement breakdown and
  comprehensive topic research.
* Model resolution: uses `typeConfig.model` if set, otherwise falls back to `defaultSmart`, wrapped via
  `getChildClient(task)`.
* A transcript file is opened and seeded with a mermaid flow diagram documenting the Auto-Fix vs. Interactive branch.

**Execution**
* File context is gathered via `getInputFileCode()`, which globs `related_files` against the project root, skips
  ignored/binary paths, and for each match either reads raw text (for known text extensions) or extracts content via
  `PaginatedDocumentReader` / generic document reader fallback.
* **Auto-Fix mode:** builds a single input string ("Expand ... Questions ... Goal ...") plus file context, calls
  `insightActor.answer(input)` once, and returns the result directly.
* **Interactive mode:** wraps the same initial answer call in a `Discussable` component, enabling `reviseResponse`
  callbacks that re-invoke `insightActor.respond(...)` with the accumulated chat history (`ModelSchema.ChatMessage`
  list) each time the user submits feedback.
* All requests/responses are appended to the transcript as markdown sections.

**Error Handling**
* The entire run body is wrapped in a top-level `try/catch`: on exception, `task.error(e)` marks the UI task failed,
  the stack trace is written to the transcript under an `## Error` heading, and `resultFn` is invoked with an
  `"Error: ..."` string rather than throwing further.
* Individual file-reading failures inside `getInputFileCode()` are caught per-file, logged as warnings, and replaced
  with an empty string so one unreadable file does not abort the whole context assembly.
* No retry or rollback logic exists — this task performs no destructive writes, so failure simply surfaces to the
  user without needing compensating actions.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val config = OrchestrationConfig(
  // ...other settings
).apply {
  // Discussion tasks are typically added by the planner/orchestrator directly,
  // but can be constructed manually for testing:
  val discussionTask = DiscussionTask(
    orchestrationConfig = this,
    planTask = DiscussionTask.DiscussionTaskExecutionConfigData(
      inquiry_questions = listOf("What is the current retry policy for the HTTP client?"),
      inquiry_goal = "Document retry behavior before adding circuit breaker",
      related_files = listOf("**/HttpClient.kt"),
      task_description = "Summarize retry policy"
    )
  )
}
```

### Prompt Segment (injected into planning LLM)

Non-Auto-Fix mode:

```
Discussion - Directly answer questions or provide insights using the LLM. Reading files is optional and can be included if relevant to the inquiry.
  * Specify the questions and the goal of the inquiry.
  * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
  * User response/feedback and iteration are supported.
```

Auto-Fix mode:

```
Discussion - Directly answer questions or provide a report using the LLM. Reading files is optional and can be included if relevant to the inquiry.
  * Specify the questions and the goal of the inquiry.
  * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
```