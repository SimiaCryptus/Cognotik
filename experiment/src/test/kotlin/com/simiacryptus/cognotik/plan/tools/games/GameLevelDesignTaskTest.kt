package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.games.GameLevelDesignTask.GameLevelDesignTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object GameLevelDesignTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = GameLevelDesignTask.GameLevelDesign,
      typeConfig = TaskTypeConfig(
        task_type = GameLevelDesignTask.GameLevelDesign.name
      ),
      executionConfig = GameLevelDesignTaskExecutionConfigData(
        level_name = "The Shadow Crypt",
        game_type = "platformer",
        level_duration_minutes = 10,
        difficulty_tier = "medium",
        player_count = 1,
        level_theme = "dungeon",
        include_boss_encounter = true,
        include_puzzles = true,
        include_secrets = true,
        include_collectibles = true,
        pacing_style = "escalating",
        generate_difficulty_variants = true,
        include_visual_layout = true
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}