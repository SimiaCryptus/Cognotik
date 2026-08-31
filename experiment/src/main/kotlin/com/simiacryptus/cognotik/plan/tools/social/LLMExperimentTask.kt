package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.safeComplete
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.apache.commons.text.similarity.LevenshteinDistance
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    task: ISessionTask,
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
          task.pool.submit {
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
    parsingModel = this,
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


  private fun createTranscriptFile(task: ISessionTask): Pair<String, FileOutputStream?> {
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

    @JvmStatic
    val LLMExperiment = TaskType(
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