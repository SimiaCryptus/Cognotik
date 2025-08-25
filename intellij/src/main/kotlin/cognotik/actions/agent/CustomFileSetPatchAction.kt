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
import com.simiacryptus.cognotik.CognotikAppServer
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
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.PathMatcher
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class CustomFileSetPatchAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    enum class OutputMode {
        EDIT_FILES,
        GENERATE_DOCUMENTATION,
        AGGREGATED_DATA_EXTRACTION
    }
    enum class BaseDirectoryMode {
        SELECTED_DIRECTORY,
        MODULE_ROOT
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
            private const val PREVIEW_UPDATE_DELAY_MS = 500L
        }

        private val patternCache = ConcurrentHashMap<String, PathMatcher>()
        private val previewExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "FileSetPreviewUpdater").apply { isDaemon = true }
        }
        private var currentPreviewTask: Future<*>? = null
        private val fileCache = ConcurrentHashMap<Path, List<Path>>()
        private val lastUpdateTime = java.util.concurrent.atomic.AtomicLong(0)

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
                    list: JList<*>?, value: Any?, index: Int,
                    isSelected: Boolean, cellHasFocus: Boolean
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

        private fun updateOutputOptionsVisibility() {
            val selectedMode = outputModeCombo.selectedItem as OutputMode
            val isGenerateMode =
                selectedMode == OutputMode.GENERATE_DOCUMENTATION || selectedMode == OutputMode.AGGREGATED_DATA_EXTRACTION
            singleOutputFile.isVisible = isGenerateMode
            outputFilename.isVisible = isGenerateMode
            outputDirectory.isVisible = isGenerateMode
            autoApply.isVisible = selectedMode == OutputMode.EDIT_FILES
            aggregationSizeSpinner.isVisible = selectedMode == OutputMode.AGGREGATED_DATA_EXTRACTION
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
                            BaseDirectoryMode.SELECTED_DIRECTORY ->
                                selectedDirectory?.toString() ?: project?.basePath ?: ""

                            BaseDirectoryMode.MODULE_ROOT ->
                                project?.basePath ?: selectedDirectory?.toString() ?: ""
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
                    if (!Thread.currentThread().isInterrupted) {
                        SwingUtilities.invokeLater {
                            updatePreview()
                        }
                    }
                } catch (e: InterruptedException) {
                    // Task was cancelled, do nothing
                }
            }
        }

        private fun updatePreview() {
            // Show loading indicator
            SwingUtilities.invokeLater {
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
                        fileSets,
                        contextFiles
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
            totalSize: Long,
            avgFileSetSize: Long,
            maxFileSetSize: Long,
            fileSetCount: Int,
            preview: String
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
            fileSets: List<FileSet>,
            contextFiles: List<Path>
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
                BaseDirectoryMode.SELECTED_DIRECTORY ->
                    selectedDirectory ?: project?.basePath?.let { Path.of(it) } ?: Path.of(".")

                BaseDirectoryMode.MODULE_ROOT ->
                    project?.basePath?.let { Path.of(it) } ?: selectedDirectory ?: Path.of(".")

            }
        }
       fun getSelectedDirectory(): Path? {
           return selectedDirectory
       }

        fun resolveFileSets(root: Path): List<FileSet> {
            // First get inclusion patterns
            val patterns = (0 until patternListModel.size)
                .map { patternListModel.getElementAt(it) }
                .filter { !it.isContext && !it.isExclusion }

            // Then get exclusion patterns
            val exclusionPatterns = (0 until patternListModel.size)
                .map { patternListModel.getElementAt(it) }
                .filter { it.isExclusion }

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
            val contextPatterns = (0 until patternListModel.size)
                .map { patternListModel.getElementAt(it) }
                .filter { it.isContext && !it.isExclusion }

            val exclusionPatterns = (0 until patternListModel.size)
                .map { patternListModel.getElementAt(it) }
                .filter { it.isExclusion }

            val contextFiles = contextPatterns.flatMap { pattern ->
                try {
                    val patternRoot = if (pattern.baseDirectory.isNotEmpty()) {
                        Path.of(pattern.baseDirectory)
                    } else {
                        root
                    }
                    val matcher = getOrCreateMatcher(pattern.pattern)
                    Files.walk(patternRoot).use { stream ->
                        stream
                            .filter { Files.isRegularFile(it) && isLLMTextFile(it.toFile(), treatDocumentsAsText.isSelected) }
                            .filter { path ->
                                val relativePath = patternRoot.relativize(path)
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
                // Check cache first
                val cacheKey = root.resolve(pattern.pattern)
                fileCache[cacheKey]?.let { it.mapNotNull { path ->
                        when {
                            Files.isDirectory(path) -> processDirectory(root, path)
                            Files.isRegularFile(path) && isLLMTextFile(
                                path.toFile(),
                                treatDocumentsAsText.isSelected
                            ) -> {
                                FileSet(root.relativize(path).toString(), listOf(path))
                            }

                            else -> null
                        }
                    }
                }
                
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
                // Cache the results
                fileCache[cacheKey] = matchedPaths

                // Filter out parent directories when child directories/files are also matched
                val filteredPaths = filterOutParentDirectories(matchedPaths)

                filteredPaths.mapNotNull { path ->
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
                val dirFiles = Files.walk(directory, 10).use { stream -> // Limit depth to avoid deep recursion
                    stream
                        .filter {
                            Files.isRegularFile(it) && isLLMTextFile(
                                it.toFile(),
                                treatDocumentsAsText.isSelected
                            )
                        }
                        .limit(1000) // Limit number of files per directory
                        .toList()
                }
                if (dirFiles.isNotEmpty()) {
                    FileSet(root.relativize(directory).toString(), dirFiles)
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
        var singleOutputFile: Boolean = true,
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
                super.doOKAction()
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
                
                userSettings.apply {
                    transformationMessage = settingsUI.transformationMessage.text
                    patterns = (0 until settingsUI.patternListModel.size)
                        .map { settingsUI.patternListModel.getElementAt(it) }
                    autoApply = settingsUI.autoApply.isSelected
                    treatDocumentsAsText = settingsUI.treatDocumentsAsText.isSelected
                    outputMode = settingsUI.getOutputMode()
                    singleOutputFile = settingsUI.singleOutputFile.isSelected
                    outputFilename = settingsUI.outputFilename.text
                    outputDirectory = selectedDirectory?.resolve(settingsUI.outputDirectory.text)?.toString()
                        ?: settingsUI.outputDirectory.text
                    concurrency = settingsUI.concurrencySpinner.value as Int
                    bigDataThreshold = settingsUI.bigDataThresholdSpinner.value as Int
                    aggregationSizeKB = settingsUI.aggregationSizeSpinner.value as Int
                }
                // Handle the actual action execution here since dialog is non-modal
                executeAction()
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
                } catch (e: Exception) {
                    log.error("Failed to execute action", e)
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to start server: ${e.message}",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
        }
        dialog.show() // BUG: As a non-modal dialog, this does not block further execution
        if (dialog.isOK) {
            Settings(dialog.userSettings, project)
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