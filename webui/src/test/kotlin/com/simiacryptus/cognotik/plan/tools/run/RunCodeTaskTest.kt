package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object RunCodeTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = RunCodeTask.RunCode,
            typeConfig = RunCodeTask.RunCodeTaskTypeConfig(
                codeRuntime = CodeRuntimes.GroovyRuntime
            ),
            executionConfig = RunCodeTask.RunCodeTaskExecutionConfigData(
                goal = "Calculate the sum of numbers from 1 to 100",
                task_description = "Use Groovy to calculate the sum of the first 100 integers and print the result"
            ),
            timeoutMinutes = 10,
        ).run()
    }
}