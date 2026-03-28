package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.ImageGenerationTask.GenerateImageTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object GenerateImageTaskTest {

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
      taskType = ImageGenerationTask.GenerateImage,
      typeConfig = TaskTypeConfig(
        task_type = ImageGenerationTask.GenerateImage.name
      ),
      executionConfig = GenerateImageTaskExecutionConfigData(
        files = listOf("test_output_image.png"),
        task_description = "A high-quality digital art piece of a serene mountain landscape at sunset, with a clear lake in the foreground reflecting the orange and purple sky, cinematic lighting, 8k resolution. The image should be photorealistic.",
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    ).run()
  }
}