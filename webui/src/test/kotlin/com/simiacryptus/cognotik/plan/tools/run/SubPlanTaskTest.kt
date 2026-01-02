package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask.SubPlanTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask.SubPlanTaskTypeConfig
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object SubPlanTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = SubPlanTask.SubPlan,
            typeConfig = SubPlanTaskTypeConfig(
                taskSettings = mutableMapOf(
                    FileModificationTask.FileModification.name to TaskTypeConfig(
                        task_type = FileModificationTask.FileModification.name
                    )
                ),
                purpose = "Testing recursive sub-planning capabilities"
            ),
            executionConfig = SubPlanTaskExecutionConfigData(
                planning_goal = "Create a file named 'subplan_test.txt' containing the text 'This was generated via a sub-plan.'",
                context = listOf("The environment is a standard Kotlin/JVM test environment.")
            ),
            timeoutMinutes = 10,
        ).run()
    }
}