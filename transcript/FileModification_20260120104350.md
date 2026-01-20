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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/chat/CodeChatAction.kt

```
﻿package cognotik.actions.chat

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.CodeChatSocketManager
import com.simiacryptus.cognotik.util.LanguageUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.text.SimpleDateFormat

class CodeChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val session = Session.newGlobalID()
        val language = LanguageUtils.getComputerLanguage(e)?.name ?: ""
        val filename = FileDocumentManager.getInstance().getFile(editor.document)?.name ?: return
        SessionProxyServer.agents[session] = CodeChatSocketManager(
            session = session,
            language = language,
            codeSelection = editor.caretModel.primaryCaret.selectedText ?: editor.document.text,
            filename = filename,
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
                BaseAction.log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    companion object {
        private val log = LoggerFactory.getLogger(CodeChatAction::class.java)
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/chat/DiffChatAction.kt

```
package cognotik.actions.chat

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.AddApplyDiffLinks.Companion.addApplyDiffLinks
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.intellij.lang.annotations.Language
import java.text.SimpleDateFormat
import com.intellij.openapi.application.ApplicationManager as IntellijAppManager

class DiffChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    val path = "/diffChat"

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return false
        val document = editor.document
        return FileDocumentManager.getInstance().getFile(document) != null
    }

    override fun handle(e: AnActionEvent) {
        try {
            val editor = e.getData(CommonDataKeys.EDITOR) ?: return
            val session = Session.newGlobalID()
            val language = ComputerLanguage.getComputerLanguage(e)?.name ?: ""
            val document = editor.document
            val filename = FileDocumentManager.getInstance().getFile(document)?.name ?: return
            val (rawText, selectionStart, selectionEnd) = getSelectionDetails(editor)
            UITools.runAsync(e.project, "Initializing Chat", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up chat session..."
                setupApplicationServer(session)
                setupSessionProxy(session, language, rawText, filename, editor, selectionStart, selectionEnd, document)
                openBrowserWindow(e, session)
            }
        } catch (ex: Throwable) {
            log.error("Error in DiffChat action", ex)
            UITools.showErrorDialog("Failed to initialize chat: ${ex.message}", "Error")
        }
    }

    private fun getSelectionDetails(editor: Editor): Triple<String, Int, Int> {
        val primaryCaret = editor.caretModel.primaryCaret
        val selectedText = primaryCaret.selectedText
        return if (selectedText != null) {
            Triple(
                selectedText,
                primaryCaret.selectionStart,
                primaryCaret.selectionEnd
            )
        } else {
            Triple(
                editor.document.text,
                0,
                editor.document.text.length
            )
        }
    }

    private fun setupApplicationServer(session: Session) {
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Code Chat",
            inputCnt = 0,
            stickyInput = true,
            loadImages = false,
            showMenubar = false
        )
    }

    private fun setupSessionProxy(
        session: Session,
        language: String,
        rawText: String,
        filename: String,
        editor: Editor,
        selectionStart: Int,
        selectionEnd: Int,
        document: Document
    ) {
        var selectionEnd = selectionEnd

        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
        SessionProxyServer.agents[session] = object : CodeChatSocketManager(
            session = session,
            language = language,
            codeSelection = rawText,
            filename = filename,
            model = AppSettingsState.instance.smartChatClient,
            parsingModel = AppSettingsState.instance.fastChatClient,
            storage = ApplicationServices.fileApplicationServices().dataStorageFactory
        ) {
            override val systemPrompt: String
                @Language("Markdown")
                get() = super.systemPrompt + """
                  Please provide code modifications in the following diff format within triple-backtick diff code blocks. Each diff block should be preceded by a header that identifies the file being modified.

                  The diff format rules are as follows:
                  - Use '-' at the beginning of a line to indicate a deletion.
                  - Use '+' at the beginning of a line to indicate an addition.
                  - Include 2 lines of context before and after every change to help identify the location of the change.
                  - If a line is part of the original code and hasn't been modified, simply include it without '+' or '-'.
                  - Lines starting with "@@" or "---" or "+++" are treated as headers and are ignored.

                """.trimIndent() + AppSettingsState.instance.processor.patchFormatPrompt

            override fun renderResponse(response: String, task: SessionTask): String = """<div>${
                renderMarkdown(response, tabs=true) {
                    addApplyDiffLinks(
                        this,
                        code = {
                            editor.document.getText(TextRange(selectionStart, selectionEnd))
                        },
                        response = response,
                        handle = { newCode: String ->
                            WriteCommandAction.runWriteCommandAction(editor.project) {
                                selectionEnd = selectionStart + newCode.length
                                document.replaceString(selectionStart, selectionStart + rawText.length, newCode)
                            }
                        },
                        task = task,
                        processor = AppSettingsState.instance.processor
                    )
                }                
            }</div>"""
        }
    }

    private fun openBrowserWindow(e: AnActionEvent, session: Session) {
        IntellijAppManager.getApplication().executeOnPooledThread {
            val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                AppSettingsState.instance.listeningEndpoint,
                AppSettingsState.instance.listeningPort
            ).server.uri.resolve("/#$session")
            BaseAction.log.info("Opening browser to $uri")
            browse(uri)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(DiffChatAction::class.java)
    }
}

