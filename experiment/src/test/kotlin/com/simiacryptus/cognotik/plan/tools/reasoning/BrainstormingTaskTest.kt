package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask.BrainstormingTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object BrainstormingTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = BrainstormingTask.Brainstorming,
      typeConfig = TaskTypeConfig(
        task_type = BrainstormingTask.Brainstorming.name
      ),
      executionConfig = BrainstormingTaskExecutionConfigData(
        problem_statement = "Design a scalable architecture for a real-time collaborative code editor (like Google Docs for code) using Kotlin.",
        target_option_count = 3,
        categories = listOf("Architecture", "Data Synchronization", "Conflict Resolution"),
        constraints = listOf(
          "Must support offline editing",
          "Eventual consistency is required",
          "Low latency for active sessions"
        ),
        include_creative_options = true,
        analysis_depth = "brief",
        task_description = "Brainstorm architectural options for a high-scale chat application"
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}