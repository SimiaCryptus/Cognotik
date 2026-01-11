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
import java.io.File
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class IterativeImageDecompositionTaskTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            PlanHarness.Companion.configurePlatform()
        }
    }

    @Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        val outputJson = "analysis_result.json"

        val harness = TaskHarness(
          taskType = ImageDecompositionTask.ImageDecomposition,
          executionConfig = ImageDecompositionTask.ImageDecompositionConfig(
            files = listOf(
              "test_image.png"
//                    "test.pdf"
            ),
            segmentation_query = "Decompose, analyze, and translate into english.",
            max_depth = 2,
            min_region_size = 50,
            output_file = outputJson
          ),
          timeoutMinutes = 10,
          typeConfig = TaskTypeConfig(ImageDecompositionTask.ImageDecomposition.name),
        )

        // Create a test image
        //writeTestImage(harness, imageName)
        File("/home/andrew/code/Cognotik/webui/workspaces/SegmentedImageGeneration/test-20260111_110850/base_generation.png").copyTo(harness.dataDir.resolve("test_image.png"), overwrite = true)
//        File("/home/andrew/Downloads/US486986.pdf").copyTo(harness.dataDir.resolve("test.pdf"), overwrite = true)

        harness.run()

        val jsonFile = harness.dataDir.resolve(outputJson)
      Assertions.assertTrue(jsonFile.exists(), "Output JSON file should exist")
      Assertions.assertTrue(jsonFile.length() > 0, "Output JSON file should not be empty")
    }

  private fun writeTestImage(
    harness: TaskHarness<ImageDecompositionTask.ImageDecompositionConfig, TaskTypeConfig>,
    imageName: String
    ) {
        val imagePath = harness.dataDir.resolve(imageName)
        val width = 800
        val height = 600
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()

        // Background
        g.color = Color.WHITE
        g.fillRect(0, 0, width, height)

        // Some regions
        g.color = Color.RED
        g.fillRect(50, 50, 200, 200)
        g.color = Color.BLACK
        g.font = Font("SansSerif", Font.BOLD, 20)
        g.drawString("Region A", 100, 150)

        g.color = Color.BLUE
        g.fillRect(400, 300, 200, 200)
        g.color = Color.WHITE
        g.drawString("Region B", 450, 400)

        // Small detail for recursion
        g.color = Color.GREEN
        g.fillRect(600, 50, 100, 100)
        g.color = Color.BLACK
        g.font = Font("SansSerif", Font.PLAIN, 10)
        g.drawString("Tiny text", 610, 100)

        g.dispose()
        ImageIO.write(image, "png", imagePath)
    }
}