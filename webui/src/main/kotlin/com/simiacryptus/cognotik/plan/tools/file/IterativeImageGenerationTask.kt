package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class IterativeImageGenerationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: IterativeImageGenerationConfig?
) : AbstractFileTask<IterativeImageGenerationTask.IterativeImageGenerationConfig>(
  orchestrationConfig,
  planTask
) {

  class IterativeImageGenerationConfig(
    @Description("The output file path for the final high-res image (e.g., 'poster.png')")
    val output_file: String? = "generated_image.png",
    @Description("The main prompt describing the image to generate")
    val prompt: String? = null,
    @Description("Optional input file path to use as the base image instead of generating one.")
    val input_file: String? = null,
    @Description("Maximum recursion depth (1-3). Warning: High depth creates massive images.")
    val max_depth: Int = 2,
    @Description("Upscale factor per level (e.g., 2.0 for 2x size, 4.0 for 4x size)")
    val upscale_factor: Double = 2.0,
    @Description("Minimum width/height (in pixels) of a region to trigger refinement.")
    val min_region_size: Int = 128,
    @Description("Maximum aspect ratio for regions (e.g., 3.0 means max 3:1 or 1:3).")
    val max_aspect_ratio: Double = 3.0,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = IterativeImageGeneration.name,
    task_description = prompt,
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      if (output_file.isNullOrBlank()) return "Output file must be specified"
      if (prompt.isNullOrBlank()) return "Prompt must be specified"
      if (max_depth < 1) return "Max depth must be at least 1"
      return ValidatedObject.validateFields(this)
    }
  }

  // Reuse the region definition from Decomposition, but simplified for generation
  data class GenerationRegion(
    @Description("A descriptive label for this region")
    val label: String = "",
    @Description("Detailed visual description of what should be in this region")
    val visual_description: String = "",
    @Description("X coordinate (0-1000 scale)")
    val x: Int = 0,
    @Description("Y coordinate (0-1000 scale)")
    val y: Int = 0,
    @Description("Width (0-1000 scale)")
    val width: Int = 0,
    @Description("Height (0-1000 scale)")
    val height: Int = 0
  )

  data class RegionPlan(
    @Description("List of regions that require higher resolution detail")
    val regions: List<GenerationRegion> = emptyList()
  )

  override fun promptSegment() = """
IterativeImageGeneration - Generates ultra-high-resolution images via recursive upscaling
* Use for: Creating posters, maps, or detailed scenes where standard generation lacks resolution.
* Mechanism: Generates a base image, identifies regions, upscales them, and uses AI to refine details recursively.
      """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val outputFile = executionConfig?.output_file ?: "output.png"
    val prompt = executionConfig?.prompt ?: return resultFn("No prompt specified")
    val inputFile = executionConfig?.input_file
    val maxDepth = executionConfig.max_depth
    val upscaleFactor = executionConfig.upscale_factor
    val minSize = executionConfig.min_region_size
    val maxAspectRatio = executionConfig.max_aspect_ratio

    val readModel = (typeConfig?.model?.let { orchestrationConfig.instance(it) } ?: defaultSmart).getChildClient(task)
    val writeModel = orchestrationConfig.defaultImage.getChildClient(task)

    val transcript = task.transcript()
    val tabs = TabbedDisplay(task)
    val logTab = tabs.newTask("Progress")

    task.ui.pool.submit {
      try {
        task.header("Starting Iterative Generation: $outputFile", level = 2)
        val configInfo = buildString {
          appendLine("**Configuration**")
          appendLine("* **Output File:** $outputFile")
          if (inputFile != null) appendLine("* **Input File:** $inputFile")
          appendLine("* **Prompt:** $prompt")
          appendLine("* **Max Depth:** $maxDepth")
          appendLine("* **Upscale Factor:** $upscaleFactor")
          appendLine("* **Min Region Size:** $minSize")
        }
        logTab.add(configInfo.renderMarkdown())
        transcript?.write("# Iterative Image Generation\n\n$configInfo\n\n".toByteArray())


        val imageAgent = ImageProcessingAgent(
          prompt = "Generate an image based on the user description",
          name = "BaseGenerator",
          model = writeModel
        )
        // 1. Generate Root Image


        var currentImage: BufferedImage
        if (inputFile != null) {
          logTab.add("Loading base image from $inputFile...".renderMarkdown())
          val file = root.resolve(inputFile).toFile()
          if (!file.exists()) throw RuntimeException("Input file not found: $inputFile")
          currentImage = ImageIO.read(file) ?: throw RuntimeException("Failed to read input image")
          val baseLink = task.linkTo(inputFile)
          tabs["Base Image"] = """<img src="$baseLink" style="max-width: 100%;" />"""
          transcript?.write("## Base Image\nLoaded from: $inputFile\n\n![Base Image]($baseLink)\n\n".toByteArray())
        } else {
          logTab.add("Generating base image...".renderMarkdown())

          val baseResult = imageAgent.answer(listOf(ImageAndText(prompt)))
          currentImage = baseResult.image ?: throw RuntimeException("Failed to generate base image")

          val baseLink = saveImage(currentImage, "base_generation.png", task)
          tabs["Base Image"] = """<img src="$baseLink" style="max-width: 100%;" />"""
          transcript?.write("## Base Generation\nPrompt: $prompt\n\n![Base Image]($baseLink)\n\n".toByteArray())
          logTab.add("Base image generated (${currentImage.width}x${currentImage.height})".renderMarkdown())
        }

        // 2. Recursive Refinement
        fun processLevel(
          sourceImage: BufferedImage,
          currentDepth: Int,
          parentDescription: String,
          path: String
        ): BufferedImage {
          if (currentDepth >= maxDepth) {
            logTab.add("Max depth $maxDepth reached. Returning image.".renderMarkdown())
            return sourceImage
          }
          // Calculate target dimensions for this level's return (Max Depth resolution)
          val levelsRemaining = maxDepth - currentDepth
          val totalScale = Math.pow(upscaleFactor, levelsRemaining.toDouble())
          val fullWidth = (sourceImage.width * totalScale).toInt()
          val fullHeight = (sourceImage.height * totalScale).toInt()
          val fullResCanvas = BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_ARGB)
          val gFull = fullResCanvas.createGraphics()
          gFull.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          gFull.drawImage(sourceImage, 0, 0, fullWidth, fullHeight, null)

          logTab.add("Analyzing depth $currentDepth for refinement regions...".renderMarkdown())

          // Analyze for regions
          val analysisAgent = ParsedImageAgent(
            resultClass = RegionPlan::class.java,
            model = readModel,
            prompt = """
                            Analyze this image to identify regions that contain dense details or specific objects
                            that would benefit from higher resolution (e.g., faces, text, intricate patterns, crowds).
                            The goal is to upscale these regions.
                            Output coordinates on a 0-1000 scale.
                            Ensure regions have an aspect ratio between 1/$maxAspectRatio and $maxAspectRatio.
                            Current Image Prompt: "$parentDescription"
                        """.trimIndent()
          )

          val plan = analysisAgent.answer(listOf(ImageAndText(text = "", image = sourceImage))).obj

          if (plan.regions.isEmpty()) {
            logTab.add("No regions found at depth $currentDepth, stopping recursion.".renderMarkdown())
            gFull.dispose()
            return fullResCanvas
          }



          transcript?.write(buildString {
            appendLine("### Depth $currentDepth Analysis")
            appendLine("Found ${plan.regions.size} regions to refine.")
            appendLine("<details><summary>Regions JSON</summary>")
            appendLine()
            appendLine("```json")
            appendLine(plan.toJson())
            appendLine("```")
            appendLine("</details>")
            appendLine()
            appendLine("| Label | Description | Coords (x,y,w,h) |")
            appendLine("|---|---|---|")
            plan.regions.forEach {
              appendLine("| ${it.label} | ${it.visual_description} | ${it.x}, ${it.y}, ${it.width}, ${it.height} |")
            }
            appendLine()
          }.toByteArray())
          // Generate segmentation debug image
          val debugImage = BufferedImage(sourceImage.width, sourceImage.height, BufferedImage.TYPE_INT_ARGB)
          val gDebug = debugImage.createGraphics()
          gDebug.drawImage(sourceImage, 0, 0, null)
          gDebug.stroke = BasicStroke(2f)
          plan.regions.forEach { region ->
            val rx = (region.x * sourceImage.width / 1000.0).toInt()
            val ry = (region.y * sourceImage.height / 1000.0).toInt()
            val rw = (region.width * sourceImage.width / 1000.0).toInt()
            val rh = (region.height * sourceImage.height / 1000.0).toInt()
            gDebug.color = Color.RED
            gDebug.drawRect(rx, ry, rw, rh)
            if (rw > 40 && rh > 20) {
              gDebug.color = Color(0, 0, 0, 150)
              gDebug.fillRect(rx, ry, rw.coerceAtMost(200), 20)
              gDebug.color = Color.WHITE
              gDebug.drawString(region.label, rx + 5, ry + 15)
            }
          }
          gDebug.dispose()
          val debugName = "${path}_segmentation.png"
          val debugLink = saveImage(debugImage, debugName, task)
          tabs["Depth $currentDepth Map"] = """<img src="$debugLink" style="max-width: 100%;" />"""
          logTab.add("Saved segmentation map for depth $currentDepth".renderMarkdown())
          transcript?.write("\n![Segmentation Map]($debugLink)\n\n".toByteArray())


          // Create intermediate image for next level processing (1 step up)
          val nextLevelWidth = (sourceImage.width * upscaleFactor).toInt()
          val nextLevelHeight = (sourceImage.height * upscaleFactor).toInt()
          val nextLevelImage = BufferedImage(nextLevelWidth, nextLevelHeight, BufferedImage.TYPE_INT_ARGB)
          val gNext = nextLevelImage.createGraphics()
          gNext.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
          gNext.drawImage(sourceImage, 0, 0, nextLevelWidth, nextLevelHeight, null)
          gNext.dispose()

          // Process each region
          plan.regions.forEachIndexed { idx, region ->

            // Convert 0-1000 coordinates to the next level dimensions
            var regionX = (region.x * nextLevelWidth / 1000.0).toInt()
            var regionY = (region.y * nextLevelHeight / 1000.0).toInt()
            var regionW = (region.width * nextLevelWidth / 1000.0).toInt()
            var regionH = (region.height * nextLevelHeight / 1000.0).toInt()

            // Enforce aspect ratio
            val currentRatio = regionW.toDouble() / regionH
            if (currentRatio > maxAspectRatio) {
              val newH = (regionW / maxAspectRatio).toInt()
              regionY -= (newH - regionH) / 2
              regionH = newH
            } else if (currentRatio < 1.0 / maxAspectRatio) {
              val newW = (regionH / maxAspectRatio).toInt()
              regionX -= (newW - regionW) / 2
              regionW = newW
            }

            // Fit to bounds
            if (regionX < 0) regionX = 0
            if (regionY < 0) regionY = 0
            if (regionX + regionW > nextLevelWidth) regionX = nextLevelWidth - regionW
            if (regionY + regionH > nextLevelHeight) regionY = nextLevelHeight - regionH
            if (regionX < 0) regionX = 0
            if (regionY < 0) regionY = 0
            val startX = regionX
            val startY = regionY
            val actualW = (regionW).coerceAtMost(nextLevelWidth - startX)
            val actualH = (regionH).coerceAtMost(nextLevelHeight - startY)

            if (actualW < minSize || actualH < minSize) {
              logTab.add("Skipping region '${region.label}' (too small: ${actualW}x${actualH} < $minSize)".renderMarkdown())
              return@forEachIndexed
            }

            logTab.add("Refining region: **${region.label}** at depth ${currentDepth + 1}".renderMarkdown())

            // Crop from the ALREADY UPSCALED canvas (which currently holds the blurry bicubic version)
            val crop = nextLevelImage.getSubimage(startX, startY, actualW, actualH)

            // Refine using Img2Img
            // We use a prompt that combines the specific detail with the global context
            val refinePrompt = """
                Refine this image segment for super-resolution.
                Keep the content, composition, and structure largely the same as the input.
                Enhance fine details, textures, and sharpness.
                Subject: ${region.label}.
                Description: ${region.visual_description}.
                Context: $parentDescription.
            """.trimIndent()

            try {
              val refinedResult = imageAgent.answer(
                listOf(
                  ImageAndText(
                    text = refinePrompt,
                    image = crop // Pass the blurry crop as the init image
                  )
                )
              )

              val refinedCrop = refinedResult.image
              if (refinedCrop != null) {
                val regionSlug = region.label.replace(Regex("[^a-zA-Z0-9]"), "_").take(20)
                val nextPath = "${path}_${idx}_$regionSlug"

                // Recurse to get max resolution version of this region
                val highResRegion = processLevel(refinedCrop, currentDepth + 1, refinePrompt, nextPath)

                // Align refinedCrop to crop to find valid bounds
                val bounds = ImagePatchLocalization.findBounds(
                  refinedCrop,
                  crop,
                  ImagePatchLocalization.SubImageBounds(0, 0, refinedCrop.width, refinedCrop.height, 0.0)
                )

                // Scale bounds to max resolution
                val regionScale = Math.pow(upscaleFactor, (maxDepth - (currentDepth + 1)).toDouble())
                val srcX = (bounds.x * regionScale).toInt().coerceIn(0, highResRegion.width)
                val srcY = (bounds.y * regionScale).toInt().coerceIn(0, highResRegion.height)
                val srcW = (bounds.width * regionScale).toInt().coerceAtMost(highResRegion.width - srcX)
                val srcH = (bounds.height * regionScale).toInt().coerceAtMost(highResRegion.height - srcY)
                val sourceRegion = highResRegion.getSubimage(srcX, srcY, srcW, srcH)

                // Paste into fullResCanvas
                val destX = (startX * regionScale).toInt()
                val destY = (startY * regionScale).toInt()
                val destW = (actualW * regionScale).toInt()
                val destH = (actualH * regionScale).toInt()
                gFull.drawImage(sourceRegion, destX, destY, destW, destH, null)

                // Save debug snippet
                val snippetName = "${nextPath}_refined.png"
                val snippetLink = saveImage(highResRegion, snippetName, task)
                transcript?.write(buildString {
                  appendLine("#### Refined Region: ${region.label}")
                  appendLine("*Prompt:* $refinePrompt")
                  appendLine("![Refined Region]($snippetLink)")
                  appendLine()
                }.toByteArray())
              }
            } catch (e: Exception) {
              log.error("Failed to refine region ${region.label}", e)
              logTab.add("Failed to refine ${region.label}: ${e.message}".renderMarkdown())
              transcript?.write("\n**Error refining ${region.label}:** ${e.message}\n".toByteArray())
            }
          }
          gFull.dispose()
          return fullResCanvas
        }

        val finalImage = processLevel(currentImage, 0, prompt, "root")

        // Save Final
        val finalPath = root.resolve(outputFile)
        ImageIO.write(finalImage, "png", finalPath.toFile())
        val finalLink = task.linkTo(outputFile)

        tabs["Final Result"] = buildString {
          appendLine("<p>Saved to <a href=\"$finalLink\">$outputFile</a> (${finalImage.width}x${finalImage.height})</p>")
          appendLine("<a href=\"$finalLink\" target=\"_blank\"><img src=\"$finalLink\" style=\"max-width: 100%; border: 1px solid #ccc;\" /></a>")
        }
        transcript?.write(buildString {
          appendLine("## Final Result")
          appendLine("**File:** $outputFile")
          appendLine("**Dimensions:** ${finalImage.width}x${finalImage.height}")
          appendLine("![Final Image]($finalLink)")
        }.toByteArray())


        task.safeComplete("Generated high-res image: ${finalImage.width}x${finalImage.height}", log)
        resultFn("Generated ultra-high-resolution image saved to $outputFile. Final dimensions: ${finalImage.width}x${finalImage.height}.")

      } catch (e: Exception) {
        log.error("Error in IterativeImageGeneration", e)
        task.error(e)
        transcript?.write("\n## Error\n${e.message}\n".toByteArray())
        resultFn("Error generating image: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }

  private fun saveImage(image: BufferedImage, name: String, task: SessionTask): String {
    val file = root.resolve(name)
    ImageIO.write(image, "png", file.toFile())
    return task.linkTo(name)
  }

  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    return ui.hrefLink("Accept Image") { fn() }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(IterativeImageGenerationTask::class.java)
    val IterativeImageGeneration = TaskType(
      "IterativeImageGeneration",
      "Writing",
      IterativeImageGenerationTask::class.java,
      IterativeImageGenerationConfig::class.java,
      TaskTypeConfig::class.java,
      "Recursively generates and upscales images for high detail.",
      """
Generates a base image, identifies regions of interest, and recursively upscales and refines them using generative AI.
Useful for:
<ul>
    <li>Large format posters</li>
    <li>Detailed maps or "Where's Waldo" style scenes</li>
    <li>Images requiring text or small details legible at high zoom</li>
</ul>
            """,
    )
  }
}