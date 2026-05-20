# Cognotik Cognitive Mode Development Guide

## 1. Introduction

While **Tasks** represent the "hands" of the Cognotik system (performing actions), **Cognitive Modes** represent the "
brain" (strategy and planning). As a developer creating a new Cognitive Mode, you are defining the control loop that
orchestrates these tasks.

This guide outlines best practices for implementing robust modes, managing LLM context, and designing architectures that
support Sub-Planning.

## 2. Core Principles

1. **Transparency First:** The user must always know *what* the agent is doing and *why*. Never perform a complex
   reasoning step without logging it to the Transcript or displaying it in the UI.
2. **Bounded Autonomy:** Your mode must strictly adhere to the `autoFix` configuration. If `autoFix` is false, the mode
   is a *proposer*, not an *executor* of side effects.
3. **State Isolation:** Do not rely on global static state. All reasoning context must be contained within the `run()`
   method's scope or the `CognitiveSchemaStrategy` state object to ensure thread safety and resumability.
4. **Graceful Degradation:** If the "Smart" model fails or hallucinates, the mode should attempt to recover (e.g., by
   simplifying the prompt) or fail loudly with a clear error, rather than looping infinitely.

## 3. Handling Configuration

Your mode must respect the `OrchestrationConfig` passed in by the user. Do not hardcode model or task selections.

### 3.1 Model Usage Guidelines

* **`config.defaultSmart`:** Use this for the core logic of your mode (Planning, Reasoning, Code Generation).
* **`config.defaultFast`:** Use this for utility operations (JSON parsing, summarizing text, extracting intent) to
  reduce latency and cost.

### 3.2 Prompt Architecture

* **Externalize Strings:** Never hardcode the "System Prompt" or "Persona Definition" inside the Cognitive Mode class
  logic.
* **Configurable Defaults:** Allow the `OrchestrationConfig` to override your default prompts. This allows users to
  tweak the "Brain" without modifying your source code.

```kotlin
class MyCustomMode(val config: OrchestrationConfig) {
  fun run() {
    // Use the user's preferred smart model
    val planner = config.defaultSmart

    // Allow prompt injection
    val systemPrompt = config.overrides["systemPrompt"] ?: DEFAULT_SYSTEM_PROMPT

    // ... execution logic
  }
}
```

### 3.3 Respecting Interaction Modes

* **If `config.autoFix == false`:** Your mode **must** pause for user confirmation before executing destructive
  actions (writing files, running shell commands).
* **If `config.autoFix == true`:** Your mode should implement self-healing logic (e.g., reading a stack trace and
  retrying) rather than asking the user.

 ---

## 4. State Management & Resource Limits

### 4.0 Using Cognitive Schema Strategies

Instead of managing raw strings or lists of messages, prefer using **Cognitive Schema Strategies** (see
`cognitive_schema.md`).

* **Why:** They provide structured state (e.g., `ScientificState`, `AgileState`) that handles serialization,
  initialization, and updates automatically.
* **How:**
  ```kotlin
  // Initialize strategy
  val strategy = CognitiveSchemaStrategy.AgileDeveloper
  var state = strategy.initialize(userPrompt, config.defaultSmart)
  // In your loop
  val guidance = strategy.getTaskSelectionGuidance(state)
  // ... execute task ...
  state = strategy.update(state, taskResult, config.defaultSmart)
  ```
  *Note:* For specialized modes that act as a direct interface to a runtime (like `CodingMode`), you may bypass the
  Schema Strategy and interact directly with a specialized Agent (e.g., `CodeAgent`) or the LLM client, provided you
  handle state history manually.

### 4.1 Implementing Token Pruning

Modes that maintain a "Reasoning State" (Goals, Facts, Hypotheses) will eventually overflow the context window.

* **Requirement:** Your mode must implement a "Garbage Collection" strategy. Periodically summarize completed steps and
  remove them from the prompt context, keeping only the active goal and relevant facts.

### 4.2 Managing Concurrency

