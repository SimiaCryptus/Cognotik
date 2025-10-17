package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.Logger

class AbstractionLadderTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: AbstractionLadderTaskExecutionConfigData?
) : AbstractTask<AbstractionLadderTask.AbstractionLadderTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class AbstractionLadderTaskExecutionConfigData(
        @Description("The concrete concept, problem, or code pattern to analyze")
        val concrete_concept: String? = null,
        @Description("Direction to traverse: 'up' for abstraction (generalizations), 'down' for concretization (specific implementations), 'both' for bidirectional analysis")
        val direction: String = "both",
        @Description("Number of abstraction levels to traverse in each direction (1-5 recommended)")
        val levels: Int = 3,
        @Description("Whether to identify design patterns, anti-patterns, and refactoring opportunities at each level")
        val identify_patterns: Boolean = true,
        @Description("Additional files for context (e.g., existing code, related implementations)")
        val related_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = AbstractionLadder.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

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
        val concept = executionConfig?.concrete_concept
        if (concept.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No concrete concept specified")
            return
        }

        val direction = executionConfig.direction.lowercase()
        if (direction !in listOf("up", "down", "both")) {
            resultFn("CONFIGURATION ERROR: Direction must be 'up', 'down', or 'both'")
            return
        }

        val levels = executionConfig.levels.coerceIn(1, 5)
        val identifyPatterns = executionConfig.identify_patterns

        val newTask = task.ui.newTask(false)
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(
            MarkdownUtil.renderMarkdown(
                "## Abstraction Ladder Analysis: `$concept`\n\n" +
                        "Direction: **$direction** | Levels: **$levels** | Pattern Analysis: **${if (identifyPatterns) "Enabled" else "Disabled"}**",
                ui = ui
            )
        )

        val contextFiles = getContextFiles()
        val priorCode = getPriorCode(agent.executionState)

        val result = StringBuilder()

        try {
            if (direction == "up" || direction == "both") {
                val upwardAnalysis = analyzeUpward(
                    concept = concept,
                    levels = levels,
                    identifyPatterns = identifyPatterns,
                    contextFiles = contextFiles,
                    priorCode = priorCode,
                    api = api,
                    newTask = newTask,
                    ui = ui
                )
                result.append("## Upward Abstraction (Generalizations)\n\n")
                result.append(upwardAnalysis)
                result.append("\n\n")
            }

            if (direction == "down" || direction == "both") {
                val downwardAnalysis = analyzeDownward(
                    concept = concept,
                    levels = levels,
                    identifyPatterns = identifyPatterns,
                    contextFiles = contextFiles,
                    priorCode = priorCode,
                    api = api,
                    newTask = newTask,
                    ui = ui
                )
                result.append("## Downward Concretization (Specific Implementations)\n\n")
                result.append(downwardAnalysis)
                result.append("\n\n")
            }

            if (identifyPatterns) {
                val patternSummary = generatePatternSummary(
                    concept = concept,
                    upwardAnalysis = if (direction == "up" || direction == "both") result.toString() else "",
                    downwardAnalysis = if (direction == "down" || direction == "both") result.toString() else "",
                    api = api,
                    newTask = newTask,
                    ui = ui
                )
                result.append("## Pattern Analysis & Recommendations\n\n")
                result.append(patternSummary)
            }

            newTask.add(MarkdownUtil.renderMarkdown(result.toString(), ui = ui))
            newTask.complete()
            resultFn(result.toString())

        } catch (e: Exception) {
            log.error("Error in abstraction ladder analysis", e)
            newTask.error(e)
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun analyzeUpward(
        concept: String,
        levels: Int,
        identifyPatterns: Boolean,
        contextFiles: String,
        priorCode: String,
        api: ChatInterface,
        newTask: SessionTask,
        ui: SocketManager
    ): String {
        val prompt = """
 Analyze the following concept by moving UP the abstraction ladder.
 Start with the concrete concept and identify increasingly general abstractions.

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
            prompt = promptSegment(),
            model = api,
        )

        return Retryable(newTask, newTask.ui) { sb ->
            sb.append(chatAgent.answer(listOf(prompt)))
            sb.toString()
        }.toString()
    }

    private fun analyzeDownward(
        concept: String,
        levels: Int,
        identifyPatterns: Boolean,
        contextFiles: String,
        priorCode: String,
        api: ChatInterface,
        newTask: SessionTask,
        ui: SocketManager
    ): String {
        val prompt = """
 Analyze the following concept by moving DOWN the abstraction ladder.
 Start with the concept and identify increasingly specific/concrete implementations.

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
            prompt = promptSegment(),
            model = api,
        )

        return Retryable(newTask, newTask.ui) { sb ->
            sb.append(chatAgent.answer(listOf(prompt)))
            sb.toString()
        }.toString()
    }

    private fun generatePatternSummary(
        concept: String,
        upwardAnalysis: String,
        downwardAnalysis: String,
        api: ChatInterface,
        newTask: SessionTask,
        ui: SocketManager
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
            prompt = promptSegment(),
            model = api,
        )
        return Retryable(newTask, newTask.ui) { sb ->
            sb.append(chatAgent.answer(listOf(prompt)))
            sb.toString()
        }.toString()
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

    companion object {
        private val log: Logger = LoggerFactory.getLogger(AbstractionLadderTask::class.java)
        val AbstractionLadder = TaskType(
            "AbstractionLadder",
            AbstractionLadderTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Traverse abstraction levels to identify patterns and design insights",
            """
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
            """
        )
    }
}