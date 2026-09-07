package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.apps.SessionProxyServer
import com.simiacryptus.cognotik.webui.servlet.ClasspathAssetServlet
import com.simiacryptus.cognotik.webui.servlet.CorsFilter
import com.simiacryptus.cognotik.webui.session.ChatServer
import jakarta.servlet.DispatcherType
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.net.URI
import java.util.*

class CognotikAppServer(
  val hostInterface: String,
  val port: Int = 8080,
) {
  /**
   * Host name to advertise in URLs. Binding to a wildcard (`0.0.0.0`, `::`) or loopback
   * (`127.0.0.1`, `::1`) address is reported as `localhost` so generated links are clickable.
   */
  val publicHost: String = displayHostFor(hostInterface)

  val server by lazy {
    try {
      log.info("Initializing server on $hostInterface:$port")
      val server = object : Server(InetSocketAddress(hostInterface, port)) {
        override fun getURI(): URI? {
          val uri = super.getURI() ?: return null
          val host = uri.host ?: return uri
          val display = displayHostFor(host)
          return if (display == host) uri
          else URI(uri.scheme, uri.userInfo, display, uri.port, uri.path, uri.query, uri.fragment)
        }
      }
      server.handler = ContextHandlerCollection().apply {
        this.handlers = arrayOf(
          newWebAppContext(SessionProxyServer(), "/")
        ).map {
          try {
            it.addFilter(FilterHolder(CorsFilter()), "/*", EnumSet.of(DispatcherType.REQUEST))
            log.debug("Added CORS filter to context: ${it.contextPath}")
            it
          } catch (e: Exception) {
            log.error("Failed to add CORS filter to context", e)
            throw e
          }
        }.toMutableList().toTypedArray<WebAppContext>()
      }
      server
    } catch (e: Exception) {
      log.error("Failed to initialize server on $hostInterface:$port", e)
      throw e
    }
  }

  var context: WebAppContext? = null
    private set

  private fun newWebAppContext(server: ChatServer, vararg paths: String): WebAppContext {
    require(paths.isNotEmpty()) { "At least one path must be provided" }
    val normalizedPaths = paths.map { if (it.startsWith("/")) it else "/$it" }.distinct()
    val primaryPath = normalizedPaths.first()
    return try {
      log.debug("Creating new WebAppContext for paths: ${normalizedPaths.joinToString(", ")}")
      require(this.context == null) { "WebAppContext has already been initialized" }
      val context = WebAppContext()
      this.context = context
      JettyWebSocketServletContainerInitializer.configure(context, null)
      context.baseResource = server.baseResource
      context.classLoader = CognotikAppServer::class.java.classLoader
      context.contextPath = primaryPath
      context.welcomeFiles = arrayOf("index.html")

      if (normalizedPaths.size > 1) {
        val aliases = normalizedPaths.drop(1)
        context.setAttribute("cognotik.contextPathAliases", aliases)
        log.debug("Registered context path aliases: ${aliases.joinToString(", ")}")
      }
      server.configure(context)
      registerSharedAssets(context)
      log.info("Successfully created WebAppContext for paths: ${normalizedPaths.joinToString(", ")}")
      context
    } catch (e: Exception) {
      log.error("Failed to create WebAppContext for paths: ${normalizedPaths.joinToString(", ")}", e)
      throw e
    }
  }

  /**
   * Mounts the shared web assets read from the classpath (never from disk):
   * `web/lib` at [LIB_PREFIX] and `web/app` at [APP_PREFIX], matching
   * `FileServerCli` so the same front-end bundles work in both servers.
   */
  private fun registerSharedAssets(context: WebAppContext) {
    registerAssetServlet(context, "web-lib", "web/lib", LIB_PREFIX)
    registerAssetServlet(context, "web-app", "web/app", APP_PREFIX)
  }

  private fun registerAssetServlet(
    context: WebAppContext,
    name: String,
    resourceRoot: String,
    prefix: String
  ) {
    try {
      val holder = ServletHolder(name, ClasspathAssetServlet(resourceRoot)).apply {
        isAsyncSupported = true
        initOrder = 1
      }

      context.addServlet(holder, "$prefix/*")
      log.info("Mounted classpath assets '$resourceRoot' at $prefix/")
    } catch (e: Exception) {
      log.error("Failed to mount classpath assets '$resourceRoot' at $prefix/", e)
      throw e
    }
  }

  fun start(): Server {
    try {
      log.info("Starting CognotikAppServer on $hostInterface:$port")
      server.start()
      if (server.isStarted) {
        log.info("CognotikAppServer successfully started on $hostInterface:$port (advertised as ${server.uri})")
        log.info("Shared assets available at http://$publicHost:$port$LIB_PREFIX/ (classpath web/lib) and http://$publicHost:$port$APP_PREFIX/ (classpath web/app)")
      } else {
        log.warn("Server start() completed but server is not in started state")
      }
      return server
    } catch (e: Exception) {
      log.error("Failed to start CognotikAppServer", e)
      throw e
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(CognotikAppServer::class.java)

    /** Bind addresses that should be advertised as `localhost` in generated URLs. */
    private val LOCAL_BIND_ALIASES = setOf(
      "0.0.0.0",
      "127.0.0.1",
      "::",
      "::1",
      "0:0:0:0:0:0:0:0",
      "0:0:0:0:0:0:0:1",
    )

    /**
     * Maps a wildcard/loopback bind address to `localhost`; any other host is returned unchanged.
     */
    fun displayHostFor(host: String?): String {
      val normalized = host?.trim()?.removeSurrounding("[", "]")?.substringBefore('%') ?: ""
      return if (normalized.isEmpty() || normalized.lowercase() in LOCAL_BIND_ALIASES) "localhost" else normalized
    }

    /**
     * Shared web assets served straight from the classpath, independent of any
     * session or workspace: `web/lib` is published at [LIB_PREFIX] and
     * `web/app` at [APP_PREFIX] (same contract as `FileServerCli`).
     */
    const val LIB_PREFIX = "/lib"

    /** @see LIB_PREFIX */
    const val APP_PREFIX = "/app"


    @Transient
    private var server: CognotikAppServer? = null

    fun isRunning(): Boolean {
      val running = server?.server?.isRunning ?: false
      log.debug("Server running status: $running")
      return running
    }

    fun getServer(
      endpoint: String = "localhost",
      port: Int = 8181
    ): CognotikAppServer {
      try {
        if (null == server || !server!!.server.isRunning) {
          if (endpoint.isBlank()) throw IllegalArgumentException("Endpoint cannot be blank when starting a new server")

          if (endpoint.isBlank()) {
            log.error("Listening endpoint is blank")
            throw IllegalStateException("Listening endpoint cannot be blank")
          }

          if (port <= 0 || port > 65535) {
            log.error("Invalid port number: $port")
            throw IllegalArgumentException("Port must be between 1 and 65535, got: $port")
          }

          log.info("Creating new CognotikAppServer instance for endpoint: $endpoint:$port")
          server = CognotikAppServer(endpoint, port)
          server!!.start()
        } else {
          log.debug("Returning existing running server instance")
        }
        return server!!
      } catch (e: Exception) {
        log.error("Failed to get or create server instance", e)
        throw e
      }
    }
  }

}