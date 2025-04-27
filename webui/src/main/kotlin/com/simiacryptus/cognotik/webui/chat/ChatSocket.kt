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
        sessionState.addSocket(this, session)

        val lastMessageTime =
            session.upgradeRequest.parameterMap["lastMessageTime"]?.firstOrNull()?.toLongOrNull() ?: 0L
        sessionState.getReplay(lastMessageTime).forEach {
            try {
                remote.sendString(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onWebSocketText(message: String) {
        super.onWebSocketText(message)
        sessionState.onWebSocketText(this, message)
    }

    override fun onWebSocketClose(statusCode: Int, reason: String?) {
        super.onWebSocketClose(statusCode, reason)

        sessionState.removeSocket(this)
    }

    companion object
}