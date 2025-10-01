package cognotik.actions.plan

 import com.intellij.openapi.project.Project
 import com.intellij.openapi.ui.ComboBox
 import com.intellij.openapi.ui.DialogWrapper
 import com.intellij.openapi.ui.Messages
 import com.intellij.ui.components.JBTextArea
 import com.intellij.ui.components.JBTextField
 import com.intellij.ui.dsl.builder.Align
 import com.intellij.ui.dsl.builder.panel
 import com.simiacryptus.cognotik.chat.model.ChatModel
 import com.simiacryptus.cognotik.interpreter.CodeRuntimes
 import com.simiacryptus.cognotik.plan.TaskType
 import com.simiacryptus.cognotik.plan.TaskTypeConfig
 import com.simiacryptus.cognotik.plan.tools.RunCodeTask
 import com.simiacryptus.cognotik.plan.tools.SelfHealingTask
 import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
 import com.simiacryptus.cognotik.plan.tools.online.FetchMethod
 import com.simiacryptus.cognotik.plan.tools.online.SeedMethod
 import java.awt.Dimension
 import javax.swing.JComponent
 import javax.swing.JScrollPane

class TaskConfigEditDialog(
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

    init {
        init()
        title = "Edit ${taskType.name} Configuration"
    }

    override fun createCenterPanel(): JComponent = panel {
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
    }.apply {
        preferredSize = Dimension(600, 500)
    }

    private fun com.intellij.ui.dsl.builder.Panel.createTaskSpecificFields() {
        when (config) {
            is RunCodeTask.RunCodeTaskTypeConfig -> createRunCodeFields(config)
            is SelfHealingTask.SelfHealingTaskTypeConfig -> createSelfHealingFields(config)
            is CrawlerAgentTask.CrawlerTaskTypeConfig -> createCrawlerFields(config)
            // Add more task types as needed
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.createRunCodeFields(config: RunCodeTask.RunCodeTaskTypeConfig) {
        group("Code Execution Settings") {
            row("Code Runtime:") {
                val runtimes = arrayOf("KotlinRuntime", "PythonRuntime", "JavaRuntime", "NodeJSRuntime")
                val combo = ComboBox(runtimes)
                combo.selectedItem = config.codeRuntime?.name ?: "KotlinRuntime"
                cell(combo)
                    .comment("Select the runtime environment for code execution")
                configFields["codeRuntime"] = combo
            }
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.createSelfHealingFields(config: SelfHealingTask.SelfHealingTaskTypeConfig) {
        group("Self-Healing Settings") {
            row("Auto-fix Commands:") {
                val textArea = JBTextArea(5, 40)
                textArea.text = config.commandAutoFixCommands?.joinToString("\n") ?: ""
                textArea.toolTipText = "Enter one command per line"
                val scrollPane = JScrollPane(textArea)
                cell(scrollPane)
                    .align(Align.FILL)
                    .comment("List of commands that can be used for auto-fixing (one per line)")
                configFields["commandAutoFixCommands"] = textArea
            }
        }
    }

    private fun com.intellij.ui.dsl.builder.Panel.createCrawlerFields(config: CrawlerAgentTask.CrawlerTaskTypeConfig) {
        group("Web Crawler Settings") {
            row("Seed Method:") {
                val methods = SeedMethod.entries.map { it.name }.toTypedArray()
                val combo = ComboBox(methods)
                combo.selectedItem = config.seed_method?.name ?: "GoogleSearch"
                cell(combo)
                    .comment("Method to seed the crawler")
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
        // Validate numeric fields
        configFields.forEach { (key, component) ->
            if (component is JBTextField && key in listOf("timeout", "max_retries", "max_pages", "concurrent_processing")) {
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
        
        val baseConfig = TaskTypeConfig(
            task_type = taskType.name,
            name = configNameField.text.trim(),
            model = selectedModel?.toApiChatModel()
        )
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

            is SelfHealingTask.SelfHealingTaskTypeConfig -> {
                SelfHealingTask.SelfHealingTaskTypeConfig(
                    task_type = baseConfig.task_type,
                    name = baseConfig.name,
                    model = baseConfig.model,
                    commandAutoFixCommands = ((configFields["commandAutoFixCommands"] as? JBTextArea)?.text
                        ?.lines()
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?: emptyList()).toMutableList()
                )
            }

            is CrawlerAgentTask.CrawlerTaskTypeConfig -> {
                CrawlerAgentTask.CrawlerTaskTypeConfig(
                    task_type = baseConfig.task_type!!,
                    name = baseConfig.name,
                    model = baseConfig.model,
                    seed_method = SeedMethod.valueOf(
                        (configFields["seed_method"] as? ComboBox<*>)?.selectedItem as? String
                            ?: "GoogleSearch"
                    ),
                    fetch_method = FetchMethod.valueOf(
                        (configFields["fetch_method"] as? ComboBox<*>)?.selectedItem as? String
                            ?: "HttpClient"
                    )
                )
            }

            else -> baseConfig
        }
    }

    companion object {
        private val CONFIG_NAME_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    }
}