package com.simiacryptus.cognotik.webui.test

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager

open class ParsedActorTestApp<T : Any>(
    private val actor: ParsedActor<T>,
    applicationName: String = "ParsedActorTest_" + actor.resultClass?.simpleName,
    temperature: Double = 0.3,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/parsedActorTest",
) {
    override fun userMessage(
        session: Session,
        user: User?,
        userMessage: String,
        ui: SocketManager
    ) {
        val message = ui.newTask()
        try {
            message.echo(userMessage.renderMarkdown)
            val response = actor.answer(listOf(userMessage))
            message.complete(
                "${response.text}\n```\n${JsonUtil.toJson(response.obj)}\n```".trim().renderMarkdown
            )
        } catch (e: Throwable) {
            log.warn("Error", e)
            message.error(e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ParsedActorTestApp::class.java)
    }

}