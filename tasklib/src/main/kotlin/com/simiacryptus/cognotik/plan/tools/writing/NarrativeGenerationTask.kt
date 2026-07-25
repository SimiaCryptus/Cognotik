package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.finalNarrative
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.finalResult
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.highLevelContent
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.highLevelPrompt
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.imageHtml
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.outlineContent
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.overviewContent
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.revisionPrompt
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.sceneContent
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.sceneExpansionPrompt
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.scenePromptText
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTaskPrompts.taskPrompt
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.util.toJson
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.OutputStreamWriter
import java.io.Writer
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
    override var task_description: String? = null,

    @Description("The subject or scenario to develop into a full narrative (alias for task_description)")
    var subject: String? = null,

    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context for the narrative")
    var related_files: List<String>? = null,

    @Description("Narrative elements to consider (characters, setting, conflict, timeline, etc.)")
    var narrative_elements: Map<String, Any>? = null,

    @Description("Target word count for the complete narrative")
    var target_word_count: Int = 5000,

    @Description("Number of acts in the narrative structure (typically 3 or 5)")
    var number_of_acts: Int = 3,

    @Description("Average number of scenes per act")
    var scenes_per_act: Int = 3,

    @Description("Writing style (e.g., 'literary', 'thriller', 'technical', 'conversational')")
    var writing_style: String = "literary",

    @Description("Point of view (e.g., 'first person', 'third person limited', 'third person omniscient')")
    var point_of_view: String = "third person limited",

    @Description("Tone (e.g., 'dramatic', 'humorous', 'suspenseful', 'reflective')")
    var tone: String = "dramatic",

    @Description("Whether to include detailed scene descriptions")
    var detailed_descriptions: Boolean = true,

    @Description("Whether to include character dialogue")
    var include_dialogue: Boolean = true,

    @Description("Whether to show internal character thoughts")
    var show_internal_thoughts: Boolean = true,

    @Description("Number of revision passes for each scene")
    var revision_passes: Int = 2,

    @Description("Whether to generate images for each scene")
    var generate_scene_images: Boolean = true,

    @Description("Whether to generate a cover image for the narrative")
    var generate_cover_image: Boolean = true,

    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = NarrativeGeneration.name,
    task_description = task_description ?: "Generate full narrative for '$subject'",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      // Canonicalize: clamp to valid ranges instead of rejecting
      target_word_count = target_word_count.coerceIn(100, 100000)
      number_of_acts = number_of_acts.coerceIn(1, 10)
      scenes_per_act = scenes_per_act.coerceIn(1, 20)
      revision_passes = revision_passes.coerceIn(0, 5)
      writing_style = writing_style.trim().ifBlank { "literary" }
      // Sync subject and task_description
      if (subject.isNullOrBlank() && !task_description.isNullOrBlank()) subject = task_description
      if (task_description.isNullOrBlank() && !subject.isNullOrBlank()) task_description = subject
      return null
    }
  }

  @Suppress("unused")
  data class NarrativeCharacterReference(
    @Description("The name of the character this reference is for")
    var character_name: String = "",
    @Description("Path or URL to a reference image for this character")
    var reference_image_path: String? = null,
    @Description("Detailed visual description of the character")
    var visual_description: String? = null,
    @Description("Personality traits and behavioral notes")
    var personality_notes: String? = null,
    @Description("Character's role in the story (e.g., 'protagonist', 'antagonist', 'supporting')")
    var role: String? = null,
    @Description("Backstory or background information")
    var backstory: String? = null,
    @Description("Relationships with other characters")
    var relationships: String? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      character_name = character_name.trim()
      if (character_name.isBlank()) return "character_name must not be blank"
      return null
    }
  }

  data class NarrativePlotContinuityDetails(
    @Description("Overall story arc or narrative structure (e.g., 'three-act structure', 'hero's journey')")
    var narrative_structure: String = "",
    @Description("Key plot points that must be included, in order")
    var key_plot_points: List<String> = emptyList(),
    @Description("Setting details (time period, location, world-building notes)")
    var setting_details: String = "",
    @Description("Themes or motifs to weave throughout the story")
    var themes: List<String> = emptyList(),
    @Description("Tone and mood guidelines")
    var tone: String = "",
    @Description("Previously established story context or backstory that this narrative continues from")
    var prior_story_context: String = "",
    @Description("Specific continuity constraints")
    var continuity_constraints: List<String> = emptyList(),
    @Description("Desired ending or resolution notes")
    var ending_notes: String = "",
    @Description("Any additional requirements or notes")
    var additional_notes: String = ""
  )


  data class NarrativeOutline(
    @Description("The title of the narrative")
    var title: String = "",
    @Description("The central premise or logline of the narrative")
    var premise: String = "",
    @Description("List of character profiles appearing in the narrative")
    var characters: List<CharacterProfile> = emptyList(),
    @Description("List of setting profiles used in the narrative")
    var settings: List<SettingProfile> = emptyList(),
    @Description("List of act outlines comprising the narrative structure")
    var acts: List<ActOutline> = emptyList(),
    @Description("Estimated total word count for the complete narrative")
    var estimated_word_count: Int = 0
  )

  data class CharacterProfile(
    @Description("The character's name")
    var name: String = "",
    @Description("Physical appearance, personality, and background description")
    var description: String = "",
    @Description("Role in the story (e.g., 'protagonist', 'antagonist', 'supporting')")
    var role: String = "",
    @Description("Key personality traits and motivations")
    var traits: List<String> = emptyList()
  )

  data class SettingProfile(
    @Description("Unique identifier for this setting")
    var setting_id: String = "",
    @Description("Visual and spatial description of the setting")
    var description: String = "",
    @Description("The mood and atmosphere of this setting")
    var atmosphere: String = "",
    @Description("Why this setting matters to the story")
    var significance: String = ""
  )

  data class HighLevelOutline(
    @Description("The title of the narrative")
    var title: String = "",
    @Description("The central premise or logline of the narrative")
    var premise: String = "",
    @Description("List of character profiles appearing in the narrative")
    var characters: List<CharacterProfile> = emptyList(),
    @Description("List of setting profiles used in the narrative")
    var settings: List<SettingProfile> = emptyList(),
    @Description("List of act summaries comprising the high-level structure")
    var acts: List<ActSummary> = emptyList(),
    @Description("Estimated total word count for the complete narrative")
    var estimated_word_count: Int = 0
  )

  data class ActSummary(
    @Description("The sequential number of this act (1-based)")
    var act_number: Int = 1,
    @Description("The title of this act")
    var title: String = "",
    @Description("What this act accomplishes in the overall story")
    var purpose: String = "",
    @Description("Major plot points and character changes in this act")
    var key_developments: List<String> = emptyList(),
    @Description("Estimated number of scenes in this act")
    var estimated_scenes: Int = 3
  )

  data class ActOutline(
    @Description("The sequential number of this act (1-based)")
    var act_number: Int = 1,
    @Description("The title of this act")
    var title: String? = "",
    @Description("What this act accomplishes in the overall story")
    var purpose: String? = "",
    @Description("List of scene outlines within this act")
    var scenes: List<SceneOutline>? = emptyList()
  )

  data class SceneOutline(
    @Description("The act number this scene belongs to")
    var act_number: Int = 1,
    @Description("The sequential scene number within the act")
    var scene_number: Int = 1,
    @Description("The title of this scene")
    var title: String = "",
    @Description("The setting_id from the defined settings where this scene takes place")
    var setting_id: String = "",
    @Description("List of character names present in this scene")
    var characters: List<String> = emptyList(),
    @Description("What this scene accomplishes in the narrative")
    var purpose: String = "",
    @Description("The major events that occur in this scene")
    var key_events: Any? = null,
    @Description("How characters feel and change emotionally during this scene")
    var emotional_arc: String = "",
    @Description("Target word count for this scene")
    var estimated_word_count: Int = 0
  )

  data class GeneratedScene(
    @Description("The sequential scene number")
    var scene_number: Int = 1,
    @Description("The act number this scene belongs to")
    var act_number: Int = 1,
    @Description("The title of this scene")
    var title: String = "",
    @Description("The full written content of the scene")
    var content: String = "",
    @Description("The actual word count of the scene content")
    var word_count: Int = 0,
    @Description("3-5 bullet points summarizing what happened in this scene")
    var key_moments: List<String> = emptyList(),
    @Description("How each character ends the scene emotionally and physically, keyed by character name")
    var character_states: Map<String, String> = emptyMap()
  )

  override fun promptSegment(): String = buildString {
    appendLine("NarrativeGeneration - Generate complete narratives from analysis and outlines")
    appendLine("  ** Extends NarrativeReasoning with full story generation")
    appendLine("  ** Specify the subject or scenario to develop")
    appendLine("  ** Define narrative elements: characters, setting, conflict, timeline")
    appendLine("  ** Set target word count and structural parameters (acts, scenes)")
    appendLine("  ** Configure writing style, POV, and tone")
    appendLine("  ** Enable detailed descriptions, dialogue, and internal thoughts")
    appendLine("  ** Performs analysis, creates outline, then writes each scene iteratively")
    appendLine("  ** Each scene receives context from previous scenes")
    appendLine("  ** Produces complete, coherent narrative with consistent style")
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
    val transcript = task.newUserFileStream(transcriptFile())?.let { OutputStreamWriter(it) }
    val dataDir = (getOutputFile(".md")?.let {
      if (it.endsWith(".md")) it.removeSuffix(".md") else null
    } ?: "comic").apply {
      val dir = task.resolveUserFile(this)
      if (dir != null && !dir.exists()) {
        dir.mkdirs()
      }
    }
    val dataFile =
      getOutputFile(".md")?.let { it.removeSuffix(".md") + ".narrative.json" } ?: "narrative.narrative.json"

    val genConfig = executionConfig

    try {

      transcript?.write("# Narrative Generation Task\n\n")

      if (genConfig == null) {
        log.error("Invalid configuration type for NarrativeGenerationTask")
        transcript?.write("ERROR: Invalid configuration type\n")
        task.safeComplete("CONFIGURATION ERROR: Invalid configuration type", log)
        resultFn("CONFIGURATION ERROR: Invalid configuration type")
        return
      }

      val subject = genConfig.subject
      if (subject.isNullOrBlank()) {
        log.error("No subject specified for narrative generation")
        transcript?.write("ERROR: No subject specified\n")
        task.safeComplete("CONFIGURATION ERROR: No subject specified", log)
        resultFn("CONFIGURATION ERROR: No subject specified")
        return
      }
      log.debug("Configuration validated - Acts: ${genConfig.number_of_acts}, Scenes/Act: ${genConfig.scenes_per_act}, Style: ${genConfig.writing_style}")

      val smartApi = defaultSmart.getChildClient(task)
      val fastApi = defaultFast.getChildClient(task)

      val tabs = TabbedDisplay(task)
      // Get input file context
      val executionConfig = executionConfig ?: throw RuntimeException("Execution config is null")
      val inputFileContext = try {
        log.debug("Loading input files: ${executionConfig.related_files?.joinToString(", ") ?: "none"}")
        super.getInputFileContent(executionConfig.related_files, root, treatDocumentsAsText = true)
      } catch (e: Exception) {
        log.error("Failed to load input files", e)
        transcript?.write("WARNING: Failed to load input files: ${e.message}\n\n")
        transcript?.flush()
        ""
      }

      if (inputFileContext.isNotBlank()) {
        log.debug("Loaded input file context: ${inputFileContext.length} characters")
        if (verbose) {
          transcript?.write("## Input Files Context\n\n$inputFileContext\n\n")
          transcript?.flush()
        }
        task.expandable("Input File Context", "<pre>${inputFileContext.truncateForDisplay(5000)}</pre>")
      }
      // Combine messages with input files
      val combinedMessages = messages + listOf(inputFileContext).filter { it.isNotBlank() }
      if (verbose) {
        transcript?.write("## Input Messages\n\n${combinedMessages.joinToString("\n\n")}\n\n")
        transcript?.flush()
      }


      // Overview tab
      val overviewTask = tabs.newTask("Overview")
      val overviewContent = overviewContent(subject, genConfig)
      overviewTask.add(overviewContent.renderMarkdown(true))
      transcript?.write("\n## Overview\n\n$overviewContent\n\n")
      transcript?.flush()
      overviewTask.update()

      val resultBuilder = StringBuilder()
      resultBuilder.append("# Generated Narrative: $subject\n\n")

      try {
        // Phase 1: Run the base narrative reasoning analysis
        log.info("Phase 1: Running narrative analysis")
        val analysisResult = StringBuilder()

        overviewTask.add("\n✅ Phase 1 Complete: Narrative analysis finished\n".renderMarkdown(true))
        overviewTask.add(
          "\n### Phase 2: Outline Generation\n*Creating detailed scene-by-scene outline...*\n".renderMarkdown(
            true
          )
        )
        overviewTask.update()

        log.info("Phase 2: Generating narrative outline")
        val outlineTask = tabs.newTask("Outline")

        outlineTask.add(
          buildString {
            appendLine("# Narrative Outline")
            appendLine()
            appendLine("**Status:** Pass 1 - Generating high-level structure...")
            appendLine()
          }.renderMarkdown(true)
        )
        outlineTask.update()

        val totalScenes = genConfig.number_of_acts * genConfig.scenes_per_act
        val wordsPerScene = genConfig.target_word_count / totalScenes

        // Generate cover image first if enabled (to use as seed for other images)
        var coverImagePath: String? = null
        if (genConfig.generate_cover_image || genConfig.generate_scene_images) {
          log.info("Phase 2.0: Generating cover image to use as visual seed")
          overviewTask.add(
            "\n### Phase 2.0: Generating Cover Image\n*Creating visual foundation for the narrative...*\n".renderMarkdown(
              true
            )
          )
          overviewTask.update()
          coverImagePath = generateCoverImage(
            parentTask = task,
            tabs = tabs,
            title = subject,
            premise = analysisResult.toString().take(500),
            transcriptWriter = transcript,
            orchestrationConfig = orchestrationConfig,
            dataDir = dataDir
          )
          if (coverImagePath != null) {
            overviewTask.add(
              "✅ Phase 2.0 Complete: Cover image generated and will be used as visual seed\n".renderMarkdown(
                true
              )
            )
          } else {
            overviewTask.add(
              "⚠️ Phase 2.0: Cover image generation failed, proceeding without visual seed\n".renderMarkdown(
                true
              )
            )
          }
          overviewTask.update()
        }

        // Pass 1: High-level outline with characters and settings
        log.info("Phase 2.1: Generating high-level outline")

        val highLevelPrompt = highLevelPrompt(subject, analysisResult, genConfig, totalScenes, wordsPerScene)

        val highLevelAgent = ParsedAgent(
          resultClass = HighLevelOutline::class.java,
          prompt = highLevelPrompt,
          model = smartApi,
          temperature = 0.7,
          parsingModel = fastApi
        )

        val highLevelOutline = try {
          highLevelAgent.answer(listOf("Generate high-level outline")).obj
        } catch (e: Exception) {
          log.error("Failed to generate high-level outline", e)
          transcript?.write("<details><summary>High-Level Outline Error</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n\n")
          transcript?.flush()
          throw RuntimeException("Failed to generate high-level outline", e)
        }

        log.info("Generated high-level outline: ${highLevelOutline.acts.size} acts, ${highLevelOutline.characters.size} characters, ${highLevelOutline.settings.size} settings")

        // Display high-level outline
        val highLevelContent = highLevelContent(highLevelOutline)
        outlineTask.add(highLevelContent.renderMarkdown(true))
        transcript?.write("\n## High-Level Outline\n\n$highLevelContent\n\n")
        transcript?.flush()
        outlineTask.update()
// Pass 2: Expand acts into detailed scenes


        log.info("Phase 2.2: Expanding acts into scenes")
        outlineTask.add("\n**Status:** Pass 2 - Expanding acts into detailed scenes...\n".renderMarkdown(true))
        outlineTask.update()

        val detailedActs = mutableListOf<ActOutline>()
        highLevelOutline.acts.forEach { actSummary ->
          log.info("Expanding Act ${actSummary.act_number}: ${actSummary.title}")

          val sceneExpansionPrompt = sceneExpansionPrompt(highLevelOutline, actSummary, detailedActs)

          val sceneExpansionAgent = ParsedAgent(
            resultClass = ActOutline::class.java,
            prompt = sceneExpansionPrompt,
            model = smartApi,
            temperature = 0.7,
            parsingModel = fastApi
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
            transcript?.write("<details><summary>Act ${actSummary.act_number} Expansion Error</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n\n")
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
        val outlineContent = outlineContent(outline)
        outlineTask.add(outlineContent.renderMarkdown(true))
        transcript?.write("\n## Outline\n\n$outlineContent\n\n")
        transcript?.flush()
        outlineTask.update()
        outlineTask.complete()

        resultBuilder.append("## ${outline.title}\n\n")
        resultBuilder.append("${outline.premise}\n\n")
        resultBuilder.append("---\n\n")

        overviewTask.add(
          "✅ Phase 2 Complete: Outline created (${outline.acts.sumOf { it.scenes?.size ?: 0 }} scenes)\n".renderMarkdown(
            true
          )
        )
        overviewTask.add(
          "\n### Phase 3: Scene Generation\n*Writing scenes iteratively with context...*\n".renderMarkdown(
            true
          )
        )
        overviewTask.update()

        // Phase 2.5: Generate setting and character images if enabled
        val allScenes = outline.acts.flatMap { it.scenes ?: emptyList() }
        val settingImages = mutableMapOf<String, String>()
        val characterImages = mutableMapOf<String, String>()

        if (genConfig.generate_scene_images) {
          log.info("Phase 2.5: Generating setting and character reference images")
          overviewTask.add(
            "\n### Phase 2.5: Generating Reference Images\n*Creating setting and character visualizations...*\n".renderMarkdown(
              true
            )
          )
          overviewTask.update()

          // Generate images for defined settings
          log.info("Generating images for ${outline.settings.size} settings")
          outline.settings.forEach { setting ->
            val settingImagePath = generateSettingImage(
              parentTask = task,
              tabs = tabs,
              transcriptWriter = transcript,
              orchestrationConfig = orchestrationConfig,
              settingProfile = setting,
              coverImagePath = coverImagePath,
              dataDir = dataDir
            )
            if (settingImagePath != null) {
              settingImages[setting.setting_id] = settingImagePath
            }
          }

          // Generate images for defined characters
          log.info("Generating images for ${outline.characters.size} characters")
          outline.characters.forEach { character ->
            val characterImagePath = generateCharacterImage(
              parentTask = task,
              tabs = tabs,
              characterProfile = character,
              transcriptWriter = transcript,
              orchestrationConfig = orchestrationConfig,
              coverImagePath = coverImagePath,
              dataDir = dataDir
            )
            if (characterImagePath != null) {
              characterImages[character.name] = characterImagePath
            }
          }
          overviewTask.add(
            "✅ Phase 2.5 Complete: Generated ${settingImages.size} setting images and ${characterImages.size} character images\n".renderMarkdown(
              true
            )
          )
          overviewTask.update()
        }

        // Phase 3: Generate each scene iteratively
        log.info("Phase 3: Generating scenes")
        val generatedScenes = mutableListOf<GeneratedScene>()
        var cumulativeWordCount = 0

        allScenes.forEachIndexed { index, sceneOutline ->
          log.info("Generating Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}/${allScenes.size}: ${sceneOutline.title}")

          overviewTask.add(
            "- Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title} ".renderMarkdown(
              true
            )
          )
          overviewTask.update()

          val sceneTask = tabs.newTask("Act ${sceneOutline.act_number} Scene ${sceneOutline.scene_number}")

          sceneTask.add(
            buildString {
              appendLine("# Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}")
              appendLine()
              appendLine("**Status:** Writing scene...")
              appendLine()
            }.renderMarkdown(true)
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

          val scenePromptText = scenePromptText(genConfig, sceneOutline, outline, recentContext)

          val sceneAgent = ParsedAgent(
            resultClass = GeneratedScene::class.java,
            prompt = scenePromptText,
            model = smartApi,
            temperature = 0.8,
            parsingModel = fastApi
          )

          var generatedScene = sceneAgent.answer(listOf("Write the scene")).obj
          // Ensure act number is preserved
          generatedScene = generatedScene.copy(act_number = sceneOutline.act_number)

          // Optional revision pass
          if (genConfig.revision_passes > 0) {
            repeat(genConfig.revision_passes) { revisionNum ->
              log.debug("Revision pass ${revisionNum + 1} for Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}")

              val revisionPrompt = revisionPrompt(sceneOutline, generatedScene)

              val revisionAgent = ChatAgent(
                prompt = revisionPrompt,
                model = smartApi,
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

          val sceneContent = sceneContent(sceneOutline, generatedScene)
          sceneTask.add(sceneContent.renderMarkdown(true))
          transcript?.write("\n## $sceneContent\n\n")
          transcript?.flush()
          sceneTask.update()
          sceneTask.complete()

          // Add to result
          resultBuilder.append("## Act ${sceneOutline.act_number}, Scene ${sceneOutline.scene_number}: ${sceneOutline.title}\n\n")
          resultBuilder.append(generatedScene.content)
          resultBuilder.append("\n\n---\n\n")

          overviewTask.add("✅ (${generatedScene.word_count} words)\n".renderMarkdown(true))
          overviewTask.update()
// Generate scene image if enabled
          if (genConfig.generate_scene_images) {
            generateSceneImage(
              parentTask = task,
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
              orchestrationConfig = orchestrationConfig,
              dataDir = dataDir
            )
          }
        }

        overviewTask.add("\n✅ Phase 3 Complete: All scenes generated\n".renderMarkdown(true))
        overviewTask.add(
          "\n### Phase 4: Final Assembly\n*Compiling complete narrative...*\n".renderMarkdown(
            true
          )
        )
        overviewTask.update()

        // Phase 4: Create final compiled version
        log.info("Phase 4: Assembling final narrative")
        val finalTask = tabs.newTask("Complete Narrative")

        val finalNarrative = finalNarrative(outline, generatedScenes, cumulativeWordCount)
        finalTask.add(finalNarrative.renderMarkdown(true))
        finalTask.update()
        finalTask.complete()
        // Final statistics
        val totalTime = System.currentTimeMillis() - startTime
        val avgWordsPerScene =
          if (generatedScenes.isNotEmpty()) cumulativeWordCount / generatedScenes.size else 0

        overviewTask.add(
          taskPrompt(
            generatedScenes,
            cumulativeWordCount,
            avgWordsPerScene,
            genConfig,
            totalTime
          ).renderMarkdown(true)
        )
        overviewTask.update()
        overviewTask.complete()
        transcript?.write("\n## Final Statistics\n\n- Total Scenes: ${generatedScenes.size}\n- Total Word Count: $cumulativeWordCount\n- Time: ${totalTime / 1000.0}s\n\n")

        val finalResult = finalResult(outline, cumulativeWordCount, generatedScenes, totalTime, outlineContent)
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
        task.resolveUserFile(dataFile)?.let { jsonFile ->
          jsonFile.writeText(narrativeData.toJson())
          log.info("Saved narrative data to ${jsonFile.absolutePath}")
          overviewTask.add(
            "\n**Data:** Saved full narrative data to `${dataFile}`\n".renderMarkdown(
              true
            )
          )
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
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```")
        transcript?.flush()
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
    } finally {
      transcript?.close()
    }
  }

  private fun generateCoverImage(
    parentTask: SessionTask,
    tabs: TabbedDisplay,
    title: String,
    premise: String,
    transcriptWriter: Writer?,
    orchestrationConfig: OrchestrationConfig,
    dataDir: String
  ): String? {
    try {
      log.info("Generating cover image for: $title")
      val coverTask = tabs.newTask("Cover Image")
      coverTask.add(
        buildString {
          appendLine("# Cover Image")
          appendLine()
          appendLine("**Status:** Generating cover image...")
          appendLine()
        }.renderMarkdown(true)
      )
      coverTask.update()
      val imageAgent = ImageProcessingAgent(
        prompt = "Create a compelling book cover image that captures the essence of this narrative",
        model = orchestrationConfig.defaultImage.getChildClient(coverTask),
        temperature = 0.8,
      )
      val coverPrompt = "$title: $premise"
      val result = imageAgent.answer(listOf(ImageAndText(coverPrompt)))
      val image = result.image
      // Save image
      val relativePath = "$dataDir/00_cover_image.png"
      val imageFile = parentTask.resolveUserFile(relativePath)!!
      imageFile.parentFile?.mkdirs()
      ImageIO.write(image, "png", imageFile)
      log.debug("Saved cover image to: ${imageFile.absolutePath}")
      // Create display link
      val link = parentTask.linkTo(relativePath)
      val imageHtml = imageHtml(title, premise, result, link)
      coverTask.add(imageHtml.renderMarkdown(true))
      coverTask.update()
      // Write to transcript
      transcriptWriter?.appendLine("## Cover Image")
      transcriptWriter?.appendLine()
      transcriptWriter?.appendLine("**Prompt:** ${result.text}")
      transcriptWriter?.appendLine()
      transcriptWriter?.appendLine("![Cover Image]($link)".transcriptFilter())
      transcriptWriter?.appendLine()
      transcriptWriter?.flush()
      coverTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
      coverTask.update()
      coverTask.complete()
      return relativePath
    } catch (e: Exception) {
      log.error("Failed to generate cover image", e)
      transcriptWriter?.appendLine("<details><summary>Cover Image Error</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>")
      transcriptWriter?.appendLine("**Cover Image Generation Failed:** ${e.message}")
      transcriptWriter?.appendLine()
      return null
    }
  }

  private fun generateSettingImage(
    parentTask: SessionTask,
    tabs: TabbedDisplay,
    settingProfile: SettingProfile,
    transcriptWriter: Writer?,
    orchestrationConfig: OrchestrationConfig,
    coverImagePath: String?,
    dataDir: String
  ): String? {
    return try {
      log.info("Generating reference image for setting: ${settingProfile.setting_id}")
      val settingTask = tabs.newTask("Setting: ${settingProfile.setting_id}")
      settingTask.add(
        buildString {
          appendLine("# Setting Reference: ${settingProfile.setting_id}")
          appendLine()
          appendLine("**Status:** Generating setting visualization...")
          appendLine()
        }.renderMarkdown(true)
      )
      settingTask.update()

      val imageAgent = ImageProcessingAgent(
        prompt = "Create a detailed, atmospheric image of this setting that captures its essence and mood. Use the cover image as visual inspiration for style and atmosphere.",
        model = orchestrationConfig.defaultImage.getChildClient(settingTask),
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
          val coverImage = ImageIO.read(parentTask.resolveUserFile(coverImagePath))
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
      val relativePath = "$dataDir/setting_${sanitizedName}_ref.png"
      val imageFile = parentTask.resolveUserFile(relativePath)!!
      imageFile.parentFile?.mkdirs()
      ImageIO.write(image, "png", imageFile)
      log.debug("Saved setting reference image to: ${imageFile.absolutePath}")

      // Create display link
      val link = parentTask.linkTo(relativePath)
      val imageHtml = imageHtml(settingProfile, result, link)
      settingTask.add(imageHtml.renderMarkdown(true))
      settingTask.update()
      // Write to transcript
      transcriptWriter?.appendLine("#### Setting: ${settingProfile.setting_id}")
      transcriptWriter?.appendLine()
      transcriptWriter?.appendLine("**Prompt:** ${result.text}")
      transcriptWriter?.appendLine()
      transcriptWriter?.appendLine("![Setting: ${settingProfile.setting_id}]($link)".transcriptFilter())
      transcriptWriter?.appendLine()
      transcriptWriter?.flush()
      settingTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
      settingTask.update()
      settingTask.complete()
      relativePath
    } catch (e: Exception) {
      log.error("Failed to generate setting reference image for: ${settingProfile.setting_id}", e)
      transcriptWriter?.appendLine("<details><summary>Setting Image Error</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>")
      transcriptWriter?.appendLine("**Setting Image Generation Failed:** ${e.message}")
      transcriptWriter?.appendLine()
      null
    }
  }

  private fun generateCharacterImage(
    parentTask: SessionTask,
    tabs: TabbedDisplay,
    characterProfile: CharacterProfile,
    transcriptWriter: Writer?,
    orchestrationConfig: OrchestrationConfig,
    coverImagePath: String?,
    dataDir: String
  ): String? {
    return try {
      log.info("Generating reference image for character: ${characterProfile.name}")
      val charTask = tabs.newTask("Character: ${characterProfile.name}")
      charTask.add(
        buildString {
          appendLine("# Character Reference: ${characterProfile.name}")
          appendLine()
          appendLine("**Status:** Generating character visualization...")
          appendLine()
        }.renderMarkdown(true)
      )
      charTask.update()

      val imageAgent = ImageProcessingAgent(
        prompt = "Create a detailed character portrait that captures their appearance, personality, and essence. Use the cover image as visual inspiration for style and atmosphere.",
        model = orchestrationConfig.defaultImage.getChildClient(charTask),
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
          val coverImage = ImageIO.read(parentTask.resolveUserFile(coverImagePath))
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
      val relativePath = "$dataDir/character_${sanitizedName}_ref.png"
      val imageFile = parentTask.resolveUserFile(relativePath)!!
      imageFile.parentFile?.mkdirs()
      ImageIO.write(image, "png", imageFile)
      log.debug("Saved character reference image to: ${imageFile.absolutePath}")

      // Create display link
      val link = parentTask.linkTo(relativePath)
      val imageHtml = buildString {
        appendLine("<div class='character-reference'>")
        appendLine("  <h4>${characterProfile.name}</h4>")
        appendLine("  <p><strong>Role:</strong> ${characterProfile.role}</p>")
        appendLine("  <p><strong>Description:</strong> ${characterProfile.description}</p>")
        appendLine("  <p><strong>Traits:</strong> ${characterProfile.traits.joinToString(", ")}</p>")
        appendLine("  <p><strong>Image Prompt:</strong> ${result.text}</p>")
        appendLine("  <a href='$link' target='_blank'>")
        appendLine("    <img src='$link' alt='${characterProfile.name}' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />")
        appendLine("  </a>")
        appendLine("</div>")
      }
      charTask.add(imageHtml.renderMarkdown(true))
      charTask.update()
      // Write to transcript
      transcriptWriter?.appendLine("#### Character: ${characterProfile.name}")
      transcriptWriter?.appendLine()
      transcriptWriter?.appendLine("**Prompt:** ${result.text}")
      transcriptWriter?.appendLine()
      transcriptWriter?.appendLine("![Character: ${characterProfile.name}]($link)".transcriptFilter())
      transcriptWriter?.appendLine()
      transcriptWriter?.flush()
      charTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
      charTask.update()
      charTask.complete()
      relativePath
    } catch (e: Exception) {
      log.error("Failed to generate character reference image for: ${characterProfile.name}", e)
      transcriptWriter?.appendLine("<details><summary>Character Image Error</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>")
      transcriptWriter?.appendLine("**Character Image Generation Failed:** ${e.message}")
      transcriptWriter?.appendLine()
      null
    }
  }

  private fun generateSceneImage(
    parentTask: SessionTask,
    tabs: TabbedDisplay,
    actNumber: Int,
    sceneNumber: Int,
    sceneTitle: String,
    sceneContent: String,
    setting: String,
    settingImagePath: String?,
    characterImagePaths: Map<String, String>,
    transcriptWriter: Writer?,
    orchestrationConfig: OrchestrationConfig,
    dataDir: String
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
        }.renderMarkdown(true)
      )
      sceneImageTask.update()
      val imageAgent = ImageProcessingAgent(
        prompt = "Create a cinematic scene illustration that captures the key moment and atmosphere",
        model = orchestrationConfig.defaultImage,
        temperature = 0.7,
      )
      // Extract key visual elements from scene
      val scenePrompt = buildString {
        append("Scene: $sceneTitle. Act $actNumber, Scene $sceneNumber. ")
        append("Setting: $setting. ")
        // Take first 500 chars of scene content for context
        append(sceneContent)
      }
      // Build input with reference images
      val imageInputs = mutableListOf<ImageAndText>()

      // Add setting reference image if available
      if (settingImagePath != null) {
        try {
          val settingImage = ImageIO.read(parentTask.resolveUserFile(settingImagePath))
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
          val charImage = ImageIO.read(parentTask.resolveUserFile(imagePath))
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
      val relativePath = "$dataDir/act_${actNumber}_scene_${sceneNumber}_image.png"
      val imageFile = parentTask.resolveUserFile(relativePath)!!
      imageFile.parentFile?.mkdirs()
      ImageIO.write(image, "png", imageFile)
      log.debug("Saved scene image to: ${imageFile.absolutePath}")
      // Create display link
      val link = parentTask.linkTo(relativePath)
      val imageHtml = imageHtml(actNumber, sceneNumber, sceneTitle, setting, result, link)
      sceneImageTask.add(imageHtml.renderMarkdown(true))
      sceneImageTask.update()
      // Write to transcript
      transcriptWriter?.appendLine("#### Act $actNumber, Scene $sceneNumber Image")
      transcriptWriter?.appendLine()
      transcriptWriter?.appendLine("**Prompt:** ${result.text}")
      transcriptWriter?.appendLine()
      transcriptWriter?.appendLine("![Act $actNumber Scene $sceneNumber]($link)".transcriptFilter())
      transcriptWriter?.appendLine()
      transcriptWriter?.flush()
      sceneImageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
      sceneImageTask.update()
      sceneImageTask.complete()
    } catch (e: Exception) {
      log.error("Failed to generate scene image for Act $actNumber, Scene $sceneNumber", e)
      transcriptWriter?.appendLine("<details><summary>Scene Image Error</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>")
      transcriptWriter?.appendLine("**Scene Image Generation Failed:** ${e.message}")
      transcriptWriter?.appendLine()
    }
  }


  companion object {
    private val log: Logger = getLogger(NarrativeGenerationTask::class.java)

    @JvmStatic
    val NarrativeGeneration = TaskType(
      name = "NarrativeGeneration",
      category = "Writing",
      taskClass = NarrativeGenerationTask::class.java,
      executionConfigClass = NarrativeGenerationTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generate complete narratives from analysis and outlines",
      tooltipHtml = buildString {
        appendLine("Extends NarrativeReasoning to generate complete, publication-ready narratives.")
        appendLine("<ul>")
        appendLine("  <li>Performs comprehensive narrative analysis (inherited from NarrativeReasoning)</li>")
        appendLine("  <li>Creates detailed scene-by-scene outline based on analysis</li>")
        appendLine("  <li>Generates each scene iteratively with full context</li>")
        appendLine("  <li>Maintains consistency by feeding previous scenes into each generation</li>")
        appendLine("  <li>Supports configurable structure (acts, scenes, word count)</li>")
        appendLine("  <li>Customizable writing style, POV, tone, and narrative elements</li>")
        appendLine("  <li>Supports character reference images and plot continuity details</li>")
        appendLine("  <li>Optional revision passes for quality improvement</li>")
        appendLine("  <li>Produces complete, coherent narrative with consistent style and voice</li>")
        appendLine("  <li>Ideal for story generation, scenario planning, user journey narratives</li>")
        appendLine("</ul>")
      },
    )

  }
}