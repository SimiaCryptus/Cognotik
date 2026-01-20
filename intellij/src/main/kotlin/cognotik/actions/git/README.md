# Git Actions

This package contains a suite of IntelliJ actions designed to integrate Git version control workflows with AI-assisted analysis and code generation. These tools allow developers to discuss changes, analyze diffs, and replicate logic from previous commits using large language models.

## Actions

### Chat with Commit
Compares selected files or revisions with the current working copy. It generates a detailed patch of the differences and opens a web-based chat interface. This allows for focused discussions on specific changes within a commit context.

### Chat with Commit Diff
Facilitates a discussion about the differences between a selected commit and the current `HEAD`. It retrieves the changes, generates a simplified diff, and initializes a chat session to help understand the evolution of the codebase between those points.

### Chat with Working Copy Diff
Provides an AI-powered overview of all uncommitted changes in the current working copy. It aggregates differences across all modified files relative to `HEAD`, enabling developers to review their work-in-progress or generate documentation for pending commits.

### Replicate Commit
An advanced action that automates the process of porting logic from a previous commit into the current project state. 
- **Analysis**: It examines the diff of a reference commit.
- **Planning**: Uses a `ParsedAgent` to identify which files in the current project need modification based on the reference diff and user instructions.
- **Execution**: Employs a `ChatAgent` to generate specific code changes and provides an interactive interface to apply these diffs to the local workspace.

## Implementation Details

- **Diff Generation**: Actions use either the internal `AppSettingsState.instance.processor` for high-quality patches or a simplified line-by-line diffing logic for quick comparisons.
- **Session Management**: Each action creates a unique global session ID and registers a `CodeChatSocketManager` or a custom `ApplicationServer` (like `PatchApp`) to handle the web-based UI.
- **AI Integration**: Uses `smartChatClient` for complex reasoning and `fastChatClient` for parsing and structured data extraction.
- **UI**: Interfaces are served via `CognotikAppServer` and automatically opened in the system's default browser.

## Dependencies

- `com.intellij.openapi.vcs`: For accessing version control data and change lists.
- `com.simiacryptus.cognotik.agents`: For AI planning and chat capabilities.
- `com.simiacryptus.cognotik.webui`: For the interactive web interface.