```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/chat/GenericChatAction.kt

```
package cognotik.actions.chat

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatSocketManager
import java.text.SimpleDateFormat

class GenericChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private val systemPrompt = ""

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return

        try {
            UITools.runAsync(project, "Initializing Chat", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up chat session..."

                val session = Session.newGlobalID()
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.agents[session] = ChatSocketManager(
                    session = session,
                    smartModel = AppSettingsState.instance.smartChatClient,
                    fastModel = AppSettingsState.instance.fastChatClient,
                    systemPrompt = systemPrompt,
                    applicationClass = ApplicationServer::class.java,
                    storage = ApplicationServices.fileApplicationServices().dataStorageFactory,
                    budget = 2.0
                )
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Code Chat",
                    inputCnt = 0,
                    stickyInput = true,
                    loadImages = false,
                    showMenubar = false
                )

                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        BaseAction.log.info("Opening browser to $uri")
                        browse(uri)
                    } catch (e: Throwable) {
                        UITools.error(log, "Failed to open browser", e)
                    }
                }
            }
        } catch (e: Throwable) {
            log.warn("Error opening browser", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(GenericChatAction::class.java)
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/chat/ImageChatAction.kt

```
package cognotik.actions.chat

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.generator.nova.GenerationSpec.Companion.nullIfEmpty
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.ContentPart
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.awt.image.BufferedImage
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import javax.imageio.ImageIO
import kotlin.io.path.name

/**
 * Action that enables multi-file code chat functionality.
 * Allows users to select multiple files and discuss them with an AI assistant.
 * Supports code modifications through patch application.
 *
 * @see BaseAction
 */

class ImageChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        val root = getRoot(event) ?: return
        val codeFiles =
            getFiles(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext) ?: arrayOf(), root).toMutableSet()

        try {
            UITools.runAsync(event.project, "Initializing Chat", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up chat session..."
                val session = Session.newGlobalID()
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.agents[session] = CodeChatManager(
                    session = session,
                    model = AppSettingsState.instance.imageChatClient,
                    parsingModel = AppSettingsState.instance.fastChatClient,
                    root = root.toFile(),
                    codeFiles = codeFiles
                )
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Image Chat",
                    inputCnt = 0,
                    stickyInput = true,
                    loadImages = false,
                    showMenubar = false
                )
                Thread {
                    Thread.sleep(500)
                    try {
                        val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                            AppSettingsState.instance.listeningEndpoint,
                            AppSettingsState.instance.listeningPort
                        ).server.uri.resolve("/#${session.toString()}")
                        BaseAction.log.info("Opening browser to $uri")
                        BrowseUtil.browse(uri)
                    } catch (e: Throwable) {
                        log.warn("Error opening browser", e)
                    }
                }.start()
            }
        } catch (e: Throwable) {
            UITools.error(log, "Failed to initialize chat session", e)
        }
    }

    private fun getRoot(event: AnActionEvent): Path? {
        val folder = event.getSelectedFolder()
        return if (null != folder) {
            folder.toFile.toPath()
        } else {
            getModuleRootForFile(event.getSelectedFile()?.parent?.toFile ?: return null).toPath()
        }
    }

    inner class CodeChatManager(
        session: Session,
        model: ChatInterface,
        parsingModel: ChatInterface,
        val root: File,
        private val codeFiles: Set<Path>
    ) : ChatSocketManager(
        session = session,
        smartModel = model,
        fastModel = parsingModel,
        systemPrompt = "",
        applicationClass = ApplicationServer::class.java,
        storage = ApplicationServices.fileApplicationServices().dataStorageFactory,
        budget = 2.0,
    ) {

        override val systemPrompt: String
            get() = """
        You are a helpful AI that helps people with coding.

        You will be answering questions about the following code:
        ${codeSummary()}
      """.trimIndent()

        override val sysMessage: ChatMessage
            get() = ChatMessage(
                ModelSchema.Role.system, listOf(
                ContentPart(text = super.systemPrompt)
            ) + codeFiles.filter { isImg(it.name) }.map { path ->
                val bufferedImage = root.resolve(path.toFile()).readBufferedImage()
                ContentPart(text = "${path}").apply { image = bufferedImage }
            })

        fun File.readBufferedImage(): BufferedImage? {
            return try {
                ImageIO.read(this)
            } catch (e: Exception) {
                log.debug("Failed to read image file: $this", e)
                null
            }
        }

        private fun codeSummary(): String {
            return codeFiles.mapNotNull { path ->
                val file = root.toPath().resolve(path).toFile()
                val exists = file.exists()
                if (!exists) log.warn("File does not exist: $file")
                if (!exists) return@mapNotNull null

                val content = try {
                    readFileContent(file).nullIfEmpty() ?: return@mapNotNull null
                } catch (e: Exception) {
                    log.warn("Failed to read file: $file", e)
                    return@mapNotNull null
                }
                path to content
            }.joinToString("\n\n") { (path, content) ->
                val extension = path.toString().split('.').lastOrNull()
                "# $path\n```$extension\n$content\n```"
            }
        }

        override fun renderResponse(response: String, task: SessionTask) = """<div>${
            renderMarkdown(response, tabs=true) { html ->
                AddApplyFileDiffLinks.instrumentFileDiffs(
                    this,
                    root = root.toPath(),
                    response = html,
                    handle = { newCodeMap ->
                        newCodeMap.forEach { (path, newCode) ->
                            task.complete("<a href='${"fileIndex/$sessionId/$path"}'>$path</a> Updated")
                        }
                    },
                    processor = AppSettingsState.instance.processor,
                )
            }
        }</div>"""

        override fun respond(
            task: SessionTask,
            userMessage: String,
            currentChatMessages: List<ModelSchema.ChatMessage>,
            transcriptStream: OutputStream?
        ): String {
            val codex = GPT4Tokenizer()
            task.verbose(codeFiles.mapNotNull { path ->
                val file = root.resolve(path.toFile())
                if (!file.exists()) {
                    log.warn("File does not exist: $file")
                    return@mapNotNull null
                }
                val estimateTokenCount = try {
                    codex.estimateTokenCount(readFileContent(file))
                } catch (e: Exception) {
                    log.warn("Failed to read file: $file", e)
                    return@mapNotNull null
                }
                "* $path - $estimateTokenCount tokens"
            }.joinToString("\n").renderMarkdown())
            return super.respond(task, userMessage, currentChatMessages, transcriptStream)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImageChatAction::class.java)

        fun getFiles(
            virtualFiles: Array<out VirtualFile>?,
            root: Path
        ): Set<Path> = virtualFiles?.filter { file ->
            // Include all files that can be read by DocumentReader or are code files
            !file.name.startsWith(".") && (file.isDirectory || isSupportedFile(file))
        }?.flatMap { file ->
            if (file.isDirectory && !file.name.startsWith(".")) {
                getFiles(file.children, root)
            } else {
                setOf(root.relativize(file.toNioPath()))
            }
        }?.toSet() ?: emptySet()

        fun isSupportedFile(file: VirtualFile): Boolean {
            val name = file.name.lowercase()
            return when {
                isDoc(name) -> true
                isImg(name) -> true
                file.inputStream.use { it.isBinary } -> true
                else -> false
            }
        }

        private fun isImg(name: String): Boolean = name.endsWith(".png") ||
                name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                name.endsWith(".gif") || name.endsWith(".bmp") ||
                name.endsWith(".tiff") || name.endsWith(".webp")

        private fun isDoc(name: String): Boolean = name.endsWith(".pdf") ||
                name.endsWith(".docx") || name.endsWith(".doc") ||
                name.endsWith(".xlsx") || name.endsWith(".xls") ||
                name.endsWith(".pptx") || name.endsWith(".ppt") ||
                name.endsWith(".odt") ||
                name.endsWith(".rtf") ||
                name.endsWith(".html") || name.endsWith(".htm") ||
                name.endsWith(".eml")

        fun readFileContent(file: File): String {
            return try {
                if (isImg(file.name.lowercase())) {
                    return ""
                }
                file.getDocumentReader().use { reader ->
                    reader.getText()
                }
            } catch (e: Exception) {
                log.debug("Failed to read as document, falling back to text: ${file.name}", e)
                // Fallback to reading as plain text
                file.readText(Charsets.UTF_8)
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/chat/ModifyFilesAction.kt

```
package cognotik.actions.chat

import cognotik.actions.BaseAction
import cognotik.actions.agent.MultiStepPatchAction.AutoDevApp.Settings
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.OutputStream
import java.net.URI
import java.nio.file.Path
import java.text.SimpleDateFormat
import kotlin.io.path.relativeTo

open class ModifyFilesAction(
    protected val showLineNumbers: Boolean = false
) : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        try {
            val virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)
            val files = virtualFiles?.map { it.toFile }?.toTypedArray<File>()
            val expandFileList = FileSelectionUtils.expandFileList(*files ?: arrayOf())
            if (expandFileList.isEmpty()) {
                return false
            }
        } catch (e: Exception) {
            log.error("Error checking if action is enabled", e)
            return false
        }
        return super.isEnabled(event)
    }

    override fun handle(e: AnActionEvent) {
        try {
            val root = getRoot(e) ?: throw RuntimeException("No file or folder selected")
            val virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(e.dataContext)
            val initialFiles =
                FileSelectionUtils.expandFileList(*virtualFiles?.map { it.toFile }?.toTypedArray() ?: arrayOf()).map {
                    it.toPath().relativeTo(root)
                }.toSet()
            val session = Session.newGlobalID()
            SessionProxyServer.metadataStorage.setSessionName(
                null,
                session,
                "${getActionName()} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
            )
            SessionProxyServer.agents[session] = PatchChatManager(
                session = session,
                model = AppSettingsState.instance.smartChatClient,
                parsingModel = AppSettingsState.instance.fastChatClient,
                root = root.toFile(),
                files = initialFiles,
                showLineNumbers = showLineNumbers
            )
            ApplicationServer.appInfoMap[session] = AppInfoData(
                applicationName = "Code Chat",
                inputCnt = 0,
                stickyInput = true,
                loadImages = false,
                showMenubar = false
            )
            launchBrowser(session.toString(), com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                AppSettingsState.instance.listeningEndpoint,
                AppSettingsState.instance.listeningPort
            ).server.uri)
        } catch (e: Exception) {

            log.error("Error in MultiDiffChatAction", e)
            UITools.showErrorDialog(e.message ?: "", "Error")
        }
    }

    protected open fun getActionName(): String =
        if (showLineNumbers) "MultiDiffChatWithLineNumbers" else "MultiDiffChat"

    private fun getRoot(event: AnActionEvent): Path? {
        val folder = event.getSelectedFolder()
        return if (null != folder) {
            folder.toFile.toPath()
        } else {
            getModuleRootForFile(event.getSelectedFile()?.parent?.toFile ?: return null).toPath()
        }
    }

    private fun launchBrowser(session: String, uri: URI) {
        Thread {
            Thread.sleep(500)
            try {
                val uri = uri.resolve("/#$session")
                BaseAction.log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    inner class PatchChatManager(
        session: Session,
        model: ChatInterface,
        parsingModel: ChatInterface,
        val root: File,
        private val files: Set<Path>,
        private val showLineNumbers: Boolean = false
    ) : ChatSocketManager(
        session = session,
        smartModel = model,
        fastModel = parsingModel,
        systemPrompt = "",
        applicationClass = ApplicationServer::class.java,
        storage = ApplicationServices.fileApplicationServices().dataStorageFactory,
        budget = 2.0,
    ) {
        override val systemPrompt: String
            get() = """
        You are a helpful AI that helps people with coding.
        You will be answering questions about the following code:
        ${codeSummary()}
        ${if (showLineNumbers) "\nNote: Line numbers are shown at the beginning of each line in the format 'NUMBER | CODE'. These are for reference only and should not be included in any patches or code modifications.\n" else ""}
        ${AppSettingsState.instance.processor.patchFormatPrompt}
      """.trimIndent()

        private fun getCodeFiles(): Set<Path> {
            if (!root.exists()) {
                log.warn("Root directory does not exist: $root")
                return emptySet()
            }
            return files.filter { path ->
                val file = root.toPath().resolve(path).toFile()
                val exists = file.exists()
                if (!exists) log.warn("File does not exist: $file")
                exists
            }.toSet()
        }

        private fun codeSummary(): String {
            return getCodeFiles().associateWith { root.toPath().resolve(it).toFile().readText(Charsets.UTF_8) }
                .entries.joinToString("\n\n") { (path, code) ->
                    val extension = path.toString().split('.').lastOrNull()
                    if (showLineNumbers) {
                        val lines = code.lines()
                        val lineNumberWidth = lines.size.toString().length
                        val numberedLines = lines.mapIndexed { index, line ->
                            String.format("%${lineNumberWidth}d | %s", index + 1, line)
                        }.joinToString("\n")
                        "# $path\n```$extension\n$numberedLines\n```"
                    } else {
                        "# $path\n```$extension\n$code\n```"
                    }
                }
        }

        override fun renderResponse(response: String, task: SessionTask) = renderMarkdown(response, tabs=true) { html ->
            AddApplyFileDiffLinks.instrumentFileDiffs(
                this,
                root = root.toPath(),
                response = html,
                handle = { newCodeMap ->
                    newCodeMap.forEach { (path, newCode) ->
                        task.complete("<a href='${"fileIndex/$sessionId/$path"}'>$path</a> Updated")
                    }
                },
                defaultFile = if (files.size == 1) files.first().let {
                    root.toPath().resolve(it).toFile().absolutePath
                } else null,
                processor = AppSettingsState.instance.processor,
            )
        }

        override fun respond(
            task: SessionTask,
            userMessage: String,
            currentChatMessages: List<ModelSchema.ChatMessage>,
            transcriptStream: OutputStream?
        ): String {
            val codex = GPT4Tokenizer()
            task.verbose((getCodeFiles().joinToString("\n") { path ->
                "* $path - ${codex.estimateTokenCount(root.resolve(path.toFile()).readText())} tokens"
            }).renderMarkdown())
            val settings = Settings()
            return super.respond(task, userMessage, currentChatMessages, transcriptStream)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ModifyFilesAction::class.java)
    }
}

```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/chat/MultiCodeChatAction.kt

```
package cognotik.actions.chat

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.vfs.VirtualFile
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat

/**
 * Action that enables multi-file code chat functionality.
 * Allows users to select multiple files and discuss them with an AI assistant.
 * Supports code modifications through patch application.
 *
 * @see BaseAction
 */

class MultiCodeChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        val root = getRoot(event) ?: return
        val codeFiles =
            getFiles(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext) ?: arrayOf(), root).toMutableSet()

        try {
            UITools.runAsync(event.project, "Initializing Chat", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up chat session..."
                val session = Session.newGlobalID()
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.agents[session] = CodeChatManager(
                    session = session,
                    model = AppSettingsState.instance.smartChatClient,
                    parsingModel = AppSettingsState.instance.fastChatClient,
                    root = root.toFile(),
                    codeFiles = codeFiles
                )
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Code Chat",
                    inputCnt = 0,
                    stickyInput = true,
                    loadImages = false,
                    showMenubar = false
                )
                Thread {
                    Thread.sleep(500)
                    try {
                        val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                            AppSettingsState.instance.listeningEndpoint,
                            AppSettingsState.instance.listeningPort
                        ).server.uri.resolve("/#${session.toString()}")
                        BaseAction.log.info("Opening browser to $uri")
                        BrowseUtil.browse(uri)
                    } catch (e: Throwable) {
                        log.warn("Error opening browser", e)
                    }
                }.start()
            }
        } catch (e: Throwable) {
            UITools.error(log, "Failed to initialize chat session", e)
        }
    }

    private fun getRoot(event: AnActionEvent): Path? {
        val folder = event.getSelectedFolder()
        return if (null != folder) {
            folder.toFile.toPath()
        } else {
            getModuleRootForFile(event.getSelectedFile()?.parent?.toFile ?: return null).toPath()
        }
    }

    inner class CodeChatManager(
        session: Session,
        model: ChatInterface,
        parsingModel: ChatInterface,
        val root: File,
        private val codeFiles: Set<Path>
    ) : ChatSocketManager(
        session = session,
        smartModel = model,
        fastModel = parsingModel,
        systemPrompt = "",
        applicationClass = ApplicationServer::class.java,
        storage = ApplicationServices.fileApplicationServices().dataStorageFactory,
        budget = 2.0,
    ) {

        override val systemPrompt: String
            get() = """
        You are a helpful AI that helps people with coding.

        You will be answering questions about the following code:
        ${codeSummary()}
      """.trimIndent()

        private fun codeSummary(): String {
            return codeFiles.mapNotNull { path ->
                val file = root.toPath().resolve(path).toFile()
                val exists = file.exists()
                if (!exists) log.warn("File does not exist: $file")
                if (!exists) return@mapNotNull null

                val content = try {
                    readFileContent(file)
                } catch (e: Exception) {
                    log.warn("Failed to read file: $file", e)
                    return@mapNotNull null
                }
                path to content
            }.joinToString("\n\n") { (path, content) ->
                val extension = path.toString().split('.').lastOrNull()?.let { it }
                "# $path\n```$extension\n$content\n```"
            }
        }

        override fun renderResponse(response: String, task: SessionTask) = """<div>${
            renderMarkdown(response, tabs=true) { html ->
                AddApplyFileDiffLinks.instrumentFileDiffs(
                    this,
                    root = root.toPath(),
                    response = html,
                    handle = { newCodeMap ->
                        newCodeMap.forEach { (path, newCode) ->
                            task.complete("<a href='${"fileIndex/$sessionId/$path"}'>$path</a> Updated")
                        }
                    },
                    processor = AppSettingsState.instance.processor,
                )
            }
        }</div>"""

        override fun respond(
            task: SessionTask,
            userMessage: String,
            currentChatMessages: List<ModelSchema.ChatMessage>,
            transcriptStream: OutputStream?
        ): String {
            val codex = GPT4Tokenizer()
            task.verbose((codeFiles.mapNotNull { path ->
                val file = root.resolve(path.toFile())
                if (!file.exists()) {
                    log.warn("File does not exist: $file")
                    return@mapNotNull null
                }

                val content = try {
                    readFileContent(file)
                } catch (e: Exception) {
                    log.warn("Failed to read file: $file", e)
                    return@mapNotNull null
                }

                "* $path - ${codex.estimateTokenCount(content)} tokens"
            }.joinToString("\n")).renderMarkdown())
            return super.respond(task, userMessage, currentChatMessages, transcriptStream)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MultiCodeChatAction::class.java)

        fun getFiles(
            virtualFiles: Array<out VirtualFile>?,
            root: Path
        ): Set<Path> = virtualFiles?.filter { file ->
            // Include all files that can be read by DocumentReader or are code files
            !file.name.startsWith(".") && (file.isDirectory || isSupportedFile(file))
        }?.flatMap { file ->
            if (file.isDirectory && !file.name.startsWith(".")) {
                getFiles(file.children, root)
            } else {
                setOf(root.relativize(file.toNioPath()))
            }
        }?.toSet() ?: emptySet()

        fun isSupportedFile(file: VirtualFile): Boolean {
            val name = file.name.lowercase()
            return when {
                name.endsWith(".pdf") ||
                        name.endsWith(".docx") || name.endsWith(".doc") ||
                        name.endsWith(".xlsx") || name.endsWith(".xls") ||
                        name.endsWith(".pptx") || name.endsWith(".ppt") ||
                        name.endsWith(".odt") ||
                        name.endsWith(".rtf") ||
                        name.endsWith(".html") || name.endsWith(".htm") ||
                        name.endsWith(".eml") -> true

                file.inputStream.use { it.isBinary } -> true
                else -> false
            }
        }

        fun readFileContent(file: File): String {
            return try {
                file.getDocumentReader().use { reader ->
                    reader.getText()
                }
            } catch (e: Exception) {
                log.debug("Failed to read as document, falling back to text: ${file.name}", e)
                // Fallback to reading as plain text
                file.readText(Charsets.UTF_8)
            }
        }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/chat/SmartChatAction.kt

```
package cognotik.actions.chat

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.SmartChatSocketManager
import java.text.SimpleDateFormat

/**
 * Smart Chat Action that provides enhanced chat functionality with:
 * - Automatic history summarization when conversation gets too long
 * - Query elevation from fast model to smart model for complex queries
 */
class SmartChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    private val systemPrompt = """
        You are a helpful AI assistant with expertise in software development, coding, and technical problem-solving.
        You provide clear, accurate, and well-structured responses.
        When discussing code, you explain your reasoning and suggest best practices.
    """.trimIndent()

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return

        try {
            UITools.runAsync(project, "Initializing Smart Chat", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up smart chat session..."

                val session = Session.newGlobalID()
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "Smart Chat @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.agents[session] = SmartChatSocketManager(
                    session = session,
                    smartModel = AppSettingsState.instance.smartChatClient,
                    fastModel = AppSettingsState.instance.fastChatClient,
                    systemPrompt = systemPrompt,
                    applicationClass = ApplicationServer::class.java,
                    storage = ApplicationServices.fileApplicationServices().dataStorageFactory,
                    budget = 2.0,
                    maxHistoryTokens = 4000,
                    targetSummaryTokens = 1000,
                    preserveRecentMessages = 4
                )
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Smart Chat",
                    inputCnt = 0,
                    stickyInput = true,
                    loadImages = false,
                    showMenubar = false
                )

                val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.uri.resolve("/#$session")
                ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        BaseAction.log.info("Opening browser to $uri")
                        browse(uri)
                    } catch (e: Throwable) {
                        UITools.error(log, "Failed to open browser", e)
                    }
                }
            }
        } catch (e: Throwable) {
            log.warn("Error opening browser", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmartChatAction::class.java)
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/chat/SmartCodeChatAction.kt

```
package cognotik.actions.chat

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.SmartChatSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.OutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat

/**
 * Smart Code Chat Action that provides enhanced multi-file code chat with:
 * - Automatic history summarization when conversation gets too long
 * - Query elevation from fast model to smart model for complex queries
 * - Support for code modifications through patch application
 */
class SmartCodeChatAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        val root = getRoot(event) ?: return
        val codeFiles =
            MultiCodeChatAction.getFiles(
                PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext) ?: arrayOf(),
                root
            ).toMutableSet()

        try {
            UITools.runAsync(event.project, "Initializing Smart Code Chat", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up smart code chat session..."
                val session = Session.newGlobalID()
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "Smart Code Chat @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.agents[session] = SmartCodeChatManager(
                    session = session,
                    model = AppSettingsState.instance.smartChatClient,
                    parsingModel = AppSettingsState.instance.fastChatClient,
                    root = root.toFile(),
                    codeFiles = codeFiles
                )
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Smart Code Chat",
                    inputCnt = 0,
                    stickyInput = true,
                    loadImages = false,
                    showMenubar = false
                )
                Thread {
                    Thread.sleep(500)
                    try {
                        val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                            AppSettingsState.instance.listeningEndpoint,
                            AppSettingsState.instance.listeningPort
                        ).server.uri.resolve("/#${session.toString()}")
                        BaseAction.log.info("Opening browser to $uri")
                        BrowseUtil.browse(uri)
                    } catch (e: Throwable) {
                        log.warn("Error opening browser", e)
                    }
                }.start()
            }
        } catch (e: Throwable) {
            UITools.error(log, "Failed to initialize smart code chat session", e)
        }
    }

    private fun getRoot(event: AnActionEvent): Path? {
        val folder = event.getSelectedFolder()
        return if (null != folder) {
            folder.toFile.toPath()
        } else {
            getModuleRootForFile(event.getSelectedFile()?.parent?.toFile ?: return null).toPath()
        }
    }

    inner class SmartCodeChatManager(
        session: Session,
        model: ChatInterface,
        parsingModel: ChatInterface,
        val root: File,
        private val codeFiles: Set<Path>
    ) : SmartChatSocketManager(
        session = session,
        smartModel = model,
        fastModel = parsingModel,
        systemPrompt = "",
        applicationClass = ApplicationServer::class.java,
        storage = ApplicationServices.fileApplicationServices().dataStorageFactory,
        budget = 2.0,
        maxHistoryTokens = 6000,
        targetSummaryTokens = 1500,
        preserveRecentMessages = 4
    ) {

        override val systemPrompt: String
            get() = """
        You are a helpful AI that helps people with coding.
        You will be answering questions about the following code:
        ${codeSummary()}
      """.trimIndent()

        private fun codeSummary(): String {
            return codeFiles.mapNotNull { path ->
                val file = root.toPath().resolve(path).toFile()
                val exists = file.exists()
                if (!exists) log.warn("File does not exist: $file")
                if (!exists) return@mapNotNull null

                val content = try {
                    MultiCodeChatAction.readFileContent(file)
                } catch (e: Exception) {
                    log.warn("Failed to read file: $file", e)
                    return@mapNotNull null
                }
                path to content
            }.joinToString("\n\n") { (path, content) ->
                val extension = path.toString().split('.').lastOrNull()?.let { it }
                "# $path\n```$extension\n$content\n```"
            }
        }

        override fun renderResponse(response: String, task: SessionTask) = """<div>${
            renderMarkdown(response, tabs=true) { html ->
                AddApplyFileDiffLinks.instrumentFileDiffs(
                    this,
                    root = root.toPath(),
                    response = html,
                    handle = { newCodeMap ->
                        newCodeMap.forEach { (path, newCode) ->
                            task.complete("<a href='${"fileIndex/$sessionId/$path"}'>$path</a> Updated")
                        }
                    },
                    processor = AppSettingsState.instance.processor,
                )
            }
        }</div>"""

        override fun respond(
            task: SessionTask,
            userMessage: String,
            currentChatMessages: List<ModelSchema.ChatMessage>,
            transcriptStream: OutputStream?
        ): String {
            val codex = GPT4Tokenizer()
            task.verbose((codeFiles.mapNotNull { path ->
                val file = root.resolve(path.toFile())
                if (!file.exists()) {
                    log.warn("File does not exist: $file")
                    return@mapNotNull null
                }

                val content = try {
                    MultiCodeChatAction.readFileContent(file)
                } catch (e: Exception) {
                    log.warn("Failed to read file: $file", e)
                    return@mapNotNull null
                }

                "* $path - ${codex.estimateTokenCount(content)} tokens"
            }.joinToString("\n")).renderMarkdown())
            return super.respond(task, userMessage, currentChatMessages, transcriptStream)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmartCodeChatAction::class.java)
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

Here is the new `README.md` file for the chat actions package.

### intellij/src/main/kotlin/cognotik/actions/chat/README.md
```markdown
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
```

### Summary of Changes
- Created a new `README.md` in `intellij/src/main/kotlin/cognotik/actions/chat/`.
- Provided a structured overview of all chat-related actions found in the directory.
- Categorized actions into "Core", "Advanced", and "Specialized" for better discoverability.
- Included brief technical implementation details to assist developers.
- Linked to the respective Kotlin source files for easy navigation.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/chat/README.md'>intellij/src/main/kotlin/cognotik/actions/chat/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/chat/README.md'>intellij/src/main/kotlin/cognotik/actions/chat/README.md</a> Updated
