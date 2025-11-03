package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.safeComplete
import com.simiacryptus.cognotik.plan.tools.reasoning.validateAndGetApi
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import javax.imageio.ImageIO

class IllustrateDocumentTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: IllustrateDocumentTaskExecutionConfigData?
) : AbstractFileTask<IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData>(orchestrationConfig, planTask) {

  data class ImageSuggestion(
    @Description("Descriptive name for the image file (without extension)")
    val imageName: String = "",
    @Description("Detailed prompt for generating the image")
    val imagePrompt: String = "",
    @Description("Location in document where image should be inserted (section heading or paragraph start)")
    val insertionPoint: String = "",
    @Description("Caption or alt text for the image")
    val caption: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (imageName.isBlank()) return "ImageSuggestion imageName cannot be blank"
      if (imagePrompt.isBlank()) return "ImageSuggestion imagePrompt cannot be blank"
      if (insertionPoint.isBlank()) return "ImageSuggestion insertionPoint cannot be blank"
      return null
    }
  }

  data class DocumentAnalysis(
    @Description("List of suggested images to enhance the document")
    val suggestions: List<ImageSuggestion> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (suggestions.isEmpty()) return "DocumentAnalysis must contain at least one suggestion"
      suggestions.forEach { suggestion ->
        suggestion.validate()?.let { return it }
      }
      return null
    }
  }

  class IllustrateDocumentTaskExecutionConfigData(
    @Description("The document file to illustrate (must be .md or .html)")
    files: List<String>? = null,
    @Description("Maximum number of images to generate (default: 5)")
    val maxImages: Int = 5,
    @Description("Image format to use (png or jpg, default: png)")
    val imageFormat: String = "png",
    @Description("Whether to automatically insert image references into the document")
    val autoInsert: Boolean = true,
    @Description("Additional instructions to append to image generation prompts (e.g., style preferences, constraints)")
    val imageInstructions: String? = null,
    @Description("Directive for the image composer on how to generate images (e.g., 'Generate a background wallpaper', 'Create hero images', 'Focus on technical diagrams')")
    val composerDirective: String? = null,
    @Description("Directive for the image integrator on how to insert images (e.g., 'Insert as page background', 'Place in sidebars', 'Create galleries')")
    val integratorDirective: String? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = IllustrateDocument.name,
    task_description = task_description,
    files = files,
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      if (files.isNullOrEmpty()) {
        return "IllustrateDocumentTask requires exactly one file to be specified"
      }
      if (files.size > 1) {
        return "IllustrateDocumentTask can only process one file at a time"
      }
      val file = files.first()
      if (!file.matches(Regex(".*\\.(md|html)$", RegexOption.IGNORE_CASE))) {
        return "IllustrateDocumentTask file must have .md or .html extension: $file"
      }
      if (maxImages < 1 || maxImages > 20) {
        return "maxImages must be between 1 and 20"
      }
      if (imageFormat !in listOf("png", "jpg", "jpeg")) {
        return "imageFormat must be 'png', 'jpg', or 'jpeg'"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
IllustrateDocument - Analyze a document and generate images to enhance its content
  ** Specify a markdown or HTML file to illustrate
  ** Configure maximum number of images (default: 5)
  ** Choose image format (png/jpg)
  ** Enable/disable automatic insertion of image references
  ** Analyzes document structure and content
  ** Generates contextually appropriate images
  ** Saves images with descriptive names in the same folder
  ** Optionally inserts image references at appropriate locations
  ** Useful for:
     - Enhancing documentation
     - Creating visual guides
     - Illustrating blog posts
     - Adding diagrams to technical content
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val startTime = System.currentTimeMillis()
    val documentFile = executionConfig?.files?.firstOrNull()

    if (documentFile == null) {
      val errorMsg = "CONFIGURATION ERROR: No document file specified"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }

    val documentPath = root.resolve(documentFile)
    if (!documentPath.toFile().exists()) {
      val errorMsg = "ERROR: Document file not found: $documentFile"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }

    val maxImages = executionConfig.maxImages.coerceIn(1, 20)
    val imageFormat = executionConfig.imageFormat.lowercase()
    val autoInsert = executionConfig.autoInsert

    log.info("Starting IllustrateDocumentTask for: $documentFile (maxImages=$maxImages, format=$imageFormat, autoInsert=$autoInsert)")

    val ui = task.ui

    try {
      // Read document content
      val documentContent = documentPath.toFile().readText()
      val isMarkdown = documentFile.endsWith(".md", ignoreCase = true)

      task.add(MarkdownUtil.renderMarkdown("## Illustrating Document: `$documentFile`", ui = ui))
      task.add(MarkdownUtil.renderMarkdown("**Format:** ${if (isMarkdown) "Markdown" else "HTML"}", ui = ui))
      task.add(MarkdownUtil.renderMarkdown("**Max Images:** $maxImages", ui = ui))
      task.add(MarkdownUtil.renderMarkdown("**Image Format:** $imageFormat", ui = ui))
      if (!executionConfig.composerDirective.isNullOrBlank()) {
        task.add(MarkdownUtil.renderMarkdown("**Composer Directive:** ${executionConfig.composerDirective}", ui = ui))
      }
      if (!executionConfig.integratorDirective.isNullOrBlank()) {
        task.add(MarkdownUtil.renderMarkdown("**Integrator Directive:** ${executionConfig.integratorDirective}", ui = ui))
      }

      // Step 1: Analyze document and suggest images
      log.info("Analyzing document to suggest images")
      task.add(MarkdownUtil.renderMarkdown("### 🔍 Analyzing Document", ui = ui))
      task.add(MarkdownUtil.renderMarkdown("Identifying sections that would benefit from visual enhancement...", ui = ui))

      val analysisPrompt = buildAnalysisPrompt(
        documentContent,
        maxImages,
        isMarkdown,
        executionConfig.composerDirective
      )

      val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return
      val parsingChatter = orchestrationConfig.parsingChatter.getChildClient(task)
      val defaultChatter = api.getChildClient(task)

      val analysisAgent = ParsedAgent(
        resultClass = DocumentAnalysis::class.java,
        prompt = analysisPrompt,
        model = defaultChatter,
        temperature = 0.5,
        parsingChatter = parsingChatter
      )

      val analysis = analysisAgent.answer(listOf(analysisPrompt))
      val suggestions = analysis.obj.suggestions.take(maxImages)

      log.info("Generated ${suggestions.size} image suggestions")
      task.add(MarkdownUtil.renderMarkdown("✅ Identified ${suggestions.size} opportunities for images", ui = ui))

      // Display suggestions
      task.add(MarkdownUtil.renderMarkdown("### 📋 Planned Images", ui = ui))
      suggestions.forEachIndexed { index, suggestion ->
        task.add(
          MarkdownUtil.renderMarkdown(
            """
          |**${index + 1}. ${suggestion.imageName}**
          |- Location: ${suggestion.insertionPoint}
          |- Caption: ${suggestion.caption}
          """.trimMargin(), ui = ui
          )
        )
      }

      // Step 2: Generate images
      log.info("Generating ${suggestions.size} images")
      task.add(MarkdownUtil.renderMarkdown("### 🎨 Generating Images", ui = ui))

      val imageAgent = ImageProcessingAgent(
        prompt = "Transform the user request into an image that enhances document content",
        name = "DocumentIllustrator",
        model = orchestrationConfig.imageChatChatter,
      )

      val generatedImages = mutableListOf<Triple<String, String, ImageSuggestion>>()
      val documentFolder = documentPath.parent

      suggestions.forEachIndexed { index, suggestion ->
        try {
          task.add(MarkdownUtil.renderMarkdown("#### Generating: ${suggestion.imageName}", ui = ui))

          // Build enhanced prompt with all supplemental instructions
          val enhancedPrompt = buildString {
            append(suggestion.imagePrompt)

            if (!executionConfig.composerDirective.isNullOrBlank()) {
              append("\n\nComposer Directive: ${executionConfig.composerDirective}")
            }

            if (!executionConfig.imageInstructions.isNullOrBlank()) {
              append("\n\nAdditional Instructions: ${executionConfig.imageInstructions}")
            }
          }

          val result = imageAgent.answer(listOf(ImageAndText(enhancedPrompt)))
          val generatedImage = result.image

          // Save image with descriptive name
          val sanitizedName = suggestion.imageName
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
          val imageFileName = "${sanitizedName}.$imageFormat"
          val imagePath = documentFolder.resolve(imageFileName)

          ImageIO.write(generatedImage, imageFormat, imagePath.toFile())
          log.info("Saved image: $imageFileName")

          // Display preview
          val previewFile = task.resolve(imageFileName)
          ImageIO.write(generatedImage, imageFormat, previewFile!!)
          val previewLink = task.linkTo(imageFileName)
          task.add("""<a href="$previewLink" target="_blank"><img src="$previewLink" style="max-width: 400px; border-radius: 4px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);" /></a>""")
          task.add(MarkdownUtil.renderMarkdown("✅ Saved as `$imageFileName`", ui = ui))

          generatedImages.add(Triple(imageFileName, imagePath.toString(), suggestion))

        } catch (e: Exception) {
          log.error("Failed to generate image: ${suggestion.imageName}", e)
          task.add(MarkdownUtil.renderMarkdown("❌ Failed to generate: ${suggestion.imageName} - ${e.message}", ui = ui))
        }
      }

// Step 3: Insert image references into document (if enabled)
      if (autoInsert && generatedImages.isNotEmpty()) {
        log.info("Inserting image references into document")
        task.add(MarkdownUtil.renderMarkdown("### 📝 Updating Document", ui = ui))

        val updatedContent = insertImageReferences(
          documentContent,
          generatedImages,
          isMarkdown,
          executionConfig.integratorDirective
        )

        // Save updated document
        documentPath.toFile().writeText(updatedContent)
        task.add(MarkdownUtil.renderMarkdown("✅ Document updated with ${generatedImages.size} image references", ui = ui))
        log.info("Document updated successfully")
      }

      val totalTime = System.currentTimeMillis() - startTime
      val summary = buildString {
        appendLine("# Document Illustration Complete")
        appendLine()
        appendLine("**Document:** $documentFile")
        appendLine()
        appendLine("**Images Generated:** ${generatedImages.size}")
        appendLine()
        appendLine("**Time:** ${totalTime / 1000.0}s")
        appendLine()
        if (autoInsert) {
          appendLine("**Status:** Document updated with image references")
        } else {
          appendLine("**Status:** Images generated (manual insertion required)")
        }
        appendLine()
        appendLine("## Generated Images")
        appendLine()
        generatedImages.forEach { (fileName, _, suggestion) ->
          appendLine("- **$fileName**: ${suggestion.caption}")
        }
      }

      log.info("IllustrateDocumentTask completed: images=${generatedImages.size}, time=${totalTime}ms")
      task.safeComplete("Generated ${generatedImages.size} images in ${totalTime / 1000}s", log)
      resultFn(summary)

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      log.error("IllustrateDocumentTask failed after ${duration}ms for: $documentFile", e)
      task.error(e)

      val errorOutput = buildString {
        appendLine("# Error Illustrating Document")
        appendLine()
        appendLine("**Document:** $documentFile")
        appendLine()
        appendLine("**Error:** ${e.message}")
        appendLine()
        appendLine("**Type:** ${e.javaClass.simpleName}")
      }

      task.safeComplete("Document illustration failed: ${e.message}", log)
      resultFn(errorOutput)
    }
  }

  private fun buildAnalysisPrompt(
    documentContent: String,
    maxImages: Int,
    isMarkdown: Boolean,
    composerDirective: String?
  ): String {
    val formatInfo = if (isMarkdown) "Markdown" else "HTML"
    val directiveSection = if (!composerDirective.isNullOrBlank()) {
      """
## Composer Directive:
${composerDirective}
**Important:** Follow this directive when suggesting images and creating prompts.
      """.trimIndent()
    } else {
      ""
    }

    return """
 You are a document enhancement expert. Analyze this $formatInfo document and suggest images that would enhance its content.
${directiveSection}

 ## Document Content:
```
${documentContent.take(10000)}
```

## Your Task:
Identify up to $maxImages locations in the document where images would add significant value. For each suggestion:

1. **imageName**: Create a descriptive, filesystem-safe name (e.g., "user_authentication_flow", "data_pipeline_diagram")
2. **imagePrompt**: Write a detailed prompt for generating the image, including:
   - Subject matter and key elements
   - Visual style (diagram, illustration, photo-realistic, etc.)
   - Color scheme and mood
   - Specific details that match the document context
3. **insertionPoint**: Identify where to insert the image by providing:
   - The exact heading text, or
   - The first few words of the paragraph where it should appear
4. **caption**: Write a clear, informative caption or alt text

## Guidelines:
- Prioritize sections with complex concepts that benefit from visualization
- Consider diagrams for processes, workflows, and architectures
- Suggest illustrations for abstract concepts
- Ensure images complement rather than duplicate text content
- Focus on high-impact locations that enhance understanding
- Make image prompts specific and detailed for best results

Generate suggestions now.
        """.trimIndent()
  }

  private fun insertImageReferences(
    content: String,
    images: List<Triple<String, String, ImageSuggestion>>,
    isMarkdown: Boolean,
    integratorDirective: String?
  ): String {
    var updatedContent = content
    // Check if integrator directive specifies special handling
    val isBackgroundWallpaper = integratorDirective?.contains("background", ignoreCase = true) == true ||
        integratorDirective?.contains("wallpaper", ignoreCase = true) == true
    val isSidebar = integratorDirective?.contains("sidebar", ignoreCase = true) == true
    val isGallery = integratorDirective?.contains("gallery", ignoreCase = true) == true
    // Handle special integration modes
    if (isBackgroundWallpaper && !isMarkdown) {
      // For HTML, add CSS background
      val firstImage = images.firstOrNull()
      if (firstImage != null) {
        val (fileName, _, suggestion) = firstImage
        val styleTag = """
<style>
  body {
    background-image: url('$fileName');
    background-size: cover;
    background-attachment: fixed;
    background-position: center;
  }
</style>
        """.trimIndent()
        // Insert style tag in head or at beginning
        val headEnd = updatedContent.indexOf("</head>", ignoreCase = true)
        if (headEnd >= 0) {
          updatedContent = updatedContent.substring(0, headEnd) + styleTag + updatedContent.substring(headEnd)
        } else {
          updatedContent = styleTag + updatedContent
        }
        // Add remaining images normally
        images.drop(1).forEach { (fileName, _, suggestion) ->
          updatedContent = insertSingleImage(updatedContent, fileName, suggestion, isMarkdown)
        }
        return updatedContent
      }
    }
    if (isGallery) {
      // Create a gallery section
      val galleryHtml = buildGallerySection(images, isMarkdown)
      // Append gallery at the end
      updatedContent += "\n\n$galleryHtml\n\n"
      return updatedContent
    }

    images.forEach { (fileName, _, suggestion) ->
      updatedContent = insertSingleImage(updatedContent, fileName, suggestion, isMarkdown, isSidebar)
    }

    return updatedContent
  }

  private fun insertSingleImage(
    content: String,
    fileName: String,
    suggestion: ImageSuggestion,
    isMarkdown: Boolean,
    isSidebar: Boolean = false
  ): String {
    val insertionPoint = suggestion.insertionPoint
    val caption = suggestion.caption
    // Create image reference based on format and mode
    val imageReference = when {
      isSidebar && !isMarkdown -> {
        "\n\n<aside style=\"float: right; margin: 1em; max-width: 300px;\">\n  <img src=\"$fileName\" alt=\"${
          caption.replace(
            "\"",
            "\\\""
          )
        }\" style=\"width: 100%;\" />\n  <p style=\"font-size: 0.9em; font-style: italic;\">$caption</p>\n</aside>\n\n"
      }

      isMarkdown -> {
        "\n\n![${caption}]($fileName)\n\n"
      }

      else -> {
        "\n\n<figure>\n  <img src=\"$fileName\" alt=\"${caption.replace("\"", "\\\"")}\" />\n  <figcaption>$caption</figcaption>\n</figure>\n\n"
      }
    }
    // Try to find insertion point
    val insertionIndex = findInsertionPoint(content, insertionPoint)
    return if (insertionIndex >= 0) {
      val result = content.substring(0, insertionIndex) +
          imageReference +
          content.substring(insertionIndex)
      log.debug("Inserted image reference for $fileName at position $insertionIndex")
      result
    } else {
      log.warn("Could not find insertion point for $fileName: $insertionPoint")
      // Append at the end as fallback
      content + imageReference
    }
  }

  private fun buildGallerySection(
    images: List<Triple<String, String, ImageSuggestion>>,
    isMarkdown: Boolean
  ): String {
    return if (isMarkdown) {
      buildString {
        appendLine("## Image Gallery")
        appendLine()
        images.forEach { (fileName, _, suggestion) ->
          appendLine("### ${suggestion.caption}")
          appendLine()
          appendLine("![${suggestion.caption}]($fileName)")
          appendLine()
        }
      }
    } else {
      buildString {
        appendLine("<section class=\"image-gallery\">")
        appendLine("  <h2>Image Gallery</h2>")
        appendLine("  <div style=\"display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1em;\">")
        images.forEach { (fileName, _, suggestion) ->
          appendLine("    <figure>")
          appendLine("      <img src=\"$fileName\" alt=\"${suggestion.caption.replace("\"", "\\\"")}\" style=\"width: 100%; height: auto;\" />")
          appendLine("      <figcaption>${suggestion.caption}</figcaption>")
          appendLine("    </figure>")
        }
        appendLine("  </div>")
        appendLine("</section>")
      }
    }
  }

  private fun findInsertionPoint(content: String, insertionPoint: String): Int {
    // Try exact match first
    var index = content.indexOf(insertionPoint)
    if (index >= 0) {
      // Find end of line to insert after
      val lineEnd = content.indexOf('\n', index)
      return if (lineEnd >= 0) lineEnd + 1 else content.length
    }

    // Try case-insensitive match
    index = content.indexOf(insertionPoint, ignoreCase = true)
    if (index >= 0) {
      val lineEnd = content.indexOf('\n', index)
      return if (lineEnd >= 0) lineEnd + 1 else content.length
    }

    // Try partial match (first 50 characters)
    val searchTerm = insertionPoint.take(50)
    index = content.indexOf(searchTerm, ignoreCase = true)
    if (index >= 0) {
      val lineEnd = content.indexOf('\n', index)
      return if (lineEnd >= 0) lineEnd + 1 else content.length
    }

    return -1
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(IllustrateDocumentTask::class.java)

    val IllustrateDocument = TaskType(
      "IllustrateDocument",
      IllustrateDocumentTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Analyze a document and generate images to enhance its content",
      """
            Intelligently analyzes document content and generates contextually appropriate images.
        <ul>
        <li>Analyzes document structure to identify optimal image locations</li>
        <li>Generates images that enhance understanding of complex concepts</li>
        <li>Saves images with descriptive names in the document's folder</li>
        <li>Automatically inserts image references at appropriate locations</li>
            <li>Supports both Markdown and HTML formats</li>
        <li>Creates diagrams, illustrations, and visual aids</li>
        <li>Provides meaningful captions and alt text</li>
        <li>Configurable image count and format</li>
            </ul>
            """
    )
  }
}