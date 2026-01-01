package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class TemporalReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: TemporalReasoningTaskExecutionConfigData?
) : AbstractTask<TemporalReasoningTask.TemporalReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    val maxOutputLength: Int = 20000

    class TemporalReasoningTaskExecutionConfigData(
        @Description("The subject or system to analyze over time")
        val subject: String? = null,
        @Description("Time range to analyze (e.g., '2023-01-01 to 2024-01-01')")
        val time_range: String? = null,
        @Description("Granularity of analysis: daily, weekly, monthly, quarterly, yearly")
        val granularity: String = "weekly",
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("Whether to identify temporal patterns and cycles")
        val identify_patterns: Boolean = true,
        @Description("Whether to predict future states")
        val predict_future: Boolean = true,
        @Description("How far into the future to predict (e.g., '3 months', '6 weeks')")
        val prediction_horizon: String? = "3 months",
        @Description("Critical events to highlight in the timeline")
        val critical_events: List<String>? = null,
        @Description("Related files containing temporal data (logs, metrics, etc.)")
        val related_files: List<String>? = null,
        @Description("Whether to analyze rate of change and acceleration")
        val analyze_rate_of_change: Boolean = true,
        @Description("Whether to identify critical transition points")
        val identify_transitions: Boolean = true,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = TemporalReasoning.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (subject.isNullOrBlank()) {
                return "subject must not be null or blank"
            }
            if (time_range.isNullOrBlank()) {
                return "time_range must not be null or blank"
            }
            if (granularity.isBlank()) {
                return "granularity must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class TimelineEvent(
        val timestamp: String = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
        val event_type: String = "generic",
        val description: String = "",
        val significance: String = "medium",
        val related_metrics: Map<String, String>? = null
    )

    data class TemporalPattern(
        val pattern_type: String = "recurring",
        val description: String = "",
        val frequency: String = "unknown",
        val confidence: String = "medium",
        val examples: List<String> = emptyList()
    )

    data class TimelineAnalysis(
        val timeline_events: List<TimelineEvent> = emptyList(),
        val patterns: List<TemporalPattern>? = null,
        val rate_of_change_analysis: String? = null,
        val transition_points: List<String>? = null,
        val future_predictions: List<String>? = null
    )

    override fun promptSegment(): String {
        return """
TemporalReasoning - Analyze how systems evolve over time and predict future states
  ** Specify the subject or system to analyze
  ** Define the time range to examine
  ** Set granularity (daily, weekly, monthly, quarterly, yearly)
  ** Optionally identify temporal patterns and cycles
  ** Optionally predict future states based on trends
  ** Optionally analyze rate of change and acceleration
  ** Optionally identify critical transition points
  ** Provide related files with temporal data (logs, metrics)
  ** Useful for:
     - Technical debt accumulation analysis
     - System evolution and architecture drift
     - Performance degradation over time
     - Bug introduction timeline analysis
     - Feature adoption and usage patterns
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
        log.info("Starting TemporalReasoning task for subject: ${executionConfig?.subject}")

        val subject = executionConfig?.subject
        if (subject.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No subject specified for temporal analysis"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val timeRange = executionConfig?.time_range
        if (timeRange.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No time range specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val api = defaultSmart ?: return
        val ui = task.ui
        val transcript = task.transcript()

        try {
            // Create tabbed display for organized output
            val tabs = TabbedDisplay(task)

            // Overview tab
            val overviewTask = tabs.newTask("Overview")
            val overviewStatus = overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Temporal Reasoning Analysis
            |
            |**Subject:** $subject
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
            |
            |**Status:** 🔄 Gathering temporal data...
        """.trimMargin(), ui = ui
                )
            )
            transcript?.write(
                """
            |# Temporal Reasoning Analysis
            |
            |**Subject:** $subject
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
            |
            |**Started:** ${java.time.LocalDateTime.now()}
            |
            |---
            |
            |## Gathering Temporal Data
            |
        """.trimMargin().toByteArray()
            )
            task.update()

            // Gather temporal data from files
            log.debug("Gathering temporal data from ${executionConfig?.related_files?.size ?: 0} file patterns")
            val dataTask = tabs.newTask("Temporal Data")
            val dataLoading = dataTask.add(
                MarkdownUtil.renderMarkdown("## Temporal Data Sources\n\n🔄 Loading temporal data...", ui = ui)
            )
            task.update()

            val temporalData = gatherTemporalData()
            log.debug("Temporal data gathered: ${temporalData.length} characters")
            dataLoading?.clear()
            transcript?.write(
                """
            |
            |### Data Sources Processed: ${executionConfig?.related_files?.size ?: 0}
            |
            |${temporalData.truncateForDisplay(maxOutputLength)}
            |
            |---
            |
        """.trimMargin().toByteArray()
            )
            dataTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Temporal Data Sources
            |
            |✅ Data gathered successfully
            |
            |**Sources processed:** ${executionConfig?.related_files?.size ?: 0}
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
        """.trimMargin(), ui = ui
                )
            )
            dataTask.expandable(
                "Temporal Data Context", MarkdownUtil.renderMarkdown(
                    """
                |```
                |${temporalData.truncateForDisplay(maxOutputLength)}
                |```
                """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Get prior context
            log.debug("Retrieving prior context from execution state")
            val priorContext = getPriorCode(agent.executionState)

            // Update overview
            overviewStatus?.setLength(0)
            overviewStatus?.append(
                MarkdownUtil.renderMarkdown(
                    """
            |## Temporal Reasoning Analysis
            |
            |**Subject:** $subject
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
            |
            |**Status:** 🔄 Constructing timeline...
        """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 1: Construct timeline
            log.debug("Constructing timeline of events")
            val timelineTask = tabs.newTask("Timeline")
            val timelineLoading = timelineTask.add(
                MarkdownUtil.renderMarkdown(
                    "## Timeline Construction\n\n🔄 Analyzing temporal data and constructing timeline...",
                    ui = ui
                )
            )
            task.update()

            val timelinePrompt = buildTimelinePrompt(subject, timeRange, temporalData, priorContext)
            val timelineAgent = ParsedAgent(
                resultClass = TimelineAnalysis::class.java,
                prompt = timelinePrompt,
                model = api,
                temperature = 0.3,
                parsingChatter = defaultFast,
            )

            val timelineAnalysis = timelineAgent.answer(listOf(timelinePrompt)).obj
            log.debug("Timeline constructed with ${timelineAnalysis.timeline_events.size} events")
            transcript?.write(
                """
            |
            |## Timeline Construction Complete
            |
            |**Events Identified:** ${timelineAnalysis.timeline_events.size}
            |
            |${formatTimeline(timelineAnalysis.timeline_events)}
        """.trimMargin().toByteArray()
            )

            timelineLoading?.clear()
            timelineTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Timeline of Events
            |
            |✅ Timeline constructed successfully
            |
            |**Events Identified:** ${timelineAnalysis.timeline_events.size}
            |
            |${formatTimeline(timelineAnalysis.timeline_events)}
        """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 2: Pattern identification (if enabled)
            if (executionConfig.identify_patterns && !timelineAnalysis.patterns.isNullOrEmpty()) {
                log.debug("Analyzing temporal patterns")
                val patternsTask = tabs.newTask("Patterns")
                transcript?.write(
                    """
            |
            |## Temporal Patterns Analysis
            |
            |**Patterns Found:** ${timelineAnalysis.patterns.size}
            |
            |${formatPatterns(timelineAnalysis.patterns)}
        """.trimMargin().toByteArray()
                )
                patternsTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
              |## Temporal Patterns
              |
              |✅ Patterns identified
              |
              |**Patterns Found:** ${timelineAnalysis.patterns.size}
              |
              |${formatPatterns(timelineAnalysis.patterns)}
          """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 3: Rate of change analysis (if enabled)
            if (executionConfig.analyze_rate_of_change && !timelineAnalysis.rate_of_change_analysis.isNullOrBlank()) {
                log.debug("Analyzing rate of change")
                val rateTask = tabs.newTask("Rate of Change")
                transcript?.write(
                    """
            |
            |## Rate of Change Analysis
            |
            |${timelineAnalysis.rate_of_change_analysis}
            |
        """.trimMargin().toByteArray()
                )
                rateTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
              |## Rate of Change Analysis
              |
              |✅ Analysis complete
              |
              |${timelineAnalysis.rate_of_change_analysis}
          """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 4: Transition points (if enabled)
            if (executionConfig.identify_transitions && !timelineAnalysis.transition_points.isNullOrEmpty()) {
                log.debug("Identifying critical transition points")
                val transitionsTask = tabs.newTask("Transition Points")
                transcript?.write(
                    """
            |
            |## Critical Transition Points
            |
            |${formatTransitions(timelineAnalysis.transition_points)}
        """.trimMargin().toByteArray()
                )
                transitionsTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
              |## Critical Transition Points
              |
              |✅ Transitions identified
              |
              |**Transitions Found:** ${timelineAnalysis.transition_points.size}
              |
              |${formatTransitions(timelineAnalysis.transition_points)}
          """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 5: Future predictions (if enabled)
            if (executionConfig.predict_future && !timelineAnalysis.future_predictions.isNullOrEmpty()) {
                log.debug("Generating future predictions")
                val predictionsTask = tabs.newTask("Future Predictions")
                transcript?.write(
                    """
            |
            |## Future State Predictions
            |
            |${formatPredictions(timelineAnalysis.future_predictions)}
        """.trimMargin().toByteArray()
                )
                predictionsTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
              |## Future State Predictions
              |
              |✅ Predictions generated
              |
              |**Prediction Horizon:** ${executionConfig.prediction_horizon}
              |
              |${formatPredictions(timelineAnalysis.future_predictions)}
          """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 6: Generate visualization
            log.debug("Generating timeline visualization")
            val vizTask = tabs.newTask("Visualization")
            val vizLoading = vizTask.add(
                MarkdownUtil.renderMarkdown("## Timeline Visualization\n\n🔄 Generating Mermaid diagram...", ui = ui)
            )
            task.update()

            val vizPrompt = buildVisualizationPrompt(timelineAnalysis, subject, timeRange)
            val chatAgent = ChatAgent(
                prompt = vizPrompt,
                model = api,
                temperature = 0.3
            )

            val vizResult = chatAgent.answer(listOf(vizPrompt))
            val mermaidCode = extractMermaidCode(vizResult)

            vizLoading?.clear()
            if (mermaidCode.isNotEmpty()) {
                transcript?.write(
                    """
            |
            |## Timeline Visualization
            |
            |```mermaid
            |$mermaidCode
            |```
            |
        """.trimMargin().toByteArray()
                )
                vizTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
              |## Timeline Visualization
              |
              |✅ Visualization generated
              |
              |```mermaid
              |$mermaidCode
              |```
          """.trimMargin(), ui = ui
                    )
                )
            } else {
                vizTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
              |## Timeline Visualization
              |
              |⚠️ Could not generate visualization
              |
              |The analysis did not produce a valid Mermaid diagram.
          """.trimMargin(), ui = ui
                    )
                )
            }
            task.update()

            // Generate final summary
            val summary = buildSummary(timelineAnalysis, subject, timeRange)
            transcript?.write(
                """
            |
            |---
            |
            |## Summary
            |
            |$summary
            |
        """.trimMargin().toByteArray()
            )

            // Update overview with completion
            overviewStatus?.setLength(0)
            overviewStatus?.append(
                MarkdownUtil.renderMarkdown(
                    """
            |## Temporal Reasoning Analysis
            |
            |**Subject:** $subject
            |
            |**Time Range:** $timeRange
            |
            |**Granularity:** ${executionConfig.granularity}
            |
            |**Status:** ✅ Analysis complete
            |
            |---
            |
            |### Summary
            |
            |$summary
        """.trimMargin(), ui = ui
                )
            )
            task.update()

            val duration = System.currentTimeMillis() - startTime
            val completionMsg = "Temporal analysis completed for '$subject' over $timeRange"
            log.info("$completionMsg (duration: ${duration}ms, events: ${timelineAnalysis.timeline_events.size}, patterns: ${timelineAnalysis.patterns?.size ?: 0})")
            transcript?.close()

            task.safeComplete(completionMsg, log)
            resultFn(summary)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("TemporalReasoning task failed after ${duration}ms for subject: $subject", e)
            transcript?.close()
            task.error(e)

            val errorTask = task.newTask()
            errorTask.add(
                MarkdownUtil.renderMarkdown(
                    "## ❌ Error\n\nAn error occurred during temporal reasoning analysis:\n\n```\n${e.message}\n```",
                    ui = ui
                )
            )
            task.safeComplete("Analysis failed: ${e.message}", log)
            resultFn("ERROR: Temporal reasoning analysis failed - ${e.message}")
        }
    }


    private fun buildTimelinePrompt(
        subject: String,
        timeRange: String,
        temporalData: String,
        priorContext: String
    ): String {
        val criticalEvents = executionConfig?.critical_events?.joinToString(", ") ?: "any significant events"
        val granularity = executionConfig?.granularity ?: "weekly"

        return """
You are an expert in temporal reasoning and timeline analysis. Your task is to analyze how a system or situation evolves over time.

## Subject to Analyze:
$subject

## Time Range:
$timeRange

## Granularity:
$granularity

## Critical Events to Highlight:
$criticalEvents

## Temporal Data:
$temporalData

## Previous Task Results:
$priorContext

## Analysis Instructions:

1. **Construct Timeline**: Create a chronological timeline of significant events
   - Identify key events and changes
   - Note timestamps and durations
   - Categorize events by type (deployment, incident, change, milestone, etc.)
   - Assess significance of each event

2. **Identify Patterns** (if enabled: ${executionConfig?.identify_patterns}):
   - Look for recurring patterns and cycles
   - Identify seasonal or periodic trends
   - Note correlations between events
   - Assess pattern confidence and frequency

3. **Analyze Rate of Change** (if enabled: ${executionConfig?.analyze_rate_of_change}):
   - Calculate velocity of change over time
   - Identify acceleration or deceleration
   - Note periods of stability vs rapid change
   - Quantify trends where possible

4. **Identify Transition Points** (if enabled: ${executionConfig?.identify_transitions}):
   - Find critical inflection points
   - Identify phase transitions
   - Note sudden changes or disruptions
   - Explain what triggered each transition

5. **Predict Future States** (if enabled: ${executionConfig?.predict_future}):
   - Extrapolate trends into the future
   - Predict likely outcomes based on historical patterns
   - Identify potential risks and opportunities
   - Provide confidence levels for predictions
   - Prediction horizon: ${executionConfig?.prediction_horizon}

## Output Format:
Provide a structured JSON response with:
- timeline_events: Array of events with timestamp, type, description, significance, and related metrics
- patterns: Array of identified patterns (if enabled)
- rate_of_change_analysis: Detailed analysis of change velocity (if enabled)
- transition_points: Array of critical transition points (if enabled)
- future_predictions: Array of predictions (if enabled)

Generate the temporal analysis now:
        """.trimIndent()
    }

    private fun buildVisualizationPrompt(
        analysis: TimelineAnalysis,
        subject: String,
        timeRange: String
    ): String {
        return """
Based on the temporal analysis for "$subject" over $timeRange, create a Mermaid timeline diagram.

Timeline Events:
${analysis.timeline_events.joinToString("\n") { "- ${it.timestamp}: ${it.description}" }}

${if (!analysis.patterns.isNullOrEmpty()) "Patterns: ${analysis.patterns.size} identified" else ""}
${if (!analysis.transition_points.isNullOrEmpty()) "Transition Points: ${analysis.transition_points.size} identified" else ""}

Use the following format:
- Use `timeline` for the diagram type
- Show events chronologically
- Use different sections for different time periods
- Highlight critical events and transition points
- Keep labels concise but descriptive

Generate the Mermaid timeline diagram now:
            """.trimIndent()
    }

    private fun gatherTemporalData(): String {
        val inputFiles = (executionConfig?.input_files ?: emptyList()) +
                (executionConfig?.related_files ?: emptyList())

        if (inputFiles.isEmpty()) {
            return "No specific temporal data files provided."
        }

        val maxFileSize = 2000

        return inputFiles.flatMap { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            root.toFile().walkTopDown()
                .filter { file ->
                    file.isFile && matcher.matches(root.relativize(file.toPath()))
                }
                .map { file ->
                    val relativePath = root.relativize(file.toPath())
                    try {
                        val content = file.readText()
                        "### $relativePath\n```\n${content.truncateForDisplay(maxFileSize)}\n```"
                    } catch (e: Exception) {
                        log.warn("Error reading temporal data file: $relativePath", e)
                        "### $relativePath\n(Error reading file: ${e.message})"
                    }
                }
                .toList()
        }.joinToString("\n\n")
    }

    private fun formatTimeline(events: List<TimelineEvent>): String {
        return buildString {
            appendLine()
            events.forEach { event ->
                appendLine("### ${event.timestamp} - ${event.event_type}")
                appendLine()
                appendLine("**Description:** ${event.description}")
                appendLine()
                appendLine("**Significance:** ${event.significance}")
                if (!event.related_metrics.isNullOrEmpty()) {
                    appendLine()
                    appendLine("**Metrics:**")
                    event.related_metrics.forEach { (key, value) ->
                        appendLine("- $key: $value")
                    }
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun formatPatterns(patterns: List<TemporalPattern>): String {
        return buildString {
            appendLine()
            patterns.forEach { pattern ->
                appendLine("### ${pattern.pattern_type}")
                appendLine()
                appendLine("**Description:** ${pattern.description}")
                appendLine()
                appendLine("**Frequency:** ${pattern.frequency}")
                appendLine()
                appendLine("**Confidence:** ${pattern.confidence}")
                appendLine()
                if (pattern.examples.isNotEmpty()) {
                    appendLine("**Examples:**")
                    pattern.examples.forEach { example ->
                        appendLine("- $example")
                    }
                }
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun formatTransitions(transitions: List<String>): String {
        return buildString {
            appendLine()
            transitions.forEachIndexed { index, transition ->
                appendLine("### Transition ${index + 1}")
                appendLine()
                appendLine(transition)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun formatPredictions(predictions: List<String>): String {
        return buildString {
            appendLine()
            predictions.forEachIndexed { index, prediction ->
                appendLine("### Prediction ${index + 1}")
                appendLine()
                appendLine(prediction)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }
    }

    private fun buildSummary(analysis: TimelineAnalysis, subject: String, timeRange: String): String {
        return buildString {
            appendLine("**Subject:** $subject")
            appendLine()
            appendLine("**Time Range:** $timeRange")
            appendLine()
            appendLine("**Events Analyzed:** ${analysis.timeline_events.size}")
            appendLine()
            if (!analysis.patterns.isNullOrEmpty()) {
                appendLine("**Patterns Identified:** ${analysis.patterns.size}")
                appendLine()
            }
            if (!analysis.transition_points.isNullOrEmpty()) {
                appendLine("**Critical Transitions:** ${analysis.transition_points.size}")
                appendLine()
            }
            if (!analysis.future_predictions.isNullOrEmpty()) {
                appendLine("**Future Predictions:** ${analysis.future_predictions.size}")
                appendLine()
            }
            appendLine()
            appendLine("The temporal analysis reveals how $subject evolved over $timeRange, ")
            appendLine("identifying key events, patterns, and trends that shaped its development.")
        }
    }

    private fun extractMermaidCode(response: String): String {
        val mermaidBlockRegex = "```mermaid\\s*([\\s\\S]*?)```".toRegex()
        val match = mermaidBlockRegex.find(response)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(TemporalReasoningTask::class.java)
        val TemporalReasoning = TaskType(
            "TemporalReasoning",
            "Reasoning",
            TemporalReasoningTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Analyze how systems evolve over time and predict future states",
            """
              Performs temporal reasoning and timeline analysis to understand system evolution.
              <ul>
                <li>Constructs chronological timelines of events and changes</li>
                <li>Identifies temporal patterns, cycles, and trends</li>
                <li>Analyzes rate of change and acceleration</li>
                <li>Identifies critical transition points and inflection points</li>
                <li>Predicts future states based on historical trends</li>
                <li>Useful for technical debt analysis, performance degradation, and system evolution</li>
              </ul>
            """
        )
    }
}