package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.webui.session.SocketManager
import com.simiacryptus.cognotik.webui.session.SocketManagerBase
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter

class ChatSocket(
    private val sessionState: SocketManager,
) : WebSocketAdapter() {

    val user get() = SocketManagerBase.getUser(session)

    override fun onWebSocketConnect(session: Session) {
        super.onWebSocketConnect(session)
        trafficLog.info("WebSocket connected: ${session.remoteAddress}, user: ${SocketManagerBase.getUser(session)?.name ?: "anonymous"}")
        sessionState.addSocket(this, session)
        trafficLog.debug("Socket added to session manager, active connections: ${sessionState.getActiveSockets().size}")

        val lastMessageTime =
            session.upgradeRequest.parameterMap["lastMessageTime"]?.firstOrNull()?.toLongOrNull() ?: 0L
        trafficLog.debug("Replaying messages since: $lastMessageTime")
        sessionState.getReplay(lastMessageTime).forEach {
            try {
                trafficLog.trace("Replaying message: ${it.take(100)}${if (it.length > 100) "..." else ""}")
                remote.sendString(it)
            } catch (e: Exception) {
                log.warn("Error replaying message to ${session.remoteAddress}", e)
                trafficLog.error("Failed to replay message to ${session.remoteAddress}: ${e.message}")
            }
        }
    }

    override fun onWebSocketText(message: String) {
        super.onWebSocketText(message)
        trafficLog.debug("Received message from ${session.remoteAddress}: ${message.take(100)}${if (message.length > 100) "..." else ""}")
        sessionState.onWebSocketText(this, message)
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        super.onWebSocketClose(statusCode, reason)
        trafficLog.info("WebSocket closed: ${session?.remoteAddress}, statusCode: $statusCode, reason: $reason")
        sessionState.removeSocket(this)
        trafficLog.debug("Socket removed from session manager, remaining connections: ${sessionState.getActiveSockets().size}")
    }

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(ChatSocket::class.java)
        private val trafficLog = org.slf4j.LoggerFactory.getLogger("TRAFFIC.com.simiacryptus.cognotik.webui.chat")
    }
}