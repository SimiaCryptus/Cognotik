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
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.tools.AbstractTask.TaskState
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.toApiChatModel
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTask
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

class NarrativeGenerationAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        if (event.getSelectedFiles().isEmpty() && event.getSelectedFolder() == null) return false
        return true
    }

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        val relatedFiles = getFiles(e)
        val dialog = NarrativeGenerationDialog(
            e.project, root, relatedFiles
        )

        if (dialog.showAndGet()) {
            try {
                val taskConfig = dialog.getTaskConfig()
                val orchestrationConfig = dialog.getOrchestrationConfig()

                UITools.runAsync(e.project, "Initializing Narrative Generation Task", true) { progress ->
                    initializeTask(e, progress, orchestrationConfig, taskConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize narrative generation task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        e: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData,
        root: File
    ) {
        progress.text = "Setting up session..."
        val session = Session.newGlobalID()

        DataStorage.sessionPaths[session] = root

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
        taskConfig: NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "Narrative Generation Task",
            path = "/narrativeGenerationTask",
            showMenubar = false,
            taskType = NarrativeGenerationTask.NarrativeGeneration,
            taskConfig = listOf(taskConfig),
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Narrative Generation Task", inputCnt = 0, stickyInput = false, showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null, session, "Narrative Generation @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    class NarrativeGenerationDialog(
        project: Project?,
        private val root: File,
        val relatedFiles: List<File>
    ) : DialogWrapper(project) {

        private val subjectField = JBTextField().apply {
            toolTipText = "The subject or scenario to develop into a full narrative"
            text = "A compelling story"
        }

        private val inputFilesField = JBTextField().apply {
            toolTipText = "Comma-separated file patterns for context (e.g., **/*.kt, docs/*.md)"
            text = relatedFiles.joinToString(", ") { it.relativeTo(root).path }
        }

        private val targetWordCountSpinner = JSpinner(SpinnerNumberModel(5000, 500, 50000, 500)).apply {
            toolTipText = "Target word count for the complete narrative"
        }

        private val numberOfActsSpinner = JSpinner(SpinnerNumberModel(3, 1, 10, 1)).apply {
            toolTipText = "Number of acts in the narrative structure (typically 3 or 5)"
        }

        private val scenesPerActSpinner = JSpinner(SpinnerNumberModel(3, 1, 10, 1)).apply {
            toolTipText = "Average number of scenes per act"
        }

        private val writingStyleCombo = ComboBox(
            arrayOf("literary", "thriller", "technical", "conversational", "academic", "journalistic")
        ).apply {
            selectedItem = "literary"
            toolTipText = "Writing style for the narrative"
        }

        private val pointOfViewCombo = ComboBox(
            arrayOf("first person", "third person limited", "third person omniscient", "second person")
        ).apply {
            selectedItem = "third person limited"
            toolTipText = "Point of view for the narrative"
        }

        private val toneCombo = ComboBox(
            arrayOf("dramatic", "humorous", "suspenseful", "reflective", "inspirational", "dark")
        ).apply {
            selectedItem = "dramatic"
            toolTipText = "Overall tone of the narrative"
        }

        private val detailedDescriptionsCheckbox = JBCheckBox("Include detailed scene descriptions", true).apply {
            toolTipText = "Whether to include vivid, sensory descriptions"
        }

        private val includeDialogueCheckbox = JBCheckBox("Include character dialogue", true).apply {
            toolTipText = "Whether to include natural dialogue between characters"
        }

        private val showInternalThoughtsCheckbox = JBCheckBox("Show internal character thoughts", true).apply {
            toolTipText = "Whether to reveal character internal thoughts and feelings"
        }

        private val revisionPassesSpinner = JSpinner(SpinnerNumberModel(1, 0, 5, 1)).apply {
            toolTipText = "Number of revision passes for each scene (0 = no revisions)"
        }

        private val generateSceneImagesCheckbox = JBCheckBox("Generate images for each scene", false).apply {
            toolTipText = "Use AI to generate visualization images for each scene"
        }

        private val generateCoverImageCheckbox = JBCheckBox("Generate cover image", false).apply {
            toolTipText = "Use AI to generate a cover image for the narrative"
        }

        private val narrativeElementsArea = JBTextArea(4, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText =
                "Optional: Define narrative elements as key:value pairs (one per line)\nExample:\nprotagonist: John Smith\nsetting: Victorian London\nconflict: Man vs Society"
        }

        private val visibleModelsCache by lazy { getVisibleModels() }

        private val modelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "AI model to use for narrative generation"
        }

        private val imageModelCombo = ComboBox(
            visibleModelsCache
                .distinctBy { it.modelName }
                .map { it.modelName }
                .toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.imageChatModel?.model?.modelName
            toolTipText = "AI model to use for generating images"
        }

        private val temperatureSlider = JSlider(0, 100, 80).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = javax.swing.JLabel("0.80")

        init {
            init()
            title = "Configure Narrative Generation Task"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("Narrative Configuration") {
                row("Subject:") {
                    cell(subjectField)
                        .align(Align.FILL)
                        .comment("The subject or scenario to develop into a full narrative")
                }

                row("Input Files:") {
                    cell(inputFilesField)
                        .align(Align.FILL)
                        .comment("File patterns for context (optional, e.g., **/*.kt, docs/*.md)")
                }

                row("Narrative Elements:") {
                    scrollCell(narrativeElementsArea)
                        .align(Align.FILL)
                        .comment("Optional: Define characters, setting, conflict, etc. (key:value pairs, one per line)")
                        .resizableColumn()
                }.resizableRow()
            }

            group("Structure") {
                row("Target Word Count:") {
                    cell(targetWordCountSpinner)
                        .comment("Total words for the complete narrative")
                }

                row("Number of Acts:") {
                    cell(numberOfActsSpinner)
                        .comment("Narrative structure (typically 3 or 5 acts)")
                }

                row("Scenes per Act:") {
                    cell(scenesPerActSpinner)
                        .comment("Average scenes in each act")
                }
            }

            group("Writing Style") {
                row("Style:") {
                    cell(writingStyleCombo)
                        .align(Align.FILL)
                        .comment("Overall writing style")
                }

                row("Point of View:") {
                    cell(pointOfViewCombo)
                        .align(Align.FILL)
                        .comment("Narrative perspective")
                }

                row("Tone:") {
                    cell(toneCombo)
                        .align(Align.FILL)
                        .comment("Emotional tone of the narrative")
                }

                row {
                    cell(detailedDescriptionsCheckbox)
                }

                row {
                    cell(includeDialogueCheckbox)
                }

                row {
                    cell(showInternalThoughtsCheckbox)
                }
            }

            group("Quality & Images") {
                row("Revision Passes:") {
                    cell(revisionPassesSpinner)
                        .comment("Number of editing passes per scene (0 = no revisions)")
                }

                row {
                    cell(generateSceneImagesCheckbox)
                }

                row {
                    cell(generateCoverImageCheckbox)
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
                        .comment("AI model for narrative generation")
                }

                row("Temperature:") {
                    cell(temperatureSlider)
                        .align(Align.FILL)
                        .comment("Higher values = more creative, lower = more focused")
                    cell(temperatureLabel)
                }
            }
        }

        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
            if (subjectField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Subject is required", subjectField)
            }

            return null
        }

        fun getTaskConfig(): NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData {
            val inputFiles = inputFilesField.text.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            val narrativeElements = narrativeElementsArea.text.lines()
                .filter { it.contains(":") }
                .associate {
                    val (key, value) = it.split(":", limit = 2)
                    key.trim() to value.trim()
                }
                .takeIf { it.isNotEmpty() }

            return NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData(
                subject = subjectField.text,
                input_files = inputFiles,
                narrative_elements = narrativeElements,
                target_word_count = targetWordCountSpinner.value as Int,
                number_of_acts = numberOfActsSpinner.value as Int,
                scenes_per_act = scenesPerActSpinner.value as Int,
                writing_style = writingStyleCombo.selectedItem as String,
                point_of_view = pointOfViewCombo.selectedItem as String,
                tone = toneCombo.selectedItem as String,
                detailed_descriptions = detailedDescriptionsCheckbox.isSelected,
                include_dialogue = includeDialogueCheckbox.isSelected,
                show_internal_thoughts = showInternalThoughtsCheckbox.isSelected,
                revision_passes = revisionPassesSpinner.value as Int,
                generate_scene_images = generateSceneImagesCheckbox.isSelected,
                generate_cover_image = generateCoverImageCheckbox.isSelected,
                state = TaskState.Pending
            )
        }

        fun getOrchestrationConfig(): OrchestrationConfig {
            val selectedModel = modelCombo.selectedItem as? String
            val model = selectedModel?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.toApiChatModel()
            }

            val selectedImageModel = imageModelCombo.selectedItem as? String
            val imageModel = selectedImageModel?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.toApiChatModel()
            }

            return OrchestrationConfig(
                "Config",
                defaultSmartModel = model ?: AppSettingsState.instance.smartModel
                ?: throw IllegalStateException("No model configured"),
                defaultFastModel = AppSettingsState.instance.fastModel
                    ?: throw IllegalStateException("Fast model not configured"),
                defaultImageModel = imageModel ?: AppSettingsState.instance.imageChatModel
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
                    model.provider == apiData.provider &&
                            model.modelName?.isNotBlank() == true &&
                            PlanConfigDialog.isVisible(model)
                } ?: listOf()
            }.distinctBy { it.modelName }.sortedBy { "${it.provider?.name} - ${it.modelName}" }
    }
}