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