# Development Actions

This package contains actions intended for development and advanced file manipulation within the IntelliJ IDE.

## Apply Patch Action

The `ApplyPatchAction` allows users to apply text-based patches directly to a selected file. This is particularly useful for applying LLM-generated code modifications or standard diffs without leaving the IDE.

### Features

- **Manual Patch Entry**: Provides a dialog with a text area to paste or type patch content.
- **Clipboard Integration**: Includes a "Paste" button within the dialog to quickly pull content from the system clipboard.
- **Validation**: 
    - Ensures the patch content is not empty.
    - Verifies that the patch actually results in changes to the target file.
    - Provides error feedback if the patch application fails.
- **Safe Execution**: Uses `WriteCommandAction` to ensure file modifications are performed within the proper IntelliJ command and write-lock context.

### Usage

1. Right-click on a single file in the Project view or within an open editor.
2. Select **Apply Patch** from the context menu.
3. In the resulting dialog, enter or paste the patch content.
4. Click **OK** to apply the changes.

### Technical Implementation

- **Base Class**: Inherits from `BaseAction`.
- **Patch Processing**: Delegates the actual patching logic to a `PatchProcessor` instance retrieved from `AppSettingsState`.
- **UI**: Built using the IntelliJ UI DSL (`panel`, `row`, `textArea`).
- **Constraints**: 
    - The action is only enabled when exactly one file is selected.
    - It is disabled if a directory is selected.
    - It runs on the Background Thread (`BGT`) for action updates to ensure UI responsiveness.

### Dependencies

- `com.simiacryptus.cognotik.diff.PatchProcessor`: Handles the logic of merging the patch with existing file content.
- `com.simiacryptus.cognotik.config.AppSettingsState`: Provides access to the configured patch processor.