package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.fileApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.SessionProxyServer
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
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
    override val inputCnt get() = 0

    data class Settings(
        val model: ChatModel,
        val parsingModel: ChatModel,
        val temperature: Double = 0.3,
        val budget: Double = 2.0,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T? = Settings(
        model = model,
        parsingModel = parsingModel,
    ) as T

    override fun newSession(user: User, session: Session): SocketManager {
        (SessionProxyServer.chats[session]?.takeIf { it != this }?.newSession(user, session)
            ?: SessionProxyServer.agents[session])?.apply {
            return this;
        }
        val user = user ?: throw IllegalArgumentException("User must be provided for chat session")
        val settings = this.settings ?: getSettings(session, user)!!
        fun instance(model: ChatModel): ChatInterface? {
            val api = fileApplicationServices().userSettingsManager.getUserSettings(user).apis
                .firstOrNull { it.provider == model.provider }?.validate()
            val threadPoolManager = ApplicationServices.threadPoolManager
            return api?.let { apiData ->
                model.instance(
                    key = apiData.key ?: return null,
                    base = apiData.baseUrl,
                    workPool = threadPoolManager.getPool(session, user),
                    temperature = settings.temperature,
                    scheduledPool = threadPoolManager.getScheduledPool(session, user),
                    onUsage = { model, usage ->
                        fileApplicationServices().usageManager.incrementUsage(
                            session,
                            user,
                            model,
                            usage
                        )
                    },
                )
            }
        }
        return ChatSocketManager(
            session = session,
            smartModel = instance(settings.model)
                ?: throw RuntimeException("No API key for model ${settings.model.name}"),
            fastModel = instance(settings.parsingModel)
                ?: throw RuntimeException("No API key for model ${settings.parsingModel.name}"),
            systemPrompt = "",
            temperature = settings.temperature,
            applicationClass = this::class.java,
            storage = dataStorage,
            fastTopicParsing = true,
            budget = settings.budget,
        )
    }
}

