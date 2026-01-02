package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.GenerateSpriteSheetTask.GenerateSpriteSheetTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object GenerateSpriteSheetTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = GenerateSpriteSheetTask.GenerateSpriteSheet,
            typeConfig = TaskTypeConfig(
                task_type = GenerateSpriteSheetTask.GenerateSpriteSheet.name
            ),
            executionConfig = GenerateSpriteSheetTaskExecutionConfigData(
                files = listOf("test_sprites.png"),
                metadata_file = "test_sprites.json",
                task_description =
                    //"A pixel art character walk cycle animation sheet. 6 frames of a knight walking side view. Uniform size and spacing.",
                    "Various superhero cats; photorealistic style; transparent background; arranged in a grid format.",
            ),
            timeoutMinutes = 15,
        ).run()
    }
}