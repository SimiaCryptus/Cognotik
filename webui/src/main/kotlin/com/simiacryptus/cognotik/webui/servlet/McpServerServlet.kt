package com.simiacryptus.cognotik.webui.servlet

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.simiacryptus.cognotik.mcp.McpJson
import com.simiacryptus.cognotik.mcp.McpServerHost
import com.simiacryptus.cognotik.mcp.McpTransport
import com.simiacryptus.cognotik.platform.ServiceRouter
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.mcp.MCPException
import com.simiacryptus.cognotik.platform.model.mcp.MCPTool
import com.simiacryptus.cognotik.platform.model.mcp.MCPToolDefinition
import jakarta.servlet.AsyncContext
import jakarta.servlet.AsyncEvent
import jakarta.servlet.AsyncListener
import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Jetty servlet hosting MCP tools over Streamable HTTP and Server-Sent Events (SSE).
 *
 * Implements the MCP Streamable HTTP transport:
 * - `POST`: Accepts JSON-RPC 2.0 requests, notifications, responses, and batches; returns
 *   `application/json` or `text/event-stream` for requests, or `202 Accepted` for notifications.
 *   Initializes sessions on `initialize` and returns the `Mcp-Session-Id` header.
 * - `GET`: Opens an SSE stream (`text/event-stream`) for server-initiated notifications/requests,
 *   supporting `Last-Event-ID` event replay and periodic keepalives.
 * - `DELETE`: Terminates an active MCP session identified by `Mcp-Session-Id`.
 *
 * Authenticates every request via [ServiceRouter.authenticate], binds [User] and [Session] into
 * [McpServerHost] (and therefore [com.simiacryptus.cognotik.platform.model.mcp.MCPCallContext]),
 * and enforces `Origin` / `Host` / CORS validation for localhost and browser protection.
 */
