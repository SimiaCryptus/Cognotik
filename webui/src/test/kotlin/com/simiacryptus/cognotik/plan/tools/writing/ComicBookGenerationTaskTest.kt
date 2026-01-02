package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object ComicBookGenerationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    @Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = ComicBookGenerationTask.ComicBookGeneration,
            typeConfig = TaskTypeConfig(
                task_type = ComicBookGenerationTask.ComicBookGeneration.name
            ),
            executionConfig = ComicBookGenerationTaskExecutionConfigData(
                subject = "A robot discovering a forgotten garden in a cyberpunk city",
                target_pages = 1,
                art_style = "cyberpunk noir",
                generate_images = false // Set to false for faster unit testing of the script logic
            ),
            timeoutMinutes = 10,
        ).run()
    }
}