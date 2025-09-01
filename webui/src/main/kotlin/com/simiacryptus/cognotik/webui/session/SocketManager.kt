package com.simiacryptus.cognotik.webui.session

import com.simiacryptus.cognotik.webui.chat.ChatSocket
import org.eclipse.jetty.websocket.api.Session

interface SocketManager {
    fun removeSocket(socket: ChatSocket)
    fun addSocket(socket: ChatSocket, session: Session)
    fun getReplay(since: Long): List<String>
    fun onWebSocketText(socket: ChatSocket, message: String)
    fun getActiveSockets(): List<ChatSocket>
    fun newTask(cancelable: Boolean = false, root: Boolean = true): SessionTask
}