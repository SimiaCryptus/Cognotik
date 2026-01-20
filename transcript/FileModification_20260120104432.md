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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/find/FindResultsChatAction.kt

```
package cognotik.actions.find

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.usages.ReadWriteAccessUsageInfo2UsageAdapter
import com.intellij.usages.Usage
import com.intellij.usages.UsageView
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.text.SimpleDateFormat
import javax.swing.Icon

class FindResultsChatAction(
    name: String? = "Chat About Find Results",
    description: String? = "Start a code chat about find results",
    icon: Icon? = null
) : BaseAction(name, description, icon) {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        val project = event.project ?: return
        val usageView = event.getData(UsageView.USAGE_VIEW_KEY) ?: return
        val usages = usageView.usages.toTypedArray()

        if (usages.isEmpty()) {
            UITools.showWarning(project, "No find results selected for chat")
            return
        }

        try {
            val root = getModuleRootForFile(
                event.getSelectedFile()?.parent?.toFile
                    ?: throw RuntimeException("No file selected")
            )

            val session = Session.newGlobalID()
            SessionProxyServer.metadataStorage.setSessionName(
                null,
                session,
                "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
            )

            val fileListMap = usages.groupBy { getFile(it) }
            SessionProxyServer.chats[session] = ChatApp(
                root = root,
                project = project,
                usages = fileListMap
            )

            ApplicationServer.appInfoMap[session] = AppInfoData(
                applicationName = "Find Results Chat",
                inputCnt = 0,
                stickyInput = true,
                loadImages = false,
                showMenubar = false
            )

            UITools.runAsync(event.project, "Opening Browser", true) { progress ->
                Thread.sleep(500)
                try {
                    val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                        AppSettingsState.instance.listeningEndpoint,
                        AppSettingsState.instance.listeningPort
                    ).server.uri.resolve("/#$session")
                    log.info("Opening browser to $uri")
                    browse(uri)
                } catch (e: Throwable) {
                    val message = "Failed to open browser: ${e.message}"
                    log.error(message, e)
                    UITools.showErrorDialog(message, "Error")
                }
            }
        } catch (ex: Exception) {
            UITools.error(log, "Error starting chat", ex)
        }
    }

    private fun getFile(it: Usage): VirtualFile? {
        var file = it.location?.editor?.file
        if (file != null && file.isValid) return file
        if (it is ReadWriteAccessUsageInfo2UsageAdapter) file = it.file
        if (file != null && file.isValid) return file
        log.warn("Usage location does not have an editor, cannot determine file")
        return null
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val usageView = event.getData(UsageView.USAGE_VIEW_KEY)
        return usageView != null && usageView.usages.isNotEmpty()
    }

    inner class ChatApp(
        override val root: File,
        val project: Project,
        val usages: Map<VirtualFile?, List<Usage>>
    ) : ApplicationServer(
        applicationName = "Find Results Chat",
        path = "/findChat",
        showMenubar = false,
    ) {
        override val inputCnt = 0
        override val stickyInput = true
        private fun formatLine(index: Int, line: String, isFocused: Boolean) = when {
            isFocused -> "/* L$index */ $line /* <<< */"
            else -> "/* L$index */ $line"
        }

        private fun getFilteredLines(project: Project, file: VirtualFile, usages: List<Usage>): String? {
            val document = PsiDocumentManager.getInstance(project)
                .getDocument(file.findPsiFile(project) ?: return null) ?: return null
            return document.text.lines().mapIndexed { index: Int, line: String ->
                val lineStart = document.getLineStartOffset(index)
                val lineEnd = document.getLineEndOffset(index)
                val intersectingUsages = usages.filter { usage ->
                    val startOffset = usage.navigationOffset
                    val endOffset = startOffset + 1
                    when {
                        startOffset >= lineEnd -> false
                        endOffset <= lineStart -> false
                        else -> true
                    }
                }
                when {
                    intersectingUsages.isNotEmpty() -> formatLine(index, line, true)
                    else -> "..."
                }
            }.joinToString("\n").replace("(?:\\.\\.\\.\n){2,}".toRegex(), "...\n")
        }

        private fun getCodeContext(): String {
            return usages.entries.joinToString("\n\n") { (file, usages) ->
                file ?: return@joinToString ""

                val document = PsiDocumentManager.getInstance(project).getDocument(
                    file.findPsiFile(project) ?: return@joinToString ""
                ) ?: return@joinToString ""

                val usageLocations = usages.joinToString("\n") { usage ->
                    val lineNumber = document.getLineNumber(usage.navigationOffset)
                    "* Line ${lineNumber + 1}: ${usage.presentation.plainText}"
                }
                "\n## ${file.name}\nUsage locations:\n$usageLocations\n```${file.extension}\n${
                    getFilteredLines(
                        project,
                        file,
                        usages
                    )
                }\n```"
            }
        }

        override fun userMessage(
            session: Session,
            user: User,
            userMessage: String,
            ui: SocketManager
        ) {
            val task = ui.newTask()
            task.echo(renderMarkdown(userMessage))
            task.verbose((getCodeContext()).renderMarkdown())
            val model = AppSettingsState.instance.smartChatClient.getChildClient(task)
            Retryable(task = task) { content ->
                val task = ui.newTask(false)
                task.add(
                    "<div>" + renderMarkdown(
                        ChatAgent(
                            prompt = """
                             You are a helpful AI that helps people understand code.
                             You will be answering questions about code with the following find results:
                             """.trimIndent() + getCodeContext(),
                            model = model
                        ).answer(listOf(userMessage))
                    ) + "</div>"
                )
                task.placeholder
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/find/FindResultsModificationAction.kt

```
package cognotik.actions.find

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.usages.Usage
import com.intellij.usages.UsageInfo2UsageAdapter
import com.intellij.usages.UsageView
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import javax.swing.Icon

