package com.simiacryptus.cognotik

import com.simiacryptus.cognotik.config.AppSettingsState
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.CognotikAppServer
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
import kotlin.apply
import kotlin.collections.map
import kotlin.collections.toMutableList
import kotlin.collections.toTypedArray
import kotlin.compareTo
import kotlin.jvm.java
import kotlin.text.isBlank

object CognotikAppServer {
    private val log = LoggerFactory.getLogger(CognotikAppServer::class.java)

    @Transient
    private var server: CognotikAppServer? = null

    fun isRunning(): Boolean {
        val running = server?.server?.isRunning ?: false
        log.debug("Server running status: $running")
        return running
    }

    fun getServer(): com.simiacryptus.cognotik.webui.application.CognotikAppServer {
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
