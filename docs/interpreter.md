# Cognotik Interpreter Subsystem Developer Guide

## Overview

The Cognotik Interpreter Subsystem provides a unified interface for executing code in multiple programming languages within the Cognotik framework. It enables dynamic code execution, validation, and integration with AI-powered coding assistants.

## Architecture

### Core Components

```
cognotik-interpreter/
├── core/                    # Core interpreter interface and utilities
├── kotlin/                  # Kotlin interpreter implementation
├── groovy/                  # Groovy interpreter implementation
└── webui/                   # Web UI integration and process interpreter
```

### Key Classes

1. **`Interpreter`** - Core interface defining the contract for all interpreters
2. **`KotlinInterpreter`** - Kotlin-specific implementation using JSR-223
3. **`GroovyInterpreter`** - Groovy-specific implementation
4. **`ProcessInterpreter`** - Generic process-based interpreter for shell commands
5. **`OutputInterceptor`** - Utility for capturing stdout/stderr from code execution

## Core Interface

### Interpreter Interface

The `Interpreter` interface defines the contract that all language-specific interpreters must implement:

```kotlin
interface Interpreter {
    fun getLanguage(): String
    fun getSymbols(): Map<String, Any>
    fun run(code: String): Any?
    fun validate(code: String): Throwable?

    // Optional overrides
    fun wrapCode(code: String): String = code
    fun <T : Any> wrapExecution(fn: java.util.function.Supplier<T?>): T? = fn.get()
}
```

#### Key Methods

- **`getLanguage()`**: Returns the language identifier (e.g., "kotlin", "groovy")
- **`getSymbols()`**: Returns available variables/objects in the interpreter context
- **`run(code: String)`**: Executes code and returns the result
- **`validate(code: String)`**: Validates code without execution, returns error or null
- **`wrapCode(code: String)`**: Optional code preprocessing (imports, setup, etc.)
- **`wrapExecution(fn: Supplier<T?>)`**: Optional execution wrapper for monitoring/security

## Language Implementations

### Kotlin Interpreter

The `KotlinInterpreter` uses the Kotlin JSR-223 scripting engine for code execution.

#### Key Features

- **Classpath Management**: Automatically includes project dependencies
- **Script Instance Sharing**: Enables variable persistence across executions
- **Error Handling**: Provides detailed error messages with line/column information
- **Import Processing**: Handles Kotlin imports correctly

#### Usage Example

```kotlin
val interpreter = KotlinInterpreter(mapOf(
    "message" to "Hello World",
    "calculator" to Calculator()
))

// Execute code
val result = interpreter.run("""
    println(message)
    calculator.add(2, 3)
""")

// Validate before execution
val error = interpreter.validate("invalid syntax here")
if (error != null) {
    throw error
}
```

#### Configuration

```kotlin
class KotlinInterpreter(
    val defs: Map<String, Any> = mapOf(),
) : Interpreter {

    // Custom script engine configuration
    open val scriptEngine: KotlinJsr223JvmScriptEngineBase
        get() = // ... engine setup with classpath and evaluation config
}
```

### Groovy Interpreter

The `GroovyInterpreter` uses Groovy's built-in scripting capabilities.

#### Usage Example

```kotlin
val interpreter = GroovyInterpreter(mapOf(
    "data" to listOf(1, 2, 3, 4, 5)
))

val result = interpreter.run("""
    data.findAll { it % 2 == 0 }.sum()
""")
```

### Process Interpreter

The `ProcessInterpreter` executes code by spawning external processes, useful for shell scripts and system commands.

#### Configuration

```kotlin
val interpreter = ProcessInterpreter(mapOf(
    "command" to listOf("python3"),  // or "python3" as String
    "language" to "python",
    "workingDir" to "/path/to/project",
    "timeoutMinutes" to 10L,
    "env" to mapOf("PYTHONPATH" to "/custom/path")
))
```

#### Usage Example

```kotlin
val result = interpreter.run("""
    import sys
    print(f"Python version: {sys.version}")
    print("Hello from Python!")
""")
```

## Output Interception

The `OutputInterceptor` captures stdout/stderr output from code execution, providing both thread-local and global output streams.

### Setup

```kotlin
// Initialize output interception (call once at startup)
OutputInterceptor.setupInterceptor()
```

### Usage

```kotlin
// Clear previous output
OutputInterceptor.clearThreadOutput()

// Execute code that produces output
interpreter.run("""println("Hello World")""")

// Retrieve captured output
val output = OutputInterceptor.getThreadOutput()
println("Captured: $output")
```

### Features

- **Thread-local buffering**: Each thread has its own output buffer
- **Global output stream**: Captures all output across threads
- **Automatic buffer management**: Prevents memory leaks with size limits
- **Original stream preservation**: Maintains normal console output

## Integration with Coding Agents

### CodingAgent Integration

The interpreter subsystem integrates with `CodingAgent` for AI-powered code generation and execution:

```kotlin
class CodingAgent<T : Interpreter>(
    val interpreter: KClass<T>,
    val symbols: Map<String, Any>,
    // ... other parameters
) {

    val actor by lazy {
        CodingActor(
            interpreter,
            symbols = symbols,
            // ... configuration
        )
    }
}
```

### Usage in Planning Tasks

