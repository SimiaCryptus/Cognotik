package com.simiacryptus.cognotik.plan.tools.code

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.code.LanguageServerTask.LanguageServerTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.code.LanguageServerTask.LanguageServerTaskTypeConfig
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object LanguageServerTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun testHover() {
        val harness = TaskHarness(
            taskType = LanguageServerTask.LanguageServer,
            typeConfig = LanguageServerTaskTypeConfig(
                task_type = LanguageServerTask.LanguageServer.name
            ),
            executionConfig = LanguageServerTaskExecutionConfigData(
                action = "hover",
                file = "Sample.kt",
                line = 1,
                character = 10,
                task_description = "Get hover information for the println function call",
            ),
            timeoutMinutes = 10,
        )

        // Create a sample file in the harness root for the LSP to analyze
        val sampleFile = harness.dataDir.resolve("Sample.kt")
        sampleFile.parentFile.mkdirs()
        sampleFile.writeText("""
            fun main() {
                println("Hello LSP")
            }
        """.trimIndent())

        harness.run()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun testDiagnostics() {
        val harness = TaskHarness(
            taskType = LanguageServerTask.LanguageServer,
            typeConfig = LanguageServerTaskTypeConfig(
                task_type = LanguageServerTask.LanguageServer.name
            ),
            executionConfig = LanguageServerTaskExecutionConfigData(
                action = "diagnostics",
                file = "Error.kt",
                task_description = "Check for syntax errors in Error.kt",
            ),
            timeoutMinutes = 10,
        )

        // Create a file with a syntax error
        val errorFile = harness.dataDir.resolve("Error.kt")
        errorFile.parentFile.mkdirs()
        errorFile.writeText("""
            fun main() {
                val x: Int = "not an int"
            }
        """.trimIndent())

        harness.run()
    }
}