class FindResultsModificationAction(
    name: String? = "Modify Find Results",
    description: String? = "Modify files based on find results",
    icon: Icon? = null
) : BaseAction(name, description, icon) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        val folder = event.getSelectedFolder()
        val root: Path = if (null != folder) {
            folder.toFile.toPath()
        } else {
            getModuleRootForFile(
                event.getSelectedFile()?.parent?.toFile
                    ?: throw RuntimeException("No file or folder selected")
            ).toPath()
        }
        val project = event.project ?: return
        val usageView = event.getData(UsageView.USAGE_VIEW_KEY) ?: return
        val usages = usageView.usages.toTypedArray()
        if (usages.isEmpty()) {
            UITools.showWarning(project, "No find results selected for modification")
            return
        }
        val modificationParams = showModificationDialog(project, *usages) ?: return
        try {
            val session = Session.newGlobalID()
            SessionProxyServer.metadataStorage.setSessionName(
                null,
                session,
                "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
            )
            val fileListMap = usages.groupBy { getFile(it) }
            SessionProxyServer.chats[session] = PatchApp(
                root = root.toFile(),
                modificationParams = modificationParams,
                project = event.project ?: return,
                usages = fileListMap
            )
            ApplicationServer.appInfoMap[session] = AppInfoData(
                applicationName = "Code Chat",
                inputCnt = 1,
                stickyInput = false,
                loadImages = false,
                showMenubar = false
            )
            UITools.runAsync(event.project, "Opening Browser", true) { progress ->
                Thread.sleep(500)
                try {
                    val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                        AppSettingsState.instance.listeningEndpoint,
                        AppSettingsState.instance.listeningPort
                    ).server.uri.resolve("/#$session")
                    log.info("Opening browser to $uri")
                    browse(uri)
                } catch (e: Throwable) {
                    val message = "Failed to open browser: ${e.message}"
                    log.error(message, e)
                    UITools.showErrorDialog(message, "Error")
                }
            }
        } catch (ex: Exception) {
            UITools.error(log, "Error modifying files", ex)
        }
    }

    private fun getFile(it: Usage) = when {
        it is UsageInfo2UsageAdapter -> {
            it.file
        }

        else -> {
            it.location?.editor?.file
        }
    }

    inner class PatchApp(
        override val root: File,
        val modificationParams: ModificationParams,
        val project: Project,
        val usages: Map<VirtualFile?, List<Usage>>,
    ) : ApplicationServer(
        applicationName = "Multi-file Patch Chat",
        path = "/patchChat",
        showMenubar = false,
    ) {
        override val inputCnt = 1
        override val stickyInput = false

        override fun newSession(user: User, session: Session): SocketManager {
            val socketManager = super.newSession(user, session)
            val task = socketManager.newTask(cancelable = false)
            val tabs = TabbedDisplay(task)
            usages.entries.map { (file, usages) ->
                val task = socketManager.newTask(cancelable = false, root = false)
                tabs[file?.name ?: "Unknown"] = task.placeholder
                lateinit var fileListingMarkdown: String
                lateinit var prompt: String
                ApplicationManager.getApplication().runReadAction {
                    file ?: return@runReadAction
                    fileListingMarkdown =
                        "## ${file.name}\n\n```${file.extension}\n${getFilteredLines(project, file, usages)}\n```\n"
                    task.add(renderMarkdown(fileListingMarkdown))
                    prompt = """
                    You are a code modification assistant. You will receive code files and locations where changes are needed.
                    Your task is to suggest appropriate modifications based on the replacement text provided.
                    Usage locations:
                    """.trimIndent() + usages.joinToString("\n") { "* `${it.presentation.plainText}`" } +
                            "\n\nRequested modification: " + modificationParams.replacementText + "\n\n" + AppSettingsState.instance.processor.patchFormatPrompt
                }
                socketManager.pool.submit {
                    //val api = api.getChildClient(task)
                    val response = ChatAgent(
                        prompt = prompt,
                        model = AppSettingsState.instance.smartChatClient.getChildClient(task)
                    ).answer(
                        listOf(
                            fileListingMarkdown
                        ),
                    ).replace(Regex("""/\* L\d+ \*/"""), "")
                        .replace(Regex("""/\* <<< \*/"""), "")
                    AddApplyFileDiffLinks.instrumentFileDiffs(
                        socketManager,
                        root = root.toPath(),
                        response = response,
                        handle = { newCodeMap ->
                            newCodeMap.forEach { (path, newCode) ->
                                task.complete("Updated $path")
                            }
                        },
                        shouldAutoApply = { modificationParams.autoApply },
                        defaultFile = file?.toFile?.path,
                        processor = AppSettingsState.instance.processor
                    ).apply {
                        task.complete(renderMarkdown(this))
                    }
                }
            }.toTypedArray().forEach { it.get() }
            return socketManager
        }

    }

    private fun getSmallestContainingEntity(psiRoot: PsiFile?, usage: Usage) =
        PsiUtil.getSmallestContainingEntity(
            element = psiRoot!!,
            selectionStart = usage.navigationOffset,
            selectionEnd = usage.presentation.plainText.length + usage.navigationOffset - 1
        )

    private fun formatLine(index: Int, line: String, isFocused: Boolean) = when {
        isFocused -> "/* L$index */ $line /* <<< */"
        else -> "/* L$index */ $line"
    }

    private fun getFilteredLines(project: Project, file: VirtualFile, usages: List<Usage>): String? {
        val document =
            PsiDocumentManager.getInstance(project).getDocument(file.findPsiFile(project) ?: return null) ?: return null
        val psiRoot: PsiFile? = file.findPsiFile(project)
        val byContainer = usages.groupBy {
            getSmallestContainingEntity(
                psiRoot,
                it
            )
        }.entries.sortedBy { it.key?.textRange?.startOffset }.toTypedArray()
        val filteredLines = document.text.lines().mapIndexed { index: Int, line: String ->
            val lineStart = document.getLineStartOffset(index)
            val lineEnd = document.getLineEndOffset(index)
            val containers = byContainer.map { it.key }.filter { psiElement ->
                psiElement ?: return@filter false
                val textRange = psiElement.textRange
                val startOffset = textRange.startOffset
                val endOffset = textRange.endOffset
                when {
                    startOffset >= lineEnd -> false
                    endOffset <= lineStart -> false
                    else -> true
                }
            }
            val intersectingUsages = usages.filter { usage ->

                val startOffset = usage.navigationOffset
                val endOffset = startOffset + 1

                when {
                    startOffset >= lineEnd -> false
                    endOffset <= lineStart -> false
                    else -> true
                }
            }
            when {
                intersectingUsages.isNotEmpty() -> formatLine(index, line, true)
                containers.isNotEmpty() -> formatLine(index, line, false)
                else -> "..."
            }
        }.joinToString("\n").replace("(?:\\.\\.\\.\n){2,}".toRegex(), "...\n")
        return filteredLines
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val usageView = event.getData(UsageView.USAGE_VIEW_KEY)
        return usageView != null && usageView.usages.isNotEmpty()
    }

    private fun showModificationDialog(project: Project, vararg usages: Usage): ModificationParams? {
        val dialog = FindResultsModificationDialog(project, usages.size)
        val config = dialog.showAndGetConfig()
        return if (config != null) {
            ModificationParams(
                replacementText = config.replacementText ?: "",
                autoApply = config.autoApply
            )
        } else null
    }

    data class ModificationParams(
        val replacementText: String,
        val autoApply: Boolean
    )

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/find/FindResultsModificationDialog.kt

