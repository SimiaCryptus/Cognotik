package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.chat.ChatSocket
import com.simiacryptus.cognotik.webui.session.SocketManager

abstract class ApplicationSocketManager(
    session: Session,
    owner: User?,
    dataStorage: StorageInterface?,
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
            user = socket.user,
            userMessage = userMessage,
            socketManager = this
        )
    }

    abstract fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        socketManager: ApplicationSocketManager
    )

    companion object {


    }
}