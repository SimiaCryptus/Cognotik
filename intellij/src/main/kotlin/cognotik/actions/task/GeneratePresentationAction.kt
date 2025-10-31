package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.apps.general.SingleTaskApp
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.file.GeneratePresentationTask
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

class GeneratePresentationAction : BaseAction() {

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
  override fun isEnabled(event: AnActionEvent): Boolean {
    if (!super.isEnabled(event)) return false
    if (event.getSelectedFiles().isEmpty() && event.getSelectedFolder() == null) return false
    return true
  }

  override fun handle(e: AnActionEvent) {
    val root = getProjectRoot(e) ?: return
    val relatedFiles = getFiles(e)
    val dialog = GeneratePresentationTaskDialog(
      e.project, root, relatedFiles
    )

    if (dialog.showAndGet()) {
      try {
        val taskConfig = dialog.getTaskConfig()
        val orchestrationConfig = dialog.getOrchestrationConfig()

        UITools.runAsync(e.project, "Initializing Presentation Generation Task", true) { progress ->
          initializeTask(e, progress, orchestrationConfig, taskConfig, root)
        }
      } catch (ex: Exception) {
        log.error("Failed to initialize presentation generation task", ex)
        UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
      }
    }
  }

  private fun initializeTask(
    e: AnActionEvent,
    progress: ProgressIndicator,
    orchestrationConfig: OrchestrationConfig,
    taskConfig: GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData,
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
    session: Session, orchestrationConfig: OrchestrationConfig, taskConfig: GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData, root: File
  ) {
    val app = object : SingleTaskApp(
      applicationName = "Presentation Generation Task",
      path = "/generatePresentationTask",
      showMenubar = false,
      taskType = GeneratePresentationTask.GeneratePresentation,
      taskConfig = taskConfig,
      instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
    ) {
      override fun instance(model: ApiChatModel) = model.instance() ?: throw IllegalStateException("Model or Provider not set")
    }

    app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
    SessionProxyServer.chats[session] = app
    ApplicationServer.appInfoMap[session] = AppInfoData(
      applicationName = "Presentation Generation Task", inputCnt = 0, stickyInput = false, showMenubar = false
    )
    SessionProxyServer.metadataStorage.setSessionName(
      null, session, "Presentation Generation @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
    )
  }

  private fun getProjectRoot(e: AnActionEvent): File? {
    val folder = e.getSelectedFolder()
    return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
      getModuleRootForFile(file)
    }
  }
}