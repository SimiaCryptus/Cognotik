---
specifies: ../webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/**/*.kt
related:
  - agent_types.md
  - user_interface.md
---

# Cognotik Task Type Implementation Standards & Review Protocol

## 1. Purpose and Scope

This document defines the strict standards for implementing, configuring, and registering `TaskType` entities within the
Cognotik Cognitive Task Planning Framework. It serves as a rubric for agentic review to ensure all tasks are
architecturally sound, planner-compatible, and user-safe.

## 2. Architectural Integrity Review

### 2.1 Registration (`TaskType.kt`)

* **Constructor Registration:** The task must be explicitly registered in the `taskConstructors` lazy map within
  `TaskType.kt`.
* **Companion Object:** The task class should ideally have a companion object exposing a static instance of its
  `TaskType` (e.g., `FileModificationTask.Companion.FileModification`) to ensure type safety during registration.
* **Serialization:** The `TaskType` enum entry must use the `TaskTypeSerializer` and `TaskTypeDeserializer`.
* **Config Mapping:** The `TaskType` definition must correctly map the specific `TaskExecutionConfig` subclass and
  `TaskTypeConfig` subclass.

### 2.2 Configuration Classes

* **Polymorphism:**
    * **Execution Config:** The specific configuration class (extending `TaskExecutionConfig`) must use `@JsonTypeInfo`
      and `@JsonTypeIdResolver` to handle polymorphic deserialization based on the `task_type` field.
    * **Type Config:** The specific settings class (extending `TaskTypeConfig`) must similarly handle polymorphism.
