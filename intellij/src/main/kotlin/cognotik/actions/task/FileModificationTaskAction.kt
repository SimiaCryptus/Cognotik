package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.apps.general.SingleTaskApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.util.getModuleRootForFile
import com.simiacryptus.cognotik.util.getSelectedFile
import com.simiacryptus.cognotik.util.getSelectedFolder
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileModificationTaskAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        
        val dialog = FileModificationTaskDialog(
            e.project,
            root
        )

        if (dialog.showAndGet()) {
            try {
                val taskConfig = dialog.getTaskConfig()
                val orchestrationConfig = dialog.getOrchestrationConfig()
                
                UITools.runAsync(e.project, "Initializing File Modification Task", true) { progress ->
                    initializeTask(e, progress, orchestrationConfig, taskConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize file modification task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        e: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: FileModificationTask.FileModificationTaskExecutionConfigData,
        root: File
    ) {
        progress.text = "Setting up session..."
        val session = Session.newGlobalID()
        
        DataStorage.sessionPaths[session] = root
        
        progress.text = "Starting server..."
        setupTaskSession(session, orchestrationConfig, taskConfig, root)
        
        Thread {
            Thread.sleep(500)
            try {
                val uri = CognotikAppServer.getServer().server.uri.resolve("/#$session")
                log.info("Opening browser to $uri")
                browse(uri)
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
        }.start()
    }

    private fun setupTaskSession(
        session: Session,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: FileModificationTask.FileModificationTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "File Modification Task",
            path = "/fileModificationTask",
            showMenubar = false,
            taskType = FileModification,
            taskConfig = taskConfig
        ) {
            override fun instance(model: ApiChatModel) = model.instance()
                ?: throw IllegalStateException("Model or Provider not set")
        }
        
        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "File Modification Task",
            inputCnt = 0,
            stickyInput = false,
            showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "File Modification @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }
}