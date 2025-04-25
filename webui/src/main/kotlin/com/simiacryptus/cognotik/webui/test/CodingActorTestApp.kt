package com.simiacryptus.cognotik.webui.test

import com.simiacryptus.cognotik.actors.CodingActor
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.cognotik.webui.application.ApplicationInterface
import com.simiacryptus.cognotik.webui.application.ApplicationServer
import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import org.slf4j.LoggerFactory
import java.util.*

open class CodingActorTestApp(
  private val actor: CodingActor,
  applicationName: String = "CodingActorTest_" + actor.name,
  temperature: Double = 0.3,
) : ApplicationServer(
  applicationName = applicationName,
  path = "/codingActorTest",
) {
  override fun userMessage(
    session: Session, user: User?, userMessage: String, ui: ApplicationInterface, api: API
  ) {
    (api as ChatClient).budget = 2.00
    val message = ui.newTask()
    try {
      message.echo(renderMarkdown(userMessage, ui = ui))
      val response = actor.answer(CodingActor.CodeRequest(listOf(userMessage to ApiModel.Role.user)), api = api)
      val canPlay = ApplicationServices.authorizationManager.isAuthorized(this::class.java, user, OperationType.Execute)
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
        renderMarkdown(
          "```${actor.language.lowercase(Locale.getDefault())}\n${response.code}\n```\n$playLink".trim(), ui = ui
        )
      )
    } catch (e: Throwable) {
      log.warn("Error", e)
      message.error(ui, e)
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(CodingActorTestApp::class.java)
  }

}