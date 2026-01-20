# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/BusinessProposalAction.kt

```
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
import com.simiacryptus.cognotik.plan.tools.writing.BusinessProposalTask
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
import javax.swing.*

class BusinessProposalAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        if (event.getSelectedFiles().isEmpty() && event.getSelectedFolder() == null) return false
        return true
    }

    override fun handle(e: AnActionEvent) {
        val root = getProjectRoot(e) ?: return
        val relatedFiles = getFiles(e)
        val dialog = BusinessProposalDialog(
            e.project, root, relatedFiles
        )

        if (dialog.showAndGet()) {
            try {
                val taskConfig = dialog.getTaskConfig()
                val orchestrationConfig = dialog.getOrchestrationConfig()

                UITools.runAsync(e.project, "Initializing Business Proposal Task", true) { progress ->
                    initializeTask(e, progress, orchestrationConfig, taskConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize business proposal task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        e: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: BusinessProposalTask.BusinessProposalTaskExecutionConfigData,
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
        taskConfig: BusinessProposalTask.BusinessProposalTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "Business Proposal Generation",
            path = "/businessProposal",
            showMenubar = false,
            taskType = BusinessProposalTask.BusinessProposal,
            taskConfig = listOf(taskConfig),
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Business Proposal Generation", inputCnt = 0, stickyInput = false, showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null, session, "Business Proposal @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    class BusinessProposalDialog(
        project: Project?,
        private val root: File,
        val relatedFiles: List<File>
    ) : DialogWrapper(project) {

        private val proposalTitleField = JBTextField().apply {
            toolTipText = "The title or name of the proposal"
            text = "Project Proposal"
        }

        private val proposalTypeCombo = ComboBox(
            arrayOf("project", "investment", "grant", "partnership", "rfp_response")
        ).apply {
            toolTipText = "The type of proposal"
            selectedItem = "project"
        }

        private val objectiveArea = JBTextArea(4, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "The primary objective or goal of the proposal"
        }

        private val proposingOrgField = JBTextField().apply {
            toolTipText = "The organization or individual submitting the proposal"
        }

        private val decisionMakersField = JBTextField().apply {
            toolTipText = "Comma-separated list of decision-makers (e.g., 'CEO, CFO, Board of Directors')"
        }

        private val budgetRangeField = JBTextField().apply {
            toolTipText = "Budget range or financial scope (e.g., '$50,000-$100,000', 'under $1M')"
        }

        private val timelineField = JBTextField().apply {
            toolTipText = "Project timeline or duration (e.g., '6 months', '2024-2025', 'Q1-Q3')"
        }

        private val stakeholdersArea = JBTextArea(3, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Key stakeholders and their interests (format: 'Name: Interest' per line)"
        }

        private val includeROICheckbox = JBCheckBox("Include ROI Analysis", true).apply {
            toolTipText = "Include detailed ROI calculations and financial projections"
        }

        private val includeRiskCheckbox = JBCheckBox("Include Risk Assessment", true).apply {
            toolTipText = "Include risk assessment and mitigation strategies"
        }

        private val includeCompetitiveCheckbox = JBCheckBox("Include Competitive Analysis", true).apply {
            toolTipText = "Include competitive analysis or alternatives comparison"
        }

        private val includeTimelineCheckbox = JBCheckBox("Include Timeline & Milestones", true).apply {
            toolTipText = "Include detailed timeline with milestones"
        }

        private val includeResourcesCheckbox = JBCheckBox("Include Resource Requirements", true).apply {
            toolTipText = "Include team/resource requirements"
        }

        private val includeAppendicesCheckbox = JBCheckBox("Include Appendices", true).apply {
            toolTipText = "Include appendices and supporting documents"
        }

        private val urgencyCombo = ComboBox(
            arrayOf("critical", "high", "moderate", "low")
        ).apply {
            toolTipText = "Urgency level of the opportunity"
            selectedItem = "moderate"
        }

        private val toneCombo = ComboBox(
            arrayOf("formal", "professional", "persuasive", "collaborative")
        ).apply {
            toolTipText = "Tone of the proposal"
            selectedItem = "professional"
        }

        private val targetWordCountSpinner = JSpinner(SpinnerNumberModel(3000, 1000, 10000, 500)).apply {
            toolTipText = "Target word count for the complete proposal"
        }

        private val revisionPassesSpinner = JSpinner(SpinnerNumberModel(1, 0, 5, 1)).apply {
            toolTipText = "Number of revision passes for quality improvement (0-5)"
        }

        private val relatedFilesField = JBTextField().apply {
            toolTipText = "Comma-separated list of related files to incorporate"
            text = relatedFiles.joinToString(", ") { it.relativeTo(root).path }
        }

        private val inputFilesField = JBTextField().apply {
            toolTipText = "Comma-separated list of input files or patterns (e.g., **/*.kt)"
            text = relatedFiles.joinToString(", ") { it.relativeTo(root).path }
        }

        private val visibleModelsCache by lazy { getVisibleModels() }

        private val modelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "AI model to use for generating the proposal"
        }

        private val temperatureSlider = JSlider(0, 100, 70).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = JLabel("0.70")

        private val autoFixCheckbox = JBCheckBox("Auto-apply generated proposal", false).apply {
            toolTipText = "Automatically save the generated proposal without manual confirmation"
        }

        init {
            init()
            title = "Configure Business Proposal Generation"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("Proposal Information") {
                row("Proposal Title:") {
                    cell(proposalTitleField)
                        .align(Align.FILL)
                        .comment("The title or name of the proposal")
                }

                row("Proposal Type:") {
                    cell(proposalTypeCombo)
                        .align(Align.FILL)
                        .comment("Type: project, investment, grant, partnership, or RFP response")
                }

                row("Objective:") {
                    scrollCell(objectiveArea)
                        .align(Align.FILL)
                        .comment("The primary objective or goal of the proposal")
                        .resizableColumn()
                }.resizableRow()

                row("Proposing Organization:") {
                    cell(proposingOrgField)
                        .align(Align.FILL)
                        .comment("Organization or individual submitting the proposal")
                }
            }

            group("Stakeholders & Audience") {
                row("Decision Makers:") {
                    cell(decisionMakersField)
                        .align(Align.FILL)
                        .comment("Comma-separated list (e.g., 'CEO, CFO, Board of Directors')")
                }

                row("Stakeholders:") {
                    scrollCell(stakeholdersArea)
                        .align(Align.FILL)
                        .comment("Key stakeholders and interests (format: 'Name: Interest' per line)")
                        .resizableColumn()
                }.resizableRow()
            }

            group("Budget & Timeline") {
                row("Budget Range:") {
                    cell(budgetRangeField)
                        .align(Align.FILL)
                        .comment("e.g., '$50,000-$100,000', 'under $1M'")
                }

                row("Timeline:") {
                    cell(timelineField)
                        .align(Align.FILL)
                        .comment("e.g., '6 months', '2024-2025', 'Q1-Q3'")
                }
            }

            group("Analysis Components") {
                row {
                    cell(includeROICheckbox)
                }
                row {
                    cell(includeRiskCheckbox)
                }
                row {
                    cell(includeCompetitiveCheckbox)
                }
                row {
                    cell(includeTimelineCheckbox)
                }
                row {
                    cell(includeResourcesCheckbox)
                }
                row {
                    cell(includeAppendicesCheckbox)
                }
            }

            group("Proposal Settings") {
                row("Urgency Level:") {
                    cell(urgencyCombo)
                        .align(Align.FILL)
                        .comment("Urgency: critical, high, moderate, or low")
                }

                row("Tone:") {
                    cell(toneCombo)
                        .align(Align.FILL)
                        .comment("Tone: formal, professional, persuasive, or collaborative")
                }

                row("Target Word Count:") {
                    cell(targetWordCountSpinner)
                        .comment("Target word count for the complete proposal (1000-10000)")
                }

                row("Revision Passes:") {
                    cell(revisionPassesSpinner)
                        .comment("Number of revision passes for quality improvement (0-5)")
                }
            }

            group("Context Files") {
                row("Related Files:") {
                    cell(relatedFilesField)
                        .align(Align.FILL)
                        .comment("Comma-separated list of related files to incorporate")
                }

                row("Input Files:") {
                    cell(inputFilesField)
                        .align(Align.FILL)
                        .comment("Comma-separated list of input files or patterns (e.g., **/*.kt)")
                }
            }

            group("Model Settings") {
                row("Model:") {
                    cell(modelCombo)
                        .align(Align.FILL)
                        .comment("AI model for generating the proposal")
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
            if (proposalTitleField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Proposal title is required", proposalTitleField)
            }

            if (objectiveArea.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("Objective is required", objectiveArea)
            }

            return null
        }

        fun getTaskConfig(): BusinessProposalTask.BusinessProposalTaskExecutionConfigData {
            val relatedFiles = relatedFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            val inputFiles = inputFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            val decisionMakers = decisionMakersField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            val stakeholders = stakeholdersArea.text.lines()
                .filter { it.contains(":") }
                .associate {
                    val parts = it.split(":", limit = 2)
                    parts[0].trim() to parts[1].trim()
                }
                .takeIf { it.isNotEmpty() }

            return BusinessProposalTask.BusinessProposalTaskExecutionConfigData(
                proposal_title = proposalTitleField.text,
                proposal_type = proposalTypeCombo.selectedItem as String,
                objective = objectiveArea.text,
                proposing_organization = proposingOrgField.text.takeIf { it.isNotBlank() },
                decision_makers = decisionMakers,
                budget_range = budgetRangeField.text.takeIf { it.isNotBlank() },
                timeline = timelineField.text.takeIf { it.isNotBlank() },
                stakeholders = stakeholders,
                include_roi_analysis = includeROICheckbox.isSelected,
                include_risk_assessment = includeRiskCheckbox.isSelected,
                include_competitive_analysis = includeCompetitiveCheckbox.isSelected,
                include_timeline_milestones = includeTimelineCheckbox.isSelected,
                include_resource_requirements = includeResourcesCheckbox.isSelected,
                include_appendices = includeAppendicesCheckbox.isSelected,
                urgency_level = urgencyCombo.selectedItem as String,
                tone = toneCombo.selectedItem as String,
                target_word_count = targetWordCountSpinner.value as Int,
                revision_passes = revisionPassesSpinner.value as Int,
                related_files = relatedFiles,
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
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/DataIngestAction.kt

```
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
                val taskConfig = dialog.getTaskConfig()
                val orchestrationConfig = dialog.getOrchestrationConfig()

                UITools.runAsync(e.project, "Initializing Data Ingestion Task", true) { progress ->
                    initializeTask(e, progress, orchestrationConfig, taskConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize Data Ingestion task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        event: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: DataIngestTask.DataIngestTaskExecutionConfigData,
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
        taskConfig: DataIngestTask.DataIngestTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "Data Ingestion Task",
            path = "/dataIngestTask",
            showMenubar = false,
            taskType = DataIngestTask.DataIngest,
            taskConfig = listOf(taskConfig),
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
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
                    model.provider == apiData.provider &&
                            model.modelName?.isNotBlank() == true &&
                            PlanConfigDialog.isVisible(model)
                } ?: listOf()
            }.distinctBy { it.modelName }.sortedBy { "${it.provider?.name} - ${it.modelName}" }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/DocProcessorAction.kt

