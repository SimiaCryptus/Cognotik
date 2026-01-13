package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.AbstractionLadderTask.AbstractionLadderTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object AbstractionLadderTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    //@org.junit.jupiter.api.Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = AbstractionLadderTask.AbstractionLadder,
            typeConfig = TaskTypeConfig(
                task_type = AbstractionLadderTask.AbstractionLadder.name
            ),
            executionConfig = AbstractionLadderTaskExecutionConfigData(
                concrete_concept = "A thread-safe singleton pattern for managing database connections",
                direction = "both",
                levels = 2,
                identify_patterns = true,
                task_description = "Analyze the abstraction levels of a thread-safe singleton database manager"
            ),
            timeoutMinutes = 10,
        ).run()
    }
}