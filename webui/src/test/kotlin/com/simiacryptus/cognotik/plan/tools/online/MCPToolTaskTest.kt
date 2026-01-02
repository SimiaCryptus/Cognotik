package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask.MCPToolTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.online.MCPToolTask.MCPToolTaskTypeConfig
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object MCPToolTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

     @Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun test() {
        TaskTestHarness(
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