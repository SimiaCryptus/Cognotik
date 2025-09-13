package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.chat.model.ChatModel
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.ApplicationServices.userSettingsManager
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.UserSettingsInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
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

    override fun newSession(user: User?, session: Session): ChatSocketManager {
        val settings = this.settings ?: getSettings(session, user)!!
        val user = user ?: throw IllegalArgumentException("User must be provided for chat session")
        fun instance(model: ChatModel) = model.getApi(user)?.let { apiData ->
            model.instance(
                key = apiData.key,
                base = apiData.baseUrl,
                temperature = settings.temperature,
                workPool = ApplicationServices.clientManager.getPool(session, user),
            )
        }
        return ChatSocketManager(
            session = session,
            model = instance(settings.model)
                ?: throw RuntimeException("No API key for model ${settings.model.name}"),
            parsingModel = instance(settings.parsingModel)
                ?: throw RuntimeException("No API key for model ${settings.parsingModel.name}"),
            systemPrompt = "",
            temperature = settings.temperature,
            applicationClass = this::class.java,
            storage = DataStorage(root),
            fastTopicParsing = true,
            budget = settings.budget,
        )
    }
}

@Deprecated("Need to refactor to include api config")
fun ChatModel.getApi(user: User): UserSettingsInterface.ApiData? =
    userSettingsManager.getUserSettings(user).apis.firstOrNull { it.provider == provider }?.validate()

