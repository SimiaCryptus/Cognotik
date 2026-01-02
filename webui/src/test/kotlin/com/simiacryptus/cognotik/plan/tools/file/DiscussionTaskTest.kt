package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.DiscussionTask.DiscussionTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object DiscussionTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

     @Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = DiscussionTask.Discussion,
            typeConfig = TaskTypeConfig(
                task_type = DiscussionTask.Discussion.name
            ),
            executionConfig = DiscussionTaskExecutionConfigData(
                inquiry_questions = listOf(
                    "What are the primary responsibilities of the Calculator class?",
                    "Are there any potential edge cases in the current implementation?"
                ),
                inquiry_goal = "Gain a deep understanding of the Calculator implementation and identify potential improvements.",
                input_files = listOf("Calculator.kt"),
                task_description = "Perform a technical analysis of the Calculator class.",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}