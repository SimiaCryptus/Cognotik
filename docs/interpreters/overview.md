# Cognotik Language Interpreters Documentation

## Overview

The Cognotik platform supports multiple programming language interpreters through a unified interface. Currently supported languages include:
- Kotlin
- Groovy
- Scala

Each interpreter implements the common `Interpreter` interface while providing language-specific features and optimizations.

## Architecture

### Common Interface

All interpreters implement the `Interpreter` interface:

```kotlin
interface Interpreter {
    fun getLanguage(): String
    fun getSymbols(): Map<String, Any>
    fun run(code: String): Any?
    fun validate(code: String): Exception?
    fun wrapCode(code: String): String
}
```

### Core Components

1. **Language Engine**: Each interpreter maintains its own scripting engine
2. **Symbol Management**: Predefined variables and functions
3. **Code Execution**: Safe execution environment
4. **Validation**: Code verification before execution
5. **Error Handling**: Standardized error reporting

## Language-Specific Implementations

### 1. Kotlin Interpreter

The Kotlin interpreter uses the JSR-223 scripting API with custom configuration.

#### Key Features
- Full Kotlin language support
- Classpath management
- Coroutine support
- Detailed error reporting

```kotlin
class KotlinInterpreter(val defs: Map<String, Any>) : Interpreter {
    // Custom script engine configuration
    val scriptEngine: KotlinJsr223JvmScriptEngineBase = // ...
    
    // Error handling with source mapping
    protected fun wrapException(
        cause: ScriptException,
        wrappedCode: String,
        code: String
    ): CodingActor.FailedToImplementException
}
```

#### Usage Example
```kotlin
val interpreter = KotlinInterpreter(mapOf(
    "helper" to HelperClass(),
    "config" to Configuration()
))

// Run code
interpreter.run("""
    val result = helper.process(config)
    println(result)
""")
```

### 2. Groovy Interpreter

The Groovy interpreter provides dynamic scripting capabilities using GroovyShell.

#### Key Features
- Dynamic typing support
- Simplified syntax
- Java interoperability
- Built-in DSL support

```kotlin
class GroovyInterpreter(private val defs: Map<String, Object>) : Interpreter {
    private val shell: GroovyShell
    
    // Initialize with compiler configuration
    init {
        val compilerConfiguration = CompilerConfiguration()
        shell = GroovyShell(compilerConfiguration)
        defs.forEach { key, value ->
            shell.setVariable(key, value)
        }
    }
}
```

#### Usage Example
```kotlin
val interpreter = GroovyInterpreter(mapOf(
    "service" to ServiceClass(),
    "data" to DataObject()
))

// Execute Groovy code
interpreter.run("""
    def result = service.transform(data)
    return result.process()
""")
```

### 3. Scala Interpreter

The Scala interpreter uses the Scala reflection API for advanced type management.

#### Key Features
- Strong type system
- Advanced pattern matching
- Type inference
- Functional programming support

```scala
class ScalaLocalInterpreter(javaDefs: java.util.Map[String, Object]) extends Interpreter {
    // Type management
    val typeTags: Map[String, Type] = // ...
    
    // Custom REPL implementation
    class CustomReplReporter(settings: Settings) extends ReplReporterImpl(settings) {
        val errors = new StringBuilder
        // Error handling implementation
    }
}
```

#### Usage Example
```scala
val interpreter = new ScalaLocalInterpreter(Map(
    "processor" -> DataProcessor,
    "context" -> ExecutionContext
).asJava)

// Run Scala code
interpreter.run("""
    case class Result(value: String)
    processor.map(context) { data =>
        Result(data.toString)
    }
""")
```

## Integration Points

### 1. Adding New Interpreters

To add a new language interpreter:

1. Implement the `Interpreter` interface
2. Handle symbol management
3. Implement code execution
4. Add error handling
5. Register with the platform

```kotlin
class NewLanguageInterpreter(defs: Map<String, Any>) : Interpreter {
    override fun getLanguage() = "NewLanguage"
    override fun getSymbols() = defs
    
    override fun run(code: String): Any? {
        // Implementation
    }
    
    override fun validate(code: String): Exception? {
        // Validation logic
    }
}
```

### 2. Symbol Integration

Symbols can be passed to interpreters for use in scripts:

```kotlin
val symbols = mapOf(
    "logger" to LoggerFactory.getLogger("Script"),
    "api" to ApiClient(),
    "database" to DatabaseConnection()
)

val interpreter = KotlinInterpreter(symbols)
```

### 3. Error Handling

Standardized error handling across interpreters:

```kotlin
try {
    interpreter.run(userCode)
} catch (e: CodingActor.FailedToImplementException) {
    // Handle implementation errors
} catch (e: ScriptException) {
    // Handle script errors
} catch (e: Exception) {
    // Handle general errors
}
```

## Best Practices

1. **Code Validation**
   - Always validate code before execution
   - Implement proper security checks
   - Handle compilation errors gracefully

2. **Resource Management**
   - Clean up resources after execution
   - Manage memory usage
   - Handle long-running scripts

3. **Error Handling**
   - Provide detailed error messages
   - Include source code context
   - Map errors to original source

4. **Security**
   - Sandbox execution environment
   - Limit available APIs
   - Validate input code

## Configuration

### Kotlin Interpreter
```kotlin
KotlinInterpreter(
    defs = mapOf(...),
    classLoader = CustomClassLoader()
)
```

### Groovy Interpreter
```kotlin
GroovyInterpreter(
    defs = mapOf(...),
    // Additional compiler configuration
)
```

### Scala Interpreter
```scala
ScalaLocalInterpreter(
    javaDefs = javaMap,
    // Custom settings configuration
)
```

## Limitations

1. **Kotlin Interpreter**
   - Limited reflection capabilities
   - Classpath management complexity
   - Compilation overhead

2. **Groovy Interpreter**
   - Performance overhead for dynamic features
   - Type safety trade-offs
   - Memory usage for dynamic typing

3. **Scala Interpreter**
   - Startup time
   - Memory footprint
   - Complex type system integration

## Future Improvements

1. **Performance Optimization**
   - Caching compiled code
   - Optimizing startup time
   - Reducing memory usage

2. **Feature Additions**
   - Additional language support
   - Enhanced debugging capabilities
   - Improved error reporting

3. **Integration Enhancements**
   - Better IDE support
   - Enhanced type inference
   - Improved symbol management