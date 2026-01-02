package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.ScriptwritingTask.ScriptwritingTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object ScriptwritingTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

     @Test
    @Timeout(15, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = ScriptwritingTask.Scriptwriting,
            typeConfig = TaskTypeConfig(
                task_type = ScriptwritingTask.Scriptwriting.name
            ),
            executionConfig = ScriptwritingTaskExecutionConfigData(
                topic = "Introduction to Kotlin Coroutines for Java Developers",
                script_type = "educational video",
                target_duration_minutes = 3,
                target_audience = "Experienced Java Developers",
                tone = "professional and technical",
                pacing = "moderate",
                include_directions = true,
                suggest_b_roll = true,
                revision_passes = 1
            ),
            timeoutMinutes = 15,
        ).run()
    }
}