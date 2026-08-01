package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.images.GenerateQRImageTask
import com.simiacryptus.cognotik.plan.tools.images.GenerateQRImageTask.GenerateQRImageTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

@Suppress("unused")
object GenerateQRImageTaskTest {

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
      taskType = GenerateQRImageTask.GenerateQRImage,
      typeConfig = TaskTypeConfig(
        task_type = GenerateQRImageTask.GenerateQRImage.name
      ),
      executionConfig = GenerateQRImageTaskExecutionConfigData(
        qr_content = "https://github.com/SimiaCryptus",
        style_directive = "A lush jungle theme with exotic flowers and vines weaving through the QR pattern, vibrant greens and floral colors",
        qr_size = 512,
        max_retries = 3,
        task_description = "Generate an artistic QR code for the project repository"
      ).apply {
        main_file = "artistic_qr.png"
      },
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}