class McpServerServlet(
  private val toolsProvider: (User, Session) -> List<MCPTool>,
  private val serverName: String = "cognotik",
  private val serverVersion: String = "1.0",
  private val pageSize: Int = 100,
  private val requireInitializedNotification: Boolean = false,
  private val listChanged: Boolean = false,
  private val allowedOrigins: Set<String> = emptySet(),
  private val allowedHosts: Set<String> = emptySet(),
  private val allowNullOrigin: Boolean = false,
  private val sessionTimeoutMs: Long = TimeUnit.MINUTES.toMillis(30),
  private val requestTimeoutMs: Long = TimeUnit.MINUTES.toMillis(5),
  private val sseKeepAliveMs: Long = TimeUnit.SECONDS.toMillis(20),
  private val maxBufferedEvents: Int = 100,
  executor: ExecutorService? = null,
) : HttpServlet(), AutoCloseable {

  constructor(
    tools: List<MCPTool> = emptyList(),
    serverName: String = "cognotik",
    serverVersion: String = "1.0",
    pageSize: Int = 100,
    requireInitializedNotification: Boolean = false,
    listChanged: Boolean = false,
    allowedOrigins: Set<String> = emptySet(),
    allowedHosts: Set<String> = emptySet(),
    allowNullOrigin: Boolean = false,
    sessionTimeoutMs: Long = TimeUnit.MINUTES.toMillis(30),
    requestTimeoutMs: Long = TimeUnit.MINUTES.toMillis(5),
    sseKeepAliveMs: Long = TimeUnit.SECONDS.toMillis(20),
    maxBufferedEvents: Int = 100,
    executor: ExecutorService? = null,
  ) : this(
    toolsProvider = { _, _ -> tools },
    serverName = serverName,
    serverVersion = serverVersion,
    pageSize = pageSize,
    requireInitializedNotification = requireInitializedNotification,
    listChanged = listChanged,
    allowedOrigins = allowedOrigins,
    allowedHosts = allowedHosts,
    allowNullOrigin = allowNullOrigin,
    sessionTimeoutMs = sessionTimeoutMs,
    requestTimeoutMs = requestTimeoutMs,
    sseKeepAliveMs = sseKeepAliveMs,
    maxBufferedEvents = maxBufferedEvents,
    executor = executor,
  )

  private val sessions = ConcurrentHashMap<String, McpHttpSession>()
  private val dynamicToolsOverride = AtomicReference<List<MCPTool>?>(null)
  private val closed = AtomicBoolean(false)

  private val ownsExecutor = executor == null
  private val workerExecutor: ExecutorService = executor ?: Executors.newCachedThreadPool { r ->
    Thread(r, "mcp-http-$serverName-worker").apply { isDaemon = true }
  }

  private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
    Thread(r, "mcp-http-$serverName-maintenance").apply { isDaemon = true }
  }.apply {
    if (sseKeepAliveMs > 0) {
      scheduleWithFixedDelay(::sendKeepAlives, sseKeepAliveMs, sseKeepAliveMs, TimeUnit.MILLISECONDS)
    }
    if (sessionTimeoutMs > 0) {
      val sweepIntervalMs = minOf(sessionTimeoutMs, TimeUnit.MINUTES.toMillis(1)).coerceAtLeast(1000L)
      scheduleWithFixedDelay(::sweepExpiredSessions, sweepIntervalMs, sweepIntervalMs, TimeUnit.MILLISECONDS)
    }
  }

  init {
    require(pageSize > 0) { "pageSize must be > 0" }
    require(maxBufferedEvents >= 0) { "maxBufferedEvents must be >= 0" }
  }

  val activeSessionCount: Int
    get() = sessions.size

  /** Replaces tools for all active and future sessions and emits `notifications/tools/list_changed` when enabled. */
  fun setTools(tools: List<MCPTool>) {
    val snapshot = tools.toList()
    dynamicToolsOverride.set(snapshot)
    sessions.values.forEach { it.host.setTools(snapshot) }
  }

  /** Registers or replaces a tool across active sessions (and overrides future sessions if dynamic tools are used). */
  fun registerTool(tool: MCPTool) {
    sessions.values.forEach { it.host.registerTool(tool) }
    dynamicToolsOverride.updateAndGet { current ->
      current?.let { list -> list.filterNot { it.definition.name == tool.definition.name } + tool }
    }
  }

  /** Unregisters a tool by name across active sessions. Returns `true` if removed from at least one session. */
  fun unregisterTool(name: String): Boolean {
    var removed = false
    sessions.values.forEach {
      if (it.host.unregisterTool(name)) removed = true
    }
    dynamicToolsOverride.updateAndGet { current ->
      current?.filterNot { it.definition.name == name }
    }
    return removed
  }

  /** Returns tool definitions from an active session or the configured tools override. */
  fun listTools(sessionId: String? = null): List<MCPToolDefinition> {
    if (sessionId != null) {
      return sessions[sessionId]?.host?.listTools() ?: emptyList()
    }
    return sessions.values.firstOrNull()?.host?.listTools()
      ?: dynamicToolsOverride.get()?.map { it.definition }
      ?: emptyList()
  }

  /** Closes and removes an active MCP session by ID. */
  fun closeSession(sessionId: String): Boolean {
    val session = sessions.remove(sessionId) ?: return false
    session.close()
    return true
  }

  override fun doOptions(req: HttpServletRequest, resp: HttpServletResponse) {
    if (!validateOriginAndHost(req, resp)) return
    applyCorsHeaders(req, resp)
    resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  override fun doPost(req: HttpServletRequest, resp: HttpServletResponse) {
    if (closed.get()) {
      resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MCP server is closed")
      return
    }
    if (!validateOriginAndHost(req, resp)) return
    applyCorsHeaders(req, resp)

    val user = authenticateRequest(req, resp) ?: return
    if (!validateProtocolVersionHeader(req, resp)) return

    val contentType = req.contentType?.substringBefore(';')?.trim()?.lowercase()
    if (contentType != null && contentType != MIME_JSON) {
      resp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json")
      return
    }

    val accept = parseAcceptPreferences(req.getHeader("Accept"))
    if (!accept.allowsJson && !accept.allowsSse) {
      resp.sendError(
        HttpServletResponse.SC_NOT_ACCEPTABLE,
        "Accept header must allow application/json or text/event-stream",
      )
      return
    }

    val bodyText = try {
      readRequestBody(req)
    } catch (e: IOException) {
      log.debug("Failed to read MCP POST body", e)
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, null, MCPException.INVALID_REQUEST, "Failed to read body")
      return
    }
    if (bodyText.isBlank()) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, null, MCPException.INVALID_REQUEST, "Empty request body")
      return
    }

    val payload = try {
      McpJson.decode(bodyText)
    } catch (e: JsonProcessingException) {
      sendJsonError(resp, HttpServletResponse.SC_BAD_REQUEST, null, JSONRPC_PARSE_ERROR, "Parse error: ${e.originalMessage}")
      return
    }

    val sessionIdHeader = req.getHeader(HEADER_SESSION_ID)?.trim()?.takeIf { it.isNotEmpty() }
    val session = if (sessionIdHeader != null) {
      val existing = sessions[sessionIdHeader]
      if (existing == null || existing.isClosed()) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown or expired MCP session")
        return
      }
      if (existing.user != user) {
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Session does not belong to authenticated user")
        return
      }
      existing.touch()
      existing
    } else {
      if (!containsInitializeRequest(payload)) {
        sendJsonError(
          resp,
          HttpServletResponse.SC_BAD_REQUEST,
          extractSingleRequestId(payload),
          MCPException.INVALID_REQUEST,
          "Missing $HEADER_SESSION_ID header",
        )
        return
      }
      createSession(user)
    }

    resp.setHeader(HEADER_SESSION_ID, session.id)
    session.host.negotiatedProtocolVersion?.let { resp.setHeader(HEADER_PROTOCOL_VERSION, it) }

    val responseFuture = session.transport.deliverInbound(payload)
    if (responseFuture == null) {
      resp.status = HttpServletResponse.SC_ACCEPTED
      return
    }

    val useSse = accept.preferSse || (!accept.allowsJson && accept.allowsSse)
    val async = req.startAsync().apply {
      timeout = requestTimeoutMs
    }
    val completed = AtomicBoolean(false)

    if (useSse) {
      resp.status = HttpServletResponse.SC_OK
      resp.contentType = MIME_SSE
      resp.characterEncoding = StandardCharsets.UTF_8.name()
      resp.setHeader("Cache-Control", "no-cache, no-transform")
      resp.setHeader("X-Accel-Buffering", "no")
      try {
        resp.flushBuffer()
      } catch (e: IOException) {
        completed.set(true)
        responseFuture.cancel(false)
        async.complete()
        return
      }
    }

    async.addListener(object : AsyncListener {
      override fun onComplete(event: AsyncEvent) {
        completed.set(true)
      }

      override fun onTimeout(event: AsyncEvent) {
        if (completed.compareAndSet(false, true)) {
          responseFuture.cancel(false)
          val asyncResp = event.asyncContext.response as HttpServletResponse
          if (!asyncResp.isCommitted) {
            sendJsonError(
              asyncResp,
              HttpServletResponse.SC_GATEWAY_TIMEOUT,
              extractSingleRequestId(payload),
              MCPException.INTERNAL_ERROR,
              "Request timed out",
            )
          } else if (useSse) {
            tryWriteSseEvent(
              asyncResp,
              null,
              McpJson.encode(
                McpJson.error(extractSingleRequestId(payload), MCPException.INTERNAL_ERROR, "Request timed out")
              ),
            )
          }
          safeComplete(event.asyncContext)
        }
      }

      override fun onError(event: AsyncEvent) {
        if (completed.compareAndSet(false, true)) {
          responseFuture.cancel(false)
          safeComplete(event.asyncContext)
        }
      }

      override fun onStartAsync(event: AsyncEvent) = Unit
    })

    responseFuture.whenComplete { reply, throwable ->
      if (!completed.compareAndSet(false, true)) return@whenComplete
      try {
        val asyncResp = async.response as HttpServletResponse
        if (throwable != null) {
          val cause = if (throwable is CancellationException) throwable else throwable.cause ?: throwable
          val errorJson = if (cause is CancellationException) {
            McpJson.error(extractSingleRequestId(payload), REQUEST_CANCELLED_ERROR, "Request cancelled")
          } else {
            log.warn("MCP request failed for session '{}'", session.id, cause)
            McpJson.error(extractSingleRequestId(payload), MCPException.INTERNAL_ERROR, "Internal error: ${cause.message}")
          }
          if (useSse) {
            tryWriteSseEvent(asyncResp, null, McpJson.encode(errorJson))
          } else if (!asyncResp.isCommitted) {
            val status = if (cause is CancellationException) HttpServletResponse.SC_OK else HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            writeJsonResponse(asyncResp, status, errorJson)
          }
          return@whenComplete
        }

        session.touch()
        session.host.negotiatedProtocolVersion?.let {
          if (!asyncResp.isCommitted) asyncResp.setHeader(HEADER_PROTOCOL_VERSION, it)
        }

        if (useSse) {
          val eventId = session.transport.nextEventId()
          tryWriteSseEvent(asyncResp, eventId, McpJson.encode(reply))
        } else if (!asyncResp.isCommitted) {
          writeJsonResponse(asyncResp, HttpServletResponse.SC_OK, reply)
        }
      } catch (e: IOException) {
        log.debug("Failed writing MCP POST response for session '{}': {}", session.id, e.message)
      } finally {
        safeComplete(async)
      }
    }
  }

  override fun doGet(req: HttpServletRequest, resp: HttpServletResponse) {
    if (closed.get()) {
      resp.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MCP server is closed")
      return
    }
    if (!validateOriginAndHost(req, resp)) return
    applyCorsHeaders(req, resp)

    val user = authenticateRequest(req, resp) ?: return
    if (!validateProtocolVersionHeader(req, resp)) return

    val accept = parseAcceptPreferences(req.getHeader("Accept"))
    if (!accept.allowsSse) {
      resp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "Accept header must allow text/event-stream")
      return
    }

    val sessionId = req.getHeader(HEADER_SESSION_ID)?.trim()?.takeIf { it.isNotEmpty() }
    if (sessionId == null) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing $HEADER_SESSION_ID header")
      return
    }
    val session = sessions[sessionId]
    if (session == null || session.isClosed()) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown or expired MCP session")
      return
    }
    if (session.user != user) {
      resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Session does not belong to authenticated user")
      return
    }
    session.touch()

    val lastEventId = req.getHeader(HEADER_LAST_EVENT_ID)?.trim()?.toLongOrNull()

    resp.status = HttpServletResponse.SC_OK
    resp.contentType = MIME_SSE
    resp.characterEncoding = StandardCharsets.UTF_8.name()
    resp.setHeader("Cache-Control", "no-cache, no-transform")
    resp.setHeader("X-Accel-Buffering", "no")
    resp.setHeader(HEADER_SESSION_ID, session.id)
    session.host.negotiatedProtocolVersion?.let { resp.setHeader(HEADER_PROTOCOL_VERSION, it) }

    val async = req.startAsync().apply {
      timeout = 0L
    }
    val subscriber = SseSubscriber(async)

    async.addListener(object : AsyncListener {
      override fun onComplete(event: AsyncEvent) {
        session.transport.removeSubscriber(subscriber)
      }

      override fun onTimeout(event: AsyncEvent) {
        session.transport.removeSubscriber(subscriber)
        subscriber.close()
      }

      override fun onError(event: AsyncEvent) {
        session.transport.removeSubscriber(subscriber)
        subscriber.close()
      }

      override fun onStartAsync(event: AsyncEvent) = Unit
    })

    try {
      resp.flushBuffer()
      if (!subscriber.sendRaw(": connected\n\n".toByteArray(StandardCharsets.UTF_8))) {
        subscriber.close()
        return
      }
      session.transport.addSubscriber(subscriber, lastEventId)
    } catch (e: IOException) {
      session.transport.removeSubscriber(subscriber)
      subscriber.close()
    }
  }

  override fun doDelete(req: HttpServletRequest, resp: HttpServletResponse) {
    if (!validateOriginAndHost(req, resp)) return
    applyCorsHeaders(req, resp)

    val user = authenticateRequest(req, resp) ?: return
    if (!validateProtocolVersionHeader(req, resp)) return

    val sessionId = req.getHeader(HEADER_SESSION_ID)?.trim()?.takeIf { it.isNotEmpty() }
    if (sessionId == null) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing $HEADER_SESSION_ID header")
      return
    }
    val session = sessions[sessionId]
    if (session == null || session.isClosed()) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Unknown or expired MCP session")
      return
    }
    if (session.user != user) {
      resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Session does not belong to authenticated user")
      return
    }

    if (sessions.remove(sessionId, session)) {
      session.close()
    }
    resp.status = HttpServletResponse.SC_NO_CONTENT
  }

  override fun destroy() {
    close()
    super.destroy()
  }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    scheduler.shutdownNow()
    val snapshot = sessions.values.toList()
    sessions.clear()
    snapshot.forEach { it.close() }
    if (ownsExecutor) {
      workerExecutor.shutdownNow()
    }
  }

  private fun createSession(user: User): McpHttpSession {
    val sessionId = UUID.randomUUID().toString()
    val session = Session(sessionId)
    val tools = dynamicToolsOverride.get() ?: toolsProvider(user, session)
    val transport = ServletMcpTransport(maxBufferedEvents)
    val host = McpServerHost(
      tools = tools,
      transport = transport,
      session = session,
      user = user,
      serverName = serverName,
      serverVersion = serverVersion,
      pageSize = pageSize,
      requireInitializedNotification = requireInitializedNotification,
      listChanged = listChanged,
      executor = workerExecutor,
    ).start()

    val httpSession = McpHttpSession(sessionId, user, session, transport, host)
    sessions[sessionId] = httpSession
    log.debug("Created MCP HTTP session '{}' for user '{}'", sessionId, user.name ?: "anonymous")
    return httpSession
  }

  private fun authenticateRequest(req: HttpServletRequest, resp: HttpServletResponse): User? {
    val user = try {
      ServiceRouter.authenticate(req)
    } catch (e: Exception) {
      log.debug("MCP authentication failed: {}", e.message)
      null
    }
    if (user == null) {
      resp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Authentication required")
    }
    return user
  }

  private fun validateProtocolVersionHeader(req: HttpServletRequest, resp: HttpServletResponse): Boolean {
    val version = req.getHeader(HEADER_PROTOCOL_VERSION)?.trim()?.takeIf { it.isNotEmpty() } ?: return true
    if (version !in McpJson.SUPPORTED_PROTOCOL_VERSIONS) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unsupported $HEADER_PROTOCOL_VERSION: $version")
      return false
    }
    return true
  }

  private fun validateOriginAndHost(req: HttpServletRequest, resp: HttpServletResponse): Boolean {
    val requestHost = normalizeHost(req.serverName)
    if (allowedHosts.isNotEmpty() && requestHost !in normalizedHostSet(allowedHosts)) {
      log.warn("Rejected MCP request with disallowed Host '{}'", req.serverName)
      resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden host")
      return false
    }
    val localAddr = req.localAddr
    if (!localAddr.isNullOrBlank() && isLoopbackIp(localAddr) && !isLoopbackHost(requestHost)) {
      if (requestHost !in normalizedHostSet(allowedHosts)) {
        log.warn("Rejected potential DNS rebinding request: Host='{}' on localAddr='{}'", req.serverName, localAddr)
        resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden host for loopback listener")
        return false
      }
    }

    val origin = req.getHeader("Origin")?.trim()?.takeIf { it.isNotEmpty() } ?: return true
    if (!isOriginAllowed(origin, req)) {
      log.warn("Rejected MCP request from untrusted Origin '{}'", origin)
      resp.sendError(HttpServletResponse.SC_FORBIDDEN, "Untrusted Origin")
      return false
    }
    return true
  }

  private fun isOriginAllowed(origin: String, req: HttpServletRequest): Boolean {
    if (origin.equals("null", ignoreCase = true)) {
      return allowNullOrigin
    }
    if (origin in allowedOrigins) return true

    val uri = try {
      URI(origin)
    } catch (_: Exception) {
      return false
    }
    val scheme = uri.scheme?.lowercase() ?: return false
    if (scheme != "http" && scheme != "https") return false
    val originHost = normalizeHost(uri.host ?: return false)
    if (originHost.isEmpty()) return false

    val normalizedOrigin = buildString {
      append(scheme).append("://").append(originHost)
      val port = effectivePort(scheme, uri.port)
      if ((scheme == "http" && port != 80) || (scheme == "https" && port != 443)) {
        append(':').append(port)
      }
    }
    if (normalizedOrigin in allowedOrigins) return true

    val reqScheme = req.scheme?.lowercase() ?: "http"
    val reqHost = normalizeHost(req.serverName)
    val reqPort = effectivePort(reqScheme, req.serverPort)
    val originPort = effectivePort(scheme, uri.port)
    if (scheme == reqScheme && originHost == reqHost && originPort == reqPort) {
      return true
    }

    if (isLoopbackHost(originHost) && isLoopbackHost(reqHost)) {
      return true
    }
    return false
  }

  private fun applyCorsHeaders(req: HttpServletRequest, resp: HttpServletResponse) {
    val origin = req.getHeader("Origin")?.trim()?.takeIf { it.isNotEmpty() }
    if (origin != null && isOriginAllowed(origin, req)) {
      resp.setHeader("Access-Control-Allow-Origin", origin)
      resp.setHeader("Access-Control-Allow-Credentials", "true")
      resp.addHeader("Vary", "Origin")
    }
    resp.setHeader("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
    resp.setHeader(
      "Access-Control-Allow-Headers",
      "Content-Type, Accept, Authorization, $HEADER_SESSION_ID, $HEADER_PROTOCOL_VERSION, $HEADER_LAST_EVENT_ID",
    )
    resp.setHeader("Access-Control-Expose-Headers", "$HEADER_SESSION_ID, $HEADER_PROTOCOL_VERSION")
  }

  private fun sendKeepAlives() {
    if (closed.get()) return
    val keepAliveBytes = ": keepalive\n\n".toByteArray(StandardCharsets.UTF_8)
    sessions.values.forEach { session ->
      session.transport.broadcastRaw(keepAliveBytes)
    }
  }

  private fun sweepExpiredSessions() {
    if (closed.get() || sessionTimeoutMs <= 0) return
    val cutoff = System.currentTimeMillis() - sessionTimeoutMs
    sessions.entries.forEach { (id, session) ->
      if (session.lastAccessedAt < cutoff) {
        if (sessions.remove(id, session)) {
          log.info("Expiring idle MCP session '{}'", id)
          session.close()
        }
      }
    }
  }

  private fun readRequestBody(req: HttpServletRequest): String {
    val charset = try {
      req.characterEncoding?.let { Charset.forName(it) } ?: StandardCharsets.UTF_8
    } catch (_: Exception) {
      StandardCharsets.UTF_8
    }
    return String(req.inputStream.readAllBytes(), charset)
  }

  private fun writeJsonResponse(resp: HttpServletResponse, status: Int, node: JsonNode) {
    resp.status = status
    resp.contentType = MIME_JSON
    resp.characterEncoding = StandardCharsets.UTF_8.name()
    val bytes = McpJson.encode(node).toByteArray(StandardCharsets.UTF_8)
    resp.setContentLength(bytes.size)
    resp.outputStream.write(bytes)
    resp.outputStream.flush()
  }

  private fun sendJsonError(
    resp: HttpServletResponse,
    status: Int,
    id: JsonNode?,
    code: Int,
    message: String,
  ) {
    if (resp.isCommitted) return
    writeJsonResponse(resp, status, McpJson.error(id, code, message))
  }

  private fun tryWriteSseEvent(resp: HttpServletResponse, id: Long?, data: String): Boolean {
    return try {
      val bytes = formatSseEvent(id, data)
      resp.outputStream.write(bytes)
      resp.outputStream.flush()
      true
    } catch (e: IOException) {
      false
    }
  }

  private fun safeComplete(async: AsyncContext) {
    try {
      async.complete()
    } catch (_: Exception) {
    }
  }

  private class McpHttpSession(
    val id: String,
    val user: User,
    val session: Session,
    val transport: ServletMcpTransport,
    val host: McpServerHost,
  ) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    @Volatile
    var lastAccessedAt: Long = System.currentTimeMillis()
      private set

    fun touch() {
      lastAccessedAt = System.currentTimeMillis()
    }

    fun isClosed(): Boolean = closed.get()

    override fun close() {
      if (!closed.compareAndSet(false, true)) return
      try {
        host.close()
      } catch (e: Exception) {
        log.debug("Error closing MCP host for session '{}'", id, e)
      }
    }
  }

  private class SseSubscriber(
    private val async: AsyncContext,
  ) {
    private val closed = AtomicBoolean(false)
    private val writeLock = Any()

    fun sendRaw(bytes: ByteArray): Boolean {
      if (closed.get()) return false
      return synchronized(writeLock) {
        if (closed.get()) return@synchronized false
        try {
          val out = async.response.outputStream
          out.write(bytes)
          out.flush()
          true
        } catch (_: IOException) {
          false
        }
      }
    }

    fun close() {
      if (!closed.compareAndSet(false, true)) return
      try {
        async.complete()
      } catch (_: Exception) {
      }
    }
  }

  private data class SseEvent(val id: Long, val payload: ByteArray)

  private class BatchWaiter(
    val expectedIds: MutableSet<String>,
    val hasAnonymousErrors: Boolean,
    val future: CompletableFuture<JsonNode>,
  )

  private class ServletMcpTransport(
    private val maxBufferedEvents: Int,
  ) : McpTransport {
    @Volatile
    private var onMessage: ((JsonNode) -> Unit)? = null

    @Volatile
    private var onClose: (() -> Unit)? = null

    private val closed = AtomicBoolean(false)
    private val pendingSingle = ConcurrentHashMap<String, CompletableFuture<JsonNode>>()
    private val pendingBatches = CopyOnWriteArrayList<BatchWaiter>()
    private val syncFallback = ThreadLocal<CompletableFuture<JsonNode>?>()

    private val sseSubscribers = CopyOnWriteArrayList<SseSubscriber>()
    private val eventSeq = AtomicLong(0)
    private val historyLock = Any()
    private val eventHistory = ArrayDeque<SseEvent>()

    override fun start(onMessage: (JsonNode) -> Unit, onClose: () -> Unit) {
      this.onMessage = onMessage
      this.onClose = onClose
    }

    fun nextEventId(): Long = eventSeq.incrementAndGet()

    fun deliverInbound(message: JsonNode): CompletableFuture<JsonNode>? {
      val handler = onMessage ?: return CompletableFuture.failedFuture(IllegalStateException("Transport not started"))
      if (closed.get()) return CompletableFuture.failedFuture(IllegalStateException("Transport is closed"))

      if (message.isArray) {
        return deliverBatch(handler, message as ArrayNode)
      }
      if (!message.isObject) {
        return deliverWithSyncFallback(handler, message)
      }

      val methodNode = message.get("method")
      val rawId = message.get("id")
      val requestId = rawId?.takeUnless { it.isNull }

      if (methodNode == null && (message.has("result") || message.has("error"))) {
        handler(message)
        return null
      }

      if (methodNode == null || !methodNode.isTextual || !isValidIdNode(rawId)) {
        return deliverWithSyncFallback(handler, message)
      }

      if (requestId == null) {
        handler(message)
        if (methodNode.asText() == "notifications/cancelled") {
          handleClientCancellation(message.path("params"))
        }
        return null
      }

      val key = idKey(requestId)
      val future = CompletableFuture<JsonNode>()
      pendingSingle.put(key, future)?.cancel(false)
      syncFallback.set(future)
      try {
        handler(message)
      } finally {
        syncFallback.remove()
      }
      return future
    }

    private fun deliverBatch(handler: (JsonNode) -> Unit, batch: ArrayNode): CompletableFuture<JsonNode>? {
      if (batch.isEmpty) {
        return deliverWithSyncFallback(handler, batch)
      }
      val expectedIds = ConcurrentHashMap.newKeySet<String>()
      var hasAnonymousErrors = false
      val cancelledRequestIds = mutableListOf<JsonNode>()

      for (elem in batch) {
        if (!elem.isObject) {
          hasAnonymousErrors = true
          continue
        }
        val jsonrpcNode = elem.get("jsonrpc")
        if (jsonrpcNode != null && (!jsonrpcNode.isTextual || jsonrpcNode.asText() != McpJson.JSONRPC_VERSION)) {
          hasAnonymousErrors = true
          continue
        }
        val rawId = elem.get("id")
        if (rawId != null && !isValidIdNode(rawId)) {
          hasAnonymousErrors = true
          continue
        }
        val methodNode = elem.get("method")
        if (methodNode == null) {
          if (!elem.has("result") && !elem.has("error")) {
            hasAnonymousErrors = true
          }
          continue
        }
        if (!methodNode.isTextual) {
          hasAnonymousErrors = true
          continue
        }
        val requestId = rawId?.takeUnless { it.isNull }
        if (requestId != null) {
          expectedIds.add(idKey(requestId))
        } else if (methodNode.asText() == "notifications/cancelled") {
          cancelledRequestIds.add(elem.path("params"))
        }
      }

      if (expectedIds.isEmpty() && !hasAnonymousErrors) {
        handler(batch)
        cancelledRequestIds.forEach { handleClientCancellation(it) }
        return null
      }

      val future = CompletableFuture<JsonNode>()
      val waiter = BatchWaiter(expectedIds, hasAnonymousErrors, future)
      pendingBatches.add(waiter)
      syncFallback.set(future)
      try {
        handler(batch)
      } finally {
        syncFallback.remove()
      }
      cancelledRequestIds.forEach { handleClientCancellation(it) }
      if (future.isDone) {
        pendingBatches.remove(waiter)
      }
      return future
    }

    private fun deliverWithSyncFallback(handler: (JsonNode) -> Unit, message: JsonNode): CompletableFuture<JsonNode> {
      val future = CompletableFuture<JsonNode>()
      syncFallback.set(future)
      try {
        handler(message)
      } finally {
        syncFallback.remove()
      }
      if (!future.isDone) {
        future.complete(McpJson.error(null, MCPException.INVALID_REQUEST, "No response produced"))
      }
      return future
    }

    private fun handleClientCancellation(params: JsonNode) {
      val reqId = params.get("requestId")?.takeUnless { it.isNull } ?: return
      val key = idKey(reqId)
      pendingSingle.remove(key)?.cancel(false)
      pendingBatches.forEach { batch ->
        if (batch.expectedIds.remove(key) && batch.expectedIds.isEmpty() && !batch.hasAnonymousErrors) {
          if (pendingBatches.remove(batch)) {
            batch.future.cancel(false)
          }
        }
      }
    }

    override fun send(message: JsonNode) {
      if (closed.get()) return
      if (message.isArray) {
        val batchIds = message.mapNotNull { elem ->
          elem.get("id")?.takeUnless { it.isNull }?.let(::idKey)
        }.toSet()
        val matched = pendingBatches.firstOrNull { waiter ->
          batchIds.any { it in waiter.expectedIds }
        }
        if (matched != null && pendingBatches.remove(matched)) {
          matched.future.complete(message)
          return
        }
        val fallback = syncFallback.get()
        if (fallback != null && !fallback.isDone) {
          pendingBatches.removeIf { it.future === fallback }
          fallback.complete(message)
          return
        }
        broadcastSse(message)
        return
      }

      if (message.isObject) {
        val idNode = message.get("id")?.takeUnless { it.isNull }
        if (idNode != null && (message.has("result") || message.has("error"))) {
          val key = idKey(idNode)
          val future = pendingSingle.remove(key)
          if (future != null) {
            future.complete(message)
            return
          }
          val matchedBatch = pendingBatches.firstOrNull { key in it.expectedIds }
          if (matchedBatch != null && pendingBatches.remove(matchedBatch)) {
            matchedBatch.future.complete(message)
            return
          }
        } else if (message.has("error")) {
          val fallback = syncFallback.get()
          if (fallback != null && !fallback.isDone) {
            fallback.complete(message)
            return
          }
        }
      }

      broadcastSse(message)
    }

    fun addSubscriber(subscriber: SseSubscriber, lastEventId: Long?) {
      if (closed.get()) {
        subscriber.close()
        return
      }
      val replay: List<SseEvent> = if (lastEventId != null && maxBufferedEvents > 0) {
        synchronized(historyLock) {
          eventHistory.filter { it.id > lastEventId }
        }
      } else {
        emptyList()
      }
      sseSubscribers.add(subscriber)
      for (event in replay) {
        if (!subscriber.sendRaw(event.payload)) {
          sseSubscribers.remove(subscriber)
          subscriber.close()
          return
        }
      }
    }

    fun removeSubscriber(subscriber: SseSubscriber) {
      sseSubscribers.remove(subscriber)
    }

    fun broadcastSse(message: JsonNode) {
      if (closed.get()) return
      val id = nextEventId()
      val bytes = formatSseEvent(id, McpJson.encode(message))
      if (maxBufferedEvents > 0) {
        synchronized(historyLock) {
          eventHistory.addLast(SseEvent(id, bytes))
          while (eventHistory.size > maxBufferedEvents) {
            eventHistory.removeFirst()
          }
        }
      }
      broadcastRaw(bytes)
    }

    fun broadcastRaw(bytes: ByteArray) {
      if (closed.get() || sseSubscribers.isEmpty()) return
      for (subscriber in sseSubscribers) {
        if (!subscriber.sendRaw(bytes)) {
          sseSubscribers.remove(subscriber)
          subscriber.close()
        }
      }
    }

    override fun close() {
      if (!closed.compareAndSet(false, true)) return
      onClose?.invoke()
      pendingSingle.values.forEach { it.cancel(false) }
      pendingSingle.clear()
      pendingBatches.forEach { it.future.cancel(false) }
      pendingBatches.clear()
      val subs = sseSubscribers.toList()
      sseSubscribers.clear()
      subs.forEach { it.close() }
      synchronized(historyLock) {
        eventHistory.clear()
      }
    }
  }

  private data class AcceptPreferences(
    val allowsJson: Boolean,
    val allowsSse: Boolean,
    val preferSse: Boolean,
  )

  companion object {
    private val log = LoggerFactory.getLogger(McpServerServlet::class.java)

    const val HEADER_SESSION_ID = "Mcp-Session-Id"
    const val HEADER_PROTOCOL_VERSION = "Mcp-Protocol-Version"
    const val HEADER_LAST_EVENT_ID = "Last-Event-ID"

    private const val MIME_JSON = "application/json"
    private const val MIME_SSE = "text/event-stream"

    private const val JSONRPC_PARSE_ERROR = -32700
    private const val REQUEST_CANCELLED_ERROR = -32000

    private val LOOPBACK_HOSTS = setOf(
      "localhost",
      "127.0.0.1",
      "::1",
      "0:0:0:0:0:0:0:1",
    )

    private fun parseAcceptPreferences(acceptHeader: String?): AcceptPreferences {
      if (acceptHeader.isNullOrBlank()) {
        return AcceptPreferences(allowsJson = true, allowsSse = false, preferSse = false)
      }
      var jsonQ = -1.0
      var sseQ = -1.0
      var wildcardQ = -1.0

      acceptHeader.split(',').forEach { rawRange ->
        val parts = rawRange.split(';').map { it.trim() }
        val mediaType = parts.firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return@forEach
        var q = 1.0
        for (i in 1 until parts.size) {
          val param = parts[i]
          if (param.startsWith("q=", ignoreCase = true)) {
            q = param.substring(2).trim().toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: 1.0
          }
        }
        when (mediaType) {
          MIME_JSON -> jsonQ = maxOf(jsonQ, q)
          MIME_SSE -> sseQ = maxOf(sseQ, q)
          "application/*" -> jsonQ = maxOf(jsonQ, q * 0.9)
          "text/*" -> sseQ = maxOf(sseQ, q * 0.9)
          "*/*" -> wildcardQ = maxOf(wildcardQ, q)
        }
      }

      val effectiveJsonQ = when {
        jsonQ >= 0.0 -> jsonQ
        wildcardQ >= 0.0 -> wildcardQ
        else -> 0.0
      }
      val effectiveSseQ = when {
        sseQ >= 0.0 -> sseQ
        wildcardQ >= 0.0 -> wildcardQ
        else -> 0.0
      }
      val allowsJson = effectiveJsonQ > 0.0
      val allowsSse = effectiveSseQ > 0.0
      val preferSse = sseQ > 0.0 && (jsonQ <= 0.0 || sseQ > jsonQ)
      return AcceptPreferences(allowsJson = allowsJson, allowsSse = allowsSse, preferSse = preferSse)
    }

    private fun containsInitializeRequest(payload: JsonNode): Boolean {
      if (payload.isObject) {
        return payload.get("method")?.takeIf { it.isTextual }?.asText() == "initialize" &&
          payload.get("id")?.takeUnless { it.isNull } != null
      }
      if (payload.isArray) {
        return payload.any { elem ->
          elem.isObject &&
            elem.get("method")?.takeIf { it.isTextual }?.asText() == "initialize" &&
            elem.get("id")?.takeUnless { it.isNull } != null
        }
      }
      return false
    }

    private fun extractSingleRequestId(payload: JsonNode): JsonNode? {
      if (!payload.isObject) return null
      return payload.get("id")?.takeIf { it.isTextual || it.isNumber }
    }

    private fun isValidIdNode(id: JsonNode?): Boolean =
      id == null || id.isTextual || id.isNumber || id.isNull

    private fun idKey(id: JsonNode): String = when {
      id.isTextual -> "s:${id.asText()}"
      id.isNumber -> "n:${id.decimalValue().stripTrailingZeros().toPlainString()}"
      else -> "j:${McpJson.encode(id)}"
    }

    private fun formatSseEvent(id: Long?, data: String, event: String = "message"): ByteArray {
      val sb = StringBuilder()
      if (id != null) sb.append("id: ").append(id).append('\n')
      sb.append("event: ").append(event).append('\n')
      data.lineSequence().forEach { line ->
        sb.append("data: ").append(line).append('\n')
      }
      sb.append('\n')
      return sb.toString().toByteArray(StandardCharsets.UTF_8)
    }

    private fun normalizeHost(host: String?): String =
      host?.trim()?.removeSurrounding("[", "]")?.substringBefore('%')?.lowercase() ?: ""

    private fun normalizedHostSet(hosts: Set<String>): Set<String> =
      hosts.mapTo(HashSet(hosts.size)) { normalizeHost(it) }

    private fun isLoopbackHost(normalizedHost: String): Boolean {
      if (normalizedHost in LOOPBACK_HOSTS) return true
      return isLoopbackIp(normalizedHost)
    }

    private fun isLoopbackIp(hostOrIp: String): Boolean {
      val clean = normalizeHost(hostOrIp)
      if (clean.isEmpty()) return false
      return try {
        InetAddress.getByName(clean).isLoopbackAddress
      } catch (_: Exception) {
        false
      }
    }

    private fun effectivePort(scheme: String, port: Int): Int = when {
      port > 0 -> port
      scheme == "https" -> 443
      else -> 80
    }
  }
}