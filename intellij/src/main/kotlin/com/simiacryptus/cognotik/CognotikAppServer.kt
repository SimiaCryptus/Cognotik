package com.simiacryptus.cognotik

 import com.simiacryptus.cognotik.util.SessionProxyServer
 import com.intellij.openapi.project.Project
import com.intellij.openapi.diagnostic.Logger
 import com.simiacryptus.cognotik.config.AppSettingsState
 import com.simiacryptus.cognotik.webui.chat.ChatServer
 import com.simiacryptus.cognotik.webui.servlet.CorsFilter
 import jakarta.servlet.DispatcherType
 import org.eclipse.jetty.server.Server
 import org.eclipse.jetty.server.handler.ContextHandlerCollection
 import org.eclipse.jetty.servlet.FilterHolder
 import org.eclipse.jetty.webapp.WebAppContext
 import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
 import java.net.InetSocketAddress
 import java.util.*

 class CognotikAppServer(
    private val localName: String,
    private val port: Int
) {

    val server by lazy {
        try {
            log.info("Initializing server on $localName:$port")
            val server = Server(InetSocketAddress(localName, port))
            server.handler = contexts
            server
        } catch (e: Exception) {
            log.error("Failed to initialize server on $localName:$port", e)
            throw e
        }
    }

    private val handlers = arrayOf(
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
    }.toMutableList()

    private val contexts by lazy {
        val contexts = ContextHandlerCollection()
        contexts.handlers = handlers.toTypedArray()
        log.debug("Created context handler collection with ${handlers.size} handlers")
        contexts
    }

    private fun newWebAppContext(server: ChatServer, path: String): WebAppContext {
        return try {
            log.debug("Creating new WebAppContext for path: $path")
            val context = WebAppContext()
            JettyWebSocketServletContainerInitializer.configure(context, null)
            context.baseResource = server.baseResource
            context.classLoader = CognotikAppServer::class.java.classLoader
            context.contextPath = path
            context.welcomeFiles = arrayOf("index.html")
            server.configure(context)
            log.info("Successfully created WebAppContext for path: $path")
            context
        } catch (e: Exception) {
            log.error("Failed to create WebAppContext for path: $path", e)
            throw e
        }
    }

    fun start() {
        try {
            log.info("Starting CognotikAppServer on $localName:$port")
            server.start()
            if (server.isStarted) {
                log.info("CognotikAppServer successfully started on $localName:$port")
            } else {
                log.warn("Server start() completed but server is not in started state")
            }
        } catch (e: Exception) {
            log.error("Failed to start CognotikAppServer", e)
            throw e
        }
    }

    companion object {
        private val log = Logger.getInstance(CognotikAppServer::class.java)
        
        @Transient
        private var server: CognotikAppServer? = null
        
        fun isRunning(): Boolean {
            val running = server?.server?.isRunning ?: false
            log.debug("Server running status: $running")
            return running
        }

        fun getServer(project: Project?): CognotikAppServer {
            try {
                if (null == server || !server!!.server.isRunning) {
                    val endpoint = AppSettingsState.instance.listeningEndpoint
                    val port = AppSettingsState.instance.listeningPort
                    
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