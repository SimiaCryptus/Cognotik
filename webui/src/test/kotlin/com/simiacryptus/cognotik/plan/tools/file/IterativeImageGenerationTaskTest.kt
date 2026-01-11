package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.util.TaskHarness
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class IterativeImageGenerationTaskTest {

  companion object {
    @JvmStatic
    @BeforeAll
    fun setup() {
      PlanHarness.Companion.configurePlatform()
    }
  }

  @Test
  @Timeout(30, unit = TimeUnit.MINUTES)
  fun test() {
    val output_file = "image.png"
    val harness = TaskHarness(
      taskType = IterativeImageGenerationTask.IterativeImageGeneration,
      executionConfig = IterativeImageGenerationTask.IterativeImageGenerationConfig(
        prompt = "Generate a 'wheres waldo' type image where we are looking for the robots hidden amongst the woodland critters.",
        max_depth = 2,
        min_region_size = 50,
        output_file = output_file
      ),
      timeoutMinutes = 30,
      typeConfig = TaskTypeConfig(IterativeImageGenerationTask.IterativeImageGeneration.name),
    )

    harness.run()

    val jsonFile = harness.dataDir.resolve(output_file)
    Assertions.assertTrue(jsonFile.exists(), "Output JSON file should exist")
    Assertions.assertTrue(jsonFile.length() > 0, "Output JSON file should not be empty")
  }
}