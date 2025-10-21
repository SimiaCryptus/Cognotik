package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BrainstormingTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: BrainstormingTaskExecutionConfigData?
) : AbstractTask<BrainstormingTask.BrainstormingTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  val maxSummaryLength: Int = 10000

  data class BrainstormedOption(
    val title: String = "",
    val description: String = "",
    val category: String? = null
  )

  data class BrainstormResult(
    val options: List<BrainstormedOption> = emptyList()
  )

  data class OptionAnalysis(
    val pros: List<String> = emptyList(),
    val cons: List<String> = emptyList(),
    val feasibility: String = "",
    val impact: String = "",
    val risks: List<String> = emptyList(),
    val requirements: List<String> = emptyList()
  )


  class BrainstormingTaskExecutionConfigData(
    @Description("The problem or question to brainstorm solutions for")
    val problem_statement: String? = null,
    @Description("Number of options to generate (default: 5-10)")
    val target_option_count: Int = 7,
    @Description("Categories or domains to consider (optional)")
    val categories: List<String>? = null,
    @Description("Constraints or requirements to consider")
    val constraints: List<String>? = null,
    @Description("Whether to include creative/unconventional options")
    val include_creative_options: Boolean = true,
    @Description("Depth of analysis for each option (brief/moderate/detailed)")
    val analysis_depth: String = "moderate",
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,

    ) : TaskExecutionConfig(
    task_type = Brainstorming.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  )

  override fun promptSegment(): String {
    return """
Brainstorming - Generate and analyze multiple solution options
  ** Specify the problem or question to brainstorm solutions for
  ** Configure target number of options (default: 7)
  ** Optionally specify categories or domains to explore
  ** Define constraints or requirements
  ** Enable/disable creative/unconventional options
  ** Set analysis depth (brief/moderate/detailed)
  ** Generates diverse options, analyzes each independently
  ** Provides comparative summary with recommendations
  ** Useful for:
     - Solution exploration
     - Decision making
     - Strategic planning
     - Problem solving
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
    log.info("Starting BrainstormingTask for problem: '${executionConfig?.problem_statement}'")

    val problemStatement = executionConfig?.problem_statement
    if (problemStatement.isNullOrBlank()) {
      val errorMsg = "CONFIGURATION ERROR: No problem statement specified"
      log.error(errorMsg)
      task.safeComplete(errorMsg, log)
      resultFn(errorMsg)
      return
    }

    val targetCount = executionConfig.target_option_count.coerceIn(3, 20)
    val categories = executionConfig.categories?.joinToString(", ") ?: "any relevant domain"
    val constraints = executionConfig.constraints ?: emptyList()
    val includeCreative = executionConfig.include_creative_options
    val analysisDepth = executionConfig.analysis_depth

    log.info("Configuration: targetCount=$targetCount, categories=$categories, includeCreative=$includeCreative, analysisDepth=$analysisDepth")

    val ui = task.ui

    try {
      // Create tabbed display for organized output
      val tabs = TabbedDisplay(task)

      // Overview tab
      val overviewTask = task.ui.newTask(false)
      tabs["Overview"] = overviewTask.placeholder

      val overviewContent = buildString {
        appendLine("# Brainstorming Session")
        appendLine()
        appendLine("**Problem Statement:** $problemStatement")
        appendLine()
        appendLine("**Target Options:** $targetCount")
        appendLine()
        appendLine("**Categories:** $categories")
        appendLine()
        if (constraints.isNotEmpty()) {
          appendLine("**Constraints:**")
          constraints.forEach { appendLine("- $it") }
          appendLine()
        }
        appendLine("**Include Creative Options:** ${if (includeCreative) "Yes" else "No"}")
        appendLine()
        appendLine("**Analysis Depth:** $analysisDepth")
        appendLine()
        appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## Progress")
        appendLine()
        appendLine("*Generating options...*")
      }
      overviewTask.add(MarkdownUtil.renderMarkdown(overviewContent, ui = ui))
      task.update()

      // Gather context from previous tasks
      val priorContext = getPriorCode(agent.executionState)
      if (priorContext.isNotBlank()) {
        log.debug("Found prior context: ${priorContext.length} characters")
        val contextTask = task.ui.newTask(false)
        tabs["Context"] = contextTask.placeholder
        contextTask.add(
          MarkdownUtil.renderMarkdown(
            """
            |# Context from Previous Tasks
            |
            |${priorContext.truncateForDisplay()}
            """.trimMargin(), ui = ui
          )
        )
        task.update()
      }

      // Step 1: Generate options using ParsedActor for structured output
      log.info("Generating $targetCount options")
      val optionsTask = task.ui.newTask(false)
      tabs["Generated Options"] = optionsTask.placeholder
      optionsTask.add(MarkdownUtil.renderMarkdown("## Generated Options\n\n🔄 Brainstorming options...", ui = ui))
      task.update()

      val brainstormPrompt = buildBrainstormPrompt(
        problemStatement,
        targetCount,
        categories,
        constraints,
        includeCreative,
        priorContext
      )

      val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return
      val parsingChatter = orchestrationConfig.parsingChatter.getChildClient(task)
      val defaultChatter = api.getChildClient(task)
      val brainstormAgent = ParsedAgent(
        resultClass = BrainstormResult::class.java,
        prompt = brainstormPrompt,
        model = defaultChatter,
        temperature = if (includeCreative) 0.8 else 0.6,
        parsingChatter = parsingChatter
      )

      val brainstormResult = brainstormAgent.answer(listOf(brainstormPrompt))
      val options = brainstormResult.obj.options

      log.info("Generated ${options.size} options")

      // Display generated options
      optionsTask.add(
        MarkdownUtil.renderMarkdown(
          buildString {
            appendLine("## Generated Options")
            appendLine()
            appendLine("✅ Generated ${options.size} options")
            appendLine()
            options.forEachIndexed { index, option ->
              appendLine("### ${index + 1}. ${option.title}")
              if (option.category != null) {
                appendLine("**Category:** ${option.category}")
              }
              appendLine()
              appendLine(option.description)
              appendLine()
            }
          }, ui = ui
        )
      )
      task.update()

      // Update overview
      overviewTask.add(
        MarkdownUtil.renderMarkdown(
          """
          |
          |✅ Generated ${options.size} options
          |
          |*Analyzing each option...*
          """.trimMargin(), ui = ui
        )
      )
      task.update()

      // Step 2: Analyze each option independently
      log.info("Analyzing ${options.size} options")
      val analyses = mutableMapOf<Int, OptionAnalysis>()
      val analysisAgent = ParsedAgent(
        resultClass = OptionAnalysis::class.java,
        prompt = "", // Will be set per option
        model = defaultChatter,
        temperature = 0.3,
        parsingChatter = parsingChatter
      )

      options.forEachIndexed { index, option ->
        val optionNumber = index + 1
        log.debug("Analyzing option $optionNumber: ${option.title}")

        val analysisTask = task.ui.newTask(false)
        tabs["Option $optionNumber Analysis"] = analysisTask.placeholder
        analysisTask.add(
          MarkdownUtil.renderMarkdown(
            """
            |# Option $optionNumber: ${option.title}
            |
            |🔄 Analyzing...
            """.trimMargin(), ui = ui
          )
        )
        task.update()

        val analysisPrompt = buildAnalysisPrompt(
          option,
          problemStatement,
          constraints,
          analysisDepth
        )

        val analysis = analysisAgent.answer(listOf(analysisPrompt))
        analyses[optionNumber] = analysis.obj

        // Display analysis
        analysisTask.add(
          MarkdownUtil.renderMarkdown(
            buildString {
              appendLine("# Option $optionNumber: ${option.title}")
              appendLine()
              if (option.category != null) {
                appendLine("**Category:** ${option.category}")
              }
              appendLine()
              appendLine("## Description")
              appendLine(option.description)
              appendLine()
              appendLine("## Analysis")
              appendLine()
              appendLine("### ✅ Pros")
              analysis.obj.pros.forEach { appendLine("- $it") }
              appendLine()
              appendLine("### ❌ Cons")
              analysis.obj.cons.forEach { appendLine("- $it") }
              appendLine()
              appendLine("### 📊 Feasibility")
              appendLine(analysis.obj.feasibility)
              appendLine()
              appendLine("### 💥 Impact")
              appendLine(analysis.obj.impact)
              appendLine()
              appendLine("### ⚠️ Risks")
              analysis.obj.risks.forEach { appendLine("- $it") }
              appendLine()
              appendLine("### 📋 Requirements")
              analysis.obj.requirements.forEach { appendLine("- $it") }
              appendLine()
              appendLine("---")
              appendLine()
              appendLine("**Status:** ✅ Analysis complete")
            }, ui = ui
          )
        )
        task.update()

        // Update overview
        overviewTask.add(
          MarkdownUtil.renderMarkdown(
            """
            |
            |✅ Analyzed option $optionNumber: ${option.title}
            """.trimMargin(), ui = ui
          )
        )
        task.update()
      }

      // Step 3: Generate comparative summary
      log.info("Generating comparative summary")
      val summaryTask = task.ui.newTask(false)
      tabs["Summary & Recommendations"] = summaryTask.placeholder
      summaryTask.add(
        MarkdownUtil.renderMarkdown(
          "## Summary & Recommendations\n\n🔄 Synthesizing findings...",
          ui = ui
        )
      )
      task.update()

      val summaryPrompt = buildSummaryPrompt(
        problemStatement,
        options,
        analyses
      )

      val summaryAgent = ChatAgent(
        prompt = summaryPrompt,
        model = defaultChatter,
        temperature = 0.4
      )

      val summary = summaryAgent.answer(listOf(summaryPrompt))

      summaryTask.add(
        MarkdownUtil.renderMarkdown(
          buildString {
            appendLine("## Summary & Recommendations")
            appendLine()
            appendLine("✅ Analysis complete")
            appendLine()
            appendLine(summary)
          }, ui = ui
        )
      )
      task.update()

      // Build final concise output
      val finalOutput = buildString {
        appendLine("# Brainstorming Results: $problemStatement")
        appendLine()
        appendLine("## Options Generated: ${options.size}")
        appendLine()
        options.forEachIndexed { index, option ->
          appendLine("### ${index + 1}. ${option.title}")
          appendLine(option.description.truncateForDisplay())
          appendLine()
        }
        appendLine("## Key Findings")
        appendLine()
        appendLine(summary.truncateForDisplay())
        appendLine()
        appendLine("---")
        appendLine("**Options:** ${options.size} | **Analysis Depth:** $analysisDepth | **Time:** ${(System.currentTimeMillis() - startTime) / 1000}s")
      }

      val totalTime = System.currentTimeMillis() - startTime
      log.info("BrainstormingTask completed: total_time=${totalTime}ms, options=${options.size}, output_size=${finalOutput.length} chars")

      // Update overview with completion
      overviewTask.add(
        MarkdownUtil.renderMarkdown(
          """
          |
          |---
          |
          |## ✅ Brainstorming Complete
          |
          |**Total Time:** ${totalTime / 1000.0}s
          |
          |**Options Generated:** ${options.size}
          |
          |**Options Analyzed:** ${analyses.size}
          |
          |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
          """.trimMargin(), ui = ui
        )
      )
      task.update()

      task.safeComplete("Generated and analyzed ${options.size} options in ${totalTime / 1000}s", log)
      resultFn(finalOutput)

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      log.error("BrainstormingTask failed after ${duration}ms for problem: $problemStatement", e)
      task.error(e)

      val errorOutput = buildString {
        appendLine("# Error in Brainstorming Task")
        appendLine()
        appendLine("**Problem:** $problemStatement")
        appendLine()
        appendLine("**Error:** ${e.message}")
        appendLine()
        appendLine("**Type:** ${e.javaClass.simpleName}")
      }

      task.safeComplete("Brainstorming failed: ${e.message}", log)
      resultFn(errorOutput)
    }
  }

  private fun buildBrainstormPrompt(
    problemStatement: String,
    targetCount: Int,
    categories: String,
    constraints: List<String>,
    includeCreative: Boolean,
    priorContext: String
  ): String {
    val constraintsSection = if (constraints.isNotEmpty()) {
      """
      |
      |## Constraints to Consider:
      |${constraints.joinToString("\n") { "- $it" }}
      """.trimMargin()
    } else {
      ""
    }

    val contextSection = if (priorContext.isNotBlank()) {
      """
      |
      |## Context from Previous Tasks:
      |$priorContext
      """.trimMargin()
    } else {
      ""
    }

    return """
You are a creative problem solver and brainstorming expert. Your task is to generate diverse, well-thought-out options for addressing a problem.

## Problem Statement:
$problemStatement

## Target:
Generate exactly $targetCount distinct options.

## Categories/Domains to Consider:
$categories
$constraintsSection
$contextSection

## Brainstorming Guidelines:
1. **Diversity**: Ensure options span different approaches and perspectives
2. **Clarity**: Each option should be clearly described and actionable
3. **Relevance**: All options must address the core problem
${if (includeCreative) "4. **Creativity**: Include unconventional and innovative approaches" else "4. **Practicality**: Focus on realistic, proven approaches"}
5. **Categorization**: Assign each option to a relevant category

## Output Format:
Generate a JSON object with an "options" array. Each option should have:
- title: A concise, descriptive name (5-10 words)
- description: A clear explanation of the option (2-4 sentences)
- category: The domain or approach category

Generate $targetCount diverse options now.
        """.trimIndent()
  }

  private fun buildAnalysisPrompt(
    option: BrainstormedOption,
    problemStatement: String,
    constraints: List<String>,
    analysisDepth: String
  ): String {
    val depthGuidance = when (analysisDepth.lowercase()) {
      "brief" -> "Provide concise analysis with 2-3 items per category."
      "detailed" -> "Provide comprehensive analysis with 5-7 items per category and detailed explanations."
      else -> "Provide moderate analysis with 3-5 items per category."
    }

    val constraintsSection = if (constraints.isNotEmpty()) {
      """
      |
      |## Constraints:
      |${constraints.joinToString("\n") { "- $it" }}
      """.trimMargin()
    } else {
      ""
    }

    return """
You are an analytical expert evaluating solution options. Analyze this option independently and objectively.

## Problem Statement:
$problemStatement

## Option to Analyze:
**Title:** ${option.title}
**Description:** ${option.description}
${if (option.category != null) "**Category:** ${option.category}" else ""}
$constraintsSection

## Analysis Instructions:
$depthGuidance

Evaluate the following aspects:

1. **Pros**: Advantages and benefits of this option
2. **Cons**: Disadvantages and limitations
3. **Feasibility**: How realistic is implementation? (Consider technical, resource, and organizational factors)
4. **Impact**: What outcomes and effects can be expected?
5. **Risks**: What could go wrong? What are the potential negative consequences?
6. **Requirements**: What resources, skills, or conditions are needed?

## Output Format:
Provide a JSON object with these fields:
- pros: Array of strings (advantages)
- cons: Array of strings (disadvantages)
- feasibility: String (assessment of how realistic this is)
- impact: String (expected outcomes and effects)
- risks: Array of strings (potential problems)
- requirements: Array of strings (what's needed to implement)

Analyze this option now.
        """.trimIndent()
  }

  private fun buildSummaryPrompt(
    problemStatement: String,
    options: List<BrainstormedOption>,
    analyses: Map<Int, OptionAnalysis>
  ): String {
    val optionsWithAnalysis = buildString {
      options.forEachIndexed { index, option ->
        val optionNumber = index + 1
        val analysis = analyses[optionNumber]
        appendLine("### Option $optionNumber: ${option.title}")
        appendLine("**Description:** ${option.description}")
        if (analysis != null) {
          appendLine("**Pros:** ${analysis.pros.size} identified")
          appendLine("**Cons:** ${analysis.cons.size} identified")
          appendLine("**Feasibility:** ${analysis.feasibility.take(100)}...")
          appendLine("**Key Risks:** ${analysis.risks.take(2).joinToString("; ")}")
        }
        appendLine()
      }
    }

    return """
You are a strategic advisor synthesizing brainstorming results. Review all options and their analyses to provide actionable recommendations.

## Problem Statement:
$problemStatement

## Options and Analyses:
$optionsWithAnalysis

## Your Task:
Provide a comprehensive summary that includes:

1. **Overview**: Brief recap of the brainstorming session
2. **Comparative Analysis**: 
   - Which options are most feasible?
   - Which have the highest potential impact?
   - Which have the lowest risk?
3. **Top Recommendations**: 
   - Identify the top 2-3 options
   - Explain why these are recommended
   - Suggest an implementation order if applicable
4. **Hybrid Approaches**: 
   - Can any options be combined?
   - Are there synergies between options?
5. **Next Steps**: 
   - What should be done to move forward?
   - What additional information is needed?

Provide a well-structured, actionable summary now.
        """.trimIndent()
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(BrainstormingTask::class.java)
    val Brainstorming = TaskType(
      "Brainstorming",
      BrainstormingTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Generate and analyze multiple solution options",
      """
              Systematically generates diverse options and analyzes each independently.
              <ul>
                <li>Generates multiple solution options for a given problem</li>
                <li>Analyzes each option independently (pros, cons, feasibility, impact, risks)</li>
                <li>Provides comparative summary with recommendations</li>
                <li>Supports creative and conventional approaches</li>
                <li>Configurable analysis depth and option count</li>
                <li>Identifies hybrid approaches and synergies</li>
                <li>Useful for decision making, strategic planning, and problem solving</li>
              </ul>
            """
    )
  }
}