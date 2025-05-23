package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.actors.SimpleActor
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.TRIPLE_TILDE
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.ApiModel.Role
import com.simiacryptus.jopenai.util.ClientUtil.toContentList
import com.simiacryptus.util.JsonUtil
import com.simiacryptus.util.toJson
import org.slf4j.LoggerFactory
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference
import kotlin.streams.asSequence

class InsightTask(
    planSettings: PlanSettings,
    planTask: InsightTaskConfigData?
) : AbstractTask<InsightTask.InsightTaskConfigData>(planSettings, planTask) {
    class InsightTaskConfigData(
        @Description("The specific questions or topics to be addressed in the inquiry")
        val inquiry_questions: List<String>? = null,
        @Description("The goal or purpose of the inquiry")
        val inquiry_goal: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskConfigBase(
        task_type = TaskType.InsightTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = (if (!planSettings.autoFix) """
InsightTask - Directly answer questions or provide insights using the LLM. Reading files is optional and can be included if relevant to the inquiry.
  * Specify the questions and the goal of the inquiry.
  * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
  * User response/feedback and iteration are supported.
  * The primary characteristic of this task is that it does not produce side effects; the LLM is used to directly process the inquiry and respond.
""" else """
InsightTask - Directly answer questions or provide a report using the LLM. Reading files is optional and can be included if relevant to the inquiry.
  * Specify the questions and the goal of the inquiry.
  * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
  * The primary characteristic of this task is that it does not produce side effects; the LLM is used to directly process the inquiry and respond.
""") + """
Available files:
${getAvailableFiles(root).joinToString("\n") { "  - $it" }}
"""
    private val insightActor by lazy {
        SimpleActor(
            name = "Insight",
            prompt = """
                Create code for a new file that fulfills the specified requirements and context.
                Given a detailed user request, break it down into smaller, actionable tasks suitable for software development.
                Compile comprehensive information and insights on the specified topic.
                Provide a comprehensive overview, including key concepts, relevant technologies, best practices, and any potential challenges or considerations.

                Ensure the information is accurate, up-to-date, and well-organized to facilitate easy understanding.

                When generating insights, consider the existing project context and focus on information that is directly relevant and applicable.
                Focus on generating insights and information that support the task types available in the system (${
                planSettings.taskSettings.filter { it.value.enabled }.keys.joinToString(", ")
            }).
                This will ensure that the inquiries are tailored to assist in the planning and execution of tasks within the system's framework.
                """.trimIndent(),
            model = taskSettings.model ?: planSettings.defaultModel,
            temperature = planSettings.temperature,
        )
    }

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {

        val toInput = { it: String ->
            messages + listOf<String>(
                getInputFileCode(),
                it,
            ).filter { it.isNotBlank() }
        }

        val taskConfig: InsightTaskConfigData? = this.taskConfig
        val inquiryResult = if (!planSettings.autoFix) Discussable(
            task = task,
            userMessage = {
                "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                    taskConfig?.inquiry_questions?.joinToString(
                        "\n"
                    )
                }\nGoal: ${taskConfig?.inquiry_goal}\n${this.taskConfig?.toJson()}"
            },
            heading = "",
            initialResponse = { it: String -> insightActor.answer(toInput(it), api = api) },
            outputFn = { design: String ->
                MarkdownUtil.renderMarkdown(design, ui = agent.ui)
            },
            ui = agent.ui,
            reviseResponse = { usermessages: List<Pair<String, Role>> ->
                val inStr = "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                    taskConfig?.inquiry_questions?.joinToString("\n")
                }\nGoal: ${taskConfig?.inquiry_goal}\n${this.taskConfig?.toJson()}"
                val messages = usermessages.map { ApiModel.ChatMessage(it.second, it.first.toContentList()) }
                    .toTypedArray<ApiModel.ChatMessage>()
                insightActor.respond(
                    messages = messages,
                    input = toInput(inStr),
                    api = api
                )
            },
            atomicRef = AtomicReference(),
            semaphore = Semaphore(0),
        ).call() else insightActor.answer(
            toInput(
                "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                    taskConfig?.inquiry_questions?.joinToString(
                        "\n"
                    )
                }\nGoal: ${taskConfig?.inquiry_goal}\n${JsonUtil.toJson(data = this)}"
            ),
            api = api
        ).apply {
            task.add(MarkdownUtil.renderMarkdown(this, ui = agent.ui))
        }
        resultFn(inquiryResult)
    }

    private fun getInputFileCode(): String =
        ((taskConfig?.input_files ?: listOf()))
            .flatMap { pattern: String ->
                val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
                listOf(FileSelectionUtils.filteredWalkAsciiTree(root.toFile()) {
                        //path -> matcher.matches(root.relativize(path.toPath())) && !FileSelectionUtils.isLLMIgnored(path.toPath())
                    when {
                        FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                        matcher.matches(root.relativize(it.toPath())) -> true
                        else -> false
                    }
                })
            }
            .distinct()
            .sortedBy { it }
            .joinToString("\n\n") { relativePath ->
                val file = root.resolve(relativePath).toFile()
                try {
                    "# $relativePath\n\n$TRIPLE_TILDE\n${codeFiles[file.toPath()] ?: file.readText()}\n$TRIPLE_TILDE"
                } catch (e: Throwable) {
                    log.warn("Error reading file: $relativePath", e)
                    ""
                }
            }

    companion object {
        private val log = LoggerFactory.getLogger(InsightTask::class.java)
    }
}