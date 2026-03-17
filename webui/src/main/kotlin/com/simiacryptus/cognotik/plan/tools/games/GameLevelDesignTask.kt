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

      val transcript = task.newUserFileStream(transcriptFile())

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