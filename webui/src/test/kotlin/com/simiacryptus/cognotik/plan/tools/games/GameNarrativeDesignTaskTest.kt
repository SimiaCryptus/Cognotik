package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.games.GameNarrativeDesignTask.GameNarrativeDesignConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object GameNarrativeDesignTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = GameNarrativeDesignTask.GameNarrativeDesign,
      typeConfig = TaskTypeConfig(
        task_type = GameNarrativeDesignTask.GameNarrativeDesign.name
      ),
      executionConfig = GameNarrativeDesignConfigData(
        game_title = "The Crystal Shards of Eldoria",
        genre = "RPG",
        narrative_style = "branching",
        player_agency_level = "high",
        num_main_characters = 3,
        num_branching_points = 5,
        num_endings = 3,
        include_dialogue_trees = true,
        include_character_arcs = true,
        include_side_quests = true,
        include_game_mechanics = true,
        tone = "heroic",
        player_role = "protagonist",
        estimated_playtime_hours = 5,
        setting = "A world where magic is dying and technology is rising from the ruins of an ancient civilization.",
        themes = listOf("legacy", "sacrifice", "progress vs tradition"),
        generate_character_portraits = false,
        generate_scene_art = false
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
    ).run()
  }
}