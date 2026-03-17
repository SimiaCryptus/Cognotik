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
import com.simiacryptus.cognotik.webui.session.getChildClient
import org.slf4j.Logger
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import kotlin.math.max
import kotlin.math.min

class GeneticOptimizationTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GeneticOptimizationTaskExecutionConfigData?
) : AbstractTask<GeneticOptimizationTask.GeneticOptimizationTaskExecutionConfigData, GeneticOptimizationTask.GeneticOptimizationTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log: Logger = LoggerFactory.getLogger(GeneticOptimizationTask::class.java)
        @JvmStatic val GeneticOptimization = TaskType(
            name = "GeneticOptimization",
            category = "Reasoning",
            taskClass = GeneticOptimizationTask::class.java,
            executionConfigClass = GeneticOptimizationTaskExecutionConfigData::class.java,
            taskSettingsClass = GeneticOptimizationTypeConfig::class.java,
            description = "Iteratively evolve and perfect text through genetic algorithms",
            tooltipHtml = "<ul>" +
                "<li>Generates variations using configurable mutation strategies</li>" +
                "<li>Evaluates variants against optimization criteria</li>" +
                "<li>Selects top performers for next generation</li>" +
                "<li>Applies crossover to combine successful traits</li>" +
                "<li>Tracks fitness progression across generations</li>" +
                "<li>Provides detailed analysis of evolution</li>" +
                "<li>Supports custom evaluation criteria and weights</li>" +
                "<li>Useful for perfecting prompts, copy, documentation, and messaging</li>" +
                "</ul>",
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
    class GeneticOptimizationTypeConfig(
        var mutation_prompt: String = "You are a text optimization expert applying genetic algorithm mutations.",
        var crossover_prompt: String = "You are a text optimization expert applying genetic algorithm crossover.",
        var evaluation_prompt: String = "You are an expert evaluator for text optimization using genetic algorithms.",
        var mutation_temperature: Double = 0.8,
        var crossover_temperature: Double = 0.7,
        var evaluation_temperature: Double = 0.3,
        var diversity_weight: Double = 0.1,
        var max_context_chars: Int = 5000,
        var max_mutation_retries: Int = 3,
        var max_crossover_retries: Int = 3,
    ) : TaskTypeConfig()


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
            if (initial_text.isNullOrEmpty()) return "initial_text must not be empty"
            if (initial_text!!.any { it.isBlank() }) return "initial_text entries must not be blank"
            if (optimization_goal.isNullOrBlank()) return "optimization_goal must not be blank"
            num_generations = num_generations.coerceIn(1, 50)
            population_size = population_size.coerceIn(2, 50)
            selection_size = selection_size.coerceIn(1, population_size - 1)
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class TextVariant(
        @Description("The full text of the mutated variant")
        var text: String = "",
        @Description("Brief explanation of what changed from the parent text")
        var mutation_description: String = "",
        @Description("The mutation strategy that was applied (e.g., 'rephrase', 'simplify', 'elaborate')")
        var strategy: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            text = text.trim()
            return if (text.isBlank()) "text must not be blank" else null
        }
    }

    data class EvaluationScore(
        @Description("Overall fitness score (0-100)")
        var overall_score: Double = 0.0,
        @Description("Breakdown of scores by criteria name to numeric score (0-100)")
        var criteria_scores: Map<String, Double> = emptyMap(),
        @Description("List of strengths identified in this variant")
        var strengths: List<String> = emptyList(),
        @Description("List of weaknesses or areas for improvement in this variant")
        var weaknesses: List<String> = emptyList(),
        @Description("Brief justification explaining the rationale for the assigned scores")
        var justification: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            overall_score = overall_score.coerceIn(0.0, 100.0)
            criteria_scores = criteria_scores.mapValues { (_, score) -> score.coerceIn(0.0, 100.0) }
            return ValidatedObject.Companion.validateFields(this)
        }
    }

    data class EvaluatedVariant(
        var text: String = "",
        var score: EvaluationScore = EvaluationScore(),
        var generation: Int = 0,
        var parent_index: Int? = null,
        var strategy: String = "",
        var diversity_score: Double = 0.0
    )

    override fun promptSegment(): String = buildString {
        appendLine("GeneticOptimization - Iteratively evolve and perfect text through genetic algorithms")
        appendLine("  ** Specify the FULL text(s) items to optimize in initial_text")
        appendLine("  ** Define the optimization_goal (e.g., clarity, persuasiveness)")
        appendLine("  ** Configure num_generations (default: 5)")
        appendLine("  ** Set population_size and selection_size")
        appendLine("  ** Choose mutation_strategies (rephrase, simplify, elaborate, restructure)")
        appendLine("  ** Enable/disable crossover for combining traits")
        appendLine("  ** Define evaluation_weights criteria and weights")
        appendLine("  ** Use this when the user wants to iteratively improve text quality through evolutionary optimization")
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val smartApi = orchestrationConfig.defaultSmart.getChildClient(task)
        val fastApi = orchestrationConfig.defaultFast.getChildClient(task)
        val transcript = task.newUserFileStream(transcriptFile())
        task.ui.pool.submit {
            try {
                val startTime = System.currentTimeMillis()
                log.info("Starting GeneticOptimizationTask. Goal: ${executionConfig?.optimization_goal}")

                executionConfig?.validate()?.let { errorMessage ->
                    val error = ValidatedObject.ValidationError(errorMessage, executionConfig)
                    handleError(error, task, transcript, resultFn)
                    return@submit
                }

                val tc = typeConfig ?: GeneticOptimizationTypeConfig()
                val diversityWeight = tc.diversity_weight
                val maxMutationRetries = tc.max_mutation_retries
                val maxCrossoverRetries = tc.max_crossover_retries
                val maxContextChars = tc.max_context_chars

                val initialText = executionConfig?.initial_text
                val optimizationGoal = executionConfig?.optimization_goal
                val numGenerations = executionConfig?.num_generations ?: 5
                val populationSize = executionConfig?.population_size ?: 6
                val selectionSize = min(executionConfig?.selection_size ?: 2, populationSize / 2)
                val mutationStrategies =
                    executionConfig?.mutation_strategies ?: listOf("rephrase", "simplify", "elaborate")
                val enableCrossover = executionConfig?.enable_crossover ?: true
                val evaluationWeights = executionConfig?.evaluation_weights ?: mapOf(
                    "clarity" to 0.35,
                    "conciseness" to 0.25,
                    "impact" to 0.25,
                    "goal_alignment" to 0.15
                )
                val constraints = executionConfig?.constraints ?: emptyList()

                if (initialText.isNullOrEmpty() || optimizationGoal.isNullOrBlank()) {
                    val error =
                        RuntimeException("Configuration error: initial_text is empty or optimization_goal is blank")
                    handleError(error, task, transcript, resultFn)
                    return@submit
                }

                log.info("Configuration validated: generations=$numGenerations, population=$populationSize, selection=$selectionSize, crossover=$enableCrossover")

                val tabs = TabbedDisplay(task)
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
                    appendLine("## Progress")
                    appendLine()
                    appendLine("- ⏳ Initializing population...")
                }
                overviewTask.add(overviewContent.renderMarkdown(true))
                transcript?.write(overviewContent.toByteArray(StandardCharsets.UTF_8))

                // Initialize population with the seed texts
                log.info("Evaluating ${initialText.size} initial texts")
                var currentPopulation = initialText.map { text ->
                    EvaluatedVariant(
                        text = text,
                        score = EvaluationScore(overall_score = 0.0),
                        generation = 0,
                        strategy = "seed",
                        diversity_score = 1.0
                    )
                }

                // Evaluate all initial texts
                currentPopulation = currentPopulation.map { variant ->
                    val evaluation =
                        evaluateVariant(variant.text, optimizationGoal, evaluationWeights, constraints, smartApi, fastApi, tc)
                    variant.copy(score = evaluation, diversity_score = 1.0)
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
                }.renderMarkdown(true))

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
                    }.renderMarkdown(true))

                    // Step 1: Generate new variants
                    val newVariants = mutableListOf<EvaluatedVariant>()

                    // Keep top performers from previous generation
                    val survivors =
                        currentPopulation.sortedByDescending { it.score.overall_score }.take(selectionSize)
                    // Track all texts in current generation to prevent duplicates
                    val existingTexts = survivors.map { it.text }.toMutableSet()

                    // Generate mutations from survivors
                    val mutationsNeeded = populationSize - survivors.size
                    val mutationsPerSurvivor = max(1, mutationsNeeded / survivors.size)

                    survivors.forEachIndexed { survivorIndex, survivor ->
                        val mutationsToGenerate = if (survivorIndex == survivors.size - 1) {
                            mutationsNeeded - (mutationsPerSurvivor * (survivors.size - 1))
                        } else {
                            mutationsPerSurvivor
                        }

                        repeat(mutationsToGenerate) {
                            val strategy = mutationStrategies.random()
                            var attempts = 0
                            var mutated: TextVariant? = null
                            while (attempts < maxMutationRetries && mutated == null) {
                                val candidate = generateMutation(
                                    survivor.text,
                                    survivor.score,
                                    strategy,
                                    optimizationGoal,
                                    constraints,
                                    maxContextChars,
                                    smartApi,
                                    fastApi,
                                    tc
                                )
                                if (candidate != null && !existingTexts.contains(candidate.text)) {
                                    mutated = candidate
                                    existingTexts.add(candidate.text)
                                }
                                attempts++
                            }

                            if (mutated != null) {
                                val ds = calculateDiversityScore(mutated.text, existingTexts.toList())
                                newVariants.add(
                                    EvaluatedVariant(
                                        text = mutated.text,
                                        score = EvaluationScore(overall_score = 0.0),
                                        generation = generation,
                                        parent_index = survivorIndex,
                                        strategy = strategy,
                                        diversity_score = ds
                                    )
                                )
                            }
                        }
                    }

                    if (enableCrossover && survivors.size >= 2 && newVariants.size < populationSize) {
                        var attempts = 0
                        var crossoverVariant: String? = null
                        while (attempts < maxCrossoverRetries && crossoverVariant == null) {
                            val candidate = applyCrossover(
                                survivors[0].text,
                                survivors[0].score,
                                survivors[1].text,
                                survivors[1].score,
                                optimizationGoal,
                                constraints,
                                smartApi,
                                tc
                            )
                            if (candidate != null && !existingTexts.contains(candidate)) {
                                crossoverVariant = candidate
                                existingTexts.add(candidate)
                            }
                            attempts++
                        }

                        if (crossoverVariant != null) {
                            val ds = calculateDiversityScore(crossoverVariant, existingTexts.toList())
                            newVariants.add(
                                EvaluatedVariant(
                                    text = crossoverVariant,
                                    score = EvaluationScore(overall_score = 0.0),
                                    generation = generation,
                                    strategy = "crossover",
                                    diversity_score = ds
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
                                smartApi,
                                fastApi,
                                tc
                            )
                            val adjustedScore = evaluation.copy(
                                overall_score = evaluation.overall_score * (1.0 - diversityWeight) + variant.diversity_score * (diversityWeight * 100.0)
                            )
                            variant.copy(score = adjustedScore)
                        } else {
                            variant
                        }
                    }

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
                        val diversityScores = currentPopulation.map { it.diversity_score }
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
                    generationTask.add(generationResults.renderMarkdown(true))
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
                            }, Avg=${
                                String.format(
                                    "%.1f",
                                    currentPopulation.map { it.score.overall_score }.average()
                                )
                            }"
                        )
                    }.renderMarkdown(true))
                }

                // Create evolution visualization tab
                val evolutionTask = tabs.newTask("Evolution Analysis")
                val evolutionAnalysis = buildString {
                    appendLine("<div id=\"evolution-analysis\" class=\"tab-content\" style=\"display: block;\" markdown=\"1\">")
                    appendLine()
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
                            strategyStats.getOrPut(variant.strategy) { mutableListOf() }
                                .add(variant.score.overall_score)
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
                    appendLine()
                    appendLine("</div>")
                }
                evolutionTask.add(evolutionAnalysis.renderMarkdown(true))
                transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
                transcript?.write(evolutionAnalysis.toByteArray(StandardCharsets.UTF_8))

                // Final overview update
                val totalTime = System.currentTimeMillis() - startTime
                val initialBestScore = evolutionHistory[0].maxOf { it.score.overall_score }
                val finalOverview = buildString {
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## ✅ Optimization Complete")
                    appendLine()
                    appendLine("| Metric | Value |")
                    appendLine("|--------|-------|")
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
                overviewTask.add(finalOverview.renderMarkdown(true))
                transcript?.write("\n\n---\n\n".toByteArray(StandardCharsets.UTF_8))
                transcript?.write(finalOverview.toByteArray(StandardCharsets.UTF_8))

                log.info("GeneticOptimizationTask completed successfully: total_time=${totalTime}ms, improvement=${bestVariant.score.overall_score - initialBestScore}, generations=$numGenerations")
                task.complete(
                    buildString {
                        append("Optimization complete: improved by ")
                        append(String.format("%.1f", bestVariant.score.overall_score - initialBestScore))
                        append(" points in ${totalTime / 1000}s")
                    }
                )
                val transcriptPath = transcriptFile()
                val link = task.linkTo(transcriptPath)
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
        transcript?.write(buildString {
            appendLine("## Error")
            appendLine()
            appendLine("<details><summary>Stack Trace</summary>")
            appendLine()
            appendLine("$TT")
            appendLine(e.stackTraceToString())
            appendLine("$TT")
            appendLine()
            appendLine("</details>")
        }.toByteArray())
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
        val compressibilities = existingTexts.map { existing ->
            compressibility(text, existing)
        }
        val avgCompressibility = compressibilities.average()
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
        maxContextChars: Int,
        api: ChatInterface,
        fastApi: ChatInterface,
        tc: GeneticOptimizationTypeConfig
    ) = try {
        val constraintsBlock = if (constraints.isNotEmpty()) {
            buildString {
                appendLine()
                appendLine("Constraints to maintain:")
                constraints.forEach { appendLine("- $it") }
            }
        } else ""
        val mutationPrompt = buildString {
            appendLine(tc.mutation_prompt)
            appendLine()
            appendLine("## Optimization Goal")
            appendLine(goal)
            appendLine()
            appendLine("## Mutation Strategy")
            appendLine(strategy)
            append(constraintsBlock)
            appendLine()
            appendLine("## Instructions")
            appendLine("Apply the \"$strategy\" mutation strategy to create a variant of the text that better achieves the optimization goal.")
            appendLine()
            appendLine("Mutation strategies:")
            appendLine("- **rephrase**: Reword while maintaining meaning, focus on different phrasing")
            appendLine("- **simplify**: Make more concise and easier to understand")
            appendLine("- **elaborate**: Add detail, examples, or explanation")
            appendLine("- **restructure**: Reorganize the structure or flow")
            appendLine("- **emphasize**: Strengthen key points or calls to action")
            appendLine("- **soften**: Make tone more gentle or diplomatic")
            appendLine()
            appendLine("Generate ONE variant that applies this strategy effectively.")
            appendLine()
            appendLine("## Parent Evaluation Feedback")
            appendLine("**Overall Score:** ${String.format("%.1f", parentScore.overall_score)}/100")
            appendLine("**Strengths to Preserve:**")
            parentScore.strengths.forEach { appendLine("- $it") }
            appendLine("**Weaknesses to Address:**")
            parentScore.weaknesses.forEach { appendLine("- $it") }
            appendLine("**Criteria Scores:**")
            parentScore.criteria_scores.entries.forEach { (criterion, score) ->
                appendLine("- $criterion: ${String.format("%.1f", score)}/100")
            }
            appendLine("**Justification:** ${parentScore.justification}")
            appendLine()
            appendLine("Use this feedback to guide your mutation:")
            appendLine("- Preserve the strengths identified in the parent")
            appendLine("- Address the weaknesses while applying the mutation strategy")
            appendLine("- Focus on improving the lowest-scoring criteria")
        }
        ParsedAgent(
            resultClass = TextVariant::class.java,
            prompt = mutationPrompt,
            model = api,
            temperature = tc.mutation_temperature,
            name = "MutationGenerator",
            parsingChatter = fastApi,
            deserializerRetries = 2,
        ).answer(
            listOf(
                buildString {
                    appendLine("## Current Text")
                    appendLine(TT)
                    appendLine(text)
                    appendLine(TT)
                }
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
        api: ChatInterface,
        tc: GeneticOptimizationTypeConfig
    ) = try {
        val crossoverPrompt = buildString {
            appendLine(tc.crossover_prompt)
            appendLine()
            appendLine("Use the evaluation feedback to guide your crossover:")
            appendLine("- Preserve the strengths from both parents")
            appendLine("- Avoid or fix the weaknesses identified in both parents")
            appendLine("- Focus on combining high-scoring aspects from each parent")
        }
        val userMessage = buildString {
            appendLine("## Parent Text 1")
            appendLine(TT)
            appendLine(text1)
            appendLine(TT)
            appendLine()
            appendLine("## Parent 1 Evaluation")
            appendLine("**Score:** ${String.format("%.1f", score1.overall_score)}/100")
            appendLine("**Strengths:**")
            score1.strengths.forEach { appendLine("- $it") }
            appendLine("**Weaknesses:**")
            score1.weaknesses.forEach { appendLine("- $it") }
            appendLine("**Criteria Scores:**")
            score1.criteria_scores.entries.forEach { (criterion, score) ->
                appendLine("- $criterion: ${String.format("%.1f", score)}/100")
            }
            appendLine()
            appendLine("## Parent Text 2")
            appendLine(TT)
            appendLine(text2)
            appendLine(TT)
            appendLine()
            appendLine("## Parent 2 Evaluation")
            appendLine("**Score:** ${String.format("%.1f", score2.overall_score)}/100")
            appendLine("**Strengths:**")
            score2.strengths.forEach { appendLine("- $it") }
            appendLine("**Weaknesses:**")
            score2.weaknesses.forEach { appendLine("- $it") }
            appendLine("**Criteria Scores:**")
            score2.criteria_scores.entries.forEach { (criterion, score) ->
                appendLine("- $criterion: ${String.format("%.1f", score)}/100")
            }
            appendLine()
            appendLine("## Optimization Goal")
            appendLine(goal)
            if (constraints.isNotEmpty()) {
                appendLine()
                appendLine("## Constraints to maintain")
                constraints.forEach { appendLine("- $it") }
            }
            appendLine()
            appendLine("## Instructions")
            appendLine("Create a new variant by combining the best elements from both parent texts.")
            appendLine("- Identify the strongest aspects of each parent")
            appendLine("- Merge them into a cohesive new variant")
            appendLine("- Ensure the result is better than either parent alone")
            appendLine("- Maintain consistency and flow")
            appendLine()
            appendLine("Generate the crossover variant now.")
        }
        ChatAgent(
            prompt = crossoverPrompt,
            model = api,
            temperature = tc.crossover_temperature
        ).answer(listOf(userMessage))
    } catch (e: Exception) {
        log.warn("Failed to apply crossover", e)
        null
    }

    private fun evaluateVariant(
        text: String,
        goal: String,
        weights: Map<String, Double>,
        constraints: List<String>,
        api: ChatInterface,
        fastApi: ChatInterface,
        tc: GeneticOptimizationTypeConfig
    ) = try {
        val constraintsBlock = if (constraints.isNotEmpty()) {
            buildString {
                appendLine()
                appendLine("Constraints:")
                constraints.forEach { appendLine("- $it") }
            }
        } else ""
        val evaluationPrompt = buildString {
            appendLine(tc.evaluation_prompt)
            appendLine()
            appendLine("## Optimization Goal")
            appendLine(goal)
            appendLine()
            appendLine("## Evaluation Criteria")
            weights.entries.forEach { (criterion, weight) ->
                appendLine("- $criterion (${String.format("%.0f%%", weight * 100)} weight)")
            }
            append(constraintsBlock)
            appendLine()
            appendLine("## Instructions")
            appendLine("Evaluate this text variant against the optimization goal and criteria.")
            appendLine()
            appendLine("For each criterion, provide a score from 0-100:")
            appendLine("- **clarity**: How clear and understandable is the text?")
            appendLine("- **conciseness**: How efficiently does it communicate?")
            appendLine("- **impact**: How effective is it at achieving the goal?")
            appendLine("- **goal_alignment**: How well does it align with the stated optimization goal?")
            appendLine()
            appendLine("Also provide:")
            appendLine("1. An overall weighted score (0-100)")
            appendLine("2. List of strengths (what works well)")
            appendLine("3. List of weaknesses (what could be improved)")
            appendLine("4. Brief justification for the scores")
            appendLine()
            appendLine("Be objective and consistent in your evaluation.")
        }
        ParsedAgent(
            resultClass = EvaluationScore::class.java,
            prompt = evaluationPrompt,
            model = api,
            temperature = tc.evaluation_temperature,
            name = "VariantEvaluator",
            parsingChatter = fastApi,
            deserializerRetries = 2,
        ).answer(
            listOf(
                buildString {
                    appendLine("## Text to Evaluate")
                    appendLine(TT)
                    appendLine(text)
                    appendLine(TT)
                }
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