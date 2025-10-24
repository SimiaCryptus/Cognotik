package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.apps.general.CmdPatchApp
import com.simiacryptus.cognotik.apps.general.PatchApp
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Semaphore
import kotlin.io.path.exists

class SelfHealingTask(
  orchestrationConfig: OrchestrationConfig, planTask: SelfHealingTaskExecutionConfigData?
) : AbstractTask<SelfHealingTask.SelfHealingTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
  class SelfHealingTaskTypeConfig(
    task_type: String? = null,
    model: ApiChatModel? = null,
    @Description("List of command executables that can be used for auto-fixing") var commandAutoFixCommands: MutableList<String>? = mutableListOf(),
    name: String? = task_type,
  ) : TaskTypeConfig(task_type, name, model), ValidatedObject {
    override fun validate(): String? {
      if (commandAutoFixCommands.isNullOrEmpty()) {
        return "commandAutoFixCommands must not be null or empty"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  class SelfHealingTaskExecutionConfigData(
    @Description("The commands to be executed with their respective working directories") val commands: List<CommandWithWorkingDir>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : ValidatedObject, TaskExecutionConfig(
    task_type = SelfHealing.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ) {
    override fun validate(): String? {
      if (commands.isNullOrEmpty()) {
        return "commands must not be null or empty"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class CommandWithWorkingDir(
    @Description("The command to be executed") val command: List<String> = emptyList(),
    @Description("The relative path of the working directory") val workingDir: String? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (command.isEmpty()) {
        return "command must not be empty"
      }
      return null
    }
  }

  override fun promptSegment() = ("""
  SelfHealing - Run a command and automatically fix any issues that arise
  * Specify the commands to be executed along with their working directories
  * Each command's working directory should be specified relative to the root directory
  * Provide the commands and their arguments in the 'commands' field
  * Each command should be a list of strings
  * Available commands:
  """.trimIndent() + typeConfig.commandAutoFixCommands?.joinToString("\n") { "    * ${File(it).name}" }).trim()

  override val typeConfig: SelfHealingTaskTypeConfig
    get() = super.typeConfig as SelfHealingTaskTypeConfig

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val semaphore = Semaphore(0)
    Retryable(task = task) {
      val markdownTranscript = transcript(task)
      val task = task.ui.newTask()
      agent.pool.submit {
        val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
          ?: orchestrationConfig.defaultChatter).getChildClient(task)
        CmdPatchApp(
          root = agent.root,
          settings = PatchApp.Settings(
            commands = this.executionConfig?.commands?.map { commandWithDir ->
              val alias = commandWithDir.command.firstOrNull()
              val cmds = executionConfig.commands.map {
                val cmd = it.command.firstOrNull()
                typeConfig.commandAutoFixCommands?.firstOrNull { it.endsWith(cmd ?: "") } ?: cmd
              }.map { File(it!!) }.associateBy { it.name }.filterKeys { it.startsWith(alias ?: "") }
              PatchApp.CommandSettings(
                executable = when {
                  cmds.isNotEmpty() -> cmds.entries.firstOrNull()?.value
                  alias.isNullOrBlank() -> null
                  root.resolve(alias).exists() -> root.resolve(alias).toFile().absoluteFile
                  File(alias).exists() -> File(alias).absoluteFile
                  else -> null
                } ?: throw IllegalArgumentException("Command not found: $alias"),
                arguments = commandWithDir.command.drop(1).joinToString(" "),
                workingDirectory = (commandWithDir.workingDir?.let { agent.root.toFile().resolve(it) }
                  ?: agent.root.toFile()).apply { mkdirs() },
                additionalInstructions = ""
              )
            } ?: emptyList(),
            autoFix = orchestrationConfig.autoFix,
            includeLineNumbers = false,
          ),
          files = agent.files,
          model = model,
          parsingModel = orchestrationConfig.parsingChatter,
          processor = orchestrationConfig.processor,
        ).also { app ->
          markdownTranscript?.let { transcript ->
            transcript.write("# Self-Healing Task Execution\n\n".toByteArray())
            transcript.write("## Commands\n".toByteArray())
          }
        }.run(
          task = task, model = model
        ).apply {
          when {
            this.exitCode == 0 -> {
              resultFn("All Commands completed")
              semaphore.release()
              markdownTranscript?.let { transcript ->
                transcript.write("\n## Result\n".toByteArray())
                transcript.write("All commands completed successfully (exit code: 0)\n".toByteArray())
                transcript.close()
              }
            }

            else -> {
              task.add(
                task.ui.hrefLink("Ignore Error", "href-link cmd-button") {
                  resultFn("Error: ${this.exitCode}")
                  semaphore.release()
                  markdownTranscript?.let { transcript ->
                    transcript.write("\n## Result\n".toByteArray())
                    transcript.write("Command failed with exit code: ${this.exitCode}\n".toByteArray())
                    transcript.close()
                  }
                }
              )
            }
          }
        }
      }
      task.placeholder
    }
    try {
      semaphore.acquire()
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  private fun transcript(task: SessionTask): FileOutputStream? {
    val (link, file) = task.createFile("transcript.md")
    val markdownTranscript = file?.outputStream()
    task.complete(
      "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
        link.removeSuffix(
          ".md"
        )
      }.pdf' target='_blank'>pdf</a>"
    )
    return markdownTranscript
  }

  companion object {
    private val log = LoggerFactory.getLogger(SelfHealingTask::class.java)
    val SelfHealing = TaskType(
      "SelfHealing",
      SelfHealingTaskExecutionConfigData::class.java,
      SelfHealingTaskTypeConfig::class.java,
      "Run a command and automatically fix any issues that arise",
      """
          Executes a command and automatically fixes any issues that arise.
          <ul>
            <li>Specify commands and working directories</li>
            <li>Supports multiple commands and directories</li>
            <li>Interactive approval mode</li>
            <li>Output diff formatting</li>
          </ul>
        """
    )

  }
}
