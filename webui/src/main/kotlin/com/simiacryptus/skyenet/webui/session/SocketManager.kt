package com.simiacryptus.skyenet.webui.session

import com.simiacryptus.skyenet.webui.chat.ChatSocket
import org.eclipse.jetty.websocket.api.Session

interface SocketManager {
  fun removeSocket(socket: ChatSocket)
  fun addSocket(socket: ChatSocket, session: Session)
  fun getReplay(since: Long = 0): List<String>
  fun onWebSocketText(socket: ChatSocket, message: String)
}