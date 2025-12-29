Of course! As a helpful AI that helps people with coding, I can provide detailed documentation for the cognitive modes
based on the provided source code. Here is a comprehensive guide to each mode's strategy, internal workings, and ideal
use cases.

***

## Cognitive Modes: A Deep Dive

The Cognitive Mode is the high-level strategy engine that determines *how* the framework approaches a user's request.
It's responsible for the initial planning, handling user interaction, and deciding which tasks to run and when. The
framework offers several distinct modes, each with a unique "thinking style" suited for different kinds of problems.

### 1. Waterfall Mode

The `WaterfallMode` implements a traditional, sequential, plan-ahead strategy. It is the most structured and predictable
of the modes.

* **High-Level Concept:** First, create a complete, detailed plan. Then, present that plan to the user for review and
  approval. Finally, execute the approved plan from start to finish without deviation.

* **How It Works (Internal Logic):**

1. **Initial Planning:** When a user message is received, `WaterfallMode` invokes its `initialPlan` method. This method
   uses a `planningActor` to generate a comprehensive plan, which is a map of task IDs to their configurations (
   `Map<String, TaskExecutionConfig>`), including all dependencies.
2. **User Discussion & Review:** The generated plan is wrapped in a `Discussable` agent. This presents the plan to the
   user in several formats:
   *   **Text:** The raw textual plan from the AI.
   *   **JSON:** The structured plan data.
   *   **Diagram:** A Mermaid.js graph visually representing the tasks and their dependencies.
   The user can then chat with the AI to revise and refine the plan until they are satisfied.
3. **Execution:** Once the user approves the plan (or if `autoFix` is enabled), the `TaskOrchestrator`'s `executePlan`
   method is called. The orchestrator then executes the entire graph of tasks, respecting all dependencies, until the
   plan is complete or a task fails.

* **Key Characteristics:**
* **Plan-First:** All planning is done upfront.
* **Transparent:** The user sees and approves the entire workflow before execution.
* **Predictable:** The execution path is fixed once approved.
* **Inflexible Execution:** The plan is not modified during the execution phase.

* **When to Use It:**
* Ideal for well-defined problems where the sequence of steps is clear from the outset.
* Excellent for projects that require formal review or approval before work begins.
* Suitable for batch processes or automated workflows that need to be reliable and repeatable.

* **Strengths:**
* Provides maximum user control over the plan.
* Reduces the risk of unexpected actions.
* The visual diagram makes complex dependencies easy to understand.

* **Weaknesses:**
* Poorly suited for ambiguous or exploratory problems where the next step is unknown.
* Cannot adapt to unexpected results or changes in the environment once execution has started.

### 2. Conversational Mode (Chat Mode)

The `ConversationalMode` is an interactive, step-by-step execution model that behaves like a powerful chatbot with
access to tools.

* **High-Level Concept:** Instead of creating a large plan, analyze the user's immediate request, select the single most
  appropriate task to perform right now, execute it, and wait for the next instruction.

* **How It Works (Internal Logic):**

1. **Message Handling:** User messages are added to a queue. A background process pulls messages one by one for
   execution.
2. **Expansion Syntax Processing:** The core logic resides in `processMsgRecursive`. Before any AI is involved, this
   function scans the message for special expansion syntax:
   *   **Alternatives `@[a|b|c]`:** Splits the command into multiple parallel tasks.
   *   **Sequence `@{a -> b -> c}`:** Chains commands to be executed sequentially.
   *   **Range `@(1..5)`:** Expands a numerical range into a sequence.
   *   **Topic Reference `@{TopicName}`:** Substitutes a placeholder with a list of items (e.g., file names) aggregated
   from previous turns.
3. **Single Task Selection:** If no expansion syntax is found, the `executeTask` method is called. It uses a
   `ParsedAgent` named "TaskChooser" which analyzes the current message and the conversation history to select and
   configure *one single task*.
4. **Execution & History:** The chosen task is executed immediately. The original user message and the task's result are
   then appended to the conversation history, providing context for the next turn.

* **Key Characteristics:**
* **Interactive & Reactive:** Acts on one command at a time, providing immediate feedback.
* **Stateful:** Maintains a conversation history to inform future actions.
* **Powerful Syntax:** The expansion syntax allows for complex parallel and sequential operations with a concise
  notation.

