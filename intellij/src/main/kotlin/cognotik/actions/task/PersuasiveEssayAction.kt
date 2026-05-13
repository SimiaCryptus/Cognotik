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
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask
import com.simiacryptus.cognotik.plan.toApiChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
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

class PersuasiveEssayAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        if (event.getSelectedFiles().isEmpty() && event.getSelectedFolder() == null) return false
        return true
    }

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        val relatedFiles = getFiles(e)
        val dialog = PersuasiveEssayTaskDialog(
            e.project, root, relatedFiles
        )

        if (dialog.showAndGet()) {
            try {
                val taskConfig = dialog.getTaskConfig()
                val orchestrationConfig = dialog.getOrchestrationConfig()

                UITools.runAsync(e.project, "Initializing Persuasive Essay Task", true) { progress ->
                    initializeTask(e, progress, orchestrationConfig, taskConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize persuasive essay task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        e: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData,
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
        taskConfig: PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            path = "/persuasiveEssayTask",
            applicationName = "Persuasive Essay Task",
            taskType = PersuasiveEssayTask.PersuasiveEssay,
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
            applicationName = "Persuasive Essay Task", inputCnt = 0, stickyInput = false, showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null, session, "Persuasive Essay @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    class PersuasiveEssayTaskDialog(
        project: Project?,
        private val root: File,
        val relatedFiles: List<File>
    ) : DialogWrapper(project) {

        private val thesisArea = JBTextArea(4, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Enter the thesis statement or position you want to argue for"
        }

        private val targetAudienceField = JBTextField().apply {
            toolTipText = "Target audience (e.g., 'general public', 'academics', 'policymakers', 'business leaders')"
            text = "general public"
        }

        private val toneField = JBTextField().apply {
            toolTipText = "Tone of the essay (e.g., 'formal', 'conversational', 'passionate', 'analytical')"
            text = "formal"
        }

        private val wordCountSpinner = JSpinner(SpinnerNumberModel(1500, 500, 5000, 100)).apply {
            toolTipText = "Target word count for the complete essay (500-5000)"
        }

        private val numArgumentsSpinner = JSpinner(SpinnerNumberModel(3, 1, 10, 1)).apply {
            toolTipText = "Number of main arguments to develop (1-10)"
        }

        private val includeCounterargumentsCheckbox = JBCheckBox("Include counterarguments and rebuttals", true).apply {
            toolTipText = "Address opposing viewpoints and provide rebuttals"
        }

        private val useRhetoricalDevicesCheckbox =
            JBCheckBox("Use rhetorical devices (ethos, pathos, logos)", true).apply {
                toolTipText = "Employ classical rhetorical techniques for persuasive impact"
            }

        private val includeEvidenceCheckbox = JBCheckBox("Include statistical evidence and citations", true).apply {
            toolTipText = "Use data, statistics, and expert testimony"
        }

        private val useAnalogiesCheckbox = JBCheckBox("Use analogies and examples", true).apply {
            toolTipText = "Include concrete examples and analogies for clarity"
        }

        private val callToActionCombo = ComboBox(arrayOf("strong", "moderate", "reflective", "none")).apply {
            selectedItem = "strong"
            toolTipText = "Type of call to action in the conclusion"
        }

        private val revisionPassesSpinner = JSpinner(SpinnerNumberModel(1, 0, 5, 1)).apply {
            toolTipText = "Number of revision passes for quality improvement (0-5)"
        }

        private val inputFilesField = JBTextField().apply {
            toolTipText = "Comma-separated list of input files or patterns (e.g., research/*.md, **/*.txt)"
            text = relatedFiles.joinToString(", ") { it.relativeTo(root).path }
        }

        private val relatedFilesField = JBTextField().apply {
            toolTipText = "Additional related files for context (optional)"
            text = ""
        }

        private val visibleModelsCache by lazy { getVisibleModels() }

        private val modelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelId }.map { it.modelId }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelId
            toolTipText = "AI model to use for generating the essay"
        }

        private val temperatureSlider = JSlider(0, 100, 70).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = javax.swing.JLabel("0.70")

        private val autoFixCheckbox = JBCheckBox("Auto-apply generated essay", false).apply {
            toolTipText = "Automatically save the generated essay without manual confirmation"
        }

        init {
            init()
            title = "Configure Persuasive Essay Task"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("Essay Configuration") {
                row("Thesis Statement:") {
                    scrollCell(thesisArea)
                        .align(Align.FILL)
                        .comment("The main position or argument you want to defend")
                        .resizableColumn()
                }.resizableRow()

                row("Target Audience:") {
                    cell(targetAudienceField)
                        .align(Align.FILL)
                        .comment("Who you're writing for (affects tone and approach)")
                }

                row("Tone:") {
                    cell(toneField)
                        .align(Align.FILL)
                        .comment("Overall tone of the essay")
                }

                row("Target Word Count:") {
                    cell(wordCountSpinner)
                        .comment("Approximate length of the complete essay")
                }

                row("Number of Arguments:") {
                    cell(numArgumentsSpinner)
                        .comment("How many main arguments to develop")
                }
            }

            group("Persuasive Techniques") {
                row {
                    cell(includeCounterargumentsCheckbox)
                }

                row {
                    cell(useRhetoricalDevicesCheckbox)
                }

                row {
                    cell(includeEvidenceCheckbox)
                }

                row {
                    cell(useAnalogiesCheckbox)
                }

                row("Call to Action:") {
                    cell(callToActionCombo)
                        .comment("Type of conclusion and call to action")
                }
            }

            group("Input Files") {
                row("Input Files:") {
                    cell(inputFilesField)
                        .align(Align.FILL)
                        .comment("Research files to incorporate (supports glob patterns)")
                }

                row("Related Files:") {
                    cell(relatedFilesField)
                        .align(Align.FILL)
                        .comment("Additional context files (optional)")
                }
            }

            group("Model Settings") {
                row("Model:") {
                    cell(modelCombo)
                        .align(Align.FILL)
                        .comment("AI model for generating the essay")
                }

                row("Temperature:") {
                    cell(temperatureSlider)
                        .align(Align.FILL)
                        .comment("Higher values = more creative, lower = more focused")
                    cell(temperatureLabel)
                }

                row("Revision Passes:") {
                    cell(revisionPassesSpinner)
                        .comment("Number of quality improvement passes")
                }

                row {
                    cell(autoFixCheckbox)
                }
            }
        }

        override fun doValidate(): com.intellij.openapi.ui.ValidationInfo? {
            if (thesisArea.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Thesis statement is required", thesisArea)
            }

            if (targetAudienceField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Target audience is required", targetAudienceField)
            }

            if (toneField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Tone is required", toneField)
            }

            val validCallToActions = setOf("strong", "moderate", "reflective", "none")
            if (callToActionCombo.selectedItem.toString().lowercase() !in validCallToActions) {
                return com.intellij.openapi.ui.ValidationInfo("Invalid call to action type", callToActionCombo)
            }

            return null
        }

        fun getTaskConfig(): PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData {
            val inputFiles = inputFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            val relatedFiles = relatedFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            return PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData(
                thesis = thesisArea.text,
                target_audience = targetAudienceField.text,
                tone = toneField.text,
                target_word_count = wordCountSpinner.value as Int,
                num_arguments = numArgumentsSpinner.value as Int,
                include_counterarguments = includeCounterargumentsCheckbox.isSelected,
                use_rhetorical_devices = useRhetoricalDevicesCheckbox.isSelected,
                include_evidence = includeEvidenceCheckbox.isSelected,
                use_analogies = useAnalogiesCheckbox.isSelected,
                call_to_action = callToActionCombo.selectedItem.toString(),
                revision_passes = revisionPassesSpinner.value as Int,
                related_files = (inputFiles ?: emptyList()) + (relatedFiles ?: emptyList()),
                state = TaskState.Pending
            )
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
                user = localUser
            )
        }

        private fun getVisibleModels() =
          ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(localUser).apis.flatMap { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.apiBase)?.filter { model ->
                    model.provider == apiData.provider &&
                            model.modelId?.isNotBlank() == true &&
                            PlanConfigDialog.isVisible(model)
                } ?: listOf()
            }.distinctBy { it.modelId }.sortedBy { "${it.provider?.name} - ${it.modelId}" }
    }

}