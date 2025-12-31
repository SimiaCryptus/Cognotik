package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
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
        name: String? = task_type,
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name,
        model = model,
    )

    class RunToolTaskExecutionConfigData(
        @Description("The tool to run")
        val tool: String? = null,
        @Description("The arguments to pass to the tool")
        val args: List<String>? = null,
        @Description("The relative file path of the working directory")
        val workingDir: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = RunTool.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        val executable: File?
            get() {

                val tools = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools

                return tools.find { it.provider?.getExecutables()?.contains(tool) == true }?.let { toolData ->
                    if (toolData.path != null) {
                        toolData.provider!!.resolve(toolData.path).forEach { resolved ->
                            return File(resolved)
                        }
                    }
                    val resolved: String? = toolData.resolve(tool)
                    if (resolved != null) {
                        File(resolved)
                    } else {
                        null
                    }
                }
            }
    }

    override fun promptSegment(): String {
        val executables: List<String>? =
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
                .tools.flatMap { it.component1()?.getExecutables() ?: emptyList() }.distinct().sorted()

        return "RunTool - Execute a tool with custom arguments\n" +
                "  * Available tools: ${executables?.joinToString(", ") ?: "None"}\n"
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        try {
            val tool = executionConfig?.tool ?: throw IllegalArgumentException("Tool not specified")
            val args = executionConfig.args ?: emptyList()
            val workingDir = executionConfig.workingDir?.let { File(it) }
                ?: File(orchestrationConfig.absoluteWorkingDir ?: ".")
            val executable = executionConfig.executable?.absolutePath
                ?: throw IllegalArgumentException("Executable for tool '$tool' not found")
            val command = listOf(executable) + args
            val commandStr = command.joinToString(" ")

            transcript?.write("## Command\n```bash\n$commandStr\n```\n\n".toByteArray())

            fun execute(): String {
                val process = ProcessBuilder(command)
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                transcript?.write("## Output\n```\n$output\n```\n\n".toByteArray())

                return if (exitCode == 0) {
                    "Tool execution successful.\nOutput:\n$output"
                } else {
                    "Tool execution failed with exit code $exitCode.\nOutput:\n$output"
                }
            }
            if (orchestrationConfig.autoFix) {
                task.header("Executing tool: $tool", level = 3)
                resultFn(execute())
                task.complete()
            } else {
                task.header("Proposed command to run", level = 3)
                task.add("<pre>$commandStr</pre>")
                val semaphore = Semaphore(0)
                var result = "Skipped"
                task.add(task.ui.hrefLink("Run Tool") {
                    try {
                        val statusBuffer = task.add("Running...")
                        result = execute()
                        statusBuffer?.setLength(0)
                        statusBuffer?.append("<b>Execution Complete</b>")
                        task.update()
                        task.expandable("Output", "<pre>${result.replace("<", "&lt;")}</pre>")
                        task.add(acceptButtonFooter(task.ui) {
                            semaphore.release()
                        })
                    } catch (e: Exception) {
                        task.error(e)
                    }
                })
                semaphore.acquire()
                resultFn(result)
                task.complete()
            }
        } catch (e: Exception) {
            log.warn("Error running tool", e)
            transcript?.write("## Error\n```\n${e.message}\n```\n\n".toByteArray())
            resultFn("Error running tool: ${e.message}")
            task.complete()
        } finally {
            transcript?.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RunToolTask::class.java)
        val RunTool = TaskType(
            "RunTool",
            "Execution & Automation",
            RunToolTaskExecutionConfigData::class.java,
            RunToolTaskTypeConfig::class.java,
            "Execute external tools",
            """
          Executes configured external tools.
        """
        )
    }
}