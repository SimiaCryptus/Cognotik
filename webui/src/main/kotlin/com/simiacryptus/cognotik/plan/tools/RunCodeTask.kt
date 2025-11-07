package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.apps.code.CodingTask
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.oneAtATime
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

class RunCodeTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: RunCodeTaskExecutionConfigData?,
) : AbstractTask<RunCodeTask.RunCodeTaskExecutionConfigData, RunCodeTask.RunCodeTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class RunCodeTaskTypeConfig(
        task_type: String = RunCode.name,
        val codeRuntime: CodeRuntimes? = null,
        model: ApiChatModel? = null,
        name: String? = task_type,
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name,
        model = model,
    )

    class RunCodeTaskExecutionConfigData(
        @Description("The task or goal to be accomplished")
        val goal: String? = null,
        @Description("The relative file path of the working directory")
        val workingDir: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = RunCode.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = """
    RunCode - Use a code interpreter to solve and complete the user's request.
      * Do not directly write code (yet)
      * Include detailed technical requirements for the needed solution
    """.trimIndent()

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val autoRunCounter = AtomicInteger(0)
        val transcript = task.transcript()
        val semaphore = Semaphore(0)
        val typeConfig = typeConfig ?: throw RuntimeException()
        val model = (typeConfig.model?.let { orchestrationConfig.instance(it) }
            ?: orchestrationConfig.defaultChatter).getChildClient(task)

//        val taskSettings = this.orchestrationConfig.getTaskSettings(TaskType.RunCodeTask)
        val taskSettings = typeConfig as? RunCodeTaskTypeConfig
        val runtime =
            taskSettings?.codeRuntime ?: CodeRuntimes.GroovyRuntime // Kotlin has issues running within IntelliJ
        val defs = mapOf(
            "env" to (orchestrationConfig.env ?: emptyMap()),
            "workingDir" to (
                    orchestrationConfig.absoluteWorkingDir?.let { File(it).absolutePath }
                        ?: orchestrationConfig.absoluteWorkingDir?.let { File(it).absolutePath }
                        ?: File(".").absolutePath
                    ),
        )
        val codeRuntime = CodeRuntimes.getRuntime(runtime, defs)

        val codingAgent = object : CodingTask<CodeRuntime>(
            dataStorage = agent.dataStorage,
            session = agent.session,
            user = agent.user,
            ui = task.ui,
            interpreter = codeRuntime::class as KClass<CodeRuntime>,
            symbols = mapOf<String, Any>(
                "env" to (orchestrationConfig.env ?: emptyMap()),
                "workingDir" to (
                        orchestrationConfig.absoluteWorkingDir?.let { File(it).absolutePath }
                            ?: orchestrationConfig.absoluteWorkingDir?.let { File(it).absolutePath }
                            ?: File(".").absolutePath
                        ),
                "language" to runtime.name.lowercase().replace("runtime", ""),
            ),
            temperature = orchestrationConfig.temperature,
            details = """
                Code a solution using ${runtime.name} to the user's request.
            """.trimIndent(),
            model = model,
            mainTask = task,
            retryable = false,
        ) {
            override fun displayFeedback(
                task: SessionTask,
                request: CodeAgent.CodeRequest,
                response: CodeAgent.CodeResult
            ) {
                val formText = StringBuilder()
                transcript?.write(
                    "## Code Request\n```${
                        runtime.name.lowercase().replace("runtime", "")
                    }\n${request.messages}\n```\n\n".toByteArray()
                )
                transcript?.write("## Execution Result\n".toByteArray())
                transcript?.write("**Result Value:**\n```\n${response.result.resultValue}\n```\n\n".toByteArray())
                transcript?.write("**Output:**\n```\n${response.result.resultOutput}\n```\n\n".toByteArray())
                var formHandle: StringBuilder? = null
                if (!orchestrationConfig.autoFix) formHandle = task.add(
                    "<div>\n${
                        if (!super.canPlay) "" else super.playButton(task, request, response, formText) { formHandle!! }
                    }\n${
                        ui.hrefLink("Continue", "href-link play-button") {
                            response.let {
                                transcript?.write("## User Action: Continue\n\n".toByteArray())
                                "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n## Output\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n"
                            }.apply { resultFn(this) }
                            semaphore.release()
                        }
                    }\n</div>\n${
                        super.ui.textInput(oneAtATime { feedback: String ->
                            super.responseAction(task, "Revising...", formHandle!!, formText) {
                                transcript?.write("## User Feedback\n$feedback\n\n".toByteArray())
                                super.feedback(task, feedback, request, response)
                            }
                        })
                    }", additionalClasses = "reply-message"
                ) else if (autoRunCounter.incrementAndGet() <= 1) {
                    responseAction(task, "Running...", formHandle, formText) {
                        execute(task, response, request)
                    }
                }
                formText.append(formHandle.toString())
                formHandle.toString()
                task.complete()
            }

            override fun execute(
                task: SessionTask,
                response: CodeAgent.CodeResult
            ): String {
                val result = super.execute(task, response)
                if (orchestrationConfig.autoFix) {
                    transcript?.write("## Auto-fix Execution\n\n".toByteArray())
                    response.let {
                        "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n## Result\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n## Output\n$TRIPLE_TILDE\n${response.result.resultOutput}\n$TRIPLE_TILDE\n"
                    }.apply { resultFn(this) }
                    semaphore.release()
                }
                return result
            }
        }
        codingAgent.start(
            codingAgent.codeRequest(
                messages.map { it to ModelSchema.Role.user } + listOf(
                    (this.executionConfig?.goal ?: "") to ModelSchema.Role.user,
                )
            )
        )
        try {
            semaphore.acquire()
        } catch (e: Throwable) {
            transcript?.write("## Error\n```\n${e.message}\n${e.stackTraceToString()}\n```\n\n".toByteArray())
            log.warn("Error", e)
        } finally {
            transcript?.write("\n## Task Completed\n".toByteArray())
            transcript?.close()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RunCodeTask::class.java)
        val RunCode = TaskType(
            "RunCode",
            RunCodeTaskExecutionConfigData::class.java,
            RunCodeTaskTypeConfig::class.java,
            "Execute code snippets safely",
            """
          Executes code snippets in a controlled environment.
          <ul>
            <li>Safe code execution handling</li>
            <li>Working directory configuration</li>
            <li>Output capture and formatting</li>
            <li>Error handling and reporting</li>
            <li>Interactive result review</li>
          </ul>
        """
        )

    }
}