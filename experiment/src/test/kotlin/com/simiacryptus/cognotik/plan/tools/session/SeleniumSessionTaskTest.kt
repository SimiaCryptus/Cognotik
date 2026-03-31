package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.session.SeleniumSessionTask.SeleniumSessionTaskExecutionConfigData
import com.simiacryptus.cognotik.platform.model.defaultUser
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.util.UUID
import java.util.concurrent.TimeUnit

object SeleniumSessionTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.Companion.configurePlatform(defaultUser)
  }

  @Tag("Integration")
  //@org.junit.jupiter.api.Test
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
      user = defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    ).run()
  }

  @Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun testSessionReuse() {
    val sessionId = "test-session-${UUID.randomUUID()}"

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
      user = defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
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
      user = defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    ).run()
  }
}