package com.simiacryptus.cognotik.plan.tools.file

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.IllustrateDocumentTask.IllustrateDocumentTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

object IllustrateDocumentTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    val harness = TaskHarness(
      taskType = IllustrateDocumentTask.IllustrateDocument,
      typeConfig = TaskTypeConfig(
        task_type = IllustrateDocumentTask.IllustrateDocument.name
      ),
      executionConfig = IllustrateDocumentTaskExecutionConfigData(
        max_images = 1,
        auto_insert = true,
        composer_directive = "Create a simple technical diagram style illustration",
        integrator_directive = "Insert the image after the first paragraph"
      ).apply {
        main_file = "test_document.md"
      },
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    )

    // Create a sample document to illustrate
    harness.dataDir.resolve("test_document.md").writeText(
      """
            # System Architecture Overview
            
            This document describes the high-level architecture of the Cognotik platform. 
            The system is designed to be modular and extensible, allowing for various task types.
            
            ## Core Components
            
            The platform consists of several key components:
            1. **Task Orchestrator**: Manages the execution flow.
            2. **Agent System**: Handles communication with LLMs.
            3. **Web UI**: Provides a user interface for interaction.
            
            ## Data Flow
            
            Data flows from the user through the UI to the orchestrator, which then delegates to specific agents.
            """.trimIndent()
    )

    harness.run()
  }
}