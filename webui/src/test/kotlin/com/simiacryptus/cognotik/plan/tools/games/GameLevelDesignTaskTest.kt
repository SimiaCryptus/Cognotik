package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.games.GameLevelDesignTask.GameLevelDesignTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object GameLevelDesignTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

    //@Test
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
        ).run()
    }
}