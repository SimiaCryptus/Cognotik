# FileModificationTask

## Overview

The `FileModificationTask` is a specialized task implementation for creating new files or modifying existing code with AI-powered assistance. It extends `AbstractFileTask` and provides intelligent code generation and modification capabilities while maintaining code quality and project standards.

## Purpose

This task enables automated or semi-automated code modifications through AI assistance, supporting both file creation and modification workflows with proper diff generation, Git integration, and approval mechanisms.

## Key Features

- **AI-Powered Code Generation**: Uses ChatAgent to generate precise code modifications based on requirements
- **Diff Format Support**: Presents changes in standard diff format for easy review
- **Git Integration**: Optionally includes Git diffs with HEAD for context
- **Dual Mode Operation**: Supports both automated application and manual approval workflows
- **Multi-File Operations**: Handles complex operations across multiple files
- **Code Quality Maintenance**: Preserves coding standards and project conventions
- **Comprehensive Documentation**: Provides clear rationale for all changes

## Configuration

### FileModificationTaskConfigData

The task configuration extends `FileTaskConfigBase` with the following parameters:

| Parameter | Type | Description | Default |
|-----------|------|-------------|---------|
| `files` | `List<String>?` | List of files to be modified | `null` |
| `related_files` | `List<String>?` | Additional files for context | `null` |
| `extractContent` | `Boolean` | Whether to extract file content | `false` |
| `modifications` | `Any?` | Specific modifications to be made | `null` |
| `includeGitDiff` | `Boolean` | Include Git diff with HEAD | `false` |
| `task_description` | `String?` | Description of the modification task | `null` |
| `task_dependencies` | `List<String>?` | List of dependent task descriptions | `null` |
| `state` | `TaskState?` | Current task state | `null` |

## Core Functionality

### 1. Git Diff Integration

The task can retrieve Git diffs for files to provide additional context:

```kotlin
private fun getGitDiff(filePath: String): String?
```

- Executes `git diff HEAD` for specified files
- Includes 10-second timeout for Git operations
- Handles errors gracefully with logging

### 2. Input File Processing

```kotlin
private fun getInputFileWithDiff(): String
```

- Combines file content with Git diffs when enabled
- Formats input for AI processing
- Provides comprehensive context for modifications

### 3. AI-Powered Modification

The task uses a ChatAgent with a detailed prompt that instructs the AI to:

#### For Existing Files:
- Write efficient, readable, and maintainable code changes
- Ensure smooth integration with existing code
- Follow project coding standards
- Consider dependencies and side effects
- Provide clear context and rationale

#### For New Files:
- Choose appropriate file locations and names
- Structure code according to project conventions
- Include necessary imports and dependencies
- Add comprehensive documentation
- Avoid duplication of existing functionality

### 4. Response Format

The AI generates responses in specific formats:

- **Existing Files**: Uses diff code blocks with file path headers
- **New Files**: Uses language-specific code blocks with file path headers
- **Context Lines**: Includes 2 lines before and after changes in diffs
- **Separation**: Code blocks separated by blank lines

## Execution Flow

### 1. Validation Phase
```kotlin
// Checks for input files
if (files.isEmpty()) {
    return "CONFIGURATION ERROR: No input files specified"
}
```

### 2. Processing Phase
- Creates new task in task manager
- Initializes ChatInterface with configured model
- Constructs ChatAgent with specialized prompt
- Processes input files and dependencies

### 3. Generation Phase
- AI generates code modifications
- Formats response according to specifications
- Includes rationale and documentation

### 4. Application Phase

#### Auto-Fix Mode:
```kotlin
if (orchestrationConfig.autoFix) {
    // Automatically applies changes
    // Updates completion notes
    // Marks task as complete
}
```

#### Manual Approval Mode:
- Presents changes with diff visualization
- Provides accept/reject buttons
- Waits for user confirmation

### 5. Completion Phase
- Updates file system with approved changes
- Generates completion notes with file links
- Releases semaphore for synchronization

## Integration Points

### Dependencies
- `ChatAgent`: For AI-powered code generation
- `AddApplyFileDiffLinks`: For diff visualization and application
- `MarkdownUtil`: For rendering markdown output
- `Retryable`: For error recovery
- `SessionTask`: For task management

### File System Operations
- Reads existing files from configured root directory
- Writes modified files back to file system
- Maintains relative path structure
- Handles file creation and modification

### Git Integration
- Optional Git diff retrieval
- Provides version control context
- Helps understand recent changes

## Error Handling

### Configuration Errors
- Validates presence of input files
- Returns clear error messages
- Prevents execution with invalid configuration

### Git Operation Errors
- Timeouts for Git commands (10 seconds)
- Graceful fallback when Git unavailable
- Warning logs for debugging

### Execution Errors
- Wrapped in Retryable for automatic recovery
- Comprehensive error logging
- Semaphore-based synchronization

## Usage Example

```kotlin
// Configuration
val config = FileModificationTaskConfigData(
    files = listOf("src/main/kotlin/MyClass.kt"),
    related_files = listOf("src/test/kotlin/MyClassTest.kt"),
    includeGitDiff = true,
    task_description = "Add error handling to MyClass",
    modifications = "Add try-catch blocks for IO operations"
)

// Create and execute task
val task = FileModificationTask(orchestrationConfig, config)
task.run(
    agent = orchestrator,
    messages = listOf("Previous context"),
    task = sessionTask,
    resultFn = { result -> println("Completed: $result") },
    orchestrationConfig = config
)
```

## Output Format

### Diff Format (Existing Files)
```diff
### src/utils/existingFile.js
  function existingFunction() {
-   return 'old result';
+   return 'new result';
  }
```

### New File Format
```javascript
### src/utils/newFile.js
function newFunction() {
  return 'new functionality';
}
```

## Best Practices

1. **File Selection**: Carefully specify input files to provide appropriate context
2. **Git Integration**: Enable `includeGitDiff` when working with recently modified files
3. **Dependencies**: List task dependencies for complex multi-step operations
4. **Auto-Fix Mode**: Use cautiously in production environments
5. **Description Clarity**: Provide clear task descriptions for better AI understanding

## Performance Considerations

- **Semaphore Synchronization**: Ensures proper task completion before proceeding
- **Timeout Management**: 10-second timeout for Git operations prevents hanging
- **Retryable Wrapper**: Automatic retry on transient failures
- **Async Execution**: Task runs in thread pool for non-blocking operation

## Logging

Uses SLF4J logging through `LoggerFactory`:
- `WARN`: Git operation failures and timeouts
- `WARN`: General execution errors
- Provides debugging information for troubleshooting

## Limitations

1. Git diff timeout fixed at 10 seconds
2. Requires file system access for modifications
3. AI model limitations affect code quality
4. No built-in rollback mechanism
5. Limited to text-based file modifications

## See Also

- [`AbstractFileTask`](./AbstractFileTask.md) - Base class for file operations
- [`FileSearchTask`](./FileSearchTask.md) - Related file search functionality
- [`TaskOrchestrator`](../../TaskOrchestrator.md) - Task orchestration system
- [`ChatAgent`](../../../actors/ChatAgent.md) - AI chat interface