# Analyze Problem Action

The `AnalyzeProblemAction` is an IntelliJ IDEA integration that allows users to analyze code issues directly from the "Problems" tool window using AI-powered agents. It automates the process of understanding an error, gathering relevant source context, and generating actionable code fixes.

## Features

- **Context-Aware Analysis**: Automatically extracts the specific line of code, surrounding context lines, file type, and project structure related to a reported problem.
- **AI-Driven Diagnostics**: Uses a multi-stage agent approach:
    - **Planning**: Identifies distinct errors and predicts which files need to be modified or referenced.
    - **Fix Generation**: Generates specific code patches in `diff` format to resolve the identified issues.
- **Interactive Web UI**: Launches a local web session where users can review the analysis and apply suggested fixes with a single click.
- **Git Integration**: Automatically locates the Git root to provide accurate relative paths and project-wide context.

## How It Works

1. **Trigger**: The user right-clicks a problem in the IntelliJ Problems view and selects the analysis action.
2. **Data Collection**: The action gathers:
    - The error message and location.
    - Snippets of the source code around the error.
    - The overall project structure.
3. **Session Initialization**: A `ProblemAnalysisApp` is started on the internal application server, and the user's default browser is opened to the session URL.
4. **Agent Execution**:
    - A `ParsedAgent` categorizes the problem and identifies relevant files.
    - A `ChatAgent` processes the gathered files and the error description to produce `diff` patches.
5. **Application**: The UI provides "Apply" links that use the `AddApplyFileDiffLinks` utility to write the suggested changes back to the local filesystem.

## Implementation Details

- **Class**: `AnalyzeProblemAction`
- **Inner Class**: `ProblemAnalysisApp` (Extends `ApplicationServer`)
- **Key Dependencies**:
    - `com.simiacryptus.cognotik.agents.ParsedAgent`: For structured error decomposition.
    - `com.simiacryptus.cognotik.agents.ChatAgent`: For generating the final fix suggestions.
    - `com.intellij.analysis.problemsView.toolWindow.ProblemNode`: To interface with the IDE's problem tracking.

## Usage

This action is typically available in the context menu of the **Problems** tool window in IntelliJ IDEA. It requires the Cognotik plugin to be configured with valid AI model endpoints (e.g., OpenAI, Anthropic) via the `AppSettingsState`.