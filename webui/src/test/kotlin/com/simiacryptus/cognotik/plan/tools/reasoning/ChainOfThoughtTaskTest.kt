package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.ChainOfThoughtTask.ChainOfThoughtTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object ChainOfThoughtTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    //@org.junit.jupiter.api.Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = ChainOfThoughtTask.ChainOfThought,
            typeConfig = TaskTypeConfig(
                task_type = ChainOfThoughtTask.ChainOfThought.name
            ),
            executionConfig = ChainOfThoughtTaskExecutionConfigData(
                problem_statement = """
                    Analyze the potential performance bottlenecks and consistency challenges 
                    in a distributed key-value store that uses a multi-leader replication 
                    strategy across three geographic regions.
                """.trimIndent(),
                reasoning_depth = 3,
                validate_steps = true,
                related_files = emptyList()
            ),
            timeoutMinutes = 10,
        ).run()
    }
}