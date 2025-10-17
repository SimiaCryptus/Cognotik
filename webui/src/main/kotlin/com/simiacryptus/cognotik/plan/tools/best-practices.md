Of course! Based on the provided code examples (`AnalysisTask`, `SocraticDialogueTask`, `CrawlerAgentTask`, `WriteHtmlTask`), here is a detailed guide on the best practices for designing and implementing new task types within this framework.

This guide synthesizes the patterns and principles observed in the code into a set of actionable recommendations.

---

## A Developer's Guide to Designing and Implementing Task Types

The provided code demonstrates a powerful and modular architecture for creating AI-driven tasks. A well-designed `TaskType` is self-contained, configurable, and provides a clear user experience. This guide will walk you through the best practices for creating your own high-quality tasks.

### 1. Core Design Principles

Before writing code, consider these high-level principles that make a task effective and maintainable.

*   **🎯 Single Responsibility Principle (SRP):** A task should do one thing well.
  *   **Good:** `WriteHtmlTask` focuses solely on generating a complete HTML file. `CrawlerAgentTask` focuses on crawling and analyzing web pages.
  *   **Bad:** A single task that crawls the web, writes a report, and then refactors code based on that report. This should be three separate, dependent tasks.

*   **🧩 Modularity and Reusability:** Design tasks that can be chained together in a larger plan. A task should not make assumptions about what ran before it, other than what is explicitly declared in its dependencies.

*   **⚙️ Clear Configuration:** Separate the *how* from the *what*.
  *   **Execution-specific parameters** (the "what," e.g., "what question to ask?") go in `TaskExecutionConfigData`.
  *   **Behavioral settings** (the "how," e.g., "which LLM model to use?") go in `TaskTypeConfig`.

*   **🗣️ Excellent User Experience (UX):** A task is not just a background process; it's an interactive component.
  *   Provide continuous, clear feedback to the user about what is happening.
  *   Organize complex output logically.
  *   Handle errors gracefully and inform the user.

*   **🔒 Safety and Predictability:** Tasks that perform actions with side effects (like writing files) should have safeguards, such as requiring user confirmation (`autoFix: false`).

### 2. The Anatomy of a Task Type

Every task is composed of several key parts. Let's break down how to implement each one effectively.

#### 2.1. Configuration Classes (`TaskExecutionConfigData` and `TaskTypeConfig`)

This is the public API of your task. Make it clear and descriptive.

*   **`TaskExecutionConfigData` (Execution-Specific):** Defines the inputs for a *single run* of the task.
  *   **Best Practices:**
    *   Use clear, descriptive property names (e.g., `inquiry_questions`, `initial_question`, `search_query`).
    *   Use the `@Description` annotation on every property. This is crucial for the system to auto-generate user interfaces and for the orchestrator LLM to understand how to configure your task.
    *   Keep types simple and serializable (lists, strings, booleans, numbers). For complex inputs, like the `content_queries` in `CrawlerAgentTask`, `Any?` is acceptable but should be well-documented.
    *   Provide sensible default values where possible (e.g., `max_depth: Int = 5` in `SocraticDialogueTask`).

*   **`TaskTypeConfig` (Type-Specific):** Defines the configuration for the *task type itself*, which usually remains constant across multiple executions.
  *   **Best Practices:**
    *   Use this for settings that control the task's fundamental behavior, such as `non_interactive` mode in `AnalysisTask` or `seed_method` in `CrawlerAgentTask`.
    *   Include common settings like `model` if the task's behavior is heavily dependent on a specific LLM.
    *   Again, use `@Description` and provide defaults.

#### 2.2. The `TaskType` Companion Object

This object acts as the registration manifest for your task. It tells the system that your task exists and how to use it.

*   **Best Practices:**
  *   **Name:** Give it a clear, `PascalCase` name (e.g., `Analysis`, `SocraticDialogue`).
  *   **Constructor:** Pass the correct `TaskExecutionConfigData` and `TaskTypeConfig` classes to the `TaskType` constructor.
  *   **Description (Short):** The 4th constructor argument is a concise, one-line description. This is often shown in dropdowns or lists.
  *   **Description (Long):** The 5th argument is a detailed HTML description.
    *   Use `<ul>` and `<li>` tags to create a bulleted list of features and capabilities.
    *   Clearly explain what the task does, what its primary use cases are, and what kind of output to expect. This is the main help text for users.
    *   **Example:** The `AnalysisTask.Analysis` object provides an excellent, detailed description.

```kotlin
val SocraticDialogue = TaskType(
    "SocraticDialogue", // 1. The task's unique name
    SocraticDialogueTaskExecutionConfigData::class.java, // 2. Execution config class
    TaskTypeConfig::class.java, // 3. Type config class
    "Explore ideas through Socratic questioning", // 4. Short description
    """
      Uses Socratic questioning methodology... // 5. Detailed HTML description
      <ul>
        <li>Creates dialogue between questioner and responder agents</li>
        ...
      </ul>
    """
)
```

#### 2.3. The `promptSegment()` Method

This provides a concise summary for the orchestrator LLM when it's building a plan. It's different from the user-facing description.

*   **Best Practices:**
  *   Keep it short and to the point.
  *   Use a consistent format. The examples use a `TaskName - Tagline` format.
  *   Use `**` or `*` to highlight key configuration parameters.
  *   Briefly mention the primary inputs and outputs/outcomes.
  *   **Example:** The `WriteHtmlTask` `promptSegment` is a perfect model, clearly listing the required inputs and the expected output format.

