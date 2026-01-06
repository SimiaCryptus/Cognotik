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
import com.simiacryptus.cognotik.input.getDocumentReader
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
            renderMarkdown(response) { html ->
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