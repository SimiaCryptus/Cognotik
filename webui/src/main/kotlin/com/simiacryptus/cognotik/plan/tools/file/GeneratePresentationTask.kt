package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger

class GeneratePresentationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: GeneratePresentationTaskExecutionConfigData?
) : AbstractFileTask<GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData>(orchestrationConfig, planTask) {

  class GeneratePresentationTaskExecutionConfigData(
    @Description("The HTML presentation file to be created (relative path, must end with .html)")
    files: List<String>? = null,
    @Description("Additional files for context (e.g., existing presentations, reference materials)")
    related_files: List<String>? = null,
    @Description("Detailed description of the presentation including topic, key points, target audience, and desired style")
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : FileTaskExecutionConfig(
    task_type = GeneratePresentation.name,
    task_description = task_description,
    files = files,
    related_files = related_files,
    task_dependencies = task_dependencies,
    state = state
  )

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
     - Custom CSS file (presentation.css) for styling
     - Autoplay controls and voice selection UI
     - Proper accessibility features
  ** Related files can include reference materials or existing presentations
  ** Output will be presented for review before being written to disk
        """.trimIndent()
  }

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

    val newTask = task.ui.newTask(false)
    val toInput = { it: String -> listOf(it) }
    val ui = task.ui
    val api = orchestrationConfig.defaultChatter

    newTask.add(MarkdownUtil.renderMarkdown("## Creating Presentation: `$htmlFile`", ui = ui))

    val contextFiles = getInputFileCode()
    val priorCode = getPriorCode(agent.executionState)

    val chatAgent = ChatAgent(
      prompt = promptSegment(),
      model = api,
    )

    // Step 1: Generate slide content only
    val outlinePrompt = """
 You are an expert presentation designer tasked with creating a Reveal.js presentation.

 ## Requirements:
 ${executionConfig?.task_description ?: "Create a presentation as specified"}

 ## Context from Related Files:
 $contextFiles

 ## Previous Task Results:
 $priorCode

 ## Instructions:
1. Generate ONLY the slide content as a sequence of HTML <section> tags:
   - A compelling title slide
   - 5-10 content slides covering the main points
   - Logical flow and transitions between topics
2. Each <section> tag should contain:
   - A clear heading
   - 2-4 key points or visual elements
   - An <aside class="notes"> element with detailed speaker notes (2-3 sentences)
 3. Use appropriate Reveal.js features:
   - data-auto-animate for smooth transitions
   - Fragments for progressive disclosure
4. Include emojis or icons where appropriate for visual interest

## Output Format:
Provide ONLY the slide sections within a code block (no DOCTYPE, html, head, or body tags):
```html
<section>
    <h1>Title</h1>
    <p class="subtitle">Subtitle</p>
    <aside class="notes">
        Speaker notes for this slide go here.
    </aside>
</section>

<section>
    <h2>Slide Title</h2>
    <ul>
        <li class="fragment">Point 1</li>
        <li class="fragment">Point 2</li>
    </ul>
    <aside class="notes">
        Detailed speaker notes explaining the content.
    </aside>
</section>
```
        """.trimIndent()

    newTask.add(MarkdownUtil.renderMarkdown("### Step 1: Generating Presentation Structure", ui = ui))

    val slideContent = extractCodeFromResponse(chatAgent.answer(toInput(outlinePrompt)), "html")

    if (slideContent.isEmpty()) {
      resultFn("ERROR: Failed to generate presentation structure")
      return
    }
    // Extract title from first slide for the HTML template
    val titleMatch = "<h1>(.*?)</h1>".toRegex().find(slideContent)
    val presentationTitle = titleMatch?.groupValues?.get(1) ?: "Presentation"
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
$slideContent
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
```html
$slideContent
```

## Requirements:
${executionConfig?.task_description ?: "Create appropriate styling for the presentation"}

## Instructions:
1. Create CSS that enhances the Reveal.js black theme with custom styling
2. Style the control container (#controlsContainer) with:
   - Fixed positioning at the top
   - Professional appearance
   - Responsive design
3. Add custom styles for:
   - .subtitle class for subtitle text
   - .fade-in-text for animated text
   - .intro-points for bullet point lists
   - Custom slide transitions and animations
4. Ensure readability with appropriate:
   - Font sizes and weights
   - Color contrasts
   - Spacing and padding
5. Add hover effects for interactive elements
6. Include responsive design for mobile devices
7. Use CSS variables for easy theme customization

## Output Format:
Provide only the CSS code within a code block:
```css
/* Custom Presentation Styles */
```
        """.trimIndent()

    newTask.add(MarkdownUtil.renderMarkdown("### Step 2: Generating Custom CSS", ui = ui))

    val cssCode = extractCodeFromResponse(chatAgent.answer(toInput(cssPrompt)), "css")

    if (cssCode.isEmpty()) {
      resultFn("ERROR: Failed to generate CSS styling")
      return
    }

    newTask.add(MarkdownUtil.renderMarkdown("### Step 3: Generating Presentation JavaScript", ui = ui))
    filesToWrite.add(htmlFile to htmlStructure)
    filesToWrite.add("presentation.css" to (standardCss + "\n\n" + cssCode))


    // Display preview
    newTask.add(MarkdownUtil.renderMarkdown("### Generated Files Preview", ui = ui))
    filesToWrite.forEach { (filename, content) ->
      newTask.add(MarkdownUtil.renderMarkdown("#### $filename", ui = ui))
      newTask.add(MarkdownUtil.renderMarkdown("```${getFileExtension(filename)}\n$content\n```", ui = ui))
    }

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
        task.add("""<a href="${task.linkTo(filename)}">${filename}</a> created""")
      }

      val summary = "Successfully wrote ${writtenFiles.joinToString(", ")}"
      newTask.complete(summary)
      resultFn(summary)
    } catch (e: Exception) {
      log.error("Error writing presentation files", e)
      newTask.error(e)
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
      val codeBlockRegex = "```$lang\\s*([\\s\\S]*?)```".toRegex()
      val match = codeBlockRegex.find(response)
      if (match != null) {
        return match.groupValues[1].trim()
      }
    }

    // Try generic code block
    val genericBlockRegex = "```\\s*([\\s\\S]*?)```".toRegex()
    val genericMatch = genericBlockRegex.find(response)
    if (genericMatch != null) {
      return genericMatch.groupValues[1].trim()
    }

    return ""
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
    val GeneratePresentation = TaskType(
      "GeneratePresentation",
      GeneratePresentationTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java
    )
  }
}