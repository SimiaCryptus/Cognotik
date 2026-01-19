package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.data.toFile
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.ReadDocumentsTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.ReadDocumentsTaskTypeConfig
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object ReadDocumentsTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

    @org.junit.jupiter.api.Tag("Integration")
    //@org.junit.jupiter.api.Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = ReadDocumentsTask.ReadDocuments,
            typeConfig = ReadDocumentsTaskTypeConfig(
                task_type = ReadDocumentsTask.ReadDocuments.name
            ),
            executionConfig = ReadDocumentsTaskExecutionConfigData(
                inquiry_questions = listOf("What operations are supported in the Calculator class?"),
                inquiry_goal = "Analyze the capabilities of the provided source code",
                input_files = listOf("Calculator.kt"),
                task_description = "Read and analyze the Calculator.kt file to understand its functionality"
            ),
            timeoutMinutes = 10,
        ).apply {
            dataDir.resolve("Calculator.kt").toFile().writeText(
                """
                    class Calculator {
                        fun add(a: Int, b: Int): Int = a + b
                        fun multiply(a: Int, b: Int): Int = a * b
                    }
                    """.trimIndent()
            )
        }.run()
    }
}