package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.service.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.ServiceKey
import com.simiacryptus.cognotik.platform.ServiceMap
import com.simiacryptus.cognotik.webui.session.ChatSocket
import com.simiacryptus.cognotik.webui.session.SocketManager

abstract class ApplicationSocketManager(
  session: Session,
  owner: User,
  dataStorage: StorageInterface = ServiceMap[ServiceKey.DATA_STORAGE],
  applicationClass: Class<*>,
) : SocketManager(
  sessionId = session,
  dataStorage = dataStorage,
  owner = owner,
  applicationClass = applicationClass,
) {
  override fun onRun(userMessage: String, socket: ChatSocket) {
    userMessage(
      session = sessionId,
      user = owner,
      userMessage = userMessage,
      socketManager = this
    )
  }

  abstract fun userMessage(
    session: Session,
    user: User = owner,
    userMessage: String,
    socketManager: ApplicationSocketManager
  )
}