package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.Retryable
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
        val observedEffect = executionConfig?.observed_effect
        if (observedEffect.isNullOrBlank()) {
            resultFn("CONFIGURATION ERROR: No observed effect specified")
            return
        }

        val newTask = task.ui.newTask(false)
        val toInput = { it: String -> listOf(it) }
        val ui = task.ui
        val api = orchestrationConfig.defaultChatter

        newTask.add(MarkdownUtil.renderMarkdown("## Causal Inference Analysis", ui = ui))
        newTask.add(MarkdownUtil.renderMarkdown("**Observed Effect:** $observedEffect", ui = ui))

        // Gather evidence from sources
        val evidenceContext = gatherEvidence()
        val priorContext = getPriorCode(agent.executionState)

        val potentialCauses = executionConfig.potential_causes ?: emptyList()
        val causesText = if (potentialCauses.isNotEmpty()) {
            "**Potential Causes to Investigate:**\n" + potentialCauses.joinToString("\n") { "- $it" }
        } else {
            "**Note:** No specific potential causes provided. Will identify causes from evidence."
        }

        newTask.add(MarkdownUtil.renderMarkdown(causesText, ui = ui))

        // Build the analysis prompt
        val prompt = buildAnalysisPrompt(
            observedEffect,
            potentialCauses,
            evidenceContext,
            priorContext
        )

val chatAgent = ChatAgent(
            prompt = promptSegment(),
            model = api,
        )

        var answer: String? = null
        Retryable(newTask) { sb ->
            answer = chatAgent.answer(toInput(prompt))
            sb.append(answer)
            answer
        }

        newTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |## Causal Analysis Results
                |
                |$answer
                """.trimMargin(),
                ui = ui
            )
        )

        // If building causal graph, generate visualization
        if (executionConfig.build_causal_graph) {
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

            var graphResult: String? = null
            Retryable(newTask) { sb ->
                graphResult = chatAgent.answer(toInput(graphPrompt))
                sb.append(graphResult)
                graphResult
            }

            val mermaidCode = extractMermaidCode(graphResult ?: "")
            if (mermaidCode.isNotEmpty()) {
                newTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
                        |## Causal Graph
                        |
                        |```mermaid
                        |$mermaidCode
                        |```
                        """.trimMargin(),
                        ui = ui
                    )
                )
            }
        }

        newTask.complete()
        resultFn(answer ?: "Analysis completed")
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

        return allSources.flatMap { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            root.toFile().walkTopDown()
                .filter { file ->
                    file.isFile && matcher.matches(root.relativize(file.toPath()))
                }
                .map { file ->
                    val relativePath = root.relativize(file.toPath())
                    try {
                        val content = file.readText()
                        "### $relativePath\n```\n${content.take(5000)}${if (content.length > 5000) "\n... (truncated)" else ""}\n```"
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