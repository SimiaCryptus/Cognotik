package com.simiacryptus.cognotik.apps.general

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.actors.ParsedResponse
import com.simiacryptus.cognotik.actors.SimpleActor
import com.simiacryptus.cognotik.diff.IterativePatchUtil.patchFormatPrompt
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.FileSelectionUtils.Companion.filteredWalk
import com.simiacryptus.cognotik.util.FileSelectionUtils.Companion.isGitignore
import com.simiacryptus.cognotik.util.FileSelectionUtils.Companion.isLLMIncludableFile
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.application.ApplicationSocketManager
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.concurrent.TimeUnit

abstract class PatchApp(
    override val root: File,
    protected val settings: Settings,
    private val api: ChatClient,
    val model: ChatModel,
    val parsingModel: ChatModel,
    private val promptPrefix: String = """The following command was run and produced an error:""",
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

    private fun renderCommandOutput(output: OutputResult): String {
        return renderMarkdown("${tripleTilde}\n${output.output}\n${tripleTilde}")
    }

    private var lastParsedErrors: ParsedErrors? = null

    private val previousParsedErrorsRecords = mutableListOf<ParsedErrorRecord>()

    private fun logEvent(event: String, data: Map<String, Any?>) {
        log.info("$event: ${JsonUtil.toJson(data)}")
    }

    abstract fun codeFiles(): Set<Path>

    open fun codeSummary(paths: List<Path>, error: ParsedError): String {
        val a = paths.map { it.toFile().findAbsolute(settings.workingDirectory, root, File(".")) }
        val b = a.filter { it.exists() && !it.isDirectory && it.length() < (256 * 1024) }
        return b.joinToString("\n\n") { path ->
            val errorLocations = error.locations?.filter { loc ->
                loc.file?.endsWith(path.name) == true || loc.file?.let { path.absolutePath.endsWith(it) } == true
            }?.flatMap {
                it.lines ?: emptyList()
            }?.toSet() ?: emptySet()

            try {
                val fileContent = path.readText()
                val lines = fileContent.lines()
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

    abstract fun output(
        task: SessionTask, settings: Settings, ui: ApplicationInterface, tabs: TabbedDisplay = TabbedDisplay(task)
    ): OutputResult

    abstract fun searchFiles(searchStrings: List<String>): Set<Path>
    override val singleInput = true
    override val stickyInput = false
    override fun newSession(user: User?, session: Session): SocketManager {
        var retries: Int = -1
        val socketManager = super.newSession(user, session)
        val ui = (socketManager as ApplicationSocketManager).applicationInterface
        val task = ui.newTask()
        var retryOnOffButton: StringBuilder? = null
        val disableButton = task.hrefLink("Disable Auto-Retry") {
            retries = 0
            retryOnOffButton?.clear()
            task.update()
        }
        if (settings.autoFix && settings.maxRetries > 0) {
            retryOnOffButton = task.add(disableButton)
        }
        lateinit var retry: Retryable
        retry = Retryable(ui = ui, task = task) { content ->
            if (retries < 0) {
                retries = when {
                    settings.autoFix -> settings.maxRetries
                    else -> 0
                }
            }
            val newTask = ui.newTask(false)
            Thread {
                val result = run(ui, newTask)
                if (result.exitCode != 0 && retries > 0) {
                    retry.retry()
                }
                retries -= 1
            }.start()
            newTask.placeholder
        }
        return socketManager
    }

    abstract fun projectSummary(): String

    private fun prunePaths(paths: List<Path>, maxSize: Int): List<Path> {
        val sortedPaths = paths.sortedByDescending { it.toFile().length() }
        var totalSize = 0
        val prunedPaths = mutableListOf<Path>()
        for (path in sortedPaths) {
            val fileSize = path.toFile().length().toInt()
            if (totalSize + fileSize > maxSize) break
            prunedPaths.add(path)
            totalSize += fileSize
        }
        return prunedPaths
    }

    data class ParsedErrors(
        val errors: List<ParsedError>? = null
    )

    data class ParsedErrorRecord(
        val errors: ParsedErrors? = null, val timestamp: Long = System.currentTimeMillis()
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
        var exitCodeOption: String = "nonzero",
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
        ui: ApplicationInterface,
        task: SessionTask,
    ): OutputResult {

        val tabs = TabbedDisplay(task)

        val outputResult = output(task, settings, ui, tabs)
        if (outputResult.exitCode == 0 && settings.exitCodeOption == "nonzero") {
            task.complete("<div>\n<div><b>Command executed successfully</b></div>\n</div>")
            return outputResult
        }
        if (settings.exitCodeOption == "zero" && outputResult.exitCode != 0) {
            task.complete("<div>\n<div><b>Command failed</b> (ignoring)</div>\n</div>")
            return outputResult
        }

        val fixTask = ui.newTask(false).apply { tabs["Fix"] = placeholder }
        try {
            val api = api.getChildClient(task = fixTask)
            val plan = if (outputResult.errors == null) {
                parsedErrorsParsedResponse(settings = settings, output = outputResult, api = api)
            } else {
                object : ParsedResponse<ParsedErrors>(
                    ParsedErrors::class.java
                ) {
                    override val text: String = ""
                    override val obj: ParsedErrors = outputResult.errors
                }
            }

            val parsedErrors: ParsedErrors = plan.obj
            lastParsedErrors = parsedErrors
            val progressHeader = fixTask.header("Processing tasks...", 3)
            fixTask.add(
                AgentPatterns.displayMapInTabs(
                    mapOf(
                        "Text" to renderMarkdown(plan.text, ui = ui),
                        "JSON" to renderMarkdown(
                            "${tripleTilde}json\n${JsonUtil.toJson(parsedErrors)}\n$tripleTilde",
                            ui = ui
                        ),
                        "Process Details" to renderMarkdown(
                            "Exit Code: ${outputResult.exitCode}\nCommand Output:\n$tripleTilde\n${outputResult.output}\n$tripleTilde",
                            ui = ui
                        )
                    ).filter { it.value.isNotBlank() },
                )
            )
            previousParsedErrorsRecords.add(ParsedErrorRecord(parsedErrors))
            fixAllErrors(
                task = fixTask,
                plan = plan,
                ui = ui,
                settings = settings,
                changed = mutableSetOf(),
                api = api,
                progressHeader = progressHeader
            )
        } catch (e: Exception) {
            fixTask.error(ui, e)
        }
        return outputResult
    }

    private fun recentErrors() =
        previousParsedErrorsRecords.flatMap { it.errors?.errors ?: emptySet() }.groupBy { it.message }.let { errors ->
            ParsedErrors(errors.map { (_, errors) ->
                errors.maxByOrNull { it.severity ?: 0 }!!
            })
        }

    private fun fixAllErrors(
        task: SessionTask,
        plan: ParsedResponse<ParsedErrors>,
        ui: ApplicationInterface,
        settings: Settings,
        changed: MutableSet<Path>,
        api: ChatClient,
        progressHeader: StringBuilder?
    ) {
        val tabs = TabbedDisplay(task)
        val errors = plan.obj.errors ?: emptyList()
        val hasErrors = errors.any { it.isWarning != true }
        val filteredErrors = errors.filter {
            if (hasErrors) {
                !settings.ignoreWarnings || (it.isWarning != true)

            } else {
                true

            }
        }
        filteredErrors.groupBy { it.message }
            .map { (msg, errors) ->
                ui.socketManager?.pool?.submit {
                    val task = ui.newTask(false).apply { tabs[msg ?: "Error"] = placeholder }
                    errors.forEach { error ->
                        task.header("Processing error: $msg", 3)
                        task.add(renderMarkdown("```json\n${JsonUtil.toJson(error)}\n```", tabs = false, ui = ui))
                        task.verbose(
                            renderMarkdown(
                                "[Extra Details] Error processed at: ${Instant.now()}",
                                tabs = false,
                                ui = ui
                            )
                        )

                        val searchResults = error.research?.searchQueries?.flatMap { query ->
                            val filter1 = filteredWalk(
                                settings.workingDirectory ?: root
                            ) { !isGitignore(it.toPath()) }.filter { isLLMIncludableFile(it) }
                            val filter2 = filter1.filter { file ->
                                FileSystems.getDefault().getPathMatcher("glob:" + query.fileGlob).matches(file.toPath())
                            }
                            val filter3 =
                                filter2.filter { it.readText().contains(query.pattern ?: "", ignoreCase = true) }
                            filter3.map { it.toPath() }.toList()
                        }?.toSet() ?: emptySet()
                        if (searchResults.isNotEmpty()) {
                            task.verbose(
                                renderMarkdown(
                                    "Search results:\n\n${searchResults.joinToString("\n") { "* `$it`" }}",
                                    tabs = false,
                                    ui = ui
                                )
                            )
                        }
                        fix(
                            error,
                            searchResults.toList().map { it.toFile().absolutePath },
                            ui,
                            settings.autoFix,
                            changed,
                            api,
                            task
                        )
                    }
                }
            }.toTypedArray().onEach { it?.get() }
        progressHeader?.set("Finished processing tasks")
        task.append("", false)
    }

    private fun parsedErrorsParsedResponse(
        settings: Settings, output: OutputResult, api: ChatClient
    ): ParsedResponse<ParsedErrors> {
        val plan = ParsedActor(
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
            parsingModel = parsingModel,
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
            ), api = api
        )
        return plan
    }

    private fun fix(
        error: ParsedError,
        additionalFiles: List<String>? = null,
        ui: ApplicationInterface,
        autoFix: Boolean,
        changed: MutableSet<Path>,
        api: ChatClient,
        task: SessionTask,
    ) {
        val childApi = api.getChildClient(task)
        val paths = ((error.research?.fixFiles ?: emptyList()) +
                (error.research?.relatedFiles ?: emptyList()) +
                (error.locations?.map { it.file } ?: emptyList()) +
                (additionalFiles ?: emptyList())).mapNotNull { filePath ->
            try {


                val normalizedRoot = root.absolutePath.replace(File.separatorChar, '/')

                val cleanPath = filePath?.let { cleanFilePath(it) } ?: return@mapNotNull null
                var relativePath =
                    if (cleanPath.contains(normalizedRoot)) cleanPath.replaceFirst(normalizedRoot, "") else cleanPath
                if (relativePath.startsWith("/")) {
                    relativePath = relativePath.drop(1)
                }
                File(relativePath).toPath()
            } catch (e: Throwable) {
                log.warn("Error: root=${root}", e)
                null
            }
        }
        val prunedPaths = prunePaths(paths, 50 * 1024)
        val (previousErrorOccurances, others) = previousParsedErrorsRecords.partition { it.errors?.errors?.any { it.message == error.message } == true }

        val summary = codeSummary(prunedPaths.distinct(), error)
        val fixResponse = SimpleActor(
            prompt = """
        You are a helpful AI that helps people with coding.
        You will be answering questions about the following code:
        $summary
        $patchFormatPrompt
        If needed, new files can be created by using code blocks labeled with the filename in the same manner.
        Note: Ignore any "/* Error: ... */" comments when generating patches - these are just for reference.
        """.trimIndent(),
            model = model,
        ).answer(
            listOf(
                "$promptPrefix\n\nFocus on and Fix the Error:\n  ${error.message ?: ""}\n" +
                        (if (error.details?.isNotBlank() == true) "Details:\n  ${error.details}\n" else "") +
                        (if (settings.additionalInstructions.isNotBlank()) "Additional Instructions:\n  ${settings.additionalInstructions}\n" else "")
            ),
            api = childApi
        ).lines().joinToString("\n") {
            it.replace(Regex("""/\* Error.*?\*/"""), "")
        }
        val markdown = AddApplyFileDiffLinks.instrumentFileDiffs(
            root = root.toPath(),
            response = fixResponse,
            ui = ui,
            api = childApi,
            shouldAutoApply = { path ->
                if (autoFix && !changed.contains(path)) {
                    changed.add(path)
                    true
                } else {
                    false
                }
            },
            model = model,
            self = ui.socketManager!!
        )
        task.verbose(
            renderMarkdown("Previous occurrences of this error:\n\n" + previousErrorOccurances.joinToString("\n") {
                "* " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(it.timestamp)
            } + "\nNon-matching instances: ${others.size}", tabs = false, ui = ui))
        task.verbose(
            renderMarkdown(
                "Files identified for modification:\n\n${
                    prunedPaths.distinct().joinToString("\n") {
                        "* `$it` (${
                            root.toPath().resolve(it).toFile().length()
                        } bytes)"
                    }
                }", tabs = false, ui = ui))
        task.complete("<div>${renderMarkdown(markdown)}</div>")
    }

}