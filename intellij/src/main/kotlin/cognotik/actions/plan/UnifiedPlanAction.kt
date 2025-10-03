package cognotik.actions.plan

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.apps.general.UnifiedPlanApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.io.File
import java.text.SimpleDateFormat

class UnifiedPlanAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val root: String = e.getRoot()
        OrchestrationConfig.instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        val dialog = PlanConfigDialog(
            e.project,
            OrchestrationConfig(
                defaultModel = AppSettingsState.instance.smartModel
                    ?: throw IllegalStateException("Smart model not configured"),
                parsingModel = AppSettingsState.instance.fastModel
                    ?: throw IllegalStateException("Fast model not configured"),
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                temperature = AppSettingsState.instance.temperature.coerceIn(0.0, 1.0),
                env = mapOf(),
                workingDir = root,
            ),
        )

        if (dialog.showAndGet()) {
            try {
                val planSettings = dialog.settings
                UITools.runAsync(e.project, "Initializing Unified Plan", true) { progress ->
                    initializeChat(e, progress, planSettings)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize unified plan", ex)
                UITools.showError(e.project, "Failed to initialize unified plan: ${ex.message}")
            }
        }
    }

    private fun initializeChat(
        e: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig
    ) {
        progress.text = "Setting up session..."
        val session = Session.newGlobalID()
        val root = getProjectRoot(e) ?: throw RuntimeException("Could not determine project root")
        progress.text = "Processing files..."
        setupChatSession(
            session,
            root,
            orchestrationConfig
        )
        progress.text = "Starting server..."
        val server = CognotikAppServer.getServer(e.project)
        openBrowser(server, session.toString())
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    private fun setupChatSession(
        session: Session,
        root: File,
        orchestrationConfig: OrchestrationConfig
    ) {
        DataStorage.sessionPaths[session] = root
        val fastChatModel = (AppSettingsState.instance.fastModel
            ?: throw IllegalStateException("Fast model not configured"))
        val app = object : UnifiedPlanApp(
            applicationName = "Unified Planning",
            path = "/unifiedPlan",
            showMenubar = false
        ) {
            override fun instance(model: ApiChatModel) = model.instance()
                ?: throw IllegalStateException("Model or Provider not set")
        }
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Cognotik",
            inputCnt = app.inputCnt,
            stickyInput = app.stickyInput,
            showMenubar = app.showMenubar
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun openBrowser(server: CognotikAppServer, session: String) {
        Thread {
            Thread.sleep(500)
            try {
                val uri = server.server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }
}