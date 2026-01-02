package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileSearchTask.SearchTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout

object FileSearchTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        TaskTestHarness.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
            taskType = FileSearchTask.FileSearch,
            typeConfig = TaskTypeConfig(
                task_type = FileSearchTask.FileSearch.name
            ),
            executionConfig = SearchTaskExecutionConfigData(
                search_pattern = "class",
                is_regex = false,
                context_lines = 3,
                input_files = listOf("*.kt"),
                task_description = "Search for class definitions in Kotlin files",
            ),
            timeoutMinutes = 10,
        ).run()
    }

    //@Test
    @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
    fun testRegex() {
        TaskTestHarness(
            taskType = FileSearchTask.FileSearch,
            typeConfig = TaskTypeConfig(
                task_type = FileSearchTask.FileSearch.name
            ),
            executionConfig = SearchTaskExecutionConfigData(
                search_pattern = "fun\\s+\\w+\\(",
                is_regex = true,
                context_lines = 1,
                input_files = listOf("src/**/*.kt"),
                task_description = "Search for function signatures using regex",
            ),
            timeoutMinutes = 10,
        ).run()
    }
}