# Task Types Developer Guide

## Overview

Task Types are the core building blocks of the Cognotik planning system. They define reusable, configurable units of work that can be composed into complex workflows. Each Task Type encapsulates specific functionality while providing a consistent interface for configuration, execution, and integration with other tasks.

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

// Configuration Data Class
class FileModificationTaskConfigData(
    val files: List<String>? = null,
    val modifications: Any? = null,
    // ... other config fields
) : TaskConfigBase(...)

// Implementation Class
class FileModificationTask(
    planSettings: PlanSettings,
    planTask: FileModificationTaskConfigData?
) : AbstractTask<FileModificationTaskConfigData>(planSettings, planTask)
```

### Type Safety and Registration

Task Types use compile-time type safety through generics:

```kotlin
class TaskType<out T : TaskConfigBase, out U : TaskSettingsBase>(
    name: String,
    val taskDataClass: Class<out T>,
    val taskSettingsClass: Class<out U>,
    // ...
)
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
class MyCustomTaskConfigData(
    @Description("Input files to process")
    val input_files: List<String> = emptyList(),

    @Description("Processing mode")
    val mode: ProcessingMode = ProcessingMode.DEFAULT,

    @Description("Output directory")
    val output_dir: String? = null,

    // Required base fields
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
) : TaskConfigBase(
    task_type = TaskType.MyCustomTask.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
)

enum class ProcessingMode {
    DEFAULT, ADVANCED, BATCH
}
```

### Step 2: Create Settings Class (Optional)

For tasks requiring global configuration:

```kotlin
class MyCustomTaskSettings(
    task_type: String? = null,
    enabled: Boolean = false,
    model: ChatModel? = null,

    @Description("Custom setting for this task type")
    var customSetting: String? = null,

    @Description("Processing timeout in minutes")
    var timeoutMinutes: Int = 30
) : TaskSettingsBase(task_type, enabled, model)
```

### Step 3: Implement Task Class

```kotlin
class MyCustomTask(
    planSettings: PlanSettings,
    planTask: MyCustomTaskConfigData?
) : AbstractTask<MyCustomTaskConfigData>(planSettings, planTask) {

    // Override settings if using custom settings class
    override val taskSettings: MyCustomTaskSettings
        get() = super.taskSettings as MyCustomTaskSettings

    override fun promptSegment() = """
        MyCustomTask - Brief description of what this task does
          ** Specify input files to process
          ** Choose processing mode (DEFAULT, ADVANCED, BATCH)
          ** Optionally specify output directory
        Available files:
        ${getAvailableFiles().joinToString("\n") { "  - $it" }}
    """.trimIndent()

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        planSettings: PlanSettings
    ) {
        // Validate configuration
        val config = taskConfig ?: run {
            val error = "MyCustomTask configuration is required"
            task.error(ui = agent.ui, Exception(error))
            resultFn(error)
            return
        }

        // Process inputs
        val results = processInputs(config, agent, task, api)

        // Generate output
        val output = formatResults(results)

        // Update UI
        task.add(MarkdownUtil.renderMarkdown(output, ui = agent.ui))

        // Return result
        resultFn(output)
    }

    private fun processInputs(
        config: MyCustomTaskConfigData,
        agent: PlanCoordinator,
        task: SessionTask,
        api: ChatClient
    ): ProcessingResults {
        // Implementation details...
    }

    private fun formatResults(results: ProcessingResults): String {
        return buildString {
            appendLine("# MyCustomTask Results")
            appendLine()
            appendLine("Processed ${results.fileCount} files")
            // ... format output
        }
    }
}
```

### Step 4: Register Task Type

```kotlin
companion object {
    val MyCustomTask = TaskType(
        "MyCustomTask",
        MyCustomTaskConfigData::class.java,
        MyCustomTaskSettings::class.java, // or TaskSettingsBase::class.java
        "Process files with custom logic",
        """
        Processes input files using configurable custom logic.
        <ul>
          <li>Supports multiple processing modes</li>
          <li>Configurable output destinations</li>
          <li>Batch processing capabilities</li>
          <li>Progress tracking and error handling</li>
        </ul>
        """
    )

    init {
        registerConstructor(MyCustomTask) { settings, task ->
            MyCustomTask(settings, task)
        }
    }
}
```

## Task Categories

### File Operations

Tasks that read, modify, or create files:

- **FileModificationTask**: Create/modify source files
- **FileSearchTask**: Search files with patterns
- **InsightTask**: Analyze files and provide insights

```kotlin
// Example: File-based task with input validation
private fun getInputFiles(): List<Path> {
    return taskConfig?.input_files?.mapNotNull { pattern ->
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        Files.walk(root)
            .filter { matcher.matches(root.relativize(it)) }
            .filter { !FileSelectionUtils.isLLMIgnored(it) }
            .toList()
    }?.flatten() ?: emptyList()
}
```

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

```kotlin
// Example: External API integration
private fun callExternalAPI(query: String): APIResponse {
    val client = HttpClient.newBuilder().build()
    val request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.example.com/search?q=$query"))
        .header("Authorization", "Bearer $token")
        .build()

    val response = client.send(request, HttpResponse.BodyHandlers.ofString())
    return parseResponse(response.body())
}
```

### Code Execution

Tasks that execute code or commands:

- **RunCodeTask**: Execute code snippets
- **RunShellCommandTask**: Execute shell commands
- **CommandAutoFixTask**: Run commands with auto-fixing

```kotlin
// Example: Safe code execution pattern
private fun executeWithTimeout(code: String, timeoutMinutes: Long): ExecutionResult {
    return try {
        val future = executor.submit { interpreter.execute(code) }
        future.get(timeoutMinutes, TimeUnit.MINUTES)
    } catch (e: TimeoutException) {
        ExecutionResult.timeout("Execution timed out after $timeoutMinutes minutes")
    } catch (e: Exception) {
        ExecutionResult.error("Execution failed: ${e.message}")
    }
}
```

## Best Practices

### Configuration Design

1. **Use descriptive field names and documentation**:
```kotlin
@Description("The maximum number of results to return (1-100)")
val max_results: Int = 10
```

2. **Provide sensible defaults**:
```kotlin
val timeout_seconds: Long = 30L,
val retry_attempts: Int = 3,
val include_metadata: Boolean = true
```

3. **Use enums for constrained choices**:
```kotlin
enum class OutputFormat { JSON, CSV, MARKDOWN }
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
private fun handleError(message: String, cause: Throwable? = null): String {
    val fullMessage = "MyCustomTask failed: $message"
    log.error(fullMessage, cause)
    task.error(ui = agent.ui, Exception(fullMessage, cause))
    resultFn(fullMessage)
    return fullMessage
}
```

3. **Use try-catch for external operations**:
```kotlin
private fun safeFileOperation(file: Path): String? {
    return try {
        Files.readString(file)
    } catch (e: IOException) {
        log.warn("Failed to read file: $file", e)
        null
    }
}
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

