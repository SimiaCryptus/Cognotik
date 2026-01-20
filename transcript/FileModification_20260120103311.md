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

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/CmdPatchApp.kt

```
package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.TimeUnit

class CmdPatchApp(
    root: Path,
    settings: Settings,
    val files: Array<out File>?,
    model: ChatInterface,
    parsingModel: ChatInterface,
    processor: PatchProcessor,
) : PatchApp(
    root.toFile(),
    settings,
    model,
    parsingModel = parsingModel,
    processor = processor,
) {

    companion object {
        private val log = LoggerFactory.getLogger(CmdPatchApp::class.java)

        fun truncate(output: String, kb: Int = 32): String {
            var returnVal = output
            if (returnVal.length > 1024 * 2 * kb) {
                returnVal =
                    returnVal.substring(0, 1024 * kb) + "\n\n... Output truncated ...\n\n" + returnVal.substring(
                        returnVal.length - 1024 * kb
                    )
            }
            return returnVal
        }
    }

    private fun getFiles(
        virtualFiles: Array<out File>?
    ): MutableSet<Path> {
        log.debug("Getting files from ${virtualFiles?.size ?: 0} input files")
        val codeFiles = mutableSetOf<Path>()
        virtualFiles?.forEach { file ->
            if (file.isDirectory) {
                if (file.name.startsWith(".")) return@forEach
                if (FileSelectionUtils.isGitignore(file.toPath())) return@forEach
                log.debug("Scanning directory: ${file.absolutePath}")
                codeFiles.addAll(getFiles(file.listFiles()))
            } else {
                log.debug("Adding file: ${file.absolutePath}")
                codeFiles.add((file.toPath()))
            }
        }
        log.debug("Found ${codeFiles.size} code files")
        return codeFiles
    }

    override fun codeFiles() =
        getFiles(files).filter { it.toFile().length() < 1024 * 1024 / 2 }.map { root.toPath().relativize(it) ?: it }
            .toSet()

    override fun projectSummary(): String {
        log.info("Generating project summary")
        val codeFiles = codeFiles()
        log.debug("Found ${codeFiles.size} code files for project summary")
        val str = codeFiles.asSequence().filter { root.toPath().resolve(it).toFile().exists() }.distinct().sorted()
            .joinToString("\n") { path ->
                "* $path - ${
                    root.toPath().resolve(path).toFile().length()
                } bytes".trim()
            }
        log.debug("Project summary generated (${str.length} chars)")
        return str
    }

    override fun output(
        task: SessionTask, settings: Settings, tabs: TabbedDisplay
    ): OutputResult {
        log.info("Starting command execution with ${settings.commands.size} commands")
        run {
            val model = model.getChildClient(task)
            var exitCode = 0
            for ((index, cmdSettings) in settings.commands.withIndex()) {
                try {
                    log.info("Executing command ${index + 1}/${settings.commands.size}: ${cmdSettings.executable} ${cmdSettings.arguments}")
                    val cmd = cmdSettings.executable.toString()
                    val commandList = when {
                        cmd.endsWith(".ps1", ignoreCase = true) -> {
                            // If it's a PowerShell script, build the command to run with powershell.exe
                            listOf(
                                "powershell.exe",
                                "-ExecutionPolicy", "Bypass", // Good practice to avoid execution policy issues
                                "-File", cmd
                            ) + cmdSettings.arguments.split(" ").filter(String::isNotBlank)
                        }

                        cmd.endsWith(".bat", ignoreCase = true) || cmd.endsWith(".cmd", ignoreCase = true) -> {
                            // If it's a batch script, build the command to run with cmd.exe
                            listOf(
                                "cmd.exe",
                                "/c", cmd
                            ) + cmdSettings.arguments.split(" ").filter(String::isNotBlank)
                        }

                        else -> {
                            // Original logic for other executables
                            listOf(cmd) + cmdSettings.arguments.split(" ").filter(String::isNotBlank)
                        }
                    }

                    val processBuilder = ProcessBuilder(commandList).directory(cmdSettings.workingDirectory)
                    processBuilder.environment().putAll(System.getenv())
                    val cmdString = processBuilder.command().joinToString(" ")
                    log.debug("Full command string: $cmdString")
                    log.debug("Working directory: {}", cmdSettings.workingDirectory)
                    val task = task.ui.newTask(false).apply { tabs[cmdString] = placeholder }
                    task.add("Working Directory: ${cmdSettings.workingDirectory}")
                    task.add("Command: $cmdString")
                    task.add("Model: $model / $parsingModel")
                    val process = processBuilder.start()
                    task.add("Started at: ${Instant.now()}")
                    val cancelButton = task.add(task.hrefLink("Stop") {
                        log.info("Process manually stopped by user")
                        process.destroy()
                    })
                    val taskOutput = task.add("")
                    val buffer = StringBuilder()
                    fun addOutput(taskOutput: StringBuilder?, task: SessionTask) {
                        synchronized(task) {
                            log.debug("Updating output display (buffer size: ${buffer.length})")
                            val extraInfo =
                                "[Verbose Info] - Updated at: ${Instant.now()} | Buffer size: ${buffer.length} chars"
                            taskOutput?.set(
                              "```\n${truncate(buffer.toString()).indent("  ")}\n\n${extraInfo}\n```".renderMarkdown(
                                true
                              )
                            )
                            task.update()
                        }
                    }

                    fun readStream(stream: InputStream) {
                        var lastUpdate = 0L
                        try {
                            log.debug("Starting stream reader thread")
                            stream.bufferedReader().use { reader ->
                                while (true) {
                                    val line = reader.readLine() ?: break
                                    if (line.isBlank()) continue
                                    buffer.append(line).append("\n")
                                    if (lastUpdate + TimeUnit.SECONDS.toMillis(15) < System.currentTimeMillis()) {
                                        log.debug("Periodic output update (${buffer.length} chars)")
                                        addOutput(taskOutput, task)
                                        lastUpdate = System.currentTimeMillis()
                                    }
                                }
                            }
                        } finally {
                            log.debug("Stream reader thread completed")
                            addOutput(taskOutput, task)
                        }
                    }
                    Thread { readStream(process.errorStream) }.start()
                    Thread { readStream(process.inputStream) }.start()

                    val startTime = System.currentTimeMillis()
                    val timeoutMillis = TimeUnit.MINUTES.toMillis(5)
                    val checkIntervalSeconds = 15L
                    var processCompleted = false

                    while (System.currentTimeMillis() - startTime < timeoutMillis) {
                        if (process.waitFor(checkIntervalSeconds, TimeUnit.SECONDS)) {
                            processCompleted = true
                            break
                        }

                        // Log process status every interval
                        log.info("Process still running after ${(System.currentTimeMillis() - startTime) / 1000} seconds")
                        try {
                            val pid = process.pid()
                            log.info("Process PID: $pid, alive: ${process.isAlive}")

                            // Log memory usage if available
                            val runtime = Runtime.getRuntime()
                            log.info("JVM Memory - Total: ${runtime.totalMemory() / 1024 / 1024}MB, Free: ${runtime.freeMemory() / 1024 / 1024}MB")

                            // Add diagnostic info to the task output
                            taskOutput?.set(
                              "```\n${truncate(buffer.toString()).indent("  ")}\n\n[Process Status] - Running for ${(System.currentTimeMillis() - startTime) / 1000}s | PID: $pid | Alive: ${process.isAlive}\n```".renderMarkdown(
                                true
                              )
                            )
                            task.update()
                        } catch (e: Exception) {
                            log.warn("Failed to get process diagnostics", e)
                        }
                    }

                    if (!processCompleted) {
                        log.warn("Process timed out after 5 minutes")
                        process.destroy()
                        cancelButton?.clear()
                        throw RuntimeException("Process timed out after 5 minutes")
                    } else {
                        exitCode = process.exitValue()
                        log.info("Process completed with exit code: $exitCode")
                        cancelButton?.clear()
                        task.update()
                        if (exitCode != 0) {
                            log.info("Command failed with exit code $exitCode, returning output")
                            return OutputResult(exitCode, outputString(buffer))
                        }
                    }
                } catch (e: Throwable) {
                    task.error(e)
                    return OutputResult(1, "Error executing command: ${e.message}")
                }
            }
        }
        log.info("All commands completed successfully")
        return OutputResult(0, "All commands completed successfully")
    }

    private fun outputString(buffer: StringBuilder): String {
        log.debug("Processing output string (${buffer.length} chars)")
        var output = buffer.toString()
        output = output.replace(Regex("\\x1B\\[[0-?]*[ -/]*[@-~]"), "")
        output = truncate(output)
        log.debug("Processed output string (${output.length} chars)")
        return output
    }

    override fun searchFiles(searchStrings: List<String>) = searchStrings.flatMap { searchString ->
        log.debug("Searching for pattern: $searchString")
        FileSelectionUtils.filteredWalk(settings.workingDirectory!!)
            .filter { it.readText().contains(searchString, ignoreCase = true) }.map { it.toPath() }.toList()
    }.toSet()
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/PatchApp.kt

```
package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.concurrent.TimeUnit

