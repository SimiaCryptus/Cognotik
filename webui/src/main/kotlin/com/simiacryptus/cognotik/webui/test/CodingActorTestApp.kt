package com.simiacryptus.cognotik.webui.test

import com.simiacryptus.cognotik.actors.CodeAgent
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.util.*

open class CodingActorTestApp(
    private val actor: CodeAgent,
    applicationName: String = "CodingActorTest_" + actor.name,
    temperature: Double = 0.3,
) : ApplicationServer(
    applicationName = applicationName,
    path = "/codingActorTest",
) {
    override fun userMessage(
        session: Session, user: User?, userMessage: String, ui: SocketManager
    ) {
        val message = ui.newTask()
        try {
            message.echo(userMessage.renderMarkdown)
            val response = actor.answer(CodeAgent.CodeRequest(listOf(userMessage to ModelSchema.Role.user)))
            val canPlay =
                ApplicationServices.authorizationManager.isAuthorized(this::class.java, user, OperationType.Execute)
            val playLink = if (!canPlay) "" else {
                ui.hrefLink("▶", "href-link play-button") {
                    message.add("Running...")
                    val result = response.result
                    message.complete(
                        "<pre>${result.resultValue}</pre>\n<pre>${result.resultOutput}</pre>"
                    )
                }
            }
            message.complete(
                "```${actor.language.lowercase(Locale.getDefault())}\n${response.code}\n```\n$playLink".trim().renderMarkdown
            )
        } catch (e: Throwable) {
            log.warn("Error", e)
            message.error(e)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CodingActorTestApp::class.java)
    }

}