package com.simiacryptus.skyenet.webui.chat

import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.file.DataStorage
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.webui.application.ApplicationServer
import java.io.File

class BasicChatApp(
  root: File,
  val api: ChatClient,
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
    api = api,
    temperature = 0.0,
    applicationClass = this::class.java,
    storage = DataStorage(root),
    fastTopicParsing = true
  )
}