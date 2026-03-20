package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.TutorialGenerationTask.TutorialGenerationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object TutorialGenerationTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = TutorialGenerationTask.TutorialGeneration,
      typeConfig = TaskTypeConfig(
        task_type = TutorialGenerationTask.TutorialGeneration.name
      ),
      executionConfig = TutorialGenerationTaskExecutionConfigData(
        goal = "Create a simple 'Hello World' application in Kotlin using Gradle",
        target_platform = "IntelliJ IDEA",
        skill_level = "beginner",
        target_step_count = 5,
        include_code_examples = true,
        include_troubleshooting = true,
        include_learning_objectives = true,
        include_next_steps = true,
        verbosity = "detailed"
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
    ).run()
  }
}