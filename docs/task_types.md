# Task Types Developer Guide

## Overview

Task Types are the core building blocks of the Cognotik planning system. They define reusable, configurable units of
work that can be composed into complex workflows. Each Task Type encapsulates specific functionality while providing a
consistent interface for configuration, execution, and integration with other tasks.

## Architecture

### Core Components

The Task Type system consists of four main components:

1. **TaskType Enum**: Defines available task types with metadata
2. **TaskConfigBase**: Configuration data for task instances
3. **TaskSettingsBase**: Global settings for task types
4. **AbstractTask**: Base implementation class

```kotlin
// Task Type Definition
val FileModificationTask = TaskType(
    "FileModificationTask",
    FileModificationTaskConfigData::class.java,
    TaskSettingsBase::class.java,
    "Create new files or modify existing code with AI-powered assistance",
    tooltipHtml = "..."
)
```

### Type Safety and Registration

Task Types use compile-time type safety through generics:

```kotlin
class FileModificationTask(
    taskSettings: TaskSettingsBase,
    task: Task
) : AbstractTask<FileModificationTaskConfigData>(taskSettings, task) {
    override fun run(...) {
        val config = taskConfig ?: return handleError("Configuration required")
        // Task logic...
    }
}
```

Tasks are registered with factory functions:

```kotlin
registerConstructor(FileModificationTask) { settings, task ->
    FileModificationTask(settings, task)
}
```

## Creating a New Task Type

### Step 1: Define Configuration Data Class

Create a data class extending `TaskConfigBase`:

```kotlin
data class MyCustomTaskConfigData(
    @Description("A descriptive field")
    val exampleField: String = "defaultValue",
    
    @Description("An optional numeric parameter")
    val optionalNumber: Int? = null
) : TaskConfigBase()
```

### Step 2: Create Settings Class (Optional)

For tasks requiring global configuration:

```kotlin
data class MyCustomTaskSettings(
    override val task_type: String = "MyCustomTask",
    override val enabled: Boolean = true,
    val customSetting: String = "default"
) : TaskSettingsBase()
```

### Step 3: Implement Task Class

### Step 4: Register Task Type

```kotlin
registerConstructor(MyCustomTask) { settings, task ->
    MyCustomTask(settings as MyCustomTaskSettings, task)
}
```

## Task Categories

### File Operations

Tasks that read, modify, or create files:

- **FileModificationTask**: Create/modify source files
- **FileSearchTask**: Search files with patterns
- **InsightTask**: Analyze files and provide insights

### Planning and Coordination

Tasks that manage other tasks:

- **PlanningTask**: Break down complex goals
- **ForeachTask**: Execute subtasks for each item

```kotlin
// Example: Coordination task pattern
override fun run(...) {
    val subTasks = generateSubTasks(taskConfig)
    val planProcessingState = PlanProcessingState(subTasks)

    agent.executePlan(
        subTasks = subTasks,
        task = task,
        planProcessingState = planProcessingState,
        // ...
    )
}
```

### External Integration

Tasks that interact with external systems:

- **WebSearchTask**: Search and analyze web content
- **GitHubSearchTask**: Search GitHub repositories
- **SeleniumSessionTask**: Browser automation

### Code Execution

Tasks that execute code or commands:

- **RunCodeTask**: Execute code snippets
- **RunShellCommandTask**: Execute shell commands
- **CommandAutoFixTask**: Run commands with auto-fixing

## Best Practices

### Configuration Design

1. **Use descriptive field names and documentation**:

```kotlin
@Description("The maximum number of results to return (1-100)")
val max_results: Int = 10
```

2. **Provide sensible defaults**

3. **Use enums for constrained choices**:

```kotlin
enum class OutputFormat { JSON, }

val output_format: OutputFormat = OutputFormat.JSON
```

### Error Handling

1. **Validate configuration early**:

```kotlin
override fun run(...) {
    val config = taskConfig ?: return handleError("Configuration required")
    if (config.input_files.isEmpty()) return handleError("No input files specified")
    // ... continue with execution
}
```

2. **Provide meaningful error messages**:

```kotlin

```

3. **Use try-catch for external operations**:

```kotlin

```

### Progress Tracking

For long-running tasks:

```kotlin
override fun run(...) {
    val totalItems = taskConfig.input_files.size
    var processedItems = 0

    taskConfig.input_files.forEach { file ->
        processedItems++
        val progress = (processedItems * 100) / totalItems
        task.add("Processing: $progress% ($processedItems/$totalItems)")

        processFile(file)
    }
}
```

### Resource Management

1. **Clean up resources**:

```kotlin
override fun run(...) {
    var resource: ExternalResource? = null
    try {
        resource = acquireResource()
        // ... use resource
    } finally {
        resource?.close()
    }
}
```

## Advanced Patterns

### Actor Integration

Many tasks use AI actors for processing:

```kotlin
private val processingActor by lazy {
    SimpleActor(
        name = "MyCustomProcessor",
        prompt = """
            Process the provided content according to the specified requirements.
            Focus on accuracy and completeness.
            Provide structured output in the requested format.
        """.trimIndent(),
        model = taskSettings.model ?: planSettings.defaultModel,
        temperature = planSettings.temperature,
    )
}


```

### Parsed Response Handling

For structured AI responses:

```kotlin
data class ProcessingResult(
    val status: String,
    val items: List<ProcessedItem>,
    val summary: String
)

private val parsedActor by lazy {
    ParsedActor(
        name = "StructuredProcessor",
        resultClass = ProcessingResult::class.java,
        prompt = "Process content and return structured results...",
        model = taskSettings.model ?: planSettings.defaultModel,
        parsingModel = planSettings.parsingModel,
        describer = agent.describer,
    )
}
```

## Migration and Versioning

When updating existing task types:

1. **Maintain backward compatibility** in configuration classes
2. **Add new fields with defaults**
3. **Handle legacy configurations**:

```kotlin
override fun run(...) {
    val config = taskConfig ?: return handleError("Configuration required")

    // Handle legacy format
    val processedConfig = if (config.new_feature_enabled) {
        config
    } else {
        config.copy(new_feature_enabled = shouldEnableByDefault(config))
    }

    // Continue with updated logic...
}
```

## Debugging and Monitoring

### Logging

```kotlin
override fun run(...) {
    log.info("Starting MyCustomTask with config: ${taskConfig?.toJson()}")

    try {
        // Task execution...
        log.debug("Processed ${results.size} items")
    } catch (e: Exception) {
        log.error("Task execution failed", e)
        throw e
    }
}
```
