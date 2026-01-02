package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object GeneratePresentationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = GeneratePresentationTask.GeneratePresentation,
            typeConfig = TaskTypeConfig(
                task_type = GeneratePresentationTask.GeneratePresentation.name
            ),
            executionConfig = GeneratePresentationTaskExecutionConfigData(
                files = listOf("kotlin_presentation.html"),
                task_description = """
                    Create a professional presentation about the benefits of Kotlin for backend development.
                    Key points to cover:
                    1. Null Safety and Type System
                    2. Coroutines for Concurrency
                    3. Java Interoperability
                    4. Modern Syntax and Productivity
                    Target audience: Java developers considering switching to Kotlin.
                """.trimIndent(),
                generate_images = false, // Set to false for faster/more reliable CI testing
            ),
            timeoutMinutes = 10,
        ).run()
    }

    //@Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun testWithImages() {
        TaskTestHarness(
            taskType = GeneratePresentationTask.GeneratePresentation,
            typeConfig = TaskTypeConfig(
                task_type = GeneratePresentationTask.GeneratePresentation.name
            ),
            executionConfig = GeneratePresentationTaskExecutionConfigData(
                files = listOf("ai_future_presentation.html"),
                task_description = "A futuristic presentation about the impact of AI on software engineering.",
                generate_images = true,
                max_images = 2
            ),
            timeoutMinutes = 15,
        ).run()
    }
}