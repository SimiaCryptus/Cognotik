package com.simiacryptus.cognotik.webui.test

import com.simiacryptus.cognotik.agents.ChatAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager

open class SimpleActorTestApp(
    private val actor: ChatAgent,
    applicationName: String = "SimpleActorTest_" + actor.javaClass.simpleName,
    temperature: Double = 0.3,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/simpleActorTest",
) {

    data class Settings(
        val actor: ChatAgent? = null,
    )

    override val settingsClass: Class<*> get() = Settings::class.java

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> initSettings(session: Session): T? = Settings(actor = actor) as T

    override fun userMessage(
        session: Session,
        user: User,
        userMessage: String,
        ui: SocketManager
    ) {
        val message = ui.newTask()
        try {
            val actor = getSettings<Settings>(session, user)?.actor ?: actor
            message.echo(userMessage.renderMarkdown(true))
            val response = actor.answer(listOf(userMessage))
            message.complete(response.renderMarkdown(true))
        } catch (e: Throwable) {
            log.warn("Error", e)
            message.error(e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SimpleActorTestApp::class.java)
    }

}