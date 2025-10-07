package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
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
        val priorCode = getPriorCode(agent.executionState!!)

        val prompt = """
You are an expert web developer tasked with creating a complete, self-contained HTML file.

## Requirements:
${executionConfig?.task_description ?: "Create an HTML page as specified"}

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Instructions:
1. Create a complete HTML5 document with proper structure
2. Embed all CSS within <style> tags in the <head> section
3. Embed all JavaScript within <script> tags (preferably before </body>)
4. Use modern HTML5 semantic elements where appropriate
5. Ensure the page is responsive and follows best practices
6. Include appropriate meta tags (viewport, charset, etc.)
7. Add comments to explain complex sections
8. Make the page visually appealing and functional

## Output Format:
Provide the complete HTML file content within a code block:
<script>
    // JavaScript code here
</script>
Generate the complete HTML file now:
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = promptSegment(),
            model = api,
        )

        var answer: String? = null
        val htmlContent = Retryable(newTask) {
            answer = chatAgent.answer(toInput(prompt))
            answer
        }

        val extractedHtml = extractHtmlFromResponse(answer!!)

        if (extractedHtml.isEmpty()) {
            resultFn("ERROR: Failed to generate valid HTML content")
            return
        }

        val outputPath = root.resolve(htmlFile)

        newTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |## Generated HTML Preview
                |
                |File: `$htmlFile`
                |
                |```html
                |${extractedHtml.take(1000)}${if (extractedHtml.length > 1000) "\n... (truncated)" else ""}
                |```
                """.trimMargin(),
                ui = ui
            )
        )

        if (orchestrationConfig.autoFix) {
            outputPath.toFile().parentFile?.mkdirs()
            outputPath.toFile().writeText(extractedHtml)
            newTask.add(
                MarkdownUtil.renderMarkdown(
                    "HTML file automatically written to `$htmlFile`",
                    ui = ui
                )
            )
            val htmlLink = ui.hrefLink("View HTML", "file:///$outputPath") { }
            newTask.complete(
                """
                |<div>
                |HTML file created: $htmlLink
                |</div>
                """.trimMargin()
            )
            resultFn(extractedHtml)
        } else {
            newTask.add(
                MarkdownUtil.renderMarkdown(
                    acceptButtonFooter(ui) {
                        try {
                            outputPath.toFile().parentFile?.mkdirs()
                            outputPath.toFile().writeText(extractedHtml)
                            val htmlLink = ui.hrefLink("View HTML", "file:///$outputPath") { }
                            newTask.complete(
                                """
                                |<div>
                                |HTML file created: $htmlLink
                                |</div>
                                """.trimMargin()
                            )
                            resultFn(extractedHtml)
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

    private fun extractHtmlFromResponse(response: String): String {
        // Try to extract HTML from code blocks
        val htmlBlockRegex = "```html\\s*([\\s\\S]*?)```".toRegex()
        val match = htmlBlockRegex.find(response)

        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // If no code block, check if the response itself is HTML
            val trimmed = response.trim()
            if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
                trimmed.startsWith("<html", ignoreCase = true)) {
                trimmed
            } else {
                ""
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(WriteHtmlTask::class.java)
        val WriteHtml = TaskType(
            "WriteHtml",
            WriteHtmlTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java
        )
    }
}