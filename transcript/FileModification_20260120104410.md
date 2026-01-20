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

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/generate/CreateFileFromDescriptionAction.kt

```
package cognotik.actions.generate

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.util.toContentList
import java.io.File

class CreateFileFromDescriptionAction :
    cognotik.actions.FileContextAction<CreateFileFromDescriptionAction.Settings>(false, true) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    private val log = Logger.getInstance(CreateFileFromDescriptionAction::class.java)

    companion object {
        private const val DEFAULT_DIRECTIVE = "Create a new file"
        private const val FILE_PATH_REGEX = """File(?:name)?: ['`"]?([^'`"]+)['`"]?"""
    }

    class ProjectFile(var path: String = "", var code: String = "")

    class SettingsUI {
    }

    class Settings(
        var directive: String = "",
        val project: Project? = null
    )

    override fun getConfig(project: Project?, e: AnActionEvent): Settings? {
        val userSettings = UITools.showDialog(
            project,
            SettingsUI::class.java,
            Settings::class.java,
            "Create File From Description"
        ) ?: return null
        return Settings(userSettings.directive, project)
    }

    override fun processSelection(
        state: SelectionState,
        config: Settings?,
        progress: ProgressIndicator
    ): Array<File> {
        progress.isIndeterminate = false
        progress.text = "Generating file from description..."
        return try {
            processSelectionInternal(state, config, progress)
        } catch (e: Exception) {
            log.error("Failed to create file from description", e)
            UITools.showErrorDialog(
                "Failed to create file: ${e.message}",
                "Error"
            )
            emptyArray()
        }
    }

    private fun processSelectionInternal(
        state: SelectionState,
        config: Settings?,
        progress: ProgressIndicator
    ): Array<File> {
        require(state.projectRoot.exists()) { "Project root directory does not exist" }
        require(state.selectedFile.exists()) { "Selected file does not exist" }

        val projectRoot = state.projectRoot.toPath()
        val inputPath = projectRoot.relativize(state.selectedFile.toPath()).toString()
        val pathSegments = inputPath.split("/").toList()
        val updirSegments = pathSegments.takeWhile { it == ".." }
        val moduleRoot = projectRoot.resolve(pathSegments.take(updirSegments.size * 2).joinToString("/"))
        val filePath = pathSegments.drop(updirSegments.size * 2).joinToString("/")
        progress.text = "Generating file content..."
        progress.fraction = 0.3

        val generatedFile = generateFile(
            filePath,
            config?.directive ?: DEFAULT_DIRECTIVE,
            AppSettingsState.instance.smartChatClient
        )

        var path = generatedFile.path
        var outputPath = moduleRoot.resolve(path)
        if (outputPath.toFile().exists()) {
            val extension = path.substringAfterLast(".")
            val name = path.substringBeforeLast(".")
            val fileIndex = (1..Int.MAX_VALUE).find {
                !File("$name.$it.$extension").exists()
            }
            path = "$name.$fileIndex.$extension"
            outputPath = moduleRoot.resolve(path)
        } else {
            outputPath = moduleRoot.resolve(path)
        }
        progress.text = "Writing file to disk..."
        progress.fraction = 0.8

        outputPath.parent.toFile().mkdirs()
        WriteCommandAction.runWriteCommandAction(config?.project) { outputPath.toFile().writeText(generatedFile.code) }
        Thread.sleep(100)

        return arrayOf(outputPath.toFile())
    }

    private fun generateFile(
        basePath: String,
        directive: String,
        model: ChatInterface
    ): ProjectFile {
        require(directive.isNotBlank()) { "Directive cannot be empty" }
        try {
            val response = run {
                model.chat(
                    listOf(
                        ChatMessage(
                            Role.system, """
                        You will interpret natural language requirements to create a new file.
                        Provide a new filename and the code to be written to the file.
                        Paths should be relative to the project root and should not exist.
                        Output the file path using the a line with the format "File: <path>".
                        Output the file code directly after the header line with no additional decoration.
                    """.trimIndent().toContentList()
                        ),
                        ChatMessage(
                            Role.user, """
                        Create a new file based on the following directive: $directive
    
                        The file location should be based on the selected path `$basePath`
                    """.trimIndent().toContentList()
                        )
                    )
                ).choices.firstOrNull()?.message?.content?.trim()
            } ?: throw IllegalStateException("Empty response from AI")
            var outputPath = basePath
            val header = response.lines().firstOrNull() ?: throw IllegalStateException("Invalid response format")
            val body = response.lines().drop(1).joinToString("\n").trim().lines()
                .dropWhile { it.startsWith("```") }
                .dropLastWhile { it.startsWith("```") }
                .joinToString("\n")
            val pathPattern = FILE_PATH_REGEX.toRegex()
            if (pathPattern.matches(header)) {
                val match = pathPattern.matchEntire(header)!!
                outputPath = match.groupValues[1]
            }
            require(body.isNotBlank()) { "Generated file content cannot be empty" }
            return ProjectFile(path = outputPath, code = body)
        } catch (e: Exception) {
            log.error("Failed to generate file content", e)
            throw IllegalStateException("Failed to generate file content: ${e.message}", e)
        }
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/generate/CreateImageAction.kt

```
package cognotik.actions.generate

import cognotik.actions.BaseAction
import cognotik.actions.agent.toFile
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.ui.JBUI
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageGenerationAgent
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.*
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.swing.*

class CreateImageAction : BaseAction() {
    inner class ImageGenerationDialog(project: Project) : DialogWrapper(project) {
        private val fileNameField = JTextField(generateDefaultFileName(), 20)
        private val instructionsArea = JTextArea(3, 20)

        init {
            log.debug("Initializing ImageGenerationDialog")
            title = "Generate Image"
            init()
        }

        private fun generateDefaultFileName(): String {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            return "generated_image_$timestamp.png"
        }

        override fun createCenterPanel(): JComponent {
            return JPanel(GridBagLayout()).apply {
                val c = GridBagConstraints()
                c.fill = GridBagConstraints.HORIZONTAL
                c.insets = JBUI.insets(5)
                c.gridx = 0; c.gridy = 0
                add(JLabel("Output filename:"), c)
                c.gridx = 1; c.gridy = 0
                add(fileNameField, c)
                c.gridx = 0; c.gridy = 1
                add(JLabel("Special instructions:"), c)
                c.gridx = 1; c.gridy = 1
                c.fill = GridBagConstraints.BOTH
                add(JScrollPane(instructionsArea), c)
            }
        }

        fun getFileName() = fileNameField.text
        fun getInstructions() = instructionsArea.text
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(e: AnActionEvent) {
        log.info("Starting CreateImageAction handler")
        val rootRef = AtomicReference<Path?>(null)
        val codeFiles: MutableSet<Path> = mutableSetOf()
        val dialog = ImageGenerationDialog(e.project!!)
        if (!dialog.showAndGet()) {
            log.debug("Dialog cancelled by user")
            return
        }
        UITools.runAsync(e.project, "Creating Image", true) { progress ->
            try {
                progress.text = "Analyzing code files..."
                log.debug("Beginning code analysis")
                fun codeSummary() = codeFiles.filter {
                    rootRef.get()?.resolve(it)?.toFile()?.exists() ?: false
                }.associateWith { rootRef.get()?.resolve(it)?.toFile()?.readText(Charsets.UTF_8) }.entries.joinToString(
                    "\n\n"
                ) { (path, code) ->
                    val extension = path.toString().split('.').lastOrNull()
                    "# $path\n```$extension\n${code}\n```"
                }

                val dataContext = e.dataContext
                val virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)
                log.debug("Found ${virtualFiles?.size ?: 0} virtual files")
                progress.text = "Determining root directory..."
                val folder = e.getSelectedFolder()
                rootRef.set(
                    if (null != folder) {
                        log.debug("Using selected folder as root: {}", folder.toFile)
                        folder.toFile.toPath()
                    } else if (1 == virtualFiles?.size) {
                        log.debug("Using parent of single file as root")
                        e.getSelectedFile()?.parent?.toNioPath()
                    } else {
                        log.debug("Using module root as root directory")
                        getModuleRootForFile(
                            e.getSelectedFile()?.parent?.toFile ?: throw RuntimeException("No file selected")
                        ).toPath()
                    }
                )
                progress.text = "Collecting files..."

                val root = rootRef.get() ?: throw RuntimeException("Root path not set")
                if (!Files.exists(root)) {
                    throw IOException("Root directory does not exist: $root")
                }
                log.info("Using root directory: $root")
                val files = getFiles(virtualFiles, root)
                codeFiles.addAll(files)
                log.debug("Collected ${codeFiles.size} code files")
                progress.text = "Generating image..."
                log.info("Starting image generation with ${codeFiles.size} files")
                val imageActor = ImageGenerationAgent(
                    prompt = """
                    You are a technical drawing assistant.
                    You will be composing an image about the following code:
                    ${codeSummary()}
                    Special instructions: ${dialog.getInstructions()}
                    """.trimIndent(),
                    textModel = AppSettingsState.instance.smartChatClient,
                    imageModel = AppSettingsState.instance.imageModel?.model,
                    imageClient = AppSettingsState.instance.imageClient
                )
                log.debug("Sending request to image generation API")
                val response = imageActor.answer(listOf(codeSummary(), dialog.getInstructions()))
                log.debug("Image generation completed successfully")
                val imagePath = root.resolve(dialog.getFileName())
                write(response, imagePath)
                VirtualFileManager.getInstance().findFileByNioPath(imagePath)?.refresh(false, false)
            } catch (ex: Throwable) {
                when (ex) {
                    is IOException -> log.error("IO error during image creation: ${ex.message}", ex)
                    is SecurityException -> log.error("Security error during image creation: ${ex.message}", ex)
                    is IllegalArgumentException -> log.error(
                        "Invalid argument during image creation: ${ex.message}",
                        ex
                    )

                    else -> log.error("Unexpected error during image creation", ex)
                }
                UITools.showErrorDialog("Failed to create image: ${ex.message}", "Error")
            }
        }
    }

    private fun write(
        code: ImageAndText, path: Path
    ) = try {
        log.debug("Creating parent directories for: {}", path)
        path.parent?.toFile()?.mkdirs()
        val format = path.toString().split(".").last()
        log.debug("Writing image in format: $format")

        val bytes = ByteArrayOutputStream().use { outputStream ->
            if (!ImageIO.write(
                    code.image, format, outputStream
                )
            ) {
                throw IOException("Unsupported or invalid image format: $format")
            }
            outputStream.toByteArray()
        }
        path.toFile().writeBytes(bytes)
        path
    } catch (e: Exception) {
        log.error("Failed to write image to $path", e)
        when (e) {
            is IOException -> throw IOException("Failed to write image: ${e.message}", e)
            is SecurityException -> throw SecurityException("Security error writing image: ${e.message}", e)
            else -> throw RuntimeException("Unexpected error writing image: ${e.message}", e)
        }
    }

    private fun getFiles(
        virtualFiles: Array<out VirtualFile>?, root: Path
    ): MutableSet<Path> {
        val codeFiles = mutableSetOf<Path>()
        virtualFiles?.forEach { file ->
            if (file.isDirectory) {
                getFiles(file.children, root)
            } else {
                val relative = root.relativize(file.toNioPath())
                codeFiles.add(relative)

            }
        }
        return codeFiles
    }

    override fun isEnabled(event: AnActionEvent): Boolean {
        if (!super.isEnabled(event)) return false
        event.getSelectedFile() ?: return false
        AppSettingsState.instance.imageModel ?: return false
        return true
    }

    companion object {
        private val log = LoggerFactory.getLogger(CreateImageAction::class.java)
    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/generate/GenerateDocumentationAction.kt

```
package cognotik.actions.generate

import cognotik.actions.FileContextAction
import cognotik.actions.test.TestResultAutofixAction.Companion.getProjectStructure
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.getSelectedFiles
import com.simiacryptus.cognotik.util.getSelectedFolder
import com.simiacryptus.cognotik.util.toContentList
import org.apache.commons.io.IOUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.swing.*

class GenerateDocumentationAction : FileContextAction<GenerateDocumentationAction.Settings>() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    class SettingsUI {
        @Name("Single Output File")
        val singleOutputFile = JCheckBox("Produce a single output file", true)

        @Name("Files to Process")
        val filesToProcess = CheckBoxList<Path>()

        @Name("AI Instruction")
        val transformationMessage = JBTextArea(4, 40)

        @Name("Recent Instructions")
        val recentInstructions = JComboBox<String>()

        @Name("Output File")
        val outputFilename = JBTextField()

        @Name("Output Directory")
        val outputDirectory = JBTextField()
    }

    class UserSettings(
        var transformationMessage: String = "Create user documentation",
        var outputFilename: String = "compiled_documentation.md",
        var filesToProcess: List<Path> = listOf(),
        var singleOutputFile: Boolean = true,
        var outputDirectory: String = "docs/"
    )

    class Settings(
        val settings: UserSettings? = null,
        val project: Project? = null,
        val root: Path? = null
    )

    override fun getConfig(project: Project?, e: AnActionEvent): Settings? {
        var root = e.getSelectedFolder()?.toNioPath()
        val files = if (root == null) {
            e.getSelectedFiles().map { it.toNioPath() }.toTypedArray()
        } else {
            Files.walk(root).filter { Files.isRegularFile(it) && !Files.isDirectory(it) }.toList().filterNotNull()
                .sortedBy { it.toString() }.toTypedArray()
        }
        val settingsUI = SettingsUI().apply {
            filesToProcess.setItems(files.toMutableList()) { path ->
                root?.relativize(path)?.toString() ?: path.toString()
            }
            files.forEach { path ->
                filesToProcess.setItemSelected(path, true)
            }
            outputDirectory.text = "docs/"
        }
        val mruDocumentationInstructions = AppSettingsState.instance.getRecentCommands("DocumentationInstructions")
        settingsUI.recentInstructions.model = DefaultComboBoxModel(
            mruDocumentationInstructions.getMostRecent(10).map {
                "${it.split(" ").first()} ${it.split(" ").drop(1).joinToString(" ")}"
            }.toTypedArray()
        )
        settingsUI.recentInstructions.selectedIndex = -1
        settingsUI.recentInstructions.addActionListener { updateUIFromSelection(settingsUI) }
        val dialog = DocumentationCompilerDialog(project, settingsUI)
        dialog.show()
        val settings: UserSettings = dialog.userSettings
        settings.singleOutputFile = settingsUI.singleOutputFile.isSelected
        settings.outputDirectory = settingsUI.outputDirectory.text
        val result = dialog.isOK
        settings.filesToProcess = when {
            result -> files.filter { path -> settingsUI.filesToProcess.isItemSelected(path) }.sortedBy { it.toString() }
                .toList()

            else -> listOf()
        }
        if (settings.filesToProcess.isEmpty()) return null
        mruDocumentationInstructions.addInstructionToHistory("${settings.outputFilename} ${settings.transformationMessage}")

        return Settings(settings, project, root)
    }

    private fun updateUIFromSelection(settingsUI: SettingsUI) {
        val selected = settingsUI.recentInstructions.selectedItem as? String
        if (selected != null) {
            val parts = selected.split(" ", limit = 2)
            if (parts.size == 2) {
                settingsUI.outputFilename.text = parts[0]
                settingsUI.transformationMessage.text = parts[1]
            } else {
                settingsUI.transformationMessage.text = selected
            }
        } else {
            settingsUI.transformationMessage.text = ""
        }
    }

    override fun processSelection(state: SelectionState, config: Settings?, progress: ProgressIndicator): Array<File> {
        progress.fraction = 0.0
        if (config?.settings == null) {

            return emptyArray<File>().also {

                return@also
            }
        }
        progress.text = "Initializing documentation generation..."

        val projectRoot = state.projectRoot.toPath()
        val outputDirectory = config.settings.outputDirectory
        var outputPath = (config.root ?: state.projectRoot.toPath()).resolve(config.settings.outputFilename)
        val relativePath = (config.root ?: state.projectRoot.toPath())?.relativize(outputPath) ?: outputPath
        outputPath = state.projectRoot.toPath().resolve(outputDirectory).resolve(relativePath)

        if (outputPath.toFile().exists()) {
            val extension = outputPath.toString().split(".").last()
            val name = outputPath.toString().split(".").dropLast(1).joinToString(".")
            val fileIndex = (1..Int.MAX_VALUE).find {
                !projectRoot.resolve("$name.$it.$extension").toFile().exists()
            }
            outputPath = projectRoot.resolve("$name.$fileIndex.$extension") ?: outputPath
        }

        val executorService = Executors.newFixedThreadPool(4)
        val transformationMessage = config.settings.transformationMessage
        val markdownContent = TreeMap<String, String>()
        try {
            val selectedPaths = config.settings.filesToProcess.sortedBy { it.toString() }
            val partitionedPaths = if (null != projectRoot) Files.walk(projectRoot)
                .filter { Files.isRegularFile(it) && !Files.isDirectory(it) }.toList().sortedBy { it.toString() }
                .groupBy { selectedPaths.contains(it) } else selectedPaths.groupBy { true }
            val totalFiles = partitionedPaths[true]?.size ?: 0
            var processedFiles = 0
            val pathList = partitionedPaths[true]?.toList()?.filterNotNull()?.map<Path, Future<Path?>> { path ->
                executorService.submit<Path?> {
                    var retries = 0
                    val maxRetries = 3
                    while (retries < maxRetries) {
                        try {
                            val fileContent =
                                IOUtils.toString(FileInputStream(path.toFile()), "UTF-8") ?: return@submit null
                            val transformContent = transformContent(
                                path,
                                fileContent,
                                transformationMessage,
                                AppSettingsState.instance.smartChatClient,
                                projectRoot
                            )
                            processTransformedContent(
                                path,
                                transformContent,
                                config,
                                projectRoot,
                                outputDirectory,
                                outputPath,
                                markdownContent
                            )
                            synchronized(progress) {
                                processedFiles++
                                progress.fraction = processedFiles.toDouble() / totalFiles
                                progress.text = "Processing file ${processedFiles} of ${totalFiles}"
                            }
                            return@submit path
                        } catch (e: Exception) {
                            retries++
                            if (retries >= maxRetries) {
                                log.error("Failed to process file after $maxRetries attempts: $path", e)
                                return@submit null
                            }
                            log.warn("Error processing file: $path. Retrying (attempt $retries)", e)
                            Thread.sleep(1000L * retries)
                        }
                    }
                    null
                }
            }?.toTypedArray()?.mapNotNull { future ->
                try {
                    future.get(2, TimeUnit.MINUTES)

                } catch (e: Exception) {
                    when (e) {
                        is TimeoutException -> log.error("File processing timed out", e)
                        else -> log.error("Error processing file", e)
                    }
                    null
                }
            } ?: listOf()
            if (config.settings.singleOutputFile == true) {
                val sortedContent = markdownContent.entries.joinToString("\n\n") { (path, content) ->
                    "# $path\n\n$content"
                }
                outputPath.parent.toFile().mkdirs()
                Files.write(outputPath, sortedContent.toByteArray())
                open(config.project!!, outputPath)
                return arrayOf(outputPath.toFile())
            } else {
                val outputDir = projectRoot.resolve(outputDirectory) ?: File(outputDirectory).toPath()
                outputDir.toFile().mkdirs()
                open(config.project!!, projectRoot.resolve(outputDirectory) ?: outputDir)
                return pathList.map { it.toFile() }.toTypedArray()
            }
        } finally {
            executorService.shutdown()
        }
    }

    private fun processTransformedContent(
        path: Path,
        transformContent: String,
        config: Settings?,
        projectRoot: Path,
        outputDirectory: String,
        outputPath: Path,
        markdownContent: TreeMap<String, String>
    ) {
        if (config?.settings?.singleOutputFile == true) {
            markdownContent[projectRoot.relativize(path).toString()] =
                transformContent.replace("(?s)(?<![^\\n])#".toRegex(), "\n##")
        } else {
            var individualOutputPath = /*selectedFolder*/ projectRoot.relativize(
                path.parent.resolve(
                    path.fileName.toString().split('.').dropLast(1).joinToString(".") + "." + outputPath.fileName
                )
            )
            individualOutputPath = projectRoot.resolve(individualOutputPath) ?: individualOutputPath
            individualOutputPath = projectRoot.relativize(individualOutputPath) ?: individualOutputPath
            individualOutputPath =
                projectRoot.resolve(outputDirectory).resolve(individualOutputPath) ?: individualOutputPath
            individualOutputPath.parent?.toFile()?.mkdirs()
            Files.write(individualOutputPath, transformContent.toByteArray())
        }
    }

    private fun transformContent(
        path: Path, fileContent: String, transformationMessage: String, model: ChatInterface, projectRoot: Path
    ) = run {
        model.chat(
            listOf(
                ModelSchema.ChatMessage(
                    ModelSchema.Role.system, """
                        You will combine natural language instructions with a user provided code example to document code.
                        """.trimIndent().toContentList(),
                ),
                ModelSchema.ChatMessage(
                    ModelSchema.Role.user,
                    "## Project:\n${getProjectStructure(projectRoot)}\n\n## $path:\n```\n$fileContent\n```\n\nInstructions: $transformationMessage".toContentList()
                ),
            )
        ).choices.first().message?.content?.trim()
    } ?: fileContent

    companion object {
        private val scheduledPool = Executors.newScheduledThreadPool(1)
    }

    fun open(project: Project, outputPath: Path) {
        lateinit var function: () -> Unit
        function = {
            val file = outputPath.toFile()
            if (file.exists()) {

                ApplicationManager.getApplication().invokeLater {
                    val ioFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                    if (false == (ioFile?.let { FileEditorManager.getInstance(project).isFileOpen(it) })) {
                        val localFileSystem = LocalFileSystem.getInstance()

                        val virtualFile = localFileSystem.refreshAndFindFileByIoFile(file)
                        virtualFile?.let {
                            FileEditorManager.getInstance(project).openFile(it, true)
                        } ?: scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
                    } else {
                        scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
                    }
                }
            } else {
                scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
            }
        }
        scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
    }

    inner class DocumentationCompilerDialog(project: Project?, private val settingsUI: SettingsUI) :
        DialogWrapper(project) {
        val userSettings = UserSettings()

        init {
            title = "Compile Documentation"

            settingsUI.transformationMessage.text = userSettings.transformationMessage
            settingsUI.outputFilename.text = userSettings.outputFilename
            settingsUI.outputDirectory.text = userSettings.outputDirectory
            settingsUI.singleOutputFile.isSelected = userSettings.singleOutputFile
            settingsUI.recentInstructions.addActionListener {
                val selected = settingsUI.recentInstructions.selectedItem as? String
                selected?.let {
                    updateUIFromSelection(settingsUI)
                }
            }
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(BorderLayout()).apply {
                val filesScrollPane = JBScrollPane(settingsUI.filesToProcess).apply {
                    preferredSize = Dimension(600, 400)

                }
                add(filesScrollPane, BorderLayout.CENTER)


                val optionsPanel = JPanel().apply {
                    layout = BoxLayout(this, BoxLayout.Y_AXIS)
                    border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

                    add(JLabel("Recent Instructions"))
                    add(settingsUI.recentInstructions)
                    add(Box.createVerticalStrut(10))
                    add(JLabel("AI Instruction"))
                    add(settingsUI.transformationMessage)
                    add(Box.createVerticalStrut(10))
                    add(Box.createVerticalStrut(10))

                    add(JLabel("Output File"))
                    add(settingsUI.outputFilename)
                    add(Box.createVerticalStrut(10))
                    add(JLabel("Output Directory"))
                    add(settingsUI.outputDirectory)
                    add(Box.createVerticalStrut(10))
                    add(settingsUI.singleOutputFile)
                }
                add(optionsPanel, BorderLayout.SOUTH)
            }
            return panel
        }

        override fun doOKAction() {
            if (!validateInput()) {
                return
            }
            super.doOKAction()
            userSettings.transformationMessage = settingsUI.transformationMessage.text
            userSettings.outputFilename = settingsUI.outputFilename.text
            userSettings.outputDirectory = settingsUI.outputDirectory.text


            userSettings.filesToProcess =
                settingsUI.filesToProcess.items.filter { path -> settingsUI.filesToProcess.isItemSelected(path) }
            userSettings.singleOutputFile = settingsUI.singleOutputFile.isSelected
        }

        private fun validateInput(): Boolean {
            if (settingsUI.transformationMessage.text.isBlank()) {
                Messages.showErrorDialog("AI Instruction cannot be empty", "Input Error")
                return false
            }
            if (settingsUI.outputFilename.text.isBlank()) {
                Messages.showErrorDialog("Output File cannot be empty", "Input Error")
                return false
            }
            if (settingsUI.outputDirectory.text.isBlank()) {
                Messages.showErrorDialog("Output Directory cannot be empty", "Input Error")
                return false
            }
            return true
        }
    }
}

