package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

@Suppress("unused")
object ComicBookGenerationTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = ComicBookGenerationTask.ComicBookGeneration,
      typeConfig = TaskTypeConfig(
        task_type = ComicBookGenerationTask.ComicBookGeneration.name
      ),
      executionConfig = ComicBookGenerationTaskExecutionConfigData(
        task_description = "A robot discovering a forgotten garden in a cyberpunk city",
        target_pages = 1,
        art_style = "cyberpunk noir",
        style_details = "High contrast, neon lights, rain-slicked surfaces",
        generate_images = false // Set to false for faster unit testing of the script logic
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