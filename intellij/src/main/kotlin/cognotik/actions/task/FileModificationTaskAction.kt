package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import cognotik.actions.plan.PlanConfigDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.AbstractTask.TaskState
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.toApiChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.Dimension
import java.io.File
import java.text.SimpleDateFormat
import javax.swing.JComponent
import javax.swing.JSlider

class FileModificationTaskAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        val files = getFiles(e)

        val dialog = FileModificationTaskDialog(
            e.project,
            root,
            files
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
        val session = Session.newUserID()

        DataStorage.userPaths[session] = root

        progress.text = "Starting server..."
        setupTaskSession(session, orchestrationConfig.copy(sessionId = session.sessionId), taskConfig, root)

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

    private fun setupTaskSession(
        session: Session,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: FileModificationTask.FileModificationTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            path = "/fileModificationTask",
            applicationName = "File Modification Task",
            taskType = FileModification,
            instanceFn = { model, user -> model.instance() ?: throw IllegalStateException("Model or Provider not set") },
            message = "Execute task",
            user = orchestrationConfig.user
        ) {
            override fun instance(model: ApiChatModel) = model.instance()
                ?: throw IllegalStateException("Model or Provider not set")
        }

      app.getSettingsFile(session, AppSettingsState.localUser).writeText(orchestrationConfig.toJson())
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

    class FileModificationTaskDialog(
        project: Project?, private val root: File, val files: List<File>
    ) : DialogWrapper(project) {

        private val taskDescriptionArea = JBTextArea(5, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Describe what modifications should be made to the files"
        }

        private val filesField = JBTextField().apply {
            toolTipText = "Comma-separated list of file paths (relative to project root) to modify or create"
            text = files.joinToString(", ") { it.relativeTo(root).path }
        }

        private val relatedFilesField = JBTextField().apply {
            toolTipText = "Comma-separated list of related files to consider for context"
        }

        private val extractContentCheckbox = JBCheckBox("Extract content from non-text files", false).apply {
            toolTipText = "Extract text content from PDF, HTML, and other document formats"
        }

        private val includeGitDiffCheckbox = JBCheckBox("Include git diff with HEAD", false).apply {
            toolTipText = "Include git diff information to show recent changes"
        }

        private val visibleModelsCache by lazy { getVisibleModels() }

        private val modelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelId }.map { it.modelId }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelId
            toolTipText = "AI model to use for this task"
        }

        private val temperatureSlider = JSlider(0, 100, 70).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = javax.swing.JLabel("0.70")

        private val autoFixCheckbox = JBCheckBox("Auto-apply fixes", false).apply {
            toolTipText = "Automatically apply suggested changes without manual confirmation"
        }

        init {
            init()
            title = "Configure File Modification Task"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("Task Configuration") {
                row("Task Description:") {
                    scrollCell(taskDescriptionArea).align(Align.FILL).comment("Describe the modifications to be made")
                        .resizableColumn()
                }.resizableRow()

                row("Files to Modify:") {
                    cell(filesField).align(Align.FILL)
                        .comment("Comma-separated file paths (e.g., src/main.kt, src/utils.kt)")
                }

                row("Related Files:") {
                    cell(relatedFilesField).align(Align.FILL).comment("Additional files for context (optional)")
                }

                row {
                    cell(extractContentCheckbox)
                }

                row {
                    cell(includeGitDiffCheckbox)
                }
            }

            group("Model Settings") {
                row("Model:") {
                    cell(modelCombo).align(Align.FILL).comment("AI model to use for generating modifications")
                }

                row("Temperature:") {
                    cell(temperatureSlider).align(Align.FILL)
                        .comment("Higher values = more creative, lower = more focused")
                    cell(temperatureLabel)
                }

                row {
                    cell(autoFixCheckbox)
                }
            }
        }

        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
            if (taskDescriptionArea.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Task description is required", taskDescriptionArea)
            }

            if (filesField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("At least one file must be specified", filesField)
            }

            return null
        }

        fun getTaskConfig(): FileModificationTask.FileModificationTaskExecutionConfigData {
            val files = filesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val relatedFiles = relatedFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            return FileModificationTask.FileModificationTaskExecutionConfigData(
                task_description = taskDescriptionArea.text,
                related_files = relatedFiles,
                includeGitDiff = includeGitDiffCheckbox.isSelected,
                state = TaskState.Pending
            ).apply {
                main_file = files.first()
            }
        }

        fun getOrchestrationConfig(): OrchestrationConfig {
            val selectedModel = modelCombo.selectedItem as? String
            val model = selectedModel?.let { modelName ->
                visibleModelsCache.find { it.modelId == modelName }?.toApiChatModel(localUser)
            }

            return OrchestrationConfig(
                "Config",
                smartModel = (model ?: AppSettingsState.instance.smartModel)?.model?.modelId
                ?: throw IllegalStateException("No model configured"),
                fastModel = AppSettingsState.instance.fastModel?.model?.modelId
                    ?: throw IllegalStateException("Fast model not configured"),
                temperature = temperatureSlider.value / 100.0,
                autoFix = autoFixCheckbox.isSelected,
                workingDir = root.absolutePath,
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                taskSettings = mutableMapOf(
                    FileModificationTask.FileModification.name to TaskTypeConfig(task_type = FileModification.name)
                ),
                user = localUser
            )
        }

        private fun getVisibleModels() =
          ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(localUser).apis.flatMap { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.apiBase)?.filter { model ->
                    model.provider == apiData.provider && model.modelId?.isNotBlank() == true && PlanConfigDialog.isVisible(
                        model
                    )
                } ?: listOf()
            }.distinctBy { it.modelId }.sortedBy { "${it.provider?.name} - ${it.modelId}" }
    }

}


fun getFiles(e: AnActionEvent): List<File> {
    val selectedFiles = e.getSelectedFiles()
    val relatedFiles = if (selectedFiles.isEmpty()) {
        e.getSelectedFolder()?.toFile?.absoluteFile?.let {
            FileSelectionUtils.filteredWalk(it) { file ->
                when {
                    FileSelectionUtils.isLLMIgnored(file.toPath()) -> false
                    it.isDirectory -> true
                    else -> false
                }
            }
        } ?: emptyList()
    } else {
        selectedFiles.map { it.toFile }
    }
    return relatedFiles
}

