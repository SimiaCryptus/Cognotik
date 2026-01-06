package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ScriptwritingTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: ScriptwritingTaskExecutionConfigData?
) : AbstractTask<ScriptwritingTask.ScriptwritingTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    protected val codeFiles = mutableMapOf<Path, String>()

    class ScriptwritingTaskExecutionConfigData(
        @Description("The topic or subject of the script")
        val topic: String? = null,

        @Description("The type of script to generate")
        val script_type: String = "video",

        @Description("Target duration in minutes")
        val target_duration_minutes: Int = 5,

        @Description("The intended audience for the script")
        val target_audience: String = "general public",

        @Description("The tone of the script")
        val tone: String = "professional",

        @Description("Whether to include visual/scene directions")
        val include_directions: Boolean = true,

        @Description("Whether to include timing markers")
        val include_timing: Boolean = true,

        @Description("Whether to suggest B-roll or supporting visuals")
        val suggest_b_roll: Boolean = true,

        @Description("Whether to include speaker notes or production notes")
        val include_notes: Boolean = true,

        @Description("Whether to mark key points for emphasis or graphics")
        val mark_key_points: Boolean = true,

        @Description("The pacing style")
        val pacing: String = "moderate",

        @Description("Whether to include an opening hook")
        val include_hook: Boolean = true,

        @Description("Whether to include a call-to-action")
        val include_cta: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,


        @Description("Number of revision passes")
        val revision_passes: Int = 1,

        @Description("Related files or research to incorporate")
        val related_files: List<String>? = null,

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
                return "target_duration_minutes must be between 1 and 180, got: $target_duration_minutes"
            }
            if (script_type.isBlank()) {
                return "script_type must not be blank"
            }
            if (tone.isBlank()) {
                return "tone must not be blank"
            }
            val validPacing = setOf("slow", "moderate", "fast", "dynamic")
            if (pacing.lowercase() !in validPacing) {
                return "pacing must be one of: ${validPacing.joinToString(", ")}, got: $pacing"
            }
            if (revision_passes < 0 || revision_passes > 5) {
                return "revision_passes must be between 0 and 5, got: $revision_passes"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class ScriptOutline(
        @Description("The script title")
        val title: String = "",
        @Description("Opening hook or attention grabber")
        val hook: String = "",
        @Description("Main sections of the script")
        val sections: List<ScriptSection> = emptyList(),
        @Description("Closing and call-to-action")
        val closing: String = "",
        @Description("Estimated total duration in seconds")
        val estimated_duration_seconds: Int = 0,
        @Description("Key messages to convey")
        val key_messages: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (sections.isEmpty()) return "sections must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ScriptSection(
        @Description("Section number")
        val section_number: Int = 1,
        @Description("Section title or purpose")
        val title: String = "",
        @Description("Key points to cover in this section")
        val key_points: List<String> = emptyList(),
        @Description("Visual elements or B-roll suggestions")
        val visual_suggestions: List<String> = emptyList(),
        @Description("Estimated duration in seconds")
        val estimated_duration_seconds: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (section_number < 1) return "section_number must be positive"
            if (title.isBlank()) return "title must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ScriptSegment(
        @Description("Segment type")
        val segment_type: String = "",
        @Description("The spoken dialogue or narration")
        val dialogue: String = "",
        @Description("Visual directions or scene description")
        val visual_direction: String = "",
        @Description("B-roll or supporting visual suggestions")
        val b_roll_suggestions: List<String> = emptyList(),
        @Description("Production notes or speaker notes")
        val notes: String = "",
        @Description("Timing marker in MM:SS format")
        val timing: String = "",
        @Description("Key points marked for emphasis or graphics")
        val key_points_marked: List<String> = emptyList(),
        @Description("Estimated duration in seconds")
        val duration_seconds: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (dialogue.isBlank()) return "dialogue must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
 Scriptwriting - Generate complete scripts for videos, podcasts, and presentations
  ** Optionally, list input files (supports glob patterns) to be examined when generating the script
  ** Available files:
  ${getAvailableFiles(root).joinToString("\n") { "  - $it" }}
  ** Specify the topic and script type (video, podcast, presentation, etc.)
  ** Set target duration and audience
  ** Configure tone and pacing
  ** Specify the topic and script type (video, podcast, presentation, etc.)
  ** Set target duration and audience
  ** Configure tone and pacing
  ** Include visual directions, timing markers, and B-roll suggestions
  ** Mark key points for emphasis or graphics
  ** Add speaker notes and production notes
  ** Performs outline creation, segment writing, and timing calculation
  ** Produces complete, production-ready script with all necessary elements
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
        log.info("Starting ScriptwritingTask for topic: '${executionConfig?.topic}'")
        val markdownTranscript = transcript(task)


        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            markdownTranscript?.close()
            resultFn("CONFIGURATION ERROR: $validationError")
            markdownTranscript?.close()
            return
        }

        val topic = executionConfig?.topic
        if (topic.isNullOrBlank()) {
            log.error("No topic specified for scriptwriting")
            task.safeComplete("CONFIGURATION ERROR: No topic specified", log)
            markdownTranscript?.close()
            resultFn("CONFIGURATION ERROR: No topic specified")
            markdownTranscript?.close()
            return
        }

        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Script Generation")
            appendLine()
            appendLine("**Topic:** $topic")
            appendLine()
        }
        markdownTranscript?.write(overviewContent.toByteArray())
        markdownTranscript?.write("\n".toByteArray())
        overviewTask.add(overviewContent.renderMarkdown)
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
        markdownTranscript?.write(overviewContent2.toByteArray())
        markdownTranscript?.write(overviewContent2.toByteArray())
        markdownTranscript?.write("\n".toByteArray())
        overviewTask.add(overviewContent2.renderMarkdown)
        task.update()

        val resultBuilder = StringBuilder()
        markdownTranscript?.write("# Research Context\n\n".toByteArray())
        markdownTranscript?.write("Context loaded from prior tasks and related files.\n\n".toByteArray())
        resultBuilder.append("# Script: $topic\n\n")

        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val contextFiles = getContextFiles()

            if (priorContext.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = tabs.newTask("Research Context")
                contextTask.add(
                    buildString {
                        appendLine("# Research Context")
                        appendLine()
                        if (priorContext.isNotBlank()) {
                            appendLine("## Prior Context")
                            appendLine(priorContext.truncateForDisplay(2000))
                            appendLine()
                        }
                        if (contextFiles.isNotBlank()) {
                            appendLine("## Related Files")
                            appendLine(contextFiles.truncateForDisplay(2000))
                        }
                    }.renderMarkdown
                )
                task.update()
            }
            markdownTranscript?.write("# Research Context\n\n".toByteArray())

            // Phase 1: Create outline
            log.info("Phase 1: Creating script outline")
            markdownTranscript?.write("# Script Outline\n\n".toByteArray())
            val outlineTask = tabs.newTask("Outline")

            outlineTask.add(
                buildString {
                    appendLine("# Script Outline")
                    appendLine()
                    appendLine("**Status:** Creating structured outline...")
                    appendLine()
                }.renderMarkdown
            )
            markdownTranscript?.write("# Script Outline\n\n".toByteArray())
            markdownTranscript?.write("Creating structured outline...\n\n".toByteArray())
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

            val outlineAgent = ParsedAgent(
                resultClass = ScriptOutline::class.java,
                prompt = """
You are an expert scriptwriter specializing in ${executionConfig.script_type} scripts. Create a detailed outline for this script.

Topic: $topic

Script Type: ${executionConfig.script_type}
Target Duration: ${executionConfig.target_duration_minutes} minutes (~$targetDurationSeconds seconds)
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}
Pacing: ${executionConfig.pacing} (~$wordsPerMinute words per minute)
Target Word Count: ~$targetWordCount words

${if (priorContext.isNotBlank()) "Research Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Additional Research:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

Create an outline with:
1. A compelling title
${if (executionConfig.include_hook) "2. An attention-grabbing opening hook (10-15 seconds)" else ""}
3. 3-5 main sections that logically progress through the topic
4. Key points to cover in each section
${if (executionConfig.suggest_b_roll) "5. Visual suggestions for each section" else ""}
6. Estimated duration for each section
${if (executionConfig.include_cta) "7. A strong closing with call-to-action" else "7. A memorable closing"}

Ensure the outline:
- Flows logically from introduction to conclusion
- Maintains the ${executionConfig.tone} tone throughout
- Fits within the ${executionConfig.target_duration_minutes}-minute timeframe
- Engages the ${executionConfig.target_audience}
- Balances information delivery with entertainment/engagement
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = defaultFast
            )

            var outline = outlineAgent.answer(listOf("Generate outline")).obj

            // Validate outline
            outline.validate()?.let { validationError ->
                log.error("Outline validation failed: $validationError")
                outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
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
            outlineTask.add(outlineContent.renderMarkdown)
            markdownTranscript?.write(outlineContent.toByteArray())
            markdownTranscript?.write(outlineContent.toByteArray())
            markdownTranscript?.write("\n".toByteArray())
            task.update()

            overviewTask.add("✅ Phase 1 Complete: Outline created (${outline.sections.size} sections)\n".renderMarkdown)
            overviewTask.add("\n### Phase 2: Script Writing\n*Writing detailed script segments...*\n".renderMarkdown)
            task.update()

            // Phase 2: Write script segments
            log.info("Phase 2: Writing script segments")
            val scriptSegments = mutableListOf<ScriptSegment>()
            var cumulativeDuration = 0
            var cumulativeWordCount = 0

            // Write opening if hook is included
            if (executionConfig.include_hook && outline.hook.isNotBlank()) {
                log.info("Writing opening hook")
                overviewTask.add("- Opening Hook ".renderMarkdown)
                task.update()

                val hookTask = tabs.newTask("Opening")

                hookTask.add(
                    buildString {
                        appendLine("# Opening Hook")
                        appendLine()
                        appendLine("**Status:** Writing opening...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val hookAgent = ParsedAgent(
                    resultClass = ScriptSegment::class.java,
                    prompt = """
You are an expert scriptwriter. Write the opening hook for this ${executionConfig.script_type} script.

Topic: $topic
Hook Concept: ${outline.hook}
Tone: ${executionConfig.tone}
Target Audience: ${executionConfig.target_audience}
Target Duration: 10-15 seconds

Write an opening that:
1. Immediately grabs attention
2. Sets the tone for the entire script
3. Hints at what's coming
4. Is conversational and natural for spoken delivery
${if (executionConfig.include_directions) "5. Includes visual direction for what the viewer sees" else ""}
${if (executionConfig.suggest_b_roll) "6. Suggests B-roll or supporting visuals" else ""}

Make it punchy, engaging, and memorable.
Ensure the dialogue sounds natural when spoken aloud.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var hookSegment = hookAgent.answer(listOf("Write opening")).obj
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
                    }.renderMarkdown
                )
                markdownTranscript?.write("# Opening Hook\n\n".toByteArray())
                markdownTranscript?.write("# Opening Hook\n\n".toByteArray())
                markdownTranscript?.write(hookSegment.dialogue.toByteArray())
                markdownTranscript?.write("\n\n".toByteArray())
                task.update()

                overviewTask.add("✅\n".renderMarkdown)
                task.update()
            }

            // Write each section
            outline.sections.forEachIndexed { index, sectionOutline ->
                log.info("Writing section ${index + 1}/${outline.sections.size}: ${sectionOutline.title}")

                overviewTask.add("- Section ${sectionOutline.section_number}: ${sectionOutline.title} ".renderMarkdown)
                task.update()

                val sectionTask = tabs.newTask("Section ${sectionOutline.section_number}")

                sectionTask.add(
                    buildString {
                        appendLine("# Section ${sectionOutline.section_number}: ${sectionOutline.title}")
                        appendLine()
                        appendLine("**Status:** Writing section...")
                        appendLine()
                    }.renderMarkdown
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

                val sectionAgent = ParsedAgent(
                    resultClass = ScriptSegment::class.java,
                    prompt = """
You are an expert scriptwriter. Write Section ${sectionOutline.section_number} of this ${executionConfig.script_type} script.

Overall Topic: $topic
Section Title: ${sectionOutline.title}
Section Purpose: Cover these key points: ${sectionOutline.key_points.joinToString("; ")}
Target Duration: ${sectionOutline.estimated_duration_seconds} seconds
Tone: ${executionConfig.tone}
Pacing: ${executionConfig.pacing}

$previousContext

${
                        if (sectionOutline.visual_suggestions.isNotEmpty()) "Visual Suggestions: ${
                            sectionOutline.visual_suggestions.joinToString(
                                "; "
                            )
                        }" else ""
                    }

Write this section with:
1. Natural, conversational dialogue that sounds good when spoken
2. Clear transitions from the previous section
3. Logical flow through the key points
4. Appropriate pacing for ${executionConfig.pacing} delivery
${if (executionConfig.include_directions) "5. Visual directions for what appears on screen" else ""}
${if (executionConfig.suggest_b_roll) "6. B-roll suggestions to support the narration" else ""}
${if (executionConfig.include_notes) "7. Production notes for the speaker/director" else ""}
${if (executionConfig.mark_key_points) "8. Mark key points that should be emphasized or shown as graphics" else ""}

Ensure the dialogue:
- Sounds natural when read aloud
- Uses contractions and conversational language
- Varies sentence length for rhythm
- Includes pauses where appropriate
- Maintains the ${executionConfig.tone} tone
- Engages the ${executionConfig.target_audience}

Aim for approximately ${sectionOutline.estimated_duration_seconds} seconds of content.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var sectionSegment = sectionAgent.answer(listOf("Write section")).obj
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
                    }.renderMarkdown
                )
                markdownTranscript?.write("## Section ${sectionOutline.section_number}: ${sectionOutline.title}\n\n".toByteArray())
                markdownTranscript?.write("## Section ${sectionOutline.section_number}: ${sectionOutline.title}\n\n".toByteArray())
                markdownTranscript?.write(sectionSegment.dialogue.toByteArray())
                markdownTranscript?.write("\n\n".toByteArray())
                task.update()

                overviewTask.add("✅ (${sectionSegment.duration_seconds}s)\n".renderMarkdown)
                task.update()
            }

            // Write closing
            log.info("Writing closing")
            overviewTask.add("- Closing ".renderMarkdown)
            task.update()

            val closingTask = tabs.newTask("Closing")

            closingTask.add(
                buildString {
                    appendLine("# Closing")
                    appendLine()
                    appendLine("**Status:** Writing closing...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val closingAgent = ParsedAgent(
                resultClass = ScriptSegment::class.java,
                prompt = """
You are an expert scriptwriter. Write the closing for this ${executionConfig.script_type} script.

Topic: $topic
Closing Concept: ${outline.closing}
Tone: ${executionConfig.tone}
Target Audience: ${executionConfig.target_audience}
${if (executionConfig.include_cta) "Include Call-to-Action: Yes" else ""}

Key Messages Covered:
${outline.key_messages.joinToString("\n") { "- $it" }}

Previous Script Context:
${scriptSegments.takeLast(1).firstOrNull()?.dialogue?.takeLast(200) ?: ""}

Write a closing that:
1. Summarizes the key takeaways
2. Reinforces the main message
3. Leaves a lasting impression
${if (executionConfig.include_cta) "4. Includes a clear, compelling call-to-action" else "4. Ends on a strong note"}
5. Sounds natural and conversational
${if (executionConfig.include_directions) "6. Includes visual direction for the final shot" else ""}

Make it memorable and motivating.
Target duration: 15-20 seconds.
          """.trimIndent(),
                model = api,
                temperature = 0.8,
                parsingChatter = defaultFast
            )

            var closingSegment = closingAgent.answer(listOf("Write closing")).obj
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
                }.renderMarkdown
            )
            markdownTranscript?.write("# Closing\n\n".toByteArray())
            markdownTranscript?.write("# Closing\n\n".toByteArray())
            markdownTranscript?.write(closingSegment.dialogue.toByteArray())
            markdownTranscript?.write("\n\n".toByteArray())
            task.update()

            overviewTask.add("✅\n".renderMarkdown)
            overviewTask.add("✅ Phase 2 Complete: All segments written\n".renderMarkdown)
            task.update()

            // Phase 3: Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                overviewTask.add("\n### Phase 3: Revision\n*Refining script for flow and timing...*\n".renderMarkdown)
                task.update()

                log.info("Phase 3: Performing ${executionConfig.revision_passes} revision pass(es)")
                val revisionTask = tabs.newTask("Revision")

                revisionTask.add(
                    buildString {
                        appendLine("# Revision Process")
                        appendLine()
                        appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val fullScript = buildFullScript(outline, scriptSegments)

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")

                    val revisionAgent = ChatAgent(
                        prompt = """
You are an expert script editor. Review and improve this ${executionConfig.script_type} script.

Current Script:
$fullScript

Focus on:
1. Natural dialogue flow and conversational tone
2. Pacing and rhythm (target: ${executionConfig.pacing})
3. Clarity and conciseness
4. Smooth transitions between sections
5. Timing accuracy (target: ${executionConfig.target_duration_minutes} minutes)
6. Engagement and audience connection
7. Consistency in tone (${executionConfig.tone})

Maintain:
- All key messages and content
- The overall structure
- Approximate duration ($cumulativeDuration seconds)
- The ${executionConfig.tone} tone

Provide the complete revised script with all formatting intact.
            """.trimIndent(),
                        model = api,
                        temperature = 0.6
                    )

                    revisionAgent.answer(listOf("Revise the script"))

                    revisionTask.add(
                        buildString {
                            appendLine("## Revision Pass ${passNum + 1}")
                            appendLine()
                            appendLine("✅ Complete")
                            appendLine()
                        }.renderMarkdown
                    )
                    task.update()
                }

                overviewTask.add("✅ Phase 3 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown)
            }

            // Phase 4: Final Assembly
            overviewTask.add("\n### Phase 4: Final Assembly\n*Compiling complete script...*\n".renderMarkdown)
            task.update()

            log.info("Phase 4: Assembling final script")
            val finalTask = tabs.newTask("Complete Script")

            val finalScript = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine("**Script Type:** ${executionConfig.script_type.capitalize()}")
                appendLine("**Duration:** ${formatTiming(cumulativeDuration)} (${cumulativeDuration}s)")
                appendLine("**Word Count:** $cumulativeWordCount")
                appendLine("**Tone:** ${executionConfig.tone.capitalize()}")
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

            finalTask.add(finalScript.renderMarkdown)
            markdownTranscript?.write("\n---\n\n# Complete Script\n\n".toByteArray())
            markdownTranscript?.write("\n---\n\n# Complete Script\n\n".toByteArray())
            markdownTranscript?.write(finalScript.toByteArray())
            markdownTranscript?.write("\n".toByteArray())
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

                productionNotesTask.add(productionNotes.renderMarkdown)
                markdownTranscript?.write("\n---\n\n".toByteArray())
                markdownTranscript?.write("\n---\n\n".toByteArray())
                markdownTranscript?.write(productionNotes.toByteArray())
                markdownTranscript?.write("\n".toByteArray())
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
                }.renderMarkdown
            )
            markdownTranscript?.write("\n---\n\n## Generation Complete\n\n".toByteArray())
            markdownTranscript?.write("\n---\n\n## Generation Complete\n\n".toByteArray())
            markdownTranscript?.write("Script generation completed successfully.\n".toByteArray())
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
            markdownTranscript?.close()
            markdownTranscript?.close()

            task.safeComplete(
                "Script generation complete: ${formatTiming(cumulativeDuration)} in ${totalTime / 1000}s",
                log
            )
            resultFn(finalResult)

        } catch (e: Exception) {
            log.error("Error during script generation", e)
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
            markdownTranscript?.close()

            val errorOutput = buildString {
                appendLine("# Error in Script Generation")
                appendLine()
                appendLine("**Topic:** $topic")
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
        markdownTranscript?.close()
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
                        appendLine(filePath.toFile().readText().truncateForDisplay(1500))
                        appendLine("```")
                        appendLine()
                    } else {
                        log.warn("Context file not found: $file")
                    }
                } catch (e: Exception) {
                    log.warn("Error reading file: $file", e)
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
        fun getAvailableFiles(
            path: Path,
            treatDocumentsAsText: Boolean = false,
        ): List<String> {
            return try {
                listOf(
                    FileSelectionUtils.filteredWalkAsciiTree(
                        path.toFile(),
                        20,
                        treatDocumentsAsText = treatDocumentsAsText
                    )
                )
            } catch (e: Exception) {
                log.error("Error listing available files", e)
                listOf("Error listing files: ${e.message}")
            }
        }

        fun extractDocumentContent(file: File) = try {
            file.readText()
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }

        val Scriptwriting = TaskType(
            "Scriptwriting",
            "Writing",
            ScriptwritingTask::class.java,
            ScriptwritingTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Generate complete scripts for videos, podcasts, and presentations",
            """
              Generates production-ready scripts with dialogue, timing, and production notes.
              <ul>
                <li>Creates detailed script outline with sections and timing</li>
                <li>Writes natural, conversational dialogue for spoken delivery</li>
                <li>Includes visual directions and scene descriptions</li>
                <li>Suggests B-roll and supporting visuals</li>
                <li>Marks key points for emphasis or graphics</li>
                <li>Provides timing markers and duration estimates</li>
                <li>Includes production notes and speaker guidance</li>
                <li>Supports multiple script types (video, podcast, presentation, commercial)</li>
                <li>Configurable tone, pacing, and audience targeting</li>
                <li>Optional revision passes for quality improvement</li>
                <li>Ideal for video production, podcasts, presentations, training videos</li>
              </ul>
            """,
        )
    }
}