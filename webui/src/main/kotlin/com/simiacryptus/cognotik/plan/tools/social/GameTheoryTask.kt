package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.input.PaginatedDocumentReader
import com.simiacryptus.cognotik.input.getDocumentReader
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.io.FileOutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

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
        val GameTheory = TaskType(
            "GameTheory",
            GameTheoryTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Analyze strategic interactions using game theory",
            """
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
            """
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
        val game_scenario: String? = null,
        @Description("List of players/agents in the game")
        val players: List<String>? = null,
        @Description("Available strategies for each player (optional, can be inferred)")
        val player_strategies: Map<String, List<String>>? = null,
        @Description("Type of game: cooperative, non-cooperative, zero-sum, repeated, sequential")
        val game_type: String? = "non-cooperative",
        @Description("Whether to construct a payoff matrix")
        val build_payoff_matrix: Boolean = true,
        @Description("Whether to identify Nash equilibria")
        val find_nash_equilibria: Boolean = true,
        @Description("Whether to analyze dominant strategies")
        val analyze_dominant_strategies: Boolean = true,
        @Description("Whether to identify Pareto optimal outcomes")
        val find_pareto_optimal: Boolean = true,
        @Description("Whether to provide strategic recommendations for each player")
        val provide_recommendations: Boolean = true,
        @Description("Whether to analyze the game as a repeated game")
        val repeated_game_analysis: Boolean = false,
        @Description("Number of iterations for repeated game analysis")
        val iterations: Int = 10,
        @Description("Additional context or constraints")
        val additional_context: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
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
            if (players.any { it.isBlank() }) {
                return "players list must not contain blank entries"
            }
            if (game_type.isNullOrBlank()) {
                return "game_type must not be null or blank"
            }
            if (game_type.isBlank()) {
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
        val startTime = System.currentTimeMillis()
        log.info("Starting GameTheory task for scenario: ${executionConfig?.game_scenario}")
        val toInput = { it: String -> messages + listOf(getInputFileCode(), it).filter { it.isNotBlank() } }

        val gameScenario = executionConfig?.game_scenario
        if (gameScenario.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No game scenario specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val players = executionConfig.players
        if (players.isNullOrEmpty()) {
            val errorMsg = "CONFIGURATION ERROR: No players specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val ui = task.ui
        val api = defaultChatter ?: return
        val transcript = transcript(task)
        // Create tabbed display for organized output
        val tabs = TabbedDisplay(task)
        // Overview tab
        val overviewTask = task.ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder


        try {
            transcript?.write("# Game Theory Analysis\n\n".toByteArray())
            transcript?.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )


            var overviewTaskStatus = overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Game Theory Analysis
            |
            |**Scenario:** $gameScenario
            |
            |**Players:** ${players.joinToString(", ")}
            |
            |**Game Type:** ${executionConfig.game_type}
            |**Status:** 🔄 Initializing analysis...
        """.trimMargin(), ui = ui
                )
            )
            transcript?.write(
                """
        |## Game Theory Analysis
        |
        |**Scenario:** $gameScenario
        |
        |**Players:** ${players.joinToString(", ")}
        |
        |**Game Type:** ${executionConfig.game_type}
        |
        |""".trimMargin().toByteArray()
            )
            task.update()

            log.debug("Retrieving prior context from execution state")
            val priorContext = getPriorCode(agent.executionState)

            // Build context section
            val contextBuilder = StringBuilder()
            if (priorContext.isNotBlank()) {
                contextBuilder.append("## Context from Previous Tasks\n\n")
                contextBuilder.append(priorContext)
                contextBuilder.append("\n\n")

                val contextTask = task.ui.newTask(false)
                tabs["Context"] = contextTask.placeholder
                contextTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |# Context from Previous Tasks
            |
            |$priorContext
            """.trimMargin(), ui = ui
                    )
                )
                transcript?.write(
                    """
          |## Context from Previous Tasks
          |
          |$priorContext
          |
          |""".trimMargin().toByteArray()
                )
                task.update()
            }

            if (executionConfig.additional_context?.isNotBlank() == true) {
                contextBuilder.append("## Additional Context\n\n")
                contextBuilder.append(executionConfig.additional_context)
                contextBuilder.append("\n\n")
            }

            // Update overview
            overviewTaskStatus?.clear()
            overviewTaskStatus = overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Game Theory Analysis
            |
            |**Scenario:** $gameScenario
            |
            |**Players:** ${players.joinToString(", ")}
            |
            |**Game Type:** ${executionConfig.game_type}
            |
            |**Status:** 🔄 Analyzing game structure...
        """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 1: Analyze game structure and strategies
            var stepStartTime = System.currentTimeMillis()
            log.debug("Analyzing game structure")
            val structureTask = task.ui.newTask(false)
            tabs["Game Structure"] = structureTask.placeholder
            val structureLoading = structureTask.add(
                MarkdownUtil.renderMarkdown(
                    "## Game Structure\n\n🔄 Analyzing game structure and strategies...",
                    ui = ui
                )
            )
            task.update()

            val structurePrompt = buildStructurePrompt(gameScenario, players, contextBuilder.toString())

            val chatAgent = ChatAgent(
                prompt = structurePrompt,
                model = api,
                temperature = 0.3
            )

            val structureAnalysis = chatAgent.answer(toInput(structurePrompt))
            log.info("Structure analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${structureAnalysis.length} characters")
            transcript?.write(
                """
        |## Game Structure Analysis
        |
        |$structureAnalysis
        |
        |""".trimMargin().toByteArray()
            )


            structureLoading?.clear()
            structureTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Game Structure Analysis
            |
            |✅ Analysis complete
            |
            |$structureAnalysis
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 2: Build payoff matrix if requested
            var payoffMatrix: String
            if (executionConfig.build_payoff_matrix) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Building payoff matrix")
                val payoffTask = task.ui.newTask(false)
                tabs["Payoff Matrix"] = payoffTask.placeholder
                val payoffLoading = payoffTask.add(
                    MarkdownUtil.renderMarkdown("## Payoff Matrix\n\n🔄 Constructing payoff matrix...", ui = ui)
                )
                task.update()

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
                transcript?.write(
                    """
          |## Payoff Matrix
          |
          |$payoffMatrix
          |
          |""".trimMargin().toByteArray()
                )


                payoffLoading?.clear()
                payoffTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |## Payoff Matrix
            |
            |✅ Matrix constructed
            |
            |$payoffMatrix
            """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 3: Find Nash equilibria if requested
            var nashEquilibria = ""
            if (executionConfig.find_nash_equilibria) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Finding Nash equilibria")
                val nashTask = task.ui.newTask(false)
                tabs["Nash Equilibria"] = nashTask.placeholder
                val nashLoading = nashTask.add(
                    MarkdownUtil.renderMarkdown("## Nash Equilibria\n\n🔄 Identifying Nash equilibria...", ui = ui)
                )
                task.update()

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
                transcript?.write(
                    """
          |## Nash Equilibria Analysis
          |
          |$nashEquilibria
          |
          |""".trimMargin().toByteArray()
                )


                nashLoading?.clear()
                nashTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |## Nash Equilibria Analysis
            |
            |✅ Analysis complete
            |
            |$nashEquilibria
            """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 4: Analyze dominant strategies if requested
            var dominantStrategies = ""
            if (executionConfig.analyze_dominant_strategies) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Analyzing dominant strategies")
                val dominantTask = task.ui.newTask(false)
                tabs["Dominant Strategies"] = dominantTask.placeholder
                val dominantLoading = dominantTask.add(
                    MarkdownUtil.renderMarkdown("## Dominant Strategies\n\n🔄 Analyzing dominant strategies...", ui = ui)
                )
                task.update()

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
                transcript?.write(
                    """
          |## Dominant Strategies Analysis
          |
          |$dominantStrategies
          |
          |""".trimMargin().toByteArray()
                )


                dominantLoading?.clear()
                dominantTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |## Dominant Strategies Analysis
            |
            |✅ Analysis complete
            |
            |$dominantStrategies
            """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 5: Find Pareto optimal outcomes if requested
            var paretoOptimal: String
            if (executionConfig.find_pareto_optimal) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Finding Pareto optimal outcomes")
                val paretoTask = task.ui.newTask(false)
                tabs["Pareto Optimality"] = paretoTask.placeholder
                val paretoLoading = paretoTask.add(
                    MarkdownUtil.renderMarkdown(
                        "## Pareto Optimality\n\n🔄 Identifying Pareto optimal outcomes...",
                        ui = ui
                    )
                )
                task.update()

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
                transcript?.write(
                    """
          |## Pareto Optimality Analysis
          |
          |$paretoOptimal
          |
          |""".trimMargin().toByteArray()
                )


                paretoLoading?.clear()
                paretoTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |## Pareto Optimality Analysis
            |
            |✅ Analysis complete
            |
            |$paretoOptimal
            """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 6: Repeated game analysis if requested
            var repeatedGameAnalysis: String
            if (executionConfig.repeated_game_analysis) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Analyzing repeated game dynamics")
                val repeatedTask = task.ui.newTask(false)
                tabs["Repeated Game"] = repeatedTask.placeholder
                val repeatedLoading = repeatedTask.add(
                    MarkdownUtil.renderMarkdown(
                        "## Repeated Game Analysis\n\n🔄 Analyzing repeated game dynamics...",
                        ui = ui
                    )
                )
                task.update()

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
                transcript?.write(
                    """
          |## Repeated Game Analysis
          |
          |$repeatedGameAnalysis
          |
          |""".trimMargin().toByteArray()
                )


                repeatedLoading?.clear()
                repeatedTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |## Repeated Game Analysis
            |
            |✅ Analysis complete
            |
            |$repeatedGameAnalysis
            """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 7: Provide strategic recommendations if requested
            var recommendations = ""
            if (executionConfig.provide_recommendations) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Generating strategic recommendations")
                val recommendTask = task.ui.newTask(false)
                tabs["Recommendations"] = recommendTask.placeholder
                val recommendLoading = recommendTask.add(
                    MarkdownUtil.renderMarkdown(
                        "## Strategic Recommendations\n\n🔄 Generating recommendations...",
                        ui = ui
                    )
                )
                task.update()

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
                transcript?.write(
                    """
          |## Strategic Recommendations
          |
          |$recommendations
          |
          |""".trimMargin().toByteArray()
                )


                recommendLoading?.clear()
                recommendTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
            |## Strategic Recommendations
            |
            |✅ Recommendations complete
            |
            |$recommendations
            """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 8: Generate comprehensive summary using ParsedAgent
            stepStartTime = System.currentTimeMillis()
            log.debug("Generating structured summary")
            val summaryTask = task.ui.newTask(false)
            tabs["Summary"] = summaryTask.placeholder
            val summaryLoading = summaryTask.add(
                MarkdownUtil.renderMarkdown("## Summary\n\n🔄 Generating comprehensive summary...", ui = ui)
            )
            task.update()

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
                parsingChatter = parsingChatter,
            )

            val gameAnalysis = parsedAgent.answer(toInput(summaryPrompt)).obj
            log.info("Structured summary generated in ${System.currentTimeMillis() - stepStartTime}ms")
            transcript?.write(
                """
        |## Game Theory Analysis Summary
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
        |
        |""".trimMargin().toByteArray())


            summaryLoading?.clear()
            summaryTask.add(
                MarkdownUtil.renderMarkdown(
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
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Update overview with completion
            overviewTaskStatus?.clear()
            overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Game Theory Analysis
            |
            |**Scenario:** $gameScenario
            |
            |**Players:** ${players.joinToString(", ")}
            |
            |**Game Type:** ${executionConfig.game_type}
            |
            |**Status:** ✅ Analysis complete
            |
            |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
        """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Build final result
            val finalResult = buildString {
                appendLine("# Game Theory Analysis: $gameScenario")
                appendLine()
                appendLine("## Players")
                appendLine(players.joinToString(", "))
                appendLine()
                appendLine("## Game Type")
                appendLine(executionConfig.game_type)
                appendLine()

                if (structureAnalysis.isNotEmpty()) {
                    appendLine("## Game Structure")
                    appendLine(structureAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                if (nashEquilibria.isNotEmpty()) {
                    appendLine("## Nash Equilibria")
                    appendLine(nashEquilibria.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                if (dominantStrategies.isNotEmpty()) {
                    appendLine("## Dominant Strategies")
                    appendLine(dominantStrategies.truncateForDisplay(maxOutputLengthPerField))
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
            log.info("$summary (duration: ${duration}ms, players: ${players.size}, game_type: ${executionConfig.game_type})")
            transcript?.write("\n---\n".toByteArray())
            transcript?.write("**Analysis completed in ${duration / 1000}s**\n".toByteArray())
            transcript?.write(
                "**Finished:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )
            transcript?.close()

            task.safeComplete(summary, log)
            resultFn(finalResult)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log.error("GameTheory task failed after ${duration}ms for scenario: $gameScenario", e)
            overviewTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Game Theory Analysis
            |
            |**Status:** ❌ Analysis Failed
            |
            |**Error:** ${e.message}
            """.trimMargin(), ui = ui
                )
            )
            task.update()
            transcript?.write("\n---\n**ERROR:** ${e.message}\n".toByteArray())
            transcript?.close()
            task.error(e)
            task.safeComplete("Analysis failed: ${e.message}", log)
            resultFn("ERROR: Game theory analysis failed - ${e.message}")
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
                executionConfig.player_strategies.entries.joinToString("\n") { (player, strategies) ->
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

    private fun transcript(task: SessionTask): FileOutputStream? {
        val transcriptFile = this.javaClass.simpleName + "_full_report_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (link, file) = Pair(task.linkTo(transcriptFile), task.resolveUserFile(transcriptFile))
        val markdownTranscript = file?.outputStream()
        task.complete(
            "Writing transcript to <a href='$link' target='_blank'>$link</a> " +
                    "<a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> " +
                    "<a href='${link.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>"
        )
        return markdownTranscript
    }
}