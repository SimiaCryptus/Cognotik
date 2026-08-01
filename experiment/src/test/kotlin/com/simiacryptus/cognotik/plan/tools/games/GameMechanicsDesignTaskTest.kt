package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask.GameMechanicsDesignTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

@Suppress("unused")
object GameMechanicsDesignTaskTest {

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
      taskType = GameMechanicsDesignTask.GameMechanicsDesign,
      typeConfig = TaskTypeConfig(
        task_type = GameMechanicsDesignTask.GameMechanicsDesign.name
      ),
      executionConfig = GameMechanicsDesignTaskExecutionConfigData(
        game_concept = "A deck-building roguelike where cards represent weather phenomena and players must manage atmospheric pressure.",
        target_audience = "hardcore",
        core_loop_duration = "20 minutes",
        num_mechanics = 4,
        balance_focus = "strategy",
        include_progression_system = true,
        include_economy_system = true,
        playtesting_scenarios = 2
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