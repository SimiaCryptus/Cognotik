Here's a best practices document for developing apps in this project:

# Best Practices for Cognotik Apps

## Application Architecture

### Core Design Principles

1. Single Responsibility:

```kotlin
// Each class should have a single, well-defined purpose
class DocumentProcessor(
    val parser: DocumentParser,
    val validator: DocumentValidator,
    val storage: DocumentStorage
)
```

2. Dependency Injection:

```kotlin
class MyApp(
    private val api: API,
    private val storage: StorageService,
    private val validator: ValidationService
) : ApplicationServer {
    // Dependencies are injected rather than created internally
}
```

### Application Structure

1. Separate core logic from UI:

```kotlin
class MyApp(
    // Core configuration 
    val settings: Settings,
    val model: ChatModel,
    // UI configuration
    applicationName: String = "My App",
    path: String = "/myapp"
) : ApplicationServer(applicationName, path) {
    // Core business logic methods
    private fun processData() {}

    // UI handling methods
    override fun userMessage() {}
}
```

2. Use immutable data classes for configuration:

```kotlin
data class Settings(
    val maxItems: Int = 100,
    val timeout: Duration = Duration.ofMinutes(5),
    val features: Set<Feature> = setOf(),
    val retryConfig: RetryConfig = RetryConfig(),
    val validationRules: List<ValidationRule> = listOf()
)
data class RetryConfig(
    val maxAttempts: Int = 3,
    val backoffMs: Long = 1000
)
```

3. Handle state management:

```kotlin
// Per-session state
private val sessionState = mutableMapOf<String, SessionState>()
// Thread-safe state updates
private val stateGuard = AtomicBoolean(false)

// Immutable state updates
fun updateState(sessionId: String, update: (SessionState) -> SessionState) {
    synchronized(stateGuard) {
        sessionState[sessionId] = update(sessionState[sessionId] ?: SessionState())
    }
}
```

## Logging Best Practices

### Logging Guidelines

1. Use consistent log formats:

```kotlin
private fun logEvent(
    event: String,
    data: Map<String, Any?>,
    level: LogLevel = LogLevel.INFO
) {
    when (level) {
        LogLevel.DEBUG -> log.debug("$event: ${data.toJson()}")
        LogLevel.INFO -> log.info("$event: ${data.toJson()}")
        LogLevel.WARN -> log.warn("$event: ${data.toJson()}")
        LogLevel.ERROR -> log.error("$event: ${data.toJson()}")
    }
}
```

### Sub-Log Creation

1. Create child API clients with dedicated logs for each major operation:

```kotlin
val api = (api as ChatClient).getChildClient().apply {
    val createFile = task.createFile(".logs/api-${UUID.randomUUID()}.log")
    createFile.second?.apply {
        logStreams += this.outputStream().buffered()
        task.verbose("API log: <a href=\"file:///$this\">$this</a>")
    }
}
```

### Structured Logging

```kotlin
log.info(
    "Processing request", mapOf(
        "userId" to user.id,
        "requestType" to requestType,
        "timestamp" to System.currentTimeMillis(),
        "context" to mapOf(
            "session" to session.id,
            "environment" to env,
            "features" to enabledFeatures
        )
    )
)
```

2. Use appropriate log levels:

```kotlin
log.debug("Fine-grained diagnostic info")
log.info("General operational events")
log.warn("Potentially harmful situations")
log.error("Error events that might still allow the app to continue")
// Add context to error logs
log.error(
    "Operation failed", mapOf(
        "error" to e.message,
        "stackTrace" to e.stackTraceToString(),
        "context" to operationContext
    )
)
```

## Resource Management

### API Client Lifecycle

```kotlin
// Use structured resource management
inline fun <T> withAPI(crossinline block: (API) -> T): T {
    return api.use { client ->
        try {
            block(client)
        } finally {
            client.close()
        }
    }
}

api.use { client ->
    try {
        // Use API client
    } finally {
        client.close()
    }
}
```

### Memory Management

```kotlin
// Use sequences for large collections and implement pagination
files.asSequence()
    .filter { it.length() < maxSize }
    .map { process(it) }
    .take(limit)
    .chunked(pageSize)
    .toList()
// Implement resource pooling
val resourcePool = ResourcePool<ExpensiveResource>(
    maxSize = 10,
    factory = { createExpensiveResource() }
)

// Clear buffers after use
buffer.clear()
```

