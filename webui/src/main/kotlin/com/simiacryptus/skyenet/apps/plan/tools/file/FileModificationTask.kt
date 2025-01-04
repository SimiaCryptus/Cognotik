package com.simiacryptus.skyenet.apps.plan.tools.file

import com.simiacryptus.diff.AddApplyFileDiffLinks

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.apps.plan.PlanCoordinator
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.apps.plan.TaskType
import com.simiacryptus.skyenet.apps.plan.tools.file.FileModificationTask.FileModificationTaskConfigData
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

class FileModificationTask(
  planSettings: PlanSettings,
  planTask: FileModificationTaskConfigData?
) : AbstractFileTask<FileModificationTaskConfigData>(planSettings, planTask) {
  class FileModificationTaskConfigData(
    input_files: List<String>? = null,
    output_files: List<String>? = null,
    @Description("Specific modifications to be made to the files")
    val modifications: Any? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : FileTaskConfigBase(
    task_type = TaskType.FileModification.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    input_files = input_files,
    output_files = output_files,
    state = state
  )

  val fileModificationActor by lazy {
    SimpleActor(
      name = "FileModification",
      prompt = """
        Generate precise code modifications and new files based on requirements:
        For modifying existing files:
        - Write efficient, readable, and maintainable code changes
        - Ensure modifications integrate smoothly with existing code
        - Follow project coding standards and patterns
        - Consider dependencies and potential side effects
        - Provide clear context and rationale for changes
                      
        For creating new files:
        - Choose appropriate file locations and names
        - Structure code according to project conventions
        - Include necessary imports and dependencies
        - Add comprehensive documentation
        - Ensure no duplication of existing functionality
                      
        Provide a clear summary explaining:
        - What changes were made and why
        - Any important implementation details
        - Potential impacts on other code
        - Required follow-up actions
                      
        Response format:
        For existing files: Use ${TRIPLE_TILDE}diff code blocks with a header specifying the file path.
        For new files: Use $TRIPLE_TILDE code blocks with a header specifying the new file path.
        The diff format should use + for line additions, - for line deletions.
        Include 2 lines of context before and after every change in diffs.
        Separate code blocks with a single blank line.
        For new files, specify the language for syntax highlighting after the opening triple backticks.
                      
        Example:
                      
        Here are the modifications:
                      
        ### src/utils/existingFile.js
        ${TRIPLE_TILDE}diff
        // Existing utility functions
        function existingFunction() {
        return 'old result';
        return 'new result';
        }
        $TRIPLE_TILDE
                      
        ### src/utils/newFile.js
        ${TRIPLE_TILDE}js
        // New utility functions
        function newFunction() {
         return 'new functionality';
        }
        $TRIPLE_TILDE
        """.trimIndent(),
      model = planSettings.getTaskSettings(TaskType.FileModification).model ?: planSettings.defaultModel,
      temperature = planSettings.temperature,
    )
  }

  override fun promptSegment() = """
   FileModification - Modify existing files or create new files
     ** For each file, specify the relative file path and the goal of the modification or creation
     ** List input files/tasks to be examined when designing the modifications or new files
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
    val defaultFile = if (((taskConfig?.input_files ?: listOf()) + (taskConfig?.output_files ?: listOf())).isEmpty()) {
      task.complete("CONFIGURATION ERROR: No input files specified")
      resultFn("CONFIGURATION ERROR: No input files specified")
      return
    } else if (((taskConfig?.input_files ?: listOf()) + (taskConfig?.output_files ?: listOf())).distinct().size == 1) {
      ((taskConfig?.input_files ?: listOf()) + (taskConfig?.output_files ?: listOf())).first()
    } else {
      null
    }

    val semaphore = Semaphore(0)
    val onComplete = { semaphore.release() }
    val process = { sb: StringBuilder ->
      val codeResult = fileModificationActor.answer(
        (messages + listOf(
          getInputFileCode(),
          this.taskConfig?.task_description ?: "",
        )).filter { it.isNotBlank() }, api
      )
      resultFn(codeResult)
      if (agent.planSettings.autoFix) {
        task.complete()
        onComplete()
        renderMarkdown(codeResult, ui = agent.ui) {
          AddApplyFileDiffLinks.instrumentFileDiffs(
            agent.ui.socketManager!!,
            root = agent.root,
            response = it,
            handle = { newCodeMap ->
              newCodeMap.forEach { (path, newCode) ->
                task.complete("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
              }
            },
            ui = agent.ui,
            api = api,
            shouldAutoApply = { agent.planSettings.autoFix },
            model = planSettings.getTaskSettings(TaskType.FileModification).model ?: planSettings.defaultModel,
            defaultFile = defaultFile
          ) + "\n\n## Auto-applied changes"
        }
      } else {
        renderMarkdown(codeResult, ui = agent.ui) {
          AddApplyFileDiffLinks.instrumentFileDiffs(
            agent.ui.socketManager!!,
            root = agent.root,
            response = it,
            handle = { newCodeMap ->
              newCodeMap.forEach { (path, newCode) ->
                task.complete("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
              }
            },
            ui = agent.ui,
            api = api,
            model = planSettings.getTaskSettings(TaskType.FileModification).model ?: planSettings.defaultModel,
            defaultFile = defaultFile,
          ) + acceptButtonFooter(agent.ui) {
            task.complete()
            onComplete()
          }
        }
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
    private val log = LoggerFactory.getLogger(FileModificationTask::class.java)
  }
}