* **When to Use It:**
* Perfect for exploratory work, debugging, or iterative development.
* When you want to guide the AI step-by-step.
* Ideal for running variations of a task on different inputs (e.g., "Analyze these
  files: @[file1.txt|file2.txt|file3.txt]").

* **Strengths:**
* Extremely flexible and allows for rapid changes in direction.
* Immediate feedback loop is great for tasks where results need to be inspected before proceeding.
* The expansion syntax is a powerful force multiplier for repetitive tasks.

* **Weaknesses:**
* Can lose sight of the high-level goal without continuous user guidance.
* Not suitable for complex, long-term planning on its own.

### 3. Adaptive Planning Mode

The `AdaptivePlanningMode` is an autonomous agent that operates in a cyclical "think, act, reflect" loop. It maintains a
complex internal "state of mind" to solve problems iteratively.

* **High-Level Concept:** Start with an initial understanding of the goal. In a loop, choose the best next actions based
  on the current state, execute them, and then update the internal state based on the results. Repeat until the goal is
  achieved.

* **How It Works (Internal Logic):**

1. **Initialization:** Upon receiving a user message, it initializes its internal state using a specific **Cognitive Strategy**.
   The default strategy (`ProjectManagerStrategy`) creates a `ReasoningState` containing goals, knowledge, and execution context.
   However, other strategies can be used to define different mental models (e.g., Scientific Method, Agile Development).
2. **The Main Loop (Think-Act-Reflect):**
   *   **Think (`getNextTask`):** At the start of each iteration, the agent analyzes its current `ReasoningState` and
   the history of past actions to decide on a small batch of tasks to execute next.
   *   **Act (`runTask`):** The selected tasks are executed, often in parallel. Their results are captured.
   *   **Reflect (`updateThinking`):** After the tasks complete, the agent analyzes their results. It uses a
   `ParsedAgent` to update its `ReasoningState`—revising goals, confirming hypotheses, answering open questions, and
   planning the next set of actions.
3. **Termination:** The loop continues until no more tasks are generated, a maximum number of iterations is reached, or
   the agent concludes the goal is complete.

* **Key Characteristics:**
* **Autonomous:** Can work towards a goal for multiple iterations without user intervention.
* **Iterative:** Refines its understanding and plan over time.
* **Stateful & Reflective:** The `ReasoningState` acts as its memory and consciousness, allowing it to learn from its
  actions.
* **Cognitive Strategies:** The mode's behavior is defined by its strategy. Available strategies include:
  *   **Project Manager:** Standard goal-oriented planning.
  *   **Scientific Researcher:** Hypothesis-driven investigation.
  *   **Agile Developer:** Iterative Test-Driven Development.
  *   **Critical Auditor:** Security and logic validation.
  *   **Creative Writer:** Narrative and content generation.

* **When to Use It:**
* Complex, ambiguous, or poorly defined problems that require research, experimentation, and adaptation.
* Long-running tasks where you want the agent to work autonomously.
* Problems where the optimal path is not known in advance.

* **Strengths:**
* Can tackle complex problems that are too difficult for single-shot planning.
* Adapts its strategy based on new information and task outcomes.
* Maintains a coherent, long-term focus on the overall goal.

* **Weaknesses:**
* Can be slower and more resource-intensive due to the multiple LLM calls per iteration.
* As an autonomous agent, it may occasionally pursue an incorrect path before self-correcting.
* Offers less direct user control during its execution loop.

### 4. Hierarchical Planning Mode

The `HierarchicalPlanningMode` employs a "divide and conquer" strategy. It breaks down large, complex goals into a tree
of smaller, manageable sub-goals and tasks, and then orchestrates their execution based on dependencies.

* **High-Level Concept:** Decompose a primary goal into a hierarchy of sub-goals. Break down the lowest-level goals into
  concrete, executable tasks. Manage the dependencies between all goals and tasks, and execute them in parallel whenever
  possible.

* **How It Works (Internal Logic):**

1. **Initial Decomposition:** The initial user message is parsed into one or more high-level root goals, forming the top
   of the `goalTree`.
2. **The Main Loop (Decompose-Execute-Update):**
   *   **Decompose (`expandGoal`):** The agent finds active goals that have not yet been broken down. It uses a "
   GoalDecomposer" agent to decompose each goal into a set of smaller sub-goals and/or a list of executable tasks.
   *   **Execute (`executeTask`):** The agent identifies all tasks whose dependencies have been met (i.e., are in
   `PENDING` status). It submits these tasks to a concurrent processor for parallel execution.
   *   **Update Status (`updateAllStatuses`):** This is a critical, continuous process. The agent re-evaluates the
   status of every goal and task in the tree. A task becomes `PENDING` when its dependencies are complete. A goal
   becomes `COMPLETED` when all its children (sub-goals and tasks) are complete. This propagation of status updates
   drives the entire execution forward. The system also detects and attempts to break circular dependencies.
3. **Termination:** The process continues until all goals are either `COMPLETED` or `BLOCKED`, and no tasks are left
   pending or running.

* **Key Characteristics:**
* **Structured & Hierarchical:** Organizes work into a clear, nested structure.
* **Dependency-Aware:** Explicitly models and manages dependencies between tasks and goals.
* **Highly Parallel:** Designed to maximize parallel execution of independent tasks.

* **When to Use It:**
* Large, complex projects that can be logically broken down into smaller parts (e.g., "build a web application").
* Problems requiring sophisticated project management with clear dependencies.
* When you want a transparent, real-time view of a complex plan's progress.

* **Strengths:**
* Brings structure and organization to massive tasks.
* Excellent at managing complex dependencies automatically.
* Enables a high degree of parallelism, potentially speeding up execution significantly.
* The goal tree provides a clear and intuitive visualization of the project's status.

* **Weaknesses:**
* Incurs significant overhead from the constant planning, decomposition, and status updates.
* The success of the entire plan is highly dependent on the quality of the AI's decomposition logic.

### 5. Parallel Mode
The `ParallelMode` is a batch-processing engine designed to execute a specific task across multiple inputs simultaneously.
* **High-Level Concept:** Analyze the user's request to identify a template task and a set of variables (e.g., a list of files). Generate all combinations of these variables, render the template for each, and execute the resulting tasks in parallel.
* **How It Works (Internal Logic):**
1. **Configuration Parsing:** The user's message is analyzed by a `ParsedAgent` to extract a `Config` object. This includes:
   *   **Variables:** Lists of items to process (e.g., file paths, input strings). Supports glob patterns (e.g., `src/**/*.kt`).
   *   **Template:** A string with placeholders (e.g., "Review the code in {{file}}").
   *   **Concurrency:** How many tasks to run at once.
   *   **Mode:** How to combine variables (`CrossJoin` for all combinations, `Zip` for pairing).
2. **Expansion & Combination:** Variable values are expanded (e.g., resolving file globs). The system then generates a list of task configurations based on the selected mode.
3. **Parallel Execution:** A `FixedConcurrencyProcessor` manages the execution. For each combination:
   *   The template is rendered with the specific values.
   *   The system determines the appropriate task implementation (using logic similar to Conversational Mode).
   *   The task is executed, and results are displayed in a tabbed interface.
* **Key Characteristics:**
* **High Throughput:** Optimized for running many independent tasks at once.
* **Template-Driven:** Uses a single instruction template applied to many contexts.
* **Flexible Inputs:** Supports file globs and variable lists.
* **When to Use It:**
* Batch operations on files (e.g., "Refactor all Java files in src/").
* Running the same analysis on multiple datasets.
* Testing a prompt against a variety of inputs.
* **Strengths:**
* Drastically reduces time for repetitive tasks.
* Automates the creation of many similar tasks.
* Visualizes progress across multiple streams via tabs.
* **Weaknesses:**
* Not suitable for tasks with dependencies between steps.
* Can consume significant API resources quickly due to parallelism.
### 6. Protocol Mode (Experimental)
The `ProtocolMode` is a rigorous, state-machine-driven strategy designed to enforce specific methodologies and ensure high-quality output through validation.
* **High-Level Concept:** Define a strict protocol (a set of states with instructions and validation criteria) to achieve the user's request. The system moves through these states, executing actions and validating them with a "Referee" agent before proceeding.
* **How It Works (Internal Logic):**
1. **Protocol Definition:** The agent analyzes the request and defines a `ProtocolDefinition`. This is a state machine containing a list of states (e.g., "Red", "Green", "Refactor" for TDD), an initial state, and transitions. Each state has specific instructions and validation criteria.
2. **State Execution Loop:**
   *   **Action:** The system enters the current state and uses a "StateExecutor" agent to perform the required task based on the state's instructions.
   *   **Validation:** A "Referee" agent reviews the result of the action against the state's `validationCriteria`.
   *   **Retry/Transition:** If the validation passes, the system transitions to the defined `nextState`. If it fails, the system retries the action (up to a limit) with feedback from the Referee.
3. **Termination:** The process continues until a terminal state (no next state) is reached or a safety limit is hit.
* **Key Characteristics:**
* **Methodical:** Enforces structured workflows like TDD or Read-Draft-Verify.
* **Self-Correcting:** The Referee loop ensures that each step meets quality standards before moving on.
* **Transparent:** The protocol and state transitions are clearly visible.
* **When to Use It:**
* Tasks requiring strict adherence to a process (e.g., Test-Driven Development).
* Generating high-stakes documentation or code where verification is crucial.
* Complex workflows that can be modeled as a state machine.
* **Strengths:**
* High reliability due to the validation step.
* Enforces best practices (like writing tests before code).
* Clear separation of concerns between execution and validation.
* **Weaknesses:**
* Can be slow due to the overhead of validation and potential retries.
* Rigid compared to conversational modes.
### 7. Session Mode (Experimental)
The `SessionMode` focuses on deep interaction with a single tool. It assigns an AI "Operator" to drive a specific tool continuously until a goal is achieved.
* **High-Level Concept:** Select the most appropriate tool for the user's request, then enter a loop where an AI operator issues commands to that tool, interprets the output, and issues new commands until the task is done.
* **How It Works (Internal Logic):**
1. **Tool Selection:** The system analyzes the user's message to select a single, persistent tool (e.g., a specific CLI wrapper or coding agent).
2. **Session Loop:**
   *   **Plan:** A "SessionOperator" agent reviews the conversation history and the current goal. It decides whether the goal is complete or what the next command should be.
   *   **Execute:** The command is executed by the selected tool.
   *   **Update:** The command and its result are added to the session history.
3. **Termination:** The loop ends when the Operator deems the goal complete or a limit is reached.
* **Key Characteristics:**
* **Tool-Centric:** Locks onto one tool and uses it extensively.
* **Autonomous Operator:** The AI acts as a user of the tool, navigating its interface or command set.
* **Stateful:** Maintains the context of the tool's session.
* **When to Use It:**
* Tasks that require multiple interactions with the same utility (e.g., "Debug this issue using the terminal").
* Exploratory tasks where the AI needs to "poke around" using a specific instrument.
* **Strengths:**
* Allows for complex, multi-step operations within a specific domain.
* Reduces context switching by focusing on one tool.
* **Weaknesses:**
* Limited to the capabilities of the selected tool.
* Can get stuck in loops if the tool provides confusing feedback.
### 8. Council Mode
The `CouncilMode` implements a democratic, multi-agent decision-making process. Instead of a single agent driving the process, a "council" of distinct personas collaborates to nominate and vote on tasks.
* **High-Level Concept:** A group of specialized agents (e.g., CEO, CTO, QA) independently analyze the situation and nominate tasks. They then vote on the best course of action. The winning tasks are executed, and all agents update their internal states based on the results.
* **How It Works (Internal Logic):**
1.  **Council Initialization:** The mode initializes a list of `CognitiveSchemaStrategy` instances, representing the council members (default: CEO, CTO, QA). Each member maintains its own private state.
2.  **The Main Loop:**
    *   **Nomination:** Each council member analyzes the current situation and nominates tasks.
    *   **Voting:** If there are conflicting nominations, the council members vote on the proposed tasks.
    *   **Execution:** The tasks with the most votes are executed.
    *   **State Update:** Every council member observes the results of the executed tasks and updates their own internal state/perspective accordingly.
* **Key Characteristics:**
* **Multi-Perspective:** Balances different viewpoints (e.g., business value vs. technical feasibility vs. quality).
* **Democratic:** Decisions are made via voting, preventing one narrow perspective from dominating.
* **When to Use It:**
* High-stakes projects requiring balanced decision-making.
* Complex architectural design where trade-offs need to be weighed.
* Situations where a single agent might be prone to bias or tunnel vision.