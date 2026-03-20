package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.LLMExperimentTask.LLMExperimentTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object LLMExperimentTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = LLMExperimentTask.LLMExperiment,
      typeConfig = TaskTypeConfig(
        task_type = LLMExperimentTask.LLMExperiment.name
      ),
      executionConfig = LLMExperimentTaskExecutionConfigData(
        prompt_templates = listOf(
          "Write a one-sentence story about a {character} who finds a {object}.",
          "In exactly ten words, describe a {character} interacting with a {object}."
        ),
        prompt_variables = mapOf(
          "character" to listOf("robot", "wizard"),
          "object" to listOf("rusty key", "glowing orb")
        ),
        temperature_values = listOf(0.2, 0.9),
        repetitions = 1,
        metrics = listOf("creativity", "adherence_to_length_constraints"),
        statistical_analysis = true
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
    ).run()
  }
}