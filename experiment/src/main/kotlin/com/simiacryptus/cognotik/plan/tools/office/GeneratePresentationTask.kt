package com.simiacryptus.cognotik.plan.tools.office

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.AbstractFileTask
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger
import javax.imageio.ImageIO

private const val TT = """```"""

class GeneratePresentationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: GeneratePresentationTaskExecutionConfigData?
) : AbstractFileTask<GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData>(
  orchestrationConfig,
  planTask
) {

  class GeneratePresentationTaskExecutionConfigData(
    @Description("The HTML presentation file to be created (relative path, must end with .html)")
    files: List<String> = emptyList(),
    @Description("Additional files for context (e.g., existing presentations, reference materials)")
    related_files: List<String>? = null,
    @Description("Detailed description of the presentation including topic, key points, target audience, and desired style")
    task_description: String? = null,
    @Description("Whether to generate images for key slides")
    val generate_images: Boolean = false,
    @Description("Width of generated images in pixels")
    val image_width: Int = 1024,
    @Description("Height of generated images in pixels")
    val image_height: Int = 1024,
    @Description("Maximum number of images to generate (1-10)")
    val max_images: Int = 5,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : ValidatedObject, FileTaskExecutionConfig(
    task_type = GeneratePresentation.name,
    task_description = task_description,
    files = files,
    related_files = related_files,
    task_dependencies = task_dependencies,
    state = state
  ) {
    override fun validate(): String? {
      // Validate that at least one file is specified
      if (files.isNullOrEmpty()) {
        return "GeneratePresentationTask requires at least one file to be specified"
      }

      // Validate that the file has .html extension
      val htmlFile = files!!.first()
      if (!htmlFile.endsWith(".html", ignoreCase = true)) {
        return "GeneratePresentationTask file must have .html extension: $htmlFile"
      }
      if (image_width < 256 || image_width > 2048) {
        return "Image width must be between 256 and 2048, got: $image_width"
      }
      if (image_height < 256 || image_height > 2048) {
        return "Image height must be between 256 and 2048, got: $image_height"
      }
      if (max_images < 1 || max_images > 10) {
        return "Max images must be between 1 and 10, got: $max_images"
      }

      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
 GeneratePresentation - Create a Reveal.js presentation with custom styling
  ** Specify the HTML presentation file path in the files array (must end with .html)
  ** Provide a detailed description including:
     - Presentation topic and title
     - Key points and sections to cover
     - Target audience and tone (professional, casual, technical, etc.)
     - Number of slides desired
     - Any specific visual style preferences
  ** The generated presentation will include:
     - Complete HTML structure using Reveal.js framework
     - Multiple slides with proper structure and speaker notes
     - Support for vertical slide stacks (nested sections for topic depth)
     - Per-slide and global scale control for content density management
     - Background images with configurable opacity
     - Auto-animate transitions between related slides
     - Custom CSS file (presentation.css) for styling
     - Standard JavaScript for autoplay, voice selection, and per-slide scaling
     - Autoplay controls and voice selection UI
     - Proper accessibility features
     - Optional AI-generated images for key slides
  ** Related files can include reference materials or existing presentations
  ** Output will be presented for review before being written to disk
        """.trimIndent()
  }

  data class PresentationSlide(
    @Description("The title of the slide")
    val title: String = "",
    @Description("The HTML content of the slide (Reveal.js compatible)")
    val html_content: String = "",
    @Description("Detailed speaker notes for this slide")
    val speaker_notes: String = "",
    @Description("A prompt for an AI image generator to create a visual for this slide")
    val image_prompt: String? = null,
    @Description("Optional background image filename for this slide")
    val background_image: String? = null,
    @Description("Background image opacity (0.0 to 1.0, default 0.2)")
    val background_opacity: Double? = null,
    @Description("Per-slide scale override (e.g. 1.6 for dense content)")
    val scale: Double? = null,
    @Description("Whether this slide uses vertical sub-slides (nested sections)")
    val has_vertical_slides: Boolean = false,
    @Description("List of vertical sub-slides if has_vertical_slides is true")
    val vertical_slides: List<VerticalSlide>? = null,
    @Description("Whether to use data-auto-animate on this slide")
    val auto_animate: Boolean = false
  )

  data class VerticalSlide(
    @Description("The HTML content of the vertical sub-slide")
    val html_content: String = "",
    @Description("Speaker notes for this vertical sub-slide")
    val speaker_notes: String = "",
    @Description("Optional background image filename")
    val background_image: String? = null,
    @Description("Background image opacity (0.0 to 1.0)")
    val background_opacity: Double? = null,
    @Description("Whether to use data-auto-animate")
    val auto_animate: Boolean = false
  )

  data class PresentationStructure(
    val slides: List<PresentationSlide> = emptyList(),
    @Description("Global scale factor for the presentation (default 1.0, increase for denser content)")
    val global_scale: Double? = null
  )


  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val htmlFiles = executionConfig?.files ?: emptyList()
    if (htmlFiles.isEmpty()) {
      resultFn("CONFIGURATION ERROR: No presentation file specified")
      return
    }

    val htmlFile = htmlFiles.first()
    if (!htmlFile.endsWith(".html", ignoreCase = true)) {
      resultFn("CONFIGURATION ERROR: File must have .html extension: $htmlFile")
      return
    }


    val filesToWrite = mutableListOf<Pair<String, String>>()
    val standardCss = this::class.java.getResource("/presentations/presentation.css")?.readText() ?: ""

    val standardJs = this::class.java.getResource("/presentations/presentation.js")?.readText() ?: ""
    filesToWrite.add("presentation.js" to standardJs)

    val revealInitCode = this::class.java.getResource("/presentations/reveal_init.js")?.readText() ?: ""
    filesToWrite.add("reveal_init.js" to revealInitCode)

    val tabs = TabbedDisplay(task)
    val overviewTask = tabs.newTask("Overview")
    val toInput = { it: String -> listOf(it) }
    val ui = task.ui
    val api = defaultSmart

    overviewTask.header("Creating Presentation: $htmlFile", level = 2)

    val contextFiles = getInputFileCode()
    val priorCode = getPriorCode(agent.executionState)

    // Step 1: Generate slide content only
    val outlinePrompt = """
 You are an expert presentation designer tasked with creating a Reveal.js presentation.

## Standard CSS Already Included:
The following standard CSS is already included and should not be duplicated:
${TT}css
$standardCss
$TT

 ## Requirements:
 ${executionConfig?.task_description ?: "Create a presentation as specified"}

 ## Context from Related Files:
 $contextFiles

 ## Previous Task Results:
 $priorCode

 ## Instructions:
1. Generate the presentation as a structured JSON object with the following features:
   - A compelling title slide
   - 5-10 content slides covering the main points
   - Logical flow and transitions between topics
2. Each slide should contain:
   - A clear title
   - 2-4 key points or visual elements


   - Detailed speaker notes (2-3 sentences minimum)
3. Use appropriate Reveal.js features in html_content:
   - class="fragment" for progressive disclosure
   - Columns layout with class="columns" and class="column"
   - class="highlight" for emphasized text
   - Emojis or icons where appropriate for visual interest
4. Advanced slide features:
   - Set auto_animate: true for smooth transitions between related slides
   - Use has_vertical_slides: true with vertical_slides array to create slide stacks
     (the outer slide gets the title, each vertical sub-slide gets its own content and notes)
   - Set scale for slides with dense content (e.g. 1.5-1.9 for content-heavy slides)
   - Set background_image to reference generated image filenames for visual slides
   - Set background_opacity (0.1-0.3) when using background images to keep text readable
5. Set global_scale on the PresentationStructure if the overall presentation needs scaling
   (e.g. 1.5 for content-dense presentations)
6. Provide an image_prompt for slides where a background or illustrative image would enhance the content

## Vertical Slide Stacks:
For complex topics, use vertical slide stacks. The outer slide provides the title (h1),
and each vertical sub-slide contains detailed content. This creates a two-dimensional
navigation structure (left/right for topics, up/down for depth within a topic).

## Scale Guidelines:
- Default scale (1.0): Normal content density
- Scale 1.3-1.5: Slides with grids or multiple columns
- Scale 1.6-1.9: Very dense slides with many items
- Per-slide scale overrides the global scale for that slide only
        """.trimIndent()

    val slideAgent = ParsedAgent(
      resultClass = PresentationStructure::class.java,
      prompt = outlinePrompt,
      model = api,
      parsingChatter = defaultFast
    )



    overviewTask.header("Step 1: Generating Presentation Structure", level = 3)
    val presentationStructure = slideAgent.answer(listOf("Generate the presentation.")).obj

    if (presentationStructure.slides.isEmpty()) {
      resultFn("ERROR: Failed to generate presentation structure")
      return
    }

    // Step 1.5: Generate images for key slides if enabled
    val imageMap = mutableMapOf<Int, String>()
    if (executionConfig?.generate_images != false) {

      val imageTask = tabs.newTask("Images")
      imageTask.header("Generating Images for Key Slides", level = 3)
      imageMap.putAll(generateSlideImages(presentationStructure, task, orchestrationConfig, imageTask))
      imageTask.complete()
    }

    val presentationTitle = presentationStructure.slides.firstOrNull()?.title ?: "Presentation"
    // Inject images into slide content
    val enhancedSlideContent = buildSlideHtml(presentationStructure, imageMap)

    // Wrap slides in the HTML template
    val htmlStructure = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta content="width=device-width, initial-scale=1.0" name="viewport">
    <meta content="$presentationTitle" name="description">
    <title>$presentationTitle</title>
    <link href="https://cdnjs.cloudflare.com" rel="preconnect">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/reset.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/reveal.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/theme/black.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/highlight/monokai.min.css" rel="stylesheet">
    <link href="presentation.css" rel="stylesheet">
</head>
<body>
<div aria-label="Presentation controls" id="controlsContainer" role="toolbar">
    <button aria-label="Toggle autoplay" aria-pressed="false" id="autoplayButton">Autoplay: Off</button>
    <label class="sr-only" for="voiceSelect">Select voice</label>
    <select aria-label="Voice selection" id="voiceSelect"></select>
</div>
<div class="reveal">
    <div class="slides">
$enhancedSlideContent
    </div>
</div>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/reveal.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/notes/notes.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/markdown/markdown.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/highlight/highlight.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/reveal.js/4.5.0/plugin/zoom/zoom.min.js"></script>
<script src="presentation.js"></script>
<script src="reveal_init.js"></script>
</body>
</html>
    """.trimIndent()


// Step 2: Generate custom CSS
    val cssPrompt = """
Based on the following Reveal.js presentation HTML, generate custom CSS styling.

 ## Slide Content:
 ${TT}html
$enhancedSlideContent
$TT
## Standard CSS Already Included:
The following standard CSS is already included — DO NOT duplicate any of these styles:
${TT}css
$standardCss
$TT


## Requirements:
${executionConfig?.task_description ?: "Create appropriate styling for the presentation"}

## Instructions:
1. Create ONLY additional custom CSS that enhances the presentation
2. DO NOT duplicate any styles already present in the standard CSS above
3. The standard CSS already handles:
   - All --scale and per-slide scaling via CSS custom properties
   - Controls container, autoplay button, voice select styling
   - Reveal.js overrides for slides, headings, lists, columns, highlights
   - Fragment animations, title pulse, hover effects
   - Responsive breakpoints and accessibility (sr-only, focus-visible)
   - Inline style overrides for font-size, margin, padding, gap
4. Focus on adding ONLY custom styles specific to this presentation:
   - .subtitle class for subtitle text
   - .fade-in-text for animated text
   - .intro-points for bullet point lists
   - Slide-specific styling based on the actual content generated
   - Custom color schemes or gradients unique to this presentation
5. Keep the CSS minimal — the standard CSS handles most layout concerns
6. Use CSS variables defined in the standard CSS where applicable:
   --color-accent, --color-accent-glow, --color-text, --color-text-muted,
   --font-size-base, --font-size-sm, --spacing-sm, --spacing-md, etc.
7. Do NOT set --scale in the custom CSS (it's set in the HTML inline style)
8. Do NOT add per-presentation scale tuning comments (already in the HTML)

## Output Format:
Provide only the ADDITIONAL custom CSS code within a code block (no duplicates):
${TT}css
/* Custom Presentation Styles */
$TT
        """.trimIndent()

    val stylingTask = tabs.newTask("Styling")
    stylingTask.header("Generating Custom CSS", level = 3)
    val chatAgent = ChatAgent(prompt = cssPrompt, model = api)

    val cssCode = extractCodeFromResponse(chatAgent.answer(toInput(cssPrompt)), "css")

    if (cssCode.isEmpty()) {
      resultFn("ERROR: Failed to generate CSS styling")
      return
    }

    stylingTask.complete()

    overviewTask.header("Step 3: Finalizing Files", level = 3)
    filesToWrite.add(htmlFile to htmlStructure)
    filesToWrite.add("presentation.css" to (standardCss + "\n\n" + cssCode))
    val transcriptStream =
      task.newUserFileStream(transcriptFile("${presentationTitle.replace(Regex("[^a-zA-Z0-9]"), "_")}"))
    transcriptStream?.close()


    // Display preview
    val filesTask = tabs.newTask("Files")
    filesTask.header("Generated Files Preview", level = 3)
    filesToWrite.forEach { (filename, content) ->
      filesTask.header(filename, level = 4)
      val codeBlock = "$TT${getFileExtension(filename)}\n$content\n$TT"
      filesTask.expandable("View Content", MarkdownUtil.renderMarkdown(codeBlock, ui = ui))
    }
    filesTask.complete()

    try {
      val outputPath = root.resolve(htmlFile)
      outputPath.toFile().parentFile?.mkdirs()
      val writtenFiles = mutableListOf<String>()

      filesToWrite.forEach { (filename, content) ->
        val path = when (filename) {
          executionConfig?.files?.firstOrNull() -> outputPath
          else -> outputPath.resolveSibling(filename)
        }
        path.toFile().parentFile?.mkdirs()
        path.toFile().writeText(content)
        writtenFiles.add(filename)
        overviewTask.add("""<a href="${task.linkTo(filename)}">${filename}</a> created""")
      }

      val summary = "Successfully wrote ${writtenFiles.joinToString(", ")}"
      overviewTask.complete(summary)
      resultFn(summary)
    } catch (e: Exception) {
      log.error("Error writing presentation files", e)
      overviewTask.error(e)
      resultFn("ERROR: ${e.message}")
    }
  }

  private fun getFileExtension(filename: String): String {
    return when {
      filename.endsWith(".html") -> "html"
      filename.endsWith(".css") -> "css"
      filename.endsWith(".js") -> "javascript"
      else -> ""
    }
  }

  private fun extractCodeFromResponse(response: String, vararg languages: String): String {
    // Try to extract code from code blocks with specified languages
    for (lang in languages) {
      val codeBlockRegex = "$TT$lang\\s*([\\s\\S]*?)${TT}".toRegex()
      val match = codeBlockRegex.find(response)
      if (match != null) {
        return match.groupValues[1].trim()
      }
    }

    // Try generic code block
    val genericBlockRegex = "$TT\\s*([\\s\\S]*?)${TT}".toRegex()
    val genericMatch = genericBlockRegex.find(response)
    if (genericMatch != null) {
      return genericMatch.groupValues[1].trim()
    }

    return ""
  }

  private fun generateSlideImages(
    structure: PresentationStructure,
    task: SessionTask,
    orchestrationConfig: OrchestrationConfig,
    imageTask: SessionTask
  ): Map<Int, String> {
    val imageMap = mutableMapOf<Int, String>()
    try {
      val slides = structure.slides
      val maxImages = executionConfig?.max_images?.coerceIn(1, 10) ?: 3
      val slideIndices = selectSlidesForImages(slides.size, maxImages)
      imageTask.add("Generating images for ${slideIndices.size} slides (indices: ${slideIndices.joinToString(", ")})")
      slideIndices.forEachIndexed { idx, slideIndex ->
        if (slideIndex >= slides.size) return@forEachIndexed
        val slide = slides[slideIndex]
        val sectionContent = slide.html_content
        val heading = slide.title

        // Extract text content (remove HTML tags)
        val textContent = sectionContent
          .replace(Regex("<aside[^>]*>.*?</aside>", RegexOption.DOT_MATCHES_ALL), "")
          .replace(Regex("<[^>]+>"), " ")
          .replace(Regex("\\s+"), " ")
          .trim()
          .take(200)
        val imageFilename = "slide_${slideIndex + 1}_image.png"
        try {
          imageTask.add("Generating image ${idx + 1}/${slideIndices.size}: <b>$heading</b>")
          val imageAgent = ImageProcessingAgent(
            prompt = "Create a professional, visually appealing image for a presentation slide",
            model = orchestrationConfig.defaultImage,
            temperature = 0.7,
          )
          val useAsBackground = slide.background_image != null || slide.has_vertical_slides
          val imagePrompt = """
Create a professional presentation ${if (useAsBackground) "background" else "slide"} image for:
Title: $heading
Context: ${slide.image_prompt ?: ""}
Content: $textContent
Style: ${if (useAsBackground) "Dark, atmospheric, subtle background suitable for text overlay with low opacity" else "Clean, modern, professional presentation aesthetic"}
          """.trimIndent()
          val result = imageAgent.answer(listOf(ImageAndText(imagePrompt)))
          val image = result.image
          // Save image
          val imageFile = task.resolveUserFile(imageFilename)!!
          ImageIO.write(image, "png", imageFile)
          imageMap[slideIndex] = imageFilename
          imageTask.image(image!!)
          imageTask.add("✅ Generated image for slide ${slideIndex + 1}: <a href='${task.linkTo(imageFilename)}' target='_blank'>$imageFilename</a>")
          log.debug("Generated image for slide ${slideIndex + 1}: $imageFilename")
        } catch (e: Exception) {
          log.error("Failed to generate image for slide ${slideIndex + 1}", e)
          imageTask.add(
            "⚠️ Failed to generate image for slide ${slideIndex + 1}: ${e.message}",
            additionalClasses = "text-danger"
          )
        }
      }
    } catch (e: Exception) {
      log.error("Error during image generation", e)
      imageTask.add("⚠️ Image generation encountered errors: ${e.message}", additionalClasses = "text-danger")
    }
    return imageMap
  }

  private fun selectSlidesForImages(totalSlides: Int, maxImages: Int): List<Int> {
    if (totalSlides <= 1) return emptyList()
    // Skip title slide (index 0), select evenly distributed slides
    val availableSlides = totalSlides - 1
    val numImages = minOf(maxImages, availableSlides)
    if (numImages <= 0) return emptyList()
    val indices = mutableListOf<Int>()
    val step = availableSlides.toDouble() / numImages
    for (i in 0 until numImages) {
      val index = (1 + (i * step)).toInt().coerceIn(1, totalSlides - 1)
      if (!indices.contains(index)) {
        indices.add(index)
      }
    }
    return indices.sorted()
  }

  private fun buildSlideHtml(structure: PresentationStructure, imageMap: Map<Int, String>): String {
    val result = StringBuilder()
    structure.slides.forEachIndexed { index, slide ->

      // Build outer section attributes
      val outerAttrs = StringBuilder()
      slide.scale?.let { outerAttrs.append(" data-scale=\"$it\"") }

      if (slide.has_vertical_slides && !slide.vertical_slides.isNullOrEmpty()) {
        // Vertical stack: outer section wraps inner sections
        result.append("        <section${outerAttrs}>\n")
        result.append("            <h1>${slide.title}</h1>\n")

        slide.vertical_slides.forEachIndexed { vIndex, vSlide ->
          val innerAttrs = buildSectionAttributes(
            autoAnimate = vSlide.auto_animate,
            backgroundImage = vSlide.background_image ?: imageMap[index]?.takeIf { vIndex == 0 },
            backgroundOpacity = vSlide.background_opacity
          )
          result.append("            <section${innerAttrs}>\n")
          result.append("                ${vSlide.html_content}\n")
          if (vSlide.speaker_notes.isNotBlank()) {
            result.append("                <aside class=\"notes\">\n")
            result.append("                    ${vSlide.speaker_notes}\n")
            result.append("                </aside>\n")
          }
          result.append("            </section>\n")
        }

        // Also add speaker notes on the outer section if present
        if (slide.speaker_notes.isNotBlank()) {
          result.append("            <aside class=\"notes\">\n")
          result.append("                ${slide.speaker_notes}\n")
          result.append("            </aside>\n")
        }
        result.append("        </section>\n\n")
      } else {
        // Simple flat slide
        val sectionAttrs = buildSectionAttributes(
          autoAnimate = slide.auto_animate,
          backgroundImage = slide.background_image ?: imageMap[index],
          backgroundOpacity = slide.background_opacity
        )
        result.append("        <section${outerAttrs}${sectionAttrs}>\n")
        result.append("            <h1>${slide.title}</h1>\n")

        // Inline image (if no background image but image was generated)
        if (slide.background_image == null && imageMap.containsKey(index)) {
          val imageFilename = imageMap[index]!!
          result.append("            <div class=\"slide-image\">\n")
          result.append("                <img src=\"$imageFilename\" alt=\"Slide visual\" style=\"max-width: 80%; max-height: 400px; margin: 20px auto; display: block; border-radius: 8px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);\">\n")
          result.append("            </div>\n")
        }

        result.append("            ${slide.html_content}\n")

        if (slide.speaker_notes.isNotBlank()) {
          result.append("            <aside class=\"notes\">\n")
          result.append("                ${slide.speaker_notes}\n")
          result.append("            </aside>\n")
        }
        result.append("        </section>\n\n")
      }


    }
    return result.toString()
  }

  private fun buildSectionAttributes(
    autoAnimate: Boolean = false,
    backgroundImage: String? = null,
    backgroundOpacity: Double? = null
  ): String {
    val attrs = StringBuilder()
    if (autoAnimate) attrs.append(" data-auto-animate")
    backgroundImage?.let {
      attrs.append(" data-background-image=\"$it\"")
      attrs.append(" data-background-size=\"cover\"")
      attrs.append(" data-background-opacity=\"${backgroundOpacity ?: 0.2}\"")
    }
    return attrs.toString()
  }


  override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
    val acceptLink = ui.hrefLink("Accept and Write Files") {
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
    private val log: Logger = LoggerFactory.getLogger(GeneratePresentationTask::class.java)

    @JvmStatic
    val GeneratePresentation = TaskType(
      "GeneratePresentation",
      "Writing",
      GeneratePresentationTask::class.java,
      GeneratePresentationTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Create complete Reveal.js presentations with narration support",
      """
              Creates professional Reveal.js presentations with speaker notes.
              <ul>
                <li>Generates complete, self-contained HTML presentations</li>
                <li>Includes Reveal.js framework integration</li>
                <li>Adds speaker notes for each slide</li>
                <li>Supports custom styling and themes</li>
                <li>Optional AI-generated images for key slides</li>
                <li>Interactive approval or auto-apply mode</li>
                <li>Includes navigation and progress indicators</li>
                <li>Optional audio narration support</li>
              </ul>
            """,
    )
  }
}