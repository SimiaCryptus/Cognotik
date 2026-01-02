package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.TechnicalExplanationTask.TechnicalExplanationTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object TechnicalExplanationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    @Test
    @Timeout(15, unit = TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = TechnicalExplanationTask.TechnicalExplanation,
            typeConfig = TaskTypeConfig(
                task_type = TechnicalExplanationTask.TechnicalExplanation.name
            ),
            executionConfig = TechnicalExplanationTaskExecutionConfigData(
                topic = "Kotlin Coroutines and Structured Concurrency",
                target_audience = "intermediate",
                level_of_detail = "moderate_detail",
                include_code_examples = true,
                explanation_format = "markdown",
                use_analogies = true,
                define_terminology = true,
                include_examples = true,
                code_language = "kotlin",
                revision_passes = 1
            ),
            timeoutMinutes = 15,
        ).run()
    }
}