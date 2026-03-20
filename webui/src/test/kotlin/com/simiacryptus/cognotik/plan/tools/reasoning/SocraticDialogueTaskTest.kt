package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.SocraticDialogueTask.SocraticDialogueTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object SocraticDialogueTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun testSocraticDialogue() {
    TaskHarness(
      taskType = SocraticDialogueTask.SocraticDialogue,
      typeConfig = TaskTypeConfig(
        task_type = SocraticDialogueTask.SocraticDialogue.name
      ),
      executionConfig = SocraticDialogueTaskExecutionConfigData(
        initial_question = "What is the nature of software quality?",
        max_depth = 2,
        challenge_assumptions = true,
        domain_constraints = listOf("Software Engineering", "Philosophy of Technology")
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
    ).run()
  }
}