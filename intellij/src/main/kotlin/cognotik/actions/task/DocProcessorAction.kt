package cognotik.actions.task

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.simiacryptus.cognotik.apps.SessionProxyServer
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.AppSettingsState.Companion.localUser
import com.simiacryptus.cognotik.docops.DocProcessor
import com.simiacryptus.cognotik.docops.DocProcessor.Companion.newProcessor
import com.simiacryptus.cognotik.docops.PlatformTaskKind
import com.simiacryptus.cognotik.docops.UpdateModes
import com.simiacryptus.cognotik.docops.model.WorkPlan
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServicesImpl
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import com.simiacryptus.cognotik.webui.session.BasicChatApp
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.linkToSession
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.io.File
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener

/**
 * Action that processes markdown documentation files with frontmatter specifications.
 *
 * This action:
 * 1. Plans work for the selected markdown files via [DocProcessor]/`DocOps` (planning is pure -
 *    nothing is written or deleted while the dialog is open)
 * 2. Shows a checklist dialog allowing users to select the update mode and which targets to build
 * 3. Re-plans with the chosen update mode, narrows the plan to the selected targets, and hands the
 *    plan to `DocOps.run` (which owns queues, sessions, cancellation, timeouts and status)
 */
class DocProcessorAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    init {
        templatePresentation.text = "📋 Build Related"
        templatePresentation.description = "Process markdown documentation files with frontmatter specifications"
    }

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
            root: File, model: ChatModel?, title: String = "Documentation Processor", fastModel: ChatModel?
        ): SocketManager = BasicChatApp(
            root = root,
            model = model?.modelId,
            fastModel = fastModel?.modelId,
        ).newSession(
            localUser, session = Session.newUserID()
        )?.let { socketManager ->
            SessionProxyServer.agents[socketManager.sessionId] = socketManager
            ApplicationServer.appInfoMap[socketManager.sessionId] = AppInfoData(
                applicationName = title, inputCnt = 1, stickyInput = false, loadImages = false, showMenubar = false
            )
            try {
                BrowseUtil.browse(
                    CognotikAppServer.getServer(
                        AppSettingsState.instance.listeningEndpoint, AppSettingsState.instance.listeningPort
                    ).server.uri.resolve("/#" + socketManager.sessionId)
                )
            } catch (e: Throwable) {
                log.warn("Error opening browser", e)
            }
            socketManager
        } ?: throw RuntimeException("Failed to create chat session")
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

        Thread {
            try {
                // Discover template variables declared in any selected markdown file's
                // frontmatter so the user can override them before processing.
                val declaredTemplateVars: Map<String, String> = collectDeclaredTemplateVars(selectedFiles)
                val templateVarOverrides: Map<String, String> = if (declaredTemplateVars.isNotEmpty()) {
                    val future = CompletableFuture<Map<String, String>?>()
                    ApplicationManager.getApplication().invokeAndWait {
                        val dialog = TemplateVarsDialog(project, declaredTemplateVars)
                        if (dialog.showAndGet()) {
                            future.complete(dialog.getValues())
                        } else {
                            future.complete(null)
                        }
                    }
                    // User cancelled the template variable dialog -> abort entire action.
                    future.get() ?: return@Thread
                } else {
                    emptyMap()
                }

                // Planning is pure: this touches nothing on disk, so it is safe to do before the
                // user has picked an update mode. The mode only affects which targets are skipped,
                // so the plan is rebuilt once the mode is known.
                val discoveryPlan = createDocProcessor(root, UpdateModes.PatchExisting, templateVarOverrides)
                    .getAll(*selectedFiles.toTypedArray())

                if (discoveryPlan.tasks.isEmpty()) {
                    UITools.showError(
                        project,
                        buildString {
                            append("No tasks found in selected files. ")
                            append("Ensure files have 'specifies', 'documents', 'transforms', 'generates' or 'folder' frontmatter.")
                            if (discoveryPlan.skipped.isNotEmpty() || discoveryPlan.failed.isNotEmpty()) {
                                append("\n\n${discoveryPlan.skipped.size} target(s) skipped, ")
                                append("${discoveryPlan.failed.size} target(s) failed to plan - see the log for details.")
                            }
                        }
                    )
                    discoveryPlan.failed.forEach { log.warn("Planning failed for ${it.target}", it.error) }
                    return@Thread
                }

                // Show dialog on EDT - includes mode selector
                val dialogResult = CompletableFuture<DocProcessorTaskDialog.DialogResult?>()
                ApplicationManager.getApplication().invokeAndWait {
                    val dialog = DocProcessorTaskDialog(project, discoveryPlan, root)
                    if (dialog.showAndGet()) {
                        dialogResult.complete(dialog.getResult())
                    } else {
                        dialogResult.complete(null)
                    }
                }
                val result = dialogResult.get() ?: return@Thread
                if (result.selectedTargets.isEmpty()) return@Thread

                val docProcessor = createDocProcessor(
                    root = root,
                    updateMode = result.selectedMode,
                    templateVarOverrides = templateVarOverrides,
                    autoFix = result.autoFix,
                )
                // Re-plan with the selected update mode, then narrow to the user's selection.
                val plan = docProcessor.getAll(*selectedFiles.toTypedArray())
                    .filter { it.target.file in result.selectedTargets }
                if (plan.tasks.isEmpty()) {
                    UITools.showError(
                        project,
                        "Nothing to do: the selected targets were all skipped by update mode ${result.selectedMode.name}."
                    )
                    return@Thread
                }

                ApplicationManager.getApplication().invokeLater {
                    ProgressManager.getInstance().run(object : Task.Backgroundable(
                        project, "Processing Documentation Tasks", true
                    ) {
                        override fun run(indicator: ProgressIndicator) {
                            run(
                                indicator = indicator,
                                root = root,
                                model = AppSettingsState.instance.smartModel?.model,
                                fastModel = AppSettingsState.instance.fastModel?.model,
                                docProcessor = docProcessor,
                                plan = plan,
                            )
                        }
                    })
                }
            } catch (ex: Throwable) {
                log.warn("Error preparing documentation tasks", ex)
                UITools.showError(project, "Error preparing documentation tasks: ${ex.message}")
            }
        }.start()
    }

    /**
     * Scan the given markdown files for frontmatter-declared template variables
     * and return a merged map of name -> default value. When the same variable
     * is declared in multiple files with different defaults, the first
     * encountered default wins (subsequent occurrences are ignored). Files that
     * fail to parse are skipped with a warning.
     */
    private fun collectDeclaredTemplateVars(files: List<File>): Map<String, String> =
        DocProcessor.listTemplateVarKeys(files)

    /** All model wiring in one place; every model falls back to whatever is configured. */
    private fun createDocProcessor(
        root: File,
        updateMode: UpdateModes,
        templateVarOverrides: Map<String, String>,
        autoFix: Boolean = true,
    ): DocProcessor {
        val settings = AppSettingsState.instance
        val smartModel = settings.smartModel?.model
            ?: settings.fastModel?.model
            ?: throw IllegalStateException("Smart model not configured")
        val fastModel = settings.fastModel?.model ?: smartModel
        val imageModel = settings.imageChatModel?.model ?: fastModel
        val audioModel = settings.audioModel?.model ?: fastModel
        return DocProcessor(
            root = root,
            docsFolder = root,
            updateMode = updateMode,
            smartModel = smartModel,
            fastModel = fastModel,
            imageModel = imageModel,
            audioModel = audioModel,
            autoFix = autoFix,
            user = localUser,
            templateVarOverrides = templateVarOverrides,
            showMenubar = false,
        )
    }

    /**
     * Executes the plan. Queues, sessions, per-task timeouts, the overall timeout and status
     * tracking are all owned by `DocOps`/`DocTaskRunner`; this method only wires the progress
     * indicator, the cancel flag and the master task log.
     */
    private fun run(
        indicator: ProgressIndicator,
        root: File,
        model: ChatModel?,
        fastModel: ChatModel?,
        docProcessor: DocProcessor,
        plan: WorkPlan<PlatformTaskKind>,
    ) {
        val totalTasks = plan.tasks.size
        indicator.isIndeterminate = false
        indicator.fraction = 0.0
        indicator.text = "Processing $totalTasks documentation task(s)..."

        val cancelFlag = AtomicBoolean(false)
        val sessions = Collections.synchronizedList(mutableListOf<Session>())
        val sessionStatusMap = ConcurrentHashMap<Session, StringBuilder>()
        val startedTasks = AtomicInteger(0)
        val scheduledFutures = mutableListOf(scheduledPool.scheduleAtFixedRate({
            if (indicator.isCanceled && !cancelFlag.get()) {
                cancelFlag.set(true)
                val threadPoolManager = ApplicationServicesImpl.threadPoolManager
                sessions.toList().forEach {
                    try {
                        threadPoolManager.getPool(it, localUser).shutdown()
                        threadPoolManager.getScheduledPool(it, localUser).shutdown()
                    } catch (e: Throwable) {
                        log.warn("Error closing session $it", e)
                    }
                }
            }
        }, 0, 1, TimeUnit.SECONDS))

        try {
            val masterTask = newBasicSession(root, model, fastModel = fastModel).newTask(root = true)
            plan.skipped.forEach { masterTask.add("Skipped ${it.target.name}: ${it.reason}") }
            plan.failed.forEach {
                log.warn("Planning failed for ${it.target}", it.error)
                masterTask.add("Failed to plan ${it.target.name}: ${it.error.message ?: it.error.toString()}")
            }
            masterTask.update()

            docProcessor.runAll(
                plan = plan,
                pool = newProcessor(user = localUser),
                cancelFlag = cancelFlag,
            ) { session ->
                sessions += session
                val started = startedTasks.incrementAndGet()
                indicator.fraction = started.toDouble() / totalTasks
                indicator.text = "Processing task $started of $totalTasks..."
                indicator.text2 = "Session: $session"
                masterTask.add(session.linkToSession("Task $started of $totalTasks"))
                    ?.let { sessionStatusMap[session] = it }
                masterTask.update()
            }

            // Summarize using the status store the runner wrote.
            runCatching {
                val status = docProcessor.docOps.statusStore.read()
                val counts = status.tasks.values.groupingBy { it.status }.eachCount()
                masterTask.add(counts.entries.joinToString(", ") { "${it.key}: ${it.value}" })
                masterTask.update()
            }.onFailure { log.debug("Could not read doc-ops status", it) }
        } catch (ex: Throwable) {
            log.warn("Documentation processing failed", ex)
            throw ex
        } finally {
            indicator.fraction = 1.0
            indicator.text = "Documentation processing complete"
            scheduledFutures.forEach { it.cancel(false) }
        }
    }

    /**
     * Dialog that displays a checklist of planned targets for user selection.
     */
    class DocProcessorTaskDialog(
        project: Project?,
        private val plan: WorkPlan<PlatformTaskKind>,
        private val root: File,
    ) : DialogWrapper(project) {
        var autoFix: Boolean = true
        private val modeComboBox = ComboBox(UpdateModes.entries.toTypedArray()).apply {
            selectedItem = UpdateModes.PatchExisting
            renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): java.awt.Component {
                    val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is UpdateModes) {
                        text = getModeLabel(value)
                        toolTipText = getModeDescription(value)
                    }
                    return comp
                }
            }
        }
        private val modeDescriptionLabel = JLabel(getModeDescription(UpdateModes.PatchExisting)).apply {
            font = font.deriveFont(font.size2D - 1f)
        }
        private val checkBoxList = CheckBoxList<TaskItem>()
        private var taskItems: List<TaskItem> = emptyList()
        private val searchField = JBTextField().apply {
            emptyText.text = "Type to filter targets..."
        }
        private val selectedStates = mutableMapOf<Int, Boolean>()
        private val selectionCountLabel = JLabel()
        private var currentPopup: Popup? = null
        private var currentHoveredItem: TaskItem? = null
        private val popupShowTimer = Timer(400) { showPopupForCurrentItem() }.apply { isRepeats = false }

        init {
            title = "Select Documentation Tasks"
            modeComboBox.addActionListener {
                val selected = modeComboBox.selectedItem as? UpdateModes ?: return@addActionListener
                modeDescriptionLabel.text = getModeDescription(selected)
            }

            taskItems = plan.tasks.mapIndexed { index, task ->
                val displayName = task.target.relativeToOrAbsolute(root)
                TaskItem(
                    index = index,
                    target = task.target.file,
                    displayName = displayName,
                    description = "Target: $displayName",
                    details = task.toString(),
                )
            }.sortedBy { it.displayName.lowercase() }

            checkBoxList.setItems(taskItems) { it.displayName }
            taskItems.forEach { item ->
                selectedStates[item.index] = true
                checkBoxList.setItemSelected(item, true)
            }
            searchField.document.addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    filterTasks(searchField.text)
                }
            })
            // Add mouse motion listener for hover details
            checkBoxList.addMouseMotionListener(object : java.awt.event.MouseMotionAdapter() {
                override fun mouseMoved(e: java.awt.event.MouseEvent) {
                    val index = checkBoxList.locationToIndex(e.point)
                    if (index >= 0 && index < checkBoxList.model.size) {
                        val item = checkBoxList.getItemAt(index)
                        if (item != null && item != currentHoveredItem) {
                            currentHoveredItem = item
                            popupShowTimer.restart()
                        }
                    } else {
                        currentHoveredItem = null
                        popupShowTimer.stop()
                        hidePopup()
                    }
                }
            })
            checkBoxList.addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseExited(e: java.awt.event.MouseEvent) {
                    currentHoveredItem = null
                    popupShowTimer.stop()
                    hidePopup()
                }

                override fun mousePressed(e: java.awt.event.MouseEvent) {
                    if (e.isPopupTrigger) showContextMenu(e)
                }

                override fun mouseReleased(e: java.awt.event.MouseEvent) {
                    if (e.isPopupTrigger) showContextMenu(e)
                }
            })
            // Also hide popup on scroll
            checkBoxList.addHierarchyListener {
                hidePopup()
            }

            // Track checkbox selection changes
            checkBoxList.addListSelectionListener(object : ListSelectionListener {
                override fun valueChanged(e: ListSelectionEvent?) {
                    syncVisibleSelectionStates()
                    updateSelectionCount()
                }
            })
            // Also listen for item changes (checkbox toggles)
            checkBoxList.model.addListDataListener(object : javax.swing.event.ListDataListener {
                override fun intervalAdded(e: javax.swing.event.ListDataEvent?) {}
                override fun intervalRemoved(e: javax.swing.event.ListDataEvent?) {}
                override fun contentsChanged(e: javax.swing.event.ListDataEvent?) {
                    syncVisibleSelectionStates()
                    updateSelectionCount()
                }
            })
            updateSelectionCount()

            init()
        }

        private fun File.relativeToRootOr(root: File): String =
            runCatching { relativeTo(root).path.ifBlank { name } }.getOrElse { path }

        private fun showPopupForCurrentItem() {
            val item = currentHoveredItem ?: return
            hidePopup()
            val detailsText = buildDetailsText(item)
            val textArea = JTextArea(detailsText).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
                background = javax.swing.UIManager.getColor("ToolTip.background") ?: java.awt.Color(255, 255, 225)
                foreground = javax.swing.UIManager.getColor("ToolTip.foreground") ?: java.awt.Color.BLACK
                font = javax.swing.UIManager.getFont("ToolTip.font") ?: font
                columns = 60
            }
            // Let the text area calculate its preferred size based on content
            val textPreferredSize = textArea.preferredSize
            val maxWidth = 500
            val maxHeight = 300
            val minWidth = 200
            val minHeight = 60
            val contentWidth = textPreferredSize.width.coerceIn(minWidth, maxWidth) + 20 // padding for scrollbar
            val contentHeight = textPreferredSize.height.coerceIn(minHeight, maxHeight) + 20
            val scrollPane = JScrollPane(textArea).apply {
                preferredSize = Dimension(contentWidth, contentHeight)
                border = BorderFactory.createLineBorder(java.awt.Color.GRAY)
            }
            try {
                val mousePos = checkBoxList.mousePosition ?: return
                val screenPos = Point(mousePos)
                SwingUtilities.convertPointToScreen(screenPos, checkBoxList)
                currentPopup = PopupFactory.getSharedInstance().getPopup(
                    checkBoxList, scrollPane, screenPos.x + 15, screenPos.y + 15
                )
                currentPopup?.show()
            } catch (_: Exception) {
                // Ignore if component is not showing
            }
        }

        private fun showContextMenu(e: java.awt.event.MouseEvent) {
            val index = checkBoxList.locationToIndex(e.point)
            if (index < 0 || index >= checkBoxList.model.size) return
            val item = checkBoxList.getItemAt(index) ?: return
            val popupMenu = JPopupMenu()
            val detailsMenuItem = JMenuItem("Details...")
            detailsMenuItem.addActionListener {
                showDetailsDialog(item)
            }
            popupMenu.add(detailsMenuItem)
            popupMenu.show(checkBoxList, e.x, e.y)
        }

        private fun showDetailsDialog(item: TaskItem) {
            hidePopup()
            popupShowTimer.stop()
            val detailsText = buildDetailsText(item)
            val textArea = JTextArea(detailsText).apply {
                isEditable = false
                lineWrap = true
                wrapStyleWord = true
                border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
                columns = 60
                rows = 15
            }
            val scrollPane = JScrollPane(textArea)
            val ownerWindow = SwingUtilities.getWindowAncestor(checkBoxList)
            val dialog = JDialog(ownerWindow, "Details: ${item.displayName}").apply {
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                contentPane.layout = BorderLayout()
                contentPane.add(scrollPane, BorderLayout.CENTER)
                isResizable = true
                preferredSize = Dimension(550, 400)
                minimumSize = Dimension(300, 200)
                pack()
                setLocationRelativeTo(ownerWindow)
            }
            dialog.isVisible = true
        }

        private fun hidePopup() {
            currentPopup?.hide()
            currentPopup = null
        }

        override fun dispose() {
            hidePopup()
            popupShowTimer.stop()
            super.dispose()
        }

        private fun buildDetailsText(item: TaskItem): String = buildString {
            appendLine("Target:")
            appendLine("  ${item.target.absolutePath}")
            appendLine()
            appendLine("Planned task:")
            appendLine("  ${item.details}")
        }.trimEnd()

        private fun syncVisibleSelectionStates() {
            for (i in 0 until checkBoxList.itemsCount) {
                val item = checkBoxList.getItemAt(i)
                if (item != null) {
                    selectedStates[item.index] = checkBoxList.isItemSelected(item)
                }
            }
        }

        private fun updateSelectionCount() {
            val totalSelected = taskItems.count { selectedStates.getOrDefault(it.index, true) }
            selectionCountLabel.text = "$totalSelected of ${taskItems.size} target(s) selected" +
                    " in ${plan.queues.size} queue(s)"
        }

        private fun filterTasks(query: String) {
            // Save current selection states
            syncVisibleSelectionStates()
            val filteredItems = if (query.isBlank()) {
                taskItems
            } else {
                val lowerQuery = query.lowercase()
                taskItems.filter { item ->
                    item.displayName.lowercase().contains(lowerQuery) || item.description.lowercase()
                        .contains(lowerQuery)
                }
            }
            checkBoxList.setItems(filteredItems) { it.displayName }
            filteredItems.forEach { item ->
                checkBoxList.setItemSelected(item, selectedStates.getOrDefault(item.index, true))
            }
            updateSelectionCount()
        }

        override fun createCenterPanel(): JComponent = panel {
            row {
                checkBox("Auto-fix issues").selected(autoFix).onChanged { autoFix = it.isSelected }
            }
            row {
                label("Select which targets to build:")
            }
            row {
                cell(searchField).align(Align.FILL)
            }
            row {
                button("Select All") {
                    for (i in 0 until checkBoxList.itemsCount) {
                        val item = checkBoxList.getItemAt(i)
                        if (item != null) checkBoxList.setItemSelected(item, true)
                    }
                    syncVisibleSelectionStates()
                    updateSelectionCount()
                    checkBoxList.repaint()
                }
                button("Deselect All") {
                    for (i in 0 until checkBoxList.itemsCount) {
                        val item = checkBoxList.getItemAt(i)
                        if (item != null) checkBoxList.setItemSelected(item, false)
                    }
                    syncVisibleSelectionStates()
                    updateSelectionCount()
                    checkBoxList.repaint()
                }
            }
            row {
                val scrollPane = JBScrollPane(checkBoxList).apply {
                    preferredSize = Dimension(600, 400)
                }
                cell(scrollPane).align(Align.FILL)
            }
            row {
                cell(selectionCountLabel).align(Align.FILL)
            }
            if (plan.skipped.isNotEmpty() || plan.failed.isNotEmpty()) {
                group("Not Planned") {
                    plan.skipped.take(10).forEach { skipped ->
                        row { label("🚫 ${skipped.target.name}: ${skipped.reason}") }
                    }
                    plan.failed.take(10).forEach { failed ->
                        row {
                            label("⚠️ ${failed.target.name}: ${failed.error.message ?: failed.error.toString()}")
                        }
                    }
                }
            }
            group("Overwrite Mode") {
                row("Mode:") {
                    cell(modeComboBox).align(Align.FILL)
                }
                row {
                    cell(modeDescriptionLabel).align(Align.FILL)
                }
                row {
                    label("Changing the mode re-plans the selected documents before execution.").apply {
                        component.font = component.font.deriveFont(component.font.size2D - 1f)
                    }
                }
            }
            group("Help") {
                row {
                    text(
                        """
            Tasks are generated from markdown frontmatter:
            <ul>
            <li><b>specifies:</b> Files that should be updated based on the documentation</li>
            <li><b>documents:</b> Documentation files to update based on source files</li>
            <li><b>transforms:</b> Source-to-destination file transformations</li>
            <li><b>generates:</b> Single output file from multiple input patterns</li>
            <li><b>folder:</b> A whole folder as the target (and effective task root)</li>
            </ul>
            """.trimIndent()
                    )
                }
            }
        }

        /** Targets (not task instances) are returned, so the plan can be rebuilt with the chosen mode. */
        fun getSelectedTargets(): Set<File> {
            // Save current visible selection states
            syncVisibleSelectionStates()
            return taskItems.filter { selectedStates.getOrDefault(it.index, true) }
                .map { it.target }
                .toSet()
        }

        fun getSelectedMode(): UpdateModes = modeComboBox.selectedItem as? UpdateModes ?: UpdateModes.PatchExisting

        fun getResult(): DialogResult = DialogResult(getSelectedTargets(), getSelectedMode(), autoFix)

        data class DialogResult(
            val selectedTargets: Set<File>,
            val selectedMode: UpdateModes,
            val autoFix: Boolean,
        )

        data class TaskItem(
            val index: Int,
            val target: File,
            val displayName: String,
            val description: String,
            val details: String,
        ) {
            override fun toString(): String = "$displayName - $description"
        }
    }

    /**
     * Simple dialog presenting one text field per declared template variable.
     * Pre-fills each field with the default declared in frontmatter. Returns
     * the (possibly edited) values keyed by variable name.
     */
    class TemplateVarsDialog(
        project: Project?,
        private val declared: Map<String, String>
    ) : DialogWrapper(project) {
        private val fields: Map<String, JBTextField> = declared.mapValues { (_, default) ->
            JBTextField(default).apply {
                columns = 40
            }
        }

        init {
            title = "Template Variables"
            init()
        }

        override fun createCenterPanel(): JComponent = panel {
            row {
                label(
                    "Provide values for the template variables declared in the selected markdown file(s). " +
                            "Values will replace {{VAR_NAME}} placeholders in frontmatter and body."
                )
            }
            for ((name, field) in fields) {
                row(name) {
                    cell(field).align(Align.FILL)
                }
            }
        }

        fun getValues(): Map<String, String> {
            val result = linkedMapOf<String, String>()
            for ((name, field) in fields) {
                result[name] = field.text ?: ""
            }
            return result
        }
    }
}