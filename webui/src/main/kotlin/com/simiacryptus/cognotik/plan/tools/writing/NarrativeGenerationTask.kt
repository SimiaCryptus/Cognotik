package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.reasoning.safeComplete
import com.simiacryptus.cognotik.plan.tools.reasoning.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.Writer
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO

open class NarrativeGenerationTask<T : NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData>(
    orchestrationConfig: OrchestrationConfig,
    planTask: T?
) : AbstractTask<T, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    open class NarrativeGenerationTaskExecutionConfigData(

        @Description("The subject or scenario to develop into a full narrative")
        val subject: String? = null,

        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context for the narrative")
        val input_files: List<String>? = null,

        @Description("Narrative elements to consider (characters, setting, conflict, timeline, etc.)")
        val narrative_elements: Map<String, Any>? = null,

        @Description("Target word count for the complete narrative")
        val target_word_count: Int = 5000,

        @Description("Number of acts in the narrative structure (typically 3 or 5)")
        val number_of_acts: Int = 3,

        @Description("Average number of scenes per act")
        val scenes_per_act: Int = 3,

        @Description("Writing style (e.g., 'literary', 'thriller', 'technical', 'conversational')")
        val writing_style: String = "literary",

        @Description("Point of view (e.g., 'first person', 'third person limited', 'third person omniscient')")
        val point_of_view: String = "third person limited",

        @Description("Tone (e.g., 'dramatic', 'humorous', 'suspenseful', 'reflective')")
        val tone: String = "dramatic",

        @Description("Whether to include detailed scene descriptions")
        val detailed_descriptions: Boolean = true,

        @Description("Whether to include character dialogue")
        val include_dialogue: Boolean = true,

        @Description("Whether to show internal character thoughts")
        val show_internal_thoughts: Boolean = true,

        @Description("Number of revision passes for each scene")
        val revision_passes: Int = 2,

        @Description("Whether to generate images for each scene")
        val generate_scene_images: Boolean = true,

        @Description("Whether to generate a cover image for the narrative")
        val generate_cover_image: Boolean = true,

        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ): TaskExecutionConfig(
        task_type = NarrativeGeneration.name,
        task_description = "Generate full narrative for '$subject'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            // Validate target_word_count
            if (target_word_count <= 0) {
                return "target_word_count must be positive, got: $target_word_count"
            }
            // Validate number_of_acts
            if (number_of_acts <= 0) {
                return "number_of_acts must be positive, got: $number_of_acts"
            }
            // Validate scenes_per_act
            if (scenes_per_act <= 0) {
                return "scenes_per_act must be positive, got: $scenes_per_act"
            }
            // Validate revision_passes
            if (revision_passes < 0) {
                return "revision_passes cannot be negative, got: $revision_passes"
            }
            return null
        }
    }

    data class NarrativeOutline(
        val title: String = "",
        val premise: String = "",
        val acts: List<ActOutline> = emptyList(),
        val estimated_word_count: Int = 0
    )

    data class ActOutline(
        val act_number: Int = 1,
        val title: String? = "",
        val purpose: String? = "",
        val scenes: List<SceneOutline>? = emptyList()
    )

    data class SceneOutline(
        val scene_number: Int = 1,
        val title: String = "",
        val setting: String = "",
        val characters: List<String> = emptyList(),
        val purpose: String = "",
        val key_events: List<String> = emptyList(),
        val emotional_arc: String = "",
        val estimated_word_count: Int = 0
    )

    data class GeneratedScene(
        val scene_number: Int = 1,
        val title: String = "",
        val content: String = "",
        val word_count: Int = 0,
        val key_moments: List<String> = emptyList(),
        val character_states: Map<String, String> = emptyMap()
    )

    override fun promptSegment(): String {
        return """
NarrativeGeneration - Generate complete narratives from analysis and outlines
  ** Extends NarrativeReasoning with full story generation
  ** Specify the subject or scenario to develop
  ** Define narrative elements: characters, setting, conflict, timeline
  ** Set target word count and structural parameters (acts, scenes)
  ** Configure writing style, POV, and tone
  ** Enable detailed descriptions, dialogue, and internal thoughts
  ** Performs analysis, creates outline, then writes each scene iteratively
  ** Each scene receives context from previous scenes
  ** Produces complete, coherent narrative with consistent style
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
        val transcript = task.transcript("NarrativeGeneration")?.let { OutputStreamWriter(it) }
        val genConfig = executionConfig
        log.info("Starting NarrativeGenerationTask for subject: '${genConfig?.subject}'")
        transcript?.write("# Narrative Generation Task\n\n")

        if (genConfig == null) {
            log.error("Invalid configuration type for NarrativeGenerationTask")
            task.safeComplete("CONFIGURATION ERROR: Invalid configuration type", log)
            resultFn("CONFIGURATION ERROR: Invalid configuration type")
            return
        }

        val subject = genConfig.subject
        if (subject.isNullOrBlank()) {
            log.error("No subject specified for narrative generation")
            task.safeComplete("CONFIGURATION ERROR: No subject specified", log)
            resultFn("CONFIGURATION ERROR: No subject specified")
            return
        }

        val api = orchestrationConfig.defaultChatter.getChildClient(task)

        val tabs = TabbedDisplay(task)
        // Get input file context
        agent.root.toFile()
        val inputFileContext = super.getInputFileContent(executionConfig.input_files, root, treatDocumentsAsText = true)
        if (inputFileContext.isNotBlank()) {
            transcript?.write("## Input Files Context\n\n$inputFileContext\n\n")
            transcript?.flush()
        }
        // Combine messages with input files
        val combinedMessages = messages + listOf(inputFileContext).filter { it.isNotBlank() }
        transcript?.write("## Input Messages\n\n${combinedMessages.joinToString("\n\n")}\n\n")
        transcript?.flush()


        // Overview tab
        val overviewTask = task.ui.newTask(false).apply { tabs["Overview"] = placeholder }
        val overviewContent = buildString {
            appendLine("# Narrative Generation")
            appendLine()
            appendLine("**Subject:** $subject")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Target Word Count: ${genConfig.target_word_count}")
            appendLine("- Structure: ${genConfig.number_of_acts} acts, ~${genConfig.scenes_per_act} scenes per act")
            appendLine("- Writing Style: ${genConfig.writing_style}")
            appendLine("- Point of View: ${genConfig.point_of_view}")
            appendLine("- Tone: ${genConfig.tone}")
            appendLine("- Detailed Descriptions: ${if (genConfig.detailed_descriptions) "✓" else "✗"}")
            appendLine("- Include Dialogue: ${if (genConfig.include_dialogue) "✓" else "✗"}")
            appendLine("- Internal Thoughts: ${if (genConfig.show_internal_thoughts) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Narrative Analysis")
            appendLine("*Running base narrative reasoning analysis...*")
        }
        overviewTask.add(overviewContent.renderMarkdown)
        transcript?.write("\n## Overview\n\n$overviewContent\n\n")
        transcript?.flush()
        task.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Generated Narrative: $subject\n\n")

        try {
            // Phase 1: Run the base narrative reasoning analysis
            log.info("Phase 1: Running narrative analysis")
            val analysisResult = StringBuilder()

            overviewTask.add("\n✅ Phase 1 Complete: Narrative analysis finished\n".renderMarkdown)
            overviewTask.add("\n### Phase 2: Outline Generation\n*Creating detailed scene-by-scene outline...*\n".renderMarkdown)
            task.update()

            // Phase 2: Generate detailed outline
            log.info("Phase 2: Generating narrative outline")
            val outlineTask = task.ui.newTask(false)
            tabs["Outline"] = outlineTask.placeholder

            outlineTask.add(
                buildString {
                    appendLine("# Narrative Outline")
                    appendLine()
                    appendLine("**Status:** Generating detailed scene-by-scene structure...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val totalScenes = genConfig.number_of_acts * genConfig.scenes_per_act
            val wordsPerScene = genConfig.target_word_count / totalScenes

            val parsingChatter = orchestrationConfig.parsingChatter.getChildClient(task)
            val outlineAgent = ParsedAgent(
                resultClass = NarrativeOutline::class.java,
                prompt = """
You are a master story architect. Based on the narrative analysis, create a detailed scene-by-scene outline.

Subject: $subject

Narrative Analysis:
${analysisResult.toString().truncateForDisplay(8000)}

Narrative Elements:
${genConfig.narrative_elements?.entries?.joinToString("\n") { (key, value) -> "- $key: $value" } ?: ""}

Create an outline with:
- ${genConfig.number_of_acts} acts
- Approximately ${genConfig.scenes_per_act} scenes per act (total ~$totalScenes scenes)
- Target: ${genConfig.target_word_count} total words (~$wordsPerScene words per scene)

For each scene, specify:
- Scene number and title
- Setting (time and place)
- Characters present
- Purpose (what this scene accomplishes)
- Key events (what happens)
- Emotional arc (how characters feel/change)
- Estimated word count

Ensure the outline:
- Follows classic story structure (setup, rising action, climax, falling action, resolution)
- Has clear cause-and-effect between scenes
- Builds tension and character development progressively
- Balances action, dialogue, and reflection
- Matches the ${genConfig.tone} tone and ${genConfig.writing_style} style
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = parsingChatter
            )

            val outline = outlineAgent.answer(listOf("Generate outline")).obj
            log.info("Generated outline: ${outline.acts.size} acts, ${outline.acts.sumOf { it.scenes?.size ?: 0 }} scenes")

            val outlineContent = buildString {
                appendLine("## ${outline.title}")
                appendLine()
                appendLine("**Premise:** ${outline.premise}")
                appendLine()
                appendLine("**Estimated Word Count:** ${outline.estimated_word_count}")
                appendLine()
                appendLine("---")
                appendLine()
                outline.acts.forEach { act ->
                    appendLine("### Act ${act.act_number}: ${act.title}")
                    appendLine()
                    appendLine("**Purpose:** ${act.purpose}")
                    appendLine()
                    act.scenes?.forEach { scene ->
                        appendLine("#### Scene ${scene.scene_number}: ${scene.title}")
                        appendLine()
                        appendLine("- **Setting:** ${scene.setting}")
                        appendLine("- **Characters:** ${scene.characters.joinToString(", ")}")
                        appendLine("- **Purpose:** ${scene.purpose}")
                        appendLine("- **Emotional Arc:** ${scene.emotional_arc}")
                        appendLine("- **Est. Words:** ${scene.estimated_word_count}")
                        appendLine()
                        appendLine("**Key Events:**")
                        scene.key_events.forEach { event ->
                            appendLine("- $event")
                        }
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }
            outlineTask.add(outlineContent.renderMarkdown)
            transcript?.write("\n## Outline\n\n$outlineContent\n\n")
            transcript?.flush()
            task.update()

            resultBuilder.append("## ${outline.title}\n\n")
            resultBuilder.append("${outline.premise}\n\n")
            resultBuilder.append("---\n\n")

            overviewTask.add("✅ Phase 2 Complete: Outline created (${outline.acts.sumOf { it.scenes?.size ?: 0 }} scenes)\n".renderMarkdown)
            overviewTask.add("\n### Phase 3: Scene Generation\n*Writing scenes iteratively with context...*\n".renderMarkdown)
            task.update()
            // Generate cover image if enabled
            if (genConfig.generate_cover_image) {
                generateCoverImage(
                    task = task,
                    tabs = tabs,
                    title = outline.title,
                    premise = outline.premise,
                    transcriptWriter = transcript,
                    orchestrationConfig = orchestrationConfig
                )
            }
            // Phase 2.5: Generate setting and character images if enabled
            val allScenes = outline.acts.flatMap { it.scenes ?: emptyList() } ?: emptyList()
            val settingImages = mutableMapOf<String, String>() // setting name -> image path
            val characterImages = mutableMapOf<String, String>() // character name -> image path
            if (genConfig.generate_scene_images) {
                log.info("Phase 2.5: Generating setting and character reference images")
                overviewTask.add("\n### Phase 2.5: Generating Reference Images\n*Creating setting and character visualizations...*\n".renderMarkdown)
                task.update()
                // Extract unique settings from all scenes
                val uniqueSettings = allScenes.map { it.setting }.distinct()
                log.info("Generating images for ${uniqueSettings.size} unique settings")
                uniqueSettings.forEach { setting ->
                    val settingImagePath = generateSettingImage(
                        task = task,
                        tabs = tabs,
                        setting = setting,
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig
                    )
                    if (settingImagePath != null) {
                        settingImages[setting] = settingImagePath
                    }
                }
                // Extract unique characters from all scenes
                val uniqueCharacters = allScenes.flatMap { it.characters }.distinct()
                log.info("Generating images for ${uniqueCharacters.size} unique characters")
                uniqueCharacters.forEach { character ->
                    val characterImagePath = generateCharacterImage(
                        task = task,
                        tabs = tabs,
                        character = character,
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig
                    )
                    if (characterImagePath != null) {
                        characterImages[character] = characterImagePath
                    }
                }
                overviewTask.add("✅ Phase 2.5 Complete: Generated ${settingImages.size} settings and ${characterImages.size} characters\n".renderMarkdown)
                task.update()
            }


            // Phase 3: Generate each scene iteratively
            log.info("Phase 3: Generating scenes")
            val generatedScenes = mutableListOf<GeneratedScene>()
            var cumulativeWordCount = 0

            allScenes.forEachIndexed { index, sceneOutline ->
                log.info("Generating scene ${index + 1}/${allScenes.size}: ${sceneOutline?.title}")

                overviewTask.add("- Scene ${sceneOutline?.scene_number}: ${sceneOutline?.title} ".renderMarkdown)
                task.update()

                val sceneTask = task.ui.newTask(false)
                tabs["Scene ${sceneOutline.scene_number}"] = sceneTask.placeholder

                sceneTask.add(
                    buildString {
                        appendLine("# Scene ${sceneOutline.scene_number}: ${sceneOutline.title}")
                        appendLine()
                        appendLine("**Status:** Writing scene...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                // Build context from previous scenes
                val recentContext = if (generatedScenes.size > 0) {
                    val lastScenes = generatedScenes.takeLast(2)
                    buildString {
                        appendLine("## Previous Scene Context")
                        lastScenes.forEach { prevScene ->
                            appendLine("### Scene ${prevScene.scene_number}: ${prevScene.title}")
                            appendLine("**Key Moments:**")
                            prevScene.key_moments.forEach { moment ->
                                appendLine("- $moment")
                            }
                            appendLine()
                            appendLine("**Character States:**")
                            prevScene.character_states.forEach { (char, state) ->
                                appendLine("- $char: $state")
                            }
                            appendLine()
                            if (prevScene == lastScenes.last()) {
                                appendLine("**Last Scene Ending:**")
                                appendLine(prevScene.content.takeLast(500))
                                appendLine()
                            }
                        }
                    }
                } else {
                    "This is the opening scene."
                }

                val sceneAgent = ParsedAgent(
                    resultClass = GeneratedScene::class.java,
                    prompt = """
You are a skilled ${genConfig.writing_style} writer. Write Scene ${sceneOutline.scene_number} of the narrative.

Overall Story: ${outline.title}
Premise: ${outline.premise}

Scene Outline:
- Title: ${sceneOutline.title}
- Setting: ${sceneOutline.setting}
- Characters: ${sceneOutline.characters.joinToString(", ")}
- Purpose: ${sceneOutline.purpose}
- Emotional Arc: ${sceneOutline.emotional_arc}
- Target Word Count: ${sceneOutline.estimated_word_count}

Key Events to Include:
${sceneOutline.key_events.joinToString("\n") { "- $it" }}

$recentContext

Writing Guidelines:
- Point of View: ${genConfig.point_of_view}
- Tone: ${genConfig.tone}
- Style: ${genConfig.writing_style}
${if (genConfig.detailed_descriptions) "- Include vivid, sensory descriptions of setting and action" else "- Keep descriptions concise"}
${if (genConfig.include_dialogue) "- Include natural, character-appropriate dialogue" else "- Minimize dialogue, focus on narration"}
${if (genConfig.show_internal_thoughts) "- Show character internal thoughts and feelings" else "- Show emotions through action and dialogue only"}

Write the complete scene with:
- A strong opening that connects to the previous scene (if any)
- Clear progression through the key events
- Character development and emotional depth
- Sensory details and atmosphere
- A compelling ending that sets up the next scene
- Approximately ${sceneOutline.estimated_word_count} words

After writing, provide:
- The scene content
- Actual word count
- Key moments (3-5 bullet points of what happened)
- Character states (how each character ends the scene emotionally/physically)

Make the writing engaging, immersive, and true to the characters and story.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = parsingChatter
                )

                var generatedScene = sceneAgent.answer(listOf("Write the scene")).obj

                // Optional revision pass
                if (genConfig.revision_passes > 0) {
                    repeat(genConfig.revision_passes) { revisionNum ->
                        log.debug("Revision pass ${revisionNum + 1} for scene ${sceneOutline.scene_number}")

                        val revisionAgent = ChatAgent(
                            prompt = """
You are an expert editor. Review and improve this scene while maintaining its core events and purpose.

Scene ${sceneOutline.scene_number}: ${sceneOutline.title}

Current Version:
${generatedScene.content}

Improve:
- Prose quality and flow
- Character voice consistency
- Sensory details and atmosphere
- Pacing and tension
- Dialogue naturalness
- Emotional impact

Maintain:
- All key events and plot points
- Character states and development
- Word count (currently ${generatedScene.word_count}, target ${sceneOutline.estimated_word_count})
- Tone and style

Provide the revised scene content only.
              """.trimIndent(),
                            model = api,
                            temperature = 0.7
                        )

                        val revisedContent = revisionAgent.answer(listOf("Revise the scene"))
                        generatedScene = generatedScene.copy(
                            content = revisedContent,
                            word_count = revisedContent.split("\\s+".toRegex()).size
                        )
                    }
                }

                generatedScenes.add(generatedScene)
                cumulativeWordCount += generatedScene.word_count

                val sceneContent = buildString {
                    appendLine("## ${sceneOutline.title}")
                    appendLine()
                    appendLine("**Setting:** ${sceneOutline.setting}")
                    appendLine()
                    appendLine("**Characters:** ${sceneOutline.characters.joinToString(", ")}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(generatedScene.content)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${generatedScene.word_count}")
                    appendLine()
                    appendLine("**Key Moments:**")
                    generatedScene.key_moments.forEach { moment ->
                        appendLine("- $moment")
                    }
                    appendLine()
                    appendLine("**Character States:**")
                    generatedScene.character_states.forEach { (char, state) ->
                        appendLine("- **$char:** $state")
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                sceneTask.add(sceneContent.renderMarkdown)
                transcript?.write("\n## $sceneContent\n\n")
                transcript?.flush()
                task.update()

                // Add to result
                resultBuilder.append("## Scene ${sceneOutline.scene_number}: ${sceneOutline.title}\n\n")
                resultBuilder.append(generatedScene.content)
                resultBuilder.append("\n\n---\n\n")

                overviewTask.add("✅ (${generatedScene.word_count} words)\n".renderMarkdown)
                task.update()
                // Generate scene image if enabled
                if (genConfig.generate_scene_images) {
                    generateSceneImage(
                        task = task,
                        tabs = tabs,
                        sceneNumber = sceneOutline.scene_number,
                        sceneTitle = sceneOutline.title,
                        sceneContent = generatedScene.content,
                        setting = sceneOutline.setting,
                        settingImagePath = settingImages[sceneOutline.setting],
                        characterImagePaths = sceneOutline.characters.mapNotNull { char ->
                            char to (characterImages[char] ?: return@mapNotNull null)
                        }.toMap(),
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig
                    )
                }
            }

            overviewTask.add("\n✅ Phase 3 Complete: All scenes generated\n".renderMarkdown)
            overviewTask.add("\n### Phase 4: Final Assembly\n*Compiling complete narrative...*\n".renderMarkdown)
            task.update()

            // Phase 4: Create final compiled version
            log.info("Phase 4: Assembling final narrative")
            val finalTask = task.ui.newTask(false)
            tabs["Complete Narrative"] = finalTask.placeholder

            val finalNarrative = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine("*${outline.premise}*")
                appendLine()
                appendLine("---")
                appendLine()

                outline.acts.forEach { act ->
                    appendLine("# Act ${act.act_number}: ${act.title}")
                    appendLine()

                    val actScenes = generatedScenes.filter { scene ->
                        act.scenes?.any { it.scene_number == scene.scene_number } == true
                    }

                    actScenes.forEach { scene ->
                        appendLine("## ${scene.title}")
                        appendLine()
                        appendLine(scene.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                    }
                }

                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**THE END**")
                appendLine()
                appendLine("*Total Word Count: $cumulativeWordCount*")
            }

            finalTask.add(finalNarrative.renderMarkdown)
            task.update()

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            val avgWordsPerScene = if (generatedScenes.isNotEmpty()) cumulativeWordCount / generatedScenes.size else 0

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Generation Complete")
                    appendLine()
                    appendLine("**Statistics:**")
                    appendLine("- Total Scenes: ${generatedScenes.size}")
                    appendLine("- Total Word Count: $cumulativeWordCount")
                    appendLine("- Average Words/Scene: $avgWordsPerScene")
                    appendLine("- Target Word Count: ${genConfig.target_word_count}")
                    appendLine("- Completion: ${(cumulativeWordCount.toFloat() / genConfig.target_word_count * 100).toInt()}%")
                    appendLine("- Total Time: ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown
            )
            task.update()
            transcript?.write("\n## Final Statistics\n\n- Total Scenes: ${generatedScenes.size}\n- Total Word Count: $cumulativeWordCount\n- Time: ${totalTime / 1000.0}s\n\n")
            transcript?.close()

            val finalResult = buildString {
                appendLine("# Narrative Generation Summary: ${outline.title}")
                appendLine()
                appendLine("A complete narrative of **$cumulativeWordCount words** across **${generatedScenes.size} scenes** was generated in **${totalTime / 1000.0}s**.")
                appendLine("> The full narrative and detailed transcript are available in the UI tabs for review.")
                appendLine()
                appendLine(outlineContent.substringBeforeLast("\n**Status:**").trim())
            }
            log.info("NarrativeGenerationTask completed: scenes=${generatedScenes.size}, words=$cumulativeWordCount, time=${totalTime}ms")
            task.safeComplete(
                "Narrative generation complete: ${generatedScenes.size} scenes, $cumulativeWordCount words in ${totalTime / 1000}s",
                log
            )
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during narrative generation", e)
            task.error(e)

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    appendLine("**Type:** ${e.javaClass.simpleName}")
                }.renderMarkdown
            )
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Narrative Generation")
                appendLine()
                appendLine("**Subject:** $subject")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                if (resultBuilder.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(resultBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
    }

    private fun generateCoverImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        title: String,
        premise: String,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating cover image for: $title")
            val task = task.ui.newTask(false)
            tabs["Cover Image"] = task.placeholder
            task.add(
                buildString {
                    appendLine("# Cover Image")
                    appendLine()
                    appendLine("**Status:** Generating cover image...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()
            val imageAgent = ImageProcessingAgent(
                prompt = "Create a compelling book cover image that captures the essence of this narrative",
                model = orchestrationConfig.imageChatChatter.getChildClient(task),
                temperature = 0.8,
            )
            val coverPrompt = "$title: $premise"
            val result = imageAgent.answer(listOf(ImageAndText(coverPrompt)))
            val image = result.image
            // Save image
            val imageFile = task.resolveUserFile("00_cover_image.png")!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved cover image to: ${imageFile.absolutePath}")
            // Create display link
            val link = task.linkTo("00_cover_image.png")
            val imageHtml = """
        <div class='cover-image'>
          <h3>$title</h3>
          <p><em>$premise</em></p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Cover' style='max-width: 600px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.2);' />
          </a>
        </div>
      """.trimIndent()
            task.add(imageHtml.renderMarkdown)
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("## Cover Image")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Cover Image]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            task.add("\n**Status:** ✅ Complete\n".renderMarkdown)
            task.update()
        } catch (e: Exception) {
            log.error("Failed to generate cover image", e)
            transcriptWriter?.appendLine("**Cover Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
        }
    }

    private fun generateSettingImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        setting: String,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig
    ): String? {
        return try {
            log.info("Generating reference image for setting: $setting")
            val task = task.ui.newTask(false)
            tabs["Setting: $setting"] = task.placeholder
            task.add(
                buildString {
                    appendLine("# Setting Reference: $setting")
                    appendLine()
                    appendLine("**Status:** Generating setting visualization...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()
            val imageAgent = ImageProcessingAgent(
                prompt = "Create a detailed, atmospheric image of this setting that captures its essence and mood",
                model = orchestrationConfig.imageChatChatter.getChildClient(task),
                temperature = 0.7,
            )
            val result = imageAgent.answer(listOf(ImageAndText(setting)))
            val image = result.image
            // Save image with sanitized filename
            val sanitizedName = setting.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val relativePath = "setting_${sanitizedName}_ref.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved setting reference image to: ${imageFile.absolutePath}")
            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='setting-reference'>
          <h4>$setting</h4>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='$setting' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            task.add(imageHtml.renderMarkdown)
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Setting: $setting")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Setting: $setting]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            task.add("\n**Status:** ✅ Complete\n".renderMarkdown)
            task.update()
            relativePath
        } catch (e: Exception) {
            log.error("Failed to generate setting reference image for: $setting", e)
            transcriptWriter?.appendLine("**Setting Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
            null
        }
    }

    private fun generateCharacterImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        character: String,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig
    ): String? {
        return try {
            log.info("Generating reference image for character: $character")
            val task = task.ui.newTask(false)
            tabs["Character: $character"] = task.placeholder
            task.add(
                buildString {
                    appendLine("# Character Reference: $character")
                    appendLine()
                    appendLine("**Status:** Generating character visualization...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()
            val imageAgent = ImageProcessingAgent(
                prompt = "Create a detailed character portrait that captures their appearance, personality, and essence",
                model = orchestrationConfig.imageChatChatter.getChildClient(task),
                temperature = 0.7,
            )
            val result = imageAgent.answer(listOf(ImageAndText(character)))
            val image = result.image
            // Save image with sanitized filename
            val sanitizedName = character.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val relativePath = "character_${sanitizedName}_ref.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved character reference image to: ${imageFile.absolutePath}")
            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='character-reference'>
          <h4>$character</h4>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='$character' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            task.add(imageHtml.renderMarkdown)
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Character: $character")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Character: $character]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            task.add("\n**Status:** ✅ Complete\n".renderMarkdown)
            task.update()
            relativePath
        } catch (e: Exception) {
            log.error("Failed to generate character reference image for: $character", e)
            transcriptWriter?.appendLine("**Character Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
            null
        }
    }

    private fun generateSceneImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        sceneNumber: Int,
        sceneTitle: String,
        sceneContent: String,
        setting: String,
        settingImagePath: String?,
        characterImagePaths: Map<String, String>,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating image for scene $sceneNumber: $sceneTitle")
            val sceneImageTask = task.ui.newTask(false)
            tabs["Scene $sceneNumber Image"] = sceneImageTask.placeholder
            sceneImageTask.add(
                buildString {
                    appendLine("# Scene $sceneNumber Image")
                    appendLine()
                    appendLine("**Status:** Generating scene visualization...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()
            val imageAgent = ImageProcessingAgent(
                prompt = "Create a cinematic scene illustration that captures the key moment and atmosphere",
                model = orchestrationConfig.imageChatChatter,
                temperature = 0.7,
            )
            // Extract key visual elements from scene
            val scenePrompt = buildString {
                append("Scene: $sceneTitle. ")
                append("Setting: $setting. ")
                // Take first 500 chars of scene content for context
                append(sceneContent.take(500))
            }
            // Build input with reference images
            val imageInputs = mutableListOf<ImageAndText>()

            // Add setting reference image if available
            if (settingImagePath != null) {
                try {
                    val settingImage = ImageIO.read(task.resolveUserFile(settingImagePath))
                    if (settingImage != null) {
                        imageInputs.add(
                            ImageAndText(
                                text = "Reference setting image for: $setting",
                                image = settingImage
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load setting reference image: $settingImagePath", e)
                }
            }

            // Add character reference images if available
            characterImagePaths.forEach { (character, imagePath) ->
                try {
                    val charImage = ImageIO.read(task.resolveUserFile(imagePath))
                    if (charImage != null) {
                        imageInputs.add(
                            ImageAndText(
                                text = "Reference character image for: $character",
                                image = charImage
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load character reference image: $imagePath", e)
                }
            }

            // Add the scene prompt
            imageInputs.add(ImageAndText(scenePrompt))

            val result = imageAgent.answer(imageInputs)
            val image = result.image
            // Save image
            val relativePath = "scene_${sceneNumber}_image.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved scene image to: ${imageFile.absolutePath}")
            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='scene-image'>
          <h4>Scene $sceneNumber: $sceneTitle</h4>
          <p><strong>Setting:</strong> $setting</p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Scene $sceneNumber' style='max-width: 600px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            sceneImageTask.add(imageHtml.renderMarkdown)
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Scene $sceneNumber Image")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Scene $sceneNumber]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            sceneImageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown)
            task.update()
        } catch (e: Exception) {
            log.error("Failed to generate scene image for scene $sceneNumber", e)
            transcriptWriter?.appendLine("**Scene Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
        }
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(NarrativeGenerationTask::class.java)
        val NarrativeGeneration = TaskType(
            "NarrativeGeneration",
            NarrativeGenerationTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate complete narratives from analysis and outlines",
            """
              Extends NarrativeReasoning to generate complete, publication-ready narratives.
              <ul>
                <li>Performs comprehensive narrative analysis (inherited from NarrativeReasoning)</li>
                <li>Creates detailed scene-by-scene outline based on analysis</li>
                <li>Generates each scene iteratively with full context</li>
                <li>Maintains consistency by feeding previous scenes into each generation</li>
                <li>Supports configurable structure (acts, scenes, word count)</li>
                <li>Customizable writing style, POV, tone, and narrative elements</li>
                <li>Optional revision passes for quality improvement</li>
                <li>Produces complete, coherent narrative with consistent style and voice</li>
                <li>Ideal for story generation, scenario planning, user journey narratives</li>
              </ul>
            """
        )
    }
}