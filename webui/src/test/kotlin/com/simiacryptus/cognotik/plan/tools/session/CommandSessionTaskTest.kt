package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask.CommandSessionTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object CommandSessionTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

     @org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = CommandSessionTask.CommandSession,
            typeConfig = TaskTypeConfig(
                task_type = CommandSessionTask.CommandSession.name
            ),
            executionConfig = CommandSessionTaskExecutionConfigData(
                command = listOf("bash"),
                inputs = listOf(
                    "export TEST_VAR='Cognotik Session'",
                    "echo \"Value: \$TEST_VAR\"",
                    "pwd"
                ),
                idle_timeout = 500,
                task_description = "Execute stateful bash commands to verify session functionality",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}