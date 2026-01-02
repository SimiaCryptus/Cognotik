package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.apps.general.PlanHarness
import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.FileModificationTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object FileAppendTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = FileAppendTask.FileAppend,
            typeConfig = TaskTypeConfig(
                task_type = FileAppendTask.FileAppend.name
            ),
            executionConfig = FileAppendTask.FileAppendTaskExecutionConfigData(
                file = "Calculator.kt",
                task_description = "Add a subtract function to the Calculator class",
                append_content = "Implement fun subtract(a: Int, b: Int): Int",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}