3. Include contextual information in logs:

```kotlin
log.info("Processing user message: $userMessage")
log.error("Error processing task ${taskId}", exception)
```

## Exception Handling

### General Exception Handling Pattern

```kotlin
try {
    // Main operation
    checkPreconditions()
    validateInput(data)
} catch (e: SocketTimeoutException) {
    log.error("Network timeout", e)
    task.add("The operation timed out. Please check your network connection.")
} catch (e: IOException) {
    log.error("I/O error", e)
    task.add("An I/O error occurred. Please try again later.")
} catch (e: IllegalStateException) {
    log.error("Invalid state", e)
    task.add("Operation cannot be completed in current state")
} catch (e: Exception) {
    log.error("Unexpected error", e)
    task.error(ui, e)
} finally {
    cleanup()
    task.complete()
}
```

### Input Validation

```kotlin
fun validate(input: UserInput) {
    require(input.name.isNotBlank()) { "Name cannot be empty" }
    require(input.age in 0..150) { "Invalid age" }
    check(isInitialized) { "System not initialized" }
}
```

### User-Friendly Error Messages

```kotlin
when (e) {
    is IllegalArgumentException -> task.add("Invalid input: ${e.message}")
    is IllegalStateException -> task.add("Operation failed: ${e.message}")
    is SecurityException -> task.add("Access denied: ${e.message}")
    else -> task.add("An unexpected error occurred. Please try again later.")
}
```

## Using Retry and Tabs

### Retryable Operations

1. Wrap retryable operations using the Retryable class:

```kotlin
Retryable(ui, task) { content ->
    try {
        // Operation that might need retry
        val result = performOperation()
        renderMarkdown(result, ui = ui)
    } catch (e: Exception) {
        task.error(ui, e)
        "Error: ${e.message}"
    }
}
```

### Tab Management

1. Create organized tab displays:

```kotlin
val tabbedDisplay = TabbedDisplay(task)
tabbedDisplay["Tab Name"] = content
tabbedDisplay.update()
```

2. Handle nested tabs:

```kotlin
val parentTabs = TabbedDisplay(task)
val childTask = ui.newTask(false)
parentTabs["Parent Tab"] = childTask.placeholder
val childTabs = TabbedDisplay(childTask)
```

## Task Status Management

### Task Lifecycle States

1. Initial State:

```kotlin
val task = ui.newTask()
task.add(SessionTask.spinner) // Show loading spinner
```

2. In Progress:

```kotlin
task.add("Processing...") // Update status
task.verbose("Detailed progress info") // Show detailed progress
```

3. Completion:

```kotlin
task.complete() // Normal completion
task.complete("Operation completed successfully") // Completion with message
```

4. Error State:

```kotlin
task.error(ui, exception) // Show error with details
```

### Progress Tracking

1. Use progress bars for long operations:

```kotlin
val progressBar = progressBar(ui.newTask())
progressBar.add(completedItems.toDouble(), totalItems.toDouble())
```

## Best Practices for Task Organization

1. Break down complex operations into subtasks:

```kotlin
val mainTask = ui.newTask()
val subTask1 = ui.newTask(false)
val subTask2 = ui.newTask(false)
mainTask.verbose(subTask1.placeholder)
mainTask.verbose(subTask2.placeholder)
```

2. Use meaningful task headers:

```kotlin
task.header("Processing Stage 1")
// ... operations ...
task.header("Processing Stage 2")
```

3. Provide visual feedback:

```kotlin
task.add(
    MarkdownUtil.renderMarkdown("""
## Current Progress
- Step 1: Complete ✓
- Step 2: In Progress ⟳
- Step 3: Pending ○
""", ui = ui
    )
)
```

## Memory and Resource Management

1. Clean up resources:

```kotlin
try {
    // Use resources
} finally {
    resource.close()
    task.complete()
}
```

2. Handle large data efficiently:

```kotlin
fun truncate(output: String, kb: Int = 32): String {
    if (output.length > 1024 * 2 * kb) {
        return output.substring(0, 1024 * kb) +
                "\n\n... Output truncated ...\n\n" +
                output.substring(output.length - 1024 * kb)
    }
    return output
}
```

Following these best practices will help ensure your apps are reliable, maintainable, and provide a good user experience.