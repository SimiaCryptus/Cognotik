package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
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
import java.io.File
import java.io.FileOutputStream

class CounterfactualAnalysisTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: CounterfactualAnalysisTaskExecutionConfigData?
) : AbstractTask<CounterfactualAnalysisTask.CounterfactualAnalysisTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxDescriptionLength = 200
    protected val codeFiles = mutableMapOf<java.nio.file.Path, String>()

    class CounterfactualAnalysisTaskExecutionConfigData(
        @Description("The actual scenario or decision to analyze")
        val actual_scenario: String? = null,
        @Description("Alternative conditions to explore (what-if scenarios)")
        val counterfactuals: List<String>? = null,
        @Description("Whether to compare outcomes across scenarios")
        val compare_outcomes: Boolean = true,
        @Description("Factors to hold constant across scenarios")
        val control_factors: List<String>? = null,
        @Description("Additional files for context (e.g., historical data, related analyses)")
        val related_files: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("Detailed description of the analysis objectives")
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = CounterfactualAnalysis.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (actual_scenario.isNullOrBlank()) {
                return "actual_scenario must not be null or blank"
            }
            if (counterfactuals.isNullOrEmpty()) {
                return "counterfactuals must contain at least one scenario"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
CounterfactualAnalysis - Explore "what-if" scenarios to understand causal relationships and decision impacts
  ** Specify the actual scenario or decision that occurred
  ** Provide a list of alternative conditions to explore (counterfactuals)
  ** Optionally specify factors to hold constant across scenarios for controlled comparison
  ** Enable outcome comparison to see differences between scenarios
  ** Useful for:
     - Risk analysis and mitigation planning
     - Decision validation and retrospective analysis
     - Understanding causal relationships
     - Exploring alternative strategies
     - Impact assessment of different choices
  ** Related files can include historical data, previous analyses, or context documents
  ** Output includes detailed analysis of each scenario and comparative insights
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
        log.info("Starting CounterfactualAnalysis task for scenario: ${executionConfig?.actual_scenario}")

        val actualScenario = executionConfig?.actual_scenario
        val counterfactuals = executionConfig?.counterfactuals ?: emptyList()

        if (actualScenario.isNullOrBlank()) {
            log.error("No actual scenario specified")
            task.safeComplete("CONFIGURATION ERROR: No actual scenario specified", log)
            resultFn("CONFIGURATION ERROR: No actual scenario specified")
            return
        }

        if (counterfactuals.isEmpty()) {
            log.error("No counterfactual scenarios specified")
            task.safeComplete("CONFIGURATION ERROR: No counterfactual scenarios specified", log)
            resultFn("CONFIGURATION ERROR: No counterfactual scenarios specified")
            return
        }

        val toInput = { it: String -> messages + listOf(getInputFileCode(), it).filter { it.isNotBlank() } }
        val transcript = task.transcript()
        transcript?.write("# Counterfactual Analysis Transcript\n\n".toByteArray())
        val api = defaultChatter ?: return

        try {
            val tabs = TabbedDisplay(task)
            val overviewTask = task.ui.newTask(false)
            tabs["Overview"] = overviewTask.placeholder

            overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    """
          |## Counterfactual Analysis
          |
          |**Actual Scenario:** ${actualScenario.truncateForDisplay(maxDescriptionLength)}
          |
          |**Counterfactuals:** ${counterfactuals.size}
          |
          |**Status:** 🔄 Starting analysis...
          """.trimMargin(),
                    ui = task.ui
                )
            )
            transcript?.write(
                """
        |## Counterfactual Analysis
        |
        |**Actual Scenario:** ${actualScenario.truncateForDisplay(maxDescriptionLength)}
        |
        |**Counterfactuals:** ${counterfactuals.size}
        |
        |**Status:** 🔄 Starting analysis...
        |
        """.trimMargin().toByteArray()
            )
        } catch (e: Exception) {
            log.warn("Failed to create overview tab", e)
        }

        val contextFiles = getContextFiles()
        val priorCode = getPriorCode(agent.executionState)

        // Analyze actual scenario
        val actualAnalysis = analyzeScenario(
            "Actual Scenario",
            actualScenario,
            contextFiles,
            priorCode,
            api,
            task,
            toInput,
            transcript
        )
        transcript?.write("\n## Actual Scenario Analysis\n\n".toByteArray())
        transcript?.write("**Scenario:** $actualScenario\n\n".toByteArray())
        transcript?.write("**Analysis:**\n\n$actualAnalysis\n\n".toByteArray())
        // Analyze counterfactual scenarios
        val counterfactualAnalyses = counterfactuals.mapIndexed { index, counterfactual ->
            transcript?.write("\n## Counterfactual Scenario ${index + 1}\n\n".toByteArray())
            transcript?.write("**Scenario:** $counterfactual\n\n".toByteArray())
            val analysis = analyzeScenario(
                "Counterfactual ${index + 1}",
                counterfactual,
                contextFiles,
                priorCode,
                api,
                task,
                toInput,
                transcript
            )
            transcript?.write("**Analysis:**\n\n$analysis\n\n".toByteArray())
            analysis
        }
        // Compare outcomes if requested
        val comparisonAnalysis = if (executionConfig?.compare_outcomes == true) {
            transcript?.write("\n## Comparative Analysis\n\n".toByteArray())
            val comparison = compareScenarios(
                actualScenario = actualScenario,
                actualAnalysisTokens = actualAnalysis.split("\\s+"),
                counterfactuals = counterfactuals,
                counterfactualAnalyses = counterfactualAnalyses,
                controlFactors = executionConfig.control_factors,
                contextFiles = contextFiles,
                priorCode = priorCode,
                api = api,
                task = task,
                toInput = toInput,
                transcript = transcript
            )
            transcript?.write(comparison.toByteArray())
            comparison
        } else {
            ""
        }

        buildString {
            appendLine("# Counterfactual Analysis Results")
            appendLine()
            appendLine("## Actual Scenario")
            appendLine(actualScenario)
            appendLine()
            appendLine("### Analysis")
            appendLine(actualAnalysis)
            appendLine()

            counterfactuals.forEachIndexed { index, counterfactual ->
                appendLine("## Counterfactual Scenario ${index + 1}")
                appendLine(counterfactual)
                appendLine()
                appendLine("### Analysis")
                appendLine(counterfactualAnalyses[index])
                appendLine()
            }

            if (comparisonAnalysis.isNotBlank()) {
                appendLine("## Comparative Analysis")
                appendLine(comparisonAnalysis)
            }
        }
        transcript?.write("\n---\n\n**Analysis Complete**\n".toByteArray())
        transcript?.close()

        val (link, _) = task.createFile("analysis_results.md")
        task.complete("Analysis complete. Full results written to <a href='$link' target='_blank'>$link</a>")

        val summaryMessage =
            "Counterfactual analysis completed in ${(System.currentTimeMillis() - startTime) / 1000}s. Results: $actualScenario with ${counterfactuals.size} counterfactual scenarios analyzed."
        task.safeComplete("Analysis complete", log)
        resultFn(summaryMessage)
    }

    private fun analyzeScenario(
        scenarioName: String,
        scenario: String,
        contextFiles: String,
        priorCode: String,
        api: ChatInterface,
        task: SessionTask,
        toInput: (String) -> List<String>,
        transcript: FileOutputStream?
    ): String {
        val prompt = """
 Analyze the following scenario in detail:

## Scenario: $scenarioName
$scenario

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Control Factors:
${executionConfig?.control_factors?.joinToString("\n") { "- $it" } ?: "None specified"}

## Instructions:
1. Describe the key elements and conditions of this scenario
2. Identify the main actors, decisions, and constraints
3. Analyze potential outcomes and their likelihood
4. Identify risks, opportunities, and trade-offs
5. Consider both short-term and long-term implications
6. Highlight any assumptions or uncertainties
7. Provide insights on causal relationships

 Provide a comprehensive analysis:
        """.trimIndent()
        transcript?.write("\n### Prompt for $scenarioName\n\n".toByteArray())
        transcript?.write("```\n$prompt\n```\n\n".toByteArray())


        val chatAgent = ChatAgent(
            prompt = promptSegment(),
            model = api,
        )

        var result: String? = chatAgent.answer(toInput(prompt))
        transcript?.write("### Response for $scenarioName\n\n".toByteArray())
        transcript?.write("${result ?: "(No response)"}\n\n".toByteArray())
        return result ?: ""
    }

    private fun compareScenarios(
        actualScenario: String,
        actualAnalysisTokens: List<String>,
        counterfactuals: List<String>,
        counterfactualAnalyses: List<String>,
        controlFactors: List<String>?,
        contextFiles: String,
        priorCode: String,
        api: ChatInterface,
        task: SessionTask,
        toInput: (String) -> List<String>,
        transcript: FileOutputStream?
    ): String {
        val scenarioComparisons = counterfactuals.zip(counterfactualAnalyses)
            .mapIndexed { index, (counterfactual, analysis) ->
                """
## Counterfactual ${index + 1}
**Scenario:** $counterfactual
**Analysis:** $analysis
                """.trimIndent()
            }.joinToString("\n\n")

        val prompt = """
Compare the following scenarios and provide insights on their differences:

## Actual Scenario
**Description:** $actualScenario
**Analysis:** ${actualAnalysisTokens.joinToString(" ")}

$scenarioComparisons

## Control Factors (held constant):
${controlFactors?.joinToString("\n") { "- $it" } ?: "None specified"}

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Instructions:
1. Compare outcomes across all scenarios
2. Identify key differences and their causes
3. Assess which factors had the most impact
4. Evaluate risks and benefits of each alternative
5. Determine which scenario(s) would have been preferable and why
6. Identify lessons learned and actionable insights
7. Highlight any surprising or counterintuitive findings
8. Provide recommendations based on the analysis

 Provide a comprehensive comparative analysis:
        """.trimIndent()
        transcript?.write("\n### Comparison Prompt\n\n".toByteArray())
        transcript?.write("```\n$prompt\n```\n\n".toByteArray())


        val chatAgent = ChatAgent(
            prompt = promptSegment(),
            model = api,
        )

        var result: String? = chatAgent.answer(toInput(prompt))
        transcript?.write("### Comparison Response\n\n".toByteArray())
        transcript?.write("${result ?: "(No response)"}\n\n".toByteArray())
        return result ?: ""
    }

    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: emptyList()
        if (relatedFiles.isEmpty()) return "No related files provided"

        return relatedFiles.joinToString("\n\n") { pattern ->
            try {
                val file = root.resolve(pattern).toFile()
                if (file.exists() && file.isFile) {
                    "# ${file.name}\n\n```\n${file.readText()}\n```"
                } else {
                    "# $pattern\n(File not found)"
                }
            } catch (e: Exception) {
                log.warn("Error reading file: $pattern", e)
                "# $pattern\n(Error reading file: ${e.message})"
            }
        }
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
            (com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk(root.toFile()) {
                when {
                    com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
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
                val content = if (!isTextFile(file)) {
                    extractDocumentContent(file)
                } else {
                    codeFiles[file.toPath()] ?: file.readText()
                }
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }

    private fun isTextFile(file: File): Boolean {
        val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
            "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
        )
        return textExtensions.contains(file.extension.lowercase())
    }

    private fun extractDocumentContent(file: File) = try {
        file.readText()
    } catch (e: Exception) {
        log.warn("Failed to extract content from ${file.name}", e)
        "Error reading file: ${e.message}"
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(CounterfactualAnalysisTask::class.java)
        val CounterfactualAnalysis = TaskType(
            "CounterfactualAnalysis",
            CounterfactualAnalysisTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Explore what-if scenarios to understand causal relationships and decision impacts",
            """
              Performs counterfactual analysis to explore alternative scenarios and outcomes.
              <ul>
                <li>Analyzes actual scenarios and alternative conditions</li>
                <li>Compares outcomes across different scenarios</li>
                <li>Identifies causal relationships and key factors</li>
                <li>Supports controlled comparison with constant factors</li>
                <li>Provides insights for risk analysis and decision validation</li>
                <li>Useful for retrospective analysis and strategic planning</li>
              </ul>
            """
        )
    }
}