#### 2.4. The `run()` Method: The Heart of the Task

This is where the magic happens. Structure it for clarity, robustness, and user feedback.

*   **Best Practices:**

  1.  **Initialization and Validation:**
    *   Start by validating the configuration. If a required parameter is missing, fail early with a clear error message using `resultFn("CONFIGURATION ERROR: ...")`. (See `WriteHtmlTask`).
    *   Initialize necessary components: UI elements (`TabbedDisplay`), agents (`ChatAgent`), and state variables.
    *   Log the start of the task with key configuration parameters.

  2.  **Provide Immediate User Feedback:**
    *   Don't leave the user with a blank screen. As soon as the task starts, create a new UI task (`task.ui.newTask()`) and add an initial status message (e.g., "Starting Socratic Dialogue...").
    *   For complex tasks, use a `TabbedDisplay` as seen in `SocraticDialogueTask` and `CrawlerAgentTask`. This is a fantastic pattern for organizing different stages of output (e.g., Overview, Context, Exchange 1, Exchange 2, Synthesis).

  3.  **Structure the Logic Flow:**
    *   **Sequential Steps:** For tasks with a clear pipeline, break the logic into commented steps. `WriteHtmlTask` is a great example: Step 1: Generate HTML, Step 2: Generate JS, Step 3: Generate CSS, Step 4: Combine.
    *   **Loops:** For iterative processes, use a loop and provide UI updates on each iteration. `SocraticDialogueTask` does this perfectly, creating a new tab for each "Exchange".
    *   **Concurrency:** For tasks that can perform parallel operations, use the provided `agent.pool` and a `CompletionService` as demonstrated in `CrawlerAgentTask`. This is an advanced pattern for I/O-bound tasks like fetching web pages.

  4.  **Interact with the LLM:**
    *   **Prompt Engineering:** Construct detailed, role-based prompts for your `ChatAgent`. Tell the agent what it is, what its goal is, what context it has, and what format the output should be in. The multi-step prompts in `WriteHtmlTask` are a prime example of "chain-of-thought" prompting.
    *   **Structured Output:** When you need to programmatically use the LLM's output (not just display it), use a `ParsedAgent` with a data class (`ParsedPage` in `CrawlerAgentTask`). This is far more reliable than parsing raw text with regex.
    *   **Context is King:** Gather all relevant context before calling the LLM. This includes `getInputFileCode()`, `getPriorCode()`, and the task's own configuration.

  5.  **Update the UI Continuously:**
    *   After each significant step (e.g., an LLM call, a file download, a loop iteration), update the UI by adding rendered markdown to the `SessionTask`.
    *   Use status markers like "✅ Complete" or "❌ Error" to make progress clear at a glance.
    *   Call `task.update()` after adding content to push the changes to the user's browser.

  6.  **Handle Side Effects Safely:**
    *   For tasks that modify the filesystem (`WriteHtmlTask`), check the `orchestrationConfig.autoFix` flag.
    *   If `false`, present the result to the user and provide an "Accept" button (`acceptButtonFooter`) to get explicit confirmation before writing the file.

  7.  **Robust Error Handling:**
    *   Wrap the entire `run` method's logic (or at least the core parts) in a `try/catch (e: Exception)` block.
    *   In the `catch` block:
      *   Log the error with the full stack trace: `log.error("...", e)`.
      *   Report the error to the UI: `task.error(e)`.
      *   Update the UI with a user-friendly error message.
      *   Return a final error summary via `resultFn`.
    *   The `SocraticDialogueTask` and `CrawlerAgentTask` have excellent examples of comprehensive `try/catch` blocks.

  8.  **Finalization:**
    *   When the task is complete, call `task.complete("Summary message...")` to mark it as finished in the UI.
    *   Call `resultFn()` with the final, concise string output of the task. This string is what gets passed to subsequent tasks as "prior context."
    *   Log the completion of the task, including key metrics like duration and items processed.

### 3. Summary: A Checklist for Your Next Task

1.  [ ] **Define Configuration:** Create `*TaskExecutionConfigData` and `*TaskTypeConfig` classes with clear, annotated properties.
2.  [ ] **Register the Task:** Create the `TaskType` companion object with clear short and long descriptions.
3.  [ ] **Create the Orchestrator Prompt:** Implement `promptSegment()` with a concise summary for the LLM.
4.  [ ] **Structure `run()`:**
  *   [ ] Validate inputs at the start.
  *   [ ] Set up UI (e.g., `TabbedDisplay`).
  *   [ ] Provide initial status feedback.
5.  [ ] **Implement Core Logic:**
  *   [ ] Break the process into logical steps/loops.
  *   [ ] Craft high-quality prompts for LLM agents.
  *   [ ] Use `ParsedAgent` for structured data.
  *   [ ] Update the UI after each step.
6.  [ ] **Add Robustness:**
  *   [ ] Wrap logic in a `try/catch` block.
  *   [ ] Handle errors gracefully in the `catch` block (log, `task.error`, UI message).
  *   [ ] For side effects, check `autoFix` and add a user confirmation step.
7.  [ ] **Finalize:**
  *   [ ] Call `task.complete()` with a summary.
  *   [ ] Call `resultFn()` with the final string output.
  *   [ ] Add informative completion logs.

By following these best practices, you can create powerful, reliable, and user-friendly tasks that integrate seamlessly into the broader orchestration framework.