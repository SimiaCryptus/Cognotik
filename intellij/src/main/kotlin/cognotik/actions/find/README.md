# Find Results Actions

This package contains IntelliJ actions designed to work with the results of "Find" operations (usages). These actions leverage AI to help developers understand or modify code across multiple search results.

## Actions

### Chat About Find Results (`FindResultsChatAction`)
This action allows you to start an AI-powered chat session focused specifically on the code locations identified in a "Find" results view.

- **Context Awareness**: It automatically gathers code context from all selected usages, including line numbers and relevant code snippets.
- **Filtered Context**: To keep the AI prompt efficient, it filters file content to show lines containing usages and their immediate surrounding context, using `...` to represent skipped sections.
- **Web Interface**: Launches a browser-based chat interface where you can ask questions about the search results.

### Modify Find Results (`FindResultsModificationAction`)
This action enables bulk code modification across multiple find results using natural language instructions.

- **Instruction Dialog**: Prompts the user for modification instructions (e.g., "Refactor this method to use the new API") and an option to auto-apply changes.
- **AI-Driven Patching**: Uses the AI to generate patches for each file containing search results.
- **Review and Apply**: Provides a tabbed interface in the browser to review suggested changes. It supports instrumented file diffs that can be applied directly back to the IDE.
- **Contextual Filtering**: Similar to the chat action, it provides the AI with a focused view of the code surrounding the usage points to ensure accurate modifications.

## Supporting Components

### Find Results Modification Dialog (`FindResultsModificationDialog`)
A standard IntelliJ dialog used by the modification action to:
- Collect natural language instructions for the AI.
- Toggle the "Auto-apply changes" setting.
- Validate that instructions are provided before proceeding.

## Implementation Details

- **Usage Integration**: Both actions integrate with the IntelliJ `UsageView`, requiring active search results to be selected.
- **Session Management**: They use `SessionProxyServer` and `ApplicationServer` to host local web-based UIs for the AI interaction.
- **AI Integration**: They utilize `ChatAgent` and `SmartChatClient` to communicate with the configured LLM, passing specialized prompts that include code context and formatting instructions (like patch formats).