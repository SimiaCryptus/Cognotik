package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.newSettings
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.DocProcessor.Companion.newProcessor
import com.simiacryptus.cognotik.util.DocProcessor.ModificationTask
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.chat.BasicChatApp
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.linkToSession
import java.awt.Dimension
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * Action that processes markdown documentation files with frontmatter specifications.
 * 
 * This action:
 * 1. Parses selected markdown files for frontmatter with 'specifies', 'documents', or 'transforms' keys
 * 2. Shows a checklist dialog allowing users to select which file generation tasks to run
 * 3. Executes the selected tasks using DocProcessor infrastructure
 */
open class DocProcessorAction(
    val mode: UpdateMode = UpdateModes.PatchExisting,
) : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    companion object {
        /**
         * Returns a pretty label for each overwrite mode
         */
        fun getModeLabel(mode: UpdateModes): String = when (mode) {
            UpdateModes.SkipExisting -> "🚫 Skip Existing Files"
            UpdateModes.OverwriteExisting -> "🔄 Overwrite All Files"
            UpdateModes.OverwriteToUpdate -> "📅 Overwrite Outdated Files"
            UpdateModes.PatchExisting -> "🩹 Patch Existing Files"
            UpdateModes.PatchToUpdate -> "📝 Patch Outdated Files"
            UpdateModes.ForceOverwrite -> "🔥 Force Overwrite (Dangerous)"
            UpdateModes.ForceUpdate -> "⚡ Force Update (Dangerous)"
        }
        /**
         * Returns a description for each overwrite mode
         */
        fun getModeDescription(mode: UpdateModes): String = when (mode) {
            UpdateModes.SkipExisting -> "Skip files that already exist, only create new files"
            UpdateModes.OverwriteExisting -> "Replace all target files with newly generated content"
            UpdateModes.OverwriteToUpdate -> "Replace only files older than their source documentation"
            UpdateModes.PatchExisting -> "Apply intelligent patches to existing files"
            UpdateModes.PatchToUpdate -> "Apply patches only to files older than their source documentation"
            UpdateModes.ForceOverwrite -> "Delete all target files before generation (use with caution)"
            UpdateModes.ForceUpdate -> "Delete target files older than their source documentation before generation (use with caution)"
        }
        fun newBasicSession(
            root: File,
            model: ChatModel?,
            title: String = "Documentation Processor"
        ): SocketManager = BasicChatApp(
            root = root,
            model = model ?: throw IllegalStateException("Smart model not configured"),
            parsingModel = model,
        ).newSession(session = Session.newUserID()).let { socketManager ->
            SessionProxyServer.agents[socketManager.sessionId] = socketManager
            ApplicationServer.appInfoMap[socketManager.sessionId] = AppInfoData(
                applicationName = title,
                inputCnt = 1,
                stickyInput = false,
                loadImages = false,
                showMenubar = false
            )
            try {
                BrowseUtil.browse(
                    CognotikAppServer.getServer(
                        AppSettingsState.instance.listeningEndpoint,
                        AppSettingsState.instance.listeningPort
                    ).server.uri.resolve("/#" + socketManager.sessionId)
                )
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
            socketManager
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
          updateMode = mode,
          fastModel = AppSettingsState.instance.fastModel?.model
            ?: throw IllegalStateException("Fast model not configured"),
          smartModel = AppSettingsState.instance.smartModel?.model
            ?: throw IllegalStateException("Smart model not configured"),
          autoFix = true,
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
                    val totalTasks = selectedTasks.size
                    ProgressManager.getInstance().run(object : Task.Backgroundable(
                        project, "Processing Documentation Tasks", true
                    ) {
                        override fun run(indicator: ProgressIndicator) {
                            run(
                                indicator,
                                totalTasks,
                                root,
                                AppSettingsState.instance.smartModel?.model,
                                docProcessor,
                                selectedTasks,
                            )
                        }
                    })
                }
            }
        }
    }

    private fun run(
        indicator: ProgressIndicator,
        totalTasks: Int,
        root: File,
        model: ChatModel?,
        docProcessor: DocProcessor,
        selectedTasks: List<ModificationTask>,
    ) {
        indicator.isIndeterminate = false
        indicator.fraction = 0.0
        indicator.text = "Processing $totalTasks documentation task(s)..."
        val cancelFlag = AtomicBoolean(false)
        val sessions = mutableListOf<Session>()
        val scheduledFutures = mutableListOf(scheduledPool.scheduleAtFixedRate({
            if (indicator.isCanceled && !cancelFlag.get()) {
                cancelFlag.set(true)
                val threadPoolManager = ApplicationServices.threadPoolManager
                sessions.forEach {
                    try {
                        threadPoolManager.getPool(it).shutdown()
                        threadPoolManager.getScheduledPool(it).shutdown()
                    } catch (e: Throwable) {
                        log.warn("Error closing session $it", e)
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS))
        val completedTasks = AtomicInteger(0)
        try {
            val masterTask = newBasicSession(root, model)
                .newTask(root = true)
            val sessions = mutableListOf<Session>()
            docProcessor.separateQueues(selectedTasks).map { docProcessor.sortByDependencies(it) }
                .filter { it.isNotEmpty() }
                .map { mods ->
                    newProcessor().submit {
                        object : UnifiedHarness(
                            fastModel = docProcessor.fastModel,
                            smartModel = docProcessor.smartModel,
                            serverless = docProcessor.serverless,
                            openBrowser = docProcessor.openBrowser,
                        ) {
                            override fun createTempDirectory(prefix: String) = docProcessor.root
                                .resolve("workspaces/${javaClass.simpleName}/test-${PlanHarness.now()}")
                                .apply { mkdirs() }
                        }.use { harness ->
                            if (cancelFlag.get()) {
                                log.info("Cancellation requested, skipping execution of remaining tasks")
                                return@submit
                            }
                            val sessionStatusMap = mutableMapOf<Session, StringBuilder?>()
                            mods.forEach { mod ->
                                val mod = mod.rebase(
                                    docProcessor.root,
                                    mod.data.relative_files?.firstOrNull()
                                        ?.let { docProcessor.root.resolve(it).parentFile }
                                        ?: docProcessor.root)
                                harness.resetSession()
                                if (cancelFlag.get()) {
                                    log.info("Cancellation requested, skipping execution of remaining tasks")
                                    throw CancellationException("Execution cancelled")
                                }
                                val session = harness.runTask(
                                    taskType = mod.taskType,
                                    timeoutMinutes = 30,
                                    message = mod.message(docProcessor.root),
                                    executionConfig = docProcessor.executionConfig(mod, harness)
                                ) { session ->
                                    if (cancelFlag.get()) {
                                        log.info("Cancellation requested, skipping execution of remaining tasks")
                                        throw CancellationException("Execution cancelled")
                                    }
                                    sessionStatusMap[session] =
                                        masterTask.add(
                                            session.linkToSession(
                                                "${mod.taskType.name}: ${mod.data.files
                                                    ?.map { mod.data.root.resolve(it) }
                                                    ?.joinToString(", "){it.absolutePath}
                                                    ?: "No files specified"
                                                }"
                                            )
                                        )
                                    sessions += session
                                    val completed1 = completedTasks.incrementAndGet()
                                    indicator.fraction = completed1.toDouble() / totalTasks
                                    indicator.text =
                                        "Processing task $completed1 of $totalTasks..."
                                    indicator.text2 = "Session: $session"
                                    sessions += session
                                    harness.createSettings(
                                        session = session,
                                        autoFix = docProcessor.autoFix,
                                        typeConfig = mod.taskType.newSettings() ?: TaskTypeConfig(
                                            task_type = mod.taskType.name
                                        ),
                                        workingDir = mod.data.root.toString()
                                    ).apply {
                                        processor = mod.patchProcessor
                                    }
                                }
                                sessionStatusMap[session]?.append(" (Complete)")
                                masterTask.update()
                            }
                        }
                    }
                }.let {
                    CompletableFuture.allOf(*it.toTypedArray()).get(90, TimeUnit.MINUTES)
                }
            sessions.toTypedArray<Session>()
        } finally {
            indicator.fraction = 1.0
            indicator.text = "Documentation processing complete"
            scheduledFutures.forEach { it.cancel(false) }
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
        private val searchField = JBTextField().apply {
            emptyText.text = "Type to filter tasks..."
        }

        init {
            title = "Select Documentation Tasks"
            
            taskItems = allTasks.mapIndexed { index, t ->
                val config = t.data
                val targetFiles = config.relative_files?.joinToString(", ") ?: throw IllegalStateException("No target files specified")
                val relatedFiles = config.relative_related_files?.take(3)?.joinToString(", ") ?: ""
                val description = buildString {
                    append("Target: $targetFiles")
                    if (relatedFiles.isNotEmpty()) {
                        append(" | Related: $relatedFiles")
                        if ((config.relative_related_files?.size ?: 0) > 3) {
                            append("...")
                        }
                    }
                }
                TaskItem(index, targetFiles, description, config)
        }.sortedBy { it.displayName.lowercase() }
            
            checkBoxList.setItems(taskItems) { it.displayName }
            taskItems.forEach { checkBoxList.setItemSelected(it, true) }
            searchField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    filterTasks(searchField.text)
                }
            })
            

            init()
        }
        private val selectedStates = mutableMapOf<Int, Boolean>()
        private fun filterTasks(query: String) {
            // Save current selection states
            taskItems.forEach { item ->
                selectedStates[item.index] = checkBoxList.isItemSelected(item)
            }
            val filteredItems = if (query.isBlank()) {
                taskItems
            } else {
                val lowerQuery = query.lowercase()
                taskItems.filter { item ->
                    item.displayName.lowercase().contains(lowerQuery) ||
                    item.description.lowercase().contains(lowerQuery)
                }
            }
            checkBoxList.setItems(filteredItems) { it.displayName }
            filteredItems.forEach { item ->
                checkBoxList.setItemSelected(item, selectedStates.getOrDefault(item.index, true))
            }
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
                cell(searchField)
                    .align(Align.FILL)
            }
            row {
                button("Select All") {
                    for (i in 0 until checkBoxList.itemsCount) {
                        val item = checkBoxList.getItemAt(i)
                        if (item != null) checkBoxList.setItemSelected(item, true)
                    }
                    checkBoxList.repaint()
                }
                button("Deselect All") {
                    for (i in 0 until checkBoxList.itemsCount) {
                        val item = checkBoxList.getItemAt(i)
                        if (item != null) checkBoxList.setItemSelected(item, false)
                    }
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

        fun getSelectedTasks(): List<ModificationTask> {
            // Save current visible selection states
            for (i in 0 until checkBoxList.itemsCount) {
                val item = checkBoxList.getItemAt(i)
                if (item != null) {
                    selectedStates[item.index] = checkBoxList.isItemSelected(item)
                }
            }
            return taskItems
                .filter { selectedStates.getOrDefault(it.index, true) }
                .map { allTasks[it.index] }
        }

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
        return UpdateModes.entries.map { mode ->
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