package cognotik.actions

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
    override val singleInput = true
    override val stickyInput = false
    override fun appInfo(session: Session) = appInfoMap.getOrPut(session) {
        AppInfoData(
            applicationName = applicationName,
            singleInput = singleInput,
            stickyInput = stickyInput,
            loadImages = false,
            showMenubar = showMenubar
        )
    }.toMap()

    override fun newSession(user: User?, session: Session) =
        chats[session]?.newSession(user, session) ?: agents[session]
        ?: throw IllegalStateException("No agent found for session $session")

    companion object {
        val metadataStorage by lazy { ApplicationServices.metadataStorageFactory(dataStorageRoot.resolve("metadatadb")) }
        val agents = mutableMapOf<Session, SocketManager>()
        val chats = mutableMapOf<Session, ChatServer>()
    }
}
