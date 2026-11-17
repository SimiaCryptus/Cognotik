package com.simiacryptus.cognotik.mcp

import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A bidirectional JSON-RPC message channel.
 * Streamable HTTP is a planned follow-up (mcp_feature_status.md F-3).
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