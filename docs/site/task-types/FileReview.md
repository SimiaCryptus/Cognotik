# FileReview

**Read files and report on requested details — without touching them.**

`FileReview` loads the specified files (with optional non-text extraction for PDFs/HTML/etc.), asks an LLM to answer a
concrete list of queries against that content, and returns a citation-backed report. No edits, no diffs, no patches —
purely analytical output suitable as context for downstream tasks.

**Badges:** `Side-Effect Safe` · `File` category · `Text/Vision model` (depends on configured `model`)

---

## Reality Check

**Input configuration**

```json
{
  "task_type": "FileReview",
  "task_description": "Summarize the retry/backoff logic used by the HTTP client",
  "main_file": "src/main/kotlin/com/example/net/HttpClient.kt",
  "related_files": [
    "src/main/kotlin/com/example/net/RetryPolicy.kt",
    "src/main/resources/http-client.yaml"
  ],
  "queries": [
    "What is the maximum retry count and backoff strategy?",
    "Which exceptions trigger a retry vs. an immediate failure?",
    "Are retry parameters configurable at runtime, and from where?"
  ],
  "report_format": "markdown table per query, then a bullet-point summary",
  "extractContent": false,
  "requireCitations": true
}
```

**Rendered output (UI)**

A `TabbedDisplay` with three tabs:

* **Report** — the LLM's markdown answer, rendered inline: one table row per query, each citing file path + line
  numbers, followed by "Summary" and "Risks / Gaps" sections.
* **Request** — the fully assembled review request (objective, numbered queries, requested format, file list) shown
  as rendered markdown for auditability.
* **Files** — a bullet list of every file path that was included in the review (`main_file` + `related_files`,
  deduplicated).

A `.review.md` transcript file is also written alongside the session, containing the context data, raw AI output,
and a completion note with character count.

---

## Documentation Tab

### Configuration

| Field Name | Required/Optional | Type | Description |
|---|---|---|---|
| `main_file` | Optional* | `String?` | Primary file to review (inherited from `FileTaskExecutionConfig`). |
| `related_files` | Optional* | `List<String>?` | Additional files/globs to include in the review. |
| `queries` | Optional** | `List<String>?` | The specific questions/details that must be reported on for the given files. |
| `report_format` | Optional | `String?` | Guidance for report shape, e.g. "markdown table", "bullet list per file", "JSON". |
| `extractContent` | Optional | `Boolean` (default `false`) | Whether to extract and review text content from non-text files (PDF, HTML, etc.). |
| `requireCitations` | Optional | `Boolean` (default `true`) | Whether the report must cite file paths and line numbers for every finding. |
| `task_description` | Optional** | `String?` | Objective/description of the review, inherited from base config. |
| `task_dependencies` | Optional | `List<String>?` | IDs of tasks this task depends on. |

\* At least one of `main_file` or `related_files` must be non-blank (enforced in `validate()`).
\** At least one of `queries` (non-blank) or `task_description` (non-blank) is required.

### Dependencies

`FileReviewTask` has no direct dependency on other Task classes; it consumes files resolved relative to `root` and
uses `ChatAgent` for LLM interaction. It is designed to be a *producer* of context for downstream tasks (e.g. edit or
code-generation tasks) via its returned report string, though this wiring happens at the orchestration level rather
than in the class itself.

### Token Usage

**Medium** — cost scales with the size of the included files (and any extracted document text) plus the number of
queries; output is a bounded, structured report rather than full file rewrites.

---

## Config & Process Tab

### Type Configuration
- `model` — the LLM used for the review (`typeConfig.model`, defaults to `defaultSmart` if unset).
- `taskSettingsClass` — `TaskTypeConfig` (standard task-level settings, e.g. model selection).

### Runtime Configuration
- `main_file` / `related_files` — files under review.
- `queries` / `report_format` — what to answer and how to shape the answer.
- `extractContent` — enables `extractDocumentContent` for non-text files.
- `requireCitations` — toggles the citation requirement in the system prompt.

### Lifecycle

1. **Initialization** — `validate()` ensures at least one file is specified and at least one query or task
   description is present. The chat interface is derived from `typeConfig.model` (or default) and bound to the
   session via `getChildClient`. A transcript file (`*.review.md`) is opened.
2. **Execution** — Runs inside a `Retryable` block:
   - Builds `fileContext` via `getInputFileCode`, extracting document content for non-text files when
     `extractContent` is true (falling back to a note if extraction fails).
   - Builds `reviewRequest` (objective, numbered queries, format, file list).
   - Logs both to the transcript, warns if no readable content was found.
   - Constructs a `ChatAgent` with a system prompt built from `getSystemPrompt()` and calls `answer(...)`.
   - Renders the result into the `Report`/`Request`/`Files` tabs and marks the task complete.
   - Releases a semaphore so the outer `run()` can proceed once the async retry-block finishes.
3. **Error Handling** — The whole flow is wrapped in try/catch: any throwable is logged (`task.error`, SLF4J),
   written to the transcript as a stack trace, and rethrown. The `finally` block always flushes the transcript.
   Retry behavior is delegated to the `Retryable` wrapper, allowing the user to re-run the review without duplicating
   file loading logic.

---

## Integration Tab

### Registering the task

```kotlin
val orchestrationConfig = OrchestrationConfig(
  // ...
  taskTypes = listOf(
    FileReviewTask.FileReview,
    // other TaskType entries
  )
)

val reviewConfig = FileReviewTask.FileReviewTaskExecutionConfigData(
  related_files = listOf("src/**/RetryPolicy.kt"),
  queries = listOf(
    "What is the maximum retry count and backoff strategy?",
    "Which exceptions trigger a retry vs. an immediate failure?"
  ),
  report_format = "markdown table per query",
  requireCitations = true,
  task_description = "Summarize retry/backoff behavior"
)
```

### Prompt segment (planning-time)

```text
FileReview - Read files and report on requested details (read-only; no edits are made)
  * Specify the main file and/or related files (incl. glob patterns) to be read
  * Specify 'queries': the concrete questions / details that must be answered from those files
  * Optionally specify 'report_format' to control the shape of the report
  * Use this to gather facts/context for later tasks instead of modifying code
```

### System prompt (execution-time, paraphrased template)

```text
You are a meticulous code/document reviewer. You are in READ-ONLY mode:
do not propose patches, diffs, or file edits - only report what the files contain.

Answer every requested detail using ONLY the provided file content:
- Address each requested detail explicitly, in the order given, as its own section or row
- Cite the file path and line number(s) supporting each statement   [omitted if requireCitations=false]
- Quote short, relevant snippets (a few lines at most) rather than large blocks
- If the answer cannot be determined from the supplied content, say "Not found in provided files"
- Do not speculate about code that was not supplied; label inferences as assumptions

Finish with:
- A short "Summary" of the key findings
- "Risks / Gaps": anything ambiguous, inconsistent, or missing

Requested details:
- <queries[0]>
- <queries[1]>
...

Preferred output format: <report_format>
```