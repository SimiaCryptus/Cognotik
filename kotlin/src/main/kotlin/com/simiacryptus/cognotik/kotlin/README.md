# KotlinCodeRuntime

`KotlinCodeRuntime` is an implementation of the `CodeRuntime` interface that provides a robust environment for executing Kotlin code snippets dynamically using the Kotlin JSR-223 scripting engine.

## Overview

This class allows for the integration of Kotlin scripting within a JVM application. it handles the complexities of script engine initialization, classpath management, and error reporting, making it easy to run arbitrary Kotlin code at runtime.

## Key Features

- **JSR-223 Integration**: Utilizes `KotlinJsr223ScriptEngineImpl` for compiling and evaluating Kotlin scripts.
- **Symbol Injection**: Accepts a map of definitions (`defs`) that are automatically bound to the script's engine scope, allowing scripts to access and manipulate host-provided objects.
- **Classpath Configuration**: Automatically detects and configures the script compilation classpath from the provided `ClassLoader`, ensuring that scripts have access to the same libraries as the host application.
- **Advanced Error Reporting**: Intercepts compilation and runtime exceptions to provide detailed diagnostic messages, including line numbers, column positions, and visual pointers to the error in the source code.
- **Code Pre-processing**: Implements `wrapCode` to organize script content, such as ensuring import statements are correctly positioned.

## Implementation Details

### Script Engine Lifecycle
The `scriptEngine` property is lazily initialized using a `KotlinJsr223JvmScriptEngineFactoryBase`. It configures the compilation environment to share script instances and includes the full classpath from the context classloader.

### Error Handling
The `wrapException` method is designed to extract precise location information from `ScriptException`. If the engine does not provide line/column numbers directly, it attempts to parse them from the exception message using regex.

### Code Execution
- **`validate(code: String)`**: Compiles the code without executing it to check for syntax errors.
- **`run(code: String)`**: Compiles and executes the code, returning the result of the last expression.

## Usage Example

```kotlin
val symbols = mapOf("context" to myAppContext)
val runtime = KotlinCodeRuntime(symbols)

val code = """
    import java.util.*
    println("Current time: " + Date())
    context.performAction()
    "Success"
""".trimIndent()

val result = runtime.run(code)
println(result) // Prints "Success"
```

## Configuration
The companion object allows for global configuration of the `classLoader` used for script compilation:
```kotlin
KotlinCodeRuntime.classLoader = myCustomClassLoader
```