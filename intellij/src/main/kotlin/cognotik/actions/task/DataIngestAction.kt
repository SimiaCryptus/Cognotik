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
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.tools.AbstractTask.TaskState
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.file.DataIngestTask
import com.simiacryptus.cognotik.plan.tools.toApiChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.Dimension
import java.io.File
import java.text.SimpleDateFormat
import javax.swing.JComponent
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class DataIngestAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        if (event.getSelectedFiles().isEmpty() && event.getSelectedFolder() == null) return false
        return true
    }

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        val files = getFiles(e)
        val dialog = DataIngestTaskDialog(e.project, root, files)

        if (dialog.showAndGet()) {
            try {
                val orchestrationConfig = dialog.getOrchestrationConfig()

                UITools.runAsync(e.project, "Initializing Data Ingestion Task", true) { progress ->
                    initializeTask(progress, orchestrationConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize Data Ingestion task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        root: File
    ) {
        progress.text = "Setting up session..."
        val session = Session.newGlobalID()

        DataStorage.sessionPaths[session] = root

        progress.text = "Starting server..."
        setupTaskSession(session, orchestrationConfig.copy(sessionId = session.sessionId))

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
        orchestrationConfig: OrchestrationConfig
    ) {
        val app = object : SingleTaskApp(
            path = "/dataIngestTask",
            applicationName = "Data Ingestion Task",
            taskType = DataIngestTask.DataIngest,
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") },
            message = "Execute task"
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Data Ingestion Task", inputCnt = 0, stickyInput = false, showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null, session, "Data Ingestion @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    class DataIngestTaskDialog(
        project: Project?,
        private val root: File,
        files: List<File>
    ) : DialogWrapper(project) {

        private val inputFilesField = JBTextField().apply {
            toolTipText = "Glob patterns for input files (e.g. **/*.log)"
            text = if (files.isNotEmpty()) files.joinToString(", ") { it.relativeTo(root).path } else "**/*.log"
        }

        private val sampleSizeSpinner = JSpinner(SpinnerNumberModel(1000, 100, 10000, 100)).apply {
            toolTipText = "Number of lines to sample for pattern discovery"
        }

        private val maxIterationsSpinner = JSpinner(SpinnerNumberModel(10, 1, 50, 1)).apply {
            toolTipText = "Maximum number of discovery iterations"
        }

        private val coverageThresholdSlider = JSlider(0, 100, 95).apply {
            addChangeListener {
                coverageLabel.text = "${value}%"
            }
        }
        private val coverageLabel = javax.swing.JLabel("95%")

        private val taskDescriptionArea = JBTextArea(4, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Optional description of the data or specific parsing requirements"
        }

        private val visibleModelsCache by lazy { getVisibleModels() }

        private val modelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "AI model to use for pattern discovery"
        }

        private val temperatureSlider = JSlider(0, 100, 10).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = javax.swing.JLabel("0.10")

        init {
            init()
            title = "Configure Data Ingestion Task"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("Data Configuration") {
                row("Input Files:") {
                    cell(inputFilesField)
                        .align(Align.FILL)
                        .comment("Glob patterns (e.g. **/*.log, logs/app-*.txt)")
                }

                row("Sample Size:") {
                    cell(sampleSizeSpinner)
                        .comment("Lines to sample for pattern discovery")
                }

                row("Max Iterations:") {
                    cell(maxIterationsSpinner)
                        .comment("Max discovery loops")
                }

                row("Coverage Threshold:") {
                    cell(coverageThresholdSlider)
                        .align(Align.FILL)
                        .comment("Stop when this % of sample is covered")
                    cell(coverageLabel)
                }

                row("Description:") {
                    scrollCell(taskDescriptionArea)
                        .align(Align.FILL)
                        .comment("Optional context about the data format")
                        .resizableColumn()
                }.resizableRow()
            }

            group("Model Settings") {
                row("Model:") {
                    cell(modelCombo)
                        .align(Align.FILL)
                        .comment("AI model for regex generation")
                }

                row("Temperature:") {
                    cell(temperatureSlider)
                        .align(Align.FILL)
                        .comment("Lower values recommended for precise regex generation")
                    cell(temperatureLabel)
                }
            }
        }

        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
            if (inputFilesField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Input files pattern is required", inputFilesField)
            }
            return null
        }

        fun getTaskConfig(): DataIngestTask.DataIngestTaskExecutionConfigData {
            val patterns = inputFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            return DataIngestTask.DataIngestTaskExecutionConfigData(
                input_files = patterns,
                sample_size = sampleSizeSpinner.value as Int,
                max_iterations = maxIterationsSpinner.value as Int,
                coverage_threshold = coverageThresholdSlider.value / 100.0,
                task_description = taskDescriptionArea.text.takeIf { it.isNotBlank() },
                state = TaskState.Pending
            )
        }

        fun getOrchestrationConfig(): OrchestrationConfig {
            val selectedModel = modelCombo.selectedItem as? String
            val model = selectedModel?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.toApiChatModel()
            }

            return OrchestrationConfig(
                "Config",
                defaultSmartModel = model ?: AppSettingsState.instance.smartModel
                ?: throw IllegalStateException("No model configured"),
                defaultFastModel = AppSettingsState.instance.fastModel
                    ?: throw IllegalStateException("Fast model not configured"),
                defaultImageModel = AppSettingsState.instance.imageChatModel ?: AppSettingsState.instance.smartModel
                ?: throw IllegalStateException("No image model configured"),
                temperature = temperatureSlider.value / 100.0,
                autoFix = false,
                workingDir = root.absolutePath,
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                )
            )
        }

        private fun getVisibleModels() =
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis.flatMap { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.filter { model ->
                  model.provider == apiData.provider && model.modelName.isNotBlank() && PlanConfigDialog.isVisible(model)
                } ?: listOf()
            }.distinctBy { it.modelName }.sortedBy { "${it.provider?.name} - ${it.modelName}" }
    }
}