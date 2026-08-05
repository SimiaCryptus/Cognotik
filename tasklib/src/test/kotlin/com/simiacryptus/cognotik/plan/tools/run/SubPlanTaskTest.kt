package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask.SubPlanTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask.SubPlanTaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.io.File

@Suppress("unused")
object SubPlanTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    val file = File("subplan_test.txt")
    if (file.exists()) file.delete()
    try {
      TaskHarness(
        taskType = SubPlanTask.SubPlan,
        typeConfig = SubPlanTaskTypeConfig(
          cognitiveSettings = WaterfallMode.WaterfallModeConfig(),
          taskSettings = mutableMapOf(
            FileModificationTask.FileModification.name to TaskTypeConfig(
              task_type = FileModificationTask.FileModification.name
            )
          ),
          purpose = "Testing recursive sub-planning capabilities"
        ),
        executionConfig = SubPlanTaskExecutionConfigData(
          planning_goal = "Create a file named 'subplan_test.txt' containing the text 'This was generated via a sub-plan.'",
          context = listOf("The environment is a standard Kotlin/JVM test environment.")
        ),
        timeoutMinutes = 10,
        user = ApplicationServicesConfig.defaultUser,
        smartModel = GeminiModels.GeminiFlash_30_Preview,
        fastModel = GeminiModels.GeminiFlash_30_Preview,
        imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
        audioModel = GeminiModels.GeminiFlash_30_Preview,
      ).run()
      Assertions.assertTrue(file.exists(), "File subplan_test.txt should have been created")
      Assertions.assertEquals("This was generated via a sub-plan.", file.readText().trim())
    } finally {
      if (file.exists()) file.delete()
    }
  }
}