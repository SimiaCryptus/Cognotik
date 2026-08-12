# AbstractFileTask

**File-context resolution base for all filesystem-aware tasks.**

`Base Class` · `Side-Effect Safe` · `No Model Requirement`

`AbstractFileTask` is not a runnable task itself — it is the shared foundation every file-manipulating task
(`FileEditTask`, `FileCreateTask`, etc.) extends to gain glob-based file resolution, ignore-pattern filtering,
binary/text detection, and LLM-ready file formatting. This page documents the contract subclasses inherit.

---

## Reality Check

**Input configuration** (fields defined on `FileTaskExecutionConfig`, inherited by every concrete subtype):

```json
{
  "task_type": "FileEdit",
  "task_description": "Refactor the pagination logic in DocumentReader",
  "related_files": [
    "src/main/kotlin/com/simiacryptus/cognotik/docs/*.kt",
    "src/main/kotlin/com/simiacryptus/cognotik/plan/tools/file/AbstractFileTask.kt"
  ],
  "task_dependencies": ["task_analyze_docs"],
  "state": "Pending"
}
```

**Rendered output** (what the user sees is produced by the *subclass*, but `AbstractFileTask` is what feeds it):
the resolved file set is rendered into the task's prompt as a sequence of Markdown-fenced blocks, one per file:

```
# src/main/kotlin/com/simiacryptus/cognotik/docs/DocumentReader.kt

​```
...file contents...
​```
```

In the UI, this appears as a collapsible "Context Files" panel listing every resolved path before the task's own
diff/output section — giving the user visibility into exactly what was fed to the model.

---

## Documentation Tab

### Configuration Table

| Field Name       | Required/Optional | Type            | Description                                                                                          |
|-------------------|--------------------|------------------|--------------------------------------------------------------------------------------------------------|
| `related_files`   | Optional            | `List<String>`   | Additional files (paths or glob patterns) used to inform the change, including files created by prior tasks. |
| `task_type`        | Optional (inherited)| `String?`        | Discriminator identifying the concrete task subtype.                                                  |
| `task_description` | Optional (inherited)| `String?`        | Human-readable description of the task's intent.                                                       |
| `task_dependencies`| Optional (inherited)| `List<String>?`  | IDs of tasks that must complete before this one runs.                                                  |
| `state`            | Optional (inherited)| `TaskState`      | Lifecycle state; defaults to `Pending`.                                                                |

Note: `main_file` (referenced in `getInputFiles()`) is expected on concrete subclasses' execution configs, not on
`FileTaskExecutionConfig` itself — every subtype must supply it for file resolution to include the primary target.

### Dependencies

`AbstractFileTask` has no hard dependency on other task types, but it is the common ancestor for all file-oriented
tasks in the plan system (edit, create, patch, etc.). It relies on:

- `FileSelectionUtils` — glob matching and `.llmignore`-style exclusion.
- `PaginatedDocumentReader` / `getDocumentReader()` — non-text document extraction (PDF, DOCX, XLS, PPT).

### Token Usage

**Medium** — cost scales directly with the number and size of files resolved via `related_files` and `main_file`
globs. A narrow glob keeps usage low; a broad wildcard (`**/*.kt`) can pull in large amounts of context.

---

## Config & Process Tab

**Type Configuration** (fixed per task-type, set at plan-authoring time):
- `isIgnored(file)` extension-based filtering rules (binary/media formats excluded by default; PDFs/DOCs allowed but
  routed through document extraction).
- `isTextFile(file)` whitelist of extensions treated as raw source text vs. document-extracted content.

**Runtime Configuration** (resolved per execution):
- `related_files` glob patterns are expanded against `root` at execution time via `FileSelectionUtils.filteredWalk`.
- `codeFiles` cache (`mutableMapOf<Path, String>`) allows subclasses to inject already-loaded/modified content instead
  of re-reading from disk, ensuring consistency across multi-step edits within one task run.

### Lifecycle Walkthrough

1. **Initialization:** Subclass constructs with an `OrchestrationConfig` and its typed execution config. No files are
   touched yet.
2. **Execution:**
   - `getInputFiles()` resolves each glob/path in `related_files` + `main_file` against the project root.
   - Each candidate is checked for existence, directory status, and ignore rules (`isIgnored`); ignored/binary
     patterns are filtered out.
   - Results are deduplicated and sorted deterministically.
   - `getInputFileCode()` maps each resolved `File` through `formatFileForLLM`, producing the Markdown-fenced context
     block. Text files are read directly (or pulled from the `codeFiles` cache); non-text files go through
     `extractDocumentContent`.
3. **Error Handling:**
   - File-read failures inside `formatFileForLLM` are caught, logged as a warning (`log.warn("Error reading file...")`),
     and degrade gracefully to an empty string rather than aborting the whole task.
   - Document extraction failures fall back from `getDocumentReader()` to raw `readText()`, and finally to an inline
     error string (`"Error reading file: ..."`) if even that fails — the task never throws on a single bad file.
   - `getOutputFile(extension)` always returns `null`, signaling this base class produces no standalone output
     artifact; that responsibility belongs entirely to subclasses.

---

## Integration Tab

### Registering a subclass task

```kotlin
class FileEditExecutionConfig(
    task_type: String? = "FileEdit",
    task_description: String? = null,
    var main_file: String = "",
    related_files: List<String>? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
) : AbstractFileTask.FileTaskExecutionConfig(
    task_type, task_description, related_files, task_dependencies, state
)

class FileEditTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: FileEditExecutionConfig?
) : AbstractFileTask<FileEditExecutionConfig>(orchestrationConfig, planTask) {
    override fun run(/* ... */) {
        val context = getInputFileCode()
        // build prompt using `context`, invoke model, apply diff...
    }
}
```

### Prompt Segment

The context block injected into the LLM prompt, generated per file by `formatFileForLLM`:

```
# {relativePath}

```
{file contents or extracted document text}
```
```

Multiple files are concatenated with a blank line between blocks via `getInputFileCode()`, giving the model a
single flat "file listing" section it can reference by path in its response.