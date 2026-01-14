package com.simiacryptus.cognotik.plan.tools.reasoning

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
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.GZIPOutputStream
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
            name = "GeneticOptimization",
            category = "Reasoning",
            taskClass = GeneticOptimizationTask::class.java,
            executionConfigClass = GeneticOptimizationTaskExecutionConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Iteratively evolve and perfect text through genetic algorithms",
            tooltipHtml = """
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
                      """,
        )
        private const val TT = """```"""
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

    class GeneticOptimizationTaskExecutionConfigData(
        @Description("The initial text(s) to optimize (seeds for genetic algorithm) - Include the ENTIRE text(s) to be optimized. Multiple texts will be used as separate seeds in the initial population.")
        var initial_text: List<String>? = null,
        @Description("The optimization goal or criteria (e.g., 'clarity and conciseness', 'persuasiveness', 'technical accuracy')")
        var optimization_goal: String? = null,
        @Description("Evaluation criteria weights (e.g., {'clarity': 0.4, 'conciseness': 0.3, 'impact': 0.3})")
        var evaluation_weights: Map<String, Double>? = null,
        @Description("Additional context or constraints for optimization")
        var constraints: List<String>? = null,

        @Description("Number of generations to evolve (default: 5)")
        var num_generations: Int = 5,
        @Description("Population size per generation (default: 6)")
        var population_size: Int = 6,
        @Description("Number of top candidates to keep each generation (default: 2)")
        var selection_size: Int = 2,
        @Description("Mutation strategies to use (e.g., 'rephrase', 'simplify', 'elaborate', 'restructure')")
        var mutation_strategies: List<String>? = listOf("rephrase", "simplify", "elaborate"),
        @Description("Whether to enable crossover (combining traits from multiple candidates)")
        var enable_crossover: Boolean = true,

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
            if (initial_text.isNullOrEmpty()) {
                return "initial_text must not be empty"
            }
            if (initial_text!!.any { it.isBlank() }) {
                return "initial_text entries must not be blank"
            }
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
            return ValidatedObject.Companion.validateFields(this)
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
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class EvaluatedVariant(
        val text: String = "",
        val score: EvaluationScore = EvaluationScore(),
        val generation: Int = 0,
        val parentIndex: Int? = null,
        val strategy: String = "",
        val diversityScore: Double = 0.0
    )

    override fun promptSegment(): String {
        return """
GeneticOptimization - Iteratively evolve and perfect text through genetic algorithms
  - Specify the FULL text(s) items to optimize
  - Define the optimization goal (e.g., clarity, persuasiveness)
  - Configure number of generations (default: 5)
  - Set population size and selection size
  - Choose mutation strategies (rephrase, simplify, elaborate, restructure)
  - Enable/disable crossover for combining traits
  - Define evaluation criteria and weights
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
            val transcript = task.transcript()
            try {
                val startTime = System.currentTimeMillis()
                log.info("Starting GeneticOptimizationTask. Goal: ${executionConfig?.optimization_goal}")

                // Validate configuration
            executionConfig?.validate()?.let { errorMessage ->
                val error = ValidatedObject.ValidationError(errorMessage, executionConfig)
                handleError(error, task, transcript, resultFn)
                return@submit
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

            if (initialText.isNullOrEmpty() || optimizationGoal.isNullOrBlank()) {
                val error = RuntimeException("Configuration error: initial_text is empty or optimization_goal is blank")
                handleError(error, task, transcript, resultFn)
                return@submit
            }

            log.info("Configuration validated: generations=$numGenerations, population=$populationSize, selection=$selectionSize, crossover=$enableCrossover")

            val tabs = TabbedDisplay(task)
            val api = defaultSmart
            transcript?.write("# Genetic Optimization Task Transcript\n\n".toByteArray())

            // Create overview tab
            val overviewTask = tabs.newTask("Overview")
            val overviewContent = buildString {
                appendLine("# Genetic Optimization Task")
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
                initialText.forEachIndexed { i, text ->
                    appendLine("<details><summary>Seed ${i + 1}</summary>\n\n$TT\n$text\n$TT\n</details>")
                }
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("- ⏳ Initializing population...")
            }
            overviewTask.add(overviewContent.renderMarkdown)
            transcript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))

            // Gather context

            overviewTask.add(buildString {
                appendLine()
            }.renderMarkdown)
            // Initialize population with the seed texts
            log.info("Evaluating ${initialText.size} initial texts")
            var currentPopulation = initialText.mapIndexed { index, text ->
                EvaluatedVariant(
                    text = text,
                    score = EvaluationScore(overall_score = 0.0),
                    generation = 0,
                    strategy = "seed",
                    diversityScore = 1.0
                )
            }

            // Evaluate all initial texts
            currentPopulation = currentPopulation.map { variant ->
                val evaluation = evaluateVariant(variant.text, optimizationGoal, evaluationWeights, constraints, api)
                variant.copy(score = evaluation, diversityScore = 1.0)
            }

            // Log and display initial evaluations
            transcript?.write("\n\n## Initial Evaluations\n\n".toByteArray(StandardCharsets.UTF_8))
            currentPopulation.forEachIndexed { index, variant ->
                log.info("Initial text ${index + 1} evaluated. Score: ${variant.score.overall_score}")

                val evalText = buildString {
                    if (currentPopulation.size > 1) {
                        appendLine("### Seed ${index + 1}")
                        appendLine()
                    }
                    appendLine("**Score:** ${String.format("%.1f", variant.score.overall_score)}/100")
                    appendLine()
                    appendLine("**Strengths:**")
                    variant.score.strengths.forEach { appendLine("- $it") }
                    appendLine()
                    appendLine("**Weaknesses:**")
                    variant.score.weaknesses.forEach { appendLine("- $it") }
                    appendLine()
                }
                transcript?.write(evalText.toByteArray(StandardCharsets.UTF_8))
            }

            // Update overview with initial scores
            val initialScores = currentPopulation.map { it.score.overall_score }
            overviewTask.add(buildString {
                appendLine()
                if (currentPopulation.size == 1) {
                    appendLine("- ✓ Initial evaluation: **${String.format("%.1f", initialScores[0])}/100**")
                } else {
                    appendLine("- ✓ Initial evaluations:")
                    appendLine("  - Best: **${String.format("%.1f", initialScores.maxOrNull() ?: 0.0)}/100**")
                    appendLine("  - Average: **${String.format("%.1f", initialScores.average())}/100**")
                    appendLine("  - Worst: **${String.format("%.1f", initialScores.minOrNull() ?: 0.0)}/100**")
                }
                appendLine("- ⏳ Starting evolution...")
            }.renderMarkdown)

            // Track best variant across all generations
            var bestVariant = currentPopulation[0]
            val evolutionHistory = mutableListOf<List<EvaluatedVariant>>()
            evolutionHistory.add(currentPopulation)

            // Evolution loop
            for (generation in 1..numGenerations) {
                log.info("Starting generation $generation of $numGenerations")

                val generationTask = tabs.newTask("Generation $generation")
                transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
                transcript?.write("# Generation $generation\n\n".toByteArray(StandardCharsets.UTF_8))
                generationTask.add(buildString {
                    appendLine("# Generation $generation")
                    appendLine()
                    appendLine("**Status:** In Progress")
                    appendLine()
                    appendLine("Generating $populationSize variants...")
                }.renderMarkdown)

// Step 1: Generate new variants
                val newVariants = mutableListOf<EvaluatedVariant>()

                // Keep top performers from previous generation
                val survivors = currentPopulation.sortedByDescending { it.score.overall_score }.take(selectionSize)
                // Track all texts in current generation to prevent duplicates
                val existingTexts = survivors.map { it.text }.toMutableSet()


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
                        newVariants.add(
                            EvaluatedVariant(
                                score = EvaluationScore(overall_score = 0.0),
                                generation = generation,
                            )
                        )
                        // Try up to 3 times to generate a unique variant
                        var attempts = 0
                        var mutated: TextVariant? = null
                        while (attempts < 3 && mutated == null) {
                            val candidate = generateMutation(
                                survivor.text,
                                survivor.score,
                                strategy,
                                optimizationGoal,
                                constraints,
                                "", // Context handled by agent state
                                api
                            )
                            if (candidate != null && !existingTexts.contains(candidate.text)) {
                                mutated = candidate
                                existingTexts.add(candidate.text)
                            }
                            attempts++
                        }

                        if (mutated != null) {
                            // Calculate diversity score based on compressibility with existing population
                            val diversityScore = calculateDiversityScore(mutated.text, existingTexts.toList())

                            newVariants.add(
                                EvaluatedVariant(
                                    text = mutated.text,
                                    score = EvaluationScore(overall_score = 0.0),
                                    generation = generation,
                                    parentIndex = survivorIndex,
                                    strategy = strategy,
                                    diversityScore = diversityScore
                                )
                            )
                        }
                    }
                }

                if (enableCrossover && survivors.size >= 2 && newVariants.size < populationSize) {

                    // Try up to 3 times to generate a unique crossover
                    var attempts = 0
                    var crossoverVariant: String? = null
                    while (attempts < 3 && crossoverVariant == null) {
                        val candidate = applyCrossover(
                            survivors[0].text,
                            survivors[0].score,
                            survivors[1].text,
                            survivors[1].score,
                            optimizationGoal,
                            constraints,
                            api
                        )
                        if (candidate != null && !existingTexts.contains(candidate)) {
                            crossoverVariant = candidate
                            existingTexts.add(candidate)
                        }
                        attempts++
                    }

                    if (crossoverVariant != null) {
                        val diversityScore = calculateDiversityScore(crossoverVariant, existingTexts.toList())

                        newVariants.add(
                            EvaluatedVariant(
                                text = crossoverVariant,
                                score = EvaluationScore(overall_score = 0.0),
                                generation = generation,
                                strategy = "crossover",
                                diversityScore = diversityScore
                            )
                        )
                    }
                }

                // Combine survivors and new variants
                currentPopulation = survivors + newVariants

// Step 2: Evaluate all variants
                currentPopulation = currentPopulation.map { variant ->
                    if (variant.score.overall_score == 0.0) {
                        val evaluation = evaluateVariant(
                            variant.text,
                            optimizationGoal,
                            evaluationWeights,
                            constraints,
                            api
                        )
                        // Combine fitness score with diversity bonus (10% weight on diversity)
                        val adjustedScore = evaluation.copy(
                            overall_score = evaluation.overall_score * 0.9 + variant.diversityScore * 10.0
                        )
                        variant.copy(score = adjustedScore)
                    } else {
                        variant
                    }
                }
                // Log diversity statistics
                val avgDiversity = currentPopulation.map { it.diversityScore }.average()

                evolutionHistory.add(currentPopulation)

                // Update best variant
                val generationBest = currentPopulation.maxByOrNull { it.score.overall_score }!!
                if (generationBest.score.overall_score > bestVariant.score.overall_score) {
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
                    val diversityScores = currentPopulation.map { it.diversityScore }
                    appendLine("- **Average Diversity:** ${String.format("%.3f", diversityScores.average())}")
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
                            appendLine("<details><summary>View Variant Text</summary>\n\n$TT")
                            appendLine(variant.text)
                            appendLine("$TT\n</details>")
                            appendLine("$TT")
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
            }

            // Create evolution visualization tab
            val evolutionTask = tabs.newTask("Evolution Analysis")
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
                    val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                    val successRate =
                        scores.count { it > initialBestScore }.toDouble() / scores.size * 100
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
                appendLine("$TT")
                appendLine(initialText)
                if (initialText.size == 1) {
                    appendLine(
                        "### Initial Text (Score: ${
                            String.format(
                                "%.1f",
                                evolutionHistory[0][0].score.overall_score
                            )
                        })"
                    )
                    appendLine("$TT")
                    appendLine(initialText[0])
                    appendLine("$TT")
                } else {
                    appendLine("### Initial Texts")
                    evolutionHistory[0].forEachIndexed { index, variant ->
                        appendLine()
                        appendLine(
                            "#### Seed ${index + 1} (Score: ${
                                String.format(
                                    "%.1f",
                                    variant.score.overall_score
                                )
                            })"
                        )
                        appendLine("$TT")
                        appendLine(variant.text)
                        appendLine("$TT")
                    }
                }
                appendLine()
                appendLine(
                    "### Final Optimized Text (Score: ${
                        String.format(
                            "%.1f",
                            bestVariant.score.overall_score
                        )
                    })"
                )
                appendLine("$TT")
                appendLine(bestVariant.text)
                appendLine("$TT")
                appendLine()
                appendLine("### Improvement Summary")
                appendLine()
                val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                appendLine(
                    "- **Score Improvement:** ${
                        String.format(
                            "%+.1f",
                            bestVariant.score.overall_score - initialBestScore
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
                    val initialScore =
                        evolutionHistory[0].map { it.score.criteria_scores[criterion] ?: 0.0 }.maxOrNull() ?: 0.0
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
                appendLine("$TT")
                appendLine(bestVariant.text)
                appendLine("$TT")
                appendLine()
                appendLine("## Performance Metrics")
                appendLine()
                val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                appendLine("- **Initial Best Score:** ${String.format("%.1f", initialBestScore)}/100")
                appendLine("- **Final Score:** ${String.format("%.1f", bestVariant.score.overall_score)}/100")
                appendLine(
                    "- **Improvement:** ${
                        String.format(
                            "%+.1f",
                            bestVariant.score.overall_score - initialBestScore
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
                val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                appendLine("| Initial Best Score | ${String.format("%.1f", initialBestScore)}/100 |")
                appendLine("| Final Score | ${String.format("%.1f", bestVariant.score.overall_score)}/100 |")
                appendLine(
                    "| Improvement | ${
                        String.format(
                            "%+.1f",
                            bestVariant.score.overall_score - initialBestScore
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
            transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
            transcript?.write(finalOverview.toByteArray(StandardCharsets.UTF_8))


            val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
            log.info("GeneticOptimizationTask completed successfully: total_time=${totalTime}ms, improvement=${bestVariant.score.overall_score - initialBestScore}, generations=$numGenerations")
            task.complete(
                "Optimization complete: improved by ${
                    String.format(
                        "%.1f",
                        bestVariant.score.overall_score - initialBestScore
                    )
                } points in ${totalTime / 1000}s"
            )
            val transcriptFile = "optimization_results_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
            val (link, _) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
            val summaryMessage = buildString {
                appendLine("Final Optimized Text")
                appendLine("---")
                appendLine("<details><summary>Optimized Content</summary>\n\n$TT")
                appendLine(bestVariant.text)
                appendLine("$TT\n</details>")
                appendLine("---")
                appendLine("**Strengths:**")
                bestVariant.score.strengths.forEach { appendLine("- $it") }
                appendLine()
                if (bestVariant.score.weaknesses.isNotEmpty()) {
                    appendLine("**Remaining Areas for Improvement:**")
                    bestVariant.score.weaknesses.forEach { appendLine("- $it") }
                    appendLine()
                }
                appendLine()
                appendLine(
                    "Detailed results: <a href='$link' target='_blank'>$link</a> " +
                            "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                            "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
                )
            }
            resultFn(summaryMessage)

            } catch (e: Exception) {
                handleError(e, task, transcript, resultFn)
            } finally {
                transcript?.close()
            }
        }
    }

    private fun handleError(e: Exception, task: SessionTask, transcript: OutputStream?, resultFn: (String) -> Unit) {
        log.error("Error in GeneticOptimizationTask: ${e.message}", e)
        task.error(e)
        transcript?.write(
            """
            ## Error
            <details><summary>Stack Trace</summary>
            ```
            ${e.stackTraceToString()}
            ```
            </details>
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        )
        resultFn("ERROR: ${e.message}")
    }

    /**
     * Calculate diversity score for a text variant based on its compressibility
     * with existing population members. Higher scores indicate more diversity.
     *
     * Returns a score between 0 and 1, where:
     * - 1.0 = highly diverse (incompressible with existing texts)
     * - 0.0 = duplicate or very similar (highly compressible)
     */
    private fun calculateDiversityScore(text: String, existingTexts: List<String>): Double {
        if (existingTexts.isEmpty()) return 1.0
        // Calculate average compressibility with all existing texts
        val compressibilities = existingTexts.map { existing ->
            compressibility(text, existing)
        }
        val avgCompressibility = compressibilities.average()
        // Convert compressibility to diversity score
        // compressibility of 1.0 (incompressible) -> diversity 1.0 (very diverse)
        // compressibility of 2.0 (duplicate) -> diversity 0.0 (not diverse)
        val diversityScore = max(0.0, min(1.0, 2.0 - avgCompressibility))
        log.debug(
            "Diversity score: ${
                String.format(
                    "%.3f",
                    diversityScore
                )
            } (avg compressibility: ${String.format("%.3f", avgCompressibility)})"
        )
        return diversityScore
    }


    private fun generateMutation(
        text: String,
        parentScore: EvaluationScore,
        strategy: String,
        goal: String,
        constraints: List<String>,
        context: String,
        api: ChatInterface
    ) = try {
        ParsedAgent(
            resultClass = TextVariant::class.java,
            prompt = """
 You are a text optimization expert applying genetic algorithm mutations.

 ## Optimization Goal
 $goal

 ## Mutation Strategy
 $strategy
${
                if (constraints.isNotEmpty()) {
                    "\n\nConstraints to maintain:\n${constraints.joinToString("\n") { "- $it" }}"
                } else ""
            }
${
                if (context.isNotBlank()) {
                    "\n\nAdditional context:\n${context.take(5000)}"
                } else ""
            }

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
 
## Parent Evaluation Feedback
**Overall Score:** ${String.format("%.1f", parentScore.overall_score)}/100
**Strengths to Preserve:**
${parentScore.strengths.joinToString("\n") { "- $it" }}
**Weaknesses to Address:**
${parentScore.weaknesses.joinToString("\n") { "- $it" }}
**Criteria Scores:**
${
                parentScore.criteria_scores.entries.joinToString("\n") { (criterion, score) ->
                    "- $criterion: ${String.format("%.1f", score)}/100"
                }
            }
**Justification:** ${parentScore.justification}

Use this feedback to guide your mutation:
- Preserve the strengths identified in the parent
- Address the weaknesses while applying the mutation strategy
- Focus on improving the lowest-scoring criteria
      """.trimIndent(),
            model = api,
            temperature = 0.8,
            name = "MutationGenerator",
            parsingChatter = defaultFast,
        ).answer(
            listOf(
                """

 ## Current Text
$TT
$text
```

        """
            )
        ).obj
    } catch (e: Exception) {
        log.warn("Failed to generate mutation with strategy $strategy", e)
        null
    }

    private fun applyCrossover(
        text1: String,
        score1: EvaluationScore,
        text2: String,
        score2: EvaluationScore,
        goal: String,
        constraints: List<String>,
        api: ChatInterface
    ) = try {
        ChatAgent(
            prompt = "You are a text optimization expert.",
            model = api,
            temperature = 0.7
        ).answer(
            listOf(
                """
 You are a text optimization expert applying genetic algorithm crossover.

                
Use the evaluation feedback to guide your crossover:
- Preserve the strengths from both parents
- Avoid or fix the weaknesses identified in both parents
- Focus on combining high-scoring aspects from each parent

 ## Parent Text 1
$TT
$text1
```

## Parent 1 Evaluation
**Score:** ${String.format("%.1f", score1.overall_score)}/100
**Strengths:**
${score1.strengths.joinToString("\n") { "- $it" }}
**Weaknesses:**
${score1.weaknesses.joinToString("\n") { "- $it" }}
**Criteria Scores:**
${
                    score1.criteria_scores.entries.joinToString("\n") { (criterion, score) ->
                        "- $criterion: ${String.format("%.1f", score)}/100"
                    }
                }
                
## Parent Text 2
```
$text2
```

## Parent 2 Evaluation
**Score:** ${String.format("%.1f", score2.overall_score)}/100

**Strengths:**
${score2.strengths.joinToString("\n") { "- $it" }}

**Weaknesses:**
${score2.weaknesses.joinToString("\n") { "- $it" }}

**Criteria Scores:**
${
                    score2.criteria_scores.entries.joinToString("\n") { (criterion, score) ->
                        "- $criterion: ${String.format("%.1f", score)}/100"
                    }
                }

## Optimization Goal
$goal
${
                    if (constraints.isNotEmpty()) {
                        "\n\nConstraints to maintain:\n${constraints.joinToString("\n") { "- $it" }}"
                    } else ""
                }

## Instructions
Create a new variant by combining the best elements from both parent texts.
- Identify the strongest aspects of each parent
- Merge them into a cohesive new variant
- Ensure the result is better than either parent alone
- Maintain consistency and flow

 Generate the crossover variant now.
      """.trimIndent()
            )
        )
    } catch (e: Exception) {
        log.warn("Failed to apply crossover", e)
        null
    }

    private fun evaluateVariant(
        text: String,
        goal: String,
        weights: Map<String, Double>,
        constraints: List<String>,
        api: ChatInterface
    ) = try {
        ParsedAgent(
            resultClass = EvaluationScore::class.java,
            prompt = """
 You are an expert evaluator for text optimization using genetic algorithms.


## Optimization Goal
$goal

## Evaluation Criteria
${
                weights.entries.joinToString("\n") { (criterion, weight) ->
                    "- $criterion (${
                        String.format(
                            "%.0f%%",
                            weight * 100
                        )
                    } weight)"
                }
            }
${
                if (constraints.isNotEmpty()) {
                    "\n\nConstraints:\n${constraints.joinToString("\n") { "- $it" }}"
                } else ""
            }

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
      """.trimIndent(),
            model = api,
            temperature = 0.3,
            name = "VariantEvaluator",
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
        log.warn("Failed to evaluate variant", e)
        EvaluationScore(
            overall_score = 0.0,
            criteria_scores = weights.keys.associateWith { 0.0 },
            strengths = emptyList(),
            weaknesses = listOf("Evaluation failed: ${e.message}"),
            justification = "Error during evaluation"
        )
    }
}