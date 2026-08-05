package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.TechnicalExplanationTask.TechnicalExplanationTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object TechnicalExplanationTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = TechnicalExplanationTask.TechnicalExplanation,
      typeConfig = TaskTypeConfig(
        task_type = TechnicalExplanationTask.TechnicalExplanation.name
      ),
      executionConfig = TechnicalExplanationTaskExecutionConfigData(
        topic = "Kotlin Coroutines and Structured Concurrency",
        target_audience = "intermediate",
        level_of_detail = "moderate_detail",
        include_code_examples = true,
        explanation_format = "markdown",
        use_analogies = true,
        define_terminology = true,
        include_examples = true,
        code_language = "kotlin",
        revision_passes = 1
      ),
      timeoutMinutes = 15,
      user = ApplicationServicesConfig.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}