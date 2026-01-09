package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.PlanHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object FileModificationTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

     @org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = FileModificationTask.FileModification,
            typeConfig = TaskTypeConfig(
                task_type = FileModificationTask.FileModification.name
            ),
            executionConfig = FileModificationTaskExecutionConfigData(
                files = listOf("Calculator.kt"),
                task_description = "Add a subtract function to the Calculator class",
                modifications = "Implement fun subtract(a: Int, b: Int): Int",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}
