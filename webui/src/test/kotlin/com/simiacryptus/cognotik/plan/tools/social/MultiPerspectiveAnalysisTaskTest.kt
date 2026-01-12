package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysisTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object MultiPerspectiveAnalysisTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

    //@org.junit.jupiter.api.Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis,
            typeConfig = TaskTypeConfig(
                task_type = MultiPerspectiveAnalysisTask.MultiPerspectiveAnalysis.name
            ),
            executionConfig = MultiPerspectiveAnalysisTaskExecutionConfigData(
                analysis_subject = "The adoption of Microservices vs Monolithic architecture for a new e-commerce platform",
                perspectives = listOf(
                    "Scalability and Performance",
                    "Development Velocity",
                    "Operational Complexity",
                    "Cost Efficiency"
                ),
                synthesize = true,
                consensus_threshold = 0.8
            ),
            timeoutMinutes = 10,
        ).run()
    }
}