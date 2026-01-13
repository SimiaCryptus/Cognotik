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
import com.simiacryptus.cognotik.util.MarkdownUtil
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
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
        val PoliticalOptimization = TaskType(
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
            overviewTask.add(MarkdownUtil.renderMarkdown(overviewContent))
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
            }.let { MarkdownUtil.renderMarkdown(it) })

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
                }.let { MarkdownUtil.renderMarkdown(it) })

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
                generationTask.add(MarkdownUtil.renderMarkdown(generationResults))
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
                }.let { MarkdownUtil.renderMarkdown(it) })
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
            consensusAnalysisTask.add(MarkdownUtil.renderMarkdown(consensusAnalysis))
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
            divisiveAnalysisTask.add(MarkdownUtil.renderMarkdown(divisiveAnalysis))
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
            evolutionTask.add(MarkdownUtil.renderMarkdown(evolutionAnalysis))
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
            overviewTask.add(MarkdownUtil.renderMarkdown(finalOverview))
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