package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.servlet.CorsFilter
import jakarta.servlet.DispatcherType
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.servlet.FilterHolder
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import java.util.*

class CognotikAppServer(
    val localName: String,
    val port: Int = 8080,
    val endpoint : String = "127.0.0.1",
) {
    val server by lazy {
        try {
            log.info("Initializing server on $localName:$port")
            val server = Server(InetSocketAddress(localName, port))
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
            log.error("Failed to initialize server on $localName:$port", e)
            throw e
        }
    }

    var context: WebAppContext? = null
        private set

    private fun newWebAppContext(server: ChatServer, path: String): WebAppContext {
        return try {
            log.debug("Creating new WebAppContext for path: $path")
            require(this.context == null) { "WebAppContext has already been initialized" }
            val context = WebAppContext()
            this.context = context
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

    fun start(): Server {
        try {
            log.info("Starting CognotikAppServer on $localName:$port")
            server.start()
            if (server.isStarted) {
                log.info("CognotikAppServer successfully started on $localName:$port")
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
                    val endpoint = endpoint
                    val port = port

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