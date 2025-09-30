# RunCodeTask

## Overview

The `RunCodeTask` is a specialized task implementation that executes code through an interpreter to solve and complete user requests. It leverages a code runtime environment to dynamically execute code and provide interactive feedback.

## Purpose

This task is designed to:
- Execute code in various runtime environments (Kotlin, Python, etc.)
- Provide interactive code execution with feedback mechanisms
- Support automatic fixing of code issues when enabled
- Integrate with the task orchestration system for complex workflows

## Configuration

### RunCodeTaskSettings

Settings that control the behavior of the RunCodeTask:

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `task_type` | String | `TaskType.RunCodeTask.name` | The type identifier for this task |
| `codeRuntime` | CodeRuntimes? | `null` | The runtime environment to use (e.g., KotlinRuntime, PythonRuntime) |
| `enabled` | Boolean | `true` | Whether this task type is enabled |
| `model` | ApiChatModel? | `null` | The AI model to use for code generation |

### RunCodeTaskConfigData

Configuration data specific to each task instance:

| Parameter | Type | Description |
|-----------|------|-------------|
| `goal` | String? | The task or goal to be accomplished |
| `workingDir` | String? | The relative file path of the working directory |
| `task_description` | String? | Description of what this task does |
| `task_dependencies` | List<String>? | List of task IDs that must complete before this task |
| `state` | TaskState? | Current state of the task execution |

## Key Features

### 1. **Multiple Runtime Support**
- Supports various code runtimes through the `CodeRuntimes` enum
- Default runtime is Kotlin if not specified
- Runtime environment includes working directory and environment variables

### 2. **Interactive Execution**
- Provides interactive feedback during code execution
- Users can continue, revise, or provide feedback on code results
- Supports both manual and automatic execution modes

### 3. **Auto-Fix Capability**
- When `autoFix` is enabled in orchestration config, automatically attempts to fix code issues
- Limits automatic retries to prevent infinite loops
- Provides detailed output including code, results, and console output

### 4. **Integration with CodingAgent**
- Extends the `CodingAgent` class for sophisticated code generation
- Passes environment variables and working directory to the runtime
- Supports temperature control for AI model responses

## Usage Example

```kotlin
// Create task settings
val settings = RunCodeTaskSettings(
    codeRuntime = CodeRuntimes.PythonRuntime,
    enabled = true,
    model = ApiChatModel.GPT4
)

// Create task configuration
val config = RunCodeTaskConfigData(
    goal = "Calculate the fibonacci sequence up to n=10",
    workingDir = "./workspace",
    task_description = "Generate and execute fibonacci calculation"
)

// Initialize and run the task
val task = RunCodeTask(orchestrationConfig, config)
task.run(agent, messages, sessionTask, resultCallback, orchestrationConfig)
```

## Execution Flow

1. **Initialization**: Sets up the code runtime with environment variables and working directory
2. **Code Generation**: Uses AI model to generate code based on the goal and messages
3. **Execution**: Runs the generated code in the specified runtime
4. **Feedback Loop**:
   - If auto-fix is disabled: Presents results and waits for user interaction
   - If auto-fix is enabled: Automatically attempts to fix issues (limited to 1 retry)
5. **Result Handling**: Formats and returns the execution results including code, output, and any errors

## Output Format

The task produces formatted output containing:
- **Command**: The generated code
- **Result**: The return value of the code execution
- **Output**: Console output from the execution

Example output format:
```
## Command
```
[generated code]
```
## Result
```
[execution result]
```
## Output
```
[console output]
```
```

## Error Handling

- Uses semaphore-based synchronization for execution control
- Catches and logs exceptions during execution
- Provides feedback mechanisms for error correction
- Limits automatic retries to prevent infinite loops

## Dependencies

- `CodeAgent`: For code generation and execution
- `CodingAgent`: Extended class providing code interaction capabilities
- `CodeRuntimes`: Runtime environment management
- `SessionTask`: UI and session management
- `TaskOrchestrator`: Parent orchestration system

## Best Practices

1. **Runtime Selection**: Choose the appropriate runtime based on the task requirements
2. **Working Directory**: Ensure the working directory has appropriate permissions
3. **Environment Variables**: Pass necessary environment variables through the orchestration config
4. **Auto-Fix Usage**: Use auto-fix cautiously for tasks that might have side effects
5. **Error Handling**: Always handle potential execution failures in dependent tasks

## Limitations

- Auto-fix is limited to one retry to prevent infinite loops
- Runtime must be supported by the CodeRuntimes implementation
- Execution is synchronous and blocks until completion or user interaction
- Security considerations depend on the underlying runtime implementation