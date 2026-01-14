package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.GenerateQRImageTask.GenerateQRImageTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object GenerateQRImageTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    //@org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = GenerateQRImageTask.GenerateQRImage,
            typeConfig = TaskTypeConfig(
                task_type = GenerateQRImageTask.GenerateQRImage.name
            ),
            executionConfig = GenerateQRImageTaskExecutionConfigData(
                files = listOf("artistic_qr.png"),
                qr_content = "https://github.com/SimiaCryptus",
                style_directive = "A lush jungle theme with exotic flowers and vines weaving through the QR pattern, vibrant greens and floral colors",
                qr_size = 512,
                max_retries = 3,
                task_description = "Generate an artistic QR code for the project repository"
            ),
            timeoutMinutes = 10,
        ).run()
    }
}