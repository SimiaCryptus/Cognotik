---
transforms: ../webui/src/main/kotlin/com/simiacryptus/cognotik/plan/cognitive/([^\./]+Mode)\.kt -> ../site/cognotik.com/$1.html
---

# Cognitive Modes: The Strategy Engine

## 1. Overview

**Cognitive Modes** represent the "Brain" of the Cognotik system. While Tasks act as the hands (performing side
effects), the Mode defines the control loop, state management, and reasoning strategy. It determines *why* a task is
selected and *how* the agent recovers from failure.

### Section A: The Header

* **Breadcrumbs:** `Home > Core > Cognitive Modes`
* **Title:** CognitiveMode
* **Badges:**
    * `Core Architecture`
    * `Stateful`
    * `Strategy Engine`
* **One-Line Pitch:** The autonomous control loop that orchestrates task execution, manages reasoning state, and defines
  the agent's problem-solving persona.

---

## 2. The Reality Check

<div class="reality-check-grid">
  <!-- Input: The Configuration -->
  <div class="panel code-panel">
    <div class="panel-header">
      <span class="icon">⚙️</span> OrchestrationConfig.json
    </div>
    <pre><code class="language-json">
{
  "mode": "AdaptivePlanningMode",
  "schema_strategy": "ScientificMethod",
  "auto_fix": true,
  "model_config": {
    "smart": "gpt-4-turbo",
    "fast": "gpt-3.5-turbo"
  }
}
    </code></pre>
  </div>

  <!-- Arrow Connector -->
  <div class="flow-arrow">→</div>

  <!-- Output: The Transcript & State -->
  <div class="panel ui-panel">
    <div class="panel-header">
      <span class="icon">🧠</span> Live Transcript
    </div>
    <div class="mock-browser">
      <div class="markdown-body">
        <h3>State: Hypothesis Testing</h3>
        <div class="mermaid">
          graph TD
          A[Analyze Error] --> B{Hypothesis?}
          B -->|Network| C[Check DNS]
          B -->|Code| D[Run Linter]
          C --> E[Refute Hypothesis]
          E --> A
        </div>
        <p><strong>Thinking:</strong> Network checks passed. Switching strategy to Code Analysis.</p>
        <p><em>Executing: RunCodeTask...</em></p>
      </div>
    </div>
  </div>
</div>

---

## 3. Technical Details

### Tab 1: Built-in Modes

Cognotik ships with four distinct architectural patterns for problem-solving.

| Mode Name                    | Architecture                    | Best Use Case                                                                                 |
|:-----------------------------|:--------------------------------|:----------------------------------------------------------------------------------------------|
| **WaterfallMode**            | `Plan -> Review -> Execute`     | Well-defined problems where the user must approve the entire roadmap before execution begins. |
| **ConversationalMode**       | `Listen -> Act -> Reply`        | Interactive debugging, exploratory sessions, or simple "Chat with Code" workflows.            |
| **AdaptivePlanningMode**     | `Loop(Think -> Act -> Reflect)` | Complex, ambiguous goals requiring research, trial-and-error, and self-correction.            |
| **HierarchicalPlanningMode** | `Tree(Decompose -> Delegate)`   | Massive projects. Breaks goals into sub-goals and spawns `SubPlanningTasks` to handle them.   |

### Tab 2: Cognitive Schema Strategies

Modes utilize **Schema Strategies** to define their "Persona" and internal state structure. This separates the *control
flow* (The Mode) from the *reasoning style* (The Strategy).

| Strategy              | Internal State    | Reasoning Style                                                                              |
|:----------------------|:------------------|:---------------------------------------------------------------------------------------------|
| **Project Manager**   | `ReasoningState`  | Generalist. Tracks short-term vs. long-term goals and manages a task queue.                  |
| **Scientific Method** | `ScientificState` | Debugger. Formulates hypotheses, tracks established facts, and attempts to falsify theories. |
| **Agile Developer**   | `AgileState`      | Coder. Follows a TDD loop: `Test Failing` -> `Implementing` -> `Refactoring`.                |
| **Critical Auditor**  | `AuditState`      | Security. Adversarial mindset designed to find flaws rather than fix them.                   |

### Tab 3: Implementation Skeleton

To create a custom mode, implement the `run()` loop. You must handle state updates and UI rendering manually.

```kotlin
class MyCustomMode(val config: OrchestrationConfig) {
    fun run(task: SessionTask, userPrompt: String, availableTasks: List<TaskType>) {
        // 1. Initialize Strategy (The "Brain")
        val strategy = CognitiveSchemaStrategy.ScientificMethod
        var state = strategy.initialize(userPrompt, config.defaultSmart)

        // 2. Execution Loop
        while (!state.isComplete()) {
            // A. Get Guidance
            val guidance = strategy.getTaskSelectionGuidance(state)

            // B. Select & Execute Task
            val tool = availableTasks.first { it.canHandle(guidance) }
            val result = tool.execute(task.ui, guidance, config)

            // C. Update State (Reflection)
            state = strategy.update(state, result, config.defaultSmart)

            // D. Observability
            task.transcript()?.write("## Step Complete: ${state.currentPhase}\n".toByteArray())
        }
        task.complete("Goal Achieved")
    }
}
```

## 4. Integration Guide

### Configuration

To use a specific mode, reference it in your `OrchestrationConfig`.

```kotlin
val config = OrchestrationConfig(
    // Select the architectural mode
    mode = CognitiveMode.AdaptivePlanningMode,

    // Inject the reasoning strategy (if supported by the mode)
    overrides = mapOf(
        "schema_strategy" to "AgileDeveloper"
    ),

    // Define autonomy level
    autoFix = true
)
```

### Observability Standards

All modes must adhere to the **Transparency First** principle.

1. **Mermaid Diagrams:** Visualize the state machine in the transcript.
2. **Collapsed Details:** Use `<details>` tags for raw JSON state dumps.
3. **Graceful Degradation:** If the LLM hallucinates, the mode must catch the error and attempt to simplify the prompt
   before crashing.

```