# Test Result Autofix Action

The `TestResultAutofixAction` is an IntelliJ IDEA integration designed to streamline the debugging process by automatically analyzing test failures and suggesting code fixes using AI. It bridges the gap between test execution results and source code modifications.

## Overview

When a test fails in the IntelliJ environment, this action can be triggered to:
1.  **Extract Context**: Capture test names, error messages, and stack traces from the test runner.
2.  **Analyze Project Structure**: Map out the project files to provide the AI with necessary context about the codebase.
3.  **Identify Errors**: Use a structured AI agent to parse the test failure into distinct, actionable errors.
4.  **Suggest Fixes**: Generate precise code patches in diff format for the identified issues.
5.  **Apply Changes**: Provide an interactive web interface to review and apply suggested fixes directly to the local filesystem.

## Key Features

- **Automated Context Gathering**: Automatically finds the Git root and scans relevant project files (respecting `.gitignore` and size limits).
- **Multi-Error Analysis**: Capable of identifying multiple distinct issues within a single test failure and generating separate fix strategies for each.
- **Interactive Web UI**: Launches a local `TestResultAutofixApp` server that opens in the user's browser, providing a rich environment for AI interaction.
- **Diff-Based Patching**: Generates standard unified diffs that can be previewed and applied with a single click.
- **Smart File Selection**: Uses AI to predict which files need fixing and which files are required for debugging context.

## Implementation Details

### Core Components

- **`TestResultAutofixAction`**: The main IntelliJ action class that handles the UI event, extracts `SMTestProxy` data, and initializes the background analysis.
- **`TestResultAutofixApp`**: An internal `ApplicationServer` implementation that manages the web-based chat session and coordinates the AI agents.
- **AI Agents**:
    - **`ParsedAgent`**: Used for structured analysis of the test output to identify specific `ParsedError` objects.
    - **`ChatAgent`**: Used to generate the actual code modifications based on the identified errors and relevant file content.

### Data Structures

- **`ParsedError`**: Contains the error message, a list of predicted files to fix, and a list of related files for context.
- **`ParsedErrors`**: A wrapper for a collection of identified errors.

## Usage

1.  Run tests in IntelliJ IDEA.
2.  Right-click on a failed test in the Test Runner tab.
3.  Select the **Test Result Autofix** action.
4.  A browser window will open showing the analysis progress.
5.  Review the suggested diffs and click the provided links to apply changes to your source code.

## Dependencies

- **IntelliJ Platform SDK**: For IDE integration and test framework access.
- **Cognotik Core**: For AI agent management (`ChatAgent`, `ParsedAgent`) and web UI infrastructure (`ApplicationServer`).
- **Markdown Rendering**: Uses internal utilities to render AI responses and diffs.