When implementing parallel modes (like `ParallelMode`), do not spawn unlimited threads.

* **Implementation:** Use `FixedConcurrencyProcessor` or a bounded `Semaphore`.
* **Rate Limits:** Handle `429 Too Many Requests` exceptions gracefully by implementing exponential backoff within your
  worker threads.

### 4.3 Cost Awareness

* **Council/Voting Patterns:** If your mode uses a "Council" pattern, ensure you are not running the voting loop on
  every trivial step. Implement a "Confidence Threshold"—only trigger the Council if the primary model's confidence is
  low.

### 4.4 Exposing Tasks as Functions

For modes that allow the LLM to write code or scripts (like `CodingMode`), you can expose Cognotik Tasks as executable
functions within the runtime environment.

* **Pattern:** Wrap the `TaskType` in a helper class that implements `MethodTypeDescriber`.
* **Usage:** This allows the LLM to "call" a task (e.g., `WebSearch.call(config)`) directly from the generated code.

```kotlin
// Example: Exposing a task to a scripting environment
inner class TaskFunctionImpl(val taskType: TaskType<*, *>) {
    fun call(config: Any, message: String): String {
        // 1. Convert config to TaskExecutionConfig
        // 2. Instantiate TaskOrchestrator
        // 3. Run task and return result string
    }
}
// In your mode's symbol registration:
val symbols = mapOf(
    "WebSearch" to TaskFunctionImpl(TaskType.WebSearch)
)
```

### 4.5 Structured Task Selection

When implementing a Planning Mode, you often need the LLM to select and configure multiple tasks in a single turn.
Instead of parsing raw text or JSON manually, use `ParsedAgent` with a container class.

* **Container Class:** Define a class (e.g., `Tasks`) that wraps a `List<TaskExecutionConfig>`.
* **Polymorphism:** Use the `TypeDescriber` to register subtypes for `TaskExecutionConfig`. This enables the LLM to
  output a polymorphic list (e.g., a `WebSearchConfig` and a `FileReadConfig` in the same list).

```kotlin
// 1. Define Container
data class Tasks(val tasks: MutableList<TaskExecutionConfig>? = null)
// 2. Register Subtypes
TaskType.getAvailableTaskTypes(config).forEach { taskType ->
    describer.registerSubType(TaskExecutionConfig::class.java, taskType.executionConfigClass)
}
// 3. Create Agent
val planner = ParsedAgent(
    resultClass = Tasks::class.java,
    model = config.defaultSmart,
    describer = describer // Pass the configured describer
)
// 4. Get Plan
val plan = planner.respond(messages).obj
plan.tasks?.forEach { taskConfig ->
    // Execute taskConfig...
}
```

 
---

## 5. Observability & Transcripts

Cognitive Modes are complex state machines. To debug them and provide value to the user, you **must** implement detailed
Transcripts.

### 5.1 Transcript Standards

* **Format:** Markdown.
* **Detail Level:** High. Use `<details>` tags to collapse raw LLM inputs/outputs, JSON state dumps, and stack traces.
* **Visuals:** Use **Mermaid** diagrams to visualize state transitions (e.g., Waterfall steps, Adaptive reasoning
  loops).

```kotlin
// Example: Logging to transcript with tabs
val transcript = task.transcript()
transcript?.write("## Step 1: Analysis\n".toByteArray())
val tabs = TabbedDisplay(task)
tabs["Plan"] = "```json\n$planJson\n```".renderMarkdown()
tabs["Diagram"] = "```mermaid\n$stateDiagram\n```".renderMarkdown()
// Example: Coding Mode Output
tabs["Code"] = "```groovy\n$generatedCode\n```".renderMarkdown()
if (output.isNotBlank()) {
    tabs["Output"] = "```text\n$output\n```".renderMarkdown()
}
```

### 5.2 Debugging Artifacts

1. **Waterfall:** Log the generated `plan.json` into a `<details>` block in the transcript.
2. **Adaptive:** Log every "Reflection" cycle. Use Mermaid to show the tree of thoughts.
3. **Protocol:** Log the Referee's scoring rationale.

