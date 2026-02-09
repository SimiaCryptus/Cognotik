package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DialecticalReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: DialecticalReasoningTaskExecutionConfigData?
) : AbstractTask<DialecticalReasoningTask.DialecticalReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {


    class DialecticalReasoningTaskExecutionConfigData(
        @Description("The thesis statement or position to analyze")
        var thesis: String? = null,
        @Description("The antithesis statement or opposing position")
        var antithesis: String? = null,
        @Description("Context or domain for the dialectical analysis")
        var context: String? = null,
        @Description("Number of synthesis levels to iterate through (1-5)")
        var synthesis_levels: Int = 3,
        @Description("Whether to preserve strengths from both sides in synthesis")
        var preserve_strengths: Boolean = true,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        @Description("Additional files for context")
        var related_files: List<String>? = null,
        @Description("List of task IDs this task depends on")
        task_dependencies: List<String>? = null,
        @Description("The current state of the task")
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
          if (thesis!!.length > 5000) return "Thesis is too long (max 5000 characters)"
          if (antithesis!!.length > 5000) return "Antithesis is too long (max 5000 characters)"
          if (context != null && context!!.length > 10000) return "Context is too long (max 10000 characters)"
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

      val tabs = TabbedDisplay(task)

      // Overview tab
      val overviewTask = tabs.newTask("Overview")
      overviewTask.header("Dialectical Reasoning Analysis", level = 1)
      task.ui.pool.submit {
        val transcript = task.newFileOutputStream(transcriptFile())
        val startTime = System.currentTimeMillis()
        var stepStartTime = startTime
        log.info("Starting DialecticalReasoningTask analysis.")
        val thesis = executionConfig?.thesis
        val antithesis = executionConfig?.antithesis


        if (thesis.isNullOrBlank() || antithesis.isNullOrBlank()) {


          try {
            if (thesis.isNullOrBlank() || antithesis.isNullOrBlank()) {
              val errorMsg = "Both thesis and antithesis must be specified"
              log.error(errorMsg)
              task.error(Exception(errorMsg))
              resultFn("CONFIGURATION ERROR: $errorMsg")
              return@submit
            }

            val context = executionConfig.context ?: "general domain"
            val synthesisLevels = executionConfig.synthesis_levels.coerceIn(1, 5)
            val preserveStrengths = executionConfig.preserve_strengths

            log.info("DialecticalReasoningTask configured with thesis='${thesis.take(50)}...', levels=$synthesisLevels")

            val api = defaultSmart ?: return@submit
            overviewTask.add(
              """
                                  **Context:** $context
                                  **Synthesis Levels:** $synthesisLevels
                                  **Preserve Strengths:** ${if (preserveStrengths) "Yes" else "No"}
                                  **Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
                                  
                                  ---
                                  ## Progress
                                  *Initializing dialectical analysis...*
                                  """.trimIndent().renderMarkdown(true)
            )

            transcript?.write(
              """
                    # Dialectical Reasoning Analysis
                    
                    **Context:** $context
                    **Synthesis Levels:** $synthesisLevels
                    **Preserve Strengths:** ${if (preserveStrengths) "Yes" else "No"}
                    **Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
                    
                    ---
                    """.trimIndent().toByteArray()
            )

            val priorContext = getPriorCode(agent.executionState)
            val relatedFilesContent = getRelatedFilesContent()
            val inputFilesContent = getInputFileCode()
            val combinedContext = listOfNotNull(
              if (priorContext.isNotBlank()) "Prior Context:\n$priorContext" else null,
              if (relatedFilesContent.isNotBlank()) "Related Information:\n$relatedFilesContent" else null,
              if (inputFilesContent.isNotBlank()) "Input Files:\n$inputFilesContent" else null
            ).joinToString("\n\n")

            if (priorContext.isNotBlank() || relatedFilesContent.isNotBlank()) {
              val contextTask = tabs.newTask("Context")
              contextTask.header("Context Information", level = 1)
              if (priorContext.isNotBlank()) {
                contextTask.expandable("Prior Task Results", priorContext.truncateForDisplay().renderMarkdown(true))
              }
              if (relatedFilesContent.isNotBlank()) {
                contextTask.expandable("Related Files", relatedFilesContent.truncateForDisplay().renderMarkdown(true))
              }

              transcript?.write(
                """
                        ## Context Information
                        <details>
                        <summary>Prior Task Results</summary>
                        
                        ${priorContext.truncateForDisplay()}
                        </details>
                        
                        <details>
                        <summary>Related Files</summary>
                        
                        ${relatedFilesContent.truncateForDisplay()}
                        </details>
                        
                        <details>
                        <summary>Input Files</summary>
                        
                        ${inputFilesContent.truncateForDisplay()}
                        </details>
                        
                        ---
                        """.trimIndent().toByteArray()
              )
            }

            val resultBuilder = StringBuilder()
            resultBuilder.append("# Dialectical Analysis\n\n")
            resultBuilder.append("**Context:** $context\n\n")

            fun writeToTranscript(header: String, content: String, timeMs: Long) {
              transcript?.write(
                """
                        ## $header
                        $content
                        
                        **Status:** ✅ Complete (${timeMs / 1000.0}s)
                        
                        ---
                        """.trimIndent().toByteArray()
              )
            }

            // Step 1: Analyze Thesis
            log.info("Analyzing thesis")
            val thesisTask = tabs.newTask("Thesis")
            thesisTask.header("Thesis Analysis", level = 1)
            thesisTask.add("**Statement:** $thesis\n\n*Analyzing...*".renderMarkdown(true))

            val thesisAgent = ChatAgent(
              prompt = """
You are analyzing a thesis statement in a dialectical reasoning process.

Context: $context

$combinedContext

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
            log.info("Thesis analysis completed in ${thesisTime}ms")
            stepStartTime = System.currentTimeMillis()

            writeToTranscript("Thesis Analysis", "**Statement:** $thesis\n\n$thesisAnalysis", thesisTime)
            resultBuilder.append("## Thesis Analysis\n\n**Statement:** $thesis\n\n$thesisAnalysis\n\n")

            thesisTask.header("Analysis", level = 2)
            thesisTask.add(thesisAnalysis.renderMarkdown(true))
            thesisTask.add("<hr/>\n**Status:** ✅ Complete (${thesisTime / 1000.0}s)".renderMarkdown(true))

            overviewTask.add("\n✅ Thesis analysis complete\n\n*Analyzing antithesis...*".renderMarkdown(true))

            // Step 2: Analyze Antithesis
            log.info("Analyzing antithesis")
            val antithesisTask = tabs.newTask("Antithesis")
            antithesisTask.header("Antithesis Analysis", level = 1)
            antithesisTask.add("**Statement:** $antithesis\n\n*Analyzing...*".renderMarkdown(true))

            val antithesisAgent = ChatAgent(
              prompt = """
You are analyzing an antithesis statement in a dialectical reasoning process.

Context: $context

$combinedContext

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
            log.info("Antithesis analysis completed in ${antithesisTime}ms")
            stepStartTime = System.currentTimeMillis()

            writeToTranscript(
              "Antithesis Analysis",
              "**Statement:** $antithesis\n\n$antithesisAnalysis",
              antithesisTime
            )
            resultBuilder.append("## Antithesis Analysis\n\n**Statement:** $antithesis\n\n$antithesisAnalysis\n\n")

            antithesisTask.header("Analysis", level = 2)
            antithesisTask.add(antithesisAnalysis.renderMarkdown(true))
            antithesisTask.add("<hr/>\n**Status:** ✅ Complete (${antithesisTime / 1000.0}s)".renderMarkdown(true))

            overviewTask.add("\n✅ Antithesis analysis complete\n\n*Exploring contradictions...*".renderMarkdown(true))

            // Step 3: Explore Contradictions
            log.info("Exploring contradictions and tensions")
            val contradictionsTask = tabs.newTask("Contradictions")
            contradictionsTask.header("Contradictions & Tensions", level = 1)
            contradictionsTask.add("*Analyzing...*".renderMarkdown(true))

            val contradictionsAgent = ChatAgent(
              prompt = """
You are exploring the contradictions and tensions between thesis and antithesis in a dialectical process.

Context: $context
$combinedContext

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
            log.info("Contradictions analysis completed in ${contradictionsTime}ms")
            stepStartTime = System.currentTimeMillis()

            writeToTranscript("Contradictions & Tensions", contradictionsAnalysis, contradictionsTime)
            resultBuilder.append("## Contradictions & Tensions\n\n$contradictionsAnalysis\n\n")

            contradictionsTask.header("Analysis", level = 2)
            contradictionsTask.add(contradictionsAnalysis.renderMarkdown(true))
            contradictionsTask.add("<hr/>\n**Status:** ✅ Complete (${contradictionsTime / 1000.0}s)".renderMarkdown(true))

            overviewTask.add("\n✅ Contradictions explored\n\n*Generating synthesis (Level 1)...*".renderMarkdown(true))

            // Step 4: Iterative Synthesis
            var currentThesis = thesis
            var currentAntithesis = antithesis
            var currentThesisAnalysis = thesisAnalysis
            var currentAntithesisAnalysis = antithesisAnalysis
            var previousSynthesis = ""
            val synthesisResults = mutableListOf<String>()

            for (level in 1..synthesisLevels) {
              log.info("Generating synthesis level $level")
              val synthesisTask = tabs.newTask("Synthesis L$level")
              synthesisTask.header("Synthesis - Level $level", level = 1)
              synthesisTask.add("*Generating higher-level synthesis...*".renderMarkdown(true))

              val synthesisPrompt = if (level == 1) {
                """
You are generating a dialectical synthesis that transcends the opposition between thesis and antithesis.

Context: $context
$combinedContext

Thesis: "$currentThesis"
Analysis: ${currentThesisAnalysis.take(1000)}...

Antithesis: "$currentAntithesis"
Analysis: ${currentAntithesisAnalysis.take(1000)}...

Contradictions & Tensions:
${contradictionsAnalysis.take(1000)}...

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
$combinedContext

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
              log.info("Synthesis level $level completed in ${synthesisTime}ms")
              stepStartTime = System.currentTimeMillis()

              writeToTranscript("Synthesis - Level $level", synthesis, synthesisTime)
              synthesisResults.add(synthesis)
              previousSynthesis = synthesis

              resultBuilder.append("## Synthesis Level $level\n\n$synthesis\n\n")
              synthesisTask.header("Result", level = 2)
              synthesisTask.add(synthesis.renderMarkdown(true))
              synthesisTask.add("<hr/>\n**Status:** ✅ Complete (${synthesisTime / 1000.0}s)".renderMarkdown(true))

              overviewTask.add("\n✅ Synthesis Level $level complete".renderMarkdown(true))
              if (level < synthesisLevels) {
                overviewTask.add("\n*Generating synthesis (Level ${level + 1})...*".renderMarkdown(true))
              }
            }

            // Step 5: Final Integration
            log.info("Generating final integration")
            val integrationTask = tabs.newTask("Final Integration")
            integrationTask.header("Final Integration", level = 1)
            integrationTask.add("*Synthesizing all levels...*".renderMarkdown(true))

            val integrationAgent = ChatAgent(
              prompt = """
You are providing a final integration of the entire dialectical reasoning process.

Context: $context
$combinedContext

Original Thesis: "$thesis"
Original Antithesis: "$antithesis"

All Synthesis Levels:
${synthesisResults.mapIndexed { index, s -> "Level ${index + 1}:\n${s.take(1000)}..." }.joinToString("\n\n")}

Your task is to provide a final integration that:
1. Summarizes the dialectical journey from thesis-antithesis to final synthesis
2. Highlights key insights gained at each level
3. Explains how the final synthesis resolves the original contradiction
4. Identifies practical implications or applications
5. Notes any remaining questions or areas for further exploration
6. Provides actionable recommendations based on the synthesis

Be thorough yet concise in your final integration.













                    """.trimIndent(),
              model = api,
              temperature = 0.6
            )

            val finalIntegration = integrationAgent.answer(listOf("Provide the final integration."))
            val integrationTime = System.currentTimeMillis() - stepStartTime
            log.info("Final integration completed in ${integrationTime}ms")

            writeToTranscript("Final Integration", finalIntegration, integrationTime)
            resultBuilder.append("## Final Integration\n\n$finalIntegration")

            integrationTask.header("Result", level = 2)
            integrationTask.add(finalIntegration.renderMarkdown(true))
            integrationTask.add("<hr/>\n**Status:** ✅ Complete (${integrationTime / 1000.0}s)".renderMarkdown(true))

            val totalTime = System.currentTimeMillis() - startTime
            log.info("DialecticalReasoningTask completed in ${totalTime}ms")

            overviewTask.add(
              """
                                  ---
                                  ## ✅ Dialectical Analysis Complete
                                  **Total Time:** ${totalTime / 1000.0}s
                                  **Synthesis Levels:** $synthesisLevels
                                  **Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
                                  """.trimIndent().renderMarkdown(true)
            )

            transcript?.write(
              """
                    ## Summary
                    **Total Time:** ${totalTime / 1000.0}s
                    **Synthesis Levels:** $synthesisLevels
                    **Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
                    """.trimIndent().toByteArray()
            )

            task.complete()
            resultFn(
              """
                    ## Dialectical Analysis Summary
                    * **Thesis:** $thesis
                    * **Antithesis:** $antithesis
                    * **Synthesis Levels:** $synthesisLevels
                    * **Final Synthesis:** ${previousSynthesis.take(500)}...
                    * Full report available in session transcript.
                    """.trimIndent()
            )

          } catch (e: Exception) {
            // Triple Log Rule
            log.error("Error in DialecticalReasoningTask: ${e.message}", e)
            task.error(e)
            transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
            overviewTask.add("\n--- \n## ❌ Error Occurred\n**Error:** ${e.message}".renderMarkdown(true))
            resultFn("Error during dialectical reasoning: ${e.message}")
          } finally {
            transcript?.close()
          }
        }
      }
    }


  private fun getInputFileCode(): String {
    val inputFiles = executionConfig?.input_files ?: return ""
    return inputFiles.flatMap { pattern ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
          matcher.matches(root.relativize(it.toPath())) -> true
          it.isDirectory -> true
          else -> false
        }
      })
    }.filter { file -> file.isFile && file.exists() }
      .distinct()
      .sortedBy { it }
      .joinToString("\n\n") { relativePath ->
        val file = root.toFile().resolve(relativePath)
        try {
          val content = file.readText()
          "# $relativePath\n\n```\n$content\n```"
        } catch (e: Throwable) {
          log.warn("Error reading file: $relativePath", e)
          ""
        }
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
        @JvmStatic val DialecticalReasoning = TaskType(
          name = "DialecticalReasoning",
          category = "Reasoning",
          taskClass = DialecticalReasoningTask::class.java,
          executionConfigClass = DialecticalReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Resolve contradictions through thesis-antithesis-synthesis",
          tooltipHtml = """
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
                      """,
        )
    }
}