abstract class PatchApp(
    override val root: File,
    protected val settings: Settings,
    val model: ChatInterface,
    val parsingModel: ChatInterface,
    private val promptPrefix: String = """The following command was run and produced an error:""",
    val processor: PatchProcessor,
) : ApplicationServer(
    applicationName = "Magic Code Fixer",
    path = "/fixCmd",
    showMenubar = false,
) {

    data class OutputResult(
        val exitCode: Int, val output: String, val errors: ParsedErrors? = null
    )

    companion object {
        private val log = LoggerFactory.getLogger(PatchApp::class.java)
        const val tripleTilde = "`" + "``"

    }

    /**
     * Helper to render command output using a consistent markdown format.
     */
    /**
     * Helper to clean file paths.
     * It returns only the part of the string before the first space,
     * treating anything after the space as a note or comment.
     */
    private fun cleanFilePath(filePath: String): String = filePath.substringBefore(" ").trim()

    private var lastParsedErrors: ParsedErrors? = null

    private val previousParsedErrorsRecords = mutableListOf<ParsedErrorRecord>()

    data class FixAttempt(
        val error: String,
        val patch: String,
        val timestamp: Long = System.currentTimeMillis(),
        val iteration: Int = 0
    )

    private val fixHistory = mutableMapOf<String, MutableList<FixAttempt>>()

    abstract fun codeFiles(): Set<Path>

    open fun codeSummary(paths: List<Path>, error: ParsedError): String {
        log.debug("Generating code summary for ${paths.size} paths related to error: ${error.message}")
        val a = paths.map { it.toFile().findAbsolute(settings.workingDirectory, root, File(".")) }
        val b = a.filter { it.exists() && !it.isDirectory && it.length() < (256 * 1024) }
        log.debug("Filtered to ${b.size} valid files for summary")
        return b.joinToString("\n\n") { path ->
            val errorLocations = error.locations?.filter { loc ->
                loc.file?.endsWith(path.name) == true || loc.file?.let { path.absolutePath.endsWith(it) } == true
            }?.flatMap {
                it.lines ?: emptyList()
            }?.toSet() ?: emptySet()
            log.debug("Found {} error locations in file: {}", errorLocations.size, path)

            try {
                val fileContent = path.readText()
                val lines = fileContent.lines()
                log.debug("Read {} lines from file: {}", lines.size, path)
                val annotatedLines = lines.mapIndexed { lineIndex, line ->
                    val lineNumber = lineIndex + 1
                    val linePrefix = if (settings.includeLineNumbers) "$lineNumber: " else ""
                    val index = lineIndex + 1
                    if (errorLocations.contains(index)) {
                        linePrefix + line + "/* Error: ${error.message ?: "?"} */"
                    } else {
                        linePrefix + line
                    }
                }
                val gitDiff = if (settings.includeGitDiffs) {
                    try {
                        log.debug("Attempting to get git diff for: {}", path)

                        val relativePath = path.toString()
                        val process = ProcessBuilder("git", "diff", "HEAD", "--", relativePath)
                            .directory(settings.workingDirectory)
                            .start()
                        val diffOutput = process.inputStream.bufferedReader().use {
                            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                                process.destroy()
                                log.warn("Git diff timed out for $path")
                            }
                            it.readText()
                        }
                        log.debug(
                            "Git diff for {}: {}",
                            path,
                            if (diffOutput.isBlank()) "No changes" else "${diffOutput.lines().size} lines of diff"
                        )
                        if (diffOutput.isNotBlank()) "\nGit Diff:\n```diff\n$diffOutput\n```" else ""
                    } catch (e: Exception) {
                        log.info("Failed to get git diff for $path: ${e.message}")
                        ""
                    }
                } else ""
                "# ${path}\n```${path.toString().split('.').lastOrNull()}\n${
                    annotatedLines.joinToString("\n")
                }\n```$gitDiff"
            } catch (e: Exception) {
                log.warn("Error reading file", e)
                "Error reading file `${path}` - ${e.message}"
            }
        }
    }

    lateinit var updateStatus: (String) -> Unit

    abstract fun output(
        task: SessionTask,
        settings: Settings,
        tabs: TabbedDisplay = TabbedDisplay(task)
    ): OutputResult

    abstract fun searchFiles(searchStrings: List<String>): Set<Path>
    override val inputCnt = 1
    override val stickyInput = false
    override fun newSession(user: User, session: Session): SocketManager {
        val ui = super.newSession(user, session)
        log.info("Creating new session for user: ${user?.id ?: "anonymous"}")
        var retries: Int = -1
        val task = ui.newTask()
        var retryOnOffButton: StringBuilder? = null
        val disableButton = task.hrefLink("Disable Auto-Retry") {
            log.info("Auto-retry disabled by user")
            retries = 0
            retryOnOffButton?.clear()
            task.update()
        }
        if (settings.autoFix && settings.maxRetries > 0) {
            log.info("Auto-fix enabled with max retries: ${settings.maxRetries}")
            retryOnOffButton = task.add(disableButton)
        }
        val currentStatus = task.add("Status: Initializing...")!!
        updateStatus = { message: String ->
            log.info("Status update: $message")
            currentStatus.set("Status: $message")
            task.update()
        }

        fun runIteration() {
            if (retries < 0) {
                retries = when {
                    settings.autoFix -> settings.maxRetries
                    else -> 0
                }
                log.debug("Initialized retries to $retries")
            }
            val currentIteration = settings.maxRetries - retries + 1
            val newTask =
                task.linkedTask("Run Command${if (retries < settings.maxRetries) " (Retry ${settings.maxRetries - retries}/$settings.maxRetries)" else ""}")
            Thread {
                log.info("Starting run thread")
                updateStatus("Running command (Iteration $currentIteration)...")
                val model = model.getChildClient(task)
                val result = run(newTask, model, currentIteration)
                log.info("Run completed with exit code: ${result.exitCode}")
                if (result.exitCode != 0) {
                    if (retries > 0) {
                        val errorStats = previousParsedErrorsRecords
                            .flatMap { record ->
                                record.errors?.errors?.map { it.message to record.iteration } ?: emptyList()
                            }
                            .groupBy({ it.first }, { it.second })
                            .map { (msg, iters) -> "'${msg?.take(20)}...' (${iters.distinct().size} iters)" }
                            .joinToString(", ")
                        log.info("Triggering retry (${retries} remaining). Active errors: $errorStats")
                        updateStatus("Command failed. Retrying (${retries} remaining)... Active Errors: $errorStats")
                        retries -= 1
                        runIteration()
                    } else {
                        updateStatus("Command failed. No retries remaining.")
                    }
                } else {
                    updateStatus("Command successful.")
                }
            }.start()
        }

        task.add(task.hrefLink("Run Again", classname = "href-link play-button") {
            if (retries <= 0 && settings.autoFix) {
                retries = settings.maxRetries
            }
            runIteration()
        })

        runIteration()
        log.info("Session setup complete")
        return ui
    }

    abstract fun projectSummary(): String

    private fun prunePaths(paths: List<Path>, maxSize: Int): List<Path> {
        log.debug("Pruning ${paths.size} paths to fit within $maxSize bytes")
        val sortedPaths = paths.sortedByDescending { it.toFile().length() }
        var totalSize = 0
        val prunedPaths = mutableListOf<Path>()
        for (path in sortedPaths) {
            val fileSize = path.toFile().length().toInt()
            if (totalSize + fileSize > maxSize) break
            prunedPaths.add(path)
            totalSize += fileSize
        }
        log.debug("Pruned to ${prunedPaths.size} paths with total size $totalSize bytes")
        return prunedPaths
    }

    data class ParsedErrors(
        val errors: List<ParsedError>? = null
    )

    data class ParsedErrorRecord(
        val errors: ParsedErrors? = null, val timestamp: Long = System.currentTimeMillis(), val iteration: Int = 0
    )

    data class SearchQuery(
        @Description("The search pattern to be used in file content matching") val pattern: String? = null,
        @Description("A glob expression to filter which files to run the search against") val fileGlob: String? = null
    )

    data class CodeLocation(
        @Description("The file path") val file: String? = null,
        @Description("The line number in the file") val lines: List<Int>? = null,
    )

    data class ResearchNotes(
        @Description("Files identified as needing modification") val fixFiles: List<String>? = null,
        @Description("Files that may be helpful for understanding the issue") val relatedFiles: List<String>? = null,
        @Description("Search queries to find relevant code") val searchQueries: List<SearchQuery>? = null
    )

    data class ParsedError(
        @Description("The error message") val message: String? = null,
        @Description("Summarize output to distill details related to the error message") val details: String? = null,
        @Description("Problem severity (higher numbers indicate more fatal issues)") val severity: Int? = 0,
        @Description("Problem complexity (higher numbers indicate more difficult issues)") val complexity: Int? = 0,
        @Description("Whether this is a warning rather than an error") val isWarning: Boolean? = false,
        @Description("Locations in code where the error occurs") val locations: List<CodeLocation>? = null,
        @Description("Research notes about files and search patterns") val research: ResearchNotes? = null
    )

    data class Settings(
        var commands: List<CommandSettings> = listOf(),
        val autoFix: Boolean = false,
        val maxRetries: Int = 3,
        val ignoreWarnings: Boolean = true,
        val includeGitDiffs: Boolean = false,
        val includeLineNumbers: Boolean = false,
    ) {

        var workingDirectory: File?
            get() = commands.firstOrNull()?.workingDirectory
            set(value) {
                commands.forEach { it.workingDirectory = value }
            }
        var additionalInstructions: String
            get() = commands.firstOrNull()?.additionalInstructions ?: ""
            set(value) {
                commands.forEach { it.additionalInstructions = value }
            }
    }

    data class CommandSettings(
        var executable: File,
        var arguments: String = "",
        var workingDirectory: File? = null,
        var additionalInstructions: String = "",
    )

    fun run(
        task: SessionTask,
        model: ChatInterface,
        iteration: Int = 0
    ): OutputResult {
        log.info("Starting run with settings: ${JsonUtil.toJson(settings)}")

        val tabs = TabbedDisplay(task)

        val outputResult = output(task, settings, tabs)
        log.info("Command execution completed with exit code: ${outputResult.exitCode}")
        if (outputResult.exitCode == 0) {
            log.info("Command executed successfully, no fixes needed")
            task.complete("<div>\n<div><b>Command executed successfully</b></div>\n</div>")
            return outputResult
        }

        val fixTask = task.ui.newTask(false).apply { tabs["Fix"] = placeholder }
        try {
            log.info("Creating child API client for fix task")
            val plan = if (outputResult.errors == null) {
                log.info("No pre-parsed errors, parsing errors from output")
                parsedErrorsParsedResponse(settings = settings, output = outputResult, model = model)
            } else {
                log.info("Using pre-parsed errors")
                object : ParsedResponse<ParsedErrors>(
                    ParsedErrors::class.java
                ) {
                    override val text: String = ""
                    override val obj: ParsedErrors = outputResult.errors
                }
            }

            val parsedErrors: ParsedErrors = plan.obj
            log.info("Parsed ${parsedErrors.errors?.size ?: 0} errors from output")
            lastParsedErrors = parsedErrors
            val progressHeader = fixTask.header("Processing tasks...", 3)
            fixTask.add(
                AgentPatterns.displayMapInTabs(
                    mapOf(
                      "Text" to plan.text.renderMarkdown(true),
                      "JSON" to "${tripleTilde}json\n${JsonUtil.toJson(parsedErrors)}\n$tripleTilde".renderMarkdown(true),
                      "Process Details" to "Exit Code: ${outputResult.exitCode}\nCommand Output:\n$tripleTilde\n${outputResult.output}\n$tripleTilde".renderMarkdown(
                        true
                      )
                    ).filter { it.value.isNotBlank() },
                )
            )
            previousParsedErrorsRecords.add(ParsedErrorRecord(parsedErrors, iteration = iteration))
            log.info("Starting to fix all errors")
            fixAllErrors(
                task = fixTask,
                plan = plan,
                settings = settings,
                progressHeader = progressHeader,
                model = model,
                iteration = iteration
            )
        } catch (e: Exception) {
            log.error("Error during fix process", e)
            fixTask.error(e)
        }
        return outputResult
    }

    private fun recentErrors() =
        previousParsedErrorsRecords.flatMap { it.errors?.errors ?: emptySet() }.groupBy { it.message }.let { errors ->
            log.debug("Processing ${errors.size} recent error groups")
            ParsedErrors(errors.map { (_, errors) ->
                errors.maxByOrNull { it.severity ?: 0 }!!
            })
        }

    private fun fixAllErrors(
        task: SessionTask,
        plan: ParsedResponse<ParsedErrors>,
        settings: Settings,
        progressHeader: StringBuilder?,
        model: ChatInterface,
        iteration: Int
    ) {
        log.info("Starting fixAllErrors")
        val errors = plan.obj.errors ?: emptyList()
        val hasErrors = errors.any { it.isWarning != true }
        log.info("Found ${errors.size} errors, hasErrors=$hasErrors")
        val filteredErrors = errors.filter {
            if (hasErrors) {
                !settings.ignoreWarnings || (it.isWarning != true)
            } else {
                true
            }
        }
        log.info("After filtering: ${filteredErrors.size} errors to fix")
        filteredErrors.groupBy { it.message }
            .map { (msg, errors) ->
                log.info("Processing error group: $msg with ${errors.size} instances")
                task.ui.pool.submit {
                    val subSession = task.linkedTask("Fix: ${msg?.take(50) ?: "Error"}...")
                    val statusBuffer = subSession.add("Status: Initializing...")!!
                    errors.forEach { error ->
                        log.info("Processing individual error: ${error.message}")
                        statusBuffer.set("Status: Analyzing error details...")
                        subSession.update()

                        subSession.header("Processing error: $msg", 3)
                        subSession.add(
                            renderMarkdown(
                                "```json\n${JsonUtil.toJson(error)}\n```",
                                tabs = false,
                                ui = subSession.ui
                            )
                        )
                        subSession.verbose(
                            renderMarkdown(
                                "[Extra Details] Error processed at: ${Instant.now()}",
                                tabs = false,
                                ui = subSession.ui
                            )
                        )
                        statusBuffer.set("Status: Searching for relevant files...")
                        subSession.update()

                        val searchResults = error.research?.searchQueries?.flatMap { query ->
                            log.debug("Executing search query: pattern=${query.pattern}, glob=${query.fileGlob}")
                            filteredWalk(settings.workingDirectory ?: root).filter { file ->
                                FileSystems.getDefault().getPathMatcher("glob:" + query.fileGlob).matches(file.toPath())
                            }.filter {
                                it.isFile &&
                                        it.length() < (1 * 1024 * 1024) &&
                                        query.pattern?.isBlank() == false &&
                                        it.readText().contains(query.pattern ?: "", ignoreCase = true)
                            }.map { it.toPath() }.toList()
                        }?.toSet() ?: emptySet()
                        log.info("Search found ${searchResults.size} relevant files")
                        if (searchResults.isNotEmpty()) {
                            subSession.verbose(
                                renderMarkdown(
                                    "Search results:\n\n${searchResults.joinToString("\n") { "* `$it`" }}",
                                    tabs = false,
                                    ui = subSession.ui
                                )
                            )
                        }
                        statusBuffer.set("Status: Generating fix...")
                        subSession.update()
                        fix(
                            error,
                            searchResults.toList().map { it.toFile().absolutePath },
                            settings.autoFix,
                            subSession,
                            model,
                            iteration
                        )
                        statusBuffer.set("Status: Complete")
                        subSession.update()
                    }
                }
            }.toTypedArray().onEach { it.get() }
        log.info("All error fixes have been submitted")
        progressHeader?.set("Finished processing tasks")
        task.append("", false)
    }

    private fun parsedErrorsParsedResponse(
        settings: Settings, output: OutputResult, model: ChatInterface
    ): ParsedResponse<ParsedErrors> {
        log.info("Parsing errors from command output")
        val plan = ParsedAgent(
            resultClass = ParsedErrors::class.java,
            exampleInstance = if (previousParsedErrorsRecords.isEmpty()) ParsedErrors(
                listOf(
                    ParsedError(
                        message = "Error message",
                        details = "Line 123: error message\n\nThis is a detailed description of the error, mainly copied from the output",
                        isWarning = false,
                        locations = listOf(
                            CodeLocation(
                                file = "src/main/java/com/example/Example.java",
                                lines = listOf(123),
                            )
                        ),
                        research = ResearchNotes(
                            fixFiles = listOf("src/main/java/com/example/Example.java"),
                            relatedFiles = listOf("src/main/java/com/example/Example.java"),
                            searchQueries = listOf(
                                SearchQuery(
                                    pattern = "error message",
                                    fileGlob = "**/*.java"
                                )
                            )
                        )
                    )
                )
            ) else recentErrors(),
            model = model,
            parsingChatter = parsingModel,
            prompt = ("""
          You are a helpful AI that helps people with coding.

          You will be answering questions about the following project:

          Project Root: """.trimIndent() + (settings.workingDirectory?.absolutePath ?: "") + """

          Files:
          """.trimIndent() + projectSummary() + """

          Given the response of a build/test process, identify one or more distinct errors.
          For each error:
             1) predict the files that need to be fixed
             2) predict related files that may be needed to debug the issue
             3) specify a search string to find relevant files - be as specific as possible
             4) provide a listing of the error locations in the code, including filename and line and column numbers
             5) provide a severity and complexity rating from 1-10
             6) summarize the output to distill details related to the error message
          """.trimIndent() + (if (settings.additionalInstructions.isNotBlank()) "Additional Instructions:\n  ${settings.additionalInstructions}\n" else ""))
        ).answer(
            listOf(
                "$promptPrefix\n\n${tripleTilde}\n${output.output}\n${tripleTilde}"
            ),
        )
        log.info("Error parsing completed")
        return plan
    }

    private fun fix(
        error: ParsedError,
        additionalFiles: List<String>? = null,
        autoFix: Boolean,
        task: SessionTask,
        model: ChatInterface,
        iteration: Int,
    ) {
        log.info("Starting fix for error: ${error.message}")
        val paths = ((error.research?.fixFiles ?: emptyList()) +
                (error.research?.relatedFiles ?: emptyList()) +
                (error.locations?.map { it.file } ?: emptyList()) +
                (additionalFiles ?: emptyList())).mapNotNull { filePath ->
            try {
                log.debug("Processing file path: $filePath")


                val normalizedRoot = root.absolutePath.replace(File.separatorChar, '/')

                val cleanPath = filePath?.let { cleanFilePath(it) } ?: return@mapNotNull null
                var relativePath =
                    if (cleanPath.contains(normalizedRoot)) cleanPath.replaceFirst(normalizedRoot, "") else cleanPath
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.drop(1)
                }
                log.debug("Normalized path: $relativePath")
                File(relativePath).toPath()
            } catch (e: Throwable) {
                log.warn("Error: root=${root}", e)
                null
            }
        }
        log.info("Collected ${paths.size} relevant file paths")
        val prunedPaths = prunePaths(paths, 50 * 1024)
        log.info("Pruned to ${prunedPaths.size} paths")
        val (previousErrorOccurances, others) = previousParsedErrorsRecords.partition { it.errors?.errors?.any { it.message == error.message } == true }
        log.info("Found ${previousErrorOccurances.size} previous occurrences of this error")

        val summary = codeSummary(prunedPaths.distinct(), error)
        log.info("Generated code summary (${summary.length} chars)")
        val historyContext = prunedPaths.mapNotNull { path ->
            val history = fixHistory[path.toString()]
            if (!history.isNullOrEmpty()) {
                val (related, unrelated) = history.partition { it.error == error.message }
                val sb = StringBuilder()
                if (related.isNotEmpty()) {
                    sb.append("### ⚠️ PRIOR FAILED FIXES for `$path` (Error: ${error.message})\n")
                    sb.append("The following patches were attempted in previous iterations but the error persists. DO NOT generate the same code again.\n")
                    sb.append(related.joinToString("\n") {
                        "- [Iter ${it.iteration}] Patch:\n```diff\n${it.patch}\n```"
                    })
                    sb.append("\n\n")
                }
                if (unrelated.isNotEmpty()) {
                    sb.append("### Other History for `$path`\n")
                    sb.append(unrelated.joinToString("\n") {
                        "- [Iter ${it.iteration}] ${it.error}"
                    })
                    sb.append("\n\nPrevious patches applied to this file:\n```diff\n" + unrelated.joinToString("\n\n") { it.patch } + "\n```")
                }
                sb.toString()
            } else null
        }.joinToString("\n\n")

        val fixResponse = ChatAgent(
            prompt = """
        You are a helpful AI that helps people with coding.
        You will be answering questions about the following code:
        $summary
        ${processor.patchFormatPrompt}
        If needed, new files can be created by using code blocks labeled with the filename in the same manner.
        Note: Ignore any "/* Error: ... */" comments when generating patches - these are just for reference.
        """.trimIndent(),
            model = model,
        ).answer(
            listOf(
                "$promptPrefix\n\nFocus on and Fix the Error:\n  ${error.message ?: ""}\n" +
                        (if (error.details?.isNotBlank() == true) "Details:\n  ${error.details}\n" else "") +
                        (if (settings.additionalInstructions.isNotBlank()) "Additional Instructions:\n  ${settings.additionalInstructions}\n" else "") +
                        (if (historyContext.isNotBlank()) "\n\nPrevious Debugging Attempts (Learn from these):\n$historyContext" else "")
            ),

            ).lines().joinToString("\n") {
            it.replace(Regex("""/\* Error.*?\*/"""), "")
        }
        log.info("Received fix response (${fixResponse.length} chars)")
        // Record history
        prunedPaths.forEach { path ->
            fixHistory.getOrPut(path.toString()) { mutableListOf() }.add(
                FixAttempt(error.message ?: "Unknown", fixResponse, iteration = iteration)
            )
        }

        val markdown = AddApplyFileDiffLinks.instrumentFileDiffs(
            self = task.ui,
            root = root.toPath(),
            response = fixResponse,
            shouldAutoApply = { path ->
                if (autoFix) {
                    log.info("Auto-applying fix to: $path")
                    true
                } else {
                    log.debug("Not auto-applying fix to: {} (autoFix={})", path, autoFix)
                    false
                }
            },
            model = model,
            processor = processor
        )
        log.info("Instrumented file diffs with apply links")
        task.verbose(
            renderMarkdown("Previous occurrences of this error:\n\n" + previousErrorOccurances.joinToString("\n") {
                "* " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(it.timestamp)
            } + "\nNon-matching instances: ${others.size}", tabs = false, ui = task.ui))
        task.verbose(
            renderMarkdown(
                "Files identified for modification:\n\n${
                    prunedPaths.distinct().joinToString("\n") {
                        "* `$it` (${
                            root.toPath().resolve(it).toFile().length()
                        } bytes)"
                    }
                }", tabs = false, ui = task.ui))
        log.info("Fix process completed for error: ${error.message}")
        task.complete("<div>${markdown.renderMarkdown()}</div>")
    }

}

fun File.findAbsolute(vararg files: File?): File {
    for (file in files) {
        val potential = File(file, this.path)
        if (potential.exists()) {
            return potential
        }
    }
    return this
}

```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/SessionProxyApp.kt

```
package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.session.SocketManager

class SessionProxyServer : ApplicationServer(
    applicationName = "AI Coding Assistant",
    path = "/",
    showMenubar = false,
) {
    override val inputCnt = 0
    override val stickyInput = false
    override fun appInfo(session: Session): Map<String, Any> {
        val chat = chats[session]
        val appInfoData = appInfoMap[session]
        return (appInfoData ?: chat?.let { chatServer ->
            AppInfoData(
                applicationName = chatServer.applicationName,
                inputCnt = chatServer.inputCnt,
                stickyInput = chatServer.stickyInput,
                loadImages = false,
                showMenubar = showMenubar,
            )
        } ?: AppInfoData(
            applicationName = "AI Coding Assistant",
            inputCnt = 0,
            stickyInput = false,
            loadImages = false,
            showMenubar = showMenubar,
        )).toMap()
    }

    override fun newSession(user: User, session: Session) =
        agents[session] ?: chats[session]?.newSession(user, session)
        ?: throw IllegalStateException("No agent found for session $session")

    companion object {
        val metadataStorage by lazy { ApplicationServices.fileApplicationServices().metadataStorageFactory }
        val agents = mutableMapOf<Session, SocketManager>()
        val chats = mutableMapOf<Session, ChatServer>()
    }
}

```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/SingleTaskApp.kt

```
package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * An application that executes a single pre-configured task without planning.
 * The task configuration is provided directly by the user through a dialog.
 */
abstract class SingleTaskApp(
    path: String,
    applicationName: String = "Single Task App",
    showMenubar: Boolean = false,
    private val taskType: TaskType<*, *>,
    private val taskConfig: List<TaskExecutionConfig>,
    val instanceFn: ((ApiChatModel) -> ChatInterface)?
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
    showMenubar = showMenubar,
    root = dataStorageRoot,
) {
    private val log = LoggerFactory.getLogger(SingleTaskApp::class.java)

    override val stickyInput = false
    override val inputCnt: Int = 1

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T =
        OrchestrationConfig(sessionId = session.sessionId, null) as T

    abstract fun instance(model: ApiChatModel): ChatInterface

    override fun newSession(
        user: User, session: Session
    ): SocketManager {
        val socketManager = super.newSession(user, session)
        startSession(session, user, socketManager)
        return socketManager
    }

    protected fun startSession(
        session: Session,
        user: User,
        socketManager: SocketManager
    ) {
        val orchestrationConfig = getOrchestrationConfig(session, user)
        if (null != instanceFn) OrchestrationConfig.instanceFn = instanceFn
        socketManager.newTask(cancelable = false, root = true).expandable(
            "Session Info", """
    Session ID: `${session}`
    
    Start Time: `${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}`
    
    Root: `${orchestrationConfig?.absoluteWorkingDir}`
    
    Session Location: `${dataStorage.getSessionDir(user, session).absolutePath}`
    
    Data Location: `${dataStorage.getDataDir(user, session).absolutePath}`
    
    Task Type: `${taskType.name}`
    
              """.renderMarkdown()
        )
        socketManager.pool.submit { executeTask(session, user, socketManager, orchestrationConfig) }
    }

    open fun getOrchestrationConfig(
        session: Session,
        user: User
    ): OrchestrationConfig? = getSettings(session, user, OrchestrationConfig::class.java)

    protected open fun onTaskComplete(result: String, task: SessionTask) {}
    protected open fun onTaskError(e: Throwable) {}

    protected open fun executeTask(
        session: Session, user: User = defaultUser, ui: SocketManager, settings: OrchestrationConfig?
    ) {
        try {
            val orchestrationConfig = settings?.apply {
                if(null == DataStorage.sessionPaths[session]) absoluteWorkingDir?.let { DataStorage.sessionPaths[session] = File(it) }
            } ?: throw IllegalStateException("OrchestrationConfig not found in session settings")

            taskConfig.forEach { taskConfig ->
                val task = ui.newTask(true)

                // Get the task implementation
                val taskImpl = orchestrationConfig.getImpl(
                    taskType = taskType, cfg = taskConfig
                )

                // Execute the task
                taskImpl.run(
                    agent = TaskOrchestrator(
                        user = user,
                        session = session,
                        dataStorage = ui.dataStorage!!,
                        root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                            ?: ui.dataStorage.getSessionDir(user, session).toPath() ?: File(".").toPath()
                    ),
                    messages = listOf(taskConfig.task_description ?: "Execute task"),
                    task = task,
                    resultFn = { result ->
                        task.complete(result.renderMarkdown(true))
                        onTaskComplete(result, task)
                    },
                    orchestrationConfig = orchestrationConfig
                )
            }

        } catch (e: Throwable) {
            log.error("Error executing task", e)
            ui.newTask().error(e)
            onTaskError(e)
        }
    }

    override fun userMessage(
        session: Session, user: User, userMessage: String, ui: SocketManager
    ) {
        // Single task apps don't accept user messages after initialization
        ui.newTask().error(
            IllegalStateException("This is a single-task application. User messages are not supported.")
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/StressTestApp.kt

```
package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import kotlin.random.Random

class StressTestApp(
    applicationName: String = "UI Stress Test",
    path: String = "/stressTest",
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
    showMenubar = true
) {
    var wasRun = false
    override fun userMessage(
        session: Session,
        user: User,
        userMessage: String,
        ui: SocketManager
    ) {
        if (wasRun) {
            return
        }
        wasRun = true
        val task = ui.newTask()
        task.add("# UI Stress Test".renderMarkdown(true))
        createNestedTabs(task, ui, 3)
    }

    private fun createNestedTabs(task: SessionTask, ui: SocketManager, depth: Int) {
        if (depth <= 0) {
            createComplexDiagram(task)
            createAndUpdatePlaceholders(task, ui)
            return
        }

        val tabDisplay = TabbedDisplay(task)

        (1..2).forEach { i ->
            val subTask = ui.newTask(false)
            tabDisplay["Tab $i"] = subTask.placeholder
            createNestedTabs(subTask, ui, depth - 1)
        }
        tabDisplay.update()
    }

    private fun createComplexDiagram(task: SessionTask) {
        val mermaidDiagram = """
            ```mermaid
            graph TD
                A[Start] --> B{Is it?}
                B -->|Yes| C[OK]
                C --> D[Rethink]
                D --> B
                B ---->|No| E[End]
            ```
        """.trimIndent()
        task.add("## Complex Diagram\n$mermaidDiagram".renderMarkdown(true))
    }

    private fun createAndUpdatePlaceholders(task: SessionTask, ui: SocketManager) {
        val placeholders = (1..5).map { ui.newTask(false) }

        placeholders.forEach { placeholder ->
            task.add(placeholder.placeholder)
        }

        repeat(10) { iteration ->
            placeholders.forEach { placeholder ->
                val content = "Placeholder content: Iteration $iteration, Random: ${Random.nextInt(100)}"
                placeholder.add(content.renderMarkdown(true))

            }
        }
        placeholders.forEach { it.complete() }
        task.complete()
    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/SymbolGraphService.kt

```
package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.describe.Description
import org.apache.tinkerpop.gremlin.process.traversal.TextP

import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.T
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.structure.VertexProperty
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONReader
import org.apache.tinkerpop.gremlin.structure.io.graphson.GraphSONWriter
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
@Description("Service for managing a graph-based representation of code symbols, files, and their relationships. It uses an in-memory TinkerGraph to store vertices representing Files, Symbols, Languages, Libraries, and Packages, and edges representing relationships like DEFINED_IN, REFERENCES, WRITTEN_IN, IN_LIBRARY, and IN_PACKAGE.")

class SymbolGraphService {

    private val graph = TinkerGraph.open()

    @Synchronized
    @Description("Clears the entire symbol graph, removing all vertices and edges. This resets the service to an empty state.")
    fun clear() {
        graph.traversal().V().drop().iterate()
    }

    @Synchronized
    @Description("Adds or updates a file vertex in the graph. If a vertex with the given ID exists, it is reused. Sets the file name and last modified timestamp.")
    fun addFile(id: String, name: String, lastModified: Long) {
        val v = getOrCreateVertex(id, "File")
        v.property(VertexProperty.Cardinality.single, "name", name)
        v.property(VertexProperty.Cardinality.single, "lastModified", lastModified)
    }

    @Synchronized
    @Description("Adds or updates a symbol vertex in the graph. Creates relationships to the containing file, language, library, and package based on the file path and extension. Stores symbol properties like name, location (offsets/line), visibility, modifiers, and annotations.")
    fun addSymbol(
        id: String,
        name: String,
        fileId: String,
        startOffset: Int? = null,
        endOffset: Int? = null,
        line: Int? = null,
        visibility: String? = null,
        modifiers: String? = null,
        annotations: String? = null,
        lastModified: Long? = null
    ) {
        val v = getOrCreateVertex(id, "Symbol")
        v.property(VertexProperty.Cardinality.single, "name", name)
        v.property(VertexProperty.Cardinality.single, "file", fileId)
        startOffset?.let { v.property(VertexProperty.Cardinality.single, "startOffset", it) }
        endOffset?.let { v.property(VertexProperty.Cardinality.single, "endOffset", it) }
        line?.let { v.property(VertexProperty.Cardinality.single, "line", it) }
        visibility?.let { v.property(VertexProperty.Cardinality.single, "visibility", it) }
        modifiers?.let { v.property(VertexProperty.Cardinality.single, "modifiers", it) }
        annotations?.let { v.property(VertexProperty.Cardinality.single, "annotations", it) }
        lastModified?.let { v.property(VertexProperty.Cardinality.single, "lastModified", it) }
        val filePath = fileId
        val fileV = getOrCreateVertex(fileId, "File")
        fileV.property(VertexProperty.Cardinality.single, "name", filePath.substringAfterLast('/'))
        if (!v.vertices(Direction.OUT, "DEFINED_IN").hasNext()) v.addEdge("DEFINED_IN", fileV)
        val extension = filePath.substringAfterLast('.', "").lowercase()
        val language = when (extension) {
            "kt" -> "Kotlin"
            "java" -> "Java"
            "js" -> "JavaScript"
            "py" -> "Python"
            "class" -> "Bytecode"
            else -> extension
        }
        if (language.isNotEmpty()) {
            val langV = getOrCreateVertex(language, "Language")
            langV.property(VertexProperty.Cardinality.single, "name", language)
            if (!v.vertices(Direction.OUT, "WRITTEN_IN").hasNext()) v.addEdge("WRITTEN_IN", langV)
        }
        var libraryName: String? = null
        var packageName: String? = null
        if (filePath.contains(".jar!/")) {
            val jarParts = filePath.split("!/")
            libraryName = jarParts[0].substringAfterLast('/')
            packageName = jarParts[1].substringBeforeLast('/').replace('/', '.')
        } else if (filePath.contains("/src/")) {
            val srcParts = filePath.split("/src/")
            libraryName = srcParts[0].substringAfterLast('/')
            val pathAfterSrc = srcParts[1]
            val cleanPath = pathAfterSrc
                .replaceFirst("main/kotlin/", "")
                .replaceFirst("main/java/", "")
                .replaceFirst("test/kotlin/", "")
                .replaceFirst("test/java/", "")
            if (cleanPath.contains('/')) packageName = cleanPath.substringBeforeLast('/').replace('/', '.')
        }
        if (libraryName != null) {
            val libV = getOrCreateVertex(libraryName, "Library")
            libV.property(VertexProperty.Cardinality.single, "name", libraryName)
            if (!v.vertices(Direction.OUT, "IN_LIBRARY").hasNext()) v.addEdge("IN_LIBRARY", libV)
        }
        if (packageName != null) {
            val pkgV = getOrCreateVertex(packageName, "Package")
            pkgV.property(VertexProperty.Cardinality.single, "name", packageName)
            if (!v.vertices(Direction.OUT, "IN_PACKAGE").hasNext()) v.addEdge("IN_PACKAGE", pkgV)
        }
    }

    @Synchronized
    @Description("Retrieves the last modified timestamp for a given file ID from the graph. Returns null if the file is not found or has no timestamp.")
    fun getLastModified(fileId: String): Long? {
        val iter = graph.vertices(fileId)
        if (iter.hasNext()) {
            val v = iter.next()
            val prop = v.property<Long>("lastModified")
            if (prop.isPresent) return prop.value()
        }
        return null
    }

    @Synchronized
    @Description("Returns a set of all file IDs currently present in the graph.")
    fun listFileIds(): Set<String> {
        val ids = mutableSetOf<String>()
        graph.traversal().V().hasLabel("File").forEachRemaining { ids.add(it.id() as String) }
        return ids
    }

    @Synchronized
    @Description("Removes a file and all symbols defined in it from the graph. Also removes the file vertex itself.")
    fun removeFile(id: String) {
        graph.traversal().V().has("Symbol", "file", id).drop().iterate()
        graph.traversal().V(id).drop().iterate()
    }

    @Synchronized
    @Description("Removes all outgoing 'REFERENCES' edges from symbols defined in the specified file. This is typically done before re-analyzing a file to clear stale references.")
    fun clearOutgoingReferences(fileId: String) {
        graph.traversal().V().has("Symbol", "file", fileId).outE("REFERENCES").drop().iterate()
    }

    @Synchronized
    @Description("Removes symbol vertices associated with a file that are not present in the provided set of kept symbol IDs. This cleans up symbols that no longer exist in the file after an update.")
    fun pruneRemovedSymbols(fileId: String, keptSymbolIds: Set<String>) {
        val toRemove = mutableListOf<Vertex>()
        graph.traversal().V().has("Symbol", "file", fileId).forEachRemaining { v ->
            if (v.id() !in keptSymbolIds) {
                toRemove.add(v)
            }
        }
        toRemove.forEach { it.remove() }
    }

    @Synchronized
    @Description("Adds a 'REFERENCES' edge from a source symbol to a target symbol. If the target symbol vertex does not exist, it is created with basic information (name, file).")
    fun addReference(sourceId: String, targetId: String, targetName: String, targetFile: String) {
        val sourceIter = graph.vertices(sourceId)
        if (sourceIter.hasNext()) {
            val sourceV = sourceIter.next()
            val targetV = getOrCreateVertex(targetId, "Symbol")
            if (!targetV.properties<Any>("name").hasNext()) {
                targetV.property(VertexProperty.Cardinality.single, "name", targetName)
                targetV.property(VertexProperty.Cardinality.single, "file", targetFile)
            }
            val exists = sourceV.edges(Direction.OUT, "REFERENCES").asSequence().any { it.inVertex().id() == targetId }
            if (!exists) {
                sourceV.addEdge("REFERENCES", targetV)
            }
        }
    }

    @Synchronized
    @Description("Retrieves a Symbol object wrapper for the vertex with the specified ID. Returns null if no such vertex exists.")
    fun getSymbol(id: String): Symbol? {
        val iter = graph.vertices(id)
        return if (iter.hasNext()) Symbol(iter.next()) else null
    }

    @Synchronized
    @Description("Searches for symbols whose names contain the given query string. Returns a list of matching Symbol objects.")
    fun search(query: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("Symbol")
            .has("name", TextP.containing(query))
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Retrieves all symbols defined in the specified file.")
    fun getSymbolsByFile(fileId: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().has("Symbol", "file", fileId).forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Retrieves all file vertices in the graph as Symbol objects.")
    fun getFiles(): List<Symbol> {
        val files = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("File").forEachRemaining { files.add(Symbol(it)) }
        return files
    }

    @Synchronized
    @Description("Retrieves all symbols written in the specified language.")
    fun getSymbolsByLanguage(language: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Language", "name", language)
            .`in`("WRITTEN_IN")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Retrieves all symbols belonging to the specified library.")
    fun getSymbolsByLibrary(library: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Library", "name", library)
            .`in`("IN_LIBRARY")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Retrieves all symbols belonging to the specified package.")
    fun getSymbolsByPackage(pkg: String): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V()
            .has("Package", "name", pkg)
            .`in`("IN_PACKAGE")
            .forEachRemaining { symbols.add(Symbol(it)) }
        return symbols
    }

    @Synchronized
    @Description("Lists the names of all languages present in the graph.")
    fun listLanguages(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Language").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
    }

    @Synchronized
    @Description("Lists the names of all libraries present in the graph.")
    fun listLibraries(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Library").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
    }

    @Synchronized
    @Description("Lists the names of all packages present in the graph.")
    fun listPackages(): List<String> {
        val list = mutableListOf<String>()
        graph.traversal().V().hasLabel("Package").values<String>("name").forEachRemaining { list.add(it) }
        return list.sorted()
    }

    @Synchronized
    @Description("Saves the current graph to a file in GraphSON format.")
    fun save(path: String) {
        FileOutputStream(path).use { os ->
            GraphSONWriter.build().create().writeGraph(os, graph)
        }
    }

    @Synchronized
    @Description("Loads the graph from a file in GraphSON format, merging it into the current graph.")
    fun load(path: File) {
        FileInputStream(path).use { `is` ->
            GraphSONReader.build().create().readGraph(`is`, graph)
        }
    }

    @Synchronized
    @Description("Lists all symbol vertices in the graph.")
    fun listSymbols(): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        graph.traversal().V().hasLabel("Symbol").forEachRemaining { v ->
            symbols.add(Symbol(v))
        }
        return symbols
    }
    @Description("Represents a node in the symbol graph, which can be a Symbol, File, Language, Library, or Package. Provides access to properties and related nodes.")

    data class Symbol(private val vertex: Vertex) {
        @get:Description("The unique identifier of the node.")
        val id: String = vertex.id() as String
        @get:Description("The name of the symbol or entity.")
        val name: String? get() = getProperty("name")
        @get:Description("The ID of the file containing this symbol.")
        val fileId: String? get() = getProperty("file")
        @get:Description("The start character offset of the symbol definition.")
        val startOffset: Int? get() = getProperty("startOffset")
        @get:Description("The end character offset of the symbol definition.")
        val endOffset: Int? get() = getProperty("endOffset")
        @get:Description("The line number where the symbol is defined.")
        val line: Int? get() = getProperty("line")
        @get:Description("The visibility modifier of the symbol (e.g., public, private).")
        val visibility: String? get() = getProperty("visibility")
        @get:Description("Other modifiers associated with the symbol (e.g., static, final).")
        val modifiers: String? get() = getProperty("modifiers")
        @get:Description("Annotations present on the symbol.")
        val annotations: String? get() = getProperty("annotations")
        @get:Description("The timestamp when the symbol was last modified.")
        val lastModified: Long? get() = getProperty("lastModified")
        @get:Description("A map of all properties stored on the vertex.")
        val properties: Map<String, Any>
            get() {
                val map = mutableMapOf<String, Any>()
                map["id"] = id
                vertex.properties<Any>().forEachRemaining { p -> map[p.key()] = p.value() }
                return map
            }
        private inline fun <reified T> getProperty(key: String): T? {
            val p = vertex.property<T>(key)
            return if (p.isPresent) p.value() else null
        }
        @Description("Returns a list of symbols referenced by this symbol.")
        fun references(): List<Symbol> {
            val list = mutableListOf<Symbol>()
            vertex.vertices(Direction.OUT, "REFERENCES").forEachRemaining { list.add(Symbol(it)) }
            return list
        }
        @Description("Returns a list of symbols that reference this symbol.")
        fun referencedBy(): List<Symbol> {
            val list = mutableListOf<Symbol>()
            vertex.vertices(Direction.IN, "REFERENCES").forEachRemaining { list.add(Symbol(it)) }
            return list
        }
        @Description("Returns the file symbol where this symbol is defined.")
        fun file(): Symbol? {
            val iter = vertex.vertices(Direction.OUT, "DEFINED_IN")
            return if (iter.hasNext()) Symbol(iter.next()) else null
        }
        @Description("Returns the name of the language this symbol is written in.")

        fun language(): String? =
            vertex.vertices(Direction.OUT, "WRITTEN_IN").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)
        @Description("Returns the name of the package this symbol belongs to.")

        fun packageName(): String? =
            vertex.vertices(Direction.OUT, "IN_PACKAGE").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)
        @Description("Returns the name of the library this symbol belongs to.")

        fun libraryName(): String? =
            vertex.vertices(Direction.OUT, "IN_LIBRARY").asSequence().firstOrNull()?.property<String>("name")
                ?.orElse(null)
    }

    private fun getOrCreateVertex(id: String, label: String): Vertex {
        val iter = graph.vertices(id)
        return if (iter.hasNext()) iter.next() else graph.addVertex(T.label, label, T.id, id)
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/UnifiedPlanApp.kt

```
package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveMode
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.FixedConcurrencyProcessor
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * A unified application that can use different cognitive modes based on configuration.
 * This allows for switching between different planning and execution strategies.
 */
abstract class UnifiedPlanApp(
    path: String,
    applicationName: String = "Unified Planning App",
    showMenubar: Boolean = true,
    var useExpansionSyntax: Boolean = true,
) : ApplicationServer(
    applicationName = applicationName,
    path = path,
    showMenubar = showMenubar,
    root = dataStorageRoot,
) {
    private val log = LoggerFactory.getLogger(UnifiedPlanApp::class.java)

    // Updated expansion patterns to match ChatSocketManager
    private val idSubPattern = """[^|\n,/\\;}\]\[><()@]+"""
    private val expansionExpressionPattern = Regex("""@\[($idSubPattern(?:[|,]$idSubPattern)+)]""")
    private val sequenceExpansionPattern = Regex("""@\{([^}]+(?:\s*->\s*[^}]+)+)\}""")
    private val rangeExpansionPattern = Regex("""@\((-?\d+)(?:\.{2,3}| to )(-?\d+)(?:(?::| by )(\d+))?\)""")
    private val topicReferencePattern = Regex("""@([A-Z][a-zA-Z0-9_]*)""")

    private val aggregateTopics = ConcurrentHashMap<String, MutableList<String>>()
    override val stickyInput = true
    override val inputCnt: Int = 4

    override fun appInfo(session: Session): Map<String, Any> {
        val settings = getSettings(session, defaultUser, OrchestrationConfig::class.java)
        return AppInfoData(
            applicationName = applicationName,
            inputCnt = when {
                settings?.cognitiveMode == CognitiveModeType.Chat -> 0
                else -> 4
            },
            stickyInput = stickyInput,
            loadImages = false,
            showMenubar = showMenubar,
        ).toMap()
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T =
        OrchestrationConfig(sessionId = session.sessionId, null) as T

    abstract fun instance(model: ApiChatModel): ChatInterface

    override fun newSession(
        user: User,
        session: Session
    ): SocketManager {
        val socketManager = super.newSession(user, session)
        val settings = getSettings(session, user, OrchestrationConfig::class.java)
        useExpansionSyntax = when (settings?.cognitiveMode) {
            CognitiveModeType.Chat -> true
            else -> false
        }
        if (useExpansionSyntax) {
            socketManager.newTask(cancelable = false, root = true).expandable(
                "Query Expansion Syntax Guide", """
                <div class="expandable-guide">
                  <p>You can use the following syntaxes in your messages to automatically expand your queries:</p>
                  <h4 class="expandable-section-title">Parallel Expansion</h4>
                  <p class="expandable-description">Use <code>@[option1|option2|option3]</code> to run the same prompt with each option in parallel.</p>
                  <p class="expandable-example"><em>Example:</em> <code>Analyze the performance of @[React|Vue|Angular] frameworks</code></p>
                  <h4 class="expandable-section-title">Sequence Expansion</h4>
                  <p class="expandable-description">Use <code>@{step1 -> step2 -> step3}</code> to run a sequence of prompts, where the output of each feeds into the next.</p>
                  <p class="expandable-example"><em>Example:</em> <code>Create a plan, then @{implement the first step -> test the implementation -> document the results}</code></p>
                  <h4 class="expandable-section-title">Range Expansion</h4>
                  <p class="expandable-description">Use <code>@(start..end:step)</code> to iterate over a range of numbers.</p>
                  <p class="expandable-example"><em>Example:</em> <code>Generate test cases for input values @(1..10:2)</code></p>
                  <h4 class="expandable-section-title">Topic Reference Expansion</h4>
                  <p class="expandable-description">Use <code>@topicType</code> to refer to previously identified topics.</p>
                  <p class="expandable-example"><em>Example:</em> <code>Create documentation for @Function</code></p>
                  <p class="expandable-footer">You can combine these syntaxes for more complex expansions.</p>
                </div>
                """.trimIndent()
            )
        }

        socketManager.newTask(cancelable = false, root = true).expandable(
            "Session Info", """
Session ID: `${session}`

Start Time: `${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}`

Root: `${settings?.absoluteWorkingDir}`

Session Location: `${dataStorage.getSessionDir(user, session).absolutePath}`

Data Location: `${dataStorage.getDataDir(user, session).absolutePath}`

Expansion Syntax: `${if (useExpansionSyntax) "Enabled" else "Disabled"}`

```json
${settings?.toJson()}
```
            """.renderMarkdown()
        )
        return socketManager
    }

    override fun userMessage(
        session: Session,
        user: User,
        userMessage: String,
        ui: SocketManager
    ) {
        try {
            val settings = try {
                getSettings(session, user, OrchestrationConfig::class.java)
            } catch (e: Exception) {
                log.error("Error retrieving orchestration config, using default", e)
                null
            }?.apply {
                if(null == DataStorage.sessionPaths[session]) absoluteWorkingDir?.let { DataStorage.sessionPaths[session] = File(it) }
            } ?: throw IllegalStateException("OrchestrationConfig not found in session settings")

            val cognitiveMode = (settings.cognitiveMode ?: CognitiveModeType.Chat).getImpl(
                orchestrationConfig = settings,
                session = session,
                user = user
            )

            log.debug("Received user message: $userMessage")

            val expandedMessage = if (useExpansionSyntax) expandTopics(userMessage) else userMessage

//            if (useExpansionSyntax && hasExpansionSyntax(expandedMessage)) {
//                processMessageWithExpansions(
//                    session,
//                    user,
//                    expandedMessage,
//                    ui,
//                    settings
//                )
//                return
//            }

            val task = ui.newTask(true)
            val mode = cognitiveMode.apply { initialize(task) }
            mode.handleUserMessage(expandedMessage, task)
            onComplete(mode, task)
        } catch (e: Throwable) {
            log.error("Error processing user message", e)
            ui.newTask().error(e)
        }
    }

    open fun onComplete(mode: CognitiveMode<*>, task: SessionTask) {
        // No-op by default
    }

    /**
     * Expands topic references in the message using previously identified topics
     */
    private fun expandTopics(userMessage: String): String {
        return topicReferencePattern.replace(userMessage) { matchResult ->
            val topicType = matchResult.groupValues[1]
            val topicList = aggregateTopics[topicType]
            val entities = synchronized(topicList ?: Any()) {
                topicList?.toList()
            }
            if (!entities.isNullOrEmpty()) {
                "@[${entities.joinToString("|")}]"
            } else {
                matchResult.value
            }
        }
    }

    /**
     * Recursively processes a message with expansion expressions.
     * Handles parallel, sequence, and range expansions similar to ChatSocketManager.
     */
    private fun processMessageRecursive(
        session: Session,
        user: User = defaultUser,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        orchestrationConfig: OrchestrationConfig
    ) {

        // Check for range expansion first
        val rangeMatch = rangeExpansionPattern.find(currentMessage)
        if (rangeMatch != null) {
            expandRange(session, user, currentMessage, ui, task, processor, rangeMatch)
            return
        }

        // Check for sequence expansion
        val sequenceMatch = sequenceExpansionPattern.find(currentMessage)
        if (sequenceMatch != null) {
            expandSequence(session, user, currentMessage, ui, task, processor, sequenceMatch)
            return
        }

        // Check for parallel expansion
        val parallelMatch = expansionExpressionPattern.find(currentMessage)
        if (parallelMatch != null && parallelMatch.groupValues[1].split('|', ',').size > 1) {
            expandParallel(session, user, currentMessage, ui, task, processor, parallelMatch, orchestrationConfig)
            return
        }
        val cognitiveMode = orchestrationConfig.cognitiveMode?.getImpl(
            orchestrationConfig = orchestrationConfig,
            session = session,
            user = user
        )?.apply { initialize(task) } ?: throw IllegalStateException("Cognitive mode not configured")
        cognitiveMode.handleUserMessage(currentMessage, task)
    }

    /**
     * Expands range expressions in the format @(start..end:step)
     */
    private fun expandRange(
        session: Session,
        user: User = defaultUser,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        rangeMatch: MatchResult
    ) {
        val start = rangeMatch.groupValues[1].toInt()
        val end = rangeMatch.groupValues[2].toInt()
        val step = rangeMatch.groupValues[3].takeIf { it.isNotEmpty() }?.toInt() ?: 1

        val items = generateSequence(start) { it + step }
            .takeWhile { if (step > 0) it <= end else it >= end }
            .toList()
            .map { it.toString() }

        expandSequenceItems(
            session,
            user,
            currentMessage,
            ui,
            task,
            processor,
            rangeMatch.value,
            items,
            this@UnifiedPlanApp.getSettings(session, user, OrchestrationConfig::class.java)!!
        )
    }

    /**
     * Expands sequence expressions in the format @{step1 -> step2 -> step3}
     */
    private fun expandSequence(
        session: Session,
        user: User = defaultUser,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        sequenceMatch: MatchResult
    ) {
        val items = sequenceMatch.groupValues[1].split(Regex("""\s*->\s*"""))
        expandSequenceItems(
            session,
            user,
            currentMessage,
            ui,
            task,
            processor,
            sequenceMatch.value,
            items,
            this@UnifiedPlanApp.getSettings(session, user, OrchestrationConfig::class.java)!!
        )
    }

    /**
     * Expands parallel expressions in the format @[option1|option2|option3]
     */
    private fun expandParallel(
        session: Session,
        user: User = defaultUser,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        parallelMatch: MatchResult,
        orchestrationConfig: OrchestrationConfig
    ) {
        val options = parallelMatch.groupValues[1].split('|', ',')
        val tabs = TabbedDisplay(task, closable = useExpansionSyntax)

        options.map { option ->
            processor.submit {
                val subTask = ui.newTask(false).apply { tabs[option] = placeholder }
                val nextMessage = currentMessage.replaceFirst(parallelMatch.value, option)

                processMessageRecursive(
                    session = session,
                    user = user,
                    currentMessage = nextMessage,
                    ui = ui,
                    task = subTask,
                    processor = processor,
                    orchestrationConfig = orchestrationConfig
                )
            }
        }.forEach { it.get() }

        tabs.update()
    }

    /**
     * Expands sequence items (used by both range and sequence expansions)
     */
    private fun expandSequenceItems(
        session: Session,
        user: User = defaultUser,
        currentMessage: String,
        ui: SocketManager,
        task: SessionTask,
        processor: FixedConcurrencyProcessor,
        expression: String,
        items: List<String>,
        orchestrationConfig: OrchestrationConfig
    ) {
        val tabs = TabbedDisplay(task, closable = useExpansionSyntax)

        for (item in items) {
            val subTask = ui.newTask(false).apply { tabs[item] = placeholder }
            val nextMessage = currentMessage.replaceFirst(expression, item)

            processMessageRecursive(
                session = session,
                user = user,
                currentMessage = nextMessage,
                ui = ui,
                task = subTask,
                processor = processor,
                orchestrationConfig = orchestrationConfig
            )
        }

        tabs.update()
    }

    companion object {
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

Here is the updated `README.md` for the `com.simiacryptus.cognotik.apps` package, based on the provided source code and documentation requirements.

### webui/src/main/kotlin/com/simiacryptus/cognotik/apps/README.md
```markdown
# com.simiacryptus.cognotik.apps

This package contains the core application implementations for the Cognotik web interface. These applications range from specialized code-fixing tools to general-purpose planning and testing utilities.

## Core Applications

### Patching & Error Correction
*   **`PatchApp`**: An abstract base class for "Magic Code Fixer" applications. It provides a robust framework for iterative error correction, including:
    *   Automatic error parsing from command output.
    *   Context-aware code summarization with line numbers and git diffs.
    *   Automated research via file searches and symbol analysis.
    *   Iterative fix attempts with history tracking to avoid repeating failed patches.
*   **`CmdPatchApp`**: A concrete implementation of `PatchApp` that executes shell commands (including PowerShell and Batch scripts). It monitors process output in real-time, handles timeouts, and triggers the AI fixing workflow if the command fails (non-zero exit code).

### Planning & Task Execution
*   **`UnifiedPlanApp`**: A versatile application supporting multiple "Cognitive Modes" (e.g., Chat, Planning). It features an advanced query expansion syntax:
    *   **Parallel Expansion**: `@[option1|option2]` runs prompts in parallel.
    *   **Sequence Expansion**: `@{step1 -> step2}` feeds output from one step into the next.
    *   **Range Expansion**: `@(1..10:2)` iterates over numeric ranges.
    *   **Topic References**: `@TopicName` expands to previously identified entities.
*   **`SingleTaskApp`**: Designed for executing specific, pre-configured tasks without the overhead of a full planning phase. It uses a `TaskOrchestrator` to run defined `TaskExecutionConfig` sequences.

### Infrastructure & Utilities
*   **`SessionProxyServer`**: Acts as a routing layer for AI Coding Assistant sessions, mapping incoming socket connections to specific agents or chat servers.
*   **`StressTestApp`**: A diagnostic tool used to verify UI performance and feature support. It generates complex Mermaid diagrams, nested tab structures, and high-frequency placeholder updates.

## Services

### `SymbolGraphService`
A sophisticated code analysis service that maintains an in-memory graph representation of the codebase using **Apache TinkerPop/TinkerGraph**.
*   **Vertices**: Represents Files, Symbols, Languages, Libraries, and Packages.
*   **Edges**: Tracks relationships such as `DEFINED_IN`, `REFERENCES`, `WRITTEN_IN`, and `IN_PACKAGE`.
*   **Features**:
    *   Incremental updates based on file modification timestamps.
    *   Cross-reference tracking (who calls what).
    *   Advanced search capabilities using Gremlin traversals.
    *   Persistence support via GraphSON format.

## Key Patterns
*   **Tabbed Displays**: Most applications utilize the `TabbedDisplay` utility to organize complex AI outputs, logs, and research data.
*   **Session Management**: Applications extend `ApplicationServer` and implement `newSession` to provide isolated environments for user interactions.
*   **Markdown Integration**: Extensive use of `renderMarkdown` for rich UI presentation, including support for Mermaid diagrams and interactive "Apply Patch" links.
```

### Summary of Changes
- Created a new `README.md` for the `com.simiacryptus.cognotik.apps` package.
- Documented the primary application classes: `PatchApp`, `CmdPatchApp`, `UnifiedPlanApp`, `SingleTaskApp`, `SessionProxyServer`, and `StressTestApp`.
- Provided a detailed description of the `SymbolGraphService` and its use of TinkerGraph for code relationship mapping.
- Highlighted the advanced query expansion syntax supported by `UnifiedPlanApp`.
- Outlined common architectural patterns used across the applications (tabs, session management, markdown).
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/apps/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/apps/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/apps/README.md</a> Updated
