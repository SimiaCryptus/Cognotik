package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.platform.model.AIModel
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.instance
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.newSettings
import com.simiacryptus.cognotik.plan.toApiChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import org.slf4j.LoggerFactory
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

open class PlanConfigDialog(
    val project: Project?,
    val settings: OrchestrationConfig,
    private val appSettingsState: AppSettingsState? = null,
    private val availableModels: List<ChatModel>? = null,
) : DialogWrapper(project) {

    private val autoPlanPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(JLabel("Max Task History Chars:"))
        add(Box.createVerticalStrut(5))
        add(JLabel("Max Tasks Per Iteration:"))
        add(Box.createVerticalStrut(5))
        isVisible = false
    }

    val cognitiveModeCombo = ComboBox(
        CognitiveModeType.entries.map { it.name }.toTypedArray()
    ).apply {
        preferredSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
        selectedIndex = 0
        toolTipText = "Select the cognitive strategy for task execution"
    }
    private val appSettings get() = appSettingsState ?: AppSettingsState.instance

    private val modelCache = mutableMapOf<String, ChatModel?>()
    private val visibleModelsCache: List<ChatModel> by lazy { availableModels ?: getVisibleModels() }
    private val cognitiveConfigCache = mutableMapOf<CognitiveModeType<*>, CognitiveModeConfig>()

    private val globalModelCombo =
        ComboBox(visibleModelsCache.distinctBy { it.modelId }.map { it.modelId }.toTypedArray()).apply {
            maximumSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
            selectedItem =
                settings.smartModel?.instance(settings.user)?.model?.modelId ?: appSettings.smartModel?.model?.modelId
            toolTipText = "Default AI model for all tasks"
        }
    private val parsingModelCombo =
        ComboBox(visibleModelsCache.distinctBy { it.modelId }.map { it.modelId }.toTypedArray()).apply {
            maximumSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
            selectedItem =
                settings.fastModel?.instance(settings.user)?.model?.modelId ?: appSettings.smartModel?.model?.modelId
            toolTipText = "AI model for parsing and understanding tasks"
        }
    private val imageChatModelCombo =
        ComboBox(visibleModelsCache.distinctBy { it.modelId }.map { it.modelId }.toTypedArray()).apply {
            maximumSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
            selectedItem =
                settings.imageModel?.instance(settings.user)?.model?.modelId
                    ?: appSettings.imageChatModel?.model?.modelId
            toolTipText = "Multimodal AI model for image-related tasks"
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
        appSettings.savedPlanConfigs?.keys?.sorted()?.forEach { addItem(it) }
    }

    // Task configuration list
    private val taskConfigListModel = DefaultListModel<TaskConfigEntry>()
    private val taskConfigList = JBList(taskConfigListModel).apply {
        cellRenderer = TaskConfigListCellRenderer()
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        toolTipText = "Configured tasks - double-click to edit"
        visibleRowCount = 2
    }


    init {
        // Load existing task configurations
        settings.taskSettings.forEach { (taskTypeName, config) ->
            val taskType = TaskType.values().find { it.name == taskTypeName }
            if (taskType != null) {
                taskConfigListModel.addElement(TaskConfigEntry(taskType, config))
            }
        }

// Double-click to edit task configuration
        taskConfigList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
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

    private class TaskConfigListCellRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

            if (component is JLabel && value is TaskConfigEntry) {
                val modelName = value.config.model?.model?.modelId?.let { " ($it)" } ?: ""
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
        val dialog = TaskConfigDialog(null, entry.taskType, entry.config, visibleModelsCache)
        if (dialog.showAndGet()) {
            val updatedConfig = dialog.getConfig()
            val oldKey =
                if (entry.config.name != null) "${entry.taskType.name}_${entry.config.name}" else entry.taskType.name
            val newKey =
                if (updatedConfig.name != null) "${entry.taskType.name}_${updatedConfig.name}" else entry.taskType.name
            settings.taskSettings.remove(oldKey)
            settings.taskSettings[newKey] = updatedConfig
            val index = taskConfigListModel.indexOf(entry)
            taskConfigListModel.removeElement(entry)
            taskConfigListModel.add(index, TaskConfigEntry(entry.taskType, updatedConfig))
            taskConfigList.selectedIndex = index
        }
    }

    private fun addTaskConfig() {
        val dialog = TaskTypeSelectionDialog(null, allowMultipleSelection = true)
        if (dialog.showAndGet()) {
            val selectedTaskTypes = dialog.getSelectedTaskTypes()
            if (selectedTaskTypes.isEmpty()) return

            // If multiple tasks selected, use default configuration without opening edit dialog
            if (selectedTaskTypes.size > 1) {
                selectedTaskTypes.forEach { taskType ->
                    val newConfig = taskType.newSettings() ?: run {
                        log.warn("Failed to create default configuration for ${taskType.name}")
                        return@forEach
                    }
                    val key = taskType.name
                    settings.taskSettings[key] = newConfig
                    taskConfigListModel.addElement(TaskConfigEntry(taskType, newConfig))
                }
                Messages.showInfoMessage(
                    "Added ${selectedTaskTypes.size} task configurations with default settings",
                    "Tasks Added"
                )
            } else {
                // Single task selected - open edit dialog
                val taskType = selectedTaskTypes[0]
                val newConfig = taskType.newSettings() ?: run {
                    Messages.showErrorDialog(
                        "Failed to create default configuration for ${taskType.name}",
                        "Error"
                    )
                    return
                }
                val editDialog = TaskConfigDialog(null, taskType, newConfig, visibleModelsCache)
                if (editDialog.showAndGet()) {
                    val config = editDialog.getConfig()
                    val key = if (config.name != null) "${taskType.name}_${config.name}" else taskType.name
                    settings.taskSettings[key] = config
                    taskConfigListModel.addElement(TaskConfigEntry(taskType, config))
                }
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
            val key =
                if (entry.config.name != null) "${entry.taskType.name}_${entry.config.name}" else entry.taskType.name
            settings.taskSettings.remove(key)
            taskConfigListModel.removeElement(entry)
        }
    }

    private fun exportTaskConfigs() {
        try {
            // Export entire orchestration configuration
            val json = toJson(
                updateSettings()?.copy(
                    shellCmd = listOf(),
                    workingDir = null,
                )
            )
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
            val importData = fromJson<OrchestrationConfig>(json, OrchestrationConfig::class.java)
            val confirmResult = JOptionPane.showConfirmDialog(
                null,
                "Import will replace all current orchestration settings. Continue?",
                "Confirm Import",
                JOptionPane.YES_NO_OPTION
            )
            if (confirmResult != JOptionPane.YES_OPTION) {
                return
            }

            loadConfig(importData) // Load into UI components

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

    private fun getVisibleModels(): List<ChatModel> =
        ApplicationServicesImpl.fileApplicationServices().userSettingsManager.getUserSettings(localUser).apis.flatMap { apiData ->
            apiData.provider?.getChatModels(apiData.key!!, apiData.apiBase)?.filter { model ->
                model.provider == apiData.provider && model.modelId.isNotBlank() && isVisible(model)
            }?.filter { !it.deprecated } ?: listOf()
        }.distinctBy { it.modelId }.sortedBy { "${it.provider?.name} - ${it.modelId}" }

    private fun saveCurrentConfig() {
        val configName = Messages.showInputDialog(
            "Enter configuration name:", "Save Configuration", Messages.getQuestionIcon()
        )?.trim()

        if (!validateConfigName(configName)) {
            return
        }

        if (appSettings.savedPlanConfigs?.containsKey(configName ?: "") == true) {
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

        try {
            val configs = appSettings.savedPlanConfigs ?: mutableMapOf()
            configs[configName!!] = toJson(updateSettings())
            appSettings.savedPlanConfigs = configs
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
        val config = appSettings.savedPlanConfigs?.get(configName)
            ?.let<String, OrchestrationConfig?> { fromJson(it, OrchestrationConfig::class.java) } ?: return
        if (taskConfigListModel.size() > 0) {
            val confirmResult = JOptionPane.showConfirmDialog(
                null, "Loading will replace current settings. Continue?", "Confirm Load", JOptionPane.YES_NO_OPTION
            )
            if (confirmResult != JOptionPane.YES_OPTION) {
                return
            }
        }
        loadConfig(config)
    }

    private fun loadConfig(config: OrchestrationConfig = settings) {
        try {
            val validatedTemp = config.temperature.coerceIn(0.0, 1.0)
            settings.temperature = validatedTemp
            temperatureSlider.value = (validatedTemp * TEMPERATURE_SCALE).toInt()


            temperatureLabel.text = TEMPERATURE_LABEL.format(settings.temperature)

            // Clear existing task configurations
            taskConfigListModel.clear()

            settings.taskSettings.clear()

            // Copy all settings from loaded config
            settings.temperature = config.temperature.coerceIn(0.0, 1.0)
            settings.autoFix = config.autoFix
            settings.smartModel = config.smartModel?.instance(config.user)?.model?.modelId
            settings.fastModel = config.fastModel?.instance(config.user)?.model?.modelId
            settings.imageModel = config.imageModel?.instance(config.user)?.model?.modelId
            settings.cognitiveSettings = config.cognitiveSettings
            cognitiveConfigCache.clear()
            config.cognitiveSettings?.let {
                if (it.type != null) cognitiveConfigCache[it.type!!] = it
            }


            // Update UI components
            temperatureSlider.value = (settings.temperature * TEMPERATURE_SCALE).toInt()
            temperatureLabel.text = TEMPERATURE_LABEL.format(settings.temperature)
            autoFixCheckbox.isSelected = settings.autoFix

            // Update cognitive mode and visibility
            val cognitiveModeName = config.cognitiveSettings?.type?.name ?: "Chat"
            cognitiveModeCombo.selectedItem = cognitiveModeName
            autoPlanPanel.isVisible = (cognitiveModeName == "Auto Plan")

            // Load task configurations
            config.taskSettings.forEach { (key, taskConfig) ->
                settings.taskSettings[key] = taskConfig
                val taskTypeName = if (key.contains("_")) key.substringBefore("_") else key
                val taskType = TaskType.values().find { it.name == taskTypeName }
                if (taskType != null) {
                    taskConfigListModel.addElement(TaskConfigEntry(taskType, taskConfig))
                }
            }

            // Update model combo boxes
            config.smartModel?.instance(config.user)?.model?.modelId?.let { modelName ->
                visibleModelsCache.find { it.modelId == modelName }?.let { model ->
                    settings.smartModel = model.toApiChatModel(localUser).model?.modelId
                    globalModelCombo.selectedItem = modelName
                }
            }

            config.fastModel?.instance(config.user)?.model?.modelId?.let { modelName ->
                visibleModelsCache.find { it.modelId == modelName }?.let { model ->
                    settings.fastModel = model.toApiChatModel(localUser).model?.modelId
                    parsingModelCombo.selectedItem = modelName
                }
            }
            config.imageModel?.instance(config.user)?.model?.modelId?.let { modelName ->
                settings.imageModel = modelName
                imageChatModelCombo.selectedItem = modelName
            }

        } catch (e: Exception) {
            log.error("Error loading configuration", e)
            Messages.showErrorDialog(
                "Error loading configuration: ${e.message}", "Load Error"
            )
        }
    }


    override fun createCenterPanel(): JComponent = JBScrollPane(panel {
        group {
            row("Saved Configs:") {
                cell(savedConfigsCombo).align(Align.FILL)
                    .comment("Select a saved configuration to load or save current settings")
            }
            row {
                button("Save") { saveCurrentConfig() }
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
                            val configs = appSettings.savedPlanConfigs ?: mutableMapOf()
                            configs.remove(selected)
                            appSettings.savedPlanConfigs = configs
                            savedConfigsCombo.removeItem(selected)
                        }
                    } else {
                        Messages.showWarningDialog(
                            "Please select a configuration to delete", "No Configuration Selected"
                        )
                    }
                }
                button("Copy") { exportTaskConfigs() }
                button("Paste") { importTaskConfigs() }
            }
            group("Planning Settings") {
                row("Cognitive Mode:") {
                    cell(cognitiveModeCombo).align(Align.FILL)
                    button("Configure") {
                        val selected = cognitiveModeCombo.selectedItem as? String
                        if (selected != null) {
                            val modeType = CognitiveModeType.valueOf(selected)
                            val config = cognitiveConfigCache.getOrPut(modeType) { modeType.newSettings() }
                            val dialog = CognitiveConfigDialog(project, modeType, config)
                            if (dialog.showAndGet()) {
                                cognitiveConfigCache[modeType] = dialog.getConfig()
                            }
                        }
                    }
                }.comment("Select the cognitive strategy for planning")
                row {
                    cell(autoPlanPanel).align(Align.FILL)
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
            row("Image Chat Model:") {
                cell(imageChatModelCombo).align(Align.FILL)
                    .comment("Multimodal AI model for image-related tasks")
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
                    button("Edit") {
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
                    button("Delete") {
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
        }
    }).apply {
        border = null
        viewport.border = null
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }


    override fun doOKAction() {
        updateSettings() ?: return
        try {
            val configs = appSettings.savedPlanConfigs ?: mutableMapOf()
            configs["Last"] = toJson(settings)
            appSettings.savedPlanConfigs = configs
        } catch (e: Exception) {
            log.warn("Failed to save 'Last' configuration", e)
        }
        super.doOKAction()
    }

    fun updateSettings(): OrchestrationConfig? {
        // Validate numeric fields

        settings.autoFix = autoFixCheckbox.isSelected
        // Apply model selections
        val selectedGlobalModel = globalModelCombo.selectedItem as? String
        if (selectedGlobalModel != null) {
            val model = visibleModelsCache.find { it.modelId == selectedGlobalModel }
            settings.smartModel = model?.toApiChatModel(localUser)?.model?.modelId
        }
        val selectedParsingModel = parsingModelCombo.selectedItem as? String
        if (selectedParsingModel != null) {
            val model = visibleModelsCache.find { it.modelId == selectedParsingModel }
            settings.fastModel = model?.toApiChatModel(localUser)?.model?.modelId
        }
        val selectedImageChatModel = imageChatModelCombo.selectedItem as? String
        if (selectedImageChatModel != null) {
            val model = visibleModelsCache.find { it.modelId == selectedImageChatModel }
            settings.imageModel = model?.toApiChatModel(localUser)?.model?.modelId
        }
        val selectedCognitiveMode = cognitiveModeCombo.selectedItem as String
        val modeType = CognitiveModeType.valueOf(selectedCognitiveMode)
        settings.cognitiveSettings = cognitiveConfigCache.getOrPut(modeType) { modeType.newSettings() }
        return settings
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

        // Validation patterns
        private val CONFIG_NAME_PATTERN = Regex("^[a-zA-Z0-9_ -]+$")

        fun isVisible(chatModel: AIModel) =
            ApplicationServicesImpl.fileApplicationServices().userSettingsManager.getUserSettings(localUser).apis
              .filter { it.key?.decrypt != null }
                .any { it.provider == chatModel.provider }
    }
}