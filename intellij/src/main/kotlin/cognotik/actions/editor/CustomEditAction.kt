package cognotik.actions.editor

import cognotik.actions.SelectionAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.simiacryptus.cognotik.actors.ProxyAgent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.UITools
import javax.swing.JOptionPane

/**
 * Action that allows custom editing of code selections using AI.
 * Supports multiple languages and provides custom edit instructions.
 */
open class CustomEditAction : SelectionAction<String>(requiresSelection = true) {
    private val log = Logger.getInstance(CustomEditAction::class.java)
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    interface VirtualAPI {
        fun editCode(
            code: String,
            operation: String,
            computerLanguage: String,
            humanLanguage: String
        ): EditedText

        data class EditedText(
            var code: String? = null,
            var language: String? = null
        )
    }

    val proxy: VirtualAPI
        get() {
            val proxyAgent = ProxyAgent(
                clazz = VirtualAPI::class.java,
                model = AppSettingsState.instance.smartChatClient,
                temperature = AppSettingsState.instance.temperature,
            )
            proxyAgent.addExample(
                VirtualAPI.EditedText(
                    """

                println("Hello, World!")
                """.trimIndent(),
                    "java"
                )
            ) {
                it.editCode(
                    """println("Hello, World!")""",
                    "Add code comments",
                    "java",
                    "English"
                )
            }
            return proxyAgent.create()
        }

    override fun getConfig(project: Project?): String? {
        return UITools.showInputDialog(
            null,
            "Enter edit instruction:",
            "Edit Code",
            JOptionPane.QUESTION_MESSAGE
        ) as String?
    }

    override fun processSelection(state: SelectionState, config: String?, progress: ProgressIndicator): String {
        if (config.isNullOrBlank()) return state.selectedText ?: ""
        return try {
            progress.isIndeterminate = true
            progress.text = "Applying edit: $config"
            val settings = AppSettingsState.instance
            val outputHumanLanguage = ""
            settings.getRecentCommands("customEdits").addInstructionToHistory(config)
            val result = proxy.editCode(
                state.selectedText ?: "",
                config,
                state.language?.name ?: state.editor?.virtualFile?.extension ?: "unknown",
                outputHumanLanguage
            )
            result.code ?: state.selectedText ?: ""
        } catch (e: Exception) {
            log.error("Failed to process edit", e)
            UITools.showErrorDialog(
                "Failed to process edit: ${e.message}",
                "Edit Error"
            )
            state.selectedText ?: ""
        }
    }
}