# SelfHealingTask

## Overview

The `SelfHealingTask` is a specialized task implementation that executes commands with automatic error recovery capabilities. It runs specified commands and attempts to automatically fix any issues that arise during execution, making it ideal for build processes, test runs, or any command-line operations that may encounter recoverable errors.

## Key Features

- **Automatic Error Recovery**: Attempts to fix issues that arise during command execution
- **Multiple Command Support**: Can execute multiple commands with different working directories
- **Configurable Working Directories**: Each command can have its own working directory relative to the root
- **Command Aliasing**: Maps command aliases to actual executable paths
- **Interactive Error Handling**: Provides options to ignore errors when auto-fix fails

## Configuration

### Task Settings (`SelfHealingTaskSettings`)

| Field | Type | Description |
|-------|------|-------------|
| `task_type` | String? | The type identifier for the task |
| `enabled` | Boolean | Whether the task is enabled (default: false) |
| `model` | ApiChatModel? | The AI model to use for auto-fixing |
| `commandAutoFixCommands` | List<String>? | List of command executables that can be used for auto-fixing |

### Task Configuration (`SelfHealingTaskConfigData`)

| Field | Type | Description |
|-------|------|-------------|
| `commands` | List<CommandWithWorkingDir>? | Commands to execute with their working directories |
| `task_description` | String? | Description of the task |
| `task_dependencies` | List<String>? | List of task dependencies |
| `state` | TaskState? | Current state of the task |

### Command Configuration (`CommandWithWorkingDir`)

| Field | Type | Description |
|-------|------|-------------|
| `command` | List<String> | Command and its arguments as a list of strings |
| `workingDir` | String? | Relative path of the working directory from root |

## Usage Example

```kotlin
val taskConfig = SelfHealingTaskConfigData(
    commands = listOf(
        CommandWithWorkingDir(
            command = listOf("npm", "test"),
            workingDir = "frontend"
        ),
        CommandWithWorkingDir(
            command = listOf("gradle", "build"),
            workingDir = "backend"
        )
    ),
    task_description = "Run tests and build the project"
 )

val settings = SelfHealingTaskSettings(
    enabled = true,
    model = ApiChatModel.GPT_4,
    commandAutoFixCommands = mutableListOf(
        "/usr/bin/npm",
        "/usr/local/bin/gradle"
    )
 )
```

## How It Works

1. **Command Resolution**: The task resolves command aliases to actual executable paths using the configured `commandAutoFixCommands` list
2. **Execution**: Commands are executed in their specified working directories
3. **Error Detection**: If a command fails (non-zero exit code), the auto-fix mechanism is triggered
4. **Auto-Fix Attempt**: Uses the configured AI model to analyze the error and attempt fixes
5. **Result Handling**:
   - If successful (exit code 0): Reports completion
   - If failed: Provides an option to ignore the error and continue

## Command Resolution Logic

The task uses a sophisticated command resolution mechanism:

1. Extracts the first element of the command as an alias
2. Searches for matching executables in `commandAutoFixCommands`
3. Falls back to checking:
   - Relative path from root directory
   - Absolute file path
      +4. Throws an error if no valid executable is found

## Integration with CmdPatchApp

The task delegates execution to `CmdPatchApp` with the following configuration:

- **Root Directory**: The agent's root directory
- **Auto-Fix**: Enabled based on orchestration configuration
- **Model**: Uses either the task-specific model or the default chatter model
- **Parsing Model**: Uses the orchestration's parsing chatter model

## Error Handling

- **Retryable Wrapper**: The entire execution is wrapped in a `Retryable` block for resilience
- **Semaphore Control**: Uses semaphores to manage asynchronous execution flow
- **Interactive Recovery**: When auto-fix fails, provides an "Ignore Error" button for manual intervention

## Best Practices

1. **Command Configuration**: Always provide full paths in `commandAutoFixCommands` for reliable execution
2. **Working Directories**: Ensure working directories exist or can be created
3. **Model Selection**: Choose an appropriate AI model based on the complexity of potential errors
4. **Dependencies**: Properly configure task dependencies to ensure correct execution order
5. **Error Messages**: The task will attempt to fix compilation errors, missing dependencies, and configuration issues automatically

## Limitations

- Requires proper configuration of available commands in `commandAutoFixCommands`
- Auto-fix capability depends on the AI model's understanding of the error context
- May not be able to fix all types of errors (e.g., hardware failures, network issues)

## Logging

The task uses SLF4J logging through `LoggerFactory` for debugging and error tracking. Monitor logs for detailed execution information and troubleshooting.