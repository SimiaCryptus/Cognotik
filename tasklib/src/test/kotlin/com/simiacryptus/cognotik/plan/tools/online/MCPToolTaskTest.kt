package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask.MCPToolTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask.MCPToolTaskTypeConfig
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object MCPToolTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = MCPToolTask.MCPTool,
      typeConfig = MCPToolTaskTypeConfig(
        task_type = MCPToolTask.MCPTool.name,
        default_timeout = 60
      ),
      executionConfig = MCPToolTaskExecutionConfigData(
        server_name = "google_search",
        tool_name = "search",
        tool_arguments = mapOf("query" to "Model Context Protocol"),
        task_description = "Search for information about MCP using the google_search server"
      ),
      timeoutMinutes = 10,
      user = ApplicationServicesConfig.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}