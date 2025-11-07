package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class DialecticalReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DialecticalReasoningTaskExecutionConfigData?
) : AbstractTask<DialecticalReasoningTask.DialecticalReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    protected val codeFiles = mutableMapOf<Path, String>()
    val maxDescriptionLength = 5000

    class DialecticalReasoningTaskExecutionConfigData(
        @Description("The thesis statement or position to analyze")
        val thesis: String? = null,
        @Description("The antithesis statement or opposing position")
        val antithesis: String? = null,
        @Description("Context or domain for the dialectical analysis")
        val context: String? = null,
        @Description("Number of synthesis levels to iterate through (1-5)")
        val synthesis_levels: Int = 3,
        @Description("Whether to preserve strengths from both sides in synthesis")
        val preserve_strengths: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
        @Description("Additional files for context")
        val related_files: List<String>? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = DialecticalReasoning.name,
        task_description = "Dialectical analysis: '$thesis' vs '$antithesis'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (thesis.isNullOrBlank()) return "Thesis must not be blank"
            if (antithesis.isNullOrBlank()) return "Antithesis must not be blank"
            if (synthesis_levels !in 1..5) return "Synthesis levels must be between 1 and 5, got: $synthesis_levels"
            if (thesis == antithesis) return "Thesis and antithesis must be different"
            if (thesis.length > 5000) return "Thesis is too long (max 5000 characters)"
            if (antithesis.length > 5000) return "Antithesis is too long (max 5000 characters)"
            if (context != null && context.length > 10000) return "Context is too long (max 10000 characters)"
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
DialecticalReasoning - Resolve contradictions through thesis-antithesis-synthesis
  ** Specify thesis and antithesis statements representing opposing positions
  ** Provide context to ground the dialectical analysis
  ** Configure synthesis_levels (1-5) to iterate toward higher understanding
  ** Set preserve_strengths=true to maintain valuable aspects of both sides
  ** Related files can provide additional context
  ** Explores contradictions and tensions between positions
  ** Generates synthesis that transcends opposition
  ** Iterates to progressively higher levels of understanding
  ** Produces structured dialectical analysis with final synthesis
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
        var stepStartTime = startTime
        var transcriptStream: FileOutputStream? = null
        log.info("Starting DialecticalReasoningTask")

        val thesis = executionConfig?.thesis
        val antithesis = executionConfig?.antithesis

        if (thesis.isNullOrBlank() || antithesis.isNullOrBlank()) {
            log.error("Both thesis and antithesis must be specified")
            task.safeComplete("CONFIGURATION ERROR: Both thesis and antithesis must be specified", log)
            resultFn("CONFIGURATION ERROR: Both thesis and antithesis must be specified")
            return
        }

        val context = executionConfig.context ?: "general domain"
        val synthesisLevels = executionConfig.synthesis_levels.coerceIn(1, 5)
        val preserveStrengths = executionConfig.preserve_strengths

        log.info("Configuration: thesis='$thesis', antithesis='$antithesis', context='$context', levels=$synthesisLevels, preserveStrengths=$preserveStrengths")

        val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return
        val ui = task.ui
        val tabs = TabbedDisplay(task)
        transcriptStream = initializeTranscript(task)

        // Overview tab
        val overviewTask = ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder

        val overviewContent = buildString {
            appendLine("# Dialectical Reasoning Analysis")
            appendLine()
            appendLine("**Context:** $context")
            appendLine()
            appendLine("**Synthesis Levels:** $synthesisLevels")
            appendLine()
            appendLine("**Preserve Strengths:** ${if (preserveStrengths) "Yes" else "No"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing dialectical analysis...*")
        }
        overviewTask.add(overviewContent.renderMarkdown)
        task.update()
        transcriptStream?.write(
            """
      |# Dialectical Reasoning Analysis
      |
      |**Context:** $context
      |**Synthesis Levels:** $synthesisLevels
      |**Preserve Strengths:** ${if (preserveStrengths) "Yes" else "No"}
      |**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
      |
      |---
      |
    """.trimMargin().toByteArray()
        )

        val priorContext = getPriorCode(agent.executionState)
        val relatedFilesContent = getRelatedFilesContent()
        val inputFilesContent = getInputFileCode()

        if (priorContext.isNotBlank() || relatedFilesContent.isNotBlank()) {
            val contextTask = ui.newTask(false)
            tabs["Context"] = contextTask.placeholder
            contextTask.add(
                buildString {
                    appendLine("# Context Information")
                    appendLine()
                    if (priorContext.isNotBlank()) {
                        appendLine("## Prior Task Results")
                        appendLine()
                        appendLine(priorContext.truncateForDisplay())
                        appendLine()
                    }
                    if (relatedFilesContent.isNotBlank()) {
                        appendLine("## Related Files")
                        appendLine()
                        appendLine(relatedFilesContent.truncateForDisplay())
                    }
                }.renderMarkdown
            )
            task.update()
            transcriptStream?.write(
                """
        |## Context Information
        |
      """.trimMargin().toByteArray()
            )
            if (priorContext.isNotBlank()) {
                transcriptStream?.write("### Prior Task Results\n\n${priorContext.truncateForDisplay()}\n\n".toByteArray())
            }
            if (relatedFilesContent.isNotBlank()) {
                transcriptStream?.write("### Related Files\n\n${relatedFilesContent.truncateForDisplay()}\n\n".toByteArray())
            }
            transcriptStream?.write("---\n\n".toByteArray())
            if (inputFilesContent.isNotBlank()) {
                transcriptStream?.write("### Input Files\n\n${inputFilesContent.truncateForDisplay()}\n\n".toByteArray())
            }
            transcriptStream?.write("---\n\n".toByteArray())
        }

        // Concise output for final result
        val resultBuilder = StringBuilder()
        resultBuilder.append("# Dialectical Analysis\n\n")
        resultBuilder.append("**Context:** $context\n\n")


        try {
            // Step 1: Analyze Thesis
            log.info("Analyzing thesis")
            val thesisTask = ui.newTask(false)
            tabs["Thesis"] = thesisTask.placeholder

            thesisTask.add(
                buildString {
                    appendLine("# Thesis Analysis")
                    appendLine()
                    appendLine("**Statement:** $thesis")
                    appendLine()
                    appendLine("*Analyzing...*")
                }.renderMarkdown
            )
            task.update()

            val thesisAgent = ChatAgent(
                prompt = """
You are analyzing a thesis statement in a dialectical reasoning process.

Context: $context

${if (priorContext.isNotBlank()) "Prior Context:\n$priorContext\n\n" else ""}
${if (relatedFilesContent.isNotBlank()) "Related Information:\n$relatedFilesContent\n\n" else ""}

Your task is to thoroughly analyze the thesis:
"$thesis"

Provide:
1. Core claims and assumptions
2. Strengths and supporting evidence
3. Internal logic and coherence
4. Scope and applicability
5. Potential limitations or blind spots

Be thorough and objective in your analysis.
        """.trimIndent(),
                model = api,
                temperature = 0.5
            )

            val thesisAnalysis = thesisAgent.answer(listOf("Analyze the thesis statement."))

            val thesisTime = System.currentTimeMillis() - stepStartTime
            log.info("Thesis analysis completed in ${thesisTime}ms: ${thesisAnalysis.length} characters")
            stepStartTime = System.currentTimeMillis()
            transcriptStream?.write(
                """
        |## Thesis Analysis
        |
        |**Statement:** $thesis
        |
        |$thesisAnalysis
        |
        |**Status:** ✅ Complete (${thesisTime / 1000.0}s)
        |
        |---
        |
      """.trimMargin().toByteArray()
            )

            thesisTask.add(
                buildString {
                    appendLine()
                    appendLine("## Analysis")
                    appendLine()
                    appendLine(thesisAnalysis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete (${thesisTime / 1000.0}s)")
                }.renderMarkdown
            )
            task.update()

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Thesis analysis complete")
                    appendLine()
                    appendLine("*Analyzing antithesis...*")
                }.renderMarkdown
            )
            task.update()

            // Step 2: Analyze Antithesis
            log.info("Analyzing antithesis")
            val antithesisTask = ui.newTask(false)
            tabs["Antithesis"] = antithesisTask.placeholder

            antithesisTask.add(
                buildString {
                    appendLine("# Antithesis Analysis")
                    appendLine()
                    appendLine("**Statement:** $antithesis")
                    appendLine()
                    appendLine("*Analyzing...*")
                }.renderMarkdown
            )
            task.update()

            val antithesisAgent = ChatAgent(
                prompt = """
You are analyzing an antithesis statement in a dialectical reasoning process.

Context: $context

${if (priorContext.isNotBlank()) "Prior Context:\n$priorContext\n\n" else ""}
${if (relatedFilesContent.isNotBlank()) "Related Information:\n$relatedFilesContent\n\n" else ""}

The thesis being opposed is:
"$thesis"

Your task is to thoroughly analyze the antithesis:
"$antithesis"

Provide:
1. Core claims and assumptions
2. Strengths and supporting evidence
3. How it challenges or contradicts the thesis
4. Internal logic and coherence
5. Scope and applicability
6. Potential limitations or blind spots

Be thorough and objective in your analysis.
        """.trimIndent(),
                model = api,
                temperature = 0.5
            )

            val antithesisAnalysis = antithesisAgent.answer(listOf("Analyze the antithesis statement."))

            val antithesisTime = System.currentTimeMillis() - stepStartTime
            log.info("Antithesis analysis completed in ${antithesisTime}ms: ${antithesisAnalysis.length} characters")
            stepStartTime = System.currentTimeMillis()
            transcriptStream?.write(
                """
        |## Antithesis Analysis
        |
        |**Statement:** $antithesis
        |
        |$antithesisAnalysis
        |
        |**Status:** ✅ Complete (${antithesisTime / 1000.0}s)
        |
        |---
        |
      """.trimMargin().toByteArray()
            )

            antithesisTask.add(
                buildString {
                    appendLine()
                    appendLine("## Analysis")
                    appendLine()
                    appendLine(antithesisAnalysis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete (${antithesisTime / 1000.0}s)")
                }.renderMarkdown
            )
            task.update()

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Antithesis analysis complete")
                    appendLine()
                    appendLine("*Exploring contradictions...*")
                }.renderMarkdown
            )
            task.update()

            // Step 3: Explore Contradictions
            log.info("Exploring contradictions and tensions")
            val contradictionsTask = ui.newTask(false)
            tabs["Contradictions"] = contradictionsTask.placeholder

            contradictionsTask.add(
                buildString {
                    appendLine("# Contradictions & Tensions")
                    appendLine()
                    appendLine("*Analyzing...*")
                }.renderMarkdown
            )
            task.update()

            val contradictionsAgent = ChatAgent(
                prompt = """
You are exploring the contradictions and tensions between thesis and antithesis in a dialectical process.

Context: $context

Thesis: "$thesis"
Thesis Analysis:
$thesisAnalysis

Antithesis: "$antithesis"
Antithesis Analysis:
$antithesisAnalysis

Your task is to identify and explore:
1. Direct contradictions between the positions
2. Underlying tensions and incompatibilities
3. Areas of partial overlap or agreement
4. Root causes of the opposition
5. What each side reveals about the other's limitations
6. The deeper question or problem both are trying to address

Be thorough in exploring the dialectical tension.
        """.trimIndent(),
                model = api,
                temperature = 0.6
            )

            val contradictionsAnalysis = contradictionsAgent.answer(listOf("Explore the contradictions and tensions."))

            val contradictionsTime = System.currentTimeMillis() - stepStartTime
            log.info("Contradictions analysis completed in ${contradictionsTime}ms: ${contradictionsAnalysis.length} characters")
            stepStartTime = System.currentTimeMillis()
            transcriptStream?.write(
                """
        |## Contradictions & Tensions
        |
        |$contradictionsAnalysis
        |
        |**Status:** ✅ Complete (${contradictionsTime / 1000.0}s)
        |
        |---
        |
      """.trimMargin().toByteArray()
            )

            contradictionsTask.add(
                buildString {
                    appendLine()
                    appendLine("## Analysis")
                    appendLine()
                    appendLine(contradictionsAnalysis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete (${contradictionsTime / 1000.0}s)")
                }.renderMarkdown
            )
            task.update()

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Contradictions explored")
                    appendLine()
                    appendLine("*Generating synthesis (Level 1)...*")
                }.renderMarkdown
            )
            task.update()

            // Step 4: Iterative Synthesis
            var currentThesis = thesis
            var currentAntithesis = antithesis
            var currentThesisAnalysis = thesisAnalysis
            var currentAntithesisAnalysis = antithesisAnalysis
            var previousSynthesis = ""
            val synthesisResults = mutableListOf<String>()

            for (level in 1..synthesisLevels) {
                log.info("Generating synthesis level $level of $synthesisLevels")
                val synthesisTask = ui.newTask(false)
                tabs["Synthesis L$level"] = synthesisTask.placeholder

                synthesisTask.add(
                    buildString {
                        appendLine("# Synthesis - Level $level")
                        appendLine()
                        appendLine("*Generating higher-level synthesis...*")
                    }.renderMarkdown
                )
                task.update()

                val synthesisPrompt = if (level == 1) {
                    """
You are generating a dialectical synthesis that transcends the opposition between thesis and antithesis.

Context: $context

Thesis: "$currentThesis"
Analysis: $currentThesisAnalysis

Antithesis: "$currentAntithesis"
Analysis: $currentAntithesisAnalysis

Contradictions & Tensions:
$contradictionsAnalysis

${if (preserveStrengths) "IMPORTANT: Preserve the valuable strengths from both sides in your synthesis." else ""}

Your task is to generate a synthesis that:
1. Transcends the opposition by finding a higher-level perspective
2. Incorporates valid insights from both thesis and antithesis
3. Resolves contradictions through reframing or integration
4. ${if (preserveStrengths) "Preserves the strengths of both positions" else "Moves beyond both positions"}
5. Addresses the deeper question both sides are grappling with
6. Provides a more complete or nuanced understanding

Provide:
- The synthesis statement (a new position that transcends the opposition)
- Explanation of how it integrates both sides
- What it preserves from thesis and antithesis
- What new understanding it provides
- Any remaining tensions or limitations

Be creative and insightful in finding the higher-level synthesis.
          """.trimIndent()
                } else {
                    """
You are generating a higher-level dialectical synthesis (Level $level).

Context: $context

Previous Synthesis (Level ${level - 1}):
$previousSynthesis

This synthesis itself can be seen as a new thesis. Consider what antithesis or limitation it might have, and generate an even higher-level synthesis.

Your task is to:
1. Identify limitations or tensions in the previous synthesis
2. Consider what perspective or position challenges it
3. Generate a new synthesis at a higher level of abstraction
4. Integrate insights from all previous levels
5. ${if (preserveStrengths) "Continue to preserve valuable strengths from original positions" else "Continue to transcend previous limitations"}

Provide:
- The new synthesis statement
- How it transcends the previous level
- What new understanding it provides
- Connection to original thesis and antithesis
- Any remaining tensions or areas for further exploration

Aim for progressively deeper insight and integration.
          """.trimIndent()
                }

                val synthesisAgent = ChatAgent(
                    prompt = synthesisPrompt,
                    model = api,
                    temperature = 0.7
                )

                val synthesis = synthesisAgent.answer(listOf("Generate the synthesis."))
                val synthesisTime = System.currentTimeMillis() - stepStartTime
                log.info("Synthesis level $level completed in ${synthesisTime}ms: ${synthesis.length} characters")
                stepStartTime = System.currentTimeMillis()
                transcriptStream?.write(
                    """
          |## Synthesis - Level $level
          |
          |$synthesis
          |
          |**Status:** ✅ Complete (${synthesisTime / 1000.0}s)
          |
          |---
          |
        """.trimMargin().toByteArray()
                )

                synthesisResults.add(synthesis)
                previousSynthesis = synthesis


                // Add to concise result only for first and last levels
                if (level == 1 || level == synthesisLevels) {
                    resultBuilder.append("### Synthesis Level $level\n\n")
                    resultBuilder.append("${synthesis.truncateForDisplay(maxDescriptionLength)}\n\n")
                }

                synthesisTask.add(
                    buildString {
                        appendLine()
                        appendLine("## Result")
                        appendLine()
                        appendLine(synthesis)
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("**Status:** ✅ Complete (${synthesisTime / 1000.0}s)")
                    }.renderMarkdown
                )
                task.update()

                overviewTask.add(
                    buildString {
                        appendLine()
                        appendLine("✅ Synthesis Level $level complete")
                        if (level < synthesisLevels) {
                            appendLine()
                            appendLine("*Generating synthesis (Level ${level + 1})...*")
                        }
                    }.renderMarkdown
                )
                task.update()
            }

            // Step 5: Final Integration
            log.info("Generating final integration")
            val integrationTask = ui.newTask(false)
            tabs["Final Integration"] = integrationTask.placeholder

            integrationTask.add(
                buildString {
                    appendLine("# Final Integration")
                    appendLine()
                    appendLine("*Synthesizing all levels...*")
                }.renderMarkdown
            )
            task.update()

            val integrationAgent = ChatAgent(
                prompt = """
You are providing a final integration of the entire dialectical reasoning process.

Context: $context

Original Thesis: "$thesis"
Original Antithesis: "$antithesis"

All Synthesis Levels:
${synthesisResults.mapIndexed { index, s -> "Level ${index + 1}:\n$s" }.joinToString("\n\n")}

Your task is to provide a final integration that:
1. Summarizes the dialectical journey from thesis-antithesis to final synthesis
2. Highlights key insights gained at each level
3. Explains how the final synthesis resolves the original contradiction
4. Identifies practical implications or applications
5. Notes any remaining questions or areas for further exploration
6. Provides actionable recommendations based on the synthesis

Be comprehensive yet concise in your final integration.
        """.trimIndent(),
                model = api,
                temperature = 0.6
            )

            val finalIntegration = integrationAgent.answer(listOf("Provide the final integration."))

            val integrationTime = System.currentTimeMillis() - stepStartTime
            log.info("Final integration completed in ${integrationTime}ms: ${finalIntegration.length} characters")
            // stepStartTime = System.currentTimeMillis() // Not needed for the last step
            transcriptStream?.write(
                """
        |## Final Integration
        |
        |$finalIntegration
        |
        |**Status:** ✅ Complete (${integrationTime / 1000.0}s)
        |
        |---
        |
      """.trimMargin().toByteArray()
            )

            resultBuilder.append("## Final Integration\n\n")
            resultBuilder.append(finalIntegration)

            integrationTask.add(
                buildString {
                    appendLine()
                    appendLine("## Result")
                    appendLine()
                    appendLine(finalIntegration)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete (${integrationTime / 1000.0}s)")
                }.renderMarkdown
            )
            task.update()

            val totalTime = System.currentTimeMillis() - startTime
            log.info("DialecticalReasoningTask completed: total_time=${totalTime}ms, synthesis_levels=$synthesisLevels, output_size=${resultBuilder.length} chars")

            // Update overview with completion
            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Dialectical Analysis Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Synthesis Levels:** $synthesisLevels")
                    appendLine()
                    appendLine("**Total Output:** ${resultBuilder.length} characters")
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown
            )
            task.update()
            transcriptStream?.write(
                """
        |
        |## Summary
        |
        |**Total Time:** ${totalTime / 1000.0}s
        |**Synthesis Levels:** $synthesisLevels
        |**Total Output:** ${resultBuilder.length} characters
        |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
        |
      """.trimMargin().toByteArray()
            )
            transcriptStream?.close()


            task.safeComplete(
                "Completed dialectical analysis with $synthesisLevels synthesis levels in ${totalTime / 1000}s",
                log
            )
            resultFn(resultBuilder.toString())

        } catch (e: Exception) {
            log.error("Error during dialectical reasoning", e)
            task.error(e)

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    appendLine("**Type:** ${e.javaClass.simpleName}")
                }.renderMarkdown
            )
            task.update()
            transcriptStream?.write(
                """
        |
        |## ❌ Error Occurred
        |
        |**Error:** ${e.message}
        |**Type:** ${e.javaClass.simpleName}
        |
      """.trimMargin().toByteArray()
            )
            transcriptStream?.close()


            val errorOutput = buildString {
                appendLine("# Error in Dialectical Reasoning")
                appendLine()
                appendLine("**Thesis:** $thesis")
                appendLine()
                appendLine("**Antithesis:** $antithesis")
                appendLine()
                appendLine("**Error:** ${e.message}")
                if (resultBuilder.isNotEmpty()) {
                    appendLine()
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(resultBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
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
        }

    private fun initializeTranscript(task: SessionTask): FileOutputStream? {
        return try {
            val transcriptFile = "dialectical_transcript_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
            val transcriptStream = file?.outputStream()
            task.complete(
                "Writing transcript to <a href='$link' target='_blank'>$link</a> " +
                        "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                        "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
            )
            log.info("Initialized transcript file: $link")
            transcriptStream
        } catch (e: Exception) {
            log.error("Failed to initialize transcript", e)
            null
        }
    }

    private fun getRelatedFilesContent(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""

        return relatedFiles.flatMap { pattern ->
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
            val files = mutableListOf<String>()
            root.toFile().walkTopDown().forEach { file ->
                if (file.isFile && matcher.matches(root.relativize(file.toPath()))) {
                    try {
                        val relativePath = root.relativize(file.toPath()).toString()
                        val content = file.readText()
                        files.add("### $relativePath\n\n```\n$content\n```")
                    } catch (e: Exception) {
                        log.warn("Error reading file: ${file.name}", e)
                    }
                }
            }
            files
        }.joinToString("\n\n")
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DialecticalReasoningTask::class.java)
        val DialecticalReasoning = TaskType(
            "DialecticalReasoning",
            DialecticalReasoningTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Resolve contradictions through thesis-antithesis-synthesis",
            """
              Applies dialectical reasoning to resolve contradictions and find higher-level synthesis.
              <ul>
                <li>Analyzes thesis and antithesis positions thoroughly</li>
                <li>Explores contradictions and tensions between positions</li>
                <li>Generates synthesis that transcends opposition</li>
                <li>Iterates through multiple synthesis levels for deeper understanding</li>
                <li>Preserves valuable aspects from both sides</li>
                <li>Provides final integration with practical implications</li>
                <li>Useful for architectural debates, requirement conflicts, and design philosophy</li>
              </ul>
            """
        )
    }
}