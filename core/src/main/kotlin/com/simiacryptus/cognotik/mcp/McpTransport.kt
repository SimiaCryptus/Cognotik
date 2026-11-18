package com.simiacryptus.cognotik.mcp

import com.fasterxml.jackson.databind.JsonNode
import org.apache.hc.client5.http.classic.methods.HttpDelete
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.HttpHeaders
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.StringEntity
import org.apache.hc.core5.io.CloseMode
import org.apache.hc.core5.util.Timeout
import org.slf4j.LoggerFactory
import java.io.*
import java.net.URI
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * A bidirectional JSON-RPC message channel.
  * Provides stdio, in-memory, and Streamable HTTP / legacy HTTP+SSE transports.
 */
interface McpTransport : AutoCloseable {
  /**
   * Starts delivering inbound messages to [handler] (on a transport-owned thread).
   * [onClosed] is invoked once when the peer disconnects or the transport is closed.
   */
  fun start(handler: (JsonNode) -> Unit, onClosed: () -> Unit = {})

  /** Sends one message. Thread-safe. */
  fun send(message: JsonNode)

  override fun close()
}

/**
 * stdio transport: newline-delimited JSON. stdout carries protocol messages only;
 * logs must go to stderr.
 */
class StdioTransport(
  private val input: InputStream,
  output: OutputStream,
  private val name: String = "stdio",
  private val onClose: () -> Unit = {},
) : McpTransport {
  private val writer = BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8))
  private val closed = AtomicBoolean(false)
  private val closedNotified = AtomicBoolean(false)
  @Volatile private var readerThread: Thread? = null
  @Volatile private var closedCallback: () -> Unit = {}

  override fun start(handler: (JsonNode) -> Unit, onClosed: () -> Unit) {
    check(readerThread == null) { "Transport already started" }
    closedCallback = onClosed
    readerThread = Thread({
      try {
        BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
          while (!closed.get()) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue
            val message = try {
              McpJson.decode(line)
            } catch (e: Exception) {
              log.warn("[$name] Ignoring unparseable MCP message: ${line.take(200)}", e)
              continue
            }
            try {
              handler(message)
            } catch (e: Exception) {
              log.warn("[$name] MCP message handler failed", e)
            }
          }
        }
      } catch (e: IOException) {
        if (!closed.get()) log.warn("[$name] MCP stdio read failed", e)
      } finally {
        notifyClosed()
      }
    }, "mcp-stdio-$name").apply {
      isDaemon = true
      start()
    }
  }

  override fun send(message: JsonNode) {
    if (closed.get()) throw IOException("MCP transport '$name' is closed")
    val line = McpJson.encode(message)
    synchronized(writer) {
      writer.write(line)
      writer.write("\n")
      writer.flush()
    }
  }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    // Close stdin of the peer first (MCP stdio shutdown), then let the owner terminate it.
    runCatching { synchronized(writer) { writer.close() } }
    runCatching { onClose() }.onFailure { log.debug("[$name] onClose failed", it) }
    notifyClosed()
  }

  private fun notifyClosed() {
    if (closedNotified.compareAndSet(false, true)) runCatching { closedCallback() }
  }

  companion object {
    private val log = LoggerFactory.getLogger(StdioTransport::class.java)

    /** Launches an MCP server subprocess; its stderr is forwarded to the debug log. */
    fun spawn(
      command: List<String>,
      env: Map<String, String> = emptyMap(),
      workingDir: File? = null,
      name: String = command.firstOrNull() ?: "stdio",
    ): StdioTransport {
      require(command.isNotEmpty()) { "Command cannot be empty" }
      val builder = ProcessBuilder(command)
      builder.environment().putAll(env)
      workingDir?.let { builder.directory(it) }
      val process = builder.start()
      Thread({
        runCatching { process.errorStream.bufferedReader().forEachLine { log.debug("[$name stderr] $it") } }
      }, "mcp-stderr-$name").apply {
        isDaemon = true
        start()
      }
      return StdioTransport(process.inputStream, process.outputStream, name) {
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
          process.destroy()
          if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
        }
      }
    }

    /** Transport over this process's stdin/stdout, for running as an MCP server. */
    fun forCurrentProcess(name: String = "stdio-server") = StdioTransport(System.`in`, System.out, name)
  }
}

