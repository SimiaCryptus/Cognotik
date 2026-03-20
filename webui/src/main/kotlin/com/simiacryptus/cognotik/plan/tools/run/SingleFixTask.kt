package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.PatchApp
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore

class SingleFixTask(
  orchestrationConfig: OrchestrationConfig, planTask: SingleFixTaskExecutionConfigData?
) : AbstractTask<SingleFixTask.SingleFixTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
  companion object {
    private val log = LoggerFactory.getLogger(SingleFixTask::class.java)

    @JvmStatic
    val SingleFix = TaskType(
      name = "SingleFix",
      category = "Execution",
      taskClass = SingleFixTask::class.java,
      executionConfigClass = SingleFixTaskExecutionConfigData::class.java,
      taskSettingsClass = SingleFixTaskTypeConfig::class.java,
      description = "Analyze a log file and fix errors found in it without running commands",
      tooltipHtml = """
    Analyzes a provided log file for errors and attempts to fix them in the codebase.
    <ul>
    <li>Does not execute commands</li>
    <li>Requires a log file path</li>
    <li>Performs one pass of fixes</li>
    </ul>
    """,
    )
  }

  class SingleFixTaskTypeConfig(
    name: String? = SingleFix.name,
    model: ApiChatModel? = null,
  ) : TaskTypeConfig(SingleFix.name, name, model)

  class SingleFixTaskExecutionConfigData(
    @Description("The path to the log file containing errors") var logFile: String? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : ValidatedObject, TaskExecutionConfig(
    task_type = SingleFix.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ) {
    override fun validate(): String? {
      if (logFile.isNullOrBlank()) {
        return "logFile must not be empty"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return "Analyze the log file '${executionConfig?.logFile}' and fix any errors found."
  }

  override val typeConfig: SingleFixTaskTypeConfig
    get() = super.typeConfig as SingleFixTaskTypeConfig

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val semaphore = Semaphore(0)
    Retryable(task = task) {
      val subTask = task.newTask()

      fun execute() {
        subTask.ui.pool.submit {
          val transcript = createTranscript(subTask)
          subTask.add(transcript.second.renderMarkdown())
          val model =
            (typeConfig.model?.let { it.instance(orchestrationConfig.user) } ?: defaultSmart).getChildClient(subTask)
          val markdownTranscript = transcript.first
          try {
            markdownTranscript?.write("## Single Fix Task Execution\n\n".toByteArray())

            val logFilePath = executionConfig?.logFile ?: throw IllegalArgumentException("Log file not specified")
            val logFile = agent.root.toFile().resolve(logFilePath)
            if (!logFile.exists()) throw IllegalArgumentException("Log file not found: $logFile")

            val workingDir =
              orchestrationConfig.workingDir?.let { agent.root.toFile().resolve(it) } ?: agent.root.toFile()

            markdownTranscript?.write("Analyzing log file: ${logFile.absolutePath}\n".toByteArray())

            object : PatchApp(
              root = agent.root.toFile(),
              settings = PatchApp.Settings(
                // Dummy command to ensure workingDirectory property works correctly in PatchApp
                commands = listOf(
                  PatchApp.CommandSettings(
                    executable = File("dummy"), workingDirectory = workingDir
                  )
                ),
                autoFix = orchestrationConfig.autoFix,
                includeLineNumbers = false,
              ),
              model = model,
              parsingModel = defaultFast,
              processor = orchestrationConfig.processor ?: PatchProcessors.Fuzzy,
            ) {
              override fun codeFiles(): Set<Path> {
                return FileSelectionUtils.filteredWalk(root).filter { it.length() < 1024 * 1024 / 2 }
                  .map { root.toPath().relativize(it.toPath()) }.toSet()
              }

              override fun projectSummary(): String {
                val codeFiles = codeFiles()
                return codeFiles.asSequence().filter { root.toPath().resolve(it).toFile().exists() }.distinct().sorted()
                  .joinToString("\n") { path ->
                    "* $path - ${root.toPath().resolve(path).toFile().length()} bytes"
                  }
              }

              override fun output(
                task: SessionTask, settings: Settings, tabs: TabbedDisplay
              ): OutputResult {
                // Return exit code 1 to trigger the fix logic in PatchApp.run
                return OutputResult(1, logFile.readText())
              }

              override fun searchFiles(searchStrings: List<String>): Set<Path> {
                return searchStrings.flatMap { searchString ->
                  FileSelectionUtils.filteredWalk(workingDir)
                    .filter { it.readText().contains(searchString, ignoreCase = true) }.map { it.toPath() }.toList()
                }.toSet()
              }
            }.run(
              task = subTask, model = model
            ).apply {
              resultFn("### Success\nLog analysis and fix generation completed.")
              semaphore.release()
              subTask.complete()
            }
          } catch (e: Throwable) {
            subTask.error(e)
            log.error("Critical error during SingleFixTask execution", e)
            markdownTranscript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())

            if (orchestrationConfig.autoFix) {
              semaphore.release()
              subTask.complete()
            }
          } finally {
            markdownTranscript?.close()
          }
        }
      }

      if (orchestrationConfig.autoFix) {
        execute()
      } else {
        subTask.add(subTask.ui.hrefLink("▶ Run SingleFix", "btn btn-primary") {
          execute()
        }.renderMarkdown())
      }
      subTask.placeholder
    }
    try {
      semaphore.acquire()
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
    task.complete()
  }

  private fun createTranscript(task: SessionTask): Pair<FileOutputStream?, String> {
    val transcriptFile =
      this.javaClass.simpleName + "_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
    val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
    val markdownTranscript = file?.outputStream()
    val html = "Writing transcript to <a href='$link' target='_blank'>$link</a>"
    return Pair(markdownTranscript, html)
  }
}