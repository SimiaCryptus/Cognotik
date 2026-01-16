package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object RunCodeTaskTest {

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