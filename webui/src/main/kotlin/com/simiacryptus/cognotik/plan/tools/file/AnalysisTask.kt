package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.PaginatedDocumentReader
import com.simiacryptus.cognotik.input.getReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.TRIPLE_TILDE
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.Companion.getAvailableFiles
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

class AnalysisTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: AnalysisTaskExecutionConfigData?
) : AbstractTask<AnalysisTask.AnalysisTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
    class AnalysisTaskExecutionConfigData(
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
    ) : TaskExecutionConfig(
        task_type = Analysis.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment() = (if (!orchestrationConfig.autoFix) """
Analysis - Directly answer questions or provide insights using the LLM. Reading files is optional and can be included if relevant to the inquiry.
  * Specify the questions and the goal of the inquiry.
  * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
  * User response/feedback and iteration are supported.
  * The primary characteristic of this task is that it does not produce side effects; the LLM is used to directly process the inquiry and respond.
""" else """
Analysis - Directly answer questions or provide a report using the LLM. Reading files is optional and can be included if relevant to the inquiry.
  * Specify the questions and the goal of the inquiry.
  * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
  * The primary characteristic of this task is that it does not produce side effects; the LLM is used to directly process the inquiry and respond.
""") + """
Available files:
${getAvailableFiles(root).joinToString("\n") { "  - $it" }}
"""

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {

        val toInput = { it: String ->
            messages + listOf(
                getInputFileCode(),
                it,
            ).filter { it.isNotBlank() }
        }

        val taskConfig: AnalysisTaskExecutionConfigData? = this.executionConfig
        val insightActor = ChatAgent(
            name = "Insight",
            prompt = """
                Create code for a new file that fulfills the specified requirements and context.
                Given a detailed user request, break it down into smaller, actionable tasks suitable for software development.
                Compile comprehensive information and insights on the specified topic.
                Provide a comprehensive overview, including key concepts, relevant technologies, best practices, and any potential challenges or considerations.
                Ensure the information is accurate, up-to-date, and well-organized to facilitate easy understanding.
                """.trimIndent(),
            model = (typeConfig.model?.let<ApiChatModel, ChatInterface> { this.orchestrationConfig.instance(it) }
                ?: this.orchestrationConfig.defaultChatter).getChildClient(task),
            temperature = this.orchestrationConfig.temperature,
        )
        val inquiryResult = if (!orchestrationConfig.autoFix) Discussable(
            task = task,
            userMessage = {
                "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                    taskConfig?.inquiry_questions?.joinToString(
                        "\n"
                    )
                }\nGoal: ${taskConfig?.inquiry_goal}\n${this.executionConfig?.toJson()}"
            },
            heading = "",
            initialResponse = { it: String -> insightActor.answer(toInput(it)) },
            outputFn = { design: String ->
                MarkdownUtil.renderMarkdown(design, ui = task.manager)
            },
            reviseResponse = { usermessages: List<Pair<String, Role>> ->
                val inStr = "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                    taskConfig?.inquiry_questions?.joinToString("\n")
                }\nGoal: ${taskConfig?.inquiry_goal}\n${this.executionConfig?.toJson()}"
                val messages = usermessages.map { ModelSchema.ChatMessage(it.second, it.first.toContentList()) }
                    .toTypedArray<ModelSchema.ChatMessage>()
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
            task.add(MarkdownUtil.renderMarkdown(this, ui = task.manager))
        }
        resultFn(inquiryResult ?: "(no response)")
    }

    private fun getInputFileCode(): String =
        ((executionConfig?.input_files ?: listOf()))
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
                    val content = if (executionConfig?.extractContent == true && !isTextFile(file)) {
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

    private fun isTextFile(file: File): Boolean {
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

    private fun extractDocumentContent(file: File) = try {
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
        private val log = LoggerFactory.getLogger(AnalysisTask::class.java)
        val Analysis = TaskType(
            "Analysis",
            AnalysisTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
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