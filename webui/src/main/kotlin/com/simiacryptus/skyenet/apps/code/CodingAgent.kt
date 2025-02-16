package com.simiacryptus.skyenet.apps.code

import com.simiacryptus.jopenai.API
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.models.ApiModel
import com.simiacryptus.jopenai.models.TextModel
import com.simiacryptus.jopenai.proxy.ValidatedObject
import com.simiacryptus.skyenet.Retryable
import com.simiacryptus.skyenet.core.actors.CodingActor
import com.simiacryptus.skyenet.core.actors.CodingActor.CodeResult
import com.simiacryptus.skyenet.core.platform.ApplicationServices
import com.simiacryptus.skyenet.core.platform.Session
import com.simiacryptus.skyenet.core.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.skyenet.core.platform.model.StorageInterface
import com.simiacryptus.skyenet.core.platform.model.User
import com.simiacryptus.skyenet.interpreter.Interpreter
import com.simiacryptus.skyenet.util.MarkdownUtil.renderMarkdown
import com.simiacryptus.skyenet.webui.application.ApplicationInterface
import com.simiacryptus.skyenet.webui.session.SessionTask
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass


open class CodingAgent<T : Interpreter>(
  val api: API,
  val dataStorage: StorageInterface,
  val session: Session,
  val user: User?,
  val ui: ApplicationInterface,
  val interpreter: KClass<T>,
  val symbols: Map<String, Any>,
  val temperature: Double = 0.1,
  val details: String? = null,
  val model: TextModel,
  private val mainTask: SessionTask,
) {
  val actors = mapOf(
    ActorTypes.CodingActor to CodingActor(
      interpreter, symbols = symbols, temperature = temperature, details = details, model = model
    )
  ).map { it.key.name to it.value }.toMap()
  enum class ActorTypes {
    CodingActor
  }

  open val actor by lazy {
    actors.get(ActorTypes.CodingActor.name)!! as CodingActor
  }

  open val canPlay by lazy {
    ApplicationServices.authorizationManager.isAuthorized(
      this::class.java, user, OperationType.Execute
    )
  }

  fun start(
    userMessage: String,
  ) {
    try {
      mainTask.echo(renderMarkdown(userMessage, ui = ui))
      val codeRequest = codeRequest(listOf(userMessage to ApiModel.Role.user))
      start(codeRequest, mainTask)
    } catch (e: Throwable) {
      log.warn("Error", e)
      mainTask.error(ui, e)
    }
  }

  fun start(
    codeRequest: CodingActor.CodeRequest,
    task: SessionTask = mainTask,
  ) {
    val task = ui.newTask(root = false)
    task.complete(task.placeholder)
    Retryable(ui, task) {
      val task = ui.newTask(root = false)
      ui.socketManager?.scheduledThreadPoolExecutor!!.schedule({
        ui.socketManager.pool.submit {
          val statusSB = task.add("Running...")
          displayCode(task, codeRequest)
          statusSB?.clear()
          task.complete()
        }
      }, 100, TimeUnit.MILLISECONDS)
      task.placeholder
    }
  }

  open fun codeRequest(messages: List<Pair<String, ApiModel.Role>>) = CodingActor.CodeRequest(messages)

  fun displayCode(
    task: SessionTask,
    codeRequest: CodingActor.CodeRequest,
  ) {
    try {
      val lastUserMessage = codeRequest.messages.last { it.second == ApiModel.Role.user }.first.trim()
      val codeResponse: CodeResult = if (lastUserMessage.startsWith("```")) {
        actor.CodeResultImpl(
          messages = actor.chatMessages(codeRequest),
          input = codeRequest,
          api = api as ChatClient,
          givenCode = lastUserMessage.removePrefix("```").removeSuffix("```")
        )
      } else {
        actor.answer(codeRequest, api = api)
      }
      displayCodeAndFeedback(task, codeRequest, codeResponse)
    } catch (e: Throwable) {
      log.warn("Error", e)
    }
  }

  protected fun displayCodeAndFeedback(
    task: SessionTask,
    codeRequest: CodingActor.CodeRequest,
    response: CodeResult,
  ) {
    try {
      displayCode(task, response)
      displayFeedback(task, append(codeRequest, response), response)
    } catch (e: Throwable) {
      task.error(ui, e)
      log.warn("Error", e)
    }
  }

  fun append(
    codeRequest: CodingActor.CodeRequest, response: CodeResult
  ) = codeRequest(
    messages = codeRequest.messages + listOf(
      response.code to ApiModel.Role.assistant,
    ).filter { it.first.isNotBlank() })

  fun displayCode(
    task: SessionTask, response: CodeResult
  ) {
    task.hideable(
      ui, renderMarkdown(
        response.renderedResponse ?:
        //language=Markdown
        "```${actor.language.lowercase(Locale.getDefault())}\n${response.code.trim()}\n```", ui = ui
      )
    )
  }

  open fun displayFeedback(
    task: SessionTask, request: CodingActor.CodeRequest, response: CodeResult
  ) {
    val formText = StringBuilder()
    var formHandle: StringBuilder? = null
    formHandle = task.add(
      "<div style=\"display: flex;flex-direction: column;\">\n${
        if (!canPlay) "" else playButton(
          task, request, response, formText
        ) { formHandle!! }
      }\n</div>\n${reviseMsg(task, request, response, formText) { formHandle!! }}", additionalClasses = "reply-message"
    )
    formText.append(formHandle.toString())
    formHandle.toString()
    task.complete()
  }


  protected fun reviseMsg(
    task: SessionTask, request: CodingActor.CodeRequest, response: CodeResult, formText: StringBuilder, formHandle: () -> StringBuilder
  ) = ui.textInput { feedback ->
    responseAction(task, "Revising...", formHandle(), formText) {
      feedback(task, feedback, request, response)
    }
  }

  protected fun regenButton(
    task: SessionTask, request: CodingActor.CodeRequest, formText: StringBuilder, formHandle: () -> StringBuilder
  ) = ""

  protected fun playButton(
    task: SessionTask, request: CodingActor.CodeRequest, response: CodeResult, formText: StringBuilder, formHandle: () -> StringBuilder
  ) = if (!canPlay) "" else ui.hrefLink("▶", "href-link play-button") {
    responseAction(task, "Running...", formHandle(), formText) {
      execute(task, response, request)
    }
  }

  protected open fun responseAction(
    task: SessionTask, message: String, formHandle: StringBuilder?, formText: StringBuilder, fn: () -> Unit = {}
  ) {
    formHandle?.clear()
    val header = task.header(message)
    try {
      fn()
    } finally {
      header?.clear()
      revertButton(task, formHandle, formText)
    }
  }

  protected open fun revertButton(
    task: SessionTask, formHandle: StringBuilder?, formText: StringBuilder
  ): StringBuilder? {
    var revertButton: StringBuilder? = null
    revertButton = task.complete(ui.hrefLink("↩", "href-link regen-button") {
      revertButton?.clear()
      formHandle?.append(formText)
      task.complete()
    })
    return revertButton
  }

  protected open fun feedback(
    task: SessionTask, feedback: String, request: CodingActor.CodeRequest, response: CodeResult
  ) {
    try {
      task.echo(renderMarkdown(feedback, ui = ui))
      start(
        codeRequest = codeRequest(
          messages = request.messages + listOf(
            response.code to ApiModel.Role.assistant,
            feedback to ApiModel.Role.user,
          ).filter { it.first.isNotBlank() }.map { it.first to it.second }), task = task
      )
    } catch (e: Throwable) {
      log.warn("Error", e)
      task.error(ui, e)
    }
  }

  protected open fun execute(
    task: SessionTask,
    response: CodeResult,
    request: CodingActor.CodeRequest,
  ) {
    try {
      val result = execute(task, response)
      displayFeedback(
        task, codeRequest(
          messages = request.messages + listOf(
            "Running...\n\n$result" to ApiModel.Role.assistant,
          ).filter { it.first.isNotBlank() }), response
      )
    } catch (e: Throwable) {
      handleExecutionError(e, task, request, response)
    }
  }

  protected open fun handleExecutionError(
    e: Throwable, task: SessionTask, request: CodingActor.CodeRequest, response: CodeResult
  ) {
    val message = when {
      e is ValidatedObject.ValidationError -> renderMarkdown(e.message ?: "", ui = ui)
      e is CodingActor.FailedToImplementException -> renderMarkdown("**Failed to Implement** \n\n${e.message}\n\n", ui = ui)
      else -> renderMarkdown("**Error `${e.javaClass.name}`**\n\n```text\n${e.stackTraceToString()}\n```\n", ui = ui)
    }
    task.add(message, true, "div", "error")
    displayCode(
      task, CodingActor.CodeRequest(
        messages = request.messages + listOf(
          response.code to ApiModel.Role.assistant,
          message to ApiModel.Role.system,
        ).filter { it.first.isNotBlank() })
    )
  }

  fun execute(
    task: SessionTask, response: CodeResult
  ): String {
    val resultValue = response.result.resultValue
    val resultOutput = response.result.resultOutput
    val result = when {
      resultValue.isBlank() || resultValue.trim().lowercase() == "null" -> "# Output\n```text\n$resultOutput\n```"
      else -> "# Result\n```\n$resultValue\n```\n\n# Output\n```text\n$resultOutput\n```"
    }
    task.add(renderMarkdown(result, ui = ui))
    return result
  }

  companion object {
    private val log = LoggerFactory.getLogger(CodingAgent::class.java)
  }
}
