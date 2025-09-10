# Developer Guide: Planning and Cognition System

## Table of Contents

1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Cognitive Modes](#cognitive-modes)
4. [Planning System](#planning-system)
5. [Task Execution](#task-execution)
6. [Implementation Guide](#implementation-guide)
7. [Configuration](#configuration)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)

## Overview

The Planning and Cognition system is a sophisticated AI-driven framework that enables intelligent task planning,
execution, and iterative problem-solving. It provides multiple cognitive strategies for handling user requests, from
simple task execution to complex goal-oriented planning.

### Key Features

- **Multiple Cognitive Modes**: Different strategies for handling user input
- **Intelligent Task Planning**: Automated breakdown of complex requests into actionable tasks
- **Iterative Execution**: Continuous learning and adaptation during execution
- **Dependency Management**: Automatic handling of task dependencies
- **Real-time Monitoring**: Live updates and progress tracking

## Architecture

### Core Components

```mermaid
graph TD
    A[User Input] --> B[Cognitive Mode Router]
    B --> C[AutoPlan Mode]
    B --> D[PlanAhead Mode]
    B --> E[TaskChat Mode]
    B --> F[GoalOriented Mode]

    C --> G[Plan Coordinator]
    D --> G
    E --> G
    F --> G

    G --> H[Task Execution Engine]
    H --> I[Task Types]
    I --> J[File Operations]
    I --> K[Command Execution]
    I --> L[Planning Tasks]

    H --> M[Progress Monitoring]
    M --> N[UI Updates]
```

### Key Classes

1. **CognitiveMode**: Base interface for all cognitive strategies
2. **PlanCoordinator**: Orchestrates plan execution and task management
3. **TaskType**: Defines available task implementations
4. **PlanSettings**: Configuration for planning behavior
5. **PlanProcessingState**: Tracks execution state and progress

## Cognitive Modes

### 1. AutoPlan Mode

The most sophisticated mode that implements iterative thinking and adaptive planning.

```kotlin

```

**Key Features:**

- **Iterative Thinking**: Maintains a `ThinkingStatus` that evolves with each iteration
- **Adaptive Planning**: Chooses tasks based on current context and progress
- **Parallel Execution**: Runs multiple tasks concurrently when possible
- **Context Awareness**: Considers previous task results when planning next steps

**Thinking Status Structure:**

```kotlin

```

**Usage Example:**

```kotlin
val autoMode = AutoPlanMode(ui, api, planSettings, session, user, describer)
autoMode.initialize()
autoMode.handleUserMessage("Create a web application with user authentication", task)
```

### 2. PlanAhead Mode

Traditional planning approach that creates a complete plan before execution.

```kotlin

```

**Key Features:**

- **Complete Planning**: Creates full task breakdown upfront
- **Sequential Execution**: Follows predetermined execution order
- **Dependency Resolution**: Handles task dependencies automatically
- **Traditional Workflow**: Familiar plan-then-execute pattern

### 3. TaskChat Mode

Conversational mode that executes tasks based on ongoing dialogue.

```kotlin

```

**Key Features:**

- **Conversational Interface**: Maintains chat history
- **Single Task Focus**: Executes one task per user message
- **Context Preservation**: Remembers previous interactions
- **Real-time Response**: Immediate task execution

### 4. GoalOriented Mode

Hierarchical goal decomposition with dependency management.

```kotlin

```

**Key Features:**

- **Goal Hierarchy**: Breaks down high-level goals into subgoals
- **Status Tracking**: Monitors goal and task completion
- **Dependency Management**: Handles complex interdependencies
- **Visual Progress**: Tree-like goal visualization

## Planning System

### Plan Settings

Configuration object that controls planning behavior:

```kotlin
,
var autoFix: Boolean = false,
val env: Map<String, String>? = mapOf(),
val workingDir: String? = ".",
val language: String? = if (isWindows) "powershell" else "bash",
var maxTaskHistoryChars: Int = 10000,
var maxTasksPerIteration: Int = 3,
var maxIterations: Int = 10
)
```

### Task Types

Available task implementations:

1. **FileModificationTask**: File creation and editing
2. **CommandAutoFixTask**: Command execution with error handling
3. **PlanningTask**: Recursive planning capabilities
4. **InsightTask**: Analysis and documentation
5. **InquiryTask**: Information gathering

### Plan Coordinator

Central orchestrator for plan execution:

```kotlin

```

**Key Methods:**

- `executePlan()`: Executes a complete plan
- `newState()`: Creates initial processing state
- `await()`: Waits for task completion

## Task Execution

### Task Lifecycle

1. **Planning Phase**: Tasks are identified and dependencies resolved
2. **Queuing Phase**: Tasks are ordered for execution
3. **Execution Phase**: Tasks run with dependency checking
4. **Monitoring Phase**: Progress is tracked and reported
5. **Completion Phase**: Results are collected and processed

### Dependency Management

The system automatically handles task dependencies:

```kotlin

```

### Progress Monitoring

Real-time progress tracking with visual updates:

```kotlin

```

## Implementation Guide

### Creating a Custom Cognitive Mode

1. **Implement the Interface**:

```kotlin

```

2. **Register the Mode**:

```kotlin
companion object : CognitiveModeStrategy {
    override val inputCnt = 1
    override fun getCognitiveMode(
        ui: ApplicationInterface,
        api: API,
        planSettings: PlanSettings,
        session: Session,
        user: User?,
        describer: TypeDescriber
    ) = CustomMode(ui, api, planSettings, session, user, describer)
}
```

3. **Add to Registry**:

```kotlin

```

### Creating Custom Task Types

1. **Define Task Configuration**:

```kotlin

```

2. **Implement Task Logic**:

```kotlin

```

## Configuration

### Environment Setup

```kotlin
val planSettings = PlanSettings(
    defaultModel = ChatModel.GPT4o,
    parsingModel = ChatModel.GPT4oMini,
    temperature = 0.2,
    budget = 5.0,
    autoFix = true,
    workingDir = "/path/to/project",
    maxIterations = 15,
    maxTasksPerIteration = 5
)
```

### Task Settings

```kotlin
planSettings.taskSettings["FileModificationTask"] = TaskSettingsBase(
    task_type = "FileModificationTask",
    enabled = true,
    model = ChatModel.GPT4o
)
```

### API Configuration

```kotlin
val api = OpenAIClient(apiKey = "your-api-key")
val chatClient = api as ChatClient
chatClient.budget = planSettings.budget
```

## Best Practices

### 1. Mode Selection

- **AutoPlan**: Complex, multi-step projects requiring adaptation
- **PlanAhead**: Well-defined projects with clear requirements
- **TaskChat**: Interactive development and exploration
- **GoalOriented**: Projects with complex hierarchical goals

### 2. Task Design

- Keep tasks focused and atomic
- Define clear dependencies
- Provide detailed descriptions
- Include error handling

### 3. Performance Optimization

- Set appropriate budget limits
- Use parallel execution when possible
- Monitor resource usage
- Implement caching for repeated operations

### 4. Error Handling

```kotlin
try {
    val result = executeTask(task)
    resultFn(result)
} catch (e: Exception) {
    log.error("Task execution failed", e)
    task.error(ui, e)
    resultFn("Error: ${e.message}")
}
```

### 5. Progress Monitoring

```kotlin
// Update UI with progress
task.add("Starting task execution...".renderMarkdown())
// ... perform work ...
task.complete("Task completed successfully".renderMarkdown())
```

## Troubleshooting

### Common Issues

1. **Circular Dependencies**
    - Check task dependency chains
    - Use `executionOrder()` to validate
    - Simplify complex dependency graphs

2. **Memory Issues**
    - Reduce `maxTaskHistoryChars`
    - Limit concurrent tasks
    - Clear completed task data

3. **API Budget Exceeded**
    - Increase budget limits
    - Optimize prompt efficiency
    - Use cheaper models for parsing

4. **Task Execution Failures**
    - Check file permissions
    - Validate working directory
    - Review environment variables

### Debugging Tools

1. **Logging**:

```kotlin
private val log = LoggerFactory.getLogger(YourClass::class.java)
log.debug("Task execution started: $taskId")
```

2. **State Inspection**:

```kotlin
log.info("Current state: ${JsonUtil.toJson(planProcessingState)}")
```

3. **Progress Tracking**:

```kotlin
task.verbose("Detailed execution information")
```

### Performance Monitoring

Monitor key metrics:

- Task execution time
- API call frequency
- Memory usage
- Concurrent task count

```kotlin
val startTime = System.currentTimeMillis()
// ... execute task ...
val duration = System.currentTimeMillis() - startTime
log.info("Task completed in ${duration}ms")
```
