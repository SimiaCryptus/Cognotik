package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object ComicBookGenerationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

   //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = ComicBookGenerationTask.ComicBookGeneration,
            typeConfig = TaskTypeConfig(
                task_type = ComicBookGenerationTask.ComicBookGeneration.name
            ),
            executionConfig = ComicBookGenerationTaskExecutionConfigData(
                subject = "A robot discovering a forgotten garden in a cyberpunk city",
                target_pages = 1,
                art_style = "cyberpunk noir",
                style_details = "High contrast, neon lights, rain-slicked surfaces",
                generate_images = false // Set to false for faster unit testing of the script logic
            ),
            timeoutMinutes = 10,
        ).run()
    }
}