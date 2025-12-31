package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
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
        val subject: String? = null,

        @Description("Target number of pages")
        val target_pages: Int = 5,

        @Description("Art style (e.g., 'manga', 'western superhero', 'noir', 'cartoon')")
        val art_style: String = "western superhero",

        @Description("Whether to generate images for each row")
        val generate_images: Boolean = true,

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
  ** Create a comic book script with page/row/frame structure
  ** Specify subject, target pages, and art style
  ** Generates character profiles and visual descriptions
  ** Can generate images for each row (strip)
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
        log.info("Starting ComicBookGenerationTask - Subject: '${executionConfig?.subject}'")
        val transcript = task.transcript("ComicBookGeneration")?.let { OutputStreamWriter(it) }
        val genConfig = executionConfig
        transcript?.write("# Comic Book Generation Task\n\n")

        if (genConfig == null || genConfig.subject.isNullOrBlank()) {
            task.safeComplete("CONFIGURATION ERROR: Invalid configuration", log)
            resultFn("CONFIGURATION ERROR")
            return
        }

        val api = defaultSmart.getChildClient(task)
        val tabs = TabbedDisplay(task)

        val overviewTask = task.ui.newTask().apply { tabs["Overview"] = placeholder }
        overviewTask.header("Comic Book Generation: ${genConfig.subject}", 1)
        val statusBuffer = overviewTask.add("Generating script...")
        task.update()

        try {
            val parsingChatter = defaultFast.getChildClient(task)
            val scriptAgent = ParsedAgent(
                resultClass = ComicScript::class.java,
                prompt = """
                    You are a professional comic book writer. Create a script for a comic book.
                    Subject: ${genConfig.subject}
                    Target Pages: ${genConfig.target_pages}
                    Style: ${genConfig.art_style}
                    
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
                    
                    For each row, provide a 'visual_description' that summarizes the row for an artist to draw as a strip.
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

            val scriptTask = task.ui.newTask().apply { tabs["Script"] = placeholder }
            scriptTask.add(scriptContent.renderMarkdown)
            transcript?.write(scriptContent)
            transcript?.flush()
            task.update()

            statusBuffer?.setLength(0)
            statusBuffer?.append("✅ Script Generated")
            overviewTask.update()

            val characterImages = mutableMapOf<String, String>()
            val rowImages = mutableMapOf<String, String>()

            if (genConfig.generate_images) {
                var lastImage: java.awt.image.BufferedImage? = null
                val charRefTask = task.ui.newTask().apply { tabs["Characters"] = placeholder }
                charRefTask.header("Character References", 1)

                val charAgent = ImageProcessingAgent(
                    prompt = "Create a character sheet for a comic book. Style: ${genConfig.art_style}",
                    model = orchestrationConfig.defaultImage.getChildClient(task),
                    temperature = 0.7
                )

                script.characters.forEach { char ->
                    try {
                        val charPrompt =
                            "Character: ${char.name}\nDescription: ${char.description}\nVisual Traits: ${char.visual_traits}\nStyle: ${genConfig.art_style}"
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

                        transcript?.write("## ${char.name}\n\n" + "![${char.name}]($link)".transcriptFilter() + "\n\n*${char.description}*\n\n")
                        transcript?.flush()
                        task.update()
                    } catch (e: Exception) {
                        log.error("Failed to generate character image for ${char.name}", e)
                    }
                }

                statusBuffer?.setLength(0)
                statusBuffer?.append("✅ Script Generated<br/>Generating pages...")
                overviewTask.update()
                task.update()

                val imageAgent = ImageProcessingAgent(
                    prompt = "Create a comic book strip based on the description. Style: ${genConfig.art_style}",
                    model = orchestrationConfig.defaultImage.getChildClient(task),
                    temperature = 0.7
                )

                val finalOutput = StringBuilder()
                finalOutput.append("# ${script.title}\n\n")

                script.pages.forEach { page ->
                    val pageTask = task.ui.newTask().apply { tabs["Page ${page.page_number}"] = placeholder }
                    pageTask.header("Page ${page.page_number}", 1)
                    finalOutput.append("## Page ${page.page_number}\n\n")

                    page.rows.forEach { row ->
                        val rowPrompt = buildString {
                            appendLine("Comic strip row, style: ${genConfig.art_style}")
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
                                  <img src='$link' alt='Page ${page.page_number} Row ${row.row_number}' style='width: 100%; max-width: 800px; border: 1px solid #ccc;' />
                                </div>
                            """.trimIndent()

                            pageTask.add(rowHtml.renderMarkdown)
                            finalOutput.append("![Row ${row.row_number}]($link)\n\n")
                            transcript?.write("![Page ${page.page_number} Row ${row.row_number}]($link)".transcriptFilter() + "\n\n")
                            transcript?.flush()

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
                statusBuffer?.append("✅ Images Generated")
                overviewTask.update()
                resultFn(finalOutput.toString())
            } else {
                resultFn(scriptContent)
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
            log.error("Error in ComicBookGenerationTask", e)
            task.error(e)
            resultFn("Error: ${e.message}")
        } finally {
            transcript?.close()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(ComicBookGenerationTask::class.java)
        val ComicBookGeneration = TaskType(
            "ComicBookGeneration",
            "Writing",
            ComicBookGenerationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate comic book scripts and visuals",
            "Creates a comic book with page/row/frame structure and optional visual generation."
        )
    }
}