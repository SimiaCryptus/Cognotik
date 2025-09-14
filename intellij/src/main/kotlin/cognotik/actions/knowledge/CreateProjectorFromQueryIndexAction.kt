package cognotik.actions.knowledge

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.apps.parse.DocumentRecord
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.IdeaOpenAIClient
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationSocketManager
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.util.LoggerFactory
import java.text.SimpleDateFormat

class CreateProjectorFromQueryIndexAction : BaseAction() {
    data class ProjectorConfig(
        val sessionId: Session = Session.newGlobalID(),
        val applicationName: String = "Projector"
    )

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val selectedFiles = event.getSelectedFiles()
        val processableFiles = selectedFiles.flatMap { file ->
            when {
                file.isDirectory -> file.findRecursively { it.name.endsWith(".index.data") }
                file.name.endsWith(".index.data") -> listOf(file)
                else -> emptyList()
            }
        }
        return processableFiles.isNotEmpty()
    }

    override fun handle(e: AnActionEvent) {
        val processableFiles = getProcessableFiles(e)
        if (processableFiles.isEmpty()) {
            UITools.showErrorDialog("Please select a valid query index file (.index.data).", "Invalid Selection")
            return
        }

        UITools.runAsync(e.project, "Creating Projector", true) { indicator ->
            try {
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                indicator.text = "Reading records..."

                val records = processableFiles.flatMap { DocumentRecord.readBinary(it.path) }
                val config = ProjectorConfig()
                indicator.text = "Setting up projector..."

                ApplicationServer.appInfoMap[config.sessionId] = AppInfoData(
                    applicationName = config.applicationName,
                    inputCnt = 0,
                    stickyInput = true,
                    loadImages = false,
                    showMenubar = false
                )

                SessionProxyServer.chats[config.sessionId] = object : ApplicationServer(
                    applicationName = config.applicationName,
                    path = "/projector",
                    showMenubar = false,
                ) {
                    override fun newSession(
                        user: User?,
                        session: Session
                    ): SocketManager {
                        val socketManager = super.newSession(user, session)
                        val ui = (socketManager as ApplicationSocketManager).applicationInterface
                        val projector = TensorflowProjector(IdeaOpenAIClient.instance, dataStorage, session, ui, null)
                        val result = projector.writeTensorflowEmbeddingProjectorHtmlFromRecords(records)
                        val task = ui.newTask(true)
                        task.complete(result)
                        return socketManager
                    }
                }
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    config.sessionId,
                    "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )

                indicator.fraction = 1.0
                indicator.text = "Opening browser..."

                val server = CognotikAppServer.getServer(e.project)

                ApplicationManager.getApplication().executeOnPooledThread {
                    Thread.sleep(500)
                    try {
                        val uri = server.server.uri.resolve("/#${config.sessionId}")
                        BaseAction.log.info("Opening browser to $uri")
                        browse(uri)
                    } catch (e: Throwable) {
                        log.warn("Error opening browser", e)
                    }
                }

            } catch (ex: Exception) {
                log.error("Error during projector creation", ex)
                UITools.showErrorDialog("Error during projector creation: ${ex.message}", "Projector Creation Failed")
            }
        }
    }

    private fun getProcessableFiles(e: AnActionEvent) = e.getSelectedFiles().flatMap { file ->
        when {
            file.isDirectory -> file.findRecursively { it.name.endsWith(".index.data") }
            file.name.endsWith(".index.data") -> listOf(file)
            else -> emptyList()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CreateProjectorFromQueryIndexAction::class.java)
    }
}