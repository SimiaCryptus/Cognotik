# Task Orchestration Tools

The `com.simiacryptus.cognotik.plan.tools` package provides the core framework for defining, configuring, and executing modular tasks within the Cognotik orchestration system. It enables a polymorphic task execution environment where different AI-driven capabilities can be composed into complex workflows.

## Core Components

### [AbstractTask](./AbstractTask.kt)
The base class for all task implementations. It manages:
- **Execution State**: Tracks task progress (Pending, InProgress, Completed).
- **Resource Access**: Provides methods for reading input files with glob pattern support and document-to-text conversion.
- **UI Integration**: Handles rendering task headers, creating tabbed displays, and managing interactive UI elements like "Accept" buttons.
- **Transcripts**: Automatically generates Markdown and HTML transcripts of task execution for auditing and debugging.
- **Model Selection**: Provides access to "Smart" and "Fast" LLM interfaces based on task-specific or global configurations.

### [TaskType](./TaskType.kt)
A `DynamicEnum` registry that maps task identifiers to their implementation classes and configuration schemas. It categorizes tasks into several domains:
- **Reasoning**: Abductive, Adversarial, Causal, Socratic, etc.
- **Writing**: Article Generation, Email Campaigns, Scriptwriting, Technical Explanations.
- **File Operations**: Search, Modification, Append, Data Ingest.
- **Coding**: Run Code, AutoFix, Language Server integration.
- **Games**: Narrative Design, Mechanics, Economy balancing.
- **Online**: Web Crawling, GitHub Search, MCP Tool integration.

### [TaskExecutionConfig](./TaskExecutionConfig.kt)
Defines the instance-specific configuration for a task, including:
- `task_type`: The specific implementation to use.
- `task_description`: A user-facing description of the goal.
- `task_dependencies`: A list of upstream task IDs that must complete before this task starts, enabling data flow between tasks.

### [TaskTypeConfig](./TaskTypeConfig.kt)
Provides global settings for a specific task type, such as the default `ApiChatModel` to be used by all instances of that task type.

### [TaskUtils](./TaskUtils.kt)
Contains utility extensions for robust task execution:
- **Triple Log Rule**: A standardized logging pattern that records errors to the UI (for the user), SLF4J (for system operations), and the Task Transcript (for detailed auditing with stack traces).
- **Safe Completion**: Ensures the UI state is correctly updated and spinners are removed even if Markdown rendering fails.
- **Display Truncation**: Prevents UI clutter by truncating large text outputs with clear omission indicators.

## Implementation Patterns

To implement a new task, extend `AbstractTask` and register it in the `TaskType` companion object. Tasks should:
1. Define a specific `TaskExecutionConfig` if they require custom parameters.
2. Implement `promptSegment()` to define their contribution to the LLM context.
3. Implement `run()` to execute the core logic, utilizing the provided `SessionTask` for UI feedback and `TaskOrchestrator` for agent interaction.

## Usage Example

Tasks are typically instantiated via the `OrchestrationConfig`:

```kotlin
val taskConfig = TaskExecutionConfig(task_type = "Brainstorming", task_description = "Generate app ideas")
val taskImpl = orchestrationConfig.getImpl(taskConfig)
taskImpl.run(agent, messages, sessionTask, { result -> /* handle result */ }, orchestrationConfig)
```

## Logging and Debugging

The system emphasizes traceability through transcripts. Every task execution can generate a transcript file located in the `transcript/` directory, providing a step-by-step record of the AI's reasoning and the task's output.