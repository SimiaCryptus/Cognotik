# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/GameEconomyTask.kt

```
package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.nio.file.FileSystems
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GameEconomyTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GameEconomyTaskExecutionConfigData?
) : AbstractTask<GameEconomyTask.GameEconomyTaskExecutionConfigData, GameEconomyTask.GameEconomyTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxOutputLengthPerField = 10000

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameEconomyTask::class.java)
        @JvmStatic val GameEconomy = TaskType(
            name = "GameEconomy",
            category = "Games",
            taskClass = GameEconomyTask::class.java,
            executionConfigClass = GameEconomyTaskExecutionConfigData::class.java,
            taskSettingsClass = GameEconomyTypeConfig::class.java,
            description = "Design complete game economic systems with progression and monetization",
            tooltipHtml = """
                        Designs comprehensive game economy systems with balanced progression.
                        <ul>
                          <li>Creates multi-resource economic systems with generation and consumption</li>
                          <li>Designs progression curves with experience and level systems</li>
                          <li>Builds skill trees and talent systems</li>
                          <li>Creates loot tables with balanced drop rates</li>
                          <li>Designs monetization strategies without pay-to-win</li>
                          <li>Implements engagement hooks (daily rewards, seasonal content, battle passes)</li>
                          <li>Forecasts economy health and player progression</li>
                          <li>Provides balance recommendations and adjustment strategies</li>
                          <li>Useful for game design, economy balancing, and monetization planning</li>
                        </ul>
                      """,
        )
    }

    // Data structures
    data class Resource(
        val name: String = "",
        val type: String = "currency",
        val generation_sources: List<Source> = emptyList(),
        val consumption_uses: List<Use> = emptyList(),
        val storage_limit: Int? = null,
        val exchange_rates: Map<String, Double> = emptyMap(),
        val sink_mechanisms: List<String> = emptyList()
    )

    data class Source(
        val activity: String = "",
        val amount_per_activity: Int = 0,
        val frequency: String = "",
        val scaling: String = ""
    )

    data class Use(
        val purpose: String = "",
        val amount_required: Int = 0,
        val frequency: String = "",
        val value_proposition: String = ""
    )

    data class ProgressionSystem(
        val levels: Int = 0,
        val experience_curve: List<Int> = emptyList(),
        val unlocks_per_level: Map<String, List<String>> = emptyMap(),
        val estimated_time_to_max: String = ""
    )

    data class LootTable(
        val name: String = "",
        val drops: List<LootDrop> = emptyList(),
        val total_weight: Int = 0
    )

    data class LootDrop(
        val item: String = "",
        val rarity: String = "",
        val weight: Int = 0,
        val drop_rate: Double = 0.0,
        val scaling: String = ""
    )

    data class MonetizationStrategy(
        val model: String = "",
        val optional_purchases: List<Purchase> = emptyList(),
        val cosmetics: List<Cosmetic> = emptyList(),
        val battle_pass: BattlePass? = null,
        val pay_to_win_risk: String = "low"
    )

    data class Purchase(
        val name: String = "",
        val price: Double = 0.0,
        val value_proposition: String = "",
        val type: String = "",
        val perceived_value: String = ""
    )

    data class Cosmetic(
        val name: String = "",
        val price: Double = 0.0,
        val category: String = "",
        val rarity: String = ""
    )

    data class BattlePass(
        val duration_days: Int = 0,
        val price: Double = 0.0,
        val free_tier_rewards: List<String> = emptyList(),
        val premium_tier_rewards: List<String> = emptyList(),
        val estimated_completion_hours: Int = 0
    )

    data class EngagementHook(
        val name: String = "",
        val type: String = "",
        val frequency: String = "",
        val reward_structure: String = "",
        val retention_impact: String = ""
    )

    data class EconomyForecast(
        val month: Int = 0,
        val projected_player_level: Double = 0.0,
        val resource_abundance: Map<String, Double> = emptyMap(),
        val inflation_rate: Double = 0.0,
        val economy_health: String = "",
        val recommended_adjustments: List<String> = emptyList()
    )

    data class GameEconomyConfig(
        val resources: List<Resource> = emptyList(),
        val progression_system: ProgressionSystem? = null,
        val loot_tables: List<LootTable> = emptyList(),
        val monetization: MonetizationStrategy? = null,
        val engagement_hooks: List<EngagementHook> = emptyList(),
        val forecasts: List<EconomyForecast> = emptyList(),
        val balance_recommendations: List<String> = emptyList()
    )
    class GameEconomyTypeConfig(
        var resourcePrompt: String = """
            You are an expert game economy designer. Your task is to design a comprehensive resource system for a game.
            ## Game Information:
            **Title:** {game_title}
            **Type:** {game_type}
            **Progression Style:** {progression_style}
            ## Design Requirements:
            - Number of resources: {num_resources}
            - Include crafting: {include_crafting}
            - Include trading: {include_trading}
            {context}
            ## Resource System Design Instructions:
            Design {num_resources} distinct resource types that work together to create a balanced economy.
            For each resource, specify:
            1. **Resource Identity**:
               - Name and type (currency, material, experience, premium)
               - Purpose and role in the economy
               - Thematic fit with game type
            2. **Generation Sources**:
               - How players acquire this resource
               - Amount per activity
               - Frequency of acquisition
               - Scaling with player progression
            3. **Consumption Uses**:
               - What players spend this resource on
               - Amount required for each use
               - Frequency of spending
               - Value proposition (why spend it?)
            4. **Storage and Limits**:
               - Storage capacity (if any)
               - Rationale for limits
               - Overflow mechanics
            5. **Exchange Rates**:
               - Conversion rates to other resources
               - Trading mechanics (if enabled)
               - Market dynamics
            6. **Sink Mechanisms**:
               - How excess resources are removed from economy
               - Inflation prevention
               - Long-term balance
            Ensure the resource system:
            - Creates meaningful choices
            - Avoids single dominant strategy
            - Provides multiple progression paths
            - Balances short-term and long-term goals
            - Supports the chosen monetization model
            Generate the complete resource system design now:
        """.trimIndent(),
        var progressionPrompt: String = """
            Based on the resource system above, design a comprehensive progression system for {game_title}.
            Create a progression system with:
            - {num_progression_tiers} levels/tiers
            - Experience curve (XP required per level)
            - Unlock schedule (what unlocks at each level)
            - Milestone rewards
            - Estimated time to reach max level
            For the experience curve, use a {progression_style} progression style.
            Consider:
            - Early game should feel rewarding (faster progression)
            - Mid game should establish rhythm
            - Late game should provide long-term goals
            - Balance between casual and hardcore players
            If skill trees are enabled ({include_skill_tree}), design:
            - Number of skill branches
            - Skills per branch
            - Synergies between skills
            - Respec mechanics
            Generate the progression system design now:
        """.trimIndent(),
        var lootPrompt: String = """
            Based on the resource and progression systems above, design a loot and reward system for {game_title}.
            Create loot tables for different content types:
            - Common enemies/activities
            - Elite enemies/challenges
            - Boss encounters
            - Quest rewards
            - Achievement rewards
            For each loot table, specify:
            - Item types and rarities (common, rare, epic, legendary)
            - Drop rates and weights
            - Scaling with difficulty/level
            - Pity systems or bad luck protection
            Design reward structures for:
            - Quest completion
            - Achievement unlocks
            - Milestone rewards
            - First-time bonuses
            Ensure the loot system:
            - Feels rewarding at all progression stages
            - Provides clear upgrade paths
            - Avoids excessive randomness frustration
            - Balances common vs rare rewards
            Generate the loot and reward system design now:
        """.trimIndent(),
        var monetizationPrompt: String = """
            Based on the complete economy design above, create a monetization strategy for {game_title}.
            Monetization Model: {monetization_model}
            Design monetization that:
            - Respects player experience (avoid pay-to-win)
            - Provides value for money
            - Offers optional purchases
            - Maintains game balance
            Include:
            1. **Optional Purchases**:
               - Cosmetic items (skins, emotes, effects)
               - Convenience items (inventory space, fast travel)
               - Time savers (XP boosts, resource doublers)
               - Price points and perceived value
            2. **Battle Pass** (if enabled: {include_battle_pass}):
               - Duration and price
               - Free tier rewards
               - Premium tier rewards
               - Estimated completion time
               - Value proposition
            3. **Cosmetics**:
               - Categories (character, weapon, mount, etc.)
               - Rarity tiers
               - Acquisition methods
               - Pricing strategy
            4. **Pay-to-Win Risk Assessment**:
               - Analyze each purchase type
               - Identify potential balance issues
               - Recommend safeguards
            Generate the monetization strategy now:
        """.trimIndent(),
        var engagementPrompt: String = """
            Based on the complete economy design above, create engagement systems for {game_title}.
            Design engagement hooks that encourage regular play without feeling manipulative:
            1. **Daily Rewards** (if enabled: {include_daily_rewards}):
               - Reward structure
               - Streak bonuses
               - Catch-up mechanics
               - Value scaling
            2. **Seasonal Content** (if enabled: {include_seasonal_content}):
               - Season duration
               - Seasonal themes
               - Exclusive rewards
               - Seasonal progression
            3. **Events**:
               - Event types (limited-time, recurring, special)
               - Event frequency
               - Event rewards
               - Participation incentives
            4. **Retention Mechanics**:
               - Login bonuses
               - Comeback rewards
               - Social features
               - Guild/clan systems
            For each engagement system, specify:
            - Frequency and duration
            - Reward structure
            - Expected retention impact
            - Balance with core gameplay
            Generate the engagement systems design now:
        """.trimIndent(),
        var forecastPrompt: String = """
            Based on the complete economy design above, generate a {forecast_months}-month forecast for {game_title}.
            For each month, project:
            1. **Player Progression**:
               - Average player level
               - Percentage reaching endgame
               - Skill tree completion
            2. **Resource Abundance**:
               - Average holdings per resource type
               - Resource velocity (generation vs consumption)
               - Wealth distribution
            3. **Economy Health**:
               - Inflation rate
               - Sink effectiveness
               - Balance issues
               - Health status (healthy, inflated, deflated)
            4. **Recommended Adjustments**:
               - Resource generation tweaks
               - Sink mechanism adjustments
               - Progression pacing changes
               - Loot table modifications
            Consider different player archetypes:
            - Casual players (1-2 hours/day)
            - Regular players (3-4 hours/day)
            - Hardcore players (6+ hours/day)
            Generate the {forecast_months}-month economy forecast now:
        """.trimIndent(),
        var balancePrompt: String = """
            Based on the complete economy design and forecast above, generate a comprehensive balance report for {game_title}.
            Analyze:
            1. **Resource Balance**:
               - Generation vs consumption rates
               - Storage limits appropriateness
               - Exchange rate fairness
               - Sink mechanism effectiveness
            2. **Progression Balance**:
               - XP curve appropriateness
               - Unlock pacing
               - Skill tree balance
               - Time-to-max-level reasonableness
            3. **Loot Balance**:
               - Drop rate fairness
               - Rarity distribution
               - Reward scaling
               - Pity system effectiveness
            4. **Monetization Balance**:
               - Pay-to-win risk assessment
               - Value proposition analysis
               - Price point appropriateness
               - Cosmetic vs power balance
            5. **Engagement Balance**:
               - Daily commitment requirements
               - FOMO (fear of missing out) risk
               - Burnout prevention
               - Casual vs hardcore balance
            6. **Overall Recommendations**:
               - Critical balance issues
               - Suggested adjustments
               - Testing priorities
               - Monitoring metrics
            Generate the comprehensive balance report now:
        """.trimIndent(),
        var summaryPrompt: String = """
            Based on all the analysis above, provide a structured summary of the game economy design.
            Extract and organize:
            - Key resources and their roles
            - Progression system overview
            - Loot system highlights
            - Monetization approach
            - Engagement systems
            - Economy health projections
            - Critical balance recommendations
            Provide this in a clear, structured format suitable for game designers and stakeholders.
          """.trimIndent()
    ) : TaskTypeConfig()


    class GameEconomyTaskExecutionConfigData(
        @Description("The title of the game")
        var game_title: String? = null,
        @Description("Type of game: RPG, strategy, idle, multiplayer")
        var game_type: String? = "RPG",
        @Description("Progression style: linear, branching, open")
        var progression_style: String? = "linear",
        @Description("Number of resource types (2-10)")
        var num_resources: Int = 3,
        @Description("Number of progression tiers/levels (5-100)")
        var num_progression_tiers: Int = 50,
        @Description("Whether to include a skill tree system")
        var include_skill_tree: Boolean = true,
        @Description("Whether to include crafting system")
        var include_crafting: Boolean = false,
        @Description("Whether to include player trading")
        var include_trading: Boolean = false,
        @Description("Monetization model: free-to-play, premium, subscription")
        var monetization_model: String? = "free-to-play",
        @Description("Whether to include daily rewards")
        var include_daily_rewards: Boolean = true,
        @Description("Whether to include seasonal content")
        var include_seasonal_content: Boolean = true,
        @Description("Whether to include battle pass system")
        var include_battle_pass: Boolean = true,
        @Description("Number of months to forecast (3-12)")
        var forecast_months: Int = 6,
        @Description("Whether to generate detailed balance report")
        var generate_balance_report: Boolean = true,
        @Description("Additional context or design constraints")
        var additional_context: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        var input_files: List<String>? = null,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : TaskExecutionConfig(
        task_type = GameEconomy.name,
        task_description = task_description ?: "Design game economy for: ${game_title?.take(1000)}",
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (game_title.isNullOrBlank()) {
                return "game_title must not be null or blank"
            }
            if (game_type.isNullOrBlank()) {
                return "game_type must not be null or blank"
            }
            if (progression_style.isNullOrBlank()) {
                return "progression_style must not be null or blank"
            }
            if (num_resources < 2 || num_resources > 10) {
                return "num_resources must be between 2 and 10"
            }
            if (num_progression_tiers < 5 || num_progression_tiers > 100) {
                return "num_progression_tiers must be between 5 and 100"
            }
            if (monetization_model.isNullOrBlank()) {
                return "monetization_model must not be null or blank"
            }
            if (forecast_months < 3 || forecast_months > 12) {
                return "forecast_months must be between 3 and 12"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment(): String {
        return """
GameEconomy - Design complete game economic systems with progression and monetization
  ** Specify the game title and type (RPG, strategy, idle, multiplayer)
  ** Define progression style (linear, branching, open)
  ** Configure number of resources (2-10) and progression tiers (5-100)
  ** Optionally include skill trees, crafting, and trading systems
  ** Choose monetization model (free-to-play, premium, subscription)
  ** Optionally include daily rewards, seasonal content, and battle passes
  ** Generate economy forecasts for 3-12 months
  ** Optionally generate detailed balance reports
  ** Useful for:
     - Game design and balancing
     - Economy system design
     - Monetization strategy
     - Player progression planning
     - Engagement system design
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

        val gameTitle = executionConfig?.game_title
        if (gameTitle.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No game title specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }
        val transcript = task.transcript()

        val ui = task.ui
        val api = defaultSmart ?: return
        // Create tabbed display for organized output
        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = task.newTask()
        tabs["Overview"] = overviewTask.placeholder
        task.ui.pool.submit {
            try {
                log.info("Starting GameEconomy task for: $gameTitle")
                val toInput = { it: String -> messages + listOf(getInputFileCode(), it).filter { it.isNotBlank() } }



            transcript?.write("# Game Economy Design: $gameTitle\n\n".toByteArray())
            transcript?.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )

            val overviewBuffer = overviewTask.add(
                """
            |## Game Economy Design
            |
            |**Game:** $gameTitle
            |
            |**Type:** ${executionConfig.game_type}
            |
            |**Progression Style:** ${executionConfig.progression_style}
            |
            |**Status:** 🔄 Initializing economy design...
        """.trimMargin().renderMarkdown()
            )
            transcript?.write(
                """
        |## Game Economy Design
        |
        |**Game:** $gameTitle
        |
        |**Type:** ${executionConfig.game_type}
        |
        |**Progression Style:** ${executionConfig.progression_style}
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

                val contextTask = task.newTask()
                tabs["Context"] = contextTask.placeholder
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
                transcript?.write(
                    "\n## Context\n<details><summary>Prior Context</summary>\n\n$priorContext\n</details>\n"
                        .toByteArray()
                )
                task.update()
            }

            if (executionConfig.additional_context?.isNotBlank() == true) {
                contextBuilder.append("## Additional Context\n\n")
                contextBuilder.append(executionConfig.additional_context)
                contextBuilder.append("\n\n")
            }

            // Update overview
            overviewBuffer?.setLength(0)
            overviewBuffer?.append(
                """
            |## Game Economy Design
            |
            |**Game:** $gameTitle
            |
            |**Type:** ${executionConfig.game_type}
            |
            |**Progression Style:** ${executionConfig.progression_style}
            |
            |**Status:** 🔄 Designing resource system...
        """.trimMargin().renderMarkdown()
            )
            overviewTask.update()

            // Step 1: Design resource system
            var stepStartTime = System.currentTimeMillis()
            log.debug("Designing resource system")
            val resourceTask = task.newTask()
            tabs["Resources"] = resourceTask.placeholder
            val resourceBuffer = resourceTask.add(

                "## Resource System\n\n🔄 Designing resource types and flows...".renderMarkdown()
            )

                val resourcePrompt = (typeConfig?.resourcePrompt ?: "")
                    .replace("{game_title}", gameTitle ?: "")
                    .replace("{game_type}", executionConfig.game_type ?: "")
                    .replace("{progression_style}", executionConfig.progression_style ?: "")
                    .replace("{num_resources}", executionConfig.num_resources.toString())
                    .replace("{include_crafting}", executionConfig.include_crafting.toString())
                    .replace("{include_trading}", executionConfig.include_trading.toString())
                    .replace("{context}", contextBuilder.toString())

                val chatAgent = ChatAgent(
                prompt = resourcePrompt,
                model = api,
                temperature = 0.3
            )

            val resourceAnalysis = chatAgent.answer(toInput(resourcePrompt))
            log.info("Resource system designed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${resourceAnalysis.length} characters")
            transcript?.write(
                """
        |## Resource System Design
        |
        |$resourceAnalysis
        |
        |""".trimMargin().toByteArray()
            )

            resourceBuffer?.setLength(0)
            resourceBuffer?.append(
                """
            |## Resource System Design
            |
            |✅ Design complete
            |
            |$resourceAnalysis
            """.trimMargin().renderMarkdown()
            )
            resourceTask.complete()
            resourceTask.update()

            // Step 2: Design progression system
            stepStartTime = System.currentTimeMillis()
            log.debug("Designing progression system")
            val progressionTask = task.newTask()
            tabs["Progression"] = progressionTask.placeholder
            val progressionBuffer = progressionTask.add(


                "## Progression System\n\n🔄 Designing level curves and unlocks...".renderMarkdown()
            )

                val progressionPrompt = (typeConfig?.progressionPrompt ?: "")
                    .replace("{game_title}", gameTitle ?: "")
                    .replace("{num_progression_tiers}", executionConfig.num_progression_tiers.toString())
                    .replace("{progression_style}", executionConfig.progression_style ?: "")
                    .replace("{include_skill_tree}", executionConfig.include_skill_tree.toString())

            val progressionAnalysis = chatAgent.answer(toInput(progressionPrompt))
            log.info("Progression system designed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${progressionAnalysis.length} characters")
            transcript?.write(
                """
        |## Progression System Design
        |
        |$progressionAnalysis
        |
        |""".trimMargin().toByteArray()
            )

            progressionBuffer?.setLength(0)
            progressionBuffer?.append(
                """
            |## Progression System Design
            |
            |✅ Design complete
            |
            |$progressionAnalysis
            """.trimMargin().renderMarkdown()
            )
            progressionTask.complete()
            progressionTask.update()

            // Step 3: Design loot and reward system
            stepStartTime = System.currentTimeMillis()
            log.debug("Designing loot system")
            val lootTask = task.newTask()
            tabs["Loot & Rewards"] = lootTask.placeholder
            val lootBuffer = lootTask.add(


                "## Loot & Reward System\n\n🔄 Designing loot tables and drop rates...".renderMarkdown()
            )

                val lootPrompt = (typeConfig?.lootPrompt ?: "")
                    .replace("{game_title}", gameTitle ?: "")

            val lootAnalysis = chatAgent.answer(toInput(lootPrompt))
            log.info("Loot system designed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${lootAnalysis.length} characters")
            transcript?.write(
                """
        |## Loot & Reward System Design
        |
        |$lootAnalysis
        |
        |""".trimMargin().toByteArray()
            )

            lootBuffer?.setLength(0)
            lootBuffer?.append(
                """
            |## Loot & Reward System Design
            |
            |✅ Design complete
            |
            |$lootAnalysis
            """.trimMargin().renderMarkdown()
            )
            lootTask.complete()
            lootTask.update()

            // Step 4: Design monetization strategy
            stepStartTime = System.currentTimeMillis()
            log.debug("Designing monetization strategy")
            val monetizationTask = task.newTask()
            tabs["Monetization"] = monetizationTask.placeholder
            val monetizationBuffer = monetizationTask.add(


                "## Monetization Strategy\n\n🔄 Designing monetization approach...".renderMarkdown()
            )

                val monetizationPrompt = (typeConfig?.monetizationPrompt ?: "")
                    .replace("{game_title}", gameTitle ?: "")
                    .replace("{monetization_model}", executionConfig.monetization_model ?: "")
                    .replace("{include_battle_pass}", executionConfig.include_battle_pass.toString())

            val monetizationAnalysis = chatAgent.answer(toInput(monetizationPrompt))
            log.info("Monetization strategy designed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${monetizationAnalysis.length} characters")
            transcript?.write(
                """
        |## Monetization Strategy Design
        |
        |$monetizationAnalysis
        |
        |""".trimMargin().toByteArray()
            )

            monetizationBuffer?.setLength(0)
            monetizationBuffer?.append(
                """
            |## Monetization Strategy Design
            |
            |✅ Design complete
            |
            |$monetizationAnalysis
            """.trimMargin().renderMarkdown()
            )
            monetizationTask.complete()
            monetizationTask.update()

            // Step 5: Design engagement systems
            stepStartTime = System.currentTimeMillis()
            log.debug("Designing engagement systems")
            val engagementTask = task.newTask()
            tabs["Engagement"] = engagementTask.placeholder
            val engagementBuffer = engagementTask.add(


                "## Engagement Systems\n\n🔄 Designing retention mechanics...".renderMarkdown()
            )

                val engagementPrompt = (typeConfig?.engagementPrompt ?: "")
                    .replace("{game_title}", gameTitle ?: "")
                    .replace("{include_daily_rewards}", executionConfig.include_daily_rewards.toString())
                    .replace("{include_seasonal_content}", executionConfig.include_seasonal_content.toString())

            val engagementAnalysis = chatAgent.answer(toInput(engagementPrompt))
            log.info("Engagement systems designed in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${engagementAnalysis.length} characters")
            transcript?.write(
                """
        |## Engagement Systems Design
        |
        |$engagementAnalysis
        |
        |""".trimMargin().toByteArray()
            )

            engagementBuffer?.setLength(0)
            engagementBuffer?.append(
                """
            |## Engagement Systems Design
            |
            |✅ Design complete
            |
            |$engagementAnalysis
            """.trimMargin().renderMarkdown()
            )
            engagementTask.complete()
            engagementTask.update()

            // Step 6: Generate economy forecast
            stepStartTime = System.currentTimeMillis()
            log.debug("Generating economy forecast")
            val forecastTask = task.newTask()
            tabs["Forecast"] = forecastTask.placeholder
            val forecastBuffer = forecastTask.add(


                "## Economy Forecast\n\n🔄 Projecting economy health...".renderMarkdown()
            )

                val forecastPrompt = (typeConfig?.forecastPrompt ?: "")
                    .replace("{game_title}", gameTitle ?: "")
                    .replace("{forecast_months}", executionConfig.forecast_months.toString())

            val forecastAnalysis = chatAgent.answer(toInput(forecastPrompt))
            log.info("Economy forecast generated in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${forecastAnalysis.length} characters")
            transcript?.write(
                """
        |## Economy Forecast (${executionConfig.forecast_months} months)
        |
        |$forecastAnalysis
        |
        |""".trimMargin().toByteArray()
            )

            forecastBuffer?.setLength(0)
            forecastBuffer?.append(
                """
            |## Economy Forecast (${executionConfig.forecast_months} months)
            |
            |✅ Forecast complete
            |
            |$forecastAnalysis
            """.trimMargin().renderMarkdown()
            )
            forecastTask.complete()
            forecastTask.update()

            // Step 7: Generate balance report (if enabled)
            var balanceReport = ""
            if (executionConfig.generate_balance_report) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Generating balance report")
                val balanceTask = task.newTask()
                tabs["Balance Report"] = balanceTask.placeholder
                val balanceBuffer = balanceTask.add(


                    "## Balance Report\n\n🔄 Analyzing economy balance...".renderMarkdown()
                )

                val balancePrompt = (typeConfig?.balancePrompt ?: "")
                    .replace("{game_title}", gameTitle ?: "")

                balanceReport = chatAgent.answer(toInput(balancePrompt))
                log.info("Balance report generated in ${System.currentTimeMillis() - stepStartTime}ms. Length: ${balanceReport.length} characters")
                transcript?.write(
                    """
          |## Balance Report
          |
          |$balanceReport
          |
          |""".trimMargin().toByteArray()
                )

                balanceBuffer?.setLength(0)
                balanceBuffer?.append(
                    """
              |## Balance Report
              |
              |✅ Report complete
              |
              |$balanceReport
              """.trimMargin().renderMarkdown()
                )
                balanceTask.complete()
                balanceTask.update()
            }

            // Step 8: Generate structured summary using ParsedAgent
            stepStartTime = System.currentTimeMillis()
            log.debug("Generating structured summary")
            val summaryTask = task.newTask()
            tabs["Summary"] = summaryTask.placeholder
            val summaryBuffer = summaryTask.add(


                "## Summary\n\n🔄 Generating comprehensive summary...".renderMarkdown()
            )

                val summaryPrompt = (typeConfig?.summaryPrompt ?: "")
                    .replace("{game_title}", gameTitle ?: "")

            val parsedAgent = ParsedAgent(
                resultClass = GameEconomyConfig::class.java,
                prompt = summaryPrompt,
                model = api,
                temperature = 0.2,
                parsingChatter = defaultFast,
            )

            val gameEconomy = parsedAgent.answer(toInput(summaryPrompt)).obj
            log.info("Structured summary generated in ${System.currentTimeMillis() - stepStartTime}ms")
            transcript?.write(
                """
        |## Game Economy Design Summary
        |
        |### Resources (${gameEconomy.resources.size})
        |${gameEconomy.resources.joinToString("\n") { "- **${it.name}** (${it.type})" }}
        |
        |### Progression System
        |${gameEconomy.progression_system?.let { "- Levels: ${it.levels}\n- Estimated time to max: ${it.estimated_time_to_max}" } ?: "Not specified"}
        |
        |### Loot Tables (${gameEconomy.loot_tables.size})
        |${gameEconomy.loot_tables.joinToString("\n") { "- ${it.name}: ${it.drops.size} drops" }}
        |
        |### Monetization
        |${gameEconomy.monetization?.let { "- Model: ${it.model}\n- Pay-to-win risk: ${it.pay_to_win_risk}" } ?: "Not specified"}
        |
        |### Engagement Hooks (${gameEconomy.engagement_hooks.size})
        |${gameEconomy.engagement_hooks.joinToString("\n") { "- ${it.name} (${it.type})" }}
        |
        |### Balance Recommendations
        |${gameEconomy.balance_recommendations.joinToString("\n") { "- $it" }}
        |
        |""".trimMargin().toByteArray()
            )

            summaryBuffer?.setLength(0)
            summaryBuffer?.append(
                """
            |## Game Economy Design Summary
            |
            |✅ Summary complete
            |
            |### Resources (${gameEconomy.resources.size})
            |${gameEconomy.resources.joinToString("\n") { "- **${it.name}** (${it.type})" }}
            |
            |### Progression System
            |${gameEconomy.progression_system?.let { "- Levels: ${it.levels}\n- Estimated time to max: ${it.estimated_time_to_max}" } ?: "Not specified"}
            |
            |### Loot Tables (${gameEconomy.loot_tables.size})
            |${gameEconomy.loot_tables.joinToString("\n") { "- ${it.name}: ${it.drops.size} drops" }}
            |
            |### Monetization
            |${gameEconomy.monetization?.let { "- Model: ${it.model}\n- Pay-to-win risk: ${it.pay_to_win_risk}" } ?: "Not specified"}
            |
            |### Engagement Hooks (${gameEconomy.engagement_hooks.size})
            |${gameEconomy.engagement_hooks.joinToString("\n") { "- ${it.name} (${it.type})" }}
            |
            |### Balance Recommendations
            |${gameEconomy.balance_recommendations.joinToString("\n") { "- $it" }}
            """.trimMargin().renderMarkdown()
            )
            summaryTask.complete()
            summaryTask.update()

            // Update overview with completion
            overviewBuffer?.setLength(0)
            overviewBuffer?.append(
                """
            |## Game Economy Design
            |
            |**Game:** $gameTitle
            |
            |**Type:** ${executionConfig.game_type}
            |
            |**Progression Style:** ${executionConfig.progression_style}
            |
            |**Status:** ✅ Design complete
            |
            |**Completed:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}
        """.trimMargin().renderMarkdown()
            )
            overviewTask.complete()
            overviewTask.update()

            // Build final result
            val finalResult = buildString {
                appendLine("# Game Economy Design: $gameTitle")
                appendLine()
                appendLine("## Game Type")
                appendLine(executionConfig.game_type)
                appendLine()
                appendLine("## Progression Style")
                appendLine(executionConfig.progression_style)
                appendLine()

                if (resourceAnalysis.isNotEmpty()) {
                    appendLine("## Resource System")
                    appendLine(resourceAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                if (progressionAnalysis.isNotEmpty()) {
                    appendLine("## Progression System")
                    appendLine(progressionAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                if (lootAnalysis.isNotEmpty()) {
                    appendLine("## Loot & Rewards")
                    appendLine(lootAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                if (monetizationAnalysis.isNotEmpty()) {
                    appendLine("## Monetization Strategy")
                    appendLine(monetizationAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                if (engagementAnalysis.isNotEmpty()) {
                    appendLine("## Engagement Systems")
                    appendLine(engagementAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                if (forecastAnalysis.isNotEmpty()) {
                    appendLine("## Economy Forecast")
                    appendLine(forecastAnalysis.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                if (balanceReport.isNotEmpty()) {
                    appendLine("## Balance Report")
                    appendLine(balanceReport.truncateForDisplay(maxOutputLengthPerField))
                    appendLine()
                }

                appendLine("---")
                appendLine("**Design completed in ${(System.currentTimeMillis() - startTime) / 1000}s**")
            }

            val duration = System.currentTimeMillis() - startTime
            val summary = "Game economy design completed for: $gameTitle"
            log.info("$summary (duration: ${duration}ms, resources: ${gameEconomy.resources.size}, levels: ${gameEconomy.progression_system?.levels ?: 0})")
            transcript?.write("\n---\n".toByteArray())
            transcript?.write("**Design completed in ${duration / 1000}s**\n".toByteArray())
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
                log.error("GameEconomy task failed after ${duration}ms for: $gameTitle", e)
                overviewTask.add(
                    """
                |## Game Economy Design
                |
                |**Status:** ❌ Design Failed
                |
                |**Error:** ${e.message}
                """.trimMargin().renderMarkdown()
                )
                overviewTask.update()
                transcript?.write("\n---\n**ERROR:** ${e.message}\n".toByteArray())
                transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                task.error(e)
                task.safeComplete("Design failed: ${e.message}", log)
                resultFn("ERROR: Game economy design failed - ${e.message}")
            } finally {
                transcript?.close()
            }
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
                val content = file.readText()
                "# $relativePath\n\n```\n$content\n```"
            } catch (e: Throwable) {
                log.warn("Error reading file: $relativePath", e)
                ""
            }
        }















}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/GameLevelDesignTask.kt

```
package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class GameLevelDesignTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GameLevelDesignTaskExecutionConfigData?
) : AbstractTask<GameLevelDesignTask.GameLevelDesignTaskExecutionConfigData, GameLevelDesignTask.GameLevelDesignTaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    class GameLevelDesignTaskExecutionConfigData(
        @Description("The name of the level")
        var level_name: String? = null,

        @Description("The type of game (e.g., 'platformer', 'shooter', 'puzzle', 'rpg', 'metroidvania')")
        var game_type: String = "platformer",

        @Description("Target duration in minutes")
        var level_duration_minutes: Int = 10,

        @Description("Difficulty tier")
        var difficulty_tier: String = "medium",

        @Description("Number of players (1 for single-player, 2+ for multiplayer)")
        var player_count: Int = 1,

        @Description("Level theme (e.g., 'forest', 'dungeon', 'city', 'space_station')")
        var level_theme: String = "dungeon",

        @Description("Whether to include a boss encounter")
        var include_boss_encounter: Boolean = false,

        @Description("Whether to include puzzle elements")
        var include_puzzles: Boolean = true,

        @Description("Whether to include secret areas")
        var include_secrets: Boolean = true,

        @Description("Whether to include collectibles")
        var include_collectibles: Boolean = true,

        @Description("Pacing style")
        var pacing_style: String = "escalating",

        @Description("Whether to generate difficulty variants")
        var generate_difficulty_variants: Boolean = false,

        @Description("Whether to include ASCII visual layout")
        var include_visual_layout: Boolean = true,

        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input context")
        var input_files: List<String>? = null,

        task_description: String? = null,
        task_dependencies: MutableList<String>? = null,
        state: TaskState? = TaskState.Pending
    ) : TaskExecutionConfig(
        task_type = GameLevelDesign.name,
        task_description = task_description ?: "Generate game level design: '$level_name'",
        task_dependencies = task_dependencies,
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (level_name.isNullOrBlank()) {
                return "level_name must not be null or blank"
            }
            if (level_duration_minutes < 5 || level_duration_minutes > 30) {
                return "level_duration_minutes must be between 5 and 30, got: $level_duration_minutes"
            }
            if (player_count < 1 || player_count > 8) {
                return "player_count must be between 1 and 8, got: $player_count"
            }
            val validDifficulties = setOf("tutorial", "easy", "medium", "hard", "expert")
            if (difficulty_tier.lowercase() !in validDifficulties) {
                return "difficulty_tier must be one of: ${validDifficulties.joinToString(", ")}, got: $difficulty_tier"
            }
            val validPacing = setOf("steady", "escalating", "varied")
            if (pacing_style.lowercase() !in validPacing) {
                return "pacing_style must be one of: ${validPacing.joinToString(", ")}, got: $pacing_style"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class GameLevel(
        @Description("The level name")
        val name: String = "",
        @Description("The level theme")
        val theme: String = "",
        @Description("The level layout")
        val layout: LevelLayout = LevelLayout(),
        @Description("Encounters in the level")
        val encounters: List<Encounter> = emptyList(),
        @Description("Collectibles in the level")
        val collectibles: List<Collectible> = emptyList(),
        @Description("Secret areas in the level")
        val secrets: List<Secret> = emptyList(),
        @Description("Pacing curve for the level")
        val pacing_curve: PacingCurve = PacingCurve(),
        @Description("Estimated duration in minutes")
        val estimated_duration_minutes: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (name.isBlank()) return "name must not be blank"
            if (theme.isBlank()) return "theme must not be blank"
            if (estimated_duration_minutes <= 0) return "estimated_duration_minutes must be positive"
            return ValidatedObject.validateFields(this)
        }
    }

    data class LevelLayout(
        @Description("Width of the level in units")
        val width: Int = 0,
        @Description("Height of the level in units")
        val height: Int = 0,
        @Description("Zones in the level")
        val zones: List<Zone> = emptyList(),
        @Description("Connections between zones")
        val connections: List<ZoneConnection> = emptyList(),
        @Description("ASCII representation of the level")
        val ascii_representation: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (width <= 0) return "width must be positive"
            if (height <= 0) return "height must be positive"
            if (zones.isEmpty()) return "zones must not be empty"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Zone(
        @Description("Unique zone identifier")
        val zone_id: String = "",
        @Description("Zone name")
        val name: String = "",
        @Description("Zone type")
        val type: String = "",
        @Description("Zone description")
        val description: String = "",
        @Description("Encounter IDs in this zone")
        val encounters: List<String> = emptyList(),
        @Description("Exit zone IDs")
        val exits: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (zone_id.isBlank()) return "zone_id must not be blank"
            if (name.isBlank()) return "name must not be blank"
            if (type.isBlank()) return "type must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class ZoneConnection(
        @Description("Source zone ID")
        val from_zone: String = "",
        @Description("Destination zone ID")
        val to_zone: String = "",
        @Description("Connection type")
        val connection_type: String = "",
        @Description("Whether the connection is locked")
        val locked: Boolean = false,
        @Description("Unlock requirement")
        val unlock_requirement: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (from_zone.isBlank()) return "from_zone must not be blank"
            if (to_zone.isBlank()) return "to_zone must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Encounter(
        @Description("Unique encounter identifier")
        val encounter_id: String = "",
        @Description("Encounter type")
        val type: String = "",
        @Description("Difficulty level")
        val difficulty: String = "",
        @Description("Enemy/obstacle composition")
        val composition: List<String> = emptyList(),
        @Description("Recommended player level")
        val recommended_level: Int = 1,
        @Description("Rewards for completing")
        val rewards: List<Reward> = emptyList(),
        @Description("Tactics for defeating/solving")
        val tactics: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (encounter_id.isBlank()) return "encounter_id must not be blank"
            if (type.isBlank()) return "type must not be blank"
            if (difficulty.isBlank()) return "difficulty must not be blank"
            if (recommended_level < 1) return "recommended_level must be at least 1"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Reward(
        @Description("Reward type")
        val type: String = "",
        @Description("Reward name")
        val name: String = "",
        @Description("Reward value")
        val value: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (type.isBlank()) return "type must not be blank"
            if (name.isBlank()) return "name must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Collectible(
        @Description("Unique collectible identifier")
        val id: String = "",
        @Description("Collectible name")
        val name: String = "",
        @Description("Collectible type")
        val type: String = "",
        @Description("Location in the level")
        val location: String = "",
        @Description("Visibility level")
        val visibility: String = "",
        @Description("Value or importance")
        val value: Int = 0
    ) : ValidatedObject {
        override fun validate(): String? {
            if (id.isBlank()) return "id must not be blank"
            if (name.isBlank()) return "name must not be blank"
            if (type.isBlank()) return "type must not be blank"
            if (location.isBlank()) return "location must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class Secret(
        @Description("Unique secret identifier")
        val id: String = "",
        @Description("Secret name")
        val name: String = "",
        @Description("Location in the level")
        val location: String = "",
        @Description("How to discover the secret")
        val discovery_method: String = "",
        @Description("Reward for finding")
        val reward: String = "",
        @Description("Difficulty to find")
        val difficulty_to_find: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (id.isBlank()) return "id must not be blank"
            if (name.isBlank()) return "name must not be blank"
            if (location.isBlank()) return "location must not be blank"
            if (discovery_method.isBlank()) return "discovery_method must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class PacingCurve(
        @Description("Pacing segments")
        val segments: List<PacingSegment> = emptyList(),
        @Description("Overall intensity (0-100)")
        val overall_intensity: Double = 50.0,
        @Description("Location of climax")
        val climax_location: String = "",
        @Description("Rest point locations")
        val rest_points: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (overall_intensity < 0 || overall_intensity > 100) {
                return "overall_intensity must be between 0 and 100"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    data class PacingSegment(
        @Description("Time in minutes")
        val time_minutes: Int = 0,
        @Description("Intensity level (0-100)")
        val intensity: Double = 50.0,
        @Description("Activity type")
        val activity_type: String = "",
        @Description("Segment description")
        val description: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (time_minutes < 0) return "time_minutes must be non-negative"
            if (intensity < 0 || intensity > 100) return "intensity must be between 0 and 100"
            if (activity_type.isBlank()) return "activity_type must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class DifficultyVariant(
        @Description("Difficulty level")
        val difficulty: String = "",
        @Description("Modifications from base level")
        val modifications: List<String> = emptyList(),
        @Description("Enemy adjustments")
        val enemy_adjustments: String = "",
        @Description("Resource adjustments")
        val resource_adjustments: String = "",
        @Description("Time limit adjustments")
        val time_adjustments: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (difficulty.isBlank()) return "difficulty must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }
    class GameLevelDesignTaskTypeConfig(
        var structurePrompt: String = "You are an expert game level designer. Create a complete level structure for this game.",
        var encounterPrompt: String = "You are an expert game encounter designer. Design detailed encounters for this level.",
        var pacingPrompt: String = "You are an expert game pacing designer. Analyze and define the pacing curve for this level.",
        var collectiblesPrompt: String = "You are an expert game content designer. Add collectibles and secrets to this level.",
        var guidancePrompt: String = "You are an expert game UX designer. Design player guidance systems for this level.",
        var variantsPrompt: String = "You are an expert game balance designer. Create difficulty variants for this level.",
        var promptSegmentTemplate: String = """
            GameLevelDesign - Generate complete game level designs with layout, pacing, and encounters
              ** Optionally, list input files (supports glob patterns) to be examined for context
              ** Specify level name and game type (platformer, shooter, puzzle, rpg, etc.)
              ** Set target duration and difficulty tier
              ** Configure player count (single or multiplayer)
              ** Choose level theme and visual style
              ** Include boss encounters, puzzles, secrets, and collectibles
              ** Define pacing style (steady, escalating, varied)
              ** Generate difficulty variants for accessibility
              ** Produces complete level design with ASCII visualization
              ** Includes encounter progression, pacing analysis, and player guidance
              ** Ideal for game development, level design documentation, and prototyping
        """.trimIndent()
    ) : TaskTypeConfig()


    override fun promptSegment(): String {
        return typeConfig?.promptSegmentTemplate ?: """
            GameLevelDesign - Generate complete game level designs with layout, pacing, and encounters
            (Template missing in config)
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
        log.info("Starting GameLevelDesignTask for level: '${executionConfig?.level_name}'")

        val transcript = task.transcript()

        // Validate configuration
        executionConfig?.validate()?.let { validationError ->
            log.error("Configuration validation failed: $validationError")
            task.safeComplete("CONFIGURATION ERROR: $validationError", log)
            task.error(ValidatedObject.ValidationError(validationError, executionConfig))
            resultFn("CONFIGURATION ERROR: $validationError")
            return
        }

        val levelName = executionConfig?.level_name
        if (levelName.isNullOrBlank()) {
            log.error("No level name specified")
            task.safeComplete("CONFIGURATION ERROR: No level name specified", log)
            resultFn("CONFIGURATION ERROR: No level name specified")
            return
        }

        val api = defaultSmart ?: return

        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = task.newTask()
        tabs["Overview"] = overviewTask.placeholder

        val overviewContent = buildString {
            appendLine("# Game Level Design")
            appendLine()
            appendLine("**Level Name:** $levelName")
            appendLine()
            appendLine("## Configuration")
            appendLine("- Game Type: ${executionConfig.game_type}")
            appendLine("- Duration: ${executionConfig.level_duration_minutes} minutes")
            appendLine("- Difficulty: ${executionConfig.difficulty_tier}")
            appendLine("- Player Count: ${executionConfig.player_count}")
            appendLine("- Theme: ${executionConfig.level_theme}")
            appendLine("- Pacing Style: ${executionConfig.pacing_style}")
            appendLine("- Boss Encounter: ${if (executionConfig.include_boss_encounter) "✓" else "✗"}")
            appendLine("- Puzzles: ${if (executionConfig.include_puzzles) "✓" else "✗"}")
            appendLine("- Secrets: ${if (executionConfig.include_secrets) "✓" else "✗"}")
            appendLine("- Collectibles: ${if (executionConfig.include_collectibles) "✓" else "✗"}")
            appendLine()
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Level Structure")
            appendLine("*Creating level layout and zone structure...*")
        }

        transcript?.apply {
            write("# Game Level Design Transcript\n\n".toByteArray())
            write("**Level Name:** $levelName\n\n".toByteArray())
            write("## Configuration\n\n".toByteArray())
            write(overviewContent.toByteArray())
            write("\n---\n\n".toByteArray())
        }

        overviewTask.add(overviewContent.renderMarkdown(true))
        task.update()

        val resultBuilder = StringBuilder()
        resultBuilder.append("# Level Design: $levelName\n\n")

        try {
            // Gather context
            val priorContext = getPriorCode(agent.executionState)
            val inputContext = getInputFileContent(executionConfig?.input_files, root)
            val combinedContext = (if (inputContext.isNotBlank()) inputContext else "") +
                    (if (priorContext.isNotBlank()) "\n\n## Prior Context\n\n$priorContext" else "")

            if (combinedContext.isNotBlank()) {
                transcript?.apply {
                    write("## Context\n<details><summary>Context Data</summary>\n\n".toByteArray())
                    write(combinedContext.toByteArray())
                    write("\n</details>\n\n---\n\n".toByteArray())
                }
                log.debug("Found context: ${combinedContext.length} chars")
                val contextTask = task.newTask()
                tabs["Context"] = contextTask.placeholder
                contextTask.add(
                  buildString {
                    appendLine("# Context")
                    appendLine()
                    appendLine(combinedContext.truncateForDisplay(2000))
                  }.renderMarkdown(true)
                )
                task.update()
            }

            // Phase 1: Create level structure
            log.info("Phase 1: Creating level structure")
            transcript?.apply {
                write("## Phase 1: Level Structure\n\n".toByteArray())
                write("Creating level layout and zone structure...\n\n".toByteArray())
            }

            val structureTask = task.newTask()
            tabs["Level Structure"] = structureTask.placeholder

            structureTask.add(
              buildString {
                appendLine("# Level Structure")
                appendLine()
                appendLine("**Status:** Designing level layout...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val numZones = when (executionConfig.level_duration_minutes) {
                in 5..10 -> 5
                in 11..15 -> 7
                in 16..20 -> 9
                else -> 11
            }

            val structureAgent = ParsedAgent(
                resultClass = GameLevel::class.java,
                prompt = """
${typeConfig?.structurePrompt}

Level Name: $levelName
Game Type: ${executionConfig.game_type}
Theme: ${executionConfig.level_theme}
Duration: ${executionConfig.level_duration_minutes} minutes
Difficulty: ${executionConfig.difficulty_tier}
Player Count: ${executionConfig.player_count}
Pacing Style: ${executionConfig.pacing_style}

${if (combinedContext.isNotBlank()) "Additional Context:\n${combinedContext.truncateForDisplay(1000)}\n" else ""}

Create a level with:
1. A clear name and theme
2. A layout with approximately $numZones zones
3. Zones should include:
   - Starting zone (safe area)
   - Combat zones (${if (executionConfig.include_boss_encounter) "including boss arena" else "regular encounters"})
   ${if (executionConfig.include_puzzles) "- Puzzle zones" else ""}
   - Exploration zones
   - Rest points
   ${if (executionConfig.include_boss_encounter) "- Boss encounter zone at the end" else ""}
4. Logical connections between zones
5. ${if (executionConfig.include_visual_layout) "ASCII representation of the level layout" else "Text description of layout"}

Design principles:
- Flow should be intuitive but allow exploration
- Difficulty should ${executionConfig.pacing_style} throughout
- Include shortcuts and alternate paths
- Balance linear progression with optional content
- Consider ${executionConfig.player_count} player(s) in space design

Keep zone descriptions brief - detailed content will be added later.
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = defaultFast
            )

            var level = structureAgent.answer(listOf("Create level structure")).obj

            // Validate structure
            level.validate()?.let { validationError ->
                log.error("Level structure validation failed: $validationError")
                structureTask.error(ValidatedObject.ValidationError(validationError, level))
                task.safeComplete("Level structure validation failed: $validationError", log)
                resultFn("ERROR: Level structure validation failed: $validationError")
                return
            }

            log.info("Generated level structure: ${level.layout.zones.size} zones")

            transcript?.apply {
                write("### Level Structure\n\n".toByteArray())
                write("**Zones:** ${level.layout.zones.size}\n\n".toByteArray())
                level.layout.zones.forEach { zone ->
                    write("- ${zone.zone_id}: ${zone.name} (${zone.type})\n".toByteArray())
                }
                write("\n---\n\n".toByteArray())
            }

            val structureContent = buildString {
                appendLine("## ${level.name}")
                appendLine()
                appendLine("**Theme:** ${level.theme}")
                appendLine("**Estimated Duration:** ${level.estimated_duration_minutes} minutes")
                appendLine()
                if (executionConfig.include_visual_layout && level.layout.ascii_representation.isNotBlank()) {
                    appendLine("### Level Layout")
                    appendLine()
                    appendLine("```")
                    appendLine(level.layout.ascii_representation)
                    appendLine("```")
                    appendLine()
                }
                appendLine("### Zones")
                appendLine()
                level.layout.zones.forEach { zone ->
                    appendLine("#### ${zone.zone_id}: ${zone.name}")
                    appendLine("- **Type:** ${zone.type}")
                    appendLine("- **Description:** ${zone.description}")
                    if (zone.exits.isNotEmpty()) {
                        appendLine("- **Exits to:** ${zone.exits.joinToString(", ")}")
                    }
                    appendLine()
                }
                appendLine("### Zone Connections")
                appendLine()
                level.layout.connections.forEach { conn ->
                    val lockInfo = if (conn.locked) " (🔒 ${conn.unlock_requirement})" else ""
                    appendLine("- ${conn.from_zone} → ${conn.to_zone} (${conn.connection_type})$lockInfo")
                }
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }

            structureTask.add(structureContent.renderMarkdown(true))
            task.update()

            overviewTask.add(
              "✅ Phase 1 Complete: Level structure created (${level.layout.zones.size} zones)\n".renderMarkdown(
                true
              )
            )
            overviewTask.add(
              "\n### Phase 2: Encounter Design\n*Designing encounters and challenges...*\n".renderMarkdown(
                true
              )
            )
            task.update()

            // Phase 2: Design encounters
            log.info("Phase 2: Designing encounters")
            transcript?.apply {
                write("## Phase 2: Encounter Design\n\n".toByteArray())
                write("Designing encounters and challenges...\n\n".toByteArray())
            }

            val encounterTask = task.newTask()
            tabs["Encounters"] = encounterTask.placeholder

            encounterTask.add(
              buildString {
                appendLine("# Encounter Design")
                appendLine()
                appendLine("**Status:** Creating encounters...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val encounterAgent = ParsedAgent(
                resultClass = GameLevel::class.java,
                prompt = """
${typeConfig?.encounterPrompt}

Level: ${level.name}
Game Type: ${executionConfig.game_type}
Difficulty: ${executionConfig.difficulty_tier}
Duration: ${executionConfig.level_duration_minutes} minutes

Zones:
${level.layout.zones.joinToString("\n") { "- ${it.zone_id}: ${it.name} (${it.type})" }}

Create encounters for each combat/puzzle zone:
1. Encounter ID and type (combat/puzzle/environmental)
2. Difficulty appropriate to zone position
3. Enemy/obstacle composition for ${executionConfig.game_type}
4. Recommended player level
5. Rewards (experience, items, currency)
6. Tactics for defeating/solving

${if (executionConfig.include_boss_encounter) "Include a challenging boss encounter in the final zone." else ""}
${if (executionConfig.include_puzzles) "Include puzzle encounters that fit the ${executionConfig.level_theme} theme." else ""}

Encounter design principles:
- Early encounters should teach mechanics
- Difficulty should ${executionConfig.pacing_style}
- Variety in encounter types
- Fair but challenging
- Rewards should match difficulty
- Consider ${executionConfig.player_count} player(s)

Return the complete level with all encounters filled in.
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = defaultFast
            )

            level = encounterAgent.answer(listOf("Design encounters")).obj

            transcript?.apply {
                write("### Encounters\n\n".toByteArray())
                write("**Total Encounters:** ${level.encounters.size}\n\n".toByteArray())
                level.encounters.forEach { enc ->
                    write("- ${enc.encounter_id}: ${enc.type} (${enc.difficulty})\n".toByteArray())
                }
                write("\n---\n\n".toByteArray())
            }

            val encounterContent = buildString {
                appendLine("## Encounter Progression")
                appendLine()
                level.encounters.forEachIndexed { index, encounter ->
                    appendLine("### ${index + 1}. ${encounter.encounter_id}")
                    appendLine()
                    appendLine("**Type:** ${encounter.type}")
                    appendLine("**Difficulty:** ${encounter.difficulty}")
                    appendLine("**Recommended Level:** ${encounter.recommended_level}")
                    appendLine()
                    if (encounter.composition.isNotEmpty()) {
                        appendLine("**Composition:**")
                        encounter.composition.forEach { comp ->
                            appendLine("- $comp")
                        }
                        appendLine()
                    }
                    if (encounter.rewards.isNotEmpty()) {
                        appendLine("**Rewards:**")
                        encounter.rewards.forEach { reward ->
                            appendLine("- ${reward.name} (${reward.type}): ${reward.value}")
                        }
                        appendLine()
                    }
                    if (encounter.tactics.isNotBlank()) {
                        appendLine("**Tactics:** ${encounter.tactics}")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }

            encounterTask.add(encounterContent.renderMarkdown(true))
            task.update()

            overviewTask.add("✅ Phase 2 Complete: ${level.encounters.size} encounters designed\n".renderMarkdown(true))
            overviewTask.add(
              "\n### Phase 3: Pacing Analysis\n*Analyzing level pacing and intensity...*\n".renderMarkdown(
                true
              )
            )
            task.update()

            // Phase 3: Pacing analysis
            log.info("Phase 3: Analyzing pacing")
            transcript?.apply {
                write("## Phase 3: Pacing Analysis\n\n".toByteArray())
                write("Analyzing level pacing and intensity...\n\n".toByteArray())
            }

            val pacingTask = task.newTask()
            tabs["Pacing"] = pacingTask.placeholder

            pacingTask.add(
              buildString {
                appendLine("# Pacing Analysis")
                appendLine()
                appendLine("**Status:** Analyzing pacing curve...")
                appendLine()
              }.renderMarkdown(true)
            )
            task.update()

            val pacingAgent = ParsedAgent(
                resultClass = GameLevel::class.java,
                prompt = """
${typeConfig?.pacingPrompt}

Level: ${level.name}
Duration: ${executionConfig.level_duration_minutes} minutes
Pacing Style: ${executionConfig.pacing_style}
Zones: ${level.layout.zones.size}
Encounters: ${level.encounters.size}

Create a pacing curve with:
1. Segments covering the entire ${executionConfig.level_duration_minutes} minutes
2. Intensity values (0-100) for each segment
3. Activity types (combat, exploration, puzzle, rest)
4. Clear climax location
5. Strategic rest points

Pacing principles for ${executionConfig.pacing_style}:
${
                    when (executionConfig.pacing_style.lowercase()) {
                        "steady" -> "- Maintain consistent intensity\n- Regular rest points\n- Gradual difficulty increase"
                        "escalating" -> "- Start low, build to climax\n- Fewer rest points near end\n- Dramatic final encounter"
                        "varied" -> "- Alternate high and low intensity\n- Frequent rest points\n- Multiple mini-climaxes"
                        else -> "- Balanced pacing throughout"
                    }
                }

Overall intensity should be appropriate for ${executionConfig.difficulty_tier} difficulty.

Return the complete level with pacing_curve filled in.
          """.trimIndent(),
                model = api,
                temperature = 0.6,
                parsingChatter = defaultFast
            )

            level = pacingAgent.answer(listOf("Analyze pacing")).obj

            transcript?.apply {
                write("### Pacing Curve\n\n".toByteArray())
                write("**Overall Intensity:** ${level.pacing_curve.overall_intensity}\n".toByteArray())
                write("**Climax:** ${level.pacing_curve.climax_location}\n".toByteArray())
                write("**Rest Points:** ${level.pacing_curve.rest_points.size}\n\n".toByteArray())
                write("---\n\n".toByteArray())
            }

            val pacingContent = buildString {
                appendLine("## Pacing Curve")
                appendLine()
                appendLine("**Overall Intensity:** ${level.pacing_curve.overall_intensity}/100")
                appendLine("**Climax Location:** ${level.pacing_curve.climax_location}")
                appendLine()
                appendLine("### Intensity Over Time")
                appendLine()
                appendLine("```")
                // ASCII graph
                val maxIntensity = 100.0
                level.pacing_curve.segments.forEach { segment ->
                    val bars = (segment.intensity / maxIntensity * 40).toInt()
                    val time = String.format("%2d", segment.time_minutes)
                    val intensity = String.format("%3.0f", segment.intensity)
                    appendLine("$time min [$intensity] ${"█".repeat(bars)} ${segment.activity_type}")
                }
                appendLine("```")
                appendLine()
                appendLine("### Pacing Segments")
                appendLine()
                level.pacing_curve.segments.forEach { segment ->
                    appendLine("**${segment.time_minutes} minutes - ${segment.activity_type}**")
                    appendLine("- Intensity: ${segment.intensity}/100")
                    appendLine("- ${segment.description}")
                    appendLine()
                }
                if (level.pacing_curve.rest_points.isNotEmpty()) {
                    appendLine("### Rest Points")
                    appendLine()
                    level.pacing_curve.rest_points.forEach { restPoint ->
                        appendLine("- $restPoint")
                    }
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }

            pacingTask.add(pacingContent.renderMarkdown(true))
            task.update()

            overviewTask.add("✅ Phase 3 Complete: Pacing curve analyzed\n".renderMarkdown(true))

            // Phase 4: Collectibles and Secrets (if enabled)
            if (executionConfig.include_collectibles || executionConfig.include_secrets) {
                overviewTask.add(
                  "\n### Phase 4: Collectibles & Secrets\n*Placing collectibles and secret areas...*\n".renderMarkdown(
                    true
                  )
                )
                task.update()

                log.info("Phase 4: Designing collectibles and secrets")
                transcript?.apply {
                    write("## Phase 4: Collectibles & Secrets\n\n".toByteArray())
                    write("Placing collectibles and secret areas...\n\n".toByteArray())
                }

                val collectiblesTask = task.newTask()
                tabs["Collectibles & Secrets"] = collectiblesTask.placeholder

                collectiblesTask.add(
                  buildString {
                    appendLine("# Collectibles & Secrets")
                    appendLine()
                    appendLine("**Status:** Designing collectibles and secrets...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                task.update()

                val collectiblesAgent = ParsedAgent(
                    resultClass = GameLevel::class.java,
                    prompt = """
${typeConfig?.collectiblesPrompt}

Level: ${level.name}
Theme: ${level.theme}
Zones: ${level.layout.zones.size}

${if (executionConfig.include_collectibles) "Add collectibles:\n- Currency/resources\n- Equipment/power-ups\n- Consumables\n- Mix of obvious and hidden placements\n- Appropriate values for ${executionConfig.difficulty_tier} difficulty" else ""}

${if (executionConfig.include_secrets) "Add secret areas:\n- Hidden rooms or paths\n- Discovery methods (exploration, puzzle-solving, combat)\n- Valuable rewards\n- Varying difficulty to find (easy, medium, hard)\n- Fit the ${executionConfig.level_theme} theme" else ""}

Design principles:
- Reward exploration
- Balance obvious and hidden content
- Secrets should feel rewarding
- Collectibles should enhance gameplay
- Consider ${executionConfig.player_count} player(s)

Return the complete level with collectibles and secrets filled in.
          """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = defaultFast
                )

                level = collectiblesAgent.answer(listOf("Add collectibles and secrets")).obj

                transcript?.apply {
                    write("### Collectibles & Secrets\n\n".toByteArray())
                    write("**Collectibles:** ${level.collectibles.size}\n".toByteArray())
                    write("**Secrets:** ${level.secrets.size}\n\n".toByteArray())
                    write("---\n\n".toByteArray())
                }

                val collectiblesContent = buildString {
                    if (level.collectibles.isNotEmpty()) {
                        appendLine("## Collectibles")
                        appendLine()
                        level.collectibles.groupBy { it.type }.forEach { (type, items) ->
                            appendLine("### ${type.capitalize()}")
                            appendLine()
                            items.forEach { collectible ->
                                appendLine("**${collectible.name}** (${collectible.visibility})")
                                appendLine("- Location: ${collectible.location}")
                                appendLine("- Value: ${collectible.value}")
                                appendLine()
                            }
                        }
                        appendLine("---")
                        appendLine()
                    }
                    if (level.secrets.isNotEmpty()) {
                        appendLine("## Secret Areas")
                        appendLine()
                        level.secrets.forEach { secret ->
                            appendLine("### ${secret.name}")
                            appendLine()
                            appendLine("**Location:** ${secret.location}")
                            appendLine("**Discovery:** ${secret.discovery_method}")
                            appendLine("**Difficulty:** ${secret.difficulty_to_find}")
                            appendLine("**Reward:** ${secret.reward}")
                            appendLine()
                            appendLine("---")
                            appendLine()
                        }
                    }
                    appendLine("**Status:** ✅ Complete")
                }

                collectiblesTask.add(collectiblesContent.renderMarkdown(true))
                task.update()

                overviewTask.add(
                  "✅ Phase 4 Complete: ${level.collectibles.size} collectibles, ${level.secrets.size} secrets\n".renderMarkdown(
                    true
                  )
                )
            }

            // Phase 5: Player Guidance
            overviewTask.add("\n### Phase 5: Player Guidance\n*Designing guidance systems...*\n".renderMarkdown(true))
            task.update()

            log.info("Phase 5: Designing player guidance")
            transcript?.apply {
                write("## Phase 5: Player Guidance\n\n".toByteArray())
                write("Designing guidance systems...\n\n".toByteArray())
            }

            val guidanceTask = task.newTask()
            tabs["Player Guidance"] = guidanceTask.placeholder

            val guidanceAgent = ParsedAgent(
                resultClass = PlayerGuidance::class.java,
                prompt = """
${typeConfig?.guidancePrompt}

Level: ${level.name}
Game Type: ${executionConfig.game_type}
Difficulty: ${executionConfig.difficulty_tier}
Player Count: ${executionConfig.player_count}

Design guidance including:
1. Implicit cues (environmental, visual, audio)
2. Explicit markers (waypoints, arrows, highlights)
3. Tutorial elements (${if (executionConfig.difficulty_tier == "tutorial") "extensive" else "minimal"})
4. Accessibility features

Guidance principles:
- ${if (executionConfig.difficulty_tier == "tutorial") "Clear and frequent guidance" else "Subtle guidance that respects player intelligence"}
- Balance hand-holding with exploration
- Use environmental storytelling
- Consider ${executionConfig.player_count} player(s)
- Fit the ${executionConfig.level_theme} theme

Create comprehensive guidance that helps without patronizing.
          """.trimIndent(),
                model = api,
                temperature = 0.6,
                parsingChatter = defaultFast
            )

            val guidance = guidanceAgent.answer(listOf("Design player guidance")).obj

            transcript?.apply {
                write("### Player Guidance\n\n".toByteArray())
                write("**Implicit Cues:** ${guidance.implicit_cues.size}\n".toByteArray())
                write("**Explicit Markers:** ${guidance.explicit_markers.size}\n\n".toByteArray())
                write("---\n\n".toByteArray())
            }

            val guidanceContent = buildString {
                appendLine("## Player Guidance Systems")
                appendLine()
                appendLine("### Implicit Cues")
                appendLine()
                guidance.implicit_cues.forEach { cue ->
                    appendLine("- **${cue.type}:** ${cue.description}")
                }
                appendLine()
                appendLine("### Explicit Markers")
                appendLine()
                guidance.explicit_markers.forEach { marker ->
                    appendLine("- **${marker.type}:** ${marker.description}")
                    appendLine("  - Location: ${marker.location}")
                }
                appendLine()
                if (guidance.tutorial_elements.isNotEmpty()) {
                    appendLine("### Tutorial Elements")
                    appendLine()
                    guidance.tutorial_elements.forEach { tutorial ->
                        appendLine("- **${tutorial.mechanic}:** ${tutorial.teaching_method}")
                    }
                    appendLine()
                }
                if (guidance.accessibility_features.isNotEmpty()) {
                    appendLine("### Accessibility Features")
                    appendLine()
                    guidance.accessibility_features.forEach { feature ->
                        appendLine("- **${feature.feature_name}:** ${feature.description}")
                    }
                    appendLine()
                }
                appendLine("**Status:** ✅ Complete")
            }

            guidanceTask.add(guidanceContent.renderMarkdown(true))
            task.update()

            overviewTask.add("✅ Phase 5 Complete: Player guidance designed\n".renderMarkdown(true))

            // Phase 6: Difficulty Variants (if enabled)
            if (executionConfig.generate_difficulty_variants) {
                overviewTask.add(
                  "\n### Phase 6: Difficulty Variants\n*Generating difficulty variants...*\n".renderMarkdown(
                    true
                  )
                )
                task.update()

                log.info("Phase 6: Generating difficulty variants")
                transcript?.apply {
                    write("## Phase 6: Difficulty Variants\n\n".toByteArray())
                    write("Generating difficulty variants...\n\n".toByteArray())
                }

                val variantsTask = task.newTask()
                tabs["Difficulty Variants"] = variantsTask.placeholder

                val variantsAgent = ParsedAgent(
                    resultClass = DifficultyVariants::class.java,
                    prompt = """
${typeConfig?.variantsPrompt}

Base Level: ${level.name}
Base Difficulty: ${executionConfig.difficulty_tier}

Create variants for:
- Easy mode (if base is medium or higher)
- Hard mode (if base is medium or lower)
- Expert mode (optional)

For each variant, specify:
1. Enemy adjustments (health, damage, count, AI)
2. Resource adjustments (health packs, ammo, checkpoints)
3. Time limit adjustments
4. Specific modifications to encounters

Variant principles:
- Easy: More forgiving, teaching-focused
- Hard: Challenging but fair
- Expert: For mastery, minimal guidance

Ensure variants maintain the core level design while adjusting challenge.
          """.trimIndent(),
                    model = api,
                    temperature = 0.6,
                    parsingChatter = defaultFast
                )

                val variants = variantsAgent.answer(listOf("Generate difficulty variants")).obj

                transcript?.apply {
                    write("### Difficulty Variants\n\n".toByteArray())
                    write("**Variants:** ${variants.variants.size}\n\n".toByteArray())
                    write("---\n\n".toByteArray())
                }

                val variantsContent = buildString {
                    appendLine("## Difficulty Variants")
                    appendLine()
                    variants.variants.forEach { variant ->
                        appendLine("### ${variant.difficulty.capitalize()} Mode")
                        appendLine()
                        appendLine("**Modifications:**")
                        variant.modifications.forEach { mod ->
                            appendLine("- $mod")
                        }
                        appendLine()
                        if (variant.enemy_adjustments.isNotBlank()) {
                            appendLine("**Enemy Adjustments:** ${variant.enemy_adjustments}")
                            appendLine()
                        }
                        if (variant.resource_adjustments.isNotBlank()) {
                            appendLine("**Resource Adjustments:** ${variant.resource_adjustments}")
                            appendLine()
                        }
                        if (variant.time_adjustments.isNotBlank()) {
                            appendLine("**Time Adjustments:** ${variant.time_adjustments}")
                            appendLine()
                        }
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("**Status:** ✅ Complete")
                }

                variantsTask.add(variantsContent.renderMarkdown(true))
                task.update()

                overviewTask.add(
                  "✅ Phase 6 Complete: ${variants.variants.size} difficulty variants\n".renderMarkdown(
                    true
                  )
                )
            }

            // Final Assembly
            overviewTask.add("\n### Final Assembly\n*Compiling complete level design...*\n".renderMarkdown(true))
            task.update()

            log.info("Final Assembly: Compiling complete level design")

            val finalTask = task.newTask()
            tabs["Complete Design"] = finalTask.placeholder

            val completeDesign = buildString {
                appendLine("# Level Design: ${level.name}")
                appendLine()
                appendLine("## Level Overview")
                appendLine()
                appendLine("**Theme:** ${level.theme}")
                appendLine("**Game Type:** ${executionConfig.game_type}")
                appendLine("**Estimated Duration:** ${level.estimated_duration_minutes} minutes")
                appendLine("**Difficulty Tier:** ${executionConfig.difficulty_tier}")
                appendLine("**Player Count:** ${executionConfig.player_count}")
                appendLine()
                appendLine("**Key Objectives:**")
                appendLine("- Navigate through ${level.layout.zones.size} zones")
                appendLine("- Complete ${level.encounters.size} encounters")
                if (executionConfig.include_collectibles) {
                    appendLine("- Collect ${level.collectibles.size} items")
                }
                if (executionConfig.include_secrets) {
                    appendLine("- Discover ${level.secrets.size} secrets")
                }
                if (executionConfig.include_boss_encounter) {
                    appendLine("- Defeat the final boss")
                }
                appendLine()
                appendLine("---")
                appendLine()

                if (executionConfig.include_visual_layout && level.layout.ascii_representation.isNotBlank()) {
                    appendLine("## Level Layout")
                    appendLine()
                    appendLine("```")
                    appendLine(level.layout.ascii_representation)
                    appendLine("```")
                    appendLine()
                    appendLine("---")
                    appendLine()
                }

                appendLine("## Zone Details")
                appendLine()
                level.layout.zones.forEach { zone ->
                    appendLine("### ${zone.zone_id}: ${zone.name}")
                    appendLine()
                    appendLine("**Type:** ${zone.type}")
                    appendLine("**Description:** ${zone.description}")
                    appendLine()
                    if (zone.encounters.isNotEmpty()) {
                        appendLine("**Encounters:**")
                        zone.encounters.forEach { encId ->
                            val encounter = level.encounters.find { it.encounter_id == encId }
                            if (encounter != null) {
                                appendLine("- ${encounter.encounter_id} (${encounter.type}, ${encounter.difficulty})")
                            }
                        }
                        appendLine()
                    }
                    if (zone.exits.isNotEmpty()) {
                        appendLine("**Exits:** ${zone.exits.joinToString(", ")}")
                        appendLine()
                    }
                    appendLine("---")
                    appendLine()
                }

                appendLine("## Encounter Progression")
                appendLine()
                level.encounters.forEachIndexed { index, encounter ->
                    appendLine("### ${index + 1}. ${encounter.encounter_id}")
                    appendLine()
                    appendLine("- **Type:** ${encounter.type}")
                    appendLine("- **Difficulty:** ${encounter.difficulty}")
                    appendLine("- **Recommended Level:** ${encounter.recommended_level}")
                    if (encounter.composition.isNotEmpty()) {
                        appendLine("- **Composition:** ${encounter.composition.joinToString(", ")}")
                    }
                    if (encounter.tactics.isNotBlank()) {
                        appendLine("- **Tactics:** ${encounter.tactics}")
                    }
                    if (encounter.rewards.isNotEmpty()) {
                        appendLine("- **Rewards:** ${encounter.rewards.joinToString(", ") { "${it.name} (${it.value})" }}")
                    }
                    appendLine()
                }
                appendLine("---")
                appendLine()

                appendLine("## Pacing Analysis")
                appendLine()
                appendLine("**Overall Intensity:** ${level.pacing_curve.overall_intensity}/100")
                appendLine("**Pacing Style:** ${executionConfig.pacing_style}")
                appendLine("**Climax Location:** ${level.pacing_curve.climax_location}")
                appendLine()
                appendLine("### Intensity Curve")
                appendLine()
                appendLine("```")
                level.pacing_curve.segments.forEach { segment ->
                    val bars = (segment.intensity / 100.0 * 40).toInt()
                    appendLine(
                        "${
                            String.format(
                                "%2d",
                                segment.time_minutes
                            )
                        }m [${"█".repeat(bars)}] ${segment.activity_type}"
                    )
                }
                appendLine("```")
                appendLine()
                if (level.pacing_curve.rest_points.isNotEmpty()) {
                    appendLine("**Rest Points:** ${level.pacing_curve.rest_points.joinToString(", ")}")
                    appendLine()
                }
                appendLine("---")
                appendLine()

                if (level.collectibles.isNotEmpty() || level.secrets.isNotEmpty()) {
                    appendLine("## Collectibles & Secrets")
                    appendLine()
                    if (level.collectibles.isNotEmpty()) {
                        appendLine("### Collectibles (${level.collectibles.size})")
                        appendLine()
                        level.collectibles.groupBy { it.type }.forEach { (type, items) ->
                            appendLine("**${type.capitalize()}:**")
                            items.forEach { item ->
                                appendLine("- ${item.name} @ ${item.location} (${item.visibility})")
                            }
                            appendLine()
                        }
                    }
                    if (level.secrets.isNotEmpty()) {
                        appendLine("### Secrets (${level.secrets.size})")
                        appendLine()
                        level.secrets.forEach { secret ->
                            appendLine("**${secret.name}**")
                            appendLine("- Location: ${secret.location}")
                            appendLine("- Discovery: ${secret.discovery_method}")
                            appendLine("- Reward: ${secret.reward}")
                            appendLine()
                        }
                    }
                    appendLine("---")
                    appendLine()
                }

                appendLine("## Statistics")
                appendLine()
                appendLine("- **Total Zones:** ${level.layout.zones.size}")
                appendLine("- **Total Encounters:** ${level.encounters.size}")
                appendLine("- **Collectibles:** ${level.collectibles.size}")
                appendLine("- **Secrets:** ${level.secrets.size}")
                appendLine("- **Estimated Playtime:** ${level.estimated_duration_minutes} minutes")
                appendLine("- **Overall Intensity:** ${level.pacing_curve.overall_intensity}/100")
            }

            finalTask.add(completeDesign.renderMarkdown(true))
            transcript?.apply {
                write("## Complete Level Design\n<details><summary>Full Design Markdown</summary>\n\n".toByteArray())
                write(completeDesign.toByteArray())
                write("\n</details>\n\n---\n\n".toByteArray())
            }
            task.update()

            // Final statistics
            val totalTime = System.currentTimeMillis() - startTime

            transcript?.apply {
                write("## Generation Complete\n\n".toByteArray())
                write("**Statistics:**\n\n".toByteArray())
                write("- Zones: ${level.layout.zones.size}\n".toByteArray())
                write("- Encounters: ${level.encounters.size}\n".toByteArray())
                write("- Collectibles: ${level.collectibles.size}\n".toByteArray())
                write("- Secrets: ${level.secrets.size}\n".toByteArray())
                write("- Total Time: ${totalTime / 1000.0}s\n\n".toByteArray())
                write(
                    "**Completed:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }\n".toByteArray()
                )
            }

            overviewTask.add(
              buildString {
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## ✅ Generation Complete")
                appendLine()
                appendLine("**Statistics:**")
                appendLine("- Total Zones: ${level.layout.zones.size}")
                appendLine("- Total Encounters: ${level.encounters.size}")
                appendLine("- Collectibles: ${level.collectibles.size}")
                appendLine("- Secrets: ${level.secrets.size}")
                appendLine("- Estimated Duration: ${level.estimated_duration_minutes} minutes")
                appendLine("- Total Time: ${totalTime / 1000.0}s")
                appendLine()
                appendLine(
                  "**Completed:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                  }"
                )
              }.renderMarkdown(true)
            )
            task.update()

            // Concise summary for resultFn
            val finalResult = buildString {
                appendLine("# Level Design Summary: ${level.name}")
                appendLine()
                appendLine("A complete ${executionConfig.game_type} level design for **${level.estimated_duration_minutes} minutes** of gameplay was generated in **${totalTime / 1000.0}s**.")
                appendLine()
                appendLine("**Structure:**")
                appendLine("- ${level.layout.zones.size} zones with logical connections")
                appendLine("- ${level.encounters.size} encounters (${executionConfig.pacing_style} pacing)")
                appendLine("- ${level.collectibles.size} collectibles")
                appendLine("- ${level.secrets.size} secret areas")
                appendLine("- Overall intensity: ${level.pacing_curve.overall_intensity}/100")
                appendLine()
                appendLine("> The complete level design with layout, encounters, pacing analysis, and player guidance is available in the Complete Design tab.")
            }

            log.info("GameLevelDesignTask completed: zones=${level.layout.zones.size}, encounters=${level.encounters.size}, time=${totalTime}ms")

            task.safeComplete(
                "Level design complete: ${level.layout.zones.size} zones, ${level.encounters.size} encounters in ${totalTime / 1000}s",
                log
            )
            resultFn(buildFinalResultWithLinks(task, finalResult, completeDesign, level, totalTime))

        } catch (e: Exception) {
            // Triple Log Rule
            task.error(e)
            log.error("Error in GameLevelDesignTask for level: '$levelName'", e)
            transcript?.write(
                """
                <details>
                <summary>Stack Trace</summary>
                ```
                ${e.stackTraceToString()}
                ```
                </details>
            """.trimIndent().toByteArray()
            )


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
            task.update()

            val errorOutput = buildString {
                appendLine("# Error in Level Design Generation")
                appendLine()
                appendLine("**Level Name:** $levelName")
                appendLine()
                appendLine("**Error:** ${e.message}")
                appendLine()
                if (resultBuilder.isNotEmpty()) {
                    appendLine("## Partial Results")
                    appendLine()
                    appendLine(resultBuilder.toString())
                }
            }
            resultFn(errorOutput)
        } finally {
            transcript?.close()
        }
    }

    private fun buildFinalResultWithLinks(
        task: SessionTask,
        summary: String,
        completeDesign: String,
        level: GameLevel,
        totalTime: Long
    ): String {
        return try {
            // Save complete design to file
            val designLink = task.saveFile("level_design.md", completeDesign.toByteArray())

            // Save summary to file
            val summaryLink = task.saveFile("level_summary.md", summary.toByteArray())

            buildString {
                appendLine("# Level Design Generation Complete")
                appendLine()
                appendLine("**Level:** ${level.name}")
                appendLine("**Zones:** ${level.layout.zones.size}")
                appendLine("**Encounters:** ${level.encounters.size}")
                appendLine("**Duration:** ${level.estimated_duration_minutes} minutes")
                appendLine("**Generation Time:** ${totalTime / 1000.0}s")
                appendLine()
                appendLine("## Output Files")
                appendLine()
                appendLine("- [Complete Level Design]($designLink) - Full level documentation")
                appendLine("  - [HTML](${designLink.removeSuffix(".md")}.html)")
                appendLine("  - [PDF](${designLink.removeSuffix(".md")}.pdf)")
                appendLine()
                appendLine("- [Level Summary]($summaryLink) - Generation summary and statistics")
                appendLine("  - [HTML](${summaryLink.removeSuffix(".md")}.html)")
                appendLine("  - [PDF](${summaryLink.removeSuffix(".md")}.pdf)")
                appendLine()
                appendLine("## Quick Stats")
                appendLine()
                appendLine("- Collectibles: ${level.collectibles.size}")
                appendLine("- Secrets: ${level.secrets.size}")
                appendLine("- Overall Intensity: ${level.pacing_curve.overall_intensity}/100")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(summary)
            }
        } catch (e: Exception) {
            log.error("Failed to create output files", e)
            buildString {
                appendLine("# Level Design Generation Complete")
                appendLine()
                appendLine("**Note:** Could not save detailed output files, but level was generated successfully.")
                appendLine()
                appendLine(summary)
            }
        }
    }


    // Additional data classes for player guidance
    data class PlayerGuidance(
        @Description("Implicit guidance cues")
        val implicit_cues: List<ImplicitCue> = emptyList(),
        @Description("Explicit guidance markers")
        val explicit_markers: List<ExplicitMarker> = emptyList(),
        @Description("Tutorial elements")
        val tutorial_elements: List<TutorialElement> = emptyList(),
        @Description("Accessibility features")
        val accessibility_features: List<AccessibilityFeature> = emptyList()
    ) : ValidatedObject

    data class ImplicitCue(
        @Description("Type of cue")
        val type: String = "",
        @Description("Description of the cue")
        val description: String = ""
    ) : ValidatedObject

    data class ExplicitMarker(
        @Description("Type of marker")
        val type: String = "",
        @Description("Description of the marker")
        val description: String = "",
        @Description("Location of the marker")
        val location: String = ""
    ) : ValidatedObject

    data class TutorialElement(
        @Description("Mechanic being taught")
        val mechanic: String = "",
        @Description("Teaching method")
        val teaching_method: String = ""
    ) : ValidatedObject

    data class AccessibilityFeature(
        @Description("Feature name")
        val feature_name: String = "",
        @Description("Feature description")
        val description: String = ""
    ) : ValidatedObject

    data class DifficultyVariants(
        @Description("List of difficulty variants")
        val variants: List<DifficultyVariant> = emptyList()
    ) : ValidatedObject

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameLevelDesignTask::class.java)

        @JvmStatic val GameLevelDesign = TaskType(
            name = "GameLevelDesign",
            category = "Games",
            taskClass = GameLevelDesignTask::class.java,
            executionConfigClass = GameLevelDesignTaskExecutionConfigData::class.java,
            taskSettingsClass = GameLevelDesignTaskTypeConfig::class.java,
            description = "Generate complete game level designs with layout, pacing, and encounters",
            tooltipHtml = """
                        Generates production-ready game level designs with comprehensive documentation.
                        <ul>
                          <li>Creates detailed level layout with zones and connections</li>
                          <li>Designs encounters with appropriate difficulty progression</li>
                          <li>Analyzes and visualizes pacing curves</li>
                          <li>Places collectibles and secret areas strategically</li>
                          <li>Designs player guidance systems (implicit and explicit)</li>
                          <li>Generates difficulty variants for accessibility</li>
                          <li>Includes ASCII/text-based level visualization</li>
                          <li>Supports multiple game types (platformer, shooter, puzzle, RPG)</li>
                          <li>Configurable pacing styles (steady, escalating, varied)</li>
                          <li>Optional boss encounters, puzzles, and secrets</li>
                          <li>Ideal for game development, level design documentation, and prototyping</li>
                        </ul>
                      """,
        )
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/GameMechanicsDesignTask.kt

```
package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
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
        var input_files: List<String>? = null,
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
      val transcript = task.transcript()

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
            val inputFileContext = getInputFileContent(executionConfig?.input_files, root)

            if (priorContext.isNotBlank() || inputFileContext.isNotBlank()) {
              transcript?.write(
                """
                    ## Context
                    <details>
                    <summary>Context Data</summary>
                    
                    ### Prior Tasks
                    $priorContext
                    
                    ### Input Files
                    $inputFileContext
                    </details>
                    
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
          transcript?.write(
            "\n\n## Error Occurred\n\n**Error:** ${e.message}\n\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>\n"
              .toByteArray()
          )

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
        @JvmStatic val GameMechanicsDesign = TaskType(
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
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/GameNarrativeDesignTask.kt

```
package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.safeComplete
import com.simiacryptus.cognotik.plan.tools.truncateForDisplay
import com.simiacryptus.cognotik.plan.tools.writing.NarrativeGenerationTask
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO


class GameNarrativeDesignTask(
    orchestrationConfig: OrchestrationConfig, planTask: GameNarrativeDesignConfigData?
) : NarrativeGenerationTask<GameNarrativeDesignTask.GameNarrativeDesignConfigData>(
    orchestrationConfig, planTask
) {

    class GameNarrativeDesignConfigData(
        @Description("The title of the game") var game_title: String? = null,

        @Description("Game genre (e.g., 'RPG', 'adventure', 'visual novel', 'action-adventure')") var genre: String = "RPG",

        @Description("Narrative style: 'linear', 'branching', or 'open-ended'") var narrative_style: String = "branching",

        @Description("Player agency level: 'low', 'medium', or 'high'") var player_agency_level: String = "high",

        @Description("Number of main characters (1-10)") var num_main_characters: Int = 4,

        @Description("Number of major branching points (3-15)") var num_branching_points: Int = 8,

        @Description("Number of possible endings (1-10)") var num_endings: Int = 4,

        @Description("Whether to include detailed dialogue trees") var include_dialogue_trees: Boolean = true,

        @Description("Whether to include character arc development") var include_character_arcs: Boolean = true,

        @Description("Whether to include side quest narratives") var include_side_quests: Boolean = true,
        @Description("Whether to include game mechanics design") var include_game_mechanics: Boolean = true,


        @Description("Overall tone (e.g., 'dark', 'heroic', 'comedic', 'mysterious')") tone: String = "heroic",

        @Description("Player's role: 'protagonist', 'observer', or 'custom'") var player_role: String = "protagonist",

        @Description("Estimated playtime in hours (1-100)") var estimated_playtime_hours: Int = 20,

        @Description("Game setting/world description") var setting: String? = null,

        @Description("Core themes to explore") var themes: List<String>? = null,

        @Description("Whether to generate character portraits") var generate_character_portraits: Boolean = false,

        @Description("Whether to generate scene concept art") var generate_scene_art: Boolean = false,

        @Description("Input files for context (e.g., world lore, character bios)") input_files: List<String>? = null,

        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : NarrativeGenerationTaskExecutionConfigData(
        subject = game_title,
        input_files = input_files,
        narrative_elements = buildNarrativeElements(
            genre, narrative_style, player_agency_level, num_main_characters, tone, player_role, setting, themes
        ),
        target_word_count = estimateWordCount(estimated_playtime_hours, narrative_style),
        number_of_acts = 3,
        scenes_per_act = calculateScenesPerAct(num_branching_points),
        writing_style = determineWritingStyle(genre),
        point_of_view = if (player_role == "protagonist") "second person" else "third person limited",
        tone = tone,
        detailed_descriptions = true,
        include_dialogue = true,
        show_internal_thoughts = player_role == "protagonist",
        revision_passes = 1,
        generate_scene_images = generate_scene_art,
        generate_cover_image = true,
        task_dependencies = task_dependencies,
        state = state
    ) {
        override val task_type: String = GameNarrativeDesign.name
        override var task_description: String? = "Design game narrative for '$game_title'"

        override fun validate(): String? {
            super.validate()?.let { return it }

            if (game_title.isNullOrBlank()) {
                return "game_title must not be blank"
            }
            if (num_main_characters !in 1..10) {
                return "num_main_characters must be between 1 and 10, got: $num_main_characters"
            }
            if (num_branching_points !in 3..15) {
                return "num_branching_points must be between 3 and 15, got: $num_branching_points"
            }
            if (num_endings !in 1..10) {
                return "num_endings must be between 1 and 10, got: $num_endings"
            }
            if (estimated_playtime_hours !in 1..100) {
                return "estimated_playtime_hours must be between 1 and 100, got: $estimated_playtime_hours"
            }

            val validNarrativeStyles = setOf("linear", "branching", "open-ended")
            if (narrative_style !in validNarrativeStyles) {
                return "narrative_style must be one of: ${validNarrativeStyles.joinToString()}, got: $narrative_style"
            }

            val validAgencyLevels = setOf("low", "medium", "high")
            if (player_agency_level !in validAgencyLevels) {
                return "player_agency_level must be one of: ${validAgencyLevels.joinToString()}, got: $player_agency_level"
            }

            val validPlayerRoles = setOf("protagonist", "observer", "custom")
            if (player_role !in validPlayerRoles) {
                return "player_role must be one of: ${validPlayerRoles.joinToString()}, got: $player_role"
            }

            return null
        }

        companion object {
            private fun buildNarrativeElements(
                genre: String,
                narrativeStyle: String,
                agencyLevel: String,
                numCharacters: Int,
                tone: String,
                playerRole: String,
                setting: String?,
                themes: List<String>?
            ): Map<String, Any> = mutableMapOf<String, Any>().apply {
                put("genre", genre)
                put("narrative_style", narrativeStyle)
                put("player_agency_level", agencyLevel)
                put("num_main_characters", numCharacters)
                put("tone", tone)
                put("player_role", playerRole)
                setting?.let { put("setting", it) }
                themes?.let { put("themes", it) }
            }

            private fun estimateWordCount(playtimeHours: Int, narrativeStyle: String): Int {
                val baseWordsPerHour = when (narrativeStyle) {
                    "linear" -> 3000
                    "branching" -> 4000
                    "open-ended" -> 5000
                    else -> 3500
                }
                return playtimeHours * baseWordsPerHour
            }

            private fun calculateScenesPerAct(branchingPoints: Int): Int {
                return (branchingPoints / 3).coerceAtLeast(3)
            }

            private fun determineWritingStyle(genre: String): String = when (genre.lowercase()) {
                "rpg" -> "epic fantasy"
                "visual novel" -> "literary"
                "adventure" -> "action-oriented"
                "horror" -> "atmospheric"
                "mystery" -> "suspenseful"
                else -> "narrative"
            }
        }
    }

    data class NarrativeAct(
        val act_number: Int = 1,
        val title: String = "",
        val description: String = "",
        val key_events: List<String> = emptyList(),
        val character_developments: Map<String, String> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (act_number < 1) {
                return "Act number must be positive, got: $act_number"
            }
            if (title.isBlank()) {
                return "Act title must not be blank"
            }
            if (description.isBlank()) {
                return "Act description must not be blank"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    // Data structures for game narrative
    data class GameNarrative(
        val title: String = "",
        val premise: String = "",
        val setting: String = "",
        val acts: List<NarrativeAct> = emptyList(),
        val characters: List<GameCharacter> = emptyList(),
        val branching_points: List<BranchingPoint> = emptyList(),
        val endings: List<GameEnding> = emptyList(),
        val themes: List<String> = emptyList(),
        val player_role: String = "",
        val estimated_playtime: String = ""
    ) : ValidatedObject {
        override fun validate(): String? {
            if (title.isBlank()) return "Game title must not be blank"
            if (premise.isBlank()) return "Premise must not be blank"
            if (acts.isEmpty()) return "Must have at least one act"
            if (characters.isEmpty()) return "Must have at least one character"
            return ValidatedObject.validateFields(this)
        }
    }

    data class GameCharacter(
        val name: String = "",
        val role: String = "",
        val arc: String = "",
        val motivations: List<String> = emptyList(),
        val dialogue_style: String = "",
        val relationship_to_player: String = "",
        val key_scenes: List<String> = emptyList(),
        val branching_reactions: Map<String, String> = emptyMap()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (name.isBlank()) return "Character name must not be blank"
            if (role.isBlank()) return "Character role must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class BranchingPoint(
        val id: String = "",
        val location: String = "",
        val description: String = "",
        val choices: List<NarrativeChoice> = emptyList(),
        val convergence_point: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (id.isBlank()) return "Branching point ID must not be blank"
            if (location.isBlank()) return "Location must not be blank"
            if (choices.isEmpty()) return "Must have at least one choice"
            return ValidatedObject.validateFields(this)
        }
    }

    data class NarrativeChoice(
        val choice_id: String = "",
        val text: String = "",
        val emotional_tone: String = "",
        val consequences: List<String> = emptyList(),
        val character_reactions: Map<String, String> = emptyMap(),
        val unlocks: List<String> = emptyList(),
        val locks: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (choice_id.isBlank()) return "Choice ID must not be blank"
            if (text.isBlank()) return "Choice text must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class GameEnding(
        val ending_id: String = "",
        val title: String = "",
        val description: String = "",
        val conditions: List<String> = emptyList(),
        val character_fates: Map<String, String> = emptyMap(),
        val thematic_resolution: String = "",
        val epilogue: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (ending_id.isBlank()) return "Ending ID must not be blank"
            if (title.isBlank()) return "Ending title must not be blank"
            if (description.isBlank()) return "Ending description must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class DialogueTree(
        val conversation_id: String = "",
        val character: String = "",
        val location: String = "",
        val root_dialogue: String = "",
        val options: List<DialogueOption> = emptyList(),
        val emotional_beats: List<String> = emptyList()
    ) : ValidatedObject {
        override fun validate(): String? {
            if (conversation_id.isBlank()) return "Conversation ID must not be blank"
            if (character.isBlank()) return "Character must not be blank"
            if (root_dialogue.isBlank()) return "Root dialogue must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class DialogueOption(
        val option_id: String = "",
        val text: String = "",
        val tone: String = "",
        val response: String = "",
        val consequences: List<String> = emptyList(),
        val next_options: List<DialogueOption>? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (option_id.isBlank()) return "Option ID must not be blank"
            if (text.isBlank()) return "Option text must not be blank"
            if (response.isBlank()) return "Response must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class DialogueTrees(
        val trees: List<DialogueTree> = emptyList()
    ) : ValidatedObject

    data class SideQuest(
        val quest_id: String = "",
        val title: String = "",
        val description: String = "",
        val giver: String = "",
        val objectives: List<String> = emptyList(),
        val rewards: List<String> = emptyList(),
        val narrative_impact: String = "",
        val unlocked_by: String? = null
    ) : ValidatedObject {
        override fun validate(): String? {
            if (quest_id.isBlank()) return "Quest ID must not be blank"
            if (title.isBlank()) return "Quest title must not be blank"
            return ValidatedObject.validateFields(this)
        }
    }

    data class SideQuests(
        val quests: List<SideQuest> = emptyList()
    ) : ValidatedObject

    data class GameMechanics(
        val core_loop: String = "",
        val key_systems: List<String> = emptyList(),
        val progression_system: String = "",
        val unique_mechanics: List<String> = emptyList(),
        val ludonarrative_harmony: String = ""
    ) : ValidatedObject

    data class GameDesignData(
        val narrative: GameNarrative? = null,
        val mechanics: GameMechanics? = null,
        val dialogue_trees: List<DialogueTree>? = null,
        val side_quests: List<SideQuest>? = null,
        val player_agency_analysis: String? = null
    )


    override fun promptSegment(): String {
        return """
GameNarrativeDesign - Create interactive game narratives with branching storylines
  ** Extends NarrativeGeneration with game-specific features
  ** Specify game title, genre, and narrative style
  ** Define player agency level and role
  ** Design core game mechanics and systems
  ** Configure branching points and multiple endings
  ** Include dialogue trees with emotional beats
  ** Character arcs that respond to player choices
  ** Side quests and optional content
  ** Produces complete game narrative design document
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
            val startTime = System.currentTimeMillis()
            val gameConfig = executionConfig!!
            log.info("Starting GameNarrativeDesignTask for game: '${gameConfig?.game_title}'")

            if (gameConfig == null) {
                log.error("Invalid configuration type for GameNarrativeDesignTask")
                task.safeComplete("CONFIGURATION ERROR: Invalid configuration type", log)
                resultFn("CONFIGURATION ERROR: Invalid configuration type")
            }

            val gameTitle = gameConfig.game_title
            if (gameTitle.isNullOrBlank()) {
                log.error("No game title specified")
                task.safeComplete("CONFIGURATION ERROR: No game title specified", log)
                resultFn("CONFIGURATION ERROR: No game title specified")
            }

            val api = defaultSmart ?: return@submit

            val tabs = TabbedDisplay(task)
            val transcript = task.transcript()

            // Create game design directory
            val gameDir = File(agent.root.toFile(), ".game_narrative_design")
            if (!gameDir.exists()) {
                gameDir.mkdirs()
                log.debug("Created game narrative design directory: ${gameDir.absolutePath}")
            }
            var gameDesignData = GameDesignData()


            // Overview tab
            val overviewTask = task.newTask()
            tabs["Overview"] = overviewTask.placeholder

            val overviewContent = buildString {
                appendLine("# Game Narrative Design")
                appendLine()
                appendLine("**Game Title:** $gameTitle")
                appendLine("**Genre:** ${gameConfig.genre}")
                appendLine()
                appendLine("## Configuration")
                appendLine("- Narrative Style: ${gameConfig.narrative_style}")
                appendLine("- Player Agency: ${gameConfig.player_agency_level}")
                appendLine("- Main Characters: ${gameConfig.num_main_characters}")
                appendLine("- Branching Points: ${gameConfig.num_branching_points}")
                appendLine("- Endings: ${gameConfig.num_endings}")
                appendLine("- Estimated Playtime: ${gameConfig.estimated_playtime_hours} hours")
                appendLine("- Tone: ${gameConfig.tone}")
                appendLine("- Player Role: ${gameConfig.player_role}")
                appendLine()
                appendLine("## Features")
                appendLine("- Dialogue Trees: ${if (gameConfig.include_dialogue_trees) "✓" else "✗"}")
                appendLine("- Character Arcs: ${if (gameConfig.include_character_arcs) "✓" else "✗"}")
                appendLine("- Side Quests: ${if (gameConfig.include_side_quests) "✓" else "✗"}")
                appendLine()
                appendLine(
                    "**Started:** ${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }"
                )
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("## Progress")
                appendLine()
                appendLine("### Phase 1: Base Narrative Generation")
                appendLine("*Generating core narrative structure...*")
            }
            overviewTask.add(overviewContent.renderMarkdown())
            transcript?.write("# Game Narrative Design: $gameTitle\n\n$overviewContent\n\n".toByteArray())
            transcript?.flush()
            task.update()

            val resultBuilder = StringBuilder()
            resultBuilder.append("# Game Narrative Design: $gameTitle\n\n")

            try {
                // Phase 1: Run base narrative generation
                log.info("Phase 1: Generating base narrative")
                val baseNarrativeResult = StringBuilder()

                super.run(agent, messages, task, { result ->
                    baseNarrativeResult.append(result)
                }, orchestrationConfig)

                overviewTask.add("\n✅ Phase 1 Complete: Base narrative generated\n".renderMarkdown())
                overviewTask.add("\n### Phase 2: Game Structure Analysis\n*Analyzing for interactive elements...*\n".renderMarkdown())
                task.update()

                // Phase 2: Generate game-specific structure
                log.info("Phase 2: Generating game structure")
                val gameStructureTask = task.newTask()
                tabs["Game Structure"] = gameStructureTask.placeholder

                gameStructureTask.add(
                  buildString {
                    appendLine("# Game Narrative Structure")
                    appendLine()
                    appendLine("**Status:** Analyzing narrative for game mechanics...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                task.update()

                val gameStructureAgent = ParsedAgent(
                    resultClass = GameNarrative::class.java, prompt = """
You are a game narrative designer. Transform the narrative into an interactive game structure.

Game Title: $gameTitle
Genre: ${gameConfig.genre}
Narrative Style: ${gameConfig.narrative_style}
Player Agency: ${gameConfig.player_agency_level}

Base Narrative:
${baseNarrativeResult.toString().truncateForDisplay(10000)}

Create a game narrative structure with:
- ${gameConfig.num_main_characters} main characters with distinct roles
- ${gameConfig.num_branching_points} major branching points
- ${gameConfig.num_endings} different endings
- Player role: ${gameConfig.player_role}
- Estimated playtime: ${gameConfig.estimated_playtime_hours} hours

For each character, define:
- Name and role (protagonist, ally, antagonist, mentor, etc.)
- Character arc (how they develop)
- Motivations and goals
- Dialogue style
- Relationship to player
- Key scenes they appear in
- How they react to different player choices

For branching points, specify:
- Location in the story (Act and scene)
- Description of the decision moment
- At least 2-3 meaningful choices
- Where paths converge (if applicable)

For endings, define:
- Unique title and description
- Conditions that trigger this ending
- Fate of each main character
- Thematic resolution
- Optional epilogue

Ensure the structure supports ${gameConfig.player_agency_level} player agency with meaningful choices.
          """.trimIndent(), model = api, temperature = 0.7, parsingChatter = defaultFast
                )

                val gameNarrative = gameStructureAgent.answer(listOf("Generate game structure")).obj
                log.info("Generated game structure: ${gameNarrative.characters.size} characters, ${gameNarrative.branching_points.size} branching points, ${gameNarrative.endings.size} endings")
                gameDesignData = gameDesignData.copy(narrative = gameNarrative)


                val gameStructureContent = buildString {
                    appendLine("## ${gameNarrative.title}")
                    appendLine()
                    appendLine("### Story Overview")
                    appendLine("**Premise:** ${gameNarrative.premise}")
                    appendLine()
                    appendLine("**Setting:** ${gameNarrative.setting}")
                    appendLine()
                    appendLine("**Themes:** ${gameNarrative.themes.joinToString(", ")}")
                    appendLine()
                    appendLine("**Player Role:** ${gameNarrative.player_role}")
                    appendLine()
                    appendLine("**Estimated Playtime:** ${gameNarrative.estimated_playtime}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("### Three-Act Structure")
                    gameNarrative.acts.forEach { act ->
                        appendLine("#### Act ${act.act_number}: ${act.title}")
                        appendLine()
                        appendLine(act.description)
                        appendLine()
                        if (act.key_events.isNotEmpty()) {
                            appendLine("**Key Events:**")
                            act.key_events.forEach { event ->
                                appendLine("- $event")
                            }
                            appendLine()
                        }
                    }
                    appendLine("---")
                    appendLine()
                    appendLine("### Main Characters")
                    gameNarrative.characters.forEachIndexed { index, char ->
                        appendLine("#### ${index + 1}. ${char.name}")
                        appendLine()
                        appendLine("- **Role:** ${char.role}")
                        appendLine("- **Arc:** ${char.arc}")
                        appendLine("- **Relationship to Player:** ${char.relationship_to_player}")
                        appendLine("- **Dialogue Style:** ${char.dialogue_style}")
                        appendLine()
                        appendLine("**Motivations:**")
                        char.motivations.forEach { motivation ->
                            appendLine("- $motivation")
                        }
                        appendLine()
                        if (char.key_scenes.isNotEmpty()) {
                            appendLine("**Key Scenes:** ${char.key_scenes.joinToString(", ")}")
                            appendLine()
                        }
                        if (char.branching_reactions.isNotEmpty()) {
                            appendLine("**Branching Reactions:**")
                            char.branching_reactions.forEach { (choice, reaction) ->
                                appendLine("- *$choice:* $reaction")
                            }
                            appendLine()
                        }
                    }
                    appendLine("**Status:** ✅ Complete")
                }
                gameStructureTask.add(gameStructureContent.renderMarkdown())
                transcript?.write("\n## Game Structure\n\n$gameStructureContent\n\n".toByteArray())
                transcript?.flush()
                task.update()

                saveAnalysisToFile(gameDir, "01_game_structure.md", gameStructureContent)
                resultBuilder.append("## Game Structure\n\n$gameStructureContent\n\n")

                overviewTask.add("✅ Phase 2 Complete: Game structure defined\n".renderMarkdown())
                task.update()
                // Phase 2.5: Game Mechanics
                if (gameConfig.include_game_mechanics) {
                    log.info("Phase 2.5: Designing game mechanics for $gameTitle")
                    overviewTask.add("\n### Phase 2.5: Game Mechanics\n*Designing gameplay systems...*\n".renderMarkdown())
                    task.update()
                    val mechanicsTask = task.newTask()
                    tabs["Mechanics"] = mechanicsTask.placeholder
                    mechanicsTask.add(
                      buildString {
                        appendLine("# Game Mechanics")
                        appendLine()
                        appendLine("**Status:** Designing systems to support the narrative...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    task.update()
                    val mechanicsAgent = ParsedAgent(
                        resultClass = GameMechanics::class.java, prompt = """
You are a lead game designer. Design the core mechanics for this game based on the narrative.
Game: $gameTitle
Genre: ${gameConfig.genre}
Narrative Style: ${gameConfig.narrative_style}
Narrative Premise: ${gameNarrative.premise}
Themes: ${gameNarrative.themes.joinToString(", ")}
Design the following:
1. **Core Loop**: What is the player doing moment-to-moment?
2. **Key Systems**: Major gameplay systems (combat, crafting, dialogue, etc.)
3. **Progression**: How does the player grow (stats, skills, equipment)?
4. **Unique Mechanics**: Specific mechanics that reinforce the narrative themes.
5. **Ludonarrative Harmony**: How gameplay reinforces the story.
Ensure mechanics fit the '${gameConfig.genre}' genre and '${gameConfig.tone}' tone.
          """.trimIndent(), model = api, temperature = 0.7, parsingChatter = defaultFast
                    )
                    val mechanics = mechanicsAgent.answer(listOf("Design game mechanics")).obj
                    gameDesignData = gameDesignData.copy(mechanics = mechanics)
                    val mechanicsContent = buildString {
                        appendLine("## Gameplay Mechanics")
                        appendLine()
                        appendLine("### Core Loop")
                        appendLine(mechanics.core_loop)
                        appendLine()
                        appendLine("### Key Systems")
                        mechanics.key_systems.forEach { sys -> appendLine("- $sys") }
                        appendLine()
                        appendLine("### Progression")
                        appendLine(mechanics.progression_system)
                        appendLine()
                        appendLine("### Unique Mechanics")
                        mechanics.unique_mechanics.forEach { mech -> appendLine("- $mech") }
                        appendLine()
                        appendLine("### Ludonarrative Harmony")
                        appendLine(mechanics.ludonarrative_harmony)
                    }
                    mechanicsTask.add(mechanicsContent.renderMarkdown())
                    transcript?.write("\n## Game Mechanics\n\n$mechanicsContent\n\n".toByteArray())
                    transcript?.flush()
                    task.update()
                    saveAnalysisToFile(gameDir, "01b_game_mechanics.md", mechanicsContent)
                    resultBuilder.append("## Game Mechanics\n\n")
                    resultBuilder.append(mechanics.core_loop.truncateForDisplay(200) + "\n\n")
                    overviewTask.add("✅ Phase 2.5 Complete: Mechanics designed\n".renderMarkdown())
                    task.update()
                }


                // Generate character portraits if enabled
                if (gameConfig.generate_character_portraits) {
                    gameNarrative.characters.take(gameConfig.num_main_characters).forEachIndexed { index, char ->
                        generateCharacterPortrait(
                            task = task,
                            tabs = tabs,
                            character = char,
                            index = index,
                            gameDir = gameDir,
                            transcript = transcript,
                            orchestrationConfig = orchestrationConfig
                        )
                    }
                }

                // Phase 3: Generate branching narrative map
                log.info("Phase 3: Generating branching narrative map")
                overviewTask.add("\n### Phase 3: Branching Narrative Map\n*Creating decision tree...*\n".renderMarkdown())
                task.update()

                val branchingMapTask = task.newTask()
                tabs["Branching Map"] = branchingMapTask.placeholder

                branchingMapTask.add(
                  buildString {
                    appendLine("# Branching Narrative Map")
                    appendLine()
                    appendLine("**Status:** Generating decision points and consequences...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                task.update()

                val branchingMapContent = buildString {
                    appendLine("## Decision Points and Consequences")
                    appendLine()
                    gameNarrative.branching_points.forEachIndexed { index, point ->
                        appendLine("### ${index + 1}. ${point.id}")
                        appendLine()
                        appendLine("**Location:** ${point.location}")
                        appendLine()
                        appendLine("**Description:** ${point.description}")
                        appendLine()
                        appendLine("**Choices:**")
                        point.choices.forEach { choice ->
                            appendLine()
                            appendLine("#### Choice: ${choice.text}")
                            appendLine("- **Tone:** ${choice.emotional_tone}")
                            appendLine()
                            if (choice.consequences.isNotEmpty()) {
                                appendLine("**Consequences:**")
                                choice.consequences.forEach { consequence ->
                                    appendLine("- $consequence")
                                }
                                appendLine()
                            }
                            if (choice.character_reactions.isNotEmpty()) {
                                appendLine("**Character Reactions:**")
                                choice.character_reactions.forEach { (char, reaction) ->
                                    appendLine("- **$char:** $reaction")
                                }
                                appendLine()
                            }
                            if (choice.unlocks.isNotEmpty()) {
                                appendLine("**Unlocks:** ${choice.unlocks.joinToString(", ")}")
                                appendLine()
                            }
                            if (choice.locks.isNotEmpty()) {
                                appendLine("**Locks:** ${choice.locks.joinToString(", ")}")
                                appendLine()
                            }
                        }
                        if (point.convergence_point != null) {
                            appendLine("**Convergence Point:** ${point.convergence_point}")
                            appendLine()
                        }
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("**Status:** ✅ Complete")
                }
                branchingMapTask.add(branchingMapContent.renderMarkdown())
                transcript?.write("\n## Branching Map\n\n$branchingMapContent\n\n".toByteArray())
                transcript?.flush()
                task.update()

                saveAnalysisToFile(gameDir, "02_branching_map.md", branchingMapContent)
                resultBuilder.append("## Branching Narrative Map\n\n")
                resultBuilder.append("${gameNarrative.branching_points.size} major decision points with multiple consequences.\n\n")

                overviewTask.add("✅ Phase 3 Complete: Branching map created (${gameNarrative.branching_points.size} points)\n".renderMarkdown())
                task.update()

                // Phase 4: Generate dialogue trees
                if (gameConfig.include_dialogue_trees) {
                    log.info("Phase 4: Generating dialogue trees")
                    overviewTask.add("\n### Phase 4: Dialogue Trees\n*Creating interactive conversations...*\n".renderMarkdown())
                    task.update()

                    val dialogueTask = task.newTask()
                    tabs["Dialogue Trees"] = dialogueTask.placeholder

                    dialogueTask.add(
                      buildString {
                        appendLine("# Dialogue Trees")
                        appendLine()
                        appendLine("**Status:** Generating branching conversations...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    task.update()

                    val dialogueAgent = ParsedAgent(
                        resultClass = DialogueTrees::class.java, prompt = """
You are a dialogue writer for interactive games. Create branching dialogue trees for key conversations.

Game: $gameTitle
Characters: ${gameNarrative.characters.joinToString(", ") { it.name }}

Create ${gameConfig.num_branching_points.coerceAtMost(5)} dialogue trees for important conversations.

For each dialogue tree:
- Unique conversation ID
- Character speaking
- Location/context
- Opening dialogue
- 2-4 player response options
- Each option should have:
  - Unique ID
  - Player's dialogue text
  - Emotional tone (aggressive, diplomatic, curious, humorous, etc.)
  - NPC's response
  - Consequences (relationship changes, unlocked content, etc.)
  - Optional follow-up options (for multi-turn conversations)
- Emotional beats (how the conversation feels)

Make dialogue:
- Natural and character-appropriate
- Reflect player agency level: ${gameConfig.player_agency_level}
- Include meaningful choices that affect relationships
- Match the ${gameConfig.tone} tone
- Support different playstyles

Ensure each character's dialogue matches their established style.
          """.trimIndent(), model = api, temperature = 0.8, parsingChatter = defaultFast
                    )

                    val dialogueTrees = dialogueAgent.answer(listOf("Generate dialogue trees")).obj.trees
                    log.info("Generated ${dialogueTrees.size} dialogue trees")
                    gameDesignData = gameDesignData.copy(dialogue_trees = dialogueTrees)


                    val dialogueContent = buildString {
                        appendLine("## Interactive Conversations")
                        appendLine()
                        dialogueTrees.forEachIndexed { index, tree ->
                            appendLine("### ${index + 1}. ${tree.conversation_id}")
                            appendLine()
                            appendLine("**Character:** ${tree.character}")
                            appendLine("**Location:** ${tree.location}")
                            appendLine()
                            appendLine("**Opening:**")
                            appendLine("> ${tree.root_dialogue}")
                            appendLine()
                            appendLine("**Player Options:**")
                            tree.options.forEach { option ->
                                appendLine()
                                appendLine("#### Option ${option.option_id}: \"${option.text}\"")
                                appendLine("- **Tone:** ${option.tone}")
                                appendLine()
                                appendLine("**Response:**")
                                appendLine("> ${option.response}")
                                appendLine()
                                if (option.consequences.isNotEmpty()) {
                                    appendLine("**Consequences:**")
                                    option.consequences.forEach { consequence ->
                                        appendLine("- $consequence")
                                    }
                                    appendLine()
                                }
                                if (option.next_options != null && option.next_options.isNotEmpty()) {
                                    appendLine("**Follow-up Options:**")
                                    option.next_options.forEach { nextOpt ->
                                        appendLine("- \"${nextOpt.text}\" (${nextOpt.tone})")
                                    }
                                    appendLine()
                                }
                            }
                            if (tree.emotional_beats.isNotEmpty()) {
                                appendLine("**Emotional Beats:** ${tree.emotional_beats.joinToString(" → ")}")
                                appendLine()
                            }
                            appendLine("---")
                            appendLine()
                        }
                        appendLine("**Status:** ✅ Complete")
                    }
                    dialogueTask.add(dialogueContent.renderMarkdown())
                    transcript?.write("\n## Dialogue Trees\n\n$dialogueContent\n\n".toByteArray())
                    transcript?.flush()
                    task.update()

                    saveAnalysisToFile(gameDir, "03_dialogue_trees.md", dialogueContent)
                    resultBuilder.append("## Dialogue Trees\n\n")
                    resultBuilder.append("${dialogueTrees.size} interactive conversations with branching options.\n\n")

                    overviewTask.add("✅ Phase 4 Complete: Dialogue trees created (${dialogueTrees.size} conversations)\n".renderMarkdown())
                    task.update()
                }

                // Phase 5: Generate multiple endings
                log.info("Phase 5: Generating multiple endings")
                overviewTask.add("\n### Phase 5: Multiple Endings\n*Creating ending variations...*\n".renderMarkdown())
                task.update()

                val endingsTask = task.newTask()
                tabs["Endings"] = endingsTask.placeholder

                endingsTask.add(
                  buildString {
                    appendLine("# Multiple Endings")
                    appendLine()
                    appendLine("**Status:** Generating ending variations...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                task.update()

                val endingsContent = buildString {
                    appendLine("## Ending Variations")
                    appendLine()
                    appendLine("The game features **${gameNarrative.endings.size} distinct endings** based on player choices.")
                    appendLine()
                    gameNarrative.endings.forEachIndexed { index, ending ->
                        appendLine("### Ending ${index + 1}: ${ending.title}")
                        appendLine()
                        appendLine("**Description:** ${ending.description}")
                        appendLine()
                        appendLine("**Conditions:**")
                        ending.conditions.forEach { condition ->
                            appendLine("- $condition")
                        }
                        appendLine()
                        appendLine("**Character Fates:**")
                        ending.character_fates.forEach { (char, fate) ->
                            appendLine("- **$char:** $fate")
                        }
                        appendLine()
                        appendLine("**Thematic Resolution:** ${ending.thematic_resolution}")
                        appendLine()
                        if (ending.epilogue != null) {
                            appendLine("**Epilogue:**")
                            appendLine("> ${ending.epilogue}")
                            appendLine()
                        }
                        appendLine("---")
                        appendLine()
                    }
                    appendLine("**Status:** ✅ Complete")
                }
                endingsTask.add(endingsContent.renderMarkdown())
                transcript?.write("\n## Endings\n\n$endingsContent\n\n".toByteArray())
                transcript?.flush()
                task.update()

                saveAnalysisToFile(gameDir, "04_endings.md", endingsContent)
                resultBuilder.append("## Multiple Endings\n\n")
                gameNarrative.endings.forEach { ending ->
                    resultBuilder.append("- **${ending.title}:** ${ending.description.truncateForDisplay(100)}\n")
                }
                resultBuilder.append("\n")

                overviewTask.add("✅ Phase 5 Complete: Endings defined (${gameNarrative.endings.size} variations)\n".renderMarkdown())
                task.update()

                // Phase 6: Generate side quests (if enabled)
                if (gameConfig.include_side_quests) {
                    log.info("Phase 6: Generating side quests")
                    overviewTask.add("\n### Phase 6: Side Quests\n*Creating optional content...*\n".renderMarkdown())
                    task.update()

                    val sideQuestsTask = task.newTask()
                    tabs["Side Quests"] = sideQuestsTask.placeholder

                    sideQuestsTask.add(
                      buildString {
                        appendLine("# Side Quests")
                        appendLine()
                        appendLine("**Status:** Generating optional narrative content...")
                        appendLine()
                      }.renderMarkdown(true)
                    )
                    task.update()

                    val sideQuestAgent = ParsedAgent(
                        resultClass = SideQuests::class.java, prompt = """
You are a quest designer. Create engaging side quests that complement the main narrative.

Game: $gameTitle
Main Characters: ${gameNarrative.characters.joinToString(", ") { it.name }}
Themes: ${gameNarrative.themes.joinToString(", ")}

Create 3-5 side quests that:
- Expand on character backstories
- Explore secondary themes
- Provide optional character development
- Offer meaningful rewards
- Can be discovered through exploration or dialogue
- Have narrative impact (even if optional)

For each quest:
- Unique quest ID
- Compelling title
- Description and story hook
- Quest giver (character name)
- 3-5 objectives
- Rewards (items, relationships, story revelations)
- How it impacts the main narrative
- What unlocks it (optional)

Make quests feel meaningful, not just filler content.
          """.trimIndent(), model = api, temperature = 0.7, parsingChatter = defaultFast
                    )

                    val sideQuests = sideQuestAgent.answer(listOf("Generate side quests")).obj.quests
                    log.info("Generated ${sideQuests.size} side quests")
                    gameDesignData = gameDesignData.copy(side_quests = sideQuests)


                    val sideQuestsContent = buildString {
                        appendLine("## Optional Narrative Content")
                        appendLine()
                        sideQuests.forEachIndexed { index, quest ->
                            appendLine("### ${index + 1}. ${quest.title}")
                            appendLine()
                            appendLine("**Quest ID:** ${quest.quest_id}")
                            appendLine()
                            appendLine("**Description:** ${quest.description}")
                            appendLine()
                            appendLine("**Quest Giver:** ${quest.giver}")
                            appendLine()
                            appendLine("**Objectives:**")
                            quest.objectives.forEach { objective ->
                                appendLine("- $objective")
                            }
                            appendLine()
                            appendLine("**Rewards:**")
                            quest.rewards.forEach { reward ->
                                appendLine("- $reward")
                            }
                            appendLine()
                            appendLine("**Narrative Impact:** ${quest.narrative_impact}")
                            appendLine()
                            if (quest.unlocked_by != null) {
                                appendLine("**Unlocked By:** ${quest.unlocked_by}")
                                appendLine()
                            }
                            appendLine("---")
                            appendLine()
                        }
                        appendLine("**Status:** ✅ Complete")
                    }
                    sideQuestsTask.add(sideQuestsContent.renderMarkdown())
                    transcript?.write("\n## Side Quests\n\n$sideQuestsContent\n\n".toByteArray())
                    transcript?.flush()
                    task.update()

                    saveAnalysisToFile(gameDir, "05_side_quests.md", sideQuestsContent)
                    resultBuilder.append("## Side Quests\n\n")
                    resultBuilder.append("${sideQuests.size} optional quests for expanded storytelling.\n\n")

                    overviewTask.add("✅ Phase 6 Complete: Side quests created (${sideQuests.size} quests)\n".renderMarkdown())
                    task.update()
                }

                // Phase 7: Player agency analysis
                log.info("Phase 7: Analyzing player agency")
                overviewTask.add("\n### Phase 7: Player Agency Analysis\n*Evaluating meaningful choices...*\n".renderMarkdown())
                task.update()

                val agencyTask = task.newTask()
                tabs["Player Agency"] = agencyTask.placeholder

                agencyTask.add(
                  buildString {
                    appendLine("# Player Agency Analysis")
                    appendLine()
                    appendLine("**Status:** Analyzing choice impact and replayability...")
                    appendLine()
                  }.renderMarkdown(true)
                )
                task.update()

                val agencyAgent = ChatAgent(
                    prompt = """
You are a game design analyst. Analyze the player agency in this game narrative.

Game: $gameTitle
Player Agency Level: ${gameConfig.player_agency_level}
Branching Points: ${gameNarrative.branching_points.size}
Endings: ${gameNarrative.endings.size}

Branching Points Summary:
${gameNarrative.branching_points.joinToString("\n") { "- ${it.id}: ${it.choices.size} choices" }}

Analyze:
1. **Meaningful Choices**: Which decisions truly impact the story?
2. **Illusion of Choice**: Are there any choices that feel meaningful but don't significantly change outcomes?
3. **Consequence Visibility**: How clearly can players see the results of their choices?
4. **Replayability Factors**: What encourages players to replay and make different choices?
5. **Agency Balance**: Does the game achieve its target agency level (${gameConfig.player_agency_level})?
6. **Choice Distribution**: Are choices well-distributed throughout the narrative?
7. **Convergence vs. Divergence**: How well does the narrative balance branching and converging paths?

Provide specific examples and recommendations for improvement.
        """.trimIndent(), model = api, temperature = 0.6
                )

                val agencyAnalysis = agencyAgent.answer(listOf("Analyze player agency"))
                log.info("Player agency analysis complete: ${agencyAnalysis.length} characters")
                gameDesignData = gameDesignData.copy(player_agency_analysis = agencyAnalysis)


                val agencyContent = buildString {
                    appendLine("## Player Agency Evaluation")
                    appendLine()
                    appendLine(agencyAnalysis)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("**Status:** ✅ Complete")
                }
                agencyTask.add(agencyContent.renderMarkdown())
                transcript?.write("\n## Player Agency Analysis\n\n$agencyContent\n\n".toByteArray())
                transcript?.flush()
                task.update()

                saveAnalysisToFile(gameDir, "06_player_agency_analysis.md", agencyContent)
                resultBuilder.append("## Player Agency\n\n")
                resultBuilder.append(agencyAnalysis.truncateForDisplay(500))
                resultBuilder.append("\n\n")

                overviewTask.add("✅ Phase 7 Complete: Player agency analyzed\n".renderMarkdown())
                task.update()

                // Phase 8: Generate design document
                log.info("Phase 8: Compiling design document")
                overviewTask.add("\n### Phase 8: Final Design Document\n*Compiling complete documentation...*\n".renderMarkdown())
                task.update()

                val designDocTask = task.newTask()
                tabs["Design Document"] = designDocTask.placeholder

                val designDocument = buildString {
                    appendLine("# Game Narrative Design Document")
                    appendLine("## ${gameNarrative.title}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## Executive Summary")
                    appendLine()
                    appendLine("**Genre:** ${gameConfig.genre}")
                    appendLine("**Narrative Style:** ${gameConfig.narrative_style}")
                    appendLine("**Player Agency:** ${gameConfig.player_agency_level}")
                    appendLine("**Estimated Playtime:** ${gameConfig.estimated_playtime_hours} hours")
                    appendLine("**Tone:** ${gameConfig.tone}")
                    appendLine()
                    appendLine("**Premise:** ${gameNarrative.premise}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## Table of Contents")
                    appendLine()
                    appendLine("1. Story Overview")
                    appendLine("2. Three-Act Structure")
                    appendLine("3. Main Characters")
                    if (gameConfig.include_game_mechanics) appendLine("4. Game Mechanics")
                    appendLine("... (see full document for details)")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(gameStructureContent)
                    appendLine()
                    if (gameConfig.include_game_mechanics) {
                        appendLine(gameDesignData.mechanics?.let {
                            "## Game Mechanics\n\n${it.core_loop}\n\n"
                        } ?: "")
                    }
                    appendLine(branchingMapContent)
                    appendLine()
                    appendLine(endingsContent)
                    appendLine()
                    appendLine(agencyContent)
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine("## Implementation Notes")
                    appendLine()
                    appendLine("### Technical Requirements")
                    appendLine("- Branching dialogue system")
                    appendLine("- Choice tracking and consequence system")
                    appendLine("- Character relationship system")
                    appendLine("- Multiple ending conditions")
                    appendLine("- Save/load with choice persistence")
                    appendLine()
                    appendLine("### Content Metrics")
                    appendLine("- Main Characters: ${gameNarrative.characters.size}")
                    appendLine("- Branching Points: ${gameNarrative.branching_points.size}")
                    appendLine("- Endings: ${gameNarrative.endings.size}")
                    appendLine("- Estimated Word Count: ${gameConfig.target_word_count}")
                    appendLine()
                    appendLine("---")
                    appendLine()
                    appendLine(
                        "**Document Generated:** ${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                        }"
                    )
                }


                designDocTask.add(designDocument.renderMarkdown())
                task.update()

                val docUrl =
                    task.saveFile(".game_narrative_design/00_complete_design_document.md", designDocument.toByteArray())
                // Save structured JSON data
                try {
                    val jsonContent = JsonUtil.toJson(gameDesignData)
                    val jsonFile =
                        task.saveFile(".game_narrative_design/game_design_data.json", jsonContent.toByteArray())
                    log.debug("Saved structured game design data to: ${jsonFile}")
                } catch (e: Exception) {
                    log.error("Failed to save game design JSON", e)
                }


                // Final statistics
                val totalTime = System.currentTimeMillis() - startTime

                overviewTask.add(
                    buildString {
                        appendLine()
                        appendLine("---")
                        appendLine()
                        appendLine("## ✅ Design Complete")
                        appendLine()
                        appendLine("**Statistics:**")
                        appendLine("- Characters: ${gameNarrative.characters.size}")
                        appendLine("- Branching Points: ${gameNarrative.branching_points.size}")
                        appendLine("- Endings: ${gameNarrative.endings.size}")
                        appendLine("- Total Time: ${totalTime / 1000.0}s")
                        appendLine()
                        appendLine("**Files Generated:**")
                        appendLine("- Complete Design Document")
                        appendLine("- Structured Game Design Data (JSON)")
                        appendLine("- Game Structure")
                        appendLine("- Branching Map")
                        if (gameConfig.include_game_mechanics) appendLine("- Game Mechanics")
                        if (gameConfig.include_dialogue_trees) appendLine("- Dialogue Trees")
                        appendLine("- Endings")
                        if (gameConfig.include_side_quests) appendLine("- Side Quests")
                        appendLine("- Player Agency Analysis")
                        appendLine()
                        appendLine(
                            "**Completed:** ${
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            }"
                        )
                    }.renderMarkdown()
                )
                task.update()

                transcript?.write("\n## Final Statistics\n\n- Total Time: ${totalTime / 1000.0}s\n- Characters: ${gameNarrative.characters.size}\n- Branching Points: ${gameNarrative.branching_points.size}\n- Endings: ${gameNarrative.endings.size}\n\n".toByteArray())
                transcript?.write(
                    "<details><summary>Full Design Data (JSON)</summary>\n\n```json\n${
                        JsonUtil.toJson(
                            gameDesignData
                        )
                    }\n```\n</details>".toByteArray()
                )


                val finalResult = buildString {
                    appendLine("# Game Narrative Design Complete: $gameTitle")
                    appendLine()
                    appendLine("A complete interactive game narrative with **${gameNarrative.characters.size} characters**, **${gameNarrative.branching_points.size} branching points**, and **${gameNarrative.endings.size} endings** was created in **${totalTime / 1000.0}s**.")
                    appendLine()
                    appendLine("## Key Features")
                    appendLine("- **Genre:** ${gameConfig.genre}")
                    appendLine("- **Narrative Style:** ${gameConfig.narrative_style}")
                    appendLine("- **Player Agency:** ${gameConfig.player_agency_level}")
                    appendLine("- **Estimated Playtime:** ${gameConfig.estimated_playtime_hours} hours")
                    appendLine()
                    appendLine("## Documentation")
                    appendLine("Complete design documentation is available at: $docUrl")
                    appendLine("- Complete Design Document")
                    appendLine("- Structured Game Design Data (JSON)")
                    appendLine("- Game Structure & Characters")
                    if (gameConfig.include_game_mechanics) appendLine("- Game Mechanics & Systems")
                    appendLine("- Branching Narrative Map")
                    if (gameConfig.include_dialogue_trees) appendLine("- Interactive Dialogue Trees")
                    appendLine("- Multiple Ending Variations")
                    if (gameConfig.include_side_quests) appendLine("- Side Quest Designs")
                    appendLine("- Player Agency Analysis")
                    appendLine()
                    appendLine("> Full details and interactive elements are available in the UI tabs.")
                }

                log.info("GameNarrativeDesignTask completed: characters=${gameNarrative.characters.size}, branching_points=${gameNarrative.branching_points.size}, endings=${gameNarrative.endings.size}, time=${totalTime}ms")

                task.safeComplete(
                    "Game narrative design complete: ${gameNarrative.characters.size} characters, ${gameNarrative.branching_points.size} branching points, ${gameNarrative.endings.size} endings in ${totalTime / 1000}s",
                    log
                )
                resultFn(finalResult)

            } catch (e: Exception) {
                log.error("Error during game narrative design", e)
                // Triple Log Rule
                task.error(e)
                transcript?.write(
                    """
                    <details>
                    <summary>Stack Trace</summary>
                    ```
                    ${e.stackTraceToString()}
                    ```
                    </details>
                """.trimIndent().toByteArray()
                )

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
                    }.renderMarkdown()
                )
                task.update()

                val errorOutput = buildString {
                    appendLine("# Error in Game Narrative Design")
                    appendLine()
                    appendLine("**Game:** $gameTitle")
                    appendLine()
                    appendLine("**Error:** ${e.message}")
                    appendLine()
                    if (resultBuilder.isNotEmpty()) {
                        appendLine("## Partial Results")
                        appendLine()
                        appendLine(resultBuilder.toString())
                    }
                }
                resultFn(errorOutput)

            } finally {
                transcript?.close()
            }
            transcript?.close()
        }
    }


    private fun saveAnalysisToFile(outputDir: File, filename: String, content: String) {
        try {
            val outputFile = File(outputDir, filename)
            outputFile.writeText(content, StandardCharsets.UTF_8)
            log.debug("Saved analysis to file: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            log.error("Failed to save analysis to file: $filename", e)
        }
    }

    private fun generateCharacterPortrait(
        task: SessionTask,
        tabs: TabbedDisplay,
        character: GameCharacter,
        index: Int,
        gameDir: File,
        transcript: OutputStream?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating portrait for character: ${character.name}")
            val portraitTask = task.newTask()
            tabs["Portrait: ${character.name}"] = portraitTask.placeholder

            portraitTask.add(
                buildString {
                    appendLine("# Character Portrait: ${character.name}")
                    appendLine()
                    appendLine("**Status:** Generating character portrait...")
                    appendLine()
                }.renderMarkdown()
            )
            task.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a detailed character portrait for a game character",
                model = orchestrationConfig.defaultImage,
                temperature = 0.7,
            )

            val portraitPrompt = buildString {
                append("Character portrait: ${character.name}, ${character.role}. ")
                append("${character.motivations.firstOrNull() ?: ""}. ")
                append("Style: game character art, detailed, professional.")
            }

            val result = imageAgent.answer(listOf(ImageAndText(portraitPrompt)))
            val image = result.image

            val relativePath = "character_${index + 1}_${character.name.replace(" ", "_")}.png"
            val imageFile = task.resolveUserFile(relativePath)!!
            ImageIO.write(image, "png", imageFile)
            log.debug("Saved character portrait to: ${imageFile.absolutePath}")

            val link = task.linkTo(relativePath)
            val imageHtml = """
        <div class='character-portrait'>
          <h3>${character.name}</h3>
          <p><strong>Role:</strong> ${character.role}</p>
          <p><strong>Arc:</strong> ${character.arc}</p>
          <p><strong>Image Prompt:</strong> ${result.text}</p>
          <a href='$link' target='_blank'>
            <img src='$link' alt='${character.name}' style='max-width: 400px; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1);' />
          </a>
        </div>
      """.trimIndent()



            portraitTask.add(imageHtml.renderMarkdown())
            task.update()

            transcript?.write(
                """
                ### Character Portrait: ${character.name}
                **Prompt:** ${result.text}
                
                ![${character.name}]($link)
                
            """.trimIndent().transcriptFilter().toByteArray()
            )
            transcript?.flush()

            portraitTask.add("\n**Status:** ✅ Complete\n".renderMarkdown())
            task.update()

        } catch (e: Exception) {
            log.error("Failed to generate character portrait for ${character.name}", e)
            transcript?.write("**Character Portrait Generation Failed:** ${e.message}\n\n".toByteArray())
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameNarrativeDesignTask::class.java)

        @JvmStatic val GameNarrativeDesign = TaskType(
            name = "GameNarrativeDesign",
            category = "Games",
            taskClass = GameNarrativeDesignTask::class.java,
            executionConfigClass = GameNarrativeDesignConfigData::class.java,
            taskSettingsClass = TaskTypeConfig::class.java,
            description = "Create interactive game narratives with branching storylines",
            tooltipHtml = """
                        Creates complete game narrative designs with interactive elements and player agency.
                        <ul>
                          <li>Extends NarrativeGeneration with game-specific features</li>
                          <li>Three-act structure adapted for interactive media</li>
                          <li>Multiple branching points with meaningful choices</li>
                          <li>Character arcs that respond to player decisions</li>
                          <li>Branching dialogue trees with emotional beats</li>
                          <li>Multiple endings based on player choices</li>
                          <li>Optional side quests and expanded content</li>
                          <li>Player agency analysis and replayability factors</li>
                          <li>Complete design documentation for implementation</li>
                          <li>Ideal for RPGs, adventure games, visual novels, interactive fiction</li>
                        </ul>
                      """,
        )
    }
}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the new `README.md` for the game design tools package, summarizing the functionality of the included tasks.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/README.md
```markdown
# Game Design Tools

This package contains a suite of AI-powered tools designed to assist in various aspects of game development, from core mechanics and economy to level design and narrative structure. These tools help automate the creation of detailed design documentation, balance analysis, and content generation.

## Tools

### [Game Economy Design](GameEconomyTask.kt)
Designs comprehensive game economy systems with balanced progression and monetization strategies.
- **Resource Systems**: Creates multi-resource economic systems with generation sources and consumption sinks.
- **Progression Curves**: Designs experience and level systems with balanced progression styles (linear, branching, etc.).
- **Skill Trees**: Builds talent systems and skill branches with synergies.
- **Loot Tables**: Creates balanced drop rates, rarity distributions, and pity systems.
- **Monetization**: Designs strategies for various models (F2P, premium, subscription) while assessing pay-to-win risks.
- **Engagement**: Implements daily rewards, seasonal content, and battle passes.
- **Forecasting**: Projects economy health, inflation rates, and player progression over 3-12 month periods.

### [Game Level Design](GameLevelDesignTask.kt)
Generates production-ready game level designs with comprehensive documentation, layout visualization, and pacing analysis.
- **Layout Generation**: Creates detailed level layouts with zones, connections, and ASCII visualizations.
- **Encounter Design**: Places enemies, puzzles, and boss encounters with appropriate difficulty progression.
- **Pacing Analysis**: Visualizes intensity curves to ensure engaging gameplay flow and strategic rest points.
- **Content Placement**: Strategically places collectibles and secret areas to reward exploration.
- **Player Guidance**: Designs implicit (environmental) and explicit (markers) guidance systems.
- **Difficulty Variants**: Generates variants for different skill levels to improve accessibility.

### [Game Mechanics Design](GameMechanicsDesignTask.kt)
Generates and analyzes core gameplay mechanics to ensure balanced and engaging systems.
- **Core Loops**: Designs moment-to-moment gameplay loops from high-level concepts.
- **Interaction Analysis**: Evaluates synergies and conflicts between different mechanics using interaction matrices.
- **Balance Evaluation**: Analyzes win rate variance, strategy diversity, and skill vs. luck expression.
- **Playtesting Simulation**: Predicts player behavior, engagement, and frustration points across different scenarios.
- **Tuning Guides**: Provides actionable parameters for difficulty settings and reward scaling.

### [Game Narrative Design](GameNarrativeDesignTask.kt)
Creates interactive game narratives with branching storylines, deep player agency, and character development.
- **Interactive Structure**: Adapts traditional narrative structures for non-linear, interactive media.
- **Branching Paths**: Designs major decision points with meaningful consequences and convergence logic.
- **Character Development**: Creates character arcs and reactions that respond to player choices.
- **Dialogue Trees**: Generates complex, branching conversations with emotional beats and relationship impacts.
- **Multiple Endings**: Defines various resolutions and character fates based on player actions.
- **Side Quests**: Designs optional content that expands the game world and explores secondary themes.

## Usage
These tools can be used independently or in sequence to build a cohesive game design document. They support various genres including RPGs, platformers, shooters, strategy, and idle games. By providing high-level concepts and constraints, designers can quickly iterate on complex systems and receive detailed analysis of their designs.

---
*Part of the Cognotik Plan Tools suite.*
```

### Summary of Changes
- Created a new `README.md` in the `com.simiacryptus.cognotik.plan.tools.games` package directory.
- Provided a high-level overview of the game design toolset.
- Included detailed descriptions for `GameEconomy`, `GameLevelDesign`, `GameMechanicsDesign`, and `GameNarrativeDesign` based on their implementation details.
- Highlighted key features such as economy forecasting, ASCII level visualization, balance metrics, and branching narrative structures.
- Linked the descriptions to the corresponding Kotlin source files for easy navigation.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/games/README.md</a> Updated
