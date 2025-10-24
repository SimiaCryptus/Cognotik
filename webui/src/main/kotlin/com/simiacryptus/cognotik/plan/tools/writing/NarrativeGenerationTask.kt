package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.safeComplete
import com.simiacryptus.cognotik.plan.tools.reasoning.truncateForDisplay
import com.simiacryptus.cognotik.plan.tools.reasoning.validateAndGetApi
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class NarrativeGenerationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: NarrativeGenerationTaskExecutionConfigData?
) : NarrativeReasoningTask<NarrativeGenerationTask.NarrativeGenerationTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class NarrativeGenerationTaskExecutionConfigData(
    @Description("The subject or scenario to develop into a full narrative")
    subject: String? = null,

    @Description("Narrative elements to consider (characters, setting, conflict, timeline, etc.)")
    narrative_elements: Map<String, Any>? = null,

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
    val revision_passes: Int = 1,

    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : NarrativeReasoningTaskExecutionConfigData(
    subject = subject,
    narrative_elements = narrative_elements,
    construct_narrative = true,
    identify_plot_points = true,
    predict_outcomes = true,
    alternative_narratives = 1,
    analyze_motivations = true,
    find_inconsistencies = true,
    task_dependencies = task_dependencies,
    state = state
  ) {
    override val task_type: String = NarrativeGeneration.name
    override var task_description: String? = "Generate full narrative for '$subject'"
    override fun validate(): String? {
      // First validate parent class
      super.validate()?.let { return it }
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
    val transcript = transcript(task)
    val genConfig = executionConfig as? NarrativeGenerationTaskExecutionConfigData
    log.info("Starting NarrativeGenerationTask for subject: '${genConfig?.subject}'")

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

    val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return

    val tabs = TabbedDisplay(task)

    // Overview tab
    val overviewTask = task.ui.newTask(false)
    tabs["Overview"] = overviewTask.placeholder

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
    transcript?.write(overviewContent.toByteArray())
    transcript?.flush()
    task.update()

    val resultBuilder = StringBuilder()
    resultBuilder.append("# Generated Narrative: $subject\n\n")

    try {
      // Phase 1: Run the base narrative reasoning analysis
      log.info("Phase 1: Running narrative analysis")
      val analysisResult = StringBuilder()

      super.run(agent, messages, task, { result ->
        analysisResult.append(result)
        transcript?.write(result.toByteArray())
        transcript?.flush()
      }, orchestrationConfig)

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
        parsingChatter = orchestrationConfig.parsingChatter
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
      transcript?.write(outlineContent.toByteArray())
      transcript?.flush()
      task.update()

      resultBuilder.append("## ${outline.title}\n\n")
      resultBuilder.append("${outline.premise}\n\n")
      resultBuilder.append("---\n\n")

      overviewTask.add("✅ Phase 2 Complete: Outline created (${outline.acts.sumOf { it.scenes?.size ?: 0 }} scenes)\n".renderMarkdown)
      overviewTask.add("\n### Phase 3: Scene Generation\n*Writing scenes iteratively with context...*\n".renderMarkdown)
      task.update()

      // Phase 3: Generate each scene iteratively
      log.info("Phase 3: Generating scenes")
      val allScenes = outline.acts.flatMap { it.scenes ?: emptyList() } ?: emptyList()
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
          parsingChatter = orchestrationConfig.parsingChatter
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
        transcript?.write(sceneContent.toByteArray())
        transcript?.flush()
        task.update()

        // Add to result
        resultBuilder.append("## Scene ${sceneOutline.scene_number}: ${sceneOutline.title}\n\n")
        resultBuilder.append(generatedScene.content)
        resultBuilder.append("\n\n---\n\n")

        overviewTask.add("✅ (${generatedScene.word_count} words)\n".renderMarkdown)
        task.update()
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
          appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        }.renderMarkdown
      )
      task.update()


      // Per best practices, the final result passed to resultFn should be a concise summary,
      // not the full text which is available in the UI.
      val finalResult = buildString {
        appendLine("# Narrative Generation Summary: ${outline.title}")
        appendLine()
        appendLine("A complete narrative of **$cumulativeWordCount words** across **${generatedScenes.size} scenes** was generated in **${totalTime / 1000.0}s**.")
        appendLine("> The full text is available in the UI for detailed review.")
        appendLine()
        appendLine(outlineContent.substringBeforeLast("\n**Status:**").trim())
      }

      log.info("NarrativeGenerationTask completed: scenes=${generatedScenes.size}, words=$cumulativeWordCount, time=${totalTime}ms")

      task.safeComplete("Narrative generation complete: ${generatedScenes.size} scenes, $cumulativeWordCount words in ${totalTime / 1000}s", log)
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

  private fun transcript(task: SessionTask): FileOutputStream? {
    val (link, file) = task.createFile("transcript.md")
    val markdownTranscript = file?.outputStream()
    task.complete(
      "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
        link.removeSuffix(
          ".md"
        )
      }.pdf' target='_blank'>pdf</a>"
    )
    return markdownTranscript
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