package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.nio.file.FileSystems
import java.nio.file.Path

class CausalInferenceTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: CausalInferenceTaskExecutionConfigData?
) : AbstractTask<CausalInferenceTask.CausalInferenceTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {
  protected val codeFiles = mutableMapOf<Path, String>()

  val maxOutputLength: Int = 20000

  class CausalInferenceTaskExecutionConfigData(
    @Description("The observed effect or outcome to explain")
    var observed_effect: String? = null,
    @Description("Potential causes to investigate")
    var potential_causes: List<String>? = null,
    @Description("Whether to build a causal graph")
    var build_causal_graph: Boolean = true,
    @Description("Whether to identify confounding factors")
    var identify_confounders: Boolean = true,
    @Description("Data sources for evidence (file patterns or paths)")
    var evidence_sources: List<String>? = null,
    @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    var related_files: List<String>? = null,
    @Description("A description of the task's purpose")
    task_description: String? = null,
    @Description("List of task IDs this task depends on")
    task_dependencies: List<String>? = null,
    @Description("The current state of the task")
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = CausalInference.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (observed_effect.isNullOrBlank()) {
        return "observed_effect must not be null or blank"
      }
      potential_causes?.let { causes ->
        if (causes.any { it.isBlank() }) {
          return "potential_causes must not contain blank entries"
        }
      }
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String = buildString {
    appendLine("CausalInference - Identify causal relationships and root causes")
    appendLine("  ** Specify the observed effect or outcome to explain")
    appendLine("  ** List potential causes to investigate")
    appendLine("  ** Optionally build a causal graph showing relationships")
    appendLine("  ** Optionally identify confounding factors")
    appendLine("  ** Provide evidence sources (logs, metrics, code files)")
    appendLine("  ** Optionally, list input files (supports glob patterns) to be examined")
    appendLine("  ** Useful for:")
    appendLine("     - Root cause analysis")
    appendLine("     - Debugging complex issues")
    appendLine("     - Understanding system behavior")
    appendLine("     - Distinguishing correlation from causation")
  }

  data class CausalAnalysisResult(
    @Description("Brief overview of key findings from the causal analysis")
    var summary: String = "",
    @Description("List of identified causal factors with their mechanisms and evidence")
    var causes: List<CausalFactor> = emptyList(),
    @Description("The fundamental root causes identified")
    var root_causes: List<String> = emptyList(),
    @Description("Description of the causal chain from root cause to observed effect")
    var causal_chain: String = "",
    @Description("Confounding variables that may create spurious correlations, null if not requested")
    var confounders: List<String>? = null,
    @Description("Recommended actions to address the root causes")
    var recommendations: List<String> = emptyList()
  )

  data class CausalFactor(
    @Description("Name or short label for this causal factor")
    var name: String = "",
    @Description("Explanation of how this cause produces the observed effect")
    var mechanism: String = "",
    @Description("Evidence supporting this causal relationship")
    var evidence: String = "",
    @Description("Strength of the causal link: one of 'strong', 'moderate', or 'weak'")
    var strength: String = "",
    @Description("Confidence level in this causal assessment")
    var confidence: String = ""
  )


  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val startTime = System.currentTimeMillis()
    val transcript = task.newUserFileStream(transcriptFile())
    try {
      task.ui.pool.submit {
        try {
          val observedEffect = executionConfig?.observed_effect
          if (observedEffect.isNullOrBlank()) {
            throw IllegalArgumentException("observed_effect must not be null or blank")
          }

          log.info("Starting CausalInference task for effect: $observedEffect")

          transcript?.write(buildString {
            appendLine("# Causal Inference Analysis")
            appendLine()
            appendLine("**Observed Effect:** $observedEffect")
            appendLine()
          }.toByteArray())

          val api = defaultSmart?.getChildClient(task) ?: throw IllegalStateException("No default chatter available")
          val fastApi = defaultFast?.getChildClient(task) ?: api

          val tabs = TabbedDisplay(task)
          val overviewTask = tabs.newTask("Overview")
          overviewTask.add("---\n\n## Causal Inference Analysis\n\n**Observed Effect:** $observedEffect".renderMarkdown())

          val overviewStatusBuffer = overviewTask.add("**Status:** 🔄 Gathering evidence...".renderMarkdown())

          val evidenceTask = tabs.newTask("Evidence Sources")
          val evidenceStatusBuffer = evidenceTask.add("## Evidence Sources\n\n🔄 Loading evidence...".renderMarkdown())

          val evidenceContext = gatherEvidence()
          val inputFileContext = getInputFileCode()

          if (verbose) transcript?.write(buildString {
            appendLine("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">")
            appendLine()
            appendLine("## Work Details")
            appendLine()
            appendLine("<details><summary>Evidence Context</summary>")
            appendLine()
            val truncatedEvidence = evidenceContext.take(maxOutputLength)
            appendLine(truncatedEvidence)
            if (evidenceContext.length > maxOutputLength) appendLine("\n... (truncated)")
            appendLine()
            appendLine("</details>")
            appendLine()
            if (inputFileContext != "No input files specified") {
              appendLine("<details><summary>Input File Context</summary>")
              appendLine()
              appendLine(inputFileContext.take(maxOutputLength))
              if (inputFileContext.length > maxOutputLength) appendLine("\n... (truncated)")
              appendLine()
              appendLine("</details>")
              appendLine()
            }
          }.toByteArray())

          evidenceStatusBuffer?.setLength(0)
          evidenceStatusBuffer?.append("## Evidence Sources\n\n✅ Evidence gathered successfully\n\n**Sources processed:** ${executionConfig?.evidence_sources?.size ?: 0}".renderMarkdown())
          evidenceTask.expandable(
            "Evidence Context",
            "```\n${evidenceContext.take(maxOutputLength)}\n```".renderMarkdown()
          )

          val priorContext = getPriorCode(agent.executionState)
          val messageContext = messages.joinToString("\n\n")
          val potentialCauses = executionConfig?.potential_causes ?: emptyList()
          val causesText = if (potentialCauses.isNotEmpty()) {
            "**Potential Causes to Investigate:**\n" + potentialCauses.joinToString("\n") { "- $it" }
          } else {
            "**Note:** No specific potential causes provided. Will identify causes from evidence."
          }

          overviewStatusBuffer?.setLength(0)
          overviewStatusBuffer?.append("$causesText\n\n**Status:** 🔄 Analyzing causal relationships...".renderMarkdown())
          overviewTask.update()
          val combinedEvidence = buildString {
            appendLine(evidenceContext)
            if (inputFileContext != "No input files specified") {
              appendLine()
              appendLine("## Input Files:")
              appendLine(inputFileContext)
            }
          }


          val prompt =
            buildAnalysisPrompt(observedEffect, potentialCauses, combinedEvidence, priorContext, messageContext)
          val analysisAgent = ParsedAgent(
            resultClass = CausalAnalysisResult::class.java,
            prompt = prompt,
            model = api,
            parsingModel = fastApi,
            deserializerRetries = 2
          )

          val analysisTask = tabs.newTask("Causal Analysis")
          analysisTask.add("## Causal Analysis\n\n🔄 Performing causal inference...".renderMarkdown())
          val analysisResult = analysisAgent.answer(listOf(prompt)).obj

          if (verbose) transcript?.write(buildString {
            appendLine("<details><summary>Raw Analysis JSON</summary>")
            appendLine()
            appendLine("```json")
            appendLine(analysisResult.toJson())
            appendLine("```")
            appendLine()
            appendLine("</details>")
            appendLine()
            appendLine("</div>")
            appendLine()
          }.toByteArray())

          val analysisMarkdown = buildString {
            appendLine("## Causal Analysis Results")
            appendLine()
            appendLine("✅ Analysis complete")
            appendLine()
            appendLine("### Summary")
            appendLine(analysisResult.summary)
            appendLine()
            appendLine("### Identified Causes")
            for (cause in analysisResult.causes) {
              appendLine("* **${cause.name}** (${cause.strength} strength)")
              appendLine("  * *Mechanism:* ${cause.mechanism}")
            }
            appendLine()
            appendLine("### Root Causes")
            for (rootCause in analysisResult.root_causes) {
              appendLine("* $rootCause")
            }
            appendLine()
            appendLine("### Causal Chain")
            appendLine(analysisResult.causal_chain)
            appendLine()
            if (!analysisResult.confounders.isNullOrEmpty()) {
              appendLine("### Confounding Factors")
              for (confounder in analysisResult.confounders!!) {
                appendLine("* $confounder")
              }
              appendLine()
            }
            appendLine("### Recommendations")
            for (recommendation in analysisResult.recommendations) {
              appendLine("* $recommendation")
            }
          }
          analysisTask.add(analysisMarkdown.renderMarkdown())
          analysisTask.complete()
          transcript?.write(buildString {
            appendLine("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">")
            appendLine()
            appendLine(analysisMarkdown)
            appendLine()
          }.toByteArray())


          overviewStatusBuffer?.setLength(0)
          overviewStatusBuffer?.append("$causesText\n\n**Status:** ✅ Analysis complete".renderMarkdown())
          overviewTask.update()

          if (executionConfig?.build_causal_graph == true) {
            generateCausalGraph(tabs, analysisResult, api, transcript)
          }

          val duration = System.currentTimeMillis() - startTime
          val summary = "Causal inference analysis completed for effect: $observedEffect"
          log.info("$summary (duration: ${duration}ms)")

          transcript?.write(buildString {
            appendLine()
            appendLine("---")
            appendLine("*Analysis completed in ${duration}ms*")
            appendLine()
            appendLine("</div>")
            appendLine()
          }.toByteArray())

          task.complete(summary)
          resultFn(buildString {
            appendLine("## Causal Inference Analysis Complete")
            appendLine()
            appendLine("* $summary")
            appendLine("* Detailed results saved to: `${transcriptFile()}`")
          })

        } catch (e: Exception) {
          task.error(e)
          log.error("CausalInference task failed", e)
          transcript?.write(buildString {
            appendLine()
            appendLine("## Error")
            appendLine()
            appendLine("<details><summary>Stack Trace</summary>")
            appendLine()
            appendLine("```")
            appendLine(e.stackTraceToString())
            appendLine("```")
            appendLine()
            appendLine("</details>")
          }.toByteArray())
          task.complete("Analysis failed: ${e.message}")
          resultFn("ERROR: Causal inference analysis failed - ${e.message}")
        } finally {
          transcript?.close()
        }
      }
    } catch (e: Exception) {
      log.error("Failed to submit CausalInference task to pool", e)
      task.error(e)
      transcript?.close()
    }
  }

  private fun generateCausalGraph(
    tabs: TabbedDisplay,
    analysisResult: CausalAnalysisResult,
    api: ChatInterface,
    transcript: java.io.OutputStream?
  ) {
    val graphTask = tabs.newTask("Causal Graph")
    val graphBuffer = graphTask.add("## Causal Graph\n\n🔄 Generating causal graph visualization...".renderMarkdown())

    val graphPrompt = buildString {
      appendLine("Based on the following causal analysis, create a Mermaid diagram showing the causal relationships.")
      appendLine()
      appendLine("Analysis:")
      appendLine(analysisResult.toJson())
      appendLine()
      appendLine("Use the following format:")
      appendLine("- Use `graph TD` for top-down flow")
      appendLine("- Show direct causal links with `-->`")
      appendLine("- Show correlations with `-.->` (dotted lines)")
      appendLine("- Label confounders clearly")
      appendLine("- Use descriptive node labels")
      appendLine()
      appendLine("Generate the Mermaid diagram now:")
    }

    val chatAgent = ChatAgent(prompt = graphPrompt, model = api)
    val graphResult = chatAgent.answer(listOf(graphPrompt))
    val mermaidCode = extractMermaidCode(graphResult ?: "")
    graphTask.complete()

    transcript?.write(buildString {
      appendLine("## Causal Graph")
      appendLine()
      appendLine("```mermaid")
      appendLine(mermaidCode)
      appendLine("```")
      appendLine()
    }.toByteArray())

    graphBuffer?.setLength(0)
    if (mermaidCode.isNotEmpty()) {
      graphBuffer?.append(buildString {
        appendLine("## Causal Graph")
        appendLine()
        appendLine("✅ Graph generated successfully")
        appendLine()
        appendLine("```mermaid")
        appendLine(mermaidCode)
        appendLine("```")
      }.renderMarkdown())
    } else {
      graphBuffer?.append(buildString {
        appendLine("## Causal Graph")
        appendLine()
        appendLine("⚠️ Failed to generate graph visualization")
        appendLine()
        appendLine("The analysis did not produce a valid Mermaid diagram.")
      }.renderMarkdown())
    }
    graphTask.update()
  }

  private fun buildAnalysisPrompt(
    observedEffect: String,
    potentialCauses: List<String>,
    evidenceContext: String,
    priorContext: String,
    messageContext: String
  ): String {
    val causesSection = if (potentialCauses.isNotEmpty()) buildString {
      appendLine("## Potential Causes to Investigate:")
      for (cause in potentialCauses) {
        appendLine("- $cause")
      }
    } else {
      "## Note: Identify potential causes from the evidence provided."
    }

    val confoundersSection = if (executionConfig?.identify_confounders == true) buildString {
      appendLine()
      appendLine("## Confounding Factors:")
      appendLine("Identify any confounding variables that might create spurious correlations.")
      appendLine("Explain how these confounders affect the causal interpretation.")
    } else {
      ""
    }

    return buildString {
      appendLine("You are an expert in causal inference and root cause analysis. Your task is to identify the true causal relationships behind an observed effect.")
      appendLine()
      appendLine("## Observed Effect:")
      appendLine(observedEffect)
      appendLine()
      appendLine(causesSection)
      appendLine()
      appendLine("## Evidence and Context:")
      appendLine(evidenceContext)
      appendLine()
      appendLine("## Previous Task Results:")
      appendLine(priorContext)
      appendLine()
      appendLine("## Analysis Instructions:")
      appendLine("1. **Distinguish Causation from Correlation**: Identify which relationships are truly causal vs merely correlated")
      appendLine("2. **Apply Causal Reasoning**: Use principles like:")
      appendLine("   - Temporal precedence (cause must precede effect)")
      appendLine("   - Mechanism (explain HOW the cause produces the effect)")
      appendLine("   - Counterfactual reasoning (what would happen without the cause?)")
      appendLine("   - Elimination of alternative explanations")
      appendLine("3. **Evaluate Each Potential Cause**: For each potential cause, assess:")
      appendLine("   - Strength of causal link")
      appendLine("   - Supporting evidence")
      appendLine("   - Alternative explanations")
      appendLine("   - Confidence level")
      appendLine("4. **Identify Root Causes**: Distinguish between:")
      appendLine("   - Root causes (fundamental sources)")
      appendLine("   - Intermediate causes (mediating factors)")
      appendLine("   - Proximate causes (immediate triggers)")
      appendLine("5. **Consider Causal Chains**: Map out sequences of causation")
      appendLine("6. **Assess Causal Strength**: Rate each causal relationship (strong/moderate/weak)")
      appendLine(confoundersSection)
      appendLine()
      appendLine("## Output Format:")
      appendLine("Provide a structured analysis with:")
      appendLine("1. **Summary**: Brief overview of key findings")
      appendLine("2. **Causal Analysis**: For each identified cause:")
      appendLine("   - Description of the causal mechanism")
      appendLine("   - Evidence supporting causation")
      appendLine("   - Strength of causal link")
      appendLine("   - Confidence level")
      appendLine("3. **Root Cause Identification**: The fundamental cause(s)")
      appendLine("4. **Causal Chain**: How causes lead to the observed effect")
      appendLine("5. **Confounders** (if requested): Variables that create spurious correlations")
      appendLine("6. **Recommendations**: Actions to address root causes")
      appendLine()
      appendLine("Generate the causal analysis now:")
    }
  }

  private fun getInputFileCode() = (executionConfig?.related_files ?: listOf())
    .flatMap { pattern: String ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isIgnored(it.toPath()) -> false
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
        val content = codeFiles[file.toPath()] ?: file.readText()
        "# $relativePath\n\n```\n$content\n```"
      } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
      }
    }.let { if (it.isBlank()) "No input files specified" else it }

  private fun gatherEvidence(): String {
    val evidenceSources = executionConfig?.evidence_sources ?: emptyList()
    val relatedFiles = executionConfig?.related_files ?: emptyList()
    val allSources = (evidenceSources + relatedFiles).distinct()

    if (allSources.isEmpty()) {
      return "No specific evidence sources provided."
    }
    val maxFileSize = 2000
    val maxTotalSize = 10000
    var totalSize = 0
    return allSources.flatMap { pattern ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      root.toFile().walkTopDown()
        .filter { file ->
          file.isFile && matcher.matches(root.relativize(file.toPath()))
        }
        .map { file ->
          val relativePath = root.relativize(file.toPath())
          try {
            if (totalSize >= maxTotalSize) {
              return@map "### $relativePath\n(Skipped - evidence limit reached)"
            }
            val content = file.readText()
            val truncated = content.take(maxFileSize)
            totalSize += truncated.length
            "### $relativePath\n```\n$truncated${if (content.length > maxFileSize) "\n... (truncated)" else ""}\n```"
          } catch (e: Exception) {
            log.warn("Error reading evidence file: $relativePath", e)
            "### $relativePath\n(Error reading file: ${e.message})"
          }
        }
        .toList()
    }.joinToString("\n\n")
  }

  private fun extractMermaidCode(response: String): String {
    val mermaidBlockRegex = "```mermaid\\s*([\\s\\S]*?)```".toRegex()
    val match = mermaidBlockRegex.find(response)
    return match?.groupValues?.get(1)?.trim() ?: ""
  }

  companion object {
      private val log: Logger = getLogger(CausalInferenceTask::class.java)

    @JvmStatic
    val CausalInference = TaskType(
      name = "CausalInference",
      category = "Reasoning",
      taskClass = CausalInferenceTask::class.java,
      executionConfigClass = CausalInferenceTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Identify causal relationships and root causes",
      tooltipHtml = buildString {
        appendLine("Performs causal inference analysis to identify true causal relationships.")
        appendLine("<ul>")
        appendLine("  <li>Distinguishes causation from correlation</li>")
        appendLine("  <li>Identifies root causes vs intermediate factors</li>")
        appendLine("  <li>Builds causal graphs showing relationships</li>")
        appendLine("  <li>Identifies confounding variables</li>")
        appendLine("  <li>Provides evidence-based causal reasoning</li>")
        appendLine("  <li>Useful for debugging and root cause analysis</li>")
        appendLine("</ul>")
      },
    )
  }
}