2. **Use connection pooling for HTTP clients**:
```kotlin
companion object {
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
}
```

### Output Formatting

1. **Use consistent markdown formatting**:
```kotlin
private fun formatResults(results: List<Result>): String = buildString {
    appendLine("# Task Results")
    appendLine()
    appendLine("## Summary")
    appendLine("- Processed: ${results.size} items")
    appendLine("- Success: ${results.count { it.success }}")
    appendLine("- Errors: ${results.count { !it.success }}")
    appendLine()

    results.forEachIndexed { index, result ->
        appendLine("### Result ${index + 1}")
        appendLine("Status: ${if (result.success) "✅ Success" else "❌ Error"}")
        if (result.data.isNotEmpty()) {
            appendLine("```")
            appendLine(result.data)
            appendLine("```")
        }
        appendLine()
    }
}
```

2. **Limit output size for large results**:
```kotlin
private fun truncateIfNeeded(content: String, maxLength: Int = 10000): String {
    return if (content.length > maxLength) {
        content.take(maxLength) + "\n\n... (truncated)"
    } else content
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

private fun processWithActor(content: String, requirements: String): String {
    return processingActor.answer(
        listOf(content, requirements),
        api = api
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

### Concurrent Processing

For tasks that can benefit from parallelism:

```kotlin
private fun processFilesInParallel(files: List<Path>): List<ProcessingResult> {
    val executor = Executors.newFixedThreadPool(4)
    try {
        val futures = files.map { file ->
            executor.submit { processFile(file) }
        }
        return futures.map { it.get() }
    } finally {
        executor.shutdown()
    }
}
```

### Session Management

For tasks that maintain state across operations:

```kotlin
companion object {
    private val activeSessions = ConcurrentHashMap<String, SessionState>()
}

private fun getOrCreateSession(sessionId: String?): SessionState {
    return sessionId?.let { id ->
        activeSessions.computeIfAbsent(id) { SessionState() }
    } ?: SessionState() // Temporary session
}
```

## Testing Task Types

### Unit Testing

```kotlin
class MyCustomTaskTest {
    @Test
    fun `should process files successfully`() {
        val planSettings = PlanSettings(/* test configuration */)
        val taskConfig = MyCustomTaskConfigData(
            input_files = listOf("test.txt"),
            mode = ProcessingMode.DEFAULT
        )

        val task = MyCustomTask(planSettings, taskConfig)
        val result = task.run(/* test parameters */)

        assertThat(result).contains("success")
    }

    @Test
    fun `should handle missing configuration`() {
        val planSettings = PlanSettings()
        val task = MyCustomTask(planSettings, null)

        assertThrows<IllegalArgumentException> {
            task.run(/* test parameters */)
        }
    }
}
```

### Integration Testing

```kotlin
@Test
fun `should integrate with planning system`() {
    val plan = mapOf(
        "task1" to MyCustomTaskConfigData(
            input_files = listOf("input.txt"),
            task_dependencies = emptyList()
        )
    )

    val coordinator = PlanCoordinator(/* configuration */)
    val results = coordinator.executePlan(plan)

    assertThat(results.completedTasks).contains("task1")
}
```

## Migration and Versioning

When updating existing task types:

1. **Maintain backward compatibility** in configuration classes
2. **Add new fields with defaults**:
```kotlin
class UpdatedTaskConfigData(
    // Existing fields...
    val existing_field: String = "",

    // New fields with defaults
    val new_feature_enabled: Boolean = false,
    val new_option: String? = null
)
```

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
companion object {
    private val log = LoggerFactory.getLogger(MyCustomTask::class.java)
}

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

### Performance Monitoring

```kotlin
private fun <T> measureTime(operation: String, block: () -> T): T {
    val startTime = System.currentTimeMillis()
    try {
        return block()
    } finally {
        val duration = System.currentTimeMillis() - startTime
        log.info("$operation completed in ${duration}ms")
    }
}
```
