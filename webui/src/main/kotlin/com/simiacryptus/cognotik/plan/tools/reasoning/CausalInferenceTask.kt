package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems

class CausalInferenceTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: CausalInferenceTaskExecutionConfigData?
) : AbstractTask<CausalInferenceTask.CausalInferenceTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  val maxOutputLength: Int = 20000

  class CausalInferenceTaskExecutionConfigData(
    @Description("The observed effect or outcome to explain")
    val observed_effect: String? = null,
    @Description("Potential causes to investigate")
    val potential_causes: List<String>? = null,
    @Description("Whether to build a causal graph")
    val build_causal_graph: Boolean = true,
    @Description("Whether to identify confounding factors")
    val identify_confounders: Boolean = true,
    @Description("Data sources for evidence (file patterns or paths)")
    val evidence_sources: List<String>? = null,
    @Description("Additional files for context")
    val related_files: List<String>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = CausalInference.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment(): String {
    return """
CausalInference - Identify causal relationships and root causes
  ** Specify the observed effect or outcome to explain
  ** List potential causes to investigate
  ** Optionally build a causal graph showing relationships
  ** Optionally identify confounding factors
  ** Provide evidence sources (logs, metrics, code files)
  ** Useful for:
     - Root cause analysis
     - Debugging complex issues
     - Understanding system behavior
     - Distinguishing correlation from causation
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
    log.info("Starting CausalInference task for effect: ${executionConfig?.observed_effect}")

    val observedEffect = executionConfig?.observed_effect
    if (observedEffect.isNullOrBlank()) {
      val errorMsg = "CONFIGURATION ERROR: No observed effect specified"
      log.error(errorMsg)
      task.complete(errorMsg)
      resultFn("CONFIGURATION ERROR: No observed effect specified")
      return
    }

    val toInput = { it: String -> listOf(it) }
    val ui = task.ui
    val api = orchestrationConfig.defaultChatter ?: run {
      log.error("No default chatter available")
      task.complete("ERROR: No API available")
      resultFn("ERROR: No API available")
      return
    }
    try {
      // Create tabbed display for organized output
      val tabs = TabbedDisplay(task)

      // Overview tab
      val overviewTask = task.ui.newTask(false)
      tabs["Overview"] = overviewTask.placeholder
      var overviewTaskStatus = overviewTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Causal Inference Analysis
            |
            |**Observed Effect:** $observedEffect
            |
            |**Status:** 🔄 Gathering evidence...
        """.trimMargin(), ui = ui
        )
      )
      task.update()

      // Gather evidence from sources
      log.debug("Gathering evidence from ${executionConfig?.evidence_sources?.size ?: 0} sources")
      val evidenceTask = task.ui.newTask(false)
      tabs["Evidence Sources"] = evidenceTask.placeholder
      val evidenceLoading =
        evidenceTask.add(MarkdownUtil.renderMarkdown("## Evidence Sources\n\n🔄 Loading evidence...", ui = ui))
      task.update()

      val evidenceContext = gatherEvidence()
      log.debug("Evidence gathered: ${evidenceContext.length} characters")
      evidenceLoading?.clear()
      evidenceTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Evidence Sources
            |
            |✅ Evidence gathered successfully
            |
            |**Sources processed:** ${executionConfig?.evidence_sources?.size ?: 0}
            |
            |<details>
            |<summary>Evidence Context:</summary>
            |
            |```
            |${evidenceContext.take(maxOutputLength)}${if (evidenceContext.length > maxOutputLength) "\n... (truncated)" else ""}
            |```
            |
            |</details>
        """.trimMargin(), ui = ui
        )
      )
      task.update()
      log.debug("Retrieving prior context from execution state")

      val priorContext = getPriorCode(agent.executionState)

      val potentialCauses = executionConfig.potential_causes ?: emptyList()
      val causesText = if (potentialCauses.isNotEmpty()) {
        "**Potential Causes to Investigate:**\n" + potentialCauses.joinToString("\n") { "- $it" }
      } else {
        "**Note:** No specific potential causes provided. Will identify causes from evidence."
      }

      // Update overview with causes
      overviewTaskStatus?.clear()
      overviewTaskStatus = overviewTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Causal Inference Analysis
            |
            |**Observed Effect:** $observedEffect
            |
            |$causesText
            |
            |**Status:** 🔄 Analyzing causal relationships...
        """.trimMargin(), ui = ui
        )
      )
      task.update()
      log.debug("Building analysis prompt with ${potentialCauses.size} potential causes")

      // Build the analysis prompt
      val prompt = buildAnalysisPrompt(
        observedEffect,
        potentialCauses,
        evidenceContext,
        priorContext
      )
      log.debug("Initializing ChatAgent with model: ${api.javaClass.simpleName}")

      val chatAgent = ChatAgent(
        prompt = prompt,
        model = api,
      )
      // Analysis tab
      val analysisTask = task.ui.newTask(false)
      tabs["Causal Analysis"] = analysisTask.placeholder
      val analysisTaskLoading = analysisTask.add(
        MarkdownUtil.renderMarkdown(
          "## Causal Analysis\n\n🔄 Performing causal inference...",
          ui = ui
        )
      )
      task.update()
      log.debug("Requesting causal analysis from LLM")


      var answer: String? = chatAgent.answer(toInput(prompt))

      analysisTaskLoading?.clear()
      analysisTask.add(
        MarkdownUtil.renderMarkdown(
          """
                |## Causal Analysis Results
                |
                |✅ Analysis complete
                |
                |$answer
                """.trimMargin(),
          ui = ui
        )
      )
      task.update()

      // Update overview status
      overviewTaskStatus?.clear()
      overviewTask.add(
        MarkdownUtil.renderMarkdown(
          """
            |## Causal Inference Analysis
            |
            |**Observed Effect:** $observedEffect
            |
            |$causesText
            |
            |**Status:** ✅ Analysis complete
        """.trimMargin(), ui = ui
        )
      )
      task.update()

      // If building causal graph, generate visualization
      if (executionConfig.build_causal_graph) {
        log.debug("Building causal graph visualization")
        val graphTask = task.ui.newTask(false)
        tabs["Causal Graph"] = graphTask.placeholder
        var graphTaskStatus = graphTask.add(
          MarkdownUtil.renderMarkdown(
            "## Causal Graph\n\n🔄 Generating causal graph visualization...",
            ui = ui
          )
        )
        task.update()

        val graphPrompt = """
Based on the causal analysis above, create a Mermaid diagram showing the causal relationships.
Use the following format:
- Use `graph TD` for top-down flow
- Show direct causal links with `-->` 
- Show correlations with `-.->` (dotted lines)
- Label confounders clearly
- Use descriptive node labels

Generate the Mermaid diagram now:
            """.trimIndent()
        log.debug("Requesting causal graph from LLM")

        var graphResult: String? = chatAgent.answer(toInput(graphPrompt))
        val mermaidCode = extractMermaidCode(graphResult ?: "")
        graphTaskStatus?.clear()
        if (mermaidCode.isNotEmpty()) {
          graphTaskStatus = graphTask.add(
            MarkdownUtil.renderMarkdown(
              """
                        |## Causal Graph
                        |
                        |✅ Graph generated successfully
                        |
                        |```mermaid
                        |$mermaidCode
                        |```
                        """.trimMargin(),
              ui = ui
            )
          )
        } else {
          graphTask.add(
            MarkdownUtil.renderMarkdown(
              """
                        |## Causal Graph
                        |
                        |⚠️ Failed to generate graph visualization
                        |
                        |The analysis did not produce a valid Mermaid diagram.
                        """.trimMargin(),
              ui = ui
            )
          )
        }
        task.update()
      }

      val duration = System.currentTimeMillis() - startTime
      val summary = "Causal inference analysis completed for effect: $observedEffect"
      log.info("$summary (duration: ${duration}ms, causes analyzed: ${potentialCauses.size}, evidence sources: ${executionConfig?.evidence_sources?.size ?: 0})")

      task.complete(summary)
      resultFn(answer ?: "Analysis completed")

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      log.error("CausalInference task failed after ${duration}ms for effect: $observedEffect", e)
      task.error(e)

      val errorTask = task.ui.newTask(false)
