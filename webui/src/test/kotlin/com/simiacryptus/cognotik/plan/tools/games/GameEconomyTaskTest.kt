package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.games.GameEconomyTask.GameEconomyTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object GameEconomyTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = GameEconomyTask.GameEconomy,
      typeConfig = TaskTypeConfig(
        task_type = GameEconomyTask.GameEconomy.name
      ),
      executionConfig = GameEconomyTaskExecutionConfigData(
        game_title = "Galactic Trader",
        game_type = "strategy",
        progression_style = "linear",
        num_resources = 4,
        num_progression_tiers = 30,
        include_skill_tree = true,
        include_crafting = true,
        monetization_model = "free-to-play",
        forecast_months = 6,
        generate_balance_report = true,
        task_description = "Design a comprehensive space trading economy with crafting and progression"
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
    ).run()
  }
}