package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.session.SocketManager

class SessionProxyServer(appname: String = "Cognotik", path: String = "/") : ApplicationServer(
  applicationName = appname,
  path = path,
  showMenubar = false,
) {
  override val inputCnt = 0
  override val stickyInput = false
  override fun appInfo(session: Session, user: User): Map<String, Any> {
    val chat = chats[session]
    val appInfoData = appInfoMap[session]
    return (appInfoData ?: chat?.let { chatServer ->
      AppInfoData(
        applicationName = chatServer.applicationName,
        inputCnt = chatServer.inputCnt,
        stickyInput = chatServer.stickyInput,
        loadImages = false,
        showMenubar = showMenubar,
      )
    } ?: AppInfoData(
      applicationName = applicationName,
      inputCnt = 0,
      stickyInput = false,
      loadImages = false,
      showMenubar = showMenubar,
    )).toMap()
  }

  override fun newSession(user: User, session: Session) =
    agents[session] ?: chats[session]?.newSession(user, session)
    ?: throw IllegalStateException("No agent found for session $session")

  companion object {
    fun setParentSession(child: Session, parent: Session) {
      ApplicationServices.fileApplicationServices().usageManager.setParentSession(child, parent)
    }

    val metadataStorage by lazy { ApplicationServices.fileApplicationServices().metadataStorageFactory }
    val agents = mutableMapOf<Session, SocketManager>()
    val chats = mutableMapOf<Session, ChatServer>()
  }
}
