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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/agent/CommandAutofixAction.kt

```
package cognotik.actions.agent

/**
 * Action that provides automated fixing of command execution issues through AI assistance
 */

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.isFile
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.bind
import com.intellij.ui.dsl.builder.panel
import com.simiacryptus.cognotik.apps.CmdPatchApp
import com.simiacryptus.cognotik.apps.PatchApp
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.ToolProvider
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.JsonUtil.fromJson
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import javax.swing.*

class CommandAutofixAction : BaseAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    /**
     * Handles the action execution.
     * Shows settings dialog, creates patch app session and opens browser interface.
     */
    override fun handle(e: AnActionEvent) {
        try {
            UITools.runAsync(e.project, "Initializing Command Autofix", true) { progress ->
                progress.isIndeterminate = true
                progress.text = "Getting settings..."
                val files = e.getSelectedFiles()
                val folders = e.getSelectedFolders().map { it.toFile.toPath() }
                val root = (folders + files.map { it.toFile.toPath() }).filterNotNull().toTypedArray().commonRoot()
                lateinit var settingsUI: SettingsUI
                val settings = run {
                    var settings1: PatchApp.Settings? = null
                    SwingUtilities.invokeAndWait {
                        settingsUI = SettingsUI(workingDirectory = root.toFile(), folders)

                        if (files.size == 1) {
                            val defaultFile = files[0]
                            val whitelist = listOf("sh", "py", "bat", "ps", "ps1", "cmd", "exe", "jar")
                            val matchesWhitelist =
                                whitelist.any { defaultFile.name.endsWith(".$it", ignoreCase = true) }
                            if (defaultFile.isFile && (defaultFile.toFile.canExecute() || matchesWhitelist)) {

                                val first = settingsUI.commandsList.firstOrNull()
                                if (first != null) {
                                    first.commandField.selectedItem = defaultFile.toFile.absolutePath
                                    first.workingDirectoryField.selectedItem = defaultFile.parent.toFile.absolutePath
                                    first.argumentsField.selectedItem = ""
                                }
                            }
                        }
                        val dialog = CommandSettingsDialog(e.project, settingsUI)
                        dialog.show()
                        settings1 = if (dialog.isOK) {
                            val commands = settingsUI.commandsList.map { cmdPanel ->
                                val executable = File(
                                    cmdPanel.commandField.selectedItem?.toString()
                                        ?: throw IllegalArgumentException("No executable selected")
                                )
                                val tools =
                                    ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools
                                tools.addAll(ToolProvider.scanRecursive(File(executable.absolutePath)))
                                val argument = cmdPanel.argumentsField.selectedItem?.toString() ?: ""
                                AppSettingsState.instance.recentArguments?.remove(argument)
                                AppSettingsState.instance.recentArguments?.add(0, argument)
                                AppSettingsState.instance.recentArguments?.apply {
                                    if (size > MAX_RECENT_ARGUMENTS) dropLast(size - MAX_RECENT_ARGUMENTS)
                                }
                                val workingDir = cmdPanel.workingDirectoryField.selectedItem?.toString() ?: ""
                                AppSettingsState.instance.recentWorkingDirs?.remove(workingDir)
                                AppSettingsState.instance.recentWorkingDirs?.add(0, workingDir)
                                AppSettingsState.instance.recentWorkingDirs?.apply {
                                    if (size > MAX_RECENT_ARGUMENTS) dropLast(size - MAX_RECENT_DIRS)
                                }
                                //require(executable.exists()) { "Executable file does not exist: $executable" }
                                val workingDirectory = File(workingDir)
                                PatchApp.CommandSettings(
                                    executable = executable,
                                    arguments = argument,
                                    workingDirectory = workingDirectory,
                                    additionalInstructions = settingsUI.additionalInstructionsField.text
                                )
                            }.toList()
                            PatchApp.Settings(
                                commands = commands,
                                autoFix = settingsUI.autoFixCheckBox.isSelected,
                                maxRetries = settingsUI.maxRetriesSlider.value,
                                includeLineNumbers = settingsUI.includeLineNumbersCheckBox.isSelected,
                                includeGitDiffs = settingsUI.includeGitDiffsCheckBox.isSelected
                            )
                        } else {
                            null
                        }
                    }
                    settings1
                } ?: return@runAsync
                val patchApp = CmdPatchApp(
                    root = root,
                    settings = settings,
                    files = files.map { it.toFile }.toTypedArray(),
                    model = AppSettingsState.instance.smartChatClient.getChildClient(),
                    parsingModel = AppSettingsState.instance.fastChatClient.getChildClient(),
                    processor = AppSettingsState.instance.processor
                )
                val session = Session.newGlobalID()
                SessionProxyServer.chats[session] = patchApp
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Code Chat",
                    inputCnt = 1,
                    stickyInput = false,
                    loadImages = false,
                    showMenubar = false
                )
                val dateFormat = SimpleDateFormat("HH:mm:ss")
                val sessionName = "${javaClass.simpleName} @ ${dateFormat.format(System.currentTimeMillis())}"
                SessionProxyServer.metadataStorage.setSessionName(null, session, sessionName)
                Thread {
                    Thread.sleep(500)
                    try {
                        val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                            AppSettingsState.instance.listeningEndpoint,
                            AppSettingsState.instance.listeningPort
                        ).server.uri.resolve("/#$session")
                        BaseAction.log.info("Opening browser to $uri")
                        browse(uri)
                    } catch (e: Throwable) {
                        log.warn("Error opening browser", e)
                    }
                }.start()
            }
        } catch (e: Throwable) {
            log.error("Failed to execute command autofix", e)
            UITools.showErrorDialog("Failed to execute command autofix: ${e.message}", "Error")
        }
    }

    /**
     * Checks if the action should be enabled
     */
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        if (event.project == null) return false
        val folder = event.getSelectedFolder()
        val hasBasePath = event.project?.basePath != null
        return folder != null || hasBasePath
    }

    companion object {
        private val log = LoggerFactory.getLogger(CommandAutofixAction::class.java)
        private const val MAX_RECENT_ARGUMENTS = 10
        private const val MAX_RECENT_DIRS = 10
        private const val TEXT_AREA_ROWS = 6

        /**
         * Dialog for command settings configuration
         */
        class CommandSettingsDialog(
            project: com.intellij.openapi.project.Project?, private val settingsUI: SettingsUI
        ) : DialogWrapper(project, true) {
            init {
                title = "Command Autofix Settings"
                init()
            }

            override fun createCenterPanel(): JComponent {
                val panel = JPanel(BorderLayout())
                panel.preferredSize = Dimension(800, 600)

                val contentPanel = JPanel(BorderLayout()).apply {
                    border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
                }

                val optionsPanel = panel {
                    row { cell(settingsUI.commandsContainerPanel) }
                    group("Saved Configurations") {
                        row {
                            cell(settingsUI.savedConfigsCombo)
                            button("Load") {
                                val configName = settingsUI.savedConfigsCombo.selectedItem as? String
                                if (!configName.isNullOrBlank()) {
                                    settingsUI.loadConfig(configName)
                                }
                            }
                            button("Save") {
                                settingsUI.saveCurrentConfig()
                            }
                        }
                    }
                    group("Code Analysis Options") {
                        row {
                            cell(settingsUI.autoFixCheckBox)
                            cell(settingsUI.includeLineNumbersCheckBox)
                            cell(settingsUI.includeGitDiffsCheckBox)
                        }
                    }
                    group("Execution Options") {
                        row("Max Retries:") {
                            cell(settingsUI.maxRetriesSlider)
                            cell(settingsUI.maxRetriesField)
                        }
                    }
                    group("Autofix On Exit Code:") {
                        buttonsGroup {
                            row {
                                settingsUI.exitCodeNonZero = radioButton(
                                    "Non-zero (Error)", SettingsUI.ExitCodeOption.NONZERO
                                )
                                settingsUI.exitCodeZero = radioButton(
                                    "Zero (Success)", SettingsUI.ExitCodeOption.ZERO
                                )
                                settingsUI.exitCodeAny = radioButton(
                                    "Any (Always Run)", SettingsUI.ExitCodeOption.ANY
                                )
                            }
                        }.apply {
                            bind({ settingsUI.exitCodeOption }, { settingsUI.exitCodeOption = it })
                        }
                    }
                    group("Additional Instructions") {
                        row {
                            cell(settingsUI.additionalInstructionsField)
                        }
                    }
                }
                contentPanel.add(optionsPanel, BorderLayout.CENTER)
                panel.add(contentPanel, BorderLayout.CENTER)
                return panel
            }
        }

        class SettingsUI(val workingDirectory: File, val folders: List<Path>) {
            val commandsPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
            }
            val commandsScrollPane = JBScrollPane(commandsPanel).apply {
                border = BorderFactory.createLoweredBevelBorder()
                verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
                horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
                preferredSize = Dimension(750, 250)

                verticalScrollBar.unitIncrement = 16

                minimumSize = Dimension(600, 150)
            }
            val commandsList = mutableListOf<CommandPanel>()
            val savedConfigsCombo = ComboBox<String>().apply {
                preferredSize = Dimension(200, 30)
                AppSettingsState.instance.savedCommandConfigsJson?.keys?.sorted()?.forEach { addItem(it) }
            }

            enum class ExitCodeOption { NONZERO, ZERO, ANY }

            var exitCodeOption = ExitCodeOption.NONZERO
            var exitCodeNonZero: Cell<JBRadioButton>? = null
            var exitCodeZero: Cell<JBRadioButton>? = null
            var exitCodeAny: Cell<JBRadioButton>? = null
            val includeGitDiffsCheckBox = JCheckBox("Include Git Working Copy Diffs").apply {
                isSelected = false
                toolTipText = "Include git diffs between working copy and HEAD when analyzing code"
            }
            val includeLineNumbersCheckBox = JCheckBox("Include Line Numbers").apply {
                isSelected = true
                toolTipText = "Show line numbers in code snippets for better context"
            }

            val commandsContainerPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createTitledBorder("Commands")
                preferredSize = Dimension(750, 300)
            }

            val addCommandButton = JButton("Add Command").apply {
                addActionListener {
                    addCommandPanel()
                }
            }

            init {

                commandsContainerPanel.add(commandsScrollPane, BorderLayout.CENTER)
                val buttonPanel = JPanel(BorderLayout()).apply {
                    border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
                    add(addCommandButton, BorderLayout.EAST)

                    add(JButton("Clear All").apply {
                        addActionListener {
                            if (commandsList.size > 0) {

                                val firstPanel = commandsList.firstOrNull()
                                commandsList.clear()
                                commandsPanel.removeAll()
                                if (firstPanel != null) {
                                    commandsList.add(firstPanel)
                                    commandsPanel.add(firstPanel)
                                    commandsPanel.add(Box.createVerticalStrut(5))
                                } else {
                                    addCommandPanel()
                                }
                                commandsPanel.revalidate()
                                commandsPanel.repaint()
                            }
                        }
                    }, BorderLayout.WEST)
                }
                commandsContainerPanel.add(buttonPanel, BorderLayout.SOUTH)

                addCommandPanel()
            }

            fun addCommandPanel() {
                val cmdPanel = CommandPanel(workingDirectory, folders)
                commandsList.add(cmdPanel)
                commandsPanel.add(cmdPanel)
                commandsPanel.add(Box.createVerticalStrut(5))
                commandsPanel.revalidate()
                commandsPanel.repaint()

                SwingUtilities.invokeLater {

                    commandsScrollPane.revalidate()
                    commandsScrollPane.repaint()

                    SwingUtilities.invokeLater {
                        commandsScrollPane.viewport.viewPosition =
                            java.awt.Point(0, commandsPanel.height - commandsScrollPane.viewport.height)
                    }
                }
            }

            fun removeCommandPanel(panel: CommandPanel) {

                if (commandsList.size <= 1) {
                    return
                }

                commandsList.remove(panel)
                commandsPanel.remove(panel)

                if (commandsPanel.componentCount > commandsList.size * 2) {
                    commandsPanel.remove(commandsPanel.getComponentZOrder(panel) + 1)
                }
                commandsPanel.revalidate()
                commandsPanel.repaint()

                SwingUtilities.invokeLater {
                    SwingUtilities.getWindowAncestor(commandsPanel)?.pack()
                }
            }

            val maxRetriesField: JSpinner = JSpinner(SpinnerNumberModel(3, 0, 10, 1)).apply {
                toolTipText = "Maximum number of auto-retry attempts (0-10)"
                addChangeListener {

                    maxRetriesSlider.value = value as Int
                }
            }
            val maxRetriesSlider = JSlider(JSlider.HORIZONTAL, 0, 10, 3).apply {
                majorTickSpacing = 2
                minorTickSpacing = 1
                paintTicks = true
                paintLabels = true
                toolTipText = "Maximum number of auto-retry attempts (0-10)"
                addChangeListener {

                    maxRetriesField.value = value
                }
            }
            val additionalInstructionsField = JTextArea().apply {
                rows = TEXT_AREA_ROWS
                columns = 60
                lineWrap = true
                wrapStyleWord = true
                border = BorderFactory.createLoweredBevelBorder()
                minimumSize = Dimension(400, 100)

            }

            val autoFixCheckBox = JCheckBox("Auto-apply fixes").apply {
                isSelected = false
            }

            fun saveCurrentConfig() {
                val configName = JOptionPane.showInputDialog(
                    null, "Enter configuration name:", "Save Configuration", JOptionPane.PLAIN_MESSAGE
                )?.trim()
                if (configName.isNullOrBlank()) {
                    JOptionPane.showMessageDialog(
                        null, "Please enter a valid configuration name", "Invalid Name", JOptionPane.WARNING_MESSAGE
                    )
                    return
                }
                val config = CommandConfig(
                    commands = commandsList.map { it.toCommandSettings() },
                    exitCodeOption = when (exitCodeOption) {
                        ExitCodeOption.ZERO -> "0"
                        ExitCodeOption.ANY -> "any"
                        ExitCodeOption.NONZERO -> "nonzero"
                    },
                    autoFix = autoFixCheckBox.isSelected,
                    maxRetries = maxRetriesSlider.value,
                    includeGitDiffs = includeGitDiffsCheckBox.isSelected,
                    includeLineNumbers = includeLineNumbersCheckBox.isSelected,
                    additionalInstructions = additionalInstructionsField.text,
                )
                AppSettingsState.instance.savedCommandConfigsJson?.set(configName, config.toJson())
                savedConfigsCombo.addItem(configName)
                savedConfigsCombo.selectedItem = configName
            }

            fun loadConfig(configName: String) {
                val config =
                    AppSettingsState.instance.savedCommandConfigsJson?.get(configName)
                        ?.let<String, CommandConfig?> { fromJson(it, CommandConfig::class.java) } ?: return
                commandsList.clear()
                commandsPanel.removeAll()
                config.commands.forEach {
                    val panel = CommandPanel(workingDirectory, folders)
                    panel.loadFromSettings(it)

                    panel.workingDirectoryField.selectedItem = workingDirectory.absolutePath
                    commandsList.add(panel)
                    commandsPanel.add(panel)
                    commandsPanel.add(Box.createVerticalStrut(5))
                }
                exitCodeOption = when (config.exitCodeOption) {
                    "0" -> ExitCodeOption.ZERO
                    "any" -> ExitCodeOption.ANY
                    else -> ExitCodeOption.NONZERO
                }
                autoFixCheckBox.isSelected = config.autoFix
                maxRetriesSlider.value = config.maxRetries
                includeGitDiffsCheckBox.isSelected = config.includeGitDiffs
                includeLineNumbersCheckBox.isSelected = config.includeLineNumbers ?: true
                additionalInstructionsField.text = config.additionalInstructions

                commandsPanel.revalidate()
                commandsPanel.repaint()
            }

            class CommandPanel(workingDirectory: File, folders: List<Path>) : JPanel() {
                val workingDirectoryField = ComboBox<String>().apply {
                    isEditable = true
                    val items = mutableListOf<String>()
                    AppSettingsState.instance.recentWorkingDirs?.forEach { addItem(it); items.add(it) }
                    if (AppSettingsState.instance.recentWorkingDirs?.isEmpty() == true) {
                        addItem(workingDirectory.absolutePath)
                    }
                    folders.forEach {
                        val absolutePath = it.toFile().absolutePath
                        if (!items.contains(absolutePath)) {
                            addItem(absolutePath)
                            items.add(absolutePath)
                        }
                    }
                    selectedItem = workingDirectory.absolutePath
                    preferredSize = Dimension(400, preferredSize.height)
                }
                val executables: List<String>? =
                    ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
                        .tools.flatMap { it.absoluteExecutablePaths() }.distinct().sorted()
                val commandField = ComboBox(executables?.toTypedArray() ?: emptyArray()).apply {
                    isEditable = true
                    preferredSize = Dimension(400, preferredSize.height)
                }
                val workingDirectoryButton = JButton("...").apply {
                    addActionListener {
                        val fileChooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            isMultiSelectionEnabled = false
                            this.selectedFile =
                                File(workingDirectoryField.selectedItem?.toString() ?: workingDirectory.absolutePath)
                        }
                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            workingDirectoryField.selectedItem = fileChooser.selectedFile.absolutePath
                        }
                    }
                }

                val commandButton = JButton("...").apply {
                    addActionListener {
                        val fileChooser = JFileChooser().apply {
                            fileSelectionMode = JFileChooser.FILES_ONLY
                            isMultiSelectionEnabled = false
                        }
                        if (fileChooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            commandField.selectedItem = fileChooser.selectedFile.absolutePath
                        }
                    }
                }
                val argumentsField = ComboBox<String>().apply {
                    isEditable = true
                    AppSettingsState.instance.recentArguments?.forEach { addItem(it) }
                    if (AppSettingsState.instance.recentArguments?.isEmpty() == true) {
                        addItem("")
                    }
                    preferredSize = Dimension(450, preferredSize.height)
                }

                init {
                    border = BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(5, 5, 5, 5), BorderFactory.createEtchedBorder()
                    )
                    layout = BorderLayout()

                    minimumSize = Dimension(650, 120)

                    val fieldsPanel = JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                    }

                    val headerPanel = JPanel(BorderLayout()).apply {
                        border = BorderFactory.createEmptyBorder(0, 0, 5, 0)
                    }
                    val removeButton = JButton("Remove").apply {
                        addActionListener {
                            val parent = SwingUtilities.getAncestorOfClass(
                                SettingsUI::class.java, this@CommandPanel
                            ) as? SettingsUI
                            parent?.removeCommandPanel(this@CommandPanel)
                        }
                    }
                    headerPanel.add(removeButton, BorderLayout.EAST)
                    add(headerPanel, BorderLayout.NORTH)

                    fieldsPanel.add(JPanel(BorderLayout(5, 0)).apply {
                        add(JLabel("Command:", SwingConstants.RIGHT).apply {
                            preferredSize = Dimension(100, preferredSize.height)
                        }, BorderLayout.WEST)
                        add(commandField, BorderLayout.CENTER)
                        add(commandButton, BorderLayout.EAST)
                        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                        alignmentX = LEFT_ALIGNMENT
                    })
                    fieldsPanel.add(Box.createVerticalStrut(5))

                    fieldsPanel.add(JPanel(BorderLayout(5, 0)).apply {
                        add(JLabel("Arguments:", SwingConstants.RIGHT).apply {
                            preferredSize = Dimension(100, preferredSize.height)
                        }, BorderLayout.WEST)
                        add(argumentsField, BorderLayout.CENTER)
                        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                        alignmentX = LEFT_ALIGNMENT
                    })
                    fieldsPanel.add(Box.createVerticalStrut(5))

                    fieldsPanel.add(JPanel(BorderLayout(5, 0)).apply {
                        add(JLabel("Directory:", SwingConstants.RIGHT).apply {
                            preferredSize = Dimension(100, preferredSize.height)
                        }, BorderLayout.WEST)
                        add(workingDirectoryField, BorderLayout.CENTER)
                        add(workingDirectoryButton, BorderLayout.EAST)
                        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                        alignmentX = LEFT_ALIGNMENT
                    })
                    add(fieldsPanel, BorderLayout.CENTER)
                }

                fun toCommandSettings(): PatchApp.CommandSettings {
                    return PatchApp.CommandSettings(
                        executable = File(commandField.selectedItem?.toString() ?: ""),
                        arguments = argumentsField.selectedItem?.toString() ?: "",
                        workingDirectory = File(workingDirectoryField.selectedItem?.toString() ?: ""),
                        additionalInstructions = ""
                    )
                }

                fun loadFromSettings(settings: PatchApp.CommandSettings) {
                    commandField.selectedItem = settings.executable.absolutePath
                    argumentsField.selectedItem = settings.arguments
                }
            }
        }
    }

    data class CommandConfig(
        val commands: List<PatchApp.CommandSettings>,
        val exitCodeOption: String,
        val autoFix: Boolean,
        val maxRetries: Int,
        val additionalInstructions: String,
        val includeGitDiffs: Boolean = false,
        val includeLineNumbers: Boolean = false,
    )

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/agent/CustomFileSetPatchAction.kt

```
package cognotik.actions.agent

