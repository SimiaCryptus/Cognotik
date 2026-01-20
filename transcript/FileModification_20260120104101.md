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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/ChatWithCommitAction.kt

```
package cognotik.actions.git

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.CodeChatSocketManager
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.isBinary
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.io.File
import java.text.SimpleDateFormat

class ChatWithCommitAction : AnAction() {
    private val log = Logger.getInstance(ChatWithCommitAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        log.info("Comparing selected revision with the current working copy")
        val files = expand(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
        val changes = e.getData(VcsDataKeys.CHANGES)
        Thread {
            try {
                val map = changes?.toList()
                    ?.associateBy { (it.beforeRevision?.file ?: it.afterRevision?.file)!!.toString() }
                val msg = map?.entries
                    ?.filter { (file, change) ->
                        val find = files?.find { it.toNioPath().toFile().absolutePath == File(file).absolutePath }
                        find != null
                    }
                    ?.joinToString("\n\n") { (file, change) ->
                        val before = change.beforeRevision?.content
                        val after = change.afterRevision?.content
                        if ((before ?: after)!!.isBinary)
                            return@joinToString "# Binary: ${change.afterRevision?.file}".prependIndent("  ")
                        if (before == null) return@joinToString "# Deleted: ${change.afterRevision?.file}\n${after}".replace(
                            "\n",
                            "\n  "
                        )
                        if (after == null) return@joinToString "# Added: ${change.beforeRevision?.file}\n${before}".replace(
                            "\n",
                            "\n  "
                        )
                        val diff = AppSettingsState.instance.processor.generatePatch(before, after)
                        "# Change: ${change.beforeRevision?.file}\n$diff".prependIndent("  ")
                    }

                openChatWithDiff(e, msg ?: "No changes found")
            } catch (e: Throwable) {
                log.error("Error comparing changes", e)
            }
        }.start()
    }

    private fun openChatWithDiff(e: AnActionEvent, diffInfo: String) {
        val session = Session.newGlobalID()
        SessionProxyServer.agents[session] = CodeChatSocketManager(
            session = session,
            language = "diff",
            codeSelection = diffInfo,
            filename = "commit_changes.diff",
            model = AppSettingsState.instance.smartChatClient,
            parsingModel = AppSettingsState.instance.fastChatClient,
            storage = ApplicationServices.fileApplicationServices().dataStorageFactory
        )
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Code Chat",
            inputCnt = 0,
            stickyInput = true,
            loadImages = false,
            showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )

        Thread {
            Thread.sleep(500)
            try {
                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    private fun expand(data: Array<VirtualFile>?): Array<VirtualFile>? {
        return data?.flatMap {
            if (it.isDirectory) {
                expand(it.children.toList().toTypedArray())?.toList() ?: listOf()
            } else {
                listOf(it)
            }
        }?.toTypedArray()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(PlatformDataKeys.PROJECT) != null &&
                e.getData(VcsDataKeys.VCS)?.name != "Git"
    }

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/ChatWithCommitDiffAction.kt

```
package cognotik.actions.git

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LocalFilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.TextRevisionNumber
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.CodeChatSocketManager
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.text.SimpleDateFormat
import com.intellij.openapi.application.ApplicationManager as IntellijAppManager

