# Chat Actions

This package contains IntelliJ actions that facilitate various types of AI-powered chat sessions. These actions integrate the IDE's context (files, selections, projects) with a web-based chat interface.

## Overview

The chat actions are designed to handle different use cases, ranging from simple text-based conversations to complex multi-file code modifications and document analysis.

### Core Chat Actions

*   **[Code Chat](CodeChatAction.kt)**: Opens a chat session focused on the current file or selected text in the editor.
*   **[Diff Chat](DiffChatAction.kt)**: An interactive chat that allows the AI to suggest code changes in a diff format, which can then be applied directly back to the editor.
*   **[Generic Chat](GenericChatAction.kt)**: A basic AI chat session without any initial code context.
*   **[Multi-Code Chat](MultiCodeChatAction.kt)**: Enables discussion across multiple selected files or directories, providing the AI with a broader project context.

### Advanced & Specialized Actions

*   **[Smart Chat](SmartChatAction.kt)**: An enhanced chat experience featuring:
    *   **History Summarization**: Automatically summarizes long conversations to stay within token limits.
    *   **Model Elevation**: Intelligently switches between fast and smart models based on query complexity.
*   **[Smart Code Chat](SmartCodeChatAction.kt)**: Combines the multi-file capabilities of `MultiCodeChatAction` with the advanced history management and model elevation of `SmartChatAction`.
*   **[Modify Files](ModifyFilesAction.kt)**: Specifically optimized for multi-file refactoring. It supports providing line numbers for better reference and handles complex patches across multiple files.
*   **[Image Chat](ImageChatAction.kt)**: A versatile action that supports not only code but also images and various document formats (PDF, DOCX, XLSX, etc.), allowing for visual and document-centric AI assistance.

## Implementation Details

All actions in this package extend `BaseAction` and typically follow these steps:
1.  **Context Gathering**: Extracting file paths, editor selections, or project structures.
2.  **Session Initialization**: Creating a unique session ID and configuring a `SocketManager` (e.g., `CodeChatSocketManager`, `ChatSocketManager`).
3.  **Server Setup**: Registering the session with the internal `ApplicationServer` and `SessionProxyServer`.
4.  **Browser Launch**: Opening the user's default web browser to the generated session URL.

## Key Components

*   **Socket Managers**: Handle the communication between the IDE and the web UI, managing the AI prompt construction and response rendering.
*   **Patch Application**: Actions like `DiffChatAction` and `ModifyFilesAction` use specialized rendering to turn AI-generated diffs into clickable "Apply" links within the chat UI.