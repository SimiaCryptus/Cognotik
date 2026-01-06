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
* **Markdown Rendering:**
    * **Extension Method:** When sending content to the UI (via `task.add` or `task.complete`), always use the `String.renderMarkdown` extension method (e.g., `myString.renderMarkdown`).
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
    var operationMode: String = "standard"
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