Here is a detailed user guide for the **Cognitive Schema Strategies** system defined in the provided code.

---

# Cognotik Cognitive Schema Strategies: User Guide

## 1. Overview
The `CognitiveSchemaStrategy` system defines the "mindset" or "persona" an AI agent adopts when solving a problem. Instead of a generic "answer the prompt" approach, this system allows the AI to structure its memory, planning, and decision-making processes according to specific methodologies (e.g., Scientific Method, Agile Development, Auditing).

Each strategy dictates:
1.  **Initialization:** How the problem is broken down initially.
2.  **State Management:** What specific data is tracked (e.g., hypotheses, TODO lists, risk assessments).
3.  **Update Logic:** How the AI reflects on completed tasks to update its plan.
4.  **Task Guidance:** How the AI decides what to do next.

## 2. Available Strategies

The system comes with five built-in strategies. Choose the one that best fits your current objective.

### A. Project Manager (The Generalist)
*   **Best for:** General complex tasks, multi-step workflows, and goals that require breaking down into sub-tasks.
*   **How it thinks:** It acts like a standard project manager. It maintains a list of short-term and long-term goals, tracks facts, and identifies open questions.
*   **Internal State:** `ReasoningState`
    *   **Goals:** Short-term vs. Long-term.
    *   **Knowledge:** Facts, Hypotheses, Open Questions.
    *   **Execution Context:** Next steps, potential challenges.
*   **Behavior:** It prioritizes completing the user's request by managing a queue of tasks and ensuring alignment with the overall goal.

### B. Scientific Method (The Debugger)
*   **Best for:** Debugging, root cause analysis, exploring unknown systems, or answering "Why?" questions.
*   **How it thinks:** It treats the problem as a scientific experiment. It refuses to guess; instead, it formulates hypotheses and creates tasks specifically to prove or disprove them.
*   **Internal State:** `ScientificState`
    *   **Research Question:** The core problem.
    *   **Hypotheses:** Potential explanations with confidence scores.
    *   **Established Facts:** Things proven true.
    *   **Refuted Theories:** Things proven false.
    *   **Experiment Log:** History of investigations.
*   **Guidance:** "Select tasks specifically designed to falsify or validate the top hypothesis."

### C. Agile Developer (The Coder)
*   **Best for:** Writing code, implementing features, and fixing specific bugs.
*   **How it thinks:** It follows a strict Test-Driven Development (TDD) loop.
*   **Internal State:** `AgileState`
    *   **User Story & Acceptance Criteria:** What needs to be built.
    *   **Current Phase:** Cycles through `TEST_FAILING` -> `IMPLEMENTING` -> `REFACTORING`.
    *   **TODO List:** Incremental coding steps.
*   **Guidance:**
    *   *If in Test Failing:* Write a test that fails.
    *   *If in Implementing:* Write code to pass the test.
    *   *If in Refactoring:* Clean up the code without changing behavior.

### D. Critical Auditor (The Security Expert)
*   **Best for:** Code reviews, security audits, compliance checks, and validating logic.
*   **How it thinks:** It adopts an adversarial mindset. It does not try to "fix" things; it tries to break them or find flaws.
*   **Internal State:** `AuditState`
    *   **Target Scope:** What is being audited.
    *   **Risk Assessment:** List of risks with severity (High/Med/Low).
    *   **Compliance Checklist:** Regulatory or logical requirements.
    *   **Vulnerabilities:** Confirmed issues.
*   **Guidance:** "Choose tasks that stress-test the system. Try to break the implementation. Do not fix issues, only report them."

### E. Creative Writer (The Author)
*   **Best for:** Writing documentation, stories, marketing copy, or long-form content.
*   **How it thinks:** It focuses on narrative flow, tone, and structure rather than technical correctness or logic.
*   **Internal State:** `NarrativeState`
    *   **Theme & Audience:** The stylistic guardrails.
    *   **Outline:** Chapters or sections with status (Draft/Reviewed/Done).
    *   **Tone Check:** Feedback on the writing style.
*   **Guidance:** "Focus on generating content. If the tone is off, select a task to rewrite or edit."

---

## 3. How It Works (The Lifecycle)

When you assign a strategy to an agent, it follows this lifecycle:

### Step 1: Initialize
When the user sends the first prompt, the strategy's `initialize` method is called.
*   **Input:** User prompt + Context.
*   **Action:** The AI uses a specific LLM prompt (defined in the strategy) to parse the request into the strategy's specific **State Object**.
*   **Example:** If using *Agile Developer*, the AI converts "Make a login page" into a `User Story`, `Acceptance Criteria`, and sets the phase to `TEST_FAILING`.

### Step 2: Task Selection
The system asks the strategy for `getTaskSelectionGuidance`.
*   **Action:** The strategy looks at its current state and tells the orchestration engine what kind of tool or task to run next.
*   **Example:** The *Scientific Method* strategy sees a hypothesis "Database is down" and guides the agent to run a "Check Database Connection" tool.

### Step 3: Execution & Update
After a tool or task is executed, the strategy's `update` method is called.
*   **Input:** The previous State + The Result of the task just performed.
*   **Action:** The AI reflects on the result and modifies the State Object.
*   **Example:**
    *   *Agile Strategy:* "The test passed." -> Change state from `IMPLEMENTING` to `REFACTORING`.
    *   *Scientific Strategy:* "Database connection failed." -> Move "Database is down" from `Hypothesis` to `Established Facts`.

---

## 4. JSON Serialization
These strategies are designed to be paused and resumed. The class uses Jackson annotations (`@JsonSerialize`, `@JsonDeserialize`) to save the strategy type and its internal state to JSON.

*   **Persistence:** You can save the entire agent session to a database or file. When reloaded, the agent remembers exactly where it was in the process (e.g., it remembers it was in the "Refactoring" phase of the Agile strategy).

## 5. Extending the System
To create a custom strategy (e.g., "Legal Analyst" or "Teacher"):

1.  Extend `CognitiveSchemaStrategy`.
2.  Define a data class for your state (e.g., `LegalState`).
3.  Implement `initialize`: Write a prompt that converts user input into `LegalState`.
4.  Implement `update`: Write a prompt that updates `LegalState` based on new findings.
5.  Implement `getTaskSelectionGuidance`: Define the logic for the next step.
6.  Add your new strategy to the `companion object` values list if you want it discoverable.