
### **Best Practices for Designing Reasoning Task Types**

This document outlines key principles and best practices for creating robust, effective, and user-friendly reasoning tasks, derived from the patterns observed in `SocraticDialogueTask`, `GameTheoryTask`, and `BrainstormingTask`.

### 1. Task Design and Structure

#### **a. Decompose Complex Reasoning into Sequential Steps**
Complex reasoning is not a single monolithic operation. Break it down into a logical pipeline of smaller, manageable steps. This improves reliability, maintainability, and provides clear points for progress updates.

*   **`GameTheoryTask`:** Executes a clear pipeline: Analyze Structure -> Build Payoff Matrix -> Find Nash Equilibria -> Analyze Dominant Strategies -> Provide Recommendations -> Summarize.
*   **`BrainstormingTask`:** Follows a three-stage process: 1) Generate all options, 2) Analyze each option independently, 3) Generate a comparative summary.
*   **`SocraticDialogueTask`:** Uses a loop where each iteration is a self-contained "exchange" (Question -> Response -> Next Question).

#### **b. Create Highly Configurable Tasks**
Expose key parameters in a dedicated configuration data class (`TaskExecutionConfigData`). This makes tasks flexible and reusable for different scenarios.

*   **Use Descriptive Annotations:** The `@Description` annotation is crucial for auto-generating UIs and making the task's purpose clear to users.
*   **Provide Sensible Defaults:** Set default values for parameters like `max_depth: Int = 5` or `build_payoff_matrix: Boolean = true`. This allows users to run the task with minimal configuration.
*   **Use Appropriate Data Types:** Use `Boolean` for toggles (e.g., `challenge_assumptions`), `Int` for counts/depth, and `List<String>` for constraints or categories.

#### **c. Validate Configuration Early and Fail Fast**
Before starting any expensive processing, validate that all required configuration parameters are present and valid. This prevents wasted computation and provides immediate, clear feedback to the user.

*   **Example:** All three tasks check for the primary input (e.g., `initial_question`, `game_scenario`, `problem_statement`) at the beginning of the `run` method and exit with a "CONFIGURATION ERROR" if it's missing.

### 2. User Experience (UX) and Progress Reporting

#### **a. Use a Tabbed Display for Organized Output**
For tasks that generate multiple distinct artifacts, a `TabbedDisplay` is an excellent pattern. It prevents the UI from becoming a single, overwhelming wall of text and allows the user to inspect different parts of the analysis.

*   **`GameTheoryTask`** creates tabs for "Overview", "Context", "Game Structure", "Payoff Matrix", "Nash Equilibria", etc.
*   **`BrainstormingTask`** uses tabs for "Overview", "Generated Options", individual "Option X Analysis" tabs, and a final "Summary".

#### **b. Provide Continuous and Granular Feedback**
Long-running tasks should never leave the user guessing about their status. Update the UI frequently with the current state.

*   **Start with an Overview:** Create an "Overview" tab first that summarizes the task's configuration and initial status.
*   **Update Status Messages:** Use clear status indicators like "🔄 Analyzing...", "✅ Analysis complete", or "Generating next question...". Update these messages in the relevant tabs and the main overview as the task progresses.
*   **Log Timings:** At the end of each major step (and the task as a whole), report the processing time. This is useful for both the user and for performance diagnostics.

#### **c. Distinguish Between Detailed UI Output and Concise Task Result**
The final output passed to `resultFn` should be a concise summary suitable for being passed as context to a subsequent task. The detailed, step-by-step analysis should be rendered to the UI but not necessarily included in the final return value.

*   **`SocraticDialogueTask`** uses two `StringBuilder`s: `fullDialogueBuilder` for the detailed UI tabs and `dialogueBuilder` for the final, summarized result.
*   **`GameTheoryTask`** truncates long analysis sections in the final result string with a note: `... (see full analysis in UI)`.

### 3. Agent and Prompt Engineering

#### **a. Use the Right Agent for the Job**
Different agent types are suited for different purposes. Choose wisely to improve reliability and reduce post-processing.

*   **`ChatAgent`:** Use for free-form, creative, or analytical text generation where the output format is flexible (e.g., generating dialogue, writing a summary, providing recommendations).
*   **`ParsedAgent`:** Use when you need a structured data object (JSON) as the output. This is far more reliable than trying to parse free-form text.
  *   **`BrainstormingTask`** uses `ParsedAgent` to get a `BrainstormResult` (a list of options) and later to get an `OptionAnalysis` for each option.
  *   **`GameTheoryTask`** uses it at the end to generate a structured `GameAnalysis` summary.

#### **b. Craft Structured, Role-Based Prompts**
Don't just ask a question. Structure your prompts to guide the AI effectively.

*   **Assign a Persona:** Start the prompt by assigning a role (e.g., "You are a Socratic questioner," "You are an expert in game theory," "You are a creative problem solver"). This primes the model for the desired behavior.
*   **Use Headings and Lists:** Organize the prompt with Markdown headings (`##`) and numbered or bulleted lists to clearly separate context, instructions, and format requirements.
*   **Be Explicit About Output:** Clearly state the desired output format, especially when using `ParsedAgent` (e.g., "Generate a JSON object with an 'options' array...").

#### **c. Chain Prompts to Build Context**
In a multi-step process, the output of one step should be the input for the next. This creates a coherent chain of reasoning.

*   **`GameTheoryTask`** explicitly passes the "game structure and payoff matrix above" in the prompt to find Nash equilibria.
*   **`SocraticDialogueTask`** includes the `currentResponse` in the prompt to generate the `nextQuestion`.

### 4. State and Robustness

#### **a. Leverage Context from Previous Tasks**
Make tasks composable by allowing them to inherit context from the broader execution plan. The `getPriorCode(agent.executionState)` function is a standard way to do this.

*   All three examples fetch this `priorContext` and, if it exists, display it in a dedicated "Context" tab and include it in their initial prompts.

#### **b. Implement Comprehensive Error Handling**
Wrap the entire `run` method's logic in a `try...catch` block. An unhandled exception can derail an entire plan.

*   **In the `catch` block:**
  1.  Log the exception with its stack trace for debugging (`log.error(...)`).
  2.  Update the task's state to reflect the error (`task.error(e)`).
  3.  Update the UI with a clear error message in the "Overview" tab.
  4.  Return a formatted error message via `resultFn` so that downstream tasks are aware of the failure.
  5.  Include any partial results if they might be useful.

#### **c. Log Key Events and Metrics**
Maintain a clear log of the task's execution. This is invaluable for debugging and performance analysis.

*   Log the task's start and its configuration.
*   Log the completion of each major step.
*   Log the final summary, including total time, number of items processed (e.g., exchanges, options), and output size.