```
package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.simiacryptus.cognotik.apps.SingleTaskApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.DocProcessor.ModificationTask
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import java.awt.Dimension
import java.text.SimpleDateFormat
import javax.swing.JComponent

/**
 * Action that processes markdown documentation files with frontmatter specifications.
 * 
 * This action:
 * 1. Parses selected markdown files for frontmatter with 'specifies', 'documents', or 'transforms' keys
 * 2. Shows a checklist dialog allowing users to select which file generation tasks to run
 * 3. Executes the selected tasks using DocProcessor infrastructure
 */
open class DocProcessorAction(
    val mode: OverwriteMode = OverwriteModes.PatchExisting,
) : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    companion object {
        /**
         * Returns a pretty label for each overwrite mode
         */
        fun getModeLabel(mode: OverwriteModes): String = when (mode) {
            OverwriteModes.SkipExisting -> "🚫 Skip Existing Files"
            OverwriteModes.OverwriteExisting -> "🔄 Overwrite All Files"
            OverwriteModes.OverwriteToUpdate -> "📅 Overwrite Outdated Files"
            OverwriteModes.PatchExisting -> "🩹 Patch Existing Files"
            OverwriteModes.PatchToUpdate -> "📝 Patch Outdated Files"
        }
        /**
         * Returns a description for each overwrite mode
         */
        fun getModeDescription(mode: OverwriteModes): String = when (mode) {
            OverwriteModes.SkipExisting -> "Skip files that already exist, only create new files"
            OverwriteModes.OverwriteExisting -> "Replace all target files with newly generated content"
            OverwriteModes.OverwriteToUpdate -> "Replace only files older than their source documentation"
            OverwriteModes.PatchExisting -> "Apply intelligent patches to existing files"
            OverwriteModes.PatchToUpdate -> "Apply patches only to files older than their source documentation"
        }
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        val selectedFiles = event.getSelectedFiles()
        if (selectedFiles.isEmpty()) return false
        return selectedFiles.any { file ->
            val fileName = file.name.lowercase()
            (fileName.endsWith(".md") || fileName.endsWith(".markdown"))
        }
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        val root = e.getSelectedFolder()?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        } ?: return
        val selectedFiles = e.getSelectedFiles()
            .filter { it.name.lowercase().let { name -> name.endsWith(".md") || name.endsWith(".markdown") } }
            .map { it.toFile }

        if (selectedFiles.isEmpty()) {
            UITools.showError(project, "No markdown files selected")
            return
        }

        val docProcessor = DocProcessor(
            root = root,
            docsFolder = root,
            concurrencyLimit = 4,
            overwriteMode = mode,
            fastModel = AppSettingsState.instance.fastModel?.model
                ?: throw IllegalStateException("Fast model not configured"),
            smartModel = AppSettingsState.instance.smartModel?.model
                ?: throw IllegalStateException("Smart model not configured")
        )
        val allTasks = docProcessor.getAll(*selectedFiles.toTypedArray())

        if (allTasks.isEmpty()) {
            UITools.showError(project, "No tasks found in selected files. Ensure files have 'specifies', 'documents', or 'transforms' frontmatter.")
            return
        }

        // Show dialog on EDT
        ApplicationManager.getApplication().invokeAndWait {
            val dialog = DocProcessorTaskDialog(project, allTasks)
            if (dialog.showAndGet()) {
                val selectedTasks = dialog.getSelectedTasks()
                if (selectedTasks.isNotEmpty()) {
                    UITools.runAsync(project, "Processing Documentation Tasks", true) { innerProgress ->
                        executeTasks(docProcessor, selectedTasks, dialog.autoFix)
                    }
                }
            }
        }
    }

    private fun executeTasks(
        docProcessor: DocProcessor,
        tasks: List<ModificationTask>,
        autoFix: Boolean
    ) {
        val session = Session.newGlobalID()
        DataStorage.sessionPaths[session] = docProcessor.root
        val orchestrationConfig = OrchestrationConfig(
            sessionId = session.toString(),
            defaultSmartModel = AppSettingsState.instance.smartModel
            ?: throw IllegalStateException("No model configured"),
            defaultFastModel = AppSettingsState.instance.fastModel
                ?: throw IllegalStateException("Fast model not configured"),
            defaultImageModel = AppSettingsState.instance.imageChatModel,
            temperature = AppSettingsState.instance.temperature,
            autoFix = autoFix,
            workingDir = docProcessor.root.toString(),
            shellCmd = listOf(
                if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
            ),
            taskSettings = mutableMapOf(
                FileModification.name to TaskTypeConfig(
                    name = "File Modification Task",
                    task_type = FileModification.name)
            )
        )

        val app = object : SingleTaskApp(
            applicationName = "Doc Update Processor",
            path = "/docUpdate",
            showMenubar = autoFix,
            taskType = FileModification,
            taskConfig = tasks.map { it.data },
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")

            override fun getOrchestrationConfig(
                session: Session,
                user: User
            ) = super.getOrchestrationConfig(session, user)?.apply {
                processor = tasks.first().patchProcessor
            }
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Document Illustration Task",
            inputCnt = 0,
            stickyInput = autoFix,
            showMenubar = autoFix
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null, session, "Document Illustration @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )

        val uri = CognotikAppServer.getServer(
            AppSettingsState.instance.listeningEndpoint,
            AppSettingsState.instance.listeningPort
        ).server.uri.resolve("/#$session")
        log.info("Opening browser to $uri")
        browse(uri)
    }

    /**
     * Dialog that displays a checklist of file generation tasks for user selection.
     */
    class DocProcessorTaskDialog(
        project: Project?,
        private val allTasks: List<ModificationTask>
    ) : DialogWrapper(project) {

        var autoFix: Boolean = true
        private val checkBoxList = CheckBoxList<TaskItem>()
        private val taskItems: List<TaskItem>

        init {
            title = "Select Documentation Tasks"
            
            taskItems = allTasks.mapIndexed { index, (config, _) ->
                val targetFiles = config.files?.joinToString(", ") ?: throw IllegalStateException("No target files specified")
                val relatedFiles = config.related_files?.take(3)?.joinToString(", ") ?: ""
                val description = buildString {
                    append("Target: $targetFiles")
                    if (relatedFiles.isNotEmpty()) {
                        append(" | Related: $relatedFiles")
                        if ((config.related_files?.size ?: 0) > 3) {
                            append("...")
                        }
                    }
                }
                TaskItem(index, targetFiles, description, config)
            }
            
            checkBoxList.setItems(taskItems) { it.displayName }
            taskItems.forEach { checkBoxList.setItemSelected(it, true) }
            
            init()
        }

        override fun createCenterPanel(): JComponent = panel {
            row {
                checkBox("Auto-fix issues")
                    .selected(autoFix)
                    .onChanged { autoFix = it.isSelected }
            }
            row {
                label("Select which file generation tasks to execute:")
            }
            row {
                button("Select All") {
                    taskItems.forEach { checkBoxList.setItemSelected(it, true) }
                }
                button("Deselect All") {
                    taskItems.forEach { checkBoxList.setItemSelected(it, false) }
                }
            }
            row {
                val scrollPane = JBScrollPane(checkBoxList).apply {
                    preferredSize = Dimension(600, 400)
                }
                cell(scrollPane)
                    .align(Align.FILL)
            }
            row {
                label("${taskItems.size} task(s) found")
            }
            group("Task Details") {
                row {
                    text("""
                        Tasks are generated from markdown frontmatter:
                        <ul>
                        <li><b>specifies:</b> Files that should be updated based on the documentation</li>
                        <li><b>documents:</b> Documentation files to update based on source files</li>
                        <li><b>transforms:</b> Source-to-destination file transformations</li>
                        </ul>
                    """.trimIndent())
                }
            }
        }

        fun getSelectedTasks() = taskItems
            .filter { checkBoxList.isItemSelected(it) }
            .map { allTasks[it.index] }

        data class TaskItem(
            val index: Int,
            val displayName: String,
            val description: String,
            val config: FileModificationTaskExecutionConfigData
        ) {
            override fun toString(): String = "$displayName - $description"
        }
    }
}

/**
 * Action group that provides a submenu with all overwrite mode options
 */
class DocProcessorActionGroup : DefaultActionGroup() {

    init {
        isPopup = true
        templatePresentation.text = "📋 Build Related"
        templatePresentation.description = "Process markdown documentation files with frontmatter specifications"
    }

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return OverwriteModes.entries.map { mode ->
            object : DocProcessorAction(mode) {
                init {
                    templatePresentation.text = getModeLabel(mode)
                    templatePresentation.description = getModeDescription(mode)
                }
            }
        }.toTypedArray()
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val selectedFiles = e.getSelectedFiles()
        val hasMarkdownFiles = selectedFiles.any { file ->
            val fileName = file.name.lowercase()
            fileName.endsWith(".md") || fileName.endsWith(".markdown")
        }
        e.presentation.isEnabledAndVisible = hasMarkdownFiles && e.project != null
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/FileModificationTaskAction.kt

```
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
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
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
        taskConfig: FileModificationTask.FileModificationTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "File Modification Task",
            path = "/fileModificationTask",
            showMenubar = false,
            taskType = FileModification,
            taskConfig = listOf(taskConfig),
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
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
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
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
                files = files,
                related_files = relatedFiles,
                extractContent = extractContentCheckbox.isSelected,
                includeGitDiff = includeGitDiffCheckbox.isSelected,
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
                ),
                taskSettings = mutableMapOf(
                    FileModificationTask.FileModification.name to TaskTypeConfig(task_type = FileModification.name)
                )
            )
        }

        private fun getVisibleModels() =
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis.flatMap { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.filter { model ->
                    model.provider == apiData.provider && model.modelName?.isNotBlank() == true && PlanConfigDialog.isVisible(
                        model
                    )
                } ?: listOf()
            }.distinctBy { it.modelName }.sortedBy { "${it.provider?.name} - ${it.modelName}" }
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


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/GeneratePresentationAction.kt

