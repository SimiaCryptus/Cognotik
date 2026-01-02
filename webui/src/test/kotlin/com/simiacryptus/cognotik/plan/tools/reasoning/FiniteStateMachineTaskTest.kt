package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.FiniteStateMachineTask.FiniteStateMachineTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object FiniteStateMachineTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

     @Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = FiniteStateMachineTask.FiniteStateMachine,
            typeConfig = TaskTypeConfig(
                task_type = FiniteStateMachineTask.FiniteStateMachine.name
            ),
            executionConfig = FiniteStateMachineTaskExecutionConfigData(
                concept_to_model = "User Authentication Flow",
                domain_context = "Web Application Security",
                initial_states = listOf("Logged Out"),
                known_events = listOf("Submit Credentials", "MFA Challenge", "Session Timeout", "Logout", "Password Reset"),
                identify_edge_cases = true,
                validate_properties = true,
                generate_test_scenarios = true,
                task_description = "Model the user authentication lifecycle including MFA, account lockout, and session expiration."
            ),
            timeoutMinutes = 10,
        ).run()
    }
}