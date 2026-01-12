package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask.BrainstormingTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object BrainstormingTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

   //@org.junit.jupiter.api.Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = BrainstormingTask.Brainstorming,
            typeConfig = TaskTypeConfig(
                task_type = BrainstormingTask.Brainstorming.name
            ),
            executionConfig = BrainstormingTaskExecutionConfigData(
                problem_statement = "Design a scalable architecture for a real-time collaborative code editor (like Google Docs for code) using Kotlin.",
                target_option_count = 3,
                categories = listOf("Architecture", "Data Synchronization", "Conflict Resolution"),
                constraints = listOf("Must support offline editing", "Eventual consistency is required", "Low latency for active sessions"),
                include_creative_options = true,
                analysis_depth = "brief",
                task_description = "Brainstorm architectural options for a high-scale chat application"
            ),
            timeoutMinutes = 10,
        ).run()
    }
}