```
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
import com.simiacryptus.cognotik.plan.tools.file.GeneratePresentationTask
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
        taskConfig: GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "Presentation Generation Task",
            path = "/generatePresentationTask",
            showMenubar = false,
            taskType = GeneratePresentationTask.GeneratePresentation,
            taskConfig = listOf(taskConfig),
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Presentation Generation Task", inputCnt = 0, stickyInput = false, showMenubar = false
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "Presentation Generation @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    class GeneratePresentationTaskDialog(
        project: Project?,
        private val root: File,
        val relatedFiles: List<File>
    ) : DialogWrapper(project) {

        private val taskDescriptionArea = JBTextArea(8, 40).apply {
            lineWrap = true
            wrapStyleWord = true
            toolTipText = "Describe the presentation including topic, key points, target audience, and desired style"
        }

        private val htmlFileField = JBTextField().apply {
            toolTipText = "Path for the HTML presentation file to create (must end with .html)"
            text =
                "${relatedFiles.firstOrNull()?.nameWithoutExtension?.let { "${it}_presentation" } ?: "presentation"}.html"
        }

        private val relatedFilesField = JBTextField().apply {
            toolTipText = "Comma-separated list of related files to consider for context (e.g., reference materials)"
            text = relatedFiles.joinToString(", ") { it.relativeTo(root).path }
        }

        private val generateImagesCheckbox = JBCheckBox("Generate images for key slides", false).apply {
            toolTipText = "Use AI to generate images for important slides in the presentation"
            addActionListener {
                imageCountSpinner.isEnabled = isSelected
                imageModelCombo.isEnabled = isSelected
            }
        }

        private val imageCountSpinner = JSpinner(SpinnerNumberModel(5, 1, 10, 1)).apply {
            toolTipText = "Maximum number of images to generate (1-10)"
            isEnabled = false
        }

        private val visibleModelsCache by lazy { getVisibleModels() }

        private val modelCombo = ComboBox(
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "AI model to use for generating presentation content"
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
            isEnabled = false
        }

        private val temperatureSlider = JSlider(0, 100, 70).apply {
            addChangeListener {
                temperatureLabel.text = "%.2f".format(value / 100.0)
            }
        }

        private val temperatureLabel = javax.swing.JLabel("0.70")

        private val autoFixCheckbox = JBCheckBox("Auto-apply generated presentation", false).apply {
            toolTipText = "Automatically write the generated presentation files without manual confirmation"
        }

        init {
            init()
            title = "Configure Presentation Generation Task"
        }

        override fun createCenterPanel(): JComponent = panel {
            group("Presentation Configuration") {
                row("HTML File:") {
                    cell(htmlFileField)
                        .align(Align.FILL)
                        .comment("Output path for the presentation file (e.g., presentation.html, slides/demo.html)")
                }

                row("Presentation Description:") {
                    scrollCell(taskDescriptionArea)
                        .align(Align.FILL)
                        .comment("Describe the presentation topic, key points, target audience, number of slides, and style preferences")
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

                row("Maximum Images:") {
                    cell(imageCountSpinner)
                        .comment("Maximum number of images to generate for key slides (1-10)")
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
                        .comment("AI model for generating presentation content")
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
            if (htmlFileField.text.isBlank()) {
                return com.intellij.openapi.ui.ValidationInfo("HTML file path is required", htmlFileField)
            }

            if (!htmlFileField.text.endsWith(".html", ignoreCase = true)) {
                return com.intellij.openapi.ui.ValidationInfo("File must have .html extension", htmlFileField)
            } else {
                if (htmlFileField.text.let { root.resolve(it) }.exists()) {
                    return com.intellij.openapi.ui.ValidationInfo("HTML file path must not exist", htmlFileField)
                }
            }

            return null
        }

        fun getTaskConfig(): GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData {
            val relatedFiles = relatedFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            return GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData(
                files = listOf(htmlFileField.text),
                related_files = relatedFiles,
                task_description = taskDescriptionArea.text,
                generate_images = generateImagesCheckbox.isSelected,
                max_images = imageCountSpinner.value as Int,
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
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/IllustrateDocumentAction.kt

```
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
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.tools.AbstractTask.TaskState
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.file.IllustrateDocumentTask
import com.simiacryptus.cognotik.plan.tools.toApiChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
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
            applicationName = "Document Illustration Task",
            path = "/illustrateDocumentTask",
            showMenubar = false,
            taskType = IllustrateDocumentTask.IllustrateDocument,
            taskConfig = listOf(taskConfig),
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
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
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "AI model for analyzing document and generating image prompts"
        }

        private val imageModelCombo = ComboBox(
            visibleModelsCache
                .distinctBy { it.modelName }
                .map { it.modelName }
                .toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.imageChatModel?.model?.modelName
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
                maxImages = maxImagesSpinner.value as Int,
                imageFormat = imageFormatCombo.selectedItem as String,
                autoInsert = autoInsertCheckbox.isSelected,
                imageInstructions = imageInstructionsField.text.takeIf { it.isNotBlank() },
                composerDirective = composerDirectiveField.text.takeIf { it.isNotBlank() },
                integratorDirective = integratorDirectiveField.text.takeIf { it.isNotBlank() },
                task_description = taskDescriptionField.text,
                state = TaskState.Pending
            )
        }

        fun getOrchestrationConfig(): OrchestrationConfig {
            val selectedTextModel = textModelCombo.selectedItem as? String
            val textModel = selectedTextModel?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.toApiChatModel()
            }

            val selectedImageModel = imageModelCombo.selectedItem as? String
            val imageModel = selectedImageModel?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.toApiChatModel()
            }

            return OrchestrationConfig(
                "Config",
                defaultSmartModel = textModel ?: AppSettingsState.instance.smartModel
                ?: throw IllegalStateException("No model configured"),
                defaultFastModel = AppSettingsState.instance.fastModel
                    ?: throw IllegalStateException("Fast model not configured"),
                defaultImageModel = imageModel ?: AppSettingsState.instance.imageChatModel
                ?: throw IllegalStateException("No image model configured"),
                temperature = temperatureSlider.value / 100.0,
                autoFix = true,
                workingDir = root.absolutePath,
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                )
            )
        }

        private fun getVisibleModels() =
            fileApplicationServices().userSettingsManager.getUserSettings().apis.flatMap { apiData ->
                apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.filter { model ->
                    model.provider == apiData.provider && model.modelName.isNotBlank() && PlanConfigDialog.isVisible(
                        model
                    )
                } ?: listOf()
            }.distinctBy { it.modelName }.sortedBy { "${it.provider?.name} - ${it.modelName}" }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/NarrativeGenerationAction.kt

