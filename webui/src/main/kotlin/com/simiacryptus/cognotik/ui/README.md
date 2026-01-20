# UI Components and Utilities

This package provides the core interactive UI components and utilities for the Cognotik web interface. These classes facilitate complex interactions between the AI agent and the user, including real-time diff application, multi-turn discussions, and dynamic tabbed layouts.

## Core Components

### Diff Application
*   **AddApplyDiffLinks**: Scans markdown responses for diff blocks and injects interactive "Apply Diff" links. It supports:
    *   Automatic application of valid patches.
    *   Reverse patching (bottom-to-top) for complex diffs.
    *   Validation and verification tabs to preview changes.
*   **AddApplyFileDiffLinks**: Extends diff application to the file system. It handles:
    *   Resolving markdown headers to physical file paths.
    *   Creating new files from code blocks.
    *   Applying patches to existing files with logging and revert capabilities.
    *   Instrumentation of AI responses to add "Save" and "Apply" buttons.

### Interactive Interaction Patterns
*   **Discussable**: A high-level component for multi-turn refinement loops. It allows the user to:
    *   View an initial AI response.
    *   Provide feedback for revision.
    *   "Accept" a design or "Retry" the generation.
    *   Navigate through the history of the discussion via tabs.
*   **Retryable**: A specialized display for asynchronous tasks that provides a "Retry" (♻) button. Each retry attempt is rendered in a new tab, allowing users to compare different outputs.

### Layout and Display Utilities
*   **TabbedDisplay**: The foundational class for all multi-tab UI elements. It manages:
    *   Dynamic addition and deletion of tabs.
    *   Real-time UI updates via `SocketManager`.
    *   Rendering of tab buttons and content containers with unique IDs.
*   **AgentPatterns**: Contains utility methods like `displayMapInTabs`, which can render a map of strings into a tabbed interface, optionally splitting large content into separate background tasks for better performance.

## Implementation Details

### UI Synchronization
Most components in this package interact with `SocketManager` and `SessionTask`. They use placeholders and real-time updates to ensure the web interface reflects the current state of background processing without requiring manual page refreshes.

### File Operation Logging
`AddApplyFileDiffLinks` includes a robust logging mechanism for file operations. When enabled, it records:
*   Operation type (NEW_FILE, AUTO_PATCH, MANUAL_PATCH).
*   Original and new code content.
*   Execution duration and stack traces.
*   Validation results.

### Filename Normalization
The `AddApplyFileDiffLinks` class includes sophisticated logic to extract and normalize filenames from various markdown header patterns (e.g., "File: path/to/file.kt", "Modified: filename.py"), ensuring reliable file system mapping even with varied AI output formats.

## Usage
These components are typically used within AI agent loops to wrap generated content before it is sent to the user. For example, `AddApplyFileDiffLinks.instrumentFileDiffs` is called on the final markdown response to make any suggested code changes interactive.