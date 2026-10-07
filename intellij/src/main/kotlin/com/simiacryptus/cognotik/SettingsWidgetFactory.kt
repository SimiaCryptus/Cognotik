package com.simiacryptus.cognotik

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.treeStructure.Tree
import com.simiacryptus.cognotik.apps.SessionProxyServer
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.UsageTable
import com.simiacryptus.cognotik.platform.*
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ChatModel
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.UserSettings
import com.simiacryptus.cognotik.text.patch.PatchProcessor
import com.simiacryptus.cognotik.text.patch.PatchProcessors
import com.simiacryptus.cognotik.util.BrowseUtil
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import icons.MyIcons
import org.slf4j.LoggerFactory.getLogger
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.net.URI
import java.util.*
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class SettingsWidgetFactory : StatusBarWidgetFactory {
    companion object {
        private val log = getLogger(SettingsWidgetFactory::class.java)
    }

    class SettingsWidget : StatusBarWidget, StatusBarWidget.MultipleTextValuesPresentation {

        private var statusBar: StatusBar? = null

        /** Model trees are created lazily (only when the popup is displayed) and repopulated on each display. */
        private val modelTrees = LinkedHashMap<String, Tree>()
        private var patchProcessorList: JBList<PatchProcessor>? = null
        private val sessionsList = JBList<Session>()
        private val sessionsListModel = DefaultListModel<Session>()

        val settings: UserSettings
            get() = ServiceKey.USER_SETTINGS.get().getUserSettings(
                CognotikConfig.localUser
            )

        private fun getModelTree(title: String): Tree = modelTrees.getOrPut(title) { createModelTree(title) }

        private fun currentModel(title: String): ApiChatModel? = when (title) {
            SMART_MODEL -> AppSettingsState.instance.smartModel
            FAST_MODEL -> AppSettingsState.instance.fastModel
            IMAGE_CHAT_MODEL -> AppSettingsState.instance.imageChatModel
            AUDIO_MODEL -> AppSettingsState.instance.audioModel
            else -> null
        }

        private fun setModel(title: String, model: ApiChatModel) {
            when (title) {
                SMART_MODEL -> AppSettingsState.instance.smartModel = model
                FAST_MODEL -> AppSettingsState.instance.fastModel = model
                IMAGE_CHAT_MODEL -> AppSettingsState.instance.imageChatModel = model
                AUDIO_MODEL -> AppSettingsState.instance.audioModel = model
            }
        }

        private fun getPatchProcessorList(): JBList<PatchProcessor> {
            if (patchProcessorList == null) {
                val listModel = DefaultListModel<PatchProcessor>()
                PatchProcessors.values().forEach { listModel.addElement(it) }
                patchProcessorList = JBList(listModel).apply {
                    cellRenderer = object : DefaultListCellRenderer() {
                        override fun getListCellRendererComponent(
                            list: JList<*>?,
                            value: Any?,
                            index: Int,
                            isSelected: Boolean,
                            cellHasFocus: Boolean
                        ): Component {
                            val component =
                                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                            if (value is PatchProcessor) {
                                text = value.label
                            }
                            return component
                        }
                    }
                    selectionMode = ListSelectionModel.SINGLE_SELECTION
                    addListSelectionListener {
                        val selected = selectedValue
                        if (selected != null) {
                            AppSettingsState.instance.processor = selected
                            statusBar?.updateWidget(ID())
                        }
                    }
                }
            }
            patchProcessorList?.setSelectedValue(AppSettingsState.instance.processor, true)
            return patchProcessorList!!
        }

        /**
         * Loads the visible models for all configured providers.
         * This may perform network calls, so it must not be invoked on the EDT.
         */
        private fun loadModelNames(settings: UserSettings): List<Pair<String?, List<String>>> {
            val pairs = settings.apis.flatMap { apiData ->
                try {
                    (apiData.provider?.getChatModels(
                        apiData.key!!,
                        apiData.apiBase
                            ?: throw IllegalArgumentException("No API found for provider: ${apiData.provider?.name}")
                    ) ?: listOf())
                        .filter { !it.deprecated }.map { model -> apiData.provider?.name!! to model }
                } catch (e: Exception) {
                    log.warn("Failed to retrieve models for provider: ${apiData.provider?.name}", e)
                    listOf()
                }
            }
            return pairs
                .filter { settings.isVisible(it) }
                .sortedBy { "${it.second.provider?.name} - ${it.second.name}" }
                .groupBy { it.second.provider?.name }
                .map { (providerName, models) -> providerName to models.mapNotNull { it.second.name } }
                .filter { it.second.isNotEmpty() }
        }

        /** Creates an empty tree; content is filled in by [refreshModelTree] when the popup is displayed. */
        private fun createModelTree(title: String): Tree {
            val root = DefaultMutableTreeNode(title)
            val tree = Tree(DefaultTreeModel(root))

            tree.accessibleContext.accessibleDescription = getMessage("tree.description", title)

            tree.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle")
            tree.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "select")

            tree.addTreeSelectionListener {
                val selectedNode = tree.lastSelectedPathComponent?.toString()
                if (selectedNode != null) {
                    tree.accessibleContext.firePropertyChange(
                        AccessibleContext.ACCESSIBLE_SELECTION_PROPERTY,
                        null,
                        getMessage("tree.selected", selectedNode)
                    )
                }
            }
            tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            tree.isRootVisible = false
            tree.showsRootHandles = true
            tree.addTreeSelectionListener {
                // Ignore programmatic selection changes (e.g. restoring the current model after a refresh)
                if (tree.getClientProperty(SUPPRESS_SELECTION_KEY) == true) return@addTreeSelectionListener
                val selectedPath = tree.selectionPath
                if (selectedPath != null && selectedPath.pathCount == 3) {
                    val modelName = selectedPath.lastPathComponent.toString()
                    val apis = this@SettingsWidget.settings.apis
                    val apiData = apis.find { apiData ->
                        apiData.provider?.getChatModels(apiData.key!!, apiData.apiBase)
                            ?.find { modelName == it.name } != null
                    }
                    val chatModel = apiData?.provider?.getChatModels(apiData.key!!, apiData.apiBase)
                        ?.find { it.name == modelName }
                    setModel(title, ApiChatModel(chatModel, apiData))
                    statusBar?.updateWidget(ID())
                }
            }
            return tree
        }

        private inline fun withSelectionSuppressed(tree: JTree, block: () -> Unit) {
            tree.putClientProperty(SUPPRESS_SELECTION_KEY, true)
            try {
                block()
            } finally {
                tree.putClientProperty(SUPPRESS_SELECTION_KEY, false)
            }
        }

        /**
         * (Re)populates the given model tree. Shows a loading placeholder immediately,
         * fetches models in the background, then rebuilds the tree on the EDT.
         * Must be called on the EDT.
         */
        private fun refreshModelTree(tree: Tree, title: String, settings: UserSettings) {
            val treeModel = tree.model as DefaultTreeModel
            val root = treeModel.root as DefaultMutableTreeNode
            val generation = ((tree.getClientProperty(LOAD_GENERATION_KEY) as? Int) ?: 0) + 1
            tree.putClientProperty(LOAD_GENERATION_KEY, generation)

            withSelectionSuppressed(tree) {
                root.removeAllChildren()
                root.add(DefaultMutableTreeNode(LOADING_TEXT))
                treeModel.reload()
            }

            Thread({
                val providers = try {
                    loadModelNames(settings)
                } catch (e: Exception) {
                    log.warn("Failed to load models for $title", e)
                    emptyList()
                }
                SwingUtilities.invokeLater {
                    // Discard results from an outdated refresh
                    if (tree.getClientProperty(LOAD_GENERATION_KEY) != generation) return@invokeLater
                    withSelectionSuppressed(tree) {
                        root.removeAllChildren()
                        if (providers.isEmpty()) {
                            root.add(DefaultMutableTreeNode(NO_MODELS_TEXT))
                        }
                        for ((providerName, modelNames) in providers) {
                            val providerNode = DefaultMutableTreeNode(providerName)
                            modelNames.forEach { providerNode.add(DefaultMutableTreeNode(it)) }
                            root.add(providerNode)
                        }
                        treeModel.reload()
                        currentModel(title)?.model?.name?.let { setSelectedModel(tree, it) }
                    }
                }
            }, "Cognotik-ModelTree-$title").apply { isDaemon = true }.start()
        }

        private val temperatureSlider by lazy {
            val slider = JSlider(0, 100, (AppSettingsState.instance.temperature * 100).toInt())
            slider.accessibleContext.accessibleDescription = getMessage("slider.description")
            slider.majorTickSpacing = 10
            slider.minorTickSpacing = 1
            slider.snapToTicks = true
            val panel = JPanel(BorderLayout(5, 5))

            val label = JLabel(String.format("%.2f", AppSettingsState.instance.temperature))
            label.accessibleContext.accessibleDescription = getMessage("label.temperature")
            slider.addChangeListener {
                slider.accessibleContext.firePropertyChange(
                    AccessibleContext.ACCESSIBLE_VALUE_PROPERTY,
                    null,
                    getMessage("slider.value", slider.value / 100.0)
                )
                AppSettingsState.instance.temperature = slider.value / 100.0
                label.text = String.format("%.2f", slider.value / 100.0)
            }

            panel.add(slider, BorderLayout.CENTER)
            panel.add(label, BorderLayout.EAST)
            panel
        }

        private fun createServerControlPanel(): JPanel {
            val panel = JPanel(BorderLayout())
            panel.accessibleContext.accessibleDescription = getMessage("panel.server.description")
            sessionsList.accessibleContext.accessibleDescription = getMessage("list.sessions.description")
            sessionsList.accessibleContext.accessibleName = getMessage("list.sessions.name")

            sessionsList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "activate")
            sessionsList.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "activate")

            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            val startButton = JButton(getMessage("server.start"))
            val stopButton = JButton(getMessage("server.stop"))

            startButton.isEnabled = !CognotikAppServer.isRunning()
            stopButton.isEnabled = CognotikAppServer.isRunning()

            startButton.addActionListener {
                CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                )
                startButton.isEnabled = false
                stopButton.isEnabled = true
                updateSessionsList()
            }
            stopButton.addActionListener {
                CognotikAppServer.getServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                ).server.stop()
                startButton.isEnabled = true
                stopButton.isEnabled = false
                updateSessionsList()
            }
            buttonPanel.add(startButton)
            buttonPanel.add(stopButton)
            panel.add(buttonPanel, BorderLayout.NORTH)

            sessionsList.model = sessionsListModel
            sessionsList.cellRenderer = SessionListRenderer()
            val sessionPanel = JPanel(BorderLayout())
            sessionPanel.add(JLabel(getMessage("label.activeSessions")), BorderLayout.NORTH)
            sessionPanel.add(JScrollPane(sessionsList), BorderLayout.CENTER)

            val actionPanel = JPanel(GridLayout(1, 3))
            val copyButton = JButton(getMessage("action.copyLink"))
            val openButton = JButton(getMessage("action.openLink"))
            val killButton = JButton(getMessage("action.killSession"))

            copyButton.isEnabled = false
            openButton.isEnabled = false
            killButton.isEnabled = false

            sessionsList.addListSelectionListener {
                val hasSelection = sessionsList.selectedValue != null
                copyButton.isEnabled = hasSelection
                openButton.isEnabled = hasSelection
                killButton.isEnabled = hasSelection
            }

            copyButton.addActionListener {
                val session = sessionsList.selectedValue
                if (session != null) {
                    val link = getSessionLink(session)
                    val selection = StringSelection(link)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
                }
            }
            openButton.addActionListener {
                val session = sessionsList.selectedValue
                if (session != null) {
                    BrowseUtil.browse(URI(getSessionLink(session)))
                }
            }
            killButton.addActionListener {
                val session = sessionsList.selectedValue
                if (session != null) {
                    val result = JOptionPane.showConfirmDialog(
                        panel,
                        getMessage("dialog.killSession.message", session.sessionId.take(8)),
                        getMessage("dialog.killSession.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    )
                    if (result == JOptionPane.YES_OPTION) {
                        kill(session)
                        updateSessionsList()
                    }
                }
            }
            actionPanel.add(copyButton)
            actionPanel.add(openButton)
            actionPanel.add(killButton)
            sessionPanel.add(actionPanel, BorderLayout.SOUTH)
            panel.add(sessionPanel, BorderLayout.CENTER)
            return panel
        }

        private fun kill(session: Session) {
            ThreadPoolManager.getPool(session, CognotikConfig.localUser).shutdownNow()
            ThreadPoolManager.getScheduledPool(session, CognotikConfig.localUser).shutdownNow()
        }

        fun updateSessionsList() {
            sessionsListModel.clear()
            (SessionProxyServer.chats.keys + SessionProxyServer.agents.keys).distinct().forEach {
                sessionsListModel.addElement(it.session)
            }
        }

        private inner class SessionListRenderer : ListCellRenderer<Session> {
            private val label = JLabel()
            override fun getListCellRendererComponent(
                list: JList<out Session>?,
                value: Session?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                label.text = if (value != null) {
                    try {
                        val sessionName =
                            ServiceKey.METADATA_DB.get().getSessionName(
                                CognotikConfig.localUser,
                                value
                            )

                        val threadFactory = ThreadPoolManager.getPool(
                            value,
                            CognotikConfig.localUser
                        ).threadFactory
                        val activeThreads = threadFactory.threads.filter {
                            when (it.state) {
                                Thread.State.RUNNABLE -> true
                                Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING -> true
                                else -> false
                            }
                        }.size
                        when {
                            sessionName.isBlank() -> getDefaultSessionLabel(value)
                            else -> "$sessionName (${value.sessionId.take(8)}) [$activeThreads threads]"
                        }
                    } catch (_: Exception) {
                        getDefaultSessionLabel(value)
                    }
                } else {
                    "Unknown Session"
                }

                if (isSelected) {
                    label.background = list?.selectionBackground
                    label.foreground = list?.selectionForeground
                } else {
                    label.background = list?.background
                    label.foreground = list?.foreground
                }

                label.accessibleContext.accessibleName = label.text
                label.accessibleContext.accessibleDescription = getMessage("session.item.description", label.text)
                return label
            }

            private fun getDefaultSessionLabel(session: Session): String {
                return "Session ${session.sessionId.take(8)}"
            }
        }

        init {
            AppSettingsState.onSettingsLoadedListeners.add {
                SwingUtilities.invokeLater {
                    statusBar?.updateWidget(ID())
                    patchProcessorList?.setSelectedValue(AppSettingsState.instance.processor, true)
                    // Only repopulate trees that are currently displayed; others are rebuilt on next display
                    val visibleTrees = modelTrees.filterValues { it.isShowing }
                    if (visibleTrees.isNotEmpty()) {
                        val settings = this@SettingsWidget.settings
                        visibleTrees.forEach { (title, tree) -> refreshModelTree(tree, title, settings) }
                    }
                }
            }
        }

        override fun ID(): String {
            return "AICodingAssistant.SettingsWidget"
        }

        override fun getPresentation(): StatusBarWidget.WidgetPresentation {
            return this
        }

        override fun install(statusBar: StatusBar) {
            this.statusBar = statusBar
        }

        override fun dispose() {

        }

        private fun createHeader(): JPanel {
            val appname = JPanel(FlowLayout(FlowLayout.LEFT, 10, 10))
            appname.add(JLabel("Cognotik"), FlowLayout.LEFT)
            appname.add(JLabel(MyIcons.icon), FlowLayout.LEFT)
            return appname
        }

        private fun setSelectedModel(tree: JTree, modelName: String) {
            val root = tree.model as DefaultTreeModel
            val rootNode = root.root as DefaultMutableTreeNode
            for (i in 0 until rootNode.childCount) {
                val providerNode = rootNode.getChildAt(i) as DefaultMutableTreeNode
                for (j in 0 until providerNode.childCount) {
                    val modelNode = providerNode.getChildAt(j) as DefaultMutableTreeNode
                    if (modelNode.userObject == modelName) {
                        val path = TreePath(modelNode.path)
                        tree.selectionPath = path
                        tree.scrollPathToVisible(path)
                        return
                    }
                }
            }
        }

        private fun createModelPanel(title: String, settings: UserSettings): JPanel {
            val tree = getModelTree(title)
            refreshModelTree(tree, title, settings)
            return JPanel(BorderLayout()).apply {
                add(JScrollPane(tree), BorderLayout.CENTER)
            }
        }

        override fun getPopup(): JBPopup {
            updateSessionsList()
            val panel = JPanel(BorderLayout())
            panel.accessibleContext.accessibleDescription = getMessage("popup.description")
            panel.add(createHeader(), BorderLayout.NORTH)

            val tabbedPane = JTabbedPane()
            tabbedPane.accessibleContext.accessibleDescription = getMessage("tabs.description")

            // Model trees are built (and repopulated) only now, when the panel is actually displayed
            val settings = this@SettingsWidget.settings
            val smartModelPanel = createModelPanel(SMART_MODEL, settings)
            val fastModelPanel = createModelPanel(FAST_MODEL, settings)
            val imageChatModelPanel = createModelPanel(IMAGE_CHAT_MODEL, settings)
            val audioModelPanel = createModelPanel(AUDIO_MODEL, settings)

            val patchProcessorPanel = JPanel(BorderLayout())
            patchProcessorPanel.add(JScrollPane(getPatchProcessorList()), BorderLayout.CENTER)

            val usagePanel = JPanel(BorderLayout())
            usagePanel.add(
                UsageTable(ServiceKey.USAGE_DB.get()),
                BorderLayout.CENTER
            )

            tabbedPane.addTab(getMessage("tab.smartModel"), smartModelPanel)
            tabbedPane.addTab(getMessage("tab.fastModel"), fastModelPanel)
            tabbedPane.addTab(getMessage("tab.imageChatModel"), imageChatModelPanel)
            tabbedPane.addTab("Audio Model", audioModelPanel)
            tabbedPane.addTab("Patch Processor", patchProcessorPanel)
            tabbedPane.addTab(getMessage("tab.server"), createServerControlPanel())
            tabbedPane.addTab(getMessage("tab.usage"), usagePanel)

            panel.add(tabbedPane, BorderLayout.CENTER)
            panel.add(temperatureSlider, BorderLayout.SOUTH)

            val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(panel, tabbedPane)
                .setRequestFocus(true)
                .setCancelOnClickOutside(true)
                .createPopup()
            popup.addListener(object : JBPopupListener {
                override fun onClosed(event: LightweightWindowEvent) {
                    updateSessionsList()
                }
            })
            return popup
        }

        override fun getSelectedValue(): String {
            return AppSettingsState.instance.smartModel?.model?.name ?: "Uninitialized"
        }

        override fun getTooltipText() = """
    Smart Model: ${AppSettingsState.instance.smartModel?.model?.name ?: "Not configured"}<br/>
    Fast Model: ${AppSettingsState.instance.fastModel?.model?.name ?: "Not configured"}<br/>
    Image Chat Model: ${AppSettingsState.instance.imageChatModel?.model?.name ?: "Not configured"}<br/>
    Audio Model: ${AppSettingsState.instance.audioModel?.model?.name ?: "Not configured"}<br/>
    Patch Processor: ${AppSettingsState.instance.processor.label}<br/>
    Temperature: ${AppSettingsState.instance.temperature}<br/>
    ${
            if (CognotikAppServer.isRunning()) {
                "Server running on ${AppSettingsState.instance.listeningEndpoint}:${AppSettingsState.instance.listeningPort}"
            } else {
                "Server stopped"
            }
        }
    """.trimIndent().trim()

        companion object {
            private const val SMART_MODEL = "Smart Model"
            private const val FAST_MODEL = "Fast Model"
            private const val IMAGE_CHAT_MODEL = "Image Chat Model"
            private const val AUDIO_MODEL = "Audio Model"
            private const val LOADING_TEXT = "Loading models..."
            private const val NO_MODELS_TEXT = "No models available"
            private const val SUPPRESS_SELECTION_KEY = "cognotik.suppressSelection"
            private const val LOAD_GENERATION_KEY = "cognotik.loadGeneration"

            private val messages = ResourceBundle.getBundle("messages.SettingsWidget")
            private fun getMessage(key: String, vararg args: Any): String =
                String.format(messages.getString(key), *args)

            fun getSessionLink(session: Session) =
                "http://${AppSettingsState.instance.listeningEndpoint}:${AppSettingsState.instance.listeningPort}/#${session}"

            fun UserSettings.isVisible(
                model: Pair<String, ChatModel>
            ): Boolean = apis.any { api ->
                api.provider?.name == model.second.provider?.name && api.key?.decrypt != null
            }
        }

    }

    override fun getId(): String {
        return "AICodingAssistant.SettingsWidgetFactory"
    }

    override fun getDisplayName(): String {
        return "AI Coding Assistant Settings"
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return SettingsWidget()
    }

    override fun isAvailable(project: Project): Boolean {
        return true
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }
}