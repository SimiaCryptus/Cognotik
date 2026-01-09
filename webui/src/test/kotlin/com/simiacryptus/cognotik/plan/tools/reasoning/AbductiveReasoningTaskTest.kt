package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.AbductiveReasoningTask.AbductiveReasoningTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object AbductiveReasoningTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

     @org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = AbductiveReasoningTask.AbductiveReasoning,
            typeConfig = TaskTypeConfig(
                task_type = AbductiveReasoningTask.AbductiveReasoning.name
            ),
            executionConfig = AbductiveReasoningTaskExecutionConfigData(
                observations = listOf(
                    "The application memory usage increases steadily over 24 hours",
                    "Garbage collection occurs frequently but doesn't reclaim much space",
                    "The issue only occurs when the 'Image Processing' module is active",
                    "Thread dumps show many 'ImageProcessor' threads in WAITING state"
                ),
                domain_context = "A high-throughput JVM-based image processing service",
                max_hypotheses = 3,
                suggest_tests = true,
                evaluate_criteria = listOf(
                    "explanatory_power",
                    "simplicity",
                    "testability",
                    "prior_probability"
                )
            ),
            timeoutMinutes = 10,
        ).run()
    }
}