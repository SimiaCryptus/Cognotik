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
