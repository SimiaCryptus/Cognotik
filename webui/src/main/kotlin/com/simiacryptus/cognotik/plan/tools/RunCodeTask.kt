package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.actors.CodeAgent
import com.simiacryptus.cognotik.apps.code.CodingTask
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.interpreter.CodeRuntime
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
    planTask: RunCodeTaskConfigData?,
) : AbstractTask<RunCodeTask.RunCodeTaskConfigData>(orchestrationConfig, planTask) {

    class RunCodeTaskSettings(
        task_type : String = TaskType.RunCodeTask.name,
        val codeRuntime: CodeRuntimes? = null,
        enabled: Boolean = true,
        model: ApiChatModel? = null,
    ) : TaskSettingsBase(
        task_type = task_type,
        enabled = enabled,
        model = model,
    )

    class RunCodeTaskConfigData(
        @Description("The task or goal to be accomplished")
        val goal: String? = null,
        @Description("The relative file path of the working directory")
        val workingDir: String? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null
    ) : TaskConfigBase(
        task_type = TaskType.RunCodeTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = """
    RunCodeTask - Use a code interpreter to solve and complete the user's request.
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
        val semaphore = Semaphore(0)
        val model = (taskSettings.model?.let { agent.orchestrationConfig.instance(it) }
            ?: agent.orchestrationConfig.defaultChatter).getChildClient(task)

//        val taskSettings = this.orchestrationConfig.getTaskSettings(TaskType.RunCodeTask)
        val taskSettings = taskSettings as? RunCodeTaskSettings
        val runtime = taskSettings?.codeRuntime ?: CodeRuntimes.GroovyRuntime // Kotlin has issues running within IntelliJ
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
            ui = task.manager,
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
                var formHandle: StringBuilder? = null
                if (!orchestrationConfig.autoFix) formHandle = task.add(
                    "<div>\n${
                        if (!super.canPlay) "" else super.playButton(task, request, response, formText) { formHandle!! }
                    }\n${
                        ui.hrefLink("Continue", "href-link play-button") {
                            response.let {
                                "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n## Output\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n"
                            }.apply { resultFn(this) }
                            semaphore.release()
                        }
                    }\n</div>\n${
                        super.ui.textInput(oneAtATime { feedback: String ->
                            super.responseAction(task, "Revising...", formHandle!!, formText) {
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
                    (this.taskConfig?.goal ?: "") to ModelSchema.Role.user,
                )
            )
        )
        try {
            semaphore.acquire()
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RunCodeTask::class.java)
    }
}