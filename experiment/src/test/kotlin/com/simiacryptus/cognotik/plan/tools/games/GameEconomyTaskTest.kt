package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.games.GameEconomyTask.GameEconomyTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object GameEconomyTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
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
      user = ApplicationServicesConfig.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}