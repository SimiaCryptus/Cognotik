package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.CmdPatchApp
import com.simiacryptus.cognotik.apps.PatchApp
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
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
        @JvmStatic val AutoFix = TaskType(
            name = "AutoFix",
            category = "Execution",
            taskClass = AutoFixTask::class.java,
            executionConfigClass = AutoFixTaskExecutionConfigData::class.java,
            taskSettingsClass = AutoFixTaskTypeConfig::class.java,
            description = "Run a command and automatically fix any issues that arise",
            tooltipHtml = """
                    Executes a command and automatically fixes any issues that arise.
                    <ul>
                      <li>Specify commands and working directories</li>
                      <li>Supports multiple commands and directories</li>
                      <li>Interactive approval mode</li>
                      <li>Output diff formatting</li>
                    </ul>
                  """,
        )
    }

    class AutoFixTaskTypeConfig(
        name: String? = AutoFix.name,
        model: ApiChatModel? = null,
        var promptTemplate: String = """
  SelfHealing - Run a command and automatically fix any issues that arise
  * Specify the commands to be executed along with their working directories
  * Each command's working directory should be specified relative to the root directory
  * Provide the commands and their arguments in the 'commands' field
  * Each command should be a list of strings
  * Available commands:
  {executables}
        """.trimIndent()
    ) : TaskTypeConfig(AutoFix.name, name, model)

    class AutoFixTaskExecutionConfigData(
        @Description("The commands to be executed with their respective working directories") var commands: MutableList<CommandWithWorkingDir>? = ArrayList(),
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
        @Description("The command to be executed") var command: MutableList<String> = ArrayList(),
        @Description("The relative path of the working directory") var workingDir: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (command.isEmpty()) {
                return "command must not be empty"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        val executables = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
            .tools.flatMap { it.component1()?.getExecutables() ?: emptyList() }.distinct().sorted()
            .joinToString("\n") { "    * $it" }
        return typeConfig.promptTemplate.replace("{executables}", executables).trim()
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
                subTask.ui.pool.submit {
                    val transcript = createTranscript(subTask)
                    subTask.add(transcript.second.renderMarkdown())
                    val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
                        ?: defaultSmart).getChildClient(subTask)
                    val markdownTranscript = transcript.first
                    try {
                        markdownTranscript?.write("## Self-Healing Task Execution\n\n".toByteArray())
                        markdownTranscript?.write("## Commands\n".toByteArray())
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
                        ).run(
                            task = subTask, model = model
                        ).apply {
                            markdownTranscript?.write("\n### Execution Result\n* **Exit Code:** ${this.exitCode}\n".toByteArray())
                            when {
                                this.exitCode == 0 -> {
                                    if (orchestrationConfig.autoFix) {
                                        resultFn("### Success\nAll commands executed successfully with exit code 0.")
                                        semaphore.release()
                                        subTask.complete()
                                    } else {
                                        subTask.add(
                                            subTask.ui.hrefLink("Accept & Continue", "btn btn-primary") {
                                                resultFn("### Success\nUser accepted command execution results.")
                                                semaphore.release()
                                                subTask.complete()
                                            }.renderMarkdown()
                                        )
                                    }
                                }

                                else -> {
                                    log.warn("Command failed with exit code ${this.exitCode}")
                                    subTask.add(
                                        subTask.ui.hrefLink("Ignore Error", "href-link cmd-button") {
                                            resultFn("### Warning\nCommands failed with exit code ${this.exitCode}, but error was ignored by user.")
                                            semaphore.release()
                                            subTask.complete()
                                        }.renderMarkdown()
                                    )
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        // Triple Log Rule: UI, SLF4J, and Transcript
                        subTask.error(e)
                        log.error("Critical error during AutoFixTask execution", e)
                        markdownTranscript?.write("\n### Execution Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())

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
                subTask.add(subTask.ui.hrefLink("▶ Run AutoFix", "btn btn-primary") {
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
        val html =
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(".md")
            }.pdf' target='_blank'>pdf</a>"
        return Pair(markdownTranscript, html)
    }

}