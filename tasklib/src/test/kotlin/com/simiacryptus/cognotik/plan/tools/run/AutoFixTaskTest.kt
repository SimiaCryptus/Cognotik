package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.autofix.AutoFixTask
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.autofix.AutoFixTask.AutoFixTaskExecutionConfigData
import com.simiacryptus.cognotik.autofix.AutoFixTask.CommandWithWorkingDir
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

@Suppress("unused")
object AutoFixTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = AutoFixTask.AutoFix,
      typeConfig = TaskTypeConfig(
        task_type = AutoFixTask.AutoFix.name
      ),
      executionConfig = AutoFixTaskExecutionConfigData(
        commands = mutableListOf(
          CommandWithWorkingDir(
            executable = "echo",
            arguments = mutableListOf("Hello, World!"),
            working_dir = "."
          )
        ),
        task_description = "Check the status of the git repository",
      ),
      timeoutMinutes = 10,
      user = ApplicationServicesConfig.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}