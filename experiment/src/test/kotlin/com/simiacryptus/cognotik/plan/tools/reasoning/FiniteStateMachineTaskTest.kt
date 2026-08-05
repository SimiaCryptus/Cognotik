package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.FiniteStateMachineTask.FiniteStateMachineTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

@Suppress("unused")
object FiniteStateMachineTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
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
      user = ApplicationServicesConfig.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}