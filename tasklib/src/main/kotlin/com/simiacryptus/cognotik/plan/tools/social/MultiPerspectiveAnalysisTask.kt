package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
        var analysis_subject: String? = null,
        @Description("List of perspectives to consider (e.g., technical, business, ethical, user)")
        var perspectives: List<String>? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the analysis")
        var related_files: List<String>? = null,
        @Description("Whether to synthesize perspectives into unified conclusion")
        var synthesize: Boolean = true,
        @Description("Minimum confidence threshold for perspective agreement (0.0-1.0)")
        var consensus_threshold: Double = 0.7,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = "MultiPerspectiveAnalysis",
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
            if (!related_files.isNullOrEmpty() && related_files!!.any { it.isBlank() }) {
                return "related_files cannot contain blank entries"
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
  ** Additional context files can be specified via related_files
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


        val transcript = task.newUserFileStream(transcriptFile().removeSuffix(".md") + ".details.md")
        val final_transcript = task.newUserFileStream(transcriptFile())

        try {


            task.ui.pool.submit {
                try {
                    val config = executionConfig ?: throw IllegalStateException("No configuration provided")
                    config.validate()?.let { throw IllegalArgumentException(it) }

                    val subject = config.analysis_subject!!
                    val perspectives = config.perspectives!!
                    val api = defaultSmart.getChildClient(task)

                    log.info("Starting MultiPerspectiveAnalysis for subject: $subject")

                    val tabs = TabbedDisplay(task)
                    val overviewTask = tabs.newTask("Overview")

                    overviewTask.add(
                        """
                        |## Multi-Perspective Analysis
                        |**Subject:** ${subject.truncateForDisplay(maxDescriptionLength)}
                        |
                        |**Perspectives:** ${perspectives.joinToString(", ")}
                        |
                        |**Status:** 🔄 Starting analysis...
                        """.trimMargin().renderMarkdown()
                    )

                    transcript?.let { stream ->
                        writeToTranscript(stream, "# Multi-Perspective Analysis Transcript\n\n")
                        writeToTranscript(
                            stream,
                            "**Subject:** ${subject.truncateForDisplay(maxDescriptionLength)}\n\n"
                        )
                        writeToTranscript(stream, "**Perspectives:** ${perspectives.joinToString(", ")}\n\n")
                        writeToTranscript(stream, "**Consensus Threshold:** ${config.consensus_threshold}\n\n")
                        writeToTranscript(stream, "---\n\n")
                    }

                    val contextFiles =
                        getInputFileContent(
                            (config.related_files ?: emptyList()) + (config.related_files ?: emptyList()), root
                        )
                    val priorCode = getPriorCode(agent.executionState)

                    val perspectiveResults = mutableMapOf<String, String>()

                    // Analyze from each perspective
                    perspectives.forEach { perspective ->
                        val perspectiveTask = tabs.newTask(perspective)
                        val prompt = """
                            You are analyzing the following subject from the **$perspective perspective**.
                            
                            ## Subject to Analyze:
                            $subject
                            
                            ## Context:
                            $contextFiles
                            
                            ## Previous Task Results:
                            $priorCode
                            
                            ## Instructions:
                            1. Analyze the subject specifically from the $perspective perspective
                            2. Identify key considerations, risks, and opportunities
                            3. Provide specific recommendations or insights
                            4. Rate your confidence in this analysis (0.0-1.0)
                            
                            Provide a thorough analysis from the $perspective viewpoint.
                        """.trimIndent()

                        val chatAgent = ChatAgent(
                            prompt = "You are an expert analyst providing perspective-specific insights.",
                            model = api,
                        )

                        val analysis = chatAgent.answer(listOf(prompt)) ?: "No analysis generated."
                        perspectiveResults[perspective] = analysis
                        transcript?.let { stream ->
                            writeToTranscript(stream, "## $perspective Perspective\n\n$analysis\n\n---\n\n")
                        }
                        perspectiveTask.complete("### $perspective Perspective\n\n$analysis".renderMarkdown())
                    }

                    tabs.update()

                    // Synthesize if requested
                    val finalResult = if (config.synthesize) {
                        val synthesisTask = tabs.newTask("Synthesis")
                        synthesisTask.add("## Synthesizing Perspectives...".renderMarkdown())

                        val synthesisPrompt = """
                            You are synthesizing multiple perspective analyses into a unified conclusion.
                            
                            ## Subject:
                            $subject
                            
                            ## Perspective Analyses:
                            ${perspectiveResults.entries.joinToString("\n\n") { (p, a) -> "### $p Perspective:\n$a" }}
                            
                            ## Synthesis Instructions:
                            1. Identify common themes and agreements
                            2. Highlight conflicts or tensions
                            3. Assess overall consensus level (target threshold: ${config.consensus_threshold})
                            4. Provide a balanced, unified recommendation
                            
                            Provide a comprehensive synthesis.
                        """.trimIndent()

                        val synthesisAgent = ChatAgent(
                            prompt = "You are an expert at synthesizing multiple viewpoints into coherent conclusions.",
                            model = api,
                        )

                        val synthesis = synthesisAgent.answer(listOf(synthesisPrompt)) ?: "Synthesis failed."
                        listOfNotNull(final_transcript, transcript).forEach { stream ->
                            writeToTranscript(
                                stream,
                                "## Synthesis\n\n$synthesis\n\n"
                            )
                        }
                        synthesisTask.complete("## Synthesis\n\n$synthesis".renderMarkdown())

                        buildString {
                            appendLine("# Multi-Perspective Analysis: $subject")
                            perspectiveResults.forEach { (p, a) -> appendLine("## $p Perspective\n$a\n") }
                            appendLine("## Synthesis\n$synthesis")
                        }
                    } else {
                        buildString {
                            appendLine("# Multi-Perspective Analysis: $subject")
                            perspectiveResults.forEach { (p, a) -> appendLine("## $p Perspective\n$a\n") }
                        }
                    }

                    resultFn(finalResult)
                    task.add("Multi-perspective analysis complete.".renderMarkdown())
                } catch (e: Exception) {
                    task.error(e)
                    log.error("Error in MultiPerspectiveAnalysisTask", e)
                    listOfNotNull(
                        final_transcript,
                        transcript
                    ).forEach { it.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray()) }
                    resultFn("Error: ${e.message}")
                } finally {
                    listOfNotNull(final_transcript, transcript).forEach { it.close() }
                    task.complete()
                }

            }
        } catch (e: Exception) {
            log.error("Failed to submit task to pool", e)
            task.error(e)
            resultFn("Error: ${e.message}")
        }


    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(MultiPerspectiveAnalysisTask::class.java)

        @JvmStatic
        val MultiPerspectiveAnalysis = TaskType(
            name = "MultiPerspectiveAnalysis",
            category = "Social",
            taskClass = MultiPerspectiveAnalysisTask::class.java,
            executionConfigClass = MultiPerspectiveAnalysisTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Analyze problems from multiple viewpoints with synthesis",
            tooltipHtml = """
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
                      """,
        )
    }
}