package com.simiacryptus.cognotik.cli

import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
import org.eclipse.jetty.server.Server
import java.net.ServerSocket

/**
 * A short-lived, in-process web server used *only* to monitor DocOps task sessions.
 *
 * This is deliberately **not** a daemon:
 *  - it is started lazily (see [start]) and only when there is actual work to execute,
 *  - it is bound to the lifetime of a single CLI invocation,
 *  - [close] is invoked from a `finally` block and from a shutdown hook.
 *
 * Task sessions are registered by `UnifiedHarness` into the process-wide
 * `SessionProxyServer` maps, so any server running in this JVM can render them at
 * `http://host:port/#<sessionId>`.
 */
class EphemeralMonitorServer(
  private val host: String = "localhost",
  requestedPort: Int? = null,
) : AutoCloseable {

  /** Port the monitor is (or will be) bound to. Chosen up-front so links are stable. */
  val port: Int = requestedPort ?: findFreePort()

  private var appServer: CognotikAppServer? = null
  private var jetty: Any? = null

  val isRunning: Boolean get() = jetty != null

  val baseUrl: String get() = "http://$host:$port/"

  fun monitorUrl(session: Session): String = "http://$host:$port/#$session"

  fun monitorUrl(sessionId: String): String = "http://$host:$port/#$sessionId"

  /** Idempotent; returns the monitor base URL. */
  @Synchronized
  fun start(): String {
    if (jetty == null) {
      val server = CognotikAppServer(localName = host, port = port)
      jetty = server.start()
      appServer = server
    }
    return baseUrl
  }

  @Synchronized
  override fun close() {
    val current = jetty
    jetty = null
    appServer = null
    if (current is Server) {
      try {
        current.stop()
      } catch (e: Exception) {
        System.err.println("warning: error stopping monitor server: ${e.message}")
      }
    }
  }

  companion object {
    fun findFreePort(): Int = ServerSocket(0).use { it.localPort }
  }
}