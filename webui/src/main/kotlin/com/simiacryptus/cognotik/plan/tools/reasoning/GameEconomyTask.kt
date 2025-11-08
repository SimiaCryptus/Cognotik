package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class GameEconomyTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GameEconomyTaskExecutionConfigData?
) : AbstractTask<GameEconomyTask.GameEconomyTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    val maxOutputLengthPerField = 10000

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameEconomyTask::class.java)
        val GameEconomy = TaskType(
            "GameEconomy",
            GameEconomyTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Design complete game economic systems with progression and monetization",
            """
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
            """
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

    class GameEconomyTaskExecutionConfigData(
        @Description("The title of the game")
        val game_title: String? = null,
        @Description("Type of game: RPG, strategy, idle, multiplayer")
        val game_type: String? = "RPG",
        @Description("Progression style: linear, branching, open")
        val progression_style: String? = "linear",
        @Description("Number of resource types (2-10)")
        val num_resources: Int = 3,
        @Description("Number of progression tiers/levels (5-100)")
        val num_progression_tiers: Int = 50,
        @Description("Whether to include a skill tree system")
        val include_skill_tree: Boolean = true,
        @Description("Whether to include crafting system")
        val include_crafting: Boolean = false,
        @Description("Whether to include player trading")
        val include_trading: Boolean = false,
        @Description("Monetization model: free-to-play, premium, subscription")
        val monetization_model: String? = "free-to-play",
        @Description("Whether to include daily rewards")
        val include_daily_rewards: Boolean = true,
        @Description("Whether to include seasonal content")
        val include_seasonal_content: Boolean = true,
        @Description("Whether to include battle pass system")
        val include_battle_pass: Boolean = true,
        @Description("Number of months to forecast (3-12)")
        val forecast_months: Int = 6,
        @Description("Whether to generate detailed balance report")
        val generate_balance_report: Boolean = true,
        @Description("Additional context or design constraints")
        val additional_context: String? = null,
        @Description("The specific files (or file patterns, e.g. **/*.kt) to be used as input for the task")
        val input_files: List<String>? = null,
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
        log.info("Starting GameEconomy task for: ${executionConfig?.game_title}")
        val toInput = { it: String -> messages + listOf(getInputFileCode(), it).filter { it.isNotBlank() } }

        val gameTitle = executionConfig?.game_title
        if (gameTitle.isNullOrBlank()) {
            val errorMsg = "CONFIGURATION ERROR: No game title specified"
            log.error(errorMsg)
            task.safeComplete(errorMsg, log)
            resultFn(errorMsg)
            return
        }

        val ui = task.ui
        val api = orchestrationConfig.defaultChatter ?: return
        val transcript = transcript(task)

        // Create tabbed display for organized output
        val tabs = TabbedDisplay(task)

        // Overview tab
        val overviewTask = task.ui.newTask(false)
        tabs["Overview"] = overviewTask.placeholder

        try {
            transcript?.write("# Game Economy Design: $gameTitle\n\n".toByteArray())
            transcript?.write(
                "**Started:** ${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                }\n".toByteArray()
            )

            var overviewTaskStatus = overviewTask.add(
                MarkdownUtil.renderMarkdown(
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
        """.trimMargin(), ui = ui
                )
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
            |## Game Economy Design
            |
            |**Game:** $gameTitle
            |
            |**Type:** ${executionConfig.game_type}
            |
            |**Progression Style:** ${executionConfig.progression_style}
            |
            |**Status:** 🔄 Designing resource system...
        """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 1: Design resource system
            var stepStartTime = System.currentTimeMillis()
            log.debug("Designing resource system")
            val resourceTask = task.ui.newTask(false)
            tabs["Resources"] = resourceTask.placeholder
            val resourceLoading = resourceTask.add(
                MarkdownUtil.renderMarkdown("## Resource System\n\n🔄 Designing resource types and flows...", ui = ui)
            )
            task.update()

            val resourcePrompt = buildResourcePrompt(gameTitle, contextBuilder.toString())
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

            resourceLoading?.clear()
            resourceTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Resource System Design
            |
            |✅ Design complete
            |
            |$resourceAnalysis
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 2: Design progression system
            stepStartTime = System.currentTimeMillis()
            log.debug("Designing progression system")
            val progressionTask = task.ui.newTask(false)
            tabs["Progression"] = progressionTask.placeholder
            val progressionLoading = progressionTask.add(
                MarkdownUtil.renderMarkdown("## Progression System\n\n🔄 Designing level curves and unlocks...", ui = ui)
            )
            task.update()

            val progressionPrompt = """
Based on the resource system above, design a comprehensive progression system for $gameTitle.

Create a progression system with:
- ${executionConfig.num_progression_tiers} levels/tiers
- Experience curve (XP required per level)
- Unlock schedule (what unlocks at each level)
- Milestone rewards
- Estimated time to reach max level

For the experience curve, use a ${executionConfig.progression_style} progression style.
Consider:
- Early game should feel rewarding (faster progression)
- Mid game should establish rhythm
- Late game should provide long-term goals
- Balance between casual and hardcore players

If skill trees are enabled (${executionConfig.include_skill_tree}), design:
- Number of skill branches
- Skills per branch
- Synergies between skills
- Respec mechanics

Generate the progression system design now:
        """.trimIndent()

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

            progressionLoading?.clear()
            progressionTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Progression System Design
            |
            |✅ Design complete
            |
            |$progressionAnalysis
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 3: Design loot and reward system
            stepStartTime = System.currentTimeMillis()
            log.debug("Designing loot system")
            val lootTask = task.ui.newTask(false)
            tabs["Loot & Rewards"] = lootTask.placeholder
            val lootLoading = lootTask.add(
                MarkdownUtil.renderMarkdown(
                    "## Loot & Reward System\n\n🔄 Designing loot tables and drop rates...",
                    ui = ui
                )
            )
            task.update()

            val lootPrompt = """
Based on the resource and progression systems above, design a loot and reward system for $gameTitle.

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
        """.trimIndent()

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

            lootLoading?.clear()
            lootTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Loot & Reward System Design
            |
            |✅ Design complete
            |
            |$lootAnalysis
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 4: Design monetization strategy
            stepStartTime = System.currentTimeMillis()
            log.debug("Designing monetization strategy")
            val monetizationTask = task.ui.newTask(false)
            tabs["Monetization"] = monetizationTask.placeholder
            val monetizationLoading = monetizationTask.add(
                MarkdownUtil.renderMarkdown("## Monetization Strategy\n\n🔄 Designing monetization approach...", ui = ui)
            )
            task.update()

            val monetizationPrompt = """
Based on the complete economy design above, create a monetization strategy for $gameTitle.

Monetization Model: ${executionConfig.monetization_model}

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

2. **Battle Pass** (if enabled: ${executionConfig.include_battle_pass}):
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
        """.trimIndent()

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

            monetizationLoading?.clear()
            monetizationTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Monetization Strategy Design
            |
            |✅ Design complete
            |
            |$monetizationAnalysis
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 5: Design engagement systems
            stepStartTime = System.currentTimeMillis()
            log.debug("Designing engagement systems")
            val engagementTask = task.ui.newTask(false)
            tabs["Engagement"] = engagementTask.placeholder
            val engagementLoading = engagementTask.add(
                MarkdownUtil.renderMarkdown("## Engagement Systems\n\n🔄 Designing retention mechanics...", ui = ui)
            )
            task.update()

            val engagementPrompt = """
Based on the complete economy design above, create engagement systems for $gameTitle.

Design engagement hooks that encourage regular play without feeling manipulative:

1. **Daily Rewards** (if enabled: ${executionConfig.include_daily_rewards}):
   - Reward structure
   - Streak bonuses
   - Catch-up mechanics
   - Value scaling

2. **Seasonal Content** (if enabled: ${executionConfig.include_seasonal_content}):
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
        """.trimIndent()

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

            engagementLoading?.clear()
            engagementTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Engagement Systems Design
            |
            |✅ Design complete
            |
            |$engagementAnalysis
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 6: Generate economy forecast
            stepStartTime = System.currentTimeMillis()
            log.debug("Generating economy forecast")
            val forecastTask = task.ui.newTask(false)
            tabs["Forecast"] = forecastTask.placeholder
            val forecastLoading = forecastTask.add(
                MarkdownUtil.renderMarkdown("## Economy Forecast\n\n🔄 Projecting economy health...", ui = ui)
            )
            task.update()

            val forecastPrompt = """
Based on the complete economy design above, generate a ${executionConfig.forecast_months}-month forecast for $gameTitle.

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

Generate the ${executionConfig.forecast_months}-month economy forecast now:
        """.trimIndent()

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

            forecastLoading?.clear()
            forecastTask.add(
                MarkdownUtil.renderMarkdown(
                    """
            |## Economy Forecast (${executionConfig.forecast_months} months)
            |
            |✅ Forecast complete
            |
            |$forecastAnalysis
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Step 7: Generate balance report (if enabled)
            var balanceReport = ""
            if (executionConfig.generate_balance_report) {
                stepStartTime = System.currentTimeMillis()
                log.debug("Generating balance report")
                val balanceTask = task.ui.newTask(false)
                tabs["Balance Report"] = balanceTask.placeholder
                val balanceLoading = balanceTask.add(
                    MarkdownUtil.renderMarkdown("## Balance Report\n\n🔄 Analyzing economy balance...", ui = ui)
                )
                task.update()

                val balancePrompt = """
Based on the complete economy design and forecast above, generate a comprehensive balance report for $gameTitle.

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
        """.trimIndent()

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

                balanceLoading?.clear()
                balanceTask.add(
                    MarkdownUtil.renderMarkdown(
                        """
              |## Balance Report
              |
              |✅ Report complete
              |
              |$balanceReport
              """.trimMargin(), ui = ui
                    )
                )
                task.update()
            }

            // Step 8: Generate structured summary using ParsedAgent
            stepStartTime = System.currentTimeMillis()
            log.debug("Generating structured summary")
            val summaryTask = task.ui.newTask(false)
            tabs["Summary"] = summaryTask.placeholder
            val summaryLoading = summaryTask.add(
                MarkdownUtil.renderMarkdown("## Summary\n\n🔄 Generating comprehensive summary...", ui = ui)
            )
            task.update()

            val summaryPrompt = """
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

            val parsedAgent = ParsedAgent(
                resultClass = GameEconomyConfig::class.java,
                prompt = summaryPrompt,
                model = api,
                temperature = 0.2,
                parsingChatter = orchestrationConfig.parsingChatter,
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

            summaryLoading?.clear()
            summaryTask.add(
                MarkdownUtil.renderMarkdown(
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
            """.trimMargin(), ui = ui
                )
            )
            task.update()

            // Update overview with completion
            overviewTaskStatus?.clear()
            overviewTask.add(
                MarkdownUtil.renderMarkdown(
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
        """.trimMargin(), ui = ui
                )
            )
            task.update()

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
                MarkdownUtil.renderMarkdown(
                    """
            |## Game Economy Design
            |
            |**Status:** ❌ Design Failed
            |
            |**Error:** ${e.message}
            """.trimMargin(), ui = ui
                )
            )
            task.update()
            transcript?.write("\n---\n**ERROR:** ${e.message}\n".toByteArray())
            transcript?.close()
            task.error(e)
            task.safeComplete("Design failed: ${e.message}", log)
            resultFn("ERROR: Game economy design failed - ${e.message}")
        }
    }

    private fun getInputFileCode() = (executionConfig?.input_files ?: listOf())
        .flatMap { pattern: String ->
            val matcher = java.nio.file.FileSystems.getDefault().getPathMatcher("glob:$pattern")
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

    private fun buildResourcePrompt(
        gameTitle: String,
        context: String
    ): String {
        return """
You are an expert game economy designer. Your task is to design a comprehensive resource system for a game.

## Game Information:
**Title:** $gameTitle
**Type:** ${executionConfig?.game_type}
**Progression Style:** ${executionConfig?.progression_style}

## Design Requirements:
- Number of resources: ${executionConfig?.num_resources}
- Include crafting: ${executionConfig?.include_crafting}
- Include trading: ${executionConfig?.include_trading}

$context

## Resource System Design Instructions:

Design ${executionConfig?.num_resources} distinct resource types that work together to create a balanced economy.

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
        """.trimIndent()
    }

    private fun transcript(task: SessionTask): FileOutputStream? {
        val transcriptFile = "transcript_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
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