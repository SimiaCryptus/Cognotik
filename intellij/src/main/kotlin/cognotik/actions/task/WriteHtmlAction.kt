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
import com.simiacryptus.cognotik.plan.tools.file.WriteHtmlTask
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
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

class WriteHtmlAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        if (event.getSelectedFiles().isEmpty() && event.getSelectedFolder() == null) return false
        return true
    }

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        val relatedFiles = getFiles(e)
        val dialog = WriteHtmlTaskDialog(
            e.project, root, relatedFiles
        )

        if (dialog.showAndGet()) {
            try {
                val orchestrationConfig = dialog.getOrchestrationConfig()

                UITools.runAsync(e.project, "Initializing HTML Generation Task", true) { progress ->
                    initializeTask(progress, orchestrationConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize HTML generation task", ex)
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
        val session = Session.newUserID()

        DataStorage.userPaths[session] = root

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
            path = "/writeHtmlTask",
            applicationName = "HTML Generation Task",
            taskType = WriteHtmlTask.WriteHtml,
            instanceFn = { model, user -> model.instance() ?: throw IllegalStateException("Model or Provider not set") },
            message = "Execute task",
            user = orchestrationConfig.user
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

      app.getSettingsFile(session, AppSettingsState.localUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "HTML Generation Task", inputCnt = 0, stickyInput = false, showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null, session, "HTML Generation @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    class WriteHtmlTaskDialog(
        project: Project?,
        private val root: File,
        val relatedFiles: List<File>
    ) : DialogWrapper(project) {

        private val taskDescriptionArea = JBTextArea(8, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Describe the HTML page to create, including layout, styling, and functionality requirements"
        }

        private val htmlFileField = JBTextField().apply {
            toolTipText = "Path for the HTML file to create (must end with .html)"
            text = "${relatedFiles.firstOrNull()?.nameWithoutExtension ?: "index"}.html"
        }

        private val relatedFilesField = JBTextField().apply {
            toolTipText = "Comma-separated list of related files to consider for context (e.g., existing templates)"
            text = relatedFiles.joinToString(", ") { it.relativeTo(root).path }
        }

        private val generateImagesCheckbox = JBCheckBox("Generate images for the page", false).apply {
            toolTipText = "Use AI to generate images for the HTML page"
            addActionListener {
                imageCountSpinner.isEnabled = isSelected
            }
        }

        private val imageCountSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1)).apply {
            toolTipText = "Number of images to generate (0-10)"
            isEnabled = false
        }

        private val visibleModelsCache by lazy { getVisibleModels() }

        private val modelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelId }.map { it.modelId }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelId
            toolTipText = "AI model to use for generating HTML, CSS, and JavaScript"
        }

        private val imageModelCombo = ComboBox(
            visibleModelsCache
                .distinctBy { it.modelId }
                .map { it.modelId }
                .toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.imageChatModel?.model?.modelId
            toolTipText = "AI model to use for generating images"
        }

        private val temperatureSlider = JSlider(0, 100, 70).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = javax.swing.JLabel("0.70")

        private val autoFixCheckbox = JBCheckBox("Auto-apply generated HTML", false).apply {
            toolTipText = "Automatically write the generated HTML file without manual confirmation"
        }

        init {
            init()
            title = "Configure HTML Generation Task"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("HTML Configuration") {
                row("HTML File:") {
                    cell(htmlFileField)
                        .align(Align.FILL)
                        .comment("Output path for the HTML file (e.g., index.html, pages/about.html)")
                }

                row("Page Description:") {
                    scrollCell(taskDescriptionArea)
                        .align(Align.FILL)
                        .comment("Describe the page layout, styling, functionality, and any specific requirements")
                        .resizableColumn()
                }.resizableRow()

                row("Related Files:") {
                    cell(relatedFilesField)
                        .align(Align.FILL)
                        .comment("Additional files for context (optional)")
                }
            }

            group("Image Generation") {
                row {
                    cell(generateImagesCheckbox)
                }

                row("Number of Images:") {
                    cell(imageCountSpinner)
                        .comment("How many images to generate (0-10)")
                }

                row("Image Model:") {
                    cell(imageModelCombo)
                        .align(Align.FILL)
                        .comment("AI model for image generation")
                }
            }

            group("Model Settings") {
                row("Text Model:") {
                    cell(modelCombo)
                        .align(Align.FILL)
                        .comment("AI model for generating HTML, CSS, and JavaScript")
                }

                row("Temperature:") {
                    cell(temperatureSlider)
                        .align(Align.FILL)
                        .comment("Higher values = more creative, lower = more focused")
                    cell(temperatureLabel)
                }

                row {
                    cell(autoFixCheckbox)
                }
            }
        }

        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
//        if (taskDescriptionArea.text.isBlank()) {
//            return com.intellij.openapi.ui.ValidationInfo("Page description is required", taskDescriptionArea)
//        }

            if (htmlFileField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("HTML file path is required", htmlFileField)
            } else {
                if (htmlFileField.text.let { root.resolve(it) }.exists()) {
                    return com.intellij.openapi.ui.ValidationInfo("HTML file path must not exist", htmlFileField)
                }
            }

            if (!htmlFileField.text.endsWith(".html", ignoreCase = true)) {
                return com.intellij.openapi.ui.ValidationInfo("File must have .html extension", htmlFileField)
            }

            return null
        }

        fun getOrchestrationConfig(): OrchestrationConfig {
            val selectedModel = modelCombo.selectedItem as? String
            val model = selectedModel?.let { modelName ->
                visibleModelsCache.find { it.modelId == modelName }?.toApiChatModel(localUser)
            }

            val selectedImageModel = imageModelCombo.selectedItem as? String
            val imageModel = selectedImageModel?.let { modelName ->
                visibleModelsCache.find { it.modelId == modelName }?.toApiChatModel(localUser)
            }

            return OrchestrationConfig(
                "Config",
                smartModel = (model ?: AppSettingsState.instance.smartModel)?.model?.modelId
                ?: throw IllegalStateException("No model configured"),
                fastModel = AppSettingsState.instance.fastModel?.model?.modelId
                    ?: throw IllegalStateException("Fast model not configured"),
                imageModel = (imageModel ?: AppSettingsState.instance.smartModel)?.model?.modelId
                ?: throw IllegalStateException("No image model configured"),
                temperature = temperatureSlider.value / 100.0,
                autoFix = autoFixCheckbox.isSelected,
                workingDir = root.absolutePath,
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                user = localUser
            )
        }

        private fun getVisibleModels() =
          ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(localUser).apis.flatMap { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.apiBase ?: throw IllegalArgumentException("No API found for provider: ${apiData.provider?.name}"))?.filter { model ->
                  model.provider == apiData.provider && model.modelId.isNotBlank() && PlanConfigDialog.isVisible(model)
                } ?: listOf()
            }.distinctBy { it.modelId }.sortedBy { "${it.provider?.name} - ${it.modelId}" }
    }

}
