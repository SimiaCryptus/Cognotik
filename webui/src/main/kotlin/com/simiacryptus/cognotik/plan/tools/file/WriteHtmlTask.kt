package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import javax.imageio.ImageIO

class WriteHtmlTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: WriteHtmlTaskExecutionConfigData?
) : AbstractFileTask<WriteHtmlTask.WriteHtmlTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class WriteHtmlTaskExecutionConfigData(
        @Description("The HTML file to be created (relative path, must end with .html)")
        files: List<String>? = null,
        @Description("Additional files for context (e.g., existing HTML templates, related files)")
        related_files: List<String>? = null,
        @Description("Detailed description of the HTML page to create, including layout, styling, and functionality requirements")
        task_description: String? = null,
        @Description("Whether to generate images for the HTML page")
        val generate_images: Boolean = false,
        @Description("Number of images to generate (valid range: 0-10)")
        var image_count: Int = 0,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : ValidatedObject, FileTaskExecutionConfig(
        task_type = WriteHtml.name,
        task_description = task_description,
        files = files,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override fun validate(): String? {
            // Validate that files list is not empty
            if (files.isNullOrEmpty()) {
                return "WriteHtmlTaskExecutionConfigData: files list cannot be null or empty"
            }

            // Validate that the file has .html extension
            val htmlFile = files.first()
            if (!htmlFile.endsWith(".html", ignoreCase = true)) {
                return "WriteHtmlTaskExecutionConfigData: file must have .html extension, got: $htmlFile"
            }

            // Validate image count
            if (image_count < 0 || image_count > 10) {
                image_count = image_count.coerceIn(0, 10)
            }

            // Call parent validation
            return super.validate()
        }
    }

    init {
        // Validate the configuration on initialization
        planTask?.validate()?.let { errorMessage ->
            throw ValidatedObject.ValidationError(errorMessage, planTask)
        }
    }

    override fun promptSegment(): String {
        return """
WriteHtml - Create a complete HTML file with embedded CSS and JavaScript
  ** Specify the HTML file path in the files array (must end with .html)
  ** Provide a detailed description of the page requirements including:
     - Layout and structure
     - Styling requirements (colors, fonts, spacing, etc.)
     - Interactive functionality needed
     - Any specific HTML5 features to use
     - Image requirements (if generate_images is enabled)
  ** The generated HTML will be a complete, self-contained document with:
     - Proper HTML5 structure (<!DOCTYPE html>, <html>, <head>, <body>)
     - Embedded CSS within <style> tags in the <head>
     - Embedded JavaScript within <script> tags (typically before </body>)
     - Responsive design considerations
     - Modern best practices
     - Generated images (if enabled) embedded as base64 or saved as separate files
  ** Related files can include existing HTML templates or reference files
  ** Output will be presented for review before being written to disk
        """
    }


    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        // Validate configuration before execution
        executionConfig?.validate()?.let { errorMessage ->
            resultFn("VALIDATION ERROR: $errorMessage")
            return
        }

        val htmlFiles = executionConfig?.files ?: emptyList()
        if (htmlFiles.isEmpty()) {
            resultFn("CONFIGURATION ERROR: No HTML file specified")
            return
        }

        val htmlFile = htmlFiles.first()
        if (!htmlFile.endsWith(".html", ignoreCase = true)) {
            resultFn("CONFIGURATION ERROR: File must have .html extension: $htmlFile")
            return
        }

        val newTask = task.newTask()
        val transcriptStream = newTask.transcript("html_generation_${htmlFile.substringBeforeLast(".")}")
        val transcriptWriter = transcriptStream?.bufferedWriter()

        val toInput = { it: String -> listOf(it) }
        val ui = task.ui
        val api = defaultSmart.getChildClient(task)

        newTask.header("Creating HTML File: $htmlFile", level = 2)

        val contextFiles = getInputFileCode()
        transcriptWriter?.write("# HTML Generation Transcript\n\n")
        transcriptWriter?.write("## Creating HTML File: `$htmlFile`\n\n")
        val priorCode = getPriorCode(agent.executionState)
        // Create directory for images if needed
        val imageDir = if (executionConfig?.generate_images == true && executionConfig.image_count > 0) {
            val dir = root.resolve(htmlFile).parent.resolve("images")
            dir.toFile().mkdirs()
            dir
        } else null

        // Step 1: Generate HTML structure with classes
        val htmlPrompt = """
You are an expert web developer tasked with creating a complete, self-contained HTML file.

## Requirements:
${executionConfig?.task_description ?: "Create an HTML page as specified"}

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Instructions:
1. Create a complete HTML5 document structure with proper semantic elements
2. Include appropriate meta tags (viewport, charset, etc.)
3. Add class names to elements that will need styling or JavaScript interaction
4. Use descriptive, semantic class names (e.g., "nav-menu", "hero-section", "card-container")
5. Include placeholder comments for where CSS and JavaScript will be added
6. Do NOT include any CSS or JavaScript yet - just the HTML structure with classes
7. Add comments to explain the purpose of major sections

## Output Format:
Provide the HTML structure within a code block:
```html
<!DOCTYPE html>
<html>
<head>
    <!-- CSS will be added here -->
</head>
<body>
    <!-- HTML structure with classes -->
</body>
<!-- JavaScript will be added here -->
</html>
```
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = htmlPrompt,
            model = api,
        )

        newTask.header("Step 1: Generating HTML Structure", level = 3)
        transcriptWriter?.write("### Step 1: Generating HTML Structure\n\n")
        transcriptWriter?.write("**Prompt:**\n```\n$htmlPrompt\n```\n\n")

        val htmlResponse = chatAgent.answer(listOf("Generate the HTML structure as per the requirements."))
        transcriptWriter?.write("**Response:**\n$htmlResponse\n\n")

        val htmlStructure = extractCodeFromResponse(htmlResponse, "html")

        if (htmlStructure.isEmpty()) {
            transcriptWriter?.close()
            resultFn("ERROR: Failed to generate HTML structure")
            return
        }
        // Step 1.5: Generate images if enabled
        val generatedImages = mutableListOf<Pair<String, String>>() // filename to description
        if (executionConfig?.generate_images == true && executionConfig.image_count > 0 && imageDir != null) {
            newTask.header("Step 1.5: Generating Images", level = 3)
            transcriptWriter?.write("### Step 1.5: Generating Images\n\n")
            val imagePrompt = """
Based on the following HTML page description and structure, identify ${executionConfig.image_count} key images that should be generated.
## Page Description:
${executionConfig.task_description}
## HTML Structure:
```html
$htmlStructure
```
For each image, provide:
1. A descriptive filename ending in .png (e.g., "hero-banner.png", "product-showcase.png")
2. A detailed visual description for image generation (be specific about style, colors, composition)

Note: All images will be generated as PNG files by an AI image model.

Format your response as:
IMAGE: filename.png
DESCRIPTION: detailed visual description
IMAGE: another-image.png
DESCRIPTION: another detailed description
      """.trimIndent()
            transcriptWriter?.write("**Prompt:**\n```\n$imagePrompt\n```\n\n")
            val imageSpecResponse = chatAgent.answer(toInput(imagePrompt))
            transcriptWriter?.write("**Response:**\n$imageSpecResponse\n\n")
            // Parse image specifications
            val imageSpecs = parseImageSpecs(imageSpecResponse)
            val imageChat = orchestrationConfig.defaultImage.getChildClient(task)
            // Generate each image
            imageSpecs.take(executionConfig.image_count).forEach { (filename, description) ->
                val filename = filename
                try {
                    newTask.add("Generating image: <b>$filename</b>...", additionalClasses = "text-info")
                    val imageAgent = ImageProcessingAgent(
                        prompt = "Create a high-quality image for a web page based on the description",
                        model = imageChat,
                        temperature = 0.7,
                    )
                    val result = imageAgent.answer(
                        listOf(
                            ImageAndText(
                                """
 Create an image for a web page with the following description:
 $description
Output format: PNG image
 Style: Modern, professional, web-optimized
          """
                            )
                        )
                    )
                    val image = result.image
                    val imageFile = task.resolveUserFile(filename)
                    ImageIO.write(image, "png", imageFile)
                    generatedImages.add(filename to description)
                    val imageLink = task.linkTo(filename)
                    val markdown = "✅ Generated: [$filename]($imageLink)"
                    newTask.add(MarkdownUtil.renderMarkdown(markdown, ui = ui))
                    newTask.image(image!!)

                    transcriptWriter?.write("**Generated Image:** $filename\n")
                    transcriptWriter?.write("**Description:** $description\n")
                    transcriptWriter?.write("**Prompt Used:** ${result.text}\n\n$markdown\n\n".transcriptFilter())
                    log.debug("Generated image: $filename")
                } catch (e: Exception) {
                    log.error("Failed to generate image: $filename", e)
                    newTask.error(e)
                    transcriptWriter?.write("**Error generating $filename:** ${e.message}\n\n")
                }
            }
        }

        // Step 2: Generate JavaScript
        val jsPrompt = """
Based on the following HTML structure, generate the JavaScript code needed for interactivity.

## HTML Structure:
```html
$htmlStructure
```

## Requirements:
${executionConfig?.task_description ?: "Add appropriate JavaScript functionality"}

## Instructions:
1. Generate JavaScript that adds interactivity to the HTML elements
2. Use modern JavaScript (ES6+) features
3. Add event listeners for user interactions
4. Include any necessary DOM manipulation
5. Add comments to explain the functionality
6. Ensure the code is efficient and follows best practices

## Output Format:
Provide only the JavaScript code within a code block:
```javascript
// JavaScript code here
```
        """.trimIndent()

        newTask.header("Step 2: Generating JavaScript", level = 3)
        transcriptWriter?.write("### Step 2: Generating JavaScript\n\n")
        transcriptWriter?.write("**Prompt:**\n```\n$jsPrompt\n```\n\n")

        val jsResponse = chatAgent.answer(toInput(jsPrompt))
        transcriptWriter?.write("**Response:**\n$jsResponse\n\n")
        val jsCode = extractCodeFromResponse(jsResponse, "javascript", "js")

        // Step 3: Generate CSS
        val cssPrompt = """
Based on the following HTML structure, generate the CSS styling.

## HTML Structure:
```html
$htmlStructure
```

## Requirements:
${executionConfig?.task_description ?: "Create appropriate styling"}

## Instructions:
1. Generate CSS that styles all the HTML elements
2. Create a visually appealing, modern design
3. Ensure responsive design (mobile-first approach)
4. Use CSS Grid and/or Flexbox for layouts
5. Include hover effects and transitions where appropriate
6. Use a consistent color scheme and typography
7. Add comments to organize the CSS sections
8. Follow CSS best practices and naming conventions

## Output Format:
Provide only the CSS code within a code block:
```css
/* CSS code here */
```
        """.trimIndent()

        newTask.header("Step 3: Generating CSS", level = 3)

        transcriptWriter?.write("### Step 3: Generating CSS\n\n")
        transcriptWriter?.write("**Prompt:**\n```\n$cssPrompt\n```\n\n")

        val cssResponse = chatAgent.answer(toInput(cssPrompt))
        transcriptWriter?.write("**Response:**\n$cssResponse\n\n")
        val cssCode = extractCodeFromResponse(cssResponse, "css")

        // Step 4: Combine everything into a complete HTML file
        val htmlWithImages =
            insertImageReferences(htmlStructure, generatedImages, chatAgent, toInput, transcriptWriter, newTask, ui)
        val completeHtml = combineHtmlComponents(htmlWithImages, cssCode, jsCode, generatedImages)

        if (completeHtml.isEmpty()) {
            transcriptWriter?.close()
            resultFn("ERROR: Failed to generate valid HTML content")
            return
        }

        task.add("""<a href="${task.linkTo(htmlFile)}">${htmlFile}</a> created""")
        val outputPath = root.resolve(htmlFile)
        transcriptWriter?.write("### Step 4: Final HTML Output\n\n")
        transcriptWriter?.write("```html\n$completeHtml\n```\n\n")


        outputPath.toFile().parentFile?.mkdirs()
        outputPath.toFile().writeText(completeHtml)
        transcriptWriter?.write("**Result:** Successfully wrote $htmlFile (auto-applied)\n")
        transcriptWriter?.close()
        newTask.complete("Successfully wrote $htmlFile")
        resultFn("Successfully wrote $htmlFile")
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

    private fun combineHtmlComponents(
        htmlStructure: String,
        cssCode: String,
        jsCode: String,
        generatedImages: List<Pair<String, String>> = emptyList()
    ): String {
        // Parse the HTML structure and insert CSS and JavaScript
        val headEndIndex = htmlStructure.indexOf("</head>", ignoreCase = true)
        val bodyEndIndex = htmlStructure.indexOf("</body>", ignoreCase = true)

        if (headEndIndex == -1 || bodyEndIndex == -1) {
            log.error("Invalid HTML structure: missing </head> or </body> tags")
            return ""
        }

        val beforeHead = htmlStructure.substring(0, headEndIndex)
        var afterHeadBeforeBody = htmlStructure.substring(headEndIndex, bodyEndIndex)
        val afterBody = htmlStructure.substring(bodyEndIndex)
        // Insert image references if images were generated
        if (generatedImages.isNotEmpty()) {
            val imageComment = """
    <!-- Generated Images:
${generatedImages.joinToString("\n") { (filename, desc) -> "         - $filename: $desc" }}
    -->
""".trimIndent()
            afterHeadBeforeBody = afterHeadBeforeBody.replace("</head>", "$imageComment\n</head>")
        }

        return buildString {
            append(beforeHead)
            if (cssCode.isNotEmpty()) {
                append("\n    <style>\n")
                append(cssCode.prependIndent("        "))
                append("\n    </style>\n")
            }
            append(afterHeadBeforeBody)
            if (jsCode.isNotEmpty()) {
                append("\n    <script>\n")
                append(jsCode.prependIndent("        "))
                append("\n    </script>\n")
            }
            append(afterBody)
        }
    }

    private fun parseImageSpecs(response: String): List<Pair<String, String>> {
        val specs = mutableListOf<Pair<String, String>>()
        val lines = response.lines()
        var currentFilename: String? = null
        var currentDescription: String? = null
        for (line in lines) {
            when {
                line.startsWith("IMAGE:", ignoreCase = true) -> {
                    // Save previous spec if exists
                    if (currentFilename != null && currentDescription != null) {
                        specs.add(currentFilename to currentDescription)
                    }
                    currentFilename = line.substringAfter(":", "").trim()
                    currentDescription = null
                }

                line.startsWith("DESCRIPTION:", ignoreCase = true) -> {
                    currentDescription = line.substringAfter(":", "").trim()
                }

                currentDescription != null && line.isNotBlank() -> {
                    // Continue multi-line description
                    currentDescription += " " + line.trim()
                }
            }
        }
        // Save last spec
        if (currentFilename != null && currentDescription != null) {
            specs.add(currentFilename to currentDescription)
        }
        return specs
    }

    private fun insertImageReferences(
        htmlStructure: String,
        generatedImages: List<Pair<String, String>>,
        chatAgent: ChatAgent,
        toInput: (String) -> List<String>,
        transcriptWriter: java.io.BufferedWriter?,
        newTask: SessionTask,
        ui: SocketManager
    ): String {
        if (generatedImages.isEmpty()) {
            return htmlStructure
        }
        newTask.header("Step 3.5: Inserting Image References", level = 3)
        transcriptWriter?.write("### Step 3.5: Inserting Image References\n\n")
        val imageList = generatedImages.joinToString("\n") { (filename, description) ->
            "- $filename: $description"
        }
        val imageInsertPrompt = """
You need to insert image references into the HTML structure.
## Current HTML Structure:
```html
$htmlStructure
```
## Generated Images:
$imageList
## Instructions:
1. Insert <img> tags at appropriate locations in the HTML where these images should appear
2. Use the given PNG filename (e.g., "filename.png") for the src attribute
3. Add appropriate alt text based on the image description
4. Add appropriate class names for styling
5. Consider the semantic meaning of where each image should go (hero sections, content areas, etc.)
6. Maintain the existing HTML structure and class names
7. Do NOT add any CSS or JavaScript - just insert the <img> tags
## Output Format:
Provide the complete updated HTML structure within a code block:
```html
<!DOCTYPE html>
...
```
        """.trimIndent()
        transcriptWriter?.write("**Prompt:**\n```\n$imageInsertPrompt\n```\n\n")
        val imageInsertResponse = chatAgent.answer(toInput(imageInsertPrompt))
        transcriptWriter?.write("**Response:**\n$imageInsertResponse\n\n")
        val updatedHtml = extractCodeFromResponse(imageInsertResponse, "html")
        return if (updatedHtml.isNotEmpty()) {
            newTask.add("✅ Successfully inserted ${generatedImages.size} image reference(s)")
            updatedHtml
        } else {
            log.warn("Failed to insert image references, using original HTML structure")
            newTask.add(
                "⚠️ Failed to insert image references, using original structure",
                additionalClasses = "text-warning"
            )
            htmlStructure
        }
    }


    override fun acceptButtonFooter(ui: SocketManager, fn: () -> Unit): String {
        val acceptLink = ui.hrefLink("Accept and Write File") {
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
        private val log: Logger = LoggerFactory.getLogger(WriteHtmlTask::class.java)
        val WriteHtml = TaskType(
            "WriteHtml",
            "Writing",
            WriteHtmlTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Create complete HTML files with embedded CSS and JavaScript",
            """
              Creates standalone HTML files with embedded CSS and JavaScript.
              <ul>
                <li>Generates complete, self-contained HTML documents</li>
                <li>Embeds CSS styles within &lt;style&gt; tags</li>
                <li>Embeds JavaScript within &lt;script&gt; tags</li>
                <li>Supports modern HTML5 features</li>
                <li>Can generate images using AI image models</li>
                <li>Automatically creates image directory and references</li>
                <li>Interactive approval or auto-apply mode</li>
                <li>Proper HTML structure and formatting</li>
              </ul>
            """
        )
    }
}