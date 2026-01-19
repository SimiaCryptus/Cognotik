package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.mcp.MCPServerRegistry
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import org.slf4j.Logger
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
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
        @Description("Whether to generate a transcript of the tool execution")
        val generate_transcript: Boolean = true,
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
        val typeConfig = typeConfig ?: throw RuntimeException()
        val config = executionConfig ?: throw IllegalStateException("Execution config is required")
        val serverName = config.server_name ?: typeConfig.default_server
        ?: throw IllegalStateException("MCP server name must be specified")
        val toolName = config.tool_name ?: throw IllegalStateException("Tool name must be specified")
        val arguments = config.tool_arguments ?: emptyMap()
        val timeout = config.timeout_seconds ?: typeConfig.default_timeout

        task.header("Executing MCP tool: $toolName", level = 2)
        task.add("Server: <strong>$serverName</strong>")
        val transcriptStream = if (typeConfig.generate_transcript) {
            task.transcript()
        } else null

        task.expandable("Arguments", "<pre><code class=\"language-json\">${JsonUtil.toJson(arguments)}</code></pre>")

        try {
            val result = executeMCPTool(
                serverName = serverName,
                toolName = toolName,
                arguments = arguments,
                timeout = timeout,
                task = task,
                transcriptStream = transcriptStream
            )

            task.add("Tool execution completed successfully", additionalClasses = "text-success")
            task.expandable("Result", "<pre><code class=\"language-json\">${JsonUtil.toJson(result)}</code></pre>")
            transcriptStream?.let {
                it.write("\n\n## Execution Completed Successfully\n".toByteArray())
                it.close()
            }

            resultFn(JsonUtil.toJson(result))
            state = TaskState.Completed
            task.complete()
        } catch (e: Exception) {
            log.error("Error executing MCP tool", e)

            if (typeConfig.auto_retry && shouldRetry(e)) {
                transcriptStream?.let {
                    it.write("\n\n## Retrying after error: ${e.message}\n".toByteArray())
                }
                task.add("Error: ${e.message}. Retrying...", additionalClasses = "text-warning")
                handleRetry(agent, messages, task, resultFn, orchestrationConfig, e)
            } else {
                transcriptStream?.close()
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
        task: SessionTask,
        transcriptStream: FileOutputStream?
    ): Map<String, Any> {
        log.info("Connecting to MCP server: $serverName")
        transcriptStream?.write("# MCP Tool Execution Transcript\n\n".toByteArray())
        transcriptStream?.write("## Server: $serverName\n".toByteArray())
        transcriptStream?.write("## Tool: $toolName\n\n".toByteArray())

        // Get MCP client from registry
        val client = MCPServerRegistry.getClient(serverName)
            ?: throw IllegalStateException("MCP server not found: $serverName")

        try {
            // Ensure client is connected
            if (!client.isConnected()) {
                val buffer = task.add("Establishing connection to MCP server...")
                transcriptStream?.write("### Establishing connection to MCP server...\n".toByteArray())
                client.connect()
                transcriptStream?.write("### Connection established\n\n".toByteArray())
                buffer?.setLength(0)
                buffer?.append("Connection established to <strong>$serverName</strong>")
                task.update()
            }

            // List available tools to verify the tool exists
            val availableTools = client.listTools()
            val tool = availableTools.find { it.name == toolName }
                ?: throw IllegalArgumentException("Tool '$toolName' not found on server '$serverName'. Available tools: ${availableTools.map { it.name }}")

            task.verbose("Tool found: ${tool.name}\nDescription: ${tool.description}")
            transcriptStream?.write("### Tool Information\n".toByteArray())
            transcriptStream?.write("- **Name**: ${tool.name}\n".toByteArray())
            transcriptStream?.write("- **Description**: ${tool.description}\n\n".toByteArray())

            // Execute the tool with timeout
            task.add("Executing tool with timeout of $timeout seconds...")
            transcriptStream?.write("### Execution\n".toByteArray())
            transcriptStream?.write("- **Arguments**: ```json\n${JsonUtil.toJson(arguments)}\n```\n".toByteArray())
            transcriptStream?.write("- **Timeout**: $timeout seconds\n\n".toByteArray())
            val startTime = System.currentTimeMillis()

            val result = try {
                client.executeTool(toolName, arguments, timeout.toLong(), TimeUnit.SECONDS)
            } catch (e: TimeoutException) {
                throw TimeoutException("Tool execution timed out after $timeout seconds")
            }

            val executionTime = System.currentTimeMillis() - startTime
            task.add("Tool executed in ${executionTime}ms")
            transcriptStream?.write("### Results\n".toByteArray())
            transcriptStream?.write("- **Execution Time**: ${executionTime}ms\n".toByteArray())
            transcriptStream?.write("- **Result**: ```json\n${JsonUtil.toJson(result)}\n```\n\n".toByteArray())

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
            transcriptStream?.write("\n### Error\n".toByteArray())
            transcriptStream?.write("```\n${e.message}\n${e.stackTraceToString()}\n```\n".toByteArray())
            throw e
        }
    }

    private fun shouldRetry(e: Exception): Boolean {
        // Determine if the error is retryable
        return when {
            e is SocketTimeoutException -> true
            e is TimeoutException -> true
            e is IOException -> true
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

        val typeConfig = typeConfig ?: throw RuntimeException()
        while (retryCount < typeConfig.max_retries) {
            retryCount++
            task.add("Retry attempt $retryCount of ${typeConfig.max_retries}", additionalClasses = "text-warning")

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
                task.add("Retry attempt $retryCount failed: ${e.message}", additionalClasses = "text-danger")
            }
        }
        task.add("All retry attempts exhausted", additionalClasses = "text-danger")
        state = TaskState.Completed

        task.error(lastException)
        throw lastException
    }

    override fun getPriorCode(executionState: ExecutionState?): String {
        val priorResults = executionConfig?.task_dependencies
            ?.mapNotNull { dependency ->
                executionState?.taskResult[dependency]?.let { result ->
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
        @JvmStatic val MCPTool = TaskType(
            "MCPTool",
            "Online & Search",
            MCPToolTask::class.java,
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
            """,
        )
    }
}