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

### 4.3 Output & Feedback

* **Streaming:** The `run` method must utilize the `task: SessionTask` object to stream updates to the UI.
    * Use `task.add()` or `task.complete()` to provide real-time feedback.
* **Transcripts (Detailed Logging):**
    * For complex tasks, especially those with large inputs/outputs (like code execution), do not dump everything into
      the main UI.
    * Use `val transcript = task.transcript()` to create a linked Markdown file.
    * Write detailed logs (inputs, raw outputs, stack traces) to this stream.
    * **Crucial:** Ensure `transcript?.close()` is called in a `finally` block.
* **Result Function:** The `resultFn` callback must be invoked with the final textual result of the task. This result is
  what downstream tasks will see.
* **Artifacts:** If the task generates files, the output text passed to `resultFn` should list the paths of created
  files to maintain context for the Planner.

### 4.4 Prompt Engineering (`promptSegment`)

* **Context:** The `promptSegment()` implementation must return a string that effectively primes the LLM for this
  specific task.
* **Dynamic Type Configuration:** The prompt must reflect the global settings defined in `typeConfig`.
    * *Example:* If `typeConfig` defines a specific runtime (e.g., "Python" vs "Groovy"), the prompt must explicitly
      state: "You are a Python interpreter..."
    * This ensures the LLM generates content compatible with the specific tool instance configured by the user.

## 5. Review Checklist for Agents

When reviewing a specific Task file (e.g., `MyNewTask.kt`), apply the following checks:

| Check ID | Category     | Requirement                                                                        | Pass/Fail Criteria                                                                                |
|:---------|:-------------|:-----------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------|
| **R1**   | **Config**   | Are all `TaskExecutionConfig` fields annotated with `@Description`?                | **Fail** if any public field lacks description.                                                   |
| **R2**   | **Safety**   | Does the task modify state (files/system)? If yes, is there an approval mechanism? | **Fail** if `File.write` or `ProcessBuilder` is used without `acceptButtonFooter` or user prompt. |
| **R3**   | **UI**       | Does the task provide visual feedback via `SessionTask`?                           | **Fail** if the task runs silently until completion.                                              |
| **R4**   | **Context**  | Does the task handle `task_dependencies`?                                          | **Fail** if upstream data is ignored.                                                             |
| **R5**   | **Registry** | Is the task registered in `TaskType.kt`?                                           | **Fail** if the `TaskType` enum or constructor map is missing the entry.                          |
| **R6**   | **Docs**     | Is there a `tooltipHtml` or `description` provided in the `TaskType` definition?   | **Fail** if null or empty.                                                                        |

## 6. Example: Compliant Task Structure

```kotlin
// 1. Configuration with Descriptions
class ExampleTaskConfig(
    @Description("The target file path")
    var path: String? = null
) : TaskExecutionConfig()

// 1b. Type Configuration (Global Settings)
class ExampleTypeConfig(
    val operationMode: String = "standard"
) : TaskTypeConfig()


// 2. Implementation
class ExampleTask(
    config: OrchestrationConfig,
    taskConfig: ExampleTaskConfig?
) : AbstractTask<ExampleTaskConfig, ExampleTypeConfig>(config, taskConfig) {

    // 3. Dynamic Prompting using TypeConfig
    override fun promptSegment(): String {
        val mode = typeConfig?.operationMode ?: "standard"
        return "Perform the example operation in '$mode' mode."
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask, // 3. UI Handle
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        // 4. Detailed Logging via Transcript
        val transcript = task.transcript()
        try {
            // 5. Dependency Handling
            val context = getPriorCode(agent.executionState)
            transcript?.write("# Analysis\nContext: $context\n".toByteArray())

            // 6. Streaming Feedback to UI
            task.add("Analyzing context...")

            // 7. Safety Check for Side Effects
            val output = "Proposed Change"
            acceptButtonFooter(task.ui) {
                File(executionConfig?.path).writeText(output)
                transcript?.write("## Action\nWrote to file.\n".toByteArray())
                resultFn("File updated.")
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