package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.run.RunToolTask.RunToolTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.run.RunToolTask.RunToolTaskTypeConfig
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object RunToolTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    @org.junit.jupiter.api.Tag("Integration")
     @org.junit.jupiter.api.Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = RunToolTask.RunTool,
            typeConfig = RunToolTaskTypeConfig(
                task_type = RunToolTask.RunTool.name
            ),
            executionConfig = RunToolTaskExecutionConfigData(
                tool = "echo",
                args = listOf("Hello World"),
                task_description = "Run echo to print Hello World"
            ),
            timeoutMinutes = 10,
        ).run()
    }
}