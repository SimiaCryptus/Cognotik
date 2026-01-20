# Agent Actions

This package contains advanced AI-driven actions that function as autonomous or semi-autonomous agents. These tools are designed to handle complex, multi-step tasks such as automated debugging, large-scale refactoring, and documentation-driven code generation.

## Core Actions

### [Command Autofix](CommandAutofixAction.kt)
Provides automated fixing of command execution issues through AI assistance.
- **Functionality**: Executes shell commands or scripts and monitors their output. If a command fails (or based on exit code configuration), the AI analyzes the error and suggests/applies fixes.
- **Configuration**: Supports multiple commands, custom working directories, argument history, and persistent configurations.

### [Custom File Set Patch](CustomFileSetPatchAction.kt)
A versatile tool for applying AI transformations to specific sets of files defined by patterns.
- **Selection**: Uses glob or regex patterns to include/exclude files. Supports designating files as "Context" (read-only for the AI) or "Target" (to be modified).
- **Output Modes**:
    - **Edit Files**: Direct code modification with optional auto-apply.
    - **Documentation**: Generates single or multi-file documentation based on code analysis.
    - **Data Extraction**: Aggregates structured data from the codebase.
- **Scalability**: Includes a "Big Data Mode" for processing large volumes of files with batching and concurrency controls.

### [Documented Mass Patch](DocumentedMassPatchAction.kt)
Synchronizes codebases with external documentation or standards.
- **Workflow**: Users select documentation files (e.g., Markdown specifications) and target source files. The AI ensures the code adheres to the requirements described in the documentation.
- **Interface**: Supports an interactive discussion mode to refine changes before application.

### [Multi-Step Patch (Auto Dev Assistant)](MultiStepPatchAction.kt)
An advanced agent that decomposes high-level user directives into executable action plans.
- **Design Phase**: Uses a specialized agent to translate a request into a `TaskList` of discrete sub-tasks.
- **Execution Phase**: Iterates through the task list, identifying relevant files for each step and applying patches sequentially.

## Architecture

The actions in this package typically utilize a decoupled architecture to handle long-running AI tasks:

1.  **Action UI**: IntelliJ-native dialogs for initial configuration and file selection.
2.  **Application Server**: Backend logic (e.g., `CustomFileSetPatchServer`) that manages the AI session, state, and concurrency.
3.  **Web UI**: Interactive interfaces served via the internal Cognotik server, allowing for rich markdown rendering, diff previews, and real-time progress tracking.

## Supporting Files
- **[WebDevelopmentAssistantAction.kt](WebDevelopmentAssistantAction.kt)**: Contains internal utility extensions for file system interoperability.