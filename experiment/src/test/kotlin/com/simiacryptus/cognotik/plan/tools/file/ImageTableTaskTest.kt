package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.images.ImageTableTask
import com.simiacryptus.cognotik.plan.tools.images.ImageTableTask.ImageTableTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.images.ImageTableTask.ImageTableTaskTypeConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object ImageTableTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = ImageTableTask.ImageTable,
      typeConfig = ImageTableTaskTypeConfig(
        parallel_generation = 2,
        image_width = 512,
        image_height = 512
      ),
      executionConfig = ImageTableTaskExecutionConfigData(
        rows = listOf("Cyberpunk City", "Enchanted Forest"),
        columns = listOf("Daylight", "Nighttime"),
        image_prompt_template = "A wide shot of a {row} during {column}",
        base_style = "Cinematic lighting, 8k resolution, highly detailed",
        output_directory = "test_output/image_table_test",
        image_format = "png",
        task_description = "Generate a comparison grid of environments under different lighting conditions"
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    ).run()
  }
}