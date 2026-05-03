package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.WriteHtmlTask.WriteHtmlTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object WriteHtmlTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = WriteHtmlTask.WriteHtml,
      typeConfig = TaskTypeConfig(
        task_type = WriteHtmlTask.WriteHtml.name
      ),
      executionConfig = WriteHtmlTaskExecutionConfigData(
        task_description = "Create a simple landing page for a coffee shop with a menu and contact section.",
        generate_images = false
      ).apply {
        main_file = "index.html"
      },
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun testWithImages() {
    TaskHarness(
      taskType = WriteHtmlTask.WriteHtml,
      typeConfig = TaskTypeConfig(
        task_type = WriteHtmlTask.WriteHtml.name
      ),
      executionConfig = WriteHtmlTaskExecutionConfigData(
        task_description = "Create a photo gallery page for a travel blog with at least one image placeholder.",
        generate_images = true,
        image_count = 1
      ).apply {
        main_file = "gallery.html"
      },
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}