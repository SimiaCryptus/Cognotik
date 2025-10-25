package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.actors.ChatAgent
import com.simiacryptus.cognotik.actors.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

class GeneticOptimizationTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: GeneticOptimizationTaskExecutionConfigData?
) : AbstractTask<GeneticOptimizationTask.GeneticOptimizationTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {
  companion object {
    private val log: Logger = LoggerFactory.getLogger(GeneticOptimizationTask::class.java)
    val GeneticOptimization = TaskType(
      "GeneticOptimization",
      GeneticOptimizationTaskExecutionConfigData::class.java,
      TaskTypeConfig::class.java,
      "Iteratively evolve and perfect text through genetic algorithms",
      """
              Uses genetic algorithms to optimize text through iterative evolution.
              <ul>
                <li>Generates variations using configurable mutation strategies</li>
                <li>Evaluates variants against optimization criteria</li>
                <li>Selects top performers for next generation</li>
                <li>Applies crossover to combine successful traits</li>
                <li>Tracks fitness progression across generations</li>
                <li>Provides detailed analysis of evolution</li>
                <li>Supports custom evaluation criteria and weights</li>
                <li>Useful for perfecting prompts, copy, documentation, and messaging</li>
              </ul>
            """
    )
  }

  class GeneticOptimizationTaskExecutionConfigData(
    @Description("The initial text to optimize (seed for genetic algorithm)")
    val initial_text: String? = null,
    @Description("Optional input files (or file patterns, e.g. **/*.kt) to be used as context for optimization")
    val input_files: List<String>? = null,
    @Description("The optimization goal or criteria (e.g., 'clarity and conciseness', 'persuasiveness', 'technical accuracy')")
    val optimization_goal: String? = null,
    @Description("Number of generations to evolve (default: 5)")
    val num_generations: Int = 5,
    @Description("Population size per generation (default: 6)")
    val population_size: Int = 6,
    @Description("Number of top candidates to keep each generation (default: 2)")
    val selection_size: Int = 2,
    @Description("Mutation strategies to use (e.g., 'rephrase', 'simplify', 'elaborate', 'restructure')")
    val mutation_strategies: List<String>? = listOf("rephrase", "simplify", "elaborate"),
    @Description("Whether to enable crossover (combining traits from multiple candidates)")
    val enable_crossover: Boolean = true,
    @Description("Evaluation criteria weights (e.g., {'clarity': 0.4, 'conciseness': 0.3, 'impact': 0.3})")
    val evaluation_weights: Map<String, Double>? = null,
    @Description("Additional context or constraints for optimization")
    val constraints: List<String>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = GeneticOptimization.name,
    task_description = task_description,
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (initial_text.isNullOrBlank()) {
        return "initial_text must not be blank"
      }
      // input_files is optional, so no validation needed

      if (optimization_goal.isNullOrBlank()) {
        return "optimization_goal must not be blank"
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
      return ValidatedObject.validateFields(this)
    }
  }

  data class TextVariant(
    @Description("The text variant")
    val text: String = "",
    @Description("Brief explanation of what changed from parent")
    val mutation_description: String = "",
    @Description("The mutation strategy used")
    val strategy: String = ""
  )

  data class EvaluationScore(
    @Description("Overall fitness score (0-100)")
    val overall_score: Double = 0.0,
    @Description("Breakdown of scores by criteria")
    val criteria_scores: Map<String, Double> = emptyMap(),
    @Description("Strengths of this variant")
    val strengths: List<String> = emptyList(),
    @Description("Weaknesses or areas for improvement")
    val weaknesses: List<String> = emptyList(),
    @Description("Brief justification for the score")
    val justification: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (overall_score < 0.0 || overall_score > 100.0) {
        return "overall_score must be between 0 and 100"
      }
      criteria_scores.forEach { (criterion, score) ->
        if (score < 0.0 || score > 100.0) {
          return "criteria_scores[$criterion] must be between 0 and 100"
        }
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class EvaluatedVariant(
    val text: String = "",
    val score: EvaluationScore = EvaluationScore(),
    val generation: Int = 0,
    val parentIndex: Int? = null,
    val strategy: String = ""
  )

  override fun promptSegment(): String {
    return """
GeneticOptimization - Iteratively evolve and perfect text through genetic algorithms
  ** Specify the initial text to optimize
  ** Define the optimization goal (e.g., clarity, persuasiveness, technical accuracy)
  ** Configure number of generations (default: 5)
  ** Set population size and selection size
  ** Choose mutation strategies (rephrase, simplify, elaborate, restructure)
  ** Enable/disable crossover for combining traits
  ** Define evaluation criteria and weights
  ** The task will:
     - Generate variations using mutation strategies
     - Evaluate each variant against optimization criteria
     - Select top performers for next generation
     - Apply crossover to combine successful traits
     - Track evolution across generations
     - Provide detailed fitness analysis
  ** Useful for:
     - Perfecting prompts and instructions
     - Refining marketing copy
     - Optimizing technical documentation
     - Improving clarity and impact of messaging
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {
    val transcript = transcript(task)
    try {
      val startTime = System.currentTimeMillis()
      messages.joinToString("\n\n")
      log.info("Starting GeneticOptimizationTask with initial_text length=${executionConfig?.initial_text?.length}, goal='${executionConfig?.optimization_goal}'")
      // Validate configuration
      executionConfig?.validate()?.let { errorMessage ->
        log.error("Configuration validation failed: $errorMessage")
        task.complete("VALIDATION ERROR: $errorMessage")
        task.error(ValidatedObject.ValidationError(errorMessage, executionConfig))
        transcript?.close()
        resultFn("VALIDATION ERROR: $errorMessage")
        return
      }


      val initialText = executionConfig?.initial_text
      val optimizationGoal = executionConfig?.optimization_goal
      val numGenerations = executionConfig?.num_generations ?: 5
      val populationSize = executionConfig?.population_size ?: 6
      val selectionSize = min(executionConfig?.selection_size ?: 2, populationSize / 2)
      val mutationStrategies = executionConfig?.mutation_strategies ?: listOf("rephrase", "simplify", "elaborate")
      val enableCrossover = executionConfig?.enable_crossover ?: true
      val evaluationWeights = executionConfig?.evaluation_weights ?: mapOf(
        "clarity" to 0.35,
        "conciseness" to 0.25,
        "impact" to 0.25,
        "goal_alignment" to 0.15
      )
      val constraints = executionConfig?.constraints ?: emptyList()
      val inputFileContent = getInputFileContent()

      if (initialText.isNullOrBlank() || optimizationGoal.isNullOrBlank()) {
        log.error("Configuration error: initial_text or optimization_goal is blank")
        task.complete("CONFIGURATION ERROR: Both initial_text and optimization_goal must be specified")
        transcript?.close()
        task.error(RuntimeException("Configuration error: initial_text or optimization_goal is blank"))
        resultFn("CONFIGURATION ERROR: Both initial_text and optimization_goal must be specified")
        return
      }

      log.info("Configuration validated: generations=$numGenerations, population=$populationSize, selection=$selectionSize, crossover=$enableCrossover")

      val tabs = TabbedDisplay(task)
      val api = orchestrationConfig.defaultChatter
      transcript?.write("# Genetic Optimization Task Transcript\n\n".toByteArray())

      // Create overview tab
      val overviewTask = task.ui.newTask(false)
      tabs["Overview"] = overviewTask.placeholder
      val overviewContent = buildString {
        appendLine("# Genetic Optimization Task")
        appendLine()
        appendLine(
          "**Started:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }"
        )
        if (inputFileContent.isNotBlank()) {
          appendLine()
          appendLine("## Input Context")
          appendLine()
          appendLine(inputFileContent)
        }
        appendLine()
        appendLine("## Configuration")
        appendLine()
        appendLine("| Parameter | Value |")
        appendLine("|-----------|-------|")
        appendLine("| Optimization Goal | $optimizationGoal |")
        appendLine("| Generations | $numGenerations |")
        appendLine("| Population Size | $populationSize |")
        appendLine("| Selection Size | $selectionSize |")
        appendLine("| Mutation Strategies | ${mutationStrategies.joinToString(", ")} |")
        appendLine("| Crossover | ${if (enableCrossover) "✓ Enabled" else "✗ Disabled"} |")
        appendLine()
        appendLine("## Evaluation Criteria")
        appendLine()
        evaluationWeights.forEach { (criterion, weight) ->
          appendLine("- **$criterion**: ${String.format("%.0f%%", weight * 100)}")
        }
        if (constraints.isNotEmpty()) {
          appendLine()
          appendLine("## Constraints")
          appendLine()
          constraints.forEach { appendLine("- $it") }
        }
        appendLine()
        appendLine("## Initial Text")
        appendLine()
        appendLine("```")
        appendLine(initialText)
        appendLine("```")
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## Progress")
        appendLine()
        appendLine("- ⏳ Initializing population...")
      }
      overviewTask.add(overviewContent.renderMarkdown)
      task.update()
      transcript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))

      // Gather context
      log.debug("Gathering prior context")
      val priorContext = getPriorCode(agent.executionState)
      log.debug("Context gathered: length=${priorContext.length}")

      // Initialize population with the seed text
      var currentPopulation = listOf(
        EvaluatedVariant(
          text = initialText,
          score = EvaluationScore(overall_score = 0.0),
          generation = 0,
          strategy = "seed"
        )
      )

      // Evaluate initial text
      log.info("Evaluating initial text")
      val initialEvaluation =
        evaluateVariant(initialText, optimizationGoal, evaluationWeights, constraints, api, inputFileContent)
      currentPopulation = listOf(
        currentPopulation[0].copy(score = initialEvaluation)
      )

      log.info("Initial evaluation: score=${initialEvaluation.overall_score}")
      transcript?.write("\n\n## Initial Evaluation\n\n".toByteArray(StandardCharsets.UTF_8))
      transcript?.write("**Score:** ${String.format("%.1f", initialEvaluation.overall_score)}/100\n\n".toByteArray(StandardCharsets.UTF_8))
      transcript?.write("**Strengths:**\n".toByteArray(StandardCharsets.UTF_8))
      initialEvaluation.strengths.forEach { transcript?.write("- $it\n".toByteArray(StandardCharsets.UTF_8)) }
      transcript?.write("\n**Weaknesses:**\n".toByteArray(StandardCharsets.UTF_8))
      initialEvaluation.weaknesses.forEach { transcript?.write("- $it\n".toByteArray(StandardCharsets.UTF_8)) }
      transcript?.write("\n".toByteArray(StandardCharsets.UTF_8))


      // Update overview with initial score
      overviewTask.add(buildString {
        appendLine()
        appendLine("- ✓ Initial evaluation: **${String.format("%.1f", initialEvaluation.overall_score)}/100**")
        appendLine("- ⏳ Starting evolution...")
      }.renderMarkdown)
      task.update()

      // Track best variant across all generations
      var bestVariant = currentPopulation[0]
      val evolutionHistory = mutableListOf<List<EvaluatedVariant>>()
      evolutionHistory.add(currentPopulation)

      // Evolution loop
      for (generation in 1..numGenerations) {
        log.info("Starting generation $generation/$numGenerations")

        val generationTask = task.ui.newTask(false)
        tabs["Generation $generation"] = generationTask.placeholder
        transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
        transcript?.write("# Generation $generation\n\n".toByteArray(StandardCharsets.UTF_8))
        generationTask.add(buildString {
          appendLine("# Generation $generation")
          appendLine()
          appendLine("**Status:** In Progress")
          appendLine()
          appendLine("Generating $populationSize variants...")
        }.renderMarkdown)
        task.update()

        // Step 1: Generate new variants
        val newVariants = mutableListOf<EvaluatedVariant>()

        // Keep top performers from previous generation
        val survivors = currentPopulation.sortedByDescending { it.score.overall_score }.take(selectionSize)
        log.debug("Selected $selectionSize survivors for generation $generation")

        // Generate mutations from survivors
        val mutationsNeeded = populationSize - survivors.size
        val mutationsPerSurvivor = max(1, mutationsNeeded / survivors.size)

        survivors.forEachIndexed { survivorIndex, survivor ->
          val mutationsToGenerate = if (survivorIndex == survivors.size - 1) {
            // Last survivor gets any remaining slots
            mutationsNeeded - (mutationsPerSurvivor * (survivors.size - 1))
          } else {
            mutationsPerSurvivor
          }

          repeat(mutationsToGenerate) {
            val strategy = mutationStrategies.random()
            log.debug("Generating mutation using strategy: $strategy")
            val mutated =
              generateMutation(survivor.text, strategy, optimizationGoal, constraints, priorContext + "\n\n" + inputFileContent, api)
            if (mutated != null) {
              newVariants.add(
                EvaluatedVariant(
                  text = mutated.text,
                  score = EvaluationScore(overall_score = 0.0),
                  generation = generation,
                  parentIndex = survivorIndex,
                  strategy = strategy
                )
              )
            }
          }
        }

        // Apply crossover if enabled
        if (enableCrossover && survivors.size >= 2 && newVariants.size < populationSize) {
          log.debug("Applying crossover")
          val crossoverVariant = applyCrossover(
            survivors[0].text,
            survivors[1].text,
            optimizationGoal,
            constraints,
            api
          )
          if (crossoverVariant != null) {
            newVariants.add(
              EvaluatedVariant(
                text = crossoverVariant,
                score = EvaluationScore(overall_score = 0.0),
                generation = generation,
                strategy = "crossover"
              )
            )
          }
        }

        // Combine survivors and new variants
        currentPopulation = survivors + newVariants

        // Step 2: Evaluate all variants
        log.info("Evaluating ${currentPopulation.size} variants in generation $generation")
        currentPopulation = currentPopulation.map { variant ->
          if (variant.score.overall_score == 0.0) {
            val evaluation = evaluateVariant(
              variant.text,
              optimizationGoal,
              evaluationWeights,
              constraints,
              api,
              inputFileContent
            )
            variant.copy(score = evaluation)
          } else {
            variant
          }
        }

        evolutionHistory.add(currentPopulation)

        // Update best variant
        val generationBest = currentPopulation.maxByOrNull { it.score.overall_score }!!
        if (generationBest.score.overall_score > bestVariant.score.overall_score) {
          log.info("New best variant found in generation $generation: score=${generationBest.score.overall_score}")
          bestVariant = generationBest
        }


        // Display generation results
        val generationResults = buildString {
          appendLine()
          appendLine("---")
          appendLine()
          appendLine("## Generation $generation Results")
          appendLine()
          appendLine("**Status:** ✓ Complete")
          appendLine()
          appendLine("### Population Statistics")
          appendLine()
          val scores = currentPopulation.map { it.score.overall_score }
          appendLine("- **Best Score:** ${String.format("%.1f", scores.maxOrNull() ?: 0.0)}/100")
          appendLine("- **Average Score:** ${String.format("%.1f", scores.average())}/100")
          appendLine("- **Worst Score:** ${String.format("%.1f", scores.minOrNull() ?: 0.0)}/100")
          appendLine(
            "- **Improvement:** ${
              String.format(
                "%.1f",
                (generationBest.score.overall_score - survivors[0].score.overall_score)
              )
            }"
          )
          appendLine()
          appendLine("### Top Variants")
          appendLine()
          currentPopulation.sortedByDescending { it.score.overall_score }.take(3)
            .forEachIndexed { index, variant ->
              appendLine(
                "#### ${index + 1}. Score: ${
                  String.format(
                    "%.1f",
                    variant.score.overall_score
                  )
                }/100 (${variant.strategy})"
              )
              appendLine()
              appendLine("```")
              appendLine(variant.text)
              appendLine("```")
              appendLine()
              appendLine("**Strengths:**")
              variant.score.strengths.forEach { appendLine("- $it") }
              appendLine()
              if (variant.score.weaknesses.isNotEmpty()) {
                appendLine("**Weaknesses:**")
                variant.score.weaknesses.forEach { appendLine("- $it") }
                appendLine()
              }
              appendLine("**Criteria Breakdown:**")
              variant.score.criteria_scores.forEach { (criterion, score) ->
                appendLine("- $criterion: ${String.format("%.1f", score)}/100")
              }
              appendLine()
              appendLine("---")
              appendLine()
            }
        }
        generationTask.add(generationResults.renderMarkdown)
        task.update()
        transcript?.write(generationResults.toByteArray(StandardCharsets.UTF_8))

        // Update overview
        overviewTask.add(buildString {
          appendLine()
          appendLine(
            "- ✓ Generation $generation: Best=${
              String.format(
                "%.1f",
                generationBest.score.overall_score
              )
            }, Avg=${String.format("%.1f", currentPopulation.map { it.score.overall_score }.average())}"
          )
        }.renderMarkdown)
        task.update()
      }

      // Create evolution visualization tab
      log.info("Creating evolution visualization")
      val evolutionTask = task.ui.newTask(false)
      tabs["Evolution Analysis"] = evolutionTask.placeholder
      val evolutionAnalysis = buildString {
        appendLine("# Evolution Analysis")
        appendLine()
        appendLine("## Fitness Progression")
        appendLine()
        appendLine("| Generation | Best Score | Average Score | Improvement |")
        appendLine("|------------|------------|---------------|-------------|")
        evolutionHistory.forEachIndexed { index, population ->
          val scores = population.map { it.score.overall_score }
          val improvement = if (index > 0) {
            scores.maxOrNull()!! - evolutionHistory[index - 1].maxOf { it.score.overall_score }
          } else {
            0.0
          }
          appendLine(
            "| $index | ${String.format("%.1f", scores.maxOrNull() ?: 0.0)} | ${
              String.format(
                "%.1f",
                scores.average()
              )
            } | ${String.format("%+.1f", improvement)} |"
          )
        }
        appendLine()
        appendLine("## Strategy Effectiveness")
        appendLine()
        val strategyStats = mutableMapOf<String, MutableList<Double>>()
        evolutionHistory.flatten().forEach { variant ->
          if (variant.strategy.isNotEmpty()) {
            strategyStats.getOrPut(variant.strategy) { mutableListOf() }.add(variant.score.overall_score)
          }
        }
        appendLine("| Strategy | Avg Score | Count | Success Rate |")
        appendLine("|----------|-----------|-------|--------------|")
        strategyStats.forEach { (strategy, scores) ->
          val avgScore = scores.average()
          val successRate =
            scores.count { it > initialEvaluation.overall_score }.toDouble() / scores.size * 100
          appendLine(
            "| $strategy | ${
              String.format(
                "%.1f",
                avgScore
              )
            } | ${scores.size} | ${String.format("%.0f%%", successRate)} |"
          )
        }
        appendLine()
        appendLine("## Best Variant Evolution")
        appendLine()
        appendLine("### Initial Text (Score: ${String.format("%.1f", initialEvaluation.overall_score)})")
        appendLine("```")
        appendLine(initialText)
        appendLine("```")
        appendLine()
        appendLine(
          "### Final Optimized Text (Score: ${
            String.format(
              "%.1f",
              bestVariant.score.overall_score
            )
          })"
        )
        appendLine("```")
        appendLine(bestVariant.text)
        appendLine("```")
        appendLine()
        appendLine("### Improvement Summary")
        appendLine()
        appendLine(
          "- **Score Improvement:** ${
            String.format(
              "%+.1f",
              bestVariant.score.overall_score - initialEvaluation.overall_score
            )
          } points"
        )
        appendLine("- **Generation Found:** ${bestVariant.generation}")
        appendLine("- **Strategy Used:** ${bestVariant.strategy}")
        appendLine()
        appendLine("### Detailed Analysis")
        appendLine()
        appendLine("**Strengths:**")
        bestVariant.score.strengths.forEach { appendLine("- $it") }
        appendLine()
        if (bestVariant.score.weaknesses.isNotEmpty()) {
          appendLine("**Remaining Areas for Improvement:**")
          bestVariant.score.weaknesses.forEach { appendLine("- $it") }
          appendLine()
        }
        appendLine("**Criteria Scores:**")
        bestVariant.score.criteria_scores.forEach { (criterion, score) ->
          val initialScore = initialEvaluation.criteria_scores[criterion] ?: 0.0
          val improvement = score - initialScore
          appendLine(
            "- $criterion: ${String.format("%.1f", score)}/100 (${
              String.format(
                "%+.1f",
                improvement
              )
            })"
          )
        }
        appendLine()
        appendLine("**Justification:**")
        appendLine(bestVariant.score.justification)
      }
      evolutionTask.add(evolutionAnalysis.renderMarkdown)
      task.update()
      transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
      transcript?.write(evolutionAnalysis.toByteArray(StandardCharsets.UTF_8))

      // Build final result
      val totalTime = System.currentTimeMillis() - startTime
      buildString {
        appendLine("# Genetic Optimization Results")
        appendLine()
        appendLine("**Optimization Goal:** $optimizationGoal")
        appendLine()
        appendLine("## Final Optimized Text")
        appendLine()
        appendLine("```")
        appendLine(bestVariant.text)
        appendLine("```")
        appendLine()
        appendLine("## Performance Metrics")
        appendLine()
        appendLine("- **Initial Score:** ${String.format("%.1f", initialEvaluation.overall_score)}/100")
        appendLine("- **Final Score:** ${String.format("%.1f", bestVariant.score.overall_score)}/100")
        appendLine(
          "- **Improvement:** ${
            String.format(
              "%+.1f",
              bestVariant.score.overall_score - initialEvaluation.overall_score
            )
          } points"
        )
        appendLine("- **Generations:** $numGenerations")
        appendLine("- **Total Variants Evaluated:** ${evolutionHistory.flatten().size}")
        appendLine("- **Best Found in Generation:** ${bestVariant.generation}")
        appendLine()
        appendLine("## Key Improvements")
        appendLine()
        bestVariant.score.strengths.forEach { appendLine("- $it") }
        appendLine()
        appendLine("*See the Evolution Analysis tab for detailed progression and strategy effectiveness*")
      }

      // Final overview update
      val finalOverview = buildString {
        appendLine()
        appendLine("---")
        appendLine()
        appendLine("## ✅ Optimization Complete")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|--------|-------|")
        appendLine("| Initial Score | ${String.format("%.1f", initialEvaluation.overall_score)}/100 |")
        appendLine("| Final Score | ${String.format("%.1f", bestVariant.score.overall_score)}/100 |")
        appendLine(
          "| Improvement | ${
            String.format(
              "%+.1f",
              bestVariant.score.overall_score - initialEvaluation.overall_score
            )
          } |"
        )
        appendLine("| Generations | $numGenerations |")
        appendLine("| Total Variants | ${evolutionHistory.flatten().size} |")
        appendLine("| Total Time | ${totalTime / 1000}s |")
        appendLine()
        appendLine("**Status:** ✓ Complete")
      }
      overviewTask.add(finalOverview.renderMarkdown)
      task.update()
      transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
      transcript?.write(finalOverview.toByteArray(StandardCharsets.UTF_8))
      transcript?.close()

      log.info("GeneticOptimizationTask completed successfully: total_time=${totalTime}ms, improvement=${bestVariant.score.overall_score - initialEvaluation.overall_score}, generations=$numGenerations")
      task.complete(
        "Optimization complete: improved by ${
          String.format(
            "%.1f",
            bestVariant.score.overall_score - initialEvaluation.overall_score
          )
        } points in ${totalTime / 1000}s"
      )
      val (link, _) = Pair(task.linkTo("optimization_results.md"), task.resolve("optimization_results.md"))
      val summaryMessage = buildString {
        appendLine("Optimization complete: improved by ${String.format("%.1f", bestVariant.score.overall_score - initialEvaluation.overall_score)} points")
        appendLine()
        appendLine("**Final Score:** ${String.format("%.1f", bestVariant.score.overall_score)}/100")
        appendLine("**Generations:** $numGenerations")
        appendLine("**Total Time:** ${totalTime / 1000}s")
        appendLine()
        appendLine(
          "Detailed results: <a href='$link' target='_blank'>$link</a> " +
              "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
              "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
        )
      }
      resultFn(summaryMessage)

    } catch (e: Exception) {
      log.error("Error during GeneticOptimizationTask execution", e)
      transcript?.close()
      task.error(e)
      task.complete("Failed with error: ${e.message}")
      resultFn("ERROR: ${e.message}")
    }
  }

  private fun transcript(task: SessionTask): FileOutputStream? {
    val (link, file) = Pair(task.linkTo("transcript.md"), task.resolve("transcript.md"))
    val markdownTranscript = file?.outputStream()
    task.complete(
      "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
        link.removeSuffix(
          ".md"
        )
      }.pdf' target='_blank'>pdf</a>"
    )
    return markdownTranscript
  }

  private fun getInputFileContent(): String {
    return (executionConfig?.input_files ?: listOf())
      .flatMap { pattern: String ->
        val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
        (com.simiacryptus.cognotik.util.FileSelectionUtils.filteredWalk(root.toFile()) {
          when {
            com.simiacryptus.cognotik.util.FileSelectionUtils.isLLMIgnored(it.toPath()) -> false
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
        "# $relativePath\n\n${file.readText()}"
      }
  }


  private fun generateMutation(
    text: String,
    strategy: String,
    goal: String,
    constraints: List<String>,
    context: String,
    api: ChatInterface
  ): TextVariant? {
    try {
      val constraintsText = if (constraints.isNotEmpty()) {
        "\n\nConstraints to maintain:\n${constraints.joinToString("\n") { "- $it" }}"
      } else ""

      val contextText = if (context.isNotBlank()) {
        "\n\nAdditional context:\n${context.take(5000)}"
      } else ""

      val prompt = """
You are a text optimization expert applying genetic algorithm mutations.

## Current Text
```
$text
```

## Optimization Goal
$goal

## Mutation Strategy
$strategy
$constraintsText
$contextText

## Instructions
Apply the "$strategy" mutation strategy to create a variant of the text that better achieves the optimization goal.

Mutation strategies:
- **rephrase**: Reword while maintaining meaning, focus on different phrasing
- **simplify**: Make more concise and easier to understand
- **elaborate**: Add detail, examples, or explanation
- **restructure**: Reorganize the structure or flow
- **emphasize**: Strengthen key points or calls to action
- **soften**: Make tone more gentle or diplomatic

Generate ONE variant that applies this strategy effectively.
      """.trimIndent()

      val mutationParser = ParsedAgent(
        resultClass = TextVariant::class.java,
        prompt = prompt,
        model = api,
        temperature = 0.8,
        name = "MutationGenerator",
        parsingChatter = orchestrationConfig.parsingChatter,
      )

      val result = mutationParser.answer(listOf(prompt)).obj
      log.debug("Generated mutation using $strategy: ${result.text.take(50)}...")
      return result

    } catch (e: Exception) {
      log.warn("Failed to generate mutation with strategy $strategy", e)
      return null
    }
  }

  private fun applyCrossover(
    text1: String,
    text2: String,
    goal: String,
    constraints: List<String>,
    api: ChatInterface
  ): String? {
    try {
      val constraintsText = if (constraints.isNotEmpty()) {
        "\n\nConstraints to maintain:\n${constraints.joinToString("\n") { "- $it" }}"
      } else ""

      val prompt = """
You are a text optimization expert applying genetic algorithm crossover.

## Parent Text 1
```
$text1
```

## Parent Text 2
```
$text2
```

## Optimization Goal
$goal
$constraintsText

## Instructions
Create a new variant by combining the best elements from both parent texts.
- Identify the strongest aspects of each parent
- Merge them into a cohesive new variant
- Ensure the result is better than either parent alone
- Maintain consistency and flow

Generate the crossover variant now.
      """.trimIndent()

      val crossoverAgent = ChatAgent(
        prompt = "You are a text optimization expert.",
        model = api,
        temperature = 0.7
      )

      val result = crossoverAgent.answer(listOf(prompt))
      log.debug("Generated crossover variant: ${result.take(50)}...")
      return result

    } catch (e: Exception) {
      log.warn("Failed to apply crossover", e)
      return null
    }
  }

  private fun evaluateVariant(
    text: String,
    goal: String,
    weights: Map<String, Double>,
    constraints: List<String>,
    api: ChatInterface,
    inputFileContent: String = ""
  ): EvaluationScore {
    try {
      val constraintsText = if (constraints.isNotEmpty()) {
        "\n\nConstraints:\n${constraints.joinToString("\n") { "- $it" }}"
      } else ""

      val weightsText = weights.entries.joinToString("\n") { (criterion, weight) ->
        "- $criterion (${String.format("%.0f%%", weight * 100)} weight)"
      }
      val contextText = if (inputFileContent.isNotBlank()) {
        "\n\nAdditional context from input files:\n${inputFileContent.take(5000)}"
      } else {
        ""
      }


      val prompt = """
 You are an expert evaluator for text optimization using genetic algorithms.
$contextText

## Text to Evaluate
```
$text
```

## Optimization Goal
$goal

## Evaluation Criteria
$weightsText
$constraintsText

## Instructions
Evaluate this text variant against the optimization goal and criteria.

For each criterion, provide a score from 0-100:
- **clarity**: How clear and understandable is the text?
- **conciseness**: How efficiently does it communicate?
- **impact**: How effective is it at achieving the goal?
- **goal_alignment**: How well does it align with the stated optimization goal?

Also provide:
1. An overall weighted score (0-100)
2. List of strengths (what works well)
3. List of weaknesses (what could be improved)
4. Brief justification for the scores

Be objective and consistent in your evaluation.
      """.trimIndent()

      val evaluationParser = ParsedAgent(
        resultClass = EvaluationScore::class.java,
        prompt = prompt,
        model = api,
        temperature = 0.3,
        name = "VariantEvaluator",
        parsingChatter = orchestrationConfig.parsingChatter,
      )

      val result = evaluationParser.answer(listOf(prompt)).obj
      log.debug("Evaluated variant: overall_score=${result.overall_score}")
      return result

    } catch (e: Exception) {
      log.warn("Failed to evaluate variant", e)
      return EvaluationScore(
        overall_score = 0.0,
        criteria_scores = weights.keys.associateWith { 0.0 },
        strengths = emptyList(),
        weaknesses = listOf("Evaluation failed: ${e.message}"),
        justification = "Error during evaluation"
      )
    }
  }
}