package com.simiacryptus.cognotik.plan.tools.data

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.SearchTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object FileSearchTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  @Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = FileSearchTask.FileSearch,
      typeConfig = TaskTypeConfig(
        task_type = FileSearchTask.FileSearch.name
      ),
      executionConfig = SearchTaskExecutionConfigData(
        search_pattern = "class",
        is_regex = false,
        context_lines = 3,
        input_files = listOf("*.kt"),
        task_description = "Search for class definitions in Kotlin files",
      ),
      timeoutMinutes = 10,
      user = ApplicationServicesConfig.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }

  @Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun testRegex() {
    TaskHarness(
      taskType = FileSearchTask.FileSearch,
      typeConfig = TaskTypeConfig(
        task_type = FileSearchTask.FileSearch.name
      ),
      executionConfig = SearchTaskExecutionConfigData(
        search_pattern = "fun\\s+\\w+\\(",
        is_regex = true,
        context_lines = 1,
        input_files = listOf("src/**/*.kt"),
        task_description = "Search for function signatures using regex",
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