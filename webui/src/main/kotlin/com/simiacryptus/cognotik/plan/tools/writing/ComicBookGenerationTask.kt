package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

open class ComicBookGenerationTask<T : ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    open class ComicBookGenerationTaskExecutionConfigData(
        @Description("The subject or scenario to develop into a comic book")
        var subject: String? = null,

        @Description("Target number of pages")
        var target_pages: Int = 5,

        @Description("Art style (e.g., 'manga', 'western superhero', 'noir', 'cartoon')")
        var art_style: String = "western superhero",
        @Description("Additional style details or visual guidelines")
        var style_details: String = "",


        @Description("Whether to generate images for each row")
        var generate_images: Boolean = true,

        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = ComicBookGeneration.name,
        task_description = "Generate comic book for '$subject'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (target_pages <= 0) return "target_pages must be positive"
            return null
        }
    }

    data class ComicScript(
        val title: String = "",
        val premise: String = "",
        val characters: List<CharacterProfile> = emptyList(),
        val pages: List<PageScript> = emptyList()
    )

    data class CharacterProfile(
        val name: String = "",
        val description: String = "",
        val visual_traits: String = ""
    )

    data class PageScript(
        val page_number: Int = 1,
        val rows: List<RowScript> = emptyList()
    )

    data class RowScript(
        val row_number: Int = 1,
        val frames: List<FrameScript> = emptyList(),
        val visual_description: String = ""
    )

    data class FrameScript(
        val frame_number: Int = 1,
        val description: String = "",
        val dialog: List<DialogLine> = emptyList(),
        val caption: String? = null
    )

    data class DialogLine(
        val character: String = "",
        val text: String = ""
    )

    override fun promptSegment(): String {
        return """
ComicBookGeneration - Generate comic book scripts and visuals
  - **Use this tool** to create professional comic book scripts with a structured page/row/frame layout.
  - **Inputs:** Requires a 'subject', 'target_pages', and 'art_style'.
  - **Capabilities:** Generates character profiles, detailed visual descriptions, and can optionally generate AI visuals for each row (strip).
  - **Output:** Returns a summary of the generated script and links to saved image artifacts.
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


      val transcript = task.transcript()
      task.ui.pool.submit {
        try {
          log.info("Task 'ComicBookGeneration' started for subject: ${executionConfig?.subject}")
          transcript?.write("# Comic Book Generation Task\n\n".toByteArray())

          val genConfig = executionConfig
          if (genConfig == null || genConfig.subject.isNullOrBlank()) {
            val err = "CONFIGURATION ERROR: Invalid or missing subject."
            task.error(Exception(err))
            log.error(err)
            resultFn(err)
            return@submit
          }

          val api = defaultSmart.getChildClient(task)
          val tabs = TabbedDisplay(task)
          val overviewTask = tabs.newTask("Overview")
          overviewTask.header("Comic Book Generation: ${genConfig.subject}", 1)
          val statusBuffer = overviewTask.add("Generating script...".renderMarkdown())
          task.update()

            val parsingChatter = defaultFast.getChildClient(task)
            val scriptAgent = ParsedAgent(
                resultClass = ComicScript::class.java,
                prompt = """
                    You are a professional comic book writer. Create a detailed script for a comic book.
                    **Subject:** ${genConfig.subject}
                    **Target Pages:** ${genConfig.target_pages}
                    **Style:** ${genConfig.art_style}
                    ${if (genConfig.style_details.isNotBlank()) "Style Details: ${genConfig.style_details}" else ""}
                    
                    Structure the output with:
                    - Title and Premise
                    - Character Profiles (Name, Description, Visual Traits)
                    - Pages (numbered)
                    - Rows per page (usually 3-4 rows per page)
                    - Frames per row (usually 1-3 frames per row)
                    
                    For each frame, provide:
                    - Visual description
                    - Dialog (Character: Text)
                    - Captions (if any)
                    
                    For each row, provide a 'visual_description' that summarizes the row for an artist to draw as a strip. Include lighting, mood, and composition details. Ensure visual consistency across panels.
                """.trimIndent(),
                model = api,
                parsingChatter = parsingChatter,
                temperature = 0.7
            )

            val script = scriptAgent.answer(listOf("Generate comic script")).obj

            val scriptContent = buildString {
                appendLine("# ${script.title}")
                appendLine("*${script.premise}*")
                appendLine("## Characters")
                script.characters.forEach { appendLine("- **${it.name}**: ${it.description} (${it.visual_traits})") }
                appendLine("## Script")
                script.pages.forEach { page ->
                    appendLine("### Page ${page.page_number}")
                    page.rows.forEach { row ->
                        appendLine("**Row ${row.row_number}**")
                        row.frames.forEach { frame ->
                            appendLine("- Panel ${frame.frame_number}: ${frame.description}")
                            frame.dialog.forEach { d -> appendLine("  - **${d.character}**: \"${d.text}\"") }
                            if (frame.caption != null) appendLine("  - *Caption*: ${frame.caption}")
                        }
                    }
                }
            }

            val scriptTask = tabs.newTask("Script")
            scriptTask.add(scriptContent.renderMarkdown)
          transcript?.write("## Generated Script\n<details><summary>Expand Script Content</summary>\n\n$scriptContent\n\n</details>\n".toByteArray())
            task.update()

            statusBuffer?.setLength(0)
          statusBuffer?.append("✅ Script Generated".renderMarkdown())
            overviewTask.update()

            val characterImages = mutableMapOf<String, String>()
            val rowImages = mutableMapOf<String, String>()

            if (genConfig.generate_images) {
              if (!orchestrationConfig.autoFix) {
                val semaphore = Semaphore(0)
                val footer = acceptButtonFooter(task.ui) {
                  statusBuffer?.setLength(0)
                  statusBuffer?.append("✅ Script Approved. Generating visuals...".renderMarkdown())
                  overviewTask.update()
                  semaphore.release()
                }
                overviewTask.add("### Approval Required\nReview the script in the 'Script' tab and click below to generate visuals.".renderMarkdown())
                overviewTask.add(footer)
                log.info("Task paused. Waiting for user approval to generate visuals.")
                semaphore.acquire()
              }

              var lastImage: java.awt.image.BufferedImage? = null
                val charRefTask = tabs.newTask("Characters")
                charRefTask.header("Character References", 1)

                val charAgent = ImageProcessingAgent(
                    prompt = "Create a character sheet for a comic book. Style: ${genConfig.art_style} ${genConfig.style_details}",
                    model = orchestrationConfig.defaultImage.getChildClient(task),
                    temperature = 0.7
                )

                script.characters.forEach { char ->
                    try {
                        val charPrompt =
                            "Character: ${char.name}\nDescription: ${char.description}\nVisual Traits: ${char.visual_traits}\nStyle: ${genConfig.art_style} ${genConfig.style_details}"
                        val inputs = mutableListOf<ImageAndText>()
                        if (lastImage != null) {
                            inputs.add(ImageAndText(text = "Style Reference", image = lastImage))
                        }
                        inputs.add(ImageAndText(charPrompt))
                        val result = charAgent.answer(inputs)
                        val image = result.image
                        lastImage = image
                        val relativePath = "char_${char.name.replace(Regex("[^a-zA-Z0-9]"), "_")}.png"
                        val baos = ByteArrayOutputStream()
                        ImageIO.write(image, "png", baos)
                        val link = task.saveFile(relativePath, baos.toByteArray())
                        characterImages[char.name] = relativePath

                        charRefTask.header(char.name, 2)
                        charRefTask.append("<img src='$link' alt='${char.name}' style='max-width: 100%'/>")
                        charRefTask.add("*${char.description}*")

                      transcript?.write(
                        "## Character: ${char.name}\n\n![${char.name}]($link)".transcriptFilter()
                          .toByteArray() + "\n\n*${char.description}*\n\n".toByteArray()
                      )
                        task.update()
                    } catch (e: Exception) {
                        log.error("Failed to generate character image for ${char.name}", e)
                    }
                }

                statusBuffer?.setLength(0)
              statusBuffer?.append("✅ Script Generated<br/>Generating pages...".renderMarkdown())
                overviewTask.update()
                task.update()

                val imageAgent = ImageProcessingAgent(
                    prompt = "Create a comic book strip based on the description. Style: ${genConfig.art_style} ${genConfig.style_details}",
                    model = orchestrationConfig.defaultImage.getChildClient(task),
                    temperature = 0.7
                )

                val finalOutput = StringBuilder()
                finalOutput.append("# ${script.title}\n\n")

                script.pages.forEach { page ->
                    val pageTask = tabs.newTask("Page ${page.page_number}")
                    pageTask.header("Page ${page.page_number}: Visuals & Script", 1)
                    finalOutput.append("## Page ${page.page_number}\n\n")

                    page.rows.forEach { row ->
                        val rowPrompt = buildString {
                            appendLine("Comic strip row, style: ${genConfig.art_style} ${genConfig.style_details}")
                            appendLine("Description: ${row.visual_description}")
                            appendLine("Panels:")
                            row.frames.forEach { frame ->
                                appendLine("- Panel ${frame.frame_number}: ${frame.description}")
                                frame.dialog.forEach { d -> appendLine("  (Speech bubble for ${d.character}: \"${d.text}\")") }
                            }
                        }

                        try {
                            val imageInputs = mutableListOf<ImageAndText>()

                            val rowContent = row.visual_description + " " + row.frames.joinToString(" ") {
                                it.description + " " + it.dialog.joinToString(" ") { d -> d.character + " " + d.text }
                            }
                            script.characters.forEach { char ->
                                if (rowContent.contains(char.name, ignoreCase = true) || char.name.split(" ")
                                        .any { it.length > 3 && rowContent.contains(it, ignoreCase = true) }
                                ) {
                                    val path = characterImages[char.name]
                                    if (path != null) {
                                        try {
                                            val img = ImageIO.read(task.resolveUserFile(path))
                                            if (img != null) {
                                                imageInputs.add(
                                                    ImageAndText(
                                                        text = "Reference for ${char.name}: ${char.visual_traits}",
                                                        image = img
                                                    )
                                                )
                                            }
                                        } catch (e: Exception) {
                                            log.warn("Failed to load character reference", e)
                                        }
                                    }
                                }
                            }
                            if (lastImage != null) {
                                imageInputs.add(
                                    ImageAndText(
                                        text = "Previous Scene / Style Reference",
                                        image = lastImage
                                    )
                                )
                            }
                            imageInputs.add(ImageAndText(rowPrompt))

                            val result = imageAgent.answer(imageInputs)
                            val image = result.image
                            lastImage = image
                            val relativePath = "page_${page.page_number}_row_${row.row_number}.png"
                            val baos = ByteArrayOutputStream()
                            ImageIO.write(image, "png", baos)
                            val link = task.saveFile(relativePath, baos.toByteArray())
                            rowImages["${page.page_number}_${row.row_number}"] = relativePath

                            val rowHtml = """
                                <div class='comic-row'>
                                  <img src='$link' alt='Page ${page.page_number} Row ${row.row_number}' title='${row.visual_description.replace("'", "&apos;")}' style='width: 100%; max-width: 800px; border: 1px solid #ccc;' />
                                </div>
                            """.trimIndent()

                            pageTask.add(rowHtml.renderMarkdown)
                            finalOutput.append("![Row ${row.row_number}]($link)\n\n")
                          transcript?.write(
                            "![Page ${page.page_number} Row ${row.row_number}]($link)".transcriptFilter()
                              .toByteArray() + "\n\n".toByteArray()
                          )

                            val textContent = buildString {
                                row.frames.forEach { frame ->
                                    frame.dialog.forEach { d -> appendLine("**${d.character}**: ${d.text}") }
                                    if (frame.caption != null) appendLine("*${frame.caption}*")
                                }
                            }
                            pageTask.add(textContent.renderMarkdown)
                            finalOutput.append(textContent + "\n\n")

                        } catch (e: Exception) {
                            log.error("Failed to generate image for Page ${page.page_number} Row ${row.row_number}", e)
                            pageTask.add("**Failed to generate image**\n".renderMarkdown)
                        }
                        task.update()
                    }
                }

                statusBuffer?.setLength(0)
              statusBuffer?.append("✅ Images Generated".renderMarkdown())
                overviewTask.update()

              resultFn(
                """
                    ## Comic Book Generation Complete
                    * **Title:** ${script.title}
                    * **Pages:** ${script.pages.size}
                    * **Visuals:** Generated for all rows.
                    * **Artifacts:**
                      * Full Metadata: `comic_book.json`
                      * Character Images: ${characterImages.size} files
                      * Page Strips: ${rowImages.size} files
                """.trimIndent()
              )
            } else {
              resultFn("## Comic Book Script Generated\nTitle: ${script.title}\nPages: ${script.pages.size}\nVisual generation was disabled.")
            }
            task.saveFile(
                "comic_book.json",
                JsonUtil.toJson(
                    mapOf(
                        "config" to genConfig,
                        "script" to script,
                        "characterImages" to characterImages,
                        "rowImages" to rowImages
                    )
                ).toByteArray()
            )


            task.safeComplete("Comic Book Generation Complete", log)

        } catch (e: Exception) {
          task.error(e)
          log.error("Error in ComicBookGenerationTask: ${e.message}")
          transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
          resultFn("Error: ${e.message}")
        } finally {
          transcript?.close()
        }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ComicBookGenerationTask::class.java)
        @JvmStatic val ComicBookGeneration = TaskType(
          name = "ComicBookGeneration",
          category = "Writing",
          taskClass = ComicBookGenerationTask::class.java,
          executionConfigClass = ComicBookGenerationTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Generate comic book scripts and visuals",
          tooltipHtml = "Creates a comic book with page/row/frame structure and optional visual generation.",
        )
    }
}