package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.actors.SimpleActor
import com.simiacryptus.cognotik.input.PaginatedDocumentReader
import com.simiacryptus.cognotik.input.getReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.TRIPLE_TILDE
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.chat.ChatClientInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.models.ApiModel
import com.simiacryptus.cognotik.models.ApiModel.Role
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.toContentList
import java.nio.file.FileSystems
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

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
        @Description("Whether to extract text content from non-text files (PDF, HTML, etc.)")
        val extractContent: Boolean = false,
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
        resultFn: (String) -> Unit,
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
            initialResponse = { it: String -> insightActor.answer(toInput(it)) },
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
        ).apply {
            task.add(MarkdownUtil.renderMarkdown(this, ui = agent.ui))
        }
        resultFn(inquiryResult ?: "(no response)")
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
                    val content = if (taskConfig?.extractContent == true && !isTextFile(file)) {
                        extractDocumentContent(file)
                    } else {
                        codeFiles[file.toPath()] ?: file.readText()
                    }
                    "# $relativePath\n\n$TRIPLE_TILDE\n$content\n$TRIPLE_TILDE"
                } catch (e: Throwable) {
                    log.warn("Error reading file: $relativePath", e)
                    ""
                }
            }

    private fun isTextFile(file: java.io.File): Boolean {
        val textExtensions = setOf(
            "txt",
            "md",
            "kt",
            "java",
            "js",
            "ts",
            "py",
            "rb",
            "go",
            "rs",
            "c",
            "cpp",
            "h",
            "hpp",
            "css",
            "html",
            "xml",
            "json",
            "yaml",
            "yml",
            "properties",
            "gradle",
            "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    private fun extractDocumentContent(file: java.io.File) = try {
        file.getReader().use { reader ->
            when (reader) {
                is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
                else -> reader.getText()
            }
        }
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
        try {
            file.readText()
        } catch (e2: Exception) {
            "Error reading file: ${e2.message}"
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(InsightTask::class.java)
        val InsightTaskType = TaskType(
            "InsightTask",
            InsightTaskConfigData::class.java,
            TaskSettingsBase::class.java,
            "Directly answer questions or provide insights using the LLM, optionally referencing files, with optional user feedback and iteration.",
            """
            Provides direct answers and insights using the LLM, optionally referencing project files.
            <ul>
              <li>Primarily processes and responds to user inquiries using the language model, without producing side effects or modifying files</li>
              <li>Reading files is optional; the task can operate with or without file input</li>
              <li>User feedback and iterative refinement are supported but not required</li>
              <li>Generates comprehensive markdown reports, explanations, and recommendations</li>
              <li>Can answer detailed questions about code, design, or project context</li>
              <li>Supports both one-shot and interactive discussion modes</li>
              <li>Ideal for technical Q&A, code reviews, and architectural analysis without making changes</li>
            </ul>
            """
        )

    }
}