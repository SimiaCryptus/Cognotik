package com.simiacryptus.cognotik.plan.tools.writing


import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.reasoning.safeComplete
import com.simiacryptus.cognotik.plan.tools.reasoning.truncateForDisplay
import com.simiacryptus.cognotik.plan.tools.reasoning.validateAndGetApi
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val i = 100

open class NarrativeReasoningTask<T : NarrativeReasoningTask.NarrativeReasoningTaskExecutionConfigData, U : TaskTypeConfig>(
  orchestrationConfig: OrchestrationConfig,
  planTask: T?
) : AbstractTask<T, U>(
  orchestrationConfig,
  planTask
) {

  val maxDescriptionLength = 100

  open class NarrativeReasoningTaskExecutionConfigData(
    @Description("The subject or scenario to analyze through narrative reasoning")
    val subject: String? = null,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    val input_files: List<String>? = null,
    @Description("Additional context or questions to guide the narrative analysis")
    val additional_context: String? = null,

    @Description("Narrative elements to consider (characters, setting, conflict, timeline, etc.)")
    val narrative_elements: Map<String, Any>? = null,

    @Description("Whether to construct a coherent narrative from the elements")
    val construct_narrative: Boolean = true,

    @Description("Whether to identify key plot points and story arcs")
    val identify_plot_points: Boolean = true,

    @Description("Whether to predict narrative outcomes and resolutions")
    val predict_outcomes: Boolean = true,

    @Description("Number of alternative narrative paths to explore")
    val alternative_narratives: Int = 3,

    @Description("Whether to analyze character motivations and stakeholder perspectives")
    val analyze_motivations: Boolean = true,

    @Description("Whether to identify narrative inconsistencies or gaps")
    val find_inconsistencies: Boolean = true,

    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = NarrativeReasoning.name,
    task_description = "Analyze '$subject' through narrative reasoning",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (subject.isNullOrBlank()) {
        return "Subject must not be null or blank"
      }
      if (alternative_narratives < 1 || alternative_narratives > 10) {
        return "Alternative narratives must be between 1 and 10, got: $alternative_narratives"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class ParsedNarrative(
    val title: String = "",
    val summary: String = "",
    val acts: List<NarrativeAct> = emptyList(),
    val themes: List<String> = emptyList(),
    val tone: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) {
        return "Narrative title must not be blank"
      }
      if (summary.isBlank()) {
        return "Narrative summary must not be blank"
      }
      if (acts.isEmpty()) {
        return "Narrative must have at least one act"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class NarrativeAct(
    val act_number: Int = 1,
    val title: String = "",
    val description: String = "",
    val key_events: List<String> = emptyList(),
    val character_developments: Map<String, String> = emptyMap()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (act_number < 1) {
        return "Act number must be positive, got: $act_number"
      }
      if (title.isBlank()) {
        return "Act title must not be blank"
      }
      if (description.isBlank()) {
        return "Act description must not be blank"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class PlotPoint(
    val type: String = "",
    val description: String = "",
    val significance: String = "",
    val timing: String = "",
    val affected_characters: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (type.isBlank()) {
        return "Plot point type must not be blank"
      }
      if (description.isBlank()) {
        return "Plot point description must not be blank"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class PlotPoints(
    val points: List<PlotPoint> = emptyList()
  ) : ValidatedObject

  data class CharacterAnalysis(
    val name: String = "",
    val role: String = "",
    val motivations: List<String> = emptyList(),
    val goals: List<String> = emptyList(),
    val conflicts: List<String> = emptyList(),
    val arc: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (name.isBlank()) {
        return "Character name must not be blank"
      }
      if (role.isBlank()) {
        return "Character role must not be blank"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class CharacterAnalyses(
    val characters: List<CharacterAnalysis> = emptyList()
  ) : ValidatedObject


  data class NarrativeOutcome(
    val scenario: String = "",
    val probability: String = "",
    val key_factors: List<String> = emptyList(),
    val consequences: List<String> = emptyList(),
    val resolution_path: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (scenario.isBlank()) {
        return "Outcome scenario must not be blank"
      }
      if (probability.isBlank()) {
        return "Outcome probability must not be blank"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class NarrativeOutcomes(
    val outcomes: List<NarrativeOutcome> = emptyList()
  ) : ValidatedObject


  data class NarrativeInconsistency(
    val type: String = "",
    val description: String = "",
    val location: String = "",
    val severity: String = "",
    val suggested_resolution: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (type.isBlank()) {
        return "Inconsistency type must not be blank"
      }
      if (description.isBlank()) {
        return "Inconsistency description must not be blank"
      }
      val validSeverities = setOf("critical", "major", "minor")
      if (severity.isNotBlank() && severity.lowercase() !in validSeverities) {
        return "Severity must be one of: ${validSeverities.joinToString(", ")}, got: $severity"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class NarrativeInconsistencies(
    val inconsistencies: List<NarrativeInconsistency> = emptyList()
  ) : ValidatedObject


  override fun promptSegment(): String {
    return """
NarrativeReasoning - Understand scenarios through storytelling and narrative structures
  ** Specify the subject or scenario to analyze
  ** Define narrative elements: characters, setting, conflict, timeline
  ** Enable narrative construction to create coherent stories
  ** Identify plot points and story arcs
  ** Predict narrative outcomes and alternative paths
  ** Analyze character motivations and stakeholder perspectives
  ** Find narrative inconsistencies or gaps
  ** Produces structured narrative analysis with insights
        """.trimIndent()
  }

  private fun transcript(task: SessionTask): FileOutputStream? {
    val (link, file) = task.createFile("narrative_transcript.md")
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


  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val startTime = System.currentTimeMillis()
    log.info("Starting NarrativeReasoningTask for subject: '${executionConfig?.subject}'")
    // Create output directory for detailed results
    val narrativeDir = File(agent.root.toFile(), ".narrative_analysis")
    if (!narrativeDir.exists()) {
      if (!narrativeDir.mkdirs()) {
        log.error("Failed to create narrative analysis directory: ${narrativeDir.absolutePath}")
        resultFn("Error: Failed to create output directory")
        return
      }
      log.debug("Created narrative analysis directory: ${narrativeDir.absolutePath}")
    }


    val subject = executionConfig?.subject
    if (subject.isNullOrBlank()) {
      log.error("No subject specified for narrative reasoning")
      task.safeComplete("CONFIGURATION ERROR: No subject specified", log)
      resultFn("CONFIGURATION ERROR: No subject specified")
      return
    }
    // Read input files if specified
    val inputFileContent = getInputFileContent()
    val messageContent = messages.joinToString("\n\n")
    val additionalContext = buildString {
      if (messageContent.isNotBlank()) {
        appendLine("## User Input")
        appendLine(messageContent)
      }
      if (inputFileContent.isNotBlank()) {
        appendLine("\n## Input Files")
        appendLine(inputFileContent)
      }
      if (executionConfig?.additional_context?.isNotBlank() == true) {
        appendLine("\n## Additional Context")
        appendLine(executionConfig.additional_context)
      }
    }


    val narrativeElements = executionConfig.narrative_elements ?: emptyMap()
    val constructNarrative = executionConfig.construct_narrative
    val identifyPlotPoints = executionConfig.identify_plot_points
    val predictOutcomes = executionConfig.predict_outcomes
    val alternativeNarratives = executionConfig.alternative_narratives.coerceIn(1, 10)
    val analyzeMotivations = executionConfig.analyze_motivations
    val findInconsistencies = executionConfig.find_inconsistencies

    log.info(
      "Configuration: constructNarrative=$constructNarrative, identifyPlotPoints=$identifyPlotPoints, " +
          "predictOutcomes=$predictOutcomes, alternativeNarratives=$alternativeNarratives, " +
          "analyzeMotivations=$analyzeMotivations, findInconsistencies=$findInconsistencies"
    )

    val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return

    task.ui
    val tabs = TabbedDisplay(task)
    // Initialize transcript
    val transcriptStream = transcript(task)
    val transcriptWriter = transcriptStream?.bufferedWriter()
    transcriptWriter?.appendLine("# Narrative Reasoning Analysis Transcript")
    transcriptWriter?.appendLine("**Subject:** $subject")
    transcriptWriter?.appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
    transcriptWriter?.appendLine()


    // Overview tab
    val overviewTask = task.ui.newTask(false)
    tabs["Overview"] = overviewTask.placeholder

    val overviewContent = buildString {
      appendLine("# Narrative Reasoning Analysis")
      appendLine()
      appendLine("**Subject:** $subject")
      appendLine()
      appendLine("## Narrative Elements")
      narrativeElements.forEach { (key, value) ->
        appendLine("- **${key.capitalize()}:** $value")
      }
      appendLine()
      appendLine("## Analysis Configuration")
      appendLine("- Construct Narrative: ${if (constructNarrative) "✓" else "✗"}")
      appendLine("- Identify Plot Points: ${if (identifyPlotPoints) "✓" else "✗"}")
      appendLine("- Predict Outcomes: ${if (predictOutcomes) "✓" else "✗"}")
      appendLine("- Alternative Narratives: $alternativeNarratives")
      appendLine("- Analyze Motivations: ${if (analyzeMotivations) "✓" else "✗"}")
      appendLine("- Find Inconsistencies: ${if (findInconsistencies) "✓" else "✗"}")
      appendLine()
      appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
      if (additionalContext.isNotBlank()) {
        appendLine()
        appendLine("## Input Context")
        appendLine(additionalContext.take(500) + if (additionalContext.length > 500) "..." else "")
      }
      appendLine()
      appendLine("---")
      appendLine()
      appendLine("## Progress")
      appendLine()
      appendLine("*Initializing narrative analysis...*")
    }
    overviewTask.add(overviewContent.renderMarkdown)
    task.update()

    val priorContext = getPriorCode(agent.executionState)
    if (priorContext.isNotBlank()) {
      log.debug("Found prior context: ${priorContext.length} characters")
      val contextTask = task.ui.newTask(false)
      tabs["Context"] = contextTask.placeholder
      contextTask.add(
        buildString {
          appendLine("# Context from Previous Tasks")
          appendLine()
          appendLine(priorContext.truncateForDisplay())
        }.renderMarkdown
      )
      task.update()
    }

    val resultBuilder = StringBuilder()
    resultBuilder.append("# Narrative Reasoning Analysis: $subject\n\n")
    // Write configuration to transcript
    transcriptWriter?.appendLine("## Configuration")
    transcriptWriter?.appendLine("- Construct Narrative: $constructNarrative")
    transcriptWriter?.appendLine("- Identify Plot Points: $identifyPlotPoints")
    transcriptWriter?.appendLine("- Predict Outcomes: $predictOutcomes")
    transcriptWriter?.appendLine("- Alternative Narratives: $alternativeNarratives")
    transcriptWriter?.appendLine("- Analyze Motivations: $analyzeMotivations")
    transcriptWriter?.appendLine("- Find Inconsistencies: $findInconsistencies")
    transcriptWriter?.appendLine()


    try {
      // Step 1: Construct the main narrative
      if (constructNarrative) {
        log.info("Step 1: Constructing main narrative")
        overviewTask.add("\n✅ Initializing narrative construction...\n".renderMarkdown)
        task.update()

        val narrativeTask = task.ui.newTask(false)
        tabs["Main Narrative"] = narrativeTask.placeholder
        transcriptWriter?.appendLine("## Step 1: Main Narrative Construction")
        transcriptWriter?.appendLine()


        narrativeTask.add(
          buildString {
            appendLine("# Main Narrative Construction")
            appendLine()
            appendLine("**Status:** Analyzing narrative elements and constructing story...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        val narrativeAgent = ParsedAgent(
          resultClass = ParsedNarrative::class.java,
          prompt = """
 You are an expert narrative analyst and storyteller. Construct a coherent narrative from the given elements.

 Subject: $subject

 Narrative Elements:
 ${narrativeElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}
${if (additionalContext.isNotBlank()) "Additional Context:\n$additionalContext\n" else ""}


 ${if (priorContext.isNotBlank()) "Additional Context:\n$priorContext\n" else ""}

 Create a structured narrative with:
1. A compelling title
2. A concise summary (2-3 sentences)
3. Three acts with key events and character developments
4. Underlying themes
5. Overall tone

Structure the narrative using classic storytelling principles (setup, confrontation, resolution).
Focus on clarity, coherence, and emotional resonance.
          """.trimIndent(),
          model = api,
          temperature = 0.7,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        val narrative = narrativeAgent.answer(listOf("Construct the narrative")).obj
        log.debug("Main narrative constructed: ${narrative.title}")

        val narrativeContent = buildString {
          appendLine("## ${narrative.title}")
          appendLine()
          appendLine("### Summary")
          appendLine(narrative.summary)
          appendLine()
          appendLine("### Themes")
          narrative.themes.forEach { theme ->
            appendLine("- $theme")
          }
          appendLine()
          appendLine("**Tone:** ${narrative.tone}")
          appendLine()
          appendLine("---")
          appendLine()
          narrative.acts.forEachIndexed { index, act ->
            appendLine("### Act ${act.act_number}: ${act.title}")
            appendLine()
            appendLine(act.description)
            appendLine()
            appendLine("**Key Events:**")
            act.key_events.forEach { event ->
              appendLine("- $event")
            }
            appendLine()
            if (act.character_developments.isNotEmpty()) {
              appendLine("**Character Developments:**")
              act.character_developments.forEach { (character, development) ->
                appendLine("- **$character:** $development")
              }
              appendLine()
            }
            if (index < narrative.acts.size - 1) {
              appendLine("---")
              appendLine()
            }
          }
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }
        narrativeTask.add(narrativeContent.renderMarkdown)
        task.update()
        // Save narrative to file
        saveAnalysisToFile(narrativeDir, "01_main_narrative.md", narrativeContent)

        // Write to transcript
        transcriptWriter?.appendLine("### ${narrative.title}")
        transcriptWriter?.appendLine()
        transcriptWriter?.appendLine("**Summary:** ${narrative.summary}")
        transcriptWriter?.appendLine()
        transcriptWriter?.appendLine("**Themes:** ${narrative.themes.joinToString(", ")}")
        transcriptWriter?.appendLine()
        transcriptWriter?.appendLine("**Tone:** ${narrative.tone}")
        transcriptWriter?.appendLine()
        narrative.acts.forEach { act ->
          transcriptWriter?.appendLine("- **Act ${act.act_number}:** ${act.title}")
        }
        transcriptWriter?.appendLine()


        resultBuilder.append("## Main Narrative: ${narrative.title}\n")
        resultBuilder.append("${narrative.summary}\n\n")
        resultBuilder.append("**Themes:** ${narrative.themes.joinToString(", ")}\n\n")

        overviewTask.add("✅ Main narrative constructed\n".renderMarkdown)
        task.update()
      }

      // Step 2: Identify plot points
      if (identifyPlotPoints) {
        log.info("Step 2: Identifying plot points")
        overviewTask.add("✅ Analyzing plot points...\n".renderMarkdown)
        task.update()

        val plotPointsTask = task.ui.newTask(false)
        tabs["Plot Points"] = plotPointsTask.placeholder
        transcriptWriter?.appendLine("## Step 2: Plot Points Analysis")
        transcriptWriter?.appendLine()


        plotPointsTask.add(
          buildString {
            appendLine("# Plot Points Analysis")
            appendLine()
            appendLine("**Status:** Identifying key narrative moments...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        val plotPointAgent = ParsedAgent(
          resultClass = PlotPoints::class.java,
          prompt = """
You are a narrative structure expert. Identify the key plot points in this narrative.

Subject: $subject

Narrative Elements:
${narrativeElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

Identify 5-7 critical plot points including:
- Inciting Incident (what sets the story in motion)
- Rising Action points (complications and challenges)
- Climax (the turning point)
- Falling Action (consequences unfold)
- Resolution (how things conclude)

For each plot point, specify:
- Type (e.g., "Inciting Incident", "First Plot Point", "Midpoint", "Climax", etc.)
- Description of what happens
- Significance to the overall narrative
- Timing in the story
- Which characters are most affected

Be specific and concrete.
          """.trimIndent(),
          model = api,
          temperature = 0.6,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        val plotPoints = plotPointAgent.answer(listOf("Identify plot points")).obj.points
        log.debug("Identified ${plotPoints.size} plot points")

        val plotPointsContent = buildString {
          appendLine("## Key Plot Points")
          appendLine()
          plotPoints.forEachIndexed { index, point ->
            appendLine("### ${index + 1}. ${point.type}")
            appendLine()
            appendLine("**Description:** ${point.description}")
            appendLine()
            appendLine("**Significance:** ${point.significance}")
            appendLine()
            appendLine("**Timing:** ${point.timing}")
            appendLine()
            if (point.affected_characters.isNotEmpty()) {
              appendLine("**Affected Characters:** ${point.affected_characters.joinToString(", ")}")
              appendLine()
            }
            if (index < plotPoints.size - 1) {
              appendLine("---")
              appendLine()
            }
          }
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }
        plotPointsTask.add(plotPointsContent.renderMarkdown)
        task.update()
        // Save plot points to file
        saveAnalysisToFile(narrativeDir, "02_plot_points.md", plotPointsContent)

        // Write to transcript
        transcriptWriter?.appendLine("### Identified ${plotPoints.size} Plot Points")
        plotPoints.forEach { point ->
          transcriptWriter?.appendLine("- **${point.type}:** ${point.description}")
        }
        transcriptWriter?.appendLine()


        resultBuilder.append("## Key Plot Points\n")
        plotPoints.take(3).forEach { point ->
          resultBuilder.append("- **${point.type}:** ${point.description.truncateForDisplay(maxDescriptionLength)}\n")
        }
        resultBuilder.append("\n")

        overviewTask.add("✅ Plot points identified (${plotPoints.size} points)\n".renderMarkdown)
        task.update()
      }

      // Step 3: Analyze character motivations
      if (analyzeMotivations) {
        log.info("Step 3: Analyzing character motivations")
        overviewTask.add("✅ Analyzing character motivations...\n".renderMarkdown)
        task.update()

        val charactersTask = task.ui.newTask(false)
        tabs["Characters"] = charactersTask.placeholder
        transcriptWriter?.appendLine("## Step 3: Character Analysis")
        transcriptWriter?.appendLine()


        charactersTask.add(
          buildString {
            appendLine("# Character Analysis")
            appendLine()
            appendLine("**Status:** Analyzing motivations and character arcs...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        val characters = (narrativeElements["characters"] as? List<*>) ?: emptyList<String>()
        val characterAgent = ParsedAgent(
          resultClass = CharacterAnalyses::class.java,
          prompt = """
You are a character psychology expert. Analyze the motivations and arcs of the characters in this narrative.

Subject: $subject

Characters: ${characters.joinToString(", ")}

Narrative Elements:
${narrativeElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

For each character, provide:
- Name
- Role in the narrative
- Core motivations (what drives them)
- Goals (what they want to achieve)
- Conflicts (internal and external)
- Character arc (how they change or grow)

Focus on psychological depth and realistic human behavior.
Consider stakeholder perspectives if analyzing organizational scenarios.
          """.trimIndent(),
          model = api,
          temperature = 0.6,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        val characterAnalyses = characterAgent.answer(listOf("Analyze characters")).obj.characters
        log.debug("Analyzed ${characterAnalyses.size} characters")

        val charactersContent = buildString {
          appendLine("## Character Analyses")
          appendLine()
          characterAnalyses.forEach { char ->
            appendLine("### ${char.name}")
            appendLine()
            appendLine("**Role:** ${char.role}")
            appendLine()
            appendLine("**Motivations:**")
            char.motivations.forEach { motivation ->
              appendLine("- $motivation")
            }
            appendLine()
            appendLine("**Goals:**")
            char.goals.forEach { goal ->
              appendLine("- $goal")
            }
            appendLine()
            appendLine("**Conflicts:**")
            char.conflicts.forEach { conflict ->
              appendLine("- $conflict")
            }
            appendLine()
            appendLine("**Character Arc:** ${char.arc}")
            appendLine()
            appendLine("---")
            appendLine()
          }
          appendLine("**Status:** ✅ Complete")
        }
        charactersTask.add(charactersContent.renderMarkdown)
        task.update()
        // Save character analysis to file
        saveAnalysisToFile(narrativeDir, "03_character_analysis.md", charactersContent)

        // Write to transcript
        transcriptWriter?.appendLine("### Analyzed ${characterAnalyses.size} Characters")
        characterAnalyses.forEach { char ->
          transcriptWriter?.appendLine("- **${char.name}** (${char.role})")
          transcriptWriter?.appendLine("  - Motivations: ${char.motivations.joinToString("; ")}")
        }
        transcriptWriter?.appendLine()


        resultBuilder.append("## Character Motivations\n")
        characterAnalyses.take(2).forEach { char ->
          resultBuilder.append("- **${char.name}:** ${char.motivations.firstOrNull() ?: "N/A"}\n")
        }
        resultBuilder.append("\n")

        overviewTask.add("✅ Character motivations analyzed (${characterAnalyses.size} characters)\n".renderMarkdown)
        task.update()
      }

      // Step 4: Predict outcomes
      if (predictOutcomes) {
        log.info("Step 4: Predicting narrative outcomes")
        overviewTask.add("✅ Predicting narrative outcomes...\n".renderMarkdown)
        task.update()

        val outcomesTask = task.ui.newTask(false)
        tabs["Predicted Outcomes"] = outcomesTask.placeholder
        transcriptWriter?.appendLine("## Step 4: Predicted Outcomes")
        transcriptWriter?.appendLine()


        outcomesTask.add(
          buildString {
            appendLine("# Predicted Outcomes")
            appendLine()
            appendLine("**Status:** Analyzing possible narrative resolutions...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        val outcomeAgent = ParsedAgent(
          resultClass = NarrativeOutcomes::class.java,
          prompt = """
You are a strategic foresight expert. Predict possible outcomes for this narrative.

Subject: $subject

Narrative Elements:
${narrativeElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

Predict $alternativeNarratives different possible outcomes, including:
- Most likely scenario
- Best case scenario
- Worst case scenario
- (Additional alternatives as needed)

For each outcome, provide:
- Scenario description
- Probability assessment (High/Medium/Low)
- Key factors that would lead to this outcome
- Consequences and implications
- Path to resolution

Be realistic and consider multiple perspectives.
          """.trimIndent(),
          model = api,
          temperature = 0.7,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        val outcomes = outcomeAgent.answer(listOf("Predict outcomes")).obj.outcomes
        log.debug("Predicted ${outcomes.size} outcomes")

        val outcomesContent = buildString {
          appendLine("## Possible Outcomes")
          appendLine()
          outcomes.forEachIndexed { index, outcome ->
            appendLine("### Outcome ${index + 1}: ${outcome.scenario}")
            appendLine()
            appendLine("**Probability:** ${outcome.probability}")
            appendLine()
            appendLine("**Key Factors:**")
            outcome.key_factors.forEach { factor ->
              appendLine("- $factor")
            }
            appendLine()
            appendLine("**Consequences:**")
            outcome.consequences.forEach { consequence ->
              appendLine("- $consequence")
            }
            appendLine()
            appendLine("**Resolution Path:** ${outcome.resolution_path}")
            appendLine()
            if (index < outcomes.size - 1) {
              appendLine("---")
              appendLine()
            }
          }
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }
        outcomesTask.add(outcomesContent.renderMarkdown)
        task.update()
        // Save outcomes to file
        saveAnalysisToFile(narrativeDir, "04_predicted_outcomes.md", outcomesContent)

        // Write to transcript
        transcriptWriter?.appendLine("### Predicted ${outcomes.size} Outcomes")
        outcomes.forEach { outcome ->
          transcriptWriter?.appendLine("- **${outcome.scenario}** (${outcome.probability})")
        }
        transcriptWriter?.appendLine()


        resultBuilder.append("## Predicted Outcomes\n")
        outcomes.forEach { outcome ->
          resultBuilder.append("- **${outcome.scenario}** (${outcome.probability})\n")
        }
        resultBuilder.append("\n")

        overviewTask.add("✅ Outcomes predicted (${outcomes.size} scenarios)\n".renderMarkdown)
        task.update()
      }

      // Step 5: Find inconsistencies
      if (findInconsistencies) {
        log.info("Step 5: Finding narrative inconsistencies")
        overviewTask.add("✅ Checking for inconsistencies...\n".renderMarkdown)
        task.update()

        val inconsistenciesTask = task.ui.newTask(false)
        tabs["Inconsistencies"] = inconsistenciesTask.placeholder
        transcriptWriter?.appendLine("## Step 5: Inconsistency Analysis")
        transcriptWriter?.appendLine()


        inconsistenciesTask.add(
          buildString {
            appendLine("# Narrative Inconsistencies")
            appendLine()
            appendLine("**Status:** Analyzing for gaps and contradictions...")
            appendLine()
          }.renderMarkdown
        )
        task.update()

        val inconsistencyAgent = ParsedAgent(
          resultClass = NarrativeInconsistencies::class.java,
          prompt = """
You are a narrative consistency expert. Identify any inconsistencies, gaps, or contradictions in this narrative.

Subject: $subject

Narrative Elements:
${narrativeElements.entries.joinToString("\n") { (key, value) -> "- $key: $value" }}

Look for:
- Logical contradictions
- Timeline inconsistencies
- Character behavior that doesn't match motivations
- Missing information or gaps
- Unrealistic assumptions
- Conflicting goals or constraints

For each inconsistency, provide:
- Type (e.g., "Logical Contradiction", "Timeline Gap", "Character Inconsistency")
- Description of the issue
- Where it occurs in the narrative
- Severity (Critical/Major/Minor)
- Suggested resolution

 Be thorough but fair. If the narrative is consistent, say so.
          """.trimIndent(),
          model = api,
          temperature = 0.5,
          parsingChatter = orchestrationConfig.parsingChatter
        )

        val inconsistencies = inconsistencyAgent.answer(listOf("Find inconsistencies")).obj.inconsistencies
        log.debug("Found ${inconsistencies.size} inconsistencies")

        val inconsistenciesContent = buildString {
          if (inconsistencies.isEmpty()) {
            appendLine("## ✅ No Significant Inconsistencies Found")
            appendLine()
            appendLine("The narrative appears to be internally consistent with no major contradictions or gaps.")
          } else {
            appendLine("## Identified Inconsistencies")
            appendLine()
            inconsistencies.forEachIndexed { index, inconsistency ->
              val severityIcon = when (inconsistency.severity.lowercase()) {
                "critical" -> "🔴"
                "major" -> "🟡"
                else -> "🟢"
              }
              appendLine("### $severityIcon ${index + 1}. ${inconsistency.type}")
              appendLine()
              appendLine("**Severity:** ${inconsistency.severity}")
              appendLine()
              appendLine("**Description:** ${inconsistency.description}")
              appendLine()
              appendLine("**Location:** ${inconsistency.location}")
              appendLine()
              appendLine("**Suggested Resolution:** ${inconsistency.suggested_resolution}")
              appendLine()
              if (index < inconsistencies.size - 1) {
                appendLine("---")
                appendLine()
              }
            }
          }
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }
        inconsistenciesTask.add(inconsistenciesContent.renderMarkdown)
        task.update()
        // Save inconsistencies to file
        saveAnalysisToFile(narrativeDir, "05_inconsistencies.md", inconsistenciesContent)

        // Write to transcript
        if (inconsistencies.isEmpty()) {
          transcriptWriter?.appendLine("### No significant inconsistencies found")
        } else {
          transcriptWriter?.appendLine("### Found ${inconsistencies.size} Inconsistencies")
          inconsistencies.forEach { inconsistency ->
            transcriptWriter?.appendLine("- **${inconsistency.type}** (${inconsistency.severity}): ${inconsistency.description}")
          }
        }
        transcriptWriter?.appendLine()


        if (inconsistencies.isNotEmpty()) {
          resultBuilder.append("## Narrative Inconsistencies\n")
          inconsistencies.take(3).forEach { inconsistency ->
            resultBuilder.append("- **${inconsistency.type}:** ${inconsistency.description.truncateForDisplay(maxDescriptionLength)}\n")
          }
          resultBuilder.append("\n")
        }

        overviewTask.add("✅ Inconsistency check complete (${inconsistencies.size} found)\n".renderMarkdown)
        task.update()
      }

      // Step 6: Generate synthesis
      log.info("Step 6: Generating synthesis")
      overviewTask.add("✅ Generating synthesis...\n".renderMarkdown)
      task.update()

      val synthesisTask = task.ui.newTask(false)
      tabs["Synthesis"] = synthesisTask.placeholder
      transcriptWriter?.appendLine("## Step 6: Synthesis")
      transcriptWriter?.appendLine()


      synthesisTask.add(
        buildString {
          appendLine("# Narrative Synthesis")
          appendLine()
          appendLine("**Status:** Synthesizing insights...")
          appendLine()
        }.renderMarkdown
      )
      task.update()

      val synthesisAgent = ChatAgent(
        prompt = """
You are a narrative synthesis expert. Provide a comprehensive synthesis of the narrative analysis.

Subject: $subject

Summarize:
1. The core narrative and its significance
2. Key insights from the analysis
3. Critical decision points or turning points
4. Stakeholder perspectives and tensions
5. Recommended actions or considerations
6. Overall assessment of narrative coherence

Be concise but insightful. Focus on actionable insights.
        """.trimIndent(),
        model = api,
        temperature = 0.6
      )

      val synthesis = synthesisAgent.answer(listOf("Generate synthesis"))
      log.debug("Synthesis generated: ${synthesis.length} characters")

      synthesisTask.add(
        buildString {
          appendLine("## Key Insights")
          appendLine()
          appendLine(synthesis)
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("**Status:** ✅ Complete")
        }.renderMarkdown
      )
      task.update()
      // Save synthesis to file
      saveAnalysisToFile(narrativeDir, "06_synthesis.md", synthesis)

      transcriptWriter?.appendLine(synthesis)
      transcriptWriter?.appendLine()


      resultBuilder.append("## Synthesis\n")
      resultBuilder.append(synthesis)
      resultBuilder.append("\n\n")

      // Final statistics
      val totalTime = System.currentTimeMillis() - startTime
      resultBuilder.append("---\n\n")
      resultBuilder.append("**Analysis Time:** ${totalTime / 1000}s | ")
      resultBuilder.append("**Subject:** $subject\n")
      // Write completion to transcript
      transcriptWriter?.appendLine("---")
      transcriptWriter?.appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
      transcriptWriter?.appendLine("**Total Time:** ${totalTime / 1000.0}s")

      overviewTask.add(
        buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## ✅ Analysis Complete")
          appendLine()
          appendLine("**Total Time:** ${totalTime / 1000.0}s")
          appendLine()
          appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        }.renderMarkdown
      )
      task.update()

      val finalResult = resultBuilder.toString()
      log.info("NarrativeReasoningTask completed: total_time=${totalTime}ms, output_size=${finalResult.length} chars")

      // Create summary message with file links
      val summaryMessage = buildString {
        appendLine("# Narrative Analysis Complete")
        appendLine()
        appendLine("**Subject:** $subject")
        appendLine("**Time:** ${totalTime / 1000}s")
        appendLine()
        appendLine("## Detailed Results")
        appendLine()
        appendLine("Full analysis has been saved to the following files:")
        appendLine()
        appendLine("- [Main Narrative](${narrativeDir.name}/01_main_narrative.md)")
        appendLine("- [Plot Points](${narrativeDir.name}/02_plot_points.md)")
        appendLine("- [Character Analysis](${narrativeDir.name}/03_character_analysis.md)")
        appendLine("- [Predicted Outcomes](${narrativeDir.name}/04_predicted_outcomes.md)")
        appendLine("- [Inconsistencies](${narrativeDir.name}/05_inconsistencies.md)")
        appendLine("- [Synthesis](${narrativeDir.name}/06_synthesis.md)")
        appendLine()
        appendLine("## Summary")
        appendLine()
        appendLine(finalResult.take(1000) + if (finalResult.length > 1000) "\n\n*See detailed files for complete analysis*" else "")
      }

      task.safeComplete("Narrative analysis complete in ${totalTime / 1000}s. Results saved to .narrative_analysis directory.", log)
      resultFn(summaryMessage)
      // Close transcript
      transcriptWriter?.flush()
      transcriptWriter?.close()

    } catch (e: Exception) {
      log.error("Error during narrative reasoning", e)
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
        appendLine("# Error in Narrative Reasoning")
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
      // Close transcript on error
      transcriptWriter?.appendLine("**Error:** ${e.message}")
      transcriptWriter?.flush()
      transcriptWriter?.close()
    }
  }

  private fun getInputFileContent(): String = (executionConfig?.input_files ?: listOf())
    .flatMap { pattern: String ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
          matcher.matches(root.relativize(it.toPath())) -> true
          it.isDirectory -> true
          else -> false
        }
      })
    }.filter { file ->
      file.isFile && file.exists()
    }
    .distinct()
    .sortedBy { it }
    .joinToString("\n\n") { relativePath ->
      val file = root.toFile().resolve(relativePath)
      try {
        val content = file.readText()
        "# $relativePath\n\n```\n$content\n```"
      } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
      }
    }

  private fun saveAnalysisToFile(
    outputDir: File,
    filename: String,
    content: String
  ) {
    try {
      val outputFile = File(outputDir, filename)
      outputFile.writeText(content, StandardCharsets.UTF_8)
      log.debug("Saved analysis to file: ${outputFile.absolutePath} (size: ${content.length} chars)")
    } catch (e: Exception) {
      log.error("Failed to save analysis to file: $filename", e)
    }
  }

  private fun isTextFile(file: File): Boolean {
    val textExtensions = setOf(
      "txt",
      "md",
      "kt",
      "java",
      "js",
      "ts",
      "py",
      "rb",
      "go",
      "rs",
      "c",
      "cpp",
      "h",
      "hpp",
      "css",
      "html",
      "xml",
      "json",
      "yaml",
      "yml",
      "properties",
      "gradle",
      "maven"
    )
    return textExtensions.contains(file.extension.lowercase())
  }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(NarrativeReasoningTask::class.java)
    val NarrativeReasoning = TaskType(
      "NarrativeReasoning",
      NarrativeReasoningTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Understand scenarios through narrative structures and storytelling",
      """
              Analyzes complex scenarios through narrative reasoning and storytelling frameworks.
              <ul>
                <li>Constructs coherent narratives from scenario elements</li>
                <li>Identifies story arcs, plot points, and narrative patterns</li>
                <li>Analyzes character motivations and stakeholder perspectives</li>
                <li>Predicts narrative outcomes and alternative scenarios</li>
                <li>Finds narrative inconsistencies and gaps</li>
                <li>Useful for user journey analysis, system evolution, change management</li>
                <li>Generates structured narrative analysis with actionable insights</li>
              </ul>
            """
    )
  }
}