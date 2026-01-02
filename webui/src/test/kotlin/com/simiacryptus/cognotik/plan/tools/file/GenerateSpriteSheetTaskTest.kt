package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.GenerateSpriteSheetTask.GenerateSpriteSheetTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object GenerateSpriteSheetTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = GenerateSpriteSheetTask.GenerateSpriteSheet,
            typeConfig = TaskTypeConfig(
                task_type = GenerateSpriteSheetTask.GenerateSpriteSheet.name
            ),
            executionConfig = GenerateSpriteSheetTaskExecutionConfigData(
                files = listOf("test_sprites.png"),
                metadata_file = "test_sprites.json",
                task_description = "A set of 4 pixel art slime monsters in different colors (red, blue, green, yellow), arranged in a clear grid layout.",
            ),
            timeoutMinutes = 15,
        ).run()
    }
}