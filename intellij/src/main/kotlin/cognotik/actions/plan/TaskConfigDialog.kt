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
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.tools.newSettings
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask
import com.simiacryptus.cognotik.plan.toApiChatModel
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
        availableModels.distinctBy { it.modelId }
            .map { it.modelId }
            .toTypedArray()
    ).apply {
        preferredSize = Dimension(300, 30)
        selectedItem = config.model?.model?.modelId
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
                val selectedModel = availableModels.find { it.modelId == selectedModelName }
                args[param] = selectedModel?.toApiChatModel(localUser)
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
        val selectedModel = availableModels.find { it.modelId == selectedModelName }
        val subPlanConfig = config as SubPlanTask.SubPlanTaskTypeConfig
        return SubPlanTask.SubPlanTaskTypeConfig(
            name = configNameField.text.trim(),
            model = selectedModel?.toApiChatModel(localUser),
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