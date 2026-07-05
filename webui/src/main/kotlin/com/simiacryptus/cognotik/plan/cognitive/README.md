# Cognitive Planning Modes

This package implements the "cognitive" layer of the AI orchestration system. It defines various strategies for how an
AI agent processes user input, maintains internal state, plans actions, and executes tasks.

## Core Architecture

The system is built around a flexible, extensible architecture:

* **`CognitiveMode`**: The abstract base class for all planning strategies. It provides the interface for handling user
  messages, managing session state, and generating transcripts.
* **`CognitiveModeType`**: A dynamic registry of available modes, linking configuration classes to their
  implementations.
* **`CognitiveSchemaStrategy`**: Defines specific "thinking" patterns used by iterative modes. These strategies manage
  how the AI's internal "Reasoning State" is initialized and updated.
  * **Project Manager**: Standard goal-oriented planning.
  * **Scientific Researcher**: Hypothesis-driven investigation.
  * **Agile Developer**: Iterative Test-Driven Development (TDD).
  * **Critical Auditor**: Security and logic validation.
  * **Creative Writer**: Narrative and content generation.

## Available Planning Modes

### 1. Conversational Mode (`Chat`)

A task-oriented chat interface that maintains conversation history. It can trigger specific tasks based on user input
and supports advanced expansion syntax for generating multiple tasks from a single prompt.

### 2. Adaptive Planning Mode (`Adaptive`)

An iterative mode that maintains a complex `ReasoningState` (goals, knowledge base, and execution context). It uses a
`CognitiveSchemaStrategy` to reflect on task results and update its plan in each iteration.

### 3. Hierarchical Planning Mode (`Hierarchical`)

A goal-oriented mode that decomposes high-level objectives into a tree of subgoals and tasks. It manages complex
dependency graphs, executing tasks only when their prerequisites are satisfied.

### 4. Council Mode (`Council`)

A multi-agent consensus mode. Different cognitive strategies (e.g., Project Manager, Developer, Auditor) nominate tasks
and vote on the best course of action for each iteration.

### 5. Coding Mode (`Coding`)

An interactive environment where the AI solves problems by writing and executing code (e.g., Groovy). It provides a
REPL-like experience where the AI can use code to call other system tasks.

### 6. Parallel Mode (`Parallel`)

Optimized for batch processing. It uses variable expansion (e.g., file globs or lists) to generate and execute multiple
tasks in parallel using cross-join or zip logic.

### 7. Protocol Mode (`Protocol`)

Executes a strict state machine. The AI defines a "protocol" consisting of states, instructions, and validation
criteria, then transitions through these states based on execution outcomes.

### 8. Waterfall Mode (`Waterfall`)

A traditional "plan-ahead" strategy. It generates a full task breakdown upfront, often visualized as a Mermaid diagram,
and then executes the plan.

### 9. Persona Chat Mode (`PersonaChat`)

Combines the conversational flow of Chat mode with the structured internal state of a specific cognitive persona (e.g.,
an Auditor or a Scientist).

## Key Features

* **Expansion Syntax**: Supports powerful syntax for task generation:
  * `@[opt1|opt2]`: Parallel alternatives.
  * `@{a -> b}`: Sequential execution steps.
  * `@(1..10)`: Numeric ranges for batch operations.
* **Transcript Logging**: Automatically generates detailed Markdown transcripts of every session, capturing reasoning,
  plans, and execution results.
* **Interactive Review**: Many modes support a "Plan Review" phase where the user can discuss and refine the AI's
  proposed plan before execution begins.
* **Dynamic Task Mapping**: Uses a `TaskChooser` agent to map natural language requirements to specific
  `TaskExecutionConfig` objects based on the available tools in the `OrchestrationConfig`.

## Implementation Details

- **`Tasks.kt`**: Provides the data structure for lists of task configurations and utilities for describing available
  task types to the AI.
- **`CognitiveModeConfig.kt`**: Base configuration class using Jackson type info for polymorphic deserialization of mode
  settings.