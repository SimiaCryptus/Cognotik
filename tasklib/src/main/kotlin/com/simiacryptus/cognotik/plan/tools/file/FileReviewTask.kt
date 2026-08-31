package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.extractDocumentContent
import com.simiacryptus.cognotik.plan.tools.file.FileReviewTask.FileReviewTaskExecutionConfigData
import com.simiacryptus.cognotik.ui.Retryable
import com.simiacryptus.cognotik.ui.Retryable.Companion.async
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

/**
 * Read-only review task: loads the requested file(s) and reports on the specific
 * details requested by the caller (the "queries"). No files are modified.
 *
 * Typical use: gather facts about existing code (APIs, invariants, call sites,
 * configuration values, risks) so that later tasks can act on a concise summary
 * instead of the raw file contents.
 */
class FileReviewTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: FileReviewTaskExecutionConfigData?
) : AbstractFileTask<FileReviewTaskExecutionConfigData>(orchestrationConfig, planTask) {

  class FileReviewTaskExecutionConfigData(
    related_files: List<String>? = null,
    @Description("The specific questions / details that must be reported on for the given files")
    val queries: List<String>? = null,
    @Description("Optional guidance for the shape of the report, e.g. 'markdown table', 'bullet list per file', 'JSON'")
    val report_format: String? = null,
    @Description("Whether to extract and review text content from non-text files (PDF, HTML, etc.)")
    val extractContent: Boolean = false,
    @Description("Whether the report must cite file paths and line numbers for every finding")
    val requireCitations: Boolean = true,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null,
  ) : FileTaskExecutionConfig(
    task_type = FileReview.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    related_files = related_files,
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (listOf<String>(main_file).orEmpty().none { it.isNotBlank() } &&
        related_files.orEmpty().none { it.isNotBlank() }
      ) {
        return "At least one file must be specified in either 'main_file' or 'related_files'"
      }
      if (queries.orEmpty().none { it.isNotBlank() } && task_description.orEmpty().isBlank()) {
        return "At least one non-blank entry in 'queries' (or a 'task_description') is required"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment() = """
  FileReview - Read files and report on requested details (read-only; no edits are made)
    * Specify the main file and/or related files (incl. glob patterns) to be read
    * Specify 'queries': the concrete questions / details that must be answered from those files
    * Optionally specify 'report_format' to control the shape of the report
    * Use this to gather facts/context for later tasks instead of modifying code
  """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    this.orchestrationConfig = orchestrationConfig
    renderTaskHeader(task)

    val typeConfig = typeConfig ?: throw RuntimeException("TypeConfig is missing")
    val chatInterface = (typeConfig.model?.instance(orchestrationConfig.user) ?: defaultSmart).getChildClient(task)
    val transcript = task.newUserFileStream(transcriptFile(".review.md"))
    val semaphore = Semaphore(0)
    val reportRef = AtomicReference("")

    try {
      transcript?.write("# File Review Task Transcript\n\n".toByteArray())

      Retryable(task, process = { task: ISessionTask ->

        // 1. Prepare context
        val fileContext = buildFileContext()
        val reviewRequest = buildReviewRequest()

        // 2. Log context
        transcript?.write(
          """
  ## Context Data
  <details>
  <summary>Review Request &amp; File Context</summary>

  ### Review Request
  $reviewRequest

  ### File Context
  ${fileContext.ifBlank { "None" }}
  </details>

                  """.toByteArray()
        )

        if (fileContext.isBlank()) {
          log.warn("FileReviewTask found no readable file content for {}", executionConfig?.related_files)
          task.add("No readable file content was found for the requested files.".renderMarkdown())
        }

        val chatAgent = ChatAgent(
          name = "FileReview",
          prompt = getSystemPrompt(),
          model = chatInterface,
          temperature = this.orchestrationConfig.temperature,
        )

        task.add("Reviewing files...".renderMarkdown())

        // 3. Execute AI
        val report = chatAgent.answer(
          (messages + listOf(
            fileContext,
            reviewRequest,
          )).filter { it.isNotBlank() }
        )
        reportRef.set(report)

        // 4. Log response
        transcript?.write(
          """
  ## Review Report
  <details>
  <summary>Raw Output</summary>

  $report
  </details>

                  """.toByteArray()
        )

        // 5. Display
        val tabs = TabbedDisplay(task)
        tabs["Report"] = report.renderMarkdown()
        tabs["Request"] = reviewRequest.renderMarkdown()
        tabs["Files"] = reviewedFilesMarkdown().renderMarkdown()

        task.complete()
        semaphore.release()
        transcript?.flush()
      }.async(task))

      semaphore.acquire()

      val report = reportRef.get().ifBlank { "No review output was produced." }
      transcript?.write("\n## Completion\n\nReview complete (${report.length} chars).\n".toByteArray())
      resultFn(report)

    } catch (e: Throwable) {
      task.error(e)
      log.error("Error in FileReviewTask", e)
      transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      throw e
    } finally {
      transcript?.flush()
    }
  }

  /**
   * Builds the file context, optionally extracting readable text from non-text
   * documents (PDF/HTML/etc.) when [FileReviewTaskExecutionConfigData.extractContent] is set.
   */
  private fun buildFileContext(): String = getInputFileCode { file ->
    val resolved = root.resolve(file.toString()).toFile()
    if (executionConfig?.extractContent == true && resolved.isFile && !isTextFile(resolved)) {
      try {
        "# $file\n\n$TRIPLE_TILDE\n${extractDocumentContent(resolved)}\n$TRIPLE_TILDE\n"
      } catch (e: Exception) {
        log.warn("Failed to extract content from $file: ${e.message}", e)
        "# $file\n\n(content could not be extracted: ${e.message})\n"
      }
    } else {
      formatFileForLLM(file).toString()
    }
  }

  private fun buildReviewRequest(): String {
    val config = executionConfig
    val queries = config?.queries?.filter { it.isNotBlank() } ?: emptyList()
    return buildString {
      appendLine("# Review Request")
      appendLine()
      config?.task_description?.takeIf { it.isNotBlank() }?.let {
        appendLine("## Objective")
        appendLine()
        appendLine(it)
        appendLine()
      }
      if (queries.isNotEmpty()) {
        appendLine("## Required Details")
        appendLine()
        queries.forEachIndexed { index, query -> appendLine("${index + 1}. $query") }
        appendLine()
      }
      config?.report_format?.takeIf { it.isNotBlank() }?.let {
        appendLine("## Requested Report Format")
        appendLine()
        appendLine(it)
        appendLine()
      }
      appendLine("## Files Under Review")
      appendLine()
      appendLine(reviewedFilesMarkdown())
    }
  }

  private fun reviewedFilesMarkdown(): String {
    val files = ((executionConfig?.related_files ?: emptyList()) +
        listOfNotNull(executionConfig?.main_file))
      .filter { it.isNotBlank() }
      .distinct()
    return if (files.isEmpty()) "(none specified)" else files.joinToString("\n") { "* $it" }
  }

  private fun getSystemPrompt(): String {
    val queries = executionConfig?.queries?.filter { it.isNotBlank() } ?: emptyList()
    return """
      You are a meticulous code/document reviewer. You are in READ-ONLY mode:
      do not propose patches, diffs, or file edits - only report what the files contain.

      Answer every requested detail using ONLY the provided file content:
      - Address each requested detail explicitly, in the order given, as its own section or row
      - ${if (executionConfig?.requireCitations != false) "Cite the file path and line number(s) supporting each statement" else "Reference the relevant file paths"}
      - Quote short, relevant snippets (a few lines at most) rather than large blocks
      - If the answer cannot be determined from the supplied content, say "Not found in provided files" and explain what is missing
      - Do not speculate about code that was not supplied; clearly label any inference as an assumption

      Finish with:
      - A short "Summary" of the key findings
      - "Risks / Gaps": anything ambiguous, inconsistent, or missing that a follow-up task should resolve
      ${if (queries.isEmpty()) "" else "\n      Requested details:\n" + queries.joinToString("\n") { "      - $it" }}
      ${
      executionConfig?.report_format?.takeIf { it.isNotBlank() }?.let { "\n      Preferred output format: $it" } ?: ""
    }
          """
  }

  private fun isTextFile(file: File): Boolean {
    val textExtensions = setOf(
      "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs",
      "c", "cpp", "h", "hpp", "css", "html", "xml", "json", "yaml",
      "yml", "properties", "gradle", "maven"
    )
    return textExtensions.contains(file.extension.lowercase())
  }

  companion object {
    private val log = LoggerFactory.getLogger(FileReviewTask::class.java)

    @JvmStatic
    val FileReview = TaskType(
      name = "FileReview",
      category = "File",
      taskClass = FileReviewTask::class.java,
      executionConfigClass = FileReviewTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Read files and report on specific requested details (read-only analysis)",
      tooltipHtml = """
                                Reads the specified files and reports on the details you ask for, without modifying anything.
                                <ul>
                                  <li>Answers an explicit list of queries about the supplied files</li>
                                  <li>Cites file paths and line numbers for each finding</li>
                                  <li>Optionally extracts text from non-text documents (PDF, HTML, etc.)</li>
                                  <li>Flags missing, ambiguous, or inconsistent information</li>
                                  <li>Produces a compact report usable as context by downstream tasks</li>
                                </ul>
                              """,
    )
  }
}