package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.WriteHtmlTask.WriteHtmlTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object WriteHtmlTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

     @org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = WriteHtmlTask.WriteHtml,
            typeConfig = TaskTypeConfig(
                task_type = WriteHtmlTask.WriteHtml.name
            ),
            executionConfig = WriteHtmlTaskExecutionConfigData(
                files = listOf("index.html"),
                task_description = "Create a simple landing page for a coffee shop with a menu and contact section.",
                generate_images = false
            ),
            timeoutMinutes = 10,
        ).run()
    }

     @org.junit.jupiter.api.Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun testWithImages() {
        TaskHarness(
            taskType = WriteHtmlTask.WriteHtml,
            typeConfig = TaskTypeConfig(
                task_type = WriteHtmlTask.WriteHtml.name
            ),
            executionConfig = WriteHtmlTaskExecutionConfigData(
                files = listOf("gallery.html"),
                task_description = "Create a photo gallery page for a travel blog with at least one image placeholder.",
                generate_images = true,
                image_count = 1
            ),
            timeoutMinutes = 15,
        ).run()
    }
}