```
package cognotik.actions.find

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class FindResultsModificationDialog(
    project: Project,
    matchCount: Int
) : DialogWrapper(project) {

    private var replacementText = "Please modify this code to: "
    private var autoApply = false

    init {
        title = "AI-Based Find Results Modification"
        setOKButtonText("Modify Code")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Modification Instructions:") {
                textArea()
                    .bindText({ replacementText }, { replacementText = it })
                    .rows(5)
                    .align(Align.FILL)
                    .comment("Enter instructions for how you want the code to be modified")
                    .focused()
                    .apply {
                        component.lineWrap = true
                        component.wrapStyleWord = true
                        component.selectAll()
                    }
            }.resizableRow()
            row {
                checkBox("Auto-apply changes")
                    .bindSelected({ autoApply }, { autoApply = it })
                    .comment("Automatically apply changes without manual confirmation")
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (replacementText.isBlank()) {
            return ValidationInfo("Please enter instructions for code modification")
        }
        if (replacementText.length < 10) {
            return ValidationInfo("Please provide more detailed instructions")
        }
        return null
    }

    data class ConfigData(
        val replacementText: String?,
        val autoApply: Boolean
    )

    fun showAndGetConfig(): ConfigData? {
        if (showAndGet()) {
            return ConfigData(
                replacementText = replacementText,
                autoApply = autoApply
            )
        }
        return null
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

Here is the new `README.md` for the `cognotik.actions.find` package, based on the provided source code.

### intellij/src/main/kotlin/cognotik/actions/find/README.md
```markdown
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
```

### Summary of Changes
- Created a new `README.md` in `intellij/src/main/kotlin/cognotik/actions/find/`.
- Documented the purpose and functionality of `FindResultsChatAction` and `FindResultsModificationAction`.
- Described the context-gathering mechanism (filtered line views) used by these actions.
- Explained the integration with the IntelliJ `UsageView` and the web-based UI components.
- Included details about the `FindResultsModificationDialog`.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/find/README.md'>intellij/src/main/kotlin/cognotik/actions/find/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/find/README.md'>intellij/src/main/kotlin/cognotik/actions/find/README.md</a> Updated
