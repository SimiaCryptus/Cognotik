package com.simiacryptus.cognotik.mcp

import com.fasterxml.jackson.databind.JsonNode
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.mcp.MCPCallContext
import com.simiacryptus.cognotik.platform.model.mcp.MCPException
import com.simiacryptus.cognotik.platform.model.mcp.MCPTool
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolDefinition
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * MCP server exposing [MCPTool]s over a [McpTransport].
 *
  * Supports `initialize`, `notifications/initialized`, `notifications/cancelled`, `ping`,
  * `tools/list` (cursor pagination), `tools/call`, JSON-RPC 2.0 batch requests, and optional
  * dynamic tool updates (`notifications/tools/list_changed`). Tool calls are dispatched on
  * [executor] so the transport reader thread is not blocked.
 *
 * @param session / [user] identify the context passed to tools (the remote client is not a platform user)
  * @param requireInitializedNotification when `true`, capability methods also require `notifications/initialized`
  * @param listChanged advertises `capabilities.tools.listChanged` and emits `notifications/tools/list_changed` on tool updates
  * @param executor optional [ExecutorService] for `tools/call` execution; if omitted, an internal daemon pool is created and shut down on [close]
 */
class McpServerHost(
  tools: List<MCPTool>,
  private val transport: McpTransport,
  private val session: Session,
  private val user: User? = null,
  private val serverName: String = "cognotik",
  private val serverVersion: String = "1.0",
  private val pageSize: Int = 100,
   private val requireInitializedNotification: Boolean = false,
   private val listChanged: Boolean = false,
   executor: ExecutorService? = null,
) : AutoCloseable {

   private val toolsLock = Any()
   private var currentTools: List<MCPTool> = tools.toList()
   @Volatile
   private var provider = LocalMCPToolProvider(currentTools, "host")

   private val ownsExecutor = executor == null
   private val workerExecutor: ExecutorService = executor ?: Executors.newCachedThreadPool { r ->
     Thread(r, "mcp-server-$serverName-worker").apply { isDaemon = true }
   }

   private val started = AtomicBoolean(false)
   private val closed = AtomicBoolean(false)
   private val inFlight = ConcurrentHashMap<String, InFlightRequest>()

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
     check(!closed.get()) { "McpServerHost '$serverName' is closed" }
     check(started.compareAndSet(false, true)) { "McpServerHost '$serverName' already started" }
     transport.start(::handle) {
       log.info("MCP client disconnected from server host '$serverName'")
       cancelAllInFlight()
     }
    return this
  }
   /** Returns a snapshot of currently registered tool definitions. */
   fun listTools(): List<MCPToolDefinition> = provider.listTools()
   /** Replaces all registered tools atomically and notifies the client if [listChanged] is enabled. */
   fun setTools(tools: List<MCPTool>) {
     synchronized(toolsLock) {
       val snapshot = tools.toList()
       provider = LocalMCPToolProvider(snapshot, "host")
       currentTools = snapshot
     }
     notifyToolsListChanged()
   }
   /** Registers or replaces a tool by name and notifies the client if [listChanged] is enabled. */
   fun registerTool(tool: MCPTool) {
     synchronized(toolsLock) {
       val updated = currentTools.filterNot { it.definition.name == tool.definition.name } + tool
       provider = LocalMCPToolProvider(updated, "host")
       currentTools = updated
     }
     notifyToolsListChanged()
   }
   /** Unregisters a tool by name. Returns `true` if removed and notifies the client if [listChanged] is enabled. */
   fun unregisterTool(name: String): Boolean {
     val removed = synchronized(toolsLock) {
       val updated = currentTools.filterNot { it.definition.name == name }
       if (updated.size == currentTools.size) {
         false
       } else {
         provider = LocalMCPToolProvider(updated, "host")
         currentTools = updated
         true
       }
     }
     if (removed) notifyToolsListChanged()
     return removed
   }
   /** Refreshes the tool list (optionally replacing it) and emits `notifications/tools/list_changed` when enabled. */
   fun refreshTools(tools: List<MCPTool>? = null) {
     if (tools != null) {
       setTools(tools)
     } else {
       notifyToolsListChanged()
     }
   }
   private fun notifyToolsListChanged() {
     if (!listChanged || closed.get() || !started.get()) return
     if (negotiatedProtocolVersion == null) return
     if (requireInitializedNotification && !clientInitialized) return
     sendSafely("notifications/tools/list_changed", McpJson.notification("notifications/tools/list_changed"))
   }


  private fun handle(message: JsonNode) {
     if (message.isArray) {
       handleBatch(message)
       return
     }
     if (!message.isObject) {
       sendSafely("invalid", McpJson.error(null, MCPException.INVALID_REQUEST, "Invalid JSON-RPC message"))
      return
    }
     val method = message.get("method")?.takeIf { it.isTextual }?.asText() ?: "request"
     processObject(message).thenAccept { reply ->
       if (reply != null && !closed.get()) {
         sendSafely(method, reply)
       }
     }
   }
   private fun handleBatch(batch: JsonNode) {
     if (batch.isEmpty) {
       sendSafely("batch", McpJson.error(null, MCPException.INVALID_REQUEST, "Empty JSON-RPC batch"))
       return
     }
     val futures = batch.map { element -> processObject(element) }
     CompletableFuture.allOf(*futures.toTypedArray()).thenAccept {
       if (closed.get()) return@thenAccept
       val responses = McpJson.mapper.createArrayNode()
       futures.forEach { future ->
         future.getNow(null)?.let { responses.add(it) }
       }
       if (!responses.isEmpty) {
         sendSafely("batch", responses)
       }
     }
   }
   private fun processObject(message: JsonNode): CompletableFuture<JsonNode?> {
     if (!message.isObject) {
       return CompletableFuture.completedFuture(
         McpJson.error(null, MCPException.INVALID_REQUEST, "Invalid JSON-RPC request")
       )
     }
     val jsonrpcNode = message.get("jsonrpc")
     if (jsonrpcNode != null && (!jsonrpcNode.isTextual || jsonrpcNode.asText() != McpJson.JSONRPC_VERSION)) {
       return CompletableFuture.completedFuture(
         McpJson.error(validErrorId(message.get("id")), MCPException.INVALID_REQUEST, "Unsupported JSON-RPC version")
       )
     }
     val rawId = message.get("id")
     if (rawId != null && !isValidId(rawId)) {
       return CompletableFuture.completedFuture(
         McpJson.error(null, MCPException.INVALID_REQUEST, "Invalid JSON-RPC id")
       )
     }
     val methodNode = message.get("method")
     if (methodNode == null) {
       if (message.has("result") || message.has("error")) {
         log.debug("Ignoring non-request MCP message (server host sends no requests)")
         return CompletableFuture.completedFuture(null)
       }
       return CompletableFuture.completedFuture(
         McpJson.error(validErrorId(rawId), MCPException.INVALID_REQUEST, "Missing method")
       )
     }
     if (!methodNode.isTextual) {
       return CompletableFuture.completedFuture(
         McpJson.error(validErrorId(rawId), MCPException.INVALID_REQUEST, "Invalid method")
       )
     }
     val method = methodNode.asText()
     val id = rawId?.takeUnless { it.isNull }
    if (id == null) {

       onNotification(method, message.path("params"))
       return CompletableFuture.completedFuture(null)
     }
     return try {
       if (method != "initialize" && method != "ping") {
         ensureInitialized()
       }
       val params = message.path("params")
       when (method) {
         "initialize" -> CompletableFuture.completedFuture(McpJson.result(id, initialize(params)))
         "ping" -> CompletableFuture.completedFuture(McpJson.result(id, McpJson.obj()))
         "tools/list" -> CompletableFuture.completedFuture(McpJson.result(id, listTools(params)))
         "tools/call" -> submitToolCall(params, id)
         else -> throw MCPException(MCPException.METHOD_NOT_FOUND, "Method not found: $method")
       }
     } catch (e: MCPException) {
       CompletableFuture.completedFuture(McpJson.error(id, e.code, e.message ?: "Error", e.data))
     } catch (e: Exception) {
       log.warn("MCP request '$method' failed", e)
       CompletableFuture.completedFuture(McpJson.error(id, MCPException.INTERNAL_ERROR, "Internal error: ${e.message}"))
     }
   }

   private fun ensureInitialized() {
     if (negotiatedProtocolVersion == null) {
       throw MCPException(MCPException.INVALID_REQUEST, "Server not initialized; client must send 'initialize' first")
     }
     if (requireInitializedNotification && !clientInitialized) {
       throw MCPException(
         MCPException.INVALID_REQUEST,
         "Client initialization incomplete; 'notifications/initialized' required",
       )
     }
   }

   private fun onNotification(method: String, params: JsonNode) {
    when (method) {
       "notifications/initialized" -> {
         if (negotiatedProtocolVersion != null) {
           clientInitialized = true
         } else {
           log.debug("Ignoring notifications/initialized received before initialize")
         }
       }

       "notifications/cancelled" -> cancelRequest(params)
      else -> log.debug("Ignoring MCP notification {}", method)
    }
  }

   private fun cancelRequest(params: JsonNode) {
     val requestId = params.get("requestId")?.takeUnless { it.isNull } ?: run {
       log.debug("Ignoring notifications/cancelled without requestId")
       return
     }
     val key = idKey(requestId)
     val entry = inFlight.remove(key)
     if (entry != null) {
       val reason = params.get("reason")?.takeIf { it.isTextual }?.asText()
       log.debug("Cancelling in-flight MCP request id={}{}", key, reason?.let { " ($it)" } ?: "")
       entry.cancel()
     } else {
       log.debug("Ignoring notifications/cancelled for unknown or completed request id={}", key)
     }
  }

  private fun initialize(params: JsonNode): JsonNode {
    val requested = params.path("protocolVersion").asText("")
    val version = if (requested in McpJson.SUPPORTED_PROTOCOL_VERSIONS) requested else McpJson.LATEST_PROTOCOL_VERSION
    negotiatedProtocolVersion = version
    return McpJson.obj().apply {
      put("protocolVersion", version)
      set<JsonNode>("capabilities", McpJson.obj().apply {
         set<JsonNode>("tools", McpJson.obj().put("listChanged", listChanged))
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

   private fun submitToolCall(params: JsonNode, id: JsonNode): CompletableFuture<JsonNode?> {
    val name = params.get("name")?.takeIf { it.isTextual }?.asText()
      ?: throw MCPException(MCPException.INVALID_PARAMS, "Missing tool name")
    val arguments = params.get("arguments")?.takeIf { it.isObject } ?: McpJson.obj()
     if (closed.get()) {
       return CompletableFuture.completedFuture(
         McpJson.error(id, MCPException.INTERNAL_ERROR, "MCP server host is closed")
       )
     }
     val responseFuture = CompletableFuture<JsonNode?>()
     val key = idKey(id)
     val entry = InFlightRequest(responseFuture)
     inFlight.put(key, entry)?.let { previous ->
       log.debug("Duplicate in-flight MCP request id={}, cancelling previous entry", key)
       previous.cancel()
     }
     try {
       val workerFuture = workerExecutor.submit {
         try {
           if (entry.cancelled.get() || Thread.currentThread().isInterrupted) {
             responseFuture.complete(null)
             return@submit
           }
           val reply = try {
             val result = provider.callTool(name, arguments, MCPCallContext(session, user, id.asText()))
             McpJson.result(id, McpJson.resultToJson(result))
           } catch (e: MCPException) {
             McpJson.error(id, e.code, e.message ?: "Error", e.data)
           } catch (e: InterruptedException) {
             log.debug("MCP tool call '{}' (id={}) interrupted", name, key)
             responseFuture.complete(null)
             return@submit
           } catch (e: Exception) {
             log.warn("MCP tool call '{}' failed", name, e)
             McpJson.error(id, MCPException.INTERNAL_ERROR, "Internal error: ${e.message}")
           }
           if (entry.cancelled.get() || Thread.currentThread().isInterrupted) {
             responseFuture.complete(null)
           } else {
             responseFuture.complete(reply)
           }
         } finally {
           inFlight.remove(key, entry)
           Thread.interrupted()
         }
       }
       entry.setFuture(workerFuture)
     } catch (e: RejectedExecutionException) {
       inFlight.remove(key, entry)
       if (entry.cancelled.get() || closed.get()) {
         responseFuture.complete(null)
       } else {
         log.warn("MCP tool call '{}' rejected by executor", name, e)
         responseFuture.complete(McpJson.error(id, MCPException.INTERNAL_ERROR, "Server busy: request rejected"))
       }
     }
     return responseFuture
   }

   private fun sendSafely(context: String, message: JsonNode) {
     if (closed.get()) return
     try {
       transport.send(message)
     } catch (e: Exception) {
       log.warn("Failed to send MCP response for '{}'", context, e)
     }
   }

   private fun cancelAllInFlight() {
     val entries = inFlight.values.toList()
     inFlight.clear()
     entries.forEach { it.cancel() }
   }

   private fun isValidId(id: JsonNode): Boolean = id.isTextual || id.isNumber || id.isNull

   private fun validErrorId(id: JsonNode?): JsonNode? = id?.takeIf { it.isTextual || it.isNumber }

   private fun idKey(id: JsonNode): String = when {
     id.isTextual -> "s:${id.asText()}"
     id.isNumber -> "n:${id.decimalValue().stripTrailingZeros().toPlainString()}"
     else -> "j:${McpJson.encode(id)}"
   }

   private class InFlightRequest(
     private val responseFuture: CompletableFuture<JsonNode?>,
   ) {
     val cancelled = AtomicBoolean(false)
     private val futureRef = AtomicReference<Future<*>?>()

     fun setFuture(future: Future<*>) {
       futureRef.set(future)
       if (cancelled.get()) {
         future.cancel(true)
       }
     }

     fun cancel() {
       if (cancelled.compareAndSet(false, true)) {
         futureRef.get()?.cancel(true)
         responseFuture.complete(null)
       }
     }
  }

  override fun close() {
     if (!closed.compareAndSet(false, true)) return
     cancelAllInFlight()
     if (ownsExecutor) {
       workerExecutor.shutdownNow()
     }
    transport.close()
  }

  companion object {
    private val log = LoggerFactory.getLogger(McpServerHost::class.java)
  }
}