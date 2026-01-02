package com.simiacryptus.cognotik.plan.tools.data

import com.simiacryptus.cognotik.apps.general.PlanTestHarness
import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.data.DataIngestTask.DataIngestTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.util.concurrent.TimeUnit

object DataIngestTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

     @Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        val harness = TaskTestHarness(
            taskType = DataIngestTask.DataIngest,
            typeConfig = TaskTypeConfig(
                task_type = DataIngestTask.DataIngest.name
            ),
            executionConfig = DataIngestTaskExecutionConfigData(
                input_files = listOf("sample.log"),
                task_description = "Parse standard application logs with timestamp, level, and message",
                sample_size = 100,
                max_iterations = 3
            ),
            timeoutMinutes = 10,
        )

        // Create a sample log file for the task to ingest
        harness.workspace.resolve("sample.log").toFile().writeText(
            """
            2023-10-27 10:00:01 INFO  Main - Application starting
            2023-10-27 10:00:02 DEBUG Config - Loading properties from disk
            2023-10-27 10:00:05 WARN  Database - Connection pool reaching limit
            2023-10-27 10:00:10 ERROR Auth - Failed login attempt for user 'admin'
            2023-10-27 10:00:15 INFO  Main - Application ready
            """.trimIndent()
        )

        harness.run()
    }
}

fun File.toFile() = this
