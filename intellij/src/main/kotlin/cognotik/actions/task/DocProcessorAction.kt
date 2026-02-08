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
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.DocProcessor.ModificationTask
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import java.awt.Dimension
import java.util.concurrent.Executors
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
                ?: throw IllegalStateException("Smart model not configured"),
            serverless = false,
            openBrowser = true,
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
                    val session = docProcessor.runAll(
                        selectedTasks, FixedConcurrencyProcessor(
                            Executors.newCachedThreadPool(),
                            docProcessor.concurrencyLimit
                        )
                    )
                    Thread {
                        Thread.sleep(500)
                        try {
                            BrowseUtil.browse(
                                CognotikAppServer.getServer(
                                    AppSettingsState.instance.listeningEndpoint,
                                    AppSettingsState.instance.listeningPort
                                ).server.uri.resolve("/#" + session)
                            )
                        } catch (e: Throwable) {
                            log.warn("Error opening browser", e)
                        }
                    }.start()
                }
            }
        }
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
        }.sortedBy { it.displayName.lowercase() }
            
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
                    checkBoxList.repaint()
                }
                button("Deselect All") {
                    taskItems.forEach { checkBoxList.setItemSelected(it, false) }
                    checkBoxList.repaint()
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
                    <li><b>generates:</b> Single output file from multiple input patterns</li>
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
            val config: DocProcessor.ModificationTaskConfig
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