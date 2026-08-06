package com.simiacryptus.cognotik.autofix

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.agents.ParsedResponse
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.text.patch.PatchProcessor
import com.simiacryptus.cognotik.text.ui.DiffInstrumentor
import com.simiacryptus.cognotik.ui.patch.SessionRenderer
import com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk
import com.simiacryptus.cognotik.util.FileSelectionUtils.prefilterFilename
import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.ui.set
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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
    private val log = getLogger(PatchApp::class.java)
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

  var updateStatus: (String) -> Unit = {}

  abstract fun output(
    task: SessionTask,
    settings: Settings,
    tabs: TabbedDisplay = TabbedDisplay(task)
  ): OutputResult

  abstract fun searchFiles(searchStrings: List<String>): Set<Path>
  override val inputCnt = 1
  override val stickyInput = false

  abstract fun projectSummary(): String

  private enum class RunState {
    IDLE, RUNNING_COMMAND, RUNNING_FIX, SUCCESS, FAILED_RETRYING, FAILED_DONE
  }

  private data class IterationRecord(
    val iteration: Int,
    val exitCode: Int,
    val errorCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val errorSummaries: List<String> = emptyList(),
    val fixApplied: Boolean = false
  )

  fun newSessionController(task: SessionTask, onComplete: (OutputResult) -> Unit = {}) = SessionController(
    task = task,
    settings = settings,
    model = model,
    executeIteration = { t, m, i -> this.executeIteration(t, m, i) },
    onComplete = onComplete,
  )

  open class SessionController(
    private val task: SessionTask,
    val settings: Settings,
    val model: ChatInterface,
    val executeIteration: (SessionTask, ChatInterface, Int) -> OutputResult,
    var updateStatus: (String) -> Unit = { _ -> },
    var lastParsedErrors: ParsedErrors? = null,
    val onComplete: (exitCode: OutputResult) -> Unit = { _ -> },
  ) {

    private val retriesRemaining = AtomicInteger(if (settings.autoFix) settings.maxRetries else 0)
    private val autoRetryEnabled = AtomicBoolean(settings.autoFix)
    private val currentIteration = AtomicInteger(0)
    private val state = AtomicReference(RunState.IDLE)
    private val isRunning = AtomicBoolean(false)
    private val iterationHistory = mutableListOf<IterationRecord>()

    // UI structure: control panel at top, summary area, then iteration details area below
    private val controlPanelBuffer: StringBuilder = task.add("")!!
    private val summaryBuffer: StringBuilder = task.add("")!!
    private val iterationAreaBuffer: StringBuilder = task.add("")!!

    // Detached tasks for each iteration's details, keyed by iteration number
    private val iterationTasks = mutableMapOf<Int, SessionTask>()

    fun start() {
      updateStatus = { message: String ->
        log.info("Status update: $message")
        renderControlPanel(statusOverride = message)
      }
      runIteration()
    }

    private fun renderControlPanel(statusOverride: String? = null) {
      val currentState = state.get()
      val iteration = currentIteration.get()
      val remaining = retriesRemaining.get()
      val autoRetry = autoRetryEnabled.get()
      val running = isRunning.get()

      // --- Status Badge ---
      val (statusIcon, statusText, statusColor) = when {
        statusOverride != null -> Triple("🔧", statusOverride, "#6c757d")
        currentState == RunState.IDLE -> Triple("⏳", "Initializing...", "#6c757d")
        currentState == RunState.RUNNING_COMMAND -> Triple("⚙️", "Running command (Iteration $iteration)...", "#0d6efd")
        currentState == RunState.RUNNING_FIX -> Triple("🔧", "Applying fixes (Iteration $iteration)...", "#0d6efd")
        currentState == RunState.SUCCESS -> Triple("✅", "Build succeeded! No errors found.", "#198754")
        currentState == RunState.FAILED_RETRYING -> Triple(
          "🔄",
          "Failed — auto-retrying ($remaining left)...",
          "#fd7e14"
        )

        currentState == RunState.FAILED_DONE -> Triple("❌", "Failed — manual retry available.", "#dc3545")
        else -> Triple("❓", "Unknown state", "#6c757d")
      }

      // --- Iteration History Timeline ---
      val timeline = if (iterationHistory.isNotEmpty()) {
        val items = iterationHistory.joinToString("") { record ->
          val icon = if (record.exitCode == 0) "✅" else "❌"
          val errInfo =
            if (record.errorCount > 0) "${record.errorCount} error${if (record.errorCount > 1) "s" else ""}" else "clean"
          val time = SimpleDateFormat("HH:mm:ss").format(record.timestamp)
          val tooltip = record.errorSummaries.joinToString("; ") { it.take(60) }
          """<span class="iteration-badge" style="display:inline-flex;align-items:center;gap:2px;padding:2px 8px;margin:2px;border-radius:12px;background:${if (record.exitCode == 0) "#d1e7dd" else "#f8d7da"};font-size:0.82em;cursor:default;" title="$tooltip">$icon #${record.iteration}: $errInfo <span style="color:#888;font-size:0.85em;">($time)</span></span>"""
        }
        """<div style="margin:6px 0;display:flex;flex-wrap:wrap;align-items:center;gap:2px;">
          <span style="font-size:0.8em;color:#666;margin-right:4px;">History:</span>$items
        </div>"""
      } else ""

      // --- Error Trend Summary ---
      val errorTrend = if (iterationHistory.size >= 2) {
        val recent = iterationHistory.takeLast(2)
        val prev = recent[0].errorCount
        val curr = recent[1].errorCount
        when {
          curr == 0 -> """<span style="color:#198754;font-size:0.85em;">All errors resolved! 🎉</span>"""
          curr < prev -> """<span style="color:#fd7e14;font-size:0.85em;">Errors reduced: $prev → $curr (↓${prev - curr})</span>"""
          curr == prev -> """<span style="color:#dc3545;font-size:0.85em;">Error count unchanged: $curr</span>"""
          else -> """<span style="color:#dc3545;font-size:0.85em;">Errors increased: $prev → $curr (↑${curr - prev})</span>"""
        }
      } else ""

      // --- Persistent Error Warnings ---
      val persistentErrors = if (iterationHistory.size >= 2) {
        val allErrors = iterationHistory.flatMap { it.errorSummaries }
        val counts = allErrors.groupingBy { it }.eachCount()
        val persistent = counts.filter { it.value >= 2 }.entries.sortedByDescending { it.value }
        if (persistent.isNotEmpty()) {
          val items = persistent.take(3).joinToString("") { (msg, count) ->
            """<div style="padding:2px 0;font-size:0.82em;">⚠️ <b>${msg.take(80)}</b> — persisted across $count iterations</div>"""
          }
          """<div style="margin:4px 0;padding:6px 10px;background:#fff3cd;border-radius:6px;border-left:3px solid #ffc107;">
            <div style="font-size:0.8em;font-weight:600;color:#856404;margin-bottom:2px;">Persistent Errors</div>
            $items
          </div>"""
        } else ""
      } else ""

      // --- Action Buttons ---
      val buttons = buildString {
        if (!running) {
          val runLabel = when (currentState) {
            RunState.SUCCESS -> "▶ Run Again"
            RunState.FAILED_DONE, RunState.FAILED_RETRYING -> "🔄 Retry"
            else -> "▶ Run"
          }
          append("""<span style="display:inline-block;">""")
          append(task.hrefLink(runLabel, classname = "href-link play-button") {
            if (!isRunning.compareAndSet(false, true)) return@hrefLink
            if (autoRetryEnabled.get()) {
              retriesRemaining.set(settings.maxRetries)
            } else {
              retriesRemaining.set(0)
            }
            runIteration()
          })
          append("</span>&nbsp;&nbsp;")
        }

        // Auto-retry toggle
        append("""<span style="display:inline-block;">""")
        if (autoRetry) {
          append(task.hrefLink("⏸ Disable Auto-Retry", classname = "href-link") {
            log.info("Auto-retry disabled by user")
            autoRetryEnabled.set(false)
            retriesRemaining.set(0)
            renderControlPanel()
          })
        } else {
          append(task.hrefLink("▶ Enable Auto-Retry (${settings.maxRetries} max)", classname = "href-link") {
            log.info("Auto-retry enabled by user")
            autoRetryEnabled.set(true)
            val currentState = state.get()
            if ((currentState == RunState.FAILED_DONE || currentState == RunState.SUCCESS) && !isRunning.get()) {
              retriesRemaining.set(settings.maxRetries)
            }
            renderControlPanel()
          })
        }
        append("</span>")

        // Stop button
        if (running && autoRetry && remaining > 0) {
          append("&nbsp;&nbsp;")
          append("""<span style="display:inline-block;">""")
          append(task.hrefLink("⏹ Stop After Current", classname = "href-link") {
            log.info("User requested stop after current iteration")
            retriesRemaining.set(0)
            autoRetryEnabled.set(false)
            renderControlPanel(statusOverride = "Stopping after current iteration completes...")
          })
          append("</span>")
        }
      }

      // --- Iteration Counter ---
      val iterationInfo = if (iteration > 0) {
        val total = settings.maxRetries + 1
        """<span style="font-size:0.85em;color:#666;">Iteration $iteration""" +
            (if (autoRetry) " / $total max" else "") +
            "</span>"
      } else ""

      // --- Assemble Control Panel ---
      val html = """
        <div class="patch-control-panel" style="border:1px solid #ccc;border-radius:8px;padding:14px 18px;margin:8px 0;background:#f8f9fa;box-shadow:0 1px 3px rgba(0,0,0,0.08);">
          <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:8px;">
            <div style="font-weight:600;font-size:1.1em;color:$statusColor;">$statusIcon $statusText</div>
            <div>$iterationInfo</div>
          </div>
          $timeline
          ${if (errorTrend.isNotBlank()) """<div style="margin:4px 0;">$errorTrend</div>""" else ""}
          $persistentErrors
          <div style="margin-top:10px;padding-top:8px;border-top:1px solid #e9ecef;display:flex;align-items:center;gap:8px;flex-wrap:wrap;">$buttons</div>
        </div>
      """.trimIndent()
      controlPanelBuffer.clear()
      controlPanelBuffer.append(html)
      task.update()
    }

    private fun renderSummary() {
      val currentState = state.get()
      val html = when (currentState) {
        RunState.SUCCESS -> {
          val totalIterations = iterationHistory.size
          val totalErrors = iterationHistory.sumOf { it.errorCount }
          val fixedErrors = if (totalIterations > 1) iterationHistory.first().errorCount else 0
          """<div style="border:1px solid #198754;border-radius:8px;padding:14px 18px;margin:8px 0;background:#d1e7dd;">
           <div style="font-weight:600;font-size:1.05em;color:#0f5132;">✅ Build Successful</div>
           <div style="font-size:0.9em;color:#0f5132;margin-top:4px;">
             Completed in $totalIterations iteration${if (totalIterations > 1) "s" else ""}.
             ${if (fixedErrors > 0) "Fixed $fixedErrors error${if (fixedErrors > 1) "s" else ""} along the way." else ""}
           </div>
         </div>"""
        }

        RunState.FAILED_DONE -> {
          val lastRecord = iterationHistory.lastOrNull()
          val errorCount = lastRecord?.errorCount ?: 0
          """<div style="border:1px solid #dc3545;border-radius:8px;padding:14px 18px;margin:8px 0;background:#f8d7da;">
           <div style="font-weight:600;font-size:1.05em;color:#842029;">❌ Build Failed</div>
           <div style="font-size:0.9em;color:#842029;margin-top:4px;">
             $errorCount error${if (errorCount != 1) "s" else ""} remaining after ${iterationHistory.size} iteration${if (iterationHistory.size > 1) "s" else ""}.
             Use the Retry button above to try again, or review the iteration details below to apply fixes manually.
           </div>
         </div>"""
        }

        else -> ""
      }
      summaryBuffer.clear()
      summaryBuffer.append(html)
      task.update()
    }


    private fun renderIterationArea() {
      // Render expandable sections for each iteration's details
      val sections = iterationTasks.entries.sortedByDescending { it.key }.joinToString("\n") { (iter, iterTask) ->
        val record = iterationHistory.find { it.iteration == iter }
        val icon = if (record?.exitCode == 0) "✅" else "❌"
        val label = "$icon Iteration $iter" + (record?.let { r ->
          val errInfo =
            if (r.errorCount > 0) " — ${r.errorCount} error${if (r.errorCount > 1) "s" else ""}" else " — success"
          errInfo
        } ?: "")
        val isLatest = iter == currentIteration.get()
        val isRunning = this.isRunning.get() && isLatest
        """<details${if (isLatest) " open" else ""}>
          <summary style="cursor:pointer;font-weight:500;padding:6px 0;font-size:0.95em;">$label</summary>
          <div style="padding:4px 0 12px 12px;border-left:2px solid #dee2e6;margin-left:8px;">
            ${iterTask.placeholder}
          </div>
        </details>"""
      }
      iterationAreaBuffer.clear()
      iterationAreaBuffer.append(
        if (sections.isNotBlank()) """
          <div style="margin-top:8px;">
            <div style="font-weight:600;font-size:0.9em;color:#495057;margin-bottom:4px;">Iteration Details</div>
            $sections
          </div>
        """.trimIndent() else ""
      )
      task.update()
    }

    private fun runIteration() {
      val iteration = currentIteration.incrementAndGet()
      state.set(RunState.RUNNING_COMMAND)
      isRunning.set(true)
      renderControlPanel()
      renderSummary()

      // Create a detached task for this iteration's output
      val iterTask = task.ui.newTask(false)
      iterationTasks[iteration] = iterTask
      renderIterationArea()

      Thread {
        try {
          log.info("Starting run thread, iteration $iteration")
          val childModel = model.getChildClient(task)
          // Wire up status updates to transition state for fix phase
          val originalUpdateStatus = updateStatus
          updateStatus = { message: String ->
            if (message.contains("fix", ignoreCase = true) || message.contains("Applying", ignoreCase = true)) {
              state.set(RunState.RUNNING_FIX)
            }
            log.info("Status update: $message")
            renderControlPanel(statusOverride = message)
          }
          val result = executeIteration(iterTask, childModel, iteration)
          updateStatus = originalUpdateStatus
          log.info("Iteration completed with exit code: ${result.exitCode}")

          val errorCount = result.errors?.errors?.size
            ?: lastParsedErrors?.errors?.size
            ?: 0
          val errorSummaries = (result.errors?.errors ?: lastParsedErrors?.errors ?: emptyList())
            .mapNotNull { it.message }

          iterationHistory.add(
            IterationRecord(
              iteration = iteration,
              exitCode = result.exitCode,
              errorCount = errorCount,
              errorSummaries = errorSummaries,
              fixApplied = result.exitCode != 0
            )
          )

          if (result.exitCode == 0) {
            state.set(RunState.SUCCESS)
            isRunning.set(false)
            renderControlPanel()
            renderSummary()
            renderIterationArea()
            onComplete(result)
          } else {
            val remaining = retriesRemaining.get()
            if (remaining > 0 && autoRetryEnabled.get()) {
              retriesRemaining.decrementAndGet()
              state.set(RunState.FAILED_RETRYING)
              log.info("Triggering retry ($remaining remaining)")
              renderControlPanel()
              renderSummary()
              renderIterationArea()
              runIteration()
            } else {
              state.set(RunState.FAILED_DONE)
              isRunning.set(false)
              renderControlPanel()
              renderSummary()
              renderIterationArea()
              onComplete(result)
            }
          }
        } catch (e: Exception) {
          log.error("Error during run iteration", e)
          iterationHistory.add(
            IterationRecord(
              iteration = iteration,
              exitCode = -1,
              errorCount = 0,
              errorSummaries = listOf("Internal error: ${e.message}")
            )
          )
          state.set(RunState.FAILED_DONE)
          isRunning.set(false)
          iterTask.error(e)
          renderControlPanel()
          renderSummary()
          renderIterationArea()
          onComplete(OutputResult(exitCode = -1, output = "Internal error: ${e.message}"))
        }
      }.start()
    }
  }


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

  /**
   * Executes a single iteration: runs the command, parses errors, and applies fixes.
   * Called by SessionController for each iteration.
   */
  internal fun executeIteration(
    task: SessionTask,
    model: ChatInterface,
    iteration: Int = 0
  ): OutputResult {
    log.info("Starting iteration $iteration with settings: ${JsonUtil.toJson(settings)}")
    // Phase 1: Run the command
    val commandTask = task.ui.newTask(false)
    task.add("<div style='font-weight:600;font-size:0.9em;color:#495057;margin:8px 0 4px;'>Command Output</div>")
    task.add(commandTask.placeholder)
    val outputResult = output(commandTask, settings)
    log.info("Command execution completed with exit code: ${outputResult.exitCode}")
    commandTask.complete()
    if (outputResult.exitCode == 0) {
      log.info("Command executed successfully, no fixes needed")
      task.add("""<div style="padding:8px 12px;background:#d1e7dd;border-radius:6px;margin:8px 0;color:#0f5132;font-weight:500;">✅ Command executed successfully</div>""")
      task.complete()
      return outputResult
    }
    // Phase 2: Parse errors
    val updateStatus = updateStatus ?: {}
    updateStatus("Parsing errors (Iteration $iteration)...")
    val fixTask = task.ui.newTask(false)
    task.add("<div style='font-weight:600;font-size:0.9em;color:#495057;margin:8px 0 4px;'>Fix Details</div>")
    task.add(fixTask.placeholder)
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
      val progressHeader = fixTask.header("Processing ${parsedErrors.errors?.size ?: 0} error(s)...", 3)
      // Show error analysis in expandable section
      val analysisHtml = buildString {
        append("<details><summary style='cursor:pointer;font-size:0.9em;color:#666;'>Error Analysis Details</summary>")
        append("<div style='padding:8px;'>")
        val map = mapOf(
          "Text" to plan.text.renderMarkdown(true),
          "JSON" to "${tripleTilde}json\n${JsonUtil.toJson(parsedErrors)}\n$tripleTilde".renderMarkdown(true),
          "Process Details" to "Exit Code: ${outputResult.exitCode}\nCommand Output:\n$tripleTilde\n${outputResult.output}\n$tripleTilde".renderMarkdown(
            true
          )
        ).filter { it.value.isNotBlank() }
        append(TabbedDisplay.displayMapInTabs(map))
        append("</div></details>")
      }
      fixTask.add(analysisHtml)
      previousParsedErrorsRecords.add(ParsedErrorRecord(parsedErrors, iteration = iteration))
      log.info("Starting to fix all errors")
      updateStatus("Applying fixes (Iteration $iteration)...")
      fixAllErrors(
        task = fixTask,
        plan = plan,
        settings = settings,
        progressHeader = progressHeader,
        model = model,
        iteration = iteration
      )
      fixTask.complete()
    } catch (e: Exception) {
      log.error("Error during fix process", e)
      fixTask.error(e)
    }
    task.complete()
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
    val completedCount = AtomicInteger(0)
    val totalErrors = filteredErrors.groupBy { it.message }.size
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
                    it.readText().contains(query.pattern, ignoreCase = true)
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
            val completed = completedCount.incrementAndGet()
            statusBuffer.set("Status: Complete ✅")
            subSession.update()
            updateStatus("Fixing errors ($completed/$totalErrors complete)...")
          }
        }
      }.toTypedArray().onEach { it.get() }
    log.info("All error fixes have been submitted")
    progressHeader?.set("✅ Finished processing $totalErrors error group${if (totalErrors > 1) "s" else ""}")
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
      parsingModel = fastModel,
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
      prefilterFilename = ::prefilterFilename
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