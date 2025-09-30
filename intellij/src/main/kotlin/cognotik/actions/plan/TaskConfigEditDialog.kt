package cognotik.actions.plan

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import java.awt.Dimension
import javax.swing.JComponent

class TaskConfigEditDialog(
    project: Project?,
    private val taskType: TaskType<*, *>,
    private val config: TaskTypeConfig,
    private val availableModels: List<ChatModel>
) : DialogWrapper(project) {

    private val configNameField = JBTextField(config.name ?: "").apply {
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

        group("Task Type Information") {
            row {
                text(taskType.description ?: "No description available")
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

        super.doOKAction()
    }

    fun getConfig(): TaskTypeConfig {
        val selectedModelName = modelCombo.selectedItem as? String
        val selectedModel = availableModels.find { it.modelName == selectedModelName }
        
        return TaskTypeConfig(
            task_type = taskType.name,
            name = configNameField.text.trim(),
            model = selectedModel?.toApiChatModel()
        )
    }

    companion object {
        private val CONFIG_NAME_PATTERN = Regex("^[a-zA-Z0-9_-]+$")
    }
}