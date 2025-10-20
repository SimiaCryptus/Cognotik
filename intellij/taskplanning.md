## Cognitive Task Planning Framework: User Documentation

### 1. Introduction

The Cognitive Task Planning Framework is a sophisticated system designed to understand high-level user goals, break them
down into a series of smaller, actionable steps called **Tasks**, and execute them intelligently. It acts as a "brain"
that can formulate a plan, execute it, and even adapt based on the results.

This framework is built around a few core concepts:

* **Cognitive Modes:** The strategic "thinking style" or approach used to solve a problem.
* **Tasks:** The individual, concrete actions the system can perform (e.g., modify a file, run a shell command, search
  the web).
* **Orchestration:** The process of managing the plan, executing tasks in the correct order, and passing information
  between them.

This document will guide you through these concepts, explaining how to use the framework effectively.

### 2. Core Concepts

#### 2.1 Tasks (`AbstractTask`)

A **Task** is the fundamental unit of work. Each task is a specialized tool designed to perform a specific function. The
framework comes with a rich library of built-in tasks for a wide range of operations.

Every task has:

* A specific purpose (e.g., `FileModificationTask`, `RunCodeTask`).
* A set of configuration parameters (`TaskExecutionConfig`).
* Dependencies on other tasks, ensuring they run in the correct sequence.
* The ability to produce a result that can be used by subsequent tasks.

#### 2.2 Cognitive Modes (`CognitiveMode`)

The **Cognitive Mode** is the high-level strategy engine. It determines *how* the framework approaches a user's request.
It's responsible for the initial planning, handling user interaction, and deciding which tasks to run and when. The
framework offers several distinct modes, each suited for different kinds of problems.

#### 2.3 Orchestration Configuration (`OrchestrationConfig`)

This is the central configuration file that governs the behavior of the entire framework. It allows you to customize:

* The AI models used for planning and parsing (`defaultModel`, `parsingModel`).
* The default **Cognitive Mode** to use.
* Which **Tasks** are enabled and their specific settings (`taskSettings`).
* The execution environment, such as the working directory and shell commands.

### 3. Cognitive Modes in Detail

You can select a Cognitive Mode to match the complexity and nature of your goal.

#### 3.1 Waterfall Mode

The `WaterfallMode` is a traditional, plan-ahead strategy.

* **How it works:** When you provide a goal, it first generates a complete, multi-step plan. It presents this plan to
  you for review, often with a visual diagram. You can discuss, revise, and approve the plan before any tasks are
  executed. Once approved, the orchestrator executes the entire plan from start to finish.
* **When to use it:** Ideal for well-defined problems where the sequence of steps is clear from the outset and you want
  to review the entire approach before execution begins.

#### 3.2 Conversational Mode (Chat Mode)

The `ConversationalMode` is an interactive, step-by-step execution model.

* **How it works:** Instead of creating a large plan upfront, this mode analyzes your message, selects a *single* best
  task to perform right now, and executes it. It maintains a conversation history, so its next action is informed by
  previous steps. It's designed for a back-and-forth dialogue.
* **When to use it:** Perfect for exploratory work, debugging, or problems where the next step is not obvious until the
  current one is complete. It excels at interactive sessions.

##### Special Feature: Expansion Syntax

`ConversationalMode` supports a powerful syntax to run multiple variations of a task in parallel or in sequence.

* **Alternatives `@[option1|option2|...]`**: Executes a task for each option in parallel.
    * *Example:* `Analyze the performance of @[algorithm_A|algorithm_B|algorithm_C]` will run three separate analysis
      tasks, one for each algorithm.

* **Sequence `@{step1 -> step2 -> ...}`**: Executes a series of tasks sequentially, feeding the context from one step to
  the next.
    * *Example:* `@{Generate a report -> Email it to the team -> Archive the report}` will perform these three actions
      in order.

* **Range `@(start..end:step)`**: Expands a numerical range into a sequence.
    * *Example:* `Run simulation for years @(2020..2023)` is equivalent to `@{2020 -> 2021 -> 2022 -> 2023}`.

