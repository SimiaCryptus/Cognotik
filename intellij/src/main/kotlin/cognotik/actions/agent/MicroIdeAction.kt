package cognotik.actions.agent

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.apps.SessionProxyServer
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.fileserver.handler.FsApiHandler
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.servlet.action.DocOpsFsActions
import com.simiacryptus.cognotik.webui.servlet.action.ExtractUtilsFsAction
import com.simiacryptus.cognotik.webui.servlet.action.ModelSelection
import com.simiacryptus.cognotik.webui.servlet.action.ModelSelectionActions
import com.simiacryptus.cognotik.webui.servlet.action.ModifyFilesFsAction
import com.simiacryptus.cognotik.webui.servlet.action.SessionFsRoots
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.net.URI
import java.text.SimpleDateFormat

class MicroIdeAction : BaseAction() {
    init {
        @Suppress("SENSELESS_COMPARISON") require(FsApiHandler.javaClass != null) { "FsApiHandler class not found" }
        ModelSelection.install { localUser }
        ModelSelectionActions.install()
        val localName = AppSettingsState.instance.listeningEndpoint
        val port = AppSettingsState.instance.listeningPort
        ModifyFilesFsAction.install(
            ModifyFilesFsAction.Config(
                root = SessionFsRoots::rootOf,
                user = SessionFsRoots::userOf,
                chatUri = {
                    URI("http://$localName:$port")
                },
            )
        )
//        DocOpsFsActions.install(
//            DocOpsFsActions.Config(
//                root = SessionFsRoots::rootOf,
//                user = SessionFsRoots::userOf,
//            )
//        )
    }
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
//                    ExtractUtilsFsAction.install(
//                        ExtractUtilsFsAction.Config { selectedFile.toFile }
//                    )
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
    )

    companion object {
        private val log = getLogger(MicroIdeAction::class.java)
        val root: File get() = File(AppSettingsState.pluginHome, "code_chat")
    }
}