package com.simiacryptus.cognotik.apps

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.diff.PatchProcessor
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.ui.patch.DiffInstrumentor
import com.simiacryptus.cognotik.ui.patch.SessionRenderer
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
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
  val fastModel: ChatInterface,
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
    val ui = super.newSession(user, session)!!
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
      val map = mapOf(
        "Text" to plan.text.renderMarkdown(true),
        "JSON" to "${tripleTilde}json\n${JsonUtil.toJson(parsedErrors)}\n$tripleTilde".renderMarkdown(true),
        "Process Details" to "Exit Code: ${outputResult.exitCode}\nCommand Output:\n$tripleTilde\n${outputResult.output}\n$tripleTilde".renderMarkdown(
          true
        )
      ).filter { it.value.isNotBlank() }
      fixTask.add(
        TabbedDisplay.displayMapInTabs(map)
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
      parsingChatter = fastModel,
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

    val markdown = DiffInstrumentor(
      processor,
      SessionRenderer(task),
    ).instrument(
      root = root.toPath(),
      response = fixResponse,
      shouldAutoApply = { path: Path ->
        if (autoFix) {
          log.info("Auto-applying fix to: $path")
          true
        } else {
          log.debug("Not auto-applying fix to: {} (autoFix={})", path, autoFix)
          false
        }
      },
      resolver = ::resolveToRelativePath,
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
