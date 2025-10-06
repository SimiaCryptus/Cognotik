package com.simiacryptus.cognotik.plan.tools.mcp

 import com.simiacryptus.cognotik.describe.Description
 import com.simiacryptus.cognotik.plan.AbstractTask
 import com.simiacryptus.cognotik.plan.ExecutionState
 import com.simiacryptus.cognotik.plan.OrchestrationConfig
 import com.simiacryptus.cognotik.plan.TaskExecutionConfig
 import com.simiacryptus.cognotik.plan.TaskOrchestrator
 import com.simiacryptus.cognotik.plan.TaskTypeConfig
 import com.simiacryptus.cognotik.util.JsonUtil
 import com.simiacryptus.cognotik.util.LoggerFactory
 import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.mcp.MCPClient
import com.simiacryptus.cognotik.mcp.MCPServerRegistry
 import org.slf4j.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

 class MCPToolTask(
    orchestrationConfig: OrchestrationConfig,
    executionConfig: MCPToolTaskExecutionConfigData?
 ) : AbstractTask<MCPToolTask.MCPToolTaskExecutionConfigData, MCPToolTask.MCPToolTaskTypeConfig>(
    orchestrationConfig,
    executionConfig
) {

    class MCPToolTaskExecutionConfigData(
        @Description("The name of the MCP server to connect to")
        val server_name: String? = null,
        @Description("The name of the tool to execute on the MCP server")
        val tool_name: String? = null,
        @Description("Arguments to pass to the MCP tool as a JSON object")
        val tool_arguments: Map<String, Any>? = null,
        @Description("Optional timeout in seconds for the tool execution")
        val timeout_seconds: Int? = 30,
        task_description: String? = null,
        task_dependencies: MutableList<String>? = null,
        state: TaskState? = null
    ) : TaskExecutionConfig(
        task_type = MCPTool.name,
        task_description = task_description,
        task_dependencies = task_dependencies,
        state = state
    )

    class MCPToolTaskTypeConfig(
        @Description("Default MCP server to use if not specified in execution config")
        val default_server: String? = null,
        @Description("Default timeout in seconds for MCP tool execution")
        val default_timeout: Int = 30,
        @Description("Whether to automatically retry failed tool executions")
        val auto_retry: Boolean = false,
        @Description("Maximum number of retry attempts")
        val max_retries: Int = 3,
        @Description("Initial retry delay in milliseconds")
        val retry_delay_ms: Long = 1000,
        @Description("Whether to use exponential backoff for retries")
        val exponential_backoff: Boolean = true,
        task_type: String? = MCPTool.name,
        name: String? = null
    ) : TaskTypeConfig(
        task_type = task_type,
        name = name
    )

    override fun promptSegment(): String {
        return """
 MCPTool - Execute tools from Model Context Protocol (MCP) servers
  ** Specify the MCP server name and tool to execute
  ** Provide tool arguments as a JSON object
  ** Configure timeout and retry behavior
  ** Supports integration with external MCP-compatible services
        """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val config = executionConfig ?: throw IllegalStateException("Execution config is required")
        val serverName = config.server_name ?: typeConfig.default_server
            ?: throw IllegalStateException("MCP server name must be specified")
        val toolName = config.tool_name ?: throw IllegalStateException("Tool name must be specified")
        val arguments = config.tool_arguments ?: emptyMap()
        val timeout = config.timeout_seconds ?: typeConfig.default_timeout

        task.add("Executing MCP tool: $toolName on server: $serverName")
        task.add("Arguments: ${JsonUtil.toJson(arguments)}")

        try {
            val result = executeMCPTool(
                serverName = serverName,
                toolName = toolName,
                arguments = arguments,
                timeout = timeout,
                task = task
            )

            task.add("Tool execution completed successfully")
            task.add("Result:\n```json\n${JsonUtil.toJson(result)}\n```")
            
            resultFn(JsonUtil.toJson(result))
            state = TaskState.Completed
        } catch (e: Exception) {
            log.error("Error executing MCP tool", e)
            
            if (typeConfig.auto_retry && shouldRetry(e)) {
                handleRetry(agent, messages, task, resultFn, orchestrationConfig, e)
            } else {
                state = TaskState.Completed
                task.error(e)
                throw e
            }
        }
    }

    private fun executeMCPTool(
        serverName: String,
        toolName: String,
        arguments: Map<String, Any>,
        timeout: Int,
        task: SessionTask
    ): Map<String, Any> {
        log.info("Connecting to MCP server: $serverName")
        task.add("Connecting to MCP server: $serverName")
        
        // Get MCP client from registry
        val client = MCPServerRegistry.getClient(serverName)
            ?: throw IllegalStateException("MCP server not found: $serverName")
        
        try {
            // Ensure client is connected
            if (!client.isConnected()) {
                task.add("Establishing connection to MCP server...")
                client.connect()
            }
            
            // List available tools to verify the tool exists
            val availableTools = client.listTools()
            val tool = availableTools.find { it.name == toolName }
                ?: throw IllegalArgumentException("Tool '$toolName' not found on server '$serverName'. Available tools: ${availableTools.map { it.name }}")
            
            task.add("Tool found: ${tool.name}")
            task.add("Tool description: ${tool.description}")
            
            // Execute the tool with timeout
            task.add("Executing tool with timeout of $timeout seconds...")
            val startTime = System.currentTimeMillis()
            
            val result = try {
                client.executeTool(toolName, arguments, timeout.toLong(), TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                throw TimeoutException("Tool execution timed out after $timeout seconds")
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            task.add("Tool executed in ${executionTime}ms")
            
            return mapOf(
                "status" to "success",
                "server" to serverName,
                "tool" to toolName,
                "arguments" to arguments,
                "result" to result!!,
                "execution_time_ms" to executionTime,
                "timestamp" to System.currentTimeMillis()
            )
        } catch (e: Exception) {
            log.error("Error executing MCP tool: ${e.message}", e)
            throw e
        }
    }

    private fun shouldRetry(e: Exception): Boolean {
        // Determine if the error is retryable
        return when {
            e is java.net.SocketTimeoutException -> true
            e is TimeoutException -> true
            e is java.io.IOException -> true
            e.message?.contains("connection", ignoreCase = true) == true -> true
            e.message?.contains("timeout", ignoreCase = true) == true -> true
            e.message?.contains("unavailable", ignoreCase = true) == true -> true
            else -> false
        }
    }

    private fun handleRetry(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig,
        lastError: Exception
    ) {
        var retryCount = 0
        var lastException = lastError
        
        while (retryCount < typeConfig.max_retries) {
            retryCount++
            task.add("Retry attempt $retryCount of ${typeConfig.max_retries}")
            
            try {
                val delay = if (typeConfig.exponential_backoff) {
                    typeConfig.retry_delay_ms * (1 shl (retryCount - 1)) // 2^(n-1) exponential backoff
                } else {
                    typeConfig.retry_delay_ms
                }
                task.add("Waiting ${delay}ms before retry...")
                Thread.sleep(delay)
                
                run(agent, messages, task, resultFn, orchestrationConfig)
                return
            } catch (e: Exception) {
                lastException = e
                log.warn("Retry attempt $retryCount failed", e)
                task.add("Retry attempt $retryCount failed: ${e.message}")
            }
        }
        task.add("All retry attempts exhausted")
        state = TaskState.Completed
        
        task.error(lastException)
        throw lastException
    }

    override fun getPriorCode(executionState: ExecutionState): String {
        val priorResults = executionConfig?.task_dependencies
            ?.mapNotNull { dependency ->
                executionState.taskResult[dependency]?.let { result ->
                    "## Results from $dependency\n$result"
                }
            }
            ?.joinToString("\n\n")
            ?: ""
        
        return if (priorResults.isNotEmpty()) {
            "# Prior Task Results\n\n$priorResults"
        } else {
            ""
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(MCPToolTask::class.java)
        val MCPTool = com.simiacryptus.cognotik.plan.TaskType(
            "MCPTool",
            MCPToolTaskExecutionConfigData::class.java,
            MCPToolTaskTypeConfig::class.java,
            "Execute tools from Model Context Protocol servers",
            """
              Executes tools from MCP (Model Context Protocol) servers.
              <ul>
                <li>Connect to MCP servers via various transports</li>
                <li>Execute tools with custom arguments</li>
                <li>Configurable timeouts and retry logic</li>
                <li>Support for multiple MCP server integrations</li>
                <li>Structured result handling</li>
                <li>Automatic tool discovery and validation</li>
                <li>Exponential backoff retry strategy</li>
              </ul>
            """
        )
    }
}