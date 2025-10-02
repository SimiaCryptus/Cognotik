package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.apps.general.CmdPatchApp
import com.simiacryptus.cognotik.apps.general.PatchApp
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
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
    ) : TaskTypeConfig(task_type, name, model)

    class SelfHealingTaskExecutionConfigData(
        @Description("The commands to be executed with their respective working directories") val commands: List<CommandWithWorkingDir>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = TaskType.SelfHealingTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class CommandWithWorkingDir(
        @Description("The command to be executed") val command: List<String> = emptyList(),
        @Description("The relative path of the working directory") val workingDir: String? = null
    )

    override fun promptSegment() = ("""
  CommandAutoFixTask - Run a command and automatically fix any issues that arise
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
            val task = task.manager.newTask()
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
                ).run(
                    task = task, model = model
                ).apply {
                    when {
                        this.exitCode == 0 -> {
                            resultFn("All Commands completed")
                            semaphore.release()
                        }

                        else -> {
                            task.add(
                                task.manager.hrefLink("Ignore Error", "href-link cmd-button") {
                                    resultFn("Error: ${this.exitCode}")
                                    semaphore.release()
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

    companion object {
        private val log = LoggerFactory.getLogger(SelfHealingTask::class.java)
    }
}