package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class InteractiveStoryTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: InteractiveStoryTaskExecutionConfigData?
) : AbstractTask<InteractiveStoryTask.InteractiveStoryTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    protected val codeFiles = mutableMapOf<Path, String>()

    class InteractiveStoryTaskExecutionConfigData(
        @Description("The premise or starting scenario for the interactive story")
        val premise: String? = null,

        @Description("The genre of the story (e.g., 'fantasy', 'sci-fi', 'mystery', 'horror', 'romance')")
        val genre: String = "fantasy",

        @Description("The target audience (e.g., 'children', 'young_adult', 'adult')")
        val target_audience: String = "young_adult",

        @Description("The tone of the story (e.g., 'lighthearted', 'serious', 'dark', 'humorous')")
        val tone: String = "serious",

        @Description("Number of major decision points in the story")
        val num_decision_points: Int = 5,

        @Description("Number of choices at each decision point")
        val choices_per_decision: Int = 3,

        @Description("Whether to track state variables (inventory, relationships, stats)")
        val track_state_variables: Boolean = true,

        @Description("State variables to track (e.g., 'health', 'reputation', 'gold', 'ally_trust')")
        val state_variables: List<String>? = null,

        @Description("Whether to ensure all paths lead to meaningful endings")
        val prevent_dead_ends: Boolean = true,

        @Description("Number of distinct endings to create")
        val num_endings: Int = 3,

        @Description("Whether to optimize for replay value with distinct experiences")
        val optimize_replay_value: Boolean = true,

        @Description("Average word count per story segment")
        val segment_word_count: Int = 300,
        @Description("Writing style (e.g., 'descriptive', 'action-packed', 'dialogue-heavy', 'introspective')")
        val writing_style: String = "descriptive",

        @Description("Point of view (e.g., 'second_person', 'first_person', 'third_person')")
        val point_of_view: String = "second_person",
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context for the story")
        val input_files: List<String>? = null,


        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = InteractiveStory.name,
        task_description = task_description ?: "Generate interactive story: '$premise'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (premise.isNullOrBlank()) {
                return "premise must not be null or blank"
            }
            if (num_decision_points < 1 || num_decision_points > 20) {
                return "num_decision_points must be between 1 and 20, got: $num_decision_points"
            }
            if (choices_per_decision < 2 || choices_per_decision > 5) {
                return "choices_per_decision must be between 2 and 5, got: $choices_per_decision"
            }
            if (num_endings < 1 || num_endings > 10) {
                return "num_endings must be between 1 and 10, got: $num_endings"
            }
            if (segment_word_count < 100 || segment_word_count > 1000) {
                return "segment_word_count must be between 100 and 1000, got: $segment_word_count"
            }
            if (genre.isNullOrBlank()) {
                return "genre must not be null or blank"
            }
            if (point_of_view.isBlank()) {
                return "point_of_view must not be blank"
            }
            if (!input_files.isNullOrEmpty()) {
                input_files.forEach { pattern ->
                    if (pattern.isBlank()) {
                        return "input_files patterns must not be blank"
                    }
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class StoryStructure(
        @Description("The story title")
        val title: String = "",
        @Description("Opening segment that sets the scene")
        val opening: String = "",
        @Description("Decision points in the story")
        val decision_points: List<DecisionPoint> = emptyList(),
        @Description("Possible endings")
        val endings: List<Ending> = emptyList(),
        @Description("State variables being tracked")
        val tracked_variables: Map<String, String> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (opening.isBlank()) return "opening must not be blank"
            if (decision_points.isEmpty()) return "decision_points must not be empty"
            if (endings.isEmpty()) return "endings must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class DecisionPoint(
        @Description("Unique identifier for this decision point")
        val id: String = "",
        @Description("The narrative segment leading to this decision")
        val narrative: String = "",
        @Description("The question or situation requiring a choice")
        val decision_prompt: String = "",
        @Description("Available choices")
        val choices: List<Choice> = emptyList(),
        @Description("Current state variable values at this point")
        val state_snapshot: Map<String, Int> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (id.isBlank()) return "id must not be blank"
            if (narrative.isBlank()) return "narrative must not be blank"
            if (decision_prompt.isBlank()) return "decision_prompt must not be blank"
            if (choices.isEmpty()) return "choices must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Choice(
        @Description("The choice text presented to the reader")
        val text: String = "",
        @Description("ID of the next decision point or ending this leads to")
        val leads_to: String = "",
        @Description("State variable changes from this choice")
        val state_changes: Map<String, Int> = emptyMap(),
        @Description("Immediate consequence description")
        val immediate_consequence: String = "",
        @Description("Long-term impact on the story")
        val long_term_impact: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (text.isBlank()) return "text must not be blank"
            if (leads_to.isBlank()) return "leads_to must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Ending(
        @Description("Unique identifier for this ending")
        val id: String = "",
        @Description("Type of ending (e.g., 'triumph', 'tragedy', 'bittersweet', 'twist')")
        val ending_type: String = "",
        @Description("The final narrative segment")
        val narrative: String = "",
        @Description("Required state conditions to reach this ending")
        val required_conditions: Map<String, String> = emptyMap(),
        @Description("Choices that led to this ending")
        val path_summary: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (id.isBlank()) return "id must not be blank"
            if (ending_type.isBlank()) return "ending_type must not be blank"
            if (narrative.isBlank()) return "narrative must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class StorySegment(
        @Description("The segment ID")
        val id: String = "",
        @Description("The narrative content")
        val content: String = "",
        @Description("Word count")
        val word_count: Int = 0,
        @Description("State changes in this segment")
        val state_changes: Map<String, Int> = emptyMap()
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
 InteractiveStory - Create choose-your-own-adventure narratives with branching paths
  ** Optionally, list input files (supports glob patterns) to be examined for context
  ** Specify the premise or starting scenario
  ** Define genre, tone, and target audience
  ** Set number of decision points and choices per decision
  ** Enable state variable tracking (health, reputation, inventory, etc.)
  ** Prevent dead ends to ensure all paths lead somewhere meaningful
  ** Create multiple distinct endings based on player choices
  ** Optimize for replay value with different experiences
  ** Track consequences across choices for coherent storytelling
  ** Produces complete interactive narrative with decision tree
  Available files:
  ${getAvailableFiles(root).joinToString("\n") { "  - $it" }}
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
        // Initialize transcript
        val transcriptStream = task.newLogStream("Transcript")
        val transcriptWriter = transcriptStream.bufferedWriter()
        // Gather input context from files and messages
        val inputContext = getInputFileCode() +
                if (messages.isNotEmpty()) "\n\n## User Input\n\n${messages.joinToString("\n\n")}" else ""


        log.info("Starting InteractiveStoryTask for premise: '${executionConfig?.premise}'")

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            transcriptWriter.close()
            resultFn("CONFIGURATION ERROR: $validationError")
            return
        }

        val premise = executionConfig?.premise
        if (premise.isNullOrBlank()) {
            log.error("No premise specified for interactive story")
            task.safeComplete("CONFIGURATION ERROR: No premise specified", log)
            transcriptWriter.close()
            resultFn("CONFIGURATION ERROR: No premise specified")
            return
        }

        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Interactive Story Generation")
            appendLine()
            appendLine("**Premise:** $premise")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Genre: ${executionConfig.genre}")
            appendLine("- Target Audience: ${executionConfig.target_audience}")
            appendLine("- Tone: ${executionConfig.tone}")
            appendLine("- Point of View: ${executionConfig.point_of_view}")
            appendLine("- Writing Style: ${executionConfig.writing_style}")
            appendLine("- Decision Points: ${executionConfig.num_decision_points}")
            appendLine("- Choices per Decision: ${executionConfig.choices_per_decision}")
            appendLine("- Number of Endings: ${executionConfig.num_endings}")
            appendLine("- Track State Variables: ${if (executionConfig.track_state_variables) "✓" else "✗"}")
            if (executionConfig.track_state_variables && !executionConfig.state_variables.isNullOrEmpty()) {
                appendLine("- State Variables: ${executionConfig.state_variables.joinToString(", ")}")
            }
            appendLine("- Prevent Dead Ends: ${if (executionConfig.prevent_dead_ends) "✓" else "✗"}")
            appendLine("- Optimize Replay Value: ${if (executionConfig.optimize_replay_value) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Story Structure Planning")
            appendLine("*Creating decision tree and story architecture...*")
        }
        // Write to transcript
        transcriptWriter.apply {
            write("# Interactive Story Generation Transcript\n\n")
            write("**Premise:** $premise\n\n")
            write("## Configuration\n\n")
            write("- Genre: ${executionConfig.genre}\n")
            write("- Target Audience: ${executionConfig.target_audience}\n")
            write("- Tone: ${executionConfig.tone}\n")
            write("- Point of View: ${executionConfig.point_of_view}\n")
            write("- Writing Style: ${executionConfig.writing_style}\n")
            write("- Decision Points: ${executionConfig.num_decision_points}\n")
            write("- Choices per Decision: ${executionConfig.choices_per_decision}\n")
            write("- Number of Endings: ${executionConfig.num_endings}\n")
            write("- Track State Variables: ${if (executionConfig.track_state_variables) "✓" else "✗"}\n")
            if (executionConfig.track_state_variables && !executionConfig.state_variables.isNullOrEmpty()) {
                write("- State Variables: ${executionConfig.state_variables.joinToString(", ")}\n")
            }
            write(
                "\n**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n"
            )
            write("---\n\n")
            flush()
        }
        overviewTask.add(MarkdownUtil.renderMarkdown(overviewContent))
        task.update()

            val storyBuilder = StringBuilder()
            storyBuilder.append("# Interactive Story: $premise\n\n")

        try {
            // Gather context from input files and messages
            val priorContext = getPriorCode(agent.executionState)
            val combinedContext = (if (inputContext.isNotBlank()) inputContext else "") +
                    (if (priorContext.isNotBlank()) "\n\n## Prior Context\n\n$priorContext" else "")

            // Gather context
            if (priorContext.isNotBlank()) {
                transcriptWriter.apply {
                    write("## Context from Previous Tasks\n\n")
                    write(priorContext.truncateForDisplay(2000))
                    write("\n\n---\n\n")
                    flush()
                }
                log.debug("Found prior context: ${priorContext.length} chars")
                val contextTask = tabs.newTask("Context")
                contextTask.add(
                    MarkdownUtil.renderMarkdown(buildString {
                        appendLine("# Context from Previous Tasks")
                        appendLine()
                        appendLine(priorContext.truncateForDisplay(2000))
                    })
                )
                task.update()
            }

            // Phase 1: Create story structure and decision tree
            transcriptWriter.apply {
                write("## Phase 1: Story Structure Planning\n\n")
                write("Creating decision tree and story architecture...\n\n")
                flush()
            }
            log.info("Phase 1: Creating story structure")
            val structureTask = tabs.newTask("Story Structure")

            structureTask.add(
                MarkdownUtil.renderMarkdown(buildString {
                    appendLine("# Story Structure & Decision Tree")
                    appendLine()
                    appendLine("**Status:** Planning narrative branches and decision points...")
                    appendLine()
                })
            )
            task.update()

            val stateVars = if (executionConfig.track_state_variables) {
                executionConfig.state_variables ?: listOf("health", "reputation", "resources")
            } else {
                emptyList()
            }
            // First, create a high-level outline
            val outlineAgent = ChatAgent(
                prompt = """
 You are an expert interactive fiction designer. Create a high-level outline for a branching story.
 Premise: $premise
 Story Parameters:
- Genre: ${executionConfig.genre}
- Target Audience: ${executionConfig.target_audience}
- Tone: ${executionConfig.tone}
- Decision Points: ${executionConfig.num_decision_points}
- Choices per Decision: ${executionConfig.choices_per_decision}
- Number of Endings: ${executionConfig.num_endings}
${if (combinedContext.isNotBlank()) "Additional Context:\n${combinedContext.truncateForDisplay(1000)}\n" else ""}
Create a brief outline with:
1. A compelling title
2. A one-paragraph opening concept
3. List of ${executionConfig.num_decision_points} decision point IDs and brief descriptions (e.g., "decision_1: Choose path in forest")
4. List of ${executionConfig.num_endings} ending IDs and types (e.g., "ending_triumph: Hero succeeds")
5. A simple flow showing how decisions connect (decision_1 -> decision_2 or ending_1)
Keep it concise - just the structure, not full narratives.
          """.trimIndent(),
                model = api,
                temperature = 0.7
            )
            val outline = outlineAgent.answer(listOf("Create outline"))
            log.debug("Generated outline: ${outline.length} chars")
            transcriptWriter.apply {
                write("### Story Outline\n\n")
                write(outline)
                write("\n\n")
                flush()
            }
            structureTask.add(
                MarkdownUtil.renderMarkdown(buildString {
                    appendLine("## Story Outline")
                    appendLine()
                    appendLine(outline)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** Building detailed structure...")
                    appendLine()
                })
            )
            task.update()
            // Now create the detailed structure in smaller pieces

            val structureAgent = ParsedAgent(
                resultClass = StoryStructure::class.java,
                prompt = """
You are an expert interactive fiction designer. Create a detailed story structure based on this outline.

OUTLINE:
$outline

Story Parameters:
- Genre: ${executionConfig.genre}
- Target Audience: ${executionConfig.target_audience}
- Tone: ${executionConfig.tone}
${if (executionConfig.track_state_variables) "- State Variables to Track: ${stateVars.joinToString(", ")}" else ""}


Expand the outline into a complete structure with:
1. The title from the outline
2. A brief opening description (2-3 sentences, NOT the full narrative)
3. Decision points with:
   - A unique ID (e.g., "decision_1", "decision_2" - use snake_case for compatibility)
   - A brief narrative description (1-2 sentences)
   - A clear decision prompt
   - ${executionConfig.choices_per_decision} meaningful choices
   - Each choice should lead to another decision point or an ending
4. Endings with unique IDs and types
${if (executionConfig.track_state_variables) "5. State variable definitions and how they're affected by choices" else ""}


IMPORTANT: Keep descriptions brief. Full narratives will be written later.
Focus on structure and connections, not detailed prose.
          """.trimIndent(),
                model = api,
                temperature = 0.5,
                parsingChatter = defaultFast
            )

            val structure = structureAgent.answer(listOf("Create detailed structure from outline")).obj

            // Validate structure
            structure.validate()?.let { validationError ->
                log.error("Structure validation failed: $validationError")
                structureTask.error(ValidatedObject.ValidationError(validationError, structure))
                transcriptWriter.apply {
                    write("**ERROR:** Structure validation failed: $validationError\n\n")
                    flush()
                    close()
                }
                task.safeComplete("Structure validation failed: $validationError", log)
                resultFn("ERROR: Structure validation failed: $validationError")
                return
            }

            log.info("Generated structure: ${structure.decision_points.size} decision points, ${structure.endings.size} endings")
            transcriptWriter.apply {
                write("### Generated Story Structure\n\n")
                write("**Title:** ${structure.title}\n\n")
                write("**Opening:** ${structure.opening}\n\n")
                write("**Decision Points:** ${structure.decision_points.size}\n\n")
                structure.decision_points.forEach { dp ->
                    write("- ${dp.id}: ${dp.decision_prompt}\n")
                    dp.choices.forEach { choice ->
                        write("  - ${choice.text} → ${choice.leads_to}\n")
                    }
                }
                write("\n**Endings:** ${structure.endings.size}\n\n")
                structure.endings.forEach { ending ->
                    write("- ${ending.id}: ${ending.ending_type}\n")
                }
                if (structure.tracked_variables.isNotEmpty()) {
                    write("\n**Tracked Variables:**\n\n")
                    structure.tracked_variables.forEach { (name, description) ->
                        write("- $name: $description\n")
                    }
                }
                write("\n---\n\n")
                flush()
            }


            val structureContent = buildString {
                appendLine("## ${structure.title}")
                appendLine()
                appendLine("### Opening")
                appendLine(structure.opening.truncateForDisplay(500))
                appendLine()
                if (structure.tracked_variables.isNotEmpty()) {
                    appendLine("### Tracked Variables")
                    structure.tracked_variables.forEach { (name, description) ->
                        appendLine("- **$name:** $description")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Decision Tree")
                appendLine()
                appendLine("```")
                appendLine("START")
                appendLine("  ↓")
                structure.decision_points.forEachIndexed { index, dp ->
                    appendLine("${dp.id}: ${dp.decision_prompt.truncateForDisplay(60)}")
                    dp.choices.forEach { choice ->
                        appendLine("  → ${choice.text.truncateForDisplay(50)} → ${choice.leads_to}")
                    }
                    if (index < structure.decision_points.size - 1) {
                        appendLine("  ↓")
                    }
                }
                appendLine()
                appendLine("ENDINGS:")
                structure.endings.forEach { ending ->
                    appendLine("  • ${ending.id}: ${ending.ending_type}")
                }
                appendLine("```")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Decision Points Summary")
                structure.decision_points.forEach { dp ->
                    appendLine("#### ${dp.id}")
                    appendLine("**Prompt:** ${dp.decision_prompt}")
                    appendLine()
                    appendLine("**Choices:**")
                    dp.choices.forEach { choice ->
                        appendLine("- ${choice.text}")
                        if (choice.state_changes.isNotEmpty()) {
                            appendLine("  - State changes: ${choice.state_changes.entries.joinToString(", ") { "${it.key} ${if (it.value >= 0) "+" else ""}${it.value}" }}")
                        }
                        appendLine("  - Leads to: ${choice.leads_to}")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("### Endings Summary")
                structure.endings.forEach { ending ->
                    appendLine("#### ${ending.id}: ${ending.ending_type}")
                    if (ending.required_conditions.isNotEmpty()) {
                        appendLine("**Conditions:** ${ending.required_conditions.entries.joinToString(", ") { "${it.key} ${it.value}" }}")
                    }
                    appendLine()
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            structureTask.add(MarkdownUtil.renderMarkdown(structureContent))
            task.update()

            overviewTask.add(MarkdownUtil.renderMarkdown("✅ Phase 1 Complete: Story structure created\n"))
            overviewTask.add(MarkdownUtil.renderMarkdown("\n### Phase 2: Opening Segment\n*Writing the story opening...*\n"))
            task.update()

            // Phase 2: Write opening segment
            transcriptWriter.apply {
                write("## Phase 2: Opening Segment\n\n")
                write("Writing the story opening...\n\n")
                flush()
            }
            log.info("Phase 2: Writing opening segment")
            val openingTask = tabs.newTask("Opening")

            openingTask.add(
                MarkdownUtil.renderMarkdown(buildString {
                    appendLine("# Opening Segment")
                    appendLine()
                    appendLine("**Status:** Writing opening narrative...")
                    appendLine()
                })
            )
            task.update()

            val openingAgent = ParsedAgent(
                resultClass = StorySegment::class.java,
                prompt = """
You are a skilled ${executionConfig.genre} writer. Write the opening segment of this interactive story.

Title: ${structure.title}
Premise: $premise

Story Parameters:
- Genre: ${executionConfig.genre}
- Tone: ${executionConfig.tone}
- Point of View: ${executionConfig.point_of_view}
- Writing Style: ${executionConfig.writing_style}
- Target Audience: ${executionConfig.target_audience}

Opening Outline: ${structure.opening}

Write an engaging opening segment (~${executionConfig.segment_word_count} words) that:
1. Immediately hooks the reader
2. Establishes the setting and atmosphere
3. Introduces the protagonist (the reader in ${executionConfig.point_of_view} POV)
4. Sets up the initial situation
5. Creates anticipation for the first decision
6. Matches the ${executionConfig.tone} tone and ${executionConfig.writing_style} style

${if (executionConfig.track_state_variables) "Initialize state variables: ${stateVars.joinToString(", ")}" else ""}

Make it immersive and compelling. The reader should feel invested immediately.
          """.trimIndent(),
                model = api,
                temperature = 0.8,
                parsingChatter = defaultFast
            )

            var openingSegment = openingAgent.answer(listOf("Write opening")).obj
            transcriptWriter.apply {
                write("### Opening Segment\n\n")
                write(openingSegment.content)
                write("\n\n**Word Count:** ${openingSegment.word_count}\n\n")
                write("---\n\n")
                flush()
            }

            openingTask.add(
                MarkdownUtil.renderMarkdown(buildString {
                    appendLine("## ${structure.title}")
                    appendLine()
                    appendLine(openingSegment.content)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${openingSegment.word_count}")
                    if (openingSegment.state_changes.isNotEmpty()) {
                        appendLine()
                        appendLine("**Initial State:**")
                        openingSegment.state_changes.forEach { (variable, value) ->
                            appendLine("- $variable: $value")
                        }
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                })
            )
            task.update()

            storyBuilder.append("## ${structure.title}\n\n")
            storyBuilder.append(openingSegment.content)
            storyBuilder.append("\n\n---\n\n")

            overviewTask.add(MarkdownUtil.renderMarkdown("✅ Phase 2 Complete: Opening written (${openingSegment.word_count} words)\n"))
            overviewTask.add(MarkdownUtil.renderMarkdown("\n### Phase 3: Decision Points\n*Writing branching narrative segments...*\n"))

            // Phase 3: Write each decision point
            transcriptWriter.apply {
                write("## Phase 3: Decision Points\n\n")
                write("Writing branching narrative segments...\n\n")
                flush()
            }
            log.info("Phase 3: Writing decision points")
            val decisionSegments = mutableMapOf<String, StorySegment>()
            var cumulativeWordCount = openingSegment.word_count

            structure.decision_points.forEachIndexed { index, decisionPoint ->
                log.info("Writing decision point ${index + 1}/${structure.decision_points.size}: ${decisionPoint.id}")

                overviewTask.add(
                    MarkdownUtil.renderMarkdown(
                        "- ${decisionPoint.id}: ${
                            decisionPoint.decision_prompt.truncateForDisplay(
                                50
                            )
                        } "
                    )
                )
                task.update()

                val dpTask = tabs.newTask("${decisionPoint.id}")

                dpTask.add(
                    MarkdownUtil.renderMarkdown(buildString {
                        appendLine("# ${decisionPoint.id}")
                        appendLine()
                        appendLine("**Status:** Writing decision point narrative...")
                        appendLine()
                    })
                )
                task.update()

                // Build context from previous segments
                val previousContext = if (decisionSegments.isNotEmpty()) {
                    buildString {
                        appendLine("## Previous Story Context")
                        val recentSegments = decisionSegments.values.toList().takeLast(2)
                        recentSegments.forEach { seg ->
                            appendLine("### ${seg.id}")
                            appendLine(seg.content.takeLast(300))
                            appendLine()
                        }
                    }
                } else {
                    buildString {
                        appendLine("## Opening Context")
                        appendLine(openingSegment.content.takeLast(300))
                    }
                }

                val decisionAgent = ParsedAgent(
                    resultClass = StorySegment::class.java,
                    prompt = """
You are a skilled ${executionConfig.genre} writer. Write the narrative segment leading to this decision point.

Title: ${structure.title}
Decision Point: ${decisionPoint.id}

Decision Outline:
- Narrative: ${decisionPoint.narrative}
- Decision Prompt: ${decisionPoint.decision_prompt}

Available Choices:
${decisionPoint.choices.joinToString("\n") { "- ${it.text}" }}

$previousContext

Story Parameters:
- Genre: ${executionConfig.genre}
- Tone: ${executionConfig.tone}
- Point of View: ${executionConfig.point_of_view}
- Writing Style: ${executionConfig.writing_style}

Write a narrative segment (~${executionConfig.segment_word_count} words) that:
1. Flows naturally from the previous segment
2. Develops the story and builds tension
3. Presents the situation requiring a decision
4. Makes all choices feel meaningful and distinct
5. Maintains the ${executionConfig.tone} tone
6. Ends with the decision prompt clearly presented

${
                        if (executionConfig.track_state_variables && decisionPoint.state_snapshot.isNotEmpty()) {
                            "Current State: ${decisionPoint.state_snapshot.entries.joinToString(", ") { "${it.key}: ${it.value}" }}"
                        } else ""
                    }

Make the reader feel the weight of their choice. Each option should feel viable but lead to different outcomes.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var segment = decisionAgent.answer(listOf("Write decision point")).obj.copy(id = decisionPoint.id)
                decisionSegments[decisionPoint.id] = segment
                cumulativeWordCount += segment.word_count
                transcriptWriter.apply {
                    write("### ${decisionPoint.id}\n\n")
                    write(segment.content)
                    write("\n\n**Decision:** ${decisionPoint.decision_prompt}\n\n")
                    decisionPoint.choices.forEach { choice ->
                        write("- ${choice.text} → ${choice.leads_to}\n")
                    }
                    write("\n**Word Count:** ${segment.word_count}\n\n---\n\n")
                    flush()
                }

                dpTask.add(
                    MarkdownUtil.renderMarkdown(buildString {
                        appendLine("## ${decisionPoint.id}")
                        appendLine()
                        appendLine(segment.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("### ${decisionPoint.decision_prompt}")
                        appendLine()
                        decisionPoint.choices.forEachIndexed { choiceIndex, choice ->
                            appendLine("**${choiceIndex + 1}. ${choice.text}**")
                            if (choice.immediate_consequence.isNotBlank()) {
                                appendLine("   - *${choice.immediate_consequence}*")
                            }
                            if (choice.state_changes.isNotEmpty()) {
                                appendLine("   - State changes: ${choice.state_changes.entries.joinToString(", ") { "${it.key} ${if (it.value >= 0) "+" else ""}${it.value}" }}")
                            }
                            appendLine()
                        }
                        appendLine("---")
                        appendLine()
                        appendLine("**Word Count:** ${segment.word_count}")
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    })
                )
                task.update()

                storyBuilder.append("## ${decisionPoint.id}\n\n")
                storyBuilder.append(segment.content)
                storyBuilder.append("\n\n")
                storyBuilder.append("### ${decisionPoint.decision_prompt}\n\n")
                decisionPoint.choices.forEachIndexed { choiceIndex, choice ->
                    storyBuilder.append("${choiceIndex + 1}. ${choice.text}\n")
                }
                storyBuilder.append("\n---\n\n")

                overviewTask.add(MarkdownUtil.renderMarkdown("✅ (${segment.word_count} words)\n"))
                task.update()
            }

            overviewTask.add(MarkdownUtil.renderMarkdown("✅ Phase 3 Complete: All decision points written\n"))
            overviewTask.add(MarkdownUtil.renderMarkdown("\n### Phase 4: Endings\n*Writing story conclusions...*\n"))
            task.update()

            // Phase 4: Write endings
            transcriptWriter.apply {
                write("## Phase 4: Endings\n\n")
                write("Writing story conclusions...\n\n")
                flush()
            }
            log.info("Phase 4: Writing endings")
            val endingSegments = mutableMapOf<String, StorySegment>()

            structure.endings.forEachIndexed { index, ending ->
                log.info("Writing ending ${index + 1}/${structure.endings.size}: ${ending.id}")

                overviewTask.add(MarkdownUtil.renderMarkdown("- ${ending.id}: ${ending.ending_type} "))
                task.update()

                val endingTask = tabs.newTask("${ending.id}")

                endingTask.add(
                    MarkdownUtil.renderMarkdown(buildString {
                        appendLine("# ${ending.id}")
                        appendLine()
                        appendLine("**Status:** Writing ending narrative...")
                        appendLine()
                    })
                )
                task.update()

                val endingAgent = ParsedAgent(
                    resultClass = StorySegment::class.java,
                    prompt = """
You are a skilled ${executionConfig.genre} writer. Write a satisfying ending for this interactive story.

Title: ${structure.title}
Ending: ${ending.id}
Ending Type: ${ending.ending_type}

Ending Outline: ${ending.narrative}

${
                        if (ending.required_conditions.isNotEmpty()) {
                            "This ending is reached when: ${ending.required_conditions.entries.joinToString(", ") { "${it.key} ${it.value}" }}"
                        } else ""
                    }

${
                        if (ending.path_summary.isNotEmpty()) {
                            "Key choices that led here:\n${ending.path_summary.joinToString("\n") { "- $it" }}"
                        } else ""
                    }

Story Parameters:
- Genre: ${executionConfig.genre}
- Tone: ${executionConfig.tone}
- Point of View: ${executionConfig.point_of_view}
- Writing Style: ${executionConfig.writing_style}

Write an ending segment (~${executionConfig.segment_word_count} words) that:
1. Provides a satisfying conclusion to the story
2. Reflects the consequences of the reader's choices
3. Matches the ${ending.ending_type} ending type
4. Maintains the ${executionConfig.tone} tone
5. Gives a sense of closure while honoring the journey
6. Makes the reader feel their choices mattered

Make this ending feel earned and meaningful. It should resonate with the path taken.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var endingSegment = endingAgent.answer(listOf("Write ending")).obj.copy(id = ending.id)
                endingSegments[ending.id] = endingSegment
                cumulativeWordCount += endingSegment.word_count
                transcriptWriter.apply {
                    write("### ${ending.id}: ${ending.ending_type}\n\n")
                    write(endingSegment.content)
                    write("\n\n**Word Count:** ${endingSegment.word_count}\n\n---\n\n")
                    flush()
                }

                endingTask.add(
                    MarkdownUtil.renderMarkdown(buildString {
                        appendLine("## ${ending.id}: ${ending.ending_type}")
                        appendLine()
                        appendLine(endingSegment.content)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**THE END**")
                        appendLine()
                        appendLine("**Word Count:** ${endingSegment.word_count}")
                        if (ending.required_conditions.isNotEmpty()) {
                            appendLine()
                            appendLine("**Conditions to Reach:**")
                            ending.required_conditions.forEach { (condition, value) ->
                                appendLine("- $condition: $value")
                            }
                        }
                        appendLine()
                        appendLine("**Status:** ✅ Complete")
                    })
                )
                task.update()

                storyBuilder.append("## ${ending.id}: ${ending.ending_type}\n\n")
                storyBuilder.append(endingSegment.content)
                storyBuilder.append("\n\n**THE END**\n\n---\n\n")

                overviewTask.add(MarkdownUtil.renderMarkdown("✅ (${endingSegment.word_count} words)\n"))
                task.update()
            }

            overviewTask.add(MarkdownUtil.renderMarkdown("✅ Phase 4 Complete: All endings written\n"))
            overviewTask.add(MarkdownUtil.renderMarkdown("\n### Phase 5: Interactive Map\n*Generating playable story map...*\n"))
            task.update()

            // Phase 5: Create interactive story map
            log.info("Phase 5: Creating interactive story map")
            val mapTask = tabs.newTask("Story Map")

            val storyMap = buildString {
                appendLine("# ${structure.title} - Interactive Story Map")
                appendLine()
                appendLine("## How to Play")
                appendLine("1. Start with the Opening segment")
                appendLine("2. At each decision point, choose one of the available options")
                appendLine("3. Follow the path indicated by your choice")
                appendLine("4. Continue until you reach an ending")
                appendLine("5. Try different choices to discover all ${structure.endings.size} endings!")
                appendLine()
                if (structure.tracked_variables.isNotEmpty()) {
                    appendLine("## Tracked Variables")
                    structure.tracked_variables.forEach { (name, description) ->
                        appendLine("- **$name:** $description")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()
                appendLine("## START: Opening")
                appendLine()
                appendLine(openingSegment.content)
                appendLine()
                val firstNodeId = structure.decision_points.firstOrNull()?.id ?: "ending"
                appendLine("**→ [Continue to: $firstNodeId](#$firstNodeId)**")
                appendLine()
                appendLine("---")
                appendLine()

                structure.decision_points.forEach { dp ->
                    val segment = decisionSegments[dp.id]
                    appendLine("## ${dp.id}")
                    appendLine()
                    if (segment != null) {
                        appendLine(segment.content)
                        appendLine()
                    }
                    appendLine("### ${dp.decision_prompt}")
                    appendLine()
                    dp.choices.forEachIndexed { index, choice ->
                        appendLine("**Choice ${index + 1}: ${choice.text}**")
                        if (choice.immediate_consequence.isNotBlank()) {
                            appendLine()
                            appendLine("*${choice.immediate_consequence}*")
                        }
                        if (choice.state_changes.isNotEmpty()) {
                            appendLine()
                            appendLine("State changes: ${choice.state_changes.entries.joinToString(", ") { "${it.key} ${if (it.value >= 0) "+" else ""}${it.value}" }}")
                        }
                        appendLine()
                        appendLine("**→ [Continue to: ${choice.leads_to}](#${choice.leads_to})**")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }

                structure.endings.forEach { ending ->
                    val segment = endingSegments[ending.id]
                    appendLine("## ${ending.id}: ${ending.ending_type}")
                    appendLine()
                    if (segment != null) {
                        appendLine(segment.content)
                        appendLine()
                    }
                    appendLine("**THE END**")
                    appendLine()
                    if (ending.required_conditions.isNotEmpty()) {
                        appendLine("*This ending is reached when: ${ending.required_conditions.entries.joinToString(", ") { "${it.key} ${it.value}" }}*")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }

                appendLine("## Story Statistics")
                appendLine()
                appendLine("- Total Word Count: $cumulativeWordCount")
                appendLine("- Decision Points: ${structure.decision_points.size}")
                appendLine("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}")
                appendLine("- Possible Endings: ${structure.endings.size}")
                appendLine("- Unique Paths: ~${calculateUniquePaths(structure)}")
            }

            mapTask.add(MarkdownUtil.renderMarkdown(storyMap))
            task.update()

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime
            transcriptWriter.apply {
                write("## Generation Complete\n\n")
                write("**Statistics:**\n\n")
                write("- Total Word Count: $cumulativeWordCount\n")
                write("- Decision Points: ${structure.decision_points.size}\n")
                write("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}\n")
                write("- Endings: ${structure.endings.size}\n")
                write("- Estimated Unique Paths: ~${calculateUniquePaths(structure)}\n")
                write("- Total Time: ${totalTime / 1000.0}s\n\n")
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n"
                )
                flush()
                close()
            }


            overviewTask.add(
                MarkdownUtil.renderMarkdown(buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Generation Complete")
                    appendLine()
                    appendLine("**Statistics:**")
                    appendLine("- Total Word Count: $cumulativeWordCount")
                    appendLine("- Decision Points: ${structure.decision_points.size}")
                    appendLine("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}")
                    appendLine("- Endings: ${structure.endings.size}")
                    appendLine("- Estimated Unique Paths: ~${calculateUniquePaths(structure)}")
                    appendLine("- Total Time: ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                })
            )
            task.update()

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Interactive Story Summary: ${structure.title}")
                appendLine()
                appendLine("A complete interactive story of **$cumulativeWordCount words** with **${structure.decision_points.size} decision points** and **${structure.endings.size} endings** was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Structure:**")
                appendLine("- Opening segment")
                appendLine("- ${structure.decision_points.size} branching decision points")
                appendLine("- ${structure.decision_points.sumOf { it.choices.size }} total choices")
                appendLine("- ${structure.endings.size} distinct endings")
                appendLine("- Estimated ${calculateUniquePaths(structure)} unique story paths")
                appendLine()
                appendLine("> The complete interactive story map is available in the Story Map tab for play-through.")
            }

            log.info("InteractiveStoryTask completed: words=$cumulativeWordCount, decisions=${structure.decision_points.size}, endings=${structure.endings.size}, time=${totalTime}ms")

            task.safeComplete(
                "Interactive story generation complete: $cumulativeWordCount words, ${structure.decision_points.size} decisions, ${structure.endings.size} endings in ${totalTime / 1000}s",
                log
            )
            resultFn(buildFinalResultWithLinks(task, finalResult, storyBuilder.toString(), cumulativeWordCount, structure, totalTime))

        } catch (e: Exception) {
            log.error("Error during interactive story generation", e)
            transcriptWriter.close()
            task.error(e)

            overviewTask.add(
                MarkdownUtil.renderMarkdown(buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    appendLine("**Type:** ${e.javaClass.simpleName}")
                })
            )
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Interactive Story Generation")
                appendLine()
                appendLine("**Premise:** $premise")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                if (storyBuilder.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(storyBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
    }

    private fun buildFinalResultWithLinks(
        task: SessionTask,
        summary: String,
        storyMap: String,
        wordCount: Int,
        structure: StoryStructure,
        totalTime: Long
    ): String {
        return try {
            // Save story map to file
            val mapLink = task.saveFile("story_map.md", storyMap.toByteArray())
            // Save summary to file
            val summaryLink = task.saveFile("story_summary.md", summary.toByteArray())
            buildString {
                appendLine("# Interactive Story Generation Complete")
                appendLine()
                appendLine("**Story:** ${structure.title}")
                appendLine("**Word Count:** $wordCount")
                appendLine("**Decision Points:** ${structure.decision_points.size}")
                appendLine("**Endings:** ${structure.endings.size}")
                appendLine("**Generation Time:** ${totalTime / 1000.0}s")
                appendLine()
                appendLine("## Output Files")
                appendLine()
                appendLine("- [Story Map (Interactive)]($mapLink) - Complete playable story with all paths")
                appendLine()
                appendLine("- [Story Summary]($summaryLink) - Generation summary and statistics")
                appendLine()
                appendLine("## Quick Stats")
                appendLine()
                appendLine("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}")
                appendLine("- Unique Paths: ~${calculateUniquePaths(structure)}")
                appendLine("- Tracked Variables: ${structure.tracked_variables.size}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(summary)
            }
        } catch (e: Exception) {
            log.error("Failed to create output files", e)
            buildString {
                appendLine("# Interactive Story Generation Complete")
                appendLine()
                appendLine("**Note:** Could not save detailed output files, but story was generated successfully.")
                appendLine()
                appendLine(summary)
            }
        }
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
                    matcher.matches(root.relativize(it.toPath())) -> true
                    it.isDirectory -> true
                    else -> false
                }
            }
        }.filter { file ->
            file.isFile && file.exists()
        }
        .distinct()
        .sortedBy { it }
        .joinToString("\n\n") { relativePath ->
            val file = root.toFile().resolve(relativePath)
            try {
                val content = codeFiles[file.toPath()] ?: file.readText()
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }


    private fun calculateUniquePaths(structure: StoryStructure): Int {
        // Simple estimation: multiply choices at each decision point
        // This is an upper bound; actual paths may converge
        val choicesPerDecision = structure.decision_points.map { it.choices.size }
        return if (choicesPerDecision.isEmpty()) {
            1
        } else {
            choicesPerDecision.fold(1) { acc, choices ->
                (acc * choices).coerceAtMost(1000) // Cap at 1000 to avoid overflow
            }
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(InteractiveStoryTask::class.java)
        val InteractiveStory = TaskType(
            "InteractiveStory",
            "Writing",
            InteractiveStoryTask::class.java,
            InteractiveStoryTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Create choose-your-own-adventure narratives with branching paths",
            """
              Generates complete interactive stories with meaningful choices and multiple endings.
              <ul>
                <li>Creates detailed story structure with decision tree</li>
                <li>Writes opening segment that hooks the reader</li>
                <li>Develops branching narrative segments for each decision point</li>
                <li>Generates multiple distinct endings based on player choices</li>
                <li>Tracks state variables (health, reputation, inventory, etc.)</li>
                <li>Ensures all paths lead to meaningful endings (no dead ends)</li>
                <li>Optimizes for replay value with significantly different experiences</li>
                <li>Tracks consequences across choices for coherent storytelling</li>
                <li>Produces complete playable interactive story map</li>
                <li>Ideal for interactive fiction, training scenarios, educational content, and games</li>
              </ul>
            """,
        )

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

        private val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
        )

        fun isTextFile(file: File): Boolean {
            return textExtensions.contains(file.extension.lowercase())
        }
    }
}