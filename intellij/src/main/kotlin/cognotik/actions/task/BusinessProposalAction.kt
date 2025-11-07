package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import cognotik.actions.plan.PlanConfigDialog
import cognotik.actions.plan.toApiChatModel
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
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.apps.general.SingleTaskApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.AbstractTask.TaskState
import com.simiacryptus.cognotik.plan.OrchestrationConfig
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
    taskConfig: BusinessProposalTask.BusinessProposalTaskExecutionConfigData,
    root: File
  ) {
    val app = object : SingleTaskApp(
      applicationName = "Business Proposal Generation",
      path = "/businessProposal",
      showMenubar = false,
      taskType = BusinessProposalTask.BusinessProposal,
      taskConfig = taskConfig,
      instanceFn = { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
    ) {
      override fun instance(model: ApiChatModel) = model.instance() ?: throw IllegalStateException("Model or Provider not set")
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
        defaultModel = model ?: AppSettingsState.instance.smartModel
        ?: throw IllegalStateException("No model configured"),
        parsingModel = AppSettingsState.instance.fastModel
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