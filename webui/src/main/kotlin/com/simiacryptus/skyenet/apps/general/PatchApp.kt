package com.simiacryptus.skyenet.apps.general

import com.simiacryptus.diff.AddApplyFileDiffLinks
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.AgentPatterns
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.TabbedDisplay
import com.simiacryptus.skyenet.core.actors.ParsedActor
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.core.util.FileValidationUtils.Companion.filteredWalk
import com.simiacryptus.skyenet.core.util.FileValidationUtils.Companion.isGitignore
import com.simiacryptus.skyenet.core.util.FileValidationUtils.Companion.isLLMIncludableFile
import com.simiacryptus.skyenet.core.util.IterativePatchUtil.patchFormatPrompt
import com.simiacryptus.skyenet.set
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import com.simiacryptus.skyenet.webui.application.ApplicationSocketManager
import com.simiacryptus.skyenet.webui.session.SessionTask
import com.simiacryptus.skyenet.webui.session.SocketManager
import com.simiacryptus.skyenet.webui.session.getChildClient
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat

abstract class PatchApp(
  override val root: File,
  protected val settings: Settings,
  private val api: ChatClient,
  val model: ChatModel,
  val parsingModel: ChatModel,
  private val promptPrefix: String = """The following command was run and produced an error:""",
  private val fixesPerIteration: Int = 1000,
) : ApplicationServer(
  applicationName = "Magic Code Fixer",
  path = "/fixCmd",
  showMenubar = false,
) {

  data class OutputResult(val exitCode: Int, val output: String)

  companion object {
    private val log = LoggerFactory.getLogger(PatchApp::class.java)
    const val tripleTilde = "`" + "``" // This is a workaround for the markdown parser when editing this file
  }

  /**
   * Helper to render command output using a consistent markdown format.
   */
  private fun renderCommandOutput(output: OutputResult): String {
    return renderMarkdown("${tripleTilde}\n${output.output}\n${tripleTilde}")
  }

  // Track the last parsed error details and use them as the example instance if available
  private var lastParsedErrors: ParsedErrors? = null

  // Stateful records of previous run results and parsed error results
  private val previousRunResults = mutableListOf<OutputResult>()
  private val previousParsedErrorsRecords = mutableListOf<ParsedErrorRecord>()

  // Add structured logging
  private fun logEvent(event: String, data: Map<String, Any?>) {
    log.info("$event: ${JsonUtil.toJson(data)}")
  }

  abstract fun codeFiles(): Set<Path>
  abstract fun codeSummary(paths: List<Path>): String
  abstract fun output(
    task: SessionTask,
    settings: Settings,
    ui: ApplicationInterface,
    tabs: TabbedDisplay = TabbedDisplay(task)
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
    val errors: ParsedErrors? = null,
    val timestamp: Long = System.currentTimeMillis()
  )

  data class SearchQuery(
    @Description("The search pattern to be used in file content matching") val pattern: String,
    @Description("A glob expression to filter which files to run the search against") val fileGlob: String
  )
  data class ParsedError(
    @Description("The error message") val message: String? = null,
    @Description("Summarize output to distill details related to the error message") val details: String? = null,
    @Description("Problem severity (higher numbers indicate more fatal issues)") val severity: Int = 0,
    @Description("Problem complexity (higher numbers indicate more difficult issues)") val complexity: Int = 0,
    @Description("Files identified as needing modification and issue-related files") val relatedFiles: List<String>? = null,
    @Description("Files identified as needing modification and issue-related files") val fixFiles: List<String>? = null,
    @Description("Search queries containing both a pattern and a file glob") val searchQueries: List<SearchQuery>? = null
  )

  data class Settings(
    var commands: List<CommandSettings> = listOf(),
    val autoFix: Boolean = false,
    val maxRetries: Int = 3,
    var exitCodeOption: String = "nonzero",
  ) {
    // For backwards compatibility and convenience
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
    // Execute each command in sequence
    val tabs = TabbedDisplay(task)

    val outputResult = output(task, settings, ui, tabs)
    previousRunResults.add(outputResult)
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
      fixAll(settings, outputResult, fixTask, ui, api)
    } catch (e: Exception) {
      fixTask.error(ui, e)
    }
    return outputResult
  }

  private fun fixAll(
    settings: Settings,
    output: OutputResult,
    task: SessionTask,
    ui: ApplicationInterface,
    api: ChatClient,
  ) {
    // Add logging for operation start
    logEvent(
      "Starting fix operation", mapOf(
        "exitCode" to output.exitCode, "settings" to settings
      )
    )
    Retryable(ui, task) { content ->
      val task = ui.newTask(false)
      fixAllInternal(
        settings = settings, output = output, task = task, ui = ui, changed = mutableSetOf(), api = api
      )
      task.placeholder
    }.also {
      // Add logging for operation completion
      logEvent(
        "Fix operation completed", mapOf(
          "success" to true
        )
      )
    }
  }
  
  fun recentErrors() = previousParsedErrorsRecords.flatMap { it.errors?.errors ?: emptySet() }
    .groupBy { it.message }
    .let { errors ->
      ParsedErrors(errors.map { (message, errors) ->
        errors.maxByOrNull { it.severity }!!
      })
    }

  private fun fixAllInternal(
    settings: Settings,
    output: OutputResult,
    task: SessionTask,
    ui: ApplicationInterface,
    changed: MutableSet<Path>,
    api: ChatClient,
  ) {
    val api = api.getChildClient(task)
    val plan = ParsedActor(
      resultClass = ParsedErrors::class.java,
      exampleInstance = if (previousParsedErrorsRecords.isEmpty()) ParsedErrors(
        listOf(
          ParsedError(
            message = "Error message",
            details = "Line 123: error message\n\nThis is a detailed description of the error, mainly copied from the output",
            relatedFiles = listOf("src/main/java/com/example/Example.java"),
            fixFiles = listOf("src/main/java/com/example/Example.java"),
            searchQueries = listOf(
              SearchQuery(
                pattern = "error message",
                fileGlob = "**/*.java"
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
        """.trimIndent() + (if (settings.additionalInstructions.isNotBlank()) "Additional Instructions:\n  ${settings.additionalInstructions}\n" else ""))
    ).answer(
      listOf(
        "$promptPrefix\n\n${tripleTilde}\n${output.output}\n${tripleTilde}"
      ), api = api
    )
    
    // Update the last parsed error details so they can be used in subsequent runs
    lastParsedErrors = plan.obj
    val progressHeader = task.header("Processing tasks...")
    task.add(
      AgentPatterns.displayMapInTabs(
        mapOf(
          "Text" to renderMarkdown(plan.text, ui = ui),
          "JSON" to renderMarkdown("${tripleTilde}json\n${JsonUtil.toJson(plan.obj)}\n${tripleTilde}", ui = ui),
          "Process Details" to renderMarkdown(
            "Exit Code: ${output.exitCode}\nCommand Output:\n${tripleTilde}\n${output.output}\n${tripleTilde}",
            ui = ui
          )
        )
      )
    )
    // Record the current error parsing result for future reference
    previousParsedErrorsRecords.add(ParsedErrorRecord(plan.obj))
    // Process errors ordered by fixPriority (lower number = higher priority)
    val tabs = TabbedDisplay(task)
    plan.obj.errors?.sortedBy { it.severity.toDouble() / it.complexity }?.takeLast(fixesPerIteration)?.forEach { error ->
      val task = ui.newTask(false).apply { tabs[error.message ?: "Error"] = placeholder }
      task.header("Processing error: ${error.message} (Severity: ${error.severity}, Complexity: ${error.complexity})")
      task.add(renderMarkdown("```json\n${JsonUtil.toJson(error)}\n```", tabs = false, ui = ui))
      task.verbose(renderMarkdown("[Extra Details] Error processed at: ${java.time.Instant.now()}", tabs = false, ui = ui))
      // Search for files using the provided search strings
      val searchResults = error.searchQueries?.flatMap { query ->
        val filter1 = filteredWalk(settings.workingDirectory ?: root) { !isGitignore(it.toPath()) }
          .filter { isLLMIncludableFile(it) }
        val filter2 = filter1.filter { file ->
          java.nio.file.FileSystems.getDefault()
            .getPathMatcher("glob:" + query.fileGlob)
            .matches(file.toPath())
        }
        val filter3 = filter2.filter { it.readText().contains(query.pattern, ignoreCase = true) }
        filter3.map { it.toPath() }.toList()
      }?.toSet() ?: emptySet()
      if (searchResults.isNotEmpty()) {
        task.verbose(
          renderMarkdown(
            "Search results:\n\n${searchResults.joinToString("\n") { "* `$it`" }}", tabs = false, ui = ui
          )
        )
      }
      Retryable(ui, task) {
        val task = ui.newTask(false)
        Thread {
          fix(
            error,
            searchResults.toList().map { it.toFile().absolutePath },
            output,
            ui,
            settings.autoFix,
            changed,
            api,
            task
          )
        }.start()
        task.placeholder
      }
    }
    progressHeader?.set("Finished processing tasks")
    task.append("", false)
  }

  private fun fix(
    error: ParsedError,
    additionalFiles: List<String>? = null,
    output: OutputResult,
    ui: ApplicationInterface,
    autoFix: Boolean,
    changed: MutableSet<Path>,
    api: ChatClient,
    task: SessionTask,
  ) {
    val api = api.getChildClient(task)
    val paths = ((error.fixFiles ?: emptyList()) + (error.relatedFiles ?: emptyList()) + (additionalFiles
      ?: emptyList())).map { filePath ->
      try {
        // First, remove the root path prefix if present so that the LLM-generated absolute paths become relative.
        // Then, strip a leading slash if still present.
        val normalizedRoot = root.absolutePath.replace(File.separatorChar, '/')
        // Trim the file path at the first space if it exists
        val cleanPath = filePath.substringBefore(" ")
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
    }.filterNotNull()
    val prunedPaths = prunePaths(paths, 50 * 1024)
    val (previousErrorOccurances, others) = previousParsedErrorsRecords
      .partition { it.errors?.errors?.any { it.message == error.message } == true }
    
    task.verbose(
      renderMarkdown(
        "Previous occurrences of this error:\n\n" +
        previousErrorOccurances.joinToString("\n") {
          "* " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(it.timestamp)
        } + "\nNon-matching instances: ${others.size}", tabs = false, ui = ui
      )
    )
    task.verbose(renderMarkdown(
      "Files identified for modification:\n\n${
        prunedPaths.distinct().joinToString("\n") {
          "* `$it` (${
            root.toPath().resolve(it).toFile().length()
          } bytes)"
        }
      }", tabs = false, ui = ui
      ))
    val summary = codeSummary(prunedPaths.distinct())
    val response = SimpleActor(
      prompt = """
        You are a helpful AI that helps people with coding.
        
        You will be answering questions about the following code:
        
        """.trimIndent() + summary + "\n" + patchFormatPrompt + """
        
        If needed, new files can be created by using code blocks labeled with the filename in the same manner.
        """.trimIndent(),
      model = model,
    ).answer(
      listOf(
        "$promptPrefix\n\n" +
            "Focus on and Fix the Error:\n  ${error.message ?: ""}\n" +
            (if (error.details?.isNotBlank() == true) "Details:\n  ${error.details}\n" else "") +
            (if (settings.additionalInstructions.isNotBlank()) "Additional Instructions:\n  ${settings.additionalInstructions}\n" else "")
      ), api = api
    )
    val socketManager = ui.socketManager ?: throw IllegalStateException("Socket Manager is not available")
    val markdown = AddApplyFileDiffLinks.instrumentFileDiffs(
      socketManager,
      root = root.toPath(),
      response = response,
      ui = ui,
      api = api,
      shouldAutoApply = { path ->
        if (autoFix && !changed.contains(path)) {
          changed.add(path)
          true
        } else {
          false
        }
      },
      model = model,
    )
    task.complete("<div>${renderMarkdown(markdown)}</div>")
  }

}