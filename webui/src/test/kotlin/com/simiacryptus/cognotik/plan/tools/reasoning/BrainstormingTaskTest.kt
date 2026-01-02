package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask.BrainstormingTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object BrainstormingTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

   //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = BrainstormingTask.Brainstorming,
            typeConfig = TaskTypeConfig(
                task_type = BrainstormingTask.Brainstorming.name
            ),
            executionConfig = BrainstormingTaskExecutionConfigData(
                problem_statement = "Design a scalable architecture for a real-time chat application using Kotlin and WebSockets.",
                target_option_count = 3,
                categories = listOf("Architecture", "Infrastructure", "User Experience"),
                constraints = listOf("Must support 100k concurrent users", "Low latency is critical"),
                include_creative_options = true,
                analysis_depth = "brief",
                task_description = "Brainstorm architectural options for a high-scale chat application"
            ),
            timeoutMinutes = 10,
        ).run()
    }
}