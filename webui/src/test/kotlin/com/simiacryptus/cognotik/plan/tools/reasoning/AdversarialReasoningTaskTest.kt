package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.AdversarialReasoningTask.AdversarialReasoningTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object AdversarialReasoningTaskTest {

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
            taskType = AdversarialReasoningTask.AdversarialReasoning,
            typeConfig = TaskTypeConfig(
                task_type = AdversarialReasoningTask.AdversarialReasoning.name
            ),
            executionConfig = AdversarialReasoningTaskExecutionConfigData(
                target_system = "A simple web application with a login form and a user profile page.",
                attack_vectors = listOf("security", "logic"),
                adversary_capability = "advanced",
                task_description = "Perform a red team analysis on a web application login system.",
                suggest_mitigations = true,
                challenge_assumptions = listOf(
                    "The user input is always sanitized by the framework",
                    "The session token is unguessable"
                )
            ),
            timeoutMinutes = 10,
        ).run()
    }
}