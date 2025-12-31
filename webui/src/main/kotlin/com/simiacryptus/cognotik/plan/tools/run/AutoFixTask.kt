package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.general.CmdPatchApp
import com.simiacryptus.cognotik.apps.general.PatchApp
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Semaphore
import kotlin.io.path.exists

class AutoFixTask(
    orchestrationConfig: OrchestrationConfig, planTask: AutoFixTaskExecutionConfigData?
) : AbstractTask<AutoFixTask.AutoFixTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

    companion object {
        private val log = LoggerFactory.getLogger(AutoFixTask::class.java)
        val AutoFix = TaskType(
            "AutoFix",
            "Execution & Automation",
            AutoFixTaskExecutionConfigData::class.java,
            AutoFixTaskTypeConfig::class.java,
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

    class AutoFixTaskTypeConfig(
        task_type: String? = null,
        model: ApiChatModel? = null,
        name: String? = task_type,
    ) : TaskTypeConfig(task_type, name, model)

    class AutoFixTaskExecutionConfigData(
        @Description("The commands to be executed with their respective working directories") val commands: List<CommandWithWorkingDir>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = AutoFix.name,
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
  """.trimIndent() + (ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
        .tools.flatMap { it.component1()?.getExecutables() ?: emptyList() }.distinct().sorted()
        .joinToString("\n") { "    * $it" })).trim()

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
            val subTask = task.ui.newTask()
            subTask.ui.pool.submit {
                val (markdownTranscript, transcriptLink) = createTranscript(subTask)
                subTask.add(transcriptLink)
                val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
                    ?: defaultSmart).getChildClient(subTask)
                CmdPatchApp(
                    root = agent.root,
                    settings = PatchApp.Settings(
                        commands = this.executionConfig?.commands?.map { commandWithDir ->
                            val alias = commandWithDir.command.firstOrNull()
                            val toolExecutable = if (alias != null) {
                                val tools =
                                    ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools
                                tools.find { it.provider?.getExecutables()?.contains(alias) == true }?.let { toolData ->
                                    if (toolData.path != null) {
                                        toolData.provider!!.resolve(toolData.path).firstOrNull()?.let { File(it) }
                                    } else {
                                        toolData.resolve(alias)?.let { File(it) }
                                    }
                                }
                            } else null
                            PatchApp.CommandSettings(
                                executable = toolExecutable ?: when {
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
                    parsingModel = defaultFast,
                    processor = orchestrationConfig.processor,
                ).also { app ->
                    markdownTranscript?.let { transcript ->
                        transcript.write("# Self-Healing Task Execution\n\n".toByteArray())
                        transcript.write("## Commands\n".toByteArray())
                    }
                }.run(
                    task = subTask, model = model
                ).apply {
                    markdownTranscript?.let { transcript ->
                        transcript.write("\n## Result\n".toByteArray())
                        transcript.write("Exit code: ${this.exitCode}\n".toByteArray())
                        transcript.close()
                    }
                    when {
                        this.exitCode == 0 -> {
                            if (orchestrationConfig.autoFix) {
                                resultFn("All Commands completed")
                                semaphore.release()
                                subTask.complete()
                            } else {
                                subTask.add(
                                    subTask.ui.hrefLink("Accept & Continue", "btn btn-primary") {
                                        resultFn("All Commands completed")
                                        semaphore.release()
                                        subTask.complete()
                                    }
                                )
                            }
                        }

                        else -> {
                            subTask.add(
                                subTask.ui.hrefLink("Ignore Error", "href-link cmd-button") {
                                    resultFn("Error: ${this.exitCode}")
                                    semaphore.release()
                                    subTask.complete()
                                }
                            )
                        }
                    }
                }
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
        val html =
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(".md")
            }.pdf' target='_blank'>pdf</a>"
        return Pair(markdownTranscript, html)
    }

}