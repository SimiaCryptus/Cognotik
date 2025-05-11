package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.platform.ApplicationServices.authenticationManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthenticationInterface
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.servlet.NewSessionServlet
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletHolder
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.websocket.server.JettyServerUpgradeRequest
import org.eclipse.jetty.websocket.server.JettyWebSocketServlet
import org.eclipse.jetty.websocket.server.JettyWebSocketServletFactory
import java.util.concurrent.ConcurrentHashMap
import java.time.Duration

abstract class ChatServer(private val resourceBase: String = "application") {

    abstract val applicationName: String
    open val dataStorage: StorageInterface? = null
    val sessions: ConcurrentHashMap<Session, SocketManager> = ConcurrentHashMap()

    inner class WebSocketHandler : JettyWebSocketServlet() {
        override fun configure(factory: JettyWebSocketServletFactory) {
            with(factory) {
                isAutoFragment = false
                idleTimeout = Duration.ofMinutes(10)
                outputBufferSize = 1024 * 1024
                inputBufferSize = 1024 * 1024
                maxBinaryMessageSize = 1024 * 1024
                maxFrameSize = 1024 * 1024
                maxTextMessageSize = 1024 * 1024
                this.availableExtensionNames.remove("permessage-deflate")
            }
            trafficLog.debug("Configuring WebSocket factory with settings: autoFragment=false, idleTimeout=10min, bufferSizes=1MB")
            factory.setCreator { req, resp ->
                try {
                    if (req.parameterMap.containsKey("sessionId")) {
                        val session = Session(req.parameterMap["sessionId"]?.first()!!)
                        trafficLog.debug("WebSocket connection request for session: ${session.sessionId}")
                        val sessionManager = sessions.computeIfAbsent(session) { s ->
                                val user =
                                    authenticationManager.getUser(req.getCookie(AuthenticationInterface.AUTH_COOKIE))
                                trafficLog.debug("Creating new session manager for session: ${s.sessionId}, user: ${user?.name ?: "anonymous"}")
                                newSession(user, s)
                        }
                        ChatSocket(sessionManager)
                    } else {
                        trafficLog.warn("WebSocket connection rejected: missing sessionId parameter")
                        throw IllegalArgumentException("sessionId is required")
                    }
                } catch (e: Exception) {
                    log.debug("Error configuring websocket", e)
                    trafficLog.error("WebSocket configuration error: ${e.message}", e)
                    resp.sendError(500, e.message)
                    null
                }
            }
        }
    }

    abstract fun newSession(user: User?, session: Session): SocketManager

    open val baseResource: Resource? get() = javaClass.classLoader.getResource(resourceBase)?.let {
        Resource.newResource(it).apply {
            if (!exists()) {
                val message = "Resource not found: $it"
                trafficLog.error("Base resource not found: $it")
                throw RuntimeException(message)
            }
        }
    }
    private val newSessionServlet by lazy { NewSessionServlet() }
    private val webSocketHandler by lazy { WebSocketHandler() }
    private val defaultServlet by lazy { DefaultServlet() }

    open fun configure(webAppContext: WebAppContext) {
        trafficLog.info("Configuring web app context for ${javaClass.simpleName}")
        webAppContext.addServlet(ServletHolder(javaClass.simpleName + "/default", defaultServlet), "/")
        webAppContext.addServlet(ServletHolder(javaClass.simpleName + "/ws", webSocketHandler), "/ws")
        webAppContext.addServlet(ServletHolder(javaClass.simpleName + "/newSession", newSessionServlet), "/newSession")
        trafficLog.debug("Servlets registered: default(/), ws(/ws), newSession(/newSession)")
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ChatServer::class.java)
        private val trafficLog = org.slf4j.LoggerFactory.getLogger("TRAFFIC.com.simiacryptus.cognotik.webui.chat")
        fun JettyServerUpgradeRequest.getCookie(name: String) = cookies?.find { it.name == name }?.value
    }
}