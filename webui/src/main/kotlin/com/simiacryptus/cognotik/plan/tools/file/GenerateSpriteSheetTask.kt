package com.simiacryptus.cognotik.plan.tools.file

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedImageAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class GenerateSpriteSheetTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GenerateSpriteSheetTaskExecutionConfigData?
) : AbstractFileTask<GenerateSpriteSheetTask.GenerateSpriteSheetTaskExecutionConfigData>(
    orchestrationConfig,
    planTask
) {

    class GenerateSpriteSheetTaskExecutionConfigData(
        @Description("The sprite sheet image file to be created (relative path, must end with .png)")
        files: List<String>? = null,
        @Description("The JSON metadata file to be created (relative path, must end with .json)")
        val metadata_file: String? = null,
        @Description("Detailed description of the sprites to generate (e.g., 'A pixel art warrior walking animation, 4 frames, side view')")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = GenerateSpriteSheet.name,
        task_description = task_description,
        files = files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            if (files.isNullOrEmpty()) return "Sprite sheet image file must be specified"
            if (metadata_file.isNullOrBlank()) return "Metadata JSON file must be specified"
            if (!files.first().endsWith(".png", ignoreCase = true)) return "Image file must be .png"
            if (!metadata_file.endsWith(".json", ignoreCase = true)) return "Metadata file must be .json"
            return ValidatedObject.validateFields(this)
        }
    }

    data class SpriteLocation(
        @Description("Name or tag of the specific sprite")
        val name: String = "",
        @Description("X coordinate of the top-left corner")
        val x: Int = 0,
        @Description("Y coordinate of the top-left corner")
        val y: Int = 0,
        @Description("Width of the sprite")
        val width: Int = 0,
        @Description("Height of the sprite")
        val height: Int = 0
    )

    data class SpriteSheetMetadata(
        @Description("List of sprites identified in the sheet")
        val sprites: List<SpriteLocation> = emptyList(),
        @Description("General description of the sprite sheet content")
        val description: String = ""
    )

    override fun promptSegment(): String {
        return """
GenerateSpriteSheet - Create a sprite sheet image and corresponding JSON metadata
  * Generates an image containing multiple sprites based on a description
  * Automatically identifies sprite locations (x, y, width, height)
  * Outputs both a .png image and a .json metadata file
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val imageFile = executionConfig?.files?.firstOrNull() ?: return resultFn("No image file specified")
        val metadataFile = executionConfig?.metadata_file ?: return resultFn("No metadata file specified")
        val description = executionConfig?.task_description ?: "Generate a sprite sheet"

        task.header("Generating Sprite Sheet: $imageFile", level = 2)

        try {
            // Step 1: Generate the Image
            task.header("Step 1: Drawing Sprites...", level = 3)

            val imageGenPrompt = """
                Create a sprite sheet based on this description: $description.
                Requirements:
                - The image is 1:1 aspect ratio, minimum 512x512 pixels.
                - Arrange sprites in a grid or logical layout.
                - CRITICAL: Use a solid MAGENTA (Hex #FF00FF) background.
                - Ensure sprites do not overlap.
                - Maintain consistent style and scale.
            """.trimIndent()

            val imageAgent = ImageProcessingAgent(
                prompt = "You are a pixel artist and game asset designer.",
                name = "SpriteGenerator",
                model = orchestrationConfig.defaultImage.getChildClient(task),
            )

            val imageResult = imageAgent.answer(listOf(ImageAndText(imageGenPrompt)))
            var generatedImage = imageResult.image ?: throw RuntimeException("Failed to generate image")

            // Process transparency before saving or analyzing
            task.header("Processing Transparency...", level = 3)
            generatedImage = makeTransparent(generatedImage)

            // Save Image
            val imageOutputPath = root.resolve(imageFile)
            imageOutputPath.toFile().parentFile?.mkdirs()
            ImageIO.write(generatedImage, "png", imageOutputPath.toFile())

            val imageLink = task.linkTo(imageFile)
            task.add("""<a href="$imageLink" target="_blank"><img src="$imageLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>""")

            // Step 2: Parse Metadata
            task.header("Step 2: Analyzing Sprite Locations...", level = 3)

            val parserAgent = ParsedImageAgent(
                resultClass = SpriteSheetMetadata::class.java,
                model = (typeConfig?.model?.let { orchestrationConfig.instance(it) } ?: defaultSmart).getChildClient(
                    task
                ),
                prompt = """
                    Identify all distinct sprites in this image.
                    Output coordinates assuming a 1000x1000 image size, regardless of actual aspect ratio.
                    For each sprite, provide:
                    1. A descriptive name (e.g., 'walk_frame_1', 'idle_stand').
                    2. The exact bounding box (x, y, width, height) in pixels.
                    Ensure bounding boxes are tight around the sprite content.
                    Ignore the background color.
                """.trimIndent(),
            )

            val parseResult = parserAgent.answer(
                listOf(
                    ImageAndText(
                        text = "Extract sprite metadata from this image.",
                        image = generatedImage
                    )
                )
            )

            val rawMetadata = parseResult.obj
            val metadata = rawMetadata.copy(sprites = rawMetadata.sprites.map { sprite ->
                val scaledX = (sprite.x * generatedImage.width / 1000.0).toInt().coerceIn(0, generatedImage.width - 1)
                val scaledY = (sprite.y * generatedImage.height / 1000.0).toInt().coerceIn(0, generatedImage.height - 1)
                val scaledW = (sprite.width * generatedImage.width / 1000.0).toInt().coerceAtMost(generatedImage.width - scaledX)
                val scaledH = (sprite.height * generatedImage.height / 1000.0).toInt().coerceAtMost(generatedImage.height - scaledY)
                sprite.copy(
                    x = scaledX,
                    y = scaledY,
                    width = scaledW,
                    height = scaledH
                )
            })

            // Save Metadata
            val jsonOutputPath = root.resolve(metadataFile)
            val mapper = jacksonObjectMapper().writerWithDefaultPrettyPrinter()
            jsonOutputPath.toFile().writeText(mapper.writeValueAsString(metadata))
            // Save Individual Sprites
            val baseName = File(imageFile).nameWithoutExtension
            val parentDir = File(imageFile).parent ?: ""
            val spriteDirRelative = if (parentDir.isEmpty()) baseName else "$parentDir/$baseName"
            val spriteDir = root.resolve(spriteDirRelative)
            spriteDir.toFile().mkdirs()
            val debugImage = BufferedImage(generatedImage.width, generatedImage.height, BufferedImage.TYPE_INT_ARGB)
            val g = debugImage.createGraphics()
            g.drawImage(generatedImage, 0, 0, null)
            g.color = Color.RED
            g.stroke = BasicStroke(2f)
            val spriteHtml = StringBuilder()
            metadata.sprites.forEach { sprite ->
                try {
                    g.drawRect(sprite.x, sprite.y, sprite.width, sprite.height)
                    if (sprite.width > 0 && sprite.height > 0) {
                        val subImage = generatedImage.getSubimage(sprite.x, sprite.y, sprite.width, sprite.height)
                        val safeName = sprite.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                        val spriteFileName = "$safeName.png"
                        ImageIO.write(subImage, "png", spriteDir.resolve(spriteFileName).toFile())
                        val spriteLink = task.linkTo("$spriteDirRelative/$spriteFileName")
                        spriteHtml.append("""<div style="display:inline-block;margin:2px;border:1px solid #ccc;padding:2px"><img src="$spriteLink" title="${sprite.name}" style="max-height:50px"/></div>""")
                    }
                } catch (e: Exception) {
                    log.warn("Failed to save sprite ${sprite.name}", e)
                }
            }
            g.dispose()
            val debugFile = if (parentDir.isEmpty()) "${baseName}_debug.png" else "$parentDir/${baseName}_debug.png"
            ImageIO.write(debugImage, "png", root.resolve(debugFile).toFile())
            val debugLink = task.linkTo(debugFile)


            // Display Results
            val metadataLink = task.linkTo(metadataFile)


            val tabs = TabbedDisplay(task)

            // Tab 1: Overview
            tabs["Overview"] = """
                <ul>
                    <li><b>Image:</b> <a href="$imageLink">$imageFile</a></li>
                    <li><b>Metadata:</b> <a href="$metadataLink">$metadataFile</a></li>
                    <li><b>Sprite Count:</b> ${metadata.sprites.size}</li>
                    <li><b>Sprites Directory:</b> $spriteDirRelative/</li>
                </ul>
                <div style="margin-top: 10px;">
                    <a href="$imageLink" target="_blank"><img src="$imageLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>
                </div>
            """.trimIndent()

            // Tab 2: Bounding Boxes
            tabs["Bounding Boxes"] = """
                <a href="$debugLink" target="_blank"><img src="$debugLink" style="max-width: 100%; border: 1px solid #ccc;" /></a>
            """.trimIndent()

            // Tab 3: Sprites
            tabs["Sprites"] = spriteHtml.toString()

            // Tab 4: Data Table
            val tableRows = metadata.sprites.joinToString("\n") {
                "| ${it.name} | ${it.x}, ${it.y} | ${it.width}x${it.height} |"
            }
            tabs["Data"] = MarkdownUtil.renderMarkdown(
                """
| Name | Position | Size |
|------|----------|------|
$tableRows
            """.trimIndent(), ui = task.ui
            )

            task.safeComplete("Generated sprite sheet with ${metadata.sprites.size} sprites", log)
            resultFn("Generated sprite sheet: $imageFile and $metadataFile. Found ${metadata.sprites.size} sprites.")

        } catch (e: Exception) {
            log.error("Error generating sprite sheet", e)
            task.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }
    private fun makeTransparent(source: BufferedImage): BufferedImage {
        // 1. Determine Background Color (assume top-left pixel is background)
        val bgRgb = source.getRGB(0, 0)
        val bgColor = Color(bgRgb)
        val width = source.width
        val height = source.height
        val dest = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        // Configuration for "Soft Keying"
        val tolerance = 15.0 // Pixels this close to BG color are fully transparent
        val softness = 60.0  // The fade-in range (gradient)
        for (x in 0 until width) {
            for (y in 0 until height) {
                val rgb = source.getRGB(x, y)
                val c = Color(rgb)
                // 2. Calculate Euclidean distance in RGB space
                val distance = Math.sqrt(
                    Math.pow((c.red - bgColor.red).toDouble(), 2.0) +
                            Math.pow((c.green - bgColor.green).toDouble(), 2.0) +
                            Math.pow((c.blue - bgColor.blue).toDouble(), 2.0)
                )
                // 3. Calculate Alpha based on distance
                var alpha: Int
                if (distance < tolerance) {
                    alpha = 0 // Fully transparent
                } else if (distance < tolerance + softness) {
                    // Gradient / Anti-aliased edge
                    val factor = (distance - tolerance) / softness
                    alpha = (factor * 255).toInt().coerceIn(0, 255)
                } else {
                    alpha = 255 // Fully opaque
                }
                // 4. Reconstruct Pixel
                // Note: For high-end spill removal, you would adjust RGB here to un-mix the background.
                // For now, keeping original RGB with calculated Alpha is usually sufficient for sprites.
                val newCol = (alpha shl 24) or (c.red shl 16) or (c.green shl 8) or c.blue
                dest.setRGB(x, y, newCol)
            }
        }
        return dest
    }


    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        return ui.hrefLink("Accept Sprite Sheet") { fn() }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GenerateSpriteSheetTask::class.java)
        val GenerateSpriteSheet = TaskType(
          name = "GenerateSpriteSheet",
          category = "Writing",
          taskClass = GenerateSpriteSheetTask::class.java,
          executionConfigClass = GenerateSpriteSheetTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate a sprite sheet and associated JSON metadata",
          tooltipHtml = """
                        Creates game assets by generating a sprite sheet image and extracting coordinate data.
                        <ul>
                          <li>Generates visual sprite sheet using AI image models</li>
                          <li>Analyzes the generated image to find sprite bounding boxes</li>
                          <li>Exports standard JSON metadata for game engine integration</li>
                        </ul>
                      """,
        )
    }
}