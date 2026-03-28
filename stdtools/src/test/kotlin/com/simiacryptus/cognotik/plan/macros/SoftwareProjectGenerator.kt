package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.cognitive.AdaptivePlanningConfig
import com.simiacryptus.cognotik.plan.cognitive.CodingMode.CodingModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode.WaterfallModeConfig
import com.simiacryptus.cognotik.plan.tools.CoreTasks
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask.AutoFixTaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask.Companion.AutoFix
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Tag
import java.io.File

class SoftwareProjectGenerator {
  init {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  //@TestFactory
  @Tag("Research")
  fun tests() = listOf(
    WaterfallModeConfig(),
    AdaptivePlanningConfig(type = CoreTasks.Coding),
    CognitiveModeConfig(type = CoreTasks.Hierarchical),
    CodingModeConfig()
  ).flatMap { cognitiveSettings2 ->
    listOf(
      DynamicTest.dynamicTest("SoftwareProjectGenerator_" + cognitiveSettings2.type?.name + "_blind") {
        ProjectGenerator(
          cognitiveSettings2 = cognitiveSettings2,
          tasks = mutableMapOf(
            FileModification.name to TaskTypeConfig(task_type = FileModification.name),
          ),
          testName = "SoftwareProjectGenerator_" + cognitiveSettings2.type?.name + "_blind",
          prompt = "Build a fun and unique browser-based game.",
        ).run()
      },
      DynamicTest.dynamicTest("SoftwareProjectGenerator_" + cognitiveSettings2.type?.name + "_spec") {
        ProjectGenerator(
          cognitiveSettings2 = cognitiveSettings2,
          tasks = mutableMapOf(
            FileModification.name to TaskTypeConfig(task_type = FileModification.name),
          ),
          testName = "SoftwareProjectGenerator_" + cognitiveSettings2.type?.name + "_spec",
          prompt = "Build a fun and unique browser-based game. As the first phase of implementation, create detailed plan and design documents.",
        ).run()
      },
      DynamicTest.dynamicTest("SoftwareProjectGenerator_" + cognitiveSettings2.type?.name + "_fixed") {
        ProjectGenerator(
          cognitiveSettings2 = cognitiveSettings2,
          tasks = mutableMapOf(
            FileModification.name to TaskTypeConfig(task_type = FileModification.name),
            AutoFix.name to AutoFixTaskTypeConfig(),
          ),
          testName = "SoftwareProjectGenerator_" + cognitiveSettings2.type?.name + "_fixed",
          prompt = "Build a fun and unique browser-based game.",
        ).run()
      }
    )
  }
}

class ProjectGenerator(
  cognitiveSettings1: CognitiveModeConfig = WaterfallModeConfig(),
  val cognitiveSettings2: CognitiveModeConfig,
  val tasks: MutableMap<String, TaskTypeConfig>,
  val testName: String,
  prompt: String,
) : PlanHarness(
  prompt = prompt,
  cognitiveSettings = cognitiveSettings1,
  user = com.simiacryptus.cognotik.platform.model.defaultUser,
  smartModel = GeminiModels.GeminiFlash_30_Preview,
  fastModel = GeminiModels.GeminiFlash_30_Preview,
  imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
) {
  override fun newConfig(session: Session, tempDir: File) = super.newConfig(session, tempDir).apply {
    taskSettings[BrainstormingTask.Brainstorming.name] = TaskTypeConfig(
      task_type = BrainstormingTask.Brainstorming.name,
    )
    taskSettings[SubPlanTask.SubPlan.name] = SubPlanTask.SubPlanTaskTypeConfig(
      name = SubPlanTask.SubPlan.name,
      cognitiveSettings = cognitiveSettings2,
      taskSettings = tasks
    )
  }

  override fun createTempDirectory() = File(".").resolve("workspaces/$testName/test-${now()}").apply { mkdirs() }
}