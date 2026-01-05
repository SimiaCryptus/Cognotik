package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask.AutoFixTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.run.AutoFixTask.CommandWithWorkingDir
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object AutoFixTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = AutoFixTask.AutoFix,
            typeConfig = TaskTypeConfig(
                task_type = AutoFixTask.AutoFix.name
            ),
            executionConfig = AutoFixTaskExecutionConfigData(
                commands = listOf(
                    CommandWithWorkingDir(
                        command = listOf("git", "status"),
                        workingDir = "."
                    )
                ),
                task_description = "Check the status of the git repository",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}