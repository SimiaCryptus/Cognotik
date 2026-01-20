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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/plan/CognitiveConfigDialog.kt

```
package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.util.DynamicEnum
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.ScrollPaneConstants
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class CognitiveConfigDialog(
    project: Project?,
    private val modeType: CognitiveModeType<*>,
    private val config: CognitiveModeConfig
) : DialogWrapper(project) {

    private val configFields = mutableMapOf<String, JComponent>()

    init {
        init()
        title = "Configure ${modeType.name} Mode"
    }

    override fun createCenterPanel(): JComponent {
        val dialogPanel = panel {
            val kClass = config::class
            val properties = kClass.memberProperties
                .filter { it.name !in setOf("type", "name") }
                .sortedBy { it.name }

            if (properties.isEmpty()) {
                row {
                    text("No configurable settings for this mode.")
                }
            } else {
                for (prop in properties) {
                    val name = prop.name
                    val label = name
                        .replace(Regex("([^_ ])_([^_ ])"), "$1 $2")
                        .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                        .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
                        .split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

                    val description = prop.findAnnotation<Description>()?.value
                    val returnType = prop.returnType
                    val classifier = returnType.classifier as? KClass<*>

                    prop.isAccessible = true
                    val currentValue = try {
                        prop.getter.call(config)
                    } catch (e: Exception) {
                        null
                    }

                    if (classifier == Boolean::class) {
                        row {
                            val checkBox = JCheckBox(label, currentValue as? Boolean ?: false)
                            if (description != null) checkBox.toolTipText = description
                            cell(checkBox).comment(description)
                            configFields[name] = checkBox
                        }
                    } else if (classifier == String::class) {
                        row(label + ":") {
                            val isTextArea = name.contains("prompt", ignoreCase = true) ||
                                    name.contains("description", ignoreCase = true)
                            if (isTextArea) {
                                val textArea = JBTextArea(currentValue as? String ?: "", 5, 40)
                                textArea.lineWrap = true
                                textArea.wrapStyleWord = true
                                if (description != null) textArea.toolTipText = description
                                cell(JScrollPane(textArea)).align(Align.FILL).comment(description)
                                configFields[name] = textArea
                            } else {
                                val textField = JBTextField(currentValue as? String ?: "")
                                if (description != null) textField.toolTipText = description
                                cell(textField).align(Align.FILL).comment(description)
                                configFields[name] = textField
                            }
                        }
                    } else if (classifier == Int::class || classifier == Long::class || classifier == Double::class) {
                        row(label + ":") {
                            val textField = JBTextField(currentValue?.toString() ?: "")
                            if (description != null) textField.toolTipText = description
                            cell(textField).comment(description)
                            configFields[name] = textField
                        }
                    } else if (classifier?.java?.isEnum == true) {
                        row(label + ":") {
                            val enumConstants = classifier.java.enumConstants
                            val items = enumConstants.map { it.toString() }.toTypedArray()
                            val comboBox = ComboBox(items)
                            comboBox.selectedItem = currentValue?.toString()
                            if (description != null) comboBox.toolTipText = description
                            cell(comboBox).comment(description)
                            configFields[name] = comboBox
                        }
                    } else if (classifier != null && DynamicEnum::class.java.isAssignableFrom(classifier.java)) {
                        row(label + ":") {
                            val companion = classifier.java.getDeclaredField("Companion").get(null)
                            val valuesMethod = companion.javaClass.getMethod("values")
                            val values = valuesMethod.invoke(companion) as List<DynamicEnum<*>>
                            val items = values.map { it.name }.toTypedArray()
                            val comboBox = ComboBox(items)
                            comboBox.selectedItem = (currentValue as? DynamicEnum<*>)?.name
                            if (description != null) comboBox.toolTipText = description
                            cell(comboBox).comment(description)
                            configFields[name] = comboBox
                        }
                    }
                }
            }
        }

        return JBScrollPane(dialogPanel).apply {
            preferredSize = Dimension(600, 500)
            border = null
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
        }
    }

    fun getConfig(): CognitiveModeConfig {
        val kClass = config::class
        val properties = kClass.memberProperties
        for (prop in properties) {
            if (prop.name in configFields) {
                val component = configFields[prop.name]
                val value: Any? = when (component) {
                    is JCheckBox -> component.isSelected
                    is JBTextField -> {
                        val text = component.text.trim()
                        when (prop.returnType.classifier) {
                            Int::class -> text.toIntOrNull()
                            Long::class -> text.toLongOrNull()
                            Double::class -> text.toDoubleOrNull()
                            else -> text.ifEmpty { null }
                        }
                    }

                    is JBTextArea -> component.text.trim()
                    is ComboBox<*> -> {
                        val selected = component.selectedItem as? String
                        val paramClass = prop.returnType.classifier as? KClass<*>
                        if (selected != null && paramClass?.java?.isEnum == true) {
                            paramClass.java.enumConstants.find { it.toString() == selected }
                        } else if (selected != null && paramClass != null && DynamicEnum::class.java.isAssignableFrom(
                                paramClass.java
                            )
                        ) {
                            val companion = paramClass.java.getDeclaredField("Companion").get(null)
                            val valueOfMethod = companion.javaClass.getMethod("valueOf", String::class.java)
                            try {
                                valueOfMethod.invoke(companion, selected)
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }
                    }

                    else -> null
                }

                if (prop is KMutableProperty<*>) {
                    try {
                        prop.setter.call(config, value)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }
        return config
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/plan/PlanConfigDialog.kt

```
package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.AIModel
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.tools.newSettings
import com.simiacryptus.cognotik.plan.tools.toApiChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
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
        ComboBox(visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()).apply {
            maximumSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
            selectedItem =
                settings.defaultSmartModel?.model?.modelName ?: appSettings.smartModel?.model?.modelName
            toolTipText = "Default AI model for all tasks"
        }
    private val parsingModelCombo =
        ComboBox(visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()).apply {
            maximumSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
            selectedItem =
                settings.defaultFastModel?.model?.modelName ?: appSettings.smartModel?.model?.modelName
            toolTipText = "AI model for parsing and understanding tasks"
        }
    private val imageChatModelCombo =
        ComboBox(visibleModelsCache.distinctBy { it.modelName }.map { it.modelName }.toTypedArray()).apply {
            maximumSize = Dimension(CONFIG_COMBO_WIDTH, CONFIG_COMBO_HEIGHT)
            selectedItem =
                settings.defaultImageModel?.model?.modelName
                    ?: appSettings.imageChatModel?.model?.modelName
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
        ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis.flatMap { apiData ->
            apiData.provider?.getChatModels(apiData.key!!, apiData.baseUrl)?.filter { model ->
                model.provider == apiData.provider && model.modelName.isNotBlank() && isVisible(model)
            } ?: listOf()
        }.distinctBy { it.modelName }.sortedBy { "${it.provider?.name} - ${it.modelName}" }

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
            settings.defaultSmartModel = config.defaultSmartModel
            settings.defaultFastModel = config.defaultFastModel
            settings.defaultImageModel = config.defaultImageModel
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
            val cognitiveModeName = config.cognitiveMode?.name ?: "Chat"
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
            config.defaultSmartModel?.model?.modelName?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.let { model ->
                    settings.defaultSmartModel = model.toApiChatModel()
                    globalModelCombo.selectedItem = modelName
                }
            }

            config.defaultFastModel?.model?.modelName?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.let { model ->
                    settings.defaultFastModel = model.toApiChatModel()
                    parsingModelCombo.selectedItem = modelName
                }
            }
            config.defaultImageModel?.model?.modelName?.let { modelName ->
                visibleModelsCache.find { it.modelName == modelName }?.let { model ->
                    settings.defaultImageModel = model.toApiChatModel()
                    imageChatModelCombo.selectedItem = modelName
                }
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
            val model = visibleModelsCache.find { it.modelName == selectedGlobalModel }
            settings.defaultSmartModel = model?.toApiChatModel()
        }
        val selectedParsingModel = parsingModelCombo.selectedItem as? String
        if (selectedParsingModel != null) {
            val model = visibleModelsCache.find { it.modelName == selectedParsingModel }
            settings.defaultFastModel = model?.toApiChatModel()
        }
        val selectedImageChatModel = imageChatModelCombo.selectedItem as? String
        if (selectedImageChatModel != null) {
            val model = visibleModelsCache.find { it.modelName == selectedImageChatModel }
            settings.defaultImageModel = model?.toApiChatModel()
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
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().apis.filter { it.key != null }
                .any { it.provider == chatModel.provider }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/plan/TaskConfigDialog.kt

```
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
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.tools.newSettings
import com.simiacryptus.cognotik.plan.tools.file.PdfFormTask
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask
import com.simiacryptus.cognotik.plan.tools.toApiChatModel
import com.simiacryptus.cognotik.util.DynamicEnum
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

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
        if (config is SubPlanTask.SubPlanTaskTypeConfig) {
            createSubPlanningFields(config)
        } else {
            createReflectionFields()
        }
    }


    private fun com.intellij.ui.dsl.builder.Panel.createReflectionFields() {
        val kClass = config::class
        val properties = kClass.memberProperties
            .filter { it.name !in setOf("task_type", "name", "model") }
            .sortedBy { it.name }

        group("Task Settings") {
            for (prop in properties) {
                val name = prop.name
                val label = name
                    .replace(Regex("([^_ ])_([^_ ])"), "$1 $2")
                    .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                    .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2")
                    .split(' ').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }

                val description = prop.findAnnotation<Description>()?.value
                val returnType = prop.returnType
                val classifier = returnType.classifier as? KClass<*>

                prop.isAccessible = true
                val currentValue = try {
                    prop.getter.call(config)
                } catch (e: Exception) {
                    null
                }

                if (classifier == Boolean::class) {
                    row {
                        val checkBox = JCheckBox(label, currentValue as? Boolean ?: false)
                        if (description != null) checkBox.toolTipText = description
                        cell(checkBox).comment(description)
                        configFields[name] = checkBox
                    }
                } else if (classifier == String::class) {
                    row(label + ":") {
                        val isTextArea = name.contains("prompt", ignoreCase = true) ||
                                name.contains("code", ignoreCase = true) ||
                                name.contains("thesis", ignoreCase = true) ||
                                name.contains("purpose", ignoreCase = true)
                        if (isTextArea) {
                            val textArea = JBTextArea(currentValue as? String ?: "", 5, 40)
                            textArea.lineWrap = true
                            textArea.wrapStyleWord = true
                            if (description != null) textArea.toolTipText = description
                            cell(JScrollPane(textArea)).align(Align.FILL).comment(description)
                            configFields[name] = textArea
                        } else {
                            val textField = JBTextField(currentValue as? String ?: "")
                            if (description != null) textField.toolTipText = description
                            cell(textField).align(Align.FILL).comment(description)
                            configFields[name] = textField
                        }
                    }
                } else if (classifier == Int::class || classifier == Long::class || classifier == Double::class) {
                    row(label + ":") {
                        val textField = JBTextField(currentValue?.toString() ?: "")
                        if (description != null) textField.toolTipText = description
                        cell(textField).comment(description)
                        configFields[name] = textField
                    }
                } else if (classifier?.java?.isEnum == true) {
                    row(label + ":") {
                        val enumConstants = classifier.java.enumConstants
                        val items = enumConstants.map { it.toString() }.toTypedArray()
                        val comboBox = ComboBox(items)
                        comboBox.selectedItem = currentValue?.toString()
                        if (description != null) comboBox.toolTipText = description
                        cell(comboBox).comment(description)
                        configFields[name] = comboBox
                    }
                } else if (classifier != null && DynamicEnum::class.java.isAssignableFrom(classifier.java)) {
                    row(label + ":") {
                        val companion = classifier.java.getDeclaredField("Companion").get(null)
                        val valuesMethod = companion.javaClass.getMethod("values")
                        val values = valuesMethod.invoke(companion) as List<DynamicEnum<*>>
                        val items = values.map { it.name }.toTypedArray()
                        val comboBox = ComboBox(items)
                        comboBox.selectedItem = (currentValue as? DynamicEnum<*>)?.name
                        if (description != null) comboBox.toolTipText = description
                        cell(comboBox).comment(description)
                        configFields[name] = comboBox
                    }
                }
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
        if (config is SubPlanTask.SubPlanTaskTypeConfig) {
            return getSubPlanConfig()
        }
        return getReflectionConfig()
    }


    private fun getReflectionConfig(): TaskTypeConfig {
        val kClass = config::class
        val constructor = kClass.primaryConstructor ?: throw IllegalStateException("No primary constructor")
        val args = mutableMapOf<KParameter, Any?>()

        for (param in constructor.parameters) {
            val name = param.name
            if (name == "task_type") {
                args[param] = config.task_type
                continue
            }
            if (name == "name") {
                args[param] = configNameField.text.trim()
                continue
            }
            if (name == "model") {
                val selectedModelName = modelCombo.selectedItem as? String
                val selectedModel = availableModels.find { it.modelName == selectedModelName }
                args[param] = selectedModel?.toApiChatModel()
                continue
            }

            val component = configFields[name]
            if (component == null) continue

            val value: Any? = when (component) {
                is JCheckBox -> component.isSelected
                is JBTextField -> {
                    val text = component.text.trim()
                    when (param.type.classifier) {
                        Int::class -> text.toIntOrNull()
                        Long::class -> text.toLongOrNull()
                        Double::class -> text.toDoubleOrNull()
                        else -> text.ifEmpty { null }
                    }
                }

                is JBTextArea -> component.text.trim()
                is ComboBox<*> -> {
                    val selected = component.selectedItem as? String
                    val paramClass = param.type.classifier as? KClass<*>
                    if (selected != null && paramClass?.java?.isEnum == true) {
                        paramClass.java.enumConstants.find { it.toString() == selected }
                    } else if (selected != null && paramClass != null && DynamicEnum::class.java.isAssignableFrom(
                            paramClass.java
                        )
                    ) {
                        val companion = paramClass.java.getDeclaredField("Companion").get(null)
                        val valueOfMethod = companion.javaClass.getMethod("valueOf", String::class.java)
                        try {
                            valueOfMethod.invoke(companion, selected)
                        } catch (e: Exception) {
                            null
                        }
                    } else {
                        null
                    }
                }

                else -> null
            }

            if (value != null) {
                args[param] = value
            } else {
                if (param.type.isMarkedNullable) {
                    args[param] = null
                } else if (param.type.classifier == String::class) {
                    args[param] = ""
                }
            }
        }
        return constructor.callBy(args)
    }

    private fun getSubPlanConfig(): TaskTypeConfig {
        val selectedModelName = modelCombo.selectedItem as? String
        val selectedModel = availableModels.find { it.modelName == selectedModelName }
        val subPlanConfig = config as SubPlanTask.SubPlanTaskTypeConfig
        return SubPlanTask.SubPlanTaskTypeConfig(
            name = configNameField.text.trim(),
            model = selectedModel?.toApiChatModel(),
            purpose = (configFields["purpose"] as? JBTextArea)?.text?.trim() ?: "",
            cognitiveSettings = CognitiveModeType.valueOf(
                (configFields["cognitiveMode"] as? ComboBox<*>)?.selectedItem as? String ?: "Waterfall"
            ).newSettings(),
            taskSettings = subPlanConfig.taskSettings.toMutableMap()
        )
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
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/plan/TaskTypeSelectionDialog.kt

```
package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.plan.tools.TaskType
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.*

class TaskTypeSelectionDialog(
    project: Project?,
    private val allowMultipleSelection: Boolean = false
) : DialogWrapper(project) {

    private val selectedTaskTypes = mutableSetOf<TaskType<*, *>>()
    private val searchField = SearchTextField(false)
    private val descriptionPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        text = if (allowMultipleSelection) {
            "<html><body><p>Select one or more task types to see their descriptions</p></body></html>"
        } else {
            "<html><body><p>Select a task type to see its description</p></body></html>"
        }
    }

    private val taskTree: JTree
    var isQuickSelect = false
        private set


    init {
        val root = DefaultMutableTreeNode("Task Types")
        val treeModel = DefaultTreeModel(root)




        taskTree = JTree(treeModel).apply {
            selectionModel.selectionMode = if (allowMultipleSelection) {
                TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION
            } else {
                TreeSelectionModel.SINGLE_TREE_SELECTION
            }
            isRootVisible = false
            showsRootHandles = true

            // Custom renderer to show task type names
            cellRenderer = object : DefaultTreeCellRenderer() {
                override fun getTreeCellRendererComponent(
                    tree: JTree?,
                    value: Any?,
                    sel: Boolean,
                    expanded: Boolean,
                    leaf: Boolean,
                    row: Int,
                    hasFocus: Boolean
                ): java.awt.Component {
                    val component = super.getTreeCellRendererComponent(
                        tree, value, sel, expanded, leaf, row, hasFocus
                    )

                    if (value is DefaultMutableTreeNode) {
                        val userObject = value.userObject
                        when (userObject) {
                            is TaskTypeNode -> {
                                text = userObject.taskType.name
                                toolTipText = userObject.taskType.description
                            }

                            is String -> {
                                text = userObject
                                toolTipText = null
                            }
                        }
                    }

                    return component
                }
            }

// Add selection listener to update description
            addTreeSelectionListener(object : TreeSelectionListener {
                override fun valueChanged(e: TreeSelectionEvent?) {

                    selectedTaskTypes.clear()

                    val paths = selectionPaths
                    if (paths != null) {
                        paths.forEach { path ->
                            val node = path.lastPathComponent as? DefaultMutableTreeNode
                            val userObject = node?.userObject
                            if (userObject is TaskTypeNode) {
                                selectedTaskTypes.add(userObject.taskType)
                            }
                        }
                    }

                    if (selectedTaskTypes.isNotEmpty()) {
                        updateDescription(selectedTaskTypes.toList())
                    } else {
                        descriptionPane.text = if (allowMultipleSelection) {
                            "<html><body><p>Select one or more task types to see their descriptions</p></body></html>"
                        } else {
                            "<html><body><p>Select a task type to see its description</p></body></html>"
                        }
                    }
                }
            })

            // Add double-click listener to select and OK
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        val path = taskTree.getPathForLocation(e.x, e.y)
                        if (path != null) {
                            val node = path.lastPathComponent as? DefaultMutableTreeNode
                            val userObject = node?.userObject
                            if (userObject is TaskTypeNode) {
                                selectedTaskTypes.clear()
                                selectedTaskTypes.add(userObject.taskType)
                                isQuickSelect = true
                                doOKAction()
                                e.consume()
                            }
                        }
                    }
                }
            })
        }

        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                updateTreeModel(searchField.text)
            }
        })

        updateTreeModel("")

        init()
        title = if (allowMultipleSelection) "Select Task Types" else "Select Task Type"
    }

    override fun getDimensionServiceKey(): String = "TaskTypeSelectionDialog"
    private fun updateTreeModel(filter: String) {
        val root = DefaultMutableTreeNode("Task Types")
        val filterText = filter.trim().lowercase()
        val tasksByPackage = TaskType.values()
            .filter {
                if (filterText.isEmpty()) true
                else it.name.lowercase().contains(filterText) ||
                        (it.description?.lowercase()?.contains(filterText) == true) ||
                        it.category.lowercase().contains(filterText)
            }
            .groupBy { it.category }
            .toSortedMap()
        tasksByPackage.forEach { (packageName, tasks) ->
            val packageNode = DefaultMutableTreeNode(packageName)
            root.add(packageNode)
            tasks.sortedBy { it.name }.forEach { taskType ->
                val taskNode = DefaultMutableTreeNode(TaskTypeNode(taskType))
                packageNode.add(taskNode)
            }
        }
        val model = DefaultTreeModel(root)
        taskTree.model = model
        // Expand all package nodes
        for (i in 0 until root.childCount) {
            taskTree.expandPath(TreePath(arrayOf(root, root.getChildAt(i))))
        }
    }


    private fun updateDescription(taskTypes: List<TaskType<*, *>>) {
        if (taskTypes.isEmpty()) {
            descriptionPane.text = if (allowMultipleSelection) {
                "<html><body><p>Select one or more task types to see their descriptions</p></body></html>"
            } else {
                "<html><body><p>Select a task type to see its description</p></body></html>"
            }
            return
        }

        if (taskTypes.size == 1) {
            val taskType = taskTypes[0]
            descriptionPane.text = buildString {
                this.append("<html><body style='font-family: sans-serif; padding: 10px;'>")
                this.append("<h3 style='margin-top: 0;'>${taskType.name}</h3>")
                this.append("<p><b>Description:</b> ${taskType.description ?: "No description available"}</p>")
                taskType.tooltipHtml?.let { html ->
                    val content = if (html.contains("<body")) {
                        html.substringAfter("<body", "")
                            .substringAfter(">", "")
                            .substringBeforeLast("</body>", html)
                    } else {
                        html.replace("<html>", "").replace("</html>", "")
                    }
                    this.append(content)
                }
                this.append("</body></html>")
            }
            descriptionPane.text = buildString {
                this.append("<html><body style='font-family: sans-serif; padding: 10px;'>")
                this.append("<h3 style='margin-top: 0;'>${taskType.name}</h3>")
                this.append("<p><b>Description:</b> ${taskType.description ?: "No description available"}</p>")
                taskType.tooltipHtml?.let { html ->
                    // Extract content between body tags or use as-is if no body tags
                    val content = if (html.contains("<body")) {
                        html.substringAfter("<body", "")
                            .substringAfter(">", "")
                            .substringBeforeLast("</body>", html)
                    } else {
                        html.replace("<html>", "").replace("</html>", "")
                    }
                    this.append(content)
                }
                this.append("</body></html>")
            }
        } else {
            // Multiple tasks selected - show summary
            descriptionPane.text = buildString {
                this.append("<html><body style='font-family: sans-serif; padding: 10px;'>")
                this.append("<h3 style='margin-top: 0;'>${taskTypes.size} Tasks Selected</h3>")
                this.append("<ul>")
                taskTypes.sortedBy { it.name }.forEach { taskType ->
                    this.append("<li><b>${taskType.name}</b>: ${taskType.description ?: "No description"}</li>")
                }
                this.append("</ul>")
                this.append("</body></html>")
            }
        }
        descriptionPane.caretPosition = 0
    }

    override fun createCenterPanel(): JComponent = panel {
        row {
            cell(searchField).align(Align.FILL)
        }
        row {
            cell(
                JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    JBScrollPane(taskTree).apply {
                        preferredSize = Dimension(300, 400)
                    },
                    JBScrollPane(descriptionPane).apply {
                        preferredSize = Dimension(400, 400)
                    }
                ).apply {
                    dividerLocation = 300
                    resizeWeight = 0.4
                })
                .align(Align.FILL)
        }.resizableRow()
    }.apply {
        preferredSize = Dimension(750, 450)
    }

    override fun doOKAction() {
        if (selectedTaskTypes.isEmpty()) {
            JOptionPane.showMessageDialog(
                contentPane,
                if (allowMultipleSelection) "Please select one or more task types" else "Please select a task type",
                "No Selection",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }
        super.doOKAction()
    }

    fun getSelectedTaskTypes(): List<TaskType<*, *>> = selectedTaskTypes.toList()

    @Deprecated("Use getSelectedTaskTypes() instead", ReplaceWith("getSelectedTaskTypes().firstOrNull()"))
    fun getSelectedTaskType(): TaskType<*, *>? = selectedTaskTypes.firstOrNull()

    private data class TaskTypeNode(val taskType: TaskType<*, *>) {
        override fun toString(): String = taskType.name
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/plan/UnifiedPlanAction.kt

```
package cognotik.actions.plan

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.simiacryptus.cognotik.apps.UnifiedPlanApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

open class UnifiedPlanAction(
    private val useProjectRoot: Boolean = true
) : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        val root: File = if (useProjectRoot) {
            getProjectRoot(e) ?: createTemporaryDirectory(e.project)
        } else {
            createTemporaryDirectory(e.project)
        }
        OrchestrationConfig.instanceFn =
            { model -> model.instance() ?: throw IllegalStateException("Model or Provider not set") }
        val dialog = PlanConfigDialog(
            e.project,
            OrchestrationConfig(
                "Init",
                defaultSmartModel = AppSettingsState.instance.smartModel
                    ?: throw IllegalStateException("Smart model not configured"),
                defaultFastModel = AppSettingsState.instance.fastModel
                    ?: throw IllegalStateException("Fast model not configured"),
                shellCmd = listOf(
                    if (System.getProperty("os.name").lowercase().contains("win")) "powershell" else "bash"
                ),
                temperature = AppSettingsState.instance.temperature.coerceIn(0.0, 1.0),
                workingDir = root.absolutePath,
            ),
        )

        if (dialog.showAndGet()) {
            try {
                val planSettings = dialog.settings
                UITools.runAsync(e.project, "Initializing Unified Plan", true) { progress ->
                    initializeChat(e, progress, planSettings)
                }
            } catch (ex: Exception) {
                log.error("Failed to initialize unified plan", ex)
                UITools.showError(e.project, "Failed to initialize unified plan: ${ex.message}")
            }
        }
    }

    private fun initializeChat(
        e: AnActionEvent,
        progress: ProgressIndicator,
        orchestrationConfig: OrchestrationConfig
    ) {
        val session = Session.newGlobalID()
        progress.text = "Processing files..."
        setupChatSession(
            session,
            orchestrationConfig.copy(
                sessionId = session.sessionId
            )
        )
        progress.text = "Starting server..."
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

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    private fun createTemporaryDirectory(project: Project?): File {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
        val scratchesDir = getScratchesDirectory()
        val tempDir = File(scratchesDir, "cognotik/$timestamp")
        tempDir.mkdirs()
        log.info("Created temporary directory: ${tempDir.absolutePath}")
        return tempDir
    }

    private fun getScratchesDirectory(): File {
        val useSystemPath = AppSettingsState.instance.useScratchesSystemPath
        val basePath = if (useSystemPath) {
            System.getProperty("idea.system.path")
        } else {
            System.getProperty("idea.config.path")
        }
        return if (basePath != null) {
            File(basePath, "scratches")
        } else {
            // Fallback to user home if properties are not set
            File(System.getProperty("user.home"), ".cognotik/scratches")
        }
    }


    private fun setupChatSession(
        session: Session,
        orchestrationConfig: OrchestrationConfig
    ) {
        val app = object : UnifiedPlanApp(
            applicationName = "Unified Planning",
            path = "/unifiedPlan",
            showMenubar = false
        ) {
            override fun instance(model: ApiChatModel) = model.instance()
                ?: throw IllegalStateException("Model or Provider not set")
        }
        app.getSettingsFile(session, UserSettingsManager.defaultUser).writeText(orchestrationConfig.toJson())
        SessionProxyServer.chats[session] = app
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Cognotik",
            inputCnt = when (orchestrationConfig.cognitiveMode) {
                CognitiveModeType.Chat -> 0
                else -> 4
            },
            stickyInput = app.stickyInput,
            showMenubar = app.showMenubar
        )
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
    }

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/plan/UnifiedPlanFromMenuAction.kt

```
package cognotik.actions.plan

/**
 * Version of UnifiedPlanAction that always creates a temporary directory,
 * ignoring any selected files or folders in the project view.
 * This is intended for use from the main menu.
 */
class UnifiedPlanFromMenuAction : UnifiedPlanAction(useProjectRoot = false)
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the new `README.md` for the `cognotik.actions.plan` package, summarizing the planning and orchestration components.

### intellij/src/main/kotlin/cognotik/actions/plan/README.md
```markdown
# Planning and Orchestration Actions

This package contains the IntelliJ IDEA actions and UI components responsible for configuring and launching the **Unified Planning** system. This system allows users to orchestrate complex AI workflows by combining different task types and cognitive strategies.

## Actions

### `UnifiedPlanAction`
The primary entry point for the planning system. It:
1. Determines the working directory (project root or temporary).
2. Displays the `PlanConfigDialog` to gather orchestration settings.
3. Initializes a `UnifiedPlanApp` session.
4. Launches the web-based UI for execution.

### `UnifiedPlanFromMenuAction`
A specialized version of `UnifiedPlanAction` intended for the main menu, which always uses a temporary directory instead of the current project context.

## Configuration Dialogs

### `PlanConfigDialog`
The central hub for orchestration settings. It manages:
- **Saved Configurations**: Loading and saving named presets (e.g., "Last", or user-defined names).
- **Cognitive Modes**: Selecting the high-level strategy (e.g., Chat, Auto Plan, Waterfall).
- **Model Selection**: Assigning default models for smart reasoning, fast parsing, and image processing.
- **Task List**: Managing the sequence of tasks to be executed, including adding, editing, and deleting task configurations.
- **Import/Export**: Support for copying/pasting orchestration settings as JSON via the clipboard.

### `TaskConfigDialog`
Provides detailed configuration for individual tasks. It uses Kotlin reflection to dynamically generate UI fields based on the task's configuration class.
- Supports standard types (String, Int, Boolean, Enums, and `DynamicEnum`).
- Handles complex tasks like `SubPlanTask` which can contain their own nested task configurations.
- Includes validation for task-specific constraints (e.g., timeouts, retry counts, domain formats).

### `CognitiveConfigDialog`
A specialized dialog for fine-tuning the parameters of the selected cognitive mode. Like the task dialog, it is dynamically generated from the configuration properties of the specific `CognitiveModeConfig`.

### `TaskTypeSelectionDialog`
A searchable tree-based UI for selecting new task types to add to a plan. It categorizes tasks and provides rich HTML descriptions and tooltips for each available task type.

## Key Features

- **Dynamic UI Generation**: Most configuration fields are generated at runtime using reflection and the `@Description` annotation, ensuring the UI stays in sync with the underlying data models.
- **Sub-Planning**: Support for recursive task execution where a task can itself be a plan with its own set of sub-tasks and cognitive settings.
- **Model Flexibility**: Allows per-task model overrides or global defaults for different stages of the planning process.
- **Persistence**: Settings are integrated with `AppSettingsState` to persist configurations across IDE restarts.

## Implementation Details

- **UI Framework**: Built using the IntelliJ UI DSL for modern, consistent dialog layouts.
- **Serialization**: Uses JSON for exporting/importing configurations and for passing settings to the web-based execution environment.
- **Web Integration**: Orchestration settings are passed to the `UnifiedPlanApp` which runs in a local web server, allowing for a rich, interactive execution interface.
```

### Summary of Changes
- Created a new `README.md` in `intellij/src/main/kotlin/cognotik/actions/plan/`.
- Documented the primary actions (`UnifiedPlanAction`, `UnifiedPlanFromMenuAction`) and their roles in the planning workflow.
- Detailed the hierarchy of configuration dialogs (`PlanConfigDialog`, `TaskConfigDialog`, `CognitiveConfigDialog`, `TaskTypeSelectionDialog`).
- Highlighted key technical features such as reflection-based UI generation, sub-planning support, and JSON-based configuration management.
- Explained the integration between the IntelliJ plugin UI and the web-based execution environment.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/plan/README.md'>intellij/src/main/kotlin/cognotik/actions/plan/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/plan/README.md'>intellij/src/main/kotlin/cognotik/actions/plan/README.md</a> Updated