* **Topic Reference `@{TopicName}`**: References a list of items that were identified and aggregated in previous steps.
    * *Example:* If a previous step identified a list of files (`file1.txt`, `file2.txt`) as belonging to the
      `SourceFile` topic, you can later reference them with `Analyze all @{SourceFile}`.

#### 3.3 Other Modes

The framework also includes other advanced modes like `AdaptivePlanningMode` and `HierarchicalPlanningMode` for more
complex, dynamic problem-solving scenarios.

### 4. Available Tasks (`TaskType`)

The framework is equipped with a wide array of tasks, which can be broadly categorized as follows:

| Category             | Example Tasks                                                                                       | Description                                                                                                  |
|:---------------------|:----------------------------------------------------------------------------------------------------|:-------------------------------------------------------------------------------------------------------------|
| **File Operations**  | `FileModificationTask`, `FileSearchTask`, `AnalysisTask`                                            | Create, read, update, delete, and search for files in the workspace.                                         |
| **Code & Execution** | `RunCodeTask`, `RunShellCommandTask`, `SelfHealingTask`                                             | Execute code snippets, run terminal commands, and attempt to automatically fix failing scripts.              |
| **Reasoning**        | `ChainOfThoughtTask`, `CausalInferenceTask`, `SocraticDialogueTask`, `MultiPerspectiveAnalysisTask` | Employ advanced reasoning techniques to analyze problems, explore causes, and consider different viewpoints. |
| **Online & Web**     | `CrawlerAgentTask`, `GitHubSearchTask`, `SeleniumSessionTask`                                       | Scrape websites, search for code on GitHub, and perform automated browser actions.                           |
| **Knowledge Base**   | `KnowledgeIndexingTask`, `VectorSearchTask`                                                         | Build and query a knowledge base from documents for semantic search and retrieval.                           |
| **Planning**         | `SubPlanningTask`                                                                                   | A powerful meta-task that can invoke the entire planning framework recursively to solve a sub-problem.       |
| **Content Gen.**     | `WriteHtmlTask`, `GeneratePresentationTask`, `NarrativeGenerationTask`                              | Create structured content like web pages, presentations, and stories.                                        |

### 5. Advanced Usage: A Deep Dive into `SubPlanningTask`

The `SubPlanningTask` is one of the most powerful tools in the framework, enabling a hierarchical, "divide and conquer"
approach to problem-solving.

#### What it Does

When a problem is too large or complex to be solved in a single plan, you can use the `SubPlanningTask`. It essentially
launches a new, independent instance of the planning framework to tackle a specific sub-goal.

#### Key Features

* **Recursion:** It can create a sub-plan, which might itself contain another `SubPlanningTask`, allowing for multiple
  levels of planning.
* **Custom Configuration:** You can assign a different **Cognitive Mode** and a unique set of enabled tasks to the
  sub-plan. For example, the main plan might be a `WaterfallMode`, but a complex reasoning step within it could be
  delegated to a `SubPlanningTask` running in `ConversationalMode`.
* **Context Passing:** It automatically passes down relevant information (like results from previous tasks) as context
  to the sub-planner.
* **Result Aggregation:** Once the sub-plan is complete, it gathers all the results and can generate a concise summary,
  which is then passed back to the main plan.

**Example Scenario:**
Your main goal is "Build a web app to display stock data."

1. A `WaterfallMode` plan is created.
2. One task in the plan is "Design the database schema." This is a complex sub-problem.
3. This task is implemented as a `SubPlanningTask` with the goal "Design a schema for storing stock tickers and
   historical price data."
4. This `SubPlanningTask` might use an `AdaptivePlanningMode` to research best practices, draft a schema, and validate
   it.
5. Once complete, the final schema design is returned as the result of the `SubPlanningTask`, and the main
   `WaterfallMode` plan continues to the next step, "Implement the backend API."

---