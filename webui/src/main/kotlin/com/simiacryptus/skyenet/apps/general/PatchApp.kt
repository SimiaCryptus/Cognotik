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
import com.simiacryptus.skyenet.core.util.FileValidationUtils
import com.simiacryptus.skyenet.core.util.IterativePatchUtil.patchFormatPrompt
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

abstract class PatchApp(
  override val root: File,
  protected val settings: Settings,
  private val api: ChatClient,
  private val model: ChatModel,
  private val promptPrefix: String = """The following command was run and produced an error:"""
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
  private val previousParsedErrorsRecords = mutableListOf<ParsedErrors>()

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
      newTask.add("Running Commands")
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

  data class ParsedError(
    @Description("The error message") val message: String? = null,
    @Description("Problem severity (higher numbers indicate more fatal issues)") val severity: Int = 0,
    @Description("Problem complexity (higher numbers indicate more difficult issues)") val complexity: Int = 0,
    @Description("Files identified as needing modification and issue-related files") val relatedFiles: List<String>? = null,
    @Description("Files identified as needing modification and issue-related files") val fixFiles: List<String>? = null,
    @Description("Search strings to find relevant files") val searchStrings: List<String>? = null
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
    val output = output(task, settings, ui, tabs)
    previousRunResults.add(output)
    if (output.exitCode == 0 && settings.exitCodeOption == "nonzero") {
      task.complete(
        "<div>\n<div><b>Command executed successfully</b></div>\n</div>"
      )
      return output
    }
    if (settings.exitCodeOption == "zero" && output.exitCode != 0) {
      task.complete(
        "<div>\n<div><b>Command failed</b> (ignoring)</div>\n</div>"
      )
      return output
    }
    val task = ui.newTask(false).apply { tabs["Fix"] = placeholder }
    try {
      fixAll(settings, output, task, ui, api)
    } catch (e: Exception) {
      task.error(ui, e)
    }
    return output
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
      resultClass = ParsedErrors::class.java, exampleInstance = lastParsedErrors ?: ParsedErrors(
        listOf(
          ParsedError(
            message = "Error message",
            relatedFiles = listOf("src/main/java/com/example/Example.java"),
            fixFiles = listOf("src/main/java/com/example/Example.java"),
            searchStrings = listOf("def exampleFunction", "TODO")
          )
        )
      ), model = model, prompt = ("""
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
    // Update the last parsed error details so they can be used in subsequent runs
    lastParsedErrors = plan.obj
    val progressHeader = task.header("Processing tasks")
    // Record the current error parsing result for future reference
    previousParsedErrorsRecords.add(plan.obj)
    // Process errors ordered by fixPriority (lower number = higher priority)
    plan.obj.errors?.sortedBy { it.severity * it.complexity }?.takeLast(1)?.forEach { error ->
      task.header("Processing error: ${error.message}")
      task.add(renderMarkdown("```json\n${JsonUtil.toJson(error)}\n```", tabs = false, ui = ui))
      // Search for files using the provided search strings
      val searchResults = error.searchStrings?.flatMap { searchString ->
        FileValidationUtils.filteredWalk(settings.workingDirectory!!) { !FileValidationUtils.isGitignore(it.toPath()) }
          .filter { FileValidationUtils.isLLMIncludableFile(it) }
          .filter { it.readText().contains(searchString, ignoreCase = true) }.map { it.toPath() }.toList()
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
        task.placeholder
      }
    }
    progressHeader?.clear()
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
    val paths = ((error.fixFiles ?: emptyList()) + (error.relatedFiles ?: emptyList()) + (additionalFiles
      ?: emptyList())).map {
      try {
        File(it).toPath()
      } catch (e: Throwable) {
        log.warn("Error: root=${root}    ", e)
        null
      }
    }.filterNotNull()
    val prunedPaths = prunePaths(paths, 50 * 1024)
    task.verbose(renderMarkdown(
        "Files identified for modification:\n\n${prunedPaths.joinToString("\n") { "* `$it` (${it.toFile().length()} bytes)" }}", tabs = false, ui = ui
      ))
    val summary = codeSummary(prunedPaths)
    val response = SimpleActor(
      prompt = """
        You are a helpful AI that helps people with coding.
        
        You will be answering questions about the following code:
        
        """.trimIndent() + summary + "\n" + patchFormatPrompt + """
        
        If needed, new files can be created by using code blocks labeled with the filename in the same manner.
        """.trimIndent(), model = model
    ).answer(
      listOf(
        "$promptPrefix\n\n${tripleTilde}\n${output.output}\n${tripleTilde}\n\nFocus on and Fix the Error:\n  ${
          error.message?.replace(
            "\n", "\n  "
          ) ?: ""
        }\n${if (settings.additionalInstructions.isNotBlank()) "Additional Instructions:\n  ${settings.additionalInstructions}\n" else ""}"
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