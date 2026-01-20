# Utility Package: com.simiacryptus.cognotik.util

This package contains a collection of utility classes and extension functions designed to simplify IntelliJ plugin development, handle background tasks, manage UI components, and provide language-specific metadata.

## Core Components

### Task Management
*   **`BgTask<T>`**: A wrapper for `Task.Backgroundable` that implements `Supplier<T>`. It provides a thread-safe way to execute background operations with progress indicators, supporting cancellation and result retrieval with timeout handling.
*   **`ModalTask<T>`**: Similar to `BgTask`, but executes as a modal dialog, blocking the UI until completion. It includes robust error handling and thread interruption logic.

### UI & Interaction
*   **`UITools`**: A comprehensive utility object for UI operations:
    - **Threading**: Manages dedicated thread pools (`pool`, `scheduledPool`) for API calls and background tasks.
    - **Reflection-based UI Binding**: Functions like `readKotlinUIViaReflection` and `writeKotlinUIViaReflection` automatically sync data between configuration objects and Swing components.
    - **Dialogs**: Simplified methods for showing error/warning dialogs and complex configuration dialogs.
    - **Error Reporting**: A sophisticated error handler that generates detailed reports (including OS info and action history) and provides direct links to GitHub issue creation.
    - **Document Editing**: Utilities for safe string replacement and deletion in IntelliJ `Document` objects with built-in undo support.
*   **`BrowseUtil`**: Handles opening URIs in the system browser and broadcasts UDP notifications to local ports for session synchronization.
*   **`showDocument.kt`**: An extension function for `Project` that opens resources or temporary files in the editor, with specific support for setting the layout to "Preview" (useful for Markdown documentation).

### Language & PSI Support
*   **`ComputerLanguage`**: An extensive enum defining metadata for dozens of programming languages, including file extensions and comment syntax (line, block, and doc comments).
*   **`LanguageUtils`**: Provides helper methods to detect the current `ComputerLanguage` based on the active editor context.
*   **`IntelliJPsiValidator`**: Implements `GrammarValidator` to check code snippets for syntax errors using IntelliJ's internal PSI (Program Structure Interface) parsers.
*   **`PsiUtil`**: Contains logic to find the smallest PSI element (e.g., a method or class) that fully contains a specific text selection.

### Chat & Networking
*   **`CodeChatSocketManager`**: A specialized WebSocket manager for code-centric AI interactions. It automatically constructs system prompts and user context based on file names, programming languages, and code selections.

## Usage Examples

### Running a Background Task
```kotlin
UITools.runAsync(project, "Processing Code") { indicator ->
    // Perform long-running operation
    val result = someOperation(indicator)
    // Update UI or state
}
```

### Detecting Language in Action
```kotlin
val language = LanguageUtils.getComputerLanguage(event)
if (language == ComputerLanguage.Kotlin) {
    // Handle Kotlin-specific logic
}
```

### Validating Code Snippets
```kotlin
val validator = IntelliJPsiValidator(project, "kt", "example.kt")
val errors = validator.validateGrammar(codeString)
if (errors.isNotEmpty()) {
    errors.forEach { println("Error at ${it.line}: ${it.message}") }
}
```

## Implementation Details
- **Concurrency**: Uses Guava's `ListeningExecutorService` for advanced future handling.
- **Reflection**: Extensively uses Kotlin reflection to reduce boilerplate when building settings UI.
- **Error Handling**: Captures a rolling log of actions and errors to provide context in bug reports.