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
import java.io.File

class GeneratePresentationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GeneratePresentationTaskExecutionConfigData?
) : AbstractFileTask<GeneratePresentationTask.GeneratePresentationTaskExecutionConfigData>(orchestrationConfig, planTask) {

    class GeneratePresentationTaskExecutionConfigData(
        @Description("The HTML presentation file to be created (relative path, must end with .html)")
        files: List<String>? = null,
        @Description("Additional files for context (e.g., existing presentations, content sources)")
        related_files: List<String>? = null,
        @Description("Detailed description of the presentation including: topic, target audience, key points, desired style, and number of slides")
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
GeneratePresentation - Create a complete Reveal.js presentation with narration
  ** Specify the HTML file path in the files array (must end with .html)
  ** Provide a detailed description including:
     - Presentation topic and objectives
     - Target audience
     - Key points to cover
     - Desired visual style and tone
     - Approximate number of slides
  ** The generated presentation will include:
     - Complete Reveal.js HTML structure
     - Styled slides with content
     - Speaker notes for each slide
     - Optional audio narration support
     - Navigation and progress indicators
  ** Related files can include existing presentations or source materials
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
        val presentationFiles = executionConfig?.files ?: emptyList()
        if (presentationFiles.isEmpty()) {
            resultFn("CONFIGURATION ERROR: No presentation file specified")
            return
        }

        val presentationFile = presentationFiles.first()
        if (!presentationFile.endsWith(".html", ignoreCase = true)) {
            resultFn("CONFIGURATION ERROR: File must have .html extension: $presentationFile")
            return
        }

        val newTask = task.ui.newTask(false)
        val toInput = { it: String -> listOf(it) }
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(MarkdownUtil.renderMarkdown("## Creating Presentation: `$presentationFile`", ui = ui))

        val contextFiles = getInputFileCode()
        val priorCode = getPriorCode(agent.executionState)

        val prompt = """
You are an expert presentation designer tasked with creating a complete Reveal.js presentation.

## Requirements:
${executionConfig?.task_description ?: "Create a presentation as specified"}

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Instructions:
1. Create a complete HTML5 document using Reveal.js framework
2. Structure the presentation with clear sections and slides
3. Include engaging content with appropriate headings and bullet points
4. Add speaker notes in <aside class="notes"> tags for each slide
5. Use appropriate Reveal.js features (fragments, transitions, etc.)
6. Include proper meta tags and title
7. Ensure responsive design and accessibility
8. Add visual styling using CSS (embedded or linked)
9. Include navigation controls and progress indicators
10. Make the presentation visually appealing and professional

## Reveal.js Structure:

Generate the complete presentation HTML now:
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
            resultFn("ERROR: Failed to generate valid presentation HTML")
            return
        }

        val outputPath = root.resolve(presentationFile)

        newTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |## Generated Presentation Preview
                |
                |File: `$presentationFile`
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

            // Copy support files if they don't exist
            copySupportFiles(outputPath.toFile().parentFile)

            newTask.add(
                MarkdownUtil.renderMarkdown(
                    "Presentation automatically written to `$presentationFile`",
                    ui = ui
                )
            )
            val htmlLink = ui.hrefLink("View Presentation", "file:///$outputPath") { }
            newTask.complete(
                """
                |<div>
                |Presentation created: $htmlLink
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

                            // Copy support files if they don't exist
                            copySupportFiles(outputPath.toFile().parentFile)

                            val htmlLink = ui.hrefLink("View Presentation", "file:///$outputPath") { }
                            newTask.complete(
                                """
                                |<div>
                                |Presentation created: $htmlLink
                                |</div>
                                """.trimMargin()
                            )
                            resultFn(extractedHtml)
                        } catch (e: Exception) {
                            log.error("Error writing presentation file", e)
                            newTask.error(e)
                            resultFn("ERROR: ${e.message}")
                        }
                    },
                    ui = ui
                )
            )
        }
    }

    private fun copySupportFiles(targetDir: File) {
        val supportFiles = listOf(
            "presentation.css",
            "presentation.js",
            "reveal_init.js"
        )

        supportFiles.forEach { fileName ->
            val targetFile = File(targetDir, fileName)
            if (!targetFile.exists()) {
                try {
                    val resourceStream = javaClass.classLoader.getResourceAsStream("presentations/$fileName")
                    if (resourceStream != null) {
                        targetFile.writeBytes(resourceStream.readBytes())
                        log.info("Copied support file: $fileName")
                    } else {
                        log.warn("Support file not found in resources: $fileName")
                    }
                } catch (e: Exception) {
                    log.error("Error copying support file $fileName", e)
                }
            }
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
        private val log: Logger = LoggerFactory.getLogger(GeneratePresentationTask::class.java)
        val GeneratePresentation = TaskType(
            "GeneratePresentation",
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
                <li>Interactive approval or auto-apply mode</li>
                <li>Includes navigation and progress indicators</li>
                <li>Optional audio narration support</li>
              </ul>
            """
        )
    }
}