val <T> CheckBoxList<T>.items: List<T>
    get() {
        val items = mutableListOf<T>()
        for (i in 0 until model.size) {
            items.add(getItemAt(i)!!)
        }
        return items
    }
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/generate/GenerateRelatedFileAction.kt

```
package cognotik.actions.generate

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.config.Name
import com.simiacryptus.cognotik.models.ModelSchema.ChatMessage
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.util.UITools
import com.simiacryptus.cognotik.util.getModuleRootForFile
import com.simiacryptus.cognotik.util.getSelectedFiles
import com.simiacryptus.cognotik.util.toContentList
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import java.awt.BorderLayout
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JTextArea

class GenerateRelatedFileAction : cognotik.actions.FileContextAction<GenerateRelatedFileAction.Settings>() {
    private val log = Logger.getInstance(GenerateRelatedFileAction::class.java)

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun isEnabled(event: AnActionEvent): Boolean {
        return event.getSelectedFiles().size == 1 && super.isEnabled(event)
    }

    data class ProjectFile(
        val path: String = "",
        val code: String = ""
    )

    class SettingsUI {
        @Name("Directive")
        var directive: JTextArea = JTextArea(3, 120).apply {
            lineWrap = true
            wrapStyleWord = true
            text = "Create README.md"
        }

        @Name("Recent Commands")
        var recentCommandsDropdown: JComboBox<String> = JComboBox()

        init {
            directive.addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusGained(e: java.awt.event.FocusEvent) {
                    directive.selectAll()
                }
            })

            val panel = createPanel()
            panel.add(createDirectivePanel(), BorderLayout.NORTH)
            panel.add(createRecentCommandsPanel(), BorderLayout.SOUTH)
            populateRecentCommands()
        }

        private fun createPanel(): JPanel {
            return JPanel(BorderLayout())
        }

        private fun createDirectivePanel(): JPanel {
            val directivePanel = JPanel()
            directivePanel.add(directive)
            return directivePanel
        }

        private fun createRecentCommandsPanel(): JPanel {
            val recentCommandsPanel = JPanel()
            recentCommandsPanel.add(recentCommandsDropdown)
            return recentCommandsPanel
        }

        private fun populateRecentCommands() {
            val recentCommands = AppSettingsState.instance.getRecentCommands("generate").getMostRecent()
            recentCommands.forEach { recentCommandsDropdown.addItem(it) }
        }
    }

    class UserSettings(
        var directive: String = "",
    )

    class Settings(
        val settings: UserSettings? = null,
        val project: Project? = null
    )

    override fun getConfig(project: Project?, e: AnActionEvent): Settings? {
        val userSettings = UITools.showDialog(
            project,
            SettingsUI::class.java,
            UserSettings::class.java,
            "Create Analogue File"
        )
        return if (userSettings != null) {
            Settings(userSettings, project)
        } else {
            null
        }
    }

    override fun processSelection(state: SelectionState, config: Settings?, progress: ProgressIndicator): Array<File> {
        if (config?.settings == null) {
            log.info("Action canceled by user.")
            return emptyArray()
        }
        try {
            progress.isIndeterminate = false
            progress.text = "Reading source file..."
            progress.fraction = 0.2
            val root = getModuleRootForFile(state.selectedFile).toPath()
            val selectedFile = state.selectedFile
            val analogue = generateFile(
                baseFile = ProjectFile(
                    path = root.relativize(selectedFile.toPath()).toString(),
                    code = IOUtils.toString(FileInputStream(selectedFile), "UTF-8")
                ),
                directive = config.settings.directive,
                progress = progress,
                model = AppSettingsState.instance.smartChatClient
            )
            progress.text = "Generating output file..."
            progress.fraction = 0.6
            var outputPath = root.resolve(analogue.path)
            if (outputPath.toFile().exists()) {
                val extension = outputPath.toString().split(".").last()
                val name = outputPath.toString().split(".").dropLast(1).joinToString(".")
                val fileIndex = (1..Int.MAX_VALUE).find {
                    !root.resolve("$name.$it.$extension").toFile().exists()
                }
                outputPath = root.resolve("$name.$fileIndex.$extension")
            }
            progress.text = "Writing output file..."
            progress.fraction = 0.8
            outputPath.parent.toFile().mkdirs()
            FileUtils.write(outputPath.toFile(), analogue.code, "UTF-8")
            open(config.project!!, outputPath)
            return arrayOf(outputPath.toFile())
        } catch (e: Exception) {
            log.error("Failed to generate related file", e)
            throw e
        }
    }

    private fun generateFile(
        baseFile: ProjectFile, directive: String, progress: ProgressIndicator, model: ChatInterface
    ): ProjectFile = try {
        progress.text = "Generating content with AI..."
        progress.fraction = 0.4
        val response =
            model.chat(
                listOf(
                    ChatMessage(
                        Role.system, """
            You will combine natural language instructions with a user provided code example to create a new file.
            Provide a new filename and the code to be written to the file.
            Paths should be relative to the project root and should not exist.
            Output the file path using the a line with the format "File: <path>".
            Output the file code directly after the header line with no additional decoration.
            """.trimIndent().toContentList(),
                    ),
                    ChatMessage(
                        Role.user, ("""
                              Create a new file based on the following directive: """.trimIndent() + directive + """

                              The file should be based on `""".trimIndent() + baseFile.path + """` which contains the following code:

                              ```
                              """.trimIndent() + baseFile.code + """
                              ```
                              """.trimIndent()).toContentList(),
                    )
                )
            ).choices.firstOrNull()?.message?.content?.trim() ?: throw IllegalStateException(
                "No response from API"
            )
        var outputPath = baseFile.path
        val header = response.split("\n").first()
        var body = response.split("\n").drop(1).joinToString("\n").trim()
        if (body.contains("```")) {
            body = body.split("```.*".toRegex()).drop(1).firstOrNull()?.trim() ?: body
        }
        val pathPattern = "File(?:name)?: ['\"]?([^'\"]+)['\"]?".toRegex()
        val matcher = pathPattern.find(header)
        if (matcher != null) {
            outputPath = matcher.groupValues[1].trim()
        }
        ProjectFile(
            path = outputPath,
            code = body
        )
    } catch (e: Exception) {
        throw e
    }

    companion object {
        fun open(project: Project, outputPath: Path) {
            val functionRef = AtomicReference<(() -> Unit)?>(null)
            val function: () -> Unit = {
                val file = outputPath.toFile()
                if (file.exists()) {

                    ApplicationManager.getApplication().invokeLater {
                        val ioFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                        if (false == (ioFile?.let { FileEditorManager.getInstance(project).isFileOpen(it) })) {
                            val localFileSystem = LocalFileSystem.getInstance()

                            val virtualFile = localFileSystem.refreshAndFindFileByIoFile(file)
                            virtualFile?.let {
                                FileEditorManager.getInstance(project).openFile(it, true)
                            } ?: scheduledPool.schedule(functionRef.get()!!, 100, TimeUnit.MILLISECONDS)
                        } else {
                            scheduledPool.schedule(functionRef.get()!!, 100, TimeUnit.MILLISECONDS)
                        }
                    }
                } else {
                    scheduledPool.schedule(functionRef.get()!!, 100, TimeUnit.MILLISECONDS)
                }
            }
            functionRef.set(function)
            scheduledPool.schedule(function, 100, TimeUnit.MILLISECONDS)
        }

    }
}
```

# /home/andrew/code/Cognotik/intellij/src/main/kotlin/cognotik/actions/generate/OCRAction.kt

```
package cognotik.actions.generate

