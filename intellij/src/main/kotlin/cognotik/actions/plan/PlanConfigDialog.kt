package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.SavedPlanConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.JsonUtil.toJson
 import org.slf4j.LoggerFactory
 import java.awt.Component
 import java.awt.Dimension
 import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
 import javax.swing.*

 class PlanConfigDialog(
    project: Project?,
    val settings: OrchestrationConfig,
 ) : DialogWrapper(project) {

    private val maxTaskHistoryCharsField = JBTextField(settings.maxTaskHistoryChars.toString()).apply {
        toolTipText = "Maximum characters to retain in task history ($MIN_TASK_HISTORY-$MAX_TASK_HISTORY)"
        inputVerifier = object : InputVerifier() {
            override fun verify(input: JComponent): Boolean {
                val text = (input as? JTextField)?.text ?: return false
                return text.toIntOrNull()?.let { it in MIN_TASK_HISTORY..MAX_TASK_HISTORY } ?: false
            }
        }
    }

    private val maxTasksPerIterationField = JBTextField(settings.maxTasksPerIteration.toString()).apply {
        toolTipText = "Maximum number of tasks to execute per iteration ($MIN_TASKS_PER_ITER-$MAX_TASKS_PER_ITER)"
        inputVerifier = object : InputVerifier() {
            override fun verify(input: JComponent): Boolean {
                val text = (input as? JTextField)?.text ?: return false
                return text.toIntOrNull()?.let { it in MIN_TASKS_PER_ITER..MAX_TASKS_PER_ITER } ?: false
            }
        }
    }

    private val maxIterationsField = JBTextField(settings.maxIterations.toString()).apply {
        toolTipText = "Maximum number of planning iterations ($MIN_ITERATIONS-$MAX_ITERATIONS)"
        inputVerifier = object : InputVerifier() {
            override fun verify(input: JComponent): Boolean {
                val text = (input as? JTextField)?.text ?: return false
                return text.toIntOrNull()?.let { it in MIN_ITERATIONS..MAX_ITERATIONS } ?: false
            }
        }
    }

    private val autoPlanPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JLabel("Max Task History Chars:"))
        add(maxTaskHistoryCharsField)
        add(Box.createVerticalStrut(5))
        add(JLabel("Max Tasks Per Iteration:"))
        add(maxTasksPerIterationField)
        add(Box.createVerticalStrut(5))
        add(JLabel("Max Iterations:"))
        add(maxIterationsField)
        isVisible = false
    }

    val cognitiveModeCombo = ComboBox(
        arrayOf(
            "Chat", "Waterfall", "Adaptive", "Hierarchical"
        )
    ).apply {
        preferredSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
        selectedIndex = 0
        toolTipText = "Select the cognitive strategy for task execution"
    }

    private val modelCache = mutableMapOf<String, ChatModel?>()
    private val visibleModelsCache by lazy { getVisibleModels() }

    private val globalModelCombo =
        ComboBox(visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()).apply {
            maximumSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
            selectedItem =
                settings.defaultModel?.model?.modelName ?: AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "Default AI model for all tasks"
        }
    private val parsingModelCombo =
        ComboBox(visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()).apply {
            maximumSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
            selectedItem =
                settings.parsingModel?.model?.modelName ?: AppSettingsState.instance.smartModel?.model?.modelName
            toolTipText = "AI model for parsing and understanding tasks"
        }

    private val temperatureSlider =
        JSlider(MIN_TEMP, MAX_TEMP, (settings.temperature * TEMPERATURE_SCALE).toInt()).apply {
            addChangeListener {
                settings.temperature = value / TEMPERATURE_SCALE
                temperatureLabel.text = TEMPERATURE_LABEL.format(settings.temperature)
            }
        }

    private val temperatureLabel = JLabel(TEMPERATURE_LABEL.format(settings.temperature))
    private val autoFixCheckbox = JCheckBox("Auto-apply fixes", settings.autoFix)

    private val savedConfigsCombo = ComboBox<String>().apply {
        preferredSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
        AppSettingsState.instance.savedPlanConfigs?.keys?.sorted()?.forEach { addItem(it) }
    }
    // Task configuration list
    private val taskConfigListModel = DefaultListModel<TaskConfigEntry>()
    private val taskConfigList = JBList(taskConfigListModel).apply {
        cellRenderer = TaskConfigListCellRenderer()
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        toolTipText = "Configured tasks - double-click to edit"
    }


    init {
        // Start with empty task configurations
        // Double-click to edit task configuration
        taskConfigList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = taskConfigList.selectedValue
                    if (selected != null) {
                        editTaskConfig(selected)
                    }
                }
            }
        })
        cognitiveModeCombo.addActionListener {
            val selected = cognitiveModeCombo.selectedItem as String
            autoPlanPanel.isVisible = (selected == "Auto Plan")
        }
        init()
        title = "Configure Planning and Tasks"
    }

    private data class TaskConfigEntry(
        val taskType: TaskType<*, *>,
        val config: TaskTypeConfig
    ) {
        override fun toString(): String {
            val name = config.name ?: "Default"
            return "${taskType.name} - $name"
        }
    }

    private inner class TaskConfigListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            if (component is JLabel && value is TaskConfigEntry) {
                val modelName = value.config.model?.model?.modelName?.let { " ($it)" } ?: ""
                text = "${value.taskType.name} - ${value.config.name ?: "Default"}"
                toolTipText = """
                    <html>
                    <body style='width: 300px; padding: 5px;'>
                    <h3>${value.taskType.name}</h3>
                    <p><b>Config:</b> ${value.config.name ?: "Default"}</p>
                    <p><b>Model:</b> $modelName</p>
                    <p>${value.taskType.tooltipHtml}</p>
                    </body>
                    </html>
                """.trimIndent()
                font = font.deriveFont(Font.PLAIN, FONT_SIZE_ENABLED)
            }
            return component
        }
    }

    private fun editTaskConfig(entry: TaskConfigEntry) {
        val dialog = TaskConfigEditDialog(null, entry.taskType, entry.config, visibleModelsCache)
        if (dialog.showAndGet()) {
            val updatedConfig = dialog.getConfig()
            settings.removeTaskConfig(entry.taskType, entry.config.name ?: "")
            settings.addTaskConfig(entry.taskType, updatedConfig)
            val index = taskConfigListModel.indexOf(entry)
            taskConfigListModel.removeElement(entry)
            taskConfigListModel.add(index, TaskConfigEntry(entry.taskType, updatedConfig))
            taskConfigList.selectedIndex = index
        }
    }

    private fun addTaskConfig() {
        // Show dialog to select task type
        val taskTypes = TaskType.values().map { it.name }.toTypedArray()
        val selectedType = Messages.showEditableChooseDialog(
            "Select Task Type",
            "Add Task Configuration",
            Messages.getQuestionIcon(),
            taskTypes,
            taskTypes[0],
            null
        )
        if (selectedType != null) {
            val taskType = TaskType.values().find { it.name == selectedType } ?: return
            val newConfig = TaskTypeConfig(
                task_type = taskType.name,
                name = null,
                model = null
            )
            val dialog = TaskConfigEditDialog(null, taskType, newConfig, visibleModelsCache)
            if (dialog.showAndGet()) {
                val config = dialog.getConfig()
                settings.addTaskConfig(taskType, config)
                taskConfigListModel.addElement(TaskConfigEntry(taskType, config))
            }
        }
    }

    private fun deleteTaskConfig(entry: TaskConfigEntry) {
        val confirmResult = JOptionPane.showConfirmDialog(
            null,
            "Delete task configuration '${entry.config.name ?: "Default"}' for ${entry.taskType.name}?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION
        )
        if (confirmResult == JOptionPane.YES_OPTION) {
            settings.removeTaskConfig(entry.taskType, entry.config.name ?: "")
            taskConfigListModel.removeElement(entry)
        }
    }
    private fun exportTaskConfigs() {
        try {
            // Export entire orchestration configuration
            val exportData = mapOf(
                "temperature" to settings.temperature,
                "autoFix" to settings.autoFix,
                "maxTaskHistoryChars" to settings.maxTaskHistoryChars,
                "maxTasksPerIteration" to settings.maxTasksPerIteration,
                "maxIterations" to settings.maxIterations,
                "defaultModel" to settings.defaultModel?.model?.modelName,
                "parsingModel" to settings.parsingModel?.model?.modelName,
                "taskConfigs" to TaskType.values().associate { taskType ->
                    taskType.name to settings.getTaskConfigs(taskType).map { config ->
                        mapOf(
                            "task_type" to config.task_type,
                            "name" to config.name,
                            "model" to config.model?.model?.modelName
                        )
                    }
                }
            )
            val json = toJson(exportData)
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(StringSelection(json), null)
            Messages.showInfoMessage(
                "Orchestration configuration exported to clipboard",
                "Export Successful"
            )
        } catch (e: Exception) {
            log.error("Failed to export orchestration configuration", e)
            Messages.showErrorDialog(
                "Failed to export configurations: ${e.message}",
                "Export Error"
            )
        }
    }

     private fun importTaskConfigs() {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val contents = clipboard.getContents(null)
            if (!contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Messages.showWarningDialog(
                    "Clipboard does not contain text data",
                    "Import Error"
                )
                return
            }
            val json = contents.getTransferData(DataFlavor.stringFlavor) as String
            val importData = fromJson(json, Map::class.java) as Map<String, Any>
            val confirmResult = JOptionPane.showConfirmDialog(
                null,
                "Import will replace all current orchestration settings. Continue?",
                "Confirm Import",
                JOptionPane.YES_NO_OPTION
            )
            if (confirmResult != JOptionPane.YES_OPTION) {
                return
            }

            // Import orchestration settings
            (importData["temperature"] as? Number)?.toDouble()?.let { temp ->
                val validatedTemp = temp.coerceIn(0.0, 1.0)
                settings.temperature = validatedTemp
                temperatureSlider.value = (validatedTemp * TEMPERATURE_SCALE).toInt()
                temperatureLabel.text = TEMPERATURE_LABEL.format(validatedTemp)
            }

            (importData["autoFix"] as? Boolean)?.let { autoFix ->
                settings.autoFix = autoFix
                autoFixCheckbox.isSelected = autoFix
            }

            (importData["maxTaskHistoryChars"] as? Number)?.toInt()?.let {
                settings.maxTaskHistoryChars = it
                maxTaskHistoryCharsField.text = it.toString()
            }

            (importData["maxTasksPerIteration"] as? Number)?.toInt()?.let {
                settings.maxTasksPerIteration = it
                maxTasksPerIterationField.text = it.toString()
            }

            (importData["maxIterations"] as? Number)?.toInt()?.let {
                settings.maxIterations = it
                maxIterationsField.text = it.toString()
            }

            // Import model settings
            (importData["defaultModel"] as? String)?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.let { model ->
                    settings.defaultModel = model.toApiChatModel()
                    globalModelCombo.selectedItem = modelName
                }
            }

            (importData["parsingModel"] as? String)?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.let { model ->
                    settings.parsingModel = model.toApiChatModel()
                    parsingModelCombo.selectedItem = modelName
                }
            }

            // Clear and import task configurations
            taskConfigListModel.clear()
            TaskType.values().forEach { taskType ->
                settings.getTaskConfigs(taskType).forEach { config ->
                    settings.removeTaskConfig(taskType, config.name ?: "")
                }
            }
            // Import new configurations
            importData.forEach { entry ->
                (importData["taskConfigs"] as? Map<String, Any>)?.forEach { (taskTypeName, configsList) ->
                    val taskType = TaskType.values().find { it.name == taskTypeName } ?: return@forEach
                    (configsList as? List<Map<String, Any>>)?.forEach { configMap ->
                        val configName = configMap["name"] as? String
                        val modelName = configMap["model"] as? String
                        val model = if (modelName != null) {
                            visibleModelsCache.find { it.modelName == modelName }?.toApiChatModel()
                        } else null
                        val config = TaskTypeConfig(
                            task_type = taskTypeName,
                            name = configName,
                            model = model
                        )
                        settings.addTaskConfig(taskType, config)
                        taskConfigListModel.addElement(TaskConfigEntry(taskType, config))
                    }
                }
            }
            Messages.showInfoMessage(
                "Successfully imported orchestration configuration",
                "Import Successful"
            )
        } catch (e: Exception) {
            log.error("Failed to import orchestration configuration", e)
            Messages.showErrorDialog(
                "Failed to import configurations: ${e.message}",
                "Import Error"
            )
        }
    }


    private fun validateConfigName(name: String?) = when {
        name.isNullOrBlank() -> {
            Messages.showWarningDialog(
                "Configuration name cannot be empty", "Invalid Name"
            )
            false
        }

        !CONFIG_NAME_PATTERN.matches(name) -> {
            Messages.showWarningDialog(
                "Configuration name can only contain letters, numbers, underscores and hyphens", "Invalid Name"
            )
            false
        }

        else -> true
    }

    private fun validateNumericField(
        field: JTextField, fieldName: String, min: Int = 1, max: Int = Int.MAX_VALUE
    ): Int? {
        return try {
            val value = field.text.toInt()
            when {
                value < min -> {
                    Messages.showWarningDialog(
                        "$fieldName must be at least $min", "Invalid Value"
                    )
                    field.requestFocusInWindow()
                    null
                }

                value > max -> {
                    Messages.showWarningDialog(
                        "$fieldName must be at most $max", "Invalid Value"
                    )
                    field.requestFocusInWindow()
                    null
                }

                else -> value
            }
        } catch (e: NumberFormatException) {
            Messages.showWarningDialog(
                "$fieldName must be a valid number", "Invalid Value"
            )
            field.requestFocusInWindow()
            null
        }
    }

    private fun getVisibleModels() =
        ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis.flatMap { apiData ->
            apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.filter { model ->
                model.provider == apiData.provider && model.modelName?.isNotBlank() == true && isVisible(model)
            } ?: listOf()
        }.distinctBy { it.modelName }.sortedBy { "${it.provider?.name} - ${it.modelName}" }

    private fun saveCurrentConfig() {
        val configName = Messages.showInputDialog(
            "Enter configuration name:", "Save Configuration", Messages.getQuestionIcon()
        )?.trim()

        if (!validateConfigName(configName)) {
            return
        }

        if (AppSettingsState.instance.savedPlanConfigs?.containsKey(configName ?: "") == true) {
            val confirmResult = JOptionPane.showConfirmDialog(
                null,
                "Configuration '$configName' already exists. Overwrite?",
                "Confirm Overwrite",
                JOptionPane.YES_NO_OPTION
            )
            if (confirmResult != JOptionPane.YES_OPTION) {
                return
            }
        }

        val taskSettingsMap = TaskType.values().associate { taskType ->
            val taskSettings = settings.getTaskSettings(taskType)
            taskType.name to TaskTypeConfig(
                task_type = taskType.name,
                model = taskSettings.model,
            )
        }

        val config = SavedPlanConfig(
            name = configName!!,
            temperature = settings.temperature,
            autoFix = settings.autoFix,
            taskSettings = taskSettingsMap
        )

        try {
            val configs = AppSettingsState.instance.savedPlanConfigs ?: mutableMapOf()
            configs[configName] = toJson(config)
            AppSettingsState.instance.savedPlanConfigs = configs
        } catch (e: Exception) {
            log.error("Failed to save configuration", e)
            Messages.showErrorDialog(
                "Failed to save configuration: ${e.message}", "Save Error"
            )
            return
        }
        savedConfigsCombo.addItem(configName)
        savedConfigsCombo.selectedItem = configName
    }

    private fun loadConfig(configName: String) {
        val config = AppSettingsState.instance.savedPlanConfigs?.get(configName)
            ?.let<String, SavedPlanConfig?> { fromJson(it, SavedPlanConfig::class.java) } ?: return

        val hasUnsavedChanges = TaskType.values().any { taskType ->
            val currentSettings = settings.getTaskSettings(taskType)
            val savedSettings = config.taskSettings[taskType.name]
            currentSettings.model?.model?.modelName != savedSettings?.model?.model?.modelName
        }

        if (hasUnsavedChanges) {
            val confirmResult = JOptionPane.showConfirmDialog(
                null, "Loading will discard unsaved changes. Continue?", "Confirm Load", JOptionPane.YES_NO_OPTION
            )
            if (confirmResult != JOptionPane.YES_OPTION) {
                return
            }
        }

        try {
            val validatedTemp = config.temperature.coerceIn(0.0, 1.0)
            settings.temperature = validatedTemp
            temperatureSlider.value = (validatedTemp * TEMPERATURE_SCALE).toInt()
            temperatureLabel.text = TEMPERATURE_LABEL.format(validatedTemp)
            settings.autoFix = config.autoFix
            autoFixCheckbox.isSelected = config.autoFix

            config.taskSettings.forEach { (taskTypeName: String, serializedSettings: TaskTypeConfig) ->
                val taskType = TaskType.values().find { it.name == taskTypeName } ?: return@forEach
                settings.setTaskSettings(taskType, serializedSettings)
            }
            // Update combo boxes if models are saved in config
            settings.defaultModel?.model?.modelName?.let { modelName ->
                globalModelCombo.selectedItem = modelName
            }
            settings.parsingModel?.model?.modelName?.let { modelName ->
                parsingModelCombo.selectedItem = modelName
            }
        } catch (e: Exception) {
            log.error("Error loading configuration", e)
            Messages.showErrorDialog(
                "Error loading configuration: ${e.message}", "Load Error"
            )
        }
    }

    override fun createCenterPanel(): JComponent = panel {
        group {
            row("Saved Configs:") {
                cell(savedConfigsCombo).align(Align.FILL)
                    .comment("Select a saved configuration to load or save current settings")
                button("Save...") {
                    saveCurrentConfig()
                }
                button("Load") {
                    val selected = savedConfigsCombo.selectedItem as? String
                    if (selected != null) {
                        loadConfig(selected)
                    } else {
                        Messages.showWarningDialog(
                            "Please select a configuration to load", "No Configuration Selected"
                        )
                    }
                }
                button("Delete") {
                    val selected = savedConfigsCombo.selectedItem as? String
                    if (selected != null) {
                        val confirmResult = JOptionPane.showConfirmDialog(
                            null, "Delete configuration '$selected'?", "Confirm Delete", JOptionPane.YES_NO_OPTION
                        )
                        if (confirmResult == Messages.YES) {
                            val configs = AppSettingsState.instance.savedPlanConfigs ?: mutableMapOf()
                            configs.remove(selected)
                            AppSettingsState.instance.savedPlanConfigs = configs
                            savedConfigsCombo.removeItem(selected)
                        }
                    } else {
                        Messages.showWarningDialog(
                            "Please select a configuration to delete", "No Configuration Selected"
                        )
                    }
                }
                button("Copy") {
                    exportTaskConfigs()
                }
                button("Paste") {
                    importTaskConfigs()
                }
            }

            row {
                cell(autoFixCheckbox).align(Align.FILL)
                    .comment("Automatically apply suggested fixes without confirmation")
            }

            row("Temperature:") {
                cell(temperatureSlider).align(Align.FILL)
                    .comment("Adjust AI response creativity (higher = more creative)")
                cell(temperatureLabel)
            }
            row("Default Model:") {
                cell(globalModelCombo).align(Align.FILL)
                    .comment("Default AI model for all tasks")
            }
            row("Parsing Model:") {
                cell(parsingModelCombo).align(Align.FILL)
                    .comment("AI model for parsing and understanding tasks")
            }

            group("Task Configurations") {
                row {
                    scrollCell(taskConfigList)
                        .align(Align.FILL)
                        .comment("Double-click to edit a task configuration")
                        .resizableColumn()
                }.resizableRow()
                row {
                    button("Add Task Config") {
                        addTaskConfig()
                    }
                    button("Edit Selected") {
                        val selected = taskConfigList.selectedValue
                        if (selected != null) {
                            editTaskConfig(selected)
                        } else {
                            Messages.showWarningDialog(
                                "Please select a task configuration to edit",
                                "No Selection"
                            )
                        }
                    }
                    button("Delete Selected") {
                        val selected = taskConfigList.selectedValue
                        if (selected != null) {
                            deleteTaskConfig(selected)
                        } else {
                            Messages.showWarningDialog(
                                "Please select a task configuration to delete",
                                "No Selection"
                            )
                        }
                    }
                }
            }


            group("Planning Settings") {
                row("Cognitive Mode:") {
                    cell(cognitiveModeCombo).align(Align.FILL).comment("Select the cognitive strategy for planning")
                }
                row {
                    cell(autoPlanPanel).align(Align.FILL)
                }
            }
        }
    }

    override fun doOKAction() {
        // Validate numeric fields
        val maxTaskHistory =
            validateNumericField(maxTaskHistoryCharsField, "Max Task History Chars", MIN_TASK_HISTORY, MAX_TASK_HISTORY)
                ?: return
        val maxTasksPerIter =
            validateNumericField(
                maxTasksPerIterationField,
                "Max Tasks Per Iteration",
                MIN_TASKS_PER_ITER,
                MAX_TASKS_PER_ITER
            ) ?: return
        val maxIters = validateNumericField(maxIterationsField, "Max Iterations", 1, 1000) ?: return
        validateNumericField(maxIterationsField, "Max Iterations", MIN_ITERATIONS, MAX_ITERATIONS) ?: return

        settings.autoFix = autoFixCheckbox.isSelected
        settings.maxTaskHistoryChars = maxTaskHistory
        settings.maxTasksPerIteration = maxTasksPerIter
        settings.maxIterations = maxIters
        // Apply model selections
        val selectedGlobalModel = globalModelCombo.selectedItem as? String
        if (selectedGlobalModel != null) {
            val model = visibleModelsCache.find { it.modelName == selectedGlobalModel }
            settings.defaultModel = model?.toApiChatModel()
        }
        val selectedParsingModel = parsingModelCombo.selectedItem as? String
        if (selectedParsingModel != null) {
            val model = visibleModelsCache.find { it.modelName == selectedParsingModel }
            settings.parsingModel = model?.toApiChatModel()
        }

        super.doOKAction()
    }

    override fun dispose() {
        // Clean up resources
        modelCache.clear()
        super.dispose()
    }

    companion object {
        private val log = LoggerFactory.getLogger(PlanConfigDialog::class.java)

        // UI Constants
        private const val CONFIG_COMBO_WIDTH = 200
        private const val CONFIG_COMBO_HEIGHT = 30
        private const val MIN_TEMP = 0
        private const val MAX_TEMP = 100
        private const val TEMPERATURE_SCALE = 100.0
        private const val TEMPERATURE_LABEL = "%.2f"
        private const val FONT_SIZE_ENABLED = 14f
        private const val MIN_TASK_HISTORY = 100
        private const val MAX_TASK_HISTORY = 1000000
        private const val MIN_TASKS_PER_ITER = 1
        private const val MAX_TASKS_PER_ITER = 100
        private const val MIN_ITERATIONS = 1
        private const val MAX_ITERATIONS = 1000

        // Validation patterns
        private val CONFIG_NAME_PATTERN = Regex("^[a-zA-Z0-9_-]+$")

        fun isVisible(chatModel: ChatModel) =
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis.filter { it.key != null }
                .any { it.provider == chatModel.provider }
    }
}


fun ChatModel.toApiChatModel(): ApiChatModel {
    val apis = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis
    return ApiChatModel(
        model = this, provider = ApiData(
            key = apis.find { it.provider == this.provider }?.key
                ?: throw IllegalArgumentException("No API Key for ${this.provider?.name}"),
            baseUrl = apis.find { it.provider == this.provider }?.baseUrl ?: this.provider?.base ?: "",
            provider = this.provider,
        ).validate()
    )
}