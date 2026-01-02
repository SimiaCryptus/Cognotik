package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.LateralThinkingTask.LateralThinkingTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object LateralThinkingTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

     @Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = LateralThinkingTask.LateralThinking,
            typeConfig = TaskTypeConfig(
                task_type = LateralThinkingTask.LateralThinking.name
            ),
            executionConfig = LateralThinkingTaskExecutionConfigData(
                problem = "How to reduce plastic waste in urban environments?",
                techniques = listOf(
                    "reversal",
                    "random_stimulus",
                    "challenge_assumptions",
                    "exaggeration"
                ),
                num_alternatives = 3,
                evaluate_feasibility = true,
                domain_context = "Urban waste management and circular economy",
                constraints = listOf(
                    "Must be cost-effective for municipal implementation",
                    "Should encourage citizen participation",
                    "Must be scalable to cities of different sizes"
                ),
                task_description = "Apply lateral thinking to find innovative solutions for urban plastic waste reduction",
            ),
            timeoutMinutes = 15,
        ).run()
    }
}