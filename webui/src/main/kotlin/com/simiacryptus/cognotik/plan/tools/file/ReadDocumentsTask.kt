package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.models.ModelSchema.Role
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask.Companion.TRIPLE_TILDE
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path

class ReadDocumentsTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: ReadDocumentsTaskExecutionConfigData?
) : AbstractTask<ReadDocumentsTask.ReadDocumentsTaskExecutionConfigData, ReadDocumentsTask.ReadDocumentsTaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  protected val codeFiles = mutableMapOf<Path, String>()

  class ReadDocumentsTaskTypeConfig(
    task_type: String? = ReadDocuments.name,
    name: String? = null
  ) : TaskTypeConfig(
    task_type = task_type,
    name = name
  ), ValidatedObject

  class ReadDocumentsTaskExecutionConfigData(
    @Description("The specific questions or topics to be addressed in the inquiry")
    val inquiry_questions: List<String>? = null,
    @Description("The goal or purpose of the inquiry")
    val inquiry_goal: String? = null,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    val input_files: List<String>? = null,
    @Description("A description of the task to be performed")
    task_description: String? = null,
    @Description("List of task IDs that this task depends on")
    task_dependencies: List<String>? = null,
    @Description("The current state of the task")
    state: TaskState? = null,
  ) : TaskExecutionConfig(
    task_type = ReadDocuments.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (inquiry_questions.isNullOrEmpty() && inquiry_goal.isNullOrBlank()) return "Either inquiry_questions or inquiry_goal must be provided"
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment() = (if (!orchestrationConfig.autoFix) """
  ReadDocuments - Deeply analyze project files and provide comprehensive technical insights or answers to specific questions.
    * inquiry_questions: Specific technical questions to address.
    * inquiry_goal: The high-level goal of the inquiry.
    * input_files: File patterns (e.g. **/*.kt) to examine.
  """ else """
  ReadDocuments - Directly answer questions or provide a report using the LLM. Reading files is optional.
    * inquiry_questions: Specific technical questions to address.
    * inquiry_goal: The high-level goal of the inquiry.
    * input_files: Optional file patterns to examine if relevant.
  """)

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {


    val transcript = task.newUserFileStream(transcriptFile())
    try {
      task.ui.pool.submit {
        try {
          val tabs = TabbedDisplay(task)
          val analysisTask = tabs.newTask("Analysis")
          val filesTask = tabs.newTask("Files")

          log.info("Starting ReadDocumentsTask execution")
          val fileContext = getInputFileCode()

          transcript?.write("## Files Read\n<details><summary>File Context</summary>\n\n$fileContext\n\n</details>\n".toByteArray())

          filesTask.header("Files Read", level = 3)
          filesTask.add(fileContext.renderMarkdown())
          filesTask.complete()

          val toInput = { it: String ->
            messages + listOf(
              fileContext,
              it,
            ).filter { it.isNotBlank() }
          }

          val taskConfig: ReadDocumentsTaskExecutionConfigData? = this.executionConfig
          val typeConfig = typeConfig ?: throw RuntimeException("Type configuration is missing")
          val insightActor = ChatAgent(
            name = "Insight",
            prompt = """
                            Create code for a new file that fulfills the specified requirements and context.
                            Given a detailed user request, break it down into smaller, actionable tasks suitable for software development.
                            Compile comprehensive information and insights on the specified topic.
                            Provide a comprehensive overview, including key concepts, relevant technologies, best practices, and any potential challenges or considerations.
                            Ensure the information is accurate, up-to-date, and well-organized to facilitate easy understanding.
                            """.trimIndent(),
            model = (typeConfig.model?.let { it.instance(orchestrationConfig.user) }
              ?: defaultSmart).getChildClient(analysisTask),
            temperature = this.orchestrationConfig.temperature,
          )
          val inquiryResult = Discussable(
            task = analysisTask,
            heading = "Read Documents",
            userMessage = {
              "Expand ${taskConfig?.task_description ?: ""}\nQuestions: ${
                taskConfig?.inquiry_questions?.joinToString("\n")
              }\nGoal: ${taskConfig?.inquiry_goal}\n${JsonUtil.toJson(data = this)}"
            },
            initialResponse = { prompt ->
              val input = toInput(prompt)
              transcript?.write("# Analysis Request\n\n${input.joinToString("\n\n")}\n\n".toByteArray())
              insightActor.answer(input)
            },
            outputFn = { it.renderMarkdown() },
            reviseResponse = { history ->
              val contextMessages = (messages + listOf(fileContext))
                .filter { it.isNotBlank() }
                .map { it to Role.user }
              insightActor.answer((contextMessages + history).map { it.first }.toList())
            }
          ).call()

          analysisTask.complete()
          log.info("ReadDocumentsTask completed successfully")
          resultFn(inquiryResult!!)
        } catch (e: Exception) {
          task.error(e)
          log.error("Error in ReadDocumentsTask: ${e.message}", e)
          transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
          throw e
        }
      }
    } finally {
      transcript?.close()
    }
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
    private val log = LoggerFactory.getLogger(ReadDocumentsTask::class.java)

    @JvmStatic
    val ReadDocuments = TaskType(
      name = "ReadDocuments",
      category = "File",
      taskClass = ReadDocumentsTask::class.java,
      executionConfigClass = ReadDocumentsTaskExecutionConfigData::class.java,
      taskSettingsClass = ReadDocumentsTaskTypeConfig::class.java,
      description = "Deeply analyze project files and provide comprehensive technical insights or answers to specific questions.",
      tooltipHtml = """
                      Analyzes project files and provides detailed technical insights using the LLM.
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

    fun getAvailableFiles(
      path: Path,
      treatDocumentsAsText: Boolean = false,
    ): List<String> {
      return try {
        listOf(
          FileSelectionUtils.filteredWalkAsciiTree(
            path.toFile(),
            20,
            treatDocumentsAsText = treatDocumentsAsText,
            render = { file: File ->
              val name = file.name
              val size: String? = if (file.isFile) {
                val length = file.length()
                when {
                  length < 1024 -> "$length B"
                  length < 1024 * 1024 -> String.format("%.2f KB", length / 1024.0)
                  length < 1024 * 1024 * 1024 -> String.format("%.2f MB", length / (1024.0 * 1024.0))
                  else -> String.format("%.2f GB", length / (1024.0 * 1024.0 * 1024.0))
                }
              } else {
                null
              }
              if (size != null) "$name ($size)" else name
            }
          )
        )
      } catch (e: Exception) {
        log.error("Error listing available files", e)
        listOf("Error listing files: ${e.message}")
      }
    }

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