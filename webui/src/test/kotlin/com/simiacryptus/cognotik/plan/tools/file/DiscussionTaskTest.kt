package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.DiscussionTask.DiscussionTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object DiscussionTaskTest {

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
      taskType = DiscussionTask.Discussion,
      typeConfig = TaskTypeConfig(
        task_type = DiscussionTask.Discussion.name
      ),
      executionConfig = DiscussionTaskExecutionConfigData(
        inquiry_questions = listOf(
          "What are the primary responsibilities of the Calculator class?",
          "Are there any potential edge cases in the current implementation?"
        ),
        inquiry_goal = "Gain a deep understanding of the Calculator implementation and identify potential improvements.",
        input_files = listOf("Calculator.kt"),
        task_description = "Perform a technical analysis of the Calculator class.",
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
    ).run()
  }
}