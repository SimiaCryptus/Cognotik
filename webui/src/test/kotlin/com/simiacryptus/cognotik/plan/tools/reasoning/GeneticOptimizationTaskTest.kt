package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.GeneticOptimizationTask.GeneticOptimizationTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object GeneticOptimizationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = GeneticOptimizationTask.GeneticOptimization,
            typeConfig = TaskTypeConfig(
                task_type = GeneticOptimizationTask.GeneticOptimization.name
            ),
            executionConfig = GeneticOptimizationTaskExecutionConfigData(
                initial_text = listOf("Write a function to calculate the factorial of a number."),
                optimization_goal = "Make the prompt more specific, professional, and include instructions for handling edge cases like negative numbers or non-integers.",
                num_generations = 2,
                population_size = 4,
                selection_size = 2,
                mutation_strategies = listOf("rephrase", "elaborate", "restructure"),
                enable_crossover = true,
                evaluation_weights = mapOf(
                    "clarity" to 0.3,
                    "completeness" to 0.4,
                    "professionalism" to 0.3
                )
            ),
            timeoutMinutes = 15,
        ).run()
    }
}