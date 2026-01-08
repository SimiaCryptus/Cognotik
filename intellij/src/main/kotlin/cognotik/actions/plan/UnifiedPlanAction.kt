package cognotik.actions.plan

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.simiacryptus.cognotik.apps.UnifiedPlanApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

open class UnifiedPlanAction(
    private val useProjectRoot: Boolean = true
) : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val root: File = if (useProjectRoot) {
            getProjectRoot(e) ?: createTemporaryDirectory(e.project)
        } else {
            createTemporaryDirectory(e.project)
        }
        OrchestrationConfig.instanceFn =
            { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        val dialog = PlanConfigDialog(
            e.project,
            OrchestrationConfig(
                "Init",
                defaultSmartModel = AppSettingsState.instance.smartModel
                    ?: throw IllegalStateException("Smart model not configured"),
                defaultFastModel = AppSettingsState.instance.fastModel
                    ?: throw IllegalStateException("Fast model not configured"),
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                temperature = AppSettingsState.instance.temperature.coerceIn(0.0, 1.0),
                workingDir = root.absolutePath,
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
        val root = File(orchestrationConfig.workingDir)
        progress.text = "Processing files..."
        setupChatSession(
            session,
            root,
            orchestrationConfig.copy(
                sessionId = session.sessionId
            )
        )
        progress.text = "Starting server..."
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

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    private fun createTemporaryDirectory(project: Project?): File {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
        val scratchesDir = getScratchesDirectory()
        val tempDir = File(scratchesDir, "cognotik/$timestamp")
        tempDir.mkdirs()
        log.info("Created temporary directory: ${tempDir.absolutePath}")
        return tempDir
    }

    private fun getScratchesDirectory(): File {
        val useSystemPath = AppSettingsState.instance.useScratchesSystemPath
        val basePath = if (useSystemPath) {
            System.getProperty("idea.system.path")
        } else {
            System.getProperty("idea.config.path")
        }
        return if (basePath != null) {
            File(basePath, "scratches")
        } else {
            // Fallback to user home if properties are not set
            File(System.getProperty("user.home"), ".cognotik/scratches")
        }
    }


    private fun setupChatSession(
        session: Session,
        root: File,
        orchestrationConfig: OrchestrationConfig
    ) {
        DataStorage.sessionPaths[session] = DataStorage.sessionPaths[session] ?: root
        val app = object : UnifiedPlanApp(
            applicationName = "Unified Planning",
            path = "/unifiedPlan",
            showMenubar = false
        ) {
            override fun instance(model: ApiChatModel) = model.instance()
                ?: throw IllegalStateException("Model or Provider not set")
        }
        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Cognotik",
            inputCnt = when (orchestrationConfig.cognitiveMode) {
                CognitiveModeType.Chat -> 0
                else -> 4
            },
            stickyInput = app.stickyInput,
            showMenubar = app.showMenubar
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

}