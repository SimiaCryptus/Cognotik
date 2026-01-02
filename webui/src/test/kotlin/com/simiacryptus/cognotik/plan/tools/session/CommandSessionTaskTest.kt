package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.session.CommandSessionTask.CommandSessionTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object CommandSessionTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = CommandSessionTask.CommandSession,
            typeConfig = TaskTypeConfig(
                task_type = CommandSessionTask.CommandSession.name
            ),
            executionConfig = CommandSessionTaskExecutionConfigData(
                command = listOf("bash"),
                inputs = listOf("echo 'Hello World'", "expr 1 + 1"),
                task_description = "Execute simple bash commands to verify session functionality",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}