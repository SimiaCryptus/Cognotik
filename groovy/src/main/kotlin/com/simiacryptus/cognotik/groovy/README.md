# Groovy Code Runtime

The `GroovyCodeRuntime` class is an implementation of the `CodeRuntime` interface designed to execute Groovy scripts within the Cognotik framework. It provides a sandboxed-style execution environment using the `GroovyShell`, allowing for dynamic code execution with pre-defined variable bindings and console output capture.

## Features

- **Language Support**: Specifically handles code identified as `groovy`.
- **Variable Binding**: Supports passing a map of symbols (objects) into the Groovy environment, making them accessible as variables within the script.
- **Console Capture**: Automatically captures output written to the `out` variable (e.g., via `println`) and appends it to the execution result.
- **Syntax Validation**: Includes a `validate` method to check for compilation errors without executing the script.
- **Integration**: Implements the standard `CodeRuntime` interface, ensuring compatibility with other components in the system.

## Usage

### Initialization

The runtime can be initialized with an optional map of definitions:

```kotlin
val runtime = GroovyCodeRuntime(mapOf("context" to myContextObject))
```

### Executing Code

The `run` method executes the provided string as a Groovy script. If the script produces console output, it is appended to the return value of the script.

```kotlin
val code = """
    println "Processing data..."
    return context.performAction()
"""
val result = runtime.run(code)
```

### Validation

To check if a snippet of Groovy code is syntactically correct:

```kotlin
val exception = runtime.validate("def x = 10; x * 2")
if (exception == null) {
    // Code is valid
}
```

## Implementation Details

- **GroovyShell**: Uses `org.codehaus.groovy.control.CompilerConfiguration` and `GroovyShell` for script parsing and execution.
- **Output Redirection**: A custom `java.io.Writer` is used to redirect `out` calls to an internal `StringBuilder`.
- **Error Handling**: 
    - Initialization failures (e.g., shell setup issues) are logged via SLF4J and thrown as `RuntimeException`.
    - Compilation errors during `run` or `validate` are caught and handled through the `wrapExecution` lifecycle.
- **State**: The `console` buffer accumulates output during the lifecycle of the runtime instance.

## Dependencies

- `groovy-all` (or `groovy` core)
- `slf4j-api`
- Cognotik Interpreter API (for `CodeRuntime` interface)