import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMTextFile
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.*
import java.text.SimpleDateFormat
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CustomFileSetPatchAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    enum class OutputMode {
        EDIT_FILES, GENERATE_DOCUMENTATION_SINGLE, GENERATE_DOCUMENTATION_MULTI, AGGREGATED_DATA_EXTRACTION_SINGLE, AGGREGATED_DATA_EXTRACTION_MULTI
    }

    enum class BaseDirectoryMode {
        SELECTED_DIRECTORY, MODULE_ROOT
    }


    data class FilePattern(
        val pattern: String,
        val isContext: Boolean = false,
        val isRegex: Boolean = false,
        val isExclusion: Boolean = false,
        val baseDirectory: String = ""
    ) {

        fun toRegex(): Regex? {
            return try {
                if (isRegex) pattern.toRegex()
                else null
            } catch (e: Exception) {
                null
            }
        }

        override fun toString(): String = buildString {
            if (isExclusion) append("[Exclude] ")
            if (isContext) append("[Context] ")
            if (isRegex) append("[Regex] ")
            if (baseDirectory.isNotEmpty()) append("[${baseDirectory}] ")
            append(pattern)
        }
    }

    data class FileSet(
        val name: String, val base: Path, val files: List<Path>
    )

    class SettingsUI(private val project: Project?, private val selectedDirectory: Path?) {
        companion object {
            private const val DEFAULT_PATTERN_WIDTH = 30
            private const val DEFAULT_TEXTAREA_ROWS = 4
            private const val DEFAULT_TEXTAREA_COLS = 40
            private const val PREVIEW_ROWS = 15
            private const val PREVIEW_COLS = 50
            private const val MAX_PREVIEW_FILES = 100
            private const val PREVIEW_UPDATE_DELAY_MS = 500L
            private const val MAX_WALK_DEPTH = 10
            private const val MAX_FILES_PER_PATTERN = 1000L
            private const val CACHE_EXPIRY_MS = 60_000L // 1 minute cache expiry
        }

        private val patternCache = ConcurrentHashMap<String, PathMatcher>()
        private val previewExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "FileSetPreviewUpdater").apply { isDaemon = true }
        }
        private var currentPreviewTask: Future<*>? = null
        private val fileCache = ConcurrentHashMap<Path, CacheEntry>()
        private val lastUpdateTime = java.util.concurrent.atomic.AtomicLong(0)
        private val isUpdating = AtomicBoolean(false)

        private data class CacheEntry(
            val paths: List<Path>, val timestamp: Long = System.currentTimeMillis()
        ) {
            fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS
        }

        @Volatile
        private var isDisposed = false

        @Name("Base Directory Mode")
        val baseDirectoryMode = JComboBox(BaseDirectoryMode.entries.toTypedArray()).apply {
            selectedItem = BaseDirectoryMode.SELECTED_DIRECTORY
            this.isEditable = true
        }


        @Name("File Patterns")
        val patternListModel = DefaultListModel<FilePattern>()
        val patternList = JList(patternListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): java.awt.Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is FilePattern) {
                        text = value.toString()
                        if (value.isExclusion) {
                            foreground = java.awt.Color.RED.darker()
                        }
                    }
                    return component
                }
            }
        }

        @Name("Pattern Input")
        val patternInput = JBTextField(DEFAULT_PATTERN_WIDTH)


        @Name("Is Context")
        val isContextCheckbox = JCheckBox("Include as context in all calls")

        @Name("Is Exclusion")
        val isExclusionCheckbox = JCheckBox("Exclude matching files")

        @Name("Use Regex")
        val useRegexCheckbox = JCheckBox("Use regex pattern instead of glob")


        @Name("AI Instruction")
        val transformationMessage = JBTextArea(DEFAULT_TEXTAREA_ROWS, DEFAULT_TEXTAREA_COLS)

        @Name("Auto Apply")
        val autoApply = JCheckBox("Auto Apply Changes")

        @Name("Treat Documents as Text")
        val treatDocumentsAsText = JCheckBox("Include PDF/HTML files as text", false)

        @Name("Output Mode")


        val outputModeCombo = JComboBox(OutputMode.entries.toTypedArray()).apply {
            selectedItem = OutputMode.EDIT_FILES
        }


        @Name("Output File")
        val outputFilename = JBTextField("output.md")

        @Name("Output Directory")
        val outputDirectory = JBTextField("output/")

        @Name("Concurrency")
        val concurrencySpinner = JSpinner(SpinnerNumberModel(4, 1, 16, 1))


        @Name("Preview")
        val previewArea = JBTextArea(PREVIEW_ROWS, PREVIEW_COLS).apply {
            isEditable = false
            font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        }

        @Name("File Volume")
        val fileVolumeLabel = JLabel("Total size: 0 KB")

        @Name("FileSet Count")
        val fileSetCountLabel = JLabel("FileSets: 0")

        @Name("Average FileSet Size")
        val avgFileSetSizeLabel = JLabel("Avg size: 0 KB")

        @Name("Max FileSet Size")
        val maxFileSetSizeLabel = JLabel("Max size: 0 KB")


        @Name("Big Data Mode Threshold")
        val bigDataThresholdSpinner = JSpinner(SpinnerNumberModel(100, 10, 1000, 10))

        @Name("Aggregation Size (KB)")
        val aggregationSizeSpinner = JSpinner(SpinnerNumberModel(10, 1, 100, 1))

        init {
            // Add listener for base directory mode changes
            baseDirectoryMode.addActionListener {
                schedulePreviewUpdate()
            }
            // Add listener for exclusion checkbox
            isExclusionCheckbox.addActionListener {
                if (isExclusionCheckbox.isSelected) {
                    isContextCheckbox.isSelected = false
                    isContextCheckbox.isEnabled = false
                } else {
                    isContextCheckbox.isEnabled = true
                }
            }


            // Add document listener to update preview when patterns change

            patternInput.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = schedulePreviewUpdate()
                override fun removeUpdate(e: DocumentEvent?) = schedulePreviewUpdate()
                override fun changedUpdate(e: DocumentEvent?) = schedulePreviewUpdate()
            })


            patternList.addListSelectionListener { schedulePreviewUpdate() }

            // Add listener for output mode changes
            outputModeCombo.addActionListener { updateOutputOptionsVisibility() }

            updateOutputOptionsVisibility()
        }

        fun dispose() {
            isDisposed = true
            currentPreviewTask?.cancel(true)
            previewExecutor.shutdown()
            try {
                if (!previewExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                    previewExecutor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                previewExecutor.shutdownNow()
                Thread.currentThread().interrupt()
            }
            patternCache.clear()
            fileCache.clear()
        }

        private fun updateOutputOptionsVisibility() {
            val selectedMode = outputModeCombo.selectedItem as OutputMode
            val isSingleFileMode =
                selectedMode == OutputMode.GENERATE_DOCUMENTATION_SINGLE || selectedMode == OutputMode.AGGREGATED_DATA_EXTRACTION_SINGLE
            val isMultiFileMode =
                selectedMode == OutputMode.GENERATE_DOCUMENTATION_MULTI || selectedMode == OutputMode.AGGREGATED_DATA_EXTRACTION_MULTI
            outputDirectory.isVisible = isSingleFileMode || isMultiFileMode
            autoApply.isVisible = selectedMode == OutputMode.EDIT_FILES
            aggregationSizeSpinner.isVisible =
                selectedMode == OutputMode.AGGREGATED_DATA_EXTRACTION_SINGLE || selectedMode == OutputMode.AGGREGATED_DATA_EXTRACTION_MULTI
        }

        fun getOutputMode(): OutputMode {
            return outputModeCombo.selectedItem as OutputMode
        }


        fun addPattern() {
            val pattern = patternInput.text.trim()
            if (pattern.isNotEmpty()) {
                // Validate pattern before adding
                val isRegex = useRegexCheckbox.isSelected
                if (!isValidPattern(pattern, isRegex)) {
                    JOptionPane.showMessageDialog(
                        patternList,
                        "Invalid ${if (isRegex) "regex" else "glob"} pattern: $pattern",
                        "Pattern Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                    return
                }
                patternListModel.addElement(
                    FilePattern(
                        pattern,
                        isContextCheckbox.isSelected,
                        isRegex,
                        isExclusionCheckbox.isSelected,
                        baseDirectory = when (baseDirectoryMode.selectedItem as BaseDirectoryMode) {
                            BaseDirectoryMode.SELECTED_DIRECTORY -> selectedDirectory?.toString() ?: project?.basePath
                            ?: ""

                            BaseDirectoryMode.MODULE_ROOT -> project?.basePath ?: selectedDirectory?.toString() ?: ""
                        }
                    )
                )
                patternInput.text = ""
                isContextCheckbox.isSelected = false
                useRegexCheckbox.isSelected = false
                isExclusionCheckbox.isSelected = false
                isContextCheckbox.isEnabled = true
                schedulePreviewUpdate()
            }
        }

        fun removeSelectedPattern() {
            val selectedIndex = patternList.selectedIndex
            if (selectedIndex >= 0) {
                patternListModel.removeElementAt(selectedIndex)
                fileCache.clear() // Clear cache when patterns change
                schedulePreviewUpdate()
            }
        }

        private fun schedulePreviewUpdate() {
            if (isDisposed) return
            // Prevent concurrent updates
            if (!isUpdating.compareAndSet(false, true)) {
                return
            }


            // Cancel any pending preview update
            currentPreviewTask?.cancel(false)
            // Throttle updates to avoid excessive computation
            val now = System.currentTimeMillis()
            if (now - lastUpdateTime.get() < 100) {
                return
            }
            lastUpdateTime.set(now)

            // Schedule a new preview update with delay
            currentPreviewTask = previewExecutor.submit {
                try {
                    Thread.sleep(PREVIEW_UPDATE_DELAY_MS)
                    if (!Thread.currentThread().isInterrupted && !isDisposed) {
                        SwingUtilities.invokeLater {
                            if (!isDisposed) {
                                updatePreview()
                            } else {
                                isUpdating.set(false)
                            }
                        }
                    }
                } catch (e: InterruptedException) {
                    // Task was cancelled, do nothing
                    Thread.currentThread().interrupt()
                } finally {
                    isUpdating.set(false)
                }
            }
        }

        private fun updatePreview() {
            if (isDisposed) return

            // Show loading indicator
            SwingUtilities.invokeLater {
                if (isDisposed) return@invokeLater
                previewArea.text = "Loading preview..."
                fileVolumeLabel.text = "Calculating..."
                fileSetCountLabel.text = "Calculating..."
                avgFileSetSizeLabel.text = "Calculating..."
                maxFileSetSizeLabel.text = "Calculating..."
            }

            // Perform expensive operations in background
            previewExecutor.submit {
                try {
                    val root = getRoot()
                    val fileSets = resolveFileSets(root)
                    val contextFiles = resolveContextFiles(root)
                    // Calculate file size statistics and update labels
                    val (totalSize, avgFileSetSize, maxFileSetSize) = calculateFileSizeStatistics(
                        fileSets, contextFiles
                    )
                    // Build preview text
                    val preview = buildPreviewText(root, fileSets, contextFiles)
                    // Update UI on EDT
                    SwingUtilities.invokeLater {
                        updatePreviewUI(totalSize, avgFileSetSize, maxFileSetSize, fileSets.size, preview)
                    }
                } catch (e: Exception) {
                    log.error("Error updating preview", e)
                    SwingUtilities.invokeLater {
                        previewArea.text = "Error loading preview: ${e.message}"
                    }
                }
            }
        }

        private fun updatePreviewUI(
            totalSize: Long, avgFileSetSize: Long, maxFileSetSize: Long, fileSetCount: Int, preview: String
        ) {
            val totalSizeKB = totalSize / 1024
            val totalSizeMB = totalSizeKB / 1024
            val sizeText = when {
                totalSizeMB > 1 -> String.format("Total size: %.1f MB", totalSizeMB.toDouble())
                totalSizeKB > 1 -> "Total size: ${totalSizeKB} KB"
                else -> "Total size: ${totalSize} bytes"
            }
            fileVolumeLabel.text = sizeText
            fileSetCountLabel.text = "FileSets: $fileSetCount"
            // Update average fileset size label
            val avgSizeKB = avgFileSetSize / 1024
            val avgSizeMB = avgSizeKB / 1024
            val avgSizeText = when {
                avgSizeMB > 1 -> String.format("Avg size: %.1f MB", avgSizeMB.toDouble())
                avgSizeKB > 1 -> "Avg size: ${avgSizeKB} KB"
                else -> "Avg size: ${avgFileSetSize} bytes"
            }
            avgFileSetSizeLabel.text = avgSizeText
            // Update max fileset size label
            val maxSizeKB = maxFileSetSize / 1024
            val maxSizeMB = maxSizeKB / 1024
            val maxSizeText = when {
                maxSizeMB > 1 -> String.format("Max size: %.1f MB", maxSizeMB.toDouble())
                maxSizeKB > 1 -> "Max size: ${maxSizeKB} KB"
                else -> "Max size: ${maxFileSetSize} bytes"
            }
            maxFileSetSizeLabel.text = maxSizeText
            previewArea.text = preview
        }

        private fun buildPreviewText(root: Path, fileSets: List<FileSet>, contextFiles: List<Path>): String {
            val preview = StringBuilder()
            if (contextFiles.isNotEmpty()) {
                preview.append("Context Files (included in all calls):\n")
                contextFiles.take(MAX_PREVIEW_FILES).forEach { file ->
                    preview.append("  - ${root.relativize(file)}\n")
                }
                if (contextFiles.size > MAX_PREVIEW_FILES) {
                    preview.append("  ... and ${contextFiles.size - MAX_PREVIEW_FILES} more files\n")
                }
                preview.append("\n")
            }
            preview.append("File Sets to Process:\n")
            fileSets.forEach { fileSet ->
                preview.append("${fileSet.name}:\n")
                fileSet.files.take(MAX_PREVIEW_FILES).forEach { file ->
                    preview.append("  - ${root.relativize(file)}\n")
                }
                if (fileSet.files.size > MAX_PREVIEW_FILES) {
                    preview.append("  ... and ${fileSet.files.size - MAX_PREVIEW_FILES} more files\n")
                }
                preview.append("\n")
            }
            if (fileSets.isEmpty() && contextFiles.isEmpty()) {
                preview.append("No files match the current patterns.")
            }
            return preview.toString()
        }

        private fun calculateFileSizeStatistics(
            fileSets: List<FileSet>, contextFiles: List<Path>
        ): Triple<Long, Long, Long> {
            var totalProcessedSize = 0L
            val fileSetSizes = mutableListOf<Long>()
            val root = getRoot()

            // Calculate processed size for context files (included in every call)
            var contextSize = 0L
            contextFiles.forEach { file ->
                try {
                    if (Files.isRegularFile(file)) {
                        val rawSize = Files.size(file)
                        val relativePath = root.relativize(file).toString()
                        val fileExtension = file.toString().split('.').lastOrNull() ?: ""

                        // Account for markdown formatting overhead
                        val headerSize = "# Context File: $relativePath\n```$fileExtension\n".length
                        val footerSize = "\n```\n\n".length
                        val formattedSize = rawSize + headerSize + footerSize
                        contextSize += formattedSize

                    }
                } catch (e: Exception) {
                    // Ignore files that can't be read
                }
            }

            // Calculate processed size for fileset files
            fileSets.forEach { fileSet ->
                var fileSetSize = contextSize // Each fileset includes context
                fileSet.files.forEach { file ->
                    try {
                        if (Files.isRegularFile(file)) {
                            val rawSize = Files.size(file)
                            val relativePath = root.relativize(file).toString()
                            val fileExtension = file.toString().split('.').lastOrNull() ?: ""

                            // Account for markdown formatting overhead
                            val headerSize = "# File: $relativePath\n```$fileExtension\n".length
                            val footerSize = "\n```\n\n".length
                            val formattedSize = rawSize + headerSize + footerSize

                            fileSetSize += formattedSize
                        }
                    } catch (e: Exception) {
                        // Ignore files that can't be read
                    }
                }
                fileSetSizes.add(fileSetSize)
                totalProcessedSize += fileSetSize
            }

            val avgFileSetSize = if (fileSetSizes.isNotEmpty()) fileSetSizes.average().toLong() else 0L
            val maxFileSetSize = fileSetSizes.maxOrNull() ?: 0L

            return Triple(totalProcessedSize, avgFileSetSize, maxFileSetSize)
        }


        fun getRoot(): Path {
            return when (baseDirectoryMode.selectedItem as BaseDirectoryMode) {
                BaseDirectoryMode.SELECTED_DIRECTORY -> selectedDirectory ?: project?.basePath?.let { Path.of(it) }
                ?: Path.of(".")

                BaseDirectoryMode.MODULE_ROOT -> project?.basePath?.let { Path.of(it) } ?: selectedDirectory ?: Path.of(
                    "."
                )

            }
        }

        fun getSelectedDirectory(): Path? {
            return selectedDirectory
        }

        fun resolveFileSets(root: Path): List<FileSet> {
            // First get inclusion patterns
            val patterns = (0 until patternListModel.size).map { patternListModel.getElementAt(it) }
                .filter { !it.isContext && !it.isExclusion }

            // Then get exclusion patterns
            val exclusionPatterns =
                (0 until patternListModel.size).map { patternListModel.getElementAt(it) }.filter { it.isExclusion }

            val fileSets = patterns.flatMap { pattern ->
                val patternRoot = if (pattern.baseDirectory.isNotEmpty()) {
                    Path.of(pattern.baseDirectory)
                } else {
                    root
                }
                resolvePattern(patternRoot, pattern)
            }
            // Apply exclusions to each file set
            return fileSets.map { fileSet ->
                val filteredFiles = fileSet.files.filter { file ->
                    !isExcluded(file, exclusionPatterns)
                }
                fileSet.copy(files = filteredFiles)
            }.filter { it.files.isNotEmpty() }
        }

        fun resolveContextFiles(root: Path): List<Path> {
            val contextPatterns = (0 until patternListModel.size).map { patternListModel.getElementAt(it) }
                .filter { it.isContext && !it.isExclusion }

            val exclusionPatterns =
                (0 until patternListModel.size).map { patternListModel.getElementAt(it) }.filter { it.isExclusion }

            val contextFiles = contextPatterns.flatMap { pattern ->
                try {
                    val patternRoot = if (pattern.baseDirectory.isNotEmpty()) {
                        Path.of(pattern.baseDirectory)
                    } else {
                        root
                    }
                    val matcher = getOrCreateMatcher(pattern.pattern)
                    Files.walk(patternRoot).use { stream ->
                        stream.filter {
                            Files.isRegularFile(it) && isLLMTextFile(
                                it.toFile(),
                                treatDocumentsAsText.isSelected
                            )
                        }.filter { path ->
                            val relativePath = patternRoot.relativize(path)
                            // Skip current directory references
                            if (relativePath.toString().isEmpty() || relativePath.toString() == ".") {
                                false
                            } else {
                                matcher.matches(relativePath)
                            }
                        }.toList()
                    }
                } catch (e: Exception) {
                    log.warn("Error resolving context pattern: ${pattern.pattern}", e)
                    emptyList()
                }
            }
            // Apply exclusions
            return contextFiles.filter { file ->
                !isExcluded(file, exclusionPatterns)
            }
        }

        private fun isExcluded(file: Path, exclusionPatterns: List<FilePattern>): Boolean {
            return exclusionPatterns.any { pattern ->
                try {
                    val patternRoot = if (pattern.baseDirectory.isNotEmpty()) {
                        Path.of(pattern.baseDirectory)
                    } else {
                        getRoot()
                    }
                    val relativePath = patternRoot.relativize(file)
                    if (pattern.isRegex) {
                        val regex = pattern.pattern.toRegex()
                        regex.matches(relativePath.toString())
                    } else {
                        val matcher = getOrCreateMatcher(pattern.pattern)
                        matcher.matches(relativePath)
                    }
                } catch (e: Exception) {
                    false
                }
            }
        }

        private fun getOrCreateMatcher(pattern: String): PathMatcher {
            return patternCache.computeIfAbsent(pattern) {
                FileSystems.getDefault().getPathMatcher("glob:$it")
            }
        }


        private fun isValidPattern(pattern: String, isRegex: Boolean): Boolean {
            return try {
                if (isRegex) {
                    pattern.toRegex()
                    true // Regex pattern is valid if it compiles
                } else {
                    FileSystems.getDefault().getPathMatcher("glob:$pattern")
                    true // Glob pattern is valid if PathMatcher can be created
                }
            } catch (e: Exception) {
                false
            }
        }

        private fun resolvePattern(root: Path, pattern: FilePattern): List<FileSet> {
            return try {
                // Validate root path
                if (!Files.exists(root)) {
                    log.warn("Root path does not exist: $root")
                    return emptyList()
                }

                // Check cache first
                val cacheKey = root.resolve(pattern.pattern)
                val cachedEntry = fileCache[cacheKey]
                if (cachedEntry != null && !cachedEntry.isExpired()) {
                    return cachedEntry.paths.mapNotNull { path ->
                        when {
                            Files.isDirectory(path) -> processDirectory(root, path)
                            Files.isRegularFile(path) && isLLMTextFile(
                                path.toFile(), treatDocumentsAsText.isSelected
                            ) -> {
                                FileSet(root.relativize(path).toString(), cacheKey, listOf(path))
                            }

                            else -> null
                        }
                    }
                }

                val matchedPaths = if (pattern.isRegex) {
                    val regex =
                        pattern.toRegex() ?: throw IllegalArgumentException("Invalid regex pattern: ${pattern.pattern}")
                    Files.walk(root, FileVisitOption.FOLLOW_LINKS).use { stream ->
                        stream.parallel() // Use parallel processing for large directories
                            .filter { path ->
                                val relativePath = root.relativize(path)
                                // Skip current directory references
                                if (relativePath.toString().isEmpty() || relativePath.toString() == ".") {
                                    false
                                } else {
                                    try {
                                        regex.matches(relativePath.toString())
                                    } catch (e: Exception) {
                                        log.warn("Regex matching failed for path: $relativePath", e)
                                        false
                                    }
                                }
                            }.filter { Files.isRegularFile(it) || Files.isDirectory(it) }.limit(MAX_FILES_PER_PATTERN)
                            .toList()
                    }
                } else {
                    val matcher = getOrCreateMatcher(pattern.pattern)
                    Files.walk(root, MAX_WALK_DEPTH).use { stream ->
                        stream.filter { path -> matcher.matches(root.relativize(path)) }
                            .filter { Files.isRegularFile(it) || Files.isDirectory(it) }.limit(MAX_FILES_PER_PATTERN)
                            .toList()
                    }
                }
                // Cache the results
                fileCache[cacheKey] = CacheEntry(matchedPaths)

                // Clean up expired cache entries periodically
                if (fileCache.size > 100) {
                    fileCache.entries.removeIf { it.value.isExpired() }
                }

                // Filter out parent directories when child directories/files are also matched
                val filteredPaths = filterOutParentDirectories(matchedPaths)

                filteredPaths.mapNotNull { path ->
                    when {
                        Files.isDirectory(path) -> processDirectory(root, path)
                        Files.isRegularFile(path) && isLLMTextFile(path.toFile(), treatDocumentsAsText.isSelected) -> {
                            FileSet(root.relativize(path).toString(), path, listOf(path))
                        }

                        else -> null
                    }
                }
            } catch (e: Exception) {
                log.warn("Error resolving pattern: ${pattern.pattern}", e)
                emptyList()
            }
        }

        private fun filterOutParentDirectories(paths: List<Path>): List<Path> {
            val sortedPaths = paths.sortedBy { it.nameCount }
            val filteredPaths = mutableListOf<Path>()
            for (path in sortedPaths) {
                val isParentOfOtherMatch = sortedPaths.any { otherPath ->
                    otherPath != path && otherPath.startsWith(path)
                }
                if (!isParentOfOtherMatch) {
                    filteredPaths.add(path)
                }
            }
            return filteredPaths
        }

        private fun processDirectory(root: Path, directory: Path): FileSet? {
            return try {
                val dirFiles = Files.walk(directory, MAX_WALK_DEPTH).use { stream ->
                    stream.filter {
                        Files.isRegularFile(it) && isLLMTextFile(
                            it.toFile(), treatDocumentsAsText.isSelected
                        )
                    }.limit(MAX_FILES_PER_PATTERN).toList()
                }
                if (dirFiles.isNotEmpty()) {
                    FileSet(root.relativize(directory).toString(), directory, dirFiles)
                } else null
            } catch (e: Exception) {
                log.warn("Error processing directory: $directory", e)
                null
            }
        }
    }

    class UserSettings(
        var transformationMessage: String = "Review and improve the code according to best practices",
        var patterns: List<FilePattern> = listOf(),
        var autoApply: Boolean = false,
        var treatDocumentsAsText: Boolean = false,
        var outputMode: OutputMode = OutputMode.EDIT_FILES,
        var outputFilename: String = "output.md",
        var outputDirectory: String = "output/",
        var concurrency: Int = 4,
        var bigDataThreshold: Int = 100,
        var aggregationSizeKB: Int = 10
    )

    class Settings(
        val settings: UserSettings? = null,
        val project: Project? = null,
    )

    override fun handle(e: AnActionEvent) {
        val project = e.project
        val selectedDirectory = getSelectedDirectory(e)
        val settingsUI = SettingsUI(project, selectedDirectory).apply {
            transformationMessage.text = "Review and improve the code according to best practices"
            autoApply.isSelected = false
            outputFilename.text = "output.md"
            outputDirectory.text = "output/"
        }
        // (project, settingsUI, "Custom File Set Patch")
        val dialog = object : DialogWrapper(project, false) {
            val userSettings = UserSettings()

            init {
                this.title = title
                isModal = false
                init()
            }

            override fun createCenterPanel(): JComponent {
                return JPanel(BorderLayout()).apply {
                    val leftPanel = JPanel(BorderLayout()).apply {
                        preferredSize = Dimension(400, 600)

                        val patternPanel = JPanel(BorderLayout()).apply {
                            border = JBUI.Borders.empty(10)

                            val inputPanel = JPanel(BorderLayout()).apply {
                                add(JLabel("File Pattern (glob syntax):"), BorderLayout.NORTH)
                                val patternAndBasePanel = JPanel(BorderLayout()).apply {
                                    add(settingsUI.patternInput, BorderLayout.CENTER)
                                }
                                add(patternAndBasePanel, BorderLayout.CENTER)

                                val checkboxPanel = JPanel().apply {
                                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                    add(settingsUI.isContextCheckbox)
                                    add(settingsUI.isExclusionCheckbox)
                                    add(settingsUI.useRegexCheckbox)
                                }
                                add(checkboxPanel, BorderLayout.AFTER_LAST_LINE)
                            }

                            val buttonPanel = JPanel().apply {
                                layout = BoxLayout(this, BoxLayout.X_AXIS)
                                val addButton = JButton("Add Pattern").apply {
                                    addActionListener { settingsUI.addPattern() }
                                }
                                val removeButton = JButton("Remove Selected").apply {
                                    addActionListener { settingsUI.removeSelectedPattern() }
                                }
                                add(addButton)
                                add(Box.createHorizontalStrut(10))
                                add(removeButton)
                            }

                            add(inputPanel, BorderLayout.NORTH)
                            add(buttonPanel, BorderLayout.CENTER)
                        }
                        val baseDirectoryPanel = JPanel(BorderLayout()).apply {
                            border = JBUI.Borders.empty(10)
                            add(JLabel("Base Directory:"), BorderLayout.NORTH)
                            add(settingsUI.baseDirectoryMode, BorderLayout.CENTER)
                        }


                        val listPanel = JPanel(BorderLayout()).apply {
                            border = JBUI.Borders.empty(10)
                            add(JLabel("Patterns:"), BorderLayout.NORTH)
                            add(JBScrollPane(settingsUI.patternList), BorderLayout.CENTER)
                        }

                        val instructionPanel = JPanel(BorderLayout()).apply {
                            border = JBUI.Borders.empty(10)
                            add(JLabel("AI Instruction:"), BorderLayout.NORTH)
                            add(JBScrollPane(settingsUI.transformationMessage), BorderLayout.CENTER)

                            val optionsPanel = JPanel().apply {
                                layout = BoxLayout(this, BoxLayout.Y_AXIS)

                                val outputModePanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                                    border = BorderFactory.createTitledBorder("Output Mode")
                                    add(settingsUI.outputModeCombo)
                                }
                                add(outputModePanel)

                                val outputOptionsPanel = JPanel().apply {
                                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                    add(settingsUI.autoApply)
                                    add(settingsUI.treatDocumentsAsText)

                                    val filePanel = JPanel(BorderLayout()).apply {
                                        add(JLabel("Output File:"), BorderLayout.WEST)
                                        add(settingsUI.outputFilename, BorderLayout.CENTER)
                                    }
                                    add(filePanel)

                                    val dirPanel = JPanel(BorderLayout()).apply {
                                        add(JLabel("Output Directory:"), BorderLayout.WEST)
                                        add(settingsUI.outputDirectory, BorderLayout.CENTER)
                                    }
                                    add(dirPanel)
                                    val aggregationPanel = JPanel(BorderLayout()).apply {
                                        add(JLabel("Aggregation Size (KB):"), BorderLayout.WEST)
                                        add(settingsUI.aggregationSizeSpinner, BorderLayout.CENTER)
                                    }
                                    add(aggregationPanel)

                                    val concurrencyPanel = JPanel(BorderLayout()).apply {
                                        add(JLabel("Concurrency:"), BorderLayout.WEST)
                                        add(settingsUI.concurrencySpinner, BorderLayout.CENTER)
                                    }
                                    add(concurrencyPanel)
                                }
                                add(outputOptionsPanel)
                            }
                            add(optionsPanel, BorderLayout.SOUTH)
                        }

                        val topPanel = JPanel(BorderLayout()).apply {
                            add(baseDirectoryPanel, BorderLayout.NORTH)
                            add(patternPanel, BorderLayout.CENTER)
                        }

                        add(topPanel, BorderLayout.NORTH)
                        add(listPanel, BorderLayout.CENTER)
                        add(instructionPanel, BorderLayout.SOUTH)
                    }

                    val rightPanel = JPanel(BorderLayout()).apply {
                        preferredSize = Dimension(500, 600)
                        border = JBUI.Borders.empty(10)

                        val topPanel = JPanel(BorderLayout()).apply {
                            val labelPanel = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                                add(JLabel("Preview:"))
                                add(Box.createHorizontalStrut(20))
                                add(settingsUI.fileVolumeLabel)
                                add(Box.createHorizontalStrut(20))
                                add(settingsUI.fileSetCountLabel)
                                add(Box.createHorizontalStrut(20))
                                add(settingsUI.avgFileSetSizeLabel)
                                add(Box.createHorizontalStrut(20))
                                add(settingsUI.maxFileSetSizeLabel)
                            }
                            add(labelPanel, BorderLayout.WEST)
                        }
                        add(topPanel, BorderLayout.NORTH)
                        add(JBScrollPane(settingsUI.previewArea), BorderLayout.CENTER)
                    }

                    add(leftPanel, BorderLayout.WEST)
                    add(rightPanel, BorderLayout.CENTER)

                    preferredSize = Dimension(900, 600)
                }
            }

            override fun doOKAction() {
                // Validate settings before proceeding
                if (settingsUI.patternListModel.isEmpty) {
                    JOptionPane.showMessageDialog(
                        this.contentPane,
                        "Please add at least one file pattern",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                    return
                }
                // Validate output settings for generation modes
                if (settingsUI.getOutputMode() != OutputMode.EDIT_FILES) {
                    val outputDir = settingsUI.outputDirectory.text.trim()
                    if (outputDir.isEmpty()) {
                        JOptionPane.showMessageDialog(
                            this.contentPane,
                            "Please specify an output directory",
                            "Validation Error",
                            JOptionPane.ERROR_MESSAGE
                        )
                        return
                    }
                }

                userSettings.apply {
                    transformationMessage = settingsUI.transformationMessage.text
                    patterns =
                        (0 until settingsUI.patternListModel.size).map { settingsUI.patternListModel.getElementAt(it) }
                    autoApply = settingsUI.autoApply.isSelected
                    treatDocumentsAsText = settingsUI.treatDocumentsAsText.isSelected
                    outputFilename = settingsUI.outputFilename.text
                    outputMode = settingsUI.getOutputMode()
                    outputDirectory = selectedDirectory?.resolve(settingsUI.outputDirectory.text)?.toString()
                        ?: settingsUI.outputDirectory.text
                    concurrency = settingsUI.concurrencySpinner.value as Int
                    bigDataThreshold = settingsUI.bigDataThresholdSpinner.value as Int
                    aggregationSizeKB = settingsUI.aggregationSizeSpinner.value as Int
                }
                super.doOKAction()
                // Handle the actual action execution here since dialog is non-modal
                executeAction()
            }

            override fun dispose() {
                settingsUI.dispose()
                super.dispose()
            }

            private fun executeAction() {
                try {
                    val session = Session.newGlobalID()
                    SessionProxyServer.metadataStorage.setSessionName(
                        null,
                        session,
                        "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                    )

                    SessionProxyServer.chats[session] = CustomFileSetPatchServer(
                        config = Settings(userSettings, project),
                        autoApply = userSettings.autoApply,
                        outputMode = userSettings.outputMode,
                        processor = AppSettingsState.instance.processor
                    )

                    ApplicationServer.appInfoMap[session] = AppInfoData(
                        applicationName = "Custom File Set Patch",
                        inputCnt = 1,
                        stickyInput = false,
                        loadImages = false,
                        showMenubar = false
                    )

                    CompletableFuture.runAsync({
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
                    }, Executors.newSingleThreadExecutor())
                } catch (e: Exception) {
                    log.error("Failed to execute action", e)
                    JOptionPane.showMessageDialog(
                        null, "Failed to start server: ${e.message}", "Error", JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
        dialog.show()
        // Note: Non-modal dialog doesn't block, action is executed via doOKAction
    }

    private fun getSelectedDirectory(e: AnActionEvent): Path? {
        val virtualFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        val selectedFile = virtualFiles?.firstOrNull()
        return when {
            selectedFile?.isDirectory == true -> selectedFile.toNioPath()
            selectedFile != null -> selectedFile.parent?.toNioPath()
            else -> e.project?.basePath?.let { Path.of(it) }
        }
    }


    override fun isEnabled(event: AnActionEvent): Boolean {
        return super.isEnabled(event) && event.project != null
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/agent/CustomFileSetPatchServer.kt

```
package cognotik.actions.agent

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

class CustomFileSetPatchServer(
    val config: CustomFileSetPatchAction.Settings,
    val autoApply: Boolean,
    val outputMode: CustomFileSetPatchAction.OutputMode,
    val processor: PatchProcessor
) : ApplicationServer(
    applicationName = "Custom File Set Patch",
    path = "/customFileSetPatch",
    showMenubar = false,
), AutoCloseable {
    companion object {
        private val log = LoggerFactory.getLogger(CustomFileSetPatchServer::class.java)
        private const val TASK_TIMEOUT_MINUTES = 30L
        private const val MAX_FILE_SIZE_MB = 10
        private const val MAX_CONTEXT_LENGTH = 100_000
        private const val BATCH_SIZE = 10
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 1000L
        private const val EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 60L
    }

    private var _root: Path? = null
    private var _selectedDirectory: Path? = null
    private val outputLock = ReentrantReadWriteLock()
    private val outputWritten = AtomicBoolean(false)
    private val processedCount = AtomicInteger(0)
    private val currentlyProcessing = ConcurrentHashMap<String, String>()
    private val completedFileSets = ConcurrentLinkedQueue<String>()
    private val errorCount = AtomicInteger(0)
    private val startTime = AtomicReference<Long>()
    private val executorService = Executors.newCachedThreadPool { r ->
        Thread(r, "FileSetProcessor").apply { isDaemon = true }
    }

    @Volatile
    private var isShutdown = false

    override val inputCnt = 0
    override val stickyInput = true
    private fun getSelectedDirectory(): Path? {
        return _selectedDirectory
    }

    private val mainActor: ChatAgent
        get() {
            val prompt = when (outputMode) {
                CustomFileSetPatchAction.OutputMode.EDIT_FILES -> """
                    You are a helpful AI that helps people with coding.
                    You will be reviewing and improving code files based on the provided instruction.
                    Please analyze the code and suggest improvements according to the given requirements.
                    Response should use one or more code patches in diff format within ```diff code blocks.
                    Each diff should be preceded by a header that identifies the file being modified.
                    The diff format should use + for line additions, - for line deletions.
                    The diff should include 2 lines of context before and after every change.
                """.trimIndent()

                CustomFileSetPatchAction.OutputMode.GENERATE_DOCUMENTATION_SINGLE,
                CustomFileSetPatchAction.OutputMode.GENERATE_DOCUMENTATION_MULTI -> """
                    You are a helpful AI that helps people with documentation.
                    You will be creating documentation for code files based on the provided instruction.
                    Please analyze the code and create comprehensive documentation according to the given requirements.
                    Response should be in markdown format with clear sections and explanations.
                """.trimIndent()

                CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_SINGLE,
                CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_MULTI -> """
                    You are a helpful AI that helps people with data extraction and analysis.
                    You will be extracting and aggregating data from multiple code files based on the provided instruction.
                    Please analyze the code files and extract relevant information according to the given requirements.
                    Response should be in markdown format with structured data and clear summaries.
                """.trimIndent()

            }

            return ChatAgent(
                prompt = prompt,
                model = AppSettingsState.instance.smartChatClient,
                temperature = AppSettingsState.instance.temperature,
            )
        }

    override fun close() {
        isShutdown = true
        executorService.shutdown()
        try {
            if (!executorService.awaitTermination(EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executorService.shutdownNow()
                log.warn("Executor service did not terminate gracefully")
            }
        } catch (e: InterruptedException) {
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }
        currentlyProcessing.clear()
        completedFileSets.clear()
    }

    private fun initializeSingleOutputFile(): Path {
        val selectedDirectory = getSelectedDirectory()
        val outputDir = selectedDirectory?.resolve(config.settings?.outputDirectory ?: "output")
            ?: throw IllegalStateException("Selected directory is not set")
        require(outputDir.toString().isNotBlank()) { "Output directory cannot be blank" }

        try {
            Files.createDirectories(outputDir)
        } catch (e: IOException) {
            log.error("Failed to create output directory: $outputDir", e)
            throw IllegalStateException("Cannot create output directory: ${e.message}", e)
        }

        val outputFile = outputDir.resolve(config.settings?.outputFilename ?: "output.${getFileExtension()}")
        // Validate output file path
        require(!Files.isDirectory(outputFile)) { "Output file path points to a directory: $outputFile" }
        require(outputFile.parent != null && Files.exists(outputFile.parent)) {
            "Parent directory does not exist: ${outputFile.parent}"
        }

        // Create/truncate the file and write header
        try {
            Files.newBufferedWriter(
                outputFile, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            ).use { writer ->
                writer.write("# Generated Output\n\n")
                writer.write("Generated at: ${java.time.LocalDateTime.now()}\n\n")
            }
        } catch (e: IOException) {
            log.error("Failed to initialize output file: $outputFile", e)
            throw IllegalStateException("Cannot initialize output file: ${e.message}", e)
        }

        return outputFile
    }

    private fun appendToSingleOutputFile(outputFile: Path, fileSetName: String, content: String) {
        outputLock.write {
            var retryCount = 0
            var lastException: IOException? = null
            // Create temp file for atomic write
            val tempFile = Files.createTempFile(outputFile.parent, "output", ".tmp")

            while (retryCount < MAX_RETRY_ATTEMPTS) {
                try {
                    // Read existing content if file exists
                    val existingContent = if (Files.exists(outputFile)) {
                        Files.readString(outputFile)
                    } else {
                        ""
                    }

                    // Write to temp file
                    Files.newBufferedWriter(tempFile).use { writer ->
                        writer.write(existingContent)
                        if (existingContent.isNotEmpty()) {
                            writer.write("\n\n---\n\n")
                        }
                        writer.write("## $fileSetName\n\n")
                        writer.write(content)
                    }
                    // Atomic move
                    Files.move(
                        tempFile,
                        outputFile,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                    )
                    outputWritten.set(true)
                    return // Success
                } catch (e: IOException) {
                    lastException = e
                    retryCount++
                    if (retryCount < MAX_RETRY_ATTEMPTS) {
                        log.warn("Failed to append to output file (attempt $retryCount): $outputFile", e)
                        Thread.sleep(RETRY_DELAY_MS)
                    }
                }
            }
            log.error("Failed to append to output file after $MAX_RETRY_ATTEMPTS attempts: $outputFile", lastException)
            throw IllegalStateException(
                "Cannot write to output file after $MAX_RETRY_ATTEMPTS attempts: ${lastException?.message}",
                lastException
            )
        }
    }

    private fun finalizeSingleOutputFile(outputFile: Path, session: Session, task: SessionTask) {
        val message =
            "<a href='fileIndex/$session/${_selectedDirectory?.relativize(outputFile) ?: outputFile}'>Generated: ${
                _selectedDirectory?.relativize(
                    outputFile
                ) ?: outputFile
            }</a>"
        task.add(message)
    }

    override fun newSession(
        user: User, session: Session
    ): SocketManager {
        val socketManager = super.newSession(user, session)
        // Validate configuration early
        if (config.settings == null) {
            val task = socketManager.newTask(cancelable = false, root = true)
            task.error(IllegalStateException("Configuration settings are missing"))
            return socketManager
        }


        val task = socketManager.newTask(cancelable = false, root = true)
        val tabs: TabbedDisplay? = null //TabbedDisplay(task)
        val userMessage = config.settings.transformationMessage
        // Validate user message
        if (userMessage.isBlank()) {
            task.error(IllegalArgumentException("Transformation message cannot be blank"))
            return socketManager
        }


        val settingsUI = CustomFileSetPatchAction.SettingsUI(
            config.project,
            selectedDirectory = config.settings.outputDirectory.let { File(it).toPath() },
        )

        config.settings.patterns.forEach { pattern ->
            settingsUI.patternListModel.addElement(pattern)
        }
        // Set the treatDocumentsAsText option from config
        settingsUI.treatDocumentsAsText.isSelected = config.settings.treatDocumentsAsText
        // Get the root directory from the settings UI which handles base directory mode
        _root = settingsUI.getRoot()
        _selectedDirectory = settingsUI.getSelectedDirectory()
        val contextFiles = settingsUI.resolveContextFiles(_root!!)
        val fileSets = settingsUI.resolveFileSets(_root!!)
        if (fileSets.isEmpty()) {
            task.error(IllegalArgumentException("No files match the specified patterns"))
            return socketManager
        }

        val contextSummary = buildContextSummary(contextFiles)
        val status: StringBuilder = task.add("Starting...<br/>")!!
        val concurrency = config.settings.concurrency
        val fixedConcurrencyProcessor = FixedConcurrencyProcessor(socketManager.pool, concurrency)
        val bigDataThreshold = config.settings.bigDataThreshold
        val useBigDataMode = fileSets.size > bigDataThreshold
        startTime.set(System.currentTimeMillis())
        // Aggregate file sets if in aggregated data extraction mode
        val processFileSets = if (outputMode == CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_SINGLE ||
            outputMode == CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_MULTI
        ) {
            aggregateFileSets(fileSets, config.settings.aggregationSizeKB)
        } else {
            fileSets
        }
        // Validate file sets
        if (processFileSets.isEmpty()) {
            task.error(IllegalStateException("No file sets to process after aggregation"))
            return socketManager
        }

        // Initialize single output file if needed
        val singleOutputFile =
            if (outputMode == CustomFileSetPatchAction.OutputMode.GENERATE_DOCUMENTATION_SINGLE ||
                outputMode == CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_SINGLE
            ) {
                initializeSingleOutputFile()
            } else null

        if (useBigDataMode && outputMode != CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_SINGLE &&
            outputMode != CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_MULTI
        ) {
            // Big data mode: use mid-level subsession for decoupling
            status.set("Processing ${processFileSets.size} file sets in big data mode (batches of $BATCH_SIZE)...<br/>")
            val progressStatus = task.add("")!!
            val errorStatus = task.add("")!!
            val completionLatch = CountDownLatch(processFileSets.size)

            val futures = processFileSets.chunked(BATCH_SIZE).flatMap { batch ->
                batch.map { fileSet ->
                    fixedConcurrencyProcessor.submit {
                        if (isShutdown) {
                            log.info("Skipping processing due to shutdown: ${fileSet.name}")
                            completionLatch.countDown()
                            return@submit
                        }
                        val fileSetName = fileSet.name
                        currentlyProcessing[fileSetName] = fileSetName

                        try {
                            // Create a subsession for this file set
                            val subSession = task.newSession()
                            val subTask = subSession.newTask()

                            processFileSet(
                                fileSet = fileSet,
                                contextSummary = contextSummary,
                                userMessage = userMessage,
                                tabs = null, // No tabs in big data mode
                                task = subTask,
                                session = session,
                                singleOutputFile = singleOutputFile,
                                useBigDataMode = true,
                                socketManager = socketManager
                            )

                            completedFileSets.offer(fileSetName)
                        } catch (e: Exception) {
                            log.error("Error processing file set: $fileSetName", e)
                            errorCount.incrementAndGet()
                            errorStatus.set(
                                """<div class="error-status" style="color: red;">
                                Errors: ${errorCount.get()} file sets failed
                            </div>""".trimIndent()
                            )
                        } finally {
                            completionLatch.countDown()
                            currentlyProcessing.remove(fileSetName)
                            val completed = processedCount.incrementAndGet()
                            val elapsedSeconds = (System.currentTimeMillis() - startTime.get()) / 1000
                            val rate = if (elapsedSeconds > 0) completed.toDouble() / elapsedSeconds else 0.0
                            val estimatedRemaining =
                                if (rate > 0) ((processFileSets.size - completed) / rate).toInt() else 0

                            // Update progress status
                            val processingList = currentlyProcessing.values.take(3).joinToString(", ") { name ->
                                """<a href="#" class="processing-link">$name</a>"""
                            }
                            val remainingCount = currentlyProcessing.size - 3
                            val processingText = if (remainingCount > 0) {
                                "$processingList (+$remainingCount more)"
                            } else {
                                processingList
                            }

                            progressStatus.set(
                                """
                            <div class="progress-status">
                                <strong>Progress: $completed / ${processFileSets.size} file sets processed (${
                                    String.format(
                                        "%.1f", completed * 100.0 / processFileSets.size
                                    )
                                }%)</strong><br/>
                                <small>Rate: ${
                                    String.format(
                                        "%.2f", rate
                                    )
                                } files/sec | Est. remaining: ${estimatedRemaining}s</small><br/>
                                ${if (currentlyProcessing.isNotEmpty()) "Currently processing: $processingText" else ""}
                            </div>
                        """.trimIndent()
                            )
                            task.update()
                        }
                    }
                }
            }


            fixedConcurrencyProcessor.submit {
                val completedFutures = mutableListOf<Future<*>>()
                futures.forEach { future ->
                    try {
                        future.get(TASK_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                        completedFutures.add(future)
                    } catch (e: Exception) {
                        log.error("Error processing file set", e)
                    }
                }
                // Handle single output file for documentation/extracts
                singleOutputFile?.let { outputFile ->
                    finalizeSingleOutputFile(outputFile, session, task)
                }
                progressStatus.set(
                    """
                    <div class="progress-status">
                        <strong>✓ Processing complete: ${completedFutures.size} file sets processed successfully</strong><br/>
                        <small>Total time: ${(System.currentTimeMillis() - startTime.get()) / 1000}s | Errors: ${errorCount.get()}</small>
                    </div>
                """.trimIndent()
                )
                task.update()
            }
        } else {
            // Normal mode: existing behavior
            val futures = processFileSets.map { fileSet ->
                fixedConcurrencyProcessor.submit {
                    processFileSet(
                        fileSet = fileSet,
                        contextSummary = contextSummary,
                        userMessage = userMessage,
                        tabs = tabs,
                        task = task,
                        session = session,
                        singleOutputFile = singleOutputFile,
                        socketManager = socketManager
                    )
                }
            }.toMutableList()
            fixedConcurrencyProcessor.submit {
                val completedFutures = mutableListOf<Future<*>>()
                while (!futures.isEmpty()) {
                    try {
                        futures.removeFirst().apply {
                            try {
                                get(TASK_TIMEOUT_MINUTES, TimeUnit.MINUTES)
                                completedFutures.add(this)
                            } catch (e: Exception) {
                                log.error("Error processing file set", e)
                                status.append("Error processing file set: ${e.message}<br/>")
                            }
                        }
                    } catch (e: Exception) {
                        log.warn("Error updating task status", e)
                        task.error(e)
                    }
                }

                // Handle single output file for documentation/extracts
                singleOutputFile?.let { outputFile ->
                    finalizeSingleOutputFile(outputFile, session, task)
                }

                status.append("Processing complete. ${completedFutures.size} file sets processed successfully.<br/>")
                task.update()
            }
        }

        return socketManager
    }

    private fun aggregateFileSets(
        fileSets: List<CustomFileSetPatchAction.FileSet>,
        targetSizeKB: Int
    ): List<CustomFileSetPatchAction.FileSet> {
        val targetSizeBytes = targetSizeKB * 1024L
        val aggregatedSets = mutableListOf<CustomFileSetPatchAction.FileSet>()
        var currentFiles = mutableListOf<Path>()
        var currentSize = 0L
        var aggregateIndex = 1
        for (fileSet in fileSets) {
            for (file in fileSet.files) {
                try {
                    val fileSize = Files.size(file)
                    // If adding this file would exceed target size and we have files, create a new aggregate
                    if (currentSize + fileSize > targetSizeBytes && currentFiles.isNotEmpty()) {
                        aggregatedSets.add(
                            CustomFileSetPatchAction.FileSet(
                                "Aggregate_${aggregateIndex++}",
                                fileSet.base,
                                currentFiles.toList()
                            )
                        )
                        currentFiles = mutableListOf()
                        currentSize = 0L
                    }
                    currentFiles.add(file)
                    currentSize += fileSize
                } catch (e: Exception) {
                    log.warn("Error getting file size for aggregation: $file", e)
                }
            }
        }
        // Add remaining files if any
        if (currentFiles.isNotEmpty()) {
            aggregatedSets.add(
                CustomFileSetPatchAction.FileSet(
                    "Aggregate_${aggregateIndex}",
                    fileSets.lastOrNull()?.base ?: Path.of(""),
                    currentFiles.toList()
                )
            )
        }
        log.info("Aggregated ${fileSets.size} file sets into ${aggregatedSets.size} aggregated sets")
        return aggregatedSets
    }


    private fun buildContextSummary(contextFiles: List<Path>): String {
        return if (contextFiles.isNotEmpty()) {
            val contextBuilder = StringBuilder()
            var totalLength = 0
            var filesProcessed = 0
            val errors = mutableListOf<String>()

            for (path in contextFiles) {
                if (filesProcessed >= 100) { // Limit context files
                    log.warn("Context file limit reached (100 files)")
                    contextBuilder.append("\n... ${contextFiles.size - filesProcessed} more files omitted ...\n")
                    break
                }

                val fileContent = try {
                    val file = path.toFile()
                    if (!file.exists()) {
                        errors.add("File not found: $path")
                        continue
                    }
                    val fileSizeMB = file.length() / (1024 * 1024)
                    if (fileSizeMB > MAX_FILE_SIZE_MB) {
                        log.warn("Skipping large file: $path (${fileSizeMB}MB)")
                        continue
                    }
                    readFileContent(file)
                } catch (e: IOException) {
                    log.error("Error reading context file: $path", e)
                    continue
                }

                val fileSection = """
                    # Context File: ${_root?.relativize(path) ?: path}
                    ```${path.toString().split('.').lastOrNull() ?: ""}
                    $fileContent
                    ```
                """.trimIndent()

                if (totalLength + fileSection.length > MAX_CONTEXT_LENGTH) {
                    log.warn("Context size limit reached, truncating remaining files")
                    break
                }

                contextBuilder.append(fileSection).append("\n\n")
                totalLength += fileSection.length
                filesProcessed++
            }
            if (errors.isNotEmpty()) {
                log.warn("Errors reading context files: ${errors.joinToString(", ")}")
            }

            contextBuilder.toString()
        } else ""
    }

    private fun processFileSet(
        fileSet: CustomFileSetPatchAction.FileSet,
        contextSummary: String,
        userMessage: String,
        tabs: TabbedDisplay?,
        task: SessionTask,
        session: Session,
        singleOutputFile: Path?,
        useBigDataMode: Boolean = false,
        socketManager: SocketManager
    ) {
        try {
            var status: StringBuilder? = null
            val fileSetContent = buildFileSetContent(fileSet)
            val fullContent = if (contextSummary.isNotEmpty()) {
                "$contextSummary\n\n$fileSetContent"
            } else {
                fileSetContent
            }
            val fileTask = when {
                useBigDataMode -> {
                    // In big data mode, use the provided task directly
                    task
                }

                tabs != null -> {
                    status = task.add("Processing ${fileSet.name}...<br/>")!!
                    socketManager.newTask(cancelable = false, root = false).apply {
                        tabs[fileSet.name] = placeholder
                    }
                }

                else -> {
                    val newSession = task.newSession()
                    status =
                        task.add("""Processing <a href="#${newSession.sessionId}" target="_blank" class="linked-task-link">${fileSet.name}</a>...<br/>""")!!
                    newSession.newTask()
                }
            }
            fileTask.header("Processing ${fileSet.name}")
            try {
                val toInput = { it: String -> listOf(fullContent, it) }
                when {
                    outputMode == CustomFileSetPatchAction.OutputMode.EDIT_FILES -> if (autoApply) {
                        handleAutoApplyMode(fileSet, userMessage, fileTask, session, toInput, socketManager)
                    } else {
                        handleInteractiveMode(fileSet, userMessage, fileTask, session, toInput, socketManager)
                    }

                    else -> {
                        handleGenerationMode(fileSet, userMessage, fileTask, session, singleOutputFile, toInput)
                    }
                }

                status?.set(status.toString().removeSuffix("<br/>") + "Completed processing ${fileSet.name}<br/>")
                task.update()
                fileTask.complete("Processed ${fileSet.name} successfully.")
            } catch (e: Exception) {
                fileTask.error(e)
            }
            task.update()
        } catch (e: Exception) {
            log.warn("Error processing ${fileSet.name}", e)
            task.error(e)
        }
    }

    private fun buildFileSetContent(fileSet: CustomFileSetPatchAction.FileSet): String {
        val contentBuilder = StringBuilder()

        fileSet.files.forEach { path ->
            try {
                val file = path.toFile()
                val fileSizeMB = file.length() / (1024 * 1024)
                if (fileSizeMB > MAX_FILE_SIZE_MB) {
                    log.warn("Skipping large file in file set: $path (${fileSizeMB}MB)")
                    return@forEach
                }

                val fileContent = readFileContent(file)
                contentBuilder.append(
                    """
                    # File: ${_root?.relativize(path) ?: path}
                    ```${path.toString().split('.').lastOrNull() ?: ""}
                    $fileContent
                    ```
                """.trimIndent()
                ).append("\n\n")
            } catch (e: IOException) {
                log.error("Error reading file: $path", e)
            }
        }
        return contentBuilder.toString()
    }

    private fun readFileContent(file: File): String {
        return try {
            require(file.exists()) { "File does not exist: ${file.absolutePath}" }
            require(file.isFile) { "Path is not a file: ${file.absolutePath}" }
            require(file.canRead()) { "File is not readable: ${file.absolutePath}" }

            // Check file size first
            val fileSizeMB = file.length() / (1024 * 1024)
            if (fileSizeMB > MAX_FILE_SIZE_MB) {
                return "File too large (${fileSizeMB}MB) - skipped"
            }
            // Check if file is binary
            if (isBinaryFile(file)) {
                return "Binary file - skipped"
            }

            when {
                file.name.endsWith(".pdf", ignoreCase = true) || file.name.endsWith(
                    "html",
                    ignoreCase = true
                ) || file.name.endsWith(".htm", ignoreCase = true) -> {
                    try {
                        file.getDocumentReader().use { reader ->
                            reader.getText()
                        }
                    } catch (e: Exception) {
                        log.warn("Failed to read document file ${file.name}, falling back to text", e)
                        file.readText(Charsets.UTF_8)
                    }
                }

                else -> {
                    try {
                        file.readText(Charsets.UTF_8)
                    } catch (e: Exception) {
                        log.warn("Failed to read as UTF-8, trying ISO-8859-1: ${file.name}", e)
                        file.readText(Charsets.ISO_8859_1)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Error reading file content: ${file.absolutePath}", e)
            "Error reading file: ${e.message}"
        }
    }

    private fun isBinaryFile(file: File): Boolean {
        return try {
            file.inputStream().use { stream ->
                val bytes = ByteArray(512)
                val bytesRead = stream.read(bytes)
                if (bytesRead <= 0) return false
                // Check for null bytes (common in binary files)
                for (i in 0 until bytesRead) {
                    if (bytes[i] == 0.toByte()) {
                        return true
                    }
                }
                // Check for high ratio of non-printable characters
                val nonPrintable = bytes.take(bytesRead).count { b ->
                    val c = b.toInt() and 0xFF
                    c < 32 && c != 9 && c != 10 && c != 13
                }
                nonPrintable.toDouble() / bytesRead > 0.3
            }
        } catch (e: Exception) {
            log.warn("Error checking if file is binary: ${file.absolutePath}", e)
            false
        }
    }

    private fun handleAutoApplyMode(
        fileSet: CustomFileSetPatchAction.FileSet,
        userMessage: String,
        task: SessionTask,
        session: Session,
        toInput: (String) -> List<String>,
        socketManager: SocketManager
    ) {
        val design = mainActor.answer(toInput(userMessage)).toContentList().firstOrNull()?.text ?: ""
        if (design.isNotBlank()) {
            task.add(
                AddApplyFileDiffLinks.instrumentFileDiffs(
                                    self = socketManager,
                                    root = _root ?: throw IllegalStateException("Root directory is not set"),
                                    response = design,
                                    handle = { newCodeMap ->
                                        newCodeMap.forEach { (path, _) ->
                                            task.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                        }
                                    },
                                    shouldAutoApply = { autoApply },
                                    model = AppSettingsState.instance.fastChatClient,
                                    defaultFile = fileSet.files.firstOrNull()?.let { (_root?.relativize(it) ?: it).toString() }
                                        ?: "",
                                    processor = processor).renderMarkdown(true))
        } else {
            task.complete("No changes suggested.")
        }
    }

    private fun handleGenerationMode(
        fileSet: CustomFileSetPatchAction.FileSet,
        userMessage: String,
        task: SessionTask,
        session: Session,
        singleOutputFile: Path?,
        toInput: (String) -> List<String>
    ) {
        val result = try {
            mainActor.answer(toInput(userMessage)).toContentList().firstOrNull()?.text ?: ""
        } catch (e: Exception) {
            log.error("Error generating content for ${fileSet.name}", e)
            task.error(e)
            return
        }

        if (singleOutputFile != null) {
            appendToSingleOutputFile(singleOutputFile, fileSet.name, result)
        } else {
            val outputDir = _selectedDirectory?.resolve(config.settings?.outputDirectory ?: "output") ?: File(
                config.settings?.outputDirectory ?: "output"
            ).toPath()
            Files.createDirectories(outputDir)
            val outputFile = generateOutputFilePath(outputDir, fileSet, config.settings?.outputFilename)
            Files.write(
                outputFile, result.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
            )
            task.complete(
                "<a href='fileIndex/$session/${_selectedDirectory?.relativize(outputFile) ?: outputFile}'>Generated: ${
                    _selectedDirectory?.relativize(outputFile) ?: outputFile
                }</a>"
            )
        }
    }

    private fun handleInteractiveMode(
        fileSet: CustomFileSetPatchAction.FileSet,
        userMessage: String,
        task: SessionTask,
        session: Session,
        toInput: (String) -> List<String>,
        socketManager: SocketManager
    ) {
        Discussable(
            task = task,
            userMessage = { userMessage },
            heading = renderMarkdown(userMessage),
            initialResponse = {
                mainActor.answer(toInput(it))
            },
            outputFn = { design: String ->
                formatOutput(design, session, fileSet, task, socketManager)
            },
            reviseResponse = { userMessages ->
                mainActor.respond(
                    messages = userMessages.map {
                        ModelSchema.ChatMessage(
                            it.second, it.first.toContentList()
                        )
                    }.toTypedArray(), input = toInput(userMessage)
                )
            },
            atomicRef = AtomicReference(),
            semaphore = Semaphore(0),
            blocking = false
        ).call()
    }

    private fun formatOutput(
        design: String,
        session: Session,
        fileSet: CustomFileSetPatchAction.FileSet,
        fileTask: SessionTask,
        socketManager: SocketManager
    ): String {
        return when (outputMode) {
            CustomFileSetPatchAction.OutputMode.EDIT_FILES -> {
                """<div>${
                    renderMarkdown(design) {
                        AddApplyFileDiffLinks.instrumentFileDiffs(
                            self = socketManager,
                            root = _root ?: throw IllegalStateException("Root directory is not set"),
                            response = design,
                            handle = { newCodeMap ->
                                newCodeMap.forEach { (path, _) ->
                                    fileTask.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                }
                            },
                            model = AppSettingsState.instance.fastChatClient,
                            defaultFile = fileSet.files.firstOrNull()
                                ?.let { (_root?.relativize(it) ?: it).toString() } ?: "",
                            processor = processor)
                    }
                }</div>"""
            }

            else -> """<div>${renderMarkdown(design)}</div>"""
        }
    }

    private fun generateOutputFilePath(
        outputDir: Path,
        fileSet: CustomFileSetPatchAction.FileSet,
        outputFilename: String?
    ): Path {

        // For single file filesets, generate output name based on the input file
        return if (fileSet.files.size == 1) {
            val inputFile = fileSet.files.first()
            val inputFileName = inputFile.fileName.toString()
            val lastDotIndex = inputFileName.lastIndexOf('.')
            val baseName = if (lastDotIndex > 0) {
                inputFileName.substring(0, lastDotIndex)
            } else {
                inputFileName
            }
            // Preserve directory structure if the fileset name contains path separators
            val fileSetPath = Path.of(fileSet.name)
            val parentDir = if (fileSetPath.parent != null) {
                outputDir.resolve(fileSetPath.parent)
            } else {
                outputDir
            }
            Files.createDirectories(parentDir)
            parentDir.resolve("${baseName}.${outputFilename}.${getFileExtension()}")
        } else {
            // For multi-file filesets, use the fileset name
            outputDir.resolve("${fileSet.name}.${getFileExtension()}")
        }
    }


    private fun getFileExtension(): String {
        return when (outputMode) {
            CustomFileSetPatchAction.OutputMode.GENERATE_DOCUMENTATION_SINGLE,
            CustomFileSetPatchAction.OutputMode.GENERATE_DOCUMENTATION_MULTI -> "md"

            CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_SINGLE,
            CustomFileSetPatchAction.OutputMode.AGGREGATED_DATA_EXTRACTION_MULTI -> "md"

            else -> "txt"
        }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/agent/DocumentedMassPatchAction.kt

```
package cognotik.actions.agent

import cognotik.actions.BaseAction
import cognotik.actions.generate.items
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMTextFile
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.util.getSelectedFiles
import com.simiacryptus.cognotik.util.getSelectedFolder
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import javax.swing.*

class DocumentedMassPatchAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    class SettingsUI {
        @Name("Documentation Files")
        val documentationFiles = CheckBoxList<Path>().apply {
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        }

        @Name("Code Files")
        val codeFiles = CheckBoxList<Path>().apply {
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        }

        @Name("AI Instruction")
        val transformationMessage = JBTextArea(4, 40)

        @Name("Auto Apply")
        val autoApply = JCheckBox("Auto Apply Changes")
    }

    class UserSettings(
        var transformationMessage: String = "Review and update code according to documentation standards",
        var documentationFiles: List<Path> = listOf(),
        var codeFilePaths: List<Path> = listOf(),
        var autoApply: Boolean = false,
    )

    class Settings(
        val settings: UserSettings? = null,
        val project: Project? = null,
    )

    override fun handle(e: AnActionEvent) {
        val project = e.project
        val config = getConfig(project, e)
        if (config == null) return

        val session = Session.newGlobalID()
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
        SessionProxyServer.chats[session] = DocumentedMassPatchServer(
            config = config,
            autoApply = config.settings?.autoApply ?: false,
            processor = AppSettingsState.instance.processor
        )
        ApplicationServer.appInfoMap[session] = AppInfoData(
            applicationName = "Documented Code Patch",
            inputCnt = 1,
            stickyInput = false,
            loadImages = false,
            showMenubar = false
        )

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

    private fun getConfig(project: Project?, e: AnActionEvent): Settings? {
        var root = e.getSelectedFolder()?.toNioPath()
        val allFiles: List<Path> = root?.let { Files.walk(it).toList() }
            ?: e.getSelectedFiles().map { it.toNioPath() }
        if (root == null) {
            root = e.project?.basePath?.let { File(it).toPath() }
        }
        val docFiles: Array<Path> = allFiles.filter { it.toString().endsWith(".md") }.toTypedArray()
        val sourceFiles: Array<Path> = allFiles.filter {
            isLLMTextFile(it.toFile()) && !it.toString().endsWith(".md")
        }.toTypedArray()

        val settingsUI = SettingsUI().apply {
            documentationFiles.setItems(docFiles.toMutableList()) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }
            codeFiles.setItems(sourceFiles.toMutableList()) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }

            docFiles.forEach { path ->
                documentationFiles.setItemSelected(path, true)
            }
            sourceFiles.forEach { path ->
                codeFiles.setItemSelected(path, true)
            }
            autoApply.isSelected = false
        }

        val dialog = ConfigDialog(project, settingsUI, "Documented Mass Patch")
        dialog.show()
        if (!dialog.isOK) return null

        return Settings(dialog.userSettings, project)
    }

    class ConfigDialog(project: Project?, private val settingsUI: SettingsUI, title: String) : DialogWrapper(project) {
        val userSettings = UserSettings()

        init {
            this.title = title
            settingsUI.transformationMessage.text = userSettings.transformationMessage
            settingsUI.autoApply.isSelected = userSettings.autoApply
            init()
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(BorderLayout()).apply {
                val mainPanel = JPanel(BorderLayout()).apply {
                    val docPanel = JPanel(BorderLayout()).apply {
                        add(JLabel("Documentation Files"), BorderLayout.NORTH)
                        add(JBScrollPane(settingsUI.documentationFiles), BorderLayout.CENTER)
                    }

                    val codePanel = JPanel(BorderLayout()).apply {
                        add(JLabel("Code Files"), BorderLayout.NORTH)
                        add(JBScrollPane(settingsUI.codeFiles), BorderLayout.CENTER)
                    }

                    val buttonPanel = JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.X_AXIS)
                        border = JBUI.Borders.empty(10)

                        add(Box.createHorizontalGlue())

                        val moveDownButton = JButton("↓").apply {
                            toolTipText = "Move selected documentation file to code files"
                            addActionListener {
                                moveSelectedItems(settingsUI.documentationFiles, settingsUI.codeFiles)
                            }
                        }
                        add(moveDownButton)

                        add(Box.createHorizontalStrut(10))

                        val moveUpButton = JButton("↑").apply {
                            toolTipText = "Move selected code file to documentation files"
                            addActionListener {
                                moveSelectedItems(settingsUI.codeFiles, settingsUI.documentationFiles)
                            }
                        }
                        add(moveUpButton)

                        add(Box.createHorizontalGlue())
                    }

                    val centerPanel = JPanel(GridBagLayout()).apply {
                        val c = GridBagConstraints()
                        c.gridx = 0
                        c.weightx = 1.0

                        c.gridy = 0
                        c.weighty = 1.0
                        c.fill = GridBagConstraints.BOTH
                        add(docPanel, c)

                        c.gridy = 1
                        c.weighty = 0.0
                        c.fill = GridBagConstraints.HORIZONTAL
                        add(buttonPanel, c)

                        c.gridy = 2
                        c.weighty = 1.0
                        c.fill = GridBagConstraints.BOTH
                        add(codePanel, c)
                    }

                    add(centerPanel, BorderLayout.CENTER)
                    preferredSize = Dimension(500, 600)
                }

                add(mainPanel, BorderLayout.CENTER)
                add(JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    add(JLabel("AI Instruction"))
                    add(settingsUI.transformationMessage)
                    add(Box.createVerticalStrut(10))
                    add(settingsUI.autoApply)
                }, BorderLayout.SOUTH)
            }
        }

        private fun moveSelectedItems(sourceList: CheckBoxList<Path>, targetList: CheckBoxList<Path>) {
            val selectedIndices = sourceList.selectedIndices
            if (selectedIndices.isEmpty()) return

            val selectedItems = selectedIndices.map { sourceList.items[it] }

            // Remove from source list
            val sourceItems = sourceList.items.toMutableList()
            selectedItems.forEach { sourceItems.remove(it) }

            // Add to target list
            val targetItems = targetList.items.toMutableList()
            targetItems.addAll(selectedItems)

            // Update both lists
            val root = userSettings.documentationFiles.firstOrNull()?.parent
                ?: userSettings.codeFilePaths.firstOrNull()?.parent
            sourceList.setItems(sourceItems) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }
            targetList.setItems(targetItems) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }

            // Select the moved items in target list
            val newIndices = selectedItems.mapNotNull { item ->
                val index = targetItems.indexOf(item)
                if (index >= 0) index else null
            }.toIntArray()
            if (newIndices.isNotEmpty()) {
                targetList.selectedIndices = newIndices
            }
        }

        override fun doOKAction() {
            super.doOKAction()
            userSettings.apply {
                transformationMessage = settingsUI.transformationMessage.text
                documentationFiles = settingsUI.documentationFiles.items
                    .filter { settingsUI.documentationFiles.isItemSelected(it) }
                codeFilePaths = settingsUI.codeFiles.items
                    .filter { settingsUI.codeFiles.isItemSelected(it) }
                autoApply = settingsUI.autoApply.isSelected
            }
        }
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        event.getSelectedFolder() ?: event.getSelectedFiles().let {
            when (it.size) {
                0 -> null
                1 -> null
                else -> it
            }
        } ?: return false
        return true
    }

}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/agent/DocumentedMassPatchServer.kt

```
package cognotik.actions.agent

import com.google.common.util.concurrent.Futures
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

class DocumentedMassPatchServer(
    val config: DocumentedMassPatchAction.Settings,
    val autoApply: Boolean,
    val processor: PatchProcessor
) : ApplicationServer(
    applicationName = "Documented Code Patch",
    path = "/patchChat",
    showMenubar = false,
) {
    private lateinit var _root: Path

    override val inputCnt = 0
    override val stickyInput = true

    private val mainActor: ChatAgent
        get() {
            return ChatAgent(
                prompt = """
         You are a helpful AI that helps people with coding.

         You will be reviewing code files based on documentation files and suggesting improvements.
         Please analyze both the documentation and code to ensure they are aligned and suggest improvements.

         Response should use one or more code patches in diff format within ```diff code blocks.
         Each diff should be preceded by a header that identifies the file being modified.
         The diff format should use + for line additions, - for line deletions.
         The diff should include 2 lines of context before and after every change.
         """.trimIndent(),
                model = AppSettingsState.instance.smartChatClient,
                temperature = AppSettingsState.instance.temperature,
            )
        }

    /**
     * Creates a new session for handling code review and patch generation
     * @param user The user initiating the session
     * @param session The session context
     * @return SocketManager for managing the session
     */

    override fun newSession(user: User, session: Session): SocketManager {
        val socketManager = super.newSession(user, session)
        _root = config.project?.basePath?.let { Path.of(it) } ?: Path.of(".")
        val task = socketManager.newTask(cancelable = false, root = true)
        val tabs = TabbedDisplay(task)
        val userMessage = config.settings?.transformationMessage ?: "Review and update code according to documentation"

        val docSummary = config.settings?.documentationFiles?.joinToString("\n\n") { path ->
            """
             # Documentation: $path
             ```md
             ${_root.resolve(path).toFile().readText(Charsets.UTF_8)}
             ```
             """.trimIndent()
        } ?: ""

        val status: StringBuilder = task.add("Starting...<br/>")!!
        val fixedConcurrencyProcessor = FixedConcurrencyProcessor(socketManager.pool, 4)
        val futures = config.settings?.codeFilePaths?.map { path: Path ->
            fixedConcurrencyProcessor.submit {
                try {
                    synchronized(status) { status.append("Processing ${path}...<br/>") }
                    task.update()
                    val codeSummary = """
                             $docSummary

                             # Code: $path
                             ```${path.toString().split('.').lastOrNull()}
                             ${_root.resolve(path).toFile().readText(Charsets.UTF_8)}
                             ```
                         """.trimIndent()

                    val fileTask = socketManager.newTask(cancelable = false, root = false).apply {
                        tabs[path.toString()] = placeholder
                    }

                    val toInput = { it: String -> listOf(codeSummary, it) }
                    if (autoApply) {
                        val design =
                            mainActor.answer(toInput(userMessage)).toContentList().firstOrNull()?.text ?: ""
                        if (design.isNotBlank()) {
                            fileTask.add(
                                AddApplyFileDiffLinks.instrumentFileDiffs(
                                                                    self = socketManager,
                                                                    root = _root,
                                                                    response = design,
                                                                    handle = { newCodeMap ->
                                                                        newCodeMap.forEach { (path, newCode) ->
                                                                            fileTask.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                                                        }
                                                                    },
                                                                    shouldAutoApply = { autoApply },
                                                                    model = AppSettingsState.instance.fastChatClient,
                                                                    defaultFile = path.toString(),
                                                                    processor = processor
                                                                ).renderMarkdown(true)
                            )
                        } else {
                            fileTask.complete("No changes suggested.")
                        }
                    } else {
                        Discussable(
                            task = fileTask,
                            userMessage = { userMessage },
                            heading = renderMarkdown(userMessage),
                            initialResponse = {
                                mainActor.answer(toInput(it))
                            },
                            outputFn = { design: String ->
                                """<div>${
                                    renderMarkdown(design) {
                                        AddApplyFileDiffLinks.instrumentFileDiffs(
                                            self = socketManager,
                                            root = _root,
                                            response = design,
                                            handle = { newCodeMap ->
                                                newCodeMap.forEach { (path, newCode) ->
                                                    fileTask.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                                }
                                            },
                                            shouldAutoApply = { autoApply },
                                            model = AppSettingsState.instance.fastChatClient,
                                            defaultFile = path.toString(),
                                            processor = processor
                                        )
                                    }
                                }</div>"""
                            },
                            reviseResponse = { userMessages ->
                                mainActor.respond(
                                    messages = userMessages.map {
                                        ModelSchema.ChatMessage(
                                            it.second,
                                            it.first.toContentList()
                                        )
                                    }.toTypedArray(),
                                    input = toInput(userMessage),
                                )
                            },
                            atomicRef = AtomicReference(),
                            semaphore = Semaphore(0),
                        ).call()
                    }
                    synchronized(status) { status.append("Completed processing ${path}<br/>") }
                    task.update()
                } catch (e: Exception) {
                    log.warn("Error processing $path", e)
                    task.error(e)
                }
            }
        }
        fixedConcurrencyProcessor.submit {
            futures?.forEach {
                Futures.getUnchecked(it)
            }
            synchronized(status) { status.append("All files processed successfully.<br/>") }
            task.update()
        }
        return socketManager
    }

    companion object {
        private val log = LoggerFactory.getLogger(DocumentedMassPatchServer::class.java)
    }
}


```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/agent/MultiStepPatchAction.kt

```
package cognotik.actions.agent

import ai.grazie.utils.mpp.UUID
import cognotik.actions.BaseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.JsonUtil.toJson
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

class MultiStepPatchAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    val path = "/autodev"
    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        event.getSelectedFile() ?: return false
        return true
    }

    override fun handle(e: AnActionEvent) {
        val project = e.project ?: return
        UITools.runAsync(project, "Initializing Auto Dev Assistant", true) { progress ->
            progress.isIndeterminate = true
            try {
                val session = Session.newGlobalID()
                val selectedFile = e.getSelectedFolder()
                if (null != selectedFile) {
                    DataStorage.sessionPaths[session] = selectedFile.toFile
                }
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )
                SessionProxyServer.chats[session] = AutoDevApp(event = e)
                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Code Chat",
                    inputCnt = 1,
                    stickyInput = false,
                    loadImages = false,
                    showMenubar = false
                )

                ApplicationManager.getApplication().invokeLater {
                    progress.text = "Opening browser..."
                    val uri = com.simiacryptus.cognotik.webui.application.CognotikAppServer.getServer(
                        AppSettingsState.instance.listeningEndpoint,
                        AppSettingsState.instance.listeningPort
                    ).server.uri.resolve("/#$session")
                    BaseAction.log.info("Opening browser to $uri")
                    browse(uri)
                }
            } catch (e: Throwable) {
                UITools.error(log, "Failed to initialize Auto Dev Assistant", e)
            }
        }
    }

    open class AutoDevApp(
        applicationName: String = "Auto Dev Assistant v1.2",
        val temperature: Double = 0.1,
        val event: AnActionEvent,
    ) : ApplicationServer(
        applicationName = applicationName,
        path = "/autodev",
        showMenubar = false,
    ) {
        companion object {
            private const val DEFAULT_BUDGET = 2.00
        }

        override fun userMessage(
            session: Session,
            user: User,
            userMessage: String,
            ui: SocketManager
        ) {
            val settings = getSettings(session, user) ?: Settings(
                budget = DEFAULT_BUDGET,
                model = AppSettingsState.instance.smartChatClient
            )
            AutoDevAgent(
                session = session,
                user = user,
                ui = ui,
                model = settings.model!!,
                parsingModel = AppSettingsState.instance.fastChatClient,
                event = event,
                processor = AppSettingsState.instance.processor,
            ).start(
                userMessage = userMessage,
            )
        }

        data class Settings(
            val budget: Double? = 2.00,
            val tools: List<String> = emptyList(),
            val model: ChatInterface? = null,
        )

        override val settingsClass: Class<*> get() = Settings::class.java

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> initSettings(session: Session): T? = Settings() as T
    }

    class AutoDevAgent(
        val session: Session,
        val user: User = defaultUser,
        val ui: SocketManager,
        val model: ChatInterface,
        val parsingModel: ChatInterface,
        val event: AnActionEvent,
        val processor: PatchProcessor,
    ) {
        val actors = mapOf(
            ActorTypes.DesignActor to ParsedAgent(
                resultClass = TaskList::class.java,
                prompt = """
          Translate the user directive into an action plan for the project.
          Break the user's request into a list of simple tasks to be performed.
          For each task, provide a list of files to be modified and a description of the changes to be made.
        """.trimIndent(),
                model = model,
                parsingChatter = parsingModel,
            ),
            ActorTypes.TaskCodingActor to ChatAgent(
                prompt = "Implement the changes to the codebase as described in the task list.\n\n" + processor.patchFormatPrompt,
                model = model
            ),
        ).map { it.key.name to it.value }.toMap()

        enum class ActorTypes {
            DesignActor,
            TaskCodingActor,
        }

        private val designActor by lazy { actors.get(ActorTypes.DesignActor.name)!! as ParsedAgent<TaskList> }
        private val taskActor by lazy { actors.get(ActorTypes.TaskCodingActor.name)!! as ChatAgent }

        fun start(
            userMessage: String,
        ) {
            val codeFiles = mutableSetOf<Path>()
            val root = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)
                ?.map { it.toFile.toPath() }?.toTypedArray()?.commonRoot()!!
            PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext)?.forEach { file ->

                codeFiles.add(root.relativize(file.toNioPath()))
            }
            require(codeFiles.isNotEmpty()) { "No files selected" }
            fun codeSummary() = codeFiles.joinToString("\n\n") { path ->
                "# $path\n```${
                    path.toString().split('.').last()
                }\n${root.resolve(path).toFile().readText()}\n```"
            }

            val task = ui.newTask()

            val toInput = { it: String -> listOf(codeSummary(), it) }
            val architectureResponse = Discussable(
                task = task,
                userMessage = { userMessage },
                heading = renderMarkdown(userMessage),
                initialResponse = { it: String -> designActor.answer(toInput(it)) },
                outputFn = { design: ParsedResponse<TaskList> ->

                    AgentPatterns.displayMapInTabs(
                        mapOf(
                          "Text" to design.text.renderMarkdown(true),
                          "JSON" to "```json\n${toJson(design.obj)}\n```".renderMarkdown(true),
                        )
                    )
                },
                reviseResponse = { userMessages: List<Pair<String, Role>> ->
                    designActor.respond(
                        messages = (userMessages.map { ModelSchema.ChatMessage(it.second, it.first.toContentList()) }
                            .toTypedArray<ModelSchema.ChatMessage>()),
                        input = toInput(userMessage),
                    )
                },
                atomicRef = AtomicReference(),
                semaphore = Semaphore(0),
            ).call()

            try {
                val taskTabs = TabbedDisplay(task)
                architectureResponse?.obj?.tasks?.map { (paths, description) ->
                    var description = (description ?: UUID.random().toString()).trim()

                    while (description.startsWith("#")) {
                        description = description.substring(1)
                    }
                    description = renderMarkdown(description, ui = task.ui, tabs = false)
                    val task = ui.newTask(false).apply { taskTabs[description] = placeholder }
                    ApplicationServices.threadPoolManager.getPool(session, user).submit {
                        task.header("Task: $description", 2)
                        Retryable(task) {
                            try {
                                val filter = codeFiles.filter { path ->
                                    paths?.find { path.toString().contains(it) }?.isNotEmpty() == true
                                }
                                require(filter.isNotEmpty()) {
                                    """
                  No files found for """.trimIndent() + paths + """

                  Root:
                  """.trimIndent() + root + """

                  Files:
                  """.trimIndent() + codeFiles.joinToString("\n") + """

                  Paths:
                  """.trimIndent() + (paths?.joinToString("\n") ?: "")
                                }
                                renderMarkdown(
                                    AddApplyFileDiffLinks.instrumentFileDiffs(
                                        ui,
                                        root = root,
                                        response = taskActor.answer(
                                            listOf(
                                                codeSummary(),
                                                userMessage,
                                                filter.joinToString("\n\n") {
                                                    "# ${it}\n```${
                                                        it.toString().split('.').last()
                                                            .let { it }
                                                    }\n${root.resolve(it).toFile().readText()}\n```"
                                                },
                                                architectureResponse.text,
                                                "Provide a change for ${paths?.joinToString(",") { it } ?: ""} ($description)"
                                            )),
                                        handle = { newCodeMap ->
                                            newCodeMap.forEach { (path, newCode) ->
                                                task.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                            }
                                        },
                                        processor = processor,
                                    )
                                )
                            } catch (e: Exception) {
                                task.error(e)
                                ""
                            }
                        }
                    }
                }?.toTypedArray()?.forEach { it.get() }
            } catch (e: Exception) {
                log.warn("Error", e)
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MultiStepPatchAction::class.java)
        val root: File get() = File(AppSettingsState.Companion.pluginHome, "code_chat")

        data class TaskList(
            @Description("List of tasks to be performed in this project")
            val tasks: List<Task> = emptyList()
        ) : ValidatedObject {
            override fun validate(): String? = when {
                tasks.isEmpty() -> "Resources are required"
                tasks.any { it.validate() != null } -> "Invalid resource"
                else -> null
            }
        }

        data class Task(
            @Description("List of paths involved in the task. This should include all files to be modified, and can include other files whose content will be informative in writing the changes.")
            val paths: List<String>? = null,
            @Description("Detailed description of the changes to be made. Markdown format is supported.")
            val description: String? = null
        ) : ValidatedObject {
            override fun validate(): String? = when {
                paths.isNullOrEmpty() -> "Paths are required"
                paths.any { it.isBlank() } -> "Invalid path"
                else -> null
            }
        }

    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/agent/WebDevelopmentAssistantAction.kt

```
﻿package cognotik.actions.agent

import com.intellij.openapi.vfs.VirtualFile
import java.io.File

val VirtualFile.toFile: File get() = File(this.path)


```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated documentation for the agent actions package.

### intellij/src/main/kotlin/cognotik/actions/agent/README.md
```markdown
# Agent Actions

This package contains advanced AI-driven actions that function as autonomous or semi-autonomous agents. These tools are designed to handle complex, multi-step tasks such as automated debugging, large-scale refactoring, and documentation-driven code generation.

## Core Actions

### [Command Autofix](CommandAutofixAction.kt)
Provides automated fixing of command execution issues through AI assistance.
- **Functionality**: Executes shell commands or scripts and monitors their output. If a command fails (or based on exit code configuration), the AI analyzes the error and suggests/applies fixes.
- **Configuration**: Supports multiple commands, custom working directories, argument history, and persistent configurations.

### [Custom File Set Patch](CustomFileSetPatchAction.kt)
A versatile tool for applying AI transformations to specific sets of files defined by patterns.
- **Selection**: Uses glob or regex patterns to include/exclude files. Supports designating files as "Context" (read-only for the AI) or "Target" (to be modified).
- **Output Modes**:
    - **Edit Files**: Direct code modification with optional auto-apply.
    - **Documentation**: Generates single or multi-file documentation based on code analysis.
    - **Data Extraction**: Aggregates structured data from the codebase.
- **Scalability**: Includes a "Big Data Mode" for processing large volumes of files with batching and concurrency controls.

### [Documented Mass Patch](DocumentedMassPatchAction.kt)
Synchronizes codebases with external documentation or standards.
- **Workflow**: Users select documentation files (e.g., Markdown specifications) and target source files. The AI ensures the code adheres to the requirements described in the documentation.
- **Interface**: Supports an interactive discussion mode to refine changes before application.

### [Multi-Step Patch (Auto Dev Assistant)](MultiStepPatchAction.kt)
An advanced agent that decomposes high-level user directives into executable action plans.
- **Design Phase**: Uses a specialized agent to translate a request into a `TaskList` of discrete sub-tasks.
- **Execution Phase**: Iterates through the task list, identifying relevant files for each step and applying patches sequentially.

## Architecture

The actions in this package typically utilize a decoupled architecture to handle long-running AI tasks:

1.  **Action UI**: IntelliJ-native dialogs for initial configuration and file selection.
2.  **Application Server**: Backend logic (e.g., `CustomFileSetPatchServer`) that manages the AI session, state, and concurrency.
3.  **Web UI**: Interactive interfaces served via the internal Cognotik server, allowing for rich markdown rendering, diff previews, and real-time progress tracking.

## Supporting Files
- **[WebDevelopmentAssistantAction.kt](WebDevelopmentAssistantAction.kt)**: Contains internal utility extensions for file system interoperability.
```

### Summary of Changes
- Created a comprehensive `README.md` for the `cognotik.actions.agent` package.
- Documented the primary AI agent actions: `CommandAutofixAction`, `CustomFileSetPatchAction`, `DocumentedMassPatchAction`, and `MultiStepPatchAction`.
- Explained the operational modes (Edit, Documentation, Extraction) and the architectural pattern (Action UI + Server + Web UI) used by these agents.
- Included references to supporting server implementations and utility files.
- Organized the content to follow the project's documentation standards for action packages.
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/agent/README.md'>intellij/src/main/kotlin/cognotik/actions/agent/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/agent/README.md'>intellij/src/main/kotlin/cognotik/actions/agent/README.md</a> Updated
