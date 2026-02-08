package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
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
        var input_files: List<String>? = null,
        @Description("Additional files for context")
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
            // Validate observed_effect is not null or blank
            if (observed_effect.isNullOrBlank()) {
                return "observed_effect must not be null or blank"
            }
            // Validate potential_causes list if provided
            potential_causes?.let { causes ->
                if (causes.any { it.isBlank() }) {
                    return "potential_causes must not contain blank entries"
                }
            }
            // Call parent validation
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
CausalInference - Identify causal relationships and root causes
  ** Specify the observed effect or outcome to explain
  ** List potential causes to investigate
  ** Optionally build a causal graph showing relationships
  ** Optionally identify confounding factors
  ** Provide evidence sources (logs, metrics, code files)
  ** Optionally, list input files (supports glob patterns) to be examined
  ** Useful for:
     - Root cause analysis
     - Debugging complex issues
     - Understanding system behavior
     - Distinguishing correlation from causation
        """.trimIndent()
    }
    data class CausalAnalysisResult(
        val summary: String = "",
        val causes: List<CausalFactor> = emptyList(),
        val root_causes: List<String> = emptyList(),
        val causal_chain: String = "",
        val confounders: List<String>? = null,
        val recommendations: List<String> = emptyList()
    )
    data class CausalFactor(
        val name: String = "",
        val mechanism: String = "",
        val evidence: String = "",
        val strength: String = "", // strong/moderate/weak
        val confidence: String = ""
    )


    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
      val startTime = System.currentTimeMillis()


      val transcript = task.newFileOutputStream(transcriptFile())

      try {


        task.ui.pool.submit {
          try {
            val observedEffect = executionConfig?.observed_effect
            if (observedEffect.isNullOrBlank()) {
              throw IllegalArgumentException("observed_effect must not be null or blank")
            }

            log.info("Starting CausalInference task for effect: $observedEffect")
            transcript?.write("# Causal Inference Analysis\n\n**Observed Effect:** $observedEffect\n".toByteArray())

            task.ui
            val api = defaultSmart?.getChildClient(task) ?: throw IllegalStateException("No default chatter available")
            val fastApi = defaultFast?.getChildClient(task) ?: api

            val tabs = TabbedDisplay(task)
            val overviewTask = tabs.newTask("Overview")
            overviewTask.add("## Input Files\n\n${getInputFileCode()}".renderMarkdown())
            overviewTask.add("---\n\n## Causal Inference Analysis\n\n**Observed Effect:** $observedEffect".renderMarkdown())

            val overviewStatusBuffer = overviewTask.add("**Status:** 🔄 Gathering evidence...".renderMarkdown())

            // Evidence Gathering
            val evidenceTask = tabs.newTask("Evidence Sources")
            val evidenceStatusBuffer = evidenceTask.add("## Evidence Sources\n\n🔄 Loading evidence...".renderMarkdown())

            val evidenceContext = gatherEvidence()
            transcript?.write(
              """
                        <details>
                        <summary>Evidence Context</summary>

                        ${evidenceContext.take(maxOutputLength)}${if (evidenceContext.length > maxOutputLength) "\n... (truncated)" else ""}
                        </details>
                    """.trimIndent().toByteArray()
            )

            evidenceStatusBuffer?.setLength(0)
            evidenceStatusBuffer?.append("## Evidence Sources\n\n✅ Evidence gathered successfully\n\n**Sources processed:** ${executionConfig?.evidence_sources?.size ?: 0}".renderMarkdown())
            evidenceTask.expandable(
              "Evidence Context",
              "```\n${evidenceContext.take(maxOutputLength)}\n```".renderMarkdown()
            )

            // Analysis Preparation
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

            val prompt =
              buildAnalysisPrompt(observedEffect, potentialCauses, evidenceContext, priorContext, messageContext)
            val analysisAgent = ParsedAgent(
              resultClass = CausalAnalysisResult::class.java,
              prompt = prompt,
              model = api,
              parsingChatter = fastApi
            )

            // Perform Analysis
            val analysisTask = tabs.newTask("Causal Analysis")
            analysisTask.add("## Causal Analysis\n\n🔄 Performing causal inference...".renderMarkdown())
            val analysisResult = analysisAgent.answer(listOf(prompt)).obj

            transcript?.write(
              """
                        ## Causal Analysis Results
                        <details>
                        <summary>Raw Analysis JSON</summary>

                        ```json
                        ${analysisResult.toJson()}
                        ```
                        </details>
                    """.trimIndent().toByteArray()
            )

            analysisTask.add(
              """
                        ## Causal Analysis Results
                        
                        ✅ Analysis complete
                        
                        ### Summary
                        ${analysisResult.summary}
                        
                        ### Identified Causes
                        ${analysisResult.causes.joinToString("\n") { "* **${it.name}** (${it.strength} strength)\n  * *Mechanism:* ${it.mechanism}" }}
                        
                        ### Root Causes
                        ${analysisResult.root_causes.joinToString("\n") { "* $it" }}
                        
                        ### Causal Chain
                        ${analysisResult.causal_chain}
                        
                        ${
                if (analysisResult.confounders != null) "### Confounding Factors\n${
                  analysisResult.confounders.joinToString(
                    "\n"
                  ) { "* $it" }
                }\n" else ""
              }
                        
                        ### Recommendations
                        ${analysisResult.recommendations.joinToString("\n") { "* $it" }}
                    """.trimMargin().renderMarkdown()
            )
            analysisTask.complete()

            overviewStatusBuffer?.setLength(0)
            overviewStatusBuffer?.append("$causesText\n\n**Status:** ✅ Analysis complete".renderMarkdown())
            overviewTask.update()

            // Causal Graph
            if (executionConfig?.build_causal_graph == true) {
              generateCausalGraph(tabs, analysisResult, api, transcript)
            }

            val duration = System.currentTimeMillis() - startTime
            val summary = "Causal inference analysis completed for effect: $observedEffect"
            log.info("$summary (duration: ${duration}ms)")

            task.complete(summary)
            resultFn(formatResultMessage(task, transcript, summary))

          } catch (e: Exception) {
            // Triple Log Rule
            task.error(e)
            log.error("CausalInference task failed", e)
            transcript?.write(
              """
                        ## Error
                        <details>
                        <summary>Stack Trace</summary>

                        ```
                        ${e.stackTraceToString()}
                        ```
                        </details>
                    """.trimIndent().toByteArray()
            )

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

    val graphPrompt = """
