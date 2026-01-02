package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.apps.general.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.session.SeleniumSessionTask.SeleniumSessionTaskExecutionConfigData
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object SeleniumSessionTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskHarness(
            taskType = SeleniumSessionTask.SeleniumSession,
            typeConfig = TaskTypeConfig(
                task_type = SeleniumSessionTask.SeleniumSession.name
            ),
            executionConfig = SeleniumSessionTaskExecutionConfigData(
                url = "https://www.google.com",
                commands = listOf(
                    "return document.title;",
                    "return window.location.href;"
                ),
                task_description = "Navigate to Google and verify page metadata",
                createTranscript = true
            ),
            timeoutMinutes = 10,
        ).run()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun testSessionReuse() {
        val sessionId = "test-session-${java.util.UUID.randomUUID()}"
        
        // First task: Open session
        TaskHarness(
            taskType = SeleniumSessionTask.SeleniumSession,
            typeConfig = TaskTypeConfig(
                task_type = SeleniumSessionTask.SeleniumSession.name
            ),
            executionConfig = SeleniumSessionTaskExecutionConfigData(
                url = "https://www.wikipedia.org",
                sessionId = sessionId,
                closeSession = false,
                task_description = "Open Wikipedia session",
            ),
            timeoutMinutes = 5,
        ).run()

        // Second task: Reuse session
        TaskHarness(
            taskType = SeleniumSessionTask.SeleniumSession,
            typeConfig = TaskTypeConfig(
                task_type = SeleniumSessionTask.SeleniumSession.name
            ),
            executionConfig = SeleniumSessionTaskExecutionConfigData(
                sessionId = sessionId,
                commands = listOf("return document.title;"),
                closeSession = true,
                task_description = "Get title from existing session",
            ),
            timeoutMinutes = 5,
        ).run()
    }
}