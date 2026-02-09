package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.AddApplyFileDiffLinks
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.nio.file.Path
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

class IllustrateDocumentTask(
    orchestrationConfig: OrchestrationConfig, planTask: IllustrateDocumentTaskExecutionConfigData?
) : AbstractFileTask<IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData>(orchestrationConfig, planTask) {

    data class ImageSuggestion(
        @Description("Descriptive name for the image file (without extension)") val imageName: String = "",
        @Description("Detailed prompt for generating the image") val imagePrompt: String = "",
        @Description("Location in document where image should be inserted (section heading or paragraph start)") val insertionPoint: String = "",
        @Description("Caption or alt text for the image") val caption: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (imageName.isBlank()) return "ImageSuggestion imageName cannot be blank"
            if (imagePrompt.isBlank()) return "ImageSuggestion imagePrompt cannot be blank"
            if (insertionPoint.isBlank()) return "ImageSuggestion insertionPoint cannot be blank"
            return null
        }
    }

    data class DocumentAnalysis(
        @Description("List of suggested images to enhance the document") val suggestions: List<ImageSuggestion> = emptyList()
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
        @Description("The document file to illustrate (must be .md or .html)") files: List<String> = emptyList(),
        @Description("Maximum number of images to generate (default: 5)") val maxImages: Int = 5,
        @Description("Image format to use (png or jpg, default: png)") val imageFormat: String = "png",
        @Description("Whether to automatically insert image references into the document") val autoInsert: Boolean = true,
        @Description("Additional instructions to append to image generation prompts (e.g., style preferences, constraints)") val imageInstructions: String? = null,
        @Description("Directive for the image composer on how to generate images (e.g., 'Generate a background wallpaper', 'Create hero images', 'Focus on technical diagrams')") val composerDirective: String? = null,
        @Description("Directive for the image integrator on how to insert images (e.g., 'Insert as page background', 'Place in sidebars', 'Create galleries')") val integratorDirective: String? = null,
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
          if (files!!.size > 1) {
                return "IllustrateDocumentTask can only process one file at a time"
            }
          val file = files!!.first()
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
  - Specify a markdown or HTML file to illustrate
  - Configure maximum number of images (default: 5)
  - Choose image format (png/jpg)
  - Analyzes document structure and content
  - Generates contextually appropriate images
  - Saves images with descriptive names in the same folder
  - Optionally inserts image references at appropriate locations
""".trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
      task.ui.pool.submit {
        val startTime = System.currentTimeMillis()
        val documentFile = executionConfig?.files?.firstOrNull()
        val transcript = task.newFileOutputStream(transcriptFile())

        try {
          if (documentFile == null) {
            val errorMsg = "CONFIGURATION ERROR: No document file specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return@submit
          }

          val documentPath = root.resolve(documentFile)
          if (!documentPath.toFile().exists()) {
            val errorMsg = "ERROR: Document file not found: $documentFile"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return@submit
          }

          val maxImages = executionConfig.maxImages.coerceIn(1, 20)
          val imageFormat = executionConfig.imageFormat.lowercase()
          val autoInsert = executionConfig.autoInsert

          log.info("Starting IllustrateDocumentTask for: $documentFile (maxImages=$maxImages, format=$imageFormat, autoInsert=$autoInsert)")
          transcript?.write("## Illustrating Document: $documentFile\n".toByteArray())

          val ui = task.ui
          val tabs = TabbedDisplay(task)

          // Read document content
          val documentContent = documentPath.toFile().readText()
          val isMarkdown = documentFile.endsWith(".md", ignoreCase = true)

          transcript?.write(
            """
                    <details>
                    <summary>Raw Document Content</summary>
                    
                    ```
                    $documentContent
                    ```
                    </details>
                """.trimIndent().toByteArray()
          )

          val overviewTask = tabs.newTask("Overview")
          overviewTask.header("Illustrating Document: $documentFile", level = 2)
          overviewTask.add(
            """
                    * **Format:** ${if (isMarkdown) "Markdown" else "HTML"}
                    * **Max Images:** $maxImages
                    * **Image Format:** $imageFormat
                """.trimIndent().renderMarkdown()
          )

          if (!executionConfig.composerDirective.isNullOrBlank()) {
            overviewTask.add("**Composer Directive:** ${executionConfig.composerDirective}".renderMarkdown())
          }
          if (!executionConfig.integratorDirective.isNullOrBlank()) {
            overviewTask.add("**Integrator Directive:** ${executionConfig.integratorDirective}".renderMarkdown())
          }

          // Step 1: Analyze document and suggest images
          log.info("Analyzing document to suggest images for $documentFile")
          val analysisTask = tabs.newTask("Analysis")
          analysisTask.header("🔍 Analyzing Document", level = 3)
          analysisTask.add("Identifying sections that would benefit from visual enhancement...".renderMarkdown())

          val analysisPrompt = buildAnalysisPrompt(
            documentContent, maxImages, isMarkdown, executionConfig.composerDirective
          )

          val api = defaultSmart ?: return@submit
          val parsingChatter = defaultFast.getChildClient(analysisTask)
          val defaultChatter = api.getChildClient(analysisTask)

          val analysisAgent = ParsedAgent(
            resultClass = DocumentAnalysis::class.java,
            prompt = analysisPrompt,
            model = defaultChatter,
            temperature = 0.5,
            parsingChatter = parsingChatter
          )

          val analysis = analysisAgent.answer(listOf(analysisPrompt))
          val suggestions = analysis.obj.suggestions.take(maxImages)

          log.info("Generated ${suggestions.size} image suggestions for $documentFile")
          transcript?.write(
            """
                    <details>
                    <summary>Image Suggestions Analysis</summary>
                    
                    ```json
                    ${JsonUtil.toJson(analysis.obj)}
                    ```
                    </details>
                """.trimIndent().toByteArray()
          )

          analysisTask.add("✅ Identified **${suggestions.size}** opportunities for images".renderMarkdown())

          // Display suggestions
          analysisTask.header("📋 Planned Images", level = 3)
          suggestions.forEachIndexed { index, suggestion ->
            analysisTask.add(
              """
                        ### ${index + 1}. ${suggestion.imageName}
                        * **Location:** ${suggestion.insertionPoint}
                        * **Caption:** ${suggestion.caption}
                    """.trimIndent().renderMarkdown()
            )
          }

          if (!orchestrationConfig.autoFix) {
            val semaphore = Semaphore(0)
            analysisTask.header("✋ Approval Required", level = 3)
            analysisTask.add("Please review the planned images above.".renderMarkdown())
            analysisTask.add(ui.hrefLink("🚀 Proceed with Generation", "btn btn-primary") {
              semaphore.release()
            })
            semaphore.acquire()
            analysisTask.add("✅ **User Approved**. Starting generation...".renderMarkdown())
          }


          // Step 2: Generate images
          log.info("Generating ${suggestions.size} images for $documentFile")
          val generationTask = tabs.newTask("Generation")
          generationTask.header("🎨 Generating Images", level = 3)

          val imageAgent = ImageProcessingAgent(
            prompt = "Transform the user request into an image that enhances document content",
            name = "DocumentIllustrator",
            model = orchestrationConfig.defaultImage,
          )

          val generatedImages = mutableListOf<Triple<String, String, ImageSuggestion>>()
          val documentFolder = documentPath.parent

          suggestions.forEachIndexed { index, suggestion ->
            try {
              generationTask.header("Generating: ${suggestion.imageName}", level = 4)

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

              val sanitizedName =
                suggestion.imageName.replace(Regex("[^a-zA-Z0-9_-]"), "_").replace(Regex("_+"), "_").trim('_')
              val imageFileName = "${sanitizedName}.$imageFormat"
              val imagePath = documentFolder.resolve(imageFileName)

              ImageIO.write(generatedImage, imageFormat, imagePath.toFile())
              log.info("Saved image: $imageFileName")

              val previewFile = task.resolveUserFile(imageFileName)
              ImageIO.write(generatedImage, imageFormat, previewFile!!)
              val previewLink = task.linkTo(imageFileName)
              generationTask.add("""<a href="$previewLink" target="_blank"><img src="$previewLink" style="max-width: 400px; border-radius: 4px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);" /></a>""")
              generationTask.add("✅ Saved as ` $imageFileName `".renderMarkdown())

              generatedImages.add(Triple(imageFileName, imagePath.toString(), suggestion))


            } catch (e: Exception) {
              log.error("Failed to generate image: ${suggestion.imageName}", e)
              generationTask.add(
                "❌ Failed to generate: ${suggestion.imageName} - ${e.message}".renderMarkdown(),
                additionalClasses = "text-danger"
              )
            }
          }
          val integrationTask = tabs.newTask("Integration")
          integrationTask.header("📝 Generating Document Patches", level = 3)
          integrationTask.complete(
            generateImageInsertionPatches(
              documentContent,
              generatedImages,
              isMarkdown,
              executionConfig.integratorDirective,
              integrationTask,
              defaultChatter,
              documentFile
            ) ?: ""
          )
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

          log.info("IllustrateDocumentTask completed for $documentFile in ${totalTime}ms")
          task.safeComplete("Generated ${generatedImages.size} images in ${totalTime / 1000}s", log)
          resultFn(summary)

        } catch (e: Exception) {
          val duration = System.currentTimeMillis() - startTime
          log.error("IllustrateDocumentTask failed for $documentFile after ${duration}ms", e)
          task.error(e)
          transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
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
        } finally {
          transcript?.close()
        }


      }
    }

    private fun buildAnalysisPrompt(
        documentContent: String, maxImages: Int, isMarkdown: Boolean, composerDirective: String?
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
    * Subject matter and key elements
    * Visual style (diagram, illustration, photo-realistic, etc.)
    * Color scheme and mood
    * Specific details that match the document context
3. **insertionPoint**: Identify where to insert the image by providing:
    * The exact heading text, or
    * The first few words of the paragraph where it should appear
4. **caption**: Write a clear, informative caption or alt text

## Guidelines:
    * Prioritize sections with complex concepts that benefit from visualization
    * Consider diagrams for processes, workflows, and architectures
    * Suggest illustrations for abstract concepts
    * Ensure images complement rather than duplicate text content
    * Focus on high-impact locations that enhance understanding
    * Make image prompts specific and detailed for best results

Generate suggestions now.
""".trimIndent()
    }

    private fun generateImageInsertionPatches(
        documentContent: String,
        images: List<Triple<String, String, ImageSuggestion>>,
        isMarkdown: Boolean,
        integratorDirective: String?,
        task: SessionTask,
        chatChatter: ChatInterface,
        documentFile: String
    ): String? {
        val semaphore = Semaphore(0)
        var patchResult: String? = null
        try {
            val formatInfo = if (isMarkdown) "Markdown" else "HTML"
            val directiveSection = if (!integratorDirective.isNullOrBlank()) {
                """
## Integrator Directive:
${integratorDirective}
**Important:** Follow this directive when inserting images into the document.
""".trimIndent()
            } else {
                ""
            }
            val imageList = images.joinToString("\n") { (fileName, _, suggestion) ->
                """
* **File**: $fileName
**Caption**: ${suggestion.caption}
**Suggested Location**: ${suggestion.insertionPoint}
**Context**: ${suggestion.imagePrompt.take(200)}
""".trimIndent()
            }
            val patchPrompt = """
You are a document integration expert. Insert image references into this $formatInfo document using diff patches.
${directiveSection}
## Document Content:
```
${documentContent}
```
## Images to Insert:
 ${imageList}
**IMPORTANT**: All images are located in the SAME DIRECTORY as the document.
Use ONLY the filename (e.g., "image.png") without any path prefix.
Do NOT use paths like "/assets/images/" or any other directory structure.

 ## Your Task:
 Generate diff patches to insert image references at appropriate locations in the document.
For ${if (isMarkdown) "Markdown" else "HTML"} format:
${if (isMarkdown) "- Use EXACTLY: ![caption](filename) where filename is the exact filename provided" else "- Use EXACTLY: <img src=\"filename\" alt=\"caption\" /> where filename is the exact filename provided"}

**CRITICAL**: Use ONLY the exact filenames provided in the image list above. Do NOT add any path prefixes like "/assets/images/" or any other directory paths.
The images are saved in the same directory as the document, so use relative filenames only.

**WRONG**: ![caption](/assets/images/filename.png)
**CORRECT**: ![caption](filename.png)

* Include 2-3 lines of context before and after each insertion
* Place images where they enhance understanding
* Ensure proper spacing and formatting
* Consider document flow and readability
Response format:
Use ```diff code blocks with a header specifying the file path.
The diff format should use + for line additions, - for line deletions.
Include 2 lines of context before and after every change.
Example:
 ### $documentFile
 ```diff
 existing line 1
 existing line 2
+![Image Caption](exact_filename_from_list.png)

+
existing line 3
existing line 4
```
Generate the patches now.
""".trimIndent()
            val subTask = task.newTask().apply { add("Generating patches...") }
            subTask.ui.pool.submit {
                try {
                    val chatAgent = ChatAgent(
                        name = "DocumentImageIntegrator", prompt = patchPrompt, model = chatChatter, temperature = 0.3
                    )
                    val response = chatAgent.answer(listOf(patchPrompt))
                    log.debug("Patch generation response: $response")
                    if (orchestrationConfig.autoFix) {
                        subTask.complete(MarkdownUtil.renderMarkdown(response, ui = subTask.ui) {
                          AddApplyFileDiffLinks(
                            orchestrationConfig.processor
                          ).instrument(
                            socketManager = subTask.ui,
                            root = root,
                            response = it,
                            handle = { newCodeMap: Map<Path, String> ->
                              newCodeMap.forEach { (path, _) ->
                                log.info("Applied patch to: $path")
                              }
                              patchResult = "Patches applied successfully"
                            },
                            shouldAutoApply = { it: Path -> true },
                            model = chatChatter,
                            defaultFile = documentFile
                          ) + "\n\n## Auto-applied image insertion patches"
                        })
                        semaphore.release()
                    } else {
                        subTask.complete(MarkdownUtil.renderMarkdown(response, ui = subTask.ui) {
                          AddApplyFileDiffLinks(
                            processor = orchestrationConfig.processor
                          ).instrument(
                            socketManager = subTask.ui,
                            root = root,
                            response = it,
                            handle = { newCodeMap: Map<Path, String> ->
                              newCodeMap.forEach { (path, _) ->
                                log.info("Applied patch to: $path")
                              }
                              patchResult = "Patches applied successfully"
                            },
                            model = chatChatter,
                            defaultFile = documentFile
                          ) + acceptButtonFooter(subTask.ui) {
                                subTask.complete()
                                semaphore.release()
                            }
                        })
                    }
                } catch (e: Exception) {
                    log.error("Failed to generate or apply patches", e)
                    subTask.error(e)
                    semaphore.release()
                }
            }
// Wait for completion
            if (!semaphore.tryAcquire(5, java.util.concurrent.TimeUnit.MINUTES)) {
                log.warn("Patch generation timed out")
                return null
            }
            return patchResult
        } catch (e: Exception) {
            log.error("Error in patch generation process", e)
            return null
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(IllustrateDocumentTask::class.java)

        @JvmStatic val IllustrateDocument = TaskType(
          name = "IllustrateDocument",
          category = "Writing",
          taskClass = IllustrateDocumentTask::class.java,
          executionConfigClass = IllustrateDocumentTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Analyze a document and generate images to enhance its content",
          tooltipHtml = """
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
          """,
        )
    }
}