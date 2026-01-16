package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object FileAppendTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    @org.junit.jupiter.api.Tag("Integration")
    //@org.junit.jupiter.api.Test
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
