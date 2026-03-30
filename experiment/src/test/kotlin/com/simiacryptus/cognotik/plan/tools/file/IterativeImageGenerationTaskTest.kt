package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.images.TiledImageGenerationTask
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class IterativeImageGenerationTaskTest {

  companion object {
    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
    }
  }

  //@org.junit.jupiter.api.Test
  @Timeout(30, unit = TimeUnit.MINUTES)
  fun test() {
    val output_file = "image.png"
    val harness = TaskHarness(
      taskType = TiledImageGenerationTask.TiledImageGeneration,
      executionConfig = TiledImageGenerationTask.TiledImageGenerationConfig(
        prompts = listOf(
          "Generate a detailed cartoon of woodland critters.",
          "Upscale and re-render as a photograph.",
          "Re-render as a circuit diagram.",
        ),
        upscale_factor = 4.0,
        min_region_size = 50,
      ).apply {
        this.main_file = output_file
      },
      temperature = 0.7,
      timeoutMinutes = 30,
      typeConfig = TaskTypeConfig(TiledImageGenerationTask.TiledImageGeneration.name),
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    )

    harness.run()

    val jsonFile = harness.dataDir.resolve(output_file)
    Assertions.assertTrue(jsonFile.exists(), "Output JSON file should exist")
    Assertions.assertTrue(jsonFile.length() > 0, "Output JSON file should not be empty")
  }
}