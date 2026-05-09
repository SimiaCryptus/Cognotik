package com.simiacryptus.cognotik.util

import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.AppInfoData
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.chat.ChatServer
import com.simiacryptus.cognotik.webui.chat.ChatSocketManager
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.LoggerFactory

open class SessionProxyServer(appname: String = "Cognotik", path: String = "/") : ApplicationServer(
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
      agents[session] ?: chats[session]?.newSession(user, session) ?: ChatSocketManager(
        session = session,
        smartModel = ChatInterface.NULL,
        fastModel = ChatInterface.NULL,
        systemPrompt = "",
        applicationClass = this::class.java,
        budget = 0.0,
        owner = user
      )

  companion object {
     private val log = LoggerFactory.getLogger(SessionProxyServer::class.java)

    fun setParentSession(child: Session, parent: Session) {
      ApplicationServices.fileApplicationServices().usageManager.setParentSession(child, parent)
    }
    var OWNER_ID = "localhost:12345"
    val metadataStorage by lazy { ApplicationServices.fileApplicationServices().metadataStorageFactory }

     private fun registerSessionOwner(session: Session) {
       try {
         metadataStorage.setSessionOwner(session, OWNER_ID)
       } catch (e: Exception) {
         log.warn("Failed to register session owner for session: $session", e)
       }
     }

     val agents: MutableMap<Session, SocketManager> = object : java.util.concurrent.ConcurrentHashMap<Session, SocketManager>() {
       override fun put(key: Session, value: SocketManager): SocketManager? {
         registerSessionOwner(key)
         return super.put(key, value)
       }

       override fun putIfAbsent(key: Session, value: SocketManager): SocketManager? {
         val result = super.putIfAbsent(key, value)
         if (result == null) registerSessionOwner(key)
         return result
       }
     }

     val chats: MutableMap<Session, ChatServer> = object : java.util.concurrent.ConcurrentHashMap<Session, ChatServer>() {
       override fun put(key: Session, value: ChatServer): ChatServer? {
         registerSessionOwner(key)
         return super.put(key, value)
       }

       override fun putIfAbsent(key: Session, value: ChatServer): ChatServer? {
         val result = super.putIfAbsent(key, value)
         if (result == null) registerSessionOwner(key)
         return result
       }
     }
  }
}