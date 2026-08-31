package com.simiacryptus.cognotik.autofix

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApiChatModel
import com.simiacryptus.cognotik.ui.Retryable
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.util.resolveTool
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.util.concurrent.Semaphore

class AutoFixTask(
  orchestrationConfig: OrchestrationConfig, planTask: AutoFixTaskExecutionConfigData?
) : AbstractTask<AutoFixTask.AutoFixTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

  companion object {
    private val log = LoggerFactory.getLogger(AutoFixTask::class.java)

    @JvmStatic
    val AutoFix = TaskType(
      name = "AutoFix",
      category = "Execution",
      taskClass = AutoFixTask::class.java,
      executionConfigClass = AutoFixTaskExecutionConfigData::class.java,
      taskSettingsClass = AutoFixTaskTypeConfig::class.java,
      description = "Run a command and automatically fix any issues that arise",
      tooltipHtml = "<p>Executes a command and automatically fixes any issues that arise.</p>" +
          "<ul>" +
          "<li>Specify commands and working directories</li>" +
          "<li>Supports multiple commands and directories</li>" +
          "<li>Interactive approval mode</li>" +
          "<li>Output diff formatting</li>" +
          "</ul>",
    )
  }

  class AutoFixTaskTypeConfig(
    name: String? = AutoFix.name,
    model: ApiChatModel? = null,
    var promptTemplate: String = buildString {
      appendLine("SelfHealing - Run a command and automatically fix any issues that arise")
      appendLine("  * Specify the commands to be executed along with their working directories")
      appendLine("  * Each command's working directory should be specified relative to the root directory")
      appendLine("  * Provide the commands and their arguments in the 'commands' field")
      appendLine("  * Each command should be a list of strings")
      appendLine("  * Available commands:")
      appendLine("  {executables}")
    }
  ) : TaskTypeConfig(AutoFix.name, name, model)

  class AutoFixTaskExecutionConfigData(
    @Description("The commands to be executed with their respective working directories. Each entry specifies a command and an optional working directory.")
    var commands: MutableList<CommandWithWorkingDir>? = ArrayList(),
    @Description("A description of what this task should accomplish")
    task_description: String? = null,
    @Description("List of task IDs that must complete before this task can run")
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : ValidatedObject, TaskExecutionConfig(
    task_type = AutoFix.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  data class CommandWithWorkingDir(
    @Description("The executable to be run, as a relative path or simple command name. DO NOT invoke a shell unless specifically instructed. DO NOT use shell features like &&, |, >, etc. These features are provided by the requested script and the runtime harness.")
    var executable: String = "",
    @Description("The arguments for the command; optional. DOES NOT SUPPORT shell features, such as quoting, &&, |, >, etc.")
    var arguments: MutableList<String> = ArrayList(),
    @Description("The relative path of the working directory for this command, relative to the project root. Null means the project root.")
    var working_dir: String? = null
  ) : ValidatedObject {
    override fun validate(): String ? {
      if (executable.isBlank()) {
        return "command must not be empty"
      }
      return null
    }
  }

  override fun promptSegment(): String {
    return typeConfig.promptTemplate.trim()
  }

  override val typeConfig: AutoFixTaskTypeConfig
    get() = super.typeConfig as AutoFixTaskTypeConfig

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
        subTask.pool.submit {
          val transcriptPath = transcriptFile()
          val transcript: FileOutputStream? = subTask.newUserFileStream(transcriptPath)
          val model = (typeConfig.model?.instance(orchestrationConfig.user) ?: defaultSmart).getChildClient(subTask)
          val fastModel = defaultFast.getChildClient(subTask)
          try {
            transcript?.write("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
            transcript?.write("## Self-Healing Task Execution\n\n".toByteArray())
            transcript?.write("### Commands\n".toByteArray())
            executionConfig?.commands?.forEachIndexed { index, cmd ->
              transcript?.write("${index + 1}. `${(listOf(cmd.executable) + cmd.arguments).joinToString(" ")}` in `${cmd.working_dir ?: orchestrationConfig.workingDir ?: agent.root}`\n".toByteArray())
            }
            transcript?.write("\n".toByteArray())
            CmdPatchApp(
              root = agent.root,
              settings = PatchApp.Settings(
                commands = executionConfig?.commands?.map { commandWithDir ->
                  val alias = (listOf(commandWithDir.executable) + commandWithDir.arguments).firstOrNull()
                  PatchApp.CommandSettings(
                    executable = alias?.resolveTool(this.root)
                    ?: throw IllegalArgumentException("Command not found: $alias"),
                    arguments = (listOf(commandWithDir.executable) + commandWithDir.arguments).drop(1)
                      .joinToString(" "),
                    workingDirectory = ((commandWithDir.working_dir
                      ?: orchestrationConfig.workingDir)?.let { agent.root.toFile().resolve(it) }
                      ?: agent.root.toFile()).apply { mkdirs() },
                    additionalInstructions = ""
                  )
                } ?: emptyList(),
                autoFix = orchestrationConfig.autoFix,
                includeLineNumbers = false,
              ),
              files = agent.files,
              model = model,
              fastModel = fastModel,
              processor = orchestrationConfig.processor,
            ).newSessionController(
              task = subTask,
              onComplete = { result ->
                transcript?.write("\n### Execution Result\n* **Exit Code:** $result\n".toByteArray())
                transcript?.write("</div>\n\n".toByteArray())
                transcript?.write("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
                when {
                  result.exitCode == 0 -> {
                    transcript?.write("## Result: Success\n\nAll commands executed successfully with exit code 0.\n".toByteArray())
                    result.output.ifBlank { null }?.apply {
                      transcript?.write("\n### Command Output\n```\n${this.truncateMiddle(5*1024)}\n```\n".toByteArray())
                    }
                    transcript?.write("</div>\n\n".toByteArray())
                    if (orchestrationConfig.autoFix) {
                      resultFn("### Success\nAll commands executed successfully with exit code 0.")
                      semaphore.release()
                      subTask.complete()
                      transcript?.close()
                    } else {
                      subTask.add(
                        subTask.hrefLink("Accept & Continue", "btn btn-primary") {
                          resultFn("### Success\nUser accepted command execution results.")
                          semaphore.release()
                          subTask.complete()
                          transcript?.close()
                        }.renderMarkdown()
                      )
                    }
                  }

                  else -> {
                    log.warn("Command failed with exit code $result")
                    transcript?.write("## Result: Failed\n\nCommands failed with exit code $result.\n".toByteArray())
                    result.output.ifBlank { null }?.apply {
                      transcript?.write("\n### Command Output\n```\n${this.truncateMiddle(5*1024)}\n```\n".toByteArray())
                    }
                    transcript?.write("</div>\n\n".toByteArray())
                    subTask.add(
                      subTask.hrefLink("Ignore Error", "href-link cmd-button") {
                        resultFn("### Warning\nCommands failed with exit code $result, but error was ignored by user.")
                        semaphore.release()
                        subTask.complete()
                        transcript?.close()
                      }.renderMarkdown()
                    )
                  }
                }
              }
            ).start()
          } catch (e: Throwable) {
            // Triple Log Rule: UI, SLF4J, and Transcript
            subTask.error(e)
            log.error("Critical error during AutoFixTask execution", e)
            transcript?.write("</div>\n\n".toByteArray())
            transcript?.write("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
            transcript?.write("## Error\n\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
            transcript?.write("</div>\n\n".toByteArray())

            // FIXED: Call resultFn in all terminal cases
            resultFn("### Error\nCritical error during task execution: ${e.message}")
            semaphore.release()
            subTask.complete()
            transcript?.close()
          }
        }
      }
      if (orchestrationConfig.autoFix) {
        execute()
      } else {
        subTask.add(subTask.hrefLink("▶ Run AutoFix", "btn btn-primary") {
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

}

private fun String.truncateMiddle(maxLen: Int): String {
  if (this.length <= maxLen) return this
  val partLen = maxLen / 2 - 3
  return this.take(partLen) + "\n...\n" + this.takeLast(partLen)
}

