package com.simiacryptus.cognotik.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.mcp.MCPCallContext
import com.simiacryptus.cognotik.platform.model.mcp.MCPException
import com.simiacryptus.cognotik.platform.model.mcp.MCPTool
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolDefinition
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolProvider
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolResult
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP client exposing a remote server's tools as an [MCPToolProvider].
 *
 * Performs the `initialize` handshake lazily (no client capabilities advertised), lists
 * tools with cursor pagination (cached until `notifications/tools/list_changed`), and
 * calls tools via `tools/call`. JSON-RPC errors from `tools/call` are returned to the
 * loop as `isError` results prefixed with "MCP error"; a timed-out request is followed
 * by `notifications/cancelled`.
 */
class McpClientToolProvider(
  override val name: String,
  private val transport: McpTransport,
  private val requestTimeout: Duration = Duration.ofSeconds(120),
  private val clientName: String = "cognotik",
  private val clientVersion: String = "1.0",
) : MCPToolProvider {

  private val nextId = AtomicLong(1)
  private val pending = ConcurrentHashMap<Long, CompletableFuture<JsonNode>>()
  private val initLock = Any()

  @Volatile private var initialized = false
  @Volatile private var started = false
  @Volatile private var cachedTools: List<MCPToolDefinition>? = null

  @Volatile
  var protocolVersion: String? = null
    private set

  @Volatile
  var serverInfo: JsonNode? = null
    private set

  /** Eagerly performs the handshake (otherwise done on first use). */
  fun connect(): McpClientToolProvider {
    ensureInitialized()
    return this
  }

  private fun ensureInitialized() {
    if (initialized) return
    synchronized(initLock) {
      if (initialized) return
      if (!started) {
        transport.start(::onMessage, ::onClosed)
        started = true
      }
      val result = request("initialize", McpJson.obj().apply {
        put("protocolVersion", McpJson.LATEST_PROTOCOL_VERSION)
        set<JsonNode>("capabilities", McpJson.obj())
        set<JsonNode>("clientInfo", McpJson.obj().put("name", clientName).put("version", clientVersion))
      })
      val version = result.path("protocolVersion").asText("")
      if (version !in McpJson.SUPPORTED_PROTOCOL_VERSIONS) {
        throw MCPException(MCPException.INVALID_REQUEST, "MCP server '$name' negotiated unsupported protocol version '$version'")
      }
      if (!result.path("capabilities").has("tools")) {
        log.warn("MCP server '{}' does not advertise the tools capability", name)
      }
      protocolVersion = version
      serverInfo = result.get("serverInfo")
      transport.send(McpJson.notification("notifications/initialized"))
      initialized = true
      log.info("Connected to MCP server '{}' (protocol {}, info {})", name, version, serverInfo)
    }
  }

  override fun listTools(): List<MCPToolDefinition> {
    cachedTools?.let { return it }
    ensureInitialized()
    val all = mutableListOf<MCPToolDefinition>()
    var cursor: String? = null
    do {
      val params = McpJson.obj().apply { cursor?.let { put("cursor", it) } }
      val result = request("tools/list", params)
      result.path("tools").forEach { all += McpJson.definitionFromJson(it) }
      cursor = result.get("nextCursor")?.takeIf { it.isTextual }?.asText()
    } while (cursor != null)
    cachedTools = all.toList()
    return all
  }

  override fun callTool(name: String, arguments: JsonNode, context: MCPCallContext): MCPToolResult {
    ensureInitialized()
    val params = McpJson.obj().apply {
      put("name", name)
      set<JsonNode>("arguments", if (arguments.isObject) arguments else McpJson.obj())
    }
    return try {
      McpJson.resultFromJson(request("tools/call", params))
    } catch (e: MCPException) {
      MCPToolResult.error("MCP error ${e.code}: ${e.message}")
    }
  }

  private fun request(method: String, params: JsonNode?): JsonNode {
    val id = nextId.getAndIncrement()
    val future = CompletableFuture<JsonNode>()
    pending[id] = future
    try {
      transport.send(McpJson.request(id, method, params))
      return future.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
      runCatching {
        transport.send(
          McpJson.notification(
            "notifications/cancelled",
            McpJson.obj().put("requestId", id).put("reason", "timeout")
          )
        )
      }
      throw MCPException(MCPException.REQUEST_TIMEOUT, "Timed out after $requestTimeout waiting for '$method' from '$name'")
    } catch (e: ExecutionException) {
      throw (e.cause as? MCPException)
        ?: MCPException(MCPException.INTERNAL_ERROR, "MCP request '$method' failed: ${e.cause?.message}", cause = e.cause)
    } catch (e: MCPException) {
      throw e
    } catch (e: InterruptedException) {
      Thread.currentThread().interrupt()
      throw e
    } catch (e: Exception) {
      throw MCPException(MCPException.INTERNAL_ERROR, "MCP request '$method' to '$name' failed: ${e.message}", cause = e)
    } finally {
      pending.remove(id)
    }
  }

  private fun onMessage(message: JsonNode) {
    val method = message.get("method")?.takeIf { it.isTextual }?.asText()
    if (method != null) {
      val id = message.get("id")?.takeUnless { it.isNull }
      if (id == null) {
        when (method) {
          "notifications/tools/list_changed" -> cachedTools = null
          "notifications/message" -> log.debug("[{}] {}", name, message.path("params"))
          else -> log.debug("[{}] Ignoring notification {}", name, method)
        }
      } else {
        val reply = if (method == "ping") McpJson.result(id, McpJson.obj())
        else McpJson.error(id, MCPException.METHOD_NOT_FOUND, "Client does not support $method")
        runCatching { transport.send(reply) }.onFailure { log.debug("[$name] Failed to reply to $method", it) }
      }
      return
    }
    val id = message.get("id")?.takeIf { it.canConvertToLong() }?.asLong() ?: run {
      log.debug("[{}] Ignoring response without a numeric id", name)
      return
    }
    val future = pending[id] ?: run {
      log.debug("[{}] Ignoring response for unknown or expired request {}", name, id)
      return
    }
    val error = message.get("error")
    if (error != null && !error.isNull) {
      future.completeExceptionally(
        MCPException(error.path("code").asInt(MCPException.INTERNAL_ERROR), error.path("message").asText("Error"), error.get("data"))
      )
    } else {
      future.complete(message.get("result") ?: McpJson.obj())
    }
  }

  private fun onClosed() {
    val closed = MCPException(MCPException.REQUEST_TIMEOUT, "MCP connection '$name' closed")
    pending.values.forEach { it.completeExceptionally(closed) }
    initialized = false
  }

  override fun close() {
    transport.close()
    onClosed()
  }

  companion object {
    private val log = LoggerFactory.getLogger(McpClientToolProvider::class.java)

    /** Connects to an MCP server launched as a subprocess (stdio transport). */
    fun stdio(
      name: String,
      command: List<String>,
      env: Map<String, String> = emptyMap(),
      workingDir: File? = null,
      requestTimeout: Duration = Duration.ofSeconds(120),
    ) = McpClientToolProvider(name, StdioTransport.spawn(command, env, workingDir, name), requestTimeout)

    /**
     * Client ⇄ in-memory transport ⇄ [McpServerHost] serving [tools]. Exercises the real
     * protocol in-process (loopback proof / tests). Closing the client closes the host transport.
     */
    fun loopback(
      name: String,
      tools: List<MCPTool>,
      session: Session,
      user: User? = null,
    ): McpClientToolProvider {
      val (clientSide, serverSide) = InMemoryTransport.pair()
      McpServerHost(tools, serverSide, session, user).start()
      return McpClientToolProvider(name, clientSide)
    }
  }
}