package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.models.LLMModel
import com.simiacryptus.cognotik.models.ModelSchema.Usage
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.ApiData
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.File
import kotlin.String

class BasicChatApp(
  root: File,
  applicationName: String = "Chat",
  val model: String? = null,
  val fastModel: String? = null,
) : ApplicationServer(
  applicationName = applicationName,
  path = root.absolutePath,
  root = root
) {
  override val stickyInput: Boolean
    get() = true
  override val inputCnt get() = 0

  data class Settings(
    val model: String? = null,
    val fastModel: String? = null,
    val temperature: Double = 0.3,
    val budget: Double = 2.0,
  )

  override val settingsClass: Class<*> get() = Settings::class.java

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> initSettings(session: Session, user: User): T = Settings() as T

  override fun newSession(user: User, session: Session): SocketManager {
    (SessionProxyServer.chats[session]?.takeIf { it != this }?.newSession(user, session)
      ?: SessionProxyServer.agents[session])?.apply {
      return this;
    }
    val settings = getSettings(session, user, Settings::class.java) ?: Settings(   )

    fun instance(model: String): ChatInterface? {
      val userSettings = fileApplicationServices().userSettingsManager.getUserSettings(user)
      val chatModel = userSettings.apis
        .filter { it.provider != null && it.key != null && it.baseUrl != null }
        .flatMap { it.provider!!.getChatModels(it.key!!, it.baseUrl!!) ?: emptyList() }
        .firstOrNull { it.modelId == model }
      return if (chatModel != null) {
        val api = userSettings.apis.find {
          it.provider?.name == chatModel.provider?.name
        } ?: return null
        val threadPoolManager = ApplicationServices.threadPoolManager
        chatModel.instance(
          key = api.key!!,
          base = api.apiBase,
          workPool = threadPoolManager.getPool(session, user),
          temperature = settings.temperature,
          scheduledPool = threadPoolManager.getScheduledPool(session, user),
          onUsage = { model: LLMModel, usage : Usage ->
            fileApplicationServices().usageManager.incrementUsage(
              session,
              user,
              model,
              usage
            )
          },
        )
      } else {
        log.warn("No API key found for model ${model} for user ${user.name}. This model will not be available in the chat session.")
        null
      }
    }

    val smartModel = settings.model ?: model ?: throw RuntimeException()
    val fastModel = settings.fastModel ?: fastModel ?: throw RuntimeException()
    val smartApi = instance(smartModel)
    val fastApi = instance(fastModel)
    return ChatSocketManager(
      session = session,
      smartModel = smartApi ?: throw RuntimeException("No API key for model ${smartModel}"),
      fastModel = fastApi?: throw RuntimeException("No API key for model ${fastModel}"),
      systemPrompt = "",
      temperature = settings.temperature,
      applicationClass = this::class.java,
      storage = dataStorage,
      budget = settings.budget,
      owner = user,
    )
  }
}

