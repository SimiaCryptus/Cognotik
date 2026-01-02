package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO

class ImageTableTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ImageTableTaskExecutionConfigData?
) : AbstractTask<ImageTableTask.ImageTableTaskExecutionConfigData, ImageTableTask.ImageTableTaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class ImageTableTaskExecutionConfigData(
        @Description("Row labels for the image table (these can be descriptive text or image file paths)")
        val rows: List<String>? = null,
        @Description("Column labels for the image table (these can be descriptive text or image file paths)")
        val columns: List<String>? = null,
        @Description("Prompt template for generating each image. Use {row} and {column} as placeholders.")
        val image_prompt_template: String? = null,
        @Description("Base style or context to apply to all generated images")
        val base_style: String? = null,
        @Description("Output directory for generated images (relative path)")
        val output_directory: String? = "generated_images",
        @Description("Image format: 'png', 'jpg', or 'jpeg'")
        val image_format: String = "png",
        @Description("Overall description of the image table purpose")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, TaskExecutionConfig(
        task_type = ImageTable.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ) {
        override fun validate(): String? {
            if (rows.isNullOrEmpty()) {
                return "ImageTableTask: rows list cannot be null or empty"
            }
            if (columns.isNullOrEmpty()) {
                return "ImageTableTask: columns list cannot be null or empty"
            }
            if (image_prompt_template.isNullOrBlank()) {
                return "ImageTableTask: image_prompt_template cannot be null or blank"
            }
            if (image_format !in listOf("png", "jpg", "jpeg")) {
                return "ImageTableTask: image_format must be 'png', 'jpg', or 'jpeg'"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    class ImageTableTaskTypeConfig(
        task_type: String? = ImageTable.name,
        @Description("Maximum number of images to generate in parallel")
        val parallel_generation: Int = 2,
        @Description("Image width in pixels")
        val image_width: Int = 512,
        @Description("Image height in pixels")
        val image_height: Int = 512,
    ) : TaskTypeConfig(task_type = task_type), ValidatedObject {
        override fun validate(): String? {
            if (parallel_generation < 1 || parallel_generation > 10) {
                return "ImageTableTask: parallel_generation must be between 1 and 10"
            }
            if (image_width < 64 || image_width > 2048) {
                return "ImageTableTask: image_width must be between 64 and 2048"
            }
            if (image_height < 64 || image_height > 2048) {
                return "ImageTableTask: image_height must be between 64 and 2048"
            }
            return null
        }
    }

    init {
        planTask?.validate()?.let { errorMessage ->
            throw ValidatedObject.ValidationError(errorMessage, planTask)
        }
    }

    override fun promptSegment(): String {
        return """
ImageTable - Generate a table/grid of AI-generated images
  ** Specify row labels in the 'rows' array (these can be descriptive text or image file paths)
  ** Specify column labels in the 'columns' array (these can be descriptive text or image file paths)
  ** Provide an image_prompt_template using {row} and {column} placeholders
  ** Optionally specify a base_style for consistent styling across all images
  ** Images are saved to the specified output_directory
  ** Generates an HTML table displaying all images with labels
  ** Example use cases:
     - Style comparison grids (subjects vs art styles)
     - Product variation displays (colors vs sizes)
     - Character emotion charts (characters vs emotions)
     - Concept exploration matrices (themes vs settings)
      """
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        executionConfig?.validate()?.let { errorMessage ->
            resultFn("VALIDATION ERROR: $errorMessage")
            return
        }

        val rows = executionConfig?.rows ?: emptyList()
        val columns = executionConfig?.columns ?: emptyList()
        val promptTemplate = executionConfig?.image_prompt_template ?: ""
        val baseStyle = executionConfig?.base_style ?: ""
        val outputDir = executionConfig?.output_directory ?: "generated_images"
        val imageFormat = executionConfig?.image_format ?: "png"

        task.header("Image Table Generation", level = 2)
        task.add("Generating ${rows.size}x${columns.size} image grid (${rows.size * columns.size} total images)")

        // Create output directory
        val outputPath = task.resolveUserFile(outputDir)
        outputPath?.mkdirs()

        // Initialize the results table to store image paths
        val imageResults = Array(rows.size) { Array(columns.size) { "" } }
        val totalImages = rows.size * columns.size
        var completedImages = 0

        // Create the image generation agent
        val imageChatChatter = orchestrationConfig.defaultImage.getChildClient(task)
        val imageAgent = ImageProcessingAgent(
            prompt = "Transform the user request into an image. Generate exactly what is described.",
            name = "ImageTableGenerator",
            model = imageChatChatter,
        )

        // Process each cell
        for (rowIdx in rows.indices) {
            for (colIdx in columns.indices) {
                completedImages++
                val rowLabel = rows[rowIdx]
                val colLabel = columns[colIdx]

                task.header(
                    "Generating image $completedImages/$totalImages: Row='$rowLabel', Column='$colLabel'",
                    level = 3
                )

                // Build the prompt for this cell
                var prompt = promptTemplate.replace("{row}", rowLabel).replace("{column}", colLabel)
                prompt = if (baseStyle.isNotBlank()) {
                    "$prompt. Style: $baseStyle"
                } else {
                    prompt
                }
                val imagePrompt = mutableListOf(ImageAndText(prompt))
                when (rowLabel.split('.').last()) {
                    "jpg", "jpeg", "png" -> {
                        val imagePath = agent.root.resolve(rowLabel)
                        if (imagePath.toFile().exists()) {
                            imagePrompt.add(ImageAndText(imagePath.toUri().toString(), image = imagePath.loadImage()))
                        } else {
                            log.warn("Image file for row label not found: $imagePath")
                        }
                    }
                }
                when (colLabel.split('.').last()) {
                    "jpg", "jpeg", "png" -> {
                        val imagePath = agent.root.resolve(colLabel)
                        if (imagePath.toFile().exists()) {
                            imagePrompt.add(ImageAndText(imagePath.toUri().toString(), image = imagePath.loadImage()))
                        } else {
                            log.warn("Image file for column label not found: $imagePath")
                        }
                    }
                }


                task.verbose("Prompt: $prompt")

                try {
                    // Generate the image
                    val result = imageAgent.answer(imagePrompt)
                    val generatedImage = result.image

                    // Create filename
                    val safeRowLabel = sanitizeFilename(rowLabel)
                    val safeColLabel = sanitizeFilename(colLabel)
                    val filename = "${safeRowLabel}_${safeColLabel}.$imageFormat"
                    val imagePath = outputPath?.resolve(filename)

                    // Save the image
                    ImageIO.write(generatedImage, imageFormat, imagePath)

                    // Store the relative path
                    val relativePath = "$outputDir/$filename"
                    imageResults[rowIdx][colIdx] = relativePath

                    // Show preview
                    task.image(generatedImage!!)

                } catch (e: Exception) {
                    log.error("Error generating image for row='$rowLabel', column='$colLabel'", e)
                    imageResults[rowIdx][colIdx] = "ERROR"
                    task.error(e)
                }
            }
        }

        // Generate the HTML table output
        task.header("Generated Image Table", level = 2)
        val htmlTable = formatAsHtmlTable(rows, columns, imageResults, task)
        task.add(htmlTable)

        // Generate markdown summary
        val markdownSummary = formatAsMarkdownSummary(rows, columns, imageResults)

        // Save the HTML table to a file
        val htmlFilename = "image_table.html"
        val htmlPath = outputPath?.resolve(htmlFilename)
        htmlPath?.writeText(generateStandaloneHtml(rows, columns, imageResults))

        val summary = buildString {
            appendLine("Successfully generated ${rows.size}x${columns.size} image table.")
            appendLine("Images saved to: $outputDir/")
            appendLine("HTML table saved to: $outputDir/$htmlFilename")
        }

        task.complete(summary)
        resultFn("$summary\n\n$markdownSummary")
    }

    private fun Path.loadImage(): BufferedImage? {
        return try {
            ImageIO.read(this.toFile())
        } catch (e: Exception) {
            log.error("Error loading image from path: $this", e)
            null
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(50)
    }

    private fun formatAsHtmlTable(
        rows: List<String>,
        columns: List<String>,
        images: Array<Array<String>>,
        task: SessionTask
    ): String {
        return buildString {
            appendLine("""<table style="border-collapse: collapse; margin: 20px 0;">""")
            appendLine("  <thead>")
            appendLine("    <tr>")
            appendLine("""      <th style="border: 1px solid #ddd; padding: 12px; background: #f5f5f5;"></th>""")
            columns.forEach { col ->
                appendLine("""      <th style="border: 1px solid #ddd; padding: 12px; background: #f5f5f5; text-align: center;">$col</th>""")
            }
            appendLine("    </tr>")
            appendLine("  </thead>")
            appendLine("  <tbody>")
            rows.forEachIndexed { rowIdx, rowHeader ->
                appendLine("    <tr>")
                appendLine("""      <th style="border: 1px solid #ddd; padding: 12px; background: #f5f5f5;">$rowHeader</th>""")
                columns.indices.forEach { colIdx ->
                    val imagePath = images[rowIdx][colIdx]
                    val cellContent = if (imagePath == "ERROR") {
                        """<span style="color: red;">Error</span>"""
                    } else {
                        val link = task.linkTo(imagePath)
                        """<a href="$link" target="_blank"><img src="$link" style="max-width: 150px; max-height: 150px; border-radius: 4px;" /></a>"""
                    }
                    appendLine("""      <td style="border: 1px solid #ddd; padding: 8px; text-align: center;">$cellContent</td>""")
                }
                appendLine("    </tr>")
            }
            appendLine("  </tbody>")
            appendLine("</table>")
        }
    }

    private fun formatAsMarkdownSummary(
        rows: List<String>,
        columns: List<String>,
        images: Array<Array<String>>
    ): String {
        return buildString {
            appendLine("### Image Table Summary")
            appendLine()
            appendLine("| Row | Column | Image Path |")
            appendLine("|-----|--------|------------|")
            rows.forEachIndexed { rowIdx, rowHeader ->
                columns.forEachIndexed { colIdx, colHeader ->
                    val imagePath = images[rowIdx][colIdx]
                    appendLine("| $rowHeader | $colHeader | $imagePath |")
                }
            }
        }
    }

    private fun generateStandaloneHtml(
        rows: List<String>,
        columns: List<String>,
        images: Array<Array<String>>
    ): String {
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html>")
            appendLine("<head>")
            appendLine("  <title>Generated Image Table</title>")
            appendLine("  <style>")
            appendLine("    body { font-family: Arial, sans-serif; padding: 20px; }")
            appendLine("    table { border-collapse: collapse; margin: 20px auto; }")
            appendLine("    th, td { border: 1px solid #ddd; padding: 12px; text-align: center; }")
            appendLine("    th { background: #f5f5f5; }")
            appendLine("    img { max-width: 200px; max-height: 200px; border-radius: 4px; cursor: pointer; }")
            appendLine("    img:hover { transform: scale(1.05); transition: transform 0.2s; }")
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <h1>Generated Image Table</h1>")
            appendLine("  <table>")
            appendLine("    <thead>")
            appendLine("      <tr>")
            appendLine("        <th></th>")
            columns.forEach { col ->
                appendLine("        <th>$col</th>")
            }
            appendLine("      </tr>")
            appendLine("    </thead>")
            appendLine("    <tbody>")
            rows.forEachIndexed { rowIdx, rowHeader ->
                appendLine("      <tr>")
                appendLine("        <th>$rowHeader</th>")
                columns.indices.forEach { colIdx ->
                    val imagePath = images[rowIdx][colIdx]
                    val filename = imagePath.substringAfterLast("/")
                    val cellContent = if (imagePath == "ERROR") {
                        """<span style="color: red;">Error</span>"""
                    } else {
                        """<a href="$filename" target="_blank"><img src="$filename" alt="$rowHeader - ${columns[colIdx]}" /></a>"""
                    }
                    appendLine("        <td>$cellContent</td>")
                }
                appendLine("      </tr>")
            }
            appendLine("    </tbody>")
            appendLine("  </table>")
            appendLine("</body>")
            appendLine("</html>")
        }
    }

    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept Image Table") {
            fn()
        }
        return """
        |
        |---
        |
        |$acceptLink
        """.trimMargin()
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ImageTableTask::class.java)
        val ImageTable = TaskType(
            "ImageTable",
            "File",
            ImageTableTask::class.java,
            ImageTableTaskExecutionConfigData::class.java,
            ImageTableTaskTypeConfig::class.java,
            "Generate a table/grid of AI-generated images",
            """
              Creates a grid of images by generating each cell using AI image generation.
              <ul>
                <li>Define rows and columns as labels for the grid</li>
                <li>Provide a prompt template with {row} and {column} placeholders</li>
                <li>Optionally specify a base style for consistent aesthetics</li>
                <li>Generates individual images and an HTML table view</li>
                <li>Useful for style comparisons, product variations, character sheets</li>
              </ul>
            """,
        )
    }
}