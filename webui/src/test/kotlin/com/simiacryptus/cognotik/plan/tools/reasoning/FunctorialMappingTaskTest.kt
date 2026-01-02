package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.FunctorialMappingTask.FunctorialMappingTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object FunctorialMappingTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = FunctorialMappingTask.FunctorialMapping,
            typeConfig = TaskTypeConfig(
                task_type = FunctorialMappingTask.FunctorialMapping.name
            ),
            executionConfig = FunctorialMappingTaskExecutionConfigData(
                problem_statement = "Find the shortest path between node A and node D in a directed weighted graph.",
                source_category_definition = """
                    Objects: Nodes in a weighted directed graph.
                    Morphisms: Weighted edges between nodes.
                    Composition: Path concatenation where weights are summed.
                """.trimIndent(),
                target_category_definition = """
                    Objects: Vector spaces over the tropical semiring (min-plus algebra).
                    Morphisms: Matrices representing transitions.
                    Composition: Matrix multiplication in the min-plus algebra.
                """.trimIndent(),
                functor_properties = "covariant",
                task_description = "Solve a graph shortest-path problem by mapping it to tropical linear algebra."
            ),
            timeoutMinutes = 10,
        ).run()
    }
}