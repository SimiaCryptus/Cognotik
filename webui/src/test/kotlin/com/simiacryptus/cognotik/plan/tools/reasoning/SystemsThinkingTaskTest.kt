package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.SystemsThinkingTask.SystemsThinkingTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object SystemsThinkingTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = SystemsThinkingTask.SystemsThinking,
      typeConfig = TaskTypeConfig(
        task_type = SystemsThinkingTask.SystemsThinking.name
      ),
      executionConfig = SystemsThinkingTaskExecutionConfigData(
        system_description = "A software development team's CI/CD pipeline and deployment process, including code reviews, automated testing, and production releases.",
        identify_feedback_loops = true,
        map_delays = true,
        find_leverage_points = true,
        simulate_interventions = listOf(
          "Implement automated regression testing",
          "Increase deployment frequency from weekly to daily",
          "Introduce a mandatory 24-hour 'cool-down' period after code freeze"
        ),
        time_horizon = "1 year",
        identify_archetypes = true,
        analyze_emergent_behavior = true,
        focus_areas = listOf(
          "Developer feedback cycle",
          "Build queue management",
          "Production stability vs. feature velocity"
        ),
        analysis_questions = listOf(
          "Why does the deployment queue tend to grow exponentially towards the end of a sprint?",
          "What are the unintended consequences of optimizing for individual developer throughput?",
          "Where is the most effective place to intervene to reduce time-to-market without sacrificing quality?"
        )
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
    ).run()
  }
}