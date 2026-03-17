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
) :
  AbstractTask<DialecticalReasoningTask.DialecticalReasoningTaskExecutionConfigData, DialecticalReasoningTask.DialecticalReasoningTypeConfig>(
    orchestrationConfig,
    planTask
  ) {
  class DialecticalReasoningTypeConfig(
    @Description("Prompt template for thesis analysis")
    var thesis_analysis_prompt: String = "You are analyzing a thesis statement in a dialectical reasoning process.",
    @Description("Prompt template for antithesis analysis")
    var antithesis_analysis_prompt: String = "You are analyzing an antithesis statement in a dialectical reasoning process.",
    @Description("Prompt template for contradictions exploration")
    var contradictions_prompt: String = "You are exploring the contradictions and tensions between thesis and antithesis in a dialectical process.",
    @Description("Prompt template for synthesis generation")
    var synthesis_prompt: String = "You are generating a dialectical synthesis that transcends the opposition between thesis and antithesis.",
    @Description("Prompt template for final integration")
    var integration_prompt: String = "You are providing a final integration of the entire dialectical reasoning process.",
    @Description("Temperature for analysis steps (thesis/antithesis)")
    var analysis_temperature: Double = 0.5,
    @Description("Temperature for synthesis steps")
    var synthesis_temperature: Double = 0.7,
    @Description("Temperature for contradictions exploration")
    var contradictions_temperature: Double = 0.6,
    @Description("Temperature for final integration")
    var integration_temperature: Double = 0.6
  ) : TaskTypeConfig()


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
    task_description = "Dialectical analysis",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (thesis.isNullOrBlank()) return "Thesis must not be blank"
      if (antithesis.isNullOrBlank()) return "Antithesis must not be blank"
      synthesis_levels = synthesis_levels.coerceIn(1, 5)
      if (thesis == antithesis) return "Thesis and antithesis must be different"
      if (thesis!!.length > 5000) return "Thesis is too long (max 5000 characters)"
      if (antithesis!!.length > 5000) return "Antithesis is too long (max 5000 characters)"
      if (context != null && context!!.length > 10000) return "Context is too long (max 10000 characters)"
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String = buildString {
    appendLine("DialecticalReasoning - Resolve contradictions through thesis-antithesis-synthesis")
    appendLine("  ** Specify thesis and antithesis statements representing opposing positions")
    appendLine("  ** Provide context to ground the dialectical analysis")
    appendLine("  ** Configure synthesis_levels (1-5) to iterate toward higher understanding")
    appendLine("  ** Set preserve_strengths=true to maintain valuable aspects of both sides")
    appendLine("  ** input_files and related_files can provide additional context")
    appendLine("  ** Explores contradictions and tensions between positions")
    appendLine("  ** Generates synthesis that transcends opposition")
    appendLine("  ** Iterates to progressively higher levels of understanding")
    appendLine("  ** Produces structured dialectical analysis with final synthesis")
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val tabs = TabbedDisplay(task)
    val overviewTask = tabs.newTask("Overview")
    overviewTask.header("Dialectical Reasoning Analysis", level = 1)

    task.ui.pool.submit {
      val transcript = task.newUserFileStream(transcriptFile())
      val startTime = System.currentTimeMillis()
      var stepStartTime = startTime
      log.info("Starting DialecticalReasoningTask analysis.")
      try {
        val thesis = executionConfig?.thesis
        val antithesis = executionConfig?.antithesis
        if (thesis.isNullOrBlank() || antithesis.isNullOrBlank()) {
          val errorMsg = "Both thesis and antithesis must be specified"
          log.error(errorMsg)
          task.error(Exception(errorMsg))
          transcript?.write(buildString {
            appendLine("## Error")
            appendLine()
            appendLine("CONFIGURATION ERROR: $errorMsg")
          }.toByteArray())
          resultFn("CONFIGURATION ERROR: $errorMsg")
          return@submit
        }

        val context = executionConfig.context ?: "general domain"
        val synthesisLevels = executionConfig.synthesis_levels.coerceIn(1, 5)
        val preserveStrengths = executionConfig.preserve_strengths
        val tc = typeConfig ?: DialecticalReasoningTypeConfig()

        log.info("DialecticalReasoningTask configured with thesis='${thesis.take(50)}...', levels=$synthesisLevels")

        val api = defaultSmart ?: return@submit
        overviewTask.add(buildString {
          appendLine("**Context:** $context")
          appendLine("**Synthesis Levels:** $synthesisLevels")
          appendLine("**Preserve Strengths:** ${if (preserveStrengths) "Yes" else "No"}")
          appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
          appendLine()
          appendLine("---")
          appendLine("## Progress")
          appendLine("*Initializing dialectical analysis...*")
        }.renderMarkdown(true))

        transcript?.write(buildString {
          appendLine("# Dialectical Reasoning Analysis")
          appendLine()
          appendLine("**Context:** $context")
          appendLine("**Synthesis Levels:** $synthesisLevels")
          appendLine("**Preserve Strengths:** ${if (preserveStrengths) "Yes" else "No"}")
          appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
          appendLine()
          appendLine("---")
        }.toByteArray())

        val priorContext = getPriorCode(agent.executionState)
        val relatedFilesContent = getRelatedFilesContent()
        val inputFilesContent = getInputFileCode()
        val combinedContext = listOfNotNull(
          if (priorContext.isNotBlank()) "Prior Context:\n$priorContext" else null,
          if (relatedFilesContent.isNotBlank()) "Related Information:\n$relatedFilesContent" else null,
          if (inputFilesContent.isNotBlank()) "Input Files:\n$inputFilesContent" else null
        ).joinToString("\n\n")

        if (priorContext.isNotBlank() || relatedFilesContent.isNotBlank() || inputFilesContent.isNotBlank()) {
          val contextTask = tabs.newTask("Context")
          contextTask.header("Context Information", level = 1)
          if (priorContext.isNotBlank()) {
            contextTask.expandable(
              "Prior Task Results",
              priorContext.truncateForDisplay().renderMarkdown(true)
            )
          }
          if (relatedFilesContent.isNotBlank()) {
            contextTask.expandable(
              "Related Files",
              relatedFilesContent.truncateForDisplay().renderMarkdown(true)
            )
          }
          if (inputFilesContent.isNotBlank()) {
            contextTask.expandable(
              "Input Files",
              inputFilesContent.truncateForDisplay().renderMarkdown(true)
            )
          }
          contextTask.complete()
        }

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Dialectical Analysis\n\n")
        resultBuilder.append("**Context:** $context\n\n")

        fun writeToTranscript(header: String, content: String, timeMs: Long) {
          transcript?.write(buildString {
            appendLine("## $header")
            appendLine(content)
            appendLine()
            appendLine("**Status:** ✅ Complete (${timeMs / 1000.0}s)")
            appendLine()
            appendLine("---")
          }.toByteArray())
        }

        // Step 1: Analyze Thesis
        log.info("Analyzing thesis")
        val thesisTask = tabs.newTask("Thesis")
        thesisTask.header("Thesis Analysis", level = 1)
        thesisTask.add("**Statement:** $thesis\n\n*Analyzing...*".renderMarkdown(true))

        val thesisPrompt = buildString {
          appendLine(tc.thesis_analysis_prompt)
          appendLine()
          appendLine("Context: $context")
          appendLine()
          if (combinedContext.isNotBlank()) {
            appendLine(combinedContext)
            appendLine()
          }
          appendLine("Your task is to thoroughly analyze the thesis:")
          appendLine("\"$thesis\"")
          appendLine()
          appendLine("Provide:")
          appendLine("1. Core claims and assumptions")
          appendLine("2. Strengths and supporting evidence")
          appendLine("3. Internal logic and coherence")
          appendLine("4. Scope and applicability")
          appendLine("5. Potential limitations or blind spots")
          appendLine()
          appendLine("Be thorough and objective in your analysis.")
        }

        val thesisAgent = ChatAgent(
          prompt = thesisPrompt,
          model = api,
          temperature = tc.analysis_temperature
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
        thesisTask.complete()

        overviewTask.add("\n✅ Thesis analysis complete\n\n*Analyzing antithesis...*".renderMarkdown(true))

        // Step 2: Analyze Antithesis
        log.info("Analyzing antithesis")
        val antithesisTask = tabs.newTask("Antithesis")
        antithesisTask.header("Antithesis Analysis", level = 1)
        antithesisTask.add("**Statement:** $antithesis\n\n*Analyzing...*".renderMarkdown(true))

        val antithesisPrompt = buildString {
          appendLine(tc.antithesis_analysis_prompt)
          appendLine()
          appendLine("Context: $context")
          appendLine()
          if (combinedContext.isNotBlank()) {
            appendLine(combinedContext)
            appendLine()
          }
          appendLine("The thesis being opposed is:")
          appendLine("\"$thesis\"")
          appendLine()
          appendLine("Your task is to thoroughly analyze the antithesis:")
          appendLine("\"$antithesis\"")
          appendLine()
          appendLine("Provide:")
          appendLine("1. Core claims and assumptions")
          appendLine("2. Strengths and supporting evidence")
          appendLine("3. How it challenges or contradicts the thesis")
          appendLine("4. Internal logic and coherence")
          appendLine("5. Scope and applicability")
          appendLine("6. Potential limitations or blind spots")
          appendLine()
          appendLine("Be thorough and objective in your analysis.")
        }

        val antithesisAgent = ChatAgent(
          prompt = antithesisPrompt,
          model = api,
          temperature = tc.analysis_temperature
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
        antithesisTask.complete()

        overviewTask.add("\n✅ Antithesis analysis complete\n\n*Exploring contradictions...*".renderMarkdown(true))

        // Step 3: Explore Contradictions
        log.info("Exploring contradictions and tensions")
        val contradictionsTask = tabs.newTask("Contradictions")
        contradictionsTask.header("Contradictions & Tensions", level = 1)
        contradictionsTask.add("*Analyzing...*".renderMarkdown(true))

        val contradictionsPrompt = buildString {
          appendLine(tc.contradictions_prompt)
          appendLine()
          appendLine("Context: $context")
          if (combinedContext.isNotBlank()) {
            appendLine(combinedContext)
          }
          appendLine()
          appendLine("Thesis: \"$thesis\"")
          appendLine("Thesis Analysis:")
          appendLine(thesisAnalysis)
          appendLine()
          appendLine("Antithesis: \"$antithesis\"")
          appendLine("Antithesis Analysis:")
          appendLine(antithesisAnalysis)
          appendLine()
          appendLine("Your task is to identify and explore:")
          appendLine("1. Direct contradictions between the positions")
          appendLine("2. Underlying tensions and incompatibilities")
          appendLine("3. Areas of partial overlap or agreement")
          appendLine("4. Root causes of the opposition")
          appendLine("5. What each side reveals about the other's limitations")
          appendLine("6. The deeper question or problem both are trying to address")
          appendLine()
          appendLine("Be thorough in exploring the dialectical tension.")
        }

        val contradictionsAgent = ChatAgent(
          prompt = contradictionsPrompt,
          model = api,
          temperature = tc.contradictions_temperature
        )

        val contradictionsAnalysis =
          contradictionsAgent.answer(listOf("Explore the contradictions and tensions."))
        val contradictionsTime = System.currentTimeMillis() - stepStartTime
        log.info("Contradictions analysis completed in ${contradictionsTime}ms")
        stepStartTime = System.currentTimeMillis()

        writeToTranscript("Contradictions & Tensions", contradictionsAnalysis, contradictionsTime)
        resultBuilder.append("## Contradictions & Tensions\n\n$contradictionsAnalysis\n\n")

        contradictionsTask.header("Analysis", level = 2)
        contradictionsTask.add(contradictionsAnalysis.renderMarkdown(true))
        contradictionsTask.add(
          "<hr/>\n**Status:** ✅ Complete (${contradictionsTime / 1000.0}s)".renderMarkdown(
            true
          )
        )
        contradictionsTask.complete()

        overviewTask.add(
          "\n✅ Contradictions explored\n\n*Generating synthesis (Level 1)...*".renderMarkdown(
            true
          )
        )

        // Step 4: Iterative Synthesis
        var previousSynthesis = ""
        val synthesisResults = mutableListOf<String>()

        for (level in 1..synthesisLevels) {
          log.info("Generating synthesis level $level")
          val synthesisTask = tabs.newTask("Synthesis L$level")
          synthesisTask.header("Synthesis - Level $level", level = 1)
          synthesisTask.add("*Generating higher-level synthesis...*".renderMarkdown(true))

          val synthesisPrompt = if (level == 1) {
            buildString {
              appendLine(tc.synthesis_prompt)
              appendLine()
              appendLine("Context: $context")
              if (combinedContext.isNotBlank()) {
                appendLine(combinedContext)
              }
              appendLine()
              appendLine("Thesis: \"$thesis\"")
              appendLine("Analysis: ${thesisAnalysis.take(1000)}...")
              appendLine()
              appendLine("Antithesis: \"$antithesis\"")
              appendLine("Analysis: ${antithesisAnalysis.take(1000)}...")
              appendLine()
              appendLine("Contradictions & Tensions:")
              appendLine("${contradictionsAnalysis.take(1000)}...")
              appendLine()
              if (preserveStrengths) {
                appendLine("IMPORTANT: Preserve the valuable strengths from both sides in your synthesis.")
                appendLine()
              }
              appendLine("Your task is to generate a synthesis that:")
              appendLine("1. Transcends the opposition by finding a higher-level perspective")
              appendLine("2. Incorporates valid insights from both thesis and antithesis")
              appendLine("3. Resolves contradictions through reframing or integration")
              if (preserveStrengths) {
                appendLine("4. Preserves the strengths of both positions")
              } else {
                appendLine("4. Moves beyond both positions")
              }
              appendLine("5. Addresses the deeper question both sides are grappling with")
              appendLine("6. Provides a more complete or nuanced understanding")
              appendLine()
              appendLine("Provide:")
              appendLine("- The synthesis statement (a new position that transcends the opposition)")
              appendLine("- Explanation of how it integrates both sides")
              appendLine("- What it preserves from thesis and antithesis")
              appendLine("- What new understanding it provides")
              appendLine("- Any remaining tensions or limitations")
              appendLine()
              appendLine("Be creative and insightful in finding the higher-level synthesis.")
            }
          } else {
            buildString {
              appendLine("You are generating a higher-level dialectical synthesis (Level $level).")
              appendLine()
              appendLine("Context: $context")
              if (combinedContext.isNotBlank()) {
                appendLine(combinedContext)
              }
              appendLine()
              appendLine("Previous Synthesis (Level ${level - 1}):")
              appendLine(previousSynthesis)
              appendLine()
              appendLine("This synthesis itself can be seen as a new thesis. Consider what antithesis or limitation it might have, and generate an even higher-level synthesis.")
              appendLine()
              appendLine("Your task is to:")
              appendLine("1. Identify limitations or tensions in the previous synthesis")
              appendLine("2. Consider what perspective or position challenges it")
              appendLine("3. Generate a new synthesis at a higher level of abstraction")
              appendLine("4. Integrate insights from all previous levels")
              if (preserveStrengths) {
                appendLine("5. Continue to preserve valuable strengths from original positions")
              } else {
                appendLine("5. Continue to transcend previous limitations")
              }
              appendLine()
              appendLine("Provide:")
              appendLine("- The new synthesis statement")
              appendLine("- How it transcends the previous level")
              appendLine("- What new understanding it provides")
              appendLine("- Connection to original thesis and antithesis")
              appendLine("- Any remaining tensions or areas for further exploration")
              appendLine()
              appendLine("Aim for progressively deeper insight and integration.")
            }
          }

          val synthesisAgent = ChatAgent(
            prompt = synthesisPrompt,
            model = api,
            temperature = tc.synthesis_temperature
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
          synthesisTask.add(
            "<hr/>\n**Status:** ✅ Complete (${synthesisTime / 1000.0}s)".renderMarkdown(true)
          )
          synthesisTask.complete()

          overviewTask.add("\n✅ Synthesis Level $level complete".renderMarkdown(true))
          if (level < synthesisLevels) {
            overviewTask.add(
              "\n*Generating synthesis (Level ${level + 1})...*".renderMarkdown(true)
            )
          }
        }

        // Step 5: Final Integration
        log.info("Generating final integration")
        val integrationTask = tabs.newTask("Final Integration")
        integrationTask.header("Final Integration", level = 1)
        integrationTask.add("*Synthesizing all levels...*".renderMarkdown(true))

        val integrationPrompt = buildString {
          appendLine(tc.integration_prompt)
          appendLine()
          appendLine("Context: $context")
          if (combinedContext.isNotBlank()) {
            appendLine(combinedContext)
          }
          appendLine()
          appendLine("Original Thesis: \"$thesis\"")
          appendLine("Original Antithesis: \"$antithesis\"")
          appendLine()
          appendLine("All Synthesis Levels:")
          synthesisResults.forEachIndexed { index, s ->
            appendLine("Level ${index + 1}:")
            appendLine("${s.take(1000)}...")
            appendLine()
          }
          appendLine("Your task is to provide a final integration that:")
          appendLine("1. Summarizes the dialectical journey from thesis-antithesis to final synthesis")
          appendLine("2. Highlights key insights gained at each level")
          appendLine("3. Explains how the final synthesis resolves the original contradiction")
          appendLine("4. Identifies practical implications or applications")
          appendLine("5. Notes any remaining questions or areas for further exploration")
          appendLine("6. Provides actionable recommendations based on the synthesis")
          appendLine()
          appendLine("Be thorough yet concise in your final integration.")
        }

        val integrationAgent = ChatAgent(
          prompt = integrationPrompt,
          model = api,
          temperature = tc.integration_temperature
        )

        val finalIntegration = integrationAgent.answer(listOf("Provide the final integration."))
        val integrationTime = System.currentTimeMillis() - stepStartTime
        log.info("Final integration completed in ${integrationTime}ms")

        writeToTranscript("Final Integration", finalIntegration, integrationTime)
        resultBuilder.append("## Final Integration\n\n$finalIntegration")

        integrationTask.header("Result", level = 2)
        integrationTask.add(finalIntegration.renderMarkdown(true))
        integrationTask.add(
          "<hr/>\n**Status:** ✅ Complete (${integrationTime / 1000.0}s)".renderMarkdown(true)
        )
        integrationTask.complete()

        val totalTime = System.currentTimeMillis() - startTime
        log.info("DialecticalReasoningTask completed in ${totalTime}ms")

        overviewTask.add(buildString {
          appendLine()
          appendLine("---")
          appendLine("## ✅ Dialectical Analysis Complete")
          appendLine("**Total Time:** ${totalTime / 1000.0}s")
          appendLine("**Synthesis Levels:** $synthesisLevels")
          appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        }.renderMarkdown(true))
        overviewTask.complete()

        transcript?.write(buildString {
          appendLine("## Summary")
          appendLine("**Total Time:** ${totalTime / 1000.0}s")
          appendLine("**Synthesis Levels:** $synthesisLevels")
          appendLine("**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        }.toByteArray())

        task.complete()
        resultFn(buildString {
          appendLine("## Dialectical Analysis Summary")
          appendLine("* **Thesis:** $thesis")
          appendLine("* **Antithesis:** $antithesis")
          appendLine("* **Synthesis Levels:** $synthesisLevels")
          appendLine("* **Final Synthesis:** ${previousSynthesis.take(500)}...")
          appendLine("* Full report available in session transcript.")
        })

      } catch (e: Exception) {
        // Triple Log Rule: UI, SLF4J, Transcript
        log.error("Error in DialecticalReasoningTask: ${e.message}", e)
        task.error(e)
        transcript?.write(buildString {
          appendLine("## Error")
          appendLine("<details><summary>Stack Trace</summary>")
          appendLine()
          appendLine("```")
          appendLine(e.stackTraceToString())
          appendLine("```")
          appendLine("</details>")
        }.toByteArray())
        overviewTask.add("\n--- \n## ❌ Error Occurred\n**Error:** ${e.message}".renderMarkdown(true))
        overviewTask.complete()
        resultFn("Error during dialectical reasoning: ${e.message}")
      } finally {
        transcript?.close()
      }
    }
  }

  private fun getInputFileCode(): String {
    val inputFiles = executionConfig?.input_files ?: return ""
    return inputFiles.flatMap { pattern ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
          matcher.matches(root.relativize(it.toPath())) -> true
          it.isDirectory -> true
          else -> false
        }
      }
    }.filter { file -> file.isFile && file.exists() }
      .distinct()
      .sortedBy { it.path }
      .joinToString("\n\n") { file ->
        try {
          val relativePath = root.relativize(file.toPath()).toString()
          val content = file.readText()
          "# $relativePath\n\n```\n$content\n```"
        } catch (e: Throwable) {
          log.warn("Error reading file: ${file.path}", e)
          ""
        }
      }
  }

  private fun getRelatedFilesContent(): String {
    val relatedFiles = executionConfig?.related_files ?: return ""
    return relatedFiles.flatMap { pattern ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
          matcher.matches(root.relativize(it.toPath())) -> true
          it.isDirectory -> true
          else -> false
        }
      }
    }.filter { file -> file.isFile && file.exists() }
      .distinct()
      .sortedBy { it.path }
      .joinToString("\n\n") { file ->
        try {
          val relativePath = root.relativize(file.toPath()).toString()
          val content = file.readText()
          "### $relativePath\n\n```\n$content\n```"
        } catch (e: Exception) {
          log.warn("Error reading file: ${file.path}", e)
          ""
        }
      }
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(DialecticalReasoningTask::class.java)

    @JvmStatic
    val DialecticalReasoning = TaskType(
      name = "DialecticalReasoning",
      category = "Reasoning",
      taskClass = DialecticalReasoningTask::class.java,
      executionConfigClass = DialecticalReasoningTaskExecutionConfigData::class.java,
      taskSettingsClass = DialecticalReasoningTypeConfig::class.java,
      description = "Resolve contradictions through thesis-antithesis-synthesis",
      tooltipHtml = buildString {
        appendLine("Applies dialectical reasoning to resolve contradictions and find higher-level synthesis.")
        appendLine("<ul>")
        appendLine("  <li>Analyzes thesis and antithesis positions thoroughly</li>")
        appendLine("  <li>Explores contradictions and tensions between positions</li>")
        appendLine("  <li>Generates synthesis that transcends opposition</li>")
        appendLine("  <li>Iterates through multiple synthesis levels for deeper understanding</li>")
        appendLine("  <li>Preserves valuable aspects from both sides</li>")
        appendLine("  <li>Provides final integration with practical implications</li>")
        appendLine("  <li>Useful for architectural debates, requirement conflicts, and design philosophy</li>")
        appendLine("</ul>")
      },
    )
  }
}