package com.simiacryptus.cognotik.webui.chat

import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.file.DataStorage
import com.simiacryptus.cognotik.platform.instance
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.models.chat.ChatModelType
import java.io.File

class BasicChatApp(
    root: File,
    val model: ChatModelType,
    val parsingModel: ChatModelType,
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
        val model: ChatModelType,
        val parsingModel: ChatModelType,
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

        return ChatSocketManager(
            session = session,
            model = settings.model.instance(user ?: throw IllegalArgumentException("User must be provided for chat session")),
            parsingModel = settings.parsingModel.instance(user),
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

