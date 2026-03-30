package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.StructuralInvariantAnalysisTask.StructuralInvariantAnalysisTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object StructuralInvariantAnalysisTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = StructuralInvariantAnalysisTask.StructuralInvariantAnalysis,
      typeConfig = TaskTypeConfig(
        task_type = StructuralInvariantAnalysisTask.StructuralInvariantAnalysis.name
      ),
      executionConfig = StructuralInvariantAnalysisTaskExecutionConfigData(
        subject_object = "A Binary Search Tree",
        transformation_types = listOf("scaling", "node_deletion", "context_inversion"),
        output_format = "fingerprint",
        related_files = emptyList()
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    ).run()
  }
}