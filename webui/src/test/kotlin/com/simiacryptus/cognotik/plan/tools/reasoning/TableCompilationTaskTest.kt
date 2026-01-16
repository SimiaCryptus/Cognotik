package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.reasoning.TableCompilationTask.TableCompilationTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.reasoning.TableCompilationTask.TableCompilationTaskTypeConfig
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object TableCompilationTaskTest {

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
            taskType = TableCompilationTask.TableCompilation,
            typeConfig = TableCompilationTaskTypeConfig(
                task_type = TableCompilationTask.TableCompilation.name,
                partition_size = 2
            ),
            executionConfig = TableCompilationTaskExecutionConfigData(
                rows = listOf("Kotlin", "Java", "Python"),
                columns = listOf("Paradigm", "Typing", "Primary Use Case"),
                cell_query = "What is the {column} of the {row} programming language?",
                task_description = "Compare popular programming languages across different dimensions.",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}