```
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
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/PersuasiveEssayAction.kt

```
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
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask
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
        taskConfig: PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "Persuasive Essay Task",
            path = "/persuasiveEssayTask",
            showMenubar = false,
            taskType = PersuasiveEssayTask.PersuasiveEssay,
            taskConfig = listOf(taskConfig),
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
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
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
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
                input_files = inputFiles,
                related_files = relatedFiles,
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
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/ResearchPaperAction.kt

```
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
            taskConfig = listOf(taskConfig),
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
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/task/WriteHtmlAction.kt

```
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
import com.simiacryptus.cognotik.plan.tools.file.WriteHtmlTask
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
                val taskConfig = dialog.getTaskConfig()
                val orchestrationConfig = dialog.getOrchestrationConfig()

                UITools.runAsync(e.project, "Initializing HTML Generation Task", true) { progress ->
                    initializeTask(e, progress, orchestrationConfig, taskConfig, root)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize HTML generation task", ex)
                UITools.showError(e.project, "Failed to initialize task: ${ex.message}")
            }
        }
    }

    private fun initializeTask(
        e: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig,
        taskConfig: WriteHtmlTask.WriteHtmlTaskExecutionConfigData,
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
        taskConfig: WriteHtmlTask.WriteHtmlTaskExecutionConfigData,
        root: File
    ) {
        val app = object : SingleTaskApp(
            applicationName = "HTML Generation Task",
            path = "/writeHtmlTask",
            showMenubar = false,
            taskType = WriteHtmlTask.WriteHtml,
            taskConfig = listOf(taskConfig),
            instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        ) {
            override fun instance(model: ApiChatModel) =
                model.instance() ?: throw IllegalStateException("Model or Provider not set")
        }

        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
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
            visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()
        ).apply {
            maximumSize = Dimension(200, 30)
            selectedItem = AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "AI model to use for generating HTML, CSS, and JavaScript"
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

        fun getTaskConfig(): WriteHtmlTask.WriteHtmlTaskExecutionConfigData {
            val relatedFiles = relatedFilesField.text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                .takeIf { it.isNotEmpty() }

            return WriteHtmlTask.WriteHtmlTaskExecutionConfigData(
                files = listOf(htmlFileField.text),
                related_files = relatedFiles,
                task_description = taskDescriptionArea.text,
                generate_images = generateImagesCheckbox.isSelected,
                image_count = imageCountSpinner.value as Int,
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
                defaultImageModel = imageModel ?: AppSettingsState.instance.smartModel
                ?: throw IllegalStateException("No image model configured"),
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

```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the task actions directory, summarizing the specialized AI-driven functionality provided by the included Kotlin files.

### intellij/src/main/kotlin/cognotik/actions/task/README.md
```markdown
# Task Actions

This package contains specialized IntelliJ actions that leverage AI models to perform complex, structured tasks. Each action provides a configuration dialog to fine-tune the AI's behavior, context, and output format.

## Available Actions

### [BusinessProposalAction](BusinessProposalAction.kt)
Generates professional business proposals tailored to specific objectives.
- **Proposal Types**: Project, investment, grant, partnership, or RFP response.
- **Components**: Optional ROI analysis, risk assessment, competitive analysis, and resource requirements.
- **Context**: Incorporates existing project files and stakeholder information to ensure relevance.

### [DataIngestAction](DataIngestAction.kt)
Automates the discovery of data patterns for ingestion and parsing.
- **Functionality**: Samples input files (such as logs) to discover patterns and generate parsing logic or regex.
- **Configuration**: Adjustable sample size, coverage thresholds, and discovery iteration limits.

### [DocProcessorAction](DocProcessorAction.kt)
A sophisticated tool for maintaining documentation and code synchronization based on file metadata.
- **Frontmatter Driven**: Processes Markdown files containing `specifies`, `documents`, or `transforms` keys.
- **Flexible Execution**: Supports multiple overwrite modes including intelligent patching and update-only logic.
- **Batch Processing**: Allows users to select and execute multiple documentation tasks simultaneously.

### [FileModificationTaskAction](FileModificationTaskAction.kt)
A general-purpose action for modifying existing files or creating new ones based on natural language.
- **Input**: Natural language description of the desired modifications.
- **Context**: Can include related files, git diffs, and text extracted from non-text formats (PDF, HTML).

### [GeneratePresentationAction](GeneratePresentationAction.kt)
Creates interactive, standalone HTML presentations.
- **Output**: Modern HTML slide decks.
- **Visuals**: Integrated AI image generation for key slides to enhance visual appeal.
- **Customization**: Configurable topic, target audience, and presentation style.

### [IllustrateDocumentAction](IllustrateDocumentAction.kt)
Enhances existing Markdown or HTML documents with AI-generated imagery.
- **Process**: Analyzes document content to identify illustration opportunities and generates contextually relevant images.
- **Integration**: Optionally inserts image references directly into the source document.
- **Advanced Control**: Provides directives for image composition and integration strategies.

### [NarrativeGenerationAction](NarrativeGenerationAction.kt)
Develops long-form narratives, stories, or complex scenarios.
- **Structure**: Supports multi-act structures with configurable scenes per act.
- **Styling**: Various writing styles (literary, technical, etc.) and narrative perspectives.
- **Visuals**: Can generate cover art and scene-specific visualizations.

### [PersuasiveEssayAction](PersuasiveEssayAction.kt)
Crafts compelling arguments and persuasive essays.
- **Techniques**: Employs rhetorical devices (ethos, pathos, logos), addresses counterarguments, and incorporates evidence.
- **Customization**: Fine-tune the thesis, target audience, tone, and call-to-action strength.

### [ResearchPaperAction](ResearchPaperAction.kt)
Generates academic-grade research papers with high rigor.
- **Paper Types**: Empirical, theoretical, review, or meta-analysis.
- **Standards**: Supports APA, MLA, Chicago, and IEEE citation styles.
- **Features**: Includes literature reviews, methodology sections, and simulated peer review for quality improvement.

### [WriteHtmlAction](WriteHtmlAction.kt)
Generates complete web pages including layout and logic.
- **Scope**: Produces integrated HTML, CSS, and JavaScript.
- **Assets**: Supports generating custom AI images specifically for the page content.
- **Requirements**: Driven by descriptions of layout, styling, and functional requirements.

## Common Features

Most actions in this package share a set of core capabilities:
- **Model Selection**: Users can choose specific AI models for text and image generation tasks.
- **Temperature Control**: Adjust the balance between creative exploration and focused precision.
- **Context Awareness**: Actions can reference project files to provide grounded, relevant results.
- **Web UI Integration**: Tasks are managed via a local web server, providing a rich interface for monitoring progress and reviewing output.
- **Auto-Apply**: Options to automatically save generated content back to the workspace once the task is complete.
```

### Summary of Changes
- Created a new `README.md` in `intellij/src/main/kotlin/cognotik/actions/task/` as specified by the documentation transform rules.
- Provided detailed summaries for all 10 specialized AI actions found in the directory.
- Highlighted unique features for each action (e.g., citation styles for research papers, frontmatter keys for the doc processor).
- Documented common patterns shared across these actions, such as model selection and Web UI integration.
- Ensured the documentation reflects the configuration options available in the Kotlin `DialogWrapper` implementations.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/task/README.md'>intellij/src/main/kotlin/cognotik/actions/task/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/task/README.md'>intellij/src/main/kotlin/cognotik/actions/task/README.md</a> Updated
