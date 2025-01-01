package com.simiacryptus.skyenet.apps.plan.tools.file

import com.simiacryptus.diff.AddApplyDiffLinks
import com.simiacryptus.diff.AddApplyFileDiffLinks.Companion
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.apps.plan.*
import com.simiacryptus.skyenet.core.actors.SimpleActor
import com.simiacryptus.skyenet.util.MarkdownUtil
import com.simiacryptus.skyenet.webui.actors.LargeOutputActor
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.util.concurrent.Semaphore

class EssayWriterTask(
  planSettings: PlanSettings,
  planTask: EssayWriterTaskConfigData?
) : AbstractTask<EssayWriterTask.EssayWriterTaskConfigData>(planSettings, planTask) {

  class EssayWriterTaskConfigData(
    @Description("The topic or title of the essay")
    val topic: String? = null,
    @Description("Key points or arguments to be covered")
    val keyPoints: List<String>? = null,
    @Description("Target audience for the essay")
    val targetAudience: String? = null,
    @Description("Desired tone (formal, casual, technical, etc)")
    val tone: String? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = null
  ) : TaskConfigBase(
    task_type = TaskType.EssayWriter.name,
    task_description = task_description,
    task_dependencies = task_dependencies,
    state = state
  )

  override fun promptSegment() = """
    EssayWriter - Generate a polished essay with iterative refinement
      ** Specify topic and key points
      ** Define target audience and tone
      ** Set desired word count
      ** Optionally specify input files for reference material
      ** Specify output file for the final essay
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
    val process = { sb: StringBuilder ->
      // Initial essay generation
      val initialEssay = LargeOutputActor(
        prompt = """
        You are a professional essay writer tasked with creating a well-structured, engaging essay.
        Consider the following aspects:
        - Target audience and appropriate tone
        - Clear introduction, body, and conclusion
        - Logical flow between sections
        - Supporting evidence and examples
        - Engaging narrative style
        
        Break down the content into logical sections using markdown headers.
        Use expansion markers (...sectionName...) for sections that need detailed development.
      """.trimIndent(),
        model = planSettings.getTaskSettings(TaskType.EssayWriter).model ?: planSettings.defaultModel,
        temperature = planSettings.temperature
      )
        .answer(
          messages + listOf(
            """
          Topic: ${taskConfig?.topic}
          Key Points: ${taskConfig?.keyPoints?.joinToString("\n- ", prefix = "- ")}
          Target Audience: ${taskConfig?.targetAudience}
          Tone: ${taskConfig?.tone}
          """.trimIndent()
          ).filter { it.isNotBlank() },
          api
        )

      // Apply redundancy review changes
      var currentEssay = initialEssay
      val redundancyResponse = SimpleActor(
        name = "RedundancyReviewer",
        prompt = "Review the essay to identify instances of redundant text or unnecessary framing language, and remove them.\n" + AddApplyDiffLinks.patchEditorPrompt,
        model = planSettings.getTaskSettings(TaskType.EssayWriter).model ?: planSettings.defaultModel,
        temperature = 0.3
      ).answer(
        messages + listOf(currentEssay),
          api
        )
      AddApplyDiffLinks.addApplyDiffLinks(
        agent.ui.socketManager!!,
        { currentEssay },
        redundancyResponse,
        { newCode -> currentEssay = newCode },
        task,
        agent.ui,
        shouldAutoApply = true,
      )

      // Apply narrative enhancements
      val narrativeResponse = SimpleActor(
        name = "NarrativeEnhancer",
        prompt = "Review and enhance the essay's narrative quality.\n" + AddApplyDiffLinks.patchEditorPrompt,
        model = planSettings.getTaskSettings(TaskType.EssayWriter).model ?: planSettings.defaultModel,
        temperature = 0.3
      ).answer(
        messages + listOf(currentEssay),
        api
      )
      AddApplyDiffLinks.addApplyDiffLinks(
        self = agent.ui.socketManager,
        code = { currentEssay },
        response = narrativeResponse,
        handle = { newCode -> currentEssay = newCode },
        task = task,
        ui = agent.ui,
        shouldAutoApply = true,
      )

      resultFn(currentEssay)

      if (agent.planSettings.autoFix) {
        task.complete()
        semaphore.release()
      }

      MarkdownUtil.renderMarkdown(currentEssay, ui = agent.ui) {
        com.simiacryptus.diff.AddApplyFileDiffLinks.instrumentFileDiffs(
          agent.ui.socketManager,
          root = agent.root,
          response = it,
          handle = { newCodeMap ->
            newCodeMap.forEach { (path, newCode) ->
              task.complete("<a href='${"fileIndex/${agent.session}/$path"}'>$path</a> Updated")
            }
          },
          ui = agent.ui,
          api = api,
          model = planSettings.getTaskSettings(TaskType.EssayWriter).model ?: planSettings.defaultModel,
        ) + if (!agent.planSettings.autoFix) acceptButtonFooter(agent.ui) {
          task.complete()
          semaphore.release()
        } else "\n\n## Auto-applied changes"
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
    private val log = LoggerFactory.getLogger(EssayWriterTask::class.java)
  }
}