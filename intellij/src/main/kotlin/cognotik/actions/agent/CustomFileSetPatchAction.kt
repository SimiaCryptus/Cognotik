package cognotik.actions.agent

import cognotik.actions.BaseAction
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMTextFile
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CustomFileSetPatchAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    enum class OutputMode {
        EDIT_FILES,
        GENERATE_DOCUMENTATION
    }


    data class FilePattern(
        val pattern: String,
        val isContext: Boolean = false,
        val isRegex: Boolean = false
    ) {
        override fun toString(): String = "${if (isContext) "[Context] " else ""}${if (isRegex) "[Regex] " else ""}$pattern"
    }

    data class FileSet(
        val name: String,
        val files: List<Path>
    )

    class SettingsUI(private val project: Project?, private val selectedDirectory: Path?) {
        companion object {
            private const val DEFAULT_PATTERN_WIDTH = 30
            private const val DEFAULT_TEXTAREA_ROWS = 4
            private const val DEFAULT_TEXTAREA_COLS = 40
            private const val PREVIEW_ROWS = 15
            private const val PREVIEW_COLS = 50
            private const val MAX_PREVIEW_FILES = 100
        }

        private val patternCache = ConcurrentHashMap<String, PathMatcher>()


        @Name("File Patterns")
        val patternListModel = DefaultListModel<FilePattern>()
        val patternList = JList(patternListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int,
                    isSelected: Boolean, cellHasFocus: Boolean
                ): java.awt.Component {
                    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    if (value is FilePattern) {
                        text = value.toString()
                    }
                    return component
                }
            }
        }

        @Name("Pattern Input")
        val patternInput = JBTextField(DEFAULT_PATTERN_WIDTH)

        @Name("Is Context")
        val isContextCheckbox = JCheckBox("Include as context in all calls")
        @Name("Use Regex")
        val useRegexCheckbox = JCheckBox("Use regex pattern instead of glob")


        @Name("AI Instruction")
        val transformationMessage = JBTextArea(DEFAULT_TEXTAREA_ROWS, DEFAULT_TEXTAREA_COLS)

        @Name("Auto Apply")
        val autoApply = JCheckBox("Auto Apply Changes")

        @Name("Treat Documents as Text")
        val treatDocumentsAsText = JCheckBox("Include PDF/HTML files as text", false)

        @Name("Output Mode")
        val outputModeGroup = ButtonGroup()

        val editFilesRadio = JRadioButton("Edit Files", true)

        val generateDocsRadio = JRadioButton("Generate Documentation")

        @Name("Single Output File")
        val singleOutputFile = JCheckBox("Produce a single output file", true)

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

        init {
            // Setup radio button group
            outputModeGroup.add(editFilesRadio)
            outputModeGroup.add(generateDocsRadio)

            // Add document listener to update preview when patterns change

            patternInput.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updatePreview()
                override fun removeUpdate(e: DocumentEvent?) = updatePreview()
                override fun changedUpdate(e: DocumentEvent?) = updatePreview()
            })

            patternList.addListSelectionListener { updatePreview() }

            // Add listeners for output mode changes
            editFilesRadio.addActionListener { updateOutputOptionsVisibility() }
            generateDocsRadio.addActionListener { updateOutputOptionsVisibility() }

            updateOutputOptionsVisibility()
        }

        private fun updateOutputOptionsVisibility() {
            val isGenerateMode = generateDocsRadio.isSelected
            singleOutputFile.isVisible = isGenerateMode
            outputFilename.isVisible = isGenerateMode
            outputDirectory.isVisible = isGenerateMode
            autoApply.isVisible = editFilesRadio.isSelected
        }

        fun getOutputMode(): OutputMode {
            return when {
                editFilesRadio.isSelected -> OutputMode.EDIT_FILES
                generateDocsRadio.isSelected -> OutputMode.GENERATE_DOCUMENTATION
                else -> OutputMode.EDIT_FILES
            }
        }


        fun addPattern() {
            val pattern = patternInput.text.trim()
            if (pattern.isNotEmpty()) {
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
                patternListModel.addElement(FilePattern(pattern, isContextCheckbox.isSelected, isRegex))
                patternInput.text = ""
                isContextCheckbox.isSelected = false
                useRegexCheckbox.isSelected = false
                updatePreview()
            }
        }

        fun removeSelectedPattern() {
            val selectedIndex = patternList.selectedIndex
            if (selectedIndex >= 0) {
                patternListModel.removeElementAt(selectedIndex)
                updatePreview()
            }
        }

        private fun updatePreview() {
            val root = getRoot()
            val fileSets = resolveFileSets(root)
            val contextFiles = resolveContextFiles(root)

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

            previewArea.text = preview.toString()
        }

        private fun getRoot(): Path =
            selectedDirectory ?: project?.basePath?.let { Path.of(it) } ?: Path.of(".")

        fun resolveFileSets(root: Path): List<FileSet> {
            val patterns = (0 until patternListModel.size)
                .map { patternListModel.getElementAt(it) }
                .filter { !it.isContext }

            return patterns.flatMap { pattern ->
                resolvePattern(root, pattern)
            }
        }

        fun resolveContextFiles(root: Path): List<Path> {
            val contextPatterns = (0 until patternListModel.size)
                .map { patternListModel.getElementAt(it) }
                .filter { it.isContext }

            return contextPatterns.flatMap { pattern ->
                try {
                    val matcher = getOrCreateMatcher(pattern.pattern)
                    Files.walk(root).use { stream ->
                        stream
                            .filter { Files.isRegularFile(it) && isLLMTextFile(it.toFile(), treatDocumentsAsText.isSelected) }
                            .filter { path ->
                                val relativePath = root.relativize(path)
                                // Skip current directory references
                                if (relativePath.toString().isEmpty() || relativePath.toString() == ".") {
                                    false
                                } else {
                                    matcher.matches(relativePath)
                                }
                            }
                            .toList()
                    }
                } catch (e: Exception) {
                    log.warn("Error resolving context pattern: ${pattern.pattern}", e)
                    emptyList()
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
                val matchedPaths = if (pattern.isRegex) {
                   val regex = pattern.pattern.toRegex()
                   Files.walk(root).use { stream ->
                       stream
                           .filter { path ->
                               val relativePath = root.relativize(path)
                               // Skip current directory references
                               if (relativePath.toString().isEmpty() || relativePath.toString() == ".") {
                                   false
                               } else {
                                   regex.matches(relativePath.toString())
                               }
                           }
                           .filter { Files.isRegularFile(it) || Files.isDirectory(it) }
                           .toList()
                   }
                } else {
                    val matcher = getOrCreateMatcher(pattern.pattern)
                    Files.walk(root).use { stream ->
                        stream.filter { path -> matcher.matches(root.relativize(path)) }
                            .filter { Files.isRegularFile(it) || Files.isDirectory(it) }
                            .toList()
                    }
                }
                matchedPaths.mapNotNull { path ->
                    when {
                        Files.isDirectory(path) -> processDirectory(root, path)
                        Files.isRegularFile(path) && isLLMTextFile(path.toFile(), treatDocumentsAsText.isSelected) -> {
                            FileSet(root.relativize(path).toString(), listOf(path))
                        }

                        else -> null
                    }
                }
            } catch (e: Exception) {
                log.warn("Error resolving pattern: ${pattern.pattern}", e)
                emptyList()
            }
        }

        private fun processDirectory(root: Path, directory: Path): FileSet? {
            val dirFiles = Files.walk(directory).use { stream ->
                stream
                    .filter { Files.isRegularFile(it) && isLLMTextFile(it.toFile(), treatDocumentsAsText.isSelected) }
                    .toList()
            }
            return if (dirFiles.isNotEmpty()) {
                FileSet(root.relativize(directory).toString(), dirFiles)
            } else null
        }
    }

