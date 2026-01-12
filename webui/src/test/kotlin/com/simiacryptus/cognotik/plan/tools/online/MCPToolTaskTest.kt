package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask.MCPToolTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask.MCPToolTaskTypeConfig
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object MCPToolTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

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
        ).run()
    }
}