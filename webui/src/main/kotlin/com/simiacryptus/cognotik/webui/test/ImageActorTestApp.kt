package com.simiacryptus.cognotik.webui.test

import com.simiacryptus.cognotik.agents.ImageGenerationAgent
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager

open class ImageActorTestApp(
    private val actor: ImageGenerationAgent,
    applicationName: String = "ImageActorTest_" + actor.javaClass.simpleName,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/imageActorTest",
) {

    data class Settings(
        val actor: ImageGenerationAgent? = null,
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
            val response = actor.answer(
                listOf(userMessage)
            )
            message.verbose(response.text)
            message.image(response.image!!)
            message.complete()
        } catch (e: Throwable) {
            log.warn("Error flushing image", e)
            message.error(e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ImageActorTestApp::class.java)
    }

}