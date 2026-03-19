package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.ReportGenerationTask.ReportGenerationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object ReportGenerationTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = ReportGenerationTask.ReportGeneration,
      typeConfig = TaskTypeConfig(
        task_type = ReportGenerationTask.ReportGeneration.name
      ),
      executionConfig = ReportGenerationTaskExecutionConfigData(
        report_topic = "Q3 Software Development Velocity and Quality",
        report_type = "performance_analysis",
        target_audience = "executives",
        time_period = "Q3 2023",
        key_metrics = listOf(
          "Sprint Velocity",
          "Bug Density",
          "Deployment Frequency",
          "Mean Time to Recovery (MTTR)"
        ),
        data_points = mapOf(
          "avg_velocity" to "45 points/sprint",
          "bug_count" to "12 critical, 45 minor",
          "deploy_freq" to "2.5 times per week",
          "mttr" to "4.2 hours"
        ),
        include_recommendations = true,
        include_risk_assessment = true,
        tone = "professional",
        target_word_count = 1000
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
    ).run()
  }
}