package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.newSettings
import com.simiacryptus.cognotik.plan.tools.run.RunCodeTask
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.fetch.FetchMethod
import com.simiacryptus.cognotik.plan.tools.online.processing.ProcessingStrategyType
import com.simiacryptus.cognotik.plan.tools.online.seed.SeedMethod
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask
import com.simiacryptus.cognotik.plan.tools.file.PdfFormTask
import com.simiacryptus.cognotik.plan.tools.toApiChatModel
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

class TaskConfigDialog(
    project: Project?,
    private val taskType: TaskType<*, *>,
    private val config: TaskTypeConfig,
    private val availableModels: List<ChatModel>
) : DialogWrapper(project) {

    private val configNameField = JBTextField(config.name ?: taskType.name).apply {
        toolTipText = "Unique name for this task configuration"
    }

    private val modelCombo = ComboBox(
        availableModels.distinctBy { it.modelName }
            .map { it.modelName }
            .toTypedArray()
    ).apply {
        preferredSize = Dimension(300, 30)
        selectedItem = config.model?.model?.modelName
        toolTipText = "AI model to use for this task type"
    }
    private val configFields = mutableMapOf<String, JComponent>()

    // For SubPlanning task settings
    private val subTaskConfigListModel = DefaultListModel<SubTaskConfigEntry>()
    private val subTaskConfigList = JBList(subTaskConfigListModel).apply {
        this.visibleRowCount = 2
    }

    init {
        init()
        title = "Edit ${taskType.name} Configuration"
        isResizable = true
    }
    override fun getDimensionServiceKey(): String = "TaskConfigEditDialog"


    override fun createCenterPanel(): JComponent {
        val dialogPanel = panel {
            group("Task Configuration") {
                row("Configuration Name:") {
                    cell(configNameField)
                        .align(Align.FILL)
                        .comment("Enter a unique name for this configuration")
                }

                row("AI Model:") {
                    cell(modelCombo)
                        .align(Align.FILL)
                        .comment("Select the AI model to use for this task type")
                }
            }
            // Add task-specific configuration fields
            createTaskSpecificFields()

            group("Task Type Information") {
                row {
                    text(taskType.description ?: "No description available")
                }
            }
        }

        return JBScrollPane(dialogPanel).apply {
            preferredSize = Dimension(900, 700)
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.createTaskSpecificFields() {
        when (config) {
            is RunCodeTask.RunCodeTaskTypeConfig -> createRunCodeFields(config)
            is AutoFixTask.AutoFixTaskTypeConfig -> createSelfHealingFields(config)
            is CrawlerAgentTask.CrawlerTaskTypeConfig -> createCrawlerFields(config)
            is MCPToolTask.MCPToolTaskTypeConfig -> createMCPToolFields(config)
            is SubPlanTask.SubPlanTaskTypeConfig -> createSubPlanningFields(config)
            is PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig -> createPersuasiveEssayFields(config)
            is PdfFormTask.PdfFormTypeConfig -> createPdfFormFields(config)
            // Add more task types as needed
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.createRunCodeFields(config: RunCodeTask.RunCodeTaskTypeConfig) {
        group("Code Execution Settings") {
            row("Code Runtime:") {
                val runtimes = CodeRuntimes.values().map { it.name }.toTypedArray()
                val combo = ComboBox(runtimes)
                combo.selectedItem = config.codeRuntime?.name ?: runtimes.firstOrNull()
                cell(combo)
                    .comment("Select the runtime environment for code execution")
                configFields["codeRuntime"] = combo
            }
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.createSelfHealingFields(config: AutoFixTask.AutoFixTaskTypeConfig) {
        group("Self-Healing Settings") {
        }
    }
    private fun com.intellij.ui.dsl.builder.Panel.createPdfFormFields(config: PdfFormTask.PdfFormTypeConfig) {
        group("PDF Form Settings") {
            row("Template File:") {
                val field = JBTextField(config.template_file ?: "")
                field.toolTipText = "Path to the PDF template file relative to project root"
                cell(field)
                    .align(Align.FILL)
                    .comment("Path to the PDF template file (e.g., templates/form.pdf)")
                configFields["template_file"] = field
            }
        }
    }


    private fun com.intellij.ui.dsl.builder.Panel.createMCPToolFields(config: MCPToolTask.MCPToolTaskTypeConfig) {
        group("MCP Tool Settings") {
            row("Default Server:") {
                val field = JBTextField(config.default_server ?: "")
                field.toolTipText = "Default MCP server name to use if not specified in execution"
                cell(field)
                    .align(Align.FILL)
                    .comment("Name of the default MCP server to connect to")
                configFields["default_server"] = field
            }
            row("Default Timeout (seconds):") {
                val field = JBTextField(config.default_timeout.toString())
                field.toolTipText = "Default timeout in seconds for tool execution (1-300)"
                cell(field)
                    .comment("Maximum time to wait for tool execution")
                configFields["default_timeout"] = field
            }
            row {
                val autoRetryCheckbox = JCheckBox("Auto Retry on Failure", config.auto_retry)
                autoRetryCheckbox.toolTipText = "Automatically retry failed tool executions"
                cell(autoRetryCheckbox)
                    .comment("Enable automatic retry for transient failures")
                configFields["auto_retry"] = autoRetryCheckbox
            }
            row("Max Retries:") {
                val field = JBTextField(config.max_retries.toString())
                field.toolTipText = "Maximum number of retry attempts (1-10)"
                cell(field)
                    .comment("Number of times to retry failed executions")
                configFields["max_retries"] = field
            }
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.createSubPlanningFields(config: SubPlanTask.SubPlanTaskTypeConfig) {
        group("Sub-Planning Settings") {
            row("Purpose:") {
                val textArea = JBTextArea(3, 40)
                textArea.text = config.purpose
                textArea.toolTipText = "Supplemental description of the purpose of this configuration"
                textArea.lineWrap = true
                textArea.wrapStyleWord = true
                val scrollPane = JScrollPane(textArea)
                cell(scrollPane)
                    .align(Align.FILL)
                    .comment("Describe the specific purpose or use case for this sub-planning configuration")
                configFields["purpose"] = textArea
            }
            row("Cognitive Mode:") {
                val modes = CognitiveModeType.entries.map { it.name }.toTypedArray()
                val combo = ComboBox(modes)
                combo.selectedItem = config.cognitiveMode?.name ?: "Adaptive"
                combo.toolTipText = "Cognitive strategy to use for sub-planning"
                cell(combo)
                    .comment("Select the cognitive mode for executing the sub-plan")
                configFields["cognitiveMode"] = combo
            }
        }
        group("Sub-Task Configurations") {
            row {
                text(
                    """
                    <html>
                    <body style='width: 500px;'>
                    <p>Configure which task types are available within sub-plans. 
                    Each task type can have its own configuration that will be used 
                    when executing within a sub-plan context.</p>
                    </body>
                    </html>
                """.trimIndent()
                )
            }
            // Load existing sub-task configurations
            config.taskSettings.forEach { (key, taskConfig) ->
                val taskTypeName = if (key.contains("_")) key.substringBefore("_") else key
                val taskType = TaskType.values().find { it.name == taskTypeName }
                if (taskType != null) {
                    subTaskConfigListModel.addElement(SubTaskConfigEntry(taskType, taskConfig, key))
                }
            }
            subTaskConfigList.cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean
                ): Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (component is JLabel && value is SubTaskConfigEntry) {
                        text = "${value.taskType.name} - ${value.config.name ?: "Default"}"
                        toolTipText = value.taskType.description
                    }
                    return component
                }
            }
            subTaskConfigList.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) {
                        val selected = subTaskConfigList.selectedValue
                        if (selected != null) {
                            editSubTaskConfig(selected, config)
                        }
                    }
                }
            })
            row {
                scrollCell(subTaskConfigList)
                    .align(Align.FILL)
                    .comment("Double-click to edit a sub-task configuration")
                    .resizableColumn()
            }.resizableRow()
            row {
                button("Add Sub-Task") {
                    addSubTaskConfig(config)
                }
                button("Edit") {
                    val selected = subTaskConfigList.selectedValue
                    if (selected != null) {
                        editSubTaskConfig(selected, config)
                    } else {
                        Messages.showWarningDialog(
                            "Please select a sub-task configuration to edit",
                            "No Selection"
                        )
                    }
                }
                button("Delete") {
                    val selected = subTaskConfigList.selectedValue
                    if (selected != null) {
                        deleteSubTaskConfig(selected, config)
                    } else {
                        Messages.showWarningDialog(
                            "Please select a sub-task configuration to delete",
                            "No Selection"
                        )
                    }
                }
            }
        }
    }

    private fun addSubTaskConfig(parentConfig: SubPlanTask.SubPlanTaskTypeConfig) {
        val dialog = TaskTypeSelectionDialog(null)
        if (dialog.showAndGet()) {
            val taskType = dialog.getSelectedTaskType() ?: return
            val newConfig = taskType.newSettings() ?: run {
                Messages.showErrorDialog(
                    "Failed to create default configuration for ${taskType.name}",
                    "Error"
                )
                return
            }

            val config = if (dialog.isQuickSelect) {
                newConfig
            } else {
                val configDialog = TaskConfigDialog(null, taskType, newConfig, availableModels)
                if (configDialog.showAndGet()) configDialog.getConfig() else return
            }

            val key = if (config.name != null) "${taskType.name}_${config.name}" else taskType.name
            parentConfig.taskSettings[key] = config
            subTaskConfigListModel.addElement(SubTaskConfigEntry(taskType, config, key))
        }
    }

    private fun editSubTaskConfig(entry: SubTaskConfigEntry, parentConfig: SubPlanTask.SubPlanTaskTypeConfig) {
        val dialog = TaskConfigDialog(null, entry.taskType, entry.config, availableModels)
        if (dialog.showAndGet()) {
            val updatedConfig = dialog.getConfig()
            val newKey =
                if (updatedConfig.name != null) "${entry.taskType.name}_${updatedConfig.name}" else entry.taskType.name
            // Remove old key if it changed
            if (entry.key != newKey) {
                parentConfig.taskSettings.remove(entry.key)
            }
            parentConfig.taskSettings[newKey] = updatedConfig
            val index = subTaskConfigListModel.indexOf(entry)
            subTaskConfigListModel.removeElement(entry)
            subTaskConfigListModel.add(index, SubTaskConfigEntry(entry.taskType, updatedConfig, newKey))
            subTaskConfigList.selectedIndex = index
        }
    }

    private fun deleteSubTaskConfig(
        entry: SubTaskConfigEntry,
        parentConfig: SubPlanTask.SubPlanTaskTypeConfig
    ) {
        val confirmResult = JOptionPane.showConfirmDialog(
            null,
            "Delete sub-task configuration '${entry.config.name ?: "Default"}' for ${entry.taskType.name}?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION
        )
        if (confirmResult == JOptionPane.YES_OPTION) {
            parentConfig.taskSettings.remove(entry.key)
            subTaskConfigListModel.removeElement(entry)
        }
    }


    private fun com.intellij.ui.dsl.builder.Panel.createCrawlerFields(config: CrawlerAgentTask.CrawlerTaskTypeConfig) {
        group("Web Crawler Settings") {
            row("Processing Strategy:") {
                val strategies = ProcessingStrategyType.entries.map { it.name }.toTypedArray()
                val combo = ComboBox(strategies)
                combo.selectedItem = config.processing_strategy?.name ?: "DefaultSummarizer"
                combo.toolTipText = "Strategy for processing and analyzing page content"
                cell(combo)
                    .comment("Select how pages should be processed and analyzed")
                configFields["processing_strategy"] = combo
            }
            row("Seed Method:") {
                val methods = SeedMethod.entries.map { it.name }.toTypedArray()
                val combo = ComboBox(methods)
                combo.selectedItem = config.seed_method?.name ?: "GoogleProxy"
                cell(combo)
                    .comment("Method to seed the crawler (e.g., GoogleSearch, DirectUrls)")
                configFields["seed_method"] = combo
            }
            row("Fetch Method:") {
                val methods = FetchMethod.entries.map { it.name }.toTypedArray()
                val combo = ComboBox(methods)
                combo.selectedItem = config.fetch_method?.name ?: "HttpClient"
                cell(combo)
                    .comment("Method used to fetch content from URLs")
                configFields["fetch_method"] = combo
            }
            row {
                val respectRobotsCheckbox = JCheckBox("Respect robots.txt", config.respect_robots_txt ?: true)
                respectRobotsCheckbox.toolTipText = "Follow robots.txt rules when crawling websites"
                cell(respectRobotsCheckbox)
                    .comment("Enable to respect robots.txt crawl rules and delays")
                configFields["respect_robots_txt"] = respectRobotsCheckbox
            }
            row("Max Pages Per Task:") {
                val field = JBTextField(config.max_pages_per_task?.toString() ?: "30")
                field.toolTipText = "Maximum number of pages to process (1-500)"
                cell(field)
                    .comment("Limit the number of pages crawled per task")
                configFields["max_pages_per_task"] = field
            }
            row("Concurrent Processing:") {
                val field = JBTextField(config.concurrent_page_processing?.toString() ?: "3")
                field.toolTipText = "Number of pages to process concurrently (1-10)"
                cell(field)
                    .comment("Number of pages to fetch and process in parallel")
                configFields["concurrent_page_processing"] = field
            }
            row("Max Final Output Size:") {
                val field = JBTextField(config.max_final_output_size?.toString() ?: "10000")
                field.toolTipText = "Maximum characters in final summary (1000-100000)"
                cell(field)
                    .comment("Maximum size of the final output summary")
                configFields["max_final_output_size"] = field
            }
            row("Min Content Length:") {
                val field = JBTextField(config.min_content_length?.toString() ?: "100")
                field.toolTipText = "Minimum content length to process (10-10000)"
                cell(field)
                    .comment("Skip pages with less content than this threshold")
                configFields["min_content_length"] = field
            }
            row("Allowed Domains:") {
                val field = JBTextField(config.allowed_domains ?: "")
                field.toolTipText =
                    "Whitespace-separated list of allowed domains/URL prefixes (leave empty to allow all)"
                cell(field)
                    .align(Align.FILL)
                    .comment("Restrict crawling to specific domains or URL prefixes (e.g., 'example.com https://docs.example.com')")
                configFields["allowed_domains"] = field
            }
            row {
                val followLinksCheckbox = JCheckBox("Follow Links", config.follow_links ?: true)
                followLinksCheckbox.toolTipText = "Automatically follow links found in analyzed pages"
                cell(followLinksCheckbox)
                    .comment("Enable to crawl linked pages automatically")
                configFields["follow_links"] = followLinksCheckbox
            }
            row {
                val allowRevisitCheckbox = JCheckBox("Allow Revisit Pages", config.allow_revisit_pages ?: false)
                allowRevisitCheckbox.toolTipText = "Allow crawling the same page multiple times"
                cell(allowRevisitCheckbox)
                    .comment("Enable to allow processing the same URL multiple times")
                configFields["allow_revisit_pages"] = allowRevisitCheckbox
            }
            row {
                val createSummaryCheckbox = JCheckBox("Create Final Summary", config.create_final_summary ?: true)
                createSummaryCheckbox.toolTipText = "Generate a comprehensive summary of all results"
                cell(createSummaryCheckbox)
                    .comment("Enable to create a final summary when output is large")
                configFields["create_final_summary"] = createSummaryCheckbox
            }
        }
    }

    override fun doOKAction() {
        val name = configNameField.text.trim()

        if (name.isEmpty()) {
            Messages.showWarningDialog(
                "Configuration name cannot be empty",
                "Invalid Name"
            )
            configNameField.requestFocusInWindow()
            return
        }

        if (!CONFIG_NAME_PATTERN.matches(name)) {
            Messages.showWarningDialog(
                "Configuration name can only contain letters, numbers, underscores and hyphens",
                "Invalid Name"
            )
            configNameField.requestFocusInWindow()
            return
        }
        // Validate task-specific fields
        if (!validateTaskSpecificFields()) {
            return
        }

        super.doOKAction()
    }

    private fun validateTaskSpecificFields(): Boolean {
        // Validate SubPlanning numeric fields
        if (config is SubPlanTask.SubPlanTaskTypeConfig) {
            val maxDepth = (configFields["max_recursion_depth"] as? JBTextField)?.text?.trim()
            if (!maxDepth.isNullOrEmpty()) {
                val value = maxDepth.toIntOrNull()
                if (value == null || value !in 1..10) {
                    Messages.showWarningDialog(
                        "Max Recursion Depth must be between 1 and 10",
                        "Invalid Value"
                    )
                    configFields["max_recursion_depth"]?.requestFocusInWindow()
                    return false
                }
            }
        }

        // Validate MCPTool numeric fields
        if (config is MCPToolTask.MCPToolTaskTypeConfig) {
            val timeout = (configFields["default_timeout"] as? JBTextField)?.text?.trim()
            if (!timeout.isNullOrEmpty()) {
                val value = timeout.toIntOrNull()
                if (value == null || value !in 1..300) {
                    Messages.showWarningDialog(
                        "Default Timeout must be between 1 and 300 seconds",
                        "Invalid Value"
                    )
                    configFields["default_timeout"]?.requestFocusInWindow()
                    return false
                }
            }
            val maxRetries = (configFields["max_retries"] as? JBTextField)?.text?.trim()
            if (!maxRetries.isNullOrEmpty()) {
                val value = maxRetries.toIntOrNull()
                if (value == null || value !in 1..10) {
                    Messages.showWarningDialog(
                        "Max Retries must be between 1 and 10",
                        "Invalid Value"
                    )
                    configFields["max_retries"]?.requestFocusInWindow()
                    return false
                }
            }
        }

        // Validate CrawlerAgent numeric fields
        if (config is CrawlerAgentTask.CrawlerTaskTypeConfig) {
            val maxPages = (configFields["max_pages_per_task"] as? JBTextField)?.text?.trim()
            if (!maxPages.isNullOrEmpty()) {
                val value = maxPages.toIntOrNull()
                if (value == null || value !in 1..1000) {
                    Messages.showWarningDialog(
                        "Max Pages Per Task must be between 1 and 1000",
                        "Invalid Value"
                    )
                    configFields["max_pages_per_task"]?.requestFocusInWindow()
                    return false
                }
            }
            val concurrent = (configFields["concurrent_page_processing"] as? JBTextField)?.text?.trim()
            if (!concurrent.isNullOrEmpty()) {
                val value = concurrent.toIntOrNull()
                if (value == null || value !in 1..10) {
                    Messages.showWarningDialog(
                        "Concurrent Processing must be between 1 and 10",
                        "Invalid Value"
                    )
                    configFields["concurrent_page_processing"]?.requestFocusInWindow()
                    return false
                }
            }
            val maxOutput = (configFields["max_final_output_size"] as? JBTextField)?.text?.trim()
            if (!maxOutput.isNullOrEmpty()) {
                val value = maxOutput.toIntOrNull()
                if (value == null || value !in 1000..100000) {
                    Messages.showWarningDialog(
                        "Max Final Output Size must be between 1000 and 100000",
                        "Invalid Value"
                    )
                    configFields["max_final_output_size"]?.requestFocusInWindow()
                    return false
                }
            }
            val minContent = (configFields["min_content_length"] as? JBTextField)?.text?.trim()
            if (!minContent.isNullOrEmpty()) {
                val value = minContent.toIntOrNull()
                if (value == null || value !in 10..10000) {
                    Messages.showWarningDialog(
                        "Min Content Length must be between 10 and 10000",
                        "Invalid Value"
                    )
                    configFields["min_content_length"]?.requestFocusInWindow()
                    return false
                }
            }
            // Validate allowed_domains format
            val allowedDomains = (configFields["allowed_domains"] as? JBTextField)?.text?.trim()
            if (!allowedDomains.isNullOrEmpty()) {
                val domains = allowedDomains.split(Regex("\\s+"))
                val invalidDomains = domains.filter { domain ->
                    domain.isNotBlank() && !domain.matches(Regex("^(https?://)?[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$"))
                }
                if (invalidDomains.isNotEmpty()) {
                    Messages.showWarningDialog(
                        "Invalid domain format: ${invalidDomains.joinToString(", ")}\n\n" +
                                "Domains should be in format: 'example.com' or 'https://example.com/path'",
                        "Invalid Domain Format"
                    )
                    configFields["allowed_domains"]?.requestFocusInWindow()
                    return false
                }
            }
        }
        // Validate PersuasiveEssayTask numeric fields
        if (config is PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig) {
            val thesis = (configFields["thesis"] as? JBTextArea)?.text?.trim()
            if (thesis.isNullOrEmpty()) {
                Messages.showWarningDialog(
                    "Thesis statement cannot be empty",
                    "Invalid Value"
                )
                configFields["thesis"]?.requestFocusInWindow()
                return false
            }
            val wordCount = (configFields["target_word_count"] as? JBTextField)?.text?.trim()
            if (!wordCount.isNullOrEmpty()) {
                val value = wordCount.toIntOrNull()
                if (value == null || value <= 0) {
                    Messages.showWarningDialog(
                        "Target Word Count must be a positive number",
                        "Invalid Value"
                    )
                    configFields["target_word_count"]?.requestFocusInWindow()
                    return false
                }
            }
            val numArgs = (configFields["num_arguments"] as? JBTextField)?.text?.trim()
            if (!numArgs.isNullOrEmpty()) {
                val value = numArgs.toIntOrNull()
                if (value == null || value !in 1..10) {
                    Messages.showWarningDialog(
                        "Number of Arguments must be between 1 and 10",
                        "Invalid Value"
                    )
                    configFields["num_arguments"]?.requestFocusInWindow()
                    return false
                }
            }
            val revisionPasses = (configFields["revision_passes"] as? JBTextField)?.text?.trim()
            if (!revisionPasses.isNullOrEmpty()) {
                val value = revisionPasses.toIntOrNull()
                if (value == null || value !in 0..5) {
                    Messages.showWarningDialog(
                        "Revision Passes must be between 0 and 5",
                        "Invalid Value"
                    )
                    configFields["revision_passes"]?.requestFocusInWindow()
                    return false
                }
            }
        }
        // Validate PdfFormTask fields
        if (config is PdfFormTask.PdfFormTypeConfig) {
            val templateFile = (configFields["template_file"] as? JBTextField)?.text?.trim()
            if (templateFile.isNullOrEmpty()) {
                Messages.showWarningDialog(
                    "Template file path cannot be empty",
                    "Invalid Value"
                )
                configFields["template_file"]?.requestFocusInWindow()
                return false
            }
        }


        // Validate numeric fields
        configFields.forEach { (key, component) ->
            if (component is JBTextField && key in listOf(
                    "timeout",
                    "max_retries",
                    "max_pages",
                    "concurrent_processing"
                )
            ) {
                val value = component.text.trim()
                if (value.isNotEmpty()) {
                    try {
                        val intValue = value.toInt()
                        if (intValue <= 0) {
                            Messages.showWarningDialog(
                                "$key must be a positive number",
                                "Invalid Value"
                            )
                            component.requestFocusInWindow()
                            return false
                        }
                    } catch (_: NumberFormatException) {
                        Messages.showWarningDialog(
                            "$key must be a valid number",
                            "Invalid Value"
                        )
                        component.requestFocusInWindow()
                        return false
                    }
                }
            }
        }
        return true
    }

    fun getConfig(): TaskTypeConfig {
        val selectedModelName = modelCombo.selectedItem as? String
        val selectedModel = availableModels.find { it.modelName == selectedModelName }
        val baseConfig = taskType.newSettings().let {
            it?.task_type = taskType.name
            it?.name = configNameField.text.trim()
            it?.model = selectedModel?.toApiChatModel()
            it
        } ?: throw IllegalStateException("Failed to create base config for task type ${taskType.name}")
        // Apply task-specific configuration
        return applyTaskSpecificConfig(baseConfig)
    }

    private fun applyTaskSpecificConfig(baseConfig: TaskTypeConfig): TaskTypeConfig {
        return when (config) {
            is RunCodeTask.RunCodeTaskTypeConfig -> {
                RunCodeTask.RunCodeTaskTypeConfig(
                    task_type = baseConfig.task_type!!,
                    name = baseConfig.name,
                    model = baseConfig.model,
                    codeRuntime = CodeRuntimes.valueOf(
                        (configFields["codeRuntime"] as? ComboBox<*>)?.selectedItem as? String
                            ?: "KotlinRuntime"
                    )
                )
            }

            is AutoFixTask.AutoFixTaskTypeConfig -> {
                AutoFixTask.AutoFixTaskTypeConfig(
                    task_type = baseConfig.task_type,
                    name = baseConfig.name,
                    model = baseConfig.model
                )
            }

            is MCPToolTask.MCPToolTaskTypeConfig -> {
                MCPToolTask.MCPToolTaskTypeConfig(
                    task_type = baseConfig.task_type!!,
                    name = baseConfig.name,
                    default_server = (configFields["default_server"] as? JBTextField)?.text?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    default_timeout = (configFields["default_timeout"] as? JBTextField)?.text?.toIntOrNull() ?: 30,
                    auto_retry = (configFields["auto_retry"] as? JCheckBox)?.isSelected ?: false,
                    max_retries = (configFields["max_retries"] as? JBTextField)?.text?.toIntOrNull() ?: 3
                )
            }


            is CrawlerAgentTask.CrawlerTaskTypeConfig -> {
                CrawlerAgentTask.CrawlerTaskTypeConfig(
                    task_type = baseConfig.task_type!!,
                    name = baseConfig.name,
                    model = baseConfig.model,
                    processing_strategy = ProcessingStrategyType.valueOf(
                        (configFields["processing_strategy"] as? ComboBox<*>)?.selectedItem as? String
                            ?: "DefaultSummarizer"
                    ),
                    seed_method = SeedMethod.valueOf(
                        (configFields["seed_method"] as? ComboBox<*>)?.selectedItem as? String
                            ?: "GoogleProxy"
                    ),
                    fetch_method = FetchMethod.valueOf(
                        (configFields["fetch_method"] as? ComboBox<*>)?.selectedItem as? String
                            ?: "HttpClient"
                    ),
                    respect_robots_txt = (configFields["respect_robots_txt"] as? JCheckBox)?.isSelected,
                    max_pages_per_task = (configFields["max_pages_per_task"] as? JBTextField)?.text?.toIntOrNull(),
                    concurrent_page_processing = (configFields["concurrent_page_processing"] as? JBTextField)?.text?.toIntOrNull(),
                    max_final_output_size = (configFields["max_final_output_size"] as? JBTextField)?.text?.toIntOrNull(),
                    min_content_length = (configFields["min_content_length"] as? JBTextField)?.text?.toIntOrNull(),
                    allowed_domains = (configFields["allowed_domains"] as? JBTextField)?.text?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    follow_links = (configFields["follow_links"] as? JCheckBox)?.isSelected,
                    allow_revisit_pages = (configFields["allow_revisit_pages"] as? JCheckBox)?.isSelected,
                    create_final_summary = (configFields["create_final_summary"] as? JCheckBox)?.isSelected
                )
            }

            is SubPlanTask.SubPlanTaskTypeConfig -> {
                SubPlanTask.SubPlanTaskTypeConfig(
                    task_type = baseConfig.task_type!!,
                    name = baseConfig.name,
                    model = baseConfig.model,
                    purpose = (configFields["purpose"] as? JBTextArea)?.text?.trim() ?: "",
                    cognitiveMode = CognitiveModeType.valueOf(
                        (configFields["cognitiveMode"] as? ComboBox<*>)?.selectedItem as? String ?: "Waterfall"
                    ),
                    taskSettings = config.taskSettings.toMutableMap()
                )
            }

            is PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig -> {
                PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig(
                    generate_images = (configFields["generate_images"] as? JCheckBox)?.isSelected ?: true,
                    generate_cover_image = (configFields["generate_cover_image"] as? JCheckBox)?.isSelected ?: true,
                )
            }
            is PdfFormTask.PdfFormTypeConfig -> {
                PdfFormTask.PdfFormTypeConfig(
                    template_file = (configFields["template_file"] as? JBTextField)?.text?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    task_type = baseConfig.task_type,
                    name = baseConfig.name
                )
            }


            else -> baseConfig
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.createPersuasiveEssayFields(config: PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig) {
        group("Image Generation") {
            row {
                val generateImagesCheckbox = JCheckBox("Generate Images", config.generate_images)
                generateImagesCheckbox.toolTipText = "Generate visualization images for arguments"
                cell(generateImagesCheckbox)
                    .comment("Enable to generate images for essay visualization")
                configFields["generate_images"] = generateImagesCheckbox
            }
            row {
                val coverImageCheckbox = JCheckBox("Generate Cover Image", config.generate_cover_image)
                coverImageCheckbox.toolTipText = "Generate a professional cover image"
                cell(coverImageCheckbox)
                    .comment("Enable to generate a cover image for the essay")
                configFields["generate_cover_image"] = coverImageCheckbox
            }
        }
    }

    private data class SubTaskConfigEntry(
        val taskType: TaskType<*, *>,
        val config: TaskTypeConfig,
        val key: String
    )

    companion object {
        private val CONFIG_NAME_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    }
}