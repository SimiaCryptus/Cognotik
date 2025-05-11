package com.simiacryptus.cognotik.webui.test

import com.simiacryptus.cognotik.actors.ParsedActor
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.util.JsonUtil
import org.slf4j.LoggerFactory

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
        ui: ApplicationInterface,
        api: API
    ) {
        (api as ChatClient).budget = 2.00
        val message = ui.newTask()
        try {
            message.echo(userMessage.renderMarkdown)
            val response = actor.answer(listOf(userMessage), api = api)
            message.complete(
              "${response.text}\n```\n${JsonUtil.toJson(response.obj)}\n```".trim().renderMarkdown
            )
        } catch (e: Throwable) {
            log.warn("Error", e)
            message.error(ui, e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(ParsedActorTestApp::class.java)
    }

}