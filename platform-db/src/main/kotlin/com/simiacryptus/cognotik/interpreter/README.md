# Interpreter Module

The `com.simiacryptus.cognotik.interpreter` package provides a robust framework for executing code across a wide variety
of programming languages. It abstracts process management, environment configuration, and tool resolution into a unified
interface.

## Core Components

### [CodeRuntimes.kt](./CodeRuntimes.kt)

`CodeRuntimes` serves as a dynamic registry and factory for all supported execution environments. It utilizes a
`DynamicEnum` pattern, allowing the system to handle a growing list of languages with consistent metadata (names,
descriptions, and file extensions).

- **Factory Mechanism**: The `getRuntime(runtimeType, params)` method instantiates the appropriate `CodeRuntime` based
  on the requested type and configuration parameters.
- **Tool Resolution**: It integrates with `ApplicationServices` to dynamically resolve executable paths (e.g., finding
  `python3` vs `python`, or locating `node`) based on user settings and system environment.
- **Extensibility**: Supports runtime registration of new language constructors via `registerConstructor`.

### [ProcessCodeRuntime.kt](./ProcessCodeRuntime.kt)

A versatile implementation of the `CodeRuntime` interface designed for languages that execute via external system
processes.

- **Execution Lifecycle**:
    1. Spawns a system process using a configured command (e.g., `bash`, `python`, `node`).
    2. Streams the source code directly into the process's standard input (stdin).
    3. Captures standard output (stdout) and standard error (stderr).
    4. Enforces execution timeouts to prevent resource exhaustion.
- **Error Reporting**: If the process writes to the error stream, the runtime returns a formatted string containing both
  the error details and the captured output to assist in debugging.

## Supported Runtimes

The following runtimes are pre-configured and ready for use:

| Runtime               | Language   | Default Tool/Command                    |
|:----------------------|:-----------|:----------------------------------------|
| **KotlinRuntime**     | Kotlin     | Internal JVM Execution                  |
| **GroovyRuntime**     | Groovy     | Internal JVM Execution                  |
| **PythonRuntime**     | Python     | `python` or `python3`                   |
| **NodeJsRuntime**     | JavaScript | `node`                                  |
| **BashRuntime**       | Bash       | `bash`                                  |
| **PowerShellRuntime** | PowerShell | `powershell` (Windows) or `pwsh` (Unix) |
| **GoRuntime**         | Go         | `go run`                                |
| **RustRuntime**       | Rust       | `rust-script`                           |
| **CmdRuntime**        | Batch      | `cmd /c`                                |
| **RubyRuntime**       | Ruby       | `ruby`                                  |
| **PerlRuntime**       | Perl       | `perl`                                  |
| **RRuntime**          | R          | `Rscript`                               |
| **PhpRuntime**        | PHP        | `php`                                   |
| **LuaRuntime**        | Lua        | `lua`                                   |
| **ScalaRuntime**      | Scala      | `scala`                                 |

## Configuration Parameters

When instantiating a runtime via `CodeRuntimes.getRuntime`, the following parameters can be provided in the
configuration map:

- `timeoutMinutes`: (Long) Maximum time allowed for execution before the process is terminated (default: 15).
- `workingDir`: (String/File) The directory in which the script will execute.
- `env`: (Map<String, String>) Environment variables to be passed to the execution context.

## Implementation Details

- **Cross-Platform Support**: The module includes logic to handle OS-specific differences, such as command names for
  PowerShell and Python, and path resolution for Windows vs. Unix-like systems.
- **Input Handling**: Code is trimmed and wrapped as necessary before being passed to the underlying process.
- **Resource Management**: Input and output streams are explicitly closed, and processes are destroyed if they exceed
  the specified timeout.