package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.LLMExperimentTask.LLMExperimentTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object LLMExperimentTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
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
        ).run()
    }
}