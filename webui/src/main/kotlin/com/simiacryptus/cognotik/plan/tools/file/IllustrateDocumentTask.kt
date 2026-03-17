package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.diff.PatchProcessors
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.ui.patch.DiffInstrumentor
import com.simiacryptus.cognotik.ui.patch.SessionRenderer
import com.simiacryptus.cognotik.util.*

import com.simiacryptus.cognotik.util.FileSelectionUtils.resolveToRelativePath
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
        @Description("Descriptive name for the image file (without extension), e.g. 'user_authentication_flow'")
        var image_name: String = "",
        @Description("Detailed prompt for generating the image, including subject matter, visual style, color scheme, and specific details")
        var image_prompt: String = "",
        @Description("Location in document where image should be inserted - the exact heading text or first few words of the target paragraph")
        var insertion_point: String = "",
        @Description("Caption or alt text for the image that describes its content for accessibility")
        var caption: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            image_name = image_name.trim()
            image_prompt = image_prompt.trim()
            insertion_point = insertion_point.trim()
            caption = caption.trim()
            if (image_name.isBlank()) return "ImageSuggestion image_name cannot be blank"
            if (image_prompt.isBlank()) return "ImageSuggestion image_prompt cannot be blank"
            if (insertion_point.isBlank()) return "ImageSuggestion insertion_point cannot be blank"
            return null
        }
    }

    data class DocumentAnalysis(
        @Description("List of suggested images to enhance the document, ordered by priority/importance")
        var suggestions: List<ImageSuggestion> = emptyList()
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
        @Description("The document file to illustrate (must be .md or .html). Exactly one file must be specified.")
        files: List<String> = emptyList(),
        @Description("Maximum number of images to generate, between 1 and 20 (default: 5)")
        var max_images: Int = 5,
        @Description("Image format to use: 'png' or 'jpg' (default: 'png')")
        var image_format: String = "png",
        @Description("Whether to automatically insert image references into the document (default: true)")
        var auto_insert: Boolean = true,
        @Description("Additional instructions to append to image generation prompts (e.g., style preferences, constraints). Null means no additional instructions.")
        var image_instructions: String? = null,
        @Description("Directive for the image composer on how to generate images (e.g., 'Generate a background wallpaper', 'Create hero images', 'Focus on technical diagrams'). Null means default behavior.")
        var composer_directive: String? = null,
        @Description("Directive for the image integrator on how to insert images (e.g., 'Insert as page background', 'Place in sidebars', 'Create galleries'). Null means default behavior.")
        var integrator_directive: String? = null,
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
            max_images = max_images.coerceIn(1, 20)
            if (image_format !in listOf("png", "jpg", "jpeg")) {
                return "image_format must be 'png', 'jpg', or 'jpeg'"
            }
            image_format = image_format.lowercase()
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String = buildString {
        appendLine("IllustrateDocument - Analyze a document and generate images to enhance its content")
        appendLine("  ** Specify a single markdown or HTML file to illustrate")
        appendLine("  ** Configure max_images (default: 5, range 1-20)")
        appendLine("  ** Choose image_format (png/jpg)")
        appendLine("  ** Optionally provide composer_directive to control image generation style")
        appendLine("  ** Optionally provide integrator_directive to control image placement")
        appendLine("  ** Analyzes document structure and content to identify optimal image locations")
        appendLine("  ** Generates contextually appropriate images with descriptive names")
        appendLine("  ** Saves images in the same folder as the document")
        appendLine("  ** Optionally inserts image references at appropriate locations via diff patches")
        appendLine("  ** Use this when the user wants to add visual enhancements to an existing document")
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
        val transcript = task.newUserFileStream(transcriptFile())

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

            val maxImages = executionConfig.max_images.coerceIn(1, 20)
            val imageFormat = executionConfig.image_format.lowercase()
            val autoInsert = executionConfig.auto_insert

            // Derive themed output paths from primary file
            val dataDir = (getOutputFile(".md")?.let {
                if (it.endsWith(".md")) it.removeSuffix(".md") else null
            } ?: documentFile.removeSuffix(".md").removeSuffix(".html")).apply {
                val dir = task.resolveUserFile(this)
                if (dir != null && !dir.exists()) dir.mkdirs()
            }

            log.info("Starting IllustrateDocumentTask for: $documentFile (maxImages=$maxImages, format=$imageFormat, autoInsert=$autoInsert)")
            transcript?.write("# Illustrating Document: $documentFile\n\n".toByteArray())
            transcript?.write("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
            transcript?.write("## Work Details\n\n".toByteArray())

            val ui = task.ui
            val tabs = TabbedDisplay(task)

            // Read document content
            val documentContent = documentPath.toFile().readText()
            val isMarkdown = documentFile.endsWith(".md", ignoreCase = true)

            transcript?.write(buildString {
                appendLine("<details><summary>Raw Document Content</summary>")
                appendLine()
                appendLine("```")
                appendLine(documentContent)
                appendLine("```")
                appendLine("</details>")
                appendLine()
            }.toByteArray())

            val overviewTask = tabs.newTask("Overview")
            overviewTask.header("Illustrating Document: $documentFile", level = 2)
            overviewTask.add(buildString {
                appendLine("* **Format:** ${if (isMarkdown) "Markdown" else "HTML"}")
                appendLine("* **Max Images:** $maxImages")
                appendLine("* **Image Format:** $imageFormat")
            }.renderMarkdown())

            if (!executionConfig.composer_directive.isNullOrBlank()) {
                overviewTask.add("**Composer Directive:** ${executionConfig.composer_directive}".renderMarkdown())
            }
            if (!executionConfig.integrator_directive.isNullOrBlank()) {
                overviewTask.add("**Integrator Directive:** ${executionConfig.integrator_directive}".renderMarkdown())
            }

            // Step 1: Analyze document and suggest images
            log.info("Analyzing document to suggest images for $documentFile")
            val analysisTask = tabs.newTask("Analysis")
            analysisTask.header("🔍 Analyzing Document", level = 3)
            analysisTask.add("Identifying sections that would benefit from visual enhancement...".renderMarkdown())

            val analysisPrompt = buildAnalysisPrompt(
                documentContent, maxImages, isMarkdown, executionConfig.composer_directive
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
            transcript?.write(buildString {
                appendLine("<details><summary>Image Suggestions Analysis</summary>")
                appendLine()
                appendLine("```json")
                appendLine(JsonUtil.toJson(analysis.obj))
                appendLine("```")
                appendLine("</details>")
                appendLine()
            }.toByteArray())

            analysisTask.add("✅ Identified **${suggestions.size}** opportunities for images".renderMarkdown())

            // Display suggestions
            analysisTask.header("📋 Planned Images", level = 3)
            suggestions.forEachIndexed { index, suggestion ->
                analysisTask.add(buildString {
                    appendLine("### ${index + 1}. ${suggestion.image_name}")
                    appendLine("* **Location:** ${suggestion.insertion_point}")
                    appendLine("* **Caption:** ${suggestion.caption}")
                }.renderMarkdown())
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
                model = orchestrationConfig.defaultImage.getChildClient(task),
            )

            val generatedImages = mutableListOf<Triple<String, String, ImageSuggestion>>()
            val documentFolder = documentPath.parent

            suggestions.forEachIndexed { index, suggestion ->
                try {
                    generationTask.header("Generating: ${suggestion.image_name}", level = 4)

                    // Build enhanced prompt with all supplemental instructions
                    val enhancedPrompt = buildString {
                        append(suggestion.image_prompt)
                        if (!executionConfig.composer_directive.isNullOrBlank()) {
                            append("\n\nComposer Directive: ${executionConfig.composer_directive}")
                        }
                        if (!executionConfig.image_instructions.isNullOrBlank()) {
                            append("\n\nAdditional Instructions: ${executionConfig.image_instructions}")
                        }
                    }

                    val result = imageAgent.answer(listOf(ImageAndText(enhancedPrompt)))
                    val generatedImage = result.image

                    val sanitizedName =
                        suggestion.image_name.replace(Regex("[^a-zA-Z0-9_-]"), "_").replace(Regex("_+"), "_").trim('_')
                    val imageFileName = "${sanitizedName}.$imageFormat"
                    val imagePath = documentFolder.resolve(imageFileName)

                    ImageIO.write(generatedImage, imageFormat, imagePath.toFile())
                    log.info("Saved image: $imageFileName")

                    // Save preview using themed directory
                    val previewFileName = "$dataDir/$imageFileName"
                    val previewFile = task.resolveUserFile(previewFileName)
                    if (previewFile != null) {
                        previewFile.parentFile?.mkdirs()
                        ImageIO.write(generatedImage, imageFormat, previewFile)
                    }
                    val previewLink = task.linkTo(previewFileName)
                    generationTask.add("""<a href="$previewLink" target="_blank"><img src="$previewLink" style="max-width: 400px; border-radius: 4px; box-shadow: 0 2px 4px rgba(0,0,0,0.1);" /></a>""")
                    generationTask.add("✅ Saved as `$imageFileName`".renderMarkdown())

                    generatedImages.add(Triple(imageFileName, imagePath.toString(), suggestion))

                    transcript?.write(buildString {
                        appendLine("- Generated image: $imageFileName for location: ${suggestion.insertion_point}")
                    }.toByteArray())

                } catch (e: Exception) {
                    // Triple Log: UI, SLF4J, Transcript
                    log.error("Failed to generate image: ${suggestion.image_name}", e)
                    generationTask.add(
                        "❌ Failed to generate: ${suggestion.image_name} - ${e.message}".renderMarkdown(),
                        additionalClasses = "text-danger"
                    )
                    transcript?.write(buildString {
                        appendLine("<details><summary>Error generating ${suggestion.image_name}</summary>")
                        appendLine()
                        appendLine("```")
                        appendLine(e.stackTraceToString())
                        appendLine("```")
                        appendLine("</details>")
                        appendLine()
                    }.toByteArray())
                }
            }

            transcript?.write("</div>\n\n".toByteArray())

            val integrationTask = tabs.newTask("Integration")
            integrationTask.header("📝 Generating Document Patches", level = 3)
            integrationTask.complete(
                generateImageInsertionPatches(
                    documentContent,
                    generatedImages,
                    isMarkdown,
                    executionConfig.integrator_directive,
                    integrationTask,
                    defaultChatter,
                    documentFile
                ) ?: ""
            )

            val totalTime = System.currentTimeMillis() - startTime

            // Write final output tab to transcript
            transcript?.write("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
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
            transcript?.write(summary.toByteArray())
            transcript?.write("\n</div>\n\n".toByteArray())

            log.info("IllustrateDocumentTask completed for $documentFile in ${totalTime}ms")
            task.safeComplete("Generated ${generatedImages.size} images in ${totalTime / 1000}s", log)
            resultFn(summary)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            // Triple Log: UI, SLF4J, Transcript
            log.error("IllustrateDocumentTask failed for $documentFile after ${duration}ms", e)
            task.error(e)
            transcript?.write(buildString {
                appendLine("## Error")
                appendLine()
                appendLine("<details><summary>Stack Trace</summary>")
                appendLine()
                appendLine("```")
                appendLine(e.stackTraceToString())
                appendLine("```")
                appendLine("</details>")
            }.toByteArray())
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
        val directiveSection = if (!composerDirective.isNullOrBlank()) buildString {
            appendLine("## Composer Directive:")
            appendLine(composerDirective)
            appendLine("**Important:** Follow this directive when suggesting images and creating prompts.")
        } else {
            ""
        }






        return buildString {
            appendLine("You are a document enhancement expert. Analyze this $formatInfo document and suggest images that would enhance its content.")
            if (directiveSection.isNotBlank()) {
                appendLine(directiveSection)
            }
            appendLine()
            appendLine("## Document Content:")
            appendLine("```")
            appendLine(documentContent.take(10000))
            appendLine("```")
            appendLine()
            appendLine("## Your Task:")
            appendLine("Identify up to $maxImages locations in the document where images would add significant value. For each suggestion:")
            appendLine()
            appendLine("1. **image_name**: Create a descriptive, filesystem-safe name (e.g., \"user_authentication_flow\", \"data_pipeline_diagram\")")
            appendLine("2. **image_prompt**: Write a detailed prompt for generating the image, including:")
            appendLine("    * Subject matter and key elements")
            appendLine("    * Visual style (diagram, illustration, photo-realistic, etc.)")
            appendLine("    * Color scheme and mood")
            appendLine("    * Specific details that match the document context")
            appendLine("3. **insertion_point**: Identify where to insert the image by providing:")
            appendLine("    * The exact heading text, or")
            appendLine("    * The first few words of the paragraph where it should appear")
            appendLine("4. **caption**: Write a clear, informative caption or alt text")
            appendLine()
            appendLine("## Guidelines:")
            appendLine("    * Prioritize sections with complex concepts that benefit from visualization")
            appendLine("    * Consider diagrams for processes, workflows, and architectures")
            appendLine("    * Suggest illustrations for abstract concepts")
            appendLine("    * Ensure images complement rather than duplicate text content")
            appendLine("    * Focus on high-impact locations that enhance understanding")
            appendLine("    * Make image prompts specific and detailed for best results")
            appendLine()
            appendLine("Generate suggestions now.")
        }
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
            val directiveSection = if (!integratorDirective.isNullOrBlank()) buildString {
                appendLine("## Integrator Directive:")
                appendLine(integratorDirective)
                appendLine("**Important:** Follow this directive when inserting images into the document.")
            } else {
                ""
            }
            val imageList = images.joinToString("\n") { (fileName, _, suggestion) -> buildString {
                appendLine("* **File**: $fileName")
                appendLine("  **Caption**: ${suggestion.caption}")
                appendLine("  **Suggested Location**: ${suggestion.insertion_point}")
                appendLine("  **Context**: ${suggestion.image_prompt.take(200)}")
            }
            }





            val patchPrompt = buildString {
                appendLine("You are a document integration expert. Insert image references into this $formatInfo document using diff patches.")
                if (directiveSection.isNotBlank()) {
                    appendLine(directiveSection)
                }
                appendLine("## Document Content:")
                appendLine("```")
                appendLine(documentContent)
                appendLine("```")
                appendLine("## Images to Insert:")
                appendLine(imageList)
                appendLine("**IMPORTANT**: All images are located in the SAME DIRECTORY as the document.")
                appendLine("Use ONLY the filename (e.g., \"image.png\") without any path prefix.")
                appendLine("Do NOT use paths like \"/assets/images/\" or any other directory structure.")
                appendLine()
                appendLine("## Your Task:")
                appendLine("Generate diff patches to insert image references at appropriate locations in the document.")
                appendLine("For ${if (isMarkdown) "Markdown" else "HTML"} format:")
                if (isMarkdown) {
                    appendLine("- Use EXACTLY: ![caption](filename) where filename is the exact filename provided")
                } else {
                    appendLine("- Use EXACTLY: <img src=\"filename\" alt=\"caption\" /> where filename is the exact filename provided")
                }
                appendLine()
                appendLine("**CRITICAL**: Use ONLY the exact filenames provided in the image list above. Do NOT add any path prefixes like \"/assets/images/\" or any other directory paths.")
                appendLine("The images are saved in the same directory as the document, so use relative filenames only.")
                appendLine()
                appendLine("**WRONG**: ![caption](/assets/images/filename.png)")
                appendLine("**CORRECT**: ![caption](filename.png)")
                appendLine()
                appendLine("* Include 2-3 lines of context before and after each insertion")
                appendLine("* Place images where they enhance understanding")
                appendLine("* Ensure proper spacing and formatting")
                appendLine("* Consider document flow and readability")
                appendLine("Response format:")
                appendLine("Use ```diff code blocks with a header specifying the file path.")
                appendLine("The diff format should use + for line additions, - for line deletions.")
                appendLine("Include 2 lines of context before and after every change.")
                appendLine("Example:")
                appendLine("### $documentFile")
                appendLine("```diff")
                appendLine(" existing line 1")
                appendLine(" existing line 2")
                appendLine("+![Image Caption](exact_filename_from_list.png)")
                appendLine("+")
                appendLine(" existing line 3")
                appendLine(" existing line 4")
                appendLine("```")
                appendLine("Generate the patches now.")
            }
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
                            DiffInstrumentor(
                              orchestrationConfig.processor ?: PatchProcessors.Fuzzy,
                              SessionRenderer(subTask),
                            ).instrument(
                                root = root,
                                response = it,
                                handle = { newCodeMap: Map<Path, String> ->
                                    newCodeMap.forEach { (path, _) ->
                                        log.info("Applied patch to: $path")
                                    }
                                    patchResult = "Patches applied successfully"
                                },
                                shouldAutoApply = { _: Path -> true },
                                defaultFile = documentFile,
                                resolver = ::resolveToRelativePath,
                            ) + "\n\n## Auto-applied image insertion patches"
                        })
                        semaphore.release()
                    } else {
                        subTask.complete(MarkdownUtil.renderMarkdown(response, ui = subTask.ui) {
                            DiffInstrumentor(
                              orchestrationConfig.processor ?: PatchProcessors.Fuzzy,
                              SessionRenderer(subTask),
                            ).instrument(
                                root = root,
                                response = it,
                                handle = { newCodeMap: Map<Path, String> ->
                                    newCodeMap.forEach { (path, _) ->
                                        log.info("Applied patch to: $path")
                                    }
                                    patchResult = "Patches applied successfully"
                                },
                                defaultFile = documentFile,
                                resolver = ::resolveToRelativePath,
                            ) + acceptButtonFooter(subTask.ui) {
                                subTask.complete()
                                semaphore.release()
                            }
                        })
                    }
                } catch (e: Exception) {
                    // Triple Log: UI, SLF4J, Transcript
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
            description = "Analyze a document and generate images to enhance its content. Specify a single .md or .html file, configure max_images and image_format, and optionally provide composer_directive and integrator_directive.",
            tooltipHtml = buildString {
                appendLine("Intelligently analyzes document content and generates contextually appropriate images.")
                appendLine("<ul>")
                appendLine("<li>Analyzes document structure to identify optimal image locations</li>")
                appendLine("<li>Generates images that enhance understanding of complex concepts</li>")
                appendLine("<li>Saves images with descriptive names in the document's folder</li>")
                appendLine("<li>Automatically inserts image references at appropriate locations</li>")
                appendLine("<li>Supports both Markdown and HTML formats</li>")
                appendLine("<li>Creates diagrams, illustrations, and visual aids</li>")
                appendLine("<li>Provides meaningful captions and alt text</li>")
                appendLine("<li>Configurable image count and format</li>")
                appendLine("<li>Supports composer and integrator directives for fine-grained control</li>")
                appendLine("</ul>")
            },
        )
    }
}