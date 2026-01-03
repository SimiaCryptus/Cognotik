package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask.SubPlanTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask.SubPlanTaskTypeConfig
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.io.File

object SubPlanTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        val file = File("subplan_test.txt")
        if (file.exists()) file.delete()
        try {
            TaskHarness(
                taskType = SubPlanTask.SubPlan,
                typeConfig = SubPlanTaskTypeConfig(
                    cognitiveSettings = WaterfallMode.WaterfallModeConfig(),
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
            Assertions.assertTrue(file.exists(), "File subplan_test.txt should have been created")
            Assertions.assertEquals("This was generated via a sub-plan.", file.readText().trim())
        } finally {
            if (file.exists()) file.delete()
        }
    }
}