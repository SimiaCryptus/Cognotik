# Plan Orchestration and Execution

This package provides the core logic for breaking down complex user requests into actionable tasks and orchestrating
their execution. It manages task dependencies, execution state, and provides visual feedback through Mermaid diagrams
and tabbed UI components.

## Key Components

### [TaskOrchestrator](TaskOrchestrator.kt)

The central engine responsible for executing a plan. It:

- Manages a thread pool for parallel task execution.
- Handles task dependencies, ensuring prerequisites are completed before a task starts.
- Updates the user interface with real-time progress using a tabbed display.
- Captures task results and handles errors/timeouts.
- Streams execution transcripts for logging and auditing.

### [OrchestrationConfig](OrchestrationConfig.kt)

Defines the environment and parameters for planning and execution:

- **Model Configuration**: Specifies which AI models to use for planning, parsing, and image processing.
- **Task Settings**: Manages configurations for different task types (e.g., file modification, shell commands).
- **Planning Actor**: Contains the logic and prompts used to transform a user request into a structured
  `TaskBreakdownResult`.
- **Environment**: Sets the working directory, shell commands (bash/powershell), and execution budget.

### [ExecutionState](ExecutionState.kt)

A data class that tracks the runtime status of a plan:

- **Sub-tasks**: The map of task configurations.
- **Queue**: The ordered list of tasks to be processed.
- **Results**: A map of outputs from completed tasks.
- **Futures**: Tracks active background processes.
- **UI Mapping**: Links internal tasks to their corresponding UI components.

### [PlanUtil](PlanUtil.kt)

A utility object providing helper functions for plan management:

- **Dependency Resolution**: Performs topological sorts to determine valid execution orders and detects circular
  dependencies.
- **Visualization**: Generates Mermaid.js graph definitions to visualize task relationships and execution status (
  Pending, In Progress, Completed).
- **Filtering**: Sanitizes and validates plans to ensure consistency.

### [TaskContextYamlDescriber](TaskContextYamlDescriber.kt)

A specialized YAML describer used to generate schemas for the AI planning actor. it ensures the LLM understands the
available task types and their configuration requirements based on the current `OrchestrationConfig`.

## Workflow

1. **Planning**: The `OrchestrationConfig` uses a `planningActor` (LLM) to analyze a user request and generate a
   `TaskBreakdownResult`.
2. **Initialization**: `TaskOrchestrator` creates an `ExecutionState` and determines the execution order based on task
   dependencies.
3. **Visualization**: A Mermaid diagram is rendered in the UI to show the task graph.
4. **Execution**:
    - Tasks are submitted to a thread pool.
    - The orchestrator waits for a task's dependencies to complete before starting it.
    - Each task is assigned a dedicated UI tab for output and status updates.
5. **Completion**: Results are collected, and the final state is returned.

## Features

- **Parallel Execution**: Tasks with no mutual dependencies run concurrently.
- **Resilient Orchestration**: Includes timeout handling and error reporting per task.
- **Dynamic UI**: Automatically generates a tabbed interface where each task's progress can be monitored independently.
- **Visual Feedback**: Real-time updates to the Mermaid dependency graph reflect the current state of the execution
  pipeline.