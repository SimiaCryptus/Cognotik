# Editor Actions

This package contains AI-powered IntelliJ actions designed to enhance the code editing experience through intelligent transformations and content insertion.

## Core Actions

### Custom Edit Action (`CustomEditAction`)
The `CustomEditAction` provides a flexible way to modify code using natural language instructions.
- **Usage**: Requires an active code selection. When triggered, it prompts the user for an edit instruction (e.g., "Add documentation," "Refactor to use streams," or "Add error handling").
- **AI Integration**: Uses a `ProxyAgent` to communicate with a `VirtualAPI`. It passes the selected code, the user's instruction, and the detected programming language to the AI model.
- **Features**: 
    - Maintains a history of recent edit commands.
    - Automatically detects the computer language from the editor context.
    - Provides visual progress feedback during the AI processing phase.

### Smart Paste Actions (`PasteActionBase`, `FastPasteAction`)
These actions facilitate "intelligent pasting," allowing users to paste content from the clipboard (including HTML from web pages) and have it automatically converted into the target programming language.
- **Functionality**: 
    - **HTML Scrubbing**: Includes a sophisticated `scrubHtml` utility that strips unnecessary tags (scripts, styles, metadata), comments, and attributes from clipboard HTML to minimize token usage and focus on relevant content.
    - **Format Conversion**: Uses AI to translate the scrubbed clipboard content into the language of the current file.
- **Implementations**:
    - `FastPasteAction`: Optimized for speed, utilizing a "fast" chat model for quick conversions.
- **Clipboard Support**: Handles multiple data flavors including HTML fragments, full HTML documents, and plain text.

## Technical Implementation Details

### ProxyAgent and VirtualAPI
Both actions leverage the `ProxyAgent` pattern. This allows the code to define a clean Kotlin interface (`VirtualAPI`) for the desired AI operations, which the `ProxyAgent` then implements dynamically by generating prompts and parsing responses from the configured LLM.

### Language Detection
The actions automatically determine the target language using the `SelectionState`, which inspects the active editor's virtual file extension or internal language settings.

### Error Handling
Actions are designed to be resilient; if an AI transformation fails, they typically fall back to the original text and display a descriptive error dialog to the user via `UITools`.