package cognotik.actions.agent

import com.simiacryptus.cognotik.actors.SimpleActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.AddApplyFileDiffLinks
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.chat.ChatClientInterface
import com.simiacryptus.jopenai.chat.model.chatModelType
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import org.slf4j.LoggerFactory
import com.simiacryptus.cognotik.input.getReader
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CustomFileSetPatchServer(
    val config: CustomFileSetPatchAction.Settings,
    val api: ChatClientInterface,
    val autoApply: Boolean,
    val outputMode: CustomFileSetPatchAction.OutputMode
) : ApplicationServer(
    applicationName = "Custom File Set Patch",
    path = "/customFileSetPatch",
    showMenubar = false,
) {
    companion object {
        private val log = LoggerFactory.getLogger(CustomFileSetPatchServer::class.java)
        private const val DEFAULT_CONCURRENCY = 4
        private const val BROWSER_OPEN_DELAY_MS = 500L
        private const val TASK_TIMEOUT_MINUTES = 30L
        private const val MAX_FILE_SIZE_MB = 10
        private const val MAX_CONTEXT_LENGTH = 100_000
    }

    private lateinit var _root: Path

    override val inputCnt = 0
    override val stickyInput = true

    private val mainActor: SimpleActor
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

                CustomFileSetPatchAction.OutputMode.GENERATE_DOCUMENTATION -> """
                    You are a helpful AI that helps people with documentation.
                    You will be creating documentation for code files based on the provided instruction.
                    Please analyze the code and create comprehensive documentation according to the given requirements.
                    Response should be in markdown format with clear sections and explanations.
                """.trimIndent()

                CustomFileSetPatchAction.OutputMode.GENERATE_EXTRACTS -> """
                    You are a helpful AI that helps people extract and transform information from code.
                    You will be analyzing code files and extracting/transforming information based on the provided instruction.
                    Please process the code according to the given requirements and provide the requested output.
                    Response should be formatted according to the specific transformation requested.
                """.trimIndent()
            }

            return SimpleActor(
                prompt = prompt,
                model = AppSettingsState.instance.smartModel.chatModelType(),
                temperature = AppSettingsState.instance.temperature,
            )
        }

    override fun newSession(user: User?, session: Session): SocketManager {
        val socketManager = super.newSession(user, session)
        val ui = (socketManager as ApplicationSocketManager).applicationInterface
        _root = config.selectedDirectory ?: config.project?.basePath?.let { Path.of(it) } ?: Path.of(".")

        val task = ui.newTask(true)
        val api = api.getChildClient(task)
        val tabs = TabbedDisplay(task)
        val userMessage =
            config.settings?.transformationMessage ?: "Review and improve the code according to best practices"

        val settingsUI = CustomFileSetPatchAction.SettingsUI(
            config.project,
            selectedDirectory = _root,
        )

        config.settings?.patterns?.forEach { pattern ->
            settingsUI.patternListModel.addElement(pattern)
        }
        // Set the treatDocumentsAsText option from config
        settingsUI.treatDocumentsAsText.isSelected = config.settings?.treatDocumentsAsText ?: false

        val contextFiles = settingsUI.resolveContextFiles(_root)
        val fileSets = settingsUI.resolveFileSets(_root)
        if (fileSets.isEmpty()) {
            task.error(ui, IllegalArgumentException("No files match the specified patterns"))
            return socketManager
        }


        val contextSummary = buildContextSummary(contextFiles)

        val status: StringBuilder = task.add("Starting...<br/>")!!
        val fixedConcurrencyProcessor = FixedConcurrencyProcessor(socketManager.pool, DEFAULT_CONCURRENCY)
        val markdownContent = TreeMap<String, String>()

        val futures = fileSets.map { fileSet ->
            fixedConcurrencyProcessor.submit {
                processFileSet(
                    fileSet = fileSet,
                    contextSummary = contextSummary,
                    userMessage = userMessage,
                    ui = ui,
                    api = api,
                    tabs = tabs,
                    status = status,
                    task = task,
                    session = session,
                    markdownContent = markdownContent
                )
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
                    status.append("Error processing file set: ${e.message}<br/>")
                }
            }

            // Handle single output file for documentation/extracts
            if (outputMode != CustomFileSetPatchAction.OutputMode.EDIT_FILES && config.settings?.singleOutputFile == true) {
                writeSingleOutputFile(markdownContent, session, task)
            }

            status.append("Processing complete. ${completedFutures.size}/${futures.size} file sets processed successfully.<br/>")
            task.update()
        }

        return socketManager
    }

    private fun buildContextSummary(contextFiles: List<Path>): String {
        return if (contextFiles.isNotEmpty()) {
            val contextBuilder = StringBuilder()
            var totalLength = 0

            for (path in contextFiles) {
                val fileContent = try {
                    val file = path.toFile()
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
                    # Context File: ${_root.relativize(path)}
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
            }
            contextBuilder.toString()
        } else ""
    }

    private fun processFileSet(
        fileSet: CustomFileSetPatchAction.FileSet,
        contextSummary: String,
        userMessage: String,
        ui: ApplicationInterface,
        api: ChatClientInterface,
        tabs: TabbedDisplay,
        status: StringBuilder,
        task: SessionTask,
        session: Session,
        markdownContent: TreeMap<String, String>
    ) {
        try {
            status.append("Processing ${fileSet.name}...<br/>")
            task.update()
            val fileSetContent = buildFileSetContent(fileSet)
            val fullContent = if (contextSummary.isNotEmpty()) {
                "$contextSummary\n\n$fileSetContent"
            } else {
                fileSetContent
            }
            val fileTask = ui.newTask(false).apply {
                tabs[fileSet.name] = placeholder
            }
            val toInput = { it: String -> listOf(fullContent, it) }
            when {
                outputMode == CustomFileSetPatchAction.OutputMode.EDIT_FILES && autoApply -> {
                    handleAutoApplyMode(fileSet, userMessage, api, fileTask, ui, session, toInput)
                }

                outputMode != CustomFileSetPatchAction.OutputMode.EDIT_FILES -> {
                    handleGenerationMode(fileSet, userMessage, api, fileTask, session, markdownContent, toInput)
                }

                else -> {
                    handleInteractiveMode(fileSet, userMessage, api, fileTask, ui, session, toInput)
                }
            }
            status.append("Completed processing ${fileSet.name}<br/>")
            task.update()
        } catch (e: Exception) {
            log.warn("Error processing ${fileSet.name}", e)
            task.error(ui, e)
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
                    # File: ${_root.relativize(path)}
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
            when {
                file.name.endsWith(".pdf", ignoreCase = true) ||
                file.name.endsWith(".html", ignoreCase = true) ||
                file.name.endsWith(".htm", ignoreCase = true) -> {
                    file.getReader().use { reader ->
                        reader.getText()
                    }
                }
                else -> file.readText(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            log.error("Error reading file content: ${file.absolutePath}", e)
            "Error reading file: ${e.message}"
        }
    }

    private fun handleAutoApplyMode(
        fileSet: CustomFileSetPatchAction.FileSet,
        userMessage: String,
        api: ChatClientInterface,
        fileTask: SessionTask,
        ui: ApplicationInterface,
        session: Session,
        toInput: (String) -> List<String>
    ) {
        val design = mainActor.answer(toInput(userMessage), api = api).toContentList().firstOrNull()?.text ?: ""
        if (design.isNotBlank()) {
            fileTask.add(
                AddApplyFileDiffLinks.instrumentFileDiffs(
                    self = ui.socketManager!!,
                    root = _root,
                    response = design,
                    handle = { newCodeMap ->
                        newCodeMap.forEach { (path, _) ->
                            fileTask.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                        }
                    },
                    ui = ui,
                    api = api as API,
                    shouldAutoApply = { autoApply },
                    model = AppSettingsState.instance.fastModel.chatModelType(),
                    defaultFile = fileSet.files.firstOrNull()?.let { _root.relativize(it).toString() } ?: ""
                ).renderMarkdown
            )
        } else {
            fileTask.complete("No changes suggested.")
        }
    }

    private fun handleGenerationMode(
        fileSet: CustomFileSetPatchAction.FileSet,
        userMessage: String,
        api: ChatClientInterface,
        fileTask: SessionTask,
        session: Session,
        markdownContent: TreeMap<String, String>,
        toInput: (String) -> List<String>
    ) {
        val result = mainActor.answer(toInput(userMessage), api = api).toContentList().firstOrNull()?.text ?: ""
        if (config.settings?.singleOutputFile == true) {
            synchronized(markdownContent) {
                markdownContent[fileSet.name] = result
            }
        } else {
            val outputDir = _root.resolve(config.settings?.outputDirectory ?: "output")
            Files.createDirectories(outputDir)
            val outputFile = outputDir.resolve("${fileSet.name}.${getFileExtension()}")
            Files.write(
                outputFile,
                result.toByteArray(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            )
            fileTask.complete(
                "<a href='fileIndex/$session/${_root.relativize(outputFile)}'>Generated: ${
                    _root.relativize(
                        outputFile
                    )
                }</a>"
            )
        }
    }

    private fun handleInteractiveMode(
        fileSet: CustomFileSetPatchAction.FileSet,
        userMessage: String,
        api: ChatClientInterface,
        fileTask: SessionTask,
        ui: ApplicationInterface,
        session: Session,
        toInput: (String) -> List<String>
    ) {
        Discussable(
            task = fileTask,
            userMessage = { userMessage },
            heading = renderMarkdown(userMessage),
            initialResponse = {
                mainActor.answer(toInput(it), api = api)
            },
            outputFn = { design: String ->
                formatOutput(design, ui, session, fileSet, fileTask, api)
            },
            ui = ui,
            reviseResponse = { userMessages ->
                mainActor.respond(
                    messages = userMessages.map {
                        ApiModel.ChatMessage(
                            it.second,
                            it.first.toContentList()
                        )
                    }.toTypedArray(),
                    input = toInput(userMessage),
                    api = api
                )
            },
            atomicRef = AtomicReference(),
            semaphore = Semaphore(0),
        ).call()
    }

    private fun formatOutput(
        design: String,
        ui: ApplicationInterface,
        session: Session,
        fileSet: CustomFileSetPatchAction.FileSet,
        fileTask: SessionTask,
        api: ChatClientInterface
    ): String {
        return when (outputMode) {
            CustomFileSetPatchAction.OutputMode.EDIT_FILES -> {
                """<div>${
                    renderMarkdown(design) {
                        AddApplyFileDiffLinks.instrumentFileDiffs(
                            self = ui.socketManager!!,
                            root = _root,
                            response = design,
                            handle = { newCodeMap ->
                                newCodeMap.forEach { (path, _) ->
                                    fileTask.complete("<a href='${"fileIndex/$session/$path"}'>$path</a> Updated")
                                }
                            },
                            ui = ui,
                            api = api as API,
                            shouldAutoApply = { false },
                            model = AppSettingsState.instance.fastModel.chatModelType(),
                            defaultFile = fileSet.files.firstOrNull()?.let { _root.relativize(it).toString() } ?: ""
                        )
                    }
                }</div>"""
            }

            else -> """<div>${renderMarkdown(design)}</div>"""
        }
    }

    private fun writeSingleOutputFile(
        markdownContent: TreeMap<String, String>,
        session: Session,
        task: SessionTask
    ) {
        val outputDir = _root.resolve(config.settings?.outputDirectory ?: "output")
        Files.createDirectories(outputDir)
        val sortedContent = markdownContent.entries.joinToString("\n\n") { (name, content) ->
            "# $name\n\n$content"
        }
        val outputFile = outputDir.resolve(config.settings?.outputFilename ?: "output.${getFileExtension()}")
        Files.write(
            outputFile,
            sortedContent.toByteArray(),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )
        task.add("<a href='fileIndex/$session/${_root.relativize(outputFile)}'>Generated: ${_root.relativize(outputFile)}</a>")
    }

    private fun getFileExtension(): String {
        return when (outputMode) {
            CustomFileSetPatchAction.OutputMode.GENERATE_DOCUMENTATION -> "md"
            CustomFileSetPatchAction.OutputMode.GENERATE_EXTRACTS -> "txt"
            else -> "md"
        }
    }
}