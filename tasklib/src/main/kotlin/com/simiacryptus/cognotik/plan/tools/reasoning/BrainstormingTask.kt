package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.platform.Description
import com.simiacryptus.cognotik.docs.PaginatedDocumentReader
import com.simiacryptus.cognotik.docs.getDocumentReader
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.util.FileSelectionUtils
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class BrainstormingTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: BrainstormingTaskExecutionConfigData?
) : AbstractTask<BrainstormingTask.BrainstormingTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  protected val codeFiles = mutableMapOf<Path, String>()

  data class BrainstormedOption(
    @Description("A concise, descriptive name for this option (5-10 words)")
    var title: String = "",
    @Description("A clear explanation of the option (2-4 sentences)")
    var description: String = "",
    @Description("The domain or approach category this option belongs to (optional)")
    var category: String? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (title.isBlank()) return "BrainstormedOption title cannot be blank"
      if (description.isBlank()) return "BrainstormedOption description cannot be blank"
      return null
    }
  }

  data class BrainstormResult(
    @Description("The list of brainstormed options generated for the problem")
    var options: List<BrainstormedOption> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (options.isEmpty()) return "BrainstormResult must contain at least one option"
      options.forEach { option ->
        option.validate()?.let { return it }
      }
      return null
    }
  }

  data class OptionAnalysis(
    @Description("List of advantages and benefits of this option")
    var pros: List<String> = emptyList(),
    @Description("List of disadvantages and limitations of this option")
    var cons: List<String> = emptyList(),
    @Description("Assessment of how realistic implementation is, considering technical, resource, and organizational factors")
    var feasibility: String = "",
    @Description("Expected outcomes and effects of implementing this option")
    var impact: String = "",
    @Description("List of potential problems and negative consequences")
    var risks: List<String> = emptyList(),
    @Description("List of resources, skills, or conditions needed to implement this option")
    var requirements: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (feasibility.isBlank()) return "OptionAnalysis feasibility cannot be blank"
      if (impact.isBlank()) return "OptionAnalysis impact cannot be blank"
      return null
    }
  }

  data class BrainstormingSummary(
    @Description("A brief executive summary of the session findings and general trends")
    var overview: String = "",
    @Description("The 1-based integer index of the single best option")
    var top_option_index: Int = 1,
    @Description("Explanation of why this option was chosen as the winner, compared against runners-up")
    var selection_reasoning: String = "",
    @Description("Concrete actions to take to implement the top recommendation")
    var next_steps: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (overview.isBlank()) return "Summary overview cannot be blank"
      if (selection_reasoning.isBlank()) return "Selection reasoning cannot be blank"
      return null
    }
  }

  class BrainstormingTaskExecutionConfigData(
    @Description("The problem or question to brainstorm solutions for")
    var problem_statement: String? = null,
    @Description("A list of specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
    var related_files: List<String>? = null,
    @Description("Number of options to generate (default: 5-10)")
    var target_option_count: Int = 7,
    @Description("A list of categories or domains to consider (optional)")
    var categories: List<String>? = null,
    @Description("A list of constraints or requirements to consider")
    var constraints: List<String>? = null,
    @Description("Whether to include creative/unconventional options")
    var include_creative_options: Boolean = true,
    @Description("Depth of analysis for each option (brief/moderate/detailed)")
    var analysis_depth: String = "moderate",
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = Brainstorming.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (problem_statement.isNullOrBlank()) {
        return "BrainstormingTaskExecutionConfigData problem_statement cannot be null or blank"
      }
      target_option_count = target_option_count.coerceIn(3, 20)
      return super.validate()
    }
  }

  override fun promptSegment(): String = buildString {
    appendLine("Brainstorming - Generate and analyze multiple solution options")
    appendLine("  ** Specify the problem or question to brainstorm solutions for")
    appendLine("  ** Configure target number of options (default: 7)")
    appendLine("  ** Optionally specify categories or domains to explore")
    appendLine("  ** Define constraints or requirements")
    appendLine("  ** Enable/disable creative/unconventional options")
    appendLine("  ** Set analysis depth (brief/moderate/detailed)")
    appendLine("  ** Generates diverse options, analyzes each independently")
    appendLine("  ** Provides comparative summary with recommendations")
    appendLine("  ** Useful for:")
    appendLine("     - Solution exploration")
    appendLine("     - Decision making")
    appendLine("     - Strategic planning")
    appendLine("     - Problem solving")
  }


  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: ISessionTask,
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

    val executionConfig = this.executionConfig ?: run {
      val errorMsg = "CONFIGURATION ERROR: Execution config is null"
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
    log.info("Input files: ${executionConfig.related_files?.joinToString(", ") ?: "none"}")

    val transcript = task.newUserFileStream(transcriptFile().removeSuffix(".md")+".details.md")
    val transcript_detailed = task.newUserFileStream(transcriptFile())
    transcript?.write("# Brainstorming Session Transcript\n\n".toByteArray())
    transcript?.write("**Input Files:** ${executionConfig.related_files?.joinToString(", ") ?: "none"}\n\n".toByteArray())
    transcript?.write("**Problem Statement:** $problemStatement\n\n".toByteArray())
    transcript?.write(
      "**Started:** ${
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
      }\n\n".toByteArray()
    )
    transcript?.write("---\n\n".toByteArray())
    transcript?.write("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
    try {
      // Initialize transcript
      transcript?.write("# Brainstorming Session Transcript\n\n".toByteArray())
      transcript?.write("**Input Files:** ${executionConfig.related_files?.joinToString(", ") ?: "none"}\n\n".toByteArray())
      transcript?.write("**Problem Statement:** $problemStatement\n\n".toByteArray())
      transcript?.write(
        "**Started:** ${
          LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }\n\n".toByteArray()
      )
      transcript?.write("---\n\n".toByteArray())
      transcript?.write("<div id=\"work-details\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())

      // Create tabbed display for organized output
      val tabs = TabbedDisplay(task)

      // Overview tab
      val overviewTask = tabs.newTask("Overview")
      overviewTask.header("Brainstorming: $problemStatement")

      val overviewContent = buildString {
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
        appendLine(
          "**Started:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }"
        )
        if (!executionConfig?.related_files.isNullOrEmpty()) {
          appendLine()
          appendLine("**Input Files:**")
          executionConfig?.related_files?.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("---")
        appendLine()
      }
      overviewTask.add(MarkdownUtil.renderMarkdown(overviewContent))
      val progressStatus = overviewTask.add(MarkdownUtil.renderMarkdown("🔄 *Generating options...*"))
      task.update()

      // Get input file content
      val inputFileContent = getInputFileCode()
      if (inputFileContent.isNotBlank()) {
        log.debug("Found input file content: ${inputFileContent.length} characters")
        val inputFilesTask = tabs.newTask("Input Files")
        inputFilesTask.add(MarkdownUtil.renderMarkdown(inputFileContent))
        inputFilesTask.complete()
        task.update()
      }


      // Gather context from previous tasks
      val priorContext = getPriorCode(agent.executionState)
      if (priorContext.isNotBlank()) {
        log.debug("Found prior context: ${priorContext.length} characters")
        val contextTask = tabs.newTask("Context")
        val contextContent = buildString {
          appendLine("# Context from Previous Tasks")
          appendLine()
          appendLine(priorContext.truncateForDisplay())
        }
        contextTask.add(MarkdownUtil.renderMarkdown(contextContent))
        contextTask.complete()
        task.update()
      }

      // Step 1: Generate options using ParsedActor for structured output
      log.info("Generating $targetCount options")
      val optionsTask = tabs.newTask("Generated Options")
      optionsTask.add(MarkdownUtil.renderMarkdown("## Generated Options\n\n🔄 Brainstorming options..."))
      task.update()

      val brainstormPrompt = buildBrainstormPrompt(
        problemStatement,
        targetCount,
        categories,
        constraints,
        includeCreative,
        priorContext,
        inputFileContent
      )

      val api = defaultSmart ?: return
      val parsingChatter = defaultFast.getChildClient(task)
      val defaultChatter = api.getChildClient(task)
      val brainstormAgent = ParsedAgent(
        resultClass = BrainstormResult::class.java,
        prompt = brainstormPrompt,
        model = defaultChatter,
        temperature = if (includeCreative) 0.8 else 0.6,
        parsingModel = parsingChatter
      )

      val brainstormResult = brainstormAgent.answer(listOf(brainstormPrompt))
      val options = brainstormResult.obj.options

      log.info("Generated ${options.size} options")
      val generatedOptionsString = buildStringStream { transcript->
        // Write to transcript
        transcript?.write("\n## Generated Options\n\n".toByteArray())
        options.forEachIndexed { index, option ->
          transcript?.write("### ${index + 1}. ${option.title}\n".toByteArray())
          if (option.category != null) {
            transcript?.write("**Category:** ${option.category}\n\n".toByteArray())
          }
          transcript?.write("${option.description}\n\n".toByteArray())
        }
      }
      transcript?.write(generatedOptionsString.toByteArray())

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
          }
        )
      )
      optionsTask.complete()
      task.update()

      // Update overview
      progressStatus?.setLength(0)
      progressStatus?.append(
        MarkdownUtil.renderMarkdown(
          "✅ Generated ${options.size} options\n\n🔄 *Analyzing each option...*"
        )
      )
      task.update()

      // Step 2: Analyze each option independently
      log.info("Analyzing ${options.size} options")
      val analyses = mutableMapOf<Int, OptionAnalysis>()

      options.forEachIndexed { index, option ->
        val optionNumber = index + 1
        log.debug("Analyzing option $optionNumber: ${option.title}")

        val analysisTask = tabs.newTask("Option $optionNumber Analysis")
        val analysisContent = buildString {
          appendLine("# Option $optionNumber: ${option.title}")
          appendLine()
          appendLine("🔄 Analyzing...")
        }
        analysisTask.add(MarkdownUtil.renderMarkdown(analysisContent))
        task.update()

        val analysisPrompt = buildAnalysisPrompt(
          option,
          problemStatement,
          constraints,
          analysisDepth
        )

        val perOptionAgent = ParsedAgent(
          resultClass = OptionAnalysis::class.java,
          prompt = analysisPrompt,
          model = defaultChatter,
          temperature = 0.3,
          parsingModel = parsingChatter
        )
        val analysis = perOptionAgent.answer(listOf(analysisPrompt))
        analyses[optionNumber] = analysis.obj
        // Write analysis to transcript
        transcript?.write("\n## Option $optionNumber Analysis: ${option.title}\n\n".toByteArray())
        transcript?.write("### ✅ Pros\n".toByteArray())
        analysis.obj.pros.forEach { transcript?.write("- $it\n".toByteArray()) }
        transcript?.write("\n### ❌ Cons\n".toByteArray())
        analysis.obj.cons.forEach { transcript?.write("- $it\n".toByteArray()) }
        transcript?.write("\n### 📊 Feasibility\n${analysis.obj.feasibility}\n\n".toByteArray())
        transcript?.write("### 💥 Impact\n${analysis.obj.impact}\n\n".toByteArray())
        transcript?.write("### ⚠️ Risks\n".toByteArray())
        analysis.obj.risks.forEach { transcript?.write("- $it\n".toByteArray()) }
        transcript?.write("\n### 📋 Requirements\n".toByteArray())
        analysis.obj.requirements.forEach { transcript?.write("- $it\n".toByteArray()) }
        transcript?.write("\n---\n\n".toByteArray())


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
            }
          )
        )
        analysisTask.complete()
        task.update()

        // Update overview
        progressStatus?.setLength(0)
        progressStatus?.append(
          MarkdownUtil.renderMarkdown(
            buildString {
              appendLine("✅ Generated ${options.size} options")
              appendLine("✅ Analyzed $optionNumber/${options.size} options")
              appendLine()
              appendLine("🔄 *Analyzing next option...*")
            }
          )
        )
        task.update()
      }

      // Step 3: Generate comparative summary
      log.info("Generating comparative summary")
      val summaryTask = tabs.newTask("Summary & Recommendations")
      summaryTask.add(
        MarkdownUtil.renderMarkdown(
          "## Summary & Recommendations\n\n🔄 Synthesizing findings..."
        )
      )
      progressStatus?.setLength(0)
      progressStatus?.append(
        MarkdownUtil.renderMarkdown(
          buildString {
            appendLine("✅ Generated ${options.size} options")
            appendLine("✅ Analyzed all ${options.size} options")
            appendLine()
            appendLine("🔄 *Synthesizing findings...*")
          }
        )
      )
      task.update()

      val summaryPrompt = buildSummaryPrompt(
        problemStatement,
        options,
        analyses
      )

      val summaryAgent = ParsedAgent(
        resultClass = BrainstormingSummary::class.java,
        prompt = summaryPrompt,
        model = defaultChatter,
        temperature = 0.4,
        parsingModel = parsingChatter
      )

      val summaryResult = summaryAgent.answer(listOf(summaryPrompt)).obj

      // Resolve top option safely
      val topOptionIndex = (summaryResult.top_option_index - 1).coerceIn(0, options.indices.last)
      val topOption = options[topOptionIndex]

      // Construct display string
      val summaryDisplay = buildString {
        appendLine("## 🏆 Top Recommendation: ${topOption.title}")
        appendLine()
        appendLine(summaryResult.selection_reasoning)
        appendLine()
        appendLine("### Overview")
        appendLine(summaryResult.overview)
        appendLine()
        appendLine("### Next Steps")
        appendLine(summaryResult.next_steps)
      }

      summaryTask.add(
        MarkdownUtil.renderMarkdown(
          buildString {
            appendLine("## Summary & Recommendations")
            appendLine()
            appendLine("✅ Analysis complete")
            appendLine()
            appendLine(summaryDisplay)
          }
        )
      )
      summaryTask.complete()
      task.update()
      // Close work-details tab section in transcript
      transcript?.write("\n</div>\n\n".toByteArray())


      val totalTime = System.currentTimeMillis() - startTime
      // Write detailed results to file
      val detailedResults = buildDetailedResults(
        problemStatement,
        options,
        analyses,
        summaryDisplay,
        totalTime
      )

      val dataDir = getOutputFile(".md")?.let {
        if (it.endsWith(".md")) it.removeSuffix(".md") else null
      } ?: "brainstorming"

      val resultsFileName = "${dataDir}_results.md"
      val (resultsLink, resultsFile) = task.createFile(resultsFileName)
      resultsFile?.outputStream()?.use { stream ->
        stream.write(detailedResults.toByteArray(StandardCharsets.UTF_8))
        stream.flush()
      }
      log.info("Saved detailed results to: $resultsLink")

      // Write final output tab section in transcript
      transcript?.write("<div id=\"final-output\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">\n\n".toByteArray())
      // Finalize transcript
      listOf(transcript_detailed, transcript).forEach { transcript ->
        transcript?.write("\n# Brainstorming Results: $problemStatement\n\n".toByteArray())
        transcript?.write("## 🏆 Top Recommendation: ${topOption.title}\n\n".toByteArray())
        transcript?.write("${topOption.description}\n\n".toByteArray())
        transcript?.write("> ${summaryResult.selection_reasoning}\n\n".toByteArray())
        transcript?.write("## Summary\n\n${summaryResult.overview}\n\n".toByteArray())
        transcript?.write("## Session Complete\n\n".toByteArray())
        transcript?.write("**Total Time:** ${totalTime / 1000.0}s\n".toByteArray())
        transcript?.write("**Options Generated:** ${options.size}\n".toByteArray())
        transcript?.write("**Options Analyzed:** ${analyses.size}\n".toByteArray())
        transcript?.write(
          "**Completed:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }\n".toByteArray()
        )
      }
      transcript_detailed?.write(generatedOptionsString.toByteArray())
      
      transcript?.write("\n</div>\n\n".toByteArray())

      // Build final concise output with file links
      val finalOutput = buildString {
        appendLine("# Brainstorming Results: $problemStatement")
        appendLine()
        appendLine("✅ Generated and analyzed ${options.size} options in ${totalTime / 1000}s")
        appendLine()
        appendLine("## 🏆 Top Recommendation: ${topOption.title}")
        appendLine()
        appendLine(topOption.description)
        appendLine()
        appendLine("> ${summaryResult.selection_reasoning}")
        appendLine()
        appendLine("## Summary")
        appendLine(summaryResult.overview.truncateForDisplay())
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## Detailed Results")
        appendLine()
        appendLine("📄 [Full Results]($resultsLink)")
        appendLine()
        appendLine("**Options:** ${options.size} | **Analysis Depth:** $analysisDepth | **Time:** ${totalTime / 1000}s")
        appendLine()
        appendLine()
        options.forEachIndexed { index, option ->
          val prefix = if (index == topOptionIndex) "🏆 " else ""
          appendLine("### $prefix${index + 1}. ${option.title}")
          appendLine()
        }
        appendLine()
        appendLine()
        appendLine("---")
      }

      log.info("BrainstormingTask completed: total_time=${totalTime}ms, options=${options.size}, output_size=${finalOutput.length} chars")

      // Update overview with completion
      progressStatus?.setLength(0)
      progressStatus?.append(
        MarkdownUtil.renderMarkdown(
          buildString {
            appendLine("---")
            appendLine("## ✅ Brainstorming Complete")
            appendLine()
            appendLine("### 🏆 Winner: ${topOption.title}")
            appendLine(summaryResult.selection_reasoning.truncateForDisplay(300))
            appendLine()
            appendLine("**Total Time:** ${totalTime / 1000.0}s | **Options:** ${options.size}")
          }
        )
      )
      overviewTask.complete()
      task.update()

      task.safeComplete("Generated and analyzed ${options.size} options in ${totalTime / 1000}s", log)
      resultFn(finalOutput)

    } catch (e: Exception) {
      val duration = System.currentTimeMillis() - startTime
      log.error("BrainstormingTask failed after ${duration}ms for problem: $problemStatement", e)
      // Write error to transcript
      transcript?.write("\n## ❌ Error Occurred\n\n".toByteArray())
      transcript?.write("**Error:** ${e.message}\n".toByteArray())
      transcript?.write("**Type:** ${e.javaClass.simpleName}\n".toByteArray())
      transcript?.write("<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n".toByteArray())

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
    } finally {
      try {
        transcript?.flush()
        transcript?.close()
      } catch (e: Exception) {
        log.warn("Failed to close transcript stream", e)
      }
    }
  }


  private fun buildBrainstormPrompt(
    problemStatement: String,
    targetCount: Int,
    categories: String,
    constraints: List<String>,
    includeCreative: Boolean,
    priorContext: String,
    inputFileContent: String = ""
  ): String = buildString {
    appendLine("You are a creative problem solver and brainstorming expert. Your task is to generate diverse, well-thought-out options for addressing a problem.")
    appendLine()
    appendLine("## Problem Statement:")
    appendLine(problemStatement)
    appendLine()
    appendLine("## Target:")
    appendLine("Generate exactly $targetCount distinct options.")
    appendLine()
    appendLine("## Categories/Domains to Consider:")
    appendLine(categories)
    if (constraints.isNotEmpty()) {
      appendLine()
      appendLine("## Constraints to Consider:")
      constraints.forEach { appendLine("- $it") }
    }
    if (priorContext.isNotBlank()) {
      appendLine()
      appendLine("## Context from Previous Tasks:")
      appendLine(priorContext)
    }
    appendLine()
    appendLine("## Brainstorming Guidelines:")
    appendLine("1. **Diversity**: Ensure options span different approaches and perspectives")
    appendLine("2. **Clarity**: Each option should be clearly described and actionable")
    appendLine("3. **Relevance**: All options must address the core problem")
    if (includeCreative) {
      appendLine("4. **Creativity**: Include unconventional and innovative approaches")
    } else {
      appendLine("4. **Practicality**: Focus on realistic, proven approaches")
    }
    appendLine("5. **Categorization**: Assign each option to a relevant category")
    appendLine()
    appendLine("## Output Format:")
    appendLine("Generate a JSON object with an \"options\" array. Each option should have:")
    appendLine("- title: A concise, descriptive name (5-10 words)")
    appendLine("- description: A clear explanation of the option (2-4 sentences)")
    appendLine("- category: The domain or approach category")
    appendLine()
    appendLine("Generate $targetCount diverse options now.")
  }

  private fun buildAnalysisPrompt(
    option: BrainstormedOption,
    problemStatement: String,
    constraints: List<String>,
    analysisDepth: String
  ): String = buildString {
    val depthGuidance = when (analysisDepth.lowercase()) {
      "brief" -> "Provide concise analysis with 2-3 items per category."
      "detailed" -> "Provide comprehensive analysis with 5-7 items per category and detailed explanations."
      else -> "Provide moderate analysis with 3-5 items per category."
    }
    appendLine("You are an analytical expert evaluating solution options. Analyze this option independently and objectively.")
    appendLine()
    appendLine("## Problem Statement:")
    appendLine(problemStatement)
    appendLine()
    appendLine("## Option to Analyze:")
    appendLine("**Title:** ${option.title}")
    appendLine("**Description:** ${option.description}")
    if (option.category != null) {
      appendLine("**Category:** ${option.category}")
    }
    if (constraints.isNotEmpty()) {
      appendLine()
      appendLine("## Constraints:")
      constraints.forEach { appendLine("- $it") }
    }
    appendLine()
    appendLine("## Analysis Instructions:")
    appendLine(depthGuidance)
    appendLine()
    appendLine("Evaluate the following aspects:")
    appendLine()
    appendLine("1. **Pros**: Advantages and benefits of this option")
    appendLine("2. **Cons**: Disadvantages and limitations")
    appendLine("3. **Feasibility**: How realistic is implementation? (Consider technical, resource, and organizational factors)")
    appendLine("4. **Impact**: What outcomes and effects can be expected?")
    appendLine("5. **Risks**: What could go wrong? What are the potential negative consequences?")
    appendLine("6. **Requirements**: What resources, skills, or conditions are needed?")
    appendLine()
    appendLine("## Output Format:")
    appendLine("Provide a JSON object with these fields:")
    appendLine("- pros: Array of strings (advantages)")
    appendLine("- cons: Array of strings (disadvantages)")
    appendLine("- feasibility: String (assessment of how realistic this is)")
    appendLine("- impact: String (expected outcomes and effects)")
    appendLine("- risks: Array of strings (potential problems)")
    appendLine("- requirements: Array of strings (what's needed to implement)")
    appendLine()
    appendLine("Analyze this option now.")
  }

  private fun buildSummaryPrompt(
    problemStatement: String,
    options: List<BrainstormedOption>,
    analyses: Map<Int, OptionAnalysis>
  ): String = buildString {
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
    appendLine("You are a strategic advisor synthesizing brainstorming results. Review all options and their analyses to provide actionable recommendations.")
    appendLine()
    appendLine("## Problem Statement:")
    appendLine(problemStatement)
    appendLine()
    appendLine("## Options and Analyses:")
    appendLine(optionsWithAnalysis)
    appendLine()
    appendLine("## Your Task:")
    appendLine("Evaluate all options and select the single best recommendation.")
    appendLine()
    appendLine("## Output Format:")
    appendLine("Provide a JSON object with the following fields:")
    appendLine("- `top_option_index`: The integer index (1-based) of the single best option.")
    appendLine("- `selection_reasoning`: Why was this option chosen as the winner? (Compare against runners-up).")
    appendLine("- `overview`: A brief executive summary of the session findings and general trends.")
    appendLine("- `next_steps`: Concrete actions to take to implement the top recommendation.")
    appendLine()
    appendLine("Select the best option and summarize the findings now.")
  }

  private fun buildDetailedResults(
    problemStatement: String,
    options: List<BrainstormedOption>,
    analyses: Map<Int, OptionAnalysis>,
    summary: String,
    totalTime: Long
  ): String = buildString {
    appendLine("# Brainstorming Session - Detailed Results")
    appendLine()
    appendLine("**Problem Statement:** $problemStatement")
    appendLine()
    appendLine("**Session Duration:** ${totalTime / 1000}s")
    appendLine()
    appendLine("**Options Generated:** ${options.size}")
    appendLine()
    appendLine(
      "**Completed:** ${
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
      }"
    )
    appendLine()
    appendLine("---")
    appendLine()
    appendLine("## All Options")
    appendLine()
    options.forEachIndexed { index, option ->
      val optionNumber = index + 1
      appendLine("### ${optionNumber}. ${option.title}")
      if (option.category != null) {
        appendLine("**Category:** ${option.category}")
      }
      appendLine()
      appendLine(option.description)
      appendLine()
      val analysis = analyses[optionNumber]
      if (analysis != null) {
        appendLine("#### Analysis")
        appendLine()
        appendLine("**Pros:**")
        analysis.pros.forEach { appendLine("- $it") }
        appendLine()
        appendLine("**Cons:**")
        analysis.cons.forEach { appendLine("- $it") }
        appendLine()
        appendLine("**Feasibility:** ${analysis.feasibility}")
        appendLine()
        appendLine("**Impact:** ${analysis.impact}")
        appendLine()
        appendLine("**Risks:**")
        analysis.risks.forEach { appendLine("- $it") }
        appendLine()
        appendLine("**Requirements:**")
        analysis.requirements.forEach { appendLine("- $it") }
        appendLine()
      }
      appendLine("---")
      appendLine()
    }
    appendLine("## Summary & Recommendations")
    appendLine()
    appendLine(summary)
    appendLine()
  }

  private fun getInputFileCode() = (executionConfig?.related_files ?: listOf())
    .flatMap { pattern: String ->
      val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
      (FileSelectionUtils.filteredWalk(root.toFile()) {
        when {
          FileSelectionUtils.isIgnored(it.toPath()) -> false
          matcher.matches(root.relativize(it.toPath())) -> true
          it.isDirectory -> true
          else -> false
        }
      })
    }.filter { file ->
      file.isFile && file.exists()
    }
    .distinct()
    .filterNotNull()
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

  private fun isTextFile(file: File): Boolean {
    val textExtensions = setOf(
      "txt", "md", "kt", "java", "js", "ts", "py", "rb", "go", "rs", "c", "cpp",
      "h", "hpp", "css", "html", "xml", "json", "yaml", "yml", "properties", "gradle", "maven"
    )
    return textExtensions.contains(file.extension.lowercase())
  }

  private fun extractDocumentContent(file: File) = try {
    file.getDocumentReader().use { reader ->
      when (reader) {
        is PaginatedDocumentReader -> reader.getText(0, reader.getPageCount())
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


  companion object {
      private val log: Logger = getLogger(BrainstormingTask::class.java)

    @JvmStatic
    val Brainstorming = TaskType(
      name = "Brainstorming",
      category = "Reasoning",
      taskClass = BrainstormingTask::class.java,
      executionConfigClass = BrainstormingTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generate and analyze multiple solution options",
      tooltipHtml = buildString {
        appendLine("Systematically generates diverse options and analyzes each independently.")
        appendLine("<ul>")
        appendLine("  <li>Generates multiple solution options for a given problem</li>")
        appendLine("  <li>Analyzes each option independently (pros, cons, feasibility, impact, risks)</li>")
        appendLine("  <li>Provides comparative summary with recommendations</li>")
        appendLine("  <li>Supports creative and conventional approaches</li>")
        appendLine("  <li>Configurable analysis depth and option count</li>")
        appendLine("  <li>Identifies hybrid approaches and synergies</li>")
        appendLine("  <li>Useful for decision making, strategic planning, and problem solving</li>")
        appendLine("</ul>")
      },
    )
  }
}

fun buildStringStream(builderAction: (OutputStream) -> Unit): String {
  val outputStream = ByteArrayOutputStream()
  builderAction(outputStream)
  return outputStream.toString(StandardCharsets.UTF_8.name())
}
