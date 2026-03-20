package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.OrchestrationConfig.Companion.instance
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileAppendTask.FileAppendTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApiChatModel
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.util.concurrent.Semaphore

class FileAppendTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: FileAppendTaskExecutionConfigData?
) : AbstractTask<FileAppendTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {

  class FileAppendTaskExecutionConfigData(
    @Description("The file to append content to")
    var file: String? = null,
    @Description("Additional files to provide context for the append operation")
    var related_files: List<String>? = null,
    @Description("The specific content to append or a description of what to add")
    var append_content: String? = null,
    task_description: String? = mutableListOf<String>().toString(),
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : TaskExecutionConfig(
    task_type = FileAppend.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (file.isNullOrBlank()) {
        return "A file must be specified in 'file'"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment() = """
FileAppend - Append content to the end of an existing file
  * Specify the relative file path and the content or goal of the addition.
  * The target file is NOT provided to the AI; only related context files are.
  * Useful for adding log entries, updating lists, or adding new exports/imports at the end of a file
""".trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val typeConfig = typeConfig ?: throw RuntimeException("Type configuration missing")
    val targetPath = executionConfig?.file ?: throw RuntimeException("Target file missing")
    val chatInterface = (typeConfig.model?.let<ApiChatModel, ChatInterface> {
      it.instance(orchestrationConfig.user)
    }
      ?: defaultSmart).getChildClient(task)
    val semaphore = Semaphore(0)
    val completionNotes = mutableListOf<String>()
    val transcript = task.newUserFileStream(transcriptFile())
    val tabs = TabbedDisplay(task)
    val overviewTab = tabs.newTask("Overview")

    try {
      overviewTab.header("File Append Task: $targetPath")
      val status = overviewTab.add("🔄 Preparing append operation...".renderMarkdown())

      transcript?.write("# File Append Task Transcript\n\n".toByteArray())
      Retryable(task, process = { subTask: SessionTask ->
        completionNotes.clear()
        val context = getInputFileContent(executionConfig?.related_files, root)
        if (context.isNotBlank()) {
          val contextTab = tabs.newTask("Context")
          contextTab.add("### Context Files\n\n$context".renderMarkdown())
          contextTab.complete()
          transcript?.write(
            """
                        <details>
                        <summary>Context Files Used</summary>
                        $context
                        </details>
                    """.trimIndent().toByteArray()
          )
        }
        status?.setLength(0)
        status?.append("🔄 Generating content to append...".renderMarkdown())
        subTask.update()

        val chatAgent = ChatAgent(
          name = "FileAppend",
          prompt = """
                        You are an assistant specialized in appending content to files.
                        Based on the requirements, generate the exact text to be added to the END of the specified file.
                        
                        
                        Provide ONLY the content to be appended. 
                        If you use a markdown code block to wrap the content, ensure it is the only thing in your response.
                        Do not include headers or explanations.
                    """.trimIndent(),
          model = chatInterface,
          temperature = this.orchestrationConfig.temperature,
        )

        val codeResult = chatAgent.answer(
          (messages + listOf(
            "Context Files:\n$context",
            "Target File Path: $targetPath",
            "Append Goal/Content: ${executionConfig?.append_content ?: executionConfig?.task_description ?: ""}",
          )).filter { it.isNotBlank() }
        ).let { extractCode(it) }
        val proposedTab = tabs.newTask("Proposed Append")
        proposedTab.add("### Proposed Append to `$targetPath`\n\n```\n$codeResult\n```".renderMarkdown())


        transcript?.write("\n## AI Proposed Append to $targetPath\n\n".toByteArray())
        transcript?.write(codeResult.toByteArray())

        val appendAction = {
          val file = root.resolve(targetPath).toFile()
          file.appendText(codeResult)
          completionNotes += ("<a href='fileIndex/${agent.session}/$targetPath'>$targetPath</a> Appended")
        }

        if (orchestrationConfig.autoFix) {
          appendAction()
          status?.setLength(0)
          status?.append("✅ **Auto-applied append to `$targetPath`.**".renderMarkdown())
          proposedTab.complete()
          semaphore.release()
        } else {
          val footer = acceptButtonFooter(task.ui) {
            appendAction()
            status?.setLength(0)
            status?.append("✅ **Appended successfully to `$targetPath`.**".renderMarkdown())
            proposedTab.complete()
            semaphore.release()
          }
          proposedTab.add(footer)
        }

        transcript?.flush()
      }.async(task.ui))

      semaphore.acquire()
      overviewTab.complete()
      transcript?.write("\n## Completion Notes\n\n".toByteArray())
      transcript?.write(completionNotes.joinToString("\n").toByteArray())
      resultFn(completionNotes.joinToString("\n"))
    } catch (e: Throwable) {
      // Triple Log Rule
      task.error(e)
      log.error("Error in FileAppendTask for $targetPath: ${e.message}", e)
      transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      overviewTab.add("❌ **Error:** ${e.message}".renderMarkdown())
      overviewTab.complete()
    } finally {
      transcript?.close()
    }
  }

  private fun extractCode(text: String): String {
    val regex = """(?s)```(?:\w+)?\n(.*?)\n```""".toRegex()
    return regex.find(text)?.groupValues?.get(1) ?: text
  }


  companion object {
    private val log = LoggerFactory.getLogger(FileAppendTask::class.java)

    @JvmStatic
    val FileAppend = TaskType(
      name = "FileAppend",
      category = "File",
      taskClass = FileAppendTask::class.java,
      executionConfigClass = FileAppendTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Append content to the end of existing files",
      tooltipHtml = """
                          Allows for precise additions to the end of files without modifying existing content.
                          <ul>
                            <li>Ideal for logs, exports, and list updates</li>
                            <li>Supports AI-generated content based on context</li>
                            <li>Provides reviewable previews before applying changes</li>
                            <li>Integrates with project structure and standards</li>
                          </ul>
                      """.trimIndent(),
    )
  }
}