import cognotik.actions.BaseAction
import cognotik.actions.chat.ImageChatAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.RenderableDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.util.UITools
import java.io.File

class OCRAction : BaseAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun handle(event: AnActionEvent) {
        val project = event.project ?: return
        val root = File(project.basePath ?: return).toPath()
        val files = ImageChatAction.Companion.getFiles(
            PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(event.dataContext),
            root
        ).map { root.resolve(it).toFile() }
        UITools.runAsync(project, "OCR Processing", true) { progress ->
            files.forEach { file ->
                if (progress.isCanceled) return@forEach
                try {
                    if (!file.exists()) return@forEach
                    file.getDocumentReader().use { reader ->
                        if (reader is PaginatedDocumentReader && reader is RenderableDocumentReader) {
                            val sb = StringBuilder()
                            val pageCount = reader.getPageCount()
                            for (page in 0 until pageCount) {
                                if (progress.isCanceled) break
                                progress.text = "Processing ${file.name} (${page + 1}/$pageCount)"
                                progress.fraction = page.toDouble() / pageCount

                                val image = reader.renderImage(page, 150f)
                                val response = AppSettingsState.Companion.instance.imageChatClient.chat(
                                    listOfNotNull(
                                        ModelSchema.ChatMessage(
                                            ModelSchema.Role.system,
                                            listOf(ModelSchema.ContentPart("You are an OCR engine. Convert the image to Markdown. Output only the markdown content."))
                                        ),
                                        ModelSchema.ChatMessage(
                                            ModelSchema.Role.user,
                                            listOf(
                                                ModelSchema.ContentPart("Convert this page")
                                                    .apply { this.image = image })
                                        )
                                    )
                                ).choices.first().message?.content ?: ""
                                sb.append(response).append("\n\n")
                            }
                            val outputFile = File(file.parentFile, file.nameWithoutExtension + ".md")
                            outputFile.writeText(sb.toString())
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Error processing ${file.name}", e)
                }
            }
        }
    }

    companion object {
        private val log = Logger.getInstance(OCRAction::class.java)
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `cognotik.actions.generate` package, based on the provided source files and documentation requirements.

### intellij/src/main/kotlin/cognotik/actions/generate/README.md
```markdown
# Generate Actions

This package contains a suite of AI-powered actions designed to generate new assets, documentation, and code structures within the IntelliJ environment. These tools leverage large language models and image generation capabilities to streamline development workflows.

## Actions

### [CreateFileFromDescriptionAction](CreateFileFromDescriptionAction.kt)
Allows users to create a new file by providing a natural language description. The AI interprets the requirements to determine both an appropriate filename and the initial content of the file.
- **Usage**: Right-click in the project view and select "Create File From Description".
- **Features**: Automatic path resolution relative to the project root and collision detection for existing files.

### [CreateImageAction](CreateImageAction.kt)
A technical drawing assistant that generates images based on the context of selected code files.
- **Usage**: Select one or more files/folders and trigger "Create Image".
- **Features**: Uses the code content as context for the image generation prompt, allowing for the creation of diagrams or illustrations relevant to the implementation.

### [GenerateDocumentationAction](GenerateDocumentationAction.kt)
Compiles user-facing or technical documentation from a selection of source files.
- **Usage**: Select files or a directory to process into documentation.
- **Features**: 
    - Supports producing a single compiled markdown file or individual documentation files for each source.
    - Maintains a history of recent documentation instructions.
    - Parallel processing of files for efficiency.

### [GenerateRelatedFileAction](GenerateRelatedFileAction.kt)
Creates a new "analogue" file based on an existing source file and a specific directive (e.g., "Create a README for this class" or "Create a unit test").
- **Usage**: Right-click a single file and select "Create Analogue File".
- **Features**: Provides the source file's content to the AI to ensure the generated file is contextually accurate.

### [OCRAction](OCRAction.kt)
Converts documents and images into Markdown text using vision-capable AI models.
- **Usage**: Select image files or PDFs and trigger "OCR Processing".
- **Features**: 
    - Handles paginated documents (like PDFs).
    - Renders document pages to images before processing with an OCR-specialized AI prompt.
    - Outputs results as `.md` files in the same directory as the source.

## Configuration
These actions primarily utilize the "Smart Chat Client" and "Image Model" settings configured in the Cognotik plugin settings. Ensure your API keys and model selections are correctly configured to use these features.
```

### Summary of Changes
- Created a new `README.md` in `intellij/src/main/kotlin/cognotik/actions/generate/`.
- Documented the five primary actions found in the provided source code: `CreateFileFromDescriptionAction`, `CreateImageAction`, `GenerateDocumentationAction`, `GenerateRelatedFileAction`, and `OCRAction`.
- Included descriptions of functionality, usage patterns, and key features for each action.
- Linked the documentation to the source files as per standard project patterns.
- Added a section on configuration dependencies (Smart Chat Client and Image Models).
</details>

                - <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/generate/README.md'>intellij/src/main/kotlin/cognotik/actions/generate/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/intellij/src/main/kotlin/cognotik/actions/generate/README.md'>intellij/src/main/kotlin/cognotik/actions/generate/README.md</a> Updated
