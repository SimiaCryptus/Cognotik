package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class GameMechanicsDesignTask(
  orchestrationConfig: OrchestrationConfig,
  planTask: GameMechanicsDesignTaskExecutionConfigData?
) : AbstractTask<GameMechanicsDesignTask.GameMechanicsDesignTaskExecutionConfigData, TaskTypeConfig>(
  orchestrationConfig,
  planTask
) {

  class GameMechanicsDesignTaskExecutionConfigData(
    @Description("High-level game concept (e.g., 'Tower defense with resource management')")
    var game_concept: String? = null,
    @Description("Target audience: 'casual', 'hardcore', 'family', 'competitive'")
    var target_audience: String? = "casual",
    @Description("Core gameplay loop duration (e.g., '5 minutes', '30 minutes', '2 hours')")
    var core_loop_duration: String? = "15 minutes",
    @Description("Number of core mechanics to design (3-8 recommended)")
    var num_mechanics: Int = 5,
    @Description("Whether to include progression system design")
    var include_progression_system: Boolean = true,
    @Description("Whether to include economy system design")
    var include_economy_system: Boolean = true,
    @Description("Whether to include difficulty scaling analysis")
    var include_difficulty_scaling: Boolean = true,
    @Description("Balance focus: 'skill', 'luck', 'strategy', 'mixed'")
    var balance_focus: String? = "mixed",
    @Description("Number of simulated playtesting scenarios to run")
    var playtesting_scenarios: Int = 3,
    @Description("Whether to generate detailed tuning guide")
    var generate_tuning_guide: Boolean = true,
    @Description("Optional input files for context (e.g., design docs, reference games)")
    var related_files: List<String>? = null,
    task_description: String? = null,
    task_dependencies: List<String>? = null,
    state: TaskState? = TaskState.Pending,
  ) : TaskExecutionConfig(
    task_type = GameMechanicsDesign.name,
    task_description = task_description
      ?: "Design game mechanics for: ${game_concept?.take(50)}${if ((game_concept?.length ?: 0) > 50) "..." else ""}",
    task_dependencies = task_dependencies?.toMutableList(),
    state = state
  ), ValidatedObject {
    override fun validate(): String? {
      if (game_concept.isNullOrBlank()) {
        return "game_concept must not be blank"
      }
      if (num_mechanics < 3 || num_mechanics > 8) {
        return "num_mechanics must be between 3 and 8"
      }
      if (target_audience !in listOf("casual", "hardcore", "family", "competitive")) {
        return "target_audience must be one of: casual, hardcore, family, competitive"
      }
      if (balance_focus !in listOf("skill", "luck", "strategy", "mixed")) {
        return "balance_focus must be one of: skill, luck, strategy, mixed"
      }
      if (playtesting_scenarios < 1 || playtesting_scenarios > 10) {
        return "playtesting_scenarios must be between 1 and 10"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  // Non-generic wrapper classes for parser compatibility
  data class GameMechanicsList(
    @Description("List of game mechanics")
    var mechanics: List<GameMechanic> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? = ValidatedObject.validateFields(this)
  }

  data class MechanicInteractionsList(
    @Description("List of mechanic interactions")
    var interactions: List<MechanicInteraction> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? = ValidatedObject.validateFields(this)
  }

  data class ProgressionCurveList(
    @Description("List of progression levels")
    var levels: List<ProgressionCurve> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? = ValidatedObject.validateFields(this)
  }

  data class PlaytestingPredictionsList(
    @Description("List of playtesting predictions")
    var predictions: List<PlaytestingPrediction> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? = ValidatedObject.validateFields(this)
  }


  data class GameMechanic(
    @Description("Name of the mechanic")
    var name: String = "",
    @Description("Detailed description of how the mechanic works")
    var description: String = "",
    @Description("List of actions players can take with this mechanic")
    var player_actions: List<String> = emptyList(),
    @Description("How the game system responds to player actions")
    var system_response: String = "",
    @Description("Type of feedback: 'immediate', 'delayed', 'cumulative'")
    var feedback_type: String = "immediate",
    @Description("Level of skill expression: 'high', 'medium', 'low'")
    var skill_expression: String = "medium",
    @Description("Luck factor from 0.0 (no luck) to 1.0 (pure luck)")
    var luck_factor: Double = 0.0,
    @Description("How this mechanic interacts with others")
    var interactions: List<MechanicInteraction> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (name.isBlank()) return "name must not be blank"
      if (description.isBlank()) return "description must not be blank"
      if (player_actions.isEmpty()) return "player_actions must not be empty"
      if (system_response.isBlank()) return "system_response must not be blank"
      if (feedback_type !in listOf("immediate", "delayed", "cumulative")) {
        return "feedback_type must be one of: immediate, delayed, cumulative"
      }
      if (skill_expression !in listOf("high", "medium", "low")) {
        return "skill_expression must be one of: high, medium, low"
      }
      if (luck_factor < 0.0 || luck_factor > 1.0) {
        return "luck_factor must be between 0.0 and 1.0"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class MechanicInteraction(
    @Description("First mechanic in the interaction")
    var mechanic_a: String = "",
    @Description("Second mechanic in the interaction")
    var mechanic_b: String = "",
    @Description("Type of interaction: 'synergy', 'conflict', 'neutral'")
    var interaction_type: String = "neutral",
    @Description("Description of how the mechanics interact")
    var description: String = "",
    @Description("Any balance concerns from this interaction")
    var balance_concern: String? = null
  ) : ValidatedObject {
    override fun validate(): String? {
      if (mechanic_a.isBlank()) return "mechanic_a must not be blank"
      if (mechanic_b.isBlank()) return "mechanic_b must not be blank"
      if (interaction_type !in listOf("synergy", "conflict", "neutral")) {
        return "interaction_type must be one of: synergy, conflict, neutral"
      }
      if (description.isBlank()) return "description must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class ProgressionCurve(
    @Description("Level number")
    var level: Int = 1,
    @Description("Experience points required to reach this level")
    var experience_required: Int = 0,
    @Description("Features, abilities, or content unlocked at this level")
    var unlocks: List<String> = emptyList(),
    @Description("Difficulty multiplier at this level (1.0 = baseline)")
    var difficulty_multiplier: Double = 1.0,
    @Description("Estimated hours of gameplay to reach this level")
    var estimated_playtime_hours: Double = 0.0
  ) : ValidatedObject {
    override fun validate(): String? {
      if (level < 1) return "level must be at least 1"
      if (experience_required < 0) return "experience_required must be non-negative"
      if (difficulty_multiplier <= 0.0) return "difficulty_multiplier must be positive"
      if (estimated_playtime_hours < 0.0) return "estimated_playtime_hours must be non-negative"
      return ValidatedObject.validateFields(this)
    }
  }

  data class EconomySystem(
    @Description("List of resource types in the game")
    var resource_types: List<ResourceType> = emptyList(),
    @Description("How resources flow through the economy")
    var flow_analysis: String = "",
    @Description("Mechanisms to remove resources from circulation")
    var sink_mechanisms: List<String> = emptyList(),
    @Description("Balance assessment of the economy")
    var balance_assessment: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (resource_types.isEmpty()) return "resource_types must not be empty"
      if (flow_analysis.isBlank()) return "flow_analysis must not be blank"
      if (sink_mechanisms.isEmpty()) return "sink_mechanisms must not be empty"
      if (balance_assessment.isBlank()) return "balance_assessment must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class ResourceType(
    @Description("Name of the resource")
    var name: String = "",
    @Description("How players generate this resource")
    var generation_methods: List<String> = emptyList(),
    @Description("Rate of generation (per minute/hour)")
    var generation_rate: String = "",
    @Description("What this resource is used for")
    var consumption_uses: List<String> = emptyList(),
    @Description("Rate of consumption")
    var consumption_rate: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (name.isBlank()) return "name must not be blank"
      if (generation_methods.isEmpty()) return "generation_methods must not be empty"
      if (generation_rate.isBlank()) return "generation_rate must not be blank"
      if (consumption_uses.isEmpty()) return "consumption_uses must not be empty"
      if (consumption_rate.isBlank()) return "consumption_rate must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class BalanceMetrics(
    @Description("Variance in win rates (lower is better, 0.0-1.0)")
    var win_rate_variance: Double = 0.0,
    @Description("Diversity of viable strategies (higher is better, 0.0-1.0)")
    var strategy_diversity: Double = 0.0,
    @Description("How much skill affects outcomes (0-100)")
    var skill_expression_score: Double = 0.0,
    @Description("How much luck affects outcomes (0-100)")
    var luck_factor_score: Double = 0.0,
    @Description("Strategies that are too powerful")
    var dominant_strategies: List<String> = emptyList(),
    @Description("Number of viable alternative strategies")
    var viable_alternatives: Int = 0,
    @Description("Estimated skill ceiling: 'low', 'medium', 'high'")
    var estimated_skill_ceiling: String = "medium"
  ) : ValidatedObject {
    override fun validate(): String? {
      if (win_rate_variance < 0.0 || win_rate_variance > 1.0) {
        return "win_rate_variance must be between 0.0 and 1.0"
      }
      if (strategy_diversity < 0.0 || strategy_diversity > 1.0) {
        return "strategy_diversity must be between 0.0 and 1.0"
      }
      if (skill_expression_score < 0.0 || skill_expression_score > 100.0) {
        return "skill_expression_score must be between 0 and 100"
      }
      if (luck_factor_score < 0.0 || luck_factor_score > 100.0) {
        return "luck_factor_score must be between 0 and 100"
      }
      if (viable_alternatives < 0) return "viable_alternatives must be non-negative"
      if (estimated_skill_ceiling !in listOf("low", "medium", "high")) {
        return "estimated_skill_ceiling must be one of: low, medium, high"
      }
      return ValidatedObject.validateFields(this)
    }
  }

  data class PlaytestingPrediction(
    @Description("Scenario name or description")
    var scenario: String = "",
    @Description("Predicted engagement curve over time")
    var engagement_curve: String = "",
    @Description("Key moments that keep players engaged")
    var retention_points: List<String> = emptyList(),
    @Description("Potential sources of player frustration")
    var frustration_triggers: List<String> = emptyList(),
    @Description("Factors that encourage replay")
    var replayability_factors: List<String> = emptyList(),
    @Description("Overall assessment of this scenario")
    var assessment: String = ""
  ) : ValidatedObject {
    override fun validate(): String? {
      if (scenario.isBlank()) return "scenario must not be blank"
      if (engagement_curve.isBlank()) return "engagement_curve must not be blank"
      if (assessment.isBlank()) return "assessment must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  data class TuningParameters(
    @Description("Recommended difficulty settings")
    var difficulty_settings: Map<String, String> = emptyMap(),
    @Description("Reward multipliers for different scenarios")
    var reward_multipliers: Map<String, Double> = emptyMap(),
    @Description("Progression speed adjustments")
    var progression_speed: String = "",
    @Description("Economy balance adjustments")
    var economy_adjustments: List<String> = emptyList(),
    @Description("Additional tuning recommendations")
    var recommendations: List<String> = emptyList()
  ) : ValidatedObject {
    override fun validate(): String? {
      if (difficulty_settings.isEmpty()) return "difficulty_settings must not be empty"
      if (reward_multipliers.isEmpty()) return "reward_multipliers must not be empty"
      if (progression_speed.isBlank()) return "progression_speed must not be blank"
      return ValidatedObject.validateFields(this)
    }
  }

  override fun promptSegment(): String {
    return """
        GameMechanicsDesign - Generates comprehensive game mechanics with balance analysis.
          ** Specify `game_concept` (e.g., "Tower defense with resource management")
          ** Configure `target_audience`, `num_mechanics`, and `balance_focus`.
          ** Use this to:
             - Design core gameplay loops and mechanics.
             - Analyze synergies and conflicts between mechanics.
             - Create progression and economy systems.
             - Evaluate balance and simulate playtesting engagement.
             - Generate actionable tuning guides.
        """.trimIndent()
  }

  override fun run(
    agent: TaskOrchestrator,
    messages: List<String>,
    task: SessionTask,
    resultFn: (String) -> Unit,
    orchestrationConfig: OrchestrationConfig
  ) {

    val gameConcept = executionConfig?.game_concept
    if (gameConcept.isNullOrBlank()) {
      val errorMsg = "CONFIGURATION ERROR: No game concept specified"
      log.error(errorMsg)
      task.error(Exception(errorMsg))
      resultFn(errorMsg)
      return
    }
    val startTime = System.currentTimeMillis()
    log.info("Starting GameMechanicsDesignTask for concept: '${gameConcept.take(50)}'")


    // Validate configuration

    val executionConfig = this.executionConfig ?: return
    executionConfig.validate()?.let { errorMessage ->
      log.error("Configuration validation failed: $errorMessage")
      task.error(ValidatedObject.ValidationError(errorMessage, executionConfig))
      task.complete("VALIDATION ERROR: $errorMessage".renderMarkdown(true))
      resultFn("VALIDATION ERROR: $errorMessage")
      return
    }

    val api = defaultSmart ?: return

    val ui = task.ui
    val tabs = TabbedDisplay(task)
    val transcript = task.newUserFileStream(transcriptFile())

    task.ui.pool.submit {
      val overviewTask = task.newTask()
      tabs["Overview"] = overviewTask.placeholder

      val targetAudience = executionConfig.target_audience ?: "casual"
      val coreLoopDuration = executionConfig.core_loop_duration ?: "15 minutes"
      val numMechanics = executionConfig.num_mechanics
      val balanceFocus = executionConfig.balance_focus ?: "mixed"
      val playtestingScenarios = executionConfig.playtesting_scenarios
      try {

        overviewTask.add(
          buildString {
            appendLine("# Game Mechanics Design")
            appendLine()
            appendLine("**Game Concept:** $gameConcept")
            appendLine()
            appendLine("**Configuration:**")
            appendLine("- Target Audience: $targetAudience")
            appendLine("- Core Loop Duration: $coreLoopDuration")
            appendLine("- Number of Mechanics: $numMechanics")
            appendLine("- Balance Focus: $balanceFocus")
            appendLine("- Playtesting Scenarios: $playtestingScenarios")
            appendLine()
            appendLine("**Design Components:**")
            appendLine("- ✅ Core Mechanics")
            if (executionConfig.include_progression_system) appendLine("- ✅ Progression System")
            if (executionConfig.include_economy_system) appendLine("- ✅ Economy System")
            if (executionConfig.include_difficulty_scaling) appendLine("- ✅ Difficulty Scaling")
            appendLine("- ✅ Balance Analysis")
            appendLine("- ✅ Playtesting Predictions")
            if (executionConfig.generate_tuning_guide) appendLine("- ✅ Tuning Guide")
            appendLine()
            appendLine(
              "**Started:** ${
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
              }"
            )
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("**Status:** 🔄 Gathering context...")
          }.renderMarkdown(true)
        )
        transcript?.write(
          "# Game Mechanics Design\n\n**Game Concept:** $gameConcept\n\n**Started:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }\n\n---\n\n".toByteArray()
        )

        // Gather context
        log.debug("Gathering context from prior tasks and input files")
        val priorContext = getPriorCode(agent.executionState)
        val inputFileContext = getInputFileContent(executionConfig?.related_files, root)

        if (priorContext.isNotBlank() || inputFileContext.isNotBlank()) {
          transcript?.write(
            """
                    ## Context
                    <details>
                    <summary>Context Data</summary>
                    
                    ---
                    
                    """.trimIndent().toByteArray()
          )
          val contextTask = task.newTask()
          tabs["Context"] = contextTask.placeholder
          contextTask.add(
            buildString {
              appendLine("# Context")
              appendLine()
              if (inputFileContext.isNotBlank()) {
                appendLine("## Input Files")
                appendLine()
                appendLine(inputFileContext.truncateForDisplay())
                appendLine()
              }
              if (priorContext.isNotBlank()) {
                appendLine("## Prior Task Results")
                appendLine()
                appendLine(priorContext.truncateForDisplay())
              }
            }.renderMarkdown(true)
          )
          contextTask.complete()
        }

        overviewTask.add(
          buildString {
            appendLine()
            appendLine("✅ Context gathered")
            appendLine()
            appendLine("**Status:** 🔄 Designing core mechanics...")
          }.renderMarkdown(true)
        )

        // Step 1: Generate Core Mechanics
        log.info("Generating $numMechanics core mechanics")
        val mechanicsTask = task.newTask()
        tabs["Core Mechanics"] = mechanicsTask.placeholder

        mechanicsTask.add("## Core Mechanics\n\n🔄 Generating mechanics...".renderMarkdown(true))

        val mechanicsPrompt = buildString {
          appendLine("Design $numMechanics core gameplay mechanics for this game:")
          appendLine()
          appendLine("**Game Concept:** $gameConcept")
          appendLine("**Target Audience:** $targetAudience")
          appendLine("**Core Loop Duration:** $coreLoopDuration")
          appendLine("**Balance Focus:** $balanceFocus")
          appendLine()
          if (priorContext.isNotBlank()) {
            appendLine("**Context:**")
            appendLine(priorContext.take(2000))
            appendLine()
          }
          appendLine("For each mechanic, provide:")
          appendLine("1. A clear, memorable name")
          appendLine("2. Detailed description of how it works")
          appendLine("3. Specific player actions")
          appendLine("4. System response to those actions")
          appendLine("5. Feedback type (immediate/delayed/cumulative)")
          appendLine("6. Skill expression level (high/medium/low)")
          appendLine("7. Luck factor (0.0-1.0)")
          appendLine()
          appendLine("Design mechanics that:")
          appendLine("- Work together cohesively")
          appendLine("- Match the target audience")
          appendLine("- Fit the core loop duration")
          appendLine("- Align with the balance focus")
          appendLine("- Create interesting decisions")
        }

        val mechanicsParser = ParsedAgent(
          resultClass = GameMechanicsList::class.java,
          prompt = mechanicsPrompt.toString(),
          model = api,
          temperature = 0.7,
          name = "MechanicsGenerator",
          parsingChatter = defaultFast,
        )

        val mechanics = mechanicsParser.answer(listOf(mechanicsPrompt.toString())).obj.mechanics
        log.info("Generated ${mechanics.size} mechanics")

        mechanicsTask.add(
          buildString {
            appendLine("## Core Mechanics")
            appendLine()
            appendLine("✅ Generated ${mechanics.size} mechanics")
            appendLine()
            mechanics.forEachIndexed { index, mechanic ->
              appendLine("### ${index + 1}. ${mechanic.name}")
              appendLine()
              appendLine("**Description:** ${mechanic.description}")
              appendLine()
              appendLine("**Player Actions:**")
              mechanic.player_actions.forEach { appendLine("- $it") }
              appendLine()
              appendLine("**System Response:** ${mechanic.system_response}")
              appendLine()
              appendLine("**Properties:**")
              appendLine("- Feedback Type: ${mechanic.feedback_type}")
              appendLine("- Skill Expression: ${mechanic.skill_expression}")
              appendLine("- Luck Factor: ${String.format("%.1f", mechanic.luck_factor * 100)}%")
              appendLine()
              appendLine("---")
              appendLine()
            }
          }.renderMarkdown(true)
        )
        transcript?.write(buildString {
          appendLine("## Core Mechanics")
          appendLine()
          mechanics.forEachIndexed { index, mechanic ->
            appendLine("### ${index + 1}. ${mechanic.name}")
            appendLine()
            appendLine("**Description:** ${mechanic.description}")
            appendLine()
            appendLine("**Player Actions:**")
            mechanic.player_actions.forEach { appendLine("- $it") }
            appendLine()
            appendLine("**System Response:** ${mechanic.system_response}")
            appendLine()
            appendLine("**Properties:**")
            appendLine("- Feedback Type: ${mechanic.feedback_type}")
            appendLine("- Skill Expression: ${mechanic.skill_expression}")
            appendLine("- Luck Factor: ${String.format("%.1f", mechanic.luck_factor * 100)}%")
            appendLine()
            if (mechanic.interactions.isNotEmpty()) {
              appendLine("**Interactions:**")
              mechanic.interactions.forEach { interaction ->
                appendLine("- ${interaction.mechanic_b}: ${interaction.interaction_type} - ${interaction.description}")
              }
              appendLine()
            }
            appendLine("---")
            appendLine()
          }
        }.toByteArray())
        mechanicsTask.complete()

        // Step 2: Analyze Mechanic Interactions
        log.debug("Analyzing mechanic interactions")
        val interactionsTask = task.newTask()
        tabs["Interaction Matrix"] = interactionsTask.placeholder

        interactionsTask.add("## Mechanic Interactions\n\n🔄 Analyzing interactions...".renderMarkdown(true))

        val interactionsPrompt = buildString {
          appendLine("Analyze how these mechanics interact with each other:")
          appendLine()
          mechanics.forEach { mechanic ->
            appendLine("**${mechanic.name}:** ${mechanic.description}")
          }
          appendLine()
          appendLine("For each pair of mechanics, identify:")
          appendLine("1. Interaction type (synergy/conflict/neutral)")
          appendLine("2. Description of the interaction")
          appendLine("3. Any balance concerns")
          appendLine()
          appendLine("Focus on meaningful interactions that affect gameplay.")
        }

        val interactionsParser = ParsedAgent(
          resultClass = MechanicInteractionsList::class.java,
          prompt = interactionsPrompt.toString(),
          model = api,
          temperature = 0.6,
          name = "InteractionAnalyzer",
          parsingChatter = defaultFast,
        )

        val interactions = interactionsParser.answer(listOf(interactionsPrompt.toString())).obj.interactions

        interactionsTask.add(
          buildString {
            appendLine("## Mechanic Interactions")
            appendLine()
            appendLine("✅ Analyzed ${interactions.size} interactions")
            appendLine()
            appendLine("### Interaction Matrix")
            appendLine()
            val synergies = interactions.filter { it.interaction_type == "synergy" }
            val conflicts = interactions.filter { it.interaction_type == "conflict" }
            val neutral = interactions.filter { it.interaction_type == "neutral" }
            appendLine("- **Synergies:** ${synergies.size}")
            appendLine("- **Conflicts:** ${conflicts.size}")
            appendLine("- **Neutral:** ${neutral.size}")
            appendLine()
            if (synergies.isNotEmpty()) {
              appendLine("### Synergies")
              appendLine()
              synergies.forEach { interaction ->
                appendLine("#### ${interaction.mechanic_a} ↔ ${interaction.mechanic_b}")
                appendLine(interaction.description)
                if (interaction.balance_concern != null) {
                  appendLine()
                  appendLine("⚠️ **Balance Concern:** ${interaction.balance_concern}")
                }
                appendLine()
              }
            }
            if (conflicts.isNotEmpty()) {
              appendLine("### Conflicts")
              appendLine()
              conflicts.forEach { interaction ->
                appendLine("#### ${interaction.mechanic_a} ⚔ ${interaction.mechanic_b}")
                appendLine(interaction.description)
                if (interaction.balance_concern != null) {
                  appendLine()
                  appendLine("⚠️ **Balance Concern:** ${interaction.balance_concern}")
                }
                appendLine()
              }
            }
          }.renderMarkdown(true)
        )
        transcript?.write(buildString {
          appendLine("## Mechanic Interactions")
          appendLine()
          val synergies = interactions.filter { it.interaction_type == "synergy" }
          val conflicts = interactions.filter { it.interaction_type == "conflict" }
          val neutral = interactions.filter { it.interaction_type == "neutral" }
          appendLine("**Summary:** ${interactions.size} interactions analyzed")
          appendLine("- Synergies: ${synergies.size}")
          appendLine("- Conflicts: ${conflicts.size}")
          appendLine("- Neutral: ${neutral.size}")
          appendLine()
          if (synergies.isNotEmpty()) {
            appendLine("### Synergies")
            appendLine()
            synergies.forEach { interaction ->
              appendLine("#### ${interaction.mechanic_a} ↔ ${interaction.mechanic_b}")
              appendLine(interaction.description)
              if (interaction.balance_concern != null) {
                appendLine()
                appendLine("⚠️ **Balance Concern:** ${interaction.balance_concern}")
              }
              appendLine()
            }
          }
          if (conflicts.isNotEmpty()) {
            appendLine("### Conflicts")
            appendLine()
            conflicts.forEach { interaction ->
              appendLine("#### ${interaction.mechanic_a} ⚔ ${interaction.mechanic_b}")
              appendLine(interaction.description)
              if (interaction.balance_concern != null) {
                appendLine()
                appendLine("⚠️ **Balance Concern:** ${interaction.balance_concern}")
              }
              appendLine()
            }
          }
          if (neutral.isNotEmpty()) {
            appendLine("### Neutral Interactions")
            appendLine()
            neutral.forEach { interaction ->
              appendLine("#### ${interaction.mechanic_a} ↔ ${interaction.mechanic_b}")
              appendLine(interaction.description)
              appendLine()
            }
          }
          appendLine("---")
          appendLine()
        }.toByteArray())
        interactionsTask.complete()

        // Step 3: Progression System (if enabled)
        if (executionConfig.include_progression_system) {
          log.debug("Designing progression system")
          val progressionTask = task.newTask()
          tabs["Progression System"] = progressionTask.placeholder

          progressionTask.add("## Progression System\n\n🔄 Designing progression curve...".renderMarkdown(true))

          val progressionPrompt = buildString {
            appendLine("Design a progression system for this game:")
            appendLine()
            appendLine("**Game Concept:** $gameConcept")
            appendLine("**Target Audience:** $targetAudience")
            appendLine("**Core Loop Duration:** $coreLoopDuration")
            appendLine()
            appendLine("**Mechanics:**")
            mechanics.forEach { appendLine("- ${it.name}") }
            appendLine()
            appendLine("Create a progression curve with 10-15 levels that:")
            appendLine("1. Gradually introduces mechanics")
            appendLine("2. Scales difficulty appropriately")
            appendLine("3. Provides meaningful unlocks")
            appendLine("4. Matches the target audience's commitment level")
            appendLine("5. Maintains engagement throughout")
            appendLine()
            appendLine("For each level, specify:")
            appendLine("- Experience required")
            appendLine("- Unlocks (new mechanics, features, content)")
            appendLine("- Difficulty multiplier")
            appendLine("- Estimated playtime to reach")
          }

          val progressionParser = ParsedAgent(
            resultClass = ProgressionCurveList::class.java,
            prompt = progressionPrompt.toString(),
            model = api,
            temperature = 0.6,
            name = "ProgressionDesigner",
            parsingChatter = defaultFast,
          )

          val progression = progressionParser.answer(listOf(progressionPrompt.toString())).obj.levels

          progressionTask.add(
            buildString {
              appendLine("## Progression System")
              appendLine()
              appendLine("✅ Designed ${progression.size} levels")
              appendLine()
              appendLine("| Level | XP Required | Difficulty | Playtime | Unlocks |")
              appendLine("|-------|-------------|------------|----------|---------|")
              progression.forEach { level ->
                appendLine(
                  "| ${level.level} | ${level.experience_required} | ${
                    String.format(
                      "%.1fx",
                      level.difficulty_multiplier
                    )
                  } | ${String.format("%.1fh", level.estimated_playtime_hours)} | ${level.unlocks.size} |"
                )
              }
              appendLine()
              appendLine("### Detailed Progression")
              appendLine()
              progression.forEach { level ->
                appendLine("#### Level ${level.level}")
                appendLine()
                appendLine("- **XP Required:** ${level.experience_required}")
                appendLine("- **Difficulty:** ${String.format("%.1fx", level.difficulty_multiplier)}")
                appendLine(
                  "- **Estimated Playtime:** ${
                    String.format(
                      "%.1f",
                      level.estimated_playtime_hours
                    )
                  } hours"
                )
                appendLine()
                if (level.unlocks.isNotEmpty()) {
                  appendLine("**Unlocks:**")
                  level.unlocks.forEach { appendLine("- $it") }
                  appendLine()
                }
              }
            }.renderMarkdown(true)
          )
          transcript?.write(buildString {
            appendLine("## Progression System")
            appendLine()
            appendLine("**Summary:** ${progression.size} levels designed")
            appendLine()
            appendLine("| Level | XP Required | Difficulty | Playtime | Unlocks |")
            appendLine("|-------|-------------|------------|----------|---------|")
            progression.forEach { level ->
              appendLine(
                "| ${level.level} | ${level.experience_required} | ${
                  String.format(
                    "%.1fx",
                    level.difficulty_multiplier
                  )
                } | ${String.format("%.1fh", level.estimated_playtime_hours)} | ${level.unlocks.size} |"
              )
            }
            appendLine()
            appendLine("### Detailed Progression")
            appendLine()
            progression.forEach { level ->
              appendLine("#### Level ${level.level}")
              appendLine()
              appendLine("- **XP Required:** ${level.experience_required}")
              appendLine("- **Difficulty:** ${String.format("%.1fx", level.difficulty_multiplier)}")
              appendLine(
                "- **Estimated Playtime:** ${
                  String.format(
                    "%.1f",
                    level.estimated_playtime_hours
                  )
                } hours"
              )
              appendLine()
              if (level.unlocks.isNotEmpty()) {
                appendLine("**Unlocks:**")
                level.unlocks.forEach { appendLine("- $it") }
                appendLine()
              }
            }
            appendLine("---")
            appendLine()
          }.toByteArray())
          progressionTask.complete()
        }

        // Step 4: Economy System (if enabled)
        if (executionConfig.include_economy_system) {
          log.debug("Designing economy system")
          val economyTask = task.newTask()
          tabs["Economy System"] = economyTask.placeholder

          economyTask.add("## Economy System\n\n🔄 Designing resource economy...".renderMarkdown(true))

          val economyPrompt = buildString {
            appendLine("Design an economy system for this game:")
            appendLine()
            appendLine("**Game Concept:** $gameConcept")
            appendLine("**Core Loop Duration:** $coreLoopDuration")
            appendLine()
            appendLine("**Mechanics:**")
            mechanics.forEach { appendLine("- ${it.name}: ${it.description}") }
            appendLine()
            appendLine("Create an economy with 3-5 resource types that:")
            appendLine("1. Support the core mechanics")
            appendLine("2. Create meaningful choices")
            appendLine("3. Have balanced generation and consumption")
            appendLine("4. Include sink mechanisms to prevent inflation")
            appendLine("5. Encourage strategic resource management")
            appendLine()
            appendLine("For each resource:")
            appendLine("- Name and purpose")
            appendLine("- Generation methods and rates")
            appendLine("- Consumption uses and rates")
            appendLine()
            appendLine("Also provide:")
            appendLine("- Flow analysis (how resources move through the economy)")
            appendLine("- Sink mechanisms")
            appendLine("- Balance assessment")
          }

          val economyParser = ParsedAgent(
            resultClass = EconomySystem::class.java,
            prompt = economyPrompt.toString(),
            model = api,
            temperature = 0.6,
            name = "EconomyDesigner",
            parsingChatter = defaultFast,
          )

          val economy = economyParser.answer(listOf(economyPrompt.toString())).obj

          economyTask.add(
            buildString {
              appendLine("## Economy System")
              appendLine()
              appendLine("✅ Designed ${economy.resource_types.size} resource types")
              appendLine()
              appendLine("### Resources")
              appendLine()
              economy.resource_types.forEach { resource ->
                appendLine("#### ${resource.name}")
                appendLine()
                appendLine("**Generation:**")
                resource.generation_methods.forEach { appendLine("- $it") }
                appendLine("- Rate: ${resource.generation_rate}")
                appendLine()
                appendLine("**Consumption:**")
                resource.consumption_uses.forEach { appendLine("- $it") }
                appendLine("- Rate: ${resource.consumption_rate}")
                appendLine()
              }
              appendLine("### Flow Analysis")
              appendLine()
              appendLine(economy.flow_analysis)
              appendLine()
              appendLine("### Sink Mechanisms")
              appendLine()
              economy.sink_mechanisms.forEach { appendLine("- $it") }
              appendLine()
              appendLine("### Balance Assessment")
              appendLine()
              appendLine(economy.balance_assessment)
            }.renderMarkdown(true)
          )
          transcript?.write(buildString {
            appendLine("## Economy System")
            appendLine()
            appendLine("**Summary:** ${economy.resource_types.size} resource types designed")
            appendLine()
            appendLine("### Resources")
            appendLine()
            economy.resource_types.forEach { resource ->
              appendLine("#### ${resource.name}")
              appendLine()
              appendLine("**Generation:**")
              resource.generation_methods.forEach { appendLine("- $it") }
              appendLine("- Rate: ${resource.generation_rate}")
              appendLine()
              appendLine("**Consumption:**")
              resource.consumption_uses.forEach { appendLine("- $it") }
              appendLine("- Rate: ${resource.consumption_rate}")
              appendLine()
            }
            appendLine("### Flow Analysis")
            appendLine()
            appendLine(economy.flow_analysis)
            appendLine()
            appendLine("### Sink Mechanisms")
            appendLine()
            economy.sink_mechanisms.forEach { appendLine("- $it") }
            appendLine()
            appendLine("### Balance Assessment")
            appendLine()
            appendLine(economy.balance_assessment)
            appendLine()
            appendLine("---")
            appendLine()
          }.toByteArray())
          economyTask.complete()
        }

        // Step 5: Balance Analysis
        log.debug("Performing balance analysis")
        val balanceTask = task.newTask()
        tabs["Balance Analysis"] = balanceTask.placeholder

        balanceTask.add("## Balance Analysis\n\n🔄 Analyzing game balance...".renderMarkdown(true))

        val balancePrompt = buildString {
          appendLine("Perform a comprehensive balance analysis:")
          appendLine()
          appendLine("**Game Concept:** $gameConcept")
          appendLine("**Balance Focus:** $balanceFocus")
          appendLine()
          appendLine("**Mechanics:**")
          mechanics.forEach { mechanic ->
            appendLine("- ${mechanic.name} (Skill: ${mechanic.skill_expression}, Luck: ${mechanic.luck_factor})")
          }
          appendLine()
          appendLine("**Interactions:**")
          interactions.filter { it.interaction_type != "neutral" }.forEach { interaction ->
            appendLine("- ${interaction.mechanic_a} ↔ ${interaction.mechanic_b}: ${interaction.interaction_type}")
          }
          appendLine()
          appendLine("Analyze:")
          appendLine("1. Win rate variance (should be low for fair games)")
          appendLine("2. Strategy diversity (should be high for interesting games)")
          appendLine("3. Skill expression score (0-100)")
          appendLine("4. Luck factor score (0-100)")
          appendLine("5. Dominant strategies (if any)")
          appendLine("6. Number of viable alternatives")
          appendLine("7. Estimated skill ceiling (low/medium/high)")
          appendLine()
          appendLine("Consider the target audience and balance focus in your analysis.")
        }

        val balanceParser = ParsedAgent(
          resultClass = BalanceMetrics::class.java,
          prompt = balancePrompt.toString(),
          model = api,
          temperature = 0.5,
          name = "BalanceAnalyzer",
          parsingChatter = defaultFast,
        )

        val balance = balanceParser.answer(listOf(balancePrompt.toString())).obj

        balanceTask.add(
          buildString {
            appendLine("## Balance Analysis")
            appendLine()
            appendLine("✅ Analysis complete")
            appendLine()
            appendLine("### Metrics")
            appendLine()
            appendLine("| Metric | Value | Assessment |")
            appendLine("|--------|-------|------------|")
            appendLine(
              "| Win Rate Variance | ${String.format("%.2f", balance.win_rate_variance)} | ${
                if (balance.win_rate_variance < 0.2) "✅ Good" else if (balance.win_rate_variance < 0.4) "⚠️ Fair" else "❌ Poor"
              } |"
            )
            appendLine(
              "| Strategy Diversity | ${String.format("%.2f", balance.strategy_diversity)} | ${
                if (balance.strategy_diversity > 0.7) "✅ Excellent" else if (balance.strategy_diversity > 0.5) "⚠️ Good" else "❌ Limited"
              } |"
            )
            appendLine(
              "| Skill Expression | ${
                String.format(
                  "%.0f",
                  balance.skill_expression_score
                )
              }/100 | - |"
            )
            appendLine("| Luck Factor | ${String.format("%.0f", balance.luck_factor_score)}/100 | - |")
            appendLine("| Viable Strategies | ${balance.viable_alternatives} | - |")
            appendLine("| Skill Ceiling | ${balance.estimated_skill_ceiling} | - |")
            appendLine()
            if (balance.dominant_strategies.isNotEmpty()) {
              appendLine("### ⚠️ Dominant Strategies")
              appendLine()
              balance.dominant_strategies.forEach { appendLine("- $it") }
              appendLine()
            }
            appendLine("### Recommendations")
            appendLine()
            if (balance.win_rate_variance > 0.3) {
              appendLine("- ⚠️ High win rate variance suggests balance issues")
            }
            if (balance.strategy_diversity < 0.5) {
              appendLine("- ⚠️ Low strategy diversity may lead to stale gameplay")
            }
            if (balance.dominant_strategies.isNotEmpty()) {
              appendLine("- ⚠️ Address dominant strategies to improve balance")
            }
            if (balance.viable_alternatives < 3) {
              appendLine("- ⚠️ Consider adding more viable strategic options")
            }
          }.renderMarkdown(true)
        )
        transcript?.write(buildString {
          appendLine("## Balance Analysis")
          appendLine()
          appendLine("### Metrics")
          appendLine()
          appendLine("| Metric | Value | Assessment |")
          appendLine("|--------|-------|------------|")
          appendLine(
            "| Win Rate Variance | ${String.format("%.2f", balance.win_rate_variance)} | ${
              if (balance.win_rate_variance < 0.2) "✅ Good" else if (balance.win_rate_variance < 0.4) "⚠️ Fair" else "❌ Poor"
            } |"
          )
          appendLine(
            "| Strategy Diversity | ${String.format("%.2f", balance.strategy_diversity)} | ${
              if (balance.strategy_diversity > 0.7) "✅ Excellent" else if (balance.strategy_diversity > 0.5) "⚠️ Good" else "❌ Limited"
            } |"
          )
          appendLine("| Skill Expression | ${String.format("%.0f", balance.skill_expression_score)}/100 | - |")
          appendLine("| Luck Factor | ${String.format("%.0f", balance.luck_factor_score)}/100 | - |")
          appendLine("| Viable Strategies | ${balance.viable_alternatives} | - |")
          appendLine("| Skill Ceiling | ${balance.estimated_skill_ceiling} | - |")
          appendLine()
          if (balance.dominant_strategies.isNotEmpty()) {
            appendLine("### ⚠️ Dominant Strategies")
            appendLine()
            balance.dominant_strategies.forEach { appendLine("- $it") }
            appendLine()
          }
          appendLine("### Recommendations")
          appendLine()
          if (balance.win_rate_variance > 0.3) {
            appendLine("- ⚠️ High win rate variance suggests balance issues")
          }
          if (balance.strategy_diversity < 0.5) {
            appendLine("- ⚠️ Low strategy diversity may lead to stale gameplay")
          }
          if (balance.dominant_strategies.isNotEmpty()) {
            appendLine("- ⚠️ Address dominant strategies to improve balance")
          }
          if (balance.viable_alternatives < 3) {
            appendLine("- ⚠️ Consider adding more viable strategic options")
          }
          appendLine()
          appendLine("---")
          appendLine()
        }.toByteArray())
        balanceTask.complete()

        // Step 6: Playtesting Predictions
        log.debug("Generating playtesting predictions")
        val playtestingTask = task.newTask()
        tabs["Playtesting"] = playtestingTask.placeholder

        playtestingTask.add("## Playtesting Predictions\n\n🔄 Simulating player scenarios...".renderMarkdown(true))

        val playtestingPrompt = buildString {
          appendLine("Predict player behavior and engagement for $playtestingScenarios different scenarios:")
          appendLine()
          appendLine("**Game Concept:** $gameConcept")
          appendLine("**Target Audience:** $targetAudience")
          appendLine("**Core Loop Duration:** $coreLoopDuration")
          appendLine()
          appendLine("**Mechanics:**")
          mechanics.forEach { appendLine("- ${it.name}") }
          appendLine()
          appendLine("For each scenario, predict:")
          appendLine("1. Engagement curve over time")
          appendLine("2. Key retention points (what keeps players engaged)")
          appendLine("3. Frustration triggers (what might cause players to quit)")
          appendLine("4. Replayability factors (what encourages replay)")
          appendLine("5. Overall assessment")
          appendLine()
          appendLine("Consider different player types:")
          appendLine("- New players (first session)")
          appendLine("- Intermediate players (10+ sessions)")
          appendLine("- Expert players (mastery level)")
        }

        val playtestingParser = ParsedAgent(
          resultClass = PlaytestingPredictionsList::class.java,
          prompt = playtestingPrompt.toString(),
          model = api,
          temperature = 0.7,
          name = "PlaytestingPredictor",
          parsingChatter = defaultFast,
        )

        val playtesting = playtestingParser.answer(listOf(playtestingPrompt.toString())).obj.predictions

        playtestingTask.add(
          buildString {
            appendLine("## Playtesting Predictions")
            appendLine()
            appendLine("✅ Simulated ${playtesting.size} scenarios")
            appendLine()
            playtesting.forEach { prediction ->
              appendLine("### ${prediction.scenario}")
              appendLine()
              appendLine("**Engagement Curve:**")
              appendLine(prediction.engagement_curve)
              appendLine()
              if (prediction.retention_points.isNotEmpty()) {
                appendLine("**Retention Points:**")
                prediction.retention_points.forEach { appendLine("- ✅ $it") }
                appendLine()
              }
              if (prediction.frustration_triggers.isNotEmpty()) {
                appendLine("**Frustration Triggers:**")
                prediction.frustration_triggers.forEach { appendLine("- ⚠️ $it") }
                appendLine()
              }
              if (prediction.replayability_factors.isNotEmpty()) {
                appendLine("**Replayability Factors:**")
                prediction.replayability_factors.forEach { appendLine("- 🔄 $it") }
                appendLine()
              }
              appendLine("**Assessment:**")
              appendLine(prediction.assessment)
              appendLine()
              appendLine("---")
              appendLine()
            }
          }.renderMarkdown(true)
        )
        transcript?.write(buildString {
          appendLine("## Playtesting Predictions")
          appendLine()
          appendLine("**Summary:** ${playtesting.size} scenarios simulated")
          appendLine()
          playtesting.forEach { prediction ->
            appendLine("### ${prediction.scenario}")
            appendLine()
            appendLine("**Engagement Curve:**")
            appendLine(prediction.engagement_curve)
            appendLine()
            if (prediction.retention_points.isNotEmpty()) {
              appendLine("**Retention Points:**")
              prediction.retention_points.forEach { appendLine("- ✅ $it") }
              appendLine()
            }
            if (prediction.frustration_triggers.isNotEmpty()) {
              appendLine("**Frustration Triggers:**")
              prediction.frustration_triggers.forEach { appendLine("- ⚠️ $it") }
              appendLine()
            }
            if (prediction.replayability_factors.isNotEmpty()) {
              appendLine("**Replayability Factors:**")
              prediction.replayability_factors.forEach { appendLine("- 🔄 $it") }
              appendLine()
            }
            appendLine("**Assessment:**")
            appendLine(prediction.assessment)
            appendLine()
            appendLine("---")
            appendLine()
          }
        }.toByteArray())
        playtestingTask.complete()

        // Step 7: Tuning Guide (if enabled)
        if (executionConfig.generate_tuning_guide) {
          log.debug("Generating tuning guide")
          val tuningTask = task.newTask()
          tabs["Tuning Guide"] = tuningTask.placeholder

          tuningTask.add("## Tuning Guide\n\n🔄 Generating tuning parameters...".renderMarkdown(true))

          val tuningPrompt = buildString {
            appendLine("Generate a comprehensive tuning guide:")
            appendLine()
            appendLine("**Game Concept:** $gameConcept")
            appendLine("**Target Audience:** $targetAudience")
            appendLine()
            appendLine("**Balance Metrics:**")
            appendLine("- Win Rate Variance: ${balance.win_rate_variance}")
            appendLine("- Strategy Diversity: ${balance.strategy_diversity}")
            appendLine("- Skill Expression: ${balance.skill_expression_score}")
            appendLine()
            appendLine("Provide:")
            appendLine("1. Difficulty settings for different player skill levels")
            appendLine("2. Reward multipliers for various scenarios")
            appendLine("3. Progression speed recommendations")
            appendLine("4. Economy adjustments to improve balance")
            appendLine("5. Additional tuning recommendations")
            appendLine()
            appendLine("Make recommendations specific and actionable.")
          }

          val tuningParser = ParsedAgent(
            resultClass = TuningParameters::class.java,
            prompt = tuningPrompt.toString(),
            model = api,
            temperature = 0.6,
            name = "TuningGuideGenerator",
            parsingChatter = defaultFast,
          )

          val tuning = tuningParser.answer(listOf(tuningPrompt.toString())).obj

          tuningTask.add(
            buildString {
              appendLine("## Tuning Guide")
              appendLine()
              appendLine("✅ Guide generated")
              appendLine()
              appendLine("### Difficulty Settings")
              appendLine()
              tuning.difficulty_settings.forEach { (level, setting) ->
                appendLine("- **$level:** $setting")
              }
              appendLine()
              appendLine("### Reward Multipliers")
              appendLine()
              tuning.reward_multipliers.forEach { (scenario, multiplier) ->
                appendLine("- **$scenario:** ${String.format("%.2fx", multiplier)}")
              }
              appendLine()
              appendLine("### Progression Speed")
              appendLine()
              appendLine(tuning.progression_speed)
              appendLine()
              if (tuning.economy_adjustments.isNotEmpty()) {
                appendLine("### Economy Adjustments")
                appendLine()
                tuning.economy_adjustments.forEach { appendLine("- $it") }
                appendLine()
              }
              if (tuning.recommendations.isNotEmpty()) {
                appendLine("### Additional Recommendations")
                appendLine()
                tuning.recommendations.forEach { appendLine("- $it") }
              }
            }.renderMarkdown(true)
          )
          transcript?.write(buildString {
            appendLine("## Tuning Guide")
            appendLine()
            appendLine("### Difficulty Settings")
            appendLine()
            tuning.difficulty_settings.forEach { (level, setting) ->
              appendLine("- **$level:** $setting")
            }
            appendLine()
            appendLine("### Reward Multipliers")
            appendLine()
            tuning.reward_multipliers.forEach { (scenario, multiplier) ->
              appendLine("- **$scenario:** ${String.format("%.2fx", multiplier)}")
            }
            appendLine()
            appendLine("### Progression Speed")
            appendLine()
            appendLine(tuning.progression_speed)
            appendLine()
            if (tuning.economy_adjustments.isNotEmpty()) {
              appendLine("### Economy Adjustments")
              appendLine()
              tuning.economy_adjustments.forEach { appendLine("- $it") }
              appendLine()
            }
            if (tuning.recommendations.isNotEmpty()) {
              appendLine("### Additional Recommendations")
              appendLine()
              tuning.recommendations.forEach { appendLine("- $it") }
              appendLine()
            }
            appendLine("---")
            appendLine()
          }.toByteArray())
          tuningTask.complete()
        }

        // Build final result
        val duration = System.currentTimeMillis() - startTime
        val finalResult = buildString {
          appendLine("# Game Mechanics Design: $gameConcept")
          appendLine()
          appendLine("## Summary")
          appendLine()
          appendLine("- **Core Mechanics:** ${mechanics.size}")
          appendLine("- **Mechanic Interactions:** ${interactions.size}")
          appendLine(
            "- **Balance Score:** ${
              String.format(
                "%.0f",
                balance.skill_expression_score
              )
            }/100 skill, ${String.format("%.0f", balance.luck_factor_score)}/100 luck"
          )
          appendLine("- **Strategy Diversity:** ${String.format("%.1f", balance.strategy_diversity * 100)}%")
          appendLine("- **Viable Strategies:** ${balance.viable_alternatives}")
          appendLine()
          appendLine("## Key Mechanics")
          appendLine()
          mechanics.take(3).forEach { mechanic ->
            appendLine("### ${mechanic.name}")
            appendLine(mechanic.description.take(200))
            appendLine()
          }
          appendLine()
          appendLine("*See detailed tabs for complete analysis*")
        }

        log.info(
          "GameMechanicsDesignTask completed: concept='$gameConcept', " +
              "duration=${duration}ms, mechanics=${mechanics.size}, " +
              "output_size=${finalResult.length} chars"
        )

        overviewTask.add(
          buildString {
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## ✅ Design Complete")
            appendLine()
            appendLine("**Total Time:** ${duration / 1000.0}s")
            appendLine()
            appendLine("**Components Generated:**")
            appendLine("- Core Mechanics: ${mechanics.size}")
            appendLine("- Interactions: ${interactions.size}")
            if (executionConfig.include_progression_system) appendLine("- Progression Levels: Designed")
            if (executionConfig.include_economy_system) appendLine("- Economy System: Designed")
            appendLine("- Balance Analysis: Complete")
            appendLine("- Playtesting: ${playtesting.size} scenarios")
            if (executionConfig.generate_tuning_guide) appendLine("- Tuning Guide: Generated")
            appendLine()
            appendLine(
              "**Completed:** ${
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
              }"
            )
          }.renderMarkdown(true)
        )
        transcript?.write(
          "\n\n## Design Complete\n\n**Total Time:** ${duration / 1000.0}s\n\n**Completed:** ${
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
          }\n".toByteArray()
        )
        overviewTask.complete()

        val relativePath = "game_mechanics_design_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val (transcriptLink, _) = Pair(task.linkTo(relativePath), task.resolveUserFile(relativePath))
        task.complete(
          ("Game mechanics design completed in ${duration / 1000}s. " +
              "View detailed design: <a href='$transcriptLink' target='_blank'>markdown</a> " +
              "<a href='${transcriptLink.removeSuffix(".md")}.html' target='_blank'>html</a> " +
              "<a href='${transcriptLink.removeSuffix(".md")}.pdf' target='_blank'>pdf</a>").renderMarkdown(true)
        )
        resultFn(
          """
                ## Game Design Complete: $gameConcept
                * Mechanics: ${mechanics.size}
                * Balance: ${String.format("%.0f", balance.skill_expression_score)}% Skill / ${
            String.format(
              "%.0f",
              balance.luck_factor_score
            )
          }% Luck
                * Strategy Diversity: ${String.format("%.1f", balance.strategy_diversity * 100)}%
                * Full report saved to: `$relativePath`
            """.trimIndent()
        )

      } catch (e: Exception) {
        val duration = System.currentTimeMillis() - startTime
        // Triple Log Rule
        task.error(e)
        log.error("GameMechanicsDesignTask failed after ${duration}ms for concept: $gameConcept", e)
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
        overviewTask.add(
          buildString {
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## ❌ Error Occurred")
            appendLine()
            appendLine("**Error:** ${e.message}")
            appendLine()
            appendLine("**Type:** ${e.javaClass.simpleName}")
          }.renderMarkdown(true)
        )
        overviewTask.complete()

        val errorOutput = buildString {
          appendLine("# Error in Game Mechanics Design")
          appendLine()
          appendLine("**Game Concept:** $gameConcept")
          appendLine()
          appendLine("**Error:** ${e.message}")
        }
        resultFn(errorOutput.toString())
      } finally {
        transcript?.close()
      }
    }
  }


  companion object {
    private val log: Logger = LoggerFactory.getLogger(GameMechanicsDesignTask::class.java)

    @JvmStatic
    val GameMechanicsDesign = TaskType(
      name = "GameMechanicsDesign",
      category = "Games",
      taskClass = GameMechanicsDesignTask::class.java,
      executionConfigClass = GameMechanicsDesignTaskExecutionConfigData::class.java,
      taskSettingsClass = TaskTypeConfig::class.java,
      description = "Generate comprehensive game mechanics with balance analysis",
      tooltipHtml = """
                        Designs complete game mechanics systems with detailed analysis.
                        <ul>
                          <li>Generates core gameplay mechanics from high-level concepts</li>
                          <li>Analyzes mechanic interactions and synergies</li>
                          <li>Designs progression and economy systems</li>
                          <li>Evaluates balance, fairness, and difficulty curves</li>
                          <li>Predicts player behavior through simulated playtesting</li>
                          <li>Provides tuning parameters and recommendations</li>
                          <li>Useful for game design prototyping, balancing, and competitive design</li>
                        </ul>
                      """,
    )
  }
}