class ChatWithCommitDiffAction : BaseAction(
    name = "Chat with Commit Diff",
    description = "Opens a chat interface to discuss commit differences"
) {
    companion object {
        private val log = Logger.getInstance(ChatWithCommitDiffAction::class.java)
    }

    override fun handle(e: AnActionEvent) {
        log.info("Comparing selected commit with the current HEAD")
        val project = e.project ?: return
        val selectedCommit = e.getData(VcsDataKeys.VCS_REVISION_NUMBER) ?: return
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val vcs = vcsManager.allActiveVcss.firstOrNull() ?: run {
            UITools.showErrorDialog("No active VCS found", "Error")
            return
        }

        UITools.runAsync(project, "Comparing Changes", true) { progress ->
            try {
                progress.text = "Retrieving changes between commits..."
                val diffInfo = getChangesBetweenCommits(project, selectedCommit).ifEmpty { "No changes found" }
                progress.text = "Opening chat interface..."
                openChatWithDiff(e, diffInfo)
            } catch (e: Throwable) {
                log.error("Error comparing changes", e)
                UITools.showErrorDialog("Error comparing changes: ${e.message}", "Error")
            }
        }
    }

    private fun openChatWithDiff(e: AnActionEvent, diffInfo: String) {
        val session = Session.newGlobalID()
        SessionProxyServer.agents[session] = CodeChatSocketManager(
            session = session,
            language = "diff",
            codeSelection = diffInfo,
            filename = "commit_changes.diff",
            model = AppSettingsState.instance.smartChatClient,
            parsingModel = AppSettingsState.instance.fastChatClient,
            storage = ApplicationServices.fileApplicationServices().dataStorageFactory
        )
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Code Chat",
            inputCnt = 0,
            stickyInput = true,
            loadImages = false,
            showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )

        IntellijAppManager.getApplication().executeOnPooledThread {
            Thread.sleep(500)
            try {
                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }
    }

    private fun getChangesBetweenCommits(project: Project, selectedCommit: VcsRevisionNumber): String {
        val commitID = (selectedCommit as TextRevisionNumber).asString()
        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.allChanges
        return changes.joinToString("\n") { change: Change ->
            buildString {
                appendLine("File: ${change.virtualFile?.path ?: "Unknown"}")
                appendLine("Type: ${change.type}")
                appendLine(getDiffForChange(project, change, selectedCommit) ?: "No diff available")
            }
        }
    }

    private fun getDiffForChange(project: Project, change: Change, selectedCommit: VcsRevisionNumber): String? {
        val file = change.virtualFile ?: return null
        val currentContent = change.afterRevision?.content ?: return null
        val selectedContent = getContentForRevision(project, file, selectedCommit) ?: return null
        return createSimpleDiff(currentContent, selectedContent)
    }

    private fun getContentForRevision(project: Project, file: VirtualFile, revisionNumber: VcsRevisionNumber): String? {
        try {
            val contentRevision = CurrentContentRevision(LocalFilePath(file.path, file.isDirectory))
            return contentRevision.content
        } catch (e: Exception) {
            log.error("Error getting content for revision", e)
            return null
        }
    }

    private fun createSimpleDiff(currentContent: String, selectedContent: String): String {
        val currentLines = currentContent.lines()
        val selectedLines = selectedContent.lines()
        val diff = StringBuilder()
        for ((index, line) in currentLines.withIndex()) {
            if (index >= selectedLines.size) {
                diff.appendLine("+ $line")
            } else if (line != selectedLines[index]) {
                diff.appendLine("- ${selectedLines[index]}")
                diff.appendLine("+ $line")
            }
        }
        if (selectedLines.size > currentLines.size) {
            for (i in currentLines.size until selectedLines.size) {
                diff.appendLine("- ${selectedLines[i]}")
            }
        }
        return diff.toString()
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/ChatWithWorkingCopyDiffAction.kt

```
package cognotik.actions.git

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.CodeChatSocketManager
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.text.SimpleDateFormat
import javax.swing.JOptionPane

class ChatWithWorkingCopyDiffAction : AnAction() {
    companion object {
        private val log = Logger.getInstance(ChatWithWorkingCopyDiffAction::class.java)
    }

    override fun actionPerformed(e: AnActionEvent) {
        log.info("Comparing HEAD with the working copy")
        val project = e.project ?: return
        val files = e.getData(VcsDataKeys.VIRTUAL_FILES)?.firstOrNull()
        val changeListManager = ChangeListManager.getInstance(project)

        Thread {
            try {
                val diffInfo = getWorkingCopyDiff(changeListManager)
                openChatWithDiff(e, diffInfo)
            } catch (e: Throwable) {
                log.error("Error comparing changes", e)
                JOptionPane.showMessageDialog(null, e.message, "Error", JOptionPane.ERROR_MESSAGE)
            }
        }.start()
    }

    private fun openChatWithDiff(e: AnActionEvent, diffInfo: String) {
        val session = Session.newGlobalID()
        SessionProxyServer.agents[session] = CodeChatSocketManager(
            session = session,
            language = "diff",
            codeSelection = diffInfo,
            filename = "working_copy_changes.diff",
            model = AppSettingsState.instance.smartChatClient,
            parsingModel = AppSettingsState.instance.fastChatClient,
            storage = ApplicationServices.fileApplicationServices().dataStorageFactory
        )
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Code Chat",
            inputCnt = 0,
            stickyInput = true,
            loadImages = false,
            showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )

        Thread {
            Thread.sleep(500)
            try {
                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    private fun getWorkingCopyDiff(changeListManager: ChangeListManager): String {
        val changes = changeListManager.allChanges
        return changes.joinToString("\n\n") { change ->
            val diffForChange = getDiffForChange(change)
            "File: ${change.virtualFile?.path ?: "Unknown"}\n" +
                    "Type: ${change.type}\n" +
                    (diffForChange ?: "No diff available")
        }.ifEmpty { "No changes found" }
    }

    private fun getDiffForChange(change: com.intellij.openapi.vcs.changes.Change): String? {
        val beforeRevision = change.beforeRevision
        val afterRevision = change.afterRevision

        if (beforeRevision == null && afterRevision == null) {
            return null
        }

        val beforeContent = beforeRevision?.content ?: ""
        val afterContent = afterRevision?.content ?: ""

        return createSimpleDiff(beforeContent, afterContent)
    }

    private fun createSimpleDiff(beforeContent: String, afterContent: String): String {
        val beforeLines = beforeContent.lines()
        val afterLines = afterContent.lines()
        val diff = StringBuilder()

        for ((index, line) in afterLines.withIndex()) {
            if (index >= beforeLines.size) {
                diff.appendLine("+ $line")
            } else if (line != beforeLines[index]) {
                diff.appendLine("- ${beforeLines[index]}")
                diff.appendLine("+ $line")
            }
        }

        if (beforeLines.size > afterLines.size) {
            for (i in afterLines.size until beforeLines.size) {
                diff.appendLine("- ${beforeLines[i]}")
            }
        }

        return diff.toString()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val vcs = e.getData(VcsDataKeys.VCS)
        e.presentation.isEnabledAndVisible = vcs != null
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/ReplicateCommitAction.kt

```
package cognotik.actions.git

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.walk

class ReplicateCommitAction : BaseAction() {
    private val log = Logger.getInstance(ReplicateCommitAction::class.java)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        val project = event.project ?: return
        try {
            val settings = getUserSettings(event) ?: run {
                Messages.showErrorDialog(project, "Could not determine working directory", "Configuration Error")
                return
            }

            val dataContext = event.dataContext
            val virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
            val folder = event.getSelectedFolder()
            val root = if (null != folder) {
                folder.toFile.toPath()
            } else {
                project.basePath?.let { File(it).toPath() }
            }!!

            val virtualFiles1 = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
            val files = expand(virtualFiles1)
            val changes = event.getData(VcsDataKeys.CHANGES)
            val session = Session.newGlobalID()

            UITools.run(project, "Replicating Commit", true) { progress ->
                progress.text = "Generating diff info..."
                val diffInfo = generateDiffInfo(files, changes)
                progress.text = "Creating patch application..."
                val patchApp = object : PatchApp(root.toFile(), session, settings, diffInfo) {
                    override fun codeFiles() = getFiles(virtualFiles)
                        .filter { it.toFile().length() < 1024 * 1024 / 2 }

                        .map { root.relativize(it) ?: it }.toSet()

                    override fun codeSummary(paths: List<Path>): String = paths
                        .filter { it.toFile().exists() }
                        .joinToString("\n\n") { path ->
                            "# ${settings.workingDirectory.toPath().relativize(path)}\n$tripleTilde${
                                path.toString().split('.').lastOrNull()
                            }\n${
                                path.toFile().readText(Charsets.UTF_8)
                            }\n$tripleTilde"
                        }

                    override fun projectSummary(): String {
                        val codeFiles = codeFiles()
                        val str = codeFiles
                            .asSequence()
                            .filter { settings.workingDirectory.toPath()?.resolve(it)?.toFile()?.exists() == true }
                            .distinct().sorted()
                            .joinToString("\n") { path ->
                                "* ${path} - ${
                                    settings.workingDirectory.toPath()?.resolve(path)?.toFile()?.length() ?: "?"
                                } bytes".trim()
                            }
                        return str
                    }
                }
                progress.text = "Setting up session..."
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.chats[session] = patchApp
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Code Chat",
                    inputCnt = 1,
                    stickyInput = false,
                    loadImages = false,
                    showMenubar = false
                )
            }
            ApplicationManager.getApplication().executeOnPooledThread {
                Thread.sleep(500)
                try {
                    val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                        AppSettingsState.instance.listeningEndpoint,
                        AppSettingsState.instance.listeningPort
                    ).server.uri.resolve("/#$session")
                    log.info("Opening browser to $uri")
                    browse(uri)
                } catch (e: Throwable) {
                    log.error("Error opening browser", e)
                    UITools.showErrorDialog("Failed to open browser: ${e.message}", "Error")
                }
            }
        } catch (e: Exception) {
            log.error("Error in ReplicateCommitAction", e)
            Messages.showErrorDialog(project, "Operation failed: ${e.message}", "Error")
        }
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val changes = event.getData(VcsDataKeys.CHANGES)
        return changes != null && changes.isNotEmpty()
    }

    private fun generateDiffInfo(files: Array<VirtualFile>?, changes: Array<out Change>?): String {
        val map = changes?.toList()
            ?.associateBy { (it.beforeRevision?.file ?: it.afterRevision?.file)!!.toString() }
        val entries = map?.entries
            ?.filter { (file, change) ->
                try {
                    val find = files?.find { it.toNioPath().toFile().absolutePath == File(file).absolutePath }
                    find != null
                } catch (e: Exception) {
                    log.error("Error comparing changes", e)
                    false
                }
            }
        return entries
            ?.joinToString("\n\n") { (file, change) ->
                val before = change.beforeRevision?.content
                val after = change.afterRevision?.content
                if ((before ?: after)!!.isBinary)
                    return@joinToString "# Binary: ${change.afterRevision?.file}".prependIndent("  ")
                if (before == null) return@joinToString "# Deleted: ${change.afterRevision?.file}\n${after}".replace(
                    "\n",
                    "\n  "
                )
                if (after == null) return@joinToString "# Added: ${change.beforeRevision?.file}\n${before}".replace(
                    "\n",
                    "\n  "
                )
                val diff = AppSettingsState.instance.processor.generatePatch(before, after)
                "# Change: ${change.beforeRevision?.file}\n$diff".prependIndent("  ")
            } ?: "No changes found"
    }

    abstract inner class PatchApp(
        override val root: File,
        val session: Session,
        val settings: Settings,
        val diffInfo: String,
    ) : ApplicationServer(
        applicationName = "Replicate Commit",
        path = "/replicateCommit",
        showMenubar = false,
    ) {
        abstract fun codeFiles(): Set<Path>
        abstract fun codeSummary(paths: List<Path>): String
        override val inputCnt = 1
        override val stickyInput = false

        override fun userMessage(
            session: Session,
            user: User,
            userMessage: String,
            ui: SocketManager
        ) {
            val task = ui.newTask()
            task.echo(userMessage)
            Thread {
                run(ui, task, session, settings, userMessage, diffInfo)
            }.start()
        }

        abstract fun projectSummary(): String
    }

    private fun PatchApp.run(
        ui: SocketManager,
        task: SessionTask,
        session: Session,
        settings: Settings,
        userMessage: String = "",
        diffInfo: String
    ) {
        try {
            val planTxt = projectSummary()
            task.add(renderMarkdown(planTxt))
            Retryable(task) {
                val task = ui.newTask(false)
                val plan = ParsedAgent(
                    resultClass = ParsedTasks::class.java,
                    prompt = """
                      You are a helpful AI that helps people with coding.

                      You will be answering questions about the following project:

                      Project Root: """.trimIndent() + (settings.workingDirectory.absolutePath ?: "") + """

                      Files:
                      """.trimIndent() + planTxt + """

                      Given the request, identify one or more tasks.
                      For each task:
                         1) predict the files that need to be fixed
                         2) predict related files that may be needed to debug the issue
                      """.trimIndent(),
                    model = AppSettingsState.instance.smartChatClient,
                    parsingChatter = AppSettingsState.instance.fastChatClient,
                ).answer(
                    listOf(
                        "We want to create a change based on the following prior commit:\n\n$tripleTilde\n$diffInfo\n$tripleTilde\n\nThe change should implement the user's request:\n\n$tripleTilde\n$userMessage\n$tripleTilde"
                    ),
                )
                task.add(
                    AgentPatterns.displayMapInTabs(
                        mapOf(
                          "Text" to plan.text.renderMarkdown(true),
                          "JSON" to "${tripleTilde}json\n${JsonUtil.toJson(plan.obj)}\n$tripleTilde".renderMarkdown(true),
                        )
                    )
                )
                plan.obj.errors?.map { planTask ->
                    Retryable(task) {
                        val task = ui.newTask(false)
                        val paths =
                            ((planTask.fixFiles ?: emptyList()) + (planTask.relatedFiles ?: emptyList())).flatMap {
                                toPaths(settings.workingDirectory.toPath(), it)
                            }
                        val codeSummary = codeSummary(paths)
                        val response = ChatAgent(
                            prompt = """
                  You are a helpful AI that helps people with coding.

                  You will be answering questions about the following code:

                  """.trimIndent() + codeSummary + "\n" + { AppSettingsState.instance.processor.patchFormatPrompt } +
                                    "\nIf needed, new files can be created by using code blocks labeled with the filename in the same manner.",
                            model = AppSettingsState.instance.smartChatClient
                        ).answer(
                            listOf(
                                """
                              We are working on executing the following directive:

                              """.trimIndent() + tripleTilde + """
                              """.trimIndent() + userMessage + """
                              """.trimIndent() + tripleTilde + """

                              Focus on the task at hand:
                              """.trimIndent() + (planTask.message?.prependIndent("  ") ?: "")
                            ),
                        )
                        val markdown = AddApplyFileDiffLinks.instrumentFileDiffs(
                            ui,
                            root = root.toPath(),
                            response = response,
                            handle = { newCodeMap ->
                                newCodeMap.forEach { (path, newCode) ->
                                    task.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                }
                            },
                            processor = AppSettingsState.instance.processor,
                        )
                        task.add(renderMarkdown(markdown))
                        task.placeholder
                    }
                    ""
                }?.joinToString { it }?.apply { task.add(this) }
                task.placeholder
            }
        } catch (e: Exception) {
            task.error(e)
        }
    }

    data class ParsedTasks(
        val errors: List<ParsedTask>? = null
    )

    data class ParsedTask(
        @Description("The task to be performed")
        val message: String? = null,
        @Description("Files identified as needing modification and issue-related files")
        val relatedFiles: List<String>? = null,
        @Description("Files identified as needing modification and issue-related files")
        val fixFiles: List<String>? = null
    )

    data class Settings(
        var workingDirectory: File,
    )

    private fun getFiles(
        virtualFiles: Array<out VirtualFile>?
    ): MutableSet<Path> {
        val codeFiles = mutableSetOf<Path>()

        virtualFiles?.forEach { file ->
            if (file.isDirectory) {
                if (file.name.startsWith(".")) return@forEach
                if (FileSelectionUtils.isGitignore(file.toNioPath())) return@forEach
                codeFiles.addAll(getFiles(file.children))
            } else {
                codeFiles.add((file.toNioPath()))
            }
        }
        return codeFiles
    }

    private fun getUserSettings(event: AnActionEvent?): Settings? {
        val root = (event ?: return null).getSelectedFolder()?.toNioPath() ?: event.project?.basePath?.let {
            File(
                it
            ).toPath()
        }
        val files = event.getSelectedFiles().map { it.path.let { File(it).toPath() } }.toMutableSet()
        if (files.isEmpty()) Files.walk(root)
            .filter { Files.isRegularFile(it) && !Files.isDirectory(it) }
            .toList().filterNotNull().forEach { files.add(it) }
        return Settings(root?.toFile() ?: return null)
    }

    private fun expand(data: Array<VirtualFile>?): Array<VirtualFile>? {
        return data?.flatMap {
            if (it.isDirectory) {
                expand(it.children.toList().toTypedArray())?.toList() ?: listOf()
            } else {
                listOf(it)
            }
        }?.toTypedArray()
    }

    companion object {
        val tripleTilde = "`" + "``"


        @OptIn(ExperimentalPathApi::class)
        fun toPaths(root: Path, it: String): Iterable<Path> {

            if (it.contains("*")) {
                val prefix = it.substringBefore("*")
                val suffix = it.substringAfter("*")
                val files = root.walk().toList()
                val pathList = files.filter {
                    it.toString().startsWith(prefix) && it.toString().endsWith(suffix)
                }.toList()
                return pathList
            } else {
                return listOf(Path.of(it))
            }
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

Here is the updated README.md for the Git actions package.

### /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/README.md
```markdown
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
```

### Summary of Changes
- Created a new `README.md` in `/home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/` as per the transformation rules.
- Documented the four primary actions found in the directory: `ChatWithCommitAction`, `ChatWithCommitDiffAction`, `ChatWithWorkingCopyDiffAction`, and `ReplicateCommitAction`.
- Provided a high-level overview of the package's purpose and technical implementation details (diffing, session management, and AI usage).
- Listed key internal and IntelliJ dependencies used by these actions.
</details>

                - <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/README.md'>/home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC//home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/README.md'>/home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/git/README.md</a> Updated
