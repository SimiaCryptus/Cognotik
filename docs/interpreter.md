# Cognotik Interpreter Subsystem Developer Guide

## Overview

The Cognotik Interpreter Subsystem provides a unified interface for executing code in multiple programming languages
within the Cognotik framework. It enables dynamic code execution, validation, and integration with AI-powered coding
assistants.

## Architecture

### Core Components

```
Cognotik/
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

The `OutputInterceptor` captures stdout/stderr output from code execution, providing both thread-local and global output
streams.

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

```

### Usage in Planning Tasks

```kotlin

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

```

## Testing

### Base Test Class

The framework provides `InterpreterTestBase` for consistent testing across implementations:

```kotlin

```

### Implementation-Specific Tests

```kotlin

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

```

2. **Add Test Coverage**:

```kotlin

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

```
