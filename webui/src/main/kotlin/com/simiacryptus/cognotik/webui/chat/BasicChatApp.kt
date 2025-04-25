package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.models.ChatModel
import java.io.File

class BasicChatApp(
  root: File,
  val model: ChatModel,
  val parsingModel: ChatModel,
  applicationName: String = "Chat"
) : ApplicationServer(
  applicationName = applicationName,
  path = root.absolutePath,
  root = root
) {
  override val stickyInput: Boolean
    get() = true
  override val singleInput: Boolean
    get() = false
  
  override fun newSession(user: User?, session: Session) = ChatSocketManager(
    session = session,
    model = model,
    parsingModel = parsingModel,
    initialAssistantPrompt = "",
    systemPrompt = "",
    api = ApplicationServices.clientManager.getChatClient(session, user),
    temperature = 0.0,
    applicationClass = this::class.java,
    storage = DataStorage(root),
    fastTopicParsing = true,
  )
}