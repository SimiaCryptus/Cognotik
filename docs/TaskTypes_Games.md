# Games

## GameEconomy

Design complete game economic systems with progression and monetization

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

#### Planner Prompt Segment

```text
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
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameEconomy",
  "game_title" : null,
  "game_type" : "RPG",
  "progression_style" : "linear",
  "num_resources" : 3,
  "num_progression_tiers" : 50,
  "include_skill_tree" : true,
  "include_crafting" : false,
  "include_trading" : false,
  "monetization_model" : "free-to-play",
  "include_daily_rewards" : true,
  "include_seasonal_content" : true,
  "include_battle_pass" : true,
  "forecast_months" : 6,
  "generate_balance_report" : true,
  "additional_context" : null,
  "input_files" : null,
  "task_description" : "Design game economy for: null",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameEconomy"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameEconomy",
  "name" : "GameEconomy",
  "model" : null
}
```

---

## GameLevelDesign

Generate complete game level designs with layout, pacing, and encounters

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

#### Planner Prompt Segment

```text
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
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameLevelDesign",
  "level_name" : null,
  "game_type" : "platformer",
  "level_duration_minutes" : 10,
  "difficulty_tier" : "medium",
  "player_count" : 1,
  "level_theme" : "dungeon",
  "include_boss_encounter" : false,
  "include_puzzles" : true,
  "include_secrets" : true,
  "include_collectibles" : true,
  "pacing_style" : "escalating",
  "generate_difficulty_variants" : false,
  "include_visual_layout" : true,
  "input_files" : null,
  "task_description" : "Generate game level design: 'null'",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameLevelDesign"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameLevelDesign",
  "name" : "GameLevelDesign",
  "model" : null
}
```

---

## GameMechanicsDesign

Generate comprehensive game mechanics with balance analysis

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

#### Planner Prompt Segment

```text
GameMechanicsDesign - Generate comprehensive game mechanics with balance analysis
  ** Specify the game concept (e.g., "Tower defense with resource management")
  ** Define target audience (casual, hardcore, family, competitive)
  ** Set core gameplay loop duration
  ** Configure number of mechanics to design (3-8)
  ** Choose balance focus (skill, luck, strategy, mixed)
  ** The task will:
     - Generate core gameplay mechanics
     - Analyze mechanic interactions
     - Design progression systems
     - Create economy systems
     - Evaluate balance and fairness
     - Simulate playtesting scenarios
     - Provide tuning parameters
  ** Useful for:
     - Game design prototyping
     - Balancing existing games
     - Competitive game design
     - Educational game mechanics
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameMechanicsDesign",
  "game_concept" : null,
  "target_audience" : "casual",
  "core_loop_duration" : "15 minutes",
  "num_mechanics" : 5,
  "include_progression_system" : true,
  "include_economy_system" : true,
  "include_difficulty_scaling" : true,
  "balance_focus" : "mixed",
  "playtesting_scenarios" : 3,
  "generate_tuning_guide" : true,
  "input_files" : null,
  "task_description" : "Design game mechanics for: null",
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameMechanicsDesign"
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameMechanicsDesign",
  "name" : "GameMechanicsDesign",
  "model" : null
}
```

---

## GameNarrativeDesign

Create interactive game narratives with branching storylines

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

#### Planner Prompt Segment

```text
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
```

#### Default Execution Configuration

```json
{
  "task_type" : "GameNarrativeDesign",
  "game_title" : null,
  "genre" : "RPG",
  "narrative_style" : "branching",
  "player_agency_level" : "high",
  "num_main_characters" : 4,
  "num_branching_points" : 8,
  "num_endings" : 4,
  "include_dialogue_trees" : true,
  "include_character_arcs" : true,
  "include_side_quests" : true,
  "include_game_mechanics" : true,
  "tone" : "heroic",
  "player_role" : "protagonist",
  "estimated_playtime_hours" : 20,
  "setting" : null,
  "themes" : null,
  "generate_character_portraits" : false,
  "generate_scene_art" : false,
  "input_files" : null,
  "task_dependencies" : null,
  "state" : "Pending",
  "task_type" : "GameNarrativeDesign",
  "task_description" : "Design game narrative for 'null'",
  "subject" : null,
  "narrative_elements" : {
    "genre" : "RPG",
    "narrative_style" : "branching",
    "player_agency_level" : "high",
    "num_main_characters" : 4,
    "tone" : "heroic",
    "player_role" : "protagonist"
  },
  "target_word_count" : 80000,
  "number_of_acts" : 3,
  "scenes_per_act" : 3,
  "writing_style" : "epic fantasy",
  "point_of_view" : "second person",
  "detailed_descriptions" : true,
  "include_dialogue" : true,
  "show_internal_thoughts" : true,
  "revision_passes" : 1,
  "generate_scene_images" : false,
  "generate_cover_image" : true
}
```

#### Default Type Configuration

```json
{
  "task_type" : "GameNarrativeDesign",
  "name" : "GameNarrativeDesign",
  "model" : null
}
```

---

