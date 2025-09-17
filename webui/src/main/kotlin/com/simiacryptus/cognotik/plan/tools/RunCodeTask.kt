package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.apps.code.CodingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.interpreter.Interpreter
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

class RunCodeTask<T : Interpreter>(
    planSettings: PlanSettings,
    planTask: RunCodeTaskConfigData?,
    val interpreter: KClass<T>,
) : AbstractTask<RunCodeTask.RunCodeTaskConfigData>(planSettings, planTask) {

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
    RunCodeTask - Use a Kotlin interpreter to solve and complete the user's request.
      * Do not directly write code (yet)
      * Include detailed technical requirements for the needed solution
    """.trimIndent()

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        planSettings: PlanSettings
    ) {
        val autoRunCounter = AtomicInteger(0)
        val semaphore = Semaphore(0)
        val model = (taskSettings.model?.let { agent.planSettings.instance(it) } ?: agent.planSettings.defaultChatter).getChildClient(task)
        val codingAgent = object : CodingAgent<T>(
            dataStorage = agent.dataStorage,
            session = agent.session,
            user = agent.user,
            ui = agent.ui,
            interpreter = interpreter,
            symbols = mapOf<String, Any>(
                "env" to (planSettings.env ?: emptyMap()),
                "workingDir" to (
                        planSettings.absoluteWorkingDir?.let { File(it).absolutePath }
                            ?: planSettings.absoluteWorkingDir?.let { File(it).absolutePath }
                            ?: File(".").absolutePath
                        ),
                "language" to "kotlin",
            ),
            temperature = planSettings.temperature,
            details = """
                Code a solution using Kotlin to the user's request.
            """.trimIndent(),
            model = model,
            mainTask = task,
            retryable = false,
        ) {
            override fun displayFeedback(
                task: SessionTask,
                request: CodingActor.CodeRequest,
                response: CodingActor.CodeResult
            ) {
                val formText = StringBuilder()
                var formHandle: StringBuilder? = null
                if (!planSettings.autoFix) formHandle = task.add(
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
                        super.ui.textInput { feedback ->
                            super.responseAction(task, "Revising...", formHandle!!, formText) {
                                super.feedback(task, feedback, request, response)
                            }
                        }
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
                response: CodingActor.CodeResult
            ): String {
                val result = super.execute(task, response)
                if (planSettings.autoFix) {
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
                messages.map { it to ApiModel.Role.user } + listOf(
                    (this.taskConfig?.goal ?: "") to ApiModel.Role.user,
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