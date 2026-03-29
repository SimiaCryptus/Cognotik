package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.images.GenerateSpriteSheetTask
import com.simiacryptus.cognotik.plan.tools.images.GenerateSpriteSheetTask.GenerateSpriteSheetTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object GenerateSpriteSheetTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = GenerateSpriteSheetTask.GenerateSpriteSheet,
      typeConfig = TaskTypeConfig(
        task_type = GenerateSpriteSheetTask.GenerateSpriteSheet.name
      ),
      executionConfig = GenerateSpriteSheetTaskExecutionConfigData(
        files = listOf("test_sprites.png"),
        metadata_file = "test_sprites.json",
        task_description =
          //"A pixel art character walk cycle animation sheet. 6 frames of a knight walking side view. Uniform size and spacing.",
          "Various superhero cats; photorealistic style; transparent background; arranged in a grid format.",
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    ).run()
  }
}