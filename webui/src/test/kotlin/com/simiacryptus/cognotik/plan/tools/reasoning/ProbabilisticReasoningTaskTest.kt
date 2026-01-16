package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.ProbabilisticReasoningTask.ProbabilisticReasoningTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object ProbabilisticReasoningTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    @org.junit.jupiter.api.Tag("Integration")
     @org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = ProbabilisticReasoningTask.ProbabilisticReasoning,
            typeConfig = TaskTypeConfig(
                task_type = ProbabilisticReasoningTask.ProbabilisticReasoning.name
            ),
            executionConfig = ProbabilisticReasoningTaskExecutionConfigData(
                decision_context = "Investigating a performance regression in the production API",
                hypotheses = mapOf(
                    "Database connection pool exhaustion" to 0.4,
                    "Memory leak in the new caching layer" to 0.3,
                    "Increased latency in downstream microservice" to 0.2,
                    "Inefficient O(N^2) algorithm in recent commit" to 0.1
                ),
                evidence = listOf(
                    "CPU usage is normal on API nodes",
                    "Database dashboard shows 95% connection utilization",
                    "Latency spikes correlate with high traffic volume",
                    "Restarting the API nodes provides temporary relief for 5 minutes"
                ),
                calculate_expected_value = true,
                identify_key_uncertainties = true,
                suggest_experiments = true,
                risk_tolerance = "medium",
                task_description = "Perform Bayesian analysis to identify the root cause of API performance issues."
            ),
            timeoutMinutes = 10,
        ).run()
    }
}