package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.PoliticalOptimizationTask.PoliticalOptimizationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object PoliticalOptimizationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

    //@org.junit.jupiter.api.Test
    @Timeout(15, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = PoliticalOptimizationTask.PoliticalOptimization,
            typeConfig = TaskTypeConfig(
                task_type = PoliticalOptimizationTask.PoliticalOptimization.name
            ),
            executionConfig = PoliticalOptimizationTaskExecutionConfigData(
                initial_text = "The government should implement a carbon tax to combat climate change while providing subsidies for renewable energy development.",
                optimization_goal = "Maximize consensus across the political spectrum while maintaining environmental effectiveness.",
                perspectives = listOf("progressive", "conservative", "libertarian", "centrist"),
                evaluation_criteria = listOf("economic_impact", "environmental_effectiveness", "individual_liberty", "social_equity"),
                consensus_mode = "maximize",
                num_generations = 2, // Low number for testing purposes
                population_size = 4,  // Small population for testing
                selection_size = 2,
                consensus_weight = 0.7
            ),
            timeoutMinutes = 15,
        ).run()
    }
}