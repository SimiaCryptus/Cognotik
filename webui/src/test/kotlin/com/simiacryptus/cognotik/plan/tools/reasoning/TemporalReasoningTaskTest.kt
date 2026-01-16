package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.TemporalReasoningTask.TemporalReasoningTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object TemporalReasoningTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    @org.junit.jupiter.api.Tag("Integration")
     @org.junit.jupiter.api.Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = TemporalReasoningTask.TemporalReasoning,
            typeConfig = TaskTypeConfig(
                task_type = TemporalReasoningTask.TemporalReasoning.name
            ),
            executionConfig = TemporalReasoningTaskExecutionConfigData(
                subject = "System Architecture Evolution",
                time_range = "2023-01-01 to 2024-01-01",
                granularity = "monthly",
                task_description = "Analyze the evolution of the system architecture and predict future technical debt accumulation",
                identify_patterns = true,
                predict_future = true,
                prediction_horizon = "6 months",
                related_files = listOf("architecture_decisions.md", "changelog.md"),
                analyze_rate_of_change = true,
                identify_transitions = true
            ),
            timeoutMinutes = 10,
        ).run()
    }
}