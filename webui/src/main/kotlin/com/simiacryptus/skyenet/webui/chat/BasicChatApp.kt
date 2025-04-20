package com.simiacryptus.skyenet.webui.chat

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.file.DataStorage
import com.simiacryptus.skyenet.core.platform.model.User
import java.io.File

class BasicChatApp(
  val root: File,
  val api: ChatClient,
  val model: ChatModel,
  val parsingModel: ChatModel,
  override val applicationName: String = "Chat"
) : ChatServer() {
  
  override fun newSession(user: User?, session: Session) = ChatSocketManager(
    session,
    model,
    parsingModel,
    "",
    "",
    "",
    api,
    0.0,
    this::class.java,
    DataStorage(root),
    true
  )
}