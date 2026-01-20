# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/dev/ApplyPatchAction.kt

```
package cognotik.actions.dev

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.*
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.util.getSelectedFiles
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

/**
 * Action that allows applying a patch to selected files in the IDE.
 */
class ApplyPatchAction : BaseAction(
    name = "Apply Patch",
    description = "Applies a patch to the current file"
) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    private val log = Logger.getInstance(ApplyPatchAction::class.java)

    override fun handle(event: AnActionEvent) {
        val project = event.project ?: return
        val virtualFiles = event.getSelectedFiles() ?: return

        val patchContent = showPatchInputDialog() ?: return
        if (patchContent.trim().isEmpty()) {
            Messages.showErrorDialog(project, "Patch content cannot be empty", "Invalid Patch")
            return
        }

        virtualFiles.forEach { virtualFile ->
            try {
                applyPatch(virtualFile, patchContent, project, AppSettingsState.instance.processor)
            } catch (e: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to apply patch to ${virtualFile.name}: ${e.message}",
                    "Patch Application Error"
                )
            }
        }
    }

    private fun showPatchInputDialog(): String? {
        var patchContent: String? = null
        lateinit var dialogPanel: DialogPanel
        lateinit var patchContentTextArea: Cell<JBTextArea>
        dialogPanel = panel {
            row {
                textArea()
                    .label("Patch Content")
                    .bindText({ patchContent ?: "" }, { patchContent = it })
                    .rows(10)
                    .columns(50)
                    .also { textArea -> patchContentTextArea = textArea }
            }
            row {
                button("Paste") {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    val clipboardContent = clipboard.getData(DataFlavor.stringFlavor) as? String
                    log.info("Pasting clipboard content: $clipboardContent")
                    patchContent = clipboardContent ?: patchContent
                    patchContentTextArea.text(patchContent ?: "")
                    dialogPanel.revalidate()
                    dialogPanel.repaint()
                    dialogPanel.apply()
                }
            }
        }
        val dialogWrapper = object : com.intellij.openapi.ui.DialogWrapper(true) {
            init {
                init()
                title = "Enter Patch Content"
            }

            override fun createCenterPanel(): DialogPanel {
                return dialogPanel
            }
        }
        dialogWrapper.showAndGet()
        return patchContent
    }

    /**
     * Applies the given patch content to a file.
     *
     * @param file The virtual file to patch
     * @param patchContent The content of the patch to apply
     * @param project The current project
     */

    private fun applyPatch(
        file: VirtualFile, patchContent: String, project: Project, processor: PatchProcessor
    ) {
        WriteCommandAction.runWriteCommandAction(project) {
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return@runWriteCommandAction
            val newContent = processor.applyPatch(psiFile.text, patchContent)
            if (newContent == psiFile.text) {
                Messages.showWarningDialog(project, "Patch made no changes to ${file.name}", "No Changes")
                return@runWriteCommandAction
            }
            psiFile.virtualFile.setBinaryContent(newContent.toByteArray())
        }
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val selectedFiles = event.getSelectedFiles()
        when {
            false -> return false
            selectedFiles.isEmpty() -> return false
            selectedFiles.size > 1 -> return false
            selectedFiles.first().isDirectory -> return false
            else -> return true
        }
    }

}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the new README file for the `cognotik.actions.dev` package, specifically documenting the `ApplyPatchAction`.

### intellij/src/main/kotlin/cognotik/actions/dev/README.md
```markdown
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
```

### Summary of Changes

- **Created `intellij/src/main/kotlin/cognotik/actions/dev/README.md`**: This file documents the `ApplyPatchAction` class found in the same directory.
- **Content**: Included an overview of the action, its key features (clipboard integration, validation), usage instructions, and technical implementation details (UI DSL usage, threading, and dependencies).
- **Alignment**: Followed the transformation rules specified in `index_docs.md` to place the documentation in the correct directory relative to the source code.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/dev/README.md'>intellij/src/main/kotlin/cognotik/actions/dev/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/dev/README.md'>intellij/src/main/kotlin/cognotik/actions/dev/README.md</a> Updated
