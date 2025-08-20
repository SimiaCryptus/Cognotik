package cognotik.actions.agent

import cognotik.actions.BaseAction
import cognotik.actions.SessionProxyServer
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.CognotikAppServer
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.BrowseUtil.browse
import com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMTextFile
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import java.awt.FlowLayout
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import kotlin.streams.toList

class CustomFileSetPatchAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    enum class OutputMode {
        EDIT_FILES,
        GENERATE_DOCUMENTATION,
        GENERATE_EXTRACTS
    }


    data class FilePattern(
        val pattern: String,
        val isContext: Boolean = false
    )

    data class FileSet(
        val name: String,
        val files: List<Path>
    )

   class SettingsUI(private val project: Project?, private val selectedDirectory: Path?) {
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
                        text = "${if (value.isContext) "[Context] " else ""}${value.pattern}"
                    }
                    return component
                }
            }
        }

        @Name("Pattern Input")
        val patternInput = JBTextField(30)

        @Name("Is Context")
        val isContextCheckbox = JCheckBox("Include as context in all calls")

        @Name("AI Instruction")
        val transformationMessage = JBTextArea(4, 40)

        @Name("Auto Apply")
        val autoApply = JCheckBox("Auto Apply Changes")
        @Name("Output Mode")
        val outputModeGroup = ButtonGroup()
        val editFilesRadio = JRadioButton("Edit Files", true)
        val generateDocsRadio = JRadioButton("Generate Documentation")
        val generateExtractsRadio = JRadioButton("Generate Extracts/Transforms")
        @Name("Single Output File")
        val singleOutputFile = JCheckBox("Produce a single output file", true)
        @Name("Output File")
        val outputFilename = JBTextField("output.md")
        @Name("Output Directory")
        val outputDirectory = JBTextField("output/")


        @Name("Preview")
        val previewArea = JBTextArea(15, 50).apply {
            isEditable = false
            font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
        }

        init {
            // Setup radio button group
            outputModeGroup.add(editFilesRadio)
            outputModeGroup.add(generateDocsRadio)
            outputModeGroup.add(generateExtractsRadio)

            // Add document listener to update preview when patterns change
            val updatePreview = {
                updatePreview()
            }
            
            patternInput.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = updatePreview()
                override fun removeUpdate(e: DocumentEvent?) = updatePreview()
                override fun changedUpdate(e: DocumentEvent?) = updatePreview()
            })
            
            patternList.addListSelectionListener { updatePreview() }
            // Add listeners for output mode changes
            editFilesRadio.addActionListener { updateOutputOptionsVisibility() }
            generateDocsRadio.addActionListener { updateOutputOptionsVisibility() }
            generateExtractsRadio.addActionListener { updateOutputOptionsVisibility() }
            updateOutputOptionsVisibility()
        }
        private fun updateOutputOptionsVisibility() {
            val isGenerateMode = generateDocsRadio.isSelected || generateExtractsRadio.isSelected
            singleOutputFile.isVisible = isGenerateMode
            outputFilename.isVisible = isGenerateMode
            outputDirectory.isVisible = isGenerateMode
            autoApply.isVisible = editFilesRadio.isSelected
        }
        fun getOutputMode(): OutputMode {
            return when {
                editFilesRadio.isSelected -> OutputMode.EDIT_FILES
                generateDocsRadio.isSelected -> OutputMode.GENERATE_DOCUMENTATION
                generateExtractsRadio.isSelected -> OutputMode.GENERATE_EXTRACTS
                else -> OutputMode.EDIT_FILES
            }
        }


        fun addPattern() {
            val pattern = patternInput.text.trim()
            if (pattern.isNotEmpty()) {
                patternListModel.addElement(FilePattern(pattern, isContextCheckbox.isSelected))
                patternInput.text = ""
                isContextCheckbox.isSelected = false
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
           val root = selectedDirectory ?: project?.basePath?.let { Path.of(it) } ?: Path.of(".")
            val fileSets = resolveFileSets(root)
            val contextFiles = resolveContextFiles(root)
            
            val preview = StringBuilder()
            
            if (contextFiles.isNotEmpty()) {
                preview.append("Context Files (included in all calls):\n")
                contextFiles.forEach { file ->
                    preview.append("  - ${root.relativize(file)}\n")
                }
                preview.append("\n")
            }
            
            preview.append("File Sets to Process:\n")
            fileSets.forEach { fileSet ->
                preview.append("${fileSet.name}:\n")
                fileSet.files.forEach { file ->
                    preview.append("  - ${root.relativize(file)}\n")
                }
                preview.append("\n")
            }
            
            if (fileSets.isEmpty() && contextFiles.isEmpty()) {
                preview.append("No files match the current patterns.")
            }
            
            previewArea.text = preview.toString()
        }

        fun resolveFileSets(root: Path): List<FileSet> {
            val patterns = (0 until patternListModel.size)
                .map { patternListModel.getElementAt(it) }
                .filter { !it.isContext }
            
            return patterns.mapNotNull { pattern ->
                try {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:${pattern.pattern}")
                    val matchedPaths = Files.walk(root)
                        .filter { matcher.matches(root.relativize(it)) }
                        .toList()
                    
                    val fileSets = mutableListOf<FileSet>()
                    
                    matchedPaths.forEach { path ->
                        when {
                            Files.isDirectory(path) -> {
                                val dirFiles = Files.walk(path)
                                    .filter { Files.isRegularFile(it) && isLLMTextFile(it.toFile()) }
                                    .toList()
                                if (dirFiles.isNotEmpty()) {
                                    fileSets.add(FileSet(root.relativize(path).toString(), dirFiles))
                                }
                            }
                            Files.isRegularFile(path) && isLLMTextFile(path.toFile()) -> {
                                fileSets.add(FileSet(root.relativize(path).toString(), listOf(path)))
                            }
                        }
                    }
                    
                    fileSets
                } catch (e: Exception) {
                    null
                }
            }.flatten()
        }

        fun resolveContextFiles(root: Path): List<Path> {
            val contextPatterns = (0 until patternListModel.size)
                .map { patternListModel.getElementAt(it) }
                .filter { it.isContext }
            
            return contextPatterns.flatMap { pattern ->
                try {
                    val matcher = FileSystems.getDefault().getPathMatcher("glob:${pattern.pattern}")
                    Files.walk(root)
                        .filter { Files.isRegularFile(it) && isLLMTextFile(it.toFile()) }
                        .filter { matcher.matches(root.relativize(it)) }
                        .toList()
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }

    class UserSettings(
        var transformationMessage: String = "Review and improve the code according to best practices",
        var patterns: List<FilePattern> = listOf(),
        var autoApply: Boolean = false,
        var outputMode: OutputMode = OutputMode.EDIT_FILES,
        var singleOutputFile: Boolean = true,
        var outputFilename: String = "output.md",
        var outputDirectory: String = "output/"
    )

    class Settings(
        val settings: UserSettings? = null,
        val project: Project? = null,
       val selectedDirectory: Path? = null,
    )

    override fun handle(e: AnActionEvent) {
        val project = e.project
       val selectedDirectory = getSelectedDirectory(e)
       val config = getConfig(project, e, selectedDirectory)
        if (config == null) return

        val session = Session.newGlobalID()
        SessionProxyServer.metadataStorage.setSessionName(
            null,
            session,
            "${javaClass.simpleName} @ ${SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis())}"
        )
        SessionProxyServer.chats[session] = CustomFileSetPatchServer(
            config = config,
            api = api,
            autoApply = config.settings?.autoApply ?: false,
            outputMode = config.settings?.outputMode ?: OutputMode.EDIT_FILES
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

   private fun getSelectedDirectory(e: AnActionEvent): Path? {
       val virtualFiles = e.getData(com.intellij.openapi.actionSystem.CommonDataKeys.VIRTUAL_FILE_ARRAY)
       val selectedFile = virtualFiles?.firstOrNull()
       return when {
           selectedFile?.isDirectory == true -> selectedFile.toNioPath()
           selectedFile != null -> selectedFile.parent?.toNioPath()
           else -> e.project?.basePath?.let { Path.of(it) }
       }
   }

   private fun getConfig(project: Project?, e: AnActionEvent, selectedDirectory: Path?): Settings? {
       val settingsUI = SettingsUI(project, selectedDirectory).apply {
            transformationMessage.text = "Review and improve the code according to best practices"
            autoApply.isSelected = false
            outputFilename.text = "output.md"
            outputDirectory.text = "output/"
        }

        val dialog = ConfigDialog(project, settingsUI, "Custom File Set Patch")
        dialog.show()
        if (!dialog.isOK) return null

       return Settings(dialog.userSettings, project, selectedDirectory)
    }

    class ConfigDialog(project: Project?, private val settingsUI: SettingsUI, title: String) : DialogWrapper(project, false) {
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
                            add(settingsUI.isContextCheckbox, BorderLayout.SOUTH)
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
                                add(settingsUI.generateExtractsRadio)
                            }
                            add(outputModePanel)
                            
                            val outputOptionsPanel = JPanel().apply {
                                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                                add(settingsUI.autoApply)
                                add(settingsUI.singleOutputFile)
                                
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
                outputMode = settingsUI.getOutputMode()
                singleOutputFile = settingsUI.singleOutputFile.isSelected
                outputFilename = settingsUI.outputFilename.text
                outputDirectory = settingsUI.outputDirectory.text
            }
        }
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        return super.isEnabled(event) && event.project != null
    }
}