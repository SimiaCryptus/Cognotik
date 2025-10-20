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
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : FileTaskExecutionConfig(
        task_type = WriteHtml.name,
        task_description = task_description,
        files = files,
        related_files = related_files,
        task_dependencies = task_dependencies,
        state = state
    )

    override fun promptSegment(): String {
        return """
WriteHtml - Create a complete HTML file with embedded CSS and JavaScript
  ** Specify the HTML file path in the files array (must end with .html)
  ** Provide a detailed description of the page requirements including:
     - Layout and structure
     - Styling requirements (colors, fonts, spacing, etc.)
     - Interactive functionality needed
     - Any specific HTML5 features to use
  ** The generated HTML will be a complete, self-contained document with:
     - Proper HTML5 structure (<!DOCTYPE html>, <html>, <head>, <body>)
     - Embedded CSS within <style> tags in the <head>
     - Embedded JavaScript within <script> tags (typically before </body>)
     - Responsive design considerations
     - Modern best practices
  ** Related files can include existing HTML templates or reference files
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
            resultFn("CONFIGURATION ERROR: No HTML file specified")
            return
        }

        val htmlFile = htmlFiles.first()
        if (!htmlFile.endsWith(".html", ignoreCase = true)) {
            resultFn("CONFIGURATION ERROR: File must have .html extension: $htmlFile")
            return
        }

        val newTask = task.ui.newTask(false)
        val toInput = { it: String -> listOf(it) }
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(MarkdownUtil.renderMarkdown("## Creating HTML File: `$htmlFile`", ui = ui))

        val contextFiles = getInputFileCode()
        val priorCode = getPriorCode(agent.executionState)

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
            prompt = promptSegment(),
            model = api,
        )

        newTask.add(MarkdownUtil.renderMarkdown("### Step 1: Generating HTML Structure", ui = ui))

      val htmlStructure = extractCodeFromResponse(chatAgent.answer(toInput(htmlPrompt)), "html")

        if (htmlStructure.isEmpty()) {
            resultFn("ERROR: Failed to generate HTML structure")
            return
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

        newTask.add(MarkdownUtil.renderMarkdown("### Step 2: Generating JavaScript", ui = ui))

      val jsCode = extractCodeFromResponse(chatAgent.answer(toInput(jsPrompt)), "javascript", "js")

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

        newTask.add(MarkdownUtil.renderMarkdown("### Step 3: Generating CSS", ui = ui))

      val cssCode = extractCodeFromResponse(chatAgent.answer(toInput(cssPrompt)), "css")
        // Step 4: Combine everything into a complete HTML file
        val completeHtml = combineHtmlComponents(htmlStructure, cssCode, jsCode)

        if (completeHtml.isEmpty()) {
            resultFn("ERROR: Failed to generate valid HTML content")
            return
        }

      task.add("""<a href="${task.linkTo(htmlFile)}">${htmlFile}</a> created""")
        val outputPath = root.resolve(htmlFile)


        if (orchestrationConfig.autoFix) {
            outputPath.toFile().parentFile?.mkdirs()
            outputPath.toFile().writeText(completeHtml)
            newTask.complete("Successfully wrote $htmlFile")
            resultFn("Successfully wrote $htmlFile")
        } else {
            newTask.add(
                MarkdownUtil.renderMarkdown(
                    acceptButtonFooter(ui) {
                        try {
                            outputPath.toFile().parentFile?.mkdirs()
                            outputPath.toFile().writeText(completeHtml)
                            newTask.complete("Successfully wrote $htmlFile")
                            resultFn("Successfully wrote $htmlFile")
                        } catch (e: Exception) {
                            log.error("Error writing HTML file", e)
                            newTask.error(e)
                            resultFn("ERROR: ${e.message}")
                        }
                    },
                    ui = ui
                )
            )
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

    private fun combineHtmlComponents(htmlStructure: String, cssCode: String, jsCode: String): String {
        // Parse the HTML structure and insert CSS and JavaScript
        val headEndIndex = htmlStructure.indexOf("</head>", ignoreCase = true)
        val bodyEndIndex = htmlStructure.indexOf("</body>", ignoreCase = true)

        if (headEndIndex == -1 || bodyEndIndex == -1) {
            log.error("Invalid HTML structure: missing </head> or </body> tags")
            return ""
        }

        val beforeHead = htmlStructure.substring(0, headEndIndex)
        val afterHeadBeforeBody = htmlStructure.substring(headEndIndex, bodyEndIndex)
        val afterBody = htmlStructure.substring(bodyEndIndex)

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
                <li>Interactive approval or auto-apply mode</li>
                <li>Proper HTML structure and formatting</li>
              </ul>
            """
        )
    }
}