class UserSettings(
        var transformationMessage: String = "Review and improve the code according to best practices",
        var patterns: List<FilePattern> = listOf(),
        var autoApply: Boolean = false,
        var treatDocumentsAsText: Boolean = false,
        var outputMode: OutputMode = OutputMode.EDIT_FILES,
        var singleOutputFile: Boolean = true,
        var outputFilename: String = "output.md",
        var outputDirectory: String = "output/",
        var concurrency: Int = 4
    )

    class Settings(
        val settings: UserSettings? = null,
        val project: Project? = null,
        val selectedDirectory: Path? = null,
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
        val dialog = object: DialogWrapper(project, false) {
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
                                add(settingsUI.patternInput, BorderLayout.CENTER)
                               
                               val checkboxPanel = JPanel().apply {
                                   layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                   add(settingsUI.isContextCheckbox)
                                   add(settingsUI.useRegexCheckbox)
                               }
                               add(checkboxPanel, BorderLayout.SOUTH)
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
                                    add(settingsUI.editFilesRadio)
                                    add(settingsUI.generateDocsRadio)
                                }
                                add(outputModePanel)

                                val outputOptionsPanel = JPanel().apply {
                                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                    add(settingsUI.autoApply)
                                    add(settingsUI.singleOutputFile)
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

                        add(patternPanel, BorderLayout.NORTH)
                        add(listPanel, BorderLayout.CENTER)
                        add(instructionPanel, BorderLayout.SOUTH)
                    }

                    val rightPanel = JPanel(BorderLayout()).apply {
                        preferredSize = Dimension(500, 600)
                        border = JBUI.Borders.empty(10)
                        add(JLabel("Preview:"), BorderLayout.NORTH)
                        add(JBScrollPane(settingsUI.previewArea), BorderLayout.CENTER)
                    }

                    add(leftPanel, BorderLayout.WEST)
                    add(rightPanel, BorderLayout.CENTER)

                    preferredSize = Dimension(900, 600)
                }
            }

            override fun doOKAction() {
                super.doOKAction()
                userSettings.apply {
                    transformationMessage = settingsUI.transformationMessage.text
                    patterns = (0 until settingsUI.patternListModel.size)
                        .map { settingsUI.patternListModel.getElementAt(it) }
                    autoApply = settingsUI.autoApply.isSelected
                    treatDocumentsAsText = settingsUI.treatDocumentsAsText.isSelected
                    outputMode = settingsUI.getOutputMode()
                    singleOutputFile = settingsUI.singleOutputFile.isSelected
                    outputFilename = settingsUI.outputFilename.text
                    outputDirectory = settingsUI.outputDirectory.text
                    concurrency = settingsUI.concurrencySpinner.value as Int
                }
                // Handle the actual action execution here since dialog is non-modal
                executeAction()
            }
            private fun executeAction() {
                val session = Session.newGlobalID()
                SessionProxyServer.metadataStorage.setSessionName(
                    null,
                    session,
                    "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
                )

                SessionProxyServer.chats[session] = CustomFileSetPatchServer(
                    config = Settings(userSettings, project, selectedDirectory),
                    api = api,
                    autoApply = userSettings.autoApply,
                    outputMode = userSettings.outputMode
                )

                ApplicationServer.appInfoMap[session] = AppInfoData(
                    applicationName = "Custom File Set Patch",
                    inputCnt = 1,
                    stickyInput = false,
                    loadImages = false,
                    showMenubar = false
                )

                val server = CognotikAppServer.getServer(e.project)
                Thread {
                    Thread.sleep(500)
                    try {
                        val uri = server.server.uri.resolve("/#$session")
                        log.info("Opening browser to $uri")
                        browse(uri)
                    } catch (e: Throwable) {
                        log.warn("Error opening browser", e)
                    }
                }.start()
            }
        }
        dialog.show() // BUG: As a non-modal dialog, this does not block further execution
        if (dialog.isOK) {
            Settings(dialog.userSettings, project, selectedDirectory)
        } else null
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