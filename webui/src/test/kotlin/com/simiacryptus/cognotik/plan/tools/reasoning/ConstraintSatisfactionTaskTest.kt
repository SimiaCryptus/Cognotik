package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.ConstraintSatisfactionTask.ConstraintSatisfactionTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object ConstraintSatisfactionTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    @org.junit.jupiter.api.Tag("Integration")
    //@org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = ConstraintSatisfactionTask.ConstraintSatisfaction,
            typeConfig = TaskTypeConfig(
                task_type = ConstraintSatisfactionTask.ConstraintSatisfaction.name
            ),
            executionConfig = ConstraintSatisfactionTaskExecutionConfigData(
                problem_description = "Select a cloud provider for a high-availability microservices architecture.",
                hard_constraints = listOf(
                    "Must support managed Kubernetes (K8s)",
                    "Must have data centers in the EU (GDPR compliance)",
                    "Must provide 99.99% uptime SLA for database services"
                ),
                soft_constraints = mapOf(
                    "Minimize monthly operational cost" to 0.8,
                    "Ease of integration with existing GitHub Actions CI/CD" to 0.5,
                    "Availability of specialized AI/ML hardware (TPUs/GPUs)" to 0.3
                ),
                search_strategy = "backtracking",
                max_iterations = 50
            ),
            timeoutMinutes = 10,
        ).run()
    }
}