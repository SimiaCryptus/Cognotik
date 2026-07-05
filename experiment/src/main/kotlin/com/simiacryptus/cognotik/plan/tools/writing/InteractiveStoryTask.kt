package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.OutputStream
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
    var premise: String? = null,

    @Description("The genre of the story. One of: 'fantasy', 'sci-fi', 'mystery', 'horror', 'romance'.")
    var genre: String = "fantasy",

    @Description("The target audience for the story. One of: 'children', 'young_adult', 'adult'.")
    var target_audience: String = "young_adult",

    @Description("The tone of the story. One of: 'lighthearted', 'serious', 'dark', 'humorous'.")
    var tone: String = "serious",

    @Description("Number of major decision points in the story")
    var num_decision_points: Int = 5,

    @Description("Number of choices at each decision point")
    var choices_per_decision: Int = 3,

    @Description("Whether to track state variables (inventory, relationships, stats)")
    var track_state_variables: Boolean = true,

    @Description("State variables to track. Examples: 'health', 'reputation', 'gold', 'ally_trust'. Null means use defaults.")
    var state_variables: List<String>? = null,

    @Description("Whether to ensure all paths lead to meaningful endings")
    var prevent_dead_ends: Boolean = true,

    @Description("Number of distinct endings to create")
    var num_endings: Int = 3,

    @Description("Whether to optimize for replay value with distinct experiences")
    var optimize_replay_value: Boolean = true,

    @Description("Average word count per story segment")
    var segment_word_count: Int = 300,

    @Description("Writing style for the narrative. One of: 'descriptive', 'action-packed', 'dialogue-heavy', 'introspective'.")
    var writing_style: String = "descriptive",

    @Description("Point of view for the narrative. One of: 'second_person', 'first_person', 'third_person'.")
    var point_of_view: String = "second_person",
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context for the story. Null means no file context.")
    var related_files: List<String>? = null,

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
      num_decision_points = num_decision_points.coerceIn(1, 20)
      choices_per_decision = choices_per_decision.coerceIn(2, 5)
      num_endings = num_endings.coerceIn(1, 10)
      segment_word_count = segment_word_count.coerceIn(100, 1000)
      if (genre.isBlank()) {
        return "genre must not be null or blank"
      }
      if (point_of_view.isBlank()) {
        return "point_of_view must not be blank"
      }
      if (!related_files.isNullOrEmpty()) {
        related_files?.forEach { pattern ->
          if (pattern.isBlank()) {
            return "related_files patterns must not be blank"
          }
        }
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class StoryStructure(
    @Description("The story title")
    var title: String = "",
    @Description("Opening segment that sets the scene")
    var opening: String = "",
    @Description("Decision points in the story")
    var decision_points: List<DecisionPoint> = emptyList(),
    @Description("Possible endings")
    var endings: List<Ending> = emptyList(),
    @Description("State variables being tracked")
    var tracked_variables: Map<String, String> = emptyMap()
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
    var id: String = "",
    @Description("The narrative segment leading to this decision")
    var narrative: String = "",
    @Description("The question or situation requiring a choice")
    var decision_prompt: String = "",
    @Description("Available choices")
    var choices: List<Choice> = emptyList(),
    @Description("Current state variable values at this point")
    var state_snapshot: Map<String, Int> = emptyMap()
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
    var text: String = "",
    @Description("ID of the next decision point or ending this leads to")
    var leads_to: String = "",
    @Description("State variable changes from this choice")
    var state_changes: Map<String, Int> = emptyMap(),
    @Description("Immediate consequence description")
    var immediate_consequence: String = "",
    @Description("Long-term impact on the story")
    var long_term_impact: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (text.isBlank()) return "text must not be blank"
      if (leads_to.isBlank()) return "leads_to must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class Ending(
    @Description("Unique identifier for this ending")
    var id: String = "",
    @Description("Type of ending (e.g., 'triumph', 'tragedy', 'bittersweet', 'twist')")
    var ending_type: String = "",
    @Description("The final narrative segment")
    var narrative: String = "",
    @Description("Required state conditions to reach this ending")
    var required_conditions: Map<String, String> = emptyMap(),
    @Description("Choices that led to this ending")
    var path_summary: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (id.isBlank()) return "id must not be blank"
      if (ending_type.isBlank()) return "ending_type must not be blank"
      if (narrative.isBlank()) return "narrative must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class StorySegment(
    @Description("The unique segment identifier")
    var id: String = "",
    @Description("The narrative content")
    var content: String = "",
    @Description("Word count")
    var word_count: Int = 0,
    @Description("State changes in this segment")
    var state_changes: Map<String, Int> = emptyMap()
  ) : ValidatedObject

  override fun promptSegment(): String = buildString {
    appendLine("InteractiveStory - Create choose-your-own-adventure narratives with branching paths")
    appendLine("  ** Optionally, list input files (supports glob patterns) to be examined for context")
    appendLine("  ** Specify the premise or starting scenario")
    appendLine("  ** Define genre, tone, and target audience")
    appendLine("  ** Set number of decision points and choices per decision")
    appendLine("  ** Enable state variable tracking (health, reputation, inventory, etc.)")
    appendLine("  ** Prevent dead ends to ensure all paths lead somewhere meaningful")
    appendLine("  ** Create multiple distinct endings based on player choices")
    appendLine("  ** Optimize for replay value with different experiences")
    appendLine("  ** Track consequences across choices for coherent storytelling")
    appendLine("  ** Produces complete interactive narrative with decision tree")
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val startTime = System.currentTimeMillis()
    val transcript: OutputStream? = task.newUserFileStream(transcriptFile())
    val smartApi = orchestrationConfig.defaultSmart.getChildClient(task)
    val fastApi = orchestrationConfig.defaultFast.getChildClient(task)
    val tabs = TabbedDisplay(task)
    val storyBuilder = StringBuilder()
    try {
      // Gather input context from files and messages
      val inputContext = getInputFileCode() +
          if (messages.isNotEmpty()) "\n\n## User Input\n\n${messages.joinToString("\n\n")}" else ""
      log.info("Starting InteractiveStoryTask for premise: '${executionConfig?.premise}'")
      // Validate configuration
      val executionConfig = executionConfig ?: throw IllegalStateException("Execution config is null")
      executionConfig?.validate()?.let { validationError ->
        log.error("Configuration validation failed: $validationError")
        task.safeComplete("CONFIGURATION ERROR: $validationError", log)
        task.error(ValidatedObject.ValidationError(validationError, executionConfig))
        transcript?.write("# CONFIGURATION ERROR\n\n$validationError\n".toByteArray())
        resultFn("CONFIGURATION ERROR: $validationError")
        return
      }
      val premise = executionConfig?.premise
      if (premise.isNullOrBlank()) {
        log.error("No premise specified for interactive story")
        task.safeComplete("CONFIGURATION ERROR: No premise specified", log)
        transcript?.write("# CONFIGURATION ERROR\n\nNo premise specified\n".toByteArray())
        resultFn("CONFIGURATION ERROR: No premise specified")
        return
      }
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
          appendLine("- State Variables: ${executionConfig.state_variables?.joinToString(", ")}")
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
      // Write transcript header with tabbed structure
      transcript?.write(buildString {
        appendLine("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">")
        appendLine()
        appendLine("# Interactive Story Generation Transcript")
        appendLine()
        appendLine("**Premise:** $premise")
        appendLine()
        appendLine("## Configuration")
        appendLine()
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
          appendLine("- State Variables: ${executionConfig.state_variables?.joinToString(", ")}")
        }
        appendLine()
        appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        appendLine()
        appendLine("---")
        appendLine()
      }.toByteArray())
      overviewTask.add(overviewContent.renderMarkdown())
      task.update()

      storyBuilder.append("# Interactive Story: $premise\n\n")

      // Gather context from input files and messages
      val priorContext = getPriorCode(agent.executionState)
      val combinedContext = (if (inputContext.isNotBlank()) inputContext else "") +
          (if (priorContext.isNotBlank()) "\n\n## Prior Context\n\n$priorContext" else "")

      // Gather context
      if (priorContext.isNotBlank()) {
        transcript?.write(buildString {
          appendLine("<details><summary>Context from Previous Tasks</summary>")
          appendLine()
          appendLine(priorContext.truncateForDisplay(2000))
          appendLine()
          appendLine("</details>")
          appendLine()
          appendLine("---")
          appendLine()
        }.toByteArray())
        log.debug("Found prior context: ${priorContext.length} chars")
        val contextTask = tabs.newTask("Context")
        contextTask.add(
          buildString {
            appendLine("# Context from Previous Tasks")
            appendLine()
            appendLine(priorContext.truncateForDisplay(2000))
          }.renderMarkdown()
        )
        task.update()
      }

      // Phase 1: Create story structure and decision tree
      transcript?.write("## Phase 1: Story Structure Planning\n\nCreating decision tree and story architecture...\n\n".toByteArray())
      log.info("Phase 1: Creating story structure")
      val structureTask = tabs.newTask("Story Structure")

      structureTask.add(
        buildString {
          appendLine("# Story Structure & Decision Tree")
          appendLine()
          appendLine("**Status:** Planning narrative branches and decision points...")
          appendLine()
        }.renderMarkdown()
      )
      task.update()

      val stateVars = if (executionConfig.track_state_variables) {
        executionConfig.state_variables ?: listOf("health", "reputation", "resources")
      } else {
        emptyList()
      }

      // First, create a high-level outline using wrapped API client
      val outlinePrompt = buildString {
        appendLine("You are an expert interactive fiction designer. Create a high-level outline for a branching story.")
        appendLine("Premise: $premise")
        appendLine("Story Parameters:")
        appendLine("- Genre: ${executionConfig.genre}")
        appendLine("- Target Audience: ${executionConfig.target_audience}")
        appendLine("- Tone: ${executionConfig.tone}")
        appendLine("- Decision Points: ${executionConfig.num_decision_points}")
        appendLine("- Choices per Decision: ${executionConfig.choices_per_decision}")
        appendLine("- Number of Endings: ${executionConfig.num_endings}")
        if (combinedContext.isNotBlank()) {
          appendLine("Additional Context:")
          appendLine(combinedContext.truncateForDisplay(1000))
        }
        appendLine("Create a brief outline with:")
        appendLine("1. A compelling title")
        appendLine("2. A one-paragraph opening concept")
        appendLine("3. List of ${executionConfig.num_decision_points} decision point IDs and brief descriptions (e.g., \"decision_1: Choose path in forest\")")
        appendLine("4. List of ${executionConfig.num_endings} ending IDs and types (e.g., \"ending_triumph: Hero succeeds\")")
        appendLine("5. A simple flow showing how decisions connect (decision_1 -> decision_2 or ending_1)")
        appendLine("Keep it concise - just the structure, not full narratives.")
      }
      val outlineAgent = ChatAgent(
        prompt = outlinePrompt,
        model = smartApi,
        temperature = 0.7
      )
      val outline = outlineAgent.answer(listOf("Create outline"))
      log.debug("Generated outline: ${outline.length} chars")
      transcript?.write(buildString {
        appendLine("### Story Outline")
        appendLine()
        appendLine("<details><summary>Generated Outline</summary>")
        appendLine()
        appendLine(outline)
        appendLine()
        appendLine("</details>")
        appendLine()
      }.toByteArray())
      structureTask.add(
        buildString {
          appendLine("## Story Outline")
          appendLine()
          appendLine(outline)
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("**Status:** Building detailed structure...")
          appendLine()
        }.renderMarkdown()
      )
      task.update()
      // Now create the detailed structure
      val structurePrompt = buildString {
        appendLine("You are an expert interactive fiction designer. Create a detailed story structure based on this outline.")
        appendLine()
        appendLine("OUTLINE:")
        appendLine(outline)
        appendLine()
        appendLine("Story Parameters:")
        appendLine("- Genre: ${executionConfig.genre}")
        appendLine("- Target Audience: ${executionConfig.target_audience}")
        appendLine("- Tone: ${executionConfig.tone}")
        if (executionConfig.track_state_variables) {
          appendLine("- State Variables to Track: ${stateVars.joinToString(", ")}")
        }
        appendLine()
        appendLine("Expand the outline into a complete structure with:")
        appendLine("1. The title from the outline")
        appendLine("2. A brief opening description (2-3 sentences, NOT the full narrative)")
        appendLine("3. Decision points with:")
        appendLine("   - A unique ID (e.g., \"decision_1\", \"decision_2\" - use snake_case for compatibility)")
        appendLine("   - A brief narrative description (1-2 sentences)")
        appendLine("   - A clear decision prompt")
        appendLine("   - ${executionConfig.choices_per_decision} meaningful choices")
        appendLine("   - Each choice should lead to another decision point or an ending")
        appendLine("4. Endings with unique IDs and types")
        if (executionConfig.track_state_variables) {
          appendLine("5. State variable definitions and how they're affected by choices")
        }
        appendLine()
        appendLine("IMPORTANT: Keep descriptions brief. Full narratives will be written later.")
        appendLine("Focus on structure and connections, not detailed prose.")
      }

      val structureAgent = ParsedAgent(
        resultClass = StoryStructure::class.java,
        prompt = structurePrompt,
        model = smartApi,
        temperature = 0.5,
        parsingModel = fastApi
      )

      val structure = structureAgent.answer(listOf("Create detailed structure from outline")).obj

      // Validate structure
      structure.validate()?.let { validationError ->
        log.error("Structure validation failed: $validationError")
        structureTask.error(ValidatedObject.ValidationError(validationError, structure))
        transcript?.write("**ERROR:** Structure validation failed: $validationError\n\n".toByteArray())
        task.safeComplete("Structure validation failed: $validationError", log)
        resultFn("ERROR: Structure validation failed: $validationError")
        return
      }

      log.info("Generated structure: ${structure.decision_points.size} decision points, ${structure.endings.size} endings")
      transcript?.write(buildString {
        appendLine("### Generated Story Structure")
        appendLine()
        appendLine("**Title:** ${structure.title}")
        appendLine()
        appendLine("**Opening:** ${structure.opening}")
        appendLine()
        appendLine("**Decision Points:** ${structure.decision_points.size}")
        appendLine()
        structure.decision_points.forEach { dp ->
          appendLine("- ${dp.id}: ${dp.decision_prompt}")
          dp.choices.forEach { choice ->
            appendLine("  - ${choice.text} → ${choice.leads_to}")
          }
        }
        appendLine()
        appendLine("**Endings:** ${structure.endings.size}")
        appendLine()
        structure.endings.forEach { ending ->
          appendLine("- ${ending.id}: ${ending.ending_type}")
        }
        if (structure.tracked_variables.isNotEmpty()) {
          appendLine()
          appendLine("**Tracked Variables:**")
          appendLine()
          structure.tracked_variables.forEach { (name, description) ->
            appendLine("- $name: $description")
          }
        }
        appendLine()
        appendLine("---")
        appendLine()
      }.toByteArray())

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
      structureTask.add(structureContent.renderMarkdown())
      task.update()

      overviewTask.add("✅ Phase 1 Complete: Story structure created\n".renderMarkdown())
      overviewTask.add("\n### Phase 2: Opening Segment\n*Writing the story opening...*\n".renderMarkdown())
      task.update()

      // Phase 2: Write opening segment
      transcript?.write("## Phase 2: Opening Segment\n\nWriting the story opening...\n\n".toByteArray())
      log.info("Phase 2: Writing opening segment")
      val openingTask = tabs.newTask("Opening")

      openingTask.add(
        buildString {
          appendLine("# Opening Segment")
          appendLine()
          appendLine("**Status:** Writing opening narrative...")
          appendLine()
        }.renderMarkdown()
      )
      task.update()
      val openingPrompt = buildString {
        appendLine("You are a skilled ${executionConfig.genre} writer. Write the opening segment of this interactive story.")
        appendLine()
        appendLine("Title: ${structure.title}")
        appendLine("Premise: $premise")
        appendLine()
        appendLine("Story Parameters:")
        appendLine("- Genre: ${executionConfig.genre}")
        appendLine("- Tone: ${executionConfig.tone}")
        appendLine("- Point of View: ${executionConfig.point_of_view}")
        appendLine("- Writing Style: ${executionConfig.writing_style}")
        appendLine("- Target Audience: ${executionConfig.target_audience}")
        appendLine()
        appendLine("Opening Outline: ${structure.opening}")
        appendLine()
        appendLine("Write an engaging opening segment (~${executionConfig.segment_word_count} words) that:")
        appendLine("1. Immediately hooks the reader")
        appendLine("2. Establishes the setting and atmosphere")
        appendLine("3. Introduces the protagonist (the reader in ${executionConfig.point_of_view} POV)")
        appendLine("4. Sets up the initial situation")
        appendLine("5. Creates anticipation for the first decision")
        appendLine("6. Matches the ${executionConfig.tone} tone and ${executionConfig.writing_style} style")
        if (executionConfig.track_state_variables) {
          appendLine()
          appendLine("Initialize state variables: ${stateVars.joinToString(", ")}")
        }
        appendLine()
        appendLine("Make it immersive and compelling. The reader should feel invested immediately.")
      }

      val openingAgent = ParsedAgent(
        resultClass = StorySegment::class.java,
        prompt = openingPrompt,
        model = smartApi,
        temperature = 0.8,
        parsingModel = fastApi
      )

      val openingSegment = openingAgent.answer(listOf("Write opening")).obj
      transcript?.write(buildString {
        appendLine("### Opening Segment")
        appendLine()
        appendLine(openingSegment.content)
        appendLine()
        appendLine("**Word Count:** ${openingSegment.word_count}")
        appendLine()
        appendLine("---")
        appendLine()
      }.toByteArray())

      openingTask.add(
        buildString {
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
        }.renderMarkdown()
      )
      task.update()

      storyBuilder.append("## ${structure.title}\n\n")
      storyBuilder.append(openingSegment.content)
      storyBuilder.append("\n\n---\n\n")

      overviewTask.add("✅ Phase 2 Complete: Opening written (${openingSegment.word_count} words)\n".renderMarkdown())
      overviewTask.add("\n### Phase 3: Decision Points\n*Writing branching narrative segments...*\n".renderMarkdown())

      // Phase 3: Write each decision point
      transcript?.write("## Phase 3: Decision Points\n\nWriting branching narrative segments...\n\n".toByteArray())
      log.info("Phase 3: Writing decision points")
      val decisionSegments = mutableMapOf<String, StorySegment>()
      var cumulativeWordCount = openingSegment.word_count

      structure.decision_points.forEachIndexed { index, decisionPoint ->
        log.info("Writing decision point ${index + 1}/${structure.decision_points.size}: ${decisionPoint.id}")

        overviewTask.add(
          "- ${decisionPoint.id}: ${
            decisionPoint.decision_prompt.truncateForDisplay(
              50
            )
          } ".renderMarkdown()
        )
        task.update()

        val dpTask = tabs.newTask("${decisionPoint.id}")

        dpTask.add(
          buildString {
            appendLine("# ${decisionPoint.id}")
            appendLine()
            appendLine("**Status:** Writing decision point narrative...")
            appendLine()
          }.renderMarkdown()
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
        val decisionPrompt = buildString {
          appendLine("You are a skilled ${executionConfig.genre} writer. Write the narrative segment leading to this decision point.")
          appendLine()
          appendLine("Title: ${structure.title}")
          appendLine("Decision Point: ${decisionPoint.id}")
          appendLine()
          appendLine("Decision Outline:")
          appendLine("- Narrative: ${decisionPoint.narrative}")
          appendLine("- Decision Prompt: ${decisionPoint.decision_prompt}")
          appendLine()
          appendLine("Available Choices:")
          decisionPoint.choices.forEach { appendLine("- ${it.text}") }
          appendLine()
          appendLine(previousContext)
          appendLine()
          appendLine("Story Parameters:")
          appendLine("- Genre: ${executionConfig.genre}")
          appendLine("- Tone: ${executionConfig.tone}")
          appendLine("- Point of View: ${executionConfig.point_of_view}")
          appendLine("- Writing Style: ${executionConfig.writing_style}")
          appendLine()
          appendLine("Write a narrative segment (~${executionConfig.segment_word_count} words) that:")
          appendLine("1. Flows naturally from the previous segment")
          appendLine("2. Develops the story and builds tension")
          appendLine("3. Presents the situation requiring a decision")
          appendLine("4. Makes all choices feel meaningful and distinct")
          appendLine("5. Maintains the ${executionConfig.tone} tone")
          appendLine("6. Ends with the decision prompt clearly presented")
          if (executionConfig.track_state_variables && decisionPoint.state_snapshot.isNotEmpty()) {
            appendLine()
            appendLine("Current State: ${decisionPoint.state_snapshot.entries.joinToString(", ") { "${it.key}: ${it.value}" }}")
          }
          appendLine()
          appendLine("Make the reader feel the weight of their choice. Each option should feel viable but lead to different outcomes.")
        }

        val decisionAgent = ParsedAgent(
          resultClass = StorySegment::class.java,
          prompt = decisionPrompt,
          model = smartApi,
          temperature = 0.8,
          parsingModel = fastApi
        )

        val segment = decisionAgent.answer(listOf("Write decision point")).obj.copy(id = decisionPoint.id)
        decisionSegments[decisionPoint.id] = segment
        cumulativeWordCount += segment.word_count
        transcript?.write(buildString {
          appendLine("### ${decisionPoint.id}")
          appendLine()
          appendLine(segment.content)
          appendLine()
          appendLine("**Decision:** ${decisionPoint.decision_prompt}")
          appendLine()
          decisionPoint.choices.forEach { choice ->
            appendLine("- ${choice.text} → ${choice.leads_to}")
          }
          appendLine()
          appendLine("**Word Count:** ${segment.word_count}")
          appendLine()
          appendLine("---")
          appendLine()
        }.toByteArray())

        dpTask.add(
          buildString {
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
          }.renderMarkdown()
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

        overviewTask.add("✅ (${segment.word_count} words)\n".renderMarkdown())
        task.update()
      }

      overviewTask.add("✅ Phase 3 Complete: All decision points written\n".renderMarkdown())
      overviewTask.add("\n### Phase 4: Endings\n*Writing story conclusions...*\n".renderMarkdown())
      task.update()

      // Phase 4: Write endings
      transcript?.write("## Phase 4: Endings\n\nWriting story conclusions...\n\n".toByteArray())
      log.info("Phase 4: Writing endings")
      val endingSegments = mutableMapOf<String, StorySegment>()

      structure.endings.forEachIndexed { index, ending ->
        log.info("Writing ending ${index + 1}/${structure.endings.size}: ${ending.id}")

        overviewTask.add("- ${ending.id}: ${ending.ending_type} ".renderMarkdown())
        task.update()

        val endingTask = tabs.newTask("${ending.id}")

        endingTask.add(
          buildString {
            appendLine("# ${ending.id}")
            appendLine()
            appendLine("**Status:** Writing ending narrative...")
            appendLine()
          }.renderMarkdown()
        )
        task.update()
        val endingPrompt = buildString {
          appendLine("You are a skilled ${executionConfig.genre} writer. Write a satisfying ending for this interactive story.")
          appendLine()
          appendLine("Title: ${structure.title}")
          appendLine("Ending: ${ending.id}")
          appendLine("Ending Type: ${ending.ending_type}")
          appendLine()
          appendLine("Ending Outline: ${ending.narrative}")
          appendLine()
          if (ending.required_conditions.isNotEmpty()) {
            appendLine("This ending is reached when: ${ending.required_conditions.entries.joinToString(", ") { "${it.key} ${it.value}" }}")
            appendLine()
          }
          if (ending.path_summary.isNotEmpty()) {
            appendLine("Key choices that led here:")
            ending.path_summary.forEach { appendLine("- $it") }
            appendLine()
          }
          appendLine("Story Parameters:")
          appendLine("- Genre: ${executionConfig.genre}")
          appendLine("- Tone: ${executionConfig.tone}")
          appendLine("- Point of View: ${executionConfig.point_of_view}")
          appendLine("- Writing Style: ${executionConfig.writing_style}")
          appendLine()
          appendLine("Write an ending segment (~${executionConfig.segment_word_count} words) that:")
          appendLine("1. Provides a satisfying conclusion to the story")
          appendLine("2. Reflects the consequences of the reader's choices")
          appendLine("3. Matches the ${ending.ending_type} ending type")
          appendLine("4. Maintains the ${executionConfig.tone} tone")
          appendLine("5. Gives a sense of closure while honoring the journey")
          appendLine("6. Makes the reader feel their choices mattered")
          appendLine()
          appendLine("Make this ending feel earned and meaningful. It should resonate with the path taken.")
        }

        val endingAgent = ParsedAgent(
          resultClass = StorySegment::class.java,
          prompt = endingPrompt,
          model = smartApi,
          temperature = 0.8,
          parsingModel = fastApi
        )

        val endingSegment = endingAgent.answer(listOf("Write ending")).obj.copy(id = ending.id)
        endingSegments[ending.id] = endingSegment
        cumulativeWordCount += endingSegment.word_count
        transcript?.write(buildString {
          appendLine("### ${ending.id}: ${ending.ending_type}")
          appendLine()
          appendLine(endingSegment.content)
          appendLine()
          appendLine("**Word Count:** ${endingSegment.word_count}")
          appendLine()
          appendLine("---")
          appendLine()
        }.toByteArray())

        endingTask.add(
          buildString {
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
          }.renderMarkdown()
        )
        task.update()

        storyBuilder.append("## ${ending.id}: ${ending.ending_type}\n\n")
        storyBuilder.append(endingSegment.content)
        storyBuilder.append("\n\n**THE END**\n\n---\n\n")

        overviewTask.add("✅ (${endingSegment.word_count} words)\n".renderMarkdown())
        task.update()
      }

      overviewTask.add("✅ Phase 4 Complete: All endings written\n".renderMarkdown())
      overviewTask.add("\n### Phase 5: Interactive Map\n*Generating playable story map...*\n".renderMarkdown())
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

      mapTask.add(storyMap.renderMarkdown())
      task.update()

      // Close the final-output div and open work-details div in transcript
      val totalTime = System.currentTimeMillis() - startTime
      transcript?.write(buildString {
        appendLine("## Generation Complete")
        appendLine()
        appendLine("**Statistics:**")
        appendLine()
        appendLine("- Total Word Count: $cumulativeWordCount")
        appendLine("- Decision Points: ${structure.decision_points.size}")
        appendLine("- Total Choices: ${structure.decision_points.sumOf { it.choices.size }}")
        appendLine("- Endings: ${structure.endings.size}")
        appendLine("- Estimated Unique Paths: ~${calculateUniquePaths(structure)}")
        appendLine("- Total Time: ${totalTime / 1000.0}s")
        appendLine()
        appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        appendLine()
        appendLine("</div>")
        appendLine()
      }.toByteArray())

      overviewTask.add(
        buildString {
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
        }.renderMarkdown()
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
      resultFn(
        buildFinalResultWithLinks(
          task,
          finalResult,
          storyBuilder.toString(),
          cumulativeWordCount,
          structure,
          totalTime
        )
      )

    } catch (e: Exception) {
      // Triple Log Rule: UI, SLF4J, Transcript
      log.error("Error during interactive story generation", e)
      task.error(e)
      transcript?.write(buildString {
        appendLine()
        appendLine("## ❌ Error Occurred")
        appendLine()
        appendLine("<details><summary>Stack Trace</summary>")
        appendLine()
        appendLine("```")
        appendLine(e.stackTraceToString())
        appendLine("```")
        appendLine()
        appendLine("</details>")
        appendLine()
        appendLine("</div>")
        appendLine()
      }.toByteArray())

      val overviewTask = try {
        tabs.newTask("Error")
      } catch (_: Exception) {
        null
      }
      overviewTask?.add(
        buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ❌ Error Occurred")
          appendLine()
          appendLine("**Error:** ${e.message}")
          appendLine()
          appendLine("**Type:** ${e.javaClass.simpleName}")
        }.renderMarkdown()
      )
      task.update()

      val errorOutput = buildString {
        appendLine("# Error in Interactive Story Generation")
        appendLine()
        appendLine("**Premise:** ${executionConfig?.premise}")
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
    } finally {
      transcript?.close()
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
      // Theme auxiliary output around the primary filename
      val baseDir = getOutputFile(".md")?.let {
        if (it.endsWith(".md")) it.removeSuffix(".md") else null
      } ?: "interactive_story"

      // Ensure the themed directory exists
      val dir = task.resolveUserFile(baseDir)
      if (dir != null && !dir.exists()) dir.mkdirs()

      val mapLink = task.saveFile("$baseDir/story_map.md", storyMap.toByteArray())
      val summaryLink = task.saveFile("$baseDir/story_summary.md", summary.toByteArray())
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

  private fun getInputFileCode() = (executionConfig?.related_files ?: listOf())
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
      private val log: Logger = getLogger(InteractiveStoryTask::class.java)

    @JvmStatic
    val InteractiveStory = TaskType(
      name = "InteractiveStory",
      category = "Writing",
      taskClass = InteractiveStoryTask::class.java,
      executionConfigClass = InteractiveStoryTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Create choose-your-own-adventure narratives with branching paths",
      tooltipHtml = "<p>Generates complete interactive stories with meaningful choices and multiple endings.</p>" +
          "<ul>" +
          "<li>Creates detailed story structure with decision tree</li>" +
          "<li>Writes opening segment that hooks the reader</li>" +
          "<li>Develops branching narrative segments for each decision point</li>" +
          "<li>Generates multiple distinct endings based on player choices</li>" +
          "<li>Tracks state variables (health, reputation, inventory, etc.)</li>" +
          "<li>Ensures all paths lead to meaningful endings (no dead ends)</li>" +
          "<li>Optimizes for replay value with significantly different experiences</li>" +
          "<li>Tracks consequences across choices for coherent storytelling</li>" +
          "<li>Produces complete playable interactive story map</li>" +
          "<li>Ideal for interactive fiction, training scenarios, educational content, and games</li>" +
          "</ul>",
    )

    private val textExtensions = setOf(
      "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
      "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
    )

    fun isTextFile(file: File): Boolean {
      return textExtensions.contains(file.extension.lowercase())
    }
  }
}