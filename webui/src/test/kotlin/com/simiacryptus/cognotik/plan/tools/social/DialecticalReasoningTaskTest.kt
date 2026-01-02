package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.DialecticalReasoningTask.DialecticalReasoningTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object DialecticalReasoningTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = DialecticalReasoningTask.DialecticalReasoning,
            typeConfig = TaskTypeConfig(
                task_type = DialecticalReasoningTask.DialecticalReasoning.name
            ),
            executionConfig = DialecticalReasoningTaskExecutionConfigData(
                thesis = "Functional programming is the superior paradigm for building scalable and maintainable software systems.",
                antithesis = "Object-oriented programming is the superior paradigm for building scalable and maintainable software systems.",
                context = "Modern enterprise software development and team productivity.",
                synthesis_levels = 2,
                preserve_strengths = true
            ),
            timeoutMinutes = 10,
        ).run()
    }
}