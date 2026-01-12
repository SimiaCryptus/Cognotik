package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.plan.TaskTypeConfig
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
      UnifiedHarness.configurePlatform()
    }
  }

 //@org.junit.jupiter.api.Test
  @Timeout(30, unit = TimeUnit.MINUTES)
  fun test() {
    val output_file = "image.png"
    val harness = TaskHarness(
      taskType = TiledImageGenerationTask.TiledImageGeneration,
      executionConfig = TiledImageGenerationTask.TiledImageGenerationConfig(
        output_file = output_file,
        prompts = listOf(
          "Generate a detailed cartoon of woodland critters.",
          "Upscale and re-render as a photograph.",
          "Re-render as a circuit diagram.",
        ),
        upscale_factor = 4.0,
        min_region_size = 50,
      ),
      temperature = 0.7,
      timeoutMinutes = 30,
      typeConfig = TaskTypeConfig(TiledImageGenerationTask.TiledImageGeneration.name),
    )

    harness.run()

    val jsonFile = harness.dataDir.resolve(output_file)
    Assertions.assertTrue(jsonFile.exists(), "Output JSON file should exist")
    Assertions.assertTrue(jsonFile.length() > 0, "Output JSON file should not be empty")
  }
}