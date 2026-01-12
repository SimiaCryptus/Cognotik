package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.ImageGenerationTask.GenerateImageTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object GenerateImageTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    //@org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = ImageGenerationTask.GenerateImage,
            typeConfig = TaskTypeConfig(
                task_type = ImageGenerationTask.GenerateImage.name
            ),
            executionConfig = GenerateImageTaskExecutionConfigData(
                files = listOf("test_output_image.png"),
                task_description = "A high-quality digital art piece of a serene mountain landscape at sunset, with a clear lake in the foreground reflecting the orange and purple sky, cinematic lighting, 8k resolution. The image should be photorealistic.",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}