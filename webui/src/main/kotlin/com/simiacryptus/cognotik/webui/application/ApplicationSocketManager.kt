package com.simiacryptus.cognotik.webui.application

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.chat.ChatSocket
import com.simiacryptus.cognotik.webui.session.SocketManagerBase
import com.simiacryptus.jopenai.API

abstract class ApplicationSocketManager(
    session: Session,
    owner: User?,
    dataStorage: StorageInterface?,
    applicationClass: Class<*>,
) : SocketManagerBase(
    session = session,
    dataStorage = dataStorage,
    owner = owner,
    applicationClass = applicationClass,
) {
    override fun onRun(userMessage: String, socket: ChatSocket) {
        userMessage(
            session = session,
            user = socket.user,
            userMessage = userMessage,
            socketManager = this,
            api = ApplicationServices.clientManager.getChatClient(
                session,
                socket.user
            )
        )
    }

    open val applicationInterface by lazy { ApplicationInterface(this) }

    abstract fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        socketManager: ApplicationSocketManager,
        api: API
    )

    companion object {



    }
}