package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.platform.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Semaphore

class ScriptwritingTask(
  orchestrationConfig: OrchestrationConfig, planTask: ScriptwritingTaskExecutionConfigData?
) : AbstractTask<ScriptwritingTask.ScriptwritingTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig, planTask
) {

  class ScriptwritingTaskExecutionConfigData(
    @Description("The topic or subject of the script") var topic: String? = null,

    @Description("The type of script to generate") var script_type: String = "video",

    @Description("Target duration in minutes") var target_duration_minutes: Int = 5,

    @Description("The intended audience for the script") var target_audience: String = "general public",

    @Description("The tone of the script") var tone: String = "professional",

    @Description("Whether to include visual/scene directions") var include_directions: Boolean = true,

    @Description("Whether to include timing markers") var include_timing: Boolean = true,

    @Description("Whether to suggest B-roll or supporting visuals") var suggest_b_roll: Boolean = true,

    @Description("Whether to include speaker notes or production notes") var include_notes: Boolean = true,

    @Description("Whether to mark key points for emphasis or graphics") var mark_key_points: Boolean = true,

    @Description("The pacing style") var pacing: String = "moderate",

    @Description("Whether to include an opening hook") var include_hook: Boolean = true,

    @Description("Whether to include a call-to-action") var include_cta: Boolean = true,

    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task") var related_files: List<String>? = null,

    @Description("Number of revision passes") var revision_passes: Int = 1,

    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = Scriptwriting.name,
    task_description = task_description ?: "Generate script for: '$topic'",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (topic.isNullOrBlank()) {
        return "topic must not be null or blank"
      }
      if (target_duration_minutes <= 0 || target_duration_minutes > 180) {
        target_duration_minutes = target_duration_minutes.coerceIn(1, 180)
      }
      if (script_type.isBlank()) {
        return "script_type must not be blank"
      }
      if (tone.isBlank()) {
        return "tone must not be blank"
      }
      val validPacing = setOf("slow", "moderate", "fast", "dynamic")
      if (pacing.lowercase() !in validPacing) {
        pacing = "moderate"
      }
      if (revision_passes < 0 || revision_passes > 5) {
        revision_passes = revision_passes.coerceIn(0, 5)
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class ScriptOutline(
    @Description("The script title") var title: String = "",
    @Description("Opening hook or attention grabber") var hook: String = "",
    @Description("Main sections of the script") var sections: List<ScriptSection> = emptyList(),
    @Description("Closing and call-to-action") var closing: String = "",
    @Description("Estimated total duration in seconds") var estimated_duration_seconds: Int = 0,
    @Description("Key messages to convey") var key_messages: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "title must not be blank"
      if (sections.isEmpty()) return "sections must not be empty"
      return ValidatedObject.validateFields(this)
    }
  }

  data class ScriptSection(
    @Description("Section number") var section_number: Int = 1,
    @Description("Section title or purpose") var title: String = "",
    @Description("Key points to cover in this section") var key_points: List<String> = emptyList(),
    @Description("Visual elements or B-roll suggestions") var visual_suggestions: List<String> = emptyList(),
    @Description("Estimated duration in seconds") var estimated_duration_seconds: Int = 0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (section_number < 1) return "section_number must be positive"
      if (title.isBlank()) return "title must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class ScriptSegment(
    @Description("Segment type") var segment_type: String = "",
    @Description("The spoken dialogue or narration") var dialogue: String = "",
    @Description("Visual directions or scene description") var visual_direction: String = "",
    @Description("B-roll or supporting visual suggestions") var b_roll_suggestions: List<String> = emptyList(),
    @Description("Production notes or speaker notes") var notes: String = "",
    @Description("Timing marker in MM:SS format") var timing: String = "",
    @Description("Key points marked for emphasis or graphics") var key_points_marked: List<String> = emptyList(),
    @Description("Estimated duration in seconds") var duration_seconds: Int = 0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (dialogue.isBlank()) return "dialogue must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String = buildString {
    appendLine("Scriptwriting - Generate complete scripts for videos, podcasts, and presentations")
    appendLine("  ** Optionally, list input files (supports glob patterns) to be examined when generating the script")
    appendLine("  ** Specify the topic and script type (video, podcast, presentation, etc.)")
    appendLine("  ** Set target duration and audience")
    appendLine("  ** Configure tone and pacing")
    appendLine("  ** Include visual directions, timing markers, and B-roll suggestions")
    appendLine("  ** Mark key points for emphasis or graphics")
    appendLine("  ** Add speaker notes and production notes")
    appendLine("  ** Performs outline creation, segment writing, and timing calculation")
    appendLine("  ** Produces complete, production-ready script with all necessary elements")
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val startTime = System.currentTimeMillis()
    log.info("Starting ScriptwritingTask for topic: '${executionConfig?.topic}'")
    var transcript: FileOutputStream? = null
    try {
      transcript = task.newUserFileStream(transcriptFile())
      val executionConfig = executionConfig ?: run {
        log.error("No execution configuration provided for ScriptwritingTask")
        task.safeComplete("CONFIGURATION ERROR: No execution configuration provided", log)
        transcript?.write("\n## Configuration Error\n\nNo execution configuration provided.\n".toByteArray())
        resultFn("CONFIGURATION ERROR: No execution configuration provided")
        return
      }
      // Validate configuration
      executionConfig?.validate()?.let { validationError ->
        log.error("Configuration validation failed: $validationError")
        task.safeComplete("CONFIGURATION ERROR: $validationError", log)
        task.error(ValidatedObject.ValidationError(validationError, executionConfig))
        transcript?.write("\n## Validation Error\n\n$validationError\n".toByteArray())
        resultFn("CONFIGURATION ERROR: $validationError")
        return
      }

      val topic = executionConfig?.topic
      if (topic.isNullOrBlank()) {
        log.error("No topic specified for scriptwriting")
        task.safeComplete("CONFIGURATION ERROR: No topic specified", log)
        transcript?.write("\n## Error\n\nNo topic specified for scriptwriting.\n".toByteArray())
        resultFn("CONFIGURATION ERROR: No topic specified")
        return
      }

      val api = defaultSmart.getChildClient(task)
      val fastApi = defaultFast.getChildClient(task)
      val tabs = TabbedDisplay(task)
      val semaphore = Semaphore(0)

      // Overview tab
      val overviewTask = tabs.newTask("Overview")

      val overviewContent = buildString {
        appendLine("# Script Generation")
        appendLine()
        appendLine("**Topic:** $topic")
        appendLine()
      }
      transcript?.write(overviewContent.toByteArray())
      transcript?.write("\n".toByteArray())
      overviewTask.add(overviewContent.renderMarkdown(true))
      task.update()
      val overviewContent2 = buildString {
        appendLine()
        appendLine("## Configuration")
        appendLine("- Script Type: ${executionConfig.script_type}")
        appendLine("- Target Duration: ${executionConfig.target_duration_minutes} minutes")
        appendLine("- Target Audience: ${executionConfig.target_audience}")
        appendLine("- Tone: ${executionConfig.tone}")
        appendLine("- Pacing: ${executionConfig.pacing}")
        appendLine("- Include Directions: ${if (executionConfig.include_directions) "✓" else "✗"}")
        appendLine("- Include Timing: ${if (executionConfig.include_timing) "✓" else "✗"}")
        appendLine("- Suggest B-Roll: ${if (executionConfig.suggest_b_roll) "✓" else "✗"}")
        appendLine("- Include Notes: ${if (executionConfig.include_notes) "✓" else "✗"}")
        appendLine("- Mark Key Points: ${if (executionConfig.mark_key_points) "✓" else "✗"}")
        appendLine()
        appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## Progress")
        appendLine()
        appendLine("### Phase 1: Research & Outline")
        appendLine("*Analyzing topic and creating script structure...*")
      }
      transcript?.write(overviewContent2.toByteArray())
      overviewTask.add(overviewContent2.renderMarkdown(true))
      task.update()

      val resultBuilder = StringBuilder()
      transcript?.write("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
      transcript?.write("# Research Context\n<details>\n<summary>Context Details</summary>\n\n".toByteArray())
      transcript?.write("Context loaded from prior tasks and related files.\n\n".toByteArray())
      resultBuilder.append("# Script: $topic\n\n")

      // Gather context
      val priorContext = getPriorCode(agent.executionState)
      val inputFileContent = getInputFileContent()
      val contextFiles = getContextFiles()

      val allContext = buildString {
        if (priorContext.isNotBlank()) append(priorContext)
        if (inputFileContent.isNotBlank()) append(inputFileContent)
        if (contextFiles.isNotBlank()) append(contextFiles)
      }

      if (allContext.isNotBlank()) {
        log.debug("Found context: priorContext=${priorContext.length} chars, inputFiles=${inputFileContent.length} chars, contextFiles=${contextFiles.length} chars")
        val contextTask = tabs.newTask("Research Context")
        contextTask.add(
          buildString {
            appendLine("# Research Context")
            appendLine()
            if (priorContext.isNotBlank()) {
              appendLine("## Prior Context")
              appendLine(priorContext.take(2000))
              appendLine()
            }
            if (inputFileContent.isNotBlank()) {
              appendLine("## Input Files")
              appendLine(inputFileContent.take(2000))
              appendLine()
            }
            if (contextFiles.isNotBlank()) {
              appendLine("## Related Files")
              appendLine(contextFiles.take(2000))
            }
          }.renderMarkdown(true)
        )
        task.update()
      }
      transcript?.write("\n</details>\n".toByteArray())

      // Phase 1: Create outline
      log.info("Phase 1: Creating script outline")
      val outlineTask = tabs.newTask("Outline")

      outlineTask.add(
        buildString {
          appendLine("# Script Outline")
          appendLine()
          appendLine("**Status:** Creating structured outline...")
          appendLine()
        }.renderMarkdown(true)
      )
      transcript?.write("# Script Outline\n<details>\n<summary>Outline Details</summary>\n\n".toByteArray())
      task.update()

      val targetDurationSeconds = executionConfig.target_duration_minutes * 60
      val wordsPerMinute = when (executionConfig.pacing.lowercase()) {
        "slow" -> 120
        "moderate" -> 150
        "fast" -> 180
        "dynamic" -> 160
        else -> 150
      }
      val targetWordCount = executionConfig.target_duration_minutes * wordsPerMinute
      val outlinePrompt = buildString {
        appendLine("You are an expert scriptwriter specializing in ${executionConfig.script_type} scripts. Create a detailed outline for this script.")
        appendLine()
        appendLine("Topic: $topic")
        appendLine()
        appendLine("Script Type: ${executionConfig.script_type}")
        appendLine("Target Duration: ${executionConfig.target_duration_minutes} minutes (~$targetDurationSeconds seconds)")
        appendLine("Target Audience: ${executionConfig.target_audience}")
        appendLine("Tone: ${executionConfig.tone}")
        appendLine("Pacing: ${executionConfig.pacing} (~$wordsPerMinute words per minute)")
        appendLine("Target Word Count: ~$targetWordCount words")
        appendLine()
        if (priorContext.isNotBlank()) {
          appendLine("Research Context:")
          appendLine(priorContext.take(3000))
          appendLine()
        }
        if (inputFileContent.isNotBlank()) {
          appendLine("Input File Content:")
          appendLine(inputFileContent.take(3000))
          appendLine()
        }
        if (contextFiles.isNotBlank()) {
          appendLine("Additional Research:")
          appendLine(contextFiles.take(3000))
          appendLine()
        }
        appendLine("Create an outline with:")
        appendLine("1. A compelling title")
        if (executionConfig.include_hook) {
          appendLine("2. An attention-grabbing opening hook (10-15 seconds)")
        }
        appendLine("3. 3-5 main sections that logically progress through the topic")
        appendLine("4. Key points to cover in each section")
        if (executionConfig.suggest_b_roll) {
          appendLine("5. Visual suggestions for each section")
        }
        appendLine("6. Estimated duration for each section")
        if (executionConfig.include_cta) {
          appendLine("7. A strong closing with call-to-action")
        } else {
          appendLine("7. A memorable closing")
        }
        appendLine()
        appendLine("Ensure the outline:")
        appendLine("- Flows logically from introduction to conclusion")
        appendLine("- Maintains the ${executionConfig.tone} tone throughout")
        appendLine("- Fits within the ${executionConfig.target_duration_minutes}-minute timeframe")
        appendLine("- Engages the ${executionConfig.target_audience}")
        appendLine("- Balances information delivery with entertainment/engagement")
      }


      val outlineAgent = ParsedAgent(
        resultClass = ScriptOutline::class.java,
        prompt = outlinePrompt,
        model = api,
        temperature = 0.7,
        parsingModel = fastApi
      )

      val outline = outlineAgent.answer(listOf("Generate outline")).obj

      // Validate outline
      outline.validate()?.let { validationError ->
        log.error("Outline validation failed: $validationError")
        outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
        transcript?.write("\n## Outline Validation Error\n\n$validationError\n".toByteArray())
        task.safeComplete("Outline validation failed: $validationError", log)
        resultFn("ERROR: Outline validation failed: $validationError")
        return
      }

      log.info("Generated outline: ${outline.sections.size} sections, ${outline.estimated_duration_seconds}s estimated")

      val outlineContent = buildString {
        appendLine("## ${outline.title}")
        appendLine()
        if (executionConfig.include_hook && outline.hook.isNotBlank()) {
          appendLine("### Opening Hook")
          appendLine(outline.hook)
          appendLine()
        }
        appendLine("### Key Messages")
        outline.key_messages.forEach { message ->
          appendLine("- $message")
        }
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("### Script Sections")
        outline.sections.forEach { section ->
          appendLine("#### Section ${section.section_number}: ${section.title}")
          appendLine()
          appendLine("**Duration:** ~${section.estimated_duration_seconds}s")
          appendLine()
          appendLine("**Key Points:**")
          section.key_points.forEach { point ->
            appendLine("- $point")
          }
          appendLine()
          if (section.visual_suggestions.isNotEmpty()) {
            appendLine("**Visual Suggestions:**")
            section.visual_suggestions.forEach { visual ->
              appendLine("- $visual")
            }
            appendLine()
          }
          appendLine("---")
          appendLine()
        }
        appendLine("### Closing")
        appendLine(outline.closing)
        appendLine()
        appendLine("**Total Estimated Duration:** ${outline.estimated_duration_seconds}s (${outline.estimated_duration_seconds / 60}m ${outline.estimated_duration_seconds % 60}s)")
        appendLine()
        appendLine("**Status:** ✅ Complete")
      }
      outlineTask.add(outlineContent.renderMarkdown(true))
      transcript?.write(outlineContent.toByteArray())
      transcript?.write("\n</details>\n".toByteArray())
      task.update()

      overviewTask.add(
        "✅ Phase 1 Complete: Outline created (${outline.sections.size} sections)\n".renderMarkdown(
          true
        )
      )
      overviewTask.add(
        "\n### Phase 2: Script Writing\n*Writing detailed script segments...*\n".renderMarkdown(
          true
        )
      )
      task.update()

      // Phase 2: Write script segments
      log.info("Phase 2: Writing script segments")
      val scriptSegments = mutableListOf<ScriptSegment>()
      var cumulativeDuration = 0
      var cumulativeWordCount = 0

      // Write opening if hook is included
      if (executionConfig.include_hook && outline.hook.isNotBlank()) {
        log.info("Writing opening hook")
        overviewTask.add("- Opening Hook ".renderMarkdown(true))
        task.update()

        val hookTask = tabs.newTask("Opening")

        hookTask.add(
          buildString {
            appendLine("# Opening Hook")
            appendLine()
            appendLine("**Status:** Writing opening...")
            appendLine()
          }.renderMarkdown(true)
        )
        task.update()
        val hookPrompt = buildString {
          appendLine("You are an expert scriptwriter. Write the opening hook for this ${executionConfig.script_type} script.")
          appendLine()
          appendLine("Topic: $topic")
          appendLine("Hook Concept: ${outline.hook}")
          appendLine("Tone: ${executionConfig.tone}")
          appendLine("Target Audience: ${executionConfig.target_audience}")
          appendLine("Target Duration: 10-15 seconds")
          appendLine()
          appendLine("Write an opening that:")
          appendLine("1. Immediately grabs attention")
          appendLine("2. Sets the tone for the entire script")
          appendLine("3. Hints at what's coming")
          appendLine("4. Is conversational and natural for spoken delivery")
          if (executionConfig.include_directions) {
            appendLine("5. Includes visual direction for what the viewer sees")
          }
          if (executionConfig.suggest_b_roll) {
            appendLine("6. Suggests B-roll or supporting visuals")
          }
          appendLine()
          appendLine("Make it punchy, engaging, and memorable.")
          appendLine("Ensure the dialogue sounds natural when spoken aloud.")
        }


        val hookAgent = ParsedAgent(
          resultClass = ScriptSegment::class.java,
          prompt = hookPrompt,
          model = api,
          temperature = 0.8,
          parsingModel = fastApi
        )

        val hookSegment = hookAgent.answer(listOf("Write opening")).obj
        scriptSegments.add(hookSegment)
        cumulativeDuration += hookSegment.duration_seconds
        cumulativeWordCount += hookSegment.dialogue.split("\\s+".toRegex()).size

        hookTask.add(
          buildString {
            appendLine("## Opening Hook")
            appendLine()
            if (executionConfig.include_timing) {
              appendLine("**[${formatTiming(0)}]**")
              appendLine()
            }
            if (executionConfig.include_directions && hookSegment.visual_direction.isNotBlank()) {
              appendLine("*${hookSegment.visual_direction}*")
              appendLine()
            }
            appendLine(hookSegment.dialogue)
            appendLine()
            if (executionConfig.suggest_b_roll && hookSegment.b_roll_suggestions.isNotEmpty()) {
              appendLine("**B-Roll:**")
              hookSegment.b_roll_suggestions.forEach { broll ->
                appendLine("- $broll")
              }
              appendLine()
            }
            if (executionConfig.include_notes && hookSegment.notes.isNotBlank()) {
              appendLine("**Notes:** ${hookSegment.notes}")
              appendLine()
            }
            appendLine("---")
            appendLine()
            appendLine(
              "**Duration:** ${hookSegment.duration_seconds}s | **Words:** ${
                hookSegment.dialogue.split(
                  "\\s+".toRegex()
                ).size
              }"
            )
            appendLine()
            appendLine("**Status:** ✅ Complete")
          }.renderMarkdown(true)
        )
        transcript?.write("# Opening Hook\n\n".toByteArray())
        transcript?.write(hookSegment.dialogue.toByteArray())
        transcript?.write("\n\n".toByteArray())
        task.update()

        overviewTask.add("✅\n".renderMarkdown(true))
        task.update()
      }

      // Write each section
      outline.sections.forEachIndexed { index, sectionOutline ->
        log.info("Writing section ${index + 1}/${outline.sections.size}: ${sectionOutline.title}")

        overviewTask.add(
          "- Section ${sectionOutline.section_number}: ${sectionOutline.title} ".renderMarkdown(
            true
          )
        )
        task.update()

        val sectionTask = tabs.newTask("Section ${sectionOutline.section_number}")

        sectionTask.add(
          buildString {
            appendLine("# Section ${sectionOutline.section_number}: ${sectionOutline.title}")
            appendLine()
            appendLine("**Status:** Writing section...")
            appendLine()
          }.renderMarkdown(true)
        )
        task.update()

        // Build context from previous segments
        val previousContext = if (scriptSegments.isNotEmpty()) {
          buildString {
            appendLine("## Previous Script Context")
            val lastSegments = scriptSegments.takeLast(2)
            lastSegments.forEach { prevSegment ->
              appendLine("**Previous Dialogue:**")
              appendLine(prevSegment.dialogue.takeLast(200))
              appendLine()
            }
            appendLine("**Current Duration:** ${cumulativeDuration}s")
          }
        } else {
          "This is the first main section."
        }
        val sectionPrompt = buildString {
          appendLine("You are an expert scriptwriter. Write Section ${sectionOutline.section_number} of this ${executionConfig.script_type} script.")
          appendLine()
          appendLine("Overall Topic: $topic")
          appendLine("Section Title: ${sectionOutline.title}")
          appendLine("Section Purpose: Cover these key points: ${sectionOutline.key_points.joinToString("; ")}")
          appendLine("Target Duration: ${sectionOutline.estimated_duration_seconds} seconds")
          appendLine("Tone: ${executionConfig.tone}")
          appendLine("Pacing: ${executionConfig.pacing}")
          appendLine()
          appendLine(previousContext)
          appendLine()
          if (sectionOutline.visual_suggestions.isNotEmpty()) {
            appendLine("Visual Suggestions: ${sectionOutline.visual_suggestions.joinToString("; ")}")
            appendLine()
          }
          appendLine("Write this section with:")
          appendLine("1. Natural, conversational dialogue that sounds good when spoken")
          appendLine("2. Clear transitions from the previous section")
          appendLine("3. Logical flow through the key points")
          appendLine("4. Appropriate pacing for ${executionConfig.pacing} delivery")
          if (executionConfig.include_directions) {
            appendLine("5. Visual directions for what appears on screen")
          }
          if (executionConfig.suggest_b_roll) {
            appendLine("6. B-roll suggestions to support the narration")
          }
          if (executionConfig.include_notes) {
            appendLine("7. Production notes for the speaker/director")
          }
          if (executionConfig.mark_key_points) {
            appendLine("8. Mark key points that should be emphasized or shown as graphics")
          }
          appendLine()
          appendLine("Ensure the dialogue:")
          appendLine("- Sounds natural when read aloud")
          appendLine("- Uses contractions and conversational language")
          appendLine("- Varies sentence length for rhythm")
          appendLine("- Includes pauses where appropriate")
          appendLine("- Maintains the ${executionConfig.tone} tone")
          appendLine("- Engages the ${executionConfig.target_audience}")
          appendLine()
          appendLine("Aim for approximately ${sectionOutline.estimated_duration_seconds} seconds of content.")
        }


        val sectionAgent = ParsedAgent(
          resultClass = ScriptSegment::class.java,
          prompt = sectionPrompt,
          model = api,
          temperature = 0.8,
          parsingModel = fastApi
        )

        val sectionSegment = sectionAgent.answer(listOf("Write section")).obj
        scriptSegments.add(sectionSegment)
        cumulativeDuration += sectionSegment.duration_seconds
        cumulativeWordCount += sectionSegment.dialogue.split("\\s+".toRegex()).size

        sectionTask.add(
          buildString {
            appendLine("## ${sectionOutline.title}")
            appendLine()
            if (executionConfig.include_timing) {
              appendLine("**[${formatTiming(cumulativeDuration - sectionSegment.duration_seconds)}]**")
              appendLine()
            }
            if (executionConfig.include_directions && sectionSegment.visual_direction.isNotBlank()) {
              appendLine("*${sectionSegment.visual_direction}*")
              appendLine()
            }
            appendLine(sectionSegment.dialogue)
            appendLine()
            if (executionConfig.suggest_b_roll && sectionSegment.b_roll_suggestions.isNotEmpty()) {
              appendLine("**B-Roll:**")
              sectionSegment.b_roll_suggestions.forEach { broll ->
                appendLine("- $broll")
              }
              appendLine()
            }
            if (executionConfig.mark_key_points && sectionSegment.key_points_marked.isNotEmpty()) {
              appendLine("**Key Points for Graphics:**")
              sectionSegment.key_points_marked.forEach { point ->
                appendLine("- $point")
              }
              appendLine()
            }
            if (executionConfig.include_notes && sectionSegment.notes.isNotBlank()) {
              appendLine("**Notes:** ${sectionSegment.notes}")
              appendLine()
            }
            appendLine("---")
            appendLine()
            appendLine(
              "**Duration:** ${sectionSegment.duration_seconds}s | **Words:** ${
                sectionSegment.dialogue.split(
                  "\\s+".toRegex()
                ).size
              }"
            )
            appendLine()
            appendLine("**Status:** ✅ Complete")
          }.renderMarkdown(true)
        )
        transcript?.write("## Section ${sectionOutline.section_number}: ${sectionOutline.title}\n\n".toByteArray())
        transcript?.write(sectionSegment.dialogue.toByteArray())
        transcript?.write("\n\n".toByteArray())
        task.update()

        overviewTask.add("✅ (${sectionSegment.duration_seconds}s)\n".renderMarkdown(true))
        task.update()
      }

      // Write closing
      log.info("Writing closing")
      overviewTask.add("- Closing ".renderMarkdown(true))
      task.update()

      val closingTask = tabs.newTask("Closing")

      closingTask.add(
        buildString {
          appendLine("# Closing")
          appendLine()
          appendLine("**Status:** Writing closing...")
          appendLine()
        }.renderMarkdown(true)
      )
      task.update()
      val closingPrompt = buildString {
        appendLine("You are an expert scriptwriter. Write the closing for this ${executionConfig.script_type} script.")
        appendLine()
        appendLine("Topic: $topic")
        appendLine("Closing Concept: ${outline.closing}")
        appendLine("Tone: ${executionConfig.tone}")
        appendLine("Target Audience: ${executionConfig.target_audience}")
        if (executionConfig.include_cta) {
          appendLine("Include Call-to-Action: Yes")
        }
        appendLine()
        appendLine("Key Messages Covered:")
        outline.key_messages.forEach { appendLine("- $it") }
        appendLine()
        appendLine("Previous Script Context:")
        appendLine(scriptSegments.takeLast(1).firstOrNull()?.dialogue?.takeLast(200) ?: "")
        appendLine()
        appendLine("Write a closing that:")
        appendLine("1. Summarizes the key takeaways")
        appendLine("2. Reinforces the main message")
        appendLine("3. Leaves a lasting impression")
        if (executionConfig.include_cta) {
          appendLine("4. Includes a clear, compelling call-to-action")
        } else {
          appendLine("4. Ends on a strong note")
        }
        appendLine("5. Sounds natural and conversational")
        if (executionConfig.include_directions) {
          appendLine("6. Includes visual direction for the final shot")
        }
        appendLine()
        appendLine("Make it memorable and motivating.")
        appendLine("Target duration: 15-20 seconds.")
      }


      val closingAgent = ParsedAgent(
        resultClass = ScriptSegment::class.java,
        prompt = closingPrompt,
        model = api,
        temperature = 0.8,
        parsingModel = fastApi
      )

      val closingSegment = closingAgent.answer(listOf("Write closing")).obj
      scriptSegments.add(closingSegment)
      cumulativeDuration += closingSegment.duration_seconds
      cumulativeWordCount += closingSegment.dialogue.split("\\s+".toRegex()).size

      closingTask.add(
        buildString {
          appendLine("## Closing")
          appendLine()
          if (executionConfig.include_timing) {
            appendLine("**[${formatTiming(cumulativeDuration - closingSegment.duration_seconds)}]**")
            appendLine()
          }
          if (executionConfig.include_directions && closingSegment.visual_direction.isNotBlank()) {
            appendLine("*${closingSegment.visual_direction}*")
            appendLine()
          }
          appendLine(closingSegment.dialogue)
          appendLine()
          if (executionConfig.suggest_b_roll && closingSegment.b_roll_suggestions.isNotEmpty()) {
            appendLine("**B-Roll:**")
            closingSegment.b_roll_suggestions.forEach { broll ->
              appendLine("- $broll")
            }
            appendLine()
          }
          if (executionConfig.include_notes && closingSegment.notes.isNotBlank()) {
            appendLine("**Notes:** ${closingSegment.notes}")
            appendLine()
          }
          appendLine("---")
          appendLine()
          appendLine(
            "**Duration:** ${closingSegment.duration_seconds}s | **Words:** ${
              closingSegment.dialogue.split(
                "\\s+".toRegex()
              ).size
            }"
          )
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }.renderMarkdown(true)
      )
      transcript?.write("# Closing\n\n".toByteArray())
      transcript?.write(closingSegment.dialogue.toByteArray())
      transcript?.write("\n\n".toByteArray())
      task.update()

      overviewTask.add("✅\n".renderMarkdown(true))
      overviewTask.add("✅ Phase 2 Complete: All segments written\n".renderMarkdown(true))
      task.update()

      // Phase 3: Revision (if enabled)
      if (executionConfig.revision_passes > 0) {
        overviewTask.add(
          "\n### Phase 3: Revision\n*Refining script for flow and timing...*\n".renderMarkdown(
            true
          )
        )
        task.update()

        log.info("Phase 3: Performing ${executionConfig.revision_passes} revision pass(es)")
        val revisionTask = tabs.newTask("Revision")

        revisionTask.add(
          buildString {
            appendLine("# Revision Process")
            appendLine()
            appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
            appendLine()
          }.renderMarkdown(true)
        )
        task.update()

        var fullScript = buildFullScript(outline, scriptSegments)

        repeat(executionConfig.revision_passes) { passNum ->
          log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")
          val revisionPrompt = buildString {
            appendLine("You are an expert script editor. Review and improve this ${executionConfig.script_type} script.")
            appendLine()
            appendLine("Current Script:")
            appendLine(fullScript)
            appendLine()
            appendLine("Focus on:")
            appendLine("1. Natural dialogue flow and conversational tone")
            appendLine("2. Pacing and rhythm (target: ${executionConfig.pacing})")
            appendLine("3. Clarity and conciseness")
            appendLine("4. Smooth transitions between sections")
            appendLine("5. Timing accuracy (target: ${executionConfig.target_duration_minutes} minutes)")
            appendLine("6. Engagement and audience connection")
            appendLine("7. Consistency in tone (${executionConfig.tone})")
            appendLine()
            appendLine("Maintain:")
            appendLine("- All key messages and content")
            appendLine("- The overall structure")
            appendLine("- Approximate duration ($cumulativeDuration seconds)")
            appendLine("- The ${executionConfig.tone} tone")
            appendLine()
            appendLine("Provide the complete revised script with all formatting intact.")
          }


          val revisionAgent = ChatAgent(
            prompt = revisionPrompt,
            model = api,
            temperature = 0.6
          )

          val revisedScript = revisionAgent.answer(listOf("Revise the script"))
          fullScript = revisedScript

          revisionTask.add(
            buildString {
              appendLine("## Revision Pass ${passNum + 1}")
              appendLine()
              appendLine("<details><summary>Revised Script</summary>")
              appendLine()
              appendLine(revisedScript)
              appendLine()
              appendLine("</details>")
              appendLine()
              appendLine("✅ Complete")
              appendLine()
            }.renderMarkdown(true)
          )
          task.update()
        }

        overviewTask.add(
          "✅ Phase 3 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown(
            true
          )
        )
      }
      transcript?.write("\n</div>\n\n".toByteArray()) // Close work-details div


      // Phase 4: Final Assembly
      overviewTask.add("\n### Phase 4: Final Assembly\n*Compiling complete script...*\n".renderMarkdown(true))
      task.update()

      log.info("Phase 4: Assembling final script")
      val finalTask = tabs.newTask("Complete Script")

      val finalScript = buildString {
        appendLine("# ${outline.title}")
        appendLine()
        appendLine("**Script Type:** ${executionConfig.script_type.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}")
        appendLine("**Duration:** ${formatTiming(cumulativeDuration)} (${cumulativeDuration}s)")
        appendLine("**Word Count:** $cumulativeWordCount")
        appendLine("**Tone:** ${executionConfig.tone.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}")
        appendLine("**Target Audience:** ${executionConfig.target_audience}")
        appendLine()
        appendLine("---")
        appendLine()

        var currentTime = 0
        scriptSegments.forEachIndexed { index, segment ->
          if (executionConfig.include_timing) {
            appendLine("**[${formatTiming(currentTime)}]**")
            appendLine()
          }

          if (executionConfig.include_directions && segment.visual_direction.isNotBlank()) {
            appendLine("*${segment.visual_direction}*")
            appendLine()
          }

          appendLine(segment.dialogue)
          appendLine()

          if (executionConfig.suggest_b_roll && segment.b_roll_suggestions.isNotEmpty()) {
            appendLine("**B-Roll:**")
            segment.b_roll_suggestions.forEach { broll ->
              appendLine("- $broll")
            }
            appendLine()
          }

          if (executionConfig.mark_key_points && segment.key_points_marked.isNotEmpty()) {
            appendLine("**Key Points for Graphics:**")
            segment.key_points_marked.forEach { point ->
              appendLine("- $point")
            }
            appendLine()
          }

          if (executionConfig.include_notes && segment.notes.isNotBlank()) {
            appendLine("**Notes:** ${segment.notes}")
            appendLine()
          }

          currentTime += segment.duration_seconds

          if (index < scriptSegments.size - 1) {
            appendLine("---")
            appendLine()
          }
        }

        appendLine()
        appendLine("---")
        appendLine()
        appendLine("**END OF SCRIPT**")
        appendLine()
        appendLine("**Total Duration:** ${formatTiming(cumulativeDuration)}")
        appendLine("**Total Word Count:** $cumulativeWordCount")
        appendLine("**Average Words Per Minute:** ${(cumulativeWordCount.toFloat() / (cumulativeDuration / 60f)).toInt()}")
      }

      finalTask.add(finalScript.renderMarkdown(true))
      transcript?.write("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
      transcript?.write("# Complete Script\n\n".toByteArray())
      transcript?.write(finalScript.toByteArray())
      transcript?.write("\n".toByteArray())
      transcript?.write("\n</div>\n\n".toByteArray())
      task.update()

      // Production notes tab
      if (executionConfig.include_notes) {
        val productionNotesTask = tabs.newTask("Production Notes")

        val productionNotes = buildString {
          appendLine("# Production Notes")
          appendLine()
          appendLine("## Script Overview")
          appendLine("- **Total Duration:** ${formatTiming(cumulativeDuration)}")
          appendLine("- **Total Segments:** ${scriptSegments.size}")
          appendLine("- **Word Count:** $cumulativeWordCount")
          appendLine("- **Average WPM:** ${(cumulativeWordCount.toFloat() / (cumulativeDuration / 60f)).toInt()}")
          appendLine()
          appendLine("## Timing Breakdown")
          var segmentTime = 0
          scriptSegments.forEachIndexed { index, segment ->
            val segmentType = when {
              index == 0 && executionConfig.include_hook -> "Opening Hook"
              index == scriptSegments.size - 1 -> "Closing"
              else -> "Section ${index}"
            }
            appendLine("- **$segmentType:** ${formatTiming(segmentTime)} - ${formatTiming(segmentTime + segment.duration_seconds)} (${segment.duration_seconds}s)")
            segmentTime += segment.duration_seconds
          }
          appendLine()
          if (executionConfig.suggest_b_roll) {
            appendLine("## B-Roll Requirements")
            val allBRoll = scriptSegments.flatMap { it.b_roll_suggestions }.distinct()
            if (allBRoll.isNotEmpty()) {
              allBRoll.forEach { broll ->
                appendLine("- $broll")
              }
            } else {
              appendLine("*No specific B-roll requirements*")
            }
            appendLine()
          }
          if (executionConfig.mark_key_points) {
            appendLine("## Graphics/Text Overlays")
            val allKeyPoints = scriptSegments.flatMap { it.key_points_marked }
            if (allKeyPoints.isNotEmpty()) {
              allKeyPoints.forEach { point ->
                appendLine("- $point")
              }
            } else {
              appendLine("*No specific graphics requirements*")
            }
            appendLine()
          }
          appendLine("## Key Messages")
          outline.key_messages.forEach { message ->
            appendLine("- $message")
          }
        }

        productionNotesTask.add(productionNotes.renderMarkdown(true))
        transcript?.write("\n---\n\n".toByteArray())
        transcript?.write(productionNotes.toByteArray())
        transcript?.write("\n".toByteArray())
        task.update()
      }

      // Final statistics
      val totalTime = System.currentTimeMillis() - startTime
      val targetDurationDiff = cumulativeDuration - targetDurationSeconds
      val durationAccuracy = 100 - (Math.abs(targetDurationDiff).toFloat() / targetDurationSeconds * 100)

      overviewTask.add(
        buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ✅ Generation Complete")
          appendLine()
          appendLine("**Statistics:**")
          appendLine("- Total Duration: ${formatTiming(cumulativeDuration)} (${cumulativeDuration}s)")
          appendLine("- Target Duration: ${formatTiming(targetDurationSeconds)} (${targetDurationSeconds}s)")
          appendLine("- Duration Accuracy: ${durationAccuracy.toInt()}%")
          appendLine("- Total Word Count: $cumulativeWordCount")
          appendLine("- Average WPM: ${(cumulativeWordCount.toFloat() / (cumulativeDuration / 60f)).toInt()}")
          appendLine("- Number of Segments: ${scriptSegments.size}")
          appendLine("- Total Time: ${totalTime / 1000.0}s")
          appendLine()
          appendLine(
            "**Completed:** ${
              LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }"
          )
        }.renderMarkdown(true)
      )
      transcript?.write("\n---\n\n## Generation Complete\n\n".toByteArray())
      transcript?.write("Script generation completed successfully.\n".toByteArray())
      task.update()

      // Concise summary for resultFn
      val finalResult = buildString {
        appendLine("# Script Generation Summary: ${outline.title}")
        appendLine()
        appendLine("A complete ${executionConfig.script_type} script of **${formatTiming(cumulativeDuration)}** ($cumulativeWordCount words) was generated in **${totalTime / 1000.0}s**.")
        appendLine()
        appendLine("**Topic:** $topic")
        appendLine()
        appendLine("**Structure:**")
        if (executionConfig.include_hook) {
          appendLine("- Opening hook")
        }
        appendLine("- ${outline.sections.size} main sections")
        appendLine("- Closing${if (executionConfig.include_cta) " with call-to-action" else ""}")
        appendLine()
        appendLine("**Duration Accuracy:** ${durationAccuracy.toInt()}% (target: ${executionConfig.target_duration_minutes}m)")
        appendLine()
        appendLine("> The complete script with all formatting, timing, and production notes is available in the Complete Script tab.")
      }

      log.info("ScriptwritingTask completed: duration=${cumulativeDuration}s, words=$cumulativeWordCount, segments=${scriptSegments.size}, time=${totalTime}ms")

      if (orchestrationConfig.autoFix) {
        log.info("Auto-fix enabled, completing automatically")
        transcript?.write("\nAuto-applying: Script generation completed automatically.\n".toByteArray())
        task.complete()
        resultFn(finalResult)
      } else {
        task.add(acceptButtonFooter(task) {
          semaphore.release()
        })
        semaphore.acquire()
        transcript?.write("\nUser Action: Accepted script.\n".toByteArray())
        task.complete()
        log.info("Script generation complete: ${formatTiming(cumulativeDuration)} in ${totalTime / 1000}s")
        resultFn(finalResult)
      }

    } catch (e: Exception) {
      // Triple Log Rule: UI, SLF4J, Transcript
      log.error("Error during script generation", e)
      task.error(e)
      transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())

      val errorResult = buildString {
        appendLine("# Error in Script Generation")
        appendLine()
        appendLine("**Topic:** ${executionConfig?.topic}")
        appendLine()
        appendLine("**Error:** ${e.message}")
      }
      resultFn(errorResult)
    } finally {
      transcript?.close()
    }
  }

  private fun getContextFiles(): String {
    val relatedFiles = executionConfig?.related_files ?: return ""
    if (relatedFiles.isEmpty()) return ""
    log.debug("Loading ${relatedFiles.size} related context files")

    return buildString {
      appendLine("## Related Research Files")
      appendLine()
      relatedFiles.forEach { file ->
        try {
          val filePath = root.resolve(file)
          if (filePath.toFile().exists()) {
            log.debug("Successfully loaded context file: $file")
            appendLine("### $file")
            appendLine("```")
            appendLine(filePath.toFile().readText().take(1500))
            appendLine("```")
            appendLine()
          } else {
            log.warn("Context file not found: $file")
          }
        } catch (e: Exception) {
          log.warn("Error reading context files", e)
        }
      }
    }
  }

  private fun getInputFileContent(): String {
    val inputFiles = executionConfig?.related_files ?: return ""
    if (inputFiles.isEmpty()) return ""
    return buildString {
      appendLine("## Input Files")
      appendLine()
      inputFiles.forEach { file ->
        try {
          val filePath = root.resolve(file)
          if (filePath.toFile().exists()) {
            log.debug("Successfully loaded input file: $file")
            appendLine("### $file")
            appendLine("```")
            appendLine(filePath.toFile().readText().take(1500))
            appendLine("```")
            appendLine()
          } else {
            log.warn("Input file not found: $file")
          }
        } catch (e: Exception) {
          log.warn("Error reading input files", e)
        }
      }
    }
  }


  private fun formatTiming(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
  }

  private fun buildFullScript(outline: ScriptOutline, segments: List<ScriptSegment>): String {
    return buildString {
      appendLine("# ${outline.title}")
      appendLine()
      var currentTime = 0
      segments.forEach { segment ->
        appendLine("[${formatTiming(currentTime)}]")
        if (segment.visual_direction.isNotBlank()) {
          appendLine("*${segment.visual_direction}*")
        }
        appendLine(segment.dialogue)
        appendLine()
        currentTime += segment.duration_seconds
      }
    }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(ScriptwritingTask::class.java)

    @JvmStatic
    val Scriptwriting = TaskType(
      name = "Scriptwriting",
      category = "Writing",
      taskClass = ScriptwritingTask::class.java,
      executionConfigClass = ScriptwritingTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generate complete scripts for videos, podcasts, and presentations",
      tooltipHtml = buildString {
        appendLine("Generates production-ready scripts with dialogue, timing, and production notes.")
        appendLine("<ul>")
        appendLine("  <li>Creates detailed script outline with sections and timing</li>")
        appendLine("  <li>Writes natural, conversational dialogue for spoken delivery</li>")
        appendLine("  <li>Includes visual directions and scene descriptions</li>")
        appendLine("  <li>Suggests B-roll and supporting visuals</li>")
        appendLine("  <li>Marks key points for emphasis or graphics</li>")
        appendLine("  <li>Provides timing markers and duration estimates</li>")
        appendLine("  <li>Includes production notes and speaker guidance</li>")
        appendLine("  <li>Supports multiple script types (video, podcast, presentation, commercial)</li>")
        appendLine("  <li>Configurable tone, pacing, and audience targeting</li>")
        appendLine("  <li>Optional revision passes for quality improvement</li>")
        appendLine("  <li>Ideal for video production, podcasts, presentations, training videos</li>")
        appendLine("</ul>")
      },
    )
  }
}