```kotlin
class RunCodeTask<T : Interpreter>(
    val interpreter: KClass<T>,
    // ... other parameters
) : AbstractTask<RunCodeTaskConfigData> {

    override fun run(/* ... */) {
        val codingAgent = CodingAgent<T>(
            interpreter = interpreter,
            symbols = mapOf(
                "env" to planSettings.env,
                "workingDir" to workingDir,
                "language" to "kotlin"
            )
        )

        codingAgent.start(userMessage)
    }
}
```

## Error Handling

### Exception Types

1. **`CodingActor.FailedToImplementException`**: Code execution failures
2. **`ScriptException`**: Compilation/syntax errors
3. **`ValidationError`**: Code validation failures

### Error Processing

```kotlin
override fun validate(code: String): Throwable? {
    return try {
        scriptEngine.compile(wrapCode(code))
        null
    } catch (ex: ScriptException) {
        wrapException(ex, wrappedCode, code)
    } catch (ex: Throwable) {
        CodingActor.FailedToImplementException(
            cause = ex,
            language = "Kotlin",
            code = code,
        )
    }
}
```

### Error Message Formatting

```kotlin
fun errorMessage(code: String, line: Int, column: Int, message: String) =
    """
    ```text
    $message at line $line column $column
      ${if (line < 0) "" else code.split("\n")[line - 1]}
      ${if (column < 0) "" else " ".repeat(column - 1) + "^"}
    ```
    """.trim()
```

## Testing

### Base Test Class

The framework provides `InterpreterTestBase` for consistent testing across implementations:

```kotlin
abstract class InterpreterTestBase {
    @Test
    fun `test run with valid code`() {
        val interpreter = newInterpreter(mapOf())
        val result = interpreter.run("2 + 2")
        Assertions.assertEquals(4, result)
    }

    @Test
    fun `test run with variables`() {
        val interpreter = newInterpreter(mapOf("x" to 2, "y" to 3))
        val result = interpreter.run("x * y")
        Assertions.assertEquals(6, result)
    }

    abstract fun newInterpreter(map: Map<String, Any>): Interpreter
}
```

### Implementation-Specific Tests

```kotlin
class KotlinInterpreterTest : InterpreterTestBase() {
    override fun newInterpreter(map: Map<String, Any>) = KotlinInterpreter(map)

    @Test
    fun `test kotlin-specific features`() {
        val interpreter = newInterpreter(mapOf())
        val result = interpreter.run("""
            fun factorial(n: Int): Int =
                if (n <= 1) 1 else n * factorial(n - 1)
            factorial(5)
        """)
        Assertions.assertEquals(120, result)
    }
}
```

## Best Practices

### Security Considerations

1. **Sandbox Execution**: Consider using security managers or containers
2. **Resource Limits**: Implement timeouts and memory limits
3. **Input Validation**: Validate code before execution
4. **Output Sanitization**: Clean output before displaying to users

### Performance Optimization

1. **Engine Reuse**: Cache script engines when possible
2. **Classpath Optimization**: Minimize classpath scanning
3. **Compilation Caching**: Cache compiled scripts for repeated execution
4. **Memory Management**: Clear buffers and release resources

### Code Organization

1. **Separation of Concerns**: Keep language-specific logic isolated
2. **Configuration Management**: Use dependency injection for interpreter setup
3. **Error Handling**: Provide meaningful error messages with context
4. **Testing**: Maintain comprehensive test coverage

## Extension Points

### Adding New Languages

1. **Implement Interpreter Interface**:
```kotlin
class PythonInterpreter(private val defs: Map<String, Any>) : Interpreter {
    override fun getLanguage() = "python"
    override fun getSymbols() = defs
    override fun run(code: String): Any? { /* implementation */ }
    override fun validate(code: String): Throwable? { /* implementation */ }
}
```

2. **Add Test Coverage**:
```kotlin
class PythonInterpreterTest : InterpreterTestBase() {
    override fun newInterpreter(map: Map<String, Any>) = PythonInterpreter(map)
}
```

3. **Register with Framework**:
```kotlin
// In your application configuration
val interpreterRegistry = mapOf(
    "python" to PythonInterpreter::class,
    "kotlin" to KotlinInterpreter::class,
    // ...
)
```

### Custom Execution Wrappers

```kotlin
class MonitoredKotlinInterpreter(defs: Map<String, Any>) : KotlinInterpreter(defs) {
    override fun <T : Any> wrapExecution(fn: Supplier<T?>): T? {
        val startTime = System.currentTimeMillis()
        return try {
            super.wrapExecution(fn)
        } finally {
            val duration = System.currentTimeMillis() - startTime
            log.info("Execution took ${duration}ms")
        }
    }
}
```

## Configuration Examples

### Basic Kotlin Setup

```kotlin
val interpreter = KotlinInterpreter(mapOf(
    "logger" to LoggerFactory.getLogger("script"),
    "config" to applicationConfig,
    "utils" to UtilityFunctions()
))
```

### Process-Based Python

```kotlin
val interpreter = ProcessInterpreter(mapOf(
    "command" to "python3",
    "language" to "python",
    "workingDir" to projectRoot,
    "env" to mapOf(
        "PYTHONPATH" to "$projectRoot/src",
        "VIRTUAL_ENV" to "$projectRoot/venv"
    )
))
```

### Multi-Language Support

```kotlin
class MultiLanguageInterpreter(
    private val interpreters: Map<String, Interpreter>
) {
    fun execute(language: String, code: String): Any? {
        return interpreters[language]?.run(code)
            ?: throw IllegalArgumentException("Unsupported language: $language")
    }
}
```
