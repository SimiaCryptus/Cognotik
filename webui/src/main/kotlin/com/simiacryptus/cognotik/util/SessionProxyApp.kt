package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig.dataStorageRoot
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.session.SocketManager

class SessionProxyServer : ApplicationServer(
    applicationName = "AI Coding Assistant",
    path = "/",
    showMenubar = false,
) {
    override val inputCnt = 0
    override val stickyInput = false
    override fun appInfo(session: Session): Map<String, Any> = ((chats[session]?.let { chatServer ->
        AppInfoData(
            applicationName = chatServer.applicationName,
            inputCnt = chatServer.inputCnt,
            stickyInput = chatServer.stickyInput,
            loadImages = false,
            showMenubar = showMenubar,
        )
    }) ?: appInfos[session] ?: AppInfoData(
        applicationName = "AI Coding Assistant",
        inputCnt = 0,
        stickyInput = false,
        loadImages = false,
        showMenubar = showMenubar,
    )).toMap()

    override fun newSession(user: User?, session: Session) =
        chats[session]?.newSession(user, session) ?: agents[session]
        ?: throw IllegalStateException("No agent found for session $session")

    companion object {
        val metadataStorage by lazy { ApplicationServices.metadataStorageFactory(dataStorageRoot.resolve("metadatadb")) }
        val agents = mutableMapOf<Session, SocketManager>()
        val chats = mutableMapOf<Session, ChatServer>()
        val appInfos = mutableMapOf<Session, AppInfoData>()
    }
}
