# FileSearch

**Pattern-based search across project files with contextual, grouped results.**

`Side-Effect Safe` · `File` · `No Model Required`

FileSearch scans a set of files (glob patterns), applies a substring or regex search, and returns matched lines
merged with surrounding context lines into readable blocks, grouped by file. It performs no writes and requires no
LLM call — the search itself is deterministic code, not model-generated.

---

## Reality Check

**Input configuration** (`SearchTaskExecutionConfigData`):

```json
{
  "task_type": "FileSearch",
  "task_description": "Find all TODO comments in Kotlin source files",
  "search_pattern": "TODO\\(.*\\)",
  "is_regex": true,
  "context_lines": 2,
  "input_files": ["src/main/kotlin/**/*.kt"],
  "extractContent": false
}
```

**Output (rendered in UI):**

- A summary line: `Found 7 match(es) in 4 file(s).`
- A `TabbedDisplay` with:
    - A **Summary** tab containing the full formatted Markdown search report (headers per file, fenced code blocks
      with line numbers, `>` prefix marking matched lines).
    - One tab per matched file (only if ≤ 20 files matched), each showing:
      ```
      ### Lines 12 - 16

      ```
        10:  fun process() {
      >  12:      // TODO(cleanup) refactor this
         13:      doWork()
      ```
      ```
- A transcript file (`<task>.md`) containing the raw formatted results wrapped in a collapsible `<details>` block.
- If no matches: plain text `No matches found.`

---

## Documentation

### Configuration

| Field Name       | Required/Optional | Type            | Description                                                                 |
|-------------------|--------------------|-----------------|-------------------------------------------------------------------------------|
| `search_pattern`  | Required           | `String`        | The search pattern (substring or regex) to look for in the files.            |
| `is_regex`        | Optional           | `Boolean`       | Whether the search pattern is a regex (`true`) or a plain substring (`false`). Default `false`. |
| `context_lines`   | Optional           | `Int`           | Number of context lines to include before and after each match. Default `2`. |
| `input_files`     | Optional           | `List<String>?` | Specific files or glob patterns to search.                                   |
| `extractContent`  | Optional           | `Boolean`       | Whether to extract/search text content from non-text files (PDF, HTML, etc.) via document extraction. Default `false`. |

### Dependencies

None on other `Task` types — FileSearch is a standalone leaf task. It relies internally on
`FileSelectionUtils.filteredWalk` for glob matching and file-ignore rules, and
`AbstractFileTask.extractDocumentContent` when `extractContent = true`.

### Token Usage

**Low.** FileSearch performs no LLM prompting — it's a pure filesystem/regex operation. The only "token cost" is
downstream: the formatted result text (capped at 500,000 chars) may be consumed by a subsequent LLM-driven task if
chained in a plan.

---

## Config & Process

### Type Configuration

- `TaskTypeConfig` — standard task type settings (no FileSearch-specific type-level config beyond the shared base).

### Runtime Configuration

- `SearchTaskExecutionConfigData` (see table above) — supplied per invocation, including the search pattern, regex
  flag, context window size, target files/globs, and content-extraction toggle.

### Lifecycle

1. **Initialization**
    - `validate()` checks `search_pattern` is non-blank and `context_lines >= 0`; nested validation is delegated via
      `ValidatedObject.validateFields`.
    - Task header is rendered; work is submitted to `task.ui.pool` for async execution.
2. **Execution**
    - Compiles the pattern (`Pattern.quote` wraps substrings; regex used as-is for `is_regex = true`).
    - For each `input_files` glob, walks the filesystem via `FileSelectionUtils.filteredWalk`, respecting
      `.gitignore`-style exclusions (`isIgnored`).
    - Reads each matching file's lines (or extracts text content first if `extractContent` and the file is
      non-text, based on a fixed set of recognized text extensions).
    - Finds all raw line matches, then merges overlapping/adjacent context windows into `DisplayBlock`s to avoid
      duplicated or fragmented output.
    - Formats results into Markdown (`formatSearchResults`), truncating gracefully at ~500,000 characters with a
      truncation notice.
    - Writes a raw-results transcript file, then renders a summary + tabbed per-file breakdown (only when ≤ 20
      files matched, to avoid excessive tab sprawl).
3. **Error Handling**
    - Per-file exceptions (e.g. unreadable/corrupt file) are caught and logged as warnings; that file simply yields
      no blocks, and search continues over remaining files.
    - A top-level try/catch around the whole run reports errors via `task.error(e)`, logs the stack trace, and
      writes an `## Error` section to the transcript.
    - `finally` ensures the transcript is closed and `task.complete()` is always called, regardless of outcome.

---

## Integration

### Registering in an OrchestrationConfig

```kotlin
val searchTask = FileSearchTask(
  orchestrationConfig = orchestrationConfig,
  planTask = FileSearchTask.SearchTaskExecutionConfigData(
    search_pattern = "TODO\\(.*\\)",
    is_regex = true,
    context_lines = 2,
    input_files = listOf("src/main/kotlin/**/*.kt"),
    extractContent = false,
    task_description = "Find all TODO comments in Kotlin source files"
  )
)
```

### Prompt Segment

FileSearch requires no model call at runtime, but exposes this planning-time prompt segment so an orchestrating LLM
knows how to configure it:

```text
FileSearch - Search for patterns in files and provide results with context
* Specify the search pattern (substring or regex)
* Specify whether the pattern is a regex or a substring
* Specify the number of context lines to include
* List files (incl glob patterns) to be searched
```