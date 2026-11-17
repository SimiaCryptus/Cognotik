package com.simiacryptus.cognotik.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.mcp.MCPCallContext
import com.simiacryptus.cognotik.platform.model.mcp.MCPException
import com.simiacryptus.cognotik.platform.model.mcp.MCPTool
import org.slf4j.LoggerFactory

/**
 * MCP server exposing [MCPTool]s over a [McpTransport].
 *
 * Supports `initialize`, `notifications/initialized`, `ping`, `tools/list` (cursor
 * pagination) and `tools/call`. Advertises only `capabilities.tools`.
 * Phase 1: requests are handled sequentially on the transport thread.
 *
 * @param session / [user] identify the context passed to tools (the remote client is not a platform user)
 */
class McpServerHost(
  tools: List<MCPTool>,
  private val transport: McpTransport,
  private val session: Session,
  private val user: User? = null,
  private val serverName: String = "cognotik",
  private val serverVersion: String = "1.0",
  private val pageSize: Int = 100,
) : AutoCloseable {

  private val provider = LocalMCPToolProvider(tools, "host")

  @Volatile
  var negotiatedProtocolVersion: String? = null
    private set

  @Volatile
  var clientInitialized: Boolean = false
    private set

  init {
    require(pageSize > 0) { "pageSize must be > 0" }
  }

  fun start(): McpServerHost {
    transport.start(::handle) { log.info("MCP client disconnected from server host '$serverName'") }
    return this
  }

  private fun handle(message: JsonNode) {
    val method = message.get("method")?.takeIf { it.isTextual }?.asText()
    val id = message.get("id")?.takeUnless { it.isNull }
    if (method == null) {
      log.debug("Ignoring non-request MCP message (server host sends no requests)")
      return
    }
    if (id == null) {
      onNotification(method)
      return
    }
    val reply = try {
      McpJson.result(id, dispatch(method, message.path("params"), id))
    } catch (e: MCPException) {
      McpJson.error(id, e.code, e.message ?: "Error", e.data)
    } catch (e: Exception) {
      log.warn("MCP request '$method' failed", e)
      McpJson.error(id, MCPException.INTERNAL_ERROR, "Internal error: ${e.message}")
    }
    try {
      transport.send(reply)
    } catch (e: Exception) {
      log.warn("Failed to send MCP response for '$method'", e)
    }
  }

  private fun onNotification(method: String) {
    when (method) {
      "notifications/initialized" -> clientInitialized = true
      "notifications/cancelled" -> log.debug("Cancellation is not supported in Phase 1; ignoring")
      else -> log.debug("Ignoring MCP notification {}", method)
    }
  }

  private fun dispatch(method: String, params: JsonNode, id: JsonNode): JsonNode = when (method) {
    "initialize" -> initialize(params)
    "ping" -> McpJson.obj()
    "tools/list" -> listTools(params)
    "tools/call" -> callTool(params, id)
    else -> throw MCPException(MCPException.METHOD_NOT_FOUND, "Method not found: $method")
  }

  private fun initialize(params: JsonNode): JsonNode {
    val requested = params.path("protocolVersion").asText("")
    val version = if (requested in McpJson.SUPPORTED_PROTOCOL_VERSIONS) requested else McpJson.LATEST_PROTOCOL_VERSION
    negotiatedProtocolVersion = version
    return McpJson.obj().apply {
      put("protocolVersion", version)
      set<JsonNode>("capabilities", McpJson.obj().apply {
        set<JsonNode>("tools", McpJson.obj().put("listChanged", false))
      })
      set<JsonNode>("serverInfo", McpJson.obj().put("name", serverName).put("version", serverVersion))
    }
  }

  private fun listTools(params: JsonNode): JsonNode {
    val all = provider.listTools()
    val cursor = params.get("cursor")?.takeUnless { it.isNull }?.asText()
    val start = if (cursor == null) 0 else cursor.toIntOrNull()?.takeIf { it in 0..all.size }
      ?: throw MCPException(MCPException.INVALID_PARAMS, "Invalid cursor: $cursor")
    val end = minOf(all.size, start + pageSize)
    return McpJson.obj().apply {
      set<JsonNode>("tools", McpJson.mapper.createArrayNode().apply {
        all.subList(start, end).forEach { add(McpJson.definitionToJson(it)) }
      })
      if (end < all.size) put("nextCursor", end.toString())
    }
  }

  private fun callTool(params: JsonNode, id: JsonNode): JsonNode {
    val name = params.get("name")?.takeIf { it.isTextual }?.asText()
      ?: throw MCPException(MCPException.INVALID_PARAMS, "Missing tool name")
    val arguments = params.get("arguments")?.takeIf { it.isObject } ?: McpJson.obj()
    val result = provider.callTool(name, arguments, MCPCallContext(session, user, id.asText()))
    return McpJson.resultToJson(result)
  }

  override fun close() {
    transport.close()
  }

  companion object {
    private val log = LoggerFactory.getLogger(McpServerHost::class.java)
  }
}