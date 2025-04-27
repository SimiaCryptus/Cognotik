package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.apps.general.CmdPatchApp
import com.simiacryptus.cognotik.apps.general.PatchApp
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ChatModel
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Semaphore
import kotlin.io.path.exists

class CommandAutoFixTask(
    planSettings: PlanSettings, planTask: CommandAutoFixTaskConfigData?
) : AbstractTask<CommandAutoFixTask.CommandAutoFixTaskConfigData>(planSettings, planTask) {
    class CommandAutoFixTaskSettings(
        task_type: String? = null,
        enabled: Boolean = false,
        model: ChatModel? = null,
        @Description("List of command executables that can be used for auto-fixing") var commandAutoFixCommands: List<String>? = listOf()
    ) : TaskSettingsBase(task_type, enabled, model)

    class CommandAutoFixTaskConfigData(
        @Description("The commands to be executed with their respective working directories") val commands: List<CommandWithWorkingDir>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskConfigBase(
        task_type = TaskType.CommandAutoFixTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    data class CommandWithWorkingDir(
        @Description("The command to be executed") val command: List<String> = emptyList(),
        @Description("The relative path of the working directory") val workingDir: String? = null
    )

    override fun promptSegment(): String {
        val settings = planSettings.getTaskSettings(TaskType.CommandAutoFixTask) as CommandAutoFixTaskSettings
        return ("""
      CommandAutoFixTask - Run a command and automatically fix any issues that arise
      * Specify the commands to be executed along with their working directories
      * Each command's working directory should be specified relative to the root directory
      * Provide the commands and their arguments in the 'commands' field
      * Each command should be a list of strings
      * Available commands:
      """.trimIndent() + settings.commandAutoFixCommands?.joinToString("\n") { "    * ${File(it).name}" }).trim()
    }

    override val taskSettings: CommandAutoFixTaskSettings
        get() = super.taskSettings as CommandAutoFixTaskSettings

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {
        val semaphore = Semaphore(0)
        Retryable(agent.ui, task = task) {
            val task = agent.ui.newTask(false)
            agent.pool.submit {
                val api = api.getChildClient(task)
                CmdPatchApp(
                    root = agent.root,
                    settings = PatchApp.Settings(
                        commands = this.taskConfig?.commands?.map { commandWithDir ->
                            val alias = commandWithDir.command.firstOrNull()
                            val cmds = taskConfig.commands.map {
                                val cmd = it.command.firstOrNull()
                                taskSettings.commandAutoFixCommands?.firstOrNull { it.endsWith(cmd ?: "") } ?: cmd
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
                        autoFix = agent.planSettings.autoFix,
                        exitCodeOption = "nonzero",
                        includeLineNumbers = false,
                    ),
                    api = api,
                    files = agent.files,
                    model = taskSettings.model ?: agent.planSettings.defaultModel,
                    parsingModel = agent.planSettings.parsingModel,
                ).run(
                    ui = agent.ui, task = task
                )
                task.add(
                    agent.ui.hrefLink("Accept", "href-link cmd-button") {
                        resultFn("All Commands completed")
                        semaphore.release()
                    }
                )
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
        private val log = LoggerFactory.getLogger(CommandAutoFixTask::class.java)
    }
}