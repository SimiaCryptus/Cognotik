package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.StructuralInvariantAnalysisTask.StructuralInvariantAnalysisTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object StructuralInvariantAnalysisTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

    //@org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = StructuralInvariantAnalysisTask.StructuralInvariantAnalysis,
            typeConfig = TaskTypeConfig(
                task_type = StructuralInvariantAnalysisTask.StructuralInvariantAnalysis.name
            ),
            executionConfig = StructuralInvariantAnalysisTaskExecutionConfigData(
                subject_object = "A Binary Search Tree",
                transformation_types = listOf("scaling", "node_deletion", "context_inversion"),
                output_format = "fingerprint",
                input_files = emptyList()
            ),
            timeoutMinutes = 10,
        ).run()
    }
}