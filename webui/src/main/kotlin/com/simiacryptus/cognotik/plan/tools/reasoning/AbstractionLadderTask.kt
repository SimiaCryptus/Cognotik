package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class AbstractionLadderTask(
    orchestrationConfig: OrchestrationConfig, planTask: AbstractionLadderTaskExecutionConfigData?
) : AbstractTask<AbstractionLadderTask.AbstractionLadderTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig, planTask
) {

    class AbstractionLadderTaskExecutionConfigData(
        @Description("The concrete concept, problem, or code pattern to analyze") val concrete_concept: String? = null,
        @Description("Direction to traverse: 'up' for abstraction (generalizations), 'down' for concretization (specific implementations), 'both' for bidirectional analysis") val direction: String = "both",
        @Description("Number of abstraction levels to traverse in each direction (1-5 recommended)") val levels: Int = 3,
        @Description("Whether to identify design patterns, anti-patterns, and refactoring opportunities at each level") val identify_patterns: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task") val input_files: List<String>? = null,
        @Description("Additional files for context (e.g., existing code, related implementations)") val related_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = AbstractionLadder.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (concrete_concept.isNullOrBlank()) {
                return "concrete_concept must not be null or blank"
            }
            if (direction.lowercase() !in listOf("up", "down", "both")) {
                return "direction must be 'up', 'down', or 'both', got: '$direction'"
            }
            if (levels !in 1..5) {
                return "levels must be between 1 and 5, got: $levels"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
AbstractionLadder - Traverse abstraction levels to find patterns and design insights
  ** Specify the concrete concept or problem to analyze
  ** Choose direction: 'up' (generalize), 'down' (concretize), or 'both'
  ** Set number of levels to traverse (1-5 recommended)
  ** Enable pattern identification to discover:
     - Design patterns and anti-patterns
     - Refactoring opportunities
     - Architectural insights
     - Code smells and improvements
  ** Related files provide context for analysis
  ** Output includes:
     - Abstraction hierarchy visualization
     - Pattern analysis at each level
     - Concrete examples and generalizations
     - Refactoring recommendations
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        var detailedOutputFile: FileOutputStream? = null
        val startTime = System.currentTimeMillis()
        log.info(
            "Starting Abstraction Ladder Analysis - Concept: ${
                executionConfig?.concrete_concept?.truncateForDisplay(
                    100
                )
            }, Direction: ${executionConfig?.direction}, Levels: ${executionConfig?.levels}"
        )
        // Validate configuration
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }


        val concept = executionConfig?.concrete_concept
        if (concept.isNullOrBlank()) {
            log.error("Configuration error: No concrete concept specified")
            task.safeComplete("CONFIGURATION ERROR: No concrete concept specified", log)
            resultFn("CONFIGURATION ERROR: No concrete concept specified")
            return
        }

        val direction = executionConfig.direction.lowercase()
        if (direction !in listOf("up", "down", "both")) {
            log.error("Configuration error: Invalid direction '$direction'")
            task.safeComplete("CONFIGURATION ERROR: Invalid direction", log)
            resultFn("CONFIGURATION ERROR: Direction must be 'up', 'down', or 'both'")
            return
        }

        val levels = executionConfig.levels.coerceIn(1, 5)
        val identifyPatterns = executionConfig.identify_patterns

        val api = defaultSmart ?: return

        // Initialize detailed output file
        detailedOutputFile = initializeDetailedOutput(task)
        detailedOutputFile?.write(
            """
      # Abstraction Ladder Analysis Transcript
      **Concept:** $concept  
      **Direction:** $direction  
      **Levels:** $levels  
      **Pattern Analysis:** ${if (identifyPatterns) "Enabled" else "Disabled"}
      ---
      
      **Concept:** $concept  
      **Direction:** $direction  
      **Levels:** $levels  
      **Pattern Analysis:** ${if (identifyPatterns) "Enabled" else "Disabled"}
    """.trimIndent().toByteArray()
        )

        val tabbedDisplay = TabbedDisplay(task)

        // Overview tab with input context
        val overviewTask = task.newTask().apply {
            tabbedDisplay["Overview"] = placeholder
            header("Abstraction Ladder Analysis: $concept", 2)
            add(
                MarkdownUtil.renderMarkdown(
                    """
          
          
          **Direction:** $direction | **Levels:** $levels | **Pattern Analysis:** ${if (identifyPatterns) "Enabled" else "Disabled"}
          
          Starting analysis...
          """.trimIndent(), ui = task.ui
                )
            )
        }
        val inputFileContent =
            super.getInputFileContent(executionConfig?.input_files, root, treatDocumentsAsText = true)


        val contextFiles = getContextFiles()
        val priorCode = getPriorCode(agent.executionState)

        val result = StringBuilder()

        try {
            if (direction == "up" || direction == "both") {
                log.info("Performing upward abstraction analysis")
                val upwardTab = task.newTask()
                tabbedDisplay["Upward Analysis"] = upwardTab.placeholder
                val upwardAnalysis = analyzeUpward(
                    concept = concept,
                    levels = levels,
                    identifyPatterns = identifyPatterns,
                    contextFiles = contextFiles,
                    inputFileContent = inputFileContent,
                    priorCode = priorCode,
                    api = api,
                    task = upwardTab
                )
                result.append("## Upward Abstraction (Generalizations)\n\n")
                result.append(upwardAnalysis)
                detailedOutputFile?.write("\n## Upward Abstraction (Generalizations)\n\n".toByteArray())
                detailedOutputFile?.write(upwardAnalysis.toByteArray())
                result.append("\n\n")
                upwardTab.add("✅ Upward analysis complete", additionalClasses = "text-success")
                upwardTab.complete()
            }

            if (direction == "down" || direction == "both") {
                log.info("Performing downward concretization analysis")
                val downwardTab = task.newTask()
                tabbedDisplay["Downward Analysis"] = downwardTab.placeholder
                val downwardAnalysis = analyzeDownward(
                    concept = concept,
                    levels = levels,
                    identifyPatterns = identifyPatterns,
                    contextFiles = contextFiles,
                    inputFileContent = inputFileContent,
                    priorCode = priorCode,
                    api = api,
                    task = downwardTab
                )
                result.append("## Downward Concretization (Specific Implementations)\n\n")
                result.append(downwardAnalysis)
                detailedOutputFile?.write("\n\n## Downward Concretization (Specific Implementations)\n\n".toByteArray())
                detailedOutputFile?.write(downwardAnalysis.toByteArray())
                result.append("\n\n")
                downwardTab.add("✅ Downward analysis complete", additionalClasses = "text-success")
                downwardTab.complete()
            }

            if (identifyPatterns) {
                log.info("Generating pattern summary and recommendations")
                val patternTab = task.newTask()
                tabbedDisplay["Pattern Analysis"] = patternTab.placeholder
                val patternSummary = generatePatternSummary(
                    concept = concept,
                    upwardAnalysis = if (direction == "up" || direction == "both") result.toString() else "",
                    downwardAnalysis = if (direction == "down" || direction == "both") result.toString() else "",
                    api = api,
                    task = patternTab
                )
                result.append("## Pattern Analysis & Recommendations\n\n")
                result.append(patternSummary)
                detailedOutputFile?.write("\n\n## Pattern Analysis & Recommendations\n\n".toByteArray())
                detailedOutputFile?.write(patternSummary.toByteArray())
                patternTab.add("✅ Pattern analysis complete", additionalClasses = "text-success")
                patternTab.complete()
            }

            // Update overview with completion status
            overviewTask.append("<hr/>")
            overviewTask.header("✅ Analysis Complete", 2)
            overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    """
          
          
          
          **Total Levels Analyzed:** $levels  
          **Directions Covered:** $direction  
          **Pattern Analysis:** ${if (identifyPatterns) "Included" else "Skipped"}
          
          See individual tabs for detailed results.
          """.trimIndent(), ui = task.ui
                )
            )
            overviewTask.complete()

            val duration = System.currentTimeMillis() - startTime
            log.info("Abstraction Ladder Analysis completed successfully - Concept: ${concept.truncateForDisplay(100)}, Levels: $levels")
            detailedOutputFile?.close()
            task.safeComplete(
                "Abstraction ladder analysis complete for '${concept.truncateForDisplay(100)}' with $levels levels in $direction direction(s) (${duration}ms)",
                log
            )
            val summaryMessage = generateSummaryMessage(
                task,
                duration,
                concept,
                levels,
                direction,
                "abstraction_ladder_analysis_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            )
            resultFn(summaryMessage)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("Error in abstraction ladder analysis after ${duration}ms", e)
            detailedOutputFile?.close()
            task.error(e)
            task.add(
                MarkdownUtil.renderMarkdown(
                    """
          ## ❌ Error During Analysis
          An error occurred while performing the abstraction ladder analysis:
          ```
          ${e.message}
          ```
          Please check the logs for more details.
          """.trimIndent(), ui = task.ui
                )
            )
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun analyzeUpward(
        concept: String,
        levels: Int,
        identifyPatterns: Boolean,
        contextFiles: String,
        inputFileContent: String,
        priorCode: String,
        api: ChatInterface,
        task: SessionTask
    ): String {
        val prompt = """
 Analyze the following concept by moving UP the abstraction ladder.
 Start with the concrete concept and identify increasingly general abstractions.

 ## Input Files:
 $inputFileContent
 
 ## Concrete Concept:
 $concept

 ## Context from Related Files:
 $contextFiles

 ## Previous Task Results:
 $priorCode

 ## Instructions:
 For each of $levels abstraction levels above the concrete concept:
 1. Identify the more general/abstract concept
 2. Explain what aspects are being generalized
 3. Provide examples of other concrete instances at the lower level
 ${if (identifyPatterns) "4. Identify relevant design patterns or architectural patterns\n5. Note refactoring opportunities" else ""}

 Format your response as:

 ### Level 0 (Concrete): [Original Concept]
- Description: [Brief description]
- Characteristics: [Key characteristics]

### Level 1: [First Abstraction]
- Generalization: [What is being abstracted]
- Examples: [Other concrete instances]
 ${if (identifyPatterns) "- Patterns: [Relevant patterns]\n- Refactoring: [Opportunities]" else ""}

 [Continue for all levels...]

 Generate the upward abstraction analysis now:
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = """
        You are an expert software architect analyzing code abstractions and design patterns.
        Your role is to identify generalizations and patterns as you move up the abstraction ladder.
      """.trimIndent(),
            model = api,
        )
        task.add("Analyzing upward abstractions...", additionalClasses = "text-info")

        return chatAgent.answer(listOf(prompt)).apply {
            task.add(this.renderMarkdown)
        }
    }

    private fun analyzeDownward(
        concept: String,
        levels: Int,
        identifyPatterns: Boolean,
        contextFiles: String,
        inputFileContent: String,
        priorCode: String,
        api: ChatInterface,
        task: SessionTask
    ): String {
        val prompt = """
 Analyze the following concept by moving DOWN the abstraction ladder.
 Start with the concept and identify increasingly specific/concrete implementations.

 ## Input Files:
 $inputFileContent
 
 ## Starting Concept:
 $concept

 ## Context from Related Files:
 $contextFiles

 ## Previous Task Results:
 $priorCode

 ## Instructions:
 For each of $levels concretization levels below the starting concept:
 1. Identify more specific/concrete implementations
 2. Explain what aspects are being specialized
 3. Provide concrete code examples or use cases
 ${if (identifyPatterns) "4. Identify implementation patterns or idioms\n5. Note code smells or anti-patterns to avoid" else ""}

 Format your response as:

 ### Level 0 (Starting): [Original Concept]
- Description: [Brief description]
- Characteristics: [Key characteristics]

### Level -1: [First Concretization]
- Specialization: [What is being made more specific]
- Examples: [Concrete implementations]
- Code: [Code snippets if applicable]
 ${if (identifyPatterns) "- Patterns: [Implementation patterns]\n- Anti-patterns: [Things to avoid]" else ""}

 [Continue for all levels...]

 Generate the downward concretization analysis now:
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = """
        You are an expert software architect analyzing code implementations and concrete patterns.
        Your role is to identify specific implementations as you move down the abstraction ladder.
      """.trimIndent(),
            model = api,
        )
        task.add("Analyzing downward concretizations...", additionalClasses = "text-info")

        return chatAgent.answer(listOf(prompt)).apply {
            task.add(this.renderMarkdown)
        }
    }

    private fun generatePatternSummary(
        concept: String, upwardAnalysis: String, downwardAnalysis: String, api: ChatInterface, task: SessionTask
    ): String {
        val prompt = """
 Based on the abstraction ladder analysis, provide a comprehensive pattern summary and recommendations.

 ## Original Concept:
 $concept

 ## Upward Analysis:
 $upwardAnalysis

 ## Downward Analysis:
 $downwardAnalysis

 ## Instructions:
 Synthesize the analysis and provide:

 1. **Design Patterns Identified**: List all relevant design patterns found at various abstraction levels
 2. **Architectural Insights**: High-level architectural patterns or principles
 3. **Refactoring Opportunities**: Specific recommendations for improving the code
 4. **Anti-patterns & Code Smells**: Issues to address
 5. **Best Practices**: Recommendations based on the abstraction analysis
 6. **Implementation Guidance**: Concrete steps for applying insights

 Format as a structured markdown report with clear sections and actionable recommendations.

 Generate the pattern summary now:
        """.trimIndent()

        val chatAgent = ChatAgent(
            prompt = """
        You are an expert software architect specializing in design patterns and code quality.
        Your role is to synthesize abstraction analysis into actionable recommendations.
      """.trimIndent(),
            model = api,
        )
        task.add("Generating pattern summary and recommendations...", additionalClasses = "text-info")
        return chatAgent.answer(listOf(prompt)).apply {
            task.add(this.renderMarkdown)
        }
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: emptyList()
        if (relatedFiles.isEmpty()) return "No related files provided."

        return relatedFiles.joinToString("\n\n") { pattern ->
            try {
                val file = root.resolve(pattern).toFile()
                if (file.exists() && file.isFile) {
                    "# $pattern\n\n```\n${file.readText()}\n```"
                } else {
                    "# $pattern\n\nFile not found or is a directory"
                }
            } catch (e: Exception) {
                log.warn("Error reading file: $pattern", e)
                "# $pattern\n\nError reading file: ${e.message}"
            }
        }
    }


    private fun initializeDetailedOutput(task: SessionTask): FileOutputStream? {
        return try {
            val (link, file) = Pair(
                task.linkTo("abstraction_ladder_analysis.md"),
                task.resolveUserFile("abstraction_ladder_analysis.md")
            )
            val outputStream = file?.outputStream()
            task.complete(
                "Writing detailed analysis to <a href='$link' target='_blank'>$link</a> " +
                        "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                        "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
            )
            log.info("Initialized detailed output file: $link")
            outputStream
        } catch (e: Exception) {
            log.error("Failed to initialize detailed output file", e)
            null
        }
    }

    private fun generateSummaryMessage(
        task: SessionTask,
        duration: Long,
        concept: String,
        levels: Int,
        direction: String,
        transcriptName: String
    ) = """
    Abstraction Ladder analysis complete for '$concept' with $levels levels in $direction direction(s).
    **Duration:** ${duration / 1000}s
    Detailed analysis: <a href='${task.linkTo(transcriptName)}' target='_blank'>View Full Report</a>
  """.trimIndent()


    companion object {
        private val log: Logger = LoggerFactory.getLogger(AbstractionLadderTask::class.java)
        val AbstractionLadder = TaskType(
          name = "AbstractionLadder",
          category = "Reasoning",
          taskClass = AbstractionLadderTask::class.java,
          executionConfigClass = AbstractionLadderTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Traverse abstraction levels to identify patterns and design insights",
          tooltipHtml = """
                        Analyzes concepts by moving up and down abstraction levels.
                        <ul>
                          <li>Move up to find generalizations and patterns</li>
                          <li>Move down to find specific implementations</li>
                          <li>Identify design patterns at each level</li>
                          <li>Discover refactoring opportunities</li>
                          <li>Analyze architectural patterns</li>
                          <li>Find code smells and anti-patterns</li>
                          <li>Generate actionable recommendations</li>
                        </ul>
                      """,
        )
    }
}