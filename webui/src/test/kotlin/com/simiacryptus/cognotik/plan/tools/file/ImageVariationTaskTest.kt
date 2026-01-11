package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.ImageVariationTask.ImageVariationConfig
import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.util.TaskHarness
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

class ImageVariationTaskTest {

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
    val inputFile = "input.png"
    val harness = TaskHarness(
      taskType = ImageVariationTask.ImageVariation,
      executionConfig = ImageVariationConfig(
        input_file = inputFile,
      ),
      temperature = 0.7,
      timeoutMinutes = 30,
      typeConfig = TaskTypeConfig(ImageVariationTask.ImageVariation.name),
    )
    File("/home/andrew/code/Cognotik/webui/workspaces/Waterfall/test-20260111_135611/base_scene.png")
      .copyTo(harness.dataDir.resolve(inputFile), overwrite = true)
    harness.run()

  }
}