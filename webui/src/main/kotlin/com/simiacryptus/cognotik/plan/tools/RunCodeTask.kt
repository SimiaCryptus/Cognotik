package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.apps.code.CodingAgent
import com.simiacryptus.cognotik.kotlin.KotlinInterpreter
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ApiModel
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.Semaphore
import kotlin.Any
import kotlin.String

class RunCodeTask(
    planSettings: PlanSettings,
    planTask: RunCodeTaskConfigData?
) : AbstractTask<RunCodeTask.RunCodeTaskConfigData>(planSettings, planTask) {

    class RunCodeTaskConfigData(
        @Description("The task or goal to be accomplished")
        val coding_task: String? = null,
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
    RunCodeTask - Execute a code snippet using Kotlin.
    """.trimIndent()

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
        val codingAgent = object : CodingAgent<KotlinInterpreter>(
            api = api,
            dataStorage = agent.dataStorage,
            session = agent.session,
            user = agent.user,
            ui = agent.ui,
            interpreter = KotlinInterpreter::class,
            symbols = mapOf<String, Any>(
                "env" to (planSettings.env ?: emptyMap()),
                "workingDir" to (
                        planSettings.workingDir?.let { File(it).absolutePath }
                            ?: planSettings.workingDir?.let { File(it).absolutePath }
                            ?: File(".").absolutePath
                ),
                "language" to "kotlin",
            ),
            temperature = planSettings.temperature,
            details = """
                Code a solution using Kotlin to the user's request.
            """.trimIndent(),
            model = taskSettings.task_type?.let { planSettings.getTaskSettings(TaskType.valueOf(it)).model }
                ?: planSettings.defaultModel,
            mainTask = task,
        ) {
            override fun displayFeedback(
                task: SessionTask,
                request: CodingActor.CodeRequest,
                response: CodingActor.CodeResult
            ) {
                val formText = StringBuilder()
                var formHandle: StringBuilder? = null
                formHandle = task.add(
                    "<div style=\"display: flex;flex-direction: column;\">\n${
                        if (!super.canPlay) "" else super.playButton(
                            task,
                            request,
                            response,
                            formText
                        ) { formHandle!! }
                    }\n${acceptButton(response)}\n</div>\n${
                        super.reviseMsg(
                            task,
                            request,
                            response,
                            formText
                        ) { formHandle!! }
                    }", additionalClasses = "reply-message"
                )
                formText.append(formHandle.toString())
                formHandle.toString()
                task.complete()
            }

            fun acceptButton(
                response: CodingActor.CodeResult
            ): String {
                return ui.hrefLink("Accept", "href-link play-button") {
                    response.let {
                        "## Command\n\n$TRIPLE_TILDE\n${response.code}\n$TRIPLE_TILDE\n## Output\n$TRIPLE_TILDE\n${response.result.resultValue}\n$TRIPLE_TILDE\n"
                    }.apply { resultFn(this) }
                    semaphore.release()
                }
            }
        }
        codingAgent.start(
            codingAgent.codeRequest(
                messages.map { it to ApiModel.Role.user } + listOf(
                    (this.taskConfig?.coding_task ?: "") to ApiModel.Role.user,
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