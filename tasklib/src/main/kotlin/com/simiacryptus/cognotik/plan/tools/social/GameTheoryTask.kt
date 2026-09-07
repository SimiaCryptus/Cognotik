package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.agents.ChatAgent
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
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.model.ISessionTask
import org.slf4j.Logger
import org.slf4j.LoggerFactory.getLogger
import java.io.File
import java.io.OutputStream
import java.nio.file.FileSystems
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GameTheoryTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: GameTheoryTaskExecutionConfigData?
) : AbstractTask<GameTheoryTask.GameTheoryTaskExecutionConfigData, GameTheoryTask.GameTheoryTypeConfig>(
  orchestrationConfig,
  planTask
) {
  val maxOutputLengthPerField = 10000

  companion object {
      private val log: Logger = getLogger(GameTheoryTask::class.java)

    @JvmStatic
    val GameTheory = TaskType(
      name = "GameTheory",
      category = "Reasoning",
      taskClass = GameTheoryTask::class.java,
      executionConfigClass = GameTheoryTaskExecutionConfigData::class.java,
      taskSettingsClass = GameTheoryTypeConfig::class.java,
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

  class GameTheoryTypeConfig(
    var analysis_temperature: Double = 0.3,
    var summary_temperature: Double = 0.2,
    var structure_prompt_template: String = """
You are an expert in game theory and strategic analysis. Your task is to analyze a strategic interaction using game theory principles.
## Game Scenario:
{game_scenario}
## Players:
{players}
{strategies_section}
## Game Type:
{game_type}
{context}
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
        """.trimIndent(),
    var payoff_matrix_prompt: String = """
Based on the game structure analysis above, construct a detailed payoff matrix.
For each combination of strategies, provide:
- The outcome for each player
- Numerical payoffs if possible (or qualitative rankings)
- Brief explanation of why these payoffs result
Format the matrix clearly using markdown tables or a structured format.
If the game has more than 2 players or complex strategy spaces, provide representative examples.
Generate the payoff matrix now:
        """.trimIndent(),
    var nash_equilibria_prompt: String = """
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
        """.trimIndent(),
    var dominant_strategies_prompt: String = """
Based on the game analysis above, identify any dominant or dominated strategies.
For each player, determine:
1. **Strictly Dominant Strategies**: Strategies that are always better regardless of what others do
2. **Weakly Dominant Strategies**: Strategies that are at least as good, and sometimes better
3. **Dominated Strategies**: Strategies that are always worse than some alternative
4. **Iteratively Eliminated Strategies**: Strategies that can be eliminated through iterated dominance
Explain the strategic implications of these findings.
Generate the dominant strategy analysis now:
        """.trimIndent(),
    var pareto_optimal_prompt: String = """
Based on the game analysis above, identify Pareto optimal outcomes.
For each outcome, determine:
1. Whether it is Pareto optimal (no player can be made better off without making another worse off)
2. Compare Pareto optimal outcomes to Nash equilibria
3. Identify any Pareto improvements over equilibrium outcomes
4. Discuss efficiency vs. equilibrium trade-offs
If there are opportunities for cooperation or coordination to reach Pareto improvements, explain them.
Generate the Pareto optimality analysis now:
        """.trimIndent(),
    var repeated_game_prompt_template: String = """
Analyze this game as a repeated game with {iterations} iterations.
Consider:
1. **Folk Theorem**: What outcomes can be sustained as equilibria in the repeated game?
2. **Trigger Strategies**: How can players use punishment strategies to enforce cooperation?
3. **Reputation Effects**: How does reputation building affect strategic choices?
4. **Discount Factors**: How does the value of future payoffs affect current decisions?
5. **Finite vs. Infinite Horizon**: Implications of the game ending after {iterations} rounds
Provide specific strategy recommendations for the repeated game context.
Generate the repeated game analysis now:
        """.trimIndent(),
    var recommendations_prompt_template: String = """
Based on the complete game theory analysis above, provide strategic recommendations for each player.
For each player ({players}), recommend:
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
        """.trimIndent(),
    var summary_prompt: String = """
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
  ) : TaskTypeConfig()


  data class GameAnalysis(
    @Description("The type of game identified (e.g., cooperative, non-cooperative, zero-sum, sequential)")
    var game_type: String? = null,
    @Description("List of players/agents identified in the game")
    var players: List<String>? = null,
    @Description("Map of player names to their available strategies")
    var strategies: Map<String, List<String>>? = null,
    @Description("Summary of the payoff matrix or payoff structure")
    var payoff_matrix: String? = null,
    @Description("List of Nash equilibria identified, described as strategy profiles")
    var nash_equilibria: List<String>? = null,
    @Description("Map of player names to their dominant strategy, if any")
    var dominant_strategies: Map<String, String>? = null,
    @Description("List of Pareto optimal outcomes identified")
    var pareto_optimal_outcomes: List<String>? = null,
    @Description("Map of player names to their strategic recommendation")
    var recommendations: Map<String, String>? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      game_type = game_type?.trim()?.ifBlank { null }
      players = players?.filter { it.isNotBlank() }?.map { it.trim() }
      nash_equilibria = nash_equilibria?.filter { it.isNotBlank() }?.map { it.trim() }
      pareto_optimal_outcomes = pareto_optimal_outcomes?.filter { it.isNotBlank() }?.map { it.trim() }
      return null
    }
  }

  private val codeFiles = mutableMapOf<Path, String>()

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
    var related_files: List<String>? = null,
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
      game_scenario = game_scenario?.trim()
      if (game_scenario.isNullOrBlank()) return "game_scenario must not be null or blank"
      players = players?.map { it.trim() }?.filter { it.isNotBlank() }
      if (players.isNullOrEmpty()) return "players list must not be null or empty"
      game_type = game_type?.trim()?.ifBlank { null } ?: "non-cooperative"
      iterations = iterations.coerceIn(1, 1000)
      if (repeated_game_analysis && iterations < 2) {
        iterations = 2
      }
      additional_context = additional_context?.trim()?.ifBlank { null }
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
    task: ISessionTask,
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

    val api = defaultSmart
    val effectiveTypeConfig = typeConfig ?: GameTheoryTypeConfig()

    task.pool.submit {
      var transcript: OutputStream? = null
      try {
        transcript = task.newUserFileStream(transcriptFile())
        val tabs = TabbedDisplay(task)
        val overviewTask = tabs.newTask("Overview")

        transcript?.write("# Game Theory Analysis\n\n".toByteArray())
        transcript?.write(
          "**Started:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }\n\n".toByteArray()
        )

        val overviewTaskStatus = overviewTask.add(
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

        val executionConfig = this.executionConfig ?: throw IllegalStateException("Execution config is null")
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
          contextTask.complete()
        }

        if (executionConfig.additional_context?.isNotBlank() == true) {
          contextBuilder.append("## Additional Context\n\n")
          contextBuilder.append(executionConfig.additional_context)
          contextBuilder.append("\n\n")
        }

        // Update overview
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

        val structurePrompt = buildStructurePrompt(gameScenario, players, contextBuilder.toString())

        val chatAgent = ChatAgent(
          prompt = structurePrompt,
          model = api,
          temperature = effectiveTypeConfig.analysis_temperature
        )

        val structureAnalysis = chatAgent.answer(toInput(structurePrompt))
        log.info("Structure analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${structureAnalysis.length} characters")
        transcript?.write("## Game Structure Analysis\n${structureAnalysis.wrapInDetails("Full Analysis")}\n\n".toByteArray())

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

        // Step 2: Build payoff matrix if requested
        var payoffMatrix = ""
        if (executionConfig.build_payoff_matrix) {
          stepStartTime = System.currentTimeMillis()
          log.debug("Building payoff matrix")
          val payoffTask = tabs.newTask("Payoff Matrix")
          val payoffLoading = payoffTask.add("## Payoff Matrix\n\n🔄 Constructing payoff matrix...".renderMarkdown())

          val payoffPrompt = effectiveTypeConfig.payoff_matrix_prompt

          payoffMatrix = chatAgent.answer(toInput(payoffPrompt))
          log.info("Payoff matrix generated in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${payoffMatrix.length} characters")
          transcript?.write("## Payoff Matrix\n${payoffMatrix.wrapInDetails("Full Matrix")}\n\n".toByteArray())

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
        }

        // Step 3: Find Nash equilibria if requested
        var nashEquilibria = ""
        if (executionConfig.find_nash_equilibria) {
          stepStartTime = System.currentTimeMillis()
          log.debug("Finding Nash equilibria")
          val nashTask = tabs.newTask("Nash Equilibria")
          val nashLoading = nashTask.add("## Nash Equilibria\n\n🔄 Identifying Nash equilibria...".renderMarkdown())

          val nashPrompt = effectiveTypeConfig.nash_equilibria_prompt

          nashEquilibria = chatAgent.answer(toInput(nashPrompt))
          log.info("Nash equilibria analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${nashEquilibria.length} characters")
          transcript?.write("## Nash Equilibria Analysis\n${nashEquilibria.wrapInDetails("Full Analysis")}\n\n".toByteArray())

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
        }

        // Step 4: Analyze dominant strategies if requested
        var dominantStrategies = ""
        if (executionConfig.analyze_dominant_strategies) {
          stepStartTime = System.currentTimeMillis()
          log.debug("Analyzing dominant strategies")
          val dominantTask = tabs.newTask("Dominant Strategies")
          val dominantLoading =
            dominantTask.add("## Dominant Strategies\n\n🔄 Analyzing dominant strategies...".renderMarkdown())

          val dominantPrompt = effectiveTypeConfig.dominant_strategies_prompt

          dominantStrategies = chatAgent.answer(toInput(dominantPrompt))
          log.info("Dominant strategies analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${dominantStrategies.length} characters")
          transcript?.write("## Dominant Strategies Analysis\n${dominantStrategies.wrapInDetails("Full Analysis")}\n\n".toByteArray())

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
        }

        // Step 5: Find Pareto optimal outcomes if requested
        var paretoOptimal = ""
        if (executionConfig.find_pareto_optimal) {
          stepStartTime = System.currentTimeMillis()
          log.debug("Finding Pareto optimal outcomes")
          val paretoTask = tabs.newTask("Pareto Optimality")
          val paretoLoading =
            paretoTask.add("## Pareto Optimality\n\n🔄 Identifying Pareto optimal outcomes...".renderMarkdown())

          val paretoPrompt = effectiveTypeConfig.pareto_optimal_prompt

          paretoOptimal = chatAgent.answer(toInput(paretoPrompt))
          log.info("Pareto optimality analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${paretoOptimal.length} characters")
          transcript?.write("## Pareto Optimality Analysis\n${paretoOptimal.wrapInDetails("Full Analysis")}\n\n".toByteArray())

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
        }

        // Step 6: Repeated game analysis if requested
        var repeatedGameAnalysis = ""
        if (executionConfig.repeated_game_analysis) {
          stepStartTime = System.currentTimeMillis()
          log.debug("Analyzing repeated game dynamics")
          val repeatedTask = tabs.newTask("Repeated Game")
          val repeatedLoading =
            repeatedTask.add("## Repeated Game Analysis\n\n🔄 Analyzing repeated game dynamics...".renderMarkdown())

          val repeatedPrompt = effectiveTypeConfig.repeated_game_prompt_template
            .replace("{iterations}", executionConfig.iterations.toString())

          repeatedGameAnalysis = chatAgent.answer(toInput(repeatedPrompt))
          log.info("Repeated game analysis completed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${repeatedGameAnalysis.length} characters")
          transcript?.write("## Repeated Game Analysis\n${repeatedGameAnalysis.wrapInDetails("Full Analysis")}\n\n".toByteArray())

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
        }

        // Step 7: Provide strategic recommendations if requested
        var recommendations = ""
        if (executionConfig.provide_recommendations) {
          stepStartTime = System.currentTimeMillis()
          log.debug("Generating strategic recommendations")
          val recommendTask = tabs.newTask("Recommendations")
          val recommendLoading =
            recommendTask.add("## Strategic Recommendations\n\n🔄 Generating recommendations...".renderMarkdown())

          val recommendPrompt = effectiveTypeConfig.recommendations_prompt_template
            .replace("{players}", players.joinToString(", "))

          recommendations = chatAgent.answer(toInput(recommendPrompt))
          log.info("Recommendations generated in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${recommendations.length} characters")
          transcript?.write("## Strategic Recommendations\n${recommendations.wrapInDetails("Full Recommendations")}\n\n".toByteArray())

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
        }

        // Step 8: Generate comprehensive summary using ParsedAgent
        stepStartTime = System.currentTimeMillis()
        log.debug("Generating structured summary")
        val summaryTask = tabs.newTask("Summary")
        val summaryLoading = summaryTask.add("## Summary\n\n🔄 Generating comprehensive summary...".renderMarkdown())

        val summaryPrompt = effectiveTypeConfig.summary_prompt

        val parsedAgent = ParsedAgent(
          resultClass = GameAnalysis::class.java,
          prompt = summaryPrompt,
          model = api,
          temperature = effectiveTypeConfig.summary_temperature,
          parsingModel = defaultFast,
          deserializerRetries = 2,
        )

        val gameAnalysis = parsedAgent.answer(toInput(summaryPrompt)).obj
        log.info("Structured summary generated in ${System.currentTimeMillis() - stepStartTime}ms")
        transcript?.write(
          "## Game Theory Analysis Summary\n${
            gameAnalysis.toString().wrapInDetails("Structured Data")
          }\n\n".toByteArray()
        )

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

        // Build final result
        val finalResult = buildFinalResult(
          gameScenario, players, executionConfig,
          structureAnalysis, payoffMatrix, nashEquilibria,
          dominantStrategies, paretoOptimal, repeatedGameAnalysis,
          recommendations, startTime
        )

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

        // Update overview with completion (after transcript write, before resultFn)
        overviewTask.add(
          """
## Game Theory Analysis

**Scenario:** $gameScenario

**Players:** ${players.joinToString(", ")}

**Game Type:** ${executionConfig?.game_type}

**Status:** ✅ Analysis complete

**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
        """.renderMarkdown()
        )
        overviewTask.complete()

        task.complete()
        resultFn(finalResult)

      } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        log.error("GameTheory task failed after ${duration}ms for scenario: $gameScenario", e)
        try {
          task.error(e)
        } catch (_: Exception) {
          // UI error reporting is best-effort
        }
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        resultFn("ERROR: Game theory analysis failed - ${e.message}")
      } finally {
        try {
          transcript?.close()
        } catch (e: Exception) {
          log.warn("Failed to close transcript", e)
        }
      }
    }
  }

  private fun buildFinalResult(
    gameScenario: String,
    players: List<String>,
    executionConfig: GameTheoryTaskExecutionConfigData?,
    structureAnalysis: String,
    payoffMatrix: String,
    nashEquilibria: String,
    dominantStrategies: String,
    paretoOptimal: String,
    repeatedGameAnalysis: String,
    recommendations: String,
    startTime: Long
  ): String = buildString {
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
    if (dominantStrategies.isNotEmpty()) {
      appendLine("## Dominant Strategies")
      appendLine(dominantStrategies.truncateForDisplay(maxOutputLengthPerField))
      appendLine()
    }
    if (paretoOptimal.isNotEmpty()) {
      appendLine("## Pareto Optimality")
      appendLine(paretoOptimal.truncateForDisplay(maxOutputLengthPerField))
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
    val executionConfig = this.executionConfig ?: throw IllegalStateException("Execution config is null")
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

    val effectiveTypeConfig = typeConfig ?: GameTheoryTypeConfig()
    return effectiveTypeConfig.structure_prompt_template
      .replace("{game_scenario}", gameScenario)
      .replace("{players}", players.joinToString("\n") { "- $it" })
      .replace("{strategies_section}", strategiesSection)
      .replace("{game_type}", executionConfig?.game_type ?: "non-cooperative")
      .replace("{context}", context)
  }
}


