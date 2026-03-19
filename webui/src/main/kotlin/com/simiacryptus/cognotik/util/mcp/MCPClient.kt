package com.simiacryptus.cognotik.util.mcp

import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import org.slf4j.Logger
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Client for communicating with MCP (Model Context Protocol) servers
 */
class MCPClient(
  private val serverConfig: MCPServerConfig
) {
  private var process: Process? = null
  private var writer: BufferedWriter? = null
  private var reader: BufferedReader? = null
  private val executor = Executors.newSingleThreadExecutor()
  private var messageId = 0

  data class MCPTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any>
  )

  fun isConnected(): Boolean = process?.isAlive == true

  fun connect() {
    if (isConnected()) {
      log.warn("Already connected to MCP server")
      return
    }

    log.info("Starting MCP server: ${serverConfig.command}")

    val processBuilder = ProcessBuilder(serverConfig.command)
    serverConfig.env?.let { processBuilder.environment().putAll(it) }

    process = processBuilder.start()
    writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
    reader = BufferedReader(InputStreamReader(process!!.inputStream))

    // Initialize the connection
    sendInitialize()

    log.info("Connected to MCP server: ${serverConfig.name}")
  }

  fun disconnect() {
    try {
      writer?.close()
      reader?.close()
      process?.destroy()
      process?.waitFor(5, TimeUnit.SECONDS)
      if (process?.isAlive == true) {
        process?.destroyForcibly()
      }
    } catch (e: Exception) {
      log.error("Error disconnecting from MCP server", e)
    } finally {
      process = null
      writer = null
      reader = null
    }
  }

  fun listTools(): List<MCPTool> {
    val response = sendRequest("tools/list", emptyMap())
    val tools = response["tools"] as? List<*> ?: emptyList<Any>()

    return tools.mapNotNull { tool ->
      val toolMap = tool as? Map<*, *> ?: return@mapNotNull null
      MCPTool(
        name = toolMap["name"] as? String ?: return@mapNotNull null,
        description = toolMap["description"] as? String ?: "",
        inputSchema = toolMap["inputSchema"] as? Map<String, Any> ?: emptyMap()
      )
    }
  }

  fun executeTool(
    toolName: String,
    arguments: Map<String, Any>,
    timeout: Long,
    unit: TimeUnit
  ): Any? {
    val params = mapOf(
      "name" to toolName,
      "arguments" to arguments
    )

    val future = executor.submit(Callable {
      sendRequest("tools/call", params)
    })

    return try {
      val response = future.get(timeout, unit)
      response["content"]
    } catch (e: TimeoutException) {
      future.cancel(true)
      throw TimeoutException("Tool execution timed out after $timeout ${unit.name.lowercase()}")
    }
  }

  private fun sendInitialize() {
    val params = mapOf(
      "protocolVersion" to "2024-11-05",
      "capabilities" to mapOf(
        "tools" to emptyMap<String, Any>()
      ),
      "clientInfo" to mapOf(
        "name" to "cognotik",
        "version" to "1.0.0"
      )
    )

    sendRequest("initialize", params)
  }

  private fun sendRequest(method: String, params: Map<String, Any>): Map<String, Any> {
    if (!isConnected()) {
      throw IllegalStateException("Not connected to MCP server")
    }

    val id = ++messageId
    val request = mapOf(
      "jsonrpc" to "2.0",
      "id" to id,
      "method" to method,
      "params" to params
    )

    val requestJson = JsonUtil.toJson(request)
    log.debug("Sending MCP request: $requestJson")

    writer!!.write(requestJson)
    writer!!.newLine()
    writer!!.flush()

    // Read response
    val responseLine = reader!!.readLine()
      ?: throw IllegalStateException("No response from MCP server")

    log.debug("Received MCP response: $responseLine")

    val response = JsonUtil.fromJson<Map<String, Any>>(responseLine, Map::class.java)

    if (response.containsKey("error")) {
      val error = response["error"] as Map<*, *>
      throw RuntimeException("MCP error: ${error["message"]}")
    }

    return response["result"] as? Map<String, Any> ?: emptyMap()
  }

  companion object {
    private val log: Logger = LoggerFactory.getLogger(MCPClient::class.java)
  }
}