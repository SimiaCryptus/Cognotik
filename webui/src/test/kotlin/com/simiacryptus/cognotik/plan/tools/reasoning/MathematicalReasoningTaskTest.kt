package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.MathematicalReasoningTask.MathematicalReasoningTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object MathematicalReasoningTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

     @Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun testAlgebraicSolving() {
        TaskTestHarness(
            taskType = MathematicalReasoningTask.MathematicalReasoning,
            typeConfig = TaskTypeConfig(
                task_type = MathematicalReasoningTask.MathematicalReasoning.name
            ),
            executionConfig = MathematicalReasoningTaskExecutionConfigData(
                problem_statement = "Solve for x: 3x + 7 = 22",
                goal = "Find the value of x",
                domain = "algebra",
                max_depth = 10,
                detail_level = "detailed"
            ),
            timeoutMinutes = 10,
        ).run()
    }

     @Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun testCalculusDerivation() {
        TaskTestHarness(
            taskType = MathematicalReasoningTask.MathematicalReasoning,
            typeConfig = TaskTypeConfig(
                task_type = MathematicalReasoningTask.MathematicalReasoning.name
            ),
            executionConfig = MathematicalReasoningTaskExecutionConfigData(
                problem_statement = "Find the derivative of f(x) = x^2 * sin(x)",
                goal = "Calculate f'(x) using the product rule",
                domain = "calculus",
                max_depth = 15,
                detail_level = "standard"
            ),
            timeoutMinutes = 10,
        ).run()
    }
}