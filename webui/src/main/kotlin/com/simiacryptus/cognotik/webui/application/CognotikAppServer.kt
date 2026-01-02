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
import java.util.EnumSet

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

    }

}