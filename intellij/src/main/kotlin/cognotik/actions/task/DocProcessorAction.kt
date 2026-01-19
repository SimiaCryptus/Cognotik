package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.instance
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.DocProcessor
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.util.getModuleRootForFile
import com.simiacryptus.cognotik.util.getSelectedFile
import com.simiacryptus.cognotik.util.getSelectedFiles
import com.simiacryptus.cognotik.util.getSelectedFolder
import java.awt.Dimension
import java.io.File
import javax.swing.JComponent

/**
 * Action that processes markdown documentation files with frontmatter specifications.
 * 
 * This action:
 * 1. Parses selected markdown files for frontmatter with 'specifies', 'documents', or 'transforms' keys
 * 2. Shows a checklist dialog allowing users to select which file generation tasks to run
 * 3. Executes the selected tasks using DocProcessor infrastructure
 */
class DocProcessorAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

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
        val root = getProjectRoot(e) ?: return
        val selectedFiles = e.getSelectedFiles()
            .filter { it.name.lowercase().let { name -> name.endsWith(".md") || name.endsWith(".markdown") } }
            .map { it.toFile }

        if (selectedFiles.isEmpty()) {
            UITools.showError(project, "No markdown files selected")
            return
        }

        UITools.runAsync(project, "Analyzing Documentation Files", true) { progress ->
            progress.text = "Parsing frontmatter..."
            
            val docProcessor = createDocProcessor(root)
            val allTasks = docProcessor.getAll(*selectedFiles.toTypedArray())
            
            if (allTasks.isEmpty()) {
                UITools.showError(project, "No tasks found in selected files. Ensure files have 'specifies', 'documents', or 'transforms' frontmatter.")
                return@runAsync
            }

            // Show dialog on EDT
            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                val dialog = DocProcessorTaskDialog(project, root, allTasks)
                if (dialog.showAndGet()) {
                    val selectedTasks = dialog.getSelectedTasks()
                    if (selectedTasks.isNotEmpty()) {
                        UITools.runAsync(project, "Processing Documentation Tasks", true) { innerProgress ->
                            executeTasks(innerProgress, docProcessor, selectedTasks)
                        }
                    }
                }
            }
        }
    }

    private fun createDocProcessor(root: File): DocProcessor {
        val settings = AppSettingsState.instance
        return DocProcessor(
            root = root,
            docsFolder = root,
            concurrencyLimit = 4,
            fastModel = settings.fastModel?.model ?: throw IllegalStateException("Fast model not configured"),
            smartModel = settings.smartModel?.model ?: throw IllegalStateException("Smart model not configured")
        )
    }

    private fun executeTasks(
        progress: ProgressIndicator,
        docProcessor: DocProcessor,
        tasks: Array<Pair<FileModificationTaskExecutionConfigData, PatchProcessors>>
    ) {
        progress.text = "Executing ${tasks.size} task(s)..."
        progress.isIndeterminate = false
        
        val concurrencyProcessor = com.simiacryptus.cognotik.util.FixedConcurrencyProcessor(
            java.util.concurrent.Executors.newCachedThreadPool(),
            docProcessor.concurrencyLimit
        )
        
        docProcessor.runAll(tasks, concurrencyProcessor)
        
        progress.text = "Completed ${tasks.size} task(s)"
    }

    private fun getProjectRoot(e: AnActionEvent): File? {
        val folder = e.getSelectedFolder()
        return folder?.toFile ?: e.getSelectedFile()?.parent?.toFile?.let { file ->
            getModuleRootForFile(file)
        }
    }

    /**
     * Dialog that displays a checklist of file generation tasks for user selection.
     */
    class DocProcessorTaskDialog(
        project: Project?,
        private val root: File,
        private val allTasks: Array<Pair<FileModificationTaskExecutionConfigData, PatchProcessors>>
    ) : DialogWrapper(project) {

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
            }
            
            checkBoxList.setItems(taskItems) { it.displayName }
            taskItems.forEach { checkBoxList.setItemSelected(it, true) }
            
            init()
        }

        override fun createCenterPanel(): JComponent = panel {
            row {
                label("Select which file generation tasks to execute:")
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
                        </ul>
                    """.trimIndent())
                }
            }
        }

        fun getSelectedTasks(): Array<Pair<FileModificationTaskExecutionConfigData, PatchProcessors>> {
            return taskItems
                .filter { checkBoxList.isItemSelected(it) }
                .map { allTasks[it.index] }
                .toTypedArray()
        }

        data class TaskItem(
            val index: Int,
            val displayName: String,
            val description: String,
            val config: FileModificationTaskExecutionConfigData
        ) {
            override fun toString(): String = "$displayName - $description"
        }
    }
}