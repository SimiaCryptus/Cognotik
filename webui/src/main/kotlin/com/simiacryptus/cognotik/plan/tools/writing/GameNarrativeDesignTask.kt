package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.agents.ImageAndText
import com.simiacryptus.cognotik.agents.ImageProcessingAgent
import com.simiacryptus.cognotik.agents.ParsedAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.TaskOrchestrator
import com.simiacryptus.cognotik.plan.TaskType
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.safeComplete
import com.simiacryptus.cognotik.plan.tools.reasoning.truncateForDisplay
import com.simiacryptus.cognotik.plan.tools.reasoning.validateAndGetApi
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.ValidatedObject
import com.simiacryptus.cognotik.webui.chat.transcriptFilter
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.BufferedWriter
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.imageio.ImageIO

class GameNarrativeDesignTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: GameNarrativeDesignConfigData?
) : NarrativeGenerationTask<GameNarrativeDesignTask.GameNarrativeDesignConfigData>(
    orchestrationConfig,
    planTask
) {

    class GameNarrativeDesignConfigData(
        @Description("The title of the game")
        val game_title: String? = null,

        @Description("Game genre (e.g., 'RPG', 'adventure', 'visual novel', 'action-adventure')")
        val genre: String = "RPG",

        @Description("Narrative style: 'linear', 'branching', or 'open-ended'")
        val narrative_style: String = "branching",

        @Description("Player agency level: 'low', 'medium', or 'high'")
        val player_agency_level: String = "high",

        @Description("Number of main characters (1-10)")
        val num_main_characters: Int = 4,

        @Description("Number of major branching points (3-15)")
        val num_branching_points: Int = 8,

        @Description("Number of possible endings (1-10)")
        val num_endings: Int = 4,

        @Description("Whether to include detailed dialogue trees")
        val include_dialogue_trees: Boolean = true,

        @Description("Whether to include character arc development")
        val include_character_arcs: Boolean = true,

        @Description("Whether to include side quest narratives")
        val include_side_quests: Boolean = true,

        @Description("Overall tone (e.g., 'dark', 'heroic', 'comedic', 'mysterious')")
        tone: String = "heroic",

        @Description("Player's role: 'protagonist', 'observer', or 'custom'")
        val player_role: String = "protagonist",

        @Description("Estimated playtime in hours (1-100)")
        val estimated_playtime_hours: Int = 20,

        @Description("Game setting/world description")
        val setting: String? = null,

        @Description("Core themes to explore")
        val themes: List<String>? = null,

        @Description("Whether to generate character portraits")
        val generate_character_portraits: Boolean = false,

        @Description("Whether to generate scene concept art")
        val generate_scene_art: Boolean = false,

        @Description("Input files for context (e.g., world lore, character bios)")
        input_files: List<String>? = null,

        task_dependencies: List<String>? = null,
        state: TaskState? = TaskState.Pending,
    ) : NarrativeGenerationTaskExecutionConfigData(
        subject = game_title,
        input_files = input_files,
        narrative_elements = buildNarrativeElements(
            genre, narrative_style, player_agency_level, num_main_characters,
            tone, player_role, setting, themes
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
        image_model = "DallE3",
        image_width = 1024,
        image_height = 1024,
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

    override fun promptSegment(): String {
        return """
GameNarrativeDesign - Create interactive game narratives with branching storylines
  ** Extends NarrativeGeneration with game-specific features
  ** Specify game title, genre, and narrative style
  ** Define player agency level and role
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
        val startTime = System.currentTimeMillis()
        val gameConfig = executionConfig as? GameNarrativeDesignConfigData
        log.info("Starting GameNarrativeDesignTask for game: '${gameConfig?.game_title}'")

        if (gameConfig == null) {
            log.error("Invalid configuration type for GameNarrativeDesignTask")
            task.safeComplete("CONFIGURATION ERROR: Invalid configuration type", log)
            resultFn("CONFIGURATION ERROR: Invalid configuration type")
            return
        }

        val gameTitle = gameConfig.game_title
        if (gameTitle.isNullOrBlank()) {
            log.error("No game title specified")
            task.safeComplete("CONFIGURATION ERROR: No game title specified", log)
            resultFn("CONFIGURATION ERROR: No game title specified")
            return
        }

        val api = validateAndGetApi(orchestrationConfig, task, log, resultFn) ?: return

        val tabs = TabbedDisplay(task)
        val transcript = createTranscript(task, gameTitle)

        // Create game design directory
        val gameDir = File(agent.root.toFile(), ".game_narrative_design")
        if (!gameDir.exists()) {
            gameDir.mkdirs()
            log.debug("Created game narrative design directory: ${gameDir.absolutePath}")
        }

        // Overview tab
        val overviewTask = task.ui.newTask(false)
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
            appendLine("**Started:** ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))}")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine("## Progress")
            appendLine()
            appendLine("### Phase 1: Base Narrative Generation")
            appendLine("*Generating core narrative structure...*")
        }
        overviewTask.add(overviewContent.renderMarkdown)
        transcript?.write("# Game Narrative Design: $gameTitle\n\n$overviewContent\n\n")
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

            overviewTask.add("\n✅ Phase 1 Complete: Base narrative generated\n".renderMarkdown)
            overviewTask.add("\n### Phase 2: Game Structure Analysis\n*Analyzing for interactive elements...*\n".renderMarkdown)
            task.update()

            // Phase 2: Generate game-specific structure
            log.info("Phase 2: Generating game structure")
            val gameStructureTask = task.ui.newTask(false)
            tabs["Game Structure"] = gameStructureTask.placeholder

            gameStructureTask.add(
                buildString {
                    appendLine("# Game Narrative Structure")
                    appendLine()
                    appendLine("**Status:** Analyzing narrative for game mechanics...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val gameStructureAgent = ParsedAgent(
                resultClass = GameNarrative::class.java,
                prompt = """
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
          """.trimIndent(),
                model = api,
                temperature = 0.7,
                parsingChatter = orchestrationConfig.parsingChatter
            )

            val gameNarrative = gameStructureAgent.answer(listOf("Generate game structure")).obj
            log.info("Generated game structure: ${gameNarrative.characters.size} characters, ${gameNarrative.branching_points.size} branching points, ${gameNarrative.endings.size} endings")

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
            gameStructureTask.add(gameStructureContent.renderMarkdown)
            transcript?.write("\n## Game Structure\n\n$gameStructureContent\n\n")
            transcript?.flush()
            task.update()

            saveAnalysisToFile(gameDir, "01_game_structure.md", gameStructureContent)
            resultBuilder.append("## Game Structure\n\n$gameStructureContent\n\n")

            overviewTask.add("✅ Phase 2 Complete: Game structure defined\n".renderMarkdown)
            task.update()

            // Generate character portraits if enabled
            if (gameConfig.generate_character_portraits) {
                gameNarrative.characters.take(gameConfig.num_main_characters).forEachIndexed { index, char ->
                    generateCharacterPortrait(
                        task = task,
                        tabs = tabs,
                        character = char,
                        index = index,
                        gameDir = gameDir,
                        transcriptWriter = transcript,
                        orchestrationConfig = orchestrationConfig
                    )
                }
            }

            // Phase 3: Generate branching narrative map
            log.info("Phase 3: Generating branching narrative map")
            overviewTask.add("\n### Phase 3: Branching Narrative Map\n*Creating decision tree...*\n".renderMarkdown)
            task.update()

            val branchingMapTask = task.ui.newTask(false)
            tabs["Branching Map"] = branchingMapTask.placeholder

            branchingMapTask.add(
                buildString {
                    appendLine("# Branching Narrative Map")
                    appendLine()
                    appendLine("**Status:** Generating decision points and consequences...")
                    appendLine()
                }.renderMarkdown
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
            branchingMapTask.add(branchingMapContent.renderMarkdown)
            transcript?.write("\n## Branching Map\n\n$branchingMapContent\n\n")
            transcript?.flush()
            task.update()

            saveAnalysisToFile(gameDir, "02_branching_map.md", branchingMapContent)
            resultBuilder.append("## Branching Narrative Map\n\n")
            resultBuilder.append("${gameNarrative.branching_points.size} major decision points with multiple consequences.\n\n")

            overviewTask.add("✅ Phase 3 Complete: Branching map created (${gameNarrative.branching_points.size} points)\n".renderMarkdown)
            task.update()

            // Phase 4: Generate dialogue trees
            if (gameConfig.include_dialogue_trees) {
                log.info("Phase 4: Generating dialogue trees")
                overviewTask.add("\n### Phase 4: Dialogue Trees\n*Creating interactive conversations...*\n".renderMarkdown)
                task.update()

                val dialogueTask = task.ui.newTask(false)
                tabs["Dialogue Trees"] = dialogueTask.placeholder

                dialogueTask.add(
                    buildString {
                        appendLine("# Dialogue Trees")
                        appendLine()
                        appendLine("**Status:** Generating branching conversations...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val dialogueAgent = ParsedAgent(
                    resultClass = DialogueTrees::class.java,
                    prompt = """
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
          """.trimIndent(),
                    model = api,
                    temperature = 0.8,
                    parsingChatter = orchestrationConfig.parsingChatter
                )

                val dialogueTrees = dialogueAgent.answer(listOf("Generate dialogue trees")).obj.trees
                log.info("Generated ${dialogueTrees.size} dialogue trees")

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
                dialogueTask.add(dialogueContent.renderMarkdown)
                transcript?.write("\n## Dialogue Trees\n\n$dialogueContent\n\n")
                transcript?.flush()
                task.update()

                saveAnalysisToFile(gameDir, "03_dialogue_trees.md", dialogueContent)
                resultBuilder.append("## Dialogue Trees\n\n")
                resultBuilder.append("${dialogueTrees.size} interactive conversations with branching options.\n\n")

                overviewTask.add("✅ Phase 4 Complete: Dialogue trees created (${dialogueTrees.size} conversations)\n".renderMarkdown)
                task.update()
            }

            // Phase 5: Generate multiple endings
            log.info("Phase 5: Generating multiple endings")
            overviewTask.add("\n### Phase 5: Multiple Endings\n*Creating ending variations...*\n".renderMarkdown)
            task.update()

            val endingsTask = task.ui.newTask(false)
            tabs["Endings"] = endingsTask.placeholder

            endingsTask.add(
                buildString {
                    appendLine("# Multiple Endings")
                    appendLine()
                    appendLine("**Status:** Generating ending variations...")
                    appendLine()
                }.renderMarkdown
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
            endingsTask.add(endingsContent.renderMarkdown)
            transcript?.write("\n## Endings\n\n$endingsContent\n\n")
            transcript?.flush()
            task.update()

            saveAnalysisToFile(gameDir, "04_endings.md", endingsContent)
            resultBuilder.append("## Multiple Endings\n\n")
            gameNarrative.endings.forEach { ending ->
                resultBuilder.append("- **${ending.title}:** ${ending.description.truncateForDisplay(100)}\n")
            }
            resultBuilder.append("\n")

            overviewTask.add("✅ Phase 5 Complete: Endings defined (${gameNarrative.endings.size} variations)\n".renderMarkdown)
            task.update()

            // Phase 6: Generate side quests (if enabled)
            if (gameConfig.include_side_quests) {
                log.info("Phase 6: Generating side quests")
                overviewTask.add("\n### Phase 6: Side Quests\n*Creating optional content...*\n".renderMarkdown)
                task.update()

                val sideQuestsTask = task.ui.newTask(false)
                tabs["Side Quests"] = sideQuestsTask.placeholder

                sideQuestsTask.add(
                    buildString {
                        appendLine("# Side Quests")
                        appendLine()
                        appendLine("**Status:** Generating optional narrative content...")
                        appendLine()
                    }.renderMarkdown
                )
                task.update()

                val sideQuestAgent = ParsedAgent(
                    resultClass = SideQuests::class.java,
                    prompt = """
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
          """.trimIndent(),
                    model = api,
                    temperature = 0.7,
                    parsingChatter = orchestrationConfig.parsingChatter
                )

                val sideQuests = sideQuestAgent.answer(listOf("Generate side quests")).obj.quests
                log.info("Generated ${sideQuests.size} side quests")

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
                sideQuestsTask.add(sideQuestsContent.renderMarkdown)
                transcript?.write("\n## Side Quests\n\n$sideQuestsContent\n\n")
                transcript?.flush()
                task.update()

                saveAnalysisToFile(gameDir, "05_side_quests.md", sideQuestsContent)
                resultBuilder.append("## Side Quests\n\n")
                resultBuilder.append("${sideQuests.size} optional quests for expanded storytelling.\n\n")

                overviewTask.add("✅ Phase 6 Complete: Side quests created (${sideQuests.size} quests)\n".renderMarkdown)
                task.update()
            }

            // Phase 7: Player agency analysis
            log.info("Phase 7: Analyzing player agency")
            overviewTask.add("\n### Phase 7: Player Agency Analysis\n*Evaluating meaningful choices...*\n".renderMarkdown)
            task.update()

            val agencyTask = task.ui.newTask(false)
            tabs["Player Agency"] = agencyTask.placeholder

            agencyTask.add(
                buildString {
                    appendLine("# Player Agency Analysis")
                    appendLine()
                    appendLine("**Status:** Analyzing choice impact and replayability...")
                    appendLine()
                }.renderMarkdown
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
        """.trimIndent(),
                model = api,
                temperature = 0.6
            )

            val agencyAnalysis = agencyAgent.answer(listOf("Analyze player agency"))
            log.info("Player agency analysis complete: ${agencyAnalysis.length} characters")

            val agencyContent = buildString {
                appendLine("## Player Agency Evaluation")
                appendLine()
                appendLine(agencyAnalysis)
                appendLine()
                appendLine("---")
                appendLine()
                appendLine("**Status:** ✅ Complete")
            }
            agencyTask.add(agencyContent.renderMarkdown)
            transcript?.write("\n## Player Agency Analysis\n\n$agencyContent\n\n")
            transcript?.flush()
            task.update()

            saveAnalysisToFile(gameDir, "06_player_agency_analysis.md", agencyContent)
            resultBuilder.append("## Player Agency\n\n")
            resultBuilder.append(agencyAnalysis.take(500))
            resultBuilder.append("\n\n")

            overviewTask.add("✅ Phase 7 Complete: Player agency analyzed\n".renderMarkdown)
            task.update()

            // Phase 8: Generate design document
            log.info("Phase 8: Compiling design document")
            overviewTask.add("\n### Phase 8: Final Design Document\n*Compiling complete documentation...*\n".renderMarkdown)
            task.update()

            val designDocTask = task.ui.newTask(false)
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
                appendLine("4. Branching Narrative Map")
                if (gameConfig.include_dialogue_trees) appendLine("5. Dialogue Trees")
                appendLine("${if (gameConfig.include_dialogue_trees) "6" else "5"}. Multiple Endings")
                if (gameConfig.include_side_quests) appendLine("${if (gameConfig.include_dialogue_trees) "7" else "6"}. Side Quests")
                appendLine("${if (gameConfig.include_dialogue_trees && gameConfig.include_side_quests) "8" else if (gameConfig.include_dialogue_trees || gameConfig.include_side_quests) "7" else "6"}. Player Agency Analysis")
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(gameStructureContent)
                appendLine()
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

            designDocTask.add(designDocument.renderMarkdown)
            task.update()

            saveAnalysisToFile(gameDir, "00_complete_design_document.md", designDocument)

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
                    appendLine("- Game Structure")
                    appendLine("- Branching Map")
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
                }.renderMarkdown
            )
            task.update()

            transcript?.write("\n## Final Statistics\n\n- Total Time: ${totalTime / 1000.0}s\n- Characters: ${gameNarrative.characters.size}\n- Branching Points: ${gameNarrative.branching_points.size}\n- Endings: ${gameNarrative.endings.size}\n\n")
            transcript?.close()

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
                appendLine("Complete design documentation is available in the `.game_narrative_design` directory:")
                appendLine("- Complete Design Document")
                appendLine("- Game Structure & Characters")
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
            task.error(e)

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
                }.renderMarkdown
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

            transcript?.appendLine("**Error:** ${e.message}")
            transcript?.close()
        }
    }

    private fun createTranscript(task: SessionTask, gameTitle: String): BufferedWriter? {
        val transcriptFile = "game_narrative_transcript_${SimpleDateFormat("yyyyMMddHHmmss").format(Date())}.md"
        val file = task.resolveUserFile(transcriptFile)
        val link = task.linkTo(transcriptFile)
        task.complete(
            "Writing transcript to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(
                    ".md"
                )
            }.pdf' target='_blank'>pdf</a>"
        )
        return file?.outputStream()?.let { BufferedWriter(java.io.OutputStreamWriter(it)) }
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
        transcriptWriter: BufferedWriter?,
        orchestrationConfig: OrchestrationConfig
    ) {
        try {
            log.info("Generating portrait for character: ${character.name}")
            val portraitTask = task.ui.newTask(false)
            tabs["Portrait: ${character.name}"] = portraitTask.placeholder

            portraitTask.add(
                buildString {
                    appendLine("# Character Portrait: ${character.name}")
                    appendLine()
                    appendLine("**Status:** Generating character portrait...")
                    appendLine()
                }.renderMarkdown
            )
            task.update()

            val imageAgent = ImageProcessingAgent(
                prompt = "Create a detailed character portrait for a game character",
                model = orchestrationConfig.imageChatChatter,
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

            portraitTask.add(imageHtml.renderMarkdown)
            task.update()

            transcriptWriter?.appendLine("### Character Portrait: ${character.name}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("**Prompt:** ${result.text}")
            transcriptWriter?.appendLine()
            transcriptWriter?.appendLine("![${character.name}]($link)".transcriptFilter())
            transcriptWriter?.appendLine()
            transcriptWriter?.flush()

            portraitTask.add("\n**Status:** ✅ Complete\n".renderMarkdown)
            task.update()

        } catch (e: Exception) {
            log.error("Failed to generate character portrait for ${character.name}", e)
            transcriptWriter?.appendLine("**Character Portrait Generation Failed:** ${e.message}")
            transcriptWriter?.appendLine()
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(GameNarrativeDesignTask::class.java)

        val GameNarrativeDesign = TaskType(
            "GameNarrativeDesign",
            GameNarrativeDesignConfigData::class.java,
            TaskTypeConfig::class.java,
            "Create interactive game narratives with branching storylines",
            """
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
            """
        )
    }
}