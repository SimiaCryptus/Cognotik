# Execution and Automation Tasks

This package provides a suite of specialized task implementations for the Cognotik orchestration framework. These tasks
focus on code execution, external tool integration, automated error correction, and recursive planning.

## Overview

The tasks in this package allow the agent to interact with the environment by running commands, executing scripts,
querying language servers, and managing complex sub-goals through recursive planning. Most tasks support both
interactive (user-approved) and automated (auto-fix) modes.

## Task Types

### [AutoFixTask](AutoFixTask.kt)

**Task Type:** `AutoFix`
Executes shell commands and automatically attempts to fix any errors that occur during execution. It uses a self-healing
loop to analyze command output and apply patches to the codebase until the command succeeds or a limit is reached.

### [LanguageServerTask](LanguageServerTask.kt)

**Task Type:** `LanguageServer`
Provides code intelligence capabilities by interacting with Language Servers (LSP). It supports operations such as:

- **Diagnostics:** Identifying syntax errors and warnings.
- **Definition:** Finding the declaration of a symbol.
- **References:** Locating all usages of a symbol.
- **Hover:** Retrieving documentation and type information.

### [RunCodeTask](RunCodeTask.kt)

**Task Type:** `RunCode`
Executes code snippets (typically Groovy or Kotlin) within the application's environment. This is useful for data
processing, complex calculations, or file system operations that are better expressed as scripts than shell commands.

### [SymbolsDbCodeTask](SymbolsDbCodeTask.kt)

**Task Type:** `SymbolsDbCodeTask`
An extension of `RunCodeTask` that provides the executing script with access to a `symbols_db` object. This object
allows the script to query a pre-computed symbol graph of the project, enabling sophisticated code analysis and
relationship mapping.

### [RunToolTask](RunToolTask.kt)

**Task Type:** `RunTool`
A wrapper for executing external CLI tools (compilers, linters, etc.) with specific arguments. It handles output capture
and provides a structured way to integrate existing development tools into the agent's workflow.

### [SingleFixTask](SingleFixTask.kt)

**Task Type:** `SingleFix`
Analyzes a provided log file (e.g., a build log or test failure) and attempts to fix the identified errors in the
codebase in a single pass, without re-running the commands that generated the log.

### [SubPlanTask](SubPlanTask.kt)

**Task Type:** `SubPlan`
Enables recursive planning by allowing a task to spawn its own sub-orchestrator. This is used to break down complex
objectives into smaller, manageable steps, potentially using different cognitive strategies for the sub-tasks.

## Base Classes and Utilities

- **[CodingTask](CodingTask.kt):** An abstract base class that provides the infrastructure for interactive code
  execution, including UI feedback loops, transcript generation, and error handling.
- **Transcript Generation:** Most tasks in this package automatically generate detailed Markdown transcripts of their
  execution, including inputs, outputs, and any generated patches.

## Configuration

Tasks are configured via `TaskExecutionConfig` data classes which specify parameters like goals, commands, working
directories, and dependencies. Global behavior, such as whether to require manual approval for side effects, is
controlled via the `OrchestrationConfig`.