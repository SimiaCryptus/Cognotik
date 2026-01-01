package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
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
        val observed_effect: String? = null,
        @Description("Potential causes to investigate")
        val potential_causes: List<String>? = null,
        @Description("Whether to build a causal graph")
        val build_causal_graph: Boolean = true,
        @Description("Whether to identify confounding factors")
        val identify_confounders: Boolean = true,
        @Description("Data sources for evidence (file patterns or paths)")
        val evidence_sources: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
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

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val transcript = task.transcript()
        val startTime = System.currentTimeMillis()
        log.info("Starting CausalInference task for effect: ${executionConfig?.observed_effect}")
        // Create transcript file for logging the analysis
        var markdownTranscript: FileOutputStream? = null

        val observedEffect = executionConfig?.observed_effect
        if (observedEffect.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No observed effect specified"
            log.error(errorMsg)
            task.complete(errorMsg)
            resultFn(formatResultMessage(task, transcript, errorMsg))
            return
        }
        markdownTranscript = task.transcript()

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            val errorMsg = "CONFIGURATION ERROR: $validationError"
            log.error(errorMsg)
            markdownTranscript?.write("# Configuration Error\n\n$errorMsg\n".toByteArray())
            markdownTranscript?.close()
            task.complete(errorMsg)
            resultFn(formatResultMessage(task, transcript, errorMsg))
            return
        }

        val toInput = { it: String -> listOf(it) }
        val ui = task.ui
        val api = defaultSmart ?: run {
            log.error("No default chatter available")
            markdownTranscript?.write("# Error\n\nNo API available\n".toByteArray())
            markdownTranscript?.close()
            task.complete("ERROR: No API available")
            resultFn(formatResultMessage(task, transcript, "ERROR: No API available"))
            return
        }
        try {
            // Create tabbed display for organized output
            val tabs = TabbedDisplay(task)
            // Write header to transcript
            markdownTranscript?.write(
                """
        |# Causal Inference Analysis
        |
        |**Observed Effect:** $observedEffect
        |**Start Time:** ${java.time.Instant.ofEpochMilli(startTime)}
        |
        |---
        |
        """.trimMargin().toByteArray()
            )

            // Overview tab
            val overviewTask = tabs.newTask("Overview")

            // Static Overview Content
            overviewTask.add(MarkdownUtil.renderMarkdown("## Input Files\n\n${getInputFileCode()}", ui = ui))
            overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    "---\n\n## Causal Inference Analysis\n\n**Observed Effect:** $observedEffect",
                    ui = ui
                )
            )

            // Dynamic Status Buffer
            val overviewStatusBuffer =
                overviewTask.add(MarkdownUtil.renderMarkdown("**Status:** 🔄 Gathering evidence...", ui = ui))

            // Gather evidence from sources
            log.debug("Gathering evidence from ${executionConfig?.evidence_sources?.size ?: 0} sources")
            val evidenceTask = tabs.newTask("Evidence Sources")
            val evidenceStatusBuffer =
                evidenceTask.add(MarkdownUtil.renderMarkdown("## Evidence Sources\n\n🔄 Loading evidence...", ui = ui))

            val evidenceContext = gatherEvidence()
            log.debug("Evidence gathered: ${evidenceContext.length} characters")
            markdownTranscript?.write(
                """
        |## Evidence Sources
        |
        |**Sources processed:** ${executionConfig?.evidence_sources?.size ?: 0}
        |
        |${evidenceContext.take(maxOutputLength)}${if (evidenceContext.length > maxOutputLength) "\n... (truncated)" else ""}
        |
        |---
        |
        """.trimMargin().toByteArray()
            )
            evidenceStatusBuffer?.setLength(0)
            evidenceStatusBuffer?.append(
                MarkdownUtil.renderMarkdown(
                    "## Evidence Sources\n\n✅ Evidence gathered successfully\n\n**Sources processed:** ${executionConfig?.evidence_sources?.size ?: 0}",
                    ui = ui
                )
            )

            evidenceTask.expandable(
                "Evidence Context",
                MarkdownUtil.renderMarkdown(
                    "```\n${evidenceContext.take(maxOutputLength)}${if (evidenceContext.length > maxOutputLength) "\n... (truncated)" else ""}\n```",
                    ui = ui
                )
            )
            task.update()
            log.debug("Retrieving prior context from execution state")

            val priorContext = getPriorCode(agent.executionState)
            val messageContext = messages.joinToString("\n\n")

            val potentialCauses = executionConfig.potential_causes ?: emptyList()
            val causesText = if (potentialCauses.isNotEmpty()) {
                "**Potential Causes to Investigate:**\n" + potentialCauses.joinToString("\n") { "- $it" }
            } else {
                "**Note:** No specific potential causes provided. Will identify causes from evidence."
            }

            // Update overview with causes
            overviewStatusBuffer?.setLength(0)
            overviewStatusBuffer?.append(
                MarkdownUtil.renderMarkdown(
                    "$causesText\n\n**Status:** 🔄 Analyzing causal relationships...",
                    ui = ui
                )
            )
            overviewTask.update()
            log.debug("Building analysis prompt with ${potentialCauses.size} potential causes")

            // Build the analysis prompt
            val prompt = buildAnalysisPrompt(
                observedEffect,
                potentialCauses,
                evidenceContext,
                priorContext,
                messageContext
            )
            log.debug("Initializing ChatAgent with model: ${api.javaClass.simpleName}")

            val chatAgent = ChatAgent(
                prompt = prompt,
                model = api,
            )
            // Analysis tab
            val analysisTask = tabs.newTask("Causal Analysis")
            val analysisBuffer = analysisTask.add(
                MarkdownUtil.renderMarkdown(
                    "## Causal Analysis\n\n🔄 Performing causal inference...",
                    ui = ui
                )
            )
            log.debug("Requesting causal analysis from LLM")


            var answer: String? = chatAgent.answer(toInput(prompt))
            // Write analysis to transcript
            markdownTranscript?.write(
                """
        |## Causal Analysis Results
        |
        |$answer
        |
        |---
        |
        """.trimMargin().toByteArray()
            )

            analysisBuffer?.setLength(0)
            analysisBuffer?.append(
                MarkdownUtil.renderMarkdown(
                    "## Causal Analysis Results\n\n✅ Analysis complete\n\n$answer",
                    ui = ui
                )
            )
            task.update()

            // Update overview status
            overviewStatusBuffer?.setLength(0)
            overviewStatusBuffer?.append(
                MarkdownUtil.renderMarkdown(
                    "$causesText\n\n**Status:** ✅ Analysis complete",
                    ui = ui
                )
            )
            overviewTask.update()

            // If building causal graph, generate visualization
            if (executionConfig.build_causal_graph) {
                log.debug("Building causal graph visualization")
                val graphTask = tabs.newTask("Causal Graph")
                val graphBuffer = graphTask.add(
                    MarkdownUtil.renderMarkdown(
                        "## Causal Graph\n\n🔄 Generating causal graph visualization...",
                        ui = ui
                    )
                )

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
                // Write graph to transcript
                markdownTranscript?.write(
                    """
          |## Causal Graph
          |
          |```mermaid
          |$mermaidCode
          |```
          |
          |---
          |
          """.trimMargin().toByteArray()
                )

                graphBuffer?.setLength(0)
                if (mermaidCode.isNotEmpty()) {
                    graphBuffer?.append(
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
                    graphBuffer?.append(
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
            // Write summary to transcript
            markdownTranscript?.write(
                """
        |## Summary
        |
        |$summary
        |
        |**Duration:** ${duration}ms
        |**Causes Analyzed:** ${potentialCauses.size}
        |**Evidence Sources:** ${executionConfig?.evidence_sources?.size ?: 0}
        |
        """.trimMargin().toByteArray()
            )
            markdownTranscript?.close()

            task.complete(summary)
            resultFn(formatResultMessage(task, transcript, summary))

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("CausalInference task failed after ${duration}ms for effect: $observedEffect", e)
            // Write error to transcript
            markdownTranscript?.write(
                """
        |## Error
        |
        |An error occurred during causal inference analysis:
        |
        |```
        |${e.message}
        |${e.stackTraceToString()}
        |```
        |
        |**Duration:** ${duration}ms
        |
        """.trimMargin().toByteArray()
            )
            markdownTranscript?.close()

            task.error(e)

            val errorTask = task.newTask()
//            tabs["Error"] = errorTask.placeholder
            errorTask.add(
                MarkdownUtil.renderMarkdown(
                    "## ❌ Error\n\nAn error occurred during causal inference analysis:\n\n```\n${e.message}\n```",
                    ui = ui
                )
            )
            task.complete("Analysis failed: ${e.message}")
            resultFn(formatResultMessage(task, transcript, "ERROR: Causal inference analysis failed - ${e.message}"))
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

    private fun formatResultMessage(task: SessionTask, transcript: FileOutputStream?, summary: String): String {
        return try {
            val (link, _) = task.createFile("analysis_results.md")
            transcript?.close()
            "✅ $summary\n\n" +
                    "📄 Detailed results: <a href='$link' target='_blank'>$link</a> " +
                    "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                    "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
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
        val CausalInference = TaskType(
            "CausalInference",
            "Reasoning",
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