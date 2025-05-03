package com.simiacryptus.cognotik

import cognotik.actions.SessionProxyServer
import com.intellij.openapi.project.Project
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

class AppServer(
    private val localName: String,
    private val port: Int
) {

    val server by lazy {
        val server = Server(InetSocketAddress(localName, port))
        server.handler = contexts
        server
    }

    private val handlers = arrayOf(
        newWebAppContext(SessionProxyServer(), "/")
    ).map {
        it.addFilter(FilterHolder(CorsFilter()), "/*", EnumSet.of(DispatcherType.REQUEST))
        it
    }.toMutableList()

    private val contexts by lazy {
        val contexts = ContextHandlerCollection()
        contexts.handlers = handlers.toTypedArray()
        contexts
    }

    private fun newWebAppContext(server: ChatServer, path: String): WebAppContext {
        val context = WebAppContext()
        JettyWebSocketServletContainerInitializer.configure(context, null)
        context.baseResource = server.baseResource
        context.classLoader = AppServer::class.java.classLoader
        context.contextPath = path
        context.welcomeFiles = arrayOf("index.html")
        server.configure(context)
        return context
    }

    fun start() {
        server.start()
    }

    companion object {
        @Transient
        private var server: AppServer? = null
        fun isRunning(): Boolean {
            return server?.server?.isRunning ?: false
        }

        fun getServer(project: Project?): AppServer {
            if (null == server || !server!!.server.isRunning) {
                server = AppServer(
                    AppSettingsState.instance.listeningEndpoint,
                    AppSettingsState.instance.listeningPort
                )
                server!!.start()
            }
            return server!!
        }

    }

}