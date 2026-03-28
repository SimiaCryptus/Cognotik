package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class ImageVariationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: ImageVariationConfig?,
) : AbstractFileTask<ImageVariationTask.ImageVariationConfig>(
  orchestrationConfig,
  planTask
) {

  val describer: AbbrevWhitelistYamlDescriber = object : AbbrevWhitelistYamlDescriber(
    "com.simiacryptus", "cognotik.actions"
  ) {
    override val includeMethods: Boolean get() = false
  }

  class ImageVariationConfig(
    @Description("The input image file")
    override var files: List<String> = emptyList(),
    @Description("Number of distinct regions to modify")
    var num_subimages: Int = 7,
    @Description("Number of alternate versions per region - at least 2")
    var num_subimage_alternates: Int = 2,
    @Description("Number of changes to apply per variation")
    var num_changes_per_variation: Int = 5,
    @Description("Number of alternative images to produce (M)")
    var num_variations: Int = 11,
    @Description("Output filename prefix")
    var output_prefix: String = "variation",
    @Description("Whether to use image patch localization to align the generated variation with the original image")
    var retarget_subimages: Boolean = false,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = ImageVariation.name,
    task_description = "Generate ${num_variations} variations with ${num_subimages}x${num_subimage_alternates} potential changes from $files",
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      if (files.isEmpty()) return "Must specify an input image file"
      if (num_subimages < 1) return "Must identify at least 1 region"
      if (num_subimage_alternates <= 1) return "Must generate at more than 1 alternate per region"
      if (num_variations < 1) return "Must generate at least 1 variation"
      return ValidatedObject.validateFields(this)
    }
  }

  data class DetectedRegion(
    @Description("Label of the object or region")
    val label: String = "",
    @Description("X coordinate (0-1000)")
    val x: Int = 0,
    @Description("Y coordinate (0-1000)")
    val y: Int = 0,
    @Description("Width (0-1000)")
    val width: Int = 0,
    @Description("Height (0-1000)")
    val height: Int = 0
  )

  data class RegionAnalysis(
    @Description("List of distinct objects or regions suitable for modification")
    val regions: List<DetectedRegion> = emptyList()
  )

  data class ChangeProposal(
    @Description("The specific visual change to apply (e.g., 'Change the red car to blue', 'Remove the hat')")
    val change_instruction: String = "",
    @Description("Description of the result")
    val result_description: String = ""
  )

  data class AppliedChange(
    val id: String,
    val region: DetectedRegion,
    val instruction: String,
    val description: String,
    val imagePath: String,
    @Transient val image: BufferedImage? = null
  )

  data class VariationManifest(
    val base_image: String,
    val changes_applied: List<ChangeDescription>
  )

  data class ChangeDescription(
    val region_label: String,
    val description: String,
    val bounds: List<Int>, // x, y, w, h
    val changeId: String? = null
  )

  override fun promptSegment() = """
ImageVariation - Creates 'Find the Differences' style image sets.
* Use for: Game asset generation, data augmentation.
* Mechanism: Decomposes image, generates N specific sub-image changes, and recombines them into M variations.
      """.trimIndent()

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newUserFileStream(transcriptFile())
    task.ui.pool.submit {
      try {
        val inputFile = primaryImageFile() ?: return@submit resultFn("No input file")
        val numSubimages = executionConfig!!.num_subimages
        val executionConfig = executionConfig ?: throw RuntimeException("Execution config not found")
        val numSubimageAlternates = executionConfig.num_subimage_alternates
        val numChangesPerVariation = executionConfig.num_changes_per_variation
        val numVariations = executionConfig.num_variations
        val prefix = executionConfig.output_prefix
        val analysisModel = orchestrationConfig.defaultSmart.getChildClient(task)
        val imageModel = orchestrationConfig.defaultImage.getChildClient(task)

        val tabs = TabbedDisplay(task)
        val logTab = tabs.newTask("Progress")

        log.info("Starting Image Variation Task for $inputFile")
        task.header("Starting Image Variation Task", level = 2)

        // 1. Load Base Image
        logTab.add("Loading base image: $inputFile".renderMarkdown())
        val baseFile = root.resolve(inputFile).toFile()
        if (!baseFile.exists()) throw RuntimeException("Input file not found: $inputFile")
        val baseImage = ImageIO.read(baseFile)
        val baseLink = task.linkTo(inputFile)
        tabs["Base Image"] = """<img src="$baseLink" style="max-width: 100%;" />"""

        // 2. Analyze Structure (Decomposition)
        logTab.add("Analyzing image structure...".renderMarkdown())
        val imageAgent = ParsedImageAgent(
          resultClass = RegionAnalysis::class.java,
          exampleInstance = RegionAnalysis(
            regions = listOf(
              DetectedRegion(label = "Red Car", x = 100, y = 200, width = 150, height = 80),
              DetectedRegion(label = "Tree", x = 400, y = 300, width = 120, height = 200),
              DetectedRegion(label = "House", x = 600, y = 250, width = 200, height = 150),
            )
          ),
          model = analysisModel,
          prompt = """
              Analyze this image to identify distinct objects or regions that could be modified for a "Find the Differences" game.
              Identify at least ${numSubimages + 2} distinct regions.
              Avoid overlapping regions if possible.
              Output coordinates on a 0-1000 scale.
            """.trimIndent(),
          describer = describer
        )
        val answer = imageAgent.answer(listOf(ImageAndText(text = "", image = baseImage)))
        val analysis = answer.obj

        transcript?.write(buildString {
          appendLine("## Structural Analysis")
          appendLine("Found ${analysis.regions.size} regions.")
          appendLine("<details><summary>Raw Analysis JSON</summary>\n\n```json")
          appendLine(analysis.toJson())
          appendLine("```\n</details>")
        }.toByteArray())

        // Draw debug map
        val debugImg = BufferedImage(baseImage.width, baseImage.height, BufferedImage.TYPE_INT_ARGB)
        val gDebug = debugImg.createGraphics()
        gDebug.drawImage(baseImage, 0, 0, null)
        gDebug.stroke = BasicStroke(3f)
        analysis.regions.forEach { r ->
          val x = (r.x * baseImage.width / 1000.0).toInt()
          val y = (r.y * baseImage.height / 1000.0).toInt()
          val w = (r.width * baseImage.width / 1000.0).toInt()
          val h = (r.height * baseImage.height / 1000.0).toInt()
          gDebug.color = Color.CYAN
          gDebug.drawRect(x, y, w, h)
          gDebug.color = Color.BLACK
          gDebug.drawString(r.label, x, y - 5)
          gDebug.color = Color.WHITE
          gDebug.drawString(r.label, x - 1, y - 6)
        }
        gDebug.dispose()
        val debugLink = saveImage(debugImg, "${prefix}_analysis.png", task)
        tabs["Analysis Map"] = """<img src="$debugLink" style="max-width: 100%;" />"""

        // 3. Generate N Changes
        val generatedChanges = mutableListOf<AppliedChange>()
        val regionsToProcess = analysis.regions.shuffled().take(numSubimages)

        regionsToProcess.forEachIndexed { regionIdx, region ->
          logTab.add("Processing region ${regionIdx + 1}/$numSubimages: **${region.label}**".renderMarkdown())

          // Crop
          val rx = (region.x * baseImage.width / 1000.0).toInt()
          val ry = (region.y * baseImage.height / 1000.0).toInt()
          val rw = (region.width * baseImage.width / 1000.0).toInt().coerceAtLeast(64)
          val rh = (region.height * baseImage.height / 1000.0).toInt().coerceAtLeast(64)

          // Safety check for bounds
          if (rx + rw > baseImage.width || ry + rh > baseImage.height) return@forEachIndexed

          val crop = baseImage.getSubimage(rx, ry, rw, rh)

          for (altIdx in 1..numSubimageAlternates) {
            // Plan Change
            val proposal = ParsedImageAgent(
              resultClass = ChangeProposal::class.java,
              model = analysisModel,
              prompt = """
                  Suggest a single, distinct visual change for this image region (Label: ${region.label}).
                  This is variation $altIdx of $numSubimageAlternates.
                  This is for a "Find the Differences" game.
                  Examples: Change color, remove object, change facial expression, rotate object.
                  The change should be visible but retain the same lighting and style.
                """.trimIndent()
            ).answer(listOf(ImageAndText(text = "", image = crop))).obj


            // Render Change
            val renderAgent = ImageProcessingAgent(
              prompt = "Apply the requested change",
              model = imageModel
            )


            val prompt = "Modify this image: ${proposal.change_instruction}. Maintain style and background."
            val result = renderAgent.answer(listOf(ImageAndText(text = prompt, image = crop)))

            if (result.image != null) {
              val changeId = "change_${regionIdx}_${altIdx}_${region.label.replace(" ", "_")}"
              val changeFilename = "${prefix}_$changeId.png"
              saveImage(result.image!!, changeFilename, task)

              generatedChanges.add(
                AppliedChange(
                  id = changeId,
                  region = region,
                  instruction = proposal.change_instruction,
                  description = proposal.result_description,
                  imagePath = changeFilename,
                  image = result.image
                )
              )

              transcript?.write(buildString {
                appendLine("### Change: ${region.label} ($altIdx/$numSubimageAlternates)")
                appendLine("**Instruction:** ${proposal.change_instruction}")
                appendLine("![Original](${task.linkTo(changeFilename)})")
              }.toByteArray())
            }
          }
        }

        // 4. Generate M Variations
        logTab.add("Composing $numVariations variations...".renderMarkdown())

        val variations = (1..numVariations).map { varIdx ->
          val canvas = BufferedImage(baseImage.width, baseImage.height, BufferedImage.TYPE_INT_ARGB)
          val g = canvas.createGraphics()
          g.drawImage(baseImage, 0, 0, null)

          // Randomly select subset of changes (e.g., 1 to N changes)
          val changesToApply = generatedChanges
            .groupBy { it.region }
            .values
            .shuffled()
            .take(numChangesPerVariation)
            .map { it.random() }
          val appliedDescriptions = mutableListOf<ChangeDescription>()

          changesToApply.forEach { change ->
            val r = change.region
            val rx = (r.x * baseImage.width / 1000.0).toInt()
            val ry = (r.y * baseImage.height / 1000.0).toInt()
            val rw = (r.width * baseImage.width / 1000.0).toInt()
            val rh = (r.height * baseImage.height / 1000.0).toInt()


            var patch = change.image!!
            if (executionConfig.retarget_subimages) {
              val originalCrop = baseImage.getSubimage(rx, ry, rw, rh)
              val bounds = ImagePatchLocalization.findBounds(
                patch,
                originalCrop,
                ImagePatchLocalization.SubImageBounds(0, 0, patch.width, patch.height, 0.0)
              )
              val bx = bounds.x.coerceIn(0, patch.width)
              val by = bounds.y.coerceIn(0, patch.height)
              val bw = bounds.width.coerceAtMost(patch.width - bx)
              val bh = bounds.height.coerceAtMost(patch.height - by)
              if (bw > 0 && bh > 0) {
                patch = patch.getSubimage(bx, by, bw, bh)
              }
            }

            // Feathering
            val featherSize = (Math.min(rw, rh) * 0.1).toInt().coerceAtLeast(1)
            val featheredPatch = featherImage(patch, featherSize)

            // Resize patch if dimensions drifted slightly (though they shouldn't with standard img2img)
            g.drawImage(featheredPatch, rx, ry, rw, rh, null)

            appliedDescriptions.add(
              ChangeDescription(
                region_label = change.region.label,
                description = change.description,
                bounds = listOf(rx, ry, rw, rh),
                changeId = change.id
              )
            )
          }
          g.dispose()

          val varFilename = "${prefix}_v${varIdx}.png"
          val varLink = saveImage(canvas, varFilename, task)

          // Save JSON Manifest
          val manifest = VariationManifest(inputFile, appliedDescriptions)
          val jsonFile = root.resolve("${prefix}_v${varIdx}.json").toFile()
          jsonFile.writeText(manifest.toJson())

          tabs["Variation $varIdx"] = """
              <p><b>Changes:</b> ${appliedDescriptions.joinToString { it.region_label }}</p>
              <a href="$varLink" target="_blank"><img src="$varLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>
              <br/><a href="${task.linkTo(jsonFile.name)}">View JSON Manifest</a>
            """.trimIndent()

          varFilename to manifest
        }
        generateGame(inputFile, variations, prefix, task)

        val completionMsg =
          "Generated ${variations.size} variations based on ${generatedChanges.size} unique modifications."
        if (orchestrationConfig.autoFix) {
          task.safeComplete(completionMsg.renderMarkdown(), log)
          resultFn(completionMsg)
        } else {
          val footer = acceptButtonFooter(task.ui) {
            task.safeComplete(completionMsg.renderMarkdown(), log)
            resultFn(completionMsg)
          }
          task.add(footer)
        }

      } catch (e: Throwable) {
        log.error("Error in ImageVariationTask: ${e.message}", e)
        task.error(e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        resultFn("Error: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }

  private fun primaryImageFile(): String? {
    return executionConfig?.files?.filter { it.endsWith(".png") }?.let {
      if (it.isEmpty()) null else it[0]
    } ?: executionConfig?.related_files?.filter { it.endsWith(".png") }?.let {
      if (it.isEmpty()) null else it[0]
    }
  }

  private fun generateGame(
    baseImage: String,
    variations: List<Pair<String, VariationManifest>>,
    prefix: String,
    task: SessionTask
  ) {
    try {
      val templateStream = javaClass.getResourceAsStream("diff_game.html") ?: return
      val template = templateStream.bufferedReader().use { it.readText() }

      // Create manifest for base image
      val baseJsonName = "${prefix}_base.json"
      root.resolve(baseJsonName).toFile().writeText(VariationManifest(baseImage, emptyList()).toJson())

      val assets = mutableListOf<Map<String, String>>()
      assets.add(
        mapOf(
          "image" to baseImage,
          "json" to baseJsonName,
          "type" to "base"
        )
      )

      variations.forEach { (imgName, _) ->
        val jsonName = imgName.substringBeforeLast(".") + ".json"
        assets.add(
          mapOf(
            "image" to imgName,
            "json" to jsonName,
            "type" to "variation"
          )
        )
      }

      val html = template.replace("/*GAME_DATA*/", assets.toJson())
      //val gameFile = root.resolve("${prefix}_game.html").toFile()
      val gameFile = root.resolve(baseImage.substringBeforeLast(".") + ".html").toFile()
      gameFile.writeText(html)
      task.newUserFileStream(transcriptFile())?.write(buildString {
        appendLine("## Interactive Game")
        appendLine("[Play 'Find the Differences'](${task.linkTo(gameFile.name)})")
      }.toByteArray())
    } catch (e: Exception) {
      log.warn("Failed to generate game", e)
    }
  }


  private fun saveImage(image: BufferedImage, name: String, task: SessionTask): String {
    val file = root.resolve(name)
    ImageIO.write(image, "png", file.toFile())
    return task.linkTo(name)
  }

  private fun featherImage(image: BufferedImage, feather: Int): BufferedImage {
    val w = image.width
    val h = image.height
    val out = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.drawImage(image, 0, 0, null)
    g.dispose()

    for (y in 0 until h) {
      for (x in 0 until w) {
        var factor = 1.0f
        if (x < feather) factor = Math.min(factor, x.toFloat() / feather)
        if (x >= w - feather) factor = Math.min(factor, (w - x).toFloat() / feather)
        if (y < feather) factor = Math.min(factor, y.toFloat() / feather)
        if (y >= h - feather) factor = Math.min(factor, (h - y).toFloat() / feather)

        if (factor < 1.0f) {
          val rgb = out.getRGB(x, y)
          val alpha = (rgb shr 24) and 0xFF
          val newAlpha = (alpha * factor).toInt()
          out.setRGB(x, y, (rgb and 0x00FFFFFF) or (newAlpha shl 24))
        }
      }
    }
    return out
  }

  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    return ui.hrefLink("Accept Variations") { fn() }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ImageVariationTask::class.java)

    @JvmStatic
    val ImageVariation = TaskType(
      name = "ImageVariation",
      category = "File",
      taskClass = ImageVariationTask::class.java,
      executionConfigClass = ImageVariationConfig::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generates 'Find the Differences' style image variations.",
      tooltipHtml = """
            Analyzes an image to find distinct regions, generates specific visual changes for those regions,
            and creates multiple alternative images by randomly combining these changes.
            Outputs images and JSON manifests describing the differences.
            """,
    )
  }
}