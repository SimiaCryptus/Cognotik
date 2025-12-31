package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems

class MultiPerspectiveAnalysisTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: MultiPerspectiveAnalysisTaskExecutionConfigData?
) : AbstractTask<MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysisTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxDescriptionLength = 2000

    class MultiPerspectiveAnalysisTaskExecutionConfigData(
        @Description("The topic or problem to analyze from multiple viewpoints")
        val analysis_subject: String? = null,
        @Description("List of perspectives to consider (e.g., technical, business, ethical, user)")
        val perspectives: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the analysis")
        val input_files: List<String>? = null,
        @Description("Whether to synthesize perspectives into unified conclusion")
        val synthesize: Boolean = true,
        @Description("Minimum confidence threshold for perspective agreement (0.0-1.0)")
        val consensus_threshold: Double = 0.7,
        @Description("Additional files for context")
        val related_files: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = MultiPerspectiveAnalysis.name,
        task_description = "Analyze '${analysis_subject}' from perspectives: ${perspectives?.joinToString(", ")}",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (analysis_subject.isNullOrBlank()) {
                return "analysis_subject cannot be null or blank"
            }
            if (perspectives.isNullOrEmpty()) {
                return "perspectives list cannot be null or empty"
            }
            if (consensus_threshold < 0.0 || consensus_threshold > 1.0) {
                return "consensus_threshold must be between 0.0 and 1.0, got: $consensus_threshold"
            }
            if (!input_files.isNullOrEmpty() && input_files.any { it.isBlank() }) {
                return "input_files cannot contain blank entries"
            }
            // Call parent validation for nested ValidatedObject fields
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
 MultiPerspectiveAnalysis - Analyze problems from multiple viewpoints with synthesis
  ** Specify the subject to analyze in analysis_subject
  ** Provide a list of perspectives to consider (e.g., technical, business, ethical, user experience)
  ** Optionally, list input files (supports glob patterns) to provide context for the analysis
  ** Set synthesize=true to generate a unified conclusion from all perspectives
  ** Configure consensus_threshold (0.0-1.0) to determine minimum agreement level
  ** Additional context files can be specified via input_files
  ** Each perspective will be analyzed independently, then synthesized
  ** Useful for:
     - Architectural decision making
     - Code review from multiple angles
     - Strategic planning
     - Risk assessment
     - Feature evaluation
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        System.currentTimeMillis()
        log.info("Starting MultiPerspectiveAnalysis for subject: ${executionConfig?.analysis_subject}")

        val subject = executionConfig?.analysis_subject
        // Validate configuration
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }

        if (subject.isNullOrBlank()) {
            log.error("No analysis subject specified")
            task.safeComplete("CONFIGURATION ERROR: No analysis subject specified", log)
            resultFn("CONFIGURATION ERROR: No analysis subject specified")
            return
        }

        val perspectives = executionConfig.perspectives
        if (perspectives.isNullOrEmpty()) {
            log.error("No perspectives specified")
            task.safeComplete("CONFIGURATION ERROR: No perspectives specified", log)
            resultFn("CONFIGURATION ERROR: No perspectives specified")
            return
        }

        val api = defaultSmart ?: run {
            log.error("No default chatter available")
            task.complete("ERROR: No API available")
            resultFn("ERROR: No API available")
            return
        }


        val tabs = TabbedDisplay(task)
        val overviewTask = task.ui.newTask()
        tabs["Overview"] = overviewTask.placeholder

        overviewTask.add(
            MarkdownUtil.renderMarkdown(
                """
                |## Multi-Perspective Analysis
                |**Subject:** ${subject.truncateForDisplay(maxDescriptionLength)}
                |
                |**Perspectives:** ${perspectives.joinToString(", ")}
                |
                |**Status:** 🔄 Starting analysis...
                """.trimMargin(),
                ui = task.ui
            )
        )
        var transcriptStream: FileOutputStream? = null


        val contextFiles = getContextFiles()
        val priorCode = getPriorCode(agent.executionState)

        val perspectiveResults = mutableMapOf<String, String>()

        try {
            transcriptStream = task.transcript("multi_perspective_analysis")
            transcriptStream?.let { stream ->
                writeToTranscript(stream, "# Multi-Perspective Analysis Transcript\n\n")
                writeToTranscript(stream, "**Subject:** ${subject.truncateForDisplay(maxDescriptionLength)}\n\n")
                writeToTranscript(stream, "**Perspectives:** ${perspectives.joinToString(", ")}\n\n")
                writeToTranscript(stream, "**Consensus Threshold:** ${executionConfig.consensus_threshold}\n\n")
                writeToTranscript(stream, "---\n\n")
            }
        } catch (e: Exception) {
            log.warn("Failed to initialize transcript", e)
        }


        // Analyze from each perspective
        perspectives.forEach { perspective ->
            val perspectiveTask = task.ui.newTask().apply {
                tabs[perspective] = placeholder
            }

            val prompt = """
You are analyzing the following subject from the **$perspective perspective**.

## Subject to Analyze:
$subject

## Context from Related Files:
$contextFiles

## Previous Task Results:
$priorCode

## Instructions:
1. Analyze the subject specifically from the $perspective perspective
2. Identify key considerations, risks, and opportunities
3. Provide specific recommendations or insights
4. Rate your confidence in this analysis (0.0-1.0)
5. Highlight any conflicts or synergies with other perspectives you can anticipate

Provide a thorough analysis from the $perspective viewpoint.
            """.trimIndent()

            val chatAgent = ChatAgent(
                prompt = "You are an expert analyst providing perspective-specific insights.",
                model = api,
            )

            try {
                var analysis: String? = chatAgent.answer(listOf(prompt))

                perspectiveResults[perspective] = analysis ?: ""
                transcriptStream?.let { stream ->
                    writeToTranscript(stream, "## $perspective Perspective\n\n$analysis\n\n---\n\n")
                }

                perspectiveTask.complete(
                    MarkdownUtil.renderMarkdown(
                        "### $perspective Perspective\n\n$analysis",
                        ui = task.ui
                    )
                )
            } catch (e: Exception) {
                log.error("Error analyzing from $perspective perspective", e)
                perspectiveTask.error(e)
                perspectiveResults[perspective] = "Error: ${e.message}"
            }
        }

        tabs.update()

        // Synthesize if requested
        val finalResult = if (executionConfig.synthesize) {
            val synthesisTask = task.ui.newTask().apply {
                tabs["Synthesis"] = placeholder
            }
            synthesisTask.add(
                MarkdownUtil.renderMarkdown(
                    "## Synthesizing Perspectives...",
                    ui = task.ui
                )
            )

            val synthesisPrompt = """
You are synthesizing multiple perspective analyses into a unified conclusion.

## Subject:
$subject

## Perspective Analyses:
${
                perspectiveResults.entries.joinToString("\n\n") { (perspective, analysis) ->
                    "### $perspective Perspective:\n$analysis"
                }
            }

## Synthesis Instructions:
1. Identify common themes and agreements across perspectives
2. Highlight conflicts or tensions between perspectives
3. Assess overall consensus level (target threshold: ${executionConfig.consensus_threshold})
4. Provide a balanced, unified recommendation
5. Note any perspectives that require special attention
6. Suggest next steps or action items

Provide a comprehensive synthesis that integrates all perspectives.
            """.trimIndent()

            val synthesisAgent = ChatAgent(
                prompt = "You are an expert at synthesizing multiple viewpoints into coherent conclusions.",
                model = api,
            )

            try {
                val synthesis = synthesisAgent.answer(listOf(synthesisPrompt))
                transcriptStream?.let { stream ->
                    writeToTranscript(stream, "## Synthesis\n\n$synthesis\n\n")
                }

                synthesisTask.complete(
                    MarkdownUtil.renderMarkdown(
                        "## Synthesis\n\n$synthesis",
                        ui = task.ui
                    )
                )

                buildString {
                    appendLine("# Multi-Perspective Analysis: $subject")
                    appendLine()
                    perspectiveResults.forEach { (perspective, analysis) ->
                        appendLine("## $perspective Perspective")
                        appendLine(analysis)
                        appendLine()
                    }
                    appendLine("## Synthesis")
                    appendLine(synthesis)
                }
            } catch (e: Exception) {
                log.error("Error synthesizing perspectives", e)
                synthesisTask.error(e)
                buildString {
                    appendLine("# Multi-Perspective Analysis: $subject")
                    appendLine()
                    perspectiveResults.forEach { (perspective, analysis) ->
                        appendLine("## $perspective Perspective")
                        appendLine(analysis)
                        appendLine()
                    }
                    appendLine("## Synthesis Error")
                    appendLine("Failed to synthesize: ${e.message}")
                }
            }
        } else {
            buildString {
                appendLine("# Multi-Perspective Analysis: $subject")
                appendLine()
                perspectiveResults.forEach { (perspective, analysis) ->
                    appendLine("## $perspective Perspective")
                    appendLine(analysis)
                    appendLine()
                }
            }
        }
        try {
            transcriptStream?.flush()
        } catch (e: Exception) {
            log.warn("Failed to close transcript stream", e)
        }


        task.safeComplete("Multi-perspective analysis complete.", log)
        resultFn(finalResult)
    }

    private fun writeToTranscript(stream: FileOutputStream, content: String) {
        try {
            stream.write(content.toByteArray(StandardCharsets.UTF_8))
            stream.flush()
        } catch (e: Exception) {
            log.error("Failed to write to transcript", e)
        }
    }


    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""

        return relatedFiles.flatMap { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            val files = mutableListOf<String>()
            root.toFile().walkTopDown().forEach { file ->
                if (file.isFile && matcher.matches(root.relativize(file.toPath()))) {
                    try {
                        val relativePath = root.relativize(file.toPath()).toString()
                        val content = file.readText()
                        files.add("# $relativePath\n\n```\n$content\n```")
                    } catch (e: Exception) {
                        log.warn("Error reading file: ${file.name}", e)
                    }
                }
            }
            files
        }.joinToString("\n\n")
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(MultiPerspectiveAnalysisTask::class.java)
        val MultiPerspectiveAnalysis = TaskType(
            "MultiPerspectiveAnalysis",
            "Social",
            MultiPerspectiveAnalysisTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Analyze problems from multiple viewpoints with synthesis",
            """
              Analyzes topics from multiple perspectives and synthesizes findings.
              <ul>
                <li>Examines subject from specified viewpoints</li>
                <li>Generates detailed analysis for each perspective</li>
                <li>Identifies agreements and conflicts</li>
                <li>Synthesizes perspectives into unified conclusion</li>
                <li>Configurable consensus threshold</li>
                <li>Useful for architectural decisions and code reviews</li>
                <li>Supports context from related files</li>
              </ul>
            """
        )
    }
}