package com.simiacryptus.skyenet.apps.plan.tools.file

import com.simiacryptus.diff.AddApplyFileDiffLinks.Companion.instrumentFileDiffs
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskType
import com.simiacryptus.skyenet.apps.plan.tools.file.DocumentationTask.DocumentationTaskConfigData
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

class DocumentationTask(
  planSettings: PlanSettings,
  planTask: DocumentationTaskConfigData?
) : AbstractFileTask<DocumentationTaskConfigData>(planSettings, planTask) {
  class DocumentationTaskConfigData(
    @Description("List topics to document")
    val topics: List<String>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    input_files: List<String>? = null,
    output_files: List<String>? = null,
    state: TaskState? = null
  ) : FileTaskConfigBase(
    task_type = TaskType.Documentation.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    input_files = input_files,
    output_files = output_files,
    state = state
  )

  override fun promptSegment() = """
    Documentation - Generate documentation
      ** List input file names and tasks to be examined
      ** List topics to document
      ** List output files to be modified or created with documentation
  """.trimIndent()

  val documentationGeneratorActor by lazy {
    SimpleActor(
      name = "DocumentationGenerator",
      prompt = """
       Create detailed and clear documentation for the provided code, covering its purpose, functionality, inputs, outputs, and any assumptions or limitations.
       Use a structured and consistent format that facilitates easy understanding and navigation. 
       Include code examples where applicable, and explain the rationale behind key design decisions and algorithm choices.
       Document any known issues or areas for improvement, providing guidance for future developers on how to extend or maintain the code.
       For existing files, provide documentation in the form of comments within the code.
       For new files, create separate markdown files with the documentation.
       Response format:
       For existing files: Use ${TRIPLE_TILDE}diff code blocks with a header specifying the file path.
       For new files: Use $TRIPLE_TILDE markdown blocks with a header specifying the new file path.
       The diff format should use + for line additions, - for line deletions.
       Include 2 lines of context before and after every change in diffs.
       Separate code blocks with a single blank line.
       """.trimIndent(),
      model = planSettings.getTaskSettings(TaskType.Documentation).model ?: planSettings.defaultModel,
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
    if (((taskConfig?.input_files ?: listOf()) + (taskConfig?.output_files ?: listOf())).isEmpty()) {
      task.complete("No input or output files specified")
      return
    }
    val semaphore = Semaphore(0)
    val onComplete = {
      semaphore.release()
    }
    val process = { sb: StringBuilder ->
      val itemsToDocument = taskConfig?.topics ?: emptyList()
      val docResult = documentationGeneratorActor.answer(
        messages + listOf<String>(
          getInputFileCode(),
          "Items to document: ${itemsToDocument.joinToString(", ")}",
          "Output files: ${taskConfig?.output_files?.joinToString(", ") ?: ""}"
        ).filter { it.isNotBlank() }, api
      )
      resultFn(docResult)
      if (agent.planSettings.autoFix) {
        val diffLinks = instrumentFileDiffs(
          agent.ui.socketManager!!,
          root = agent.root,
          response = docResult,
          handle = { newCodeMap ->
            newCodeMap.forEach { (path, newCode) ->
              task.complete("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
            }
          },
          ui = agent.ui,
          api = api,
          shouldAutoApply = { agent.planSettings.autoFix },
          model = planSettings.getTaskSettings(TaskType.Documentation).model ?: planSettings.defaultModel,
        )
        task.complete()
        onComplete()
        MarkdownUtil.renderMarkdown(diffLinks + "\n\n## Auto-applied documentation changes", ui = agent.ui)
      } else {
        MarkdownUtil.renderMarkdown(
          instrumentFileDiffs(
            agent.ui.socketManager!!,
            root = agent.root,
            response = docResult,
            handle = { newCodeMap ->
              newCodeMap.forEach { (path, newCode) ->
                task.complete("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
              }
            },
            ui = agent.ui,
            api = api,
            model = planSettings.getTaskSettings(TaskType.Documentation).model ?: planSettings.defaultModel,
          ) + acceptButtonFooter(agent.ui) {
            task.complete()
            onComplete()
          }, ui = agent.ui
        )
      }
    }
    Retryable(agent.ui, task = task, process = process)
    try {
      semaphore.acquire()
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(DocumentationTask::class.java)
  }
}