---

## 6. Concurrency & Error Handling

### 6.1 Thread Management

Cognitive modes often orchestrate multiple sub-tasks or parallel reasoning chains.

* **Heavy Computation:** Use `task.ui.pool` for any non-trivial processing (parsing large plans, RAG lookups).
* **Scheduling:** Use `task.ui.scheduledThreadPoolExecutor` for periodic state checks or timeouts.
* **Logging:** Always log start/stop/progress events to SLF4J to correlate with UI events.

### 6.2 Exception Safety

* **Catch All:** Wrap high-level logic in `try/catch` blocks.
* **Reporting:**
  1. **UI:** `task.error(e)`
  2. **Log:** SLF4J error.
  3. **Transcript:** Embed the stack trace in a `<details>` section.
* **Recovery:** If a mode crashes, attempt to save the current state (e.g., `plan.json`) so the user can resume later.

```kotlin
try {
  executeComplexLogic()
} catch (e: Throwable) {
  log.error("Critical failure in mode execution", e)
  task.error(e)
  transcript?.write(
    """
         <details>
         <summary>Stack Trace</summary>
         ```
         ${e.stackTraceToString()}
         ```
         </details>
     """.trimIndent().toByteArray()
  )
}
```

---

## 7. Implementation Skeleton

Below is a standard skeleton for a robust, interactive **Planning Mode**.

```kotlin
class MyAdaptiveMode(val config: OrchestrationConfig) {
  private val log = LoggerFactory.getLogger(javaClass)
  fun run(
    task: SessionTask,
    userPrompt: String,
    availableTasks: List<TaskType>
  ) {
    val transcript = task.transcript()
    val tabs = TabbedDisplay(task)
    // 1. Initialize Strategy
    val strategy = CognitiveSchemaStrategy.ProjectManager
    var state = strategy.initialize(userPrompt, config.defaultSmart)
    // 2. UI Setup
    val reasoningTab = tabs.newTask("Reasoning")
    val executionTab = tabs.newTask("Execution")
    try {
      while (!state.isComplete()) {
        // 3. Plan Next Step
        val guidance = strategy.getTaskSelectionGuidance(state)
        reasoningTab.add("Thinking: $guidance".renderMarkdown())
        // 4. Select Tool (Simplified)
        val selectedTool = availableTasks.firstOrNull { it.canHandle(guidance) }
          ?: throw RuntimeException("No tool found for guidance: $guidance")
        // 5. Execute Tool
        // Note: Pass 'executionTab' so the tool renders in the correct UI slot
        val result = selectedTool.execute(
          task = executionTab,
          input = guidance,
          config = config
        )
        // 6. Update State
        state = strategy.update(state, result, config.defaultSmart)
        // 7. Log to Transcript
        transcript?.write("## Step Complete\nResult: $result\n".toByteArray())
      }
      task.complete("Goal Achieved!")
    } catch (e: Exception) {
      task.error(e)
      transcript?.write(e.stackTraceToString().toByteArray())
    } finally {
      transcript?.close()
    }
  }
}
```

 
---

## 7. Prompt Architecture

When defining the System Prompts for your mode, remember that specific phrasing alters tool usage probabilities.

* **Security Contexts:** If your mode is for auditing, explicitly boost `AnalysisTask` and `FileSearchTask` in the
  prompt instructions.
* **Execution Contexts:** If your mode is for implementation, explicitly boost `RunShellCommandTask` and `RunCodeTask`.

**Best Practice:** Do not rely on the model "guessing" which tools to use. Your mode should dynamically inject the list
of available tools into the system prompt based on the current state.

```kotlin
// Example: Dynamic Prompt Construction
val toolsPrompt = availableTools.joinToString("\n") { it.usageDescription }
val systemPrompt = """
     You are a specialized agent.

     AVAILABLE TOOLS:
     $toolsPrompt
""".trimIndent()
```