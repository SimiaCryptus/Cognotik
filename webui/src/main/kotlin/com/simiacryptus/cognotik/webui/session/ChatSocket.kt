package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.webui.session.SocketManager
import org.eclipse.jetty.websocket.api.Session
import org.eclipse.jetty.websocket.api.WebSocketAdapter
import org.slf4j.LoggerFactory

class ChatSocket(
  private val sessionState: SocketManager,
) : WebSocketAdapter() {

  val user get() = SocketManager.getUser(session)

  override fun onWebSocketConnect(session: Session) {
    super.onWebSocketConnect(session)
    try {
      trafficLog.info("WebSocket connected: ${session.remoteAddress}, user: ${SocketManager.getUser(session)?.name ?: "anonymous"}")
      sessionState.addSocket(this, session)
      trafficLog.debug("Socket added to session manager, active connections: ${sessionState.getActiveSockets().size}")

      val firstOrNull = session.upgradeRequest.parameterMap["lastMessageTime"]?.firstOrNull()
      val lastMessageTime =
        when (firstOrNull) {
          "-Infinity" -> Long.MIN_VALUE
          "Infinity" -> Long.MAX_VALUE
          "null" -> 0L
          "" -> 0L
          null -> 0L
          else -> firstOrNull.toLongOrNull()
        } ?: 0L
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
    } catch (e: Exception) {
      log.warn("Error during WebSocket connection setup", e)
      trafficLog.error("WebSocket connection error from ${session.remoteAddress}: ${e.message}", e)
      session.close(1011, "WebSocket connection error: ${e.message}")
    }
  }

  override fun onWebSocketError(cause: Throwable?) {
    log.warn("WebSocket error", cause)
    super.onWebSocketError(cause)
  }

  override fun onWebSocketText(message: String) {
    super.onWebSocketText(message)
    trafficLog.debug(
      "Received message from {}: {}{}",
      session.remoteAddress,
      message.take(100),
      if (message.length > 100) "..." else ""
    )
    sessionState.onWebSocketText(this, message)
  }

  override fun onWebSocketClose(statusCode: Int, reason: String?) {
    super.onWebSocketClose(statusCode, reason)
    trafficLog.info("WebSocket closed: ${session?.remoteAddress}, statusCode: $statusCode, reason: $reason")
    sessionState.removeSocket(this)
    trafficLog.debug("Socket removed from session manager, remaining connections: ${sessionState.getActiveSockets().size}")
  }

  companion object {
    private val log = LoggerFactory.getLogger(ChatSocket::class.java)
    private val trafficLog = LoggerFactory.getLogger("TRAFFIC.com.simiacryptus.cognotik.webui.chat")
  }
}