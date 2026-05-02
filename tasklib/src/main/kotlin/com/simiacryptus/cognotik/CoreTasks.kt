package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.cognitive.*
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.file.*
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.plan.tools.online.SeleniumFetchTask
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask
import com.simiacryptus.cognotik.plan.tools.reasoning.SocraticDialogueTask
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.plan.tools.social.DialecticalReasoningTask
import com.simiacryptus.cognotik.plan.tools.social.GameTheoryTask
import com.simiacryptus.cognotik.plan.tools.social.MultiPerspectiveAnalysisTask
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask
import com.simiacryptus.cognotik.plan.tools.writing.*

object CoreTasks : CognotikPlugin {

  @JvmStatic
  val Chat = CognitiveModeType("Chat", ConversationalModeConfig::class.java, inputCnt = ConversationalMode.inputCnt)

  @JvmStatic
  val Adaptive = CognitiveModeType(
    "Adaptive", AdaptivePlanningConfig::class.java, inputCnt = AdaptivePlanningMode.inputCnt
  )

  @JvmStatic
  val Waterfall = CognitiveModeType(
    "Waterfall", WaterfallMode.WaterfallModeConfig::class.java, inputCnt = WaterfallMode.inputCnt
  )

  override fun init() {
    // Core Task Types
    TaskType.registerTaskType(AutoFixTask.AutoFix)
    TaskType.registerTaskType(AudioGenerationTask.GenerateAudio)
    TaskType.registerTaskType(BrainstormingTask.Brainstorming)
    TaskType.registerTaskType(ComicBookGenerationTask.ComicBookGeneration)
    TaskType.registerTaskType(CreateErbTemplateTask.CreateErbTemplate)
    TaskType.registerTaskType(CrawlerAgentTask.CrawlerAgent)
    TaskType.registerTaskType(DialecticalReasoningTask.DialecticalReasoning)
    TaskType.registerTaskType(FileModificationTask.FileModification)
    TaskType.registerTaskType(IllustrateDocumentTask.IllustrateDocument)
    TaskType.registerTaskType(ImageGenerationTask.GenerateImage)
    TaskType.registerTaskType(GameTheoryTask.GameTheory)
    TaskType.registerTaskType(IterativeFileModificationTask.IterativeFileModification)
    TaskType.registerTaskType(MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis)
    TaskType.registerTaskType(NarrativeGenerationTask.NarrativeGeneration)
    TaskType.registerTaskType(PersuasiveEssayTask.PersuasiveEssay)
    TaskType.registerTaskType(RenderErbTemplateTask.RenderErbTemplate)
    TaskType.registerTaskType(SeleniumFetchTask.SeleniumFetch)
    TaskType.registerTaskType(SocraticDialogueTask.SocraticDialogue)
    TaskType.registerTaskType(SubPlanTask.SubPlan)
    TaskType.registerTaskType(TutorialGenerationTask.TutorialGeneration)
    TaskType.registerTaskType(WriteHtmlTask.WriteHtml)

    CognitiveModeType.registerCognitiveMode(Chat) { config, session, user -> ConversationalMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Adaptive) { config, session, user -> AdaptivePlanningMode(config, session, user) }
    CognitiveModeType.registerCognitiveMode(Waterfall) { config, session, user -> WaterfallMode(config, session, user) }


    OrchestrationConfig.exampleInstance = OrchestrationConfig.TaskBreakdownResult(
      tasksByID = mapOf(
        "1" to AutoFixTask.AutoFixTaskExecutionConfigData(
          task_description = "Task 1", task_dependencies = listOf(), commands = mutableListOf(
            AutoFixTask.CommandWithWorkingDir(
              command = mutableListOf("echo", "Hello, World!"), working_dir = "."
            )
          )
        ), "2" to FileModificationTask.FileModificationTaskExecutionConfigData(
          task_description = "Task 2",
          task_dependencies = listOf("1"),
          related_files = listOf("input2.txt"),
        ).apply {
          main_file = "output2.txt"
        }
      ),
    )

  }
}