package cognotik.actions.agent

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.text.patch.PatchProcessor
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.text.ui.DiffInstrumentor
import com.simiacryptus.cognotik.ui.patch.SessionRenderer
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.UUID
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

class MicroIdeAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    val path = "/μIDE"
    override fun isEnabled(event: AnActionEvent): Boolean {
        event.getSelectedFolder() ?: return false
        return true
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        UITools.runAsync(project, "Initializing μIDE", true) { progress ->
            progress.isIndeterminate = true
            try {
                val session = Session.newUserID()
                val selectedFile = e.getSelectedFolder()
                if (null != selectedFile) {
                    DataStorage.userPaths[session] = selectedFile.toFile
                }
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.chats[session] = MicroIdeApp(event = e)
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Cognotik μIDE",
                    inputCnt = 0,
                    stickyInput = false,
                    loadImages = false,
                    showMenubar = true
                )

                ApplicationManager.getApplication().invokeLater {
                    progress.text = "Opening browser..."
                    val uri = CognotikAppServer.getServer(
                        AppSettingsState.instance.listeningEndpoint,
                        AppSettingsState.instance.listeningPort
                    ).server.uri.resolve("/ui/?session=${session}#/")
                    BaseAction.log.info("Opening browser to $uri")
                    browse(uri)
                }
            } catch (e: Throwable) {
                UITools.error(log, "Failed to initialize Auto Dev Assistant", e)
            }
        }
    }

    open class MicroIdeApp(
        applicationName: String = "Cognotik μIDE",
        val event: AnActionEvent,
    ) : ApplicationServer(
        applicationName = applicationName,
        path = "/μIDE",
        showMenubar = false,
    ) {
        companion object {
        }
    }


    companion object {
        private val log = getLogger(MicroIdeAction::class.java)
        val root: File get() = File(AppSettingsState.pluginHome, "code_chat")



    }
}