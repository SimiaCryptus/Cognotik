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
import com.simiacryptus.cognotik.plan.tools.writing.ResearchPaperGenerationTask
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

class ResearchPaperAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        if (event.getSelectedFiles().isEmpty() && event.getSelectedFolder() == null) return false
        return true
    }

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        val relatedFiles = getFiles(e)
        val dialog = ResearchPaperTaskDialog(
            e.project, root, relatedFiles
        )

        if (dialog.showAndGet()) {
            try {
                val taskConfig = dialog.getTaskConfig()
                val orchestrationConfig = dialog.getOrchestrationConfig()

                UITools.runAsync(e.project, "Initializing Research Paper Generation Task", true) { progress ->
                    initializeTask(e, progress, orchestrationConfig, taskConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize research paper generation task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        e: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: ResearchPaperGenerationTask.ResearchPaperGenerationTaskExecutionConfigData,
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
        taskConfig: ResearchPaperGenerationTask.ResearchPaperGenerationTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "Research Paper Generation Task",
            path = "/researchPaperTask",
            showMenubar = false,
            taskType = ResearchPaperGenerationTask.ResearchPaperGeneration,
            taskConfig = taskConfig,
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Research Paper Generation Task", inputCnt = 0, stickyInput = false, showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null, session, "Research Paper @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    class ResearchPaperTaskDialog(
        project: Project?,
        private val root: File,
        val relatedFiles: List<File>
    ) : DialogWrapper(project) {

        private val researchTopicArea = JBTextArea(4, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Describe the main research question or topic for the paper"
        }

        private val paperTypeCombo = ComboBox(
            arrayOf("empirical", "theoretical", "review", "meta-analysis", "systematic-review")
        ).apply {
            selectedItem = "empirical"
            toolTipText = "Type of research paper to generate"
        }

        private val academicLevelCombo = ComboBox(
            arrayOf("undergraduate", "masters", "phd", "postdoc")
        ).apply {
            selectedItem = "masters"
            toolTipText = "Academic level for the paper's complexity and rigor"
        }

        private val citationStyleCombo = ComboBox(
            arrayOf("apa", "mla", "chicago", "ieee")
        ).apply {
            selectedItem = "apa"
            toolTipText = "Citation style to use throughout the paper"
        }

        private val targetWordCountSpinner = JSpinner(SpinnerNumberModel(8000, 1000, 50000, 1000)).apply {
            toolTipText = "Target word count for the complete paper (1000-50000)"
        }

        private val numberOfSectionsSpinner = JSpinner(SpinnerNumberModel(6, 3, 15, 1)).apply {
            toolTipText = "Number of main sections (3-15)"
        }

        private val revisionPassesSpinner = JSpinner(SpinnerNumberModel(1, 0, 5, 1)).apply {
            toolTipText = "Number of revision passes for quality improvement (0-5)"
        }

        private val includeLiteratureReviewCheckbox = JBCheckBox("Include Literature Review", true).apply {
            toolTipText = "Include a comprehensive literature review section"
        }

        private val includeMethodologyCheckbox = JBCheckBox("Include Methodology", true).apply {
            toolTipText = "Include a methodology section describing research methods"
        }

        private val includeStatisticalAnalysisCheckbox = JBCheckBox("Include Statistical Analysis", true).apply {
            toolTipText = "Include descriptions of statistical analysis methods"
        }

        private val includePeerReviewCheckbox = JBCheckBox("Include Peer Review Simulation", true).apply {
            toolTipText = "Simulate peer review to identify weaknesses and improvements"
        }

        private val inputFilesField = JBTextField().apply {
            toolTipText = "Comma-separated list of input files or patterns (e.g., **/*.kt, docs/*.md)"
            text = relatedFiles.joinToString(", ") { it.relativeTo(root).path }
        }

        private val researchFilesField = JBTextField().apply {
            toolTipText = "Comma-separated list of research source files to incorporate"
            text = ""
        }

        private val visibleModelsCache by lazy { getVisibleModels() }

        private val modelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "AI model to use for generating the research paper"
        }

        private val temperatureSlider = JSlider(0, 100, 70).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = javax.swing.JLabel("0.70")

        private val autoFixCheckbox = JBCheckBox("Auto-apply generated content", false).apply {
            toolTipText = "Automatically apply generated content without manual confirmation"
        }

        init {
            init()
            title = "Configure Research Paper Generation Task"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("Research Configuration") {
                row("Research Topic:") {
                    scrollCell(researchTopicArea)
                        .align(Align.FILL)
                        .comment("Describe the main research question or topic")
                        .resizableColumn()
                }.resizableRow()

                row("Paper Type:") {
                    cell(paperTypeCombo)
                        .align(Align.FILL)
                        .comment("Type of research paper (empirical, theoretical, review, etc.)")
                }

                row("Academic Level:") {
                    cell(academicLevelCombo)
                        .align(Align.FILL)
                        .comment("Academic level for complexity and rigor")
                }

                row("Citation Style:") {
                    cell(citationStyleCombo)
                        .align(Align.FILL)
                        .comment("Citation format (APA, MLA, Chicago, IEEE)")
                }

                row("Target Word Count:") {
                    cell(targetWordCountSpinner)
                        .comment("Target word count for the complete paper")
                }

                row("Number of Sections:") {
                    cell(numberOfSectionsSpinner)
                        .comment("Number of main sections (excluding abstract/conclusion)")
                }

                row("Revision Passes:") {
                    cell(revisionPassesSpinner)
                        .comment("Number of revision passes for quality improvement")
                }
            }

            group("Paper Features") {
                row {
                    cell(includeLiteratureReviewCheckbox)
                }

                row {
                    cell(includeMethodologyCheckbox)
                }

                row {
                    cell(includeStatisticalAnalysisCheckbox)
                }

                row {
                    cell(includePeerReviewCheckbox)
                }
            }

            group("Source Files") {
                row("Input Files:") {
                    cell(inputFilesField)
                        .align(Align.FILL)
                        .comment("Files or patterns to use as input (e.g., **/*.kt, docs/*.md)")
                }

                row("Research Files:") {
                    cell(researchFilesField)
                        .align(Align.FILL)
                        .comment("Research source files to incorporate (optional)")
                }
            }

            group("Model Settings") {
                row("Model:") {
                    cell(modelCombo)
                        .align(Align.FILL)
                        .comment("AI model for generating the research paper")
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
            if (researchTopicArea.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Research topic is required", researchTopicArea)
            }

            val targetWordCount = targetWordCountSpinner.value as Int
            if (targetWordCount < 1000 || targetWordCount > 50000) {
                return com.intellij.openapi.ui.ValidationInfo(
                    "Target word count must be between 1000 and 50000",
                    targetWordCountSpinner
                )
            }

            val numberOfSections = numberOfSectionsSpinner.value as Int
            if (numberOfSections < 3 || numberOfSections > 15) {
                return com.intellij.openapi.ui.ValidationInfo(
                    "Number of sections must be between 3 and 15",
                    numberOfSectionsSpinner
                )
            }

            val revisionPasses = revisionPassesSpinner.value as Int
            if (revisionPasses < 0 || revisionPasses > 5) {
                return com.intellij.openapi.ui.ValidationInfo(
                    "Revision passes must be between 0 and 5",
                    revisionPassesSpinner
                )
            }

            return null
        }

        fun getTaskConfig(): ResearchPaperGenerationTask.ResearchPaperGenerationTaskExecutionConfigData {
            val inputFiles = inputFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            val researchFiles = researchFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            return ResearchPaperGenerationTask.ResearchPaperGenerationTaskExecutionConfigData(
                research_topic = researchTopicArea.text,
                paper_type = paperTypeCombo.selectedItem as String,
                academic_level = academicLevelCombo.selectedItem as String,
                target_word_count = targetWordCountSpinner.value as Int,
                citation_style = citationStyleCombo.selectedItem as String,
                include_literature_review = includeLiteratureReviewCheckbox.isSelected,
                include_methodology = includeMethodologyCheckbox.isSelected,
                include_statistical_analysis = includeStatisticalAnalysisCheckbox.isSelected,
                include_peer_review = includePeerReviewCheckbox.isSelected,
                number_of_sections = numberOfSectionsSpinner.value as Int,
                revision_passes = revisionPassesSpinner.value as Int,
                research_files = researchFiles,
                input_files = inputFiles,
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
                temperature = temperatureSlider.value / 100.0,
                autoFix = autoFixCheckbox.isSelected,
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