/**
 * In-process transport pair. Messages are serialised and parsed on every hop so the
 * wire format is really exercised (used for loopback tests).
 */
class InMemoryTransport private constructor(private val name: String) : McpTransport {
  private lateinit var peer: InMemoryTransport
  private val started = CountDownLatch(1)
  private val closed = AtomicBoolean(false)
  @Volatile private var handler: ((JsonNode) -> Unit)? = null
  @Volatile private var closedCallback: () -> Unit = {}
  private val inbox = Executors.newSingleThreadExecutor { r ->
    Thread(r, "mcp-inmem-$name").apply { isDaemon = true }
  }

  override fun start(handler: (JsonNode) -> Unit, onClosed: () -> Unit) {
    this.handler = handler
    this.closedCallback = onClosed
    started.countDown()
  }

  override fun send(message: JsonNode) {
    if (closed.get() || peer.closed.get()) throw IOException("MCP in-memory transport '$name' is closed")
    val wire = McpJson.encode(message)
    peer.inbox.execute {
      peer.started.await()
      try {
        peer.handler?.invoke(McpJson.decode(wire))
      } catch (e: Exception) {
        log.warn("[${peer.name}] MCP message handler failed", e)
      }
    }
  }

  override fun close() {
    if (!closed.compareAndSet(false, true)) return
    inbox.shutdownNow()
    runCatching { closedCallback() }
    if (::peer.isInitialized && !peer.closed.get()) peer.close()
  }

  companion object {
    private val log = LoggerFactory.getLogger(InMemoryTransport::class.java)

    /** Returns a connected (client, server) pair. */
    fun pair(): Pair<InMemoryTransport, InMemoryTransport> {
      val a = InMemoryTransport("client")
      val b = InMemoryTransport("server")
      a.peer = b
      b.peer = a
      return a to b
    }
  }
}
/**
  * Transport mode selection for [StreamableHttpTransport].
  */
enum class HttpTransportMode {
   /** Try Streamable HTTP POST first; fall back to legacy HTTP+SSE on HTTP 400/404/405. */
   AUTO,
   /** Use MCP 2025-03-26 / 2025-06-18 Streamable HTTP exclusively. */
   STREAMABLE_HTTP,
   /** Use legacy 2024-11-05 HTTP+SSE (initial GET `endpoint` event + POST message endpoint) exclusively. */
   LEGACY_SSE,
}
/**
  * Remote HTTP MCP client transport built on Apache HttpClient 5.
  *
  * Supports MCP 2025-03-26 / 2025-06-18 Streamable HTTP (POST JSON-RPC requests accepting
  * `application/json` or `text/event-stream` SSE responses, `Mcp-Session-Id` tracking,
  * `MCP-Protocol-Version` propagation, optional GET SSE stream for server-initiated messages,
  * custom headers / Bearer auth, and `DELETE` on close) as well as automatic or explicit
  * fallback to legacy 2024-11-05 HTTP+SSE endpoints (`endpoint` event + message POST URI).
  */
