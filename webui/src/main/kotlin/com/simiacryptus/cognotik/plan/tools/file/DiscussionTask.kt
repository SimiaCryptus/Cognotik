package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.PaginatedDocumentReader
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.TRIPLE_TILDE
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicReference

class DiscussionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DiscussionTaskExecutionConfigData?
) : AbstractTask<DiscussionTask.DiscussionTaskExecutionConfigData, DiscussionTask.DiscussionTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    protected val codeFiles = mutableMapOf<Path, String>()

    class DiscussionTaskTypeConfig(
        task_type: String? = Discussion.name,
        name: String? = null
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name
    ), ValidatedObject

    class DiscussionTaskExecutionConfigData(
        @Description("The specific questions or topics to be addressed in the inquiry")
        val inquiry_questions: List<String>? = null,
        @Description("The goal or purpose of the inquiry")
        val inquiry_goal: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = Discussion.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject

    override fun promptSegment() = (if (!orchestrationConfig.autoFix) """
  Discussion - Directly answer questions or provide insights using the LLM. Reading files is optional and can be included if relevant to the inquiry.
    * Specify the questions and the goal of the inquiry.
    * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
    * User response/feedback and iteration are supported.
  """ else """
  Discussion - Directly answer questions or provide a report using the LLM. Reading files is optional and can be included if relevant to the inquiry.
    * Specify the questions and the goal of the inquiry.
    * Optionally, list input files (supports glob patterns) to be examined when answering the questions.
  """)

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val (_, transcript) = initializeTranscript(task)

        val toInput = { it: String ->
            messages + listOf(
                getInputFileCode(),
                it,
            ).filter { it.isNotBlank() }
        }

        val taskConfig: DiscussionTaskExecutionConfigData? = this.executionConfig
        val typeConfig = typeConfig ?: throw RuntimeException()
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
                ?: defaultSmart).getChildClient(task),
            temperature = this.orchestrationConfig.temperature,
        )
        val inquiryResult = if (orchestrationConfig.autoFix) {
            val input = toInput(
                "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                    taskConfig?.inquiry_questions?.joinToString(
                        "\n"
                    )
                }\nGoal: ${taskConfig?.inquiry_goal}\n\n${JsonUtil.toJson(executionConfig)}"
            )
            transcript?.write("# Analysis Request\n\n${input.joinToString("\n\n")}\n\n".toByteArray())
            insightActor.answer(input)
        } else
            Discussable(
                task = task,
                userMessage = {
                    "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                        taskConfig?.inquiry_questions?.joinToString(
                            "\n"
                        )
                    }\nGoal: ${taskConfig?.inquiry_goal}\n${this.executionConfig?.toJson()}"
                },
                heading = taskConfig?.task_description ?: "Discussion",
                initialResponse = { it: String ->
                    transcript?.write("# Initial Request\n\n$it\n\n".toByteArray())
                    insightActor.answer(toInput(it)).also { response ->
                        transcript?.write("# Initial Response\n\n$response\n\n".toByteArray())
                    }
                },
                outputFn = { design: String ->
                    MarkdownUtil.renderMarkdown(design, ui = task.ui)
                },
                reviseResponse = { usermessages: List<Pair<String, Role>> ->
                    val inStr = "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                        taskConfig?.inquiry_questions?.joinToString("\n")
                    }\nGoal: ${taskConfig?.inquiry_goal}\n${this.executionConfig?.toJson()}"
                    val messages = usermessages.map { ModelSchema.ChatMessage(it.second, it.first.toContentList()) }
                        .toTypedArray<ModelSchema.ChatMessage>()
                    transcript?.write("# Revision Request\n\n${usermessages.joinToString("\n") { "${it.second}: ${it.first}" }}\n\n".toByteArray())
                    insightActor.respond(
                        messages = messages,
                        input = toInput(inStr),
                    ).also { response ->
                        transcript?.write("# Revision Response\n\n$response\n\n".toByteArray())
                    }
                },
                atomicRef = AtomicReference(),
                semaphore = Semaphore(0),
            ).call()
        transcript?.close()
        task.complete()
        resultFn(inquiryResult ?: "(no response)")
    }


    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            })
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .filterNotNull()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = if (!isTextFile(file)) {
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

    companion object {
        private val log = LoggerFactory.getLogger(DiscussionTask::class.java)
        val Discussion = TaskType(
          name = "Discussion",
          category = "File",
          taskClass = DiscussionTask::class.java,
          executionConfigClass = DiscussionTaskExecutionConfigData::class.java,
          taskSettingsClass = DiscussionTaskTypeConfig::class.java,
          description = "Directly answer questions or provide insights using the LLM, optionally referencing files, with optional user feedback and iteration.",
          tooltipHtml = """
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
                      """,
        )


        private val textExtensions = setOf(
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

        fun isTextFile(file: File): Boolean {
            return textExtensions.contains(file.extension.lowercase())
        }

        fun extractDocumentContent(file: File) = try {
            file.getDocumentReader().use { reader ->
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
    }
}