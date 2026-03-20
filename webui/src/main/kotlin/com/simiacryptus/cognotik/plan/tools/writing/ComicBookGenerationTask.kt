package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.util.concurrent.Semaphore
import javax.imageio.ImageIO

open class ComicBookGenerationTask<T : ComicBookGenerationTask.ComicBookGenerationTaskExecutionConfigData>(
  orchestrationConfig: OrchestrationConfig, planTask: T?
) : AbstractTask<T, TaskTypeConfig>(
  orchestrationConfig, planTask
) {

  data class CharacterReference(
    @Description("The name of the character this reference is for")
    var character_name: String = "",
    @Description("Path or URL to a reference image for this character")
    var reference_image_path: String? = null,
    @Description("Detailed visual description of the character (appearance, clothing, distinguishing features, color palette, etc.)")
    var visual_description: String? = null,
    @Description("Personality traits and behavioral notes to inform how the character is depicted")
    var personality_notes: String? = null,
    @Description("Character's role in the story (e.g., protagonist, antagonist, supporting)")
    var role: String? = null,
    @Description("Relationships with other characters")
    var relationships: String? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      character_name = character_name.trim()
      if (character_name.isBlank()) return "character_name must not be blank"
      return null
    }
  }

  data class PlotContinuityDetails(
    @Description("Overall story arc or narrative structure (e.g., 'three-act structure', 'episodic')")
    var narrative_structure: String = "",
    @Description("Key plot points that must be included, in order")
    var key_plot_points: List<String> = emptyList(),
    @Description("Setting details (time period, location, world-building notes)")
    var setting_details: String = "",
    @Description("Themes or motifs to weave throughout the story")
    var themes: List<String> = emptyList(),
    @Description("Tone and mood guidelines (e.g., 'dark and gritty', 'lighthearted and humorous')")
    var tone: String = "",
    @Description("Previously established story context or backstory that this comic continues from")
    var prior_story_context: String = "",
    @Description("Specific continuity constraints (e.g., 'Character X must not appear until page 3', 'The artifact is revealed in the climax')")
    var continuity_constraints: List<String> = emptyList(),
    @Description("Desired ending or resolution notes")
    var ending_notes: String = "",
    @Description("Any additional requirements or notes for the writer/artist")
    var additional_notes: String = ""
  )

  @Suppress("PropertyName", "LocalVariableName")
  open class ComicBookGenerationTaskExecutionConfigData(
    @Description("The subject or scenario to develop into a comic book")
    override var task_description: String? = null,
    @Description("Target number of pages. Must be a positive integer.")
    var target_pages: Int = 5,
    @Description("Art style (e.g., 'manga', 'western superhero', 'noir', 'cartoon')")
    var art_style: String = "western superhero",
    @Description("Additional style details or visual guidelines")
    var style_details: String = "",
    @Description("Whether to generate images for each row")
    var generate_images: Boolean = true,
    @Description("Optional character references with images and detailed descriptions for visual consistency")
    var character_references: List<CharacterReference> = emptyList(),
    @Description("Optional plot continuity details to guide narrative structure and consistency")
    var plot_continuity: PlotContinuityDetails? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = ComicBookGeneration.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      target_pages = target_pages.coerceIn(1, 100)
      art_style = art_style.trim().ifBlank { "western superhero" }
      return null
    }
  }

  data class ComicScript(
    @Description("The title of the comic book")
    var title: String = "",
    @Description("The premise or synopsis of the comic book story")
    var premise: String = "",
    @Description("List of character profiles appearing in the comic")
    var characters: List<CharacterProfile> = emptyList(),
    @Description("List of pages comprising the comic book")
    var pages: List<PageScript> = emptyList()
  )

  data class CharacterProfile(
    @Description("The name of the character")
    var name: String = "",
    @Description("A textual description of the character's personality and role")
    var description: String = "",
    @Description("Visual traits for the artist to reference when drawing the character")
    var visual_traits: String = ""
  )

  data class PageScript(
    @Description("The page number within the comic book")
    var page_number: Int = 1,
    @Description("List of rows (strips) on this page")
    var rows: List<RowScript> = emptyList()
  )

  data class RowScript(
    @Description("The row number within the page")
    var row_number: Int = 1,
    @Description("List of frames (panels) in this row")
    var frames: List<FrameScript> = emptyList(),
    @Description("A visual description summarizing the row for an artist to draw as a strip")
    var visual_description: String = ""
  )

  data class FrameScript(
    @Description("The frame (panel) number within the row")
    var frame_number: Int = 1,
    @Description("Visual description of what is happening in this frame")
    var description: String = "",
    @Description("List of dialog lines spoken by characters in this frame")
    var dialog: List<DialogLine> = emptyList(),
    @Description("Optional narrative caption text for this frame")
    var caption: String? = null
  )

  data class DialogLine(
    @Description("The name of the character speaking")
    var character: String = "",
    @Description("The dialog text spoken by the character")
    var text: String = ""
  )

  override fun promptSegment(): String = buildString {
    appendLine("ComicBookGeneration - Generate comic book scripts and visuals")
    appendLine("  ** Use this tool to create professional comic book scripts with a structured page/row/frame layout.")
    appendLine("  ** Inputs: Requires a 'task_description' (subject), 'target_pages', and 'art_style'.")
    appendLine("  ** Optional Inputs:")
    appendLine("    - 'character_references': Provide reference images and detailed descriptions for characters to ensure visual consistency.")
    appendLine("    - 'plot_continuity': Specify narrative structure, key plot points, themes, tone, prior story context, and continuity constraints.")
    appendLine("  ** Capabilities: Generates character profiles, detailed visual descriptions, and can optionally generate AI visuals for each row (strip).")
    appendLine("  ** Output: Returns a summary of the generated script and links to saved image artifacts.")
  }

  private fun buildCharacterReferencePrompt(refs: List<CharacterReference>): String {
    if (refs.isEmpty()) return ""
    return buildString {
      appendLine("\n## Pre-defined Character References")
      appendLine("The following characters have been pre-defined. Use these details to ensure consistency:")
      refs.forEach { ref ->
        appendLine("\n### ${ref.character_name}")
        if (ref.visual_description?.isNotBlank() == true) appendLine("- **Visual Description:** ${ref.visual_description}")
        if (ref.personality_notes?.isNotBlank() == true) appendLine("- **Personality:** ${ref.personality_notes}")
        if (ref.role?.isNotBlank() == true) appendLine("- **Role:** ${ref.role}")
        if (ref.relationships?.isNotBlank() == true) appendLine("- **Relationships:** ${ref.relationships}")
      }
    }
  }

  private fun buildPlotContinuityPrompt(continuity: PlotContinuityDetails?): String {
    if (continuity == null) return ""
    return buildString {
      appendLine("\n## Plot Continuity & Narrative Guidelines")
      if (continuity.narrative_structure?.isNotBlank() == true) appendLine("- **Narrative Structure:** ${continuity.narrative_structure}")
      if (continuity.setting_details?.isNotBlank() == true) appendLine("- **Setting:** ${continuity.setting_details}")
      if (continuity.tone?.isNotBlank() == true) appendLine("- **Tone/Mood:** ${continuity.tone}")
      if (continuity.prior_story_context?.isNotBlank() == true) appendLine("- **Prior Story Context:** ${continuity.prior_story_context}")
      if (continuity.themes.isNotEmpty()) appendLine("- **Themes:** ${continuity.themes.joinToString(", ")}")
      if (continuity.key_plot_points.isNotEmpty()) {
        appendLine("- **Key Plot Points (in order):**")
        continuity.key_plot_points.forEachIndexed { i, point -> appendLine("  ${i + 1}. $point") }
      }
      if (continuity.continuity_constraints.isNotEmpty()) {
        appendLine("- **Continuity Constraints:**")
        continuity.continuity_constraints.forEach { appendLine("  - $it") }
      }
      if (continuity.ending_notes?.isNotBlank() == true) appendLine("- **Ending/Resolution Notes:** ${continuity.ending_notes}")
      if (continuity.additional_notes?.isNotBlank() == true) appendLine("- **Additional Notes:** ${continuity.additional_notes}")
    }
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = task.newUserFileStream(transcriptFile())
    task.ui.pool.submit {
      val dataDir = (getOutputFile(".md")?.let {
        if (it.endsWith(".md")) it.removeSuffix(".md") else null
      } ?: "comic").apply {
        val dir = task.resolveUserFile(this)
        if (dir != null && !dir.exists()) {
          dir.mkdirs()
        }
      }
      val dataFile = getOutputFile(".md")?.let {
        if (it.endsWith(".md")) it.removeSuffix(".md") + ".comic.json" else null
      } ?: "comic_book.json"

      try {
        val subject = messages.firstOrNull() ?: executionConfig?.task_description
        log.info("Task 'ComicBookGeneration' started for subject: $subject")
        transcript?.write("# Comic Book Generation Task\n\n".toByteArray())

        if (subject.isNullOrBlank()) {
          val err = "CONFIGURATION ERROR: Invalid or missing subject."
          task.error(Exception(err))
          log.error(err)
          transcript?.write("## Error\n\n<details><summary>Configuration Error</summary>\n\n$err\n\n</details>\n".toByteArray())
          resultFn(err)
          return@submit
        }

        val api = defaultSmart.getChildClient(task)
        val tabs = TabbedDisplay(task)
        val overviewTask = tabs.newTask("Overview")
        overviewTask.header("Comic Book Generation: $subject", 1)
        val statusBuffer = overviewTask.add("Generating script...".renderMarkdown())
        task.update()

        val characterRefPrompt = buildCharacterReferencePrompt(executionConfig?.character_references ?: emptyList())
        val plotContinuityPrompt = buildPlotContinuityPrompt(executionConfig?.plot_continuity)

        val parsingChatter = defaultFast.getChildClient(task)
        val scriptPrompt = buildString {
          appendLine("You are a professional comic book writer. Create a detailed script for a comic book.")
          appendLine("**Subject:** $subject")
          appendLine("**Target Pages:** ${executionConfig!!.target_pages}")
          appendLine("**Style:** ${executionConfig.art_style}")
          if (executionConfig.style_details?.isNotBlank() == true) appendLine("Style Details: ${executionConfig.style_details}")
          append(characterRefPrompt)
          append(plotContinuityPrompt)
          appendLine()
          appendLine("Structure the output with:")
          appendLine("- Title and Premise")
          appendLine("- Character Profiles (Name, Description, Visual Traits)")
          if (executionConfig.character_references.isNotEmpty()) {
            appendLine("  - IMPORTANT: Incorporate the pre-defined character references above into the character profiles. Maintain visual and personality consistency with the provided descriptions.")
          }
          appendLine("- Pages (numbered)")
          appendLine("- Rows per page (usually 3-4 rows per page)")
          appendLine("- Frames per row (usually 1-3 frames per row)")
          appendLine()
          appendLine("For each frame, provide:")
          appendLine("- Visual description")
          appendLine("- Dialog (Character: Text)")
          appendLine("- Captions (if any)")
          appendLine()
          appendLine("For each row, provide a 'visual_description' that summarizes the row for an artist to draw as a strip. Include lighting, mood, and composition details. Ensure visual consistency across panels.")
          if (plotContinuityPrompt?.isNotBlank() == true) {
            appendLine()
            appendLine("IMPORTANT: Follow the plot continuity guidelines strictly. Ensure all key plot points are addressed in order, continuity constraints are respected, and the narrative maintains the specified tone and themes throughout.")
          }
        }
        val scriptAgent = ParsedAgent(
          resultClass = ComicScript::class.java,
          prompt = scriptPrompt,
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
        scriptTask.add(scriptContent.renderMarkdown(true))
        transcript?.write("## Generated Script\n\n<details><summary>Full Script</summary>\n\n$scriptContent\n\n</details>\n\n".toByteArray())
        task.update()

        statusBuffer?.setLength(0)
        statusBuffer?.append("✅ Script Generated".renderMarkdown())
        overviewTask.update()

        val characterImages = mutableMapOf<String, String>()
        val rowImages = mutableMapOf<String, String>()

        // Pre-load any user-provided character reference images
        val preloadedCharacterRefImages = mutableMapOf<String, java.awt.image.BufferedImage>()
        executionConfig?.character_references?.forEach { ref ->
          val referenceImagePath = ref.reference_image_path
          if (referenceImagePath?.isNotBlank() == true) {
            try {
              val img = ImageIO.read(task.resolveUserFile(referenceImagePath))
              if (img != null) {
                preloadedCharacterRefImages[ref.character_name] = img
                log.info("Loaded reference image for character '${ref.character_name}' from $referenceImagePath")
              }
            } catch (e: Exception) {
              log.warn(
                "Failed to load reference image for character '${ref.character_name}' from $referenceImagePath",
                e
              )
              transcript?.write("**Warning:** Failed to load reference image for '${ref.character_name}': ${e.message}\n\n".toByteArray())
            }
          }
        }

        if (executionConfig?.generate_images == true) {
          if (!orchestrationConfig.autoFix) {
            val semaphore = Semaphore(0)
            val footer = acceptButtonFooter(task.ui) {
              statusBuffer?.setLength(0)
              statusBuffer?.append("✅ Script Approved. Generating visuals...".renderMarkdown())
              overviewTask.update()
              transcript?.write("## User Action\n\nScript approved by user. Proceeding with visual generation.\n\n".toByteArray())
              semaphore.release()
            }
            overviewTask.add("### Approval Required\nReview the script in the 'Script' tab and click below to generate visuals.".renderMarkdown())
            overviewTask.add(footer)
            log.info("Task paused. Waiting for user approval to generate visuals.")
            semaphore.acquire()
          } else {
            transcript?.write("## Auto-Fix Mode\n\nAuto-applying: proceeding directly to visual generation.\n\n".toByteArray())
          }

          var lastImage: java.awt.image.BufferedImage? = null
          val charRefTask = tabs.newTask("Characters")
          charRefTask.header("Character References", 1)
          val charPromptText = buildString {
            appendLine("Create a character sheet for a comic book.")
            appendLine("Style: ${executionConfig.art_style} ${executionConfig.style_details}")
          }

          val charAgent = ImageProcessingAgent(
            prompt = charPromptText,
            model = orchestrationConfig.defaultImage.getChildClient(task),
            temperature = 0.7
          )

          // Build a lookup map from character references for easy access
          val charRefLookup = executionConfig.character_references?.associateBy { it.character_name.lowercase() }!!

          script.characters.forEach { char ->
            try {
              val matchedRef = charRefLookup[char.name.lowercase()]
              val enhancedVisualTraits = buildString {
                append(char.visual_traits)
                if (matchedRef != null) {
                  if (matchedRef.visual_description?.isNotBlank() == true) append(" ${matchedRef.visual_description}")
                  if (matchedRef.personality_notes?.isNotBlank() == true) append(" Personality: ${matchedRef.personality_notes}")
                }
              }

              val charPrompt = buildString {
                appendLine("Character: ${char.name}")
                appendLine("Description: ${char.description}")
                appendLine("Visual Traits: $enhancedVisualTraits")
                appendLine("Style: ${executionConfig?.art_style} ${executionConfig?.style_details}")
              }
              val inputs = mutableListOf<ImageAndText>()

              // Use user-provided reference image if available
              val userRefImage = preloadedCharacterRefImages[char.name]
              if (userRefImage != null) {
                inputs.add(
                  ImageAndText(
                    text = "User-provided reference image for ${char.name}. Match this character's appearance closely.",
                    image = userRefImage
                  )
                )
              }

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
              val link = task.saveFile("$dataDir/$relativePath", baos.toByteArray())
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
              task.error(e)
              log.error("Failed to generate character image for ${char.name}", e)
              transcript?.write("\n## Character Image Error: ${char.name}\n\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n\n</details>\n\n".toByteArray())
            }
          }

          statusBuffer?.setLength(0)
          statusBuffer?.append("✅ Script Generated<br/>Generating pages...".renderMarkdown())
          overviewTask.update()
          task.update()
          val imageAgentPrompt = buildString {
            appendLine("Create a comic book strip based on the description.")
            appendLine("Style: ${executionConfig.art_style} ${executionConfig.style_details}")
          }

          val imageAgent = ImageProcessingAgent(
            prompt = imageAgentPrompt,
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
                appendLine("Comic strip row, style: ${executionConfig?.art_style} ${executionConfig?.style_details}")
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
                    // First try user-provided reference images
                    val userRefImg = preloadedCharacterRefImages[char.name]
                    if (userRefImg != null) {
                      val matchedRef = charRefLookup[char.name.lowercase()]
                      val refText = buildString {
                        append("User reference for ${char.name}: ${char.visual_traits}")
                        if (matchedRef?.visual_description?.isNotBlank() == true) append(" ${matchedRef.visual_description}")
                      }
                      imageInputs.add(ImageAndText(text = refText, image = userRefImg))
                    }

                    // Then try generated character images
                    val path = characterImages[char.name]
                    if (path != null) {
                      try {
                        val img = ImageIO.read(task.resolveUserFile("$dataDir/$path"))
                        if (img != null) {
                          imageInputs.add(
                            ImageAndText(
                              text = "Generated reference for ${char.name}: ${char.visual_traits}", image = img
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
                      text = "Previous Scene / Style Reference", image = lastImage
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
                val link = task.saveFile("$dataDir/$relativePath", baos.toByteArray())
                rowImages["${page.page_number}_${row.row_number}"] = relativePath

                val rowHtml = buildString {
                  appendLine("<div class='comic-row'>")
                  appendLine("  <img src='$link' alt='Page ${page.page_number} Row ${row.row_number}' title='${row.visual_description}' style='width: 100%; max-width: 800px; border: 1px solid #ccc;' />")
                  appendLine("</div>")
                }

                pageTask.add(rowHtml.renderMarkdown(true))
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
                pageTask.add(textContent.renderMarkdown(true))
                finalOutput.append(textContent + "\n\n")

              } catch (e: Exception) {
                task.error(e)
                log.error("Failed to generate image for Page ${page.page_number} Row ${row.row_number}", e)
                transcript?.write("\n## Row Image Error: Page ${page.page_number} Row ${row.row_number}\n\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n\n</details>\n\n".toByteArray())
                pageTask.add("**Failed to generate image**\n".renderMarkdown(true))
              }
              task.update()
            }
          }

          statusBuffer?.setLength(0)
          statusBuffer?.append("✅ Images Generated".renderMarkdown())
          overviewTask.update()

          val resultSummary = buildString {
            appendLine("## Comic Book Generation Complete")
            appendLine("* **Title:** ${script.title}")
            appendLine("* **Pages:** ${script.pages.size}")
            appendLine("* **Visuals:** Generated for all rows.")
            appendLine("* **Character References Provided:** ${executionConfig?.character_references?.size}")
            appendLine("* **Plot Continuity:** ${if (executionConfig?.plot_continuity != null) "Configured" else "Not specified"}")
            appendLine("* **Artifacts:**")
            appendLine("  * Full Metadata: `${dataFile}`")
            appendLine("  * Character Images: ${characterImages.size} files")
            appendLine("  * Page Strips: ${rowImages.size} files")
          }
          resultFn(resultSummary)
        } else {
          resultFn("## Comic Book Script Generated\nTitle: ${script.title}\nPages: ${script.pages.size}\nVisual generation was disabled.")
        }
        task.saveFile(
          dataFile, JsonUtil.toJson(
            mapOf(
              "config" to executionConfig,
              "script" to script,
              "characterImages" to characterImages,
              "rowImages" to rowImages
            )
          ).toByteArray()
        )

        task.safeComplete("Comic Book Generation Complete", log)

      } catch (e: Exception) {
        task.error(e)
        log.error("Error in ComicBookGenerationTask: ${e.message}", e)
        transcript?.write("## Error\n\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n\n</details>\n".toByteArray())
        resultFn("Error: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ComicBookGenerationTask::class.java)

    @JvmStatic
    val ComicBookGeneration = TaskType(
      name = "ComicBookGeneration",
      category = "Writing",
      taskClass = ComicBookGenerationTask::class.java,
      executionConfigClass = ComicBookGenerationTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generate comic book scripts and visuals",
      tooltipHtml = "Creates a comic book with page/row/frame structure and optional visual generation. Supports character reference images and plot continuity details.",
    )
  }
}