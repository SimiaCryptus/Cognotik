package com.simiacryptus.cognotik.plan.tools.games

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.games.GameMechanicsDesignTask.GameMechanicsDesignTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object GameMechanicsDesignTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
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
        ).run()
    }
}