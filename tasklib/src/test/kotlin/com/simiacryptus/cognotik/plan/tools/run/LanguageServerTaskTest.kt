package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.code.LanguageServerTask
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object LanguageServerTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun testHover() {
    val harness = TaskHarness(
      taskType = LanguageServerTask.LanguageServer,
      typeConfig = LanguageServerTask.LanguageServerTaskTypeConfig(
        task_type = LanguageServerTask.LanguageServer.name
      ),
      executionConfig = LanguageServerTask.LanguageServerTaskExecutionConfigData(
        action = "hover",
        file = "Sample.kt",
        line = 1,
        character = 10,
        task_description = "Get hover information for the println function call",
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    )

    // Create a sample file in the harness root for the LSP to analyze
    val sampleFile = harness.dataDir.resolve("Sample.kt")
    sampleFile.parentFile.mkdirs()
    sampleFile.writeText(
      """
            fun main() {
                println("Hello LSP")
            }
        """.trimIndent()
    )

    harness.run()
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun testDiagnostics() {
    val harness = TaskHarness(
      taskType = LanguageServerTask.LanguageServer,
      typeConfig = LanguageServerTask.LanguageServerTaskTypeConfig(
        task_type = LanguageServerTask.LanguageServer.name
      ),
      executionConfig = LanguageServerTask.LanguageServerTaskExecutionConfigData(
        action = "diagnostics",
        file = "Error.kt",
        task_description = "Check for syntax errors in Error.kt",
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    )

    // Create a file with a syntax error
    val errorFile = harness.dataDir.resolve("Error.kt")
    errorFile.parentFile.mkdirs()
    errorFile.writeText(
      """
            fun main() {
                val x: Int = "not an int"
            }
        """.trimIndent()
    )

    harness.run()
  }
}