package cognotik.actions.chat

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.ui.patch.DiffInstrumentor
import com.simiacryptus.cognotik.ui.patch.SessionRenderer
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
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
                    fastModel = AppSettingsState.instance.fastChatClient,
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
        fastModel: ChatInterface,
        val root: File,
        private val codeFiles: Set<Path>
    ) : SmartChatSocketManager(
      session = session,
      smartModel = model,
      fastModel = fastModel,
      systemPrompt = "",
      applicationClass = ApplicationServer::class.java,
      budget = 2.0,
      maxHistoryTokens = 6000,
      targetSummaryTokens = 1500,
      owner = localUser
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

        override fun renderResponse(response: String, task: SessionTask) =
          "<div>" + renderMarkdown(response, tabs = true) { html ->
            DiffInstrumentor(
              AppSettingsState.instance.processor,
              SessionRenderer(task),
            ).instrument(
              root = root.toPath(),
              response = html,
              handle = { newCodeMap: Map<Path, String> ->
                newCodeMap.forEach { (path, newCode) ->
                  task.complete("<a href='${"fileIndex/$sessionId/$path"}'>$path</a> Updated")
                }
              },
              resolver = ::resolveToRelativePath,
            )
          } + "</div>"

        override fun respond(
            task: SessionTask,
            userMessage: String,
            currentChatMessages: List<ModelSchema.ChatMessage>,
            transcriptStream: OutputStream?
        ): String {
            task.verbose((codeFiles.mapNotNull { path ->
                val file = root.resolve(path.toFile())
                if (!file.exists()) {
                    log.warn("File does not exist: $file")
                    return@mapNotNull null
                }
                "* $path - ${file.length()} bytes"
            }.joinToString("\n")).renderMarkdown())
            return super.respond(task, userMessage, currentChatMessages, transcriptStream)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SmartCodeChatAction::class.java)
    }
}