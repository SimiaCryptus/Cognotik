package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.util.concurrent.Semaphore

class RunToolTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: RunToolTaskExecutionConfigData?,
) : AbstractTask<RunToolTask.RunToolTaskExecutionConfigData, RunToolTask.RunToolTaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class RunToolTaskTypeConfig(
    task_type: String = RunTool.name,
    model: ApiChatModel? = null,
    name: String? = RunTool.name,
  ) : TaskTypeConfig(
    task_type = task_type,
    name = name,
    model = model,
  )

  class RunToolTaskExecutionConfigData(
    @Description("The tool to run")
    var tool: String? = null,
    @Description("The arguments to pass to the tool")
    var args: List<String>? = null,
    @Description("The relative file path of the working directory")
    var workingDir: String? = null,
    @Description("A description of the task's purpose")
    task_description: String? = null,
    @Description("List of task IDs this task depends on")
    task_dependencies: List<String>? = null,
    @Description("The current state of the task")
    state: TaskState? = null
  ) : TaskExecutionConfig(
    task_type = RunTool.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment(): String {
    val executables: List<String>? =
      ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(orchestrationConfig.user)
        .tools.flatMap { it.component1()?.getExecutables() ?: emptyList() }.distinct().sorted()

    return """
            RunTool - Execute external CLI tools with custom arguments.
            * **Use when:** You need to run compilers, linters, search tools, or custom scripts.
            * **Available tools:** ${executables?.joinToString(", ") ?: "None"}
            * **Inputs:** Specify the `tool` name and a list of `args`.
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newUserFileStream(transcriptFile())
    task.ui.pool.submit {
      try {
        log.info("Starting RunToolTask for tool: ${executionConfig?.tool}")
        val tabs = TabbedDisplay(task)

        val context = getPriorCode(agent.executionState)
        if (context.isNotBlank()) {
          tabs["Context"] = "```\n$context\n```".renderMarkdown()
          transcript?.write("# Context\n<details><summary>Prior Code</summary>\n\n```\n$context\n```\n</details>\n".toByteArray())
        }

        val tool = executionConfig?.tool ?: throw IllegalArgumentException("Tool not specified")
        val args = executionConfig?.args ?: emptyList()
        val workingDir = executionConfig?.workingDir?.let { File(it) }
          ?: File(orchestrationConfig.absoluteWorkingDir ?: ".")
        val tools =
          ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(orchestrationConfig.user).tools
        val executionConfig = this.executionConfig ?: throw IllegalStateException("Execution config is null")
        val executable =
          tools.find { it.provider?.getExecutables()?.contains(executionConfig.tool) == true }?.let { toolData ->
            if (toolData.path != null) {
              toolData.provider!!.resolve(toolData.path).forEach { resolved -> File(resolved) }
            }
            val resolved: String? = toolData.resolve(executionConfig.tool)
            if (resolved != null) {
              File(resolved)
            } else {
              null
            }
          }
        val command = listOf(executable?.absolutePath
          ?: throw IllegalArgumentException("Executable for tool '$tool' not found")
        ) + args
        val commandStr = command.joinToString(" ")
        tabs["Command"] = "```bash\n$commandStr\n```".renderMarkdown()

        transcript?.write("## Command\n```bash\n$commandStr\n```\n\n".toByteArray())

        fun execute(outputTask: SessionTask): String {
          val status = outputTask.add("Executing process...".renderMarkdown())
          val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()

          val output = process.inputStream.bufferedReader().readText()
          val exitCode = process.waitFor()
          status?.setLength(0)
          status?.append("**Execution Complete** (Exit Code: $exitCode)".renderMarkdown())
          outputTask.update()
          outputTask.add("#### Output\n```\n$output\n```".renderMarkdown())

          transcript?.write("## Output\n<details><summary>Process Output</summary>\n\n```\n$output\n```\n</details>\n\n".toByteArray())

          return if (exitCode == 0) {
            "### Tool execution successful\n**Tool:** `$tool`\n\n#### Output\n$output"
          } else {
            "### Tool execution failed (Exit Code: $exitCode)\n**Tool:** `$tool`\n\n#### Output\n$output"
          }
        }

        if (orchestrationConfig.autoFix) {
          val outputTask = tabs.newTask("Output")
          resultFn(execute(outputTask))
          outputTask.complete()
          task.complete()
        } else {
          val semaphore = Semaphore(0)
          var result = "Skipped"

          task.add("### Approval Required\nReview the command in the **Command** tab before running.".renderMarkdown())

          task.add(task.ui.hrefLink("▶ Run Tool", "btn btn-primary") {
            try {
              val outputTask = tabs.newTask("Output")
              result = execute(outputTask)
              outputTask.complete()

              task.add(acceptButtonFooter(task.ui) {
                semaphore.release()
              })
            } catch (e: Exception) {
              task.error(e)
              log.error("Error in RunTool hrefLink", e)
              transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
            }
          })

          semaphore.acquire()
          resultFn(result)
          task.complete()
        }
      } catch (e: Exception) {
        task.error(e)
        log.error("Error running tool", e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        throw e
      } finally {
        transcript?.close()
      }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(RunToolTask::class.java)

    @JvmStatic
    val RunTool = TaskType(
      name = "RunTool",
      category = "Execution",
      taskClass = RunToolTask::class.java,
      executionConfigClass = RunToolTaskExecutionConfigData::class.java,
      taskSettingsClass = RunToolTaskTypeConfig::class.java,
      description = "Execute external tools",
      tooltipHtml = """
                <p>Executes configured external tools and scripts.</p>
                <ul>
                    <li>Supports custom arguments and working directories.</li>
                    <li>Captures stdout and stderr.</li>
                    <li>Requires manual approval for side effects unless auto-fix is enabled.</li>
                </ul>
            """,
    )
  }
}