//            tabs["Error"] = errorTask.placeholder
      errorTask.add(MarkdownUtil.renderMarkdown("## ❌ Error\n\nAn error occurred during causal inference analysis:\n\n```\n${e.message}\n```", ui = ui))
      task.complete("Analysis failed: ${e.message}")
      resultFn("ERROR: Causal inference analysis failed - ${e.message}")
    }
  }

  private fun buildAnalysisPrompt(
    observedEffect: String,
    potentialCauses: List<String>,
    evidenceContext: String,
    priorContext: String
  ): String {
    val causesSection = if (potentialCauses.isNotEmpty()) {
      """
            |## Potential Causes to Investigate:
            |${potentialCauses.joinToString("\n") { "- $it" }}
            """.trimMargin()
    } else {
      "## Note: Identify potential causes from the evidence provided."
    }

    val confoundersSection = if (executionConfig?.identify_confounders == true) {
      """
            |
            |## Confounding Factors:
            |Identify any confounding variables that might create spurious correlations.
            |Explain how these confounders affect the causal interpretation.
            """.trimMargin()
    } else {
      ""
    }

    return """
You are an expert in causal inference and root cause analysis. Your task is to identify the true causal relationships behind an observed effect.

## Observed Effect:
$observedEffect

$causesSection

## Evidence and Context:
$evidenceContext

## Previous Task Results:
$priorContext

## Analysis Instructions:
1. **Distinguish Causation from Correlation**: Identify which relationships are truly causal vs merely correlated
2. **Apply Causal Reasoning**: Use principles like:
   - Temporal precedence (cause must precede effect)
   - Mechanism (explain HOW the cause produces the effect)
   - Counterfactual reasoning (what would happen without the cause?)
   - Elimination of alternative explanations
3. **Evaluate Each Potential Cause**: For each potential cause, assess:
   - Strength of causal link
   - Supporting evidence
   - Alternative explanations
   - Confidence level
4. **Identify Root Causes**: Distinguish between:
   - Root causes (fundamental sources)
   - Intermediate causes (mediating factors)
   - Proximate causes (immediate triggers)
5. **Consider Causal Chains**: Map out sequences of causation
6. **Assess Causal Strength**: Rate each causal relationship (strong/moderate/weak)
$confoundersSection

## Output Format:
Provide a structured analysis with:
1. **Summary**: Brief overview of key findings
2. **Causal Analysis**: For each identified cause:
   - Description of the causal mechanism
   - Evidence supporting causation
   - Strength of causal link
   - Confidence level
3. **Root Cause Identification**: The fundamental cause(s)
4. **Causal Chain**: How causes lead to the observed effect
5. **Confounders** (if requested): Variables that create spurious correlations
6. **Recommendations**: Actions to address root causes

Generate the causal analysis now:
        """.trimIndent()
  }

  private fun gatherEvidence(): String {
    val evidenceSources = executionConfig?.evidence_sources ?: emptyList()
    val relatedFiles = executionConfig?.related_files ?: emptyList()
    val allSources = (evidenceSources + relatedFiles).distinct()

    if (allSources.isEmpty()) {
      return "No specific evidence sources provided."
    }
    val maxFileSize = 2000 // Reduced from 5000
    val maxTotalSize = 10000 // Limit total evidence context
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
    private val log: Logger = LoggerFactory.getLogger(CausalInferenceTask::class.java)
    val CausalInference = TaskType(
      "CausalInference",
      CausalInferenceTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Identify causal relationships and root causes",
      """
              Performs causal inference analysis to identify true causal relationships.
              <ul>
                <li>Distinguishes causation from correlation</li>
                <li>Identifies root causes vs intermediate factors</li>
                <li>Builds causal graphs showing relationships</li>
                <li>Identifies confounding variables</li>
                <li>Provides evidence-based causal reasoning</li>
                <li>Useful for debugging and root cause analysis</li>
              </ul>
            """
    )
  }
}