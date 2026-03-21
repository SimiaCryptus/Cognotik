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
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.AbstractTask.TaskState
import com.simiacryptus.cognotik.plan.tools.file.IllustrateDocumentTask
import com.simiacryptus.cognotik.plan.tools.toApiChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
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

class IllustrateDocumentAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val selectedFile = event.getSelectedFile()
        if (selectedFile == null) return false
        val fileName = selectedFile.name.lowercase()
        return fileName.endsWith(".md") || fileName.endsWith(".html")
    }

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        val selectedFile = e.getSelectedFile() ?: return

        val dialog = IllustrateDocumentTaskDialog(
            e.project, root, selectedFile.toFile
        )

        if (dialog.showAndGet()) {
            try {
                val taskConfig = dialog.getTaskConfig()
                val orchestrationConfig = dialog.getOrchestrationConfig()
                val session = Session.newGlobalID()
                DataStorage.sessionPaths[session] = root
                UITools.runAsync(e.project, "Initializing Document Illustration Task", true) { progress ->
                    initializeTask(progress, orchestrationConfig, taskConfig, session)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize document illustration task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData,
        session: Session
    ) {
        progress.text = "Starting server..."
        setupTaskSession(session, orchestrationConfig, taskConfig)
        try {
            Thread.sleep(500)
            progress.text = "Opening browser..."
            val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                AppSettingsState.instance.listeningEndpoint,
                AppSettingsState.instance.listeningPort
            ).server.uri.resolve("/#$session")
            log.info("Opening browser to $uri")
            browse(uri)
        } catch (e: Throwable) {
            log.warn("Error opening browser", e)
        }
    }

    private fun setupTaskSession(
        session: Session,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData
    ) {
        val app = object : SingleTaskApp(
            path = "/illustrateDocumentTask",
            applicationName = "Document Illustration Task",
            taskType = IllustrateDocumentTask.IllustrateDocument,
            taskConfig = taskConfig,
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
            applicationName = "Document Illustration Task",
            inputCnt = 0,
            stickyInput = false,
            showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null, session, "Document Illustration @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    class IllustrateDocumentTaskDialog(
        project: Project?,
        private val root: File,
        private val documentFile: File
    ) : DialogWrapper(project) {

        private val documentPathField = JBTextField().apply {
            text = documentFile.relativeTo(root).path
            isEditable = false
            toolTipText = "Document to illustrate (Markdown or HTML)"
        }

        private val maxImagesSpinner = JSpinner(SpinnerNumberModel(5, 1, 20, 1)).apply {
            toolTipText = "Maximum number of images to generate (1-20)"
        }

        private val imageFormatCombo = ComboBox(arrayOf("png", "jpg", "jpeg")).apply {
            selectedItem = "png"
            toolTipText = "Image format for generated files"
        }

        private val autoInsertCheckbox = JBCheckBox("Automatically insert image references", true).apply {
            toolTipText = "Insert image references into the document at appropriate locations"
        }
        private val imageInstructionsField = com.intellij.ui.components.JBTextArea().apply {
            text = ""
            rows = 3
            lineWrap = true
            wrapStyleWord = true
            toolTipText =
                "Additional instructions for image generation (e.g., 'Use a minimalist style', 'Include company branding colors')"
        }
        private val composerDirectiveField = com.intellij.ui.components.JBTextArea().apply {
            text = ""
            rows = 3
            lineWrap = true
            wrapStyleWord = true
            toolTipText =
                "Directives for the image composer (e.g., 'Generate a background wallpaper', 'Create hero images', 'Focus on technical diagrams')"
        }
        private val integratorDirectiveField = com.intellij.ui.components.JBTextArea().apply {
            text = ""
            rows = 3
            lineWrap = true
            wrapStyleWord = true
            toolTipText =
                "Directives for the image integrator (e.g., 'Insert as page background', 'Place images in sidebars', 'Create image galleries')"
        }


        private val taskDescriptionField = com.intellij.ui.components.JBTextArea().apply {
            text = "Illustrate document: ${documentFile.name}"
            rows = 3
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Description of the illustration task"
        }


        private val visibleModelsCache by lazy { getVisibleModels() }

        private val textModelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelId }.map { it.modelId }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelId
            toolTipText = "AI model for analyzing document and generating image prompts"
        }

        private val imageModelCombo = ComboBox(
            visibleModelsCache
                .distinctBy { it.modelId }
                .map { it.modelId }
                .toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.imageChatModel?.model?.modelId
            toolTipText = "AI model for generating images"
        }

        private val temperatureSlider = JSlider(0, 100, 50).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = javax.swing.JLabel("0.50")

        init {
            init()
            title = "Configure Document Illustration Task"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("Document Configuration") {
                row("Document File:") {
                    cell(documentPathField)
                        .align(Align.FILL)
                        .comment("The Markdown or HTML document to illustrate")
                }

                row("Max Images:") {
                    cell(maxImagesSpinner)
                        .comment("Maximum number of images to generate (1-20)")
                }

                row("Image Format:") {
                    cell(imageFormatCombo)
                        .comment("File format for generated images")
                }
            }

            row {
                cell(autoInsertCheckbox)
                    .comment("Automatically insert image references at appropriate locations in the document")
            }

            row("Image Instructions:") {
                scrollCell(imageInstructionsField)
                    .align(Align.FILL)
                    .comment("Additional instructions to append to all image generation prompts (optional)")
            }
            group("Advanced Directives") {
                row("Composer Directive:") {
                    scrollCell(composerDirectiveField)
                        .align(Align.FILL)
                        .comment("Instructions for how to compose and generate images (e.g., 'Generate a background wallpaper for the page', 'Create hero images for each section')")
                }
                row("Integrator Directive:") {
                    scrollCell(integratorDirectiveField)
                        .align(Align.FILL)
                        .comment("Instructions for how to integrate images into the document (e.g., 'Insert as CSS background', 'Create floating sidebars', 'Build image galleries')")
                }
            }


            //      row("Task Description:") {
            //        scrollCell(taskDescriptionField)
            //          .align(Align.FILL)
//          .comment("Describe what you want to achieve with this illustration task")
//      }

            group("Model Settings") {
                row("Text Model:") {
                    cell(textModelCombo)
                        .align(Align.FILL)
                        .comment("AI model for document analysis and image prompt generation")
                }

                row("Image Model:") {
                    cell(imageModelCombo)
                        .align(Align.FILL)
                        .comment("AI model for generating images")
                }

                row("Temperature:") {
                    cell(temperatureSlider)
                        .align(Align.FILL)
                        .comment("Higher values = more creative, lower = more focused")
                    cell(temperatureLabel)
                }
            }

            group("About") {
                row {
                    text(
                        """
                    This task will:
                    <ul>
                    <li>Analyze your document to identify sections that would benefit from images</li>
                    <li>Generate contextually appropriate images using AI</li>
                    <li>Save images with descriptive names in the document's folder</li>
                    <li>Optionally insert image references at appropriate locations</li>
                    </ul>
                """.trimIndent()
                    )
                }
            }
        }

        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
            if (!documentFile.exists()) {
                return com.intellij.openapi.ui.ValidationInfo("Document file does not exist", documentPathField)
            }

            val fileName = documentFile.name.lowercase()
            if (!fileName.endsWith(".md") && !fileName.endsWith(".html")) {
                return com.intellij.openapi.ui.ValidationInfo("Document must be .md or .html file", documentPathField)
            }

            return null
        }

        fun getTaskConfig(): IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData {
            return IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData(
                files = listOf(documentFile.relativeTo(root).path),
                max_images = maxImagesSpinner.value as Int,
                image_format = imageFormatCombo.selectedItem as String,
                auto_insert = autoInsertCheckbox.isSelected,
                image_instructions = imageInstructionsField.text.takeIf { it.isNotBlank() },
                composer_directive = composerDirectiveField.text.takeIf { it.isNotBlank() },
                integrator_directive = integratorDirectiveField.text.takeIf { it.isNotBlank() },
                task_description = taskDescriptionField.text,
                state = TaskState.Pending
            )
        }

        fun getOrchestrationConfig(): OrchestrationConfig {
            val selectedTextModel = textModelCombo.selectedItem as? String
            val textModel = selectedTextModel?.let { modelName ->
                visibleModelsCache.find { it.modelId == modelName }?.toApiChatModel(localUser)
            }

            val selectedImageModel = imageModelCombo.selectedItem as? String
            val imageModel = selectedImageModel?.let { modelName ->
                visibleModelsCache.find { it.modelId == modelName }?.toApiChatModel(localUser)
            }

            return OrchestrationConfig(
                "Config",
                smartModel = (textModel ?: AppSettingsState.instance.smartModel)?.model?.modelId
                ?: throw IllegalStateException("No model configured"),
                fastModel = AppSettingsState.instance.fastModel?.model?.modelId
                    ?: throw IllegalStateException("Fast model not configured"),
                imageModel = (imageModel ?: AppSettingsState.instance.imageChatModel)?.model?.modelId
                ?: throw IllegalStateException("No image model configured"),
                temperature = temperatureSlider.value / 100.0,
                autoFix = true,
                workingDir = root.absolutePath,
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                user = localUser
            )
        }

        private fun getVisibleModels() =
          fileApplicationServices().userSettingsManager.getUserSettings(localUser).apis.flatMap { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.apiBase ?: throw IllegalArgumentException("No API found for provider: ${apiData.provider?.name}"))?.filter { model ->
                    model.provider == apiData.provider && model.modelId.isNotBlank() && PlanConfigDialog.isVisible(
                        model
                    )
                } ?: listOf()
            }.distinctBy { it.modelId }.sortedBy { "${it.provider?.name} - ${it.modelId}" }
    }
}