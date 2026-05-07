package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
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
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    var related_files: List<String>? = null,
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

  override fun promptSegment(): String = buildString {
    appendLine("CounterfactualAnalysis - Explore \"what-if\" scenarios to understand causal relationships and decision impacts")
    appendLine("  ** Specify the actual scenario or decision that occurred")
    appendLine("  ** Provide a list of alternative conditions to explore (counterfactuals)")
    appendLine("  ** Optionally specify factors to hold constant across scenarios for controlled comparison")
    appendLine("  ** Enable outcome comparison to see differences between scenarios")
    appendLine("  ** Useful for:")
    appendLine("     - Risk analysis and mitigation planning")
    appendLine("     - Decision validation and retrospective analysis")
    appendLine("     - Understanding causal relationships")
    appendLine("     - Exploring alternative strategies")
    appendLine("     - Impact assessment of different choices")
    appendLine("  ** Related files can include historical data, previous analyses, or context documents")
    appendLine("  ** Output includes detailed analysis of each scenario and comparative insights")
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {


    val transcript = task.newUserFileStream(transcriptFile())










    task.ui.pool.submit {
      try {
        log.info("Starting CounterfactualAnalysis task.")

        val actualScenario = executionConfig?.actual_scenario
        val counterfactuals = executionConfig?.counterfactuals ?: emptyList()

        if (actualScenario.isNullOrBlank()) {
          val err = "CONFIGURATION ERROR: No actual scenario specified"
          log.error(err)
          task.error(Exception(err))
          transcript?.write("\n## Error\n\n$err\n".toByteArray())
          resultFn(err)
          return@submit
        }

        if (counterfactuals.isEmpty()) {
          val err = "CONFIGURATION ERROR: No counterfactual scenarios specified"
          log.error(err)
          task.error(Exception(err))
          transcript?.write("\n## Error\n\n$err\n".toByteArray())
          resultFn(err)
          return@submit
        }

        transcript?.write("# Counterfactual Analysis\n\n".toByteArray())
        val api = (defaultSmart ?: return@submit).getChildClient(task)

        val tabs = TabbedDisplay(task)
        val overviewTask = tabs.newTask("Overview")

        val overviewText = buildString {
          appendLine("## Counterfactual Analysis")
          appendLine()
          appendLine("**Actual Scenario:** ${actualScenario.truncateForDisplay(maxDescriptionLength)}")
          appendLine()
          appendLine("**Counterfactuals:** ${counterfactuals.size}")
          appendLine()
          appendLine("**Status:** \uD83D\uDD04 Starting analysis...")
        }
        overviewTask.add(overviewText.renderMarkdown())
        transcript?.write("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
        transcript?.write("$overviewText\n\n".toByteArray())

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
            transcript
          )
          transcript?.write("**Analysis:**\n\n$analysis\n\n".toByteArray())
          analysis
        }

        // Compare outcomes if requested
        val comparisonAnalysis = if (executionConfig?.compare_outcomes == true) {
          val comparisonTab = tabs.newTask("Comparison")
          transcript?.write("\n## Comparative Analysis\n\n".toByteArray())
          val executionConfig = this.executionConfig ?: return@submit
          val comparison = compareScenarios(
            actualScenario = actualScenario,
            actualAnalysisTokens = actualAnalysis.split("\\s+"),
            counterfactuals = counterfactuals,
            counterfactualAnalyses = counterfactualAnalyses,
            controlFactors = executionConfig.control_factors,
            contextFiles = contextFiles,
            priorCode = priorCode,
            api = api,
            tab = comparisonTab,
            transcript = transcript
          )
          transcript?.write(comparison.toByteArray())
          comparison
        } else {
          ""
        }
        transcript?.write("\n</div>\n\n".toByteArray())
        transcript?.write("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())

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
        transcript?.write(fullReport.toByteArray())
        transcript?.write("\n</div>\n\n".toByteArray())
        transcript?.write("\n---\n\n**Analysis Complete**\n".toByteArray())

        task.complete("Analysis complete. Full results written to ${transcriptFile()}".renderMarkdown())

        val summaryMessage = buildString {
          appendLine("## Counterfactual Analysis Complete")
          appendLine("* **Scenario:** `${actualScenario.truncateForDisplay(50)}`")
          appendLine("* **Alternatives Analyzed:** ${counterfactuals.size}")
          appendLine("* **Report:** ${transcriptFile()}")
        }
        resultFn(summaryMessage)
      } catch (e: Exception) {
        task.error(e)
        log.error("Error in CounterfactualAnalysisTask", e)
        transcript?.write("\n## Error\n\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
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
    transcript: FileOutputStream?
  ): String {


    val controlFactorsText = executionConfig?.control_factors?.joinToString("\n") { "- $it" } ?: "None specified"
    val prompt = buildString {
      appendLine("Analyze the following scenario in detail:")
      appendLine()
      appendLine("## Scenario: $scenarioName")
      appendLine(scenario)
      appendLine()
      appendLine("## Context from Related Files:")
      appendLine(contextFiles)
      appendLine()
      appendLine("## Previous Task Results:")
      appendLine(priorCode)
      appendLine()
      appendLine("## Control Factors:")
      appendLine(controlFactorsText)
      appendLine()
      appendLine("## Instructions:")
      appendLine("1. Describe the key elements and conditions of this scenario")
      appendLine("2. Identify the main actors, decisions, and constraints")
      appendLine("3. Analyze potential outcomes and their likelihood")
      appendLine("4. Identify risks, opportunities, and trade-offs")
      appendLine("5. Consider both short-term and long-term implications")
      appendLine("6. Highlight any assumptions or uncertainties")
      appendLine("7. Provide insights on causal relationships")
    }
    transcript?.write("\n<details><summary>Prompt for $scenarioName</summary>\n\n```\n$prompt\n```\n</details>\n\n".toByteArray())


    val chatAgent = ChatAgent(
      prompt = prompt,
      model = api,
    )

    val result: String? = chatAgent.answer(listOf("Provide a comprehensive analysis"))
    transcript?.write("<details><summary>Response for $scenarioName</summary>\n\n${result ?: "(No response)"}\n\n</details>\n\n".toByteArray())
    tab.add("## $scenarioName\n\n${result ?: "No analysis generated."}".renderMarkdown(true))
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
    transcript: FileOutputStream?
  ): String {
    val scenarioComparisons = counterfactuals.zip(counterfactualAnalyses)
      .mapIndexed { index, (counterfactual, analysis) ->
        buildString {
          appendLine("## Counterfactual ${index + 1}")
          appendLine("**Scenario:** $counterfactual")
          appendLine("**Analysis:** $analysis")
        }
      }.joinToString("\n\n")


    val controlFactorsText = controlFactors?.joinToString("\n") { "- $it" } ?: "None specified"
    val prompt = buildString {
      appendLine("Compare the following scenarios and provide insights on their differences:")
      appendLine()
      appendLine("## Actual Scenario")
      appendLine("**Description:** $actualScenario")
      appendLine("**Analysis:** ${actualAnalysisTokens.joinToString(" ")}")
      appendLine()
      appendLine(scenarioComparisons)
      appendLine()
      appendLine("## Control Factors (held constant):")
      appendLine(controlFactorsText)
      appendLine()
      appendLine("## Context from Related Files:")
      appendLine(contextFiles)
      appendLine()
      appendLine("## Previous Task Results:")
      appendLine(priorCode)
      appendLine()
      appendLine("## Instructions:")
      appendLine("1. Compare outcomes across all scenarios")
      appendLine("2. Identify key differences and their causes")
      appendLine("3. Assess which factors had the most impact")
      appendLine("4. Evaluate risks and benefits of each alternative")
      appendLine("5. Determine which scenario(s) would have been preferable and why")
      appendLine("6. Identify lessons learned and actionable insights")
      appendLine("7. Highlight any surprising or counterintuitive findings")
      appendLine("8. Provide recommendations based on the analysis")
    }
    transcript?.write("\n<details><summary>Comparison Prompt</summary>\n\n```\n$prompt\n```\n</details>\n\n".toByteArray())


    val chatAgent = ChatAgent(
      prompt = prompt,
      model = api,
    )

    val result: String? = chatAgent.answer(listOf("Provide a comprehensive comparative analysis"))
    transcript?.write("<details><summary>Comparison Response</summary>\n\n${result ?: "(No response)"}\n\n</details>\n\n".toByteArray())
    tab.add("## Comparative Analysis\n\n${result ?: "No comparison generated."}".renderMarkdown(true))
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

  private fun isTextFile(file: java.io.File): Boolean {
    val textExtensions = setOf(
      "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp", "h", "hpp",
      "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
    )
    return textExtensions.contains(file.extension.lowercase())
  }

  private fun extractDocumentContent(file: java.io.File) = try {
    file.readText()
  } catch (e: Exception) {
    log.warn("Failed to extract content from ${file.name}", e)
    "Error reading file: ${e.message}"
  }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(CounterfactualAnalysisTask::class.java)

    @JvmStatic
    val CounterfactualAnalysis = TaskType(
      name = "CounterfactualAnalysis",
      category = "Reasoning",
      taskClass = CounterfactualAnalysisTask::class.java,
      executionConfigClass = CounterfactualAnalysisTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Explore what-if scenarios to understand causal relationships and decision impacts",
      tooltipHtml = "Performs counterfactual analysis to explore alternative scenarios and outcomes." +
          "<ul>" +
          "<li>Analyzes actual scenarios and alternative conditions</li>" +
          "<li>Compares outcomes across different scenarios</li>" +
          "<li>Identifies causal relationships and key factors</li>" +
          "<li>Supports controlled comparison with constant factors</li>" +
          "<li>Provides insights for risk analysis and decision validation</li>" +
          "<li>Useful for retrospective analysis and strategic planning</li>" +
          "</ul>",
    )
  }
}