Based on the following causal analysis, create a Mermaid diagram showing the causal relationships.

Analysis:
${analysisResult.toJson()}

Use the following format:
- Use `graph TD` for top-down flow
- Show direct causal links with `-->` 
- Show correlations with `-.->` (dotted lines)
- Label confounders clearly
- Use descriptive node labels

Generate the Mermaid diagram now:







        """.trimIndent()

    val chatAgent = ChatAgent(prompt = graphPrompt, model = api)
    val graphResult = chatAgent.answer(listOf(graphPrompt))
    val mermaidCode = extractMermaidCode(graphResult ?: "")
    graphTask.complete()

    transcript?.write("## Causal Graph\n\n```mermaid\n$mermaidCode\n```\n".toByteArray())

    graphBuffer?.setLength(0)
    if (mermaidCode.isNotEmpty()) {
      graphBuffer?.append(
        """
                ## Causal Graph
                
                ✅ Graph generated successfully
                
                ```mermaid
                $mermaidCode
                ```
            """.trimIndent().renderMarkdown()
      )
    } else {
      graphBuffer?.append(
        """
                ## Causal Graph
                
                ⚠️ Failed to generate graph visualization
                
                The analysis did not produce a valid Mermaid diagram.
            """.trimIndent().renderMarkdown()
      )
        }
    }

    private fun buildAnalysisPrompt(
        observedEffect: String,
        potentialCauses: List<String>,
        evidenceContext: String,
        priorContext: String,
        messageContext: String
    ): String {
        val causesSection = if (potentialCauses.isNotEmpty()) {
            """
## User Input and Context:
$messageContext
---
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

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
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
                val content = codeFiles[file.toPath()] ?: file.readText()
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }.let { if (it.isBlank()) "No input files specified" else it }

  private fun formatResultMessage(task: SessionTask, transcript: java.io.OutputStream?, summary: String): String {
        return try {
            val (link, _) = task.createFile("analysis_results.md")
          """
                ## Analysis Complete
                * $summary
                * Detailed results saved to: `$link`
            """.trimIndent()
        } catch (e: Exception) {
            log.error("Failed to create result file", e)
            summary
        }
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
        @JvmStatic val CausalInference = TaskType(
          name = "CausalInference",
          category = "Reasoning",
          taskClass = CausalInferenceTask::class.java,
          executionConfigClass = CausalInferenceTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Identify causal relationships and root causes",
          tooltipHtml = """
                        Performs causal inference analysis to identify true causal relationships.
                        <ul>
                          <li>Distinguishes causation from correlation</li>
                          <li>Identifies root causes vs intermediate factors</li>
                          <li>Builds causal graphs showing relationships</li>
                          <li>Identifies confounding variables</li>
                          <li>Provides evidence-based causal reasoning</li>
                          <li>Useful for debugging and root cause analysis</li>
                        </ul>
                      """,
        )
    }
}