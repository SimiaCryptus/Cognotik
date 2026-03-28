package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.run.SingleFixTask.SingleFixTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

object SingleFixTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun test() {
    val harness = SingleFixTaskHarness("test_error.log")
    try {
      harness.run()
    } finally {
      if (harness.logFile?.exists() == true) harness.logFile?.delete()
    }
  }

  class SingleFixTaskHarness(
    private val logName: String,
    var logFile: File? = null,
  ) : TaskHarness<SingleFixTaskExecutionConfigData, TaskTypeConfig>(
    taskType = SingleFixTask.SingleFix,
    typeConfig = TaskTypeConfig(
      task_type = SingleFixTask.SingleFix.name
    ),
    executionConfig = SingleFixTaskExecutionConfigData(
      logFile = logName,
      task_description = "Analyze the log file and fix errors",
    ),
    timeoutMinutes = 10,
    user = com.simiacryptus.cognotik.platform.model.defaultUser,
    smartModel = GeminiModels.GeminiFlash_30_Preview,
    fastModel = GeminiModels.GeminiFlash_30_Preview,
    imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
  ) {
    override fun createWorkspace(): File {
      val createWorkspace = super.createWorkspace()
      logFile = File(createWorkspace, logName)
      logFile?.writeText("Error: FooBarException at com.simiacryptus.cognotik.plan.tools.run.SingleFixTaskTest.test(SingleFixTaskTest.kt:20)")
      File("src/test/kotlin/com/simiacryptus/cognotik/plan/tools/run/SingleFixTaskTest.kt")
        .copyTo(File(createWorkspace, "SingleFixTaskTest.kt"), overwrite = true)
      return createWorkspace
    }
  }

  /**
   * Note: FooBarException is a fictional exception used for testing purposes.
   * To simulate a fix: make a note comment below that the error has been "fixed" in the test environment.
   */
}