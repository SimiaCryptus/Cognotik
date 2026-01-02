package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.CounterfactualAnalysisTask.CounterfactualAnalysisTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object CounterfactualAnalysisTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = CounterfactualAnalysisTask.CounterfactualAnalysis,
            typeConfig = TaskTypeConfig(
                task_type = CounterfactualAnalysisTask.CounterfactualAnalysis.name
            ),
            executionConfig = CounterfactualAnalysisTaskExecutionConfigData(
                actual_scenario = "The team decided to use a monolithic architecture for the new e-commerce platform to speed up initial development.",
                counterfactuals = listOf(
                    "The team decided to use a microservices architecture from the start.",
                    "The team decided to use a serverless architecture using AWS Lambda."
                ),
                compare_outcomes = true,
                control_factors = listOf("Project budget", "Team size", "Target launch date"),
                task_description = "Analyze the impact of different architectural choices on long-term scalability and maintenance costs.",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}