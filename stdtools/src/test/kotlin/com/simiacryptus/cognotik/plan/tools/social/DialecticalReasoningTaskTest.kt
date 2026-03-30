package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.DialecticalReasoningTask.DialecticalReasoningTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object DialecticalReasoningTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = DialecticalReasoningTask.DialecticalReasoning,
      typeConfig = TaskTypeConfig(
        task_type = DialecticalReasoningTask.DialecticalReasoning.name
      ),
      executionConfig = DialecticalReasoningTaskExecutionConfigData(
        thesis = "Functional programming is the superior paradigm for building scalable and maintainable software systems.",
        antithesis = "Object-oriented programming is the superior paradigm for building scalable and maintainable software systems.",
        context = "Modern enterprise software development and team productivity.",
        synthesis_levels = 3,
        preserve_strengths = true
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    ).run()
  }
}