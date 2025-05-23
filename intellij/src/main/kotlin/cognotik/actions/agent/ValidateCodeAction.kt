package cognotik.actions.agent

import cognotik.actions.BaseAction
import cognotik.actions.SessionProxyServer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.simiacryptus.cognotik.AppServer
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.apps.general.PatchApp
import com.simiacryptus.cognotik.apps.general.ValidationPatchApp
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.models.chatModel
import org.slf4j.LoggerFactory
import java.text.SimpleDateFormat

/**
 * Action that provides code validation and syntax checking through AI assistance
 */
class ValidateCodeAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        try {
            UITools.runAsync(event.project, "Initializing Code Validation", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Setting up validation..."

                val files = UITools.getSelectedFiles(event)
                val folders = UITools.getSelectedFolders(event)
                if (files.isEmpty() && folders.isEmpty()) {
                    UITools.showErrorDialog("Please select files or folders to validate", "No Selection")
                    return@runAsync
                }

                val root = folders.firstOrNull()?.toFile?.toPath()
                    ?: files.firstOrNull()?.parent?.toFile?.toPath()
                    ?: event.project?.basePath?.let { java.io.File(it).toPath() }
                    ?: throw IllegalStateException("Could not determine project root")

                val settings = PatchApp.Settings(
                    commands = listOf(),
                    autoFix = true,
                    maxRetries = 1,
                    includeLineNumbers = false
                )

                val session = Session.newGlobalID()
                val patchApp = ValidationPatchApp(
                    root = root.toFile(),
                    settings = settings,
                    api = api,
                    files = files.map { it.toFile }.toTypedArray(),
                    model = AppSettingsState.instance.smartModel.chatModel(),
                    parsingModel = AppSettingsState.instance.fastModel.chatModel()
                )

                SessionProxyServer.chats[session] = patchApp
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Code Validator",
                    singleInput = true,
                    stickyInput = false,
                    loadImages = false,
                    showMenubar = false
                )

                val dateFormat = SimpleDateFormat("HH:mm:ss")
                val sessionName = "${javaClass.simpleName} @ ${dateFormat.format(System.currentTimeMillis())}"
                SessionProxyServer.metadataStorage.setSessionName(null, session, sessionName)

                val server = AppServer.getServer(event.project)
                Thread {
                    Thread.sleep(500)
                    try {
                        val uri = server.server.uri.resolve("/#$session")
                        log.info("Opening browser to $uri")
                        browse(uri)
                    } catch (e: Throwable) {
                        log.warn("Error opening browser", e)
                        UITools.showErrorDialog("Failed to open browser: ${e.message}", "Error")
                    }
                }.start()
            }
        } catch (e: Throwable) {
            log.error("Failed to execute code validation", e)
            UITools.showErrorDialog("Failed to execute code validation: ${e.message}", "Error")
        }
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (event.project == null) return false
        val hasSelection = UITools.getSelectedFiles(event).isNotEmpty() ||
                UITools.getSelectedFolders(event).isNotEmpty()
        return hasSelection
    }

    companion object {
        private val log = LoggerFactory.getLogger(ValidateCodeAction::class.java)
    }
}