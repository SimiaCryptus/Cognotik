package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.apps.general.OutlineApp
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.models.ChatModel
import com.simiacryptus.jopenai.models.OpenAIModels
import java.io.File

class BasicChatApp(
  root: File,
  val model: ChatModel,
  val parsingModel: ChatModel,
  applicationName: String = "Chat",
  val settings: Settings? = null,
) : ApplicationServer(
  applicationName = applicationName,
  path = root.absolutePath,
  root = root
) {
  override val stickyInput: Boolean
    get() = true
  override val singleInput: Boolean
    get() = false

  data class Settings(
    val model: ChatModel = OpenAIModels.GPT4o,
    val parsingModel: ChatModel = OpenAIModels.GPT4oMini,
    val temperature: Double = 0.3,
    val budget: Double = 2.0,
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session): T? = Settings() as T

  override fun newSession(user: User?, session: Session): ChatSocketManager {
    val settings = this.settings ?: getSettings(session, user)!!
    return ChatSocketManager(
      session = session,
      model = settings.model,
      parsingModel = settings.parsingModel,
      initialAssistantPrompt = "",
      systemPrompt = "",
      api = ApplicationServices.clientManager.getChatClient(session, user),
      temperature = settings.temperature,
      applicationClass = this::class.java,
      storage = DataStorage(root),
      fastTopicParsing = true,
      budget = settings.budget,
    )
  }
}