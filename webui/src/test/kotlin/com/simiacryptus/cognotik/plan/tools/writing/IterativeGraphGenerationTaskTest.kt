package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.IterativeGraphGenerationTask.IterativeGraphGenerationTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object IterativeGraphGenerationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

     @Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = IterativeGraphGenerationTask.IterativeGraphGeneration,
            typeConfig = TaskTypeConfig(
                task_type = IterativeGraphGenerationTask.IterativeGraphGeneration.name
            ),
            executionConfig = IterativeGraphGenerationTaskExecutionConfigData(
                goal_prompt = "Map the relationships between characters in a small story.",
                context_data = """
                    Alice is Bob's sister. 
                    Bob works for Charlie. 
                    Charlie is friends with Alice.
                """.trimIndent(),
                node_types = listOf("Person"),
                edge_types = listOf("SISTER_OF", "WORKS_FOR", "FRIEND_OF"),
                max_iterations = 2
            ),
            timeoutMinutes = 10,
        ).run()
    }
}