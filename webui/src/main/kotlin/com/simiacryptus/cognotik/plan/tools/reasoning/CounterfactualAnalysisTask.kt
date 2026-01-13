package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
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
        var actual_scenario: String? = null,
        @Description("Alternative conditions to explore (what-if scenarios)")
        var counterfactuals: List<String>? = null,
        @Description("Whether to compare outcomes across scenarios")
        var compare_outcomes: Boolean = true,
        @Description("Factors to hold constant across scenarios")
        var control_factors: List<String>? = null,
        @Description("Additional files for context (e.g., historical data, related analyses)")
        var related_files: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
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


        val transcript = task.transcript()










        task.ui.pool.submit {
            try {
                log.info("Starting CounterfactualAnalysis task.")

                val actualScenario = executionConfig?.actual_scenario
                val counterfactuals = executionConfig?.counterfactuals ?: emptyList()

                if (actualScenario.isNullOrBlank()) {
                    val err = "CONFIGURATION ERROR: No actual scenario specified"
                    log.error(err)
                    task.error(Exception(err))
                    resultFn(err)
                    return@submit
                }

                if (counterfactuals.isEmpty()) {
                    val err = "CONFIGURATION ERROR: No counterfactual scenarios specified"
                    log.error(err)
                    task.error(Exception(err))
                    resultFn(err)
                    return@submit
                }

                val toInput = { it: String -> messages + listOf(getInputFileCode(), it).filter { it.isNotBlank() } }
                transcript?.write("# Counterfactual Analysis Transcript\n\n".toByteArray())
                val api = defaultSmart ?: return@submit

                val tabs = TabbedDisplay(task)
                val overviewTask = tabs.newTask("Overview")

                overviewTask.add(
                    """
                    |## Counterfactual Analysis
                    |
                    |**Actual Scenario:** ${actualScenario.truncateForDisplay(maxDescriptionLength)}
                    |
                    |**Counterfactuals:** ${counterfactuals.size}
                    |
                    |**Status:** 🔄 Starting analysis...
                    """.trimMargin().renderMarkdown()
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

                val contextFiles = getContextFiles()
                val priorCode = getPriorCode(agent.executionState)
                val actualTab = tabs.newTask("Actual Scenario")

                // Analyze actual scenario
                val actualAnalysis = analyzeScenario(
                    "Actual Scenario",
                    actualScenario,
                    contextFiles,
                    priorCode,
                    api,
                    actualTab,
                    toInput,
                    transcript
                )
                transcript?.write("\n## Actual Scenario Analysis\n\n".toByteArray())
                transcript?.write("**Scenario:** $actualScenario\n\n".toByteArray())
                transcript?.write("**Analysis:**\n\n$actualAnalysis\n\n".toByteArray())
                val counterfactualTab = tabs.newTask("Counterfactuals")
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
                        counterfactualTab,
                        toInput,
                        transcript
                    )
                    transcript?.write("**Analysis:**\n\n$analysis\n\n".toByteArray())
                    analysis
                }

                // Compare outcomes if requested
                val comparisonAnalysis = if (executionConfig?.compare_outcomes == true) {
                    val comparisonTab = tabs.newTask("Comparison")
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
                        comparisonTab,
                        toInput = toInput,
                        transcript = transcript
                    )
                    transcript?.write(comparison.toByteArray())
                    comparison
                } else {
                    ""
                }

                val fullReport = buildString {
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

                val (link, file) = task.createFile("analysis_results.md")
                file?.writeText(fullReport)
                task.complete("Analysis complete. Full results written to <a href='$link' target='_blank'>$link</a>")

                val summaryMessage = """
                    ## Counterfactual Analysis Complete
                    * **Scenario:** `${actualScenario.truncateForDisplay(50)}`
                    * **Alternatives Analyzed:** ${counterfactuals.size}
                    * **Report:** [Download Analysis Results]($link)
                """.trimIndent()
                resultFn(summaryMessage)
            } catch (e: Exception) {
                task.error(e)
                log.error("Error in CounterfactualAnalysisTask", e)
                transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
            } finally {
                transcript?.close()
            }
        }
    }

    private fun analyzeScenario(
        scenarioName: String,
        scenario: String,
        contextFiles: String,
        priorCode: String,
        api: ChatInterface,
        tab: SessionTask,
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
        """.trimIndent()
        transcript?.write("\n<details><summary>Prompt for $scenarioName</summary>\n\n```\n$prompt\n```\n</details>\n\n".toByteArray())


        val chatAgent = ChatAgent(
            prompt = prompt,
            model = api,
        )

        var result: String? = chatAgent.answer(listOf("Provide a comprehensive analysis"))
        transcript?.write("<details><summary>Response for $scenarioName</summary>\n\n${result ?: "(No response)"}\n\n</details>\n\n".toByteArray())
        tab.add("## $scenarioName\n\n${result ?: "No analysis generated."}".renderMarkdown)
        tab.update()
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
        tab: SessionTask,
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

        """.trimIndent()
        transcript?.write("\n<details><summary>Comparison Prompt</summary>\n\n```\n$prompt\n```\n</details>\n\n".toByteArray())


        val chatAgent = ChatAgent(
            prompt = prompt,
            model = api,
        )

        var result: String? = chatAgent.answer(listOf("Provide a comprehensive comparative analysis"))
        transcript?.write("<details><summary>Comparison Response</summary>\n\n${result ?: "(No response)"}\n\n</details>\n\n".toByteArray())
        tab.add("## Comparative Analysis\n\n${result ?: "No comparison generated."}".renderMarkdown)
        tab.update()
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
          name = "CounterfactualAnalysis",
          category = "Reasoning",
          taskClass = CounterfactualAnalysisTask::class.java,
          executionConfigClass = CounterfactualAnalysisTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Explore what-if scenarios to understand causal relationships and decision impacts",
          tooltipHtml = """
                        Performs counterfactual analysis to explore alternative scenarios and outcomes.
                        <ul>
                          <li>Analyzes actual scenarios and alternative conditions</li>
                          <li>Compares outcomes across different scenarios</li>
                          <li>Identifies causal relationships and key factors</li>
                          <li>Supports controlled comparison with constant factors</li>
                          <li>Provides insights for risk analysis and decision validation</li>
                          <li>Useful for retrospective analysis and strategic planning</li>
                        </ul>
                      """,
        )
    }
}