class StreamableHttpTransport(
   private val endpointUrl: String,
   private val headers: Map<String, String> = emptyMap(),
   private val bearerToken: String? = null,
   private val enableGetStream: Boolean = true,
   private val connectTimeout: Duration = Duration.ofSeconds(30),
   private val requestTimeout: Duration = Duration.ofSeconds(120),
   private val mode: HttpTransportMode = HttpTransportMode.AUTO,
   private val name: String = endpointUrl,
   httpClient: CloseableHttpClient? = null,
) : McpTransport {
   private val endpointUri: URI = URI.create(endpointUrl).also { uri ->
     require(uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) {
       "Unsupported MCP HTTP scheme '${uri.scheme}' for '$endpointUrl'"
     }
   }
   private val ownsHttpClient = httpClient == null
   private val client: CloseableHttpClient = httpClient ?: HttpClients.custom()
     .setDefaultRequestConfig(
       RequestConfig.custom()
         .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
         .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
         .setResponseTimeout(Timeout.ofMilliseconds(requestTimeout.toMillis()))
         .build()
     )
     .build()
   @Volatile
   var sessionId: String? = null
     private set
   @Volatile
   var protocolVersion: String? = null
     private set
   @Volatile
   private var postEndpointUri: URI = endpointUri
   @Volatile
   private var resolvedMode: HttpTransportMode? = if (mode == HttpTransportMode.AUTO) null else mode
   @Volatile
   private var lastEventId: String? = null
   @Volatile
   private var handler: ((JsonNode) -> Unit)? = null
   @Volatile
   private var closedCallback: () -> Unit = {}
   private val started = AtomicBoolean(false)
   private val closed = AtomicBoolean(false)
   private val closedNotified = AtomicBoolean(false)
   private val getStreamStarted = AtomicBoolean(false)
   private val modeLock = Any()
   private val pendingInitIds = ConcurrentHashMap.newKeySet<String>()
   private val activeRequests = ConcurrentHashMap.newKeySet<HttpUriRequestBase>()
   private val endpointFuture = CompletableFuture<URI>()
   private val threadSeq = AtomicInteger(0)
   private val executor = Executors.newCachedThreadPool { r ->
     Thread(r, "mcp-http-$name-${threadSeq.incrementAndGet()}").apply { isDaemon = true }
   }
   override fun start(handler: (JsonNode) -> Unit, onClosed: () -> Unit) {
     check(started.compareAndSet(false, true)) { "Transport '$name' already started" }
     this.handler = handler
     this.closedCallback = onClosed
     if (mode == HttpTransportMode.LEGACY_SSE) {
       startLegacySseStream()
     }
   }
   override fun send(message: JsonNode) {
     if (closed.get()) throw IOException("MCP transport '$name' is closed")
     check(started.get()) { "MCP transport '$name' not started" }
     if (message.path("method").asText() == "initialize" && message.has("id")) {
       pendingInitIds.add(McpJson.encode(message.get("id")))
     }
     if (resolvedMode == null) {
       synchronized(modeLock) {
         if (resolvedMode == null) {
           sendAutoFirst(message)
           return
         }
       }
     }
     when (resolvedMode) {
       HttpTransportMode.LEGACY_SSE -> sendLegacy(message)
       else -> sendStreamable(message)
     }
   }
   private fun sendAutoFirst(message: JsonNode) {
     when (executeStreamablePost(message, allowLegacyFallback = true)) {
       is PostOutcome.Success -> {
         resolvedMode = HttpTransportMode.STREAMABLE_HTTP
         maybeStartGetStream(message)
       }
       is PostOutcome.Fallback -> {
         log.debug("[{}] Streamable HTTP POST rejected; falling back to legacy HTTP+SSE", name)
         resolvedMode = HttpTransportMode.LEGACY_SSE
         startLegacySseStream()
         sendLegacy(message)
       }
     }
   }
   private fun sendStreamable(message: JsonNode) {
     executeStreamablePost(message, allowLegacyFallback = false)
     maybeStartGetStream(message)
   }
   private fun maybeStartGetStream(message: JsonNode) {
     if (enableGetStream && message.path("method").asText() != "initialize") {
       ensureGetStreamStarted()
     }
   }
   private sealed interface PostOutcome {
     data object Success : PostOutcome
     data class Fallback(val statusCode: Int, val detail: String) : PostOutcome
   }
   private fun executeStreamablePost(message: JsonNode, allowLegacyFallback: Boolean): PostOutcome {
     val post = HttpPost(endpointUri).apply {
       applyCommonHeaders(this, includeSession = true, includeProtocolVersion = true)
       setHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream")
       entity = StringEntity(McpJson.encode(message), ContentType.APPLICATION_JSON)
       config = RequestConfig.custom()
         .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
         .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
         .setResponseTimeout(Timeout.ofMilliseconds(requestTimeout.toMillis()))
         .build()
     }
     val ready = CompletableFuture<PostOutcome>()
     executor.execute {
       activeRequests.add(post)
       try {
         client.execute(post) { response ->
           val status = response.code
           response.getFirstHeader(HEADER_SESSION_ID)?.value?.takeIf { it.isNotBlank() }?.let {
             sessionId = it
           }
           if (status == 404 && sessionId != null && !allowLegacyFallback) {
             sessionId = null
             val body = readBodyQuietly(response)
             throw IOException("MCP session expired or not found (HTTP 404)${bodySuffix(body)}")
           }
           val contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE)?.value
             ?.substringBefore(';')?.trim()?.lowercase()
           if (status in 200..299) {
             if (status == 202 || status == 204 || response.entity == null) {
               EntityUtils.consumeQuietly(response.entity)
               ready.complete(PostOutcome.Success)
             } else if (contentType == "text/event-stream") {
               ready.complete(PostOutcome.Success)
               readSseStream(response.entity.content, isLegacyStream = false)
             } else {
               val body = EntityUtils.toString(response.entity, Charsets.UTF_8)?.trim().orEmpty()
               if (body.isNotEmpty()) {
                 dispatchJsonString(body)
               }
               ready.complete(PostOutcome.Success)
             }
           } else {
             val body = readBodyQuietly(response)
             if (contentType == "application/json" && body.isNotEmpty() && tryDispatchJsonRpcError(body)) {
               ready.complete(PostOutcome.Success)
             } else if (allowLegacyFallback && status in LEGACY_FALLBACK_STATUSES) {
               ready.complete(PostOutcome.Fallback(status, body.take(200)))
             } else {
               throw IOException(
                 "MCP HTTP POST to '$endpointUri' failed with HTTP $status ${response.reasonPhrase}${bodySuffix(body)}"
               )
             }
           }
         }
       } catch (e: Throwable) {
         if (!ready.isDone) {
           ready.completeExceptionally(e)
         } else if (!closed.get()) {
           log.debug("[{}] POST SSE stream terminated: {}", name, e.message)
         }
       } finally {
         activeRequests.remove(post)
       }
     }
     val waitMillis = connectTimeout.toMillis() + requestTimeout.toMillis() + 5_000L
     return try {
       ready.get(waitMillis, TimeUnit.MILLISECONDS)
     } catch (e: TimeoutException) {
       runCatching { post.cancel() }
       throw IOException("Timed out waiting for HTTP response from '$endpointUri'")
     } catch (e: CancellationException) {
       throw IOException("MCP transport '$name' is closed", e)
     } catch (e: ExecutionException) {
       throw (e.cause as? IOException)
         ?: IOException("MCP HTTP POST to '$endpointUri' failed: ${e.cause?.message}", e.cause)
     } catch (e: InterruptedException) {
       Thread.currentThread().interrupt()
       throw IOException("Interrupted waiting for HTTP response from '$endpointUri'", e)
     }
   }
   private fun ensureGetStreamStarted() {
     if (closed.get() || !enableGetStream) return
     if (!getStreamStarted.compareAndSet(false, true)) return
     executor.execute {
       runStreamableGetLoop()
     }
   }
   private fun runStreamableGetLoop() {
     while (!closed.get()) {
       val get = HttpGet(endpointUri).apply {
         applyCommonHeaders(this, includeSession = true, includeProtocolVersion = true)
         setHeader(HttpHeaders.ACCEPT, "text/event-stream")
         lastEventId?.let { setHeader(HEADER_LAST_EVENT_ID, it) }
         config = RequestConfig.custom()
           .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
           .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
           .setResponseTimeout(Timeout.DISABLED)
           .build()
       }
       var shouldReconnect = false
       activeRequests.add(get)
       try {
         client.execute(get) { response ->
           val status = response.code
           response.getFirstHeader(HEADER_SESSION_ID)?.value?.takeIf { it.isNotBlank() }?.let {
             sessionId = it
           }
           val contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE)?.value
             ?.substringBefore(';')?.trim()?.lowercase()
           when {
             status == 405 || status == 501 -> {
               EntityUtils.consumeQuietly(response.entity)
               log.debug("[{}] Server does not support GET SSE stream (HTTP {})", name, status)
             }
             status == 404 -> {
               EntityUtils.consumeQuietly(response.entity)
               if (sessionId != null) {
                 log.warn("[{}] MCP session '{}' expired (GET returned 404)", name, sessionId)
                 sessionId = null
               }
             }
             status in 200..299 && contentType == "text/event-stream" && response.entity != null -> {
               val prevEventId = lastEventId
               readSseStream(response.entity.content, isLegacyStream = false)
               shouldReconnect = !closed.get() && lastEventId != null && lastEventId != prevEventId
             }
             else -> {
               EntityUtils.consumeQuietly(response.entity)
               log.debug("[{}] GET SSE stream ended with HTTP {} ({})", name, status, contentType)
             }
           }
         }
       } catch (e: Exception) {
         if (!closed.get()) {
           log.debug("[{}] GET SSE stream closed: {}", name, e.message)
         }
       } finally {
         activeRequests.remove(get)
       }
       if (!shouldReconnect || closed.get()) break
       try {
         Thread.sleep(250)
       } catch (_: InterruptedException) {
         Thread.currentThread().interrupt()
         break
       }
     }
   }
   private fun startLegacySseStream() {
     if (!getStreamStarted.compareAndSet(false, true)) return
     executor.execute {
       val get = HttpGet(endpointUri).apply {
         applyCommonHeaders(this, includeSession = false, includeProtocolVersion = false)
         setHeader(HttpHeaders.ACCEPT, "text/event-stream")
         config = RequestConfig.custom()
           .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
           .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
           .setResponseTimeout(Timeout.DISABLED)
           .build()
       }
       activeRequests.add(get)
       try {
         client.execute(get) { response ->
           val status = response.code
           val contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE)?.value
             ?.substringBefore(';')?.trim()?.lowercase()
           if (status !in 200..299 || contentType != "text/event-stream" || response.entity == null) {
             val body = readBodyQuietly(response)
             throw IOException(
               "Legacy SSE GET '$endpointUri' failed with HTTP $status ($contentType)${bodySuffix(body)}"
             )
           }
           readSseStream(response.entity.content, isLegacyStream = true)
           if (!endpointFuture.isDone) {
             endpointFuture.completeExceptionally(
               IOException("Legacy SSE stream '$endpointUri' closed before emitting endpoint event")
             )
           }
         }
       } catch (e: Throwable) {
         if (!endpointFuture.isDone) {
           endpointFuture.completeExceptionally(e)
         }
         if (!closed.get()) {
           log.warn("[$name] Legacy SSE stream closed", e)
         }
       } finally {
         activeRequests.remove(get)
         if (!closed.get()) {
           notifyClosed()
         }
       }
     }
   }
   private fun sendLegacy(message: JsonNode) {
     val targetUri = try {
       endpointFuture.get(connectTimeout.toMillis(), TimeUnit.MILLISECONDS)
     } catch (e: TimeoutException) {
       throw IOException("Timed out waiting for legacy SSE endpoint event from '$endpointUri'")
     } catch (e: CancellationException) {
       throw IOException("MCP transport '$name' is closed", e)
     } catch (e: ExecutionException) {
       throw (e.cause as? IOException)
         ?: IOException("Legacy SSE connection to '$endpointUri' failed: ${e.cause?.message}", e.cause)
     } catch (e: InterruptedException) {
       Thread.currentThread().interrupt()
       throw IOException("Interrupted waiting for legacy SSE endpoint event from '$endpointUri'", e)
     }
     val post = HttpPost(targetUri).apply {
       applyCommonHeaders(this, includeSession = false, includeProtocolVersion = false)
       setHeader(HttpHeaders.ACCEPT, "application/json, text/event-stream")
       entity = StringEntity(McpJson.encode(message), ContentType.APPLICATION_JSON)
       config = RequestConfig.custom()
         .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
         .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectTimeout.toMillis()))
         .setResponseTimeout(Timeout.ofMilliseconds(requestTimeout.toMillis()))
         .build()
     }
     activeRequests.add(post)
     try {
       client.execute(post) { response ->
         val status = response.code
         val contentType = response.getFirstHeader(HttpHeaders.CONTENT_TYPE)?.value
           ?.substringBefore(';')?.trim()?.lowercase()
         if (status in 200..299) {
           if (status != 202 && status != 204 && response.entity != null && contentType == "application/json") {
             val body = EntityUtils.toString(response.entity, Charsets.UTF_8)?.trim().orEmpty()
             if (body.isNotEmpty()) {
               dispatchJsonString(body)
             }
           } else {
             EntityUtils.consumeQuietly(response.entity)
           }
         } else {
           val body = readBodyQuietly(response)
           throw IOException(
             "Legacy MCP POST to '$targetUri' failed with HTTP $status ${response.reasonPhrase}${bodySuffix(body)}"
           )
         }
       }
     } finally {
       activeRequests.remove(post)
     }
   }
   private fun readSseStream(input: InputStream, isLegacyStream: Boolean) {
     BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
       var eventType: String? = null
       val dataBuilder = StringBuilder()
       var hasData = false
       fun dispatchEvent() {
         if (!hasData) {
           eventType = null
           return
         }
         if (dataBuilder.isNotEmpty() && dataBuilder[dataBuilder.length - 1] == '\n') {
           dataBuilder.setLength(dataBuilder.length - 1)
         }
         val type = eventType ?: "message"
         val data = dataBuilder.toString()
         eventType = null
         dataBuilder.setLength(0)
         hasData = false
         when (type) {
           "endpoint" -> if (isLegacyStream && !endpointFuture.isDone) {
             runCatching { endpointUri.resolve(data.trim()) }
               .onSuccess { resolved ->
                 postEndpointUri = resolved
                 endpointFuture.complete(resolved)
               }
               .onFailure {
                 endpointFuture.completeExceptionally(IOException("Invalid legacy SSE endpoint URI '$data'", it))
               }
           }
           "message" -> if (data.isNotBlank()) {
             dispatchJsonString(data)
           }
           else -> log.debug("[{}] Ignoring SSE event type '{}'", name, type)
         }
       }
       while (!closed.get()) {
         val line = reader.readLine() ?: break
         if (line.isEmpty()) {
           dispatchEvent()
           continue
         }
         if (line.startsWith(':')) continue
         val colonIdx = line.indexOf(':')
         val field: String
         val value: String
         if (colonIdx == -1) {
           field = line
           value = ""
         } else {
           field = line.substring(0, colonIdx)
           val raw = line.substring(colonIdx + 1)
           value = if (raw.startsWith(' ')) raw.substring(1) else raw
         }
         when (field) {
           "event" -> eventType = value
           "data" -> {
             dataBuilder.append(value).append('\n')
             hasData = true
           }
           "id" -> if (!value.contains('\u0000')) {
             lastEventId = value
           }
           "retry" -> { /* optional reconnection hint */ }
         }
       }
     }
   }
   private fun applyCommonHeaders(
     request: HttpUriRequestBase,
     includeSession: Boolean,
     includeProtocolVersion: Boolean,
   ) {
     var hasAuth = false
     headers.forEach { (k, v) ->
       request.setHeader(k, v)
       if (k.equals(HttpHeaders.AUTHORIZATION, ignoreCase = true)) hasAuth = true
     }
     if (!hasAuth && !bearerToken.isNullOrBlank()) {
       request.setHeader(HttpHeaders.AUTHORIZATION, "Bearer $bearerToken")
     }
     if (includeSession) {
       sessionId?.let { request.setHeader(HEADER_SESSION_ID, it) }
     }
     if (includeProtocolVersion) {
       protocolVersion?.let { request.setHeader(HEADER_PROTOCOL_VERSION, it) }
     }
   }
   private fun dispatchJsonString(json: String) {
     val node = try {
       McpJson.decode(json)
     } catch (e: Exception) {
       log.warn("[$name] Ignoring unparseable MCP JSON message: ${json.take(200)}", e)
       return
     }
     dispatchJson(node)
   }
   private fun dispatchJson(node: JsonNode) {
     if (node.isArray) {
       node.forEach { dispatchSingle(it) }
     } else {
       dispatchSingle(node)
     }
   }
   private fun dispatchSingle(node: JsonNode) {
     if (node.has("result") && node.has("id")) {
       val idKey = McpJson.encode(node.get("id"))
       if (pendingInitIds.remove(idKey)) {
         node.path("result").get("protocolVersion")?.takeIf { it.isTextual }?.asText()?.let {
           protocolVersion = it
         }
       }
     }
     try {
       handler?.invoke(node)
     } catch (e: Exception) {
       log.warn("[$name] MCP message handler failed", e)
     }
   }
   private fun tryDispatchJsonRpcError(json: String): Boolean {
     val node = runCatching { McpJson.decode(json) }.getOrNull() ?: return false
     val isRpc = (node.isObject && node.has("jsonrpc") && (node.has("error") || node.has("result"))) ||
       (node.isArray && node.size() > 0 && node.all { it.isObject && it.has("jsonrpc") })
     if (!isRpc) return false
     dispatchJson(node)
     return true
   }
   private fun readBodyQuietly(response: ClassicHttpResponse): String =
     response.entity?.let { entity ->
       runCatching { EntityUtils.toString(entity, Charsets.UTF_8) }.getOrNull()?.trim().orEmpty()
     }.orEmpty()
   private fun bodySuffix(body: String): String =
     if (body.isBlank()) "" else ": ${body.take(300)}"
   override fun close() {
     if (!closed.compareAndSet(false, true)) return
     val sid = sessionId
     if (sid != null && resolvedMode != HttpTransportMode.LEGACY_SSE) {
       runCatching {
         val delete = HttpDelete(endpointUri).apply {
           applyCommonHeaders(this, includeSession = true, includeProtocolVersion = true)
           config = RequestConfig.custom()
             .setConnectTimeout(Timeout.ofSeconds(3))
             .setConnectionRequestTimeout(Timeout.ofSeconds(3))
             .setResponseTimeout(Timeout.ofSeconds(3))
             .build()
         }
         client.execute(delete) { response ->
           EntityUtils.consumeQuietly(response.entity)
         }
       }.onFailure { log.debug("[{}] MCP DELETE session failed: {}", name, it.message) }
     }
     activeRequests.forEach { req -> runCatching { req.cancel() } }
     activeRequests.clear()
     endpointFuture.cancel(true)
     executor.shutdownNow()
     if (ownsHttpClient) {
       runCatching { client.close(CloseMode.IMMEDIATE) }
         .onFailure { log.debug("[{}] HttpClient close failed", name, it) }
     }
     notifyClosed()
   }
   private fun notifyClosed() {
     if (closedNotified.compareAndSet(false, true)) runCatching { closedCallback() }
   }
   companion object {
     private val log = LoggerFactory.getLogger(StreamableHttpTransport::class.java)
     private val LEGACY_FALLBACK_STATUSES = setOf(400, 404, 405)
     const val HEADER_SESSION_ID = "Mcp-Session-Id"
     const val HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version"
     const val HEADER_LAST_EVENT_ID = "Last-Event-ID"
     /** Creates a transport configured strictly for legacy 2024-11-05 HTTP+SSE servers. */
     fun legacySse(
       endpointUrl: String,
       headers: Map<String, String> = emptyMap(),
       bearerToken: String? = null,
       connectTimeout: Duration = Duration.ofSeconds(30),
       requestTimeout: Duration = Duration.ofSeconds(120),
       name: String = endpointUrl,
       httpClient: CloseableHttpClient? = null,
     ) = StreamableHttpTransport(
       endpointUrl = endpointUrl,
       headers = headers,
       bearerToken = bearerToken,
       enableGetStream = true,
       connectTimeout = connectTimeout,
       requestTimeout = requestTimeout,
       mode = HttpTransportMode.LEGACY_SSE,
       name = name,
       httpClient = httpClient,
     )
   }
}