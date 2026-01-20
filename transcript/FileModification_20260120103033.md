# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/DialecticalReasoningTask.kt

```
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
        val transcript = task.transcript()
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
            transcript?.write(
              """
                    ## ❌ Error Occurred
                    **Error:** ${e.message}
                    <details>
                    <summary>Stack Trace</summary>
                    
                    ```
                    ${e.stackTraceToString()}
                    ```
                    </details>
                    """.trimIndent().toByteArray()
            )

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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/EthicalReasoningTask.kt

```
package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class EthicalReasoningTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: EthicalReasoningTaskExecutionConfigData?
) : AbstractTask<EthicalReasoningTask.EthicalReasoningTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    companion object {
        private val log: Logger = LoggerFactory.getLogger(EthicalReasoningTask::class.java)
        @JvmStatic val EthicalReasoning = TaskType(
          name = "EthicalReasoning",
          category = "Reasoning",
          taskClass = EthicalReasoningTask::class.java,
          executionConfigClass = EthicalReasoningTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Analyze a dilemma through multiple ethical frameworks to guide decision-making.",
          tooltipHtml = """
                        Provides a structured analysis of a complex ethical problem or decision.
                        <ul>
                          <li>Evaluates a dilemma from the perspectives of several established ethical frameworks (e.g., Utilitarianism, Deontology, Virtue Ethics).</li>
                          <li>For each framework, it assesses the situation, applies the framework's core principles, and determines a recommended course of action.</li>
                          <li>Synthesizes these findings to provide a comprehensive recommendation, highlighting points of convergence, divergence, and the ethical trade-offs involved.</li>
                          <li>Useful for AI safety, product development, policy making, and corporate governance.</li>
                          <li>Generates a downloadable transcript in markdown, HTML, and PDF formats.</li>
                        </ul>
                      """,
        )
    }

    class EthicalReasoningTaskExecutionConfigData(
        @Description("A clear description of the ethical problem or decision to be made.")
        var ethical_dilemma: String? = null,
        @Description("Optional input files (supports glob patterns) to provide context for the ethical analysis")
        var input_files: List<String>? = null,
        @Description("A list of individuals, groups, or entities affected by the decision.")
        var stakeholders: List<String>? = null,
        @Description("The ethical frameworks to apply. Options: utilitarianism, deontology, virtue_ethics, care_ethics, rights_based.")
        var ethical_frameworks: List<String>? = listOf("utilitarianism", "deontology", "virtue_ethics"),
        @Description("Optional background information or constraints relevant to the dilemma.")
        var context: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = EthicalReasoning.name,
        task_description = "Analyze ethical dilemma: ${ethical_dilemma?.take(100)}",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (ethical_dilemma.isNullOrBlank()) {
                return "ethical_dilemma must not be null or blank"
            }
            if (stakeholders.isNullOrEmpty()) {
                return "stakeholders must not be null or empty"
            }
            ethical_frameworks?.forEach { framework ->
                if (framework.isBlank()) {
                    return "Invalid ethical_frameworks entry: must not be blank"
                }
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
 EthicalReasoning - Analyze a dilemma through multiple ethical frameworks
  ** Optionally specify input files (supports glob patterns) to provide context
  ** Files will be read and included in the analysis
  ** Specify the ethical dilemma and stakeholders
  ** Specify the ethical dilemma and stakeholders
  ** Choose from frameworks: utilitarianism, deontology, virtue_ethics, care_ethics, rights_based
  ** Provides analysis from each framework's perspective
  ** Synthesizes findings into a balanced recommendation
  ** Highlights ethical trade-offs and points of conflict
  ** Useful for:
     - AI safety and alignment
     - Product and policy ethics
     - Corporate governance
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
      log.info("EthicalReasoning task started. Dilemma: ${executionConfig?.ethical_dilemma?.truncateForDisplay(100)}")
        // Validate configuration first
        executionConfig?.validate()?.let { validationError ->
            val errorMsg = "VALIDATION ERROR: $validationError"
          log.warn("EthicalReasoning validation failed: $validationError")
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }


        val dilemma = executionConfig?.ethical_dilemma
        if (dilemma.isNullOrBlank()) {
          val errorMsg = "ERROR: No ethical dilemma specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }
        val stakeholders = executionConfig?.stakeholders
        if (stakeholders.isNullOrEmpty()) {
          val errorMsg = "ERROR: No stakeholders specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }
        val frameworks = executionConfig?.ethical_frameworks ?: listOf("utilitarianism", "deontology", "virtue_ethics")
        val context = executionConfig?.context ?: ""

        val api = defaultSmart ?: return
      val transcript = task.transcript()
        val tabs = TabbedDisplay(task)

      task.ui.pool.submit {
        val startTime = System.currentTimeMillis()
        val overviewTask = tabs.newTask("Overview")
        try {
            transcript?.write("# Ethical Reasoning Analysis\n\n".toByteArray())
            transcript?.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n".toByteArray()
            )
            transcript?.write("**Dilemma:** $dilemma\n\n".toByteArray())
            transcript?.write("**Stakeholders:** ${stakeholders.joinToString(", ")}\n\n".toByteArray())
            transcript?.write("**Frameworks:** ${frameworks.joinToString(", ")}\n\n".toByteArray())
            transcript?.write("---\n\n".toByteArray())

            var overviewTaskStatus = overviewTask.add(
                    """
            |## Ethical Reasoning Analysis
            |
            |**Dilemma:** ${dilemma.truncateForDisplay()}
            |
            |**Stakeholders:** ${stakeholders.joinToString(", ")}
            |
            |**Frameworks:** ${frameworks.joinToString(", ")}
            |
            |**Status:** 🔄 Initializing analysis...
        """.trimMargin().renderMarkdown()
            )

            val priorContext = getPriorCode(agent.executionState)
          val fileContext = executionConfig?.input_files?.let {
            getInputFileContent(it, root)
          } ?: ""

          val fullContext = buildString {
            if (fileContext.isNotBlank()) {
              append("## File Context\n\n")
              append(fileContext)
              append("\n\n")
            }
                if (priorContext.isNotBlank()) {
                    append("## Context from Previous Tasks\n\n")
                    append(priorContext)
                    append("\n\n")
                }
                if (context.isNotBlank()) {
                    append("## Additional Context\n\n")
                    append(context)
                    append("\n\n")
                }
            }

            if (fullContext.isNotBlank()) {
                val contextTask = tabs.newTask("Context")
              contextTask.add("## Analysis Context\n\n$fullContext".renderMarkdown())
                contextTask.complete()
            }

            // Step 1: Dilemma & Stakeholder Analysis
            val analysisTask = tabs.newTask("Dilemma Analysis")
            val analysisLoading = analysisTask.add(
              "## Dilemma & Stakeholder Analysis\n\n🔄 Analyzing...".renderMarkdown()
            )

            val analysisPrompt = """
You are an expert in ethical analysis. Your first task is to deconstruct the provided ethical dilemma and analyze the stakeholders.

**Ethical Dilemma:**
$dilemma

**Stakeholders:**
${stakeholders.joinToString(", ")}

$fullContext

**Instructions:**
1.  **Deconstruct the Dilemma:** Clearly articulate the core conflict, the decision to be made, and the key ethical questions at play.
2.  **Analyze Stakeholders:** For each stakeholder, identify their interests, rights, and how they might be positively or negatively impacted by different outcomes.

Provide a detailed analysis.
      """.trimIndent()

            val chatAgent = ChatAgent(
                prompt = "",
                model = api
            )
            val dilemmaAnalysis = chatAgent.answer(listOf(analysisPrompt))
            transcript?.write("## Dilemma & Stakeholder Analysis\n\n".toByteArray())
            transcript?.write("${dilemmaAnalysis}\n\n".toByteArray())
            transcript?.write("---\n\n".toByteArray())


            analysisLoading?.clear()
          analysisTask.add("## Dilemma & Stakeholder Analysis\n\n$dilemmaAnalysis".renderMarkdown())
            analysisTask.complete()
          task.update()

            overviewTaskStatus?.clear()
            overviewTaskStatus = overviewTask.add(
                    """
            |## Ethical Reasoning Analysis
            |
            |**Dilemma:** ${dilemma.truncateForDisplay()}
            |
            |**Status:** 🔄 Applying ethical frameworks...
        """.trimMargin().renderMarkdown()
            )

            // Step 2: Framework Application
            val frameworkAnalyses = mutableMapOf<String, String>()
            for (framework in frameworks) {
                val capitalizedFramework = framework.replaceFirstChar { it.titlecase() }
                val frameworkTask = tabs.newTask("Framework: $capitalizedFramework")
                val frameworkLoading = frameworkTask.add(
                  "## $capitalizedFramework Analysis\n\n🔄 Applying framework...".renderMarkdown()
                )

                val frameworkPrompt = """
You are an expert specializing in the **$capitalizedFramework** ethical framework.
Analyze the following dilemma from this specific perspective.

**Ethical Dilemma:**
$dilemma

**Stakeholders:**
${stakeholders.joinToString(", ")}

**Dilemma & Stakeholder Analysis:**
$dilemmaAnalysis

$fullContext

**Instructions:**
1.  **Explain Core Principles:** Briefly explain the core principles of $capitalizedFramework ethics.
2.  **Apply to Dilemma:** Apply these principles to the dilemma, considering the impact on all stakeholders.
3.  **Derive Recommendation:** Based on your analysis, what is the recommended course of action from a purely $capitalizedFramework perspective? Justify your recommendation.

Provide a clear and structured analysis.
        """.trimIndent()

                val frameworkAnalysis = chatAgent.answer(listOf<String>(frameworkPrompt))
                frameworkAnalyses[framework] = frameworkAnalysis
                transcript?.write("## $capitalizedFramework Analysis\n\n".toByteArray())
                transcript?.write("${frameworkAnalysis}\n\n".toByteArray())
                transcript?.write("---\n\n".toByteArray())


                frameworkLoading?.clear()
              frameworkTask.add("## $capitalizedFramework Analysis\n\n$frameworkAnalysis".renderMarkdown())
                frameworkTask.complete()
              task.update()
            }

            // Step 3: Synthesis and Recommendation
            val synthesisTask = tabs.newTask("Synthesis")
            val synthesisLoading = synthesisTask.add(
              "## Synthesis & Recommendation\n\n🔄 Synthesizing results...".renderMarkdown()
            )

            val synthesisPrompt = """
You are a master ethicist. Your task is to synthesize the analyses from multiple ethical frameworks to provide a final, balanced recommendation.

**Ethical Dilemma:**
$dilemma

**Analyses from Different Frameworks:**
${frameworkAnalyses.entries.joinToString("\n\n") { "### ${it.key.replaceFirstChar { char -> char.titlecase() }} Analysis\n${it.value}" }}

**Instructions:**
1.  **Compare and Contrast:** Identify areas of agreement and conflict between the recommendations from each framework.
2.  **Discuss Trade-offs:** Clearly articulate the ethical trade-offs involved in choosing one course of action over another.
3.  **Formulate Final Recommendation:** Provide a final, comprehensive recommendation. It should be practical and acknowledge the complexities and conflicting values. Justify why this recommendation is the most ethically sound approach, considering all perspectives.

Provide a detailed synthesis and a clear final recommendation.
      """.trimIndent()

            val synthesis = chatAgent.answer(listOf<String>(synthesisPrompt))
            transcript?.write("## Synthesis & Recommendation\n\n".toByteArray())
            transcript?.write("${synthesis}\n\n".toByteArray())
            transcript?.write("---\n\n".toByteArray())


            synthesisLoading?.clear()
          synthesisTask.add("## Synthesis & Recommendation\n\n$synthesis".renderMarkdown())
            synthesisTask.complete()
          task.update()

            // Final result and overview update
            val finalRecommendationSummary = chatAgent.answer(
                listOf<String>(
                    """
 Based on the following synthesis, provide a very concise summary (2-3 sentences) of the final recommendation and the key trade-off.

 **Synthesis:**
 $synthesis
        """.trimIndent()
                )
            )
            transcript?.write("## Final Recommendation Summary\n\n".toByteArray())
            transcript?.write("${finalRecommendationSummary}\n\n".toByteArray())
            transcript?.write("---\n\n".toByteArray())
            transcript?.write(
                "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )


            overviewTaskStatus?.clear()
            overviewTask.add(
                    """
            |## Ethical Reasoning Analysis
            |
            |**Dilemma:** ${dilemma.truncateForDisplay()}
            |
            |**Status:** ✅ Analysis complete
            |
            |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
            |
            |---
            |
            |### Final Recommendation Summary
            |$finalRecommendationSummary
        """.trimMargin().renderMarkdown()
            )
            overviewTask.complete()

            val finalResult = buildString {
                appendLine("# Ethical Reasoning Summary")
                appendLine()
                appendLine("**Dilemma:** ${dilemma.truncateForDisplay()}")
                appendLine()
                appendLine("**Recommendation:** $finalRecommendationSummary")
                appendLine()
                appendLine("---")
                appendLine("Detailed analysis is available in the UI tabs.")
            }
          val reportFile = "Ethical_Analysis_${SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())}.md"
          val reportUrl = task.saveFile(reportFile, transcript.toString().toByteArray())
          task.add("Analysis report generated: <a href='$reportUrl'>Download Markdown</a>")


          val duration = System.currentTimeMillis() - startTime

          log.info("EthicalReasoning task completed in ${duration}ms")
          task.safeComplete("Analysis complete.", log)
          resultFn("$finalResult\n\n---\n\nFull report saved to: `$reportFile`")

        } catch (e: Exception) {
          log.error("EthicalReasoning task failed", e)
          transcript?.write(
            """
                ## ERROR
                <details>
                <summary>Stack Trace</summary>
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent().toByteArray()
          )


          overviewTask.add(
                    """
            |## Ethical Reasoning Analysis
            |
            |**Status:** ❌ Analysis Failed
            |
            |**Error:** ${e.message}
            """.trimMargin().renderMarkdown()
            )
            task.error(e)
            task.safeComplete("Analysis failed: ${e.message}", log)
            resultFn("ERROR: Ethical reasoning analysis failed - ${e.message}")
        } finally {
          transcript?.close()
        }
    }

    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/GameTheoryTask.kt

```
package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GameTheoryTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GameTheoryTaskExecutionConfigData?
) : AbstractTask<GameTheoryTask.GameTheoryTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxOutputLengthPerField = 10000

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameTheoryTask::class.java)
        @JvmStatic val GameTheory = TaskType(
          name = "GameTheory",
          category = "Reasoning",
          taskClass = GameTheoryTask::class.java,
          executionConfigClass = GameTheoryTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Analyze strategic interactions using game theory",
          tooltipHtml = """
                        Performs comprehensive game theory analysis of strategic situations.
                        <ul>
                          <li>Analyzes game structure and player strategies</li>
                          <li>Constructs payoff matrices for strategy combinations</li>
                          <li>Identifies Nash equilibria (pure and mixed strategies)</li>
                          <li>Analyzes dominant and dominated strategies</li>
                          <li>Finds Pareto optimal outcomes</li>
                          <li>Supports repeated game analysis with trigger strategies</li>
                          <li>Provides strategic recommendations for each player</li>
                          <li>Handles cooperative, non-cooperative, zero-sum, and sequential games</li>
                          <li>Useful for competitive analysis, negotiation, and strategic planning</li>
                        </ul>
                      """,
        )
        private val textExtensions = setOf(
            "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs",
            "c", "cpp", "h", "hpp", "css", "html", "xml", "json", "yaml",
            "yml", "properties", "gradle", "maven"
        )

        fun isTextFile(file: File): Boolean {
            return textExtensions.contains(file.extension.lowercase())
        }

        fun extractDocumentContent(file: File) = try {
            file.getDocumentReader().use { reader ->
                when (reader) {
                    is PaginatedDocumentReader ->
                        reader.getText(0, reader.getPageCount())

                    else -> reader.getText()
                }
            }
        } catch (e: Exception) {
            log.warn("Failed to extract content from ${file.name}, falling back to raw text", e)
            try {
                file.readText()
            } catch (e2: Exception) {
                "Error reading file: ${e2.message}"
            }
        }


    }

    data class GameAnalysis(
        val game_type: String? = null,
        val players: List<String>? = null,
        val strategies: Map<String, List<String>>? = null,
        val payoff_matrix: String? = null,
        val nash_equilibria: List<String>? = null,
        val dominant_strategies: Map<String, String>? = null,
        val pareto_optimal_outcomes: List<String>? = null,
        val recommendations: Map<String, String>? = null
    )

    protected val codeFiles = mutableMapOf<Path, String>()

    class GameTheoryTaskExecutionConfigData(
        @Description("The strategic situation or game to analyze")
        var game_scenario: String? = null,
        @Description("List of players/agents in the game")
        var players: List<String>? = null,
        @Description("Available strategies for each player (optional, can be inferred)")
        var player_strategies: Map<String, List<String>>? = null,
        @Description("Type of game: cooperative, non-cooperative, zero-sum, repeated, sequential")
        var game_type: String? = "non-cooperative",
        @Description("Whether to construct a payoff matrix")
        var build_payoff_matrix: Boolean = true,
        @Description("Whether to identify Nash equilibria")
        var find_nash_equilibria: Boolean = true,
        @Description("Whether to analyze dominant strategies")
        var analyze_dominant_strategies: Boolean = true,
        @Description("Whether to identify Pareto optimal outcomes")
        var find_pareto_optimal: Boolean = true,
        @Description("Whether to provide strategic recommendations for each player")
        var provide_recommendations: Boolean = true,
        @Description("Whether to analyze the game as a repeated game")
        var repeated_game_analysis: Boolean = false,
        @Description("Number of iterations for repeated game analysis")
        var iterations: Int = 10,
        @Description("Additional context or constraints")
        var additional_context: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = GameTheory.name,
        task_description = task_description ?: "Analyze game theory scenario: ${game_scenario?.take(1000)}",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (game_scenario.isNullOrBlank()) {
                return "game_scenario must not be null or blank"
            }
            if (players.isNullOrEmpty()) {
                return "players list must not be null or empty"
            }
            if (players?.any { it.isBlank() } == true) {
                return "players list must not contain blank entries"
            }
            if (game_type.isNullOrBlank()) {
                return "game_type must not be null or blank"
            }
            if (game_type?.isBlank() != false) {
                return "game_type must not be blank"
            }
            if (iterations < 1) {
                return "iterations must be at least 1"
            }
            if (repeated_game_analysis && iterations < 2) {
                return "repeated_game_analysis requires at least 2 iterations"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
GameTheory - Analyze strategic interactions using game theory
  ** Specify the strategic situation or game scenario
  ** Define players and their available strategies
  ** Choose game type: cooperative, non-cooperative, zero-sum, repeated, sequential
  ** Optionally build payoff matrices
  ** Identify Nash equilibria and dominant strategies
  ** Find Pareto optimal outcomes
  ** Provide strategic recommendations for each player
  ** Analyze repeated games with multiple iterations
  ** Useful for:
     - Strategic decision making
     - Competitive analysis
     - Negotiation planning
     - Market strategy
     - Conflict resolution
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {

      val toInput = { it: String -> messages + listOf(getInputFileCode(), it).filter { it.isNotBlank() } }
      val gameScenario = executionConfig?.game_scenario
      val players = executionConfig?.players
        val startTime = System.currentTimeMillis()
      log.info("Task 'GameTheory' started for scenario: ${gameScenario?.take(50)}. Details logged to transcript.")

        if (gameScenario.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No game scenario specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        if (players.isNullOrEmpty()) {
            val errorMsg = "CONFIGURATION ERROR: No players specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val api = defaultSmart ?: return

      task.ui.pool.submit {
        val transcript = task.transcript()
        val tabs = TabbedDisplay(task)
        val overviewTask = tabs.newTask("Overview")
        task.ui

        try {
          transcript?.write("# Game Theory Analysis\n\n".toByteArray())
          transcript?.write(
            "**Started:** ${
              LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }\n\n".toByteArray()
          )

          var overviewTaskStatus = overviewTask.add(
                    """
            |## Game Theory Analysis
            |
            |**Scenario:** $gameScenario
            |
            |**Players:** ${players.joinToString(", ")}
            |
            |**Game Type:** ${executionConfig?.game_type}
            |**Status:** 🔄 Initializing analysis...
        """.trimMargin().renderMarkdown()
                )

          transcript?.write(
            """
        |## Game Theory Analysis
        |
        |**Scenario:** $gameScenario
        |**Players:** ${players.joinToString(", ")}
        |
        |**Game Type:** ${executionConfig.game_type}
        |
        |""".trimMargin().toByteArray()
            )
            overviewTask.update()

            log.debug("Retrieving prior context from execution state")
            val priorContext = getPriorCode(agent.executionState)

            // Build context section
            val contextBuilder = StringBuilder()
            if (priorContext.isNotBlank()) {
                contextBuilder.append("## Context from Previous Tasks\n\n")
                contextBuilder.append(priorContext)
                contextBuilder.append("\n\n")

                val contextTask = tabs.newTask("Context")
              contextTask.add(
                """
            |# Context from Previous Tasks
            |
            |$priorContext
            """.trimMargin().renderMarkdown()

              )
                transcript?.write(
                    """
          |## Context from Previous Tasks
          |
          |$priorContext
          |
          |""".trimMargin().toByteArray()
                )
                contextTask.update()
            }

            if (executionConfig.additional_context?.isNotBlank() == true) {
                contextBuilder.append("## Additional Context\n\n")
                contextBuilder.append(executionConfig.additional_context)
                contextBuilder.append("\n\n")
            }

            // Update overview
            overviewTaskStatus?.clear()
            overviewTaskStatus = overviewTask.add(
              """
            |## Game Theory Analysis
            |
            |**Scenario:** $gameScenario
            |
            |**Players:** ${players.joinToString(", ")}
            |
            |**Game Type:** ${executionConfig?.game_type}
            |
            |**Status:** 🔄 Analyzing game structure...
        """.trimMargin().renderMarkdown()
            )
            overviewTask.update()

            // Step 1: Analyze game structure and strategies
            var stepStartTime = System.currentTimeMillis()
            log.debug("Analyzing game structure")
            val structureTask = tabs.newTask("Game Structure")
          val structureLoading =
            structureTask.add("## Game Structure\n\n🔄 Analyzing game structure and strategies...".renderMarkdown())
            structureTask.update()

            val structurePrompt = buildStructurePrompt(gameScenario, players, contextBuilder.toString())

            val chatAgent = ChatAgent(
                prompt = structurePrompt,
                model = api,
                temperature = 0.3
            )

            val structureAnalysis = chatAgent.answer(toInput(structurePrompt))
            log.info("Structure analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${structureAnalysis.length} characters")
          transcript?.write("## Game Structure Analysis\n<details><summary>Full Analysis</summary>\n\n$structureAnalysis\n</details>\n\n".toByteArray())


            structureLoading?.clear()
            structureTask.add(
              """
            |## Game Structure Analysis
            |
            |✅ Analysis complete
            |
            |$structureAnalysis
            """.trimMargin().renderMarkdown()
            )
          structureTask.complete()
            structureTask.update()

            // Step 2: Build payoff matrix if requested
            var payoffMatrix = ""
            if (executionConfig.build_payoff_matrix) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Building payoff matrix")
                val payoffTask = tabs.newTask("Payoff Matrix")
              val payoffLoading = payoffTask.add("## Payoff Matrix\n\n🔄 Constructing payoff matrix...".renderMarkdown())
                payoffTask.update()

                val payoffPrompt = """
Based on the game structure analysis above, construct a detailed payoff matrix.

For each combination of strategies, provide:
- The outcome for each player
- Numerical payoffs if possible (or qualitative rankings)
- Brief explanation of why these payoffs result

Format the matrix clearly using markdown tables or a structured format.
If the game has more than 2 players or complex strategy spaces, provide representative examples.

Generate the payoff matrix now:
        """.trimIndent()

                payoffMatrix = chatAgent.answer(toInput(payoffPrompt))
                log.info("Payoff matrix generated in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${payoffMatrix.length} characters")
              transcript?.write("## Payoff Matrix\n<details><summary>Full Matrix</summary>\n\n$payoffMatrix\n</details>\n\n".toByteArray())


                payoffLoading?.clear()
                payoffTask.add(
                  """
            |## Payoff Matrix
            |
            |✅ Matrix constructed
            |
            |$payoffMatrix
            """.trimMargin().renderMarkdown()
                )
              payoffTask.complete()
                payoffTask.update()
            }

            // Step 3: Find Nash equilibria if requested
            var nashEquilibria = ""
            if (executionConfig.find_nash_equilibria) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Finding Nash equilibria")
                val nashTask = tabs.newTask("Nash Equilibria")
              val nashLoading = nashTask.add("## Nash Equilibria\n\n🔄 Identifying Nash equilibria...".renderMarkdown())
                nashTask.update()

                val nashPrompt = """
Based on the game structure and payoff matrix above, identify all Nash equilibria.

For each Nash equilibrium:
1. Describe the strategy profile (what each player does)
2. Explain why it's a Nash equilibrium (no player can improve by deviating unilaterally)
3. Classify it as pure strategy or mixed strategy equilibrium
4. Assess its stability and likelihood

If there are multiple equilibria, discuss:
- Which is most likely to occur
- Coordination problems between equilibria
- Pareto dominance relationships

Generate the Nash equilibrium analysis now:
        """.trimIndent()

                nashEquilibria = chatAgent.answer(toInput(nashPrompt))
                log.info("Nash equilibria analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${nashEquilibria.length} characters")
              transcript?.write("## Nash Equilibria Analysis\n<details><summary>Full Analysis</summary>\n\n$nashEquilibria\n</details>\n\n".toByteArray())


                nashLoading?.clear()
                nashTask.add(
                  """
            |## Nash Equilibria Analysis
            |
            |✅ Analysis complete
            |
            |$nashEquilibria
            """.trimMargin().renderMarkdown()
                )
              nashTask.complete()
                nashTask.update()
            }

            // Step 4: Analyze dominant strategies if requested
            var dominantStrategies = ""
            if (executionConfig.analyze_dominant_strategies) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Analyzing dominant strategies")
                val dominantTask = tabs.newTask("Dominant Strategies")
              val dominantLoading =
                dominantTask.add("## Dominant Strategies\n\n🔄 Analyzing dominant strategies...".renderMarkdown())
                dominantTask.update()

                val dominantPrompt = """
Based on the game analysis above, identify any dominant or dominated strategies.

For each player, determine:
1. **Strictly Dominant Strategies**: Strategies that are always better regardless of what others do
2. **Weakly Dominant Strategies**: Strategies that are at least as good, and sometimes better
3. **Dominated Strategies**: Strategies that are always worse than some alternative
4. **Iteratively Eliminated Strategies**: Strategies that can be eliminated through iterated dominance

Explain the strategic implications of these findings.

Generate the dominant strategy analysis now:
        """.trimIndent()

                dominantStrategies = chatAgent.answer(toInput(dominantPrompt))
                log.info("Dominant strategies analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${dominantStrategies.length} characters")
              transcript?.write("## Dominant Strategies Analysis\n<details><summary>Full Analysis</summary>\n\n$dominantStrategies\n</details>\n\n".toByteArray())


                dominantLoading?.clear()
                dominantTask.add(
                  """
            |## Dominant Strategies Analysis
            |
            |✅ Analysis complete
            |
            |$dominantStrategies
            """.trimMargin().renderMarkdown()
                )
              dominantTask.complete()
                dominantTask.update()
            }

            // Step 5: Find Pareto optimal outcomes if requested
            var paretoOptimal = ""
            if (executionConfig.find_pareto_optimal) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Finding Pareto optimal outcomes")
                val paretoTask = tabs.newTask("Pareto Optimality")
              val paretoLoading =
                paretoTask.add("## Pareto Optimality\n\n🔄 Identifying Pareto optimal outcomes...".renderMarkdown())
                paretoTask.update()

                val paretoPrompt = """
Based on the game analysis above, identify Pareto optimal outcomes.

For each outcome, determine:
1. Whether it is Pareto optimal (no player can be made better off without making another worse off)
2. Compare Pareto optimal outcomes to Nash equilibria
3. Identify any Pareto improvements over equilibrium outcomes
4. Discuss efficiency vs. equilibrium trade-offs

If there are opportunities for cooperation or coordination to reach Pareto improvements, explain them.

Generate the Pareto optimality analysis now:
        """.trimIndent()

                paretoOptimal = chatAgent.answer(toInput(paretoPrompt))
                log.info("Pareto optimality analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${paretoOptimal.length} characters")
              transcript?.write("## Pareto Optimality Analysis\n<details><summary>Full Analysis</summary>\n\n$paretoOptimal\n</details>\n\n".toByteArray())


                paretoLoading?.clear()
                paretoTask.add(
                  """
            |## Pareto Optimality Analysis
            |
            |✅ Analysis complete
            |
            |$paretoOptimal
            """.trimMargin().renderMarkdown()
                )
              paretoTask.complete()
                paretoTask.update()
            }

            // Step 6: Repeated game analysis if requested
            var repeatedGameAnalysis = ""
            if (executionConfig.repeated_game_analysis) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Analyzing repeated game dynamics")
                val repeatedTask = tabs.newTask("Repeated Game")
              val repeatedLoading =
                repeatedTask.add("## Repeated Game Analysis\n\n🔄 Analyzing repeated game dynamics...".renderMarkdown())
                repeatedTask.update()

                val repeatedPrompt = """
Analyze this game as a repeated game with ${executionConfig.iterations} iterations.

Consider:
1. **Folk Theorem**: What outcomes can be sustained as equilibria in the repeated game?
2. **Trigger Strategies**: How can players use punishment strategies to enforce cooperation?
3. **Reputation Effects**: How does reputation building affect strategic choices?
4. **Discount Factors**: How does the value of future payoffs affect current decisions?
5. **Finite vs. Infinite Horizon**: Implications of the game ending after ${executionConfig.iterations} rounds

Provide specific strategy recommendations for the repeated game context.

Generate the repeated game analysis now:
        """.trimIndent()

                repeatedGameAnalysis = chatAgent.answer(toInput(repeatedPrompt))
                log.info("Repeated game analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${repeatedGameAnalysis.length} characters")
              transcript?.write("## Repeated Game Analysis\n<details><summary>Full Analysis</summary>\n\n$repeatedGameAnalysis\n</details>\n\n".toByteArray())


                repeatedLoading?.clear()
                repeatedTask.add(
                  """
            |## Repeated Game Analysis
            |
            |✅ Analysis complete
            |
            |$repeatedGameAnalysis
            """.trimMargin().renderMarkdown()
                )
              repeatedTask.complete()
                repeatedTask.update()
            }

            // Step 7: Provide strategic recommendations if requested
            var recommendations = ""
            if (executionConfig.provide_recommendations) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Generating strategic recommendations")
                val recommendTask = tabs.newTask("Recommendations")
              val recommendLoading =
                recommendTask.add("## Strategic Recommendations\n\n🔄 Generating recommendations...".renderMarkdown())
                recommendTask.update()

                val recommendPrompt = """
Based on the complete game theory analysis above, provide strategic recommendations for each player.

For each player (${players.joinToString(", ")}), recommend:
1. **Optimal Strategy**: What strategy should they play and why?
2. **Contingent Strategies**: How should they respond to different opponent actions?
3. **Risk Assessment**: What are the risks of their recommended strategy?
4. **Coordination Opportunities**: Are there opportunities for beneficial coordination?
5. **Information Considerations**: How should they use or reveal information?

Also provide:
- **Overall Strategic Insights**: Key takeaways from the analysis
- **Potential Pitfalls**: Common mistakes to avoid
- **Implementation Guidance**: How to execute the recommended strategies

Generate the strategic recommendations now:
        """.trimIndent()

                recommendations = chatAgent.answer(toInput(recommendPrompt))
                log.info("Recommendations generated in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${recommendations.length} characters")
              transcript?.write("## Strategic Recommendations\n<details><summary>Full Recommendations</summary>\n\n$recommendations\n</details>\n\n".toByteArray())


                recommendLoading?.clear()
                recommendTask.add(
                  """
            |## Strategic Recommendations
            |
            |✅ Recommendations complete
            |
            |$recommendations
            """.trimMargin().renderMarkdown()
                )
              recommendTask.complete()
                recommendTask.update()
            }

            // Step 8: Generate comprehensive summary using ParsedAgent
            stepStartTime = System.currentTimeMillis()
            log.debug("Generating structured summary")
            val summaryTask = tabs.newTask("Summary")
          val summaryLoading = summaryTask.add("## Summary\n\n🔄 Generating comprehensive summary...".renderMarkdown())
            summaryTask.update()

            val summaryPrompt = """
Based on all the analysis above, provide a structured summary of the game theory analysis.

Extract and organize:
- Game type and key characteristics
- Players and their strategies
- Payoff structure (brief summary)
- Nash equilibria found
- Dominant strategies identified
- Pareto optimal outcomes
- Key recommendations for each player

Provide this in a clear, structured format.
      """.trimIndent()

            val parsedAgent = ParsedAgent(
                resultClass = GameAnalysis::class.java,
                prompt = summaryPrompt,
                model = api,
                temperature = 0.2,
                parsingChatter = defaultFast,
            )

            val gameAnalysis = parsedAgent.answer(toInput(summaryPrompt)).obj
            log.info("Structured summary generated in ${System.currentTimeMillis() - stepStartTime}ms")
          transcript?.write("## Game Theory Analysis Summary\n<details><summary>Structured Data</summary>\n\n$gameAnalysis\n</details>\n\n".toByteArray())


            summaryLoading?.clear()
            summaryTask.add(
              """
            |## Game Theory Analysis Summary
            |
            |✅ Summary complete
            |
            |### Game Type
            |${gameAnalysis.game_type ?: "Not specified"}
            |
            |### Players
            |${gameAnalysis.players?.joinToString(", ") ?: "Not specified"}
            |
            |### Nash Equilibria
            |${gameAnalysis.nash_equilibria?.joinToString("\n") { "- $it" } ?: "None identified"}
            |
            |### Dominant Strategies
            |${gameAnalysis.dominant_strategies?.entries?.joinToString("\n") { "- **${it.key}**: ${it.value}" } ?: "None identified"}
            |
            |### Pareto Optimal Outcomes
            |${gameAnalysis.pareto_optimal_outcomes?.joinToString("\n") { "- $it" } ?: "None identified"}
            |
            |### Strategic Recommendations
            |${gameAnalysis.recommendations?.entries?.joinToString("\n") { "- **${it.key}**: ${it.value}" } ?: "None provided"}
            """.trimMargin().renderMarkdown()
            )
          summaryTask.complete()
            summaryTask.update()

            // Update overview with completion
            overviewTaskStatus?.clear()
            overviewTask.add(
              """
            |## Game Theory Analysis
            |
            |**Scenario:** $gameScenario
            |
            |**Players:** ${players.joinToString(", ")}
            |
            |**Game Type:** ${executionConfig?.game_type}
            |
            |**Status:** ✅ Analysis complete
            |
            |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
        """.trimMargin().renderMarkdown()
            )
          overviewTask.complete()
            overviewTask.update()

            // Build final result
            val finalResult = buildString {
                appendLine("# Game Theory Analysis: $gameScenario")
                appendLine()
                appendLine("## Players")
                appendLine(players.joinToString(", "))
                appendLine()
                appendLine("## Game Type")
              appendLine(executionConfig?.game_type)
                appendLine()

                if (structureAnalysis.isNotEmpty()) {
                    appendLine("## Game Structure")
                    appendLine(structureAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }
                if (payoffMatrix.isNotEmpty()) {
                    appendLine("## Payoff Matrix")
                    appendLine(payoffMatrix.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }


                if (nashEquilibria.isNotEmpty()) {
                    appendLine("## Nash Equilibria")
                    appendLine(nashEquilibria.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }
                if (paretoOptimal.isNotEmpty()) {
                    appendLine("## Pareto Optimality")
                    appendLine(paretoOptimal.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }


                if (dominantStrategies.isNotEmpty()) {
                    appendLine("## Dominant Strategies")
                    appendLine(dominantStrategies.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }
                if (repeatedGameAnalysis.isNotEmpty()) {
                    appendLine("## Repeated Game Analysis")
                    appendLine(repeatedGameAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }


                if (recommendations.isNotEmpty()) {
                    appendLine("## Key Recommendations")
                    appendLine(recommendations.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                appendLine("---")
                appendLine("**Analysis completed in ${(System.currentTimeMillis() - startTime) / 1000}s**")
            }

            val duration = System.currentTimeMillis() - startTime
            val summary = "Game theory analysis completed for scenario: $gameScenario"
          log.info("$summary (duration: ${duration}ms, players: ${players.size}, game_type: ${executionConfig?.game_type})")
            transcript?.write("\n---\n".toByteArray())
            transcript?.write("**Analysis completed in ${duration / 1000}s**\n".toByteArray())
          transcript?.write(
            "**Finished:** ${
              LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }\n".toByteArray()
          )
            transcript?.close()

          task.complete()
            resultFn(finalResult)

        } catch (e: Exception) {
          val duration = System.currentTimeMillis() - startTime
          log.error("GameTheory task failed after ${duration}ms for scenario: $gameScenario", e)
          overviewTask.add(
                    """
            |## Game Theory Analysis
            |
            |**Status:** ❌ Analysis Failed
            |
            |**Error:** ${e.message}
            """.trimMargin().renderMarkdown()
                )
          overviewTask.complete()
          overviewTask.update()
          transcript?.write("\n---\n**ERROR:** ${e.message}\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
          transcript?.close()
          task.error(e)
          resultFn("ERROR: Game theory analysis failed - ${e.message}")
        }
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
        val content = if (!isTextFile(file)) {
          extractDocumentContent(file)
        } else {
          codeFiles[file.toPath()] ?: file.readText()
        }
        "# $relativePath\n\n```\n$content\n```"
      } catch (e: Throwable) {
        log.warn("Error reading file: $relativePath", e)
        ""
      }
    }

    private fun buildStructurePrompt(
        gameScenario: String,
        players: List<String>,
        context: String
    ): String {
        val strategiesSection = if (executionConfig?.player_strategies?.isNotEmpty() == true) {
            """
            |## Known Strategies:
            |${
                executionConfig.player_strategies?.entries?.joinToString("\n") { (player, strategies) ->
                    "- **$player**: ${strategies.joinToString(", ")}"
                }
            }
            """.trimMargin()
        } else {
            "## Note: Identify available strategies for each player from the scenario."
        }

        return """
You are an expert in game theory and strategic analysis. Your task is to analyze a strategic interaction using game theory principles.

## Game Scenario:
$gameScenario

## Players:
${players.joinToString("\n") { "- $it" }}

$strategiesSection

## Game Type:
${executionConfig?.game_type}

$context

## Analysis Instructions:
1. **Identify the Game Structure**:
   - What type of game is this? (cooperative, non-cooperative, zero-sum, constant-sum, sequential, simultaneous)
   - Is it a one-shot game or repeated game?
   - Is there perfect or imperfect information?
   - Are there any asymmetries between players?

2. **Define Strategy Spaces**:
   - What are the available strategies for each player?
   - Are strategies discrete or continuous?
   - Are there any constraints on strategy choices?

3. **Characterize Payoffs**:
   - What are the objectives of each player?
   - How do outcomes depend on strategy combinations?
   - Are payoffs transferable or non-transferable?

4. **Identify Key Features**:
   - Are there opportunities for commitment or signaling?
   - Can players communicate or coordinate?
   - Are there information asymmetries?
   - What is the timing of moves?

Provide a comprehensive analysis of the game structure.

Generate the game structure analysis now:
        """.trimIndent()
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/LLMExperimentTask.kt

```
package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

class LLMExperimentTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: LLMExperimentTaskExecutionConfigData?
) : AbstractTask<LLMExperimentTask.LLMExperimentTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class LLMExperimentTaskExecutionConfigData(
        @Description("The base prompt templates to test (use {variable} for case-insensitive substitution)")
        val prompt_templates: List<String>? = null,
        @Description("Variables to substitute in the prompt template with their possible values")
        val prompt_variables: Map<String, List<String>>? = null,
        @Description("Specific metrics to track (e.g., response_length, sentiment, contains_keywords)")
        val metrics: List<String>? = listOf("response_length", "response_time"),
        @Description("List of temperature values to test (e.g., [0.0, 0.5, 1.0])")
        val temperature_values: List<Double>? = listOf(0.1, 0.7),
        @Description("Number of times to repeat each experimental condition")
        val repetitions: Int = 3,
        @Description("Whether to analyze statistical significance of results")
        val statistical_analysis: Boolean = true,
        @Description("Significance level for statistical tests (e.g., 0.05 for 95% confidence)")
        val significance_level: Double = 0.05,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = LLMExperiment.name,
        task_description = "Conduct LLM experiment with ${repetitions} repetitions",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (prompt_templates.isNullOrEmpty()) {
                return "prompt_templates cannot be null or empty"
            }
            if (prompt_templates.any { it.isBlank() }) {
                return "prompt_templates cannot contain blank templates"
            }
            if (repetitions < 1 || repetitions > 100) {
                return "repetitions must be between 1 and 100, got: $repetitions"
            }
            if (temperature_values.isNullOrEmpty()) {
                return "temperature_values cannot be null or empty"
            }
            if (temperature_values.any { it < 0.0 || it > 2.0 }) {
                return "temperature_values must be between 0.0 and 2.0"
            }
            return null
        }
    }

    override fun promptSegment(): String {
        return """
 LLMExperiment - Conduct controlled experiments on LLM behavior
  ** Specify one or more prompt templates with variables for substitution
  ** Define experimental conditions (temperature(s), prompt variations)
  ** Configure number of repetitions for statistical validity
  ** Rate custom attributes in responses
  ** Analyze statistical significance of results
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
        log.info("Starting LLMExperimentTask: repetitions=${executionConfig?.repetitions}")

        // Validate configuration
        executionConfig?.validate()?.let { error ->
            log.error("Configuration validation failed: $error")
            task.safeComplete("CONFIGURATION ERROR: $error", log)
            resultFn("CONFIGURATION ERROR: $error")
            return
        }

        val promptTemplates = executionConfig?.prompt_templates ?: listOf()
        val promptVariables = executionConfig?.prompt_variables ?: emptyMap()
        val temperatureValues = executionConfig?.temperature_values ?: listOf(0.5)
        val repetitions = executionConfig?.repetitions ?: 5
        val metrics = executionConfig?.metrics ?: listOf("response_length", "response_time")
        val statisticalAnalysis = executionConfig?.statistical_analysis ?: true
        val api = defaultSmart.getChildClient(task)

        val (transcriptLink, transcriptStream) = createTranscriptFile(task)
        val transcriptWriter = transcriptStream?.bufferedWriter()
        transcriptWriter?.apply {
            write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            write("## Experimental Design\n\n")
            write("- **Prompt Templates:** ${promptTemplates.size}\n")
            promptTemplates.forEachIndexed { idx, template ->
                write("  ${idx + 1}. `${template.take(100)}${if (template.length > 100) "..." else ""}`\n")
            }
            write("- **Temperature Values:** ${temperatureValues.joinToString(", ")}\n")
            write("- **Repetitions:** $repetitions\n")
            write("### Variables\n\n")
            promptVariables.forEach { (key, values) ->
                write("- **$key:** ${values.joinToString(", ")}\n")
            }
            write("\n---\n\n")
            flush()
        }

        // Create tabbed display
        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("**Repetitions per Condition:** $repetitions")
            appendLine()
            appendLine("**Temperature Values:** ${temperatureValues.joinToString(", ")}")
            appendLine()
            appendLine("**Prompt Templates:** ${promptTemplates.size}")
            appendLine()
            appendLine("**Prompt Variables:** ${promptVariables.size} variable(s)")
            promptVariables.forEach { (key, values) ->
                appendLine("  - `$key`: ${values.size} value(s)")
            }
            appendLine()
            appendLine("**Metrics:** ${metrics.joinToString(", ")}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("*Initializing experiment...*")
        }
        overviewTask.add(overviewContent.renderMarkdown())

// Generate experimental conditions
        val conditions = generateExperimentalConditions(
            promptTemplates,
            promptVariables,
            temperatureValues
        )

        val totalTrials = conditions.size * repetitions
        log.info("Generated ${conditions.size} experimental conditions, total trials: $totalTrials")

        overviewTask.add(
            buildString {
                appendLine()
                appendLine("✅ Experimental design complete")
                appendLine()
                appendLine("**Total Conditions:** ${conditions.size}")
                appendLine()
                appendLine("**Total Trials:** $totalTrials")
                appendLine()
                appendLine("*Running experiments...*")
            }.renderMarkdown()
        )

        // Data collection
        val results = ConcurrentHashMap<Int, MutableList<ExperimentalResult>>()
        val completedTrials = AtomicInteger(0)
        val failedTrials = AtomicInteger(0)

        try {
            // Create progress tab
            val progressTask = tabs.newTask("Progress")

            conditions.forEachIndexed { conditionIndex, condition ->
                val conditionStartTime = System.currentTimeMillis()
                log.info("Running condition ${conditionIndex + 1}/${conditions.size}: temp=${condition.temperature}, vars=${condition.variables}")
                // Initialize results list for this condition
                results[conditionIndex] = mutableListOf()

                progressTask.add(
                    buildString {
                        appendLine("## Condition ${conditionIndex + 1}/${conditions.size}")
                        appendLine()
                        appendLine("**Temperature:** ${condition.temperature}")
                        appendLine()
                        appendLine("**Variables:** ${condition.variables}")
                        appendLine()
                        appendLine("**Prompt:** ${condition.prompt.take(200)}${if (condition.prompt.length > 200) "..." else ""}")
                        appendLine()
                        appendLine("*Running ${repetitions} repetitions...*")
                        appendLine()
                    }.renderMarkdown()
                )

                transcriptWriter?.apply {
                    write("## Condition ${conditionIndex + 1}: Temperature ${condition.temperature}\n\n")
                    write("**Variables:** ${condition.variables}\n\n")
                    write("**Prompt:**\n```\n${condition.prompt}\n```\n\n")
                    flush()
                }

                // Submit all repetitions for this condition to thread pool
                val futures = (0 until repetitions).map { rep ->
                    task.ui.pool.submit {
                        val trialStartTime = System.currentTimeMillis()

                        // Create agent with specific temperature
                        val experimentAgent = ChatAgent(
                            prompt = "",
                            model = api,
                            temperature = condition.temperature
                        )

                        try {
                            val response = experimentAgent.answer(listOf(condition.prompt))
                            val trialTime = System.currentTimeMillis() - trialStartTime
                            val result = ExperimentalResult(
                                conditionIndex = conditionIndex,
                                repetition = rep,
                                temperature = condition.temperature,
                                variables = condition.variables,
                                prompt = condition.prompt,
                                response = response,
                                responseTime = trialTime,
                                metrics = api.calculateMetrics(metrics, response)
                            )

                            // Thread-safe addition to results
                            results[conditionIndex]?.add(result)
                            val completed = completedTrials.incrementAndGet()

                            log.debug("Trial ${completed}/${totalTrials} complete: ${trialTime}ms, ${response.length} chars")

                            synchronized(transcriptWriter ?: Object()) {
                                transcriptWriter?.apply {
                                    write("### Repetition ${rep + 1}\n\n")
                                    write("**Response Time:** ${trialTime}ms\n\n")
                                    write("**Response:**\n```\n${response.take(500)}${if (response.length > 500) "..." else ""}\n```\n\n")
                                    flush()
                                }
                            }

                        } catch (e: Exception) {
                            failedTrials.incrementAndGet()
                            log.error("Error in trial ${completedTrials.get() + 1}", e)
                            synchronized(transcriptWriter ?: Object()) {
                                transcriptWriter?.apply {
                                    write("### Repetition ${rep + 1}\n\n")
                                    write("**ERROR:** ${e.message}\n\n")
                                    flush()
                                }
                            }
                        }
                    }
                }
                // Wait for all repetitions to complete
                futures.forEach { it.get() }
                // Get results for this condition
                val conditionResults = results[conditionIndex] ?: emptyList()

                // Generate condition summary
                val conditionSummary = generateConditionSummary(conditionResults, metrics)


                val conditionTime = System.currentTimeMillis() - conditionStartTime
                // Write summary to transcript
                transcriptWriter?.apply {
                    write("\n### Condition Summary\n\n")
                    write(conditionSummary)
                    write("\n---\n\n")
                    flush()
                }
                // Update progress tab with summary
                progressTask.add(
                    buildString {
                        appendLine("✅ Condition ${conditionIndex + 1} complete (${conditionTime / 1000.0}s)")
                        appendLine()
                        appendLine("**Successful Trials:** ${conditionResults.size}/${repetitions}")
                        appendLine()
                        appendLine("### Summary")
                        appendLine()
                        appendLine(conditionSummary)
                        appendLine()
                        appendLine("**Progress:** ${completedTrials.get()}/${totalTrials} trials (${(completedTrials.get() * 100.0 / totalTrials).toInt()}%)")
                        appendLine()
                    }.renderMarkdown()
                )

                overviewTask.add(
                    buildString {
                        appendLine()
                        appendLine("✅ Condition ${conditionIndex + 1}/${conditions.size} complete")
                    }.renderMarkdown()
                )


            }

            log.info("All trials complete, analyzing results...")
            // Flatten results for analysis
            val allResults = results.values.flatten()


            // Generate detailed statistical tables
            log.info("Generating detailed statistical tables")
            val statisticalTablesTask = tabs.newTask("Statistical Tables")
            statisticalTablesTask.add(
                buildString {
                    appendLine("# Detailed Statistical Analysis")
                    appendLine()
                    appendLine("*Computing comprehensive statistics...*")
                }.renderMarkdown()
            )
            val statisticalTables = generateStatisticalTables(
                allResults,
                conditions,
                executionConfig?.significance_level ?: 0.05
            )
            transcriptWriter?.apply {
                write("\n---\n\n## Detailed Statistical Tables\n\n")
                write(statisticalTables)
                write("\n\n")
                flush()
            }
            statisticalTablesTask.add(
                buildString {
                    appendLine()
                    appendLine(statisticalTables)
                }.renderMarkdown()
            )


            // Analysis
            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("*Analyzing results...*")
                }.renderMarkdown()
            )
            val analysisTask = tabs.newTask("Analysis")
            analysisTask.add(
                buildString {
                    appendLine("# Statistical Analysis")
                    appendLine()
                    appendLine("*Computing statistics...*")
                }.renderMarkdown()
            )
            val analysis = analyzeResults(allResults, conditions, metrics, statisticalAnalysis, statisticalTables)
            transcriptWriter?.apply {
                write("\n---\n\n## Statistical Analysis\n\n")
                write(analysis)
                write("\n\n")
                flush()
            }
            analysisTask.add(
                buildString {
                    appendLine()
                    appendLine(analysis)
                }.renderMarkdown()
            )

            // Generate insights using LLM
            log.info("Generating insights from experimental results")
            val insightsTask = tabs.newTask("Insights")

            insightsTask.add(
                buildString {
                    appendLine("# Experimental Insights")
                    appendLine()
                    appendLine("*Generating insights...*")
                }.renderMarkdown()
            )

            val insightsAgent = ChatAgent(
                prompt = """
You are an expert in experimental psychology and LLM behavior analysis.
Analyze the following experimental results and provide insights about:
1. Key patterns and trends observed
2. Implications for LLM behavior and characteristics
3. Potential biases or limitations revealed
4. Recommendations for further investigation
5. Practical applications of findings

Be specific and reference the data provided.
                """.trimIndent(),
                model = api,
                temperature = 0.3
            )

            val insightsPrompt = buildString {
                appendLine("Experimental Design:")
                appendLine("- Conditions: ${conditions.size}")
                appendLine("- Repetitions: $repetitions")
                appendLine("- Temperature values: ${temperatureValues.joinToString(", ")}")
                appendLine()
                appendLine("Results Summary:")
                appendLine(analysis)
                appendLine()
                appendLine("Sample Responses:")
                allResults.take(5).forEach { result ->
                    appendLine("- Temp ${result.temperature}: ${result.response.take(150)}...")
                }
            }

            val insights = insightsAgent.answer(listOf(insightsPrompt))

            transcriptWriter?.apply {
                write("## Insights and Interpretation\n\n")
                write(insights)
                write("\n\n")
                flush()
            }

            insightsTask.add(
                buildString {
                    appendLine()
                    appendLine(insights)
                }.renderMarkdown()
            )

            // Create summary
            val totalTime = System.currentTimeMillis() - startTime
            val avgTrialTime = if (allResults.isNotEmpty()) allResults.map { it.responseTime }.average() else 0.0
            val successRate = if (totalTrials > 0) (allResults.size * 100.0 / totalTrials) else 0.0

            val summary = buildString {
                appendLine("## Overview")
                appendLine()
                appendLine("- **Total Conditions:** ${conditions.size}")
                appendLine("- **Repetitions:** $repetitions")
                appendLine("- **Total Trials:** $totalTrials")
                appendLine("- **Successful Trials:** ${allResults.size}")
                appendLine("- **Failed Trials:** ${failedTrials.get()}")
                appendLine("- **Success Rate:** ${String.format("%.1f", successRate)}%")
                appendLine("- **Total Time:** ${totalTime / 1000.0}s")
                appendLine("- **Avg Trial Time:** ${avgTrialTime / 1000.0}s")
                appendLine(
                    "- **Throughput:** ${
                        String.format(
                            "%.2f",
                            allResults.size / (totalTime / 1000.0)
                        )
                    } trials/sec"
                )
                appendLine()
                appendLine("## Key Findings")
                appendLine()
                appendLine(analysis.take(5000))
                appendLine()
                appendLine("## Insights")
                appendLine()
                appendLine(insights.take(10000))
                appendLine()
            }

            transcriptWriter?.apply {
                write("---\n\n")
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n\n"
                )
                write("**Total Time:** ${totalTime / 1000.0}s | **Trials:** ${allResults.size}/${totalTrials} | **Avg Trial Time:** ${avgTrialTime / 1000.0}s\n")
                flush()
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Experiment Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Trials Completed:** ${allResults.size}/${totalTrials}")
                    appendLine()
                    appendLine("**Success Rate:** ${String.format("%.1f", successRate)}%")
                    appendLine()
                    appendLine(
                        "**Throughput:** ${
                            String.format(
                                "%.2f",
                                allResults.size / (totalTime / 1000.0)
                            )
                        } trials/sec"
                    )
                    appendLine()
                    appendLine(
                        "**Completed:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }.renderMarkdown()
            )

            log.info("LLMExperimentTask completed: trials=${allResults.size}/${totalTrials}, time=${totalTime}ms")

            task.complete("Completed ${allResults.size} trials across ${conditions.size} conditions in ${totalTime / 1000}s]")

            val finalMessage = buildString {
                appendLine(summary)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(
                    "Full experiment report: <a href='$transcriptLink' target='_blank'>${
                        transcriptLink.split('/', '\\').last()
                    }</a> <a href='${
                        transcriptLink.removeSuffix(".md") + ".html"
                    }' target='_blank'>html</a>"
                )
            }
            resultFn(finalMessage)

        } catch (e: Exception) {
            log.error("Error during LLM experiment", e)

            task.error(e)
            val allResults = results.values.flatten()
            transcriptWriter?.apply {
                write("\n\n---\n\n## ❌ Error Occurred\n\n")
                write("**Error:** ${e.message}\n\n")
                write("**Completed Trials:** ${allResults.size}/${totalTrials}\n\n")
                flush()
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    appendLine("**Completed Trials:** ${results.size}/${totalTrials}")
                }.renderMarkdown()
            )

            val errorOutput = buildString {
                appendLine("# Error in LLM Experiment")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                appendLine("**Completed Trials:** ${results.size}/${totalTrials}")
                appendLine("**Completed Trials:** ${allResults.size}/${totalTrials}")
                if (allResults.isNotEmpty()) {
                    appendLine()
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine("${allResults.size} trials completed before error")
                }
            }
            resultFn(errorOutput)
        }
    }

    private fun generateExperimentalConditions(
        templates: List<String>,
        variables: Map<String, List<String>>,
        temperatures: List<Double>
    ): List<ExperimentalCondition> {
        val uniqueConditions = mutableSetOf<String>()
        val conditions = mutableListOf<ExperimentalCondition>()

        // Generate all combinations of variables
        val variableCombinations = if (variables.isEmpty()) {
            listOf(emptyMap<String, String>())
        } else {
            generateVariableCombinations(variables)
        }

        // For each temperature and variable combination
        temperatures.forEach { temp ->
            templates.forEach { template ->
                variableCombinations.forEach { varMap ->
                    var prompt = template
                    varMap.forEach { (key, value) ->
                        // Case-insensitive replacement
                        prompt = prompt.replace("{$key}", value, ignoreCase = true)
                    }

                    // Create a unique key for this condition to avoid duplicates
                    val conditionKey = "$temp|$prompt"

                    if (uniqueConditions.add(conditionKey)) {
                        conditions.add(
                            ExperimentalCondition(
                                temperature = temp,
                                variables = varMap,
                                prompt = prompt
                            )
                        )
                    }
                }
            }
        }

        return conditions
    }

    private fun generateVariableCombinations(variables: Map<String, List<String>>): List<Map<String, String>> {
        if (variables.isEmpty()) return listOf(emptyMap())

        val keys = variables.keys.toList()
        val values = keys.map { variables[it]!! }

        fun combine(index: Int, current: Map<String, String>): List<Map<String, String>> {
            if (index == keys.size) return listOf(current)

            val results = mutableListOf<Map<String, String>>()
            values[index].forEach { value ->
                results.addAll(combine(index + 1, current + (keys[index] to value)))
            }
            return results
        }

        return combine(0, emptyMap())
    }

    data class MetricRating(
        val score: Double = 0.0,
        val reasoning: String? = null
    )

    data class MetricRatings(
        val ratings: Map<String, MetricRating> = emptyMap()
    )

    private fun ChatInterface.calculateMetrics(metrics: List<String>, response: String) = ParsedAgent(
        resultClass = MetricRatings::class.java,
        prompt = """
            You are an expert evaluator analyzing text responses.
            Rate the given response on the specified metrics: ${metrics.joinToString(", ")}.
            Provide a score between 0.0 and 10.0, where:
            - 0.0 = Very poor/absent
            - 5.0 = Average/moderate
            - 10.0 = Excellent/exceptional
            Be objective and consistent in your ratings.
            Provide brief reasoning for your score.
        """.trimIndent(),
        model = this,
        temperature = 0.1,
        parsingChatter = this,
    ).answer(
        listOf(
            "Response to evaluate:\n```\n$response\n```"
        )
    ).obj.ratings.map { (metric, rating) ->
        (metrics.find { it.lowercase() == metric.lowercase() } ?: metrics.minBy<String, Int> { metricName ->
            LevenshteinDistance.getDefaultInstance().apply(metricName.lowercase(), metric.lowercase())
        }) to rating.score
    }.toMap()

    private fun analyzeResults(
        results: List<ExperimentalResult>,
        conditions: List<ExperimentalCondition>,
        metrics: List<String>,
        statisticalAnalysis: Boolean,
        statisticalTables: String
    ): String {
        val analysis = StringBuilder()

        analysis.appendLine("### Summary Statistics\n")

        // Group by temperature
        val byTemperature = results.groupBy { it.temperature }
        byTemperature.forEach { (temp, tempResults) ->
            analysis.appendLine("#### Temperature: $temp\n")
            analysis.appendLine("- **Trials:** ${tempResults.size}")
            analysis.appendLine(
                "- **Avg Response Length:** ${
                    tempResults.map { it.response.length }.average().toInt()
                } chars"
            )
            analysis.appendLine(
                "- **Avg Response Time:** ${
                    tempResults.map { it.responseTime }.average().toInt()
                }ms"
            )

            // Metrics analysis
            metrics.forEach { metric ->
                val values = tempResults.mapNotNull { it.metrics[metric] }
                if (values.isNotEmpty()) {
                    val mean = values.average()
                    val stdDev = calculateStdDev(values)
                    analysis.appendLine(
                        "- **$metric:** mean=${String.format("%.2f", mean)}, sd=${
                            String.format(
                                "%.2f",
                                stdDev
                            )
                        }"
                    )
                }
            }
            analysis.appendLine()
        }

        // Variable analysis
        if (conditions.any { it.variables.isNotEmpty() }) {
            analysis.appendLine("### Variable Effects\n")

            val allVariableKeys = conditions.flatMap { it.variables.keys }.distinct()
            allVariableKeys.forEach { varKey ->
                analysis.appendLine("#### Variable: $varKey\n")

                val byVarValue = results.groupBy { it.variables[varKey] }
                byVarValue.forEach { (value, varResults) ->
                    if (value != null) {
                        analysis.appendLine(
                            "- **$value:** ${varResults.size} trials, avg length=${
                                varResults.map { it.response.length }.average().toInt()
                            }"
                        )
                    }
                }
                analysis.appendLine()
            }
        }

        // Statistical significance testing
        if (statisticalAnalysis) {
            analysis.appendLine("### Statistical Analysis\n")
            analysis.appendLine(statisticalTables)
            analysis.appendLine()
        }

        // Response diversity
        analysis.appendLine("### Response Diversity\n")
        byTemperature.forEach { (temp, tempResults) ->
            analysis.appendLine(
                "- **Temperature $temp:** ${diversity(tempResults)}"
            )
        }

        return analysis.toString()
    }

    fun diversity(results: List<ExperimentalResult>): String {
        val compressibility = results.map { a ->
            results.filter { it != a }.map { b ->
                compressibility(a.response, b.response)
            }.average()
        }.average()
        // 1 -> incompressible (high diversity)
        // 2 -> duplicate (low diversity)
        return when {
            compressibility.isNaN() -> "N/A"
            compressibility < 1.1 -> "High Diversity"
            compressibility < 1.5 -> "Moderate Diversity"
            else -> "Low Diversity"
        } + " (Compressibility: ${String.format("%.2f", compressibility)})"
    }


    private fun generateStatisticalTables(
        results: List<ExperimentalResult>,
        conditions: List<ExperimentalCondition>,
        significanceLevel: Double
    ): String {
        val tables = StringBuilder()
        // Collect all metric names (both measured and LLM-graded)
        val allMetricNames = mutableSetOf<String>()
        allMetricNames.add("response_length")
        allMetricNames.add("response_time")
        results.forEach { result ->
            allMetricNames.addAll(result.metrics.keys)
        }
        tables.appendLine("## Comprehensive Statistical Analysis")
        tables.appendLine()
        tables.appendLine("**Significance Level:** α = $significanceLevel")
        tables.appendLine()
        // Table 1: Descriptive Statistics by Temperature
        tables.appendLine("### Table 1: Descriptive Statistics by Temperature")
        tables.appendLine()
        tables.appendLine("| Temperature | N | Metric | Mean | SD | Min | Max | Median | CV |")
        tables.appendLine("|------------|---|--------|------|----|----|-----|--------|-----|")
        val byTemperature = results.groupBy { it.temperature }.toSortedMap()
        byTemperature.forEach { (temp, tempResults) ->
            // Measured metrics
            val responseLengths = tempResults.map { it.response.length.toDouble() }
            val responseTimes = tempResults.map { it.responseTime.toDouble() }
            tables.appendLine(formatStatRow(temp, tempResults.size, "Response Length (chars)", responseLengths))
            tables.appendLine(formatStatRow(temp, tempResults.size, "Response Time (ms)", responseTimes))
            // LLM-graded metrics
            allMetricNames.filter { it !in setOf("response_length", "response_time") }.forEach { metric ->
                val values = tempResults.mapNotNull { it.metrics[metric] }
                if (values.isNotEmpty()) {
                    tables.appendLine(formatStatRow(temp, tempResults.size, metric, values))
                }
            }
        }
        tables.appendLine()
        // Table 2: Pairwise Comparisons - Temperature Effects
        if (byTemperature.size > 1) {
            tables.appendLine("### Table 2: Pairwise Temperature Comparisons")
            tables.appendLine()
            tables.appendLine("| Metric | Temp 1 | Temp 2 | Mean Diff | t-statistic | df | p-value | Significant | Effect Size (Cohen's d) |")
            tables.appendLine("|--------|--------|--------|-----------|-------------|----|---------|-----------|-----------------------|")
            val temps = byTemperature.keys.sorted()
            for (i in temps.indices) {
                for (j in i + 1 until temps.size) {
                    val temp1 = temps[i]
                    val temp2 = temps[j]
                    val results1 = byTemperature[temp1]!!
                    val results2 = byTemperature[temp2]!!
                    // Response Length
                    val lengths1 = results1.map { it.response.length.toDouble() }
                    val lengths2 = results2.map { it.response.length.toDouble() }
                    tables.appendLine(
                        formatComparisonRow(
                            "Response Length",
                            temp1,
                            temp2,
                            lengths1,
                            lengths2,
                            significanceLevel
                        )
                    )
                    // Response Time
                    val times1 = results1.map { it.responseTime.toDouble() }
                    val times2 = results2.map { it.responseTime.toDouble() }
                    tables.appendLine(
                        formatComparisonRow(
                            "Response Time",
                            temp1,
                            temp2,
                            times1,
                            times2,
                            significanceLevel
                        )
                    )
                    // LLM-graded metrics
                    allMetricNames.filter { it !in setOf("response_length", "response_time") }.forEach { metric ->
                        val values1 = results1.mapNotNull { it.metrics[metric] }
                        val values2 = results2.mapNotNull { it.metrics[metric] }
                        if (values1.size >= 2 && values2.size >= 2) {
                            tables.appendLine(
                                formatComparisonRow(
                                    metric,
                                    temp1,
                                    temp2,
                                    values1,
                                    values2,
                                    significanceLevel
                                )
                            )
                        }
                    }
                }
            }
            tables.appendLine()
        }
        // Table 3: Variable Effects Analysis
        val allVariableKeys = conditions.flatMap { it.variables.keys }.distinct()
        if (allVariableKeys.isNotEmpty()) {
            tables.appendLine("### Table 3: Variable Effects Analysis")
            tables.appendLine()
            allVariableKeys.forEach { varKey ->
                tables.appendLine("#### Variable: $varKey")
                tables.appendLine()
                tables.appendLine("| Value | N | Metric | Mean | SD | 95% CI |")
                tables.appendLine("|-------|---|--------|------|----|---------| ")
                val byVarValue = results.groupBy { it.variables[varKey] }
                byVarValue.forEach { (value, varResults) ->
                    if (value != null && varResults.isNotEmpty()) {
                        // Response Length
                        val lengths = varResults.map { it.response.length.toDouble() }
                        tables.appendLine(formatVariableRow(value, varResults.size, "Response Length", lengths))
                        // Response Time
                        val times = varResults.map { it.responseTime.toDouble() }
                        tables.appendLine(formatVariableRow(value, varResults.size, "Response Time", times))
                        // LLM-graded metrics
                        allMetricNames.filter { it !in setOf("response_length", "response_time") }.forEach { metric ->
                            val values = varResults.mapNotNull { it.metrics[metric] }
                            if (values.isNotEmpty()) {
                                tables.appendLine(formatVariableRow(value, varResults.size, metric, values))
                            }
                        }
                    }
                }
                tables.appendLine()
                // Pairwise comparisons for this variable
                if (byVarValue.size > 1) {
                    tables.appendLine("**Pairwise Comparisons for $varKey:**")
                    tables.appendLine()
                    tables.appendLine("| Metric | Value 1 | Value 2 | Mean Diff | t-statistic | p-value | Significant |")
                    tables.appendLine("|--------|---------|---------|-----------|-------------|---------|------------|")
                    val values = byVarValue.keys.filterNotNull().sorted()
                    for (i in values.indices) {
                        for (j in i + 1 until values.size) {
                            val val1 = values[i]
                            val val2 = values[j]
                            val results1 = byVarValue[val1]!!
                            val results2 = byVarValue[val2]!!
                            // Response Length
                            val lengths1 = results1.map { it.response.length.toDouble() }
                            val lengths2 = results2.map { it.response.length.toDouble() }
                            tables.appendLine(
                                formatSimpleComparisonRow(
                                    "Response Length",
                                    val1,
                                    val2,
                                    lengths1,
                                    lengths2,
                                    significanceLevel
                                )
                            )
                            // LLM-graded metrics
                            allMetricNames.filter { it !in setOf("response_length", "response_time") }
                                .forEach { metric ->
                                    val metricVals1 = results1.mapNotNull { it.metrics[metric] }
                                    val metricVals2 = results2.mapNotNull { it.metrics[metric] }
                                    if (metricVals1.size >= 2 && metricVals2.size >= 2) {
                                        tables.appendLine(
                                            formatSimpleComparisonRow(
                                                metric,
                                                val1,
                                                val2,
                                                metricVals1,
                                                metricVals2,
                                                significanceLevel
                                            )
                                        )
                                    }
                                }
                        }
                    }
                    tables.appendLine()
                }
            }
        }
        // Table 4: Correlation Matrix
        tables.appendLine("### Table 4: Metric Correlation Matrix")
        tables.appendLine()
        tables.appendLine("Pearson correlation coefficients between all metrics:")
        tables.appendLine()
        val metricsList = allMetricNames.toList()
        tables.append("| Metric |")
        metricsList.forEach { tables.append(" $it |") }
        tables.appendLine()
        tables.append("|--------|")
        metricsList.forEach { tables.append("--------|") }
        tables.appendLine()
        metricsList.forEach { metric1 ->
            tables.append("| $metric1 |")
            metricsList.forEach { metric2 ->
                val values1 = when (metric1) {
                    "response_length" -> results.map { it.response.length.toDouble() }
                    "response_time" -> results.map { it.responseTime.toDouble() }
                    else -> results.mapNotNull { it.metrics[metric1] }
                }
                val values2 = when (metric2) {
                    "response_length" -> results.map { it.response.length.toDouble() }
                    "response_time" -> results.map { it.responseTime.toDouble() }
                    else -> results.mapNotNull { it.metrics[metric2] }
                }
                if (values1.size == values2.size && values1.size >= 2) {
                    val correlation = calculateCorrelation(values1, values2)
                    tables.append(" ${String.format("%.3f", correlation)} |")
                } else {
                    tables.append(" N/A |")
                }
            }
            tables.appendLine()
        }
        tables.appendLine()
        // Table 5: Effect Sizes Summary
        tables.appendLine("### Table 5: Effect Sizes Summary")
        tables.appendLine()
        tables.appendLine("Cohen's d interpretation: Small (0.2), Medium (0.5), Large (0.8)")
        tables.appendLine()
        tables.appendLine("| Comparison Type | Factor 1 | Factor 2 | Metric | Effect Size | Interpretation |")
        tables.appendLine("|----------------|----------|----------|--------|-------------|----------------|")
        // Temperature effect sizes
        if (byTemperature.size > 1) {
            val temps = byTemperature.keys.sorted()
            for (i in temps.indices) {
                for (j in i + 1 until temps.size) {
                    val temp1 = temps[i]
                    val temp2 = temps[j]
                    val results1 = byTemperature[temp1]!!
                    val results2 = byTemperature[temp2]!!
                    allMetricNames.forEach { metric ->
                        val values1 = when (metric) {
                            "response_length" -> results1.map { it.response.length.toDouble() }
                            "response_time" -> results1.map { it.responseTime.toDouble() }
                            else -> results1.mapNotNull { it.metrics[metric] }
                        }
                        val values2 = when (metric) {
                            "response_length" -> results2.map { it.response.length.toDouble() }
                            "response_time" -> results2.map { it.responseTime.toDouble() }
                            else -> results2.mapNotNull { it.metrics[metric] }
                        }
                        if (values1.size >= 2 && values2.size >= 2) {
                            val effectSize = calculateCohenD(values1, values2)
                            val interpretation = interpretEffectSize(effectSize)
                            tables.appendLine(
                                "| Temperature | $temp1 | $temp2 | $metric | ${
                                    String.format(
                                        "%.3f",
                                        effectSize
                                    )
                                } | $interpretation |"
                            )
                        }
                    }
                }
            }
        }
        tables.appendLine()
        // Statistical test notes
        tables.appendLine("### Statistical Notes")
        tables.appendLine()
        tables.appendLine("- **t-statistic**: Measures the difference between groups relative to variation within groups")
        tables.appendLine("- **p-value**: Probability of observing this difference by chance (significant if < α)")
        tables.appendLine("- **Cohen's d**: Standardized measure of effect size (difference in standard deviations)")
        tables.appendLine("- **CV**: Coefficient of Variation (SD/Mean), measures relative variability")
        tables.appendLine("- **95% CI**: 95% Confidence Interval for the mean")
        tables.appendLine("- **df**: Degrees of freedom for t-test")
        tables.appendLine()
        return tables.toString()
    }

    private fun formatStatRow(temp: Double, n: Int, metric: String, values: List<Double>): String {
        val mean = values.average()
        val sd = calculateStdDev(values)
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val median = values.sorted()[values.size / 2]
        val cv = if (mean != 0.0) sd / mean else 0.0
        return "| $temp | $n | $metric | ${String.format("%.2f", mean)} | ${String.format("%.2f", sd)} | " +
                "${String.format("%.2f", min)} | ${String.format("%.2f", max)} | ${String.format("%.2f", median)} | " +
                "${String.format("%.3f", cv)} |"
    }

    private fun formatComparisonRow(
        metric: String,
        temp1: Double,
        temp2: Double,
        values1: List<Double>,
        values2: List<Double>,
        alpha: Double
    ): String {
        val mean1 = values1.average()
        val mean2 = values2.average()
        val meanDiff = mean1 - mean2
        val tStat = calculateTStatistic(values1, values2)
        val df = values1.size + values2.size - 2
        val pValue = calculatePValue(tStat, df)
        val significant = if (pValue < alpha) "✓" else "✗"
        val cohenD = calculateCohenD(values1, values2)
        return "| $metric | $temp1 | $temp2 | ${String.format("%.2f", meanDiff)} | " +
                "${String.format("%.3f", tStat)} | $df | ${String.format("%.4f", pValue)} | " +
                "$significant | ${String.format("%.3f", cohenD)} |"
    }

    private fun formatVariableRow(value: String, n: Int, metric: String, values: List<Double>): String {
        val mean = values.average()
        val sd = calculateStdDev(values)
        val ci = 1.96 * sd / sqrt(n.toDouble())
        val ciLower = mean - ci
        val ciUpper = mean + ci
        return "| $value | $n | $metric | ${String.format("%.2f", mean)} | ${String.format("%.2f", sd)} | " +
                "[${String.format("%.2f", ciLower)}, ${String.format("%.2f", ciUpper)}] |"
    }

    private fun formatSimpleComparisonRow(
        metric: String,
        val1: String,
        val2: String,
        values1: List<Double>,
        values2: List<Double>,
        alpha: Double
    ): String {
        val mean1 = values1.average()
        val mean2 = values2.average()
        val meanDiff = mean1 - mean2
        val tStat = calculateTStatistic(values1, values2)
        val df = values1.size + values2.size - 2
        val pValue = calculatePValue(tStat, df)
        val significant = if (pValue < alpha) "✓" else "✗"
        return "| $metric | $val1 | $val2 | ${String.format("%.2f", meanDiff)} | " +
                "${String.format("%.3f", tStat)} | ${String.format("%.4f", pValue)} | $significant |"
    }

    private fun calculateCorrelation(values1: List<Double>, values2: List<Double>): Double {
        if (values1.size != values2.size || values1.size < 2) return 0.0
        val mean1 = values1.average()
        val mean2 = values2.average()
        var numerator = 0.0
        var sum1Sq = 0.0
        var sum2Sq = 0.0
        for (i in values1.indices) {
            val diff1 = values1[i] - mean1
            val diff2 = values2[i] - mean2
            numerator += diff1 * diff2
            sum1Sq += diff1 * diff1
            sum2Sq += diff2 * diff2
        }
        val denominator = sqrt(sum1Sq * sum2Sq)
        return if (denominator > 0) numerator / denominator else 0.0
    }

    private fun calculateCohenD(values1: List<Double>, values2: List<Double>): Double {
        if (values1.size < 2 || values2.size < 2) return 0.0
        val mean1 = values1.average()
        val mean2 = values2.average()
        val var1 = values1.sumOf { (it - mean1) * (it - mean1) } / (values1.size - 1)
        val var2 = values2.sumOf { (it - mean2) * (it - mean2) } / (values2.size - 1)
        val pooledSD = sqrt((var1 + var2) / 2)
        return if (pooledSD > 0) (mean1 - mean2) / pooledSD else 0.0
    }

    private fun interpretEffectSize(d: Double): String {
        val absD = abs(d)
        return when {
            absD < 0.2 -> "Negligible"
            absD < 0.5 -> "Small"
            absD < 0.8 -> "Medium"
            else -> "Large"
        }
    }

    private fun calculatePValue(tStat: Double, df: Int): Double {
        // Simplified p-value approximation using normal distribution for large df
        // For small df, this is less accurate but provides a reasonable estimate
        val absTStat = abs(tStat)
        // For df > 30, t-distribution approximates normal distribution
        if (df > 30) {
            // Using complementary error function approximation
            val z = absTStat
            val p = 0.5 * (1.0 - erf(z / sqrt(2.0)))
            return 2.0 * p // Two-tailed test
        }
        // For smaller df, use a conservative approximation
        // This is a rough approximation and should be replaced with proper t-distribution
        val adjustment = 1.0 + (1.0 / (4.0 * df))
        val z = absTStat / adjustment
        val p = 0.5 * (1.0 - erf(z / sqrt(2.0)))
        return 2.0 * p
    }

    private fun erf(x: Double): Double {
        // Approximation of error function using Abramowitz and Stegun formula
        val sign = if (x >= 0) 1.0 else -1.0
        val absX = abs(x)
        val a1 = 0.254829592
        val a2 = -0.284496736
        val a3 = 1.421413741
        val a4 = -1.453152027
        val a5 = 1.061405429
        val p = 0.3275911
        val t = 1.0 / (1.0 + p * absX)
        val y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * exp(-absX * absX)
        return sign * y
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    private fun calculateTStatistic(sample1: List<Double>, sample2: List<Double>): Double {
        if (sample1.size < 2 || sample2.size < 2) return 0.0

        val mean1 = sample1.average()
        val mean2 = sample2.average()
        val var1 = sample1.map { (it - mean1) * (it - mean1) }.average()
        val var2 = sample2.map { (it - mean2) * (it - mean2) }.average()

        val pooledStdErr = sqrt(var1 / sample1.size + var2 / sample2.size)

        return if (pooledStdErr > 0) (mean1 - mean2) / pooledStdErr else 0.0
    }

    private fun generateConditionSummary(
        conditionResults: List<ExperimentalResult>,
        metrics: List<String>
    ): String {
        if (conditionResults.isEmpty()) {
            return "No successful trials completed for this condition."
        }
        val summary = StringBuilder()
        // Basic statistics
        summary.appendLine("**Successful Trials:** ${conditionResults.size}")
        summary.appendLine()
        // Response characteristics
        val responseLengths = conditionResults.map { it.response.length }
        val responseTimes = conditionResults.map { it.responseTime }
        summary.appendLine("**Response Length:**")
        summary.appendLine("- Mean: ${responseLengths.average().toInt()} chars")
        summary.appendLine("- Min: ${responseLengths.minOrNull() ?: 0} chars")
        summary.appendLine("- Max: ${responseLengths.maxOrNull() ?: 0} chars")
        summary.appendLine(
            "- Std Dev: ${
                String.format(
                    "%.2f",
                    calculateStdDev(responseLengths.map { it.toDouble() })
                )
            } chars"
        )
        summary.appendLine()
        summary.appendLine("**Response Time:**")
        summary.appendLine("- Mean: ${responseTimes.average().toInt()}ms")
        summary.appendLine("- Min: ${responseTimes.minOrNull() ?: 0}ms")
        summary.appendLine("- Max: ${responseTimes.maxOrNull() ?: 0}ms")
        summary.appendLine(
            "- Std Dev: ${
                String.format(
                    "%.2f",
                    calculateStdDev(responseTimes.map { it.toDouble() })
                )
            }ms"
        )
        summary.appendLine()
        // Custom metrics
        if (metrics.isNotEmpty()) {
            summary.appendLine("**Custom Metrics:**")
            summary.appendLine()
            metrics.forEach { metric ->
                val metricValues = conditionResults.mapNotNull { it.metrics[metric] }
                if (metricValues.isNotEmpty()) {
                    summary.appendLine("- $metric:")
                    summary.appendLine("  - Mean: ${String.format("%.2f", metricValues.average())}")
                    summary.appendLine("  - Min: ${String.format("%.2f", metricValues.minOrNull() ?: 0.0)}")
                    summary.appendLine("  - Max: ${String.format("%.2f", metricValues.maxOrNull() ?: 0.0)}")
                    summary.appendLine(
                        "  - Std Dev: ${
                            String.format(
                                "%.2f",
                                calculateStdDev(metricValues)
                            )
                        }"
                    )
                }
            }
        }
        // Response diversity
        summary.appendLine()
        summary.appendLine()
        summary.appendLine("**Response Diversity:** ${diversity(conditionResults)})")
        summary.appendLine()
        summary.appendLine("**Sample Responses:**")
        conditionResults.take(3).forEachIndexed { idx, result ->
            val preview = result.response.take(150).replace("\n", " ")
            summary.appendLine("${idx + 1}. \"${preview}${if (result.response.length > 150) "..." else ""}\"")
        }
        return summary.toString()
    }


    private fun createTranscriptFile(task: SessionTask): Pair<String, FileOutputStream?> {
        val transcriptFile = "llm_experiment_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.add(
            "Writing experiment report to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(
                    ".md"
                )
            }.pdf' target='_blank'>pdf</a>"
        )
        return Pair(link, markdownTranscript)
    }

    data class ExperimentalCondition(
        val temperature: Double = 0.0,
        val variables: Map<String, String> = emptyMap(),
        val prompt: String = "",
    )

    data class ExperimentalResult(
        val conditionIndex: Int = 0,
        val repetition: Int = 0,
        val temperature: Double = 0.0,
        val variables: Map<String, String> = emptyMap(),
        val prompt: String = "",
        val response: String = "",
        val responseTime: Long = 0L,
        val metrics: Map<String, Double> = emptyMap()
    )


    companion object {
        private val log: Logger = LoggerFactory.getLogger(LLMExperimentTask::class.java)
        @JvmStatic val LLMExperiment = TaskType(
          name = "LLMExperiment",
          category = "Social",
          taskClass = LLMExperimentTask::class.java,
          executionConfigClass = LLMExperimentTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Conduct controlled experiments on LLM behavior",
          tooltipHtml = """
                        Conducts rigorous experiments to characterize LLM behaviors and biases.
                        <ul>
                          <li>Experimentally-controlled prompts with variable substitution</li>
                          <li>Multiple temperature settings for comparison</li>
                          <li>Configurable repetitions for statistical validity</li>
                          <li>Custom metrics tracking (length, sentiment, patterns)</li>
                          <li>Statistical analysis including t-tests and variance</li>
                          <li>Response diversity and consistency measurement</li>
          <li>Automated insight generation from results</li>
                          <li>Comprehensive experiment reports with visualizations</li>
                          <li>Concurrent execution for faster experiment completion</li>
                        </ul>
                        <p><strong>Use cases:</strong> Bias studies, cognitive studies, logical performance analysis, consistency testing</p>
                      """,
        )

        fun compressedStringBits(str: String): Int {
            val byteStream = ByteArrayOutputStream()
            val gzipStream = GZIPOutputStream(byteStream)
            gzipStream.write(str.toByteArray(Charsets.UTF_8))
            gzipStream.close()
            return byteStream.size() * 8 // bits
        }

        /**
         * Calculates the compressibility between two strings based on their compressed sizes.
         * 1 -> incompressible (high diversity)
         * 2 -> duplicate (low diversity)
         */
        fun compressibility(strA: String, strB: String): Double =
            (compressedStringBits(strA) + compressedStringBits(strA)).toDouble() / compressedStringBits(strA + strB).toDouble()
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/LLMPollSimulationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt

class LLMPollSimulationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: LLMPollSimulationTaskExecutionConfigData?
) : AbstractTask<LLMPollSimulationTask.LLMPollSimulationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class LLMPollSimulationTaskExecutionConfigData(
        @Description("List of survey questions to ask respondents")
        var questions: List<SurveyQuestion>? = null,
        @Description("Respondent profile templates defining demographics and characteristics")
        var respondent_profiles: List<RespondentProfile>? = null,
        @Description("Number of simulated respondents to generate per profile")
        var respondents_per_profile: Int = 10,
        @Description("Whether to include demographic information in responses")
        var include_demographics: Boolean = true,
        @Description("Demographic dimensions to track (e.g., age, gender, location)")
        var demographic_dimensions: List<String>? = listOf("age", "gender", "location", "education"),
        @Description("Whether to generate cross-tabulation analysis")
        var cross_tabulation: Boolean = true,
        @Description("Whether to perform sentiment analysis on open-ended responses")
        var sentiment_analysis: Boolean = true,
        @Description("Whether to detect response biases and patterns")
        var bias_detection: Boolean = true,
        @Description("Temperature for LLM responses (0.0-1.0, higher = more varied)")
        var temperature: Double = 0.7,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending
    ) : TaskExecutionConfig(
        task_type = LLMPollSimulation.name,
        task_description = "Simulate poll with ${respondent_profiles?.size ?: 0} profiles",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (questions.isNullOrEmpty()) {
                return "questions cannot be null or empty"
            }
            if (questions!!.any { it.text.isBlank() }) {
                return "questions cannot contain blank text"
            }
            if (respondent_profiles.isNullOrEmpty()) {
                return "respondent_profiles cannot be null or empty"
            }
            if (respondents_per_profile < 1 || respondents_per_profile > 1000) {
                return "respondents_per_profile must be between 1 and 1000, got: $respondents_per_profile"
            }
            if (temperature < 0.0 || temperature > 1.0) {
                return "temperature must be between 0.0 and 1.0, got: $temperature"
            }
            // Validate question types and options
            questions!!.forEach { question ->
                when (question.type) {
                    QuestionType.MULTIPLE_CHOICE, QuestionType.SINGLE_CHOICE, QuestionType.RANKING -> {
                        if (question.options.isNullOrEmpty()) {
                            return "Question '${question.id}' of type ${question.type} must have options"
                        }
                    }

                    QuestionType.LIKERT_SCALE, QuestionType.RATING -> {
                        if (question.min == null || question.max == null) {
                            return "Question '${question.id}' of type ${question.type} must have min and max validation"
                        }
                    }

                    else -> {}
                }
            }
            return null
        }
    }

    data class SurveyQuestion(
        @Description("Unique identifier for the question")
        val id: String = UUID.randomUUID().toString(),
        @Description("The question text to present to respondents")
        val text: String = "",
        @Description("Type of question (MULTIPLE_CHOICE, SINGLE_CHOICE, LIKERT_SCALE, etc.)")
        val type: QuestionType = QuestionType.OPEN_ENDED,
        @Description("Whether this question is required")
        val required: Boolean = true,
        @Description("ID of question this depends on (for conditional logic)")
        val conditional_on: String? = null,
        @Description("Available options for choice-based questions (required for MULTIPLE_CHOICE, SINGLE_CHOICE, RANKING)")
        val options: List<String>? = null,
        @Description("Validation rule: min (required for LIKERT_SCALE, RATING)")
        val min: Int? = null,
        @Description("Validation rule: max (required for LIKERT_SCALE, RATING)")
        val max: Int? = null
    )

    enum class QuestionType {
        MULTIPLE_CHOICE,
        SINGLE_CHOICE,
        LIKERT_SCALE,
        RATING,
        OPEN_ENDED,
        YES_NO,
        RANKING,
    }

    data class RespondentProfile(
        @Description("Unique identifier for the profile")
        val id: String = UUID.randomUUID().toString(),
        @Description("Description of this respondent type")
        val description: String = "",
        @Description("Demographic attributes (age, gender, location, etc.)")
        val demographics: Map<String, String>? = null,
        @Description("Personality traits and characteristics")
        val characteristics: List<String>? = null,
        @Description("Background context and life experiences")
        val background_context: String? = null
    )

    data class SimulatedRespondent(
        val id: String = "",
        val profile: RespondentProfile = RespondentProfile(),
        val demographics: Map<String, String> = mapOf(),
        val persona_prompt: String = ""
    )

    data class SurveyResponse(
        val respondent_id: String = "",
        val answers: Map<String, Any> = mapOf(),
        val demographics: Map<String, String> = mapOf(),
        val response_time: Long = 0L,
        val reasoning: Map<String, String>? = null
    )

    data class ParsedResponse(
        val answer: Any? = null,
        val reasoning: String? = null
    )

    data class QuestionResponse(
        @Description("The answer to the question")
        val answer: Any? = null,
        @Description("Brief explanation of the reasoning behind the answer")
        val reasoning: String? = null
    )


    override fun promptSegment(): String {
        return """
LLMPollSimulation - Simulate polls and surveys with diverse AI personas
  ** Define survey questions with various types (multiple choice, Likert, open-ended)
  ** Create respondent profiles with demographics and characteristics
  ** Generate realistic survey responses from simulated personas
  ** Analyze results with cross-tabulations and statistical summaries
  ** Detect response patterns, biases, and sentiment
  ** Test survey instruments before real-world deployment
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
        log.info("Starting LLMPollSimulationTask execution")
        val transcript = task.transcript()

        // Create tabbed display
        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        try {
            // Validate configuration
            executionConfig?.validate()?.let { error ->
                val msg = "Configuration validation failed: $error"
                log.error(msg)
                task.error(Exception(msg))
                transcript?.write("## Configuration Error\n$msg\n".toByteArray())
                resultFn(msg)
                return
            }

            val questions = executionConfig?.questions ?: listOf()
            val profiles = executionConfig?.respondent_profiles ?: listOf()
        val respondentsPerProfile = executionConfig?.respondents_per_profile ?: 10
        val temperature = executionConfig?.temperature ?: 0.7
        val api = defaultSmart.getChildClient(task)

        val (transcriptLink, transcriptStream) = createTranscriptFile(task)
        val transcriptWriter = transcriptStream?.bufferedWriter()
        transcriptWriter?.apply {
            write("# Poll Simulation Report\n\n")
            write("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}\n\n")
            write("## Survey Design\n\n")
            write("- **Total Questions:** ${questions.size}\n")
            write("- **Respondent Profiles:** ${profiles.size}\n")
            write("- **Respondents per Profile:** $respondentsPerProfile\n")
            write("- **Total Respondents:** ${profiles.size * respondentsPerProfile}\n")
            write("- **Temperature:** $temperature\n\n")
            write("### Questions\n\n")
            questions.forEachIndexed { idx, q ->
                write("${idx + 1}. **${q.id}** (${q.type}): ${q.text}\n")
                if (!q.options.isNullOrEmpty()) {
                    write("   - Options: ${q.options.joinToString(", ")}\n")
                }
            }
            write("\n---\n\n")
            flush()
        }
            transcript?.write("## Survey Design\n- Questions: ${questions.size}\n- Profiles: ${profiles.size}\n- Total Respondents: ${profiles.size * respondentsPerProfile}\n\n".toByteArray())
            transcript?.write("<details><summary>Question Details</summary>\n\n".toByteArray())
            questions.forEach { q -> transcript?.write("- **${q.id}**: ${q.text}\n".toByteArray()) }
            transcript?.write("\n</details>\n\n".toByteArray())


        overviewTask.add(
            buildString {
                appendLine("# Poll Simulation Overview")
                appendLine()
                appendLine("**Survey Questions:** ${questions.size}")
                appendLine()
                appendLine("**Respondent Profiles:** ${profiles.size}")
                appendLine()
                appendLine("**Total Respondents:** ${profiles.size * respondentsPerProfile}")
                appendLine()
                appendLine("**Temperature:** $temperature")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("*Generating respondent personas...*")
            }.renderMarkdown()
        )

            // Generate respondents
            log.info("Generating ${profiles.size * respondentsPerProfile} simulated respondents")
            val respondents = generateRespondents(profiles, respondentsPerProfile)

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Generated ${respondents.size} respondent personas")
                    appendLine()
                    appendLine("*Conducting survey...*")
                }.renderMarkdown()
            )

            transcriptWriter?.apply {
                write("## Respondent Profiles\n\n")
                profiles.forEach { profile ->
                    write("### ${profile.description}\n\n")
                    write("**Demographics:** ${profile.demographics}\n\n")
                    write("**Characteristics:** ${profile.characteristics?.joinToString(", ")}\n\n")
                    if (profile.background_context != null) {
                        write("**Background:** ${profile.background_context}\n\n")
                    }
                }
                write("\n---\n\n")
                flush()
            }
            transcript?.write("## Respondent Profiles\n".toByteArray())
            profiles.forEach { profile ->
                transcript?.write("- ${profile.description} (${profile.demographics})\n".toByteArray())
            }
            transcript?.write("\n".toByteArray())

            // Progress tab
            val progressTask = tabs.newTask("Progress")

            // Conduct survey
            val responses = ConcurrentHashMap<String, SurveyResponse>()
            val completedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val totalRespondents = respondents.size

            progressTask.add(
                buildString {
                    appendLine("# Survey Progress")
                    appendLine()
                    appendLine("**Total Respondents:** $totalRespondents")
                    appendLine()
                    appendLine("*Collecting responses...*")
                }.renderMarkdown()
            )

            // Submit all surveys to thread pool
            val futures = respondents.map { respondent ->
                task.ui.pool.submit {
                    try {
                        val response = conductSurvey(respondent, questions, api, temperature)
                        responses[respondent.id] = response
                        val completed = completedCount.incrementAndGet()

                        if (completed % 10 == 0 || completed == totalRespondents) {
                            progressTask.add(
                                buildString {
                                    appendLine()
                                    appendLine("**Progress:** $completed / $totalRespondents (${(completed * 100 / totalRespondents)}%)")
                                }.renderMarkdown()
                            )
                        }

                        log.debug("Survey completed for respondent ${respondent.id}")
                    } catch (e: Exception) {
                        failedCount.incrementAndGet()
                        log.error("Error conducting survey for respondent ${respondent.id}: ${e.message}")
                        transcript?.write("<details><summary>Error: Respondent ${respondent.id}</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())
                    }
                }
            }

            // Wait for all surveys to complete
            futures.forEach { it.get() }

            val successfulResponses = responses.values.toList()
            log.info("Survey complete: ${successfulResponses.size}/${totalRespondents} successful responses")

            progressTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("✅ **Survey Complete**")
                    appendLine()
                    appendLine("**Successful Responses:** ${successfulResponses.size}")
                    appendLine("**Failed Responses:** ${failedCount.get()}")
                }.renderMarkdown()
            )

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("✅ Survey complete: ${successfulResponses.size}/${totalRespondents} responses")
                    appendLine()
                    appendLine("*Analyzing results...*")
                }.renderMarkdown()
            )

            // Write responses to transcript
            transcriptWriter?.apply {
                write("## Survey Responses\n\n")
            }
            transcript?.write("## Survey Results\n- Successful: ${successfulResponses.size}\n- Failed: ${failedCount.get()}\n\n".toByteArray())
            transcript?.write("<details><summary>Sample Responses</summary>\n\n".toByteArray())
            successfulResponses.take(5).forEach { response ->
                transcript?.write("### Respondent: ${response.respondent_id}\n".toByteArray())
                transcript?.write("Demographics: ${response.demographics}\n".toByteArray())
                response.answers.forEach { (qId, answer) ->
                    transcript?.write("- $qId: $answer\n".toByteArray())
                }
                transcript?.write("\n".toByteArray())
            }
            transcript?.write("</details>\n\n".toByteArray())
            transcriptWriter?.apply {
                write("\n---\n\n")
                flush()
            }

            // Generate descriptive statistics
            val statsTask = tabs.newTask("Statistics")

            statsTask.add(
                buildString {
                    appendLine("# Descriptive Statistics")
                    appendLine()
                    appendLine("*Computing statistics...*")
                }.renderMarkdown()
            )

            val statistics = generateDescriptiveStatistics(successfulResponses, questions)

            transcriptWriter?.apply {
                write("## Descriptive Statistics\n\n")
                write(statistics)
                write("\n\n")
                flush()
            }

            statsTask.add(
                buildString {
                    appendLine()
                    appendLine(statistics)
                }.renderMarkdown()
            )

            // Cross-tabulation analysis
            if (executionConfig?.cross_tabulation == true && executionConfig.include_demographics) {
                val crossTabTask = tabs.newTask("Cross-Tabulation")

                crossTabTask.add(
                    buildString {
                        appendLine("# Cross-Tabulation Analysis")
                        appendLine()
                        appendLine("*Generating cross-tabs...*")
                    }.renderMarkdown()
                )

                val crossTabs = generateCrossTabs(
                    successfulResponses,
                    questions,
                    executionConfig.demographic_dimensions ?: listOf()
                )

                transcriptWriter?.apply {
                    write("## Cross-Tabulation Analysis\n\n")
                    write(crossTabs)
                    write("\n\n")
                    flush()
                }

                crossTabTask.add(
                    buildString {
                        appendLine()
                        appendLine(crossTabs)
                    }.renderMarkdown()
                )
            }

            // Sentiment analysis
            if (executionConfig?.sentiment_analysis == true) {
                val sentimentTask = tabs.newTask("Sentiment")

                sentimentTask.add(
                    buildString {
                        appendLine("# Sentiment Analysis")
                        appendLine()
                        appendLine("*Analyzing sentiment...*")
                    }.renderMarkdown()
                )

                val sentiment = performSentimentAnalysis(successfulResponses, questions, api)

                transcriptWriter?.apply {
                    write("## Sentiment Analysis\n\n")
                    write(sentiment)
                    write("\n\n")
                    flush()
                }

                sentimentTask.add(
                    buildString {
                        appendLine()
                        appendLine(sentiment)
                    }.renderMarkdown()
                )
            }

            // Bias detection
            if (executionConfig?.bias_detection == true) {
                val biasTask = tabs.newTask("Bias Detection")

                biasTask.add(
                    buildString {
                        appendLine("# Bias Detection")
                        appendLine()
                        appendLine("*Detecting biases...*")
                    }.renderMarkdown()
                )

                val biases = detectBiases(successfulResponses, questions, api)

                transcriptWriter?.apply {
                    write("## Bias Detection\n\n")
                    write(biases)
                    write("\n\n")
                    flush()
                }

                biasTask.add(
                    buildString {
                        appendLine()
                        appendLine(biases)
                    }.renderMarkdown()
                )
            }

            // Generate insights
            log.info("Generating insights from poll results")
            val insightsTask = tabs.newTask("Insights")

            insightsTask.add(
                buildString {
                    appendLine("# Poll Insights")
                    appendLine()
                    appendLine("*Generating insights...*")
                }.renderMarkdown()
            )

            val insightsAgent = ChatAgent(
                prompt = """
You are an expert survey researcher and data analyst.
Analyze the following poll results and provide insights about:
1. Key findings and trends
2. Demographic patterns and differences
3. Response consistency and quality
4. Potential biases or limitations
5. Recommendations for survey improvement
6. Implications for real-world polling

Be specific and reference the data provided.
                """.trimIndent(),
                model = api,
                temperature = 0.3
            )

            val insightsPrompt = buildString {
                appendLine("Poll Design:")
                appendLine("- Questions: ${questions.size}")
                appendLine("- Respondents: ${successfulResponses.size}")
                appendLine("- Profiles: ${profiles.size}")
                appendLine()
                appendLine("Results Summary:")
                appendLine(statistics.take(2000))
                appendLine()
                appendLine("Sample Responses:")
                successfulResponses.take(3).forEach { response ->
                    appendLine("- Demographics: ${response.demographics}")
                    appendLine(
                        "  Answers: ${
                            response.answers.entries.take(3).joinToString(", ") { "${it.key}: ${it.value}" }
                        }"
                    )
                }
            }

            val insights = insightsAgent.answer(listOf(insightsPrompt))

            transcriptWriter?.apply {
                write("## Insights and Recommendations\n\n")
                write(insights)
                write("\n\n")
                flush()
            }

            insightsTask.add(
                buildString {
                    appendLine()
                    appendLine(insights)
                }.renderMarkdown()
            )

            // Final summary
            val totalTime = System.currentTimeMillis() - startTime
            val avgResponseTime = if (successfulResponses.isNotEmpty()) {
                successfulResponses.map { it.response_time }.average()
            } else 0.0

            val summary = buildString {
                appendLine("## Summary")
                appendLine()
                appendLine("- **Total Respondents:** $totalRespondents")
                appendLine("- **Successful Responses:** ${successfulResponses.size}")
                appendLine(
                    "- **Response Rate:** ${
                        String.format(
                            "%.1f",
                            successfulResponses.size * 100.0 / totalRespondents
                        )
                    }%"
                )
                appendLine("- **Total Time:** ${totalTime / 1000.0}s")
                appendLine("- **Avg Response Time:** ${avgResponseTime / 1000.0}s")
                appendLine()
                appendLine("## Key Findings")
                appendLine()
                appendLine(statistics.take(1000))
                appendLine()
                appendLine("## Insights")
                appendLine()
                appendLine(insights.take(2000))
                appendLine()
            }

            transcriptWriter?.apply {
                write("---\n\n")
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n\n"
                )
                write("**Total Time:** ${totalTime / 1000.0}s | **Responses:** ${successfulResponses.size}/$totalRespondents\n")
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Poll Simulation Complete")
                    appendLine()
                    appendLine("**Total Time:** ${totalTime / 1000.0}s")
                    appendLine()
                    appendLine("**Responses:** ${successfulResponses.size}/$totalRespondents")
                    appendLine()
                    appendLine(
                        "**Response Rate:** ${
                            String.format(
                                "%.1f",
                                successfulResponses.size * 100.0 / totalRespondents
                            )
                        }%"
                    )
                }.renderMarkdown()
            )

            log.info("LLMPollSimulationTask completed: responses=${successfulResponses.size}/$totalRespondents, time=${totalTime}ms")

            task.complete("Completed poll simulation with ${successfulResponses.size} responses in ${totalTime / 1000}s".renderMarkdown())

            val finalMessage = buildString {
                appendLine(summary)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(
                    "Full poll report: <a href='$transcriptLink' target='_blank'>$transcriptLink</a> <a href='${
                        transcriptLink.removeSuffix(
                            ".md"
                        )
                    }.html' target='_blank'>html</a>".renderMarkdown()
                )
            }
            resultFn(finalMessage)

        } catch (e: Exception) {
            log.error("Error during poll simulation: ${e.message}")
            task.error(e)
            transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())

            transcript?.apply {
                write("\n\n---\n\n## ❌ Error Occurred\n\n".toByteArray())
                write("**Error:** ${e.message}\n\n".toByteArray())
                close()
            }

            overviewTask.add(
                buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ❌ Error Occurred")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                }.renderMarkdown()
            )

            resultFn("Error in poll simulation: ${e.message}")
        } finally {
            transcript?.close()
        }
    }

    private fun generateRespondents(
        profiles: List<RespondentProfile>,
        respondentsPerProfile: Int
    ): List<SimulatedRespondent> {
        val respondents = mutableListOf<SimulatedRespondent>()

        profiles.forEach { profile ->
            repeat(respondentsPerProfile) { index ->
                val respondentId = "${profile.id}_${index + 1}"

                // Generate or use demographics
                val demographics = if (profile.demographics != null) {
                    profile.demographics.toMutableMap()
                } else {
                    generateRealisticDemographics()
                }
                val contextVariation = contextVariations[Random().nextInt(contextVariations.size)]


                // Build persona prompt
                val personaPrompt = buildPersonaPrompt(profile, demographics, contextVariation)

                respondents.add(
                    SimulatedRespondent(
                        id = respondentId,
                        profile = profile,
                        demographics = demographics,
                        persona_prompt = personaPrompt
                    )
                )
            }
        }

        return respondents
    }

    private fun generateRealisticDemographics(): MutableMap<String, String> {
        val random = Random()
        val demographics = mutableMapOf<String, String>()

        // Age
        val ageRanges = listOf("18-24", "25-34", "35-44", "45-54", "55-64", "65+")
        demographics["age"] = ageRanges[random.nextInt(ageRanges.size)]

        // Gender
        val genders = listOf("Male", "Female", "Non-binary", "Prefer not to say")
        demographics["gender"] = genders[random.nextInt(genders.size)]

        // Location
        val locations = listOf("Urban", "Suburban", "Rural")
        demographics["location"] = locations[random.nextInt(locations.size)]

        // Education
        val educationLevels = listOf("High School", "Some College", "Bachelor's", "Master's", "Doctorate")
        demographics["education"] = educationLevels[random.nextInt(educationLevels.size)]

        return demographics
    }

    private fun buildPersonaPrompt(
        profile: RespondentProfile,
        demographics: Map<String, String>,
        contextVariation: String
    ): String {
        return """
You are participating in a survey. Please respond authentically based on your profile:

Profile: ${profile.description}

Demographics:
${demographics.entries.joinToString("\n") { "- ${it.key}: ${it.value}" }}

${if (profile.background_context != null) "Background:\n${profile.background_context}\n\n" else ""}
${if (!profile.characteristics.isNullOrEmpty()) "Characteristics:\n${profile.characteristics.joinToString("\n") { "- $it" }}\n\n" else ""}
Context: $contextVariation

Instructions:
- Answer each question honestly from your perspective
- Consider your background and values when responding
- If a question doesn't apply to you, indicate that clearly
- Maintain consistency across your responses
- Be thoughtful and realistic in your answers
        """.trimIndent()
    }

    private fun conductSurvey(
        respondent: SimulatedRespondent,
        questions: List<SurveyQuestion>,
        api: ChatInterface,
        temperature: Double
    ): SurveyResponse {
        val startTime = System.currentTimeMillis()
        val answers = mutableMapOf<String, Any>()
        val reasoning = mutableMapOf<String, String>()

        val surveyAgent = ChatAgent(
            prompt = respondent.persona_prompt,
            model = api,
            temperature = temperature
        )
        val responseParser = ParsedAgent(
            resultClass = QuestionResponse::class.java,
            prompt = """
Parse the survey response and extract the answer and reasoning.
For the answer field, return the appropriate type based on the question:
- For multiple choice: return a list of selected options
- For single choice: return the selected option as a string
- For numeric ratings/scales: return the number
- For yes/no: return "Yes" or "No"
- For rankings: return a list of options in ranked order
- For open-ended: return the full text response
            """.trimIndent(),
            model = api,
            temperature = 0.1,
            parsingChatter = api
        )


        questions.forEach { question ->
            // Check conditional logic
            if (question.conditional_on != null) {
                val conditionMet = checkCondition(question.conditional_on, answers)
                if (!conditionMet) {
                    return@forEach // Skip this question
                }
            }

            // Format question based on type
            val questionPrompt = formatQuestion(question)

            // Get response
            val response = surveyAgent.answer(listOf(questionPrompt))

            // Parse response based on question type
            val parsedResponse = try {
                val parsed = responseParser.answer(
                    listOf(
                        "Question Type: ${question.type}",
                        "Question: ${question.text}",
                        if (!question.options.isNullOrEmpty()) "Options: ${question.options.joinToString(", ")}" else "",
                        "Response: $response"
                    )
                ).obj
                ParsedResponse(parsed.answer, parsed.reasoning)
            } catch (e: Exception) {
                log.warn("Error parsing response for question ${question.id}", e)
                ParsedResponse(response, null)
            }

            answers[question.id] = parsedResponse.answer ?: ""
            if (parsedResponse.reasoning != null) {
                reasoning[question.id] = parsedResponse.reasoning
            }
        }

        val responseTime = System.currentTimeMillis() - startTime

        return SurveyResponse(
            respondent_id = respondent.id,
            answers = answers,
            demographics = respondent.demographics,
            response_time = responseTime,
            reasoning = reasoning
        )
    }

    private fun checkCondition(conditionalOn: String, answers: Map<String, Any>): Boolean {
        // Simple condition checking - can be extended for complex logic
        return answers.containsKey(conditionalOn)
    }

    private fun formatQuestion(question: SurveyQuestion): String {
        return buildString {
            appendLine(question.text)
            appendLine()

            when (question.type) {
                QuestionType.MULTIPLE_CHOICE -> {
                    appendLine("Select all that apply:")
                    question.options?.forEachIndexed { idx, option ->
                        appendLine("${idx + 1}. $option")
                    }
                    appendLine()
                    appendLine("Provide your answer as a comma-separated list of numbers (e.g., '1, 3, 4')")
                }

                QuestionType.SINGLE_CHOICE -> {
                    appendLine("Select one:")
                    question.options?.forEachIndexed { idx, option ->
                        appendLine("${idx + 1}. $option")
                    }
                    appendLine()
                    appendLine("Provide your answer as a single number (e.g., '2')")
                }

                QuestionType.LIKERT_SCALE -> {
                    val min = question.min as? Int ?: 1
                    val max = question.max as? Int ?: 5
                    appendLine("Rate on a scale from $min to $max:")
                    appendLine("$min = Strongly Disagree, $max = Strongly Agree")
                    appendLine()
                    appendLine("Provide your answer as a single number")
                }

                QuestionType.RATING -> {
                    val min = question.min as? Int ?: 1
                    val max = question.max as? Int ?: 10
                    appendLine("Rate from $min to $max")
                    appendLine()
                    appendLine("Provide your answer as a single number")
                }

                QuestionType.YES_NO -> {
                    appendLine("Answer Yes or No")
                }

                QuestionType.RANKING -> {
                    appendLine("Rank the following options in order of preference (1 = most preferred):")
                    question.options?.forEachIndexed { idx, option ->
                        appendLine("${idx + 1}. $option")
                    }
                    appendLine()
                    appendLine("Provide your ranking as a comma-separated list (e.g., '3, 1, 2, 4')")
                }

                QuestionType.OPEN_ENDED -> {
                    appendLine("Please provide your answer in your own words.")
                }

                else -> {}
            }

            if (question.required) {
                appendLine()
                appendLine("(This question is required)")
            }
        }
    }


    private fun generateDescriptiveStatistics(
        responses: List<SurveyResponse>,
        questions: List<SurveyQuestion>
    ): String {
        val stats = StringBuilder()
        stats.appendLine("### Response Summary\n")
        stats.appendLine("- **Total Responses:** ${responses.size}")
        stats.appendLine("- **Avg Response Time:** ${responses.map { it.response_time }.average().toInt()}ms")
        stats.appendLine()

        questions.forEach { question ->
            stats.appendLine("### ${question.id}: ${question.text}\n")
            val answers = responses.mapNotNull { it.answers[question.id] }
            when (question.type) {
                QuestionType.MULTIPLE_CHOICE, QuestionType.SINGLE_CHOICE -> {
                    val frequency = mutableMapOf<String, Int>()
                    answers.forEach { answer ->
                        when (answer) {
                            is String -> frequency[answer] = frequency.getOrDefault(answer, 0) + 1
                            is List<*> -> answer.forEach { item ->
                                val key = item.toString()
                                frequency[key] = frequency.getOrDefault(key, 0) + 1
                            }
                        }
                    }

                    stats.appendLine("**Response Distribution:**\n")
                    frequency.entries.sortedByDescending { it.value }.forEach { (option, count) ->
                        val percentage = (count * 100.0 / responses.size)
                        stats.appendLine("- $option: $count (${String.format("%.1f", percentage)}%)")
                    }
                    stats.appendLine()
                }

                QuestionType.LIKERT_SCALE, QuestionType.RATING -> {
                    val numericAnswers = answers.mapNotNull { (it as? Number)?.toDouble() }
                    if (numericAnswers.isNotEmpty()) {
                        val mean = numericAnswers.average()
                        val stdDev = calculateStdDev(numericAnswers)
                        val median = numericAnswers.sorted()[numericAnswers.size / 2]

                        stats.appendLine("**Statistics:**\n")
                        stats.appendLine("- Mean: ${String.format("%.2f", mean)}")
                        stats.appendLine("- Median: ${String.format("%.2f", median)}")
                        stats.appendLine("- Std Dev: ${String.format("%.2f", stdDev)}")
                        stats.appendLine("- Min: ${numericAnswers.minOrNull()}")
                        stats.appendLine("- Max: ${numericAnswers.maxOrNull()}")
                        stats.appendLine()
                    }
                }

                QuestionType.YES_NO -> {
                    val yesCount = answers.count { it.toString().equals("Yes", ignoreCase = true) }
                    val noCount = answers.count { it.toString().equals("No", ignoreCase = true) }
                    stats.appendLine("**Results:**\n")
                    stats.appendLine("- Yes: $yesCount (${String.format("%.1f", yesCount * 100.0 / responses.size)}%)")
                    stats.appendLine("- No: $noCount (${String.format("%.1f", noCount * 100.0 / responses.size)}%)")
                    stats.appendLine()
                }

                QuestionType.OPEN_ENDED -> {
                    val avgLength = answers.map { it.toString().length }.average()
                    stats.appendLine("**Text Analysis:**\n")
                    stats.appendLine("- Responses: ${answers.size}")
                    stats.appendLine("- Avg Length: ${avgLength.toInt()} characters")
                    stats.appendLine()
                    stats.appendLine("**Sample Responses:**\n")
                    answers.take(3).forEach { answer ->
                        val preview = answer.toString().take(150)
                        stats.appendLine("- \"${preview}${if (answer.toString().length > 150) "..." else ""}\"")
                    }
                    stats.appendLine()
                }

                else -> {}
            }
        }

        return stats.toString()
    }

    private fun generateCrossTabs(
        responses: List<SurveyResponse>,
        questions: List<SurveyQuestion>,
        demographicDimensions: List<String>
    ): String {
        val crossTabs = StringBuilder()

        crossTabs.appendLine("### Cross-Tabulation by Demographics\n")

        demographicDimensions.forEach { dimension ->
            crossTabs.appendLine("#### By $dimension\n")

            // Get unique values for this dimension
            val dimensionValues = responses.mapNotNull { it.demographics[dimension] }.distinct().sorted()

            questions.forEach { question ->
                if (question.type in listOf(
                        QuestionType.SINGLE_CHOICE,
                        QuestionType.YES_NO,
                        QuestionType.LIKERT_SCALE
                    )
                ) {
                    crossTabs.appendLine("**${question.id}**\n")

                    // Create cross-tab table
                    crossTabs.appendLine("| $dimension | Response | Count | % |")
                    crossTabs.appendLine("|-----------|----------|-------|---|")

                    dimensionValues.forEach { dimValue ->
                        val subset = responses.filter { it.demographics[dimension] == dimValue }
                        val answers = subset.mapNotNull { it.answers[question.id] }

                        when (question.type) {
                            QuestionType.SINGLE_CHOICE, QuestionType.YES_NO -> {
                                val frequency = answers.groupingBy { it.toString() }.eachCount()
                                frequency.forEach { (answer, count) ->
                                    val percentage = if (subset.isNotEmpty()) count * 100.0 / subset.size else 0.0
                                    crossTabs.appendLine(
                                        "| $dimValue | $answer | $count | ${
                                            String.format(
                                                "%.1f",
                                                percentage
                                            )
                                        }% |"
                                    )
                                }
                            }

                            QuestionType.LIKERT_SCALE -> {
                                val numericAnswers = answers.mapNotNull { (it as? Number)?.toDouble() }
                                if (numericAnswers.isNotEmpty()) {
                                    val mean = numericAnswers.average()
                                    crossTabs.appendLine("| $dimValue | Mean | ${String.format("%.2f", mean)} | - |")
                                }
                            }

                            else -> {}
                        }
                    }
                    crossTabs.appendLine()
                }
            }
        }

        return crossTabs.toString()
    }

    data class SentimentScore(
        val positive: Double = 0.0,
        val negative: Double = 0.0,
        val neutral: Double = 0.0,
        val overall: String = "",
    )

    private fun performSentimentAnalysis(
        responses: List<SurveyResponse>,
        questions: List<SurveyQuestion>,
        api: ChatInterface
    ): String {
        val sentiment = StringBuilder()

        sentiment.appendLine("### Sentiment Analysis of Open-Ended Responses\n")

        val openEndedQuestions = questions.filter { it.type == QuestionType.OPEN_ENDED }

        if (openEndedQuestions.isEmpty()) {
            sentiment.appendLine("*No open-ended questions to analyze*\n")
            return sentiment.toString()
        }


        val sentimentAgent = ParsedAgent(
            resultClass = SentimentScore::class.java,
            prompt = """
Analyze the sentiment of the following text response.
Provide scores for positive, negative, and neutral sentiment (0.0 to 1.0, sum to 1.0).
Also provide an overall sentiment classification: Positive, Negative, or Neutral.
            """.trimIndent(),
            model = api,
            temperature = 0.1,
            parsingChatter = api
        )

        openEndedQuestions.forEach { question ->
            sentiment.appendLine("#### ${question.id}: ${question.text}\n")

            val answers = responses.mapNotNull { it.answers[question.id]?.toString() }
            val sentiments = mutableListOf<SentimentScore>()

            answers.take(20).forEach { answer ->
                try {
                    val score = sentimentAgent.answer(listOf("Text: $answer")).obj
                    sentiments.add(score)
                } catch (e: Exception) {
                    log.warn("Error analyzing sentiment", e)
                }
            }

            if (sentiments.isNotEmpty()) {
                val avgPositive = sentiments.map { it.positive }.average()
                val avgNegative = sentiments.map { it.negative }.average()
                val avgNeutral = sentiments.map { it.neutral }.average()

                sentiment.appendLine("**Average Sentiment Scores:**\n")
                sentiment.appendLine("- Positive: ${String.format("%.2f", avgPositive)}")
                sentiment.appendLine("- Negative: ${String.format("%.2f", avgNegative)}")
                sentiment.appendLine("- Neutral: ${String.format("%.2f", avgNeutral)}")
                sentiment.appendLine()

                val overallCounts = sentiments.groupingBy { it.overall }.eachCount()
                sentiment.appendLine("**Overall Classification:**\n")
                overallCounts.forEach { (classification, count) ->
                    sentiment.appendLine(
                        "- $classification: $count (${
                            String.format(
                                "%.1f",
                                count * 100.0 / sentiments.size
                            )
                        }%)"
                    )
                }
                sentiment.appendLine()
            }
        }

        return sentiment.toString()
    }

    private fun detectBiases(
        responses: List<SurveyResponse>,
        questions: List<SurveyQuestion>,
        api: ChatInterface
    ): String {
        val biases = StringBuilder()

        biases.appendLine("### Bias Detection Analysis\n")
        // Response pattern analysis
        biases.appendLine("#### Response Patterns\n")
        val patternBiases = StringBuilder()


        questions.forEach { question ->
            when (question.type) {
                QuestionType.LIKERT_SCALE, QuestionType.RATING -> {
                    val answers = responses.mapNotNull { (it.answers[question.id] as? Number)?.toDouble() }
                    if (answers.isNotEmpty()) {
                        val mean = answers.average()
                        val stdDev = calculateStdDev(answers)

                        // Check for central tendency bias
                        val min = question.min as? Int ?: 1
                        val max = question.max as? Int ?: 5
                        val midpoint = (min + max) / 2.0

                        if (abs(mean - midpoint) < 0.5) {
                            patternBiases.appendLine(
                                "⚠️ **${question.id}**: Possible central tendency bias (mean=${
                                    String.format(
                                        "%.2f",
                                        mean
                                    )
                                }, midpoint=$midpoint)"
                            )
                        }

                        // Check for low variance (acquiescence bias)
                        if (stdDev < 0.5) {
                            patternBiases.appendLine(
                                "⚠️ **${question.id}**: Low variance detected (sd=${
                                    String.format(
                                        "%.2f",
                                        stdDev
                                    )
                                }), possible acquiescence bias"
                            )
                        }
                    }
                }

                QuestionType.MULTIPLE_CHOICE -> {
                    // Check for primacy/recency effects
                    val selections = mutableMapOf<Int, Int>()
                    responses.forEach { response ->
                        val answer = response.answers[question.id]
                        if (answer is List<*>) {
                            answer.forEach { item ->
                                val index = question.options?.indexOf(item.toString()) ?: -1
                                if (index >= 0) {
                                    selections[index] = selections.getOrDefault(index, 0) + 1
                                }
                            }
                        }
                    }

                    if (selections.isNotEmpty()) {
                        val firstOption = selections[0] ?: 0
                        val lastOption = selections[selections.keys.maxOrNull() ?: 0] ?: 0
                        val avgMiddle = selections.filter { it.key != 0 && it.key != selections.keys.maxOrNull() }
                            .values.average()

                        if (firstOption > avgMiddle * 1.5) {
                            patternBiases.appendLine(
                                "⚠️ **${question.id}**: Possible primacy effect (first option selected ${
                                    String.format(
                                        "%.1f",
                                        firstOption * 100.0 / responses.size
                                    )
                                }% of time)"
                            )
                        }
                        if (lastOption > avgMiddle * 1.5) {
                            patternBiases.appendLine(
                                "⚠️ **${question.id}**: Possible recency effect (last option selected ${
                                    String.format(
                                        "%.1f",
                                        lastOption * 100.0 / responses.size
                                    )
                                }% of time)"
                            )
                        }
                    }
                }

                else -> {}
            }
        }
        if (patternBiases.isNotEmpty()) {
            biases.append(patternBiases)
        } else {
            biases.appendLine("*No significant response pattern biases detected.*")
        }

        biases.appendLine()

       // Demographic Bias analysis
        biases.appendLine("#### Demographic Bias Analysis\n")
        val demoBiases = StringBuilder()

        val demographicDimensions = responses.flatMap { it.demographics.keys }.distinct()

        demographicDimensions.forEach { dimension ->
            val dimensionValues = responses.mapNotNull { it.demographics[dimension] }.distinct()

            if (dimensionValues.size > 1) {
                questions.filter { it.type in listOf(QuestionType.LIKERT_SCALE, QuestionType.RATING) }
                    .forEach { question ->
                        val groupMeans = dimensionValues.map { dimValue ->
                            val subset = responses.filter { it.demographics[dimension] == dimValue }
                            val answers = subset.mapNotNull { (it.answers[question.id] as? Number)?.toDouble() }
                            dimValue to if (answers.isNotEmpty()) answers.average() else 0.0
                        }.toMap()

                        val maxDiff = groupMeans.values.maxOrNull()!! - groupMeans.values.minOrNull()!!

                        if (maxDiff > 1.0) {
                            demoBiases.appendLine(
                                "⚠️ **${question.id}** by $dimension: Significant difference detected (max diff=${
                                    String.format(
                                        "%.2f",
                                        maxDiff
                                    )
                                })"
                            )
                            groupMeans.forEach { (value, mean) ->
                                demoBiases.appendLine("  - $value: ${String.format("%.2f", mean)}")
                            }
                        }
                    }
            }
        }
        if (demoBiases.isNotEmpty()) {
            biases.append(demoBiases)
        } else {
            biases.appendLine("*No significant demographic biases detected.*")
        }

        biases.appendLine()

        // Response quality indicators
        biases.appendLine("#### Response Quality\n")
        val qualityIssues = StringBuilder()

        val avgResponseTime = responses.map { it.response_time }.average()
        val fastResponses = responses.count { it.response_time < avgResponseTime * 0.5 }

        if (fastResponses > responses.size * 0.2) {
            qualityIssues.appendLine(
                "⚠️ **Fast Responses**: ${
                    String.format(
                        "%.1f",
                        fastResponses * 100.0 / responses.size
                    )
                }% of responses were unusually fast, possible satisficing behavior"
            )
        }
        if (qualityIssues.isNotEmpty()) {
            biases.append(qualityIssues)
        } else {
            biases.appendLine("*No significant response quality issues detected.*")
        }

        biases.appendLine()

        return biases.toString()
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }

    private fun createTranscriptFile(task: SessionTask): Pair<String, FileOutputStream?> {
        val reportFile = "poll_simulation_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(reportFile), task.resolveUserFile(reportFile))
        val outputStream = file?.outputStream()
        task.add(
            "Writing poll report to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a>".renderMarkdown()
        )
        return Pair(link, outputStream)
    }
    private val contextVariations = listOf(
        "You are taking this survey quickly during a short break.",
        "You are thoughtful and taking your time to answer carefully.",
        "You are slightly skeptical about the survey's purpose.",
        "You are enthusiastic about the topic.",
        "You are tired after a long day.",
        "You are distracted by your environment but trying to focus.",
        "You are very opinionated on this subject.",
        "You are neutral and trying to be objective."
    )


    companion object {
        private val log: Logger = LoggerFactory.getLogger(LLMPollSimulationTask::class.java)
        @JvmStatic val LLMPollSimulation = TaskType(
            name = "LLMPollSimulation",
            category = "Social",
            taskClass = LLMPollSimulationTask::class.java,
            executionConfigClass = LLMPollSimulationTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Simulate polls and surveys with AI personas",
            tooltipHtml = """
                        Simulates polls and surveys using LLMs to model diverse respondent personas.
                        <ul>
                          <li>Define survey questions with multiple types (choice, Likert, open-ended)</li>
                          <li>Create respondent profiles with demographics and characteristics</li>
                          <li>Generate realistic survey responses from simulated personas</li>
                          <li>Analyze results with descriptive statistics and frequency distributions</li>
                          <li>Cross-tabulation analysis by demographic dimensions</li>
                          <li>Sentiment analysis for open-ended responses</li>
                          <li>Bias detection (central tendency, primacy/recency effects)</li>
                          <li>Automated insights and recommendations</li>
                          <li>Comprehensive reports with visualizations</li>
                        </ul>
                        <p><strong>Use cases:</strong> Survey instrument testing, response pattern exploration, demographic analysis, bias detection</p>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/MultiPerspectiveAnalysisTask.kt

```
package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets

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
        var input_files: List<String>? = null,
        @Description("Whether to synthesize perspectives into unified conclusion")
        var synthesize: Boolean = true,
        @Description("Minimum confidence threshold for perspective agreement (0.0-1.0)")
        var consensus_threshold: Double = 0.7,
        @Description("Additional files for context")
        var related_files: List<String>? = null,
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
          if (!input_files.isNullOrEmpty() && input_files!!.any { it.isBlank() }) {
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


      val transcript = task.transcript()

      try {


        task.ui.pool.submit {
          try {
            val config = executionConfig ?: throw IllegalStateException("No configuration provided")
            config.validate()?.let { throw IllegalArgumentException(it) }

            val subject = config.analysis_subject!!
            val perspectives = config.perspectives!!
            val api = defaultSmart ?: throw IllegalStateException("No default smart model available")

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
              writeToTranscript(stream, "**Subject:** ${subject.truncateForDisplay(maxDescriptionLength)}\n\n")
              writeToTranscript(stream, "**Perspectives:** ${perspectives.joinToString(", ")}\n\n")
              writeToTranscript(stream, "**Consensus Threshold:** ${config.consensus_threshold}\n\n")
              writeToTranscript(stream, "---\n\n")
            }

            val contextFiles =
              getInputFileContent((config.input_files ?: emptyList()) + (config.related_files ?: emptyList()), root)
            val priorCode = getPriorCode(agent.executionState)

            transcript?.let { stream ->
              writeToTranscript(stream, "## Context\n")
              writeToTranscript(stream, "<details><summary>Context Files and Prior Code</summary>\n\n")
              writeToTranscript(stream, "### Context Files\n$contextFiles\n\n")
              writeToTranscript(stream, "### Prior Code\n$priorCode\n\n")
              writeToTranscript(stream, "</details>\n\n")
            }

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
              transcript?.let { stream -> writeToTranscript(stream, "## Synthesis\n\n$synthesis\n\n") }
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
            transcript?.write(
              """
                        <details>
                        <summary>Stack Trace</summary>

                        ```
                        ${e.stackTraceToString()}
                        ```
                        </details>
                    """.trimIndent().toByteArray()
            )
            resultFn("Error: ${e.message}")
          } finally {
            transcript?.close()
            task.complete()
          }

        }
      } catch (e: Exception) {
        log.error("Failed to submit task to pool", e)
        task.error(e)
        resultFn("Error: ${e.message}")
      }


    }

  private fun writeToTranscript(stream: java.io.OutputStream, text: String) {
    stream.write(text.toByteArray(StandardCharsets.UTF_8))
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(MultiPerspectiveAnalysisTask::class.java)
        @JvmStatic val MultiPerspectiveAnalysis = TaskType(
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/PersuasiveEssayTask.kt

```
package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

class PersuasiveEssayTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: PersuasiveEssayTaskExecutionConfigData?
) : AbstractTask<PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData, PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    class PersuasiveEssayTaskTypeConfig(
        @Description("Whether to generate images for the essay")
        var generate_images: Boolean = true,
        @Description("Whether to generate a cover image for the essay")
        var generate_cover_image: Boolean = true,
    ) : TaskTypeConfig(
        task_type = PersuasiveEssay.name,
        name = "Persuasive Essay Task"
    )

    class PersuasiveEssayTaskExecutionConfigData(
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,

        @Description("The thesis statement or position to argue for")
        var thesis: String? = null,

        @Description("The target audience (e.g., 'general public', 'academics', 'policymakers', 'business leaders')")
        var target_audience: String = "general public",

        @Description("The tone of the essay (e.g., 'formal', 'conversational', 'passionate', 'analytical')")
        var tone: String = "formal",

        @Description("Target word count for the complete essay")
        var target_word_count: Int = 1500,

        @Description("Number of main arguments to develop")
        var num_arguments: Int = 3,

        @Description("Whether to include counterarguments and rebuttals")
        var include_counterarguments: Boolean = true,

        @Description("Whether to use rhetorical devices (ethos, pathos, logos)")
        var use_rhetorical_devices: Boolean = true,

        @Description("Whether to include statistical evidence and citations")
        var include_evidence: Boolean = true,

        @Description("Whether to use analogies and examples")
        var use_analogies: Boolean = true,

        @Description("Call to action type (MUST BE one of: 'strong', 'moderate', 'reflective', 'none')")
        var call_to_action: String = "strong",

        @Description("Number of revision passes for quality improvement")
        var revision_passes: Int = 1,

        @Description("Related files or research to incorporate")
        var related_files: List<String>? = null,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = PersuasiveEssay.name,
        task_description = task_description ?: "Generate persuasive essay for thesis: '$thesis'",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (thesis.isNullOrBlank()) {
                return "thesis must not be null or blank"
            }
            if (target_word_count <= 0) {
                return "target_word_count must be positive, got: $target_word_count"
            }
            if (num_arguments < 1 || num_arguments > 10) {
                return "num_arguments must be between 1 and 10, got: $num_arguments"
            }
            if (revision_passes < 0 || revision_passes > 5) {
                return "revision_passes must be between 0 and 5, got: $revision_passes"
            }
            if (target_audience.isBlank()) {
                return "target_audience must not be blank"
            }
            if (tone.isBlank()) {
                return "tone must not be blank"
            }
            val validCallToActions = setOf("strong", "moderate", "reflective", "none")
            if (call_to_action.lowercase() !in validCallToActions) {
                return "call_to_action must be one of: ${validCallToActions.joinToString(", ")}, got: $call_to_action"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class EssayOutline(
        @Description("The essay title")
        val title: String = "",
        @Description("Hook or opening statement")
        val hook: String = "",
        @Description("Background context")
        val background: String = "",
        @Description("Clear thesis statement")
        val thesis_statement: String = "",
        @Description("Main arguments to develop")
        val arguments: List<ArgumentOutline> = emptyList(),
        @Description("Counterarguments to address")
        val counterarguments: List<CounterargumentOutline> = emptyList(),
        @Description("Conclusion strategy")
        val conclusion_strategy: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "title must not be blank"
            if (thesis_statement.isBlank()) return "thesis_statement must not be blank"
            if (arguments.isEmpty()) return "arguments must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ArgumentOutline(
        @Description("Argument number")
        val number: Int = 1,
        @Description("Main claim of this argument")
        val claim: String = "",
        @Description("Supporting points")
        val supporting_points: List<String> = emptyList(),
        @Description("Evidence types to use")
        val evidence_types: List<String> = emptyList(),
        @Description("Rhetorical approach")
        val rhetorical_approach: String = "",
        @Description("Estimated word count")
        val estimated_word_count: Int = 0
    ) : ValidatedObject

    data class CounterargumentOutline(
        @Description("The opposing viewpoint")
        val opposing_view: String = "",
        @Description("Rebuttal strategy")
        val rebuttal_strategy: String = "",
        @Description("Estimated word count")
        val estimated_word_count: Int = 0
    ) : ValidatedObject

    data class EssaySection(
        @Description("Section type")
        val section_type: String = "",
        @Description("Section content")
        val content: String = "",
        @Description("Word count")
        val word_count: Int = 0,
        @Description("Rhetorical devices used")
        val rhetorical_devices: List<String> = emptyList(),
        @Description("Key persuasive elements")
        val persuasive_elements: List<String> = emptyList()
    ) : ValidatedObject

    override fun promptSegment(): String {
        return """
 PersuasiveEssay - Generate compelling persuasive essays with structured arguments
  ** Specify the thesis statement or position to argue
  ** Optionally provide input files (supports glob patterns) to incorporate as research
  ** Define target audience and tone
  ** Set target word count and number of main arguments
  ** Enable counterarguments and rebuttals for balanced perspective
  ** Use rhetorical devices (ethos, pathos, logos) for persuasive impact
  ** Include statistical evidence and citations
  ** Incorporate analogies and examples for clarity
  ** Configure call to action strength
  ** Performs outline creation, argument development, and iterative writing
  ** Produces complete, well-structured persuasive essay
  ** Detailed output saved to files with links in summary
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {


        task.ui.pool.submit {
            val startTime = System.currentTimeMillis()
            log.info("Starting PersuasiveEssayTask for thesis: '${executionConfig?.thesis}'")
            val transcript = task.transcript()
            try {
                transcript?.write("# Persuasive Essay Generation Transcript\n\n".toByteArray())
                transcript?.write(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n\n".toByteArray()
                )
                transcript?.write("**Thesis:** ${executionConfig?.thesis}\n\n---\n\n".toByteArray())

                // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            resultFn("CONFIGURATION ERROR: $validationError")
            return@submit
        }

        val thesis = executionConfig?.thesis
        if (thesis.isNullOrBlank()) {
            log.error("No thesis specified for persuasive essay")
            task.safeComplete("CONFIGURATION ERROR: No thesis specified", log)
            resultFn("CONFIGURATION ERROR: No thesis specified")
            return@submit
        }

                val api = defaultSmart ?: return@submit

        val tabs = TabbedDisplay(task)
        // Generate cover image if enabled
        if (typeConfig!!.generate_cover_image) {
            generateCoverImage(
                task = task,
                tabs = tabs,
                title = thesis,
                audience = executionConfig.target_audience,
                tone = executionConfig.tone,
                transcript = transcript,
                orchestrationConfig = orchestrationConfig
            )
        }

        // Overview tab
        val overviewTask = tabs.newTask("Overview")

        val overviewContent = buildString {
            appendLine("# Persuasive Essay Generation")
            appendLine()
            appendLine("**Thesis:** $thesis")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Target Audience: ${executionConfig.target_audience}")
            appendLine("- Tone: ${executionConfig.tone}")
            appendLine("- Target Word Count: ${executionConfig.target_word_count}")
            appendLine("- Number of Arguments: ${executionConfig.num_arguments}")
            appendLine("- Include Counterarguments: ${if (executionConfig.include_counterarguments) "✓" else "✗"}")
            appendLine("- Use Rhetorical Devices: ${if (executionConfig.use_rhetorical_devices) "✓" else "✗"}")
            appendLine("- Include Evidence: ${if (executionConfig.include_evidence) "✓" else "✗"}")
            appendLine("- Use Analogies: ${if (executionConfig.use_analogies) "✓" else "✗"}")
            appendLine("- Call to Action: ${executionConfig.call_to_action}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Research & Outline")
            appendLine("*Analyzing thesis and creating essay structure...*")
        }
        overviewTask.add(overviewContent.renderMarkdown(true))
        overviewTask.update()
                transcript?.write("## Configuration\n\n".toByteArray())
                transcript?.write(overviewContent.toByteArray())
                transcript?.write("\n\n".toByteArray())

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Persuasive Essay: $thesis\n\n")

        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val inputFileContent =
                super.getInputFileContent(executionConfig.input_files, root, treatDocumentsAsText = true)
            val contextFiles = getContextFiles()

            if (priorContext.isNotBlank() || inputFileContent.isNotBlank() || contextFiles.isNotBlank()) {
                log.debug("Found context: priorContext=${priorContext.length} chars, contextFiles=${contextFiles.length} chars")
                val contextTask = tabs.newTask("Research Context")
                contextTask.add(
                  buildString {
                    appendLine("# Research Context")
                    appendLine()
                    if (priorContext.isNotBlank()) {
                      appendLine("## Prior Context")
                      appendLine(priorContext.truncateForDisplay(2000))
                      appendLine()
                    }
                    if (inputFileContent.isNotBlank()) {
                      appendLine("## Input Files")
                      appendLine(inputFileContent.truncateForDisplay(3000))
                      appendLine()
                    }
                    if (contextFiles.isNotBlank()) {
                      appendLine("## Related Files")
                      appendLine(contextFiles.truncateForDisplay(2000))
                    }
                  }.renderMarkdown(true)
                )
                contextTask.complete()
                transcript?.write(
                    """
                            <details>
                            <summary>Research Context</summary>
                            $priorContext
                            $inputFileContent
                            $contextFiles
                            </details>
                        """.trimIndent().toByteArray()
                )
            }

            // Phase 1: Create outline
            log.info("Phase 1: Creating essay outline")
            val outlineTask = tabs.newTask("Outline")

            outlineTask.add(
              buildString {
                appendLine("# Essay Outline")
                appendLine()
                appendLine("**Status:** Creating structured outline...")
                appendLine()
              }.renderMarkdown(true)
            )
            outlineTask.update()

            val wordsPerArgument = (executionConfig.target_word_count * 0.6).toInt() / executionConfig.num_arguments
            val counterargumentWords = if (executionConfig.include_counterarguments) {
                (executionConfig.target_word_count * 0.15).toInt()
            } else 0

            val outlineAgent = ParsedAgent(
                resultClass = EssayOutline::class.java,
                prompt = """
You are an expert in persuasive writing and rhetoric. Create a detailed outline for a persuasive essay.

Thesis: $thesis

Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}
Target Word Count: ${executionConfig.target_word_count}
Number of Arguments: ${executionConfig.num_arguments}

${if (inputFileContent.isNotBlank()) "Input Files:\n${inputFileContent.truncateForDisplay(3000)}\n" else ""}
${if (priorContext.isNotBlank()) "Research Context:\n${priorContext.truncateForDisplay(3000)}\n" else ""}
${if (contextFiles.isNotBlank()) "Additional Research:\n${contextFiles.truncateForDisplay(3000)}\n" else ""}

Create an outline with:
1. A compelling hook that grabs attention
2. Background context (100-150 words)
3. Clear, specific thesis statement
4. ${executionConfig.num_arguments} main arguments (~$wordsPerArgument words each)
${if (executionConfig.include_counterarguments) "5. 2-3 counterarguments with rebuttal strategies (~$counterargumentWords words total)" else ""}
6. Conclusion strategy

For each argument, specify:
- The main claim
- 3-4 supporting points
- Types of evidence to use (statistics, expert testimony, examples, analogies)
${if (executionConfig.use_rhetorical_devices) "- Rhetorical approach (ethos/pathos/logos emphasis)" else ""}

Ensure the outline:
- Builds a logical progression of ideas
- Addresses the ${executionConfig.target_audience} effectively
- Maintains a ${executionConfig.tone} tone
- Includes diverse types of support
- Anticipates and addresses objections
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = defaultFast
            )

            var outline = outlineAgent.answer(listOf("Generate outline")).obj

            // Validate outline
            outline.validate()?.let { validationError ->
                log.error("Outline validation failed: $validationError")
                outlineTask.error(ValidatedObject.ValidationError(validationError, outline))
                task.safeComplete("Outline validation failed: $validationError", log)
                resultFn("ERROR: Outline validation failed: $validationError")
                return@submit
            }

            log.info("Generated outline: ${outline.arguments.size} arguments, ${outline.counterarguments.size} counterarguments")

            val outlineContent = buildString {
                appendLine("## ${outline.title}")
                appendLine()
                appendLine("### Hook")
                appendLine(outline.hook)
                appendLine()
                appendLine("### Background")
                appendLine(outline.background)
                appendLine()
                appendLine("### Thesis Statement")
                appendLine("> ${outline.thesis_statement}")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("### Main Arguments")
                outline.arguments.forEach { arg ->
                    appendLine("#### Argument ${arg.number}: ${arg.claim}")
                    appendLine()
                    appendLine("**Supporting Points:**")
                    arg.supporting_points.forEach { point ->
                        appendLine("- $point")
                    }
                    appendLine()
                    appendLine("**Evidence Types:** ${arg.evidence_types.joinToString(", ")}")
                    appendLine()
                    if (arg.rhetorical_approach.isNotBlank()) {
                        appendLine("**Rhetorical Approach:** ${arg.rhetorical_approach}")
                        appendLine()
                    }
                    appendLine("**Est. Words:** ${arg.estimated_word_count}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }
                if (outline.counterarguments.isNotEmpty()) {
                    appendLine("### Counterarguments & Rebuttals")
                    outline.counterarguments.forEach { counter ->
                        appendLine("**Opposing View:** ${counter.opposing_view}")
                        appendLine()
                        appendLine("**Rebuttal Strategy:** ${counter.rebuttal_strategy}")
                        appendLine()
                        appendLine("**Est. Words:** ${counter.estimated_word_count}")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }
                appendLine("### Conclusion Strategy")
                appendLine(outline.conclusion_strategy)
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            outlineTask.add(outlineContent.renderMarkdown(true))
            outlineTask.complete()
            transcript?.write("## Essay Outline\n\n".toByteArray())
            transcript?.write(outlineContent.toByteArray())
            transcript?.write("\n\n".toByteArray())

            overviewTask.add("✅ Phase 1 Complete: Outline created\n".renderMarkdown(true))
            overviewTask.add("\n### Phase 2: Introduction\n*Writing compelling introduction...*\n".renderMarkdown(true))
            overviewTask.update()
            // Generate outline visualization image if enabled
            if (typeConfig!!.generate_images) {
                generateOutlineImage(
                    task = task,
                    tabs = tabs,
                    title = outline.title,
                    outline = outline,
                    transcript = transcript,
                    orchestrationConfig = orchestrationConfig
                )
            }

            // Phase 2: Write Introduction
            log.info("Phase 2: Writing introduction")
            val introTask = tabs.newTask("Introduction")

            introTask.add(
              buildString {
                appendLine("# Introduction")
                appendLine()
                appendLine("**Status:** Writing introduction...")
                appendLine()
              }.renderMarkdown(true)
            )
            introTask.update()

            val introAgent = ParsedAgent(
                resultClass = EssaySection::class.java,
                prompt = """
You are an expert persuasive writer. Write a compelling introduction for this essay.

Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}

Outline:
Hook: ${outline.hook}
Background: ${outline.background}
Thesis Statement: ${outline.thesis_statement}

Write an introduction (200-300 words) that:
1. Opens with the compelling hook
2. Provides necessary background context
3. Establishes credibility and relevance
4. Builds toward the thesis statement
5. Clearly states the thesis
${if (executionConfig.use_rhetorical_devices) "6. Uses appropriate rhetorical devices (ethos to establish credibility)" else ""}

Make it engaging and set the tone for the entire essay.
Speak directly to the ${executionConfig.target_audience}.
          """.trimIndent(),
                model = api,
                temperature = 0.8,
                parsingChatter = defaultFast
            )

            var introduction = introAgent.answer(listOf("Write introduction")).obj

            introTask.add(
              buildString {
                appendLine("## Introduction")
                appendLine()
                appendLine(introduction.content)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Word Count:** ${introduction.word_count}")
                if (introduction.rhetorical_devices.isNotEmpty()) {
                  appendLine()
                  appendLine("**Rhetorical Devices:** ${introduction.rhetorical_devices.joinToString(", ")}")
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
              }.renderMarkdown(true)
            )
            introTask.complete()
            transcript?.write("## Introduction\n\n".toByteArray())
            transcript?.write(introduction.content.toByteArray())
            transcript?.write("\n\n**Word Count:** ${introduction.word_count}\n\n".toByteArray())


            resultBuilder.append(introduction.content)
            resultBuilder.append("\n\n")

            overviewTask.add(
              "✅ Phase 2 Complete: Introduction written (${introduction.word_count} words)\n".renderMarkdown(
                true
              )
            )
            overviewTask.add("\n### Phase 3: Body Arguments\n*Developing main arguments...*\n".renderMarkdown(true))
            overviewTask.update()

            // Phase 3: Write each argument
            log.info("Phase 3: Writing body arguments")
            val argumentSections = mutableListOf<EssaySection>()
            var cumulativeWordCount = introduction.word_count

            outline.arguments.forEachIndexed { index, argOutline ->
                log.info("Writing argument ${index + 1}/${outline.arguments.size}: ${argOutline.claim}")

                overviewTask.add(
                  "- Argument ${index + 1}: ${argOutline.claim.truncateForDisplay(50)} ".renderMarkdown(
                    true
                  )
                )
                overviewTask.update()

                val argTask = tabs.newTask("Argument ${index + 1}")

                argTask.add(
                  buildString {
                    appendLine("# Argument ${index + 1}")
                    appendLine()
                    appendLine("**Status:** Writing argument...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                argTask.update()

                // Build context from previous arguments
                val previousContext = if (argumentSections.isNotEmpty()) {
                    buildString {
                        appendLine("## Previous Arguments Summary")
                        argumentSections.takeLast(1).forEach { prevArg ->
                            appendLine("**Previous Claim:** ${prevArg.persuasive_elements.firstOrNull() ?: ""}")
                            appendLine("**Key Points:** ${prevArg.content.take(200)}...")
                            appendLine()
                        }
                    }
                } else {
                    "This is the first argument."
                }

                val argumentAgent = ParsedAgent(
                    resultClass = EssaySection::class.java,
                    prompt = """
You are an expert persuasive writer. Write a compelling body paragraph for this argument.

Overall Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}

Argument to Develop:
Claim: ${argOutline.claim}
Supporting Points: ${argOutline.supporting_points.joinToString("; ")}
Evidence Types: ${argOutline.evidence_types.joinToString(", ")}
${if (argOutline.rhetorical_approach.isNotBlank()) "Rhetorical Approach: ${argOutline.rhetorical_approach}" else ""}
Target Words: ${argOutline.estimated_word_count}

$previousContext

Write a well-developed argument paragraph that:
1. Opens with a clear topic sentence stating the claim
2. Provides detailed supporting points
${if (executionConfig.include_evidence) "3. Includes specific evidence (statistics, expert quotes, examples)" else ""}
${if (executionConfig.use_analogies) "4. Uses analogies or concrete examples for clarity" else ""}
${if (executionConfig.use_rhetorical_devices) "5. Employs appropriate rhetorical devices (${argOutline.rhetorical_approach})" else ""}
6. Connects back to the thesis
7. Transitions smoothly to the next point

Make it persuasive, logical, and engaging.
Aim for approximately ${argOutline.estimated_word_count} words.
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = defaultFast
                )

                var argumentSection = argumentAgent.answer(listOf("Write argument")).obj
                argumentSections.add(argumentSection)
                cumulativeWordCount += argumentSection.word_count

                argTask.add(
                  buildString {
                    appendLine("## ${argOutline.claim}")
                    appendLine()
                    appendLine(argumentSection.content)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${argumentSection.word_count}")
                    if (argumentSection.rhetorical_devices.isNotEmpty()) {
                      appendLine()
                      appendLine("**Rhetorical Devices:** ${argumentSection.rhetorical_devices.joinToString(", ")}")
                    }
                    if (argumentSection.persuasive_elements.isNotEmpty()) {
                      appendLine()
                      appendLine("**Persuasive Elements:** ${argumentSection.persuasive_elements.joinToString(", ")}")
                    }
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                  }.renderMarkdown(true)
                )
                argTask.complete()
                transcript?.write("## Argument ${index + 1}: ${argOutline.claim}\n\n".toByteArray())
                transcript?.write(argumentSection.content.toByteArray())
                transcript?.write("\n\n**Word Count:** ${argumentSection.word_count}\n\n".toByteArray())


                resultBuilder.append(argumentSection.content)
                resultBuilder.append("\n\n")
                // Generate argument visualization image if enabled
                if (typeConfig!!.generate_images) {
                    generateArgumentImage(
                        task = task,
                        tabs = tabs,
                        argumentNumber = index + 1,
                        claim = argOutline.claim,
                        content = argumentSection.content,
                        transcript = transcript,
                        orchestrationConfig = orchestrationConfig
                    )
                }

                overviewTask.add("✅ (${argumentSection.word_count} words)\n".renderMarkdown(true))
                overviewTask.update()
            }

            overviewTask.add("✅ Phase 3 Complete: All arguments written\n".renderMarkdown(true))

            // Phase 4: Counterarguments (if enabled)
            if (executionConfig.include_counterarguments && outline.counterarguments.isNotEmpty()) {
                overviewTask.add("\n### Phase 4: Counterarguments\n*Addressing opposing views...*\n".renderMarkdown(true))
                overviewTask.update()

                log.info("Phase 4: Writing counterarguments and rebuttals")
                val counterTask = tabs.newTask("Counterarguments")

                counterTask.add(
                  buildString {
                    appendLine("# Counterarguments & Rebuttals")
                    appendLine()
                    appendLine("**Status:** Writing counterarguments...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                counterTask.update()

                val counterAgent = ParsedAgent(
                    resultClass = EssaySection::class.java,
                    prompt = """
You are an expert persuasive writer. Write a section addressing counterarguments.

Overall Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}

Counterarguments to Address:
${outline.counterarguments.joinToString("\n") { "- ${it.opposing_view}\n  Rebuttal: ${it.rebuttal_strategy}" }}

Write a counterargument section that:
1. Acknowledges opposing viewpoints fairly and respectfully
2. Demonstrates understanding of the other side
3. Provides strong, logical rebuttals
4. Strengthens your original thesis
5. Maintains credibility through balanced treatment

Use phrases like "While some argue...", "Critics may claim...", "However..."
Show why your position is stronger despite valid concerns.
Aim for approximately $counterargumentWords words.
          """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = defaultFast
                )

                var counterSection = counterAgent.answer(listOf("Write counterarguments")).obj
                cumulativeWordCount += counterSection.word_count

                counterTask.add(
                  buildString {
                    appendLine("## Addressing Opposing Views")
                    appendLine()
                    appendLine(counterSection.content)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Word Count:** ${counterSection.word_count}")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                  }.renderMarkdown(true)
                )
                counterTask.complete()
                transcript?.write("## Counterarguments & Rebuttals\n\n".toByteArray())
                transcript?.write(counterSection.content.toByteArray())
                transcript?.write("\n\n**Word Count:** ${counterSection.word_count}\n\n".toByteArray())

                resultBuilder.append(counterSection.content)
                resultBuilder.append("\n\n")
                // Generate counterargument visualization image if enabled
                if (typeConfig!!.generate_images) {
                    generateCounterargumentImage(
                        task = task,
                        tabs = tabs,
                        content = counterSection.content,
                        transcript = transcript,
                        orchestrationConfig = orchestrationConfig
                    )
                }

                overviewTask.add(
                  "✅ Phase 4 Complete: Counterarguments addressed (${counterSection.word_count} words)\n".renderMarkdown(
                    true
                  )
                )
            }

            // Phase 5: Conclusion
            overviewTask.add("\n### Phase 5: Conclusion\n*Writing powerful conclusion...*\n".renderMarkdown(true))
            overviewTask.update()

            log.info("Phase 5: Writing conclusion")
            val conclusionTask = tabs.newTask("Conclusion")

            conclusionTask.add(
              buildString {
                appendLine("# Conclusion")
                appendLine()
                appendLine("**Status:** Writing conclusion...")
                appendLine()
              }.renderMarkdown(true)
            )
            conclusionTask.update()

            val conclusionAgent = ParsedAgent(
                resultClass = EssaySection::class.java,
                prompt = """
You are an expert persuasive writer. Write a powerful conclusion for this essay.

Overall Thesis: $thesis
Target Audience: ${executionConfig.target_audience}
Tone: ${executionConfig.tone}
Call to Action Type: ${executionConfig.call_to_action}

Main Arguments Presented:
${
                    argumentSections.mapIndexed { i, arg ->
                        "${i + 1}. ${
                            arg.persuasive_elements.firstOrNull() ?: arg.content.take(
                                100
                            )
                        }"
                    }.joinToString("\n")
                }

Conclusion Strategy: ${outline.conclusion_strategy}

Write a conclusion (200-250 words) that:
1. Restates the thesis in fresh language
2. Synthesizes the main arguments
3. Emphasizes the significance and implications
4. Leaves a lasting impression
${
                    when (executionConfig.call_to_action.lowercase()) {
                        "strong" -> "5. Includes a powerful, specific call to action"
                        "moderate" -> "5. Suggests concrete next steps or considerations"
                        "reflective" -> "5. Invites thoughtful reflection on the topic"
                        else -> ""
                    }
                }
${if (executionConfig.use_rhetorical_devices) "6. Uses rhetorical devices for emotional impact (pathos)" else ""}

Make it memorable and motivating.
End on a strong note that reinforces your position.
          """.trimIndent(),
                model = api,
                temperature = 0.8,
                parsingChatter = defaultFast
            )

            var conclusion = conclusionAgent.answer(listOf("Write conclusion")).obj
            cumulativeWordCount += conclusion.word_count

            conclusionTask.add(
              buildString {
                appendLine("## Conclusion")
                appendLine()
                appendLine(conclusion.content)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Word Count:** ${conclusion.word_count}")
                if (conclusion.rhetorical_devices.isNotEmpty()) {
                  appendLine()
                  appendLine("**Rhetorical Devices:** ${conclusion.rhetorical_devices.joinToString(", ")}")
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
              }.renderMarkdown(true)
            )
            conclusionTask.complete()
            transcript?.write("## Conclusion\n\n".toByteArray())
            transcript?.write(conclusion.content.toByteArray())
            transcript?.write("\n\n**Word Count:** ${conclusion.word_count}\n\n".toByteArray())


            resultBuilder.append(conclusion.content)
            resultBuilder.append("\n\n")

            overviewTask.add(
              "✅ Phase 5 Complete: Conclusion written (${conclusion.word_count} words)\n".renderMarkdown(
                true
              )
            )

            // Phase 6: Revision (if enabled)
            if (executionConfig.revision_passes > 0) {
                overviewTask.add("\n### Phase 6: Revision\n*Refining and polishing...*\n".renderMarkdown(true))
                overviewTask.update()

                log.info("Phase 6: Performing ${executionConfig.revision_passes} revision pass(es)")
                val revisionTask = tabs.newTask("Revision")

                revisionTask.add(
                  buildString {
                    appendLine("# Revision Process")
                    appendLine()
                    appendLine("**Status:** Performing ${executionConfig.revision_passes} revision pass(es)...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                revisionTask.update()

                val fullEssay = resultBuilder.toString()

                repeat(executionConfig.revision_passes) { passNum ->
                    log.debug("Revision pass ${passNum + 1}/${executionConfig.revision_passes}")

                    val revisionAgent = ChatAgent(
                        prompt = """
You are an expert editor specializing in persuasive writing. Review and improve this essay.

Current Essay:
$fullEssay

Focus on:
1. Strengthening argument logic and flow
2. Enhancing persuasive language and rhetoric
3. Improving transitions between ideas
4. Ensuring consistent tone (${executionConfig.tone})
5. Polishing sentence structure and word choice
6. Verifying thesis support throughout
7. Maximizing impact on ${executionConfig.target_audience}

Maintain:
- All key arguments and evidence
- The thesis and main claims
- Approximate word count ($cumulativeWordCount words)
- The ${executionConfig.tone} tone

Provide the complete revised essay.
            """.trimIndent(),
                        model = api,
                        temperature = 0.6
                    )

                    val revisedEssay = revisionAgent.answer(listOf("Revise the essay"))
                    resultBuilder.clear()
                    resultBuilder.append(revisedEssay)

                    revisionTask.add(
                      buildString {
                        appendLine("## Revision Pass ${passNum + 1}")
                        appendLine()
                        appendLine("✅ Complete")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    revisionTask.update()
                    transcript?.write("### Revision Pass ${passNum + 1}\n\n".toByteArray())
                    transcript?.write("Completed revision pass ${passNum + 1} of ${executionConfig.revision_passes}\n\n".toByteArray())
                }
                revisionTask.complete()

                overviewTask.add(
                  "✅ Phase 6 Complete: ${executionConfig.revision_passes} revision pass(es) completed\n".renderMarkdown(
                    true
                  )
                )
            }

            // Phase 7: Final Assembly
            overviewTask.add("\n### Phase 7: Final Assembly\n*Compiling complete essay...*\n".renderMarkdown(true))
            overviewTask.update()

            log.info("Phase 7: Assembling final essay")
            val finalTask = tabs.newTask("Complete Essay")

            val finalEssay = buildString {
                appendLine("# ${outline.title}")
                appendLine()
                appendLine(resultBuilder.toString())
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Total Word Count:** $cumulativeWordCount")
                appendLine()
                appendLine("**Target Word Count:** ${executionConfig.target_word_count}")
                appendLine()
                appendLine("**Completion:** ${(cumulativeWordCount.toFloat() / executionConfig.target_word_count * 100).toInt()}%")
            }
            // Save complete essay to file
            val essayLink = task.saveFile("persuasive_essay.md", finalEssay.toByteArray(StandardCharsets.UTF_8))
            log.info("Saved complete essay to: $essayLink")


            finalTask.add(finalEssay.renderMarkdown(true))
            finalTask.complete()
            transcript?.write("## Complete Essay\n\n".toByteArray())
            transcript?.write(finalEssay.toByteArray())
            transcript?.write("\n\n".toByteArray())


            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime

            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Generation Complete")
                appendLine()
                appendLine("**Statistics:**")
                appendLine("- Total Word Count: $cumulativeWordCount")
                appendLine("- Target Word Count: ${executionConfig.target_word_count}")
                appendLine("- Completion: ${(cumulativeWordCount.toFloat() / executionConfig.target_word_count * 100).toInt()}%")
                appendLine("- Number of Arguments: ${argumentSections.size}")
                appendLine("- Counterarguments: ${if (executionConfig.include_counterarguments) "✓ Included" else "✗ Not included"}")
                appendLine("- Revision Passes: ${executionConfig.revision_passes}")
                appendLine("- Total Time: ${totalTime / 1000.0}s")
                appendLine()
                appendLine(
                  "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  }"
                )
              }.renderMarkdown(true)
            )
            overviewTask.complete()


            transcript?.write("---\n\n".toByteArray())
            transcript?.write("## Generation Complete\n\n".toByteArray())
            transcript?.write("**Total Word Count:** $cumulativeWordCount\n\n".toByteArray())
            transcript?.write("**Total Time:** ${totalTime / 1000.0}s\n\n".toByteArray())
            transcript?.write(
                "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n\n".toByteArray()
            )


            val (transcriptLink, _) = task.createFile("transcript.md")

            // Concise summary for resultFn with file links
            val finalResult = buildString {
                appendLine("# Persuasive Essay Summary: ${outline.title}")
                appendLine()
                appendLine("A complete persuasive essay of **$cumulativeWordCount words** was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Thesis:** $thesis")
                appendLine()
                appendLine("**Structure:**")
                appendLine("- Introduction with compelling hook")
                appendLine("- ${argumentSections.size} main arguments with evidence")
                if (executionConfig.include_counterarguments) {
                    appendLine("- Counterarguments and rebuttals")
                }
                appendLine("- Conclusion with ${executionConfig.call_to_action} call to action")
                appendLine()
                appendLine("## Output Files")
                appendLine()
                appendLine("- **Complete Essay:** <a href='$essayLink' target='_blank'>$essayLink</a>")
                appendLine("  - <a href='${essayLink.removeSuffix(".md")}.html' target='_blank'>HTML</a>")
                appendLine("  - <a href='${essayLink.removeSuffix(".md")}.pdf' target='_blank'>PDF</a>")
                appendLine()
                appendLine("- **Transcript:** <a href='$transcriptLink' target='_blank'>$transcriptLink</a>")
                appendLine("  - <a href='${transcriptLink.removeSuffix(".md")}.html' target='_blank'>HTML</a>")
                appendLine("  - <a href='${transcriptLink.removeSuffix(".md")}.pdf' target='_blank'>PDF</a>")
            }

            log.info("PersuasiveEssayTask completed: words=$cumulativeWordCount, arguments=${argumentSections.size}, time=${totalTime}ms")

            task.safeComplete(
                "Persuasive essay generation complete: $cumulativeWordCount words in ${totalTime / 1000}s",
                log
            )

            if (orchestrationConfig.autoFix) {
                resultFn(finalResult)
            } else {
                val footer = acceptButtonFooter(task.ui) {
                    resultFn(finalResult)
                }
                task.add(footer)
            }

        } catch (e: Exception) {
            log.error("Error during persuasive essay generation: ${e.message}", e)

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
              }.renderMarkdown(true)
            )
            overviewTask.complete()
            task.error(e)
            transcript?.write(
                """
                        <details>
                        <summary>Error: ${e.message}</summary>
                        ```
                        ${e.stackTraceToString()}
                        ```
                        </details>
                    """.trimIndent().toByteArray()
            )


            val errorOutput = buildString {
                appendLine("# Error in Persuasive Essay Generation")
                appendLine()
                appendLine("**Thesis:** $thesis")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                if (resultBuilder.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(resultBuilder.toString())
                }
            }
            resultFn(errorOutput)
        }
            } finally {
                transcript?.close()
            }

        }
    }


    private fun generateCoverImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        title: String,
        audience: String,
        tone: String,
        transcript: OutputStream?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating cover image for: $title")
            val imageTask = tabs.newTask("Cover Image")
            imageTask.add(
              buildString {
                appendLine("# Cover Image")
                appendLine()
                appendLine("**Status:** Generating cover image...")
                appendLine()
              }.renderMarkdown(true)
            )
            imageTask.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a professional, compelling cover image for a persuasive essay",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.8,
            )

            val coverPrompt = "Persuasive Essay: $title\nTarget Audience: $audience\nTone: $tone"
            val result = imageAgent.answer(listOf(ImageAndText(coverPrompt)))
            val image = result.image

            // Save image
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            val link = task.saveFile("00_cover_image.png", baos.toByteArray())
            log.debug("Saved cover image to: $link")

            // Create display link
            val imageHtml = """
        <div class='cover-image'>
          <h3>$title</h3>
          <p><em>For $audience</em></p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Cover' style='max-width: 600px; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.2);' />
          </a>
        </div>
      """.trimIndent()
            imageTask.add(imageHtml.renderMarkdown(true))
            imageTask.update()

            transcript?.write("## Cover Image\n\n".toByteArray())
            transcript?.write("**Prompt:** ${result.text}\n\n".toByteArray())
            transcript?.write("![Cover Image]($link)\n\n".transcriptFilter().toByteArray())

            imageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
            imageTask.complete()
        } catch (e: Exception) {
            log.error("Failed to generate cover image: ${e.message}")
            transcript?.write(
                """
                <details>
                <summary>Cover Image Generation Failed</summary>
                ${e.message}
                </details>
            """.trimIndent().toByteArray()
            )
        }
    }

    private fun generateOutlineImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        title: String,
        outline: EssayOutline,
        transcript: OutputStream?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating outline visualization image")
            val imageTask = tabs.newTask("Outline Visualization")
            imageTask.add(
              buildString {
                appendLine("# Outline Visualization")
                appendLine()
                appendLine("**Status:** Generating outline visualization...")
                appendLine()
              }.renderMarkdown(true)
            )
            imageTask.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create an infographic-style visualization of the essay outline and argument structure",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.7,
            )

            val outlinePrompt = buildString {
                append("Essay Title: $title\n")
                append("Thesis: ${outline.thesis_statement}\n")
                append("Arguments:\n")
                outline.arguments.forEach { arg ->
                    append("${arg.number}. ${arg.claim}\n")
                }
            }

            val result = imageAgent.answer(listOf(ImageAndText(outlinePrompt)))
            val image = result.image

            // Save image
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            val link = task.saveFile("01_outline_visualization.png", baos.toByteArray())
            log.debug("Saved outline visualization to: $link")

            // Create display link
            val imageHtml = """
        <div class='outline-image'>
          <h4>Argument Structure</h4>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Outline' style='max-width: 600px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            imageTask.add(imageHtml.renderMarkdown(true))
            imageTask.update()

            transcript?.write("## Outline Visualization\n\n".toByteArray())
            transcript?.write("**Prompt:** ${result.text}\n\n".toByteArray())
            transcript?.write("![Outline]($link)\n\n".transcriptFilter().toByteArray())

            imageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
            imageTask.complete()
        } catch (e: Exception) {
            log.error("Failed to generate outline visualization: ${e.message}")
            transcript?.write(
                """
                <details>
                <summary>Outline Image Generation Failed</summary>
                ${e.message}
                </details>
            """.trimIndent().toByteArray()
            )
        }
    }

    private fun generateArgumentImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        argumentNumber: Int,
        claim: String,
        content: String,
        transcript: OutputStream?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating image for argument $argumentNumber")
            val imageTask = tabs.newTask("Argument $argumentNumber Image")
            imageTask.add(
              buildString {
                appendLine("# Argument $argumentNumber Visualization")
                appendLine()
                appendLine("**Status:** Generating argument visualization...")
                appendLine()
              }.renderMarkdown(true)
            )
            imageTask.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a visual representation that illustrates this persuasive argument",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.7,
            )

            val argumentPrompt = buildString {
                append("Argument $argumentNumber: $claim\n")
                append("Summary: ${content.take(300)}")
            }

            val result = imageAgent.answer(listOf(ImageAndText(argumentPrompt)))
            val image = result.image

            // Save image
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            val link = task.saveFile("argument_${argumentNumber}_image.png", baos.toByteArray())
            log.debug("Saved argument $argumentNumber image to: $link")

            // Create display link
            val imageHtml = """
        <div class='argument-image'>
          <h4>Argument $argumentNumber: ${claim.take(60)}</h4>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Argument $argumentNumber' style='max-width: 500px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            imageTask.add(imageHtml.renderMarkdown(true))
            imageTask.update()

            transcript?.write("#### Argument $argumentNumber Image\n\n".toByteArray())
            transcript?.write("**Prompt:** ${result.text}\n\n".toByteArray())
            transcript?.write("![Argument $argumentNumber]($link)\n\n".transcriptFilter().toByteArray())

            imageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
            imageTask.complete()
        } catch (e: Exception) {
            log.error("Failed to generate argument $argumentNumber image: ${e.message}")
            transcript?.write(
                """
                <details>
                <summary>Argument $argumentNumber Image Generation Failed</summary>
                ${e.message}
                </details>
            """.trimIndent().toByteArray()
            )
        }
    }

    private fun generateCounterargumentImage(
        task: SessionTask,
        tabs: TabbedDisplay,
        content: String,
        transcript: OutputStream?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating counterargument visualization image")
            val imageTask = tabs.newTask("Counterargument Image")
            imageTask.add(
              buildString {
                appendLine("# Counterargument Visualization")
                appendLine()
                appendLine("**Status:** Generating counterargument visualization...")
                appendLine()
              }.renderMarkdown(true)
            )
            imageTask.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a balanced visual representation showing counterarguments and rebuttals",
                model = orchestrationConfig.defaultImage.getChildClient(task),
                temperature = 0.7,
            )

            val counterPrompt = buildString {
                append("Counterarguments and Rebuttals\n")
                append("Summary: ${content.take(300)}")
            }

            val result = imageAgent.answer(listOf(ImageAndText(counterPrompt)))
            val image = result.image

            // Save image
            val baos = ByteArrayOutputStream()
            ImageIO.write(image, "png", baos)
            val link = task.saveFile("counterargument_image.png", baos.toByteArray())
            log.debug("Saved counterargument image to: $link")

            // Create display link
            val imageHtml = """
        <div class='counterargument-image'>
          <h4>Counterarguments & Rebuttals</h4>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='Counterarguments' style='max-width: 500px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()
            imageTask.add(imageHtml.renderMarkdown(true))
            imageTask.update()

            transcript?.write("## Counterargument Visualization\n\n".toByteArray())
            transcript?.write("**Prompt:** ${result.text}\n\n".toByteArray())
            transcript?.write("![Counterarguments]($link)\n\n".transcriptFilter().toByteArray())

            imageTask.add("\n**Status:** ✅ Complete\n".renderMarkdown(true))
            imageTask.complete()
        } catch (e: Exception) {
            log.error("Failed to generate counterargument image: ${e.message}")
            transcript?.write(
                """
                <details>
                <summary>Counterargument Image Generation Failed</summary>
                ${e.message}
                </details>
            """.trimIndent().toByteArray()
            )
        }
    }


    private fun getContextFiles(): String {
        val relatedFiles = executionConfig?.related_files ?: return ""
        if (relatedFiles.isEmpty()) return ""
        log.debug("Loading ${relatedFiles.size} related context files")

        return buildString {
            appendLine("## Related Research Files")
            appendLine()
            relatedFiles.forEach { file ->
                try {
                    val filePath = root.resolve(file)
                    if (filePath.toFile().exists()) {
                        log.debug("Successfully loaded context file: $file")
                        appendLine("### $file")
                        appendLine("```")
                        appendLine(filePath.toFile().readText().truncateForDisplay(1500))
                        appendLine("```")
                        appendLine()
                    } else {
                        log.warn("Context file not found: $file")
                    }
                } catch (e: Exception) {
                    log.warn("Error reading file: $file", e)
                }
            }
        }
    }


    companion object {
        private val log: Logger = LoggerFactory.getLogger(PersuasiveEssayTask::class.java)
        @JvmStatic val PersuasiveEssay = TaskType(
            name = "PersuasiveEssay",
            category = "Writing",
            taskClass = PersuasiveEssayTask::class.java,
            executionConfigClass = PersuasiveEssayTaskExecutionConfigData::class.java,
            taskSettingsClass = PersuasiveEssayTaskTypeConfig::class.java,
            description = "Generate compelling persuasive essays with structured arguments",
            tooltipHtml = """
                        Generates complete, well-structured persuasive essays using rhetorical techniques.
                        <ul>
                          <li>Creates detailed outline with thesis, arguments, and counterarguments</li>
                          <li>Writes compelling introduction with hook and background</li>
                          <li>Develops main arguments with evidence and rhetorical devices</li>
                          <li>Addresses counterarguments with strong rebuttals</li>
                          <li>Crafts powerful conclusion with call to action</li>
                          <li>Supports multiple tones and target audiences</li>
                          <li>Includes optional revision passes for quality</li>
                          <li>Uses ethos, pathos, and logos for persuasive impact</li>
                          <li>Ideal for opinion pieces, proposals, advocacy, and academic arguments</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/PoliticalOptimizationTask.kt

```
package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

class PoliticalOptimizationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: PoliticalOptimizationTaskExecutionConfigData?
) : AbstractTask<PoliticalOptimizationTask.PoliticalOptimizationTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(PoliticalOptimizationTask::class.java)
        @JvmStatic val PoliticalOptimization = TaskType(
          name = "PoliticalOptimization",
          category = "Social",
          taskClass = PoliticalOptimizationTask::class.java,
          executionConfigClass = PoliticalOptimizationTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Optimize text using multi-perspective political consensus analysis",
          tooltipHtml = """
                        Evaluates and optimizes text from multiple political perspectives using consensus-based fitness.
                        <ul>
                          <li>Evaluates text from configurable political perspectives (left, center, right, libertarian, etc.)</li>
                          <li>Measures agreement/disagreement across perspectives</li>
                          <li>Calculates consensus fitness (positive = unifying, negative = divisive)</li>
                          <li>Identifies wedge issues and points of contention</li>
                          <li>Generates variants that maximize consensus or highlight divisions</li>
                          <li>Provides detailed perspective-by-perspective analysis</li>
                          <li>Tracks evolution of consensus across generations</li>
                          <li>Useful for crafting bipartisan messaging, identifying divisive topics, or understanding political framing</li>
                        </ul>
                      """,
        )
        private const val TT = """```"""
    }

    class PoliticalOptimizationTaskExecutionConfigData(
        @Description("The initial text to optimize")
        val initial_text: String? = null,
        @Description("The optimization goal (e.g., 'maximize consensus', 'minimize divisiveness', 'identify wedge issues')")
        val optimization_goal: String? = null,
        @Description("Perspectives to evaluate from (e.g., 'progressive', 'conservative', 'libertarian', 'centrist')")
        val perspectives: List<String>? = listOf("progressive", "conservative", "libertarian", "centrist"),
        @Description("Evaluation criteria (e.g., 'clarity', 'persuasiveness', 'factual_accuracy', 'emotional_appeal')")
        val evaluation_criteria: List<String>? = listOf(
            "clarity",
            "persuasiveness",
            "factual_accuracy",
            "emotional_appeal"
        ),
        @Description("Consensus mode: 'maximize' (find agreement), 'minimize' (find wedge issues), or 'explore' (both)")
        val consensus_mode: String? = "explore",

        @Description("Number of generations to evolve (default: 5)")
        val num_generations: Int = 5,
        @Description("Population size per generation (default: 8)")
        val population_size: Int = 8,
        @Description("Number of top candidates to keep each generation (default: 3)")
        val selection_size: Int = 3,
        @Description("Mutation strategies (e.g., 'rephrase', 'emphasize', 'soften', 'reframe')")
        val mutation_strategies: List<String>? = listOf("rephrase", "emphasize", "soften", "reframe"),
        @Description("Whether to enable crossover")
        val enable_crossover: Boolean = true,
        @Description("Consensus weight in fitness calculation (0.0-1.0, default: 0.6)")
        val consensus_weight: Double = 0.6,

        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = PoliticalOptimization.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (initial_text.isNullOrBlank()) {
                return "initial_text must not be blank"
            }
            if (optimization_goal.isNullOrBlank()) {
                return "optimization_goal must not be blank"
            }
            if (perspectives.isNullOrEmpty()) {
                return "perspectives must not be empty"
            }
            if (perspectives.size < 2) {
                return "perspectives must have at least 2 entries"
            }
            if (evaluation_criteria.isNullOrEmpty()) {
                return "evaluation_criteria must not be empty"
            }
            if (consensus_mode !in listOf("maximize", "minimize", "explore")) {
                return "consensus_mode must be 'maximize', 'minimize', or 'explore'"
            }
            if (num_generations < 1) {
                return "num_generations must be at least 1"
            }
            if (population_size < 2) {
                return "population_size must be at least 2"
            }
            if (selection_size < 1 || selection_size >= population_size) {
                return "selection_size must be between 1 and population_size-1"
            }
            if (consensus_weight < 0.0 || consensus_weight > 1.0) {
                return "consensus_weight must be between 0.0 and 1.0"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class PerspectiveEvaluation(
        @Description("The political perspective name")
        val perspective: String = "",
        @Description("Scores for each evaluation criterion (0-100)")
        val criteria_scores: Map<String, Double> = emptyMap(),
        @Description("Overall score from this perspective (0-100)")
        val overall_score: Double = 0.0,
        @Description("What this perspective likes about the text")
        val strengths: List<String> = emptyList(),
        @Description("What this perspective dislikes or finds problematic")
        val weaknesses: List<String> = emptyList(),
        @Description("Key concerns from this perspective")
        val concerns: List<String> = emptyList(),
        @Description("Justification for the scores")
        val justification: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            criteria_scores.forEach { (criterion, score) ->
                if (score < 0.0 || score > 100.0) {
                    return "criteria_scores[$criterion] must be between 0 and 100"
                }
            }
            if (overall_score < 0.0 || overall_score > 100.0) {
                return "overall_score must be between 0 and 100"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class MultiPerspectiveEvaluation(
        @Description("Evaluations from each perspective")
        val perspective_evaluations: List<PerspectiveEvaluation> = emptyList(),
        @Description("Consensus score: positive = agreement, negative = divisiveness, magnitude = strength")
        val consensus_score: Double = 0.0,
        @Description("Standard deviation of overall scores across perspectives")
        val score_variance: Double = 0.0,
        @Description("Average score across all perspectives")
        val average_score: Double = 0.0,
        @Description("Points of agreement across perspectives")
        val common_ground: List<String> = emptyList(),
        @Description("Points of contention or disagreement")
        val points_of_contention: List<String> = emptyList(),
        @Description("Whether this text is a wedge issue (high divisiveness)")
        val is_wedge_issue: Boolean = false
    )

    data class TextVariant(
        @Description("The text variant")
        val text: String = "",
        @Description("Brief explanation of what changed from parent")
        val mutation_description: String = "",
        @Description("The mutation strategy used")
        val strategy: String = ""
    )

    data class EvaluatedVariant(
        val text: String = "",
        val evaluation: MultiPerspectiveEvaluation = MultiPerspectiveEvaluation(),
        val fitness: Double = 0.0,
        val generation: Int = 0,
        val parentIndex: Int? = null,
        val strategy: String = ""
    )

    override fun promptSegment(): String {
        return """
PoliticalOptimization - Optimize text using multi-perspective political consensus analysis
  ** Specify the initial text to analyze/optimize
  ** Define political perspectives to evaluate from (progressive, conservative, libertarian, centrist, etc.)
  ** Set optimization goal (maximize consensus, minimize divisiveness, or explore both)
  ** Configure evaluation criteria (clarity, persuasiveness, factual accuracy, emotional appeal, etc.)
  ** Choose consensus mode:
     - maximize: Find text that unifies across perspectives
     - minimize: Identify wedge issues and divisive framing
     - explore: Generate both unifying and divisive variants
  ** The task will:
     - Evaluate text from each political perspective independently
     - Calculate consensus score (positive = unifying, negative = divisive)
     - Identify common ground and points of contention
     - Generate variants optimized for consensus or division
     - Track evolution of agreement/disagreement
     - Provide perspective-by-perspective analysis
  ** Useful for:
     - Crafting bipartisan messaging
     - Understanding political framing effects
     - Identifying divisive topics and language
     - Testing message reception across political spectrum
     - Finding common ground in contentious debates
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
        try {
            val startTime = System.currentTimeMillis()
            log.info("Starting PoliticalOptimizationTask with ${executionConfig?.perspectives?.size} perspectives")

            // Validate configuration
            executionConfig?.validate()?.let { errorMessage ->
                log.error("Configuration validation failed: $errorMessage")
                task.complete("VALIDATION ERROR: $errorMessage")
                task.error(ValidatedObject.ValidationError(errorMessage, executionConfig))
                transcript?.close()
                resultFn("VALIDATION ERROR: $errorMessage")
                return
            }

            val initialText = executionConfig?.initial_text!!
            val optimizationGoal = executionConfig?.optimization_goal!!
            val perspectives = executionConfig?.perspectives!!
            val evaluationCriteria = executionConfig?.evaluation_criteria!!
            val consensusMode = executionConfig?.consensus_mode ?: "explore"
            val numGenerations = executionConfig?.num_generations ?: 5
            val populationSize = executionConfig?.population_size ?: 8
            val selectionSize = executionConfig?.selection_size ?: 3
            val mutationStrategies = executionConfig?.mutation_strategies ?: listOf("rephrase", "emphasize", "soften")
            val enableCrossover = executionConfig?.enable_crossover ?: true
            val consensusWeight = executionConfig?.consensus_weight ?: 0.6

            log.info("Configuration: perspectives=${perspectives.size}, criteria=${evaluationCriteria.size}, mode=$consensusMode, generations=$numGenerations")

            val tabs = TabbedDisplay(task)
            val api = defaultSmart
            transcript?.write("# Political Optimization Task Transcript\n\n".toByteArray())

            val overviewTask = tabs.newTask("Overview")
            val overviewContent = buildString {
                appendLine("# Political Optimization Task")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("## Configuration")
                appendLine()
                appendLine("| Parameter | Value |")
                appendLine("|-----------|-------|")
                appendLine("| Optimization Goal | $optimizationGoal |")
                appendLine("| Consensus Mode | $consensusMode |")
                appendLine("| Perspectives | ${perspectives.joinToString(", ")} |")
                appendLine("| Evaluation Criteria | ${evaluationCriteria.joinToString(", ")} |")
                appendLine("| Generations | $numGenerations |")
                appendLine("| Population Size | $populationSize |")
                appendLine("| Selection Size | $selectionSize |")
                appendLine("| Consensus Weight | ${String.format("%.0f%%", consensusWeight * 100)} |")
                appendLine("| Crossover | ${if (enableCrossover) "âœ“ Enabled" else "âœ— Disabled"} |")
                appendLine()
                appendLine("## Initial Text")
                appendLine()
                appendLine("$TT")
                appendLine(initialText)
                appendLine("$TT")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("- â ³ Evaluating initial text from ${perspectives.size} perspectives...")
            }
            overviewTask.add(overviewContent.renderMarkdown())
            transcript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))

            // Initialize population
            log.info("Evaluating initial text")
            val initialEvaluation = evaluateFromMultiplePerspectives(
                initialText,
                perspectives,
                evaluationCriteria,
                api
            )
            val initialFitness = calculateFitness(initialEvaluation, consensusMode, consensusWeight)

            var currentPopulation = listOf(
                EvaluatedVariant(
                    text = initialText,
                    evaluation = initialEvaluation,
                    fitness = initialFitness,
                    generation = 0,
                    strategy = "seed"
                )
            )

            log.info(
                "Initial evaluation: consensus=${
                    String.format(
                        "%.2f",
                        initialEvaluation.consensus_score
                    )
                }, fitness=${String.format("%.2f", initialFitness)}"
            )

            // Write initial evaluation
            transcript?.write("\n\n## Initial Evaluation\n\n".toByteArray(StandardCharsets.UTF_8))
            transcript?.write(formatEvaluationReport(initialEvaluation).toByteArray(StandardCharsets.UTF_8))

            // Update overview with initial results
            overviewTask.add(buildString {
                appendLine()
                appendLine("- âœ“ Initial evaluation complete")
                appendLine(
                    "  - Consensus Score: **${
                        String.format(
                            "%.2f",
                            initialEvaluation.consensus_score
                        )
                    }** (${if (initialEvaluation.consensus_score > 0) "unifying" else "divisive"})"
                )
                appendLine("  - Average Score: **${String.format("%.1f", initialEvaluation.average_score)}/100**")
                appendLine("  - Variance: **${String.format("%.2f", initialEvaluation.score_variance)}**")
                appendLine("  - Wedge Issue: ${if (initialEvaluation.is_wedge_issue) "âš ï¸  Yes" else "âœ“ No"}")
                appendLine()
                appendLine("- â ³ Starting evolution...")
            }.let { it.renderMarkdown() })

            // Track best variants
            var bestConsensusVariant = currentPopulation[0]
            var bestDivisiveVariant = currentPopulation[0]
            val evolutionHistory = mutableListOf<List<EvaluatedVariant>>()
            evolutionHistory.add(currentPopulation)

            // Evolution loop
            for (generation in 1..numGenerations) {
                log.info("Starting generation $generation/$numGenerations")

                val generationTask = tabs.newTask("Generation $generation")
                transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
                transcript?.write("# Generation $generation\n\n".toByteArray(StandardCharsets.UTF_8))

                generationTask.add(buildString {
                    appendLine("# Generation $generation")
                    appendLine()
                    appendLine("**Status:** In Progress")
                    appendLine()
                    appendLine("Generating $populationSize variants...")
                }.let { it.renderMarkdown() })

                // Generate new variants
                val newVariants = mutableListOf<EvaluatedVariant>()

                // Select survivors based on fitness
                val survivors = when (consensusMode) {
                    "maximize" -> currentPopulation.sortedByDescending { it.fitness }.take(selectionSize)
                    "minimize" -> currentPopulation.sortedBy { it.fitness }.take(selectionSize)
                    else -> {
                        // Explore mode: keep mix of consensus and divisive
                        val topConsensus = currentPopulation.sortedByDescending { it.evaluation.consensus_score }
                            .take(selectionSize / 2)
                        val topDivisive = currentPopulation.sortedBy { it.evaluation.consensus_score }
                            .take(selectionSize - topConsensus.size)
                        (topConsensus + topDivisive).distinctBy { it.text }
                    }
                }

                log.debug("Selected ${survivors.size} survivors for generation $generation")

                // Generate mutations
                val mutationsNeeded = populationSize - survivors.size
                val mutationsPerSurvivor = kotlin.math.max(1, mutationsNeeded / survivors.size)

                survivors.forEachIndexed { survivorIndex, survivor ->
                    val mutationsToGenerate = if (survivorIndex == survivors.size - 1) {
                        mutationsNeeded - (mutationsPerSurvivor * (survivors.size - 1))
                    } else {
                        mutationsPerSurvivor
                    }

                    repeat(mutationsToGenerate) {
                        val strategy = mutationStrategies.random()
                        log.debug("Generating mutation using strategy: $strategy")
                        val mutated = generatePoliticalMutation(
                            survivor.text,
                            survivor.evaluation,
                            strategy,
                            optimizationGoal,
                            consensusMode,
                            perspectives,
                            api
                        )
                        if (mutated != null) {
                            newVariants.add(
                                EvaluatedVariant(
                                    text = mutated.text,
                                    evaluation = MultiPerspectiveEvaluation(),
                                    fitness = 0.0,
                                    generation = generation,
                                    parentIndex = survivorIndex,
                                    strategy = strategy
                                )
                            )
                        }
                    }
                }

                // Apply crossover
                if (enableCrossover && survivors.size >= 2 && newVariants.size < populationSize) {
                    log.debug("Applying crossover")
                    val crossoverVariant = applyPoliticalCrossover(
                        survivors[0].text,
                        survivors[0].evaluation,
                        survivors[1].text,
                        survivors[1].evaluation,
                        optimizationGoal,
                        consensusMode,
                        perspectives,
                        api
                    )
                    if (crossoverVariant != null) {
                        newVariants.add(
                            EvaluatedVariant(
                                text = crossoverVariant,
                                evaluation = MultiPerspectiveEvaluation(),
                                fitness = 0.0,
                                generation = generation,
                                strategy = "crossover"
                            )
                        )
                    }
                }

                // Combine survivors and new variants
                currentPopulation = survivors + newVariants

                // Evaluate all variants
                log.info("Evaluating ${currentPopulation.size} variants in generation $generation")
                currentPopulation = currentPopulation.map { variant ->
                    if (variant.fitness == 0.0) {
                        val evaluation = evaluateFromMultiplePerspectives(
                            variant.text,
                            perspectives,
                            evaluationCriteria,
                            api
                        )
                        val fitness = calculateFitness(evaluation, consensusMode, consensusWeight)
                        variant.copy(evaluation = evaluation, fitness = fitness)
                    } else {
                        variant
                    }
                }

                evolutionHistory.add(currentPopulation)

                // Update best variants
                val generationBestConsensus = currentPopulation.maxByOrNull { it.evaluation.consensus_score }!!
                val generationBestDivisive = currentPopulation.minByOrNull { it.evaluation.consensus_score }!!

                if (generationBestConsensus.evaluation.consensus_score > bestConsensusVariant.evaluation.consensus_score) {
                    log.info("New best consensus variant in generation $generation: score=${generationBestConsensus.evaluation.consensus_score}")
                    bestConsensusVariant = generationBestConsensus
                }
                if (generationBestDivisive.evaluation.consensus_score < bestDivisiveVariant.evaluation.consensus_score) {
                    log.info("New best divisive variant in generation $generation: score=${generationBestDivisive.evaluation.consensus_score}")
                    bestDivisiveVariant = generationBestDivisive
                }

                val consensusScores = currentPopulation.map { it.evaluation.consensus_score }
                val avgScores = currentPopulation.map { it.evaluation.average_score }
                // Display generation results
                val generationResults = buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## Generation $generation Results")
                    appendLine()
                    appendLine("**Status:** âœ“ Complete")
                    appendLine()
                    appendLine("### Population Statistics")
                    appendLine()
                    appendLine(
                        "- **Consensus Range:** ${
                            String.format(
                                "%.2f",
                                consensusScores.minOrNull() ?: 0.0
                            )
                        } to ${String.format("%.2f", consensusScores.maxOrNull() ?: 0.0)}"
                    )
                    appendLine("- **Average Consensus:** ${String.format("%.2f", consensusScores.average())}")
                    appendLine("- **Average Quality:** ${String.format("%.1f", avgScores.average())}/100")
                    appendLine("- **Wedge Issues:** ${currentPopulation.count { it.evaluation.is_wedge_issue }}")
                    appendLine()
                    appendLine("### Most Unifying Variant")
                    appendLine()
                    appendLine(
                        "**Consensus Score:** ${
                            String.format(
                                "%.2f",
                                generationBestConsensus.evaluation.consensus_score
                            )
                        }"
                    )
                    appendLine()
                    appendLine("$TT")
                    appendLine(generationBestConsensus.text)
                    appendLine("$TT")
                    appendLine()
                    appendLine("**Common Ground:**")
                    generationBestConsensus.evaluation.common_ground.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("### Most Divisive Variant")
                    appendLine()
                    appendLine(
                        "**Consensus Score:** ${
                            String.format(
                                "%.2f",
                                generationBestDivisive.evaluation.consensus_score
                            )
                        }"
                    )
                    appendLine()
                    appendLine("$TT")
                    appendLine(generationBestDivisive.text)
                    appendLine("$TT")
                    appendLine()
                    appendLine("**Points of Contention:**")
                    generationBestDivisive.evaluation.points_of_contention.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("### Perspective Breakdown")
                    appendLine()
                    perspectives.forEach { perspective ->
                        val scores = currentPopulation.map { variant ->
                            variant.evaluation.perspective_evaluations.find { it.perspective == perspective }?.overall_score
                                ?: 0.0
                        }
                        appendLine(
                            "- **$perspective:** Avg ${
                                String.format(
                                    "%.1f",
                                    scores.average()
                                )
                            }/100, Range ${String.format("%.1f", scores.minOrNull() ?: 0.0)}-${
                                String.format(
                                    "%.1f",
                                    scores.maxOrNull() ?: 0.0
                                )
                            }"
                        )
                    }
                }
                generationTask.add(generationResults.renderMarkdown())
                generationTask.complete()
                transcript?.write(generationResults.toByteArray(StandardCharsets.UTF_8))

                // Update overview
                overviewTask.add(buildString {
                    appendLine()
                    appendLine(
                        "- âœ“ Generation $generation: Consensus=${
                            String.format(
                                "%.2f",
                                consensusScores.average()
                            )
                        }, Quality=${String.format("%.1f", avgScores.average())}"
                    )
                }.let { it.renderMarkdown() })
            }

            // Create analysis tabs
            log.info("Creating detailed analysis")

            val consensusAnalysisTask = tabs.newTask("Consensus Analysis")
            val consensusAnalysis = buildString {
                appendLine("# Consensus Analysis")
                appendLine()
                appendLine("## Most Unifying Text")
                appendLine()
                appendLine(
                    "**Consensus Score:** ${
                        String.format(
                            "%.2f",
                            bestConsensusVariant.evaluation.consensus_score
                        )
                    } (Higher = More Agreement)"
                )
                appendLine()
                appendLine(
                    "**Average Quality:** ${
                        String.format(
                            "%.1f",
                            bestConsensusVariant.evaluation.average_score
                        )
                    }/100"
                )
                appendLine()
                appendLine(
                    "**Score Variance:** ${
                        String.format(
                            "%.2f",
                            bestConsensusVariant.evaluation.score_variance
                        )
                    } (Lower = More Agreement)"
                )
                appendLine()
                appendLine("**Generation Found:** ${bestConsensusVariant.generation}")
                appendLine()
                appendLine("### Text")
                appendLine()
                appendLine("$TT")
                appendLine(bestConsensusVariant.text)
                appendLine("$TT")
                appendLine()
                appendLine("### Common Ground")
                appendLine()
                bestConsensusVariant.evaluation.common_ground.forEach { appendLine("- $it") }
                appendLine()
                appendLine("### Perspective-by-Perspective Scores")
                appendLine()
                appendLine("| Perspective | Overall Score | Key Strengths |")
                appendLine("|-------------|---------------|---------------|")
                bestConsensusVariant.evaluation.perspective_evaluations.sortedByDescending { it.overall_score }
                    .forEach { eval ->
                        appendLine(
                            "| ${eval.perspective} | ${
                                String.format(
                                    "%.1f",
                                    eval.overall_score
                                )
                            }/100 | ${eval.strengths.firstOrNull() ?: "N/A"} |"
                        )
                    }
                appendLine()
                appendLine("### Detailed Perspective Analysis")
                appendLine()
                bestConsensusVariant.evaluation.perspective_evaluations.forEach { eval ->
                    appendLine("#### ${eval.perspective} Perspective")
                    appendLine()
                    appendLine("**Score:** ${String.format("%.1f", eval.overall_score)}/100")
                    appendLine()
                    appendLine("**Strengths:**")
                    eval.strengths.forEach { appendLine("- $it") }
                    appendLine()
                    if (eval.weaknesses.isNotEmpty()) {
                        appendLine("**Weaknesses:**")
                        eval.weaknesses.forEach { appendLine("- $it") }
                        appendLine()
                    }
                    if (eval.concerns.isNotEmpty()) {
                        appendLine("**Concerns:**")
                        eval.concerns.forEach { appendLine("- $it") }
                        appendLine()
                    }
                    appendLine("**Criteria Scores:**")
                    eval.criteria_scores.forEach { (criterion, score) ->
                        appendLine("- $criterion: ${String.format("%.1f", score)}/100")
                    }
                    appendLine()
                }
            }
            consensusAnalysisTask.add(consensusAnalysis.renderMarkdown())
            consensusAnalysisTask.complete()
            transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
            transcript?.write(consensusAnalysis.toByteArray(StandardCharsets.UTF_8))

            val divisiveAnalysisTask = tabs.newTask("Divisiveness Analysis")
            val divisiveAnalysis = buildString {
                appendLine("# Divisiveness Analysis")
                appendLine()
                appendLine("## Most Divisive Text (Wedge Issue)")
                appendLine()
                appendLine(
                    "**Consensus Score:** ${
                        String.format(
                            "%.2f",
                            bestDivisiveVariant.evaluation.consensus_score
                        )
                    } (Lower/Negative = More Divisive)"
                )
                appendLine()
                appendLine(
                    "**Average Quality:** ${
                        String.format(
                            "%.1f",
                            bestDivisiveVariant.evaluation.average_score
                        )
                    }/100"
                )
                appendLine()
                appendLine(
                    "**Score Variance:** ${
                        String.format(
                            "%.2f",
                            bestDivisiveVariant.evaluation.score_variance
                        )
                    } (Higher = More Disagreement)"
                )
                appendLine()
                appendLine("**Generation Found:** ${bestDivisiveVariant.generation}")
                appendLine()
                appendLine("### Text")
                appendLine()
                appendLine("$TT")
                appendLine(bestDivisiveVariant.text)
                appendLine("$TT")
                appendLine()
                appendLine("### Points of Contention")
                appendLine()
                bestDivisiveVariant.evaluation.points_of_contention.forEach { appendLine("- $it") }
                appendLine()
                appendLine("### Perspective-by-Perspective Scores")
                appendLine()
                appendLine("| Perspective | Overall Score | Key Concerns |")
                appendLine("|-------------|---------------|--------------|")
                bestDivisiveVariant.evaluation.perspective_evaluations.sortedBy { it.overall_score }.forEach { eval ->
                    appendLine(
                        "| ${eval.perspective} | ${
                            String.format(
                                "%.1f",
                                eval.overall_score
                            )
                        }/100 | ${eval.concerns.firstOrNull() ?: eval.weaknesses.firstOrNull() ?: "N/A"} |"
                    )
                }
                appendLine()
                appendLine("### Detailed Perspective Analysis")
                appendLine()
                bestDivisiveVariant.evaluation.perspective_evaluations.forEach { eval ->
                    appendLine("#### ${eval.perspective} Perspective")
                    appendLine()
                    appendLine("**Score:** ${String.format("%.1f", eval.overall_score)}/100")
                    appendLine()
                    if (eval.strengths.isNotEmpty()) {
                        appendLine("**Strengths:**")
                        eval.strengths.forEach { appendLine("- $it") }
                        appendLine()
                    }
                    appendLine("**Weaknesses:**")
                    eval.weaknesses.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("**Concerns:**")
                    eval.concerns.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("**Criteria Scores:**")
                    eval.criteria_scores.forEach { (criterion, score) ->
                        appendLine("- $criterion: ${String.format("%.1f", score)}/100")
                    }
                    appendLine()
                }
                appendLine()
                appendLine("## Polarization Analysis")
                appendLine()
                val highScorers =
                    bestDivisiveVariant.evaluation.perspective_evaluations.filter { it.overall_score > 60 }
                val lowScorers = bestDivisiveVariant.evaluation.perspective_evaluations.filter { it.overall_score < 40 }
                if (highScorers.isNotEmpty() && lowScorers.isNotEmpty()) {
                    appendLine("### Perspectives That Favor This Text")
                    highScorers.forEach {
                        appendLine(
                            "- **${it.perspective}** (${
                                String.format(
                                    "%.1f",
                                    it.overall_score
                                )
                            }/100): ${it.strengths.firstOrNull() ?: ""}"
                        )
                    }
                    appendLine()
                    appendLine("### Perspectives That Oppose This Text")
                    lowScorers.forEach {
                        appendLine(
                            "- **${it.perspective}** (${
                                String.format(
                                    "%.1f",
                                    it.overall_score
                                )
                            }/100): ${it.concerns.firstOrNull() ?: it.weaknesses.firstOrNull() ?: ""}"
                        )
                    }
                }
            }
            divisiveAnalysisTask.add(divisiveAnalysis.renderMarkdown())
            divisiveAnalysisTask.complete()
            transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
            transcript?.write(divisiveAnalysis.toByteArray(StandardCharsets.UTF_8))

            val evolutionTask = tabs.newTask("Evolution")
            val evolutionAnalysis = buildString {
                appendLine("# Evolution Analysis")
                appendLine()
                appendLine("## Consensus Progression")
                appendLine()
                appendLine("| Generation | Best Consensus | Worst Consensus | Avg Consensus | Avg Quality |")
                appendLine("|------------|----------------|-----------------|---------------|-------------|")
                evolutionHistory.forEachIndexed { index, population ->
                    val consensusScores = population.map { it.evaluation.consensus_score }
                    val qualityScores = population.map { it.evaluation.average_score }
                    appendLine(
                        "| $index | ${
                            String.format(
                                "%.2f",
                                consensusScores.maxOrNull() ?: 0.0
                            )
                        } | ${String.format("%.2f", consensusScores.minOrNull() ?: 0.0)} | ${
                            String.format(
                                "%.2f",
                                consensusScores.average()
                            )
                        } | ${String.format("%.1f", qualityScores.average())} |"
                    )
                }
                appendLine()
                appendLine("## Strategy Effectiveness")
                appendLine()
                val strategyStats = mutableMapOf<String, MutableList<Double>>()
                evolutionHistory.flatten().forEach { variant ->
                    if (variant.strategy.isNotEmpty()) {
                        strategyStats.getOrPut(variant.strategy) { mutableListOf() }
                            .add(variant.evaluation.consensus_score)
                    }
                }
                appendLine("| Strategy | Avg Consensus | Count | Unifying Rate | Divisive Rate |")
                appendLine("|----------|---------------|-------|---------------|---------------|")
                strategyStats.forEach { (strategy, scores) ->
                    val avgConsensus = scores.average()
                    val unifyingRate = scores.count { it > 0 }.toDouble() / scores.size * 100
                    val divisiveRate = scores.count { it < 0 }.toDouble() / scores.size * 100
                    appendLine(
                        "| $strategy | ${String.format("%.2f", avgConsensus)} | ${scores.size} | ${
                            String.format(
                                "%.0f%%",
                                unifyingRate
                            )
                        } | ${String.format("%.0f%%", divisiveRate)} |"
                    )
                }
                appendLine()
                appendLine("## Perspective Trends")
                appendLine()
                perspectives.forEach { perspective ->
                    appendLine("### $perspective")
                    appendLine()
                    appendLine("| Generation | Avg Score | Range |")
                    appendLine("|------------|-----------|-------|")
                    evolutionHistory.forEachIndexed { gen, population ->
                        val scores = population.mapNotNull { variant ->
                            variant.evaluation.perspective_evaluations.find { it.perspective == perspective }?.overall_score
                        }
                        if (scores.isNotEmpty()) {
                            appendLine(
                                "| $gen | ${String.format("%.1f", scores.average())} | ${
                                    String.format(
                                        "%.1f",
                                        scores.minOrNull() ?: 0.0
                                    )
                                }-${String.format("%.1f", scores.maxOrNull() ?: 0.0)} |"
                            )
                        }
                    }
                    appendLine()
                }
            }
            evolutionTask.add(evolutionAnalysis.renderMarkdown())
            evolutionTask.complete()
            transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
            transcript?.write(evolutionAnalysis.toByteArray(StandardCharsets.UTF_8))

            // Final summary
            val totalTime = System.currentTimeMillis() - startTime
            val finalOverview = buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## âœ… Optimization Complete")
                appendLine()
                appendLine("| Metric | Value |")
                appendLine("|--------|-------|")
                appendLine("| Initial Consensus | ${String.format("%.2f", initialEvaluation.consensus_score)} |")
                appendLine(
                    "| Best Consensus | ${
                        String.format(
                            "%.2f",
                            bestConsensusVariant.evaluation.consensus_score
                        )
                    } |"
                )
                appendLine(
                    "| Most Divisive | ${
                        String.format(
                            "%.2f",
                            bestDivisiveVariant.evaluation.consensus_score
                        )
                    } |"
                )
                appendLine(
                    "| Consensus Improvement | ${
                        String.format(
                            "%+.2f",
                            bestConsensusVariant.evaluation.consensus_score - initialEvaluation.consensus_score
                        )
                    } |"
                )
                appendLine("| Generations | $numGenerations |")
                appendLine("| Total Variants | ${evolutionHistory.flatten().size} |")
                appendLine("| Total Time | ${totalTime / 1000}s |")
                appendLine()
                appendLine("**Status:** âœ“ Complete")
            }
            overviewTask.add(finalOverview.renderMarkdown())
            overviewTask.complete()
            transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
            transcript?.write(finalOverview.toByteArray(StandardCharsets.UTF_8))
            transcript?.close()

            log.info("PoliticalOptimizationTask completed: time=${totalTime}ms, consensus_improvement=${bestConsensusVariant.evaluation.consensus_score - initialEvaluation.consensus_score}")
            task.complete(
                "Optimization complete: found consensus variant (${
                    String.format(
                        "%.2f",
                        bestConsensusVariant.evaluation.consensus_score
                    )
                }) and divisive variant (${
                    String.format(
                        "%.2f",
                        bestDivisiveVariant.evaluation.consensus_score
                    )
                }) in ${totalTime / 1000}s"
            )

            val transcriptFile = "political_optimization_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            val (link, _) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
            val summaryMessage = buildString {
                appendLine("## Most Unifying Text")
                appendLine()
                appendLine(
                    "**Consensus Score:** ${
                        String.format(
                            "%.2f",
                            bestConsensusVariant.evaluation.consensus_score
                        )
                    }"
                )
                appendLine()
                appendLine("$TT")
                appendLine(bestConsensusVariant.text)
                appendLine("$TT")
                appendLine()
                appendLine("**Common Ground:**")
                bestConsensusVariant.evaluation.common_ground.take(3).forEach { appendLine("- $it") }
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Most Divisive Text")
                appendLine()
                appendLine(
                    "**Consensus Score:** ${
                        String.format(
                            "%.2f",
                            bestDivisiveVariant.evaluation.consensus_score
                        )
                    }"
                )
                appendLine()
                appendLine("$TT")
                appendLine(bestDivisiveVariant.text)
                appendLine("$TT")
                appendLine()
                appendLine("**Points of Contention:**")
                bestDivisiveVariant.evaluation.points_of_contention.take(3).forEach { appendLine("- $it") }
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(
                    "Detailed analysis: <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                        link.removeSuffix(
                            ".md"
                        )
                    }.pdf' target='_blank'>pdf</a>"
                )
            }
            resultFn(summaryMessage)

        } catch (e: Exception) {
            log.error("Error during PoliticalOptimizationTask execution", e)
            transcript?.close()
            task.error(e)
            task.complete("Failed with error: ${e.message}")
            resultFn("ERROR: ${e.message}")
        }
    }

    private fun evaluateFromMultiplePerspectives(
        text: String,
        perspectives: List<String>,
        criteria: List<String>,
        api: ChatInterface
    ): MultiPerspectiveEvaluation {
        log.debug("Evaluating text from ${perspectives.size} perspectives")
        val perspectiveEvaluations = perspectives.map { perspective ->
            evaluateFromPerspective(text, perspective, criteria, api)
        }

        val overallScores = perspectiveEvaluations.map { it.overall_score }
        val averageScore = overallScores.average()
        val variance = calculateStdDev(overallScores)

        // Calculate consensus score: positive = agreement, negative = divisiveness
        // Uses signed variance: low variance = high consensus (positive), high variance = divisive (negative)
        val consensusScore = 100.0 - variance * 2.0 // Scale so variance of 50 = consensus of 0

        // Identify common ground and contention
        val commonGround = findCommonGround(perspectiveEvaluations)
        val pointsOfContention = findPointsOfContention(perspectiveEvaluations)

        // A text is a wedge issue if variance is high (perspectives disagree significantly)
        val isWedgeIssue = variance > 25.0

        return MultiPerspectiveEvaluation(
            perspective_evaluations = perspectiveEvaluations,
            consensus_score = consensusScore,
            score_variance = variance,
            average_score = averageScore,
            common_ground = commonGround,
            points_of_contention = pointsOfContention,
            is_wedge_issue = isWedgeIssue
        )
    }

    private fun evaluateFromPerspective(
        text: String,
        perspective: String,
        criteria: List<String>,
        api: ChatInterface
    ): PerspectiveEvaluation {
        return try {
            ParsedAgent(
                resultClass = PerspectiveEvaluation::class.java,
                prompt = """
You are evaluating text from the perspective of someone with a $perspective political viewpoint.

## Instructions
Evaluate the following text honestly from this political perspective, considering how someone with these views would perceive it.

For each criterion, provide a score from 0-100:
${criteria.joinToString("\n") { "- **$it**: How well does the text perform on this dimension from a $perspective perspective?" }}

Also provide:
1. An overall score (0-100) representing how favorable this text is from a $perspective viewpoint
2. Strengths: What aspects would appeal to this perspective?
3. Weaknesses: What aspects would be problematic for this perspective?
4. Concerns: What specific concerns would this perspective have?
5. Justification: Brief explanation of the scores

Be authentic to the perspective. Consider:
- Core values and priorities of this viewpoint
- Language and framing preferences
- Potential triggers or sensitive topics
- What would resonate vs. alienate

The perspective field in your response should be: "$perspective"
                """.trimIndent(),
                model = api,
                temperature = 0.4,
                name = "PerspectiveEvaluator_$perspective",
                parsingChatter = defaultFast,
            ).answer(
                listOf(
                    """
## Text to Evaluate
$TT
$text
```
                    """.trimIndent()
                )
            ).obj
        } catch (e: Exception) {
            log.warn("Failed to evaluate from $perspective perspective", e)
            PerspectiveEvaluation(
                perspective = perspective,
                criteria_scores = criteria.associateWith { 0.0 },
                overall_score = 0.0,
                strengths = emptyList(),
                weaknesses = listOf("Evaluation failed: ${e.message}"),
                concerns = emptyList(),
                justification = "Error during evaluation"
            )
        }
    }

    private fun calculateStdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }

    private fun calculateFitness(
        evaluation: MultiPerspectiveEvaluation,
        consensusMode: String,
        consensusWeight: Double
    ): Double {
        val qualityWeight = 1.0 - consensusWeight

        return when (consensusMode) {
            "maximize" -> {
                // Maximize consensus (positive score) and quality
                (evaluation.consensus_score * consensusWeight) + (evaluation.average_score * qualityWeight)
            }

            "minimize" -> {
                // Maximize divisiveness (negative consensus score) while maintaining some quality
                (-evaluation.consensus_score * consensusWeight) + (evaluation.average_score * qualityWeight * 0.5)
            }

            else -> { // "explore"
                // Reward both high consensus and high divisiveness, weighted by quality
                val consensusMagnitude = abs(evaluation.consensus_score)
                (consensusMagnitude * consensusWeight) + (evaluation.average_score * qualityWeight)
            }
        }
    }

    private fun findCommonGround(evaluations: List<PerspectiveEvaluation>): List<String> {
        // Find strengths mentioned by multiple perspectives
        val strengthCounts = mutableMapOf<String, Int>()
        evaluations.forEach { eval ->
            eval.strengths.forEach { strength ->
                val normalized = strength.lowercase().trim()
                strengthCounts[normalized] = strengthCounts.getOrDefault(normalized, 0) + 1
            }
        }

        // Return strengths mentioned by at least half of perspectives
        val threshold = evaluations.size / 2
        return strengthCounts.filter { it.value >= threshold }
            .keys
            .take(5)
            .toList()
    }

    private fun findPointsOfContention(evaluations: List<PerspectiveEvaluation>): List<String> {
        // Find concerns/weaknesses that are perspective-specific
        val concernCounts = mutableMapOf<String, Int>()
        evaluations.forEach { eval ->
            (eval.concerns + eval.weaknesses).forEach { concern ->
                val normalized = concern.lowercase().trim()
                concernCounts[normalized] = concernCounts.getOrDefault(normalized, 0) + 1
            }
        }

        // Return concerns mentioned by only some perspectives (not universal)
        val maxCount = evaluations.size - 1
        return concernCounts.filter { it.value in 1..maxCount }
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }

    private fun generatePoliticalMutation(
        text: String,
        parentEvaluation: MultiPerspectiveEvaluation,
        strategy: String,
        goal: String,
        consensusMode: String,
        perspectives: List<String>,
        api: ChatInterface
    ): TextVariant? {
        return try {
            val targetGuidance = when (consensusMode) {
                "maximize" -> "Create a variant that will achieve higher consensus across all perspectives. Focus on common ground and avoid divisive language."
                "minimize" -> "Create a variant that will be more divisive. Emphasize aspects that appeal strongly to some perspectives while alienating others."
                else -> "Create a variant that either increases consensus OR increases divisiveness, depending on which direction seems more promising."
            }

            ParsedAgent(
                resultClass = TextVariant::class.java,
                prompt = """
You are optimizing text for multi-perspective political consensus analysis.

## Optimization Goal
$goal

## Consensus Mode
$consensusMode

## Target Guidance
$targetGuidance

## Mutation Strategy
$strategy

## Perspectives Being Evaluated
${perspectives.joinToString(", ")}

## Parent Evaluation Summary
**Consensus Score:** ${
                    String.format(
                        "%.2f",
                        parentEvaluation.consensus_score
                    )
                } (${if (parentEvaluation.consensus_score > 0) "unifying" else "divisive"})
**Average Quality:** ${String.format("%.1f", parentEvaluation.average_score)}/100
**Variance:** ${String.format("%.2f", parentEvaluation.score_variance)}
**Wedge Issue:** ${if (parentEvaluation.is_wedge_issue) "Yes" else "No"}

**Common Ground:**
${parentEvaluation.common_ground.joinToString("\n") { "- $it" }}

**Points of Contention:**
${parentEvaluation.points_of_contention.joinToString("\n") { "- $it" }}

## Perspective-Specific Feedback
${
                    parentEvaluation.perspective_evaluations.joinToString("\n\n") { eval ->
                        "**${eval.perspective}** (${String.format("%.1f", eval.overall_score)}/100):\n" +
                                "- Strengths: ${eval.strengths.joinToString("; ")}\n" +
                                "- Concerns: ${eval.concerns.joinToString("; ")}"
                    }
                }

## Instructions
Apply the "$strategy" mutation strategy to create a variant that better achieves the optimization goal.

Mutation strategies:
- **rephrase**: Change wording while maintaining meaning
- **emphasize**: Strengthen certain points or language
- **soften**: Make tone more moderate or diplomatic
- **reframe**: Change the framing or perspective of the argument
- **polarize**: Make language more appealing to some perspectives (for divisiveness)
- **bridge**: Add language that connects different perspectives (for consensus)

Generate ONE variant with a clear mutation_description explaining what changed.
The strategy field should be: "$strategy"
                """.trimIndent(),
                model = api,
                temperature = 0.8,
                name = "PoliticalMutationGenerator",
                parsingChatter = defaultFast,
            ).answer(
                listOf(
                    """
## Current Text
$TT
$text
```
                    """.trimIndent()
                )
            ).obj
        } catch (e: Exception) {
            log.warn("Failed to generate political mutation with strategy $strategy", e)
            null
        }
    }

    private fun applyPoliticalCrossover(
        text1: String,
        eval1: MultiPerspectiveEvaluation,
        text2: String,
        eval2: MultiPerspectiveEvaluation,
        goal: String,
        consensusMode: String,
        perspectives: List<String>,
        api: ChatInterface
    ): String? {
        return try {
            val targetGuidance = when (consensusMode) {
                "maximize" -> "Combine elements that increase consensus. Focus on what both parents do well."
                "minimize" -> "Combine elements that increase divisiveness. Emphasize contrasting aspects."
                else -> "Combine the most effective elements from both parents, whether for consensus or division."
            }

            ParsedAgent(
                resultClass = TextVariant::class.java,
                prompt = """
You are applying crossover in political consensus optimization.

## Optimization Goal
$goal

## Consensus Mode
$consensusMode

## Target Guidance
$targetGuidance

## Perspectives Being Evaluated
${perspectives.joinToString(", ")}

## Parent 1 Evaluation
**Consensus Score:** ${String.format("%.2f", eval1.consensus_score)}
**Average Quality:** ${String.format("%.1f", eval1.average_score)}/100
**Common Ground:** ${eval1.common_ground.joinToString("; ")}

## Parent 2 Evaluation
**Consensus Score:** ${String.format("%.2f", eval2.consensus_score)}
**Average Quality:** ${String.format("%.1f", eval2.average_score)}/100
**Common Ground:** ${eval2.common_ground.joinToString("; ")}

## Instructions
Create a new variant by combining the best elements from both parents.
- Identify what each parent does well
- Merge complementary strengths
- Create a cohesive result that outperforms both parents

Generate the crossover variant.
                """.trimIndent(),
                model = api,
                temperature = 0.7,
                name = "PoliticalCrossoverGenerator",
                parsingChatter = defaultFast,
            ).answer(
                listOf(
                    """
## Parent Text 1
$TT
$text1
```

## Parent Text 2
$TT
$text2
```
                    """.trimIndent()
                )
            ).obj.text
        } catch (e: Exception) {
            log.warn("Failed to apply political crossover", e)
            null
        }
    }

    private fun formatEvaluationReport(evaluation: MultiPerspectiveEvaluation): String {
        return buildString {
            appendLine(
                "**Consensus Score:** ${
                    String.format(
                        "%.2f",
                        evaluation.consensus_score
                    )
                } (${if (evaluation.consensus_score > 0) "Unifying" else "Divisive"})"
            )
            appendLine()
            appendLine("**Average Quality:** ${String.format("%.1f", evaluation.average_score)}/100")
            appendLine()
            appendLine("**Score Variance:** ${String.format("%.2f", evaluation.score_variance)}")
            appendLine()
            appendLine("**Wedge Issue:** ${if (evaluation.is_wedge_issue) "âš ï¸ Yes" else "âœ“ No"}")
            appendLine()
            if (evaluation.common_ground.isNotEmpty()) {
                appendLine("**Common Ground:**")
                evaluation.common_ground.forEach { appendLine("- $it") }
                appendLine()
            }
            if (evaluation.points_of_contention.isNotEmpty()) {
                appendLine("**Points of Contention:**")
                evaluation.points_of_contention.forEach { appendLine("- $it") }
                appendLine()
            }
            appendLine("### Perspective Scores")
            appendLine()
            appendLine("| Perspective | Score |")
            appendLine("|-------------|-------|")
            evaluation.perspective_evaluations.sortedByDescending { it.overall_score }.forEach { eval ->
                appendLine("| ${eval.perspective} | ${String.format("%.1f", eval.overall_score)}/100 |")
            }
        }
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the `com.simiacryptus.cognotik.plan.tools.social` package, incorporating the documentation and specifications from the provided source files.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/README.md
```markdown
# Social and Reasoning Tasks

This package contains a suite of advanced reasoning and social simulation tasks. These tools leverage LLMs to perform complex analyses, simulate human-like interactions, and resolve multi-faceted problems through structured frameworks.

## Overview

The tasks in this package are designed for:
- **Complex Reasoning**: Dialectical, ethical, and multi-perspective analysis.
- **Strategic Analysis**: Game theory and political optimization.
- **Social Simulation**: Poll simulations and controlled LLM experiments.
- **Advanced Writing**: Generation of structured persuasive essays.

---

## Available Tasks

### 1. Dialectical Reasoning Task
Resolves contradictions by iterating through thesis, antithesis, and synthesis phases to reach higher-level understanding.

*   **Key Features**:
    *   Explores contradictions and tensions between positions.
    *   Generates synthesis that transcends opposition.
    *   Iterates through multiple synthesis levels (1-5).
    *   Preserves valuable aspects from both sides.

| Parameter | Description |
| :--- | :--- |
| `thesis` | The thesis statement or position to analyze. |
| `antithesis` | The antithesis statement or opposing position. |
| `context` | Context or domain for the dialectical analysis. |
| `synthesis_levels` | Number of synthesis levels to iterate through (1-5). |
| `preserve_strengths` | Whether to preserve strengths from both sides in synthesis. |
| `input_files` | Specific files or glob patterns for input. |

### 2. Ethical Reasoning Task
Analyzes complex dilemmas through multiple established ethical frameworks (e.g., Utilitarianism, Deontology, Virtue Ethics).

*   **Key Features**:
    *   Evaluates dilemmas from diverse philosophical perspectives.
    *   Identifies stakeholder impacts.
    *   Synthesizes findings into a balanced recommendation.
    *   Highlights ethical trade-offs and points of conflict.

| Parameter | Description |
| :--- | :--- |
| `ethical_dilemma` | Clear description of the ethical problem. |
| `stakeholders` | List of individuals or groups affected. |
| `ethical_frameworks` | Frameworks to apply (utilitarianism, deontology, etc.). |
| `input_files` | Optional input files for context. |

### 3. Game Theory Task
Performs comprehensive game theory analysis of strategic situations, supporting cooperative and non-cooperative models.

*   **Key Features**:
    *   Constructs payoff matrices.
    *   Identifies Nash equilibria and Pareto optimal outcomes.
    *   Analyzes dominant and dominated strategies.
    *   Supports repeated game analysis with trigger strategies.

| Parameter | Description |
| :--- | :--- |
| `game_scenario` | The strategic situation to analyze. |
| `players` | List of players/agents in the game. |
| `game_type` | cooperative, non-cooperative, zero-sum, repeated, sequential. |
| `iterations` | Number of iterations for repeated game analysis. |

### 4. LLM Experiment Task
Conducts controlled, repeatable experiments to characterize LLM behaviors, biases, and performance.

*   **Key Features**:
    *   Variable substitution in prompt templates.
    *   Statistical analysis (t-tests, variance, significance).
    *   Response diversity and consistency measurement.
    *   Automated insight generation.

| Parameter | Description |
| :--- | :--- |
| `prompt_templates` | Base prompt templates to test. |
| `prompt_variables` | Variables to substitute in templates. |
| `metrics` | Metrics to track (e.g., response_length, sentiment). |
| `repetitions` | Number of times to repeat each condition. |
| `significance_level` | Alpha level for statistical tests (e.g., 0.05). |

### 5. LLM Poll Simulation Task
Simulates surveys and polls using diverse AI personas to test instruments or explore demographic patterns.

*   **Key Features**:
    *   Supports multiple question types (Likert, Choice, Open-ended).
    *   Generates realistic respondent personas with demographics.
    *   Performs cross-tabulation and sentiment analysis.
    *   Detects response biases (central tendency, primacy effects).

| Parameter | Description |
| :--- | :--- |
| `questions` | List of survey questions. |
| `respondent_profiles` | Templates defining demographics and traits. |
| `respondents_per_profile` | Number of simulated respondents per profile. |
| `demographic_dimensions` | Dimensions to track (age, gender, etc.). |

### 6. Multi-Perspective Analysis Task
Examines a topic from several distinct viewpoints (e.g., technical, business, user) and synthesizes them.

*   **Key Features**:
    *   Independent analysis per perspective.
    *   Identification of agreements and conflicts.
    *   Consensus-based synthesis.

| Parameter | Description |
| :--- | :--- |
| `analysis_subject` | The topic or problem to analyze. |
| `perspectives` | List of viewpoints to consider. |
| `synthesize` | Whether to generate a unified conclusion. |
| `consensus_threshold` | Minimum confidence for agreement (0.0-1.0). |

### 7. Persuasive Essay Task
Generates high-quality, structured persuasive essays using rhetorical techniques and iterative refinement.

*   **Key Features**:
    *   Structured argument development (Ethos, Pathos, Logos).
    *   Addresses counterarguments and rebuttals.
    *   Iterative revision passes for quality.
    *   Optional image generation for cover and arguments.

| Parameter | Description |
| :--- | :--- |
| `thesis` | The position to argue for. |
| `target_audience` | e.g., 'general public', 'policymakers'. |
| `tone` | e.g., 'formal', 'analytical', 'passionate'. |
| `target_word_count` | Target length for the essay. |
| `call_to_action` | 'strong', 'moderate', 'reflective', or 'none'. |

### 8. Political Optimization Task
Evaluates and evolves text to find common ground or identify wedge issues across the political spectrum.

*   **Key Features**:
    *   Evaluates text from left, center, right, and other perspectives.
    *   Calculates consensus fitness scores.
    *   Uses evolutionary algorithms (mutation/crossover) to optimize text.
    *   Identifies points of contention and unifying language.

| Parameter | Description |
| :--- | :--- |
| `initial_text` | The text to optimize. |
| `optimization_goal` | e.g., 'maximize consensus', 'identify wedge issues'. |
| `perspectives` | Political viewpoints to evaluate from. |
| `num_generations` | Number of evolutionary cycles. |
| `consensus_mode` | 'maximize', 'minimize', or 'explore'. |

---

## Implementation Details

All tasks in this package extend `AbstractTask` and utilize:
- **Tabbed Displays**: For organized, multi-step output.
- **Transcripts**: Detailed markdown logs of the reasoning process.
- **Parsed Agents**: For structured data extraction and evaluation.
- **Concurrent Execution**: Leveraging thread pools for simulations and multi-perspective runs.
```

### Summary of Changes
- Created a comprehensive `README.md` for the `social` package.
- Summarized 8 distinct tasks: `DialecticalReasoning`, `EthicalReasoning`, `GameTheory`, `LLMExperiment`, `LLMPollSimulation`, `MultiPerspectiveAnalysis`, `PersuasiveEssay`, and `PoliticalOptimization`.
- Included key features and primary configuration parameters for each task based on their `ExecutionConfigData` and `TaskType` metadata.
- Added an overview and implementation details section to explain the shared patterns (transcripts, tabbed displays, etc.) used across these social tools.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/social/README.md</a> Updated
