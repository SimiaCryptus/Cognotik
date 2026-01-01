package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.CodeAgent.Companion.indent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.OutputStreamWriter
import java.io.Writer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
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
    ) : TaskExecutionConfig(
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
        val characters: List<CharacterProfile> = emptyList(),
        val settings: List<SettingProfile> = emptyList(),
        val acts: List<ActOutline> = emptyList(),
        val estimated_word_count: Int = 0
    )

    data class CharacterProfile(
        val name: String = "",
        val description: String = "",
        val role: String = "",
        val traits: List<String> = emptyList()
    )

    data class SettingProfile(
        val setting_id: String = "",
        val description: String = "",
        val atmosphere: String = "",
        val significance: String = ""
    )

    data class HighLevelOutline(
        val title: String = "",
        val premise: String = "",
        val characters: List<CharacterProfile> = emptyList(),
        val settings: List<SettingProfile> = emptyList(),
        val acts: List<ActSummary> = emptyList(),
        val estimated_word_count: Int = 0
    )

    data class ActSummary(
        val act_number: Int = 1,
        val title: String = "",
        val purpose: String = "",
        val key_developments: List<String> = emptyList(),
        val estimated_scenes: Int = 3
    )


    data class ActOutline(
        val act_number: Int = 1,
        val title: String? = "",
        val purpose: String? = "",
        val scenes: List<SceneOutline>? = emptyList()
    )

    data class SceneOutline(
        val act_number: Int = 1,
        val scene_number: Int = 1,
        val title: String = "",
        val setting_id: String = "",
        val characters: List<String> = emptyList(),
        val purpose: String = "",
        val key_events: List<String> = emptyList(),
        val emotional_arc: String = "",
        val estimated_word_count: Int = 0
    )

    data class GeneratedScene(
        val scene_number: Int = 1,
        val act_number: Int = 1,
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
        log.info("Starting NarrativeGenerationTask - Subject: '${executionConfig?.subject}', Target words: ${executionConfig?.target_word_count}")
        val transcript = task.transcript("NarrativeGeneration")?.let { OutputStreamWriter(it) }
        val genConfig = executionConfig
        transcript?.write("# Narrative Generation Task\n\n")

        if (genConfig == null) {
            log.error("Invalid configuration type for NarrativeGenerationTask")
            transcript?.write("ERROR: Invalid configuration type\n")
            transcript?.close()
            task.safeComplete("CONFIGURATION ERROR: Invalid configuration type", log)
            resultFn("CONFIGURATION ERROR: Invalid configuration type")
            return
        }

        val subject = genConfig.subject
        if (subject.isNullOrBlank()) {
            log.error("No subject specified for narrative generation")
            transcript?.write("ERROR: No subject specified\n")
            transcript?.close()
            task.safeComplete("CONFIGURATION ERROR: No subject specified", log)
            resultFn("CONFIGURATION ERROR: No subject specified")
            return
        }
        log.debug("Configuration validated - Acts: ${genConfig.number_of_acts}, Scenes/Act: ${genConfig.scenes_per_act}, Style: ${genConfig.writing_style}")

        val api = defaultSmart.getChildClient(task)

        val tabs = TabbedDisplay(task)
        // Get input file context
        val inputFileContext = try {
            log.debug("Loading input files: ${executionConfig.input_files?.joinToString(", ") ?: "none"}")
            super.getInputFileContent(executionConfig.input_files, root, treatDocumentsAsText = true)
        } catch (e: Exception) {
            log.error("Failed to load input files", e)
            transcript?.write("WARNING: Failed to load input files: ${e.message}\n\n")
            transcript?.flush()
            ""
        }

        if (inputFileContext.isNotBlank()) {
            log.debug("Loaded input file context: ${inputFileContext.length} characters")
            transcript?.write("## Input Files Context\n\n$inputFileContext\n\n")
            transcript?.flush()
            task.expandable("Input File Context", "<pre>${inputFileContext.truncateForDisplay(5000)}</pre>")
        }
        // Combine messages with input files
        val combinedMessages = messages + listOf(inputFileContext).filter { it.isNotBlank() }
        transcript?.write("## Input Messages\n\n${combinedMessages.joinToString("\n\n")}\n\n")
        transcript?.flush()


        // Overview tab
        val overviewTask = tabs.newTask("Overview")
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
        overviewTask.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Generated Narrative: $subject\n\n")

        try {
            // Phase 1: Run the base narrative reasoning analysis
            log.info("Phase 1: Running narrative analysis")
            val analysisResult = StringBuilder()

            overviewTask.add("\n✅ Phase 1 Complete: Narrative analysis finished\n".renderMarkdown)
            overviewTask.add("\n### Phase 2: Outline Generation\n*Creating detailed scene-by-scene outline...*\n".renderMarkdown)
            overviewTask.update()

// Phase 2: Generate detailed outline
            log.info("Phase 2: Generating narrative outline")
            val outlineTask = tabs.newTask("Outline")

            outlineTask.add(
                buildString {
                    appendLine("# Narrative Outline")
                    appendLine()
                    appendLine("**Status:** Pass 1 - Generating high-level structure...")
                    appendLine()
                }.renderMarkdown
            )
            outlineTask.update()

            val totalScenes = genConfig.number_of_acts * genConfig.scenes_per_act
            val wordsPerScene = genConfig.target_word_count / totalScenes

            val parsingChatter = defaultFast.getChildClient(task)
            // Generate cover image first if enabled (to use as seed for other images)
            var coverImagePath: String? = null
            if (genConfig.generate_cover_image || genConfig.generate_scene_images) {
                log.info("Phase 2.0: Generating cover image to use as visual seed")
                overviewTask.add("\n### Phase 2.0: Generating Cover Image\n*Creating visual foundation for the narrative...*\n".renderMarkdown)
                overviewTask.update()
                coverImagePath = generateCoverImage(
                    task = task,
                    tabs = tabs,
                    title = subject,
                    premise = analysisResult.toString().take(500),
                    transcriptWriter = transcript,
                    orchestrationConfig = orchestrationConfig
                )
                if (coverImagePath != null) {
                    overviewTask.add("✅ Phase 2.0 Complete: Cover image generated and will be used as visual seed\n".renderMarkdown)
                } else {
                    overviewTask.add("⚠️ Phase 2.0: Cover image generation failed, proceeding without visual seed\n".renderMarkdown)
                }
                overviewTask.update()
            }

            // Pass 1: High-level outline with characters and settings
            log.info("Phase 2.1: Generating high-level outline")

            val highLevelAgent = ParsedAgent(
                resultClass = HighLevelOutline::class.java,
                prompt = """
You are a master story architect. Based on the narrative analysis, create a high-level narrative structure.

Subject: $subject

Narrative Analysis:
${analysisResult.toString().truncateForDisplay(8000)}

Narrative Elements:
${genConfig.narrative_elements?.entries?.joinToString("\n") { (key, value) -> "- $key: $value" } ?: ""}

- ${genConfig.number_of_acts} acts
- Approximately ${genConfig.scenes_per_act} scenes per act (total ~$totalScenes scenes)
- Target: ${genConfig.target_word_count} total words (~$wordsPerScene words per scene)

For each scene, specify:
- Scene number and title
- Purpose (what this scene accomplishes)
- Key events (what happens)
- Emotional arc (how characters feel/change)
- Setting (choose from available settings or describe a new one)
- Characters present (from the character list)
- Estimated word count (~$wordsPerScene words)

Ensure the outline:
- Has clear cause-and-effect between scenes
- Matches the ${genConfig.tone} tone and ${genConfig.writing_style} style


Create a high-level outline with:
1. **Characters**: Define all major characters with:
   - Name
   - Detailed description (appearance, personality, background)
   - Role in the story (protagonist, antagonist, supporting, etc.)
   - Key traits and motivations
2. **Settings**: Define all major locations/settings with:
   - Name
   - Detailed description (visual details, atmosphere)
   - Atmosphere/mood
   - Significance to the story
3. **Act Structure**: Create ${genConfig.number_of_acts} acts with:
   - Act number and title
   - Purpose (what this act accomplishes in the story)
   - Key developments (major plot points and character changes)
   - Estimated number of scenes (approximately ${genConfig.scenes_per_act} per act)
Target: ${genConfig.target_word_count} total words
Style: ${genConfig.writing_style}
Tone: ${genConfig.tone}
POV: ${genConfig.point_of_view}
Ensure the structure:
- Has well-defined, memorable characters
- Uses vivid, atmospheric settings
- Follows classic story structure (setup, rising action, climax, falling action, resolution)
- Builds tension and stakes progressively
- Matches the ${genConfig.tone} tone and ${genConfig.writing_style} style
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = parsingChatter
            )

            val highLevelOutline = try {
                highLevelAgent.answer(listOf("Generate high-level outline")).obj
            } catch (e: Exception) {
                log.error("Failed to generate high-level outline", e)
                transcript?.write("ERROR: Failed to generate high-level outline: ${e.message}\n\n")
                transcript?.flush()
                throw RuntimeException("Failed to generate high-level outline", e)
            }

            log.info("Generated high-level outline: ${highLevelOutline.acts.size} acts, ${highLevelOutline.characters.size} characters, ${highLevelOutline.settings.size} settings")

            // Display high-level outline
            val highLevelContent = buildString {
                appendLine("## ${highLevelOutline.title}")
                appendLine()
                appendLine("**Premise:** ${highLevelOutline.premise}")
                appendLine()
                appendLine("**Estimated Word Count:** ${highLevelOutline.estimated_word_count}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Characters")
                appendLine()
                highLevelOutline.characters.forEach { char ->
                    appendLine("#### ${char.name}")
                    appendLine()
                    appendLine("**Role:** ${char.role}")
                    appendLine()
                    appendLine("**Description:** ${char.description}")
                    appendLine()
                    appendLine("**Traits:** ${char.traits.joinToString(", ")}")
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Settings")
                appendLine()
                highLevelOutline.settings.forEach { setting ->
                    appendLine("#### ${setting.setting_id}")
                    appendLine()
                    appendLine("**Description:** ${setting.description}")
                    appendLine()
                    appendLine("**Atmosphere:** ${setting.atmosphere}")
                    appendLine()
                    appendLine("**Significance:** ${setting.significance}")
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Act Structure")
                appendLine()
                highLevelOutline.acts.forEach { act ->
                    appendLine("#### Act ${act.act_number}: ${act.title}")
                    appendLine()
                    appendLine("**Purpose:** ${act.purpose}")
                    appendLine()
                    appendLine("**Estimated Scenes:** ${act.estimated_scenes}")
                    appendLine()
                    appendLine("**Key Developments:**")
                    act.key_developments.forEach { dev ->
                        appendLine("- $dev")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("**Status:** ✅ Pass 1 Complete")
            }
            outlineTask.add(highLevelContent.renderMarkdown)
            transcript?.write("\n## High-Level Outline\n\n$highLevelContent\n\n")
            transcript?.flush()
            outlineTask.update()
// Pass 2: Expand acts into detailed scenes
            log.info("Phase 2.2: Expanding acts into scenes")
            outlineTask.add("\n**Status:** Pass 2 - Expanding acts into detailed scenes...\n".renderMarkdown)
            outlineTask.update()

            val detailedActs = mutableListOf<ActOutline>()
            highLevelOutline.acts.forEach { actSummary ->
                log.info("Expanding Act ${actSummary.act_number}: ${actSummary.title}")

                val sceneExpansionAgent = ParsedAgent(
                    resultClass = ActOutline::class.java,
                    prompt = """
You are a master story architect. Expand this act into detailed scenes.

**High-Level Narrative Context:**
  ${highLevelOutline.toJson().indent("  ")}

**Act:**
  ${actSummary.toJson().indent("  ")}

**Previous Acts Context:**
  ${
                        detailedActs.joinToString("\n") { act -> "Act ${act.act_number}: ${act.title} - ${act.scenes?.size ?: 0} scenes" }
                            .indent("  ")
                    }

Create approximately ${actSummary.estimated_scenes} scenes for this act. For each scene specify:
- Fulfills the act's purpose and key developments
- Appropriate setting_id from defined settings
- Characters present from defined characters
          """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = parsingChatter
                )
                try {
                    val expandedAct = sceneExpansionAgent.answer(listOf("Expand act into scenes")).obj
                    // Add act number to each scene
                    val actWithNumberedScenes = expandedAct.copy(
                        scenes = expandedAct.scenes?.map { scene ->
                            scene.copy(act_number = actSummary.act_number)
                        }
                    )
                    detailedActs.add(actWithNumberedScenes)
                    log.debug("Expanded Act ${actSummary.act_number} into ${expandedAct.scenes?.size ?: 0} scenes")
                } catch (e: Exception) {
                    log.error("Failed to expand Act ${actSummary.act_number}", e)
                    transcript?.write("ERROR: Failed to expand Act ${actSummary.act_number}: ${e.message}\n\n")
                    transcript?.flush()
                    throw RuntimeException("Failed to expand Act ${actSummary.act_number}", e)
                }
            }

            // Combine into final outline
            val outline = NarrativeOutline(
                title = highLevelOutline.title,
                premise = highLevelOutline.premise,
                characters = highLevelOutline.characters,
                settings = highLevelOutline.settings,
                acts = detailedActs,
                estimated_word_count = highLevelOutline.estimated_word_count
            )
            log.info("Complete outline: ${outline.acts.size} acts, ${outline.acts.sumOf { it.scenes?.size ?: 0 }} scenes")

            // Display complete detailed outline
            val outlineContent = buildString {
                appendLine("## ${outline.title}")
                appendLine()
                appendLine("**Premise:** ${outline.premise}")
                appendLine()
                appendLine("**Estimated Word Count:** ${outline.estimated_word_count}")
                appendLine()
                appendLine("**Total Scenes:** ${outline.acts.sumOf { it.scenes?.size ?: 0 }}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Detailed Scene Breakdown")
                appendLine()
                outline.acts.forEach { act ->
                    appendLine("### Act ${act.act_number}: ${act.title}")
                    appendLine()
                    appendLine("**Purpose:** ${act.purpose}")
                    appendLine()
                    act.scenes?.forEach { scene ->
                        appendLine("#### Scene ${scene.scene_number}: ${scene.title}")
                        appendLine()
                        appendLine("- **Setting:** ${scene.setting_id}")
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
            outlineTask.update()
            outlineTask.complete()

            resultBuilder.append("## ${outline.title}\n\n")
            resultBuilder.append("${outline.premise}\n\n")
            resultBuilder.append("---\n\n")

            overviewTask.add("✅ Phase 2 Complete: Outline created (${outline.acts.sumOf { it.scenes?.size ?: 0 }} scenes)\n".renderMarkdown)
            overviewTask.add("\n### Phase 3: Scene Generation\n*Writing scenes iteratively with context...*\n".renderMarkdown)
            overviewTask.update()

            // Phase 2.5: Generate setting and character images if enabled
            val allScenes = outline.acts.flatMap { it.scenes ?: emptyList() } ?: emptyList()
            val settingImages = mutableMapOf<String, String>()
            val characterImages = mutableMapOf<String, String>()

            if (genConfig.generate_scene_images) {
                log.info("Phase 2.5: Generating setting and character reference images")
                overviewTask.add("\n### Phase 2.5: Generating Reference Images\n*Creating setting and character visualizations...*\n".renderMarkdown)
                overviewTask.update()

                // Generate images for defined settings
                log.info("Generating images for ${outline.settings.size} settings")
                outline.settings.forEach { setting ->
                    val settingImagePath = generateSettingImage(
                        task = task,
                        tabs = tabs,
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig,
                        settingProfile = setting,
                        coverImagePath = coverImagePath
                    )
                    if (settingImagePath != null) {
                        settingImages[setting.setting_id] = settingImagePath
                    }
                }

                // Generate images for defined characters
                log.info("Generating images for ${outline.characters.size} characters")
                outline.characters.forEach { character ->
                    val characterImagePath = generateCharacterImage(
                        task = task,
                        tabs = tabs,
                        characterProfile = character,
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig,
                        coverImagePath = coverImagePath
                    )
                    if (characterImagePath != null) {
                        characterImages[character.name] = characterImagePath
                    }
                }
                overviewTask.add("✅ Phase 2.5 Complete: Generated ${settingImages.size} setting images and ${characterImages.size} character images\n".renderMarkdown)
                overviewTask.update()
            }


            // Phase 3: Generate each scene iteratively
            log.info("Phase 3: Generating scenes")
            val generatedScenes = mutableListOf<GeneratedScene>()
            var cumulativeWordCount = 0

            allScenes.forEachIndexed { index, sceneOutline ->
                log.info("Generating Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}/${allScenes.size}: ${sceneOutline?.title}")

                overviewTask.add("- Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline?.title} ".renderMarkdown)
                overviewTask.update()

                val sceneTask = tabs.newTask("Act ${sceneOutline.act_number} Scene ${sceneOutline.scene_number}")

                sceneTask.add(
                    buildString {
                        appendLine("# Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}")
                        appendLine()
                        appendLine("**Status:** Writing scene...")
                        appendLine()
                    }.renderMarkdown
                )
                sceneTask.update()

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
This is Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}.

 Overall Story: ${outline.title}
Premise: ${outline.premise}

Scene Outline:
  ${sceneOutline.toJson().indent("  ")}

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
                // Ensure act number is preserved
                generatedScene = generatedScene.copy(act_number = sceneOutline.act_number)

                // Optional revision pass
                if (genConfig.revision_passes > 0) {
                    repeat(genConfig.revision_passes) { revisionNum ->
                        log.debug("Revision pass ${revisionNum + 1} for Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}")

                        val revisionAgent = ChatAgent(
                            prompt = """
 You are an expert editor. Review and improve this scene while maintaining its core events and purpose.

Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}

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
                    appendLine("**Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}**")
                    appendLine()
                    appendLine("**Setting:** ${sceneOutline.setting_id}")
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
                sceneTask.update()
                sceneTask.complete()

                // Add to result
                resultBuilder.append("## Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}\n\n")
                resultBuilder.append(generatedScene.content)
                resultBuilder.append("\n\n---\n\n")

                overviewTask.add("✅ (${generatedScene.word_count} words)\n".renderMarkdown)
                overviewTask.update()
// Generate scene image if enabled
                if (genConfig.generate_scene_images) {
                    generateSceneImage(
                        task = task,
                        tabs = tabs,
                        actNumber = sceneOutline.act_number,
                        sceneNumber = sceneOutline.scene_number,
                        sceneTitle = sceneOutline.title,
                        sceneContent = generatedScene.content,
                        setting = sceneOutline.setting_id,
                        settingImagePath = settingImages.entries.find {
                            sceneOutline.setting_id.lowercase().contains(it.key.lowercase())
                        }?.value,
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
            overviewTask.update()

            // Phase 4: Create final compiled version
            log.info("Phase 4: Assembling final narrative")
            val finalTask = tabs.newTask("Complete Narrative")

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
            finalTask.update()
            finalTask.complete()

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
            overviewTask.update()
            overviewTask.complete()
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
            val narrativeData = mapOf(
                "config" to genConfig,
                "highLevelOutline" to highLevelOutline,
                "outline" to outline,
                "scenes" to generatedScenes,
                "assets" to mapOf(
                    "coverImage" to coverImagePath,
                    "settingImages" to settingImages,
                    "characterImages" to characterImages
                ),
                "statistics" to mapOf(
                    "wordCount" to cumulativeWordCount,
                    "sceneCount" to generatedScenes.size,
                    "durationMs" to totalTime
                )
            )
            task.resolveUserFile("narrative_data.json")?.let { jsonFile ->
                jsonFile.writeText(narrativeData.toJson())
                log.info("Saved narrative data to ${jsonFile.absolutePath}")
                overviewTask.add("\n**Data:** Saved full narrative data to `narrative_data.json`\n".renderMarkdown)
                overviewTask.update()
            }

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
            overviewTask.update()

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
    ): String? {
        try {
            log.info("Generating cover image for: $title")
            val task = tabs.newTask("Cover Image")
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
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.8,
            )
            val coverPrompt = "$title: $premise"
            val result = imageAgent.answer(listOf(ImageAndText(coverPrompt)))
            val image = result.image
            // Save image
            val relativePath = "00_cover_image.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved cover image to: ${imageFile.absolutePath}")
            // Create display link
            val link = task.linkTo(relativePath)
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
            task.complete()
            return relativePath
        } catch (e: Exception) {
            log.error("Failed to generate cover image", e)
            transcriptWriter?.appendLine("**Cover Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
            return null
        }
    }

    private fun generateSettingImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        settingProfile: SettingProfile,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig,
        coverImagePath: String?
    ): String? {
        return try {
            log.info("Generating reference image for setting: ${settingProfile.setting_id}")
            val task = tabs.newTask("Setting: ${settingProfile.setting_id}")
            task.add(
                buildString {
                    appendLine("# Setting Reference: ${settingProfile.setting_id}")
                    appendLine()
                    appendLine("**Status:** Generating setting visualization...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a detailed, atmospheric image of this setting that captures its essence and mood. Use the cover image as visual inspiration for style and atmosphere.",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.7,
            )

            val settingPrompt = buildString {
                appendLine("${settingProfile.setting_id}")
                appendLine("Description: ${settingProfile.description}")
                appendLine("Atmosphere: ${settingProfile.atmosphere}")
            }.toString()

            // Build input with cover image as reference if available
            val imageInputs = mutableListOf<ImageAndText>()

            if (coverImagePath != null) {
                try {
                    val coverImage = ImageIO.read(task.resolveUserFile(coverImagePath))
                    if (coverImage != null) {
                        imageInputs.add(
                            ImageAndText(
                                text = "Cover image - use as visual style reference",
                                image = coverImage
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load cover image for setting reference: $coverImagePath", e)
                }
            }

            imageInputs.add(ImageAndText(settingPrompt))

            val result = imageAgent.answer(imageInputs)
            val image = result.image
            // Save image with sanitized filename
            val sanitizedName = settingProfile.setting_id.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val relativePath = "setting_${sanitizedName}_ref.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved setting reference image to: ${imageFile.absolutePath}")

            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='setting-reference'>
          <h4>${settingProfile.setting_id}</h4>
          <p><strong>Description:</strong> ${settingProfile.description}</p>
          <p><strong>Atmosphere:</strong> ${settingProfile.atmosphere}</p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='${settingProfile.setting_id}' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            task.add(imageHtml.renderMarkdown)
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Setting: ${settingProfile.setting_id}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Setting: ${settingProfile.setting_id}]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            task.add("\n**Status:** ✅ Complete\n".renderMarkdown)
            task.update()
            task.complete()
            relativePath
        } catch (e: Exception) {
            log.error("Failed to generate setting reference image for: ${settingProfile.setting_id}", e)
            transcriptWriter?.appendLine("**Setting Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
            null
        }
    }

    private fun generateCharacterImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        characterProfile: CharacterProfile,
        transcriptWriter: Writer?,
        orchestrationConfig: OrchestrationConfig,
        coverImagePath: String?
    ): String? {
        return try {
            log.info("Generating reference image for character: ${characterProfile.name}")
            val task = tabs.newTask("Character: ${characterProfile.name}")
            task.add(
                buildString {
                    appendLine("# Character Reference: ${characterProfile.name}")
                    appendLine()
                    appendLine("**Status:** Generating character visualization...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a detailed character portrait that captures their appearance, personality, and essence. Use the cover image as visual inspiration for style and atmosphere.",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.7,
            )

            val characterPrompt = buildString {
                appendLine("${characterProfile.name}")
                appendLine("Role: ${characterProfile.role}")
                appendLine("Description: ${characterProfile.description}")
                appendLine("Traits: ${characterProfile.traits.joinToString(", ")}")
            }.toString()

            // Build input with cover image as reference if available
            val imageInputs = mutableListOf<ImageAndText>()

            if (coverImagePath != null) {
                try {
                    val coverImage = ImageIO.read(task.resolveUserFile(coverImagePath))
                    if (coverImage != null) {
                        imageInputs.add(
                            ImageAndText(
                                text = "Cover image - use as visual style reference",
                                image = coverImage
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load cover image for character reference: $coverImagePath", e)
                }
            }

            imageInputs.add(ImageAndText(characterPrompt))

            val result = imageAgent.answer(imageInputs)
            val image = result.image
            // Save image with sanitized filename
            val sanitizedName = characterProfile.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(50)
            val relativePath = "character_${sanitizedName}_ref.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved character reference image to: ${imageFile.absolutePath}")

            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='character-reference'>
          <h4>${characterProfile.name}</h4>
          <p><strong>Role:</strong> ${characterProfile.role}</p>
          <p><strong>Description:</strong> ${characterProfile.description}</p>
          <p><strong>Traits:</strong> ${characterProfile.traits.joinToString(", ")}</p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='${characterProfile.name}' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            task.add(imageHtml.renderMarkdown)
            task.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Character: ${characterProfile.name}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Character: ${characterProfile.name}]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            task.add("\n**Status:** ✅ Complete\n".renderMarkdown)
            task.update()
            task.complete()
            relativePath
        } catch (e: Exception) {
            log.error("Failed to generate character reference image for: ${characterProfile.name}", e)
            transcriptWriter?.appendLine("**Character Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
            null
        }
    }

    private fun generateSceneImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        actNumber: Int,
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
            log.info("Generating image for Act $actNumber, Scene $sceneNumber: $sceneTitle")
            val sceneImageTask = tabs.newTask("Act $actNumber Scene $sceneNumber Image")
            sceneImageTask.add(
                buildString {
                    appendLine("# Act $actNumber, Scene $sceneNumber Image")
                    appendLine()
                    appendLine("**Status:** Generating scene visualization...")
                    appendLine()
                }.renderMarkdown
            )
            sceneImageTask.update()
            val imageAgent = ImageProcessingAgent(
                prompt = "Create a cinematic scene illustration that captures the key moment and atmosphere",
                model = orchestrationConfig.defaultImage,
                temperature = 0.7,
            )
            // Extract key visual elements from scene
            val scenePrompt = buildString {
                append("Scene: $sceneTitle. ")
                append("Setting: $setting. ")
                // Take first 500 chars of scene content for context
                append(sceneContent)
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
                    } else {
                        log.debug("Setting reference image is null for path: $settingImagePath")
                    }
                } catch (e: Exception) {
                    log.warn("Failed to load setting reference image: $settingImagePath", e)
                }
            } else {
                log.debug("No setting reference image available for setting: $setting")
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
            val relativePath = "act_${actNumber}_scene_${sceneNumber}_image.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved scene image to: ${imageFile.absolutePath}")
            // Create display link
            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='scene-image'>
          <h4>Act $actNumber, Scene $sceneNumber: $sceneTitle</h4>
          <p><strong>Setting:</strong> $setting</p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Act $actNumber Scene $sceneNumber' style='max-width: 600px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            sceneImageTask.add(imageHtml.renderMarkdown)
            sceneImageTask.update()
            // Write to transcript
            transcriptWriter?.appendLine("#### Act $actNumber, Scene $sceneNumber Image")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![Act $actNumber Scene $sceneNumber]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()
            sceneImageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown)
            sceneImageTask.update()
            sceneImageTask.complete()
        } catch (e: Exception) {
            log.error("Failed to generate scene image for Act $actNumber, Scene $sceneNumber", e)
            transcriptWriter?.appendLine("**Scene Image Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
        }
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(NarrativeGenerationTask::class.java)
        val NarrativeGeneration = TaskType(
            "NarrativeGeneration",
            "Writing",
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