* **Separation of Concerns:**
    * **Execution Config:** Must only contain parameters specific to a *single run* of the task (e.g., "target_file", "
      search_query").
    * **Type Config:** Must only contain global settings for the tool (e.g., "default_model", "api_keys", "
      enabled_features").
* **Mutability & Scripting:**
    * **Mutable Fields:** All fields in both `TaskExecutionConfig` and `TaskTypeConfig` subclasses must be mutable (`var`) and provide sensible default values.
    * **Reasoning:** This is strictly required for interoperability with scripting environments (e.g., Groovy), where configurations are often instantiated and then modified dynamically via property setters.
* **No-Argument Constructor Requirement:**
  * All configuration data classes **must** have a no-argument constructor. This means every field must have a default
    value.
  * **Reasoning:** Jackson deserialization (used by `ParsedAgent` and the planning pipeline) requires a no-arg
    constructor to instantiate objects before populating fields. Without defaults, deserialization will fail at runtime.
  * *Bad:* `class MyConfig(val target_file: String) : TaskExecutionConfig()`
  * *Good:* `class MyConfig(var target_file: String? = null) : TaskExecutionConfig()`
*   **Prompt Configuration:**
    *   **Hardcoding Forbidden:** Do not hardcode prompt templates or system instructions inside the class.
    *   **Config Fields:** Define prompt strings, templates, and formatters within the `TaskTypeConfig`. This allows users to tune the "personality" or specific instructions of a tool without recompiling.

## 3. Planner Compatibility (The "Description" Contract)

The Planner (LLM) relies entirely on text descriptions to understand how to use a task.

### 3.1 Field Annotations

* **Mandatory Description:** Every field in the `TaskExecutionConfig` subclass **must** be annotated with
  `@Description`.
* **Clarity:** Descriptions must explain *what* the field does and *how* the planner should populate it.
    * *Bad:* `@Description("The file")`
    * *Good:* `@Description("The relative path of the file to be modified. Must exist in the working directory.")`

### 3.2 Task Description

* **Purpose:** The `TaskType` definition in `TaskType.kt` must provide a `description` string.
* **Content:** This description must concisely explain the task's capability to the Planner. It should highlight
  expected inputs and the nature of the output.

## 3.3 Structured Output with `ParsedAgent` and Typed Data Classes

Many tasks use `ParsedAgent<T>` to extract structured data from LLM responses. When defining the target data class `T`,
strict adherence to the following rules is required to ensure reliable parsing.

### Data Class Design Rules

1. **Default Values on All Fields (No-Arg Constructor):**
   Every field must have a default value. `ParsedAgent` relies on Jackson deserialization, which instantiates the object
   via a no-arg constructor and then sets properties. A missing default will cause a runtime
   `MissingKotlinParameterException`.
   ```kotlin
   // ✗ BAD — No no-arg constructor; ParsedAgent will fail to deserialize
   data class AnalysisResult(
       val summary: String,
       val issues: List<String>,
       val severity: Int
   )
   // ✓ GOOD — All fields have defaults; safe for ParsedAgent
   data class AnalysisResult(
       var summary: String = "",
       var issues: List<String> = emptyList(),
       var severity: Int = 0
   )
   ```
2. **Use `var`, Not `val`:**
   Fields should be mutable (`var`). This is required for:
  * Jackson property-based deserialization.
  * `ValidatedObject` canonicalization (e.g., trimming strings, clamping ranges).
  * Scripting environment interoperability (Groovy, etc.).
3. **`@Description` on Every Field:**
   The `TypeDescriber` reads `@Description` annotations to generate the schema that tells the LLM what each field means.
   Without descriptions, the LLM must guess from field names alone, which is unreliable for domain-specific semantics.
   ```kotlin
   // ✗ BAD — LLM has no guidance on what these fields mean
   data class RefactorPlan(
       var files: List<String> = emptyList(),
       var strategy: String = ""
   )
   // ✓ GOOD — LLM understands the purpose and constraints of each field
   data class RefactorPlan(
       @Description("List of relative file paths to refactor. Must exist in the working directory.")
       var files: List<String> = emptyList(),
       @Description("The refactoring strategy to apply. One of: 'extract_method', 'inline', 'rename'.")
       var strategy: String = ""
   )
   ```
4. **JSON-Style Field Naming:**
   Use `snake_case` for field names rather than `camelCase`. LLMs produce more reliable JSON when field names follow
   JSON conventions.
   ```kotlin
   // ✗ Avoid: camelCase field names
   var targetFile: String = ""
   // ✓ Prefer: snake_case field names
   var target_file: String = ""
   ```
5. **Nullable Types for Optional Fields:**
   Use nullable types (`String?`) with a `null` default for fields that the LLM may legitimately omit. This prevents the
   parser from failing when the LLM doesn't include an optional field.
   ```kotlin
   data class SearchConfig(
       @Description("The search query string")
       var query: String = "",
       @Description("Optional regex filter to apply to results. Null means no filtering.")
       var regex_filter: String? = null
   )
   ```
6. **Implement `ValidatedObject` for Robustness:**
   LLMs are probabilistic and will occasionally produce out-of-range values, wrong types, or malformed strings. Use
   `ValidatedObject` to canonicalize and recover rather than reject.
   ```kotlin
   data class PaginationConfig(
       @Description("Number of results per page. Must be between 1 and 100.")
       var page_size: Int = 10
   ) : ValidatedObject {
       override fun validate(): String? {
           // Canonicalize: clamp to valid range instead of rejecting
           page_size = page_size.coerceIn(1, 100)
           return null // null means valid
       }
   }
   ```
7. **Nested Types Follow the Same Rules:**
   If your data class contains nested objects, those nested types must also have no-arg constructors, `@Description`
   annotations, and `var` fields.
   ```kotlin
   data class DeploymentPlan(
       @Description("The target environment configuration")
       var environment: EnvironmentConfig = EnvironmentConfig(),
       @Description("List of services to deploy")
       var services: List<ServiceConfig> = emptyList()
   )
   data class EnvironmentConfig(
       @Description("Environment name (e.g., 'staging', 'production')")
       var name: String = "staging",
       @Description("AWS region for deployment")
       var region: String = "us-east-1"
   )
   data class ServiceConfig(
       @Description("The service identifier")
       var service_id: String = "",
       @Description("Docker image tag to deploy")
       var image_tag: String = "latest"
   )
   ```
8. **Avoid `Any` Unless Truly Dynamic:**
   While `Any` types are supported (deserialized as `List`/`Map` by Jackson), prefer explicit types whenever possible.
   Explicit types produce better schemas and more reliable LLM output.

### Using `ParsedAgent` in Task Implementations

When a task needs structured LLM output (e.g., generating a plan, extracting entities, producing a typed report), use
`ParsedAgent<T>` rather than manually parsing raw text.

```kotlin
// Inside a task's run() method:
val smartClient = orchestrationConfig.defaultSmart.getChildClient(task)
val fastClient = orchestrationConfig.defaultFast.getChildClient(task)
val analysisAgent = ParsedAgent(
  clazz = AnalysisResult::class.java,
  prompt = "Analyze the following code and produce a structured report.",
  model = smartClient, // Wrapped exactly once with getChildClient(task)
  parsingChatter = fastClient, // Wrapped exactly once with getChildClient(task)
  describer = YamlDescriber() // Default; generates token-efficient schema
)
val response: ParsedResponse<AnalysisResult> = analysisAgent.answer(
  listOf("Analyze this code:\n```\n$codeContent\n```")
)
val result: AnalysisResult = response.obj
task.add("Found ${result.issues.size} issues with severity ${result.severity}".renderMarkdown())
```

**Key considerations when using `ParsedAgent` in tasks:**

* **`exampleInstance`:** Provide an example instance to improve schema adherence for complex types.
* **`parsingChatter`:** Use a faster/cheaper model for the JSON extraction step to reduce cost.
* **`deserializerRetries`:** Set to 2-3 for production tasks to handle occasional malformed JSON.
* **Error Handling:** Wrap `ParsedAgent` calls in try-catch and apply the Triple Log Rule (UI, SLF4J, Transcript).

## 4. Implementation Standards (`AbstractTask`)

### 4.1 User Safety & Side Effects

* **The "Human-in-the-Loop" Rule:** Any task that performs a side effect (modifies files, runs shell commands, sends
  network requests) **must** require user approval unless `auto-apply` is explicitly enabled in the global config.
* **UI Implementation:** Use `acceptButtonFooter` (or equivalent logic) to render a confirmation button in the UI before
  committing destructive actions.

### 4.2 Input Handling

* **File Context:** When reading files, use `getInputFileContent` from `AbstractTask`. This ensures:
    * Glob patterns are handled correctly.
    * Binary files are treated appropriately.
    * Context limits are respected (via `FileSelectionUtils`).
* **Dependencies:** The task must utilize `getPriorCode(executionState)` to retrieve results from upstream tasks defined
  in `task_dependencies`.
### 4.2.1 API Client Wrapping (The "Wrap Once" Rule)
When a task obtains a `ChatInterface` from the orchestration config (e.g., `orchestrationConfig.defaultSmart`, `orchestrationConfig.defaultFast`, or `orchestrationConfig.instance(model)`), it **must** wrap it exactly once using the `ChatInterface.getChildClient(task: SessionTask)` extension function defined in `SessionTask.kt`.
*   **Purpose:** `getChildClient(task)` creates a child client and attaches a log stream from the current `SessionTask`. This ensures:
    *   API calls are logged to the task's session-specific log file (visible via the "API log" link in the UI).
    *   Client hierarchy is maintained for budget tracking and request attribution.
    *   Each task gets its own isolated logging context.
*   **Exactly Once:** The client must be wrapped exactly once per task. Wrapping zero times means API calls are not logged to the task's transcript. Wrapping more than once creates redundant nested clients and duplicate log streams, inflating log output and wasting resources.
*   **Scope:** Wrap at the point of use within the `run()` method, not in constructors or field initializers, since the `SessionTask` is only available at execution time.
```kotlin
// ✗ BAD — Using the raw API client without wrapping; no task-level logging
override fun run(agent: TaskOrchestrator, messages: List<String>, task: SessionTask, resultFn: (String) -> Unit, orchestrationConfig: OrchestrationConfig) {
    val api = orchestrationConfig.defaultSmart
    api.chat(request) // API call is not logged to the task's session
}
// ✗ BAD — Wrapping twice; duplicate log streams
override fun run(agent: TaskOrchestrator, messages: List<String>, task: SessionTask, resultFn: (String) -> Unit, orchestrationConfig: OrchestrationConfig) {
    val api = orchestrationConfig.defaultSmart.getChildClient(task).getChildClient(task)
    api.chat(request) // Logged twice, nested client hierarchy is wasteful
}
// ✓ GOOD — Wrapped exactly once at the point of use
override fun run(agent: TaskOrchestrator, messages: List<String>, task: SessionTask, resultFn: (String) -> Unit, orchestrationConfig: OrchestrationConfig) {
    val api = orchestrationConfig.defaultSmart.getChildClient(task)
    api.chat(request) // Properly logged to the task's session log
}
// ✓ GOOD — Wrapping when passing to a ParsedAgent or other consumer
override fun run(agent: TaskOrchestrator, messages: List<String>, task: SessionTask, resultFn: (String) -> Unit, orchestrationConfig: OrchestrationConfig) {
    val analysisAgent = ParsedAgent(
        resultClass = AnalysisResult::class.java,
        prompt = "Analyze the code.",
        model = orchestrationConfig.defaultSmart.getChildClient(task),
        parsingChatter = orchestrationConfig.defaultFast.getChildClient(task),
    )
}
```
**Note:** The `OrchestrationConfig.planningActor()` method already follows this pattern internally (calling `defaultSmart.getChildClient(task)` and `defaultFast.getChildClient(task)`). All task implementations must do the same when they access API clients directly.


### 4.3 Output & Feedback

* **Streaming:** The `run` method must utilize the `task: SessionTask` object to stream updates to the UI.
    * Use `task.add()` or `task.complete()` to provide real-time feedback.
* **Transcripts (Detailed Logging):**
    *   **Mandatory:** Transcripts are required for all tasks to enable postmortem diagnostics and user auditing.
    *   **Format:** Transcripts must be valid Markdown.
    *   **Rich Content:**
        *   Use **Mermaid** diagrams for flows or logic visualization.
        *   Use `<details><summary>Label</summary>...content...</details>` blocks for high-volume data (raw JSON, stack traces, large file content) to keep the document readable.
    *   **Lifecycle:** Ensure `transcript?.close()` is called in a `finally` block.
    * **Crucial:** Ensure `transcript?.close()` is called in a `finally` block.
* **Result Function:** The `resultFn` callback must be invoked with the final textual result of the task. This result is
  what downstream tasks will see.
* **Artifacts:** If the task generates files, the output text passed to `resultFn` should list the paths of created
  files to maintain context for the Planner.

### 4.3.1 Output File Convention (The "Single Main File" Rule)

Tasks should, whenever possible, produce a **single main output file** whose path is declared in the `files` field of
the task's `TaskExecutionConfig`. This convention serves multiple purposes:

* **Planner Visibility:** The `files` field is inspected by the Planner and downstream tasks. By declaring the output
  file path upfront, other tasks in the plan can reference it as an input dependency without guessing.
* **User Discoverability:** The UI renders links to files listed in `files`, giving the user a clear artifact to inspect
  after the task completes.
* **Transcript Unification:** For many tasks, the "main output file" *is* the transcript itself. This avoids the
  anti-pattern of producing both a transcript and a separate output file with overlapping content.

#### How It Works

The `AbstractTask` base class provides a `transcriptFile()` method that checks whether the `files` field contains
exactly one file with a `.md` extension. If so, that path is used as the transcript destination. Otherwise, a default
timestamped path under `transcript/` is generated.

```kotlin
// In AbstractTask:
fun transcriptFile(): String = getOutputFile(".md") ?: transcriptFile(taskType)
fun getOutputFile(extension: String): String? = executionConfig?.files?.let { when {
    // Excluded for tasks that define their own file semantics
    executionConfig is RenderErbTemplateTask.RenderErbTemplateTaskExecutionConfig -> null
    executionConfig is AbstractFileTask.FileTaskExecutionConfig -> null
    // If exactly one file matches the extension, use it
    it.filter { it.endsWith(extension) }.size == 1 -> it.first { it.endsWith(extension) }
    else -> null
} }
```

#### Standard Pattern for Task Implementations

When implementing a task that produces a report, analysis, or any textual artifact, follow this pattern:

```kotlin
override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
) {
    // transcriptFile() returns the path from `files` if a single .md file is specified,
    // otherwise falls back to a generated path like "transcript/MyTask_20240101120000.md"
    val transcript = task.newFileOutputStream(transcriptFile())
    try {
        // ... task logic ...
        transcript?.write("# My Task Output\n\n".toByteArray())
        transcript?.write("Results go here...\n".toByteArray())
        // ... more output ...
        resultFn("Task completed. Output written to ${transcriptFile()}")
    } finally {
        transcript?.close()
    }
}
```

#### When the Planner Specifies the Output File

The Planner can control where a task writes its output by populating the `files` field in the execution config. For
example, a plan might specify:

```json
{
    "task_type": "Inquiry",
    "task_description": "Research the authentication options and write a report",
    "files": ["docs/auth_research.md"],
    "task_dependencies": []
}
```

In this case, `transcriptFile()` returns `"docs/auth_research.md"`, and the task's transcript (which *is* the research
report) is written directly to the user-specified location. The user sees a clickable link to `docs/auth_research.md` in
the UI, and downstream tasks can reference it.

#### Guidelines

| Scenario                               | Recommended Approach                                                                                                            |
|:---------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------|
| Task produces a single report/analysis | Declare one `.md` file in `files`; use `transcriptFile()` as the output path                                                    |
| Task modifies existing source files    | Use `files` to list the target source files (not the transcript); generate a separate transcript via `transcriptFile(taskType)` |
| Task produces multiple artifacts       | List all artifacts in `files`; use `transcriptFile(taskType)` for the process log                                               |
| Task produces no file artifacts        | Leave `files` empty; transcript is auto-generated under `transcript/`                                                           |
#### Theming Auxiliary Output Around the Primary Filename
When a task produces multiple artifacts (images, data files, sub-reports) in addition to its primary output, the primary output filename declared in `files` should be used as the **naming theme** for all related artifacts. This creates a cohesive, discoverable output structure.
**Pattern:** Strip the extension from the primary output file and use the resulting base name to derive:
1. **A directory** for auxiliary artifacts (images, generated assets, intermediate files).
2. **Companion data files** with the same base name but different extensions or suffixes.
**Example from `ComicBookGenerationTask`:**
If the planner specifies `files: ["my_story.md"]`, the task derives:
- `dataDir = "my_story"` — a directory for character images (`my_story/char_Hero.png`) and page strips (`my_story/page_1_row_1.png`)
- `dataFile = "my_story.comic.json"` — structured metadata saved alongside the primary markdown output
```kotlin
// Deriving themed paths from the primary output file
val dataDir = (getOutputFile(".md")?.let {
    if (it.endsWith(".md")) it.removeSuffix(".md") else null
} ?: "comic").apply {
    val dir = task.resolveUserFile(this)
    if (dir != null && !dir.exists()) dir.mkdirs()
}
val dataFile = getOutputFile(".md")?.let {
    if (it.endsWith(".md")) it.removeSuffix(".md") + ".comic.json" else null
} ?: "comic_book.json"
```
**Why this matters:**
- **User discoverability:** All artifacts related to a task are grouped under a predictable name. A user who sees `my_story.md` in their workspace can intuit that `my_story/` contains the images and `my_story.comic.json` contains the structured data.
- **Planner context:** Downstream tasks can predict artifact locations based on the declared output file, enabling reliable cross-task references.
- **Cleanup:** Themed naming makes it trivial to identify and remove all artifacts from a specific task run.
**Guidelines for themed output:**
| Artifact Type | Naming Convention | Example (primary file: `report.md`) |
|:---|:---|:---|
| Auxiliary directory | `{base}/` | `report/` |
| Generated images | `{base}/{descriptive_name}.png` | `report/chart_revenue.png` |
| Structured data | `{base}.{task_suffix}.json` | `report.analysis.json` |
| Sub-reports | `{base}/{section_name}.md` | `report/appendix_a.md` |
**Fallback:** Always provide a sensible default when `getOutputFile()` returns `null` (i.e., when the planner didn't specify a primary file). The comic book task falls back to `"comic"` for the directory and `"comic_book.json"` for the data file.


#### Anti-Patterns

* **Ignoring `files` entirely:** Writing output to a hardcoded or random path while `files` specifies a destination. The
  Planner and downstream tasks will look for the file at the declared path and find nothing.
* **Duplicate output:** Writing the same content to both a transcript file and a separate output file. Use
  `transcriptFile()` to unify them when the transcript *is* the deliverable.
* **Not closing the stream:** Always close the `FileOutputStream` in a `finally` block. An unclosed stream can result in
  truncated output files.
* **Unthemed auxiliary files:** Generating auxiliary artifacts with names unrelated to the primary output file (e.g., writing to `output_images/img1.png` when the primary file is `my_story.md`). This breaks discoverability and makes it impossible for downstream tasks or users to associate artifacts with their source task.
* **Missing fallback defaults:** Relying on `getOutputFile()` without a fallback. If the planner doesn't specify a file, the task should still produce coherently named output using a hardcoded default base name.

* **Markdown Rendering:**
    * **Extension Method:** When sending content to the UI (via `task.add` or `task.complete`), always use the `String.renderMarkdown` extension method (e.g., `myString.renderMarkdown`).
### 4.5 Concurrency & Threading
*   **Offloading:** Do not block the main execution thread with heavy computations or I/O.
*   **Standard Pools:**
    *   **Async Processing:** Access `task.ui.pool` (ExecutorService) for heavy lifting.
    *   **Scheduling:** Access `task.ui.scheduledThreadPoolExecutor` for delayed checks, timeouts, or periodic polling.
*   **Logging:** Ensure runtime progress of async threads is logged to SLF4J.
### 4.6 Exception Handling
*   **The "Triple Log" Rule:** All exceptions must be captured and logged to three destinations before rethrowing or recovering:
    1.  **UI:** `task.error(e)` (Visual feedback for the user).
    2.  **SLF4J:** `log.error("Context...", e)` (System logs).
    3.  **Transcript:** Write a `<details>` block containing the stack trace to the transcript file.
*   **Recovery:** If the task can recover, log the recovery action. If not, rethrow to let the Orchestrator handle the failure state.
    * **Capabilities:** This utility automatically handles:
        * Mermaid diagram generation (converting code blocks to SVGs).
        * Tabbed views for complex outputs (separating Source vs. Rendered view).
        * HTML sanitization and styling.

### 4.4 Prompt Engineering (`promptSegment`)

* **Context:** The `promptSegment()` implementation must return a string that effectively primes the LLM for this
  specific task.
* **Dynamic Type Configuration:** The prompt must reflect the global settings defined in `typeConfig`.
    * *Example:* If `typeConfig` defines a specific runtime (e.g., "Python" vs "Groovy"), the prompt must explicitly
      state: "You are a Python interpreter..."
    * This ensures the LLM generates content compatible with the specific tool instance configured by the user.


## 5. Review Checklist for Agents

When reviewing a specific Task file (e.g., `MyNewTask.kt`), apply the following checks:

| Check ID | Category     | Requirement                                                                                              | Pass/Fail Criteria                                                                                                                                  |
|:---------|:-------------|:---------------------------------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------|
| **R1**   | **Config**   | Are all `TaskExecutionConfig` fields annotated with `@Description`?                                      | **Fail** if any public field lacks description.                                                                                                     |
| **R2**   | **Safety**   | Does the task modify state (files/system)? If yes, is there an approval mechanism?                       | **Fail** if `File.write` or `ProcessBuilder` is used without `acceptButtonFooter` or user prompt.                                                   |
| **R3**   | **UI**       | Does the task provide visual feedback via `SessionTask`?                                                 | **Fail** if the task runs silently until completion.                                                                                                |
| **R4**   | **Context**  | Does the task handle `task_dependencies`?                                                                | **Fail** if upstream data is ignored.                                                                                                               |
| **R5**   | **Registry** | Is the task registered in `TaskType.kt`?                                                                 | **Fail** if the `TaskType` enum or constructor map is missing the entry.                                                                            |
| **R6**   | **Docs**     | Is there a `tooltipHtml` or `description` provided in the `TaskType` definition?                         | **Fail** if null or empty.                                                                                                                          |
| **R7**   | **Debug**    | Is the transcript used with `<details>` for verbose data?                                                | **Fail** if raw dumps clutter the main view or if transcript is missing.                                                                            |
| **R8**   | **Async**    | Are heavy ops offloaded to `task.ui.pool`?                                                               | **Fail** if `Thread.sleep` or blocking I/O occurs on the main thread.                                                                               |
| **R9**   | **Data**     | Do all `ParsedAgent` target classes have no-arg constructors (all fields defaulted)?                     | **Fail** if any field lacks a default value.                                                                                                        |
| **R10**  | **Data**     | Are all fields in data classes annotated with `@Description`?                                            | **Fail** if any public field used in LLM schema generation lacks `@Description`.                                                                    |
| **R11**  | **Data**     | Are data class fields `var` (not `val`)?                                                                 | **Fail** if `val` is used on fields that need deserialization or canonicalization.                                                                  |
| **R12**  | **Data**     | Do data class field names use `snake_case`?                                                              | **Warn** if `camelCase` is used; **Fail** if names are ambiguous or misleading.                                                                     |
| **R13**  | **API**      | Are all `ChatInterface` instances obtained from config wrapped exactly once with `getChildClient(task)`? | **Fail** if raw config clients are used without wrapping, or if wrapped more than once.                                                             |
| **R14**  | **Output**   | Does the task use `transcriptFile()` for its main output when producing a single file artifact?          | **Warn** if a hardcoded path is used when `files` contains a single `.md` entry. **Fail** if `files` declares a path but the task writes elsewhere. |
| **R15**  | **Output**   | Is the transcript `FileOutputStream` closed in a `finally` block?                                        | **Fail** if the stream can be left open on exception paths.                                                                                         |

## 6. Example: Compliant Task Structure

```kotlin
// 1. Configuration with Descriptions
class ExampleTaskConfig(
    @Description("The target file path")
    var path: String? = null
) : TaskExecutionConfig()

// 1b. Type Configuration (Global Settings)
class ExampleTypeConfig(
    var operationMode: String = "standard",
    var promptTemplate: String = "Perform the example operation in '{mode}' mode."
) : TaskTypeConfig()


// 2. Implementation
class ExampleTask(
    config: OrchestrationConfig,
    taskConfig: ExampleTaskConfig?
) : AbstractTask<ExampleTaskConfig, ExampleTypeConfig>(config, taskConfig) {
    private val log = org.slf4j.LoggerFactory.getLogger(javaClass)

    // 3. Dynamic Prompting using TypeConfig
    override fun promptSegment(): String {
        return typeConfig?.promptTemplate?.replace("{mode}", typeConfig?.operationMode ?: "standard") ?: ""
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask, // 3. UI Handle
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        // 4. Wrap API clients exactly once with getChildClient(task)
        val smartApi = orchestrationConfig.defaultSmart.getChildClient(task)
        val fastApi = orchestrationConfig.defaultFast.getChildClient(task)
        
        // 5. Detailed Logging via Transcript
        // Uses the path from `files` if a single .md is specified; otherwise auto-generates
        val transcript = task.newFileOutputStream(transcriptFile())
        
        try {
            // 6. Offload heavy work to session pool
            task.ui.pool.submit {
                try {
                    log.info("Starting ExampleTask analysis...")
                    
                    // 7. Dependency Handling
                    val context = getPriorCode(agent.executionState)
                    transcript?.write("# Analysis\n<details><summary>Context Data</summary>\n\n```\n$context\n```\n</details>\n".toByteArray())

                    // 8. Streaming Feedback to UI
                    task.add("Analyzing context...".renderMarkdown())

                    // 9. Use wrapped API client for LLM calls
                    // smartApi.chat(request) // Properly logged to task session

                    // 10. Safety Check for Side Effects
                    val output = "Proposed Change"
                    
                    // Switch back to UI thread for interaction if needed, or handle logic here
                    acceptButtonFooter(task.ui) {
                        File(executionConfig?.path).writeText(output)
                        transcript?.write("## Action\nWrote to file.\n".toByteArray())
                        log.info("ExampleTask completed successfully.")
                        resultFn("File updated.")
                    }
                } catch (e: Exception) {
                    // 9. Triple Log Exception Handling
                    task.error(e)
                    log.error("Error in ExampleTask", e)
                    transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                    throw e
                }
            }
        } finally {
            transcript?.close()
        }
    }

    companion object {
        // 8. Type Definition
        val Example = TaskType(
            "ExampleTask",
            "File Operations",
            ExampleTaskConfig::class.java,
            ExampleTypeConfig::class.java,
            description = "Updates a file with example data."
        )
    }
}
```

---

# Best Practices: Handling `autoFix` and User Oversight

In the Cognotik environment, `autoFix` is the toggle between **Autonomous Mode** (agent-driven) and **Interactive Mode** (human-in-the-loop). Proper implementation ensures that side effects (file writes, code execution) are safely guarded while providing a seamless UI for manual review.

## 1. The Core Conditional Pattern
Every task that performs a side effect should follow this structural template:

```kotlin
if (orchestrationConfig.autoFix) {
    // 1. Perform side effect immediately
    // 2. Log to transcript
    // 3. Release semaphore/call resultFn
} else {
    // 1. Display proposed changes/logic to UI
    // 2. Provide interactive controls (Discussable, hrefLink)
    // 3. Wait for user to trigger completion (acceptButtonFooter)
}
```

## 2. Guarding Logic with `Discussable`
Use `Discussable` when the output of a task is a "thought product" (like a plan, a report, or a design) that the user might want to refine before it becomes the "official" result of the task.

*   **When to use:** `DiscussionTask`, Planning phases, or complex architectural decisions.
*   **Best Practice:** In interactive mode, wrap the AI's response logic in a `Discussable` block. This allows the user to provide feedback, which triggers a `reviseResponse` call to the LLM.

```kotlin
// Example from DiscussionTask.kt
if (orchestrationConfig.autoFix) {
    insightActor.answer(input) // Direct execution
} else {
    Discussable(
        task = task,
        initialResponse = { input -> insightActor.answer(input) },
        reviseResponse = { messages -> insightActor.respond(messages) },
        // ...
    ).call() // Interactive loop
}
```

## 3. Guarding Side Effects with `hrefLink`
Side effects like running code or applying specific patches should be bound to UI triggers when `autoFix` is disabled.

*   **When to use:** Executing shell commands, running scripts, or applying specific file diffs.
*   **Best Practice:** Use `ui.hrefLink` to create buttons that perform the action only upon a click.

```kotlin
// Example from RunCodeTask.kt
if (!orchestrationConfig.autoFix) {
    task.add(ui.hrefLink("▶ Run", "play-button") {
        execute(task, response) // Side effect happens ONLY on click
    })
}
```

## 4. Finalizing Tasks with `acceptButtonFooter`
When a task involves multiple potential changes (like `FileModificationTask`), the user needs a way to signal that they are satisfied with the state of the workspace and ready to move to the next task in the plan.

*   **When to use:** At the end of any task where `autoFix` is false and a `Semaphore` is blocking the orchestrator.
*   **Best Practice:** Append the `acceptButtonFooter` to the final markdown output. This button should release the semaphore or call the `resultFn`.

```kotlin
// Example from FileModificationTask.kt
val footer = acceptButtonFooter(task.ui) {
    task.complete()
    semaphore.release() // Unblocks the TaskOrchestrator
}
task.complete(codeResult.renderMarkdown + footer)
```

## 5. Transcript Logging
Regardless of whether `autoFix` is enabled, all actions—both AI-generated and user-triggered—must be written to the `transcript`.

*   **Auto Mode:** Log "Auto-applying changes..."
*   **Manual Mode:** Log "User Action: [Button Name]" or "User Feedback: [Text]".

This ensures that the final log of the session is a complete record of how the current state was reached.

## 6. Summary Table

| Feature | `autoFix == true` | `autoFix == false` |
| :--- | :--- | :--- |
| **Execution** | Immediate | Guarded by `hrefLink` or `Discussable` |
| **User Feedback** | Skipped | Enabled via `ui.textInput` or `Discussable` |
| **Completion** | Automatic `semaphore.release()` | Manual via `acceptButtonFooter` |
| **File Diffs** | `shouldAutoApply = true` | Manual "Apply" links |
| **Transcript** | Logs AI intent + result | Logs AI intent + User actions |

## 7. Checklist for New Tasks
1. [ ] Does the task modify files or run code?
2. [ ] If `autoFix` is false, is there a `Semaphore` or blocking mechanism to wait for the user?
3. [ ] Are side effects wrapped in a `hrefLink` handler for manual mode?
4. [ ] Is there a `textInput` or `Discussable` to allow the user to correct the AI?
5. [ ] Does the manual path end with a clear "Continue" or "Accept" button?
6. [ ] Are all paths (Auto and Manual) logging to the `transcript`?


# The Cognotik Task Definition Framework: A Guide to Self-Describing Task Types

In the Cognotik framework, a **Task** is not just a unit of code execution; it is a semantic entity that advertises its own capabilities, requirements, and usage patterns to the AI Planner.

This guide details the architecture of `TaskType` and its associated components, explaining how to create tasks that are "self-describing" so that the Cognitive Orchestrator can intelligently employ them to solve complex user problems.

---

## 1. The Anatomy of a Self-Describing Task

A Task is defined by the convergence of four distinct elements. When you create a new tool, you are essentially defining a contract between the **Code** (Logic) and the **Cognitive Model** (Planner).

### A. The Definition (`TaskType`)
Located in `TaskType.kt`, this is the registry entry. It binds the logic, configuration, and metadata together.

*   **Name:** The unique identifier (e.g., "Brainstorming").
*   **Category:** Grouping for the UI and Planner (e.g., "Reasoning", "File Operations").
*   **Classes:** References to the Logic Class (`AbstractTask`) and Data Class (`TaskExecutionConfig`).
*   **Description:** A high-level summary used by the Planner to decide *if* this tool is relevant.
*   **Tooltip HTML:** A user-facing description shown in the Web UI.

### B. The Configuration (`TaskExecutionConfig`)
This data class defines the **Inputs**. It uses Java/Kotlin annotations to describe itself to the LLM.

*   **`@Description` Annotations:** Every field in this class must be annotated. The `TypeDescriber` reads these strings to generate the schema definition that tells the LLM exactly what arguments to provide.
*   **Validation:** By implementing `ValidatedObject`, the config ensures the LLM didn't hallucinate invalid parameters (e.g., `target_option_count` must be between 3 and 20).

### C. The Logic (`AbstractTask`)
This is the implementation. It contains two critical components:
1.  **`run()`**: The actual code execution.
2.  **`promptSegment()`**: The "Sales Pitch" to the Planner.

### D. The "Sales Pitch" (`promptSegment`)
This method returns a string that is injected into the System Prompt of the Planning Agent. It must concisely explain:
*   **What** the task does.
*   **When** to use it.
*   **How** to configure it (briefly).

---

## 2. The "Self-Describing" Workflow

The power of this framework lies in how these facets interact during the Planning Phase.

1.  **Introspection:** When `OrchestrationConfig.planningActor` is called, the system iterates through all enabled `TaskType`s.
2.  **Schema Generation:** It uses reflection on the `executionConfigClass` to build a JSON schema, using the `@Description` text to explain fields to the LLM.
3.  **Prompt Assembly:** It calls `promptSegment()` on every task instance.
4.  **Context Injection:** The Planner receives a prompt containing:
  *   "The available task types are:"
  *   [List of all `promptSegment` outputs]
  *   [JSON Schema of all `TaskExecutionConfig` objects]

**Result:** The LLM understands how to use a Java/Kotlin class it has never seen before, simply by reading the metadata you provided in the code.

---

## 3. Creating and Maintaining a Task

To create a new task, follow this standard procedure.

### Step 1: Define the Input Data
Create a class extending `TaskExecutionConfig`. Use `@Description` heavily.

```kotlin
class MyNewTaskConfig(
  @Description("The primary file to analyze. Must be a relative path within the working directory.")
  var target_file: String? = null,

  @Description("How aggressive the analysis should be. One of: 'low', 'medium', 'high'.")
  var intensity: String = "medium"
) : TaskExecutionConfig(task_type = "MyNewTask")
```

### Step 2: Implement the Logic
Create a class extending `AbstractTask`.

```kotlin
class MyNewTask(
    config: OrchestrationConfig,
    task: MyNewTaskConfig?
) : AbstractTask<MyNewTaskConfig, TaskTypeConfig>(config, task) {

    override fun promptSegment(): String = buildString {
        appendLine("MyNewTask - Analyzes a specific file with configurable intensity.")
        appendLine("  ** Specify the target_file path")
        appendLine("  ** Set intensity (default: medium)")
        appendLine("  ** Use this when the user asks for deep code inspection.")
    }

    override fun run(...) {
        // Implementation logic here
        // 1. Validate inputs
        // 2. Perform work
        // 3. Write to Transcript (Audit)
        // 4. Update UI (User)
        // 5. Return result string (Planner Context)
    }
}
```

### Step 3: Register the Task
Add the entry to the `TaskType` companion object in `TaskType.kt`.

```kotlin
val MyNewTask = TaskType(
    "MyNewTask",
    "Analysis",
    MyNewTask::class.java,
    MyNewTaskConfig::class.java,
    TaskTypeConfig::class.java,
    "Performs deep analysis on files",
    "<ul><li>Analyzes code structure</li><li>Reports complexity</li></ul>"
)
```

---

## 4. Target Audiences and Objectives

When writing the self-describing facets, you are writing for four distinct audiences simultaneously.

| Audience              | Facet             | Objective                                              | Best Practice                                                                     |
|:----------------------|:------------------|:-------------------------------------------------------|:----------------------------------------------------------------------------------|
| **The Planner (LLM)** | `promptSegment()` | To convince the LLM to select this tool for the plan.  | Use bullet points. Be imperative. Highlight specific use cases ("Useful for..."). |
| **The Planner (LLM)** | `@Description`    | To ensure the LLM fills the JSON parameters correctly. | Be precise about data types and constraints (e.g., "Must be a valid file path").  |
| **The User**          | `tooltipHtml`     | To explain to the human what the tool does in the UI.  | Use HTML lists. Keep it non-technical and benefit-focused.                        |
| **The Developer**     | `validate()`      | To prevent runtime errors from bad LLM output.         | Enforce constraints strictly (e.g., `require(count > 0)`).                        |

---

## 5. Interaction with Cognitive Planning

The interaction between Task Types and Cognitive Planning is bidirectional.

### A. Planner -> Task (Instantiation)
The Planner (e.g., `WaterfallMode` or `ConversationalMode`) generates a JSON plan.
*   The `task_type` field in JSON maps to the `TaskType` enum.
*   The `Orchestrator` uses `TaskType.getImpl()` to instantiate the specific `AbstractTask`.
*   The JSON parameters are deserialized into the `TaskExecutionConfig`.

### B. Task -> Planner (Feedback Loop)
Once a task completes via `run()`, it calls `resultFn(String)`.
*   **Success:** The output string is fed back into the context window of the Planner.
*   **Failure:** If `task.error()` is called, the exception message is fed back.
*   **Adaptation:** The Planner reads this result to decide the *next* step. For example, if `FileSearchTask` returns "No files found," the Planner might decide to run `BrainstormingTask` to generate new ideas instead of proceeding to `FileModificationTask`.

### C. Hierarchical Planning (`SubPlanningTask`)
Tasks can be recursive. A `SubPlanningTask` is a specific `TaskType` that spins up a *new* Orchestrator.
*   It allows a "Parent Plan" to delegate a complex objective to a "Child Plan."
*   The Child Plan has its own set of allowed `TaskType`s, defined in its configuration.

---

## 6. Maintenance Best Practices

*   **Keep Prompts Concise:** The `promptSegment` consumes context tokens. Be brief. Do not explain *how* the code works, only *what* it does and *what* it needs.
2.  **Validate Aggressively:** LLMs are probabilistic. They *will* occasionally send `null` for a non-nullable field or "five" instead of `5`. Use the `ValidatedObject` interface to catch these early and return clear error messages so the LLM can self-correct.
3.  **IO Discipline:** Follow the "Triple Log Rule":
  * **UI:** Visual updates for the user.
  * **Transcript:** Detailed data dumps for audit.
  * **ResultFn:** Summarized, markdown-formatted text for the LLM's next thought process.

4. **Output File Discipline:**
  * **Declare Before You Write:** If your task produces a file artifact, declare its path in the `files` field of the
    execution config so the Planner and UI can find it.
  * **Unify Transcript and Output:** When the task's deliverable is a text document (report, analysis, research notes),
    use `transcriptFile()` to write the deliverable as the transcript itself, avoiding duplicate files.
  * **Theme Auxiliary Output:** When producing multiple artifacts, derive directory names and companion file names from
    the primary output file's base name (see "Theming Auxiliary Output Around the Primary Filename" above). Always
    provide a sensible fallback default when no primary file is specified.
  * **Close Streams:** Always close `FileOutputStream` in a `finally` block. Use the pattern:
    `val transcript = task.newFileOutputStream(transcriptFile())` at the top of `run()`, with `transcript?.close()` in
    `finally`.
5. **Multi-Line String Literals:**
    * **`trimMargin` / `trimIndent` Forbidden:** Do not use `trimMargin()` or `trimIndent()` on multi-line string literals (triple-quoted strings). When these strings are included in larger prompt compositions or multiline template inclusions, the margin/indent stripping interacts unpredictably with the surrounding indentation context, producing malformed output.
    * **Use `buildString` Instead:** Construct multi-line strings using `buildString { ... }` with explicit `appendLine()` calls. This makes the output deterministic regardless of how the code is indented or where the string is included.
    * **Use `String.indent(spaceTxt)` for Indentation:** When a block of text needs to be indented (e.g., for embedding inside a larger template), use the `String.indent(spaceTxt)` extension function rather than relying on source-code indentation tricks.
    * **Examples:**
    ```kotlin
    // ✗ BAD — trimIndent will break when this string is composed into a larger prompt
    override fun promptSegment(): String = """
        MyTask - Does something useful.
          ** Specify the target_file path
          ** Set intensity (default: medium)
    """.trimIndent()

    // ✗ BAD — trimMargin has the same fragility problem
    override fun promptSegment(): String = """
        |MyTask - Does something useful.
        |  ** Specify the target_file path
        |  ** Set intensity (default: medium)
    """.trimMargin()

    // ✓ GOOD — deterministic output, safe for composition
    override fun promptSegment(): String = buildString {
        appendLine("MyTask - Does something useful.")
        appendLine("  ** Specify the target_file path")
        appendLine("  ** Set intensity (default: medium)")
    }

    // ✓ GOOD — using indent() to nest content inside a template
    val details = buildString {
        appendLine("- Item A")
        appendLine("- Item B")
    }
    val wrapped = buildString {
        appendLine("Available options:")
        append(details.indent("    "))
    }
    ```
6. **Deprecation:** If changing a `TaskExecutionConfig` field, remember that old plans or saved sessions might fail to
   deserialize. Handle backward compatibility or version your tasks if necessary.
7. **Data Class Hygiene:** When modifying data classes used with `ParsedAgent`:
  * Never remove a default value from a field.
  * Never change a `var` to a `val`.
  * When adding new fields, always provide a default value to maintain backward compatibility.
  * When renaming fields, consider adding a `@JsonAlias` for the old name to support deserialization of existing plans.