package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.docs.isDocumentFile
import com.simiacryptus.cognotik.plan.ExecutionState
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.exists

abstract class AbstractTask<T : TaskExecutionConfig, U : TaskTypeConfig>(
  val orchestrationConfig: OrchestrationConfig,
  open val executionConfig: T?
) {

  var state: TaskState? = TaskState.Pending

  protected open val root: Path
    get() = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
      ?: throw IllegalStateException("Working directory not set")

  open val taskType: String = executionConfig?.task_type ?: this::class.simpleName ?: "UnknownTask"

  open val typeConfig: U?
    get() = taskType.let { task_type -> orchestrationConfig.taskSettings.values.firstOrNull { it.task_type == task_type } as? U }
  val verbose: Boolean get() = typeConfig?.verbose == true

  open val defaultSmart: ChatInterface
    get() = typeConfig?.model?.let { it.instance(orchestrationConfig.user) } ?: orchestrationConfig.defaultSmart

  open val defaultFast: ChatInterface
    get() = orchestrationConfig.defaultFast

  enum class TaskState {
    Pending,
    InProgress,
    Completed,
  }

  open fun getPriorCode(executionState: ExecutionState?) =
    executionConfig?.task_dependencies?.joinToString("\n\n\n") { dependency ->
      "# $dependency\n\n${executionState?.taskResult[dependency] ?: ""}"
    } ?: ""

  protected open fun renderTaskHeader(task: SessionTask, title: String? = null) {
    task.header(title ?: taskType)
    executionConfig?.task_description?.let {
      task.add("**Description:** $it".renderMarkdown())
    }
  }


  @Suppress("unused")
  fun String.wrapInDetails(summary: String): String {
    //return "<details><summary>$summary</summary>\n\n${this}\n</details>"
    return this
  }

  protected open fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    val footerTask = ui.newTask(false)
    lateinit var textHandle: StringBuilder
    @Suppress("AssignedValueIsNeverRead")
    textHandle = footerTask.complete(
      """<div style="margin-top: 20px; border-top: 1px solid #ccc; padding-top: 10px;">""" + ui.hrefLink(
        "Accept Result",
        classname = "href-link cmd-button"
      ) {
        try {
          textHandle.set("""<div class="cmd-button">Accepted</div>""")
          footerTask.complete()
        } catch (e: Throwable) {
          log.warn("Error", e)
        }
        fn()
      } + "</div>")!!
    return footerTask.placeholder
  }

  abstract fun promptSegment(): String

  abstract fun run(
    agent: TaskOrchestrator,
    messages: List<String> = listOf(),
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig,
  )

  fun getInputFileContent(
    files: List<String>?,
    root: Path,
    treatDocumentsAsText: Boolean = true,
  ): String = (files ?: listOf())
    .flatMap { pattern: String ->
      if (root.resolve(pattern).exists()) {
        return@flatMap listOf(root.resolve(pattern).toFile())
      }
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (FileSelectionUtils.filteredWalk(root.toFile(), treatDocumentsAsText = treatDocumentsAsText) {
        when {
          FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
          it.isDirectory -> true
          !matcher.matches(root.relativize(it.toPath())) -> false
          else -> true
        }
      })
    }.filter { file ->
      file.isFile && file.exists()
    }
    .distinct()
    .sortedBy { it }
    .joinToString("\n\n") { relativePath ->
      val file = root.toFile().resolve(relativePath)
      try {
        if (treatDocumentsAsText && file.isDocumentFile()) {
          file.getDocumentReader().getText()
        } else {
          "# $relativePath\n\n```\n${file.readText()}\n```"
        }
      } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
      }
    }

  fun SessionTask.newUserFileStream(transcriptFile: String, name: String = "Transcript"): FileOutputStream? {
    val (link, file) = Pair(linkTo(transcriptFile), resolveUserFile(transcriptFile))
    val markdownTranscript = file?.outputStream()
    add("[$name](${link.removeSuffix(".md")}.html)".renderMarkdown())
    return markdownTranscript
  }

  fun SessionTask.newSystemFileStream(transcriptFile: String): FileOutputStream? {
    val (link, file) = Pair(linkTo(transcriptFile), resolveSystemFile(transcriptFile))
    val markdownTranscript = file?.outputStream()
    add("[Transcript](${link.removeSuffix(".md")}.html)".renderMarkdown())
    return markdownTranscript
  }

  fun transcriptFile(name: String): String = "transcript/${name}_${now()}.md"

  fun transcriptFile(): String = getOutputFile(".md") ?: transcriptFile(taskType)

  open fun getOutputFile(extension: String): String? = executionConfig?.files?.let {
    when {
      it.filter { it.endsWith(extension) }.size == 1 -> it.first { it.endsWith(extension) }
      else -> null
    }
  }

  fun createTabbedDisplay(task: SessionTask) = TabbedDisplay(task)
  open fun writeToTranscript(stream: FileOutputStream, string: String) {
    stream.write(string.toByteArray())
    stream.flush()
  }

  companion object {
    val log = LoggerFactory.getLogger(AbstractTask::class.java)
    private fun now(): String? = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
  }
}