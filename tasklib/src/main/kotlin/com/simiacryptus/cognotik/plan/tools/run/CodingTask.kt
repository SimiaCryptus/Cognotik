package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.agents.CodeAgent.CodeRequest
import com.simiacryptus.cognotik.chat.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.exceptions.FailedToImplementException
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.OperationType
import com.simiacryptus.cognotik.platform.StorageInterface
import com.simiacryptus.cognotik.platform.model.Principal
import com.simiacryptus.cognotik.platform.model.ResourceRef
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.ui.Retryable
import com.simiacryptus.cognotik.ui.TabbedDisplay
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.ui.Retryable.Companion.async
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import org.slf4j.LoggerFactory.getLogger
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale.getDefault

open class CodingTask<T : CodeRuntime>(
  val dataStorage: StorageInterface,
  val session: Session,
  val user: User,
  val ui: SocketManager,
  val codeRuntime: T,
  val symbols: Map<String, Any>,
  val temperature: Double = 0.1,
  val details: String? = null,
  val model: ChatInterface,
  private val mainTask: SessionTask,
  val retryable: Boolean = true,
  val autoFix: Boolean = false,
  val describer: TypeDescriber = AbbrevWhitelistYamlDescriber(
    "com.simiacryptus"
  ),
) {


  open val canPlay by lazy {
    ApplicationServices.authorizationManager.isAuthorized(
      ResourceRef.of(this::class.java),
      Principal.of(user),
      OperationType.Execute
    )
  }

  fun start(
    codeRequest: CodeRequest,
    task: SessionTask = mainTask,
  ) {
    val subTask = task.newTask()
    task.complete(subTask.placeholder)
    if (retryable) {
      Retryable(ui.newTask(true), process = { innerTask: SessionTask ->
        try {
          val statusSB = innerTask.add("Running...")
          displayCode(innerTask, codeRequest)
          statusSB?.clear()
        } catch (e: Throwable) {
          log.error("Error in CodingTask start (retryable)", e)
          innerTask.error(e)
          val transcript = innerTask.transcript()
          try {
            transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
          } finally {
            transcript?.close()
          }
        } finally {
          innerTask.complete()
        }
        Unit
      }.async(task.ui))
    } else {
      ui.pool.submit {
        try {
          val statusSB = subTask.add("Running...")
          displayCode(subTask, codeRequest)
          statusSB?.clear()
          subTask.update()
        } catch (e: Throwable) {
          log.error("Error in CodingTask start", e)
          subTask.error(e)
          val transcript = subTask.transcript()
          try {
            transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
          } finally {
            transcript?.close()
          }
        } finally {
          subTask.complete()
        }
      }
    }
  }

  fun SessionTask.transcript(name: String = this.javaClass.simpleName): FileOutputStream? {
    val relativePath = "transcript/${name}_${SimpleDateFormat("yyyyMMddHHmmss").format(System.currentTimeMillis())}.md"
    val (link, file) = Pair(linkTo(relativePath), resolveUserFile(relativePath))
    val markdownTranscript = file?.outputStream()
    add(
      "Writing $name to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
        link.removeSuffix(
          ".md"
        )
      }.pdf' target='_blank'>pdf</a>".renderMarkdown(),
      additionalClasses = "verbose"
    )
    return markdownTranscript
  }

  open fun codeRequest(messages: List<Pair<String, ModelSchema.Role>>) = CodeRequest(messages)

  fun displayCode(
    task: SessionTask,
    codeRequest: CodeRequest,
  ) {
    try {
      val lastUserMessage = codeRequest.messages.last { it.second == ModelSchema.Role.user }.first.trim()
      val codeAgent = CodeAgent(
        codeRuntime,
        symbols = symbols(task),
        temperature = temperature,
        details = details,
        model = model,
        fallbackModel = model,
        describer = describer,
        user = user,
      )
      val codeResponse: CodeAgent.CodeResult = if (lastUserMessage.startsWith("```")) {
        codeAgent.CodeResultImpl(
          messages = codeAgent.chatMessages(codeRequest),
          input = codeRequest,
          givenCode = lastUserMessage.removePrefix("```").removeSuffix("```")
        )
      } else {
        codeAgent.answer(codeRequest)
      }
      displayCodeAndFeedback(task, codeRequest, codeResponse, codeAgent.language)
    } catch (e: Throwable) {
      log.error("Error in displayCode", e)
      task.error(e)
      val transcript = task.transcript()
      try {
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      } finally {
        transcript?.close()
      }
    }
  }

  open fun symbols(task: SessionTask) = symbols + mapOf(
    "task" to task,
  )

  protected fun displayCodeAndFeedback(
    task: SessionTask,
    codeRequest: CodeRequest,
    response: CodeAgent.CodeResult,
    language: String,
  ) {
    try {
      val string = response.renderedResponse
        ?: "\n```${language.lowercase(getDefault())}\n${response.code.trim()}\n```\n"
      task.expanded("Code", string.renderMarkdown(true))
      val transcript = task.transcript()
      try {
        transcript?.write("# Generated Code\n$string\n".toByteArray())
      } finally {
        transcript?.close()
      }
      if (autoFix && canPlay) {
        execute(task, response, codeRequest)
      } else {
        displayFeedback(
          task, codeRequest(
            messages = codeRequest.messages + listOf(
              response.code to ModelSchema.Role.assistant,
            ).filter { it.first.isNotBlank() }), response
        )
      }
    } catch (e: Throwable) {
      log.error("Error in displayCodeAndFeedback", e)
      task.error(e)
      val transcript = task.transcript()
      try {
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      } finally {
        transcript?.close()
      }
    }
  }

  open fun displayFeedback(
    task: SessionTask, request: CodeRequest, response: CodeAgent.CodeResult
  ) {
    val formHandle = task.add("", additionalClasses = "reply-message")
    val formText = StringBuilder()
    formText.append("<div>\n")
    if (canPlay) {
      formText.append(playButton(task, request, response, formText) { formHandle!! })
    }
    formText.append("\n</div>\n")
    formText.append(ui.textInput { feedback ->
      responseAction(task, "Revising...", formHandle, formText) {
        feedback(task, feedback, request, response)
      }
    })
    formHandle?.append(formText)
    task.update()
    task.complete()
  }

  protected fun playButton(
    task: SessionTask,
    request: CodeRequest,
    response: CodeAgent.CodeResult,
    formText: StringBuilder,
    formHandle: () -> StringBuilder
  ) = if (!canPlay) "" else ui.hrefLink("▶ Run", "href-link play-button") {
    responseAction(task, "Running...", formHandle(), formText) {
      execute(task, response, request)
    }
  }.replace("<a class", """<a style="font-size: large;" class""")

  protected open fun responseAction(
    task: SessionTask, message: String, formHandle: StringBuilder?, formText: StringBuilder, fn: () -> Unit = {}
  ) {
    formHandle?.clear()
    task.update()
    val header = task.header(message, 2)
    try {
      fn()
    } finally {
      header?.clear()
      var revertButton: StringBuilder? = null
      val link = ui.hrefLink("↩", "href-link regen-button") {
        revertButton?.clear()
        formHandle?.append(formText)
        task.update()
      }
      revertButton = task.add(link)
      task.complete()
    }
  }

  protected open fun feedback(
    task: SessionTask, feedback: String, request: CodeRequest, response: CodeAgent.CodeResult
  ) {
    try {
      task.echo(feedback.renderMarkdown(true))
      start(
        codeRequest = codeRequest(
          messages = request.messages + listOf(
            response.code to ModelSchema.Role.assistant,
            feedback to ModelSchema.Role.user,
          ).filter { it.first.isNotBlank() }.map { it.first to it.second }), task = task
      )
    } catch (e: Throwable) {
      log.error("Error in feedback", e)
      task.error(e)
    }
  }

  protected fun execute(
    task: SessionTask,
    response: CodeAgent.CodeResult,
    request: CodeRequest,
  ) {
    try {
      val result = execute(task, response)
      displayFeedback(
        task, codeRequest(
          messages = request.messages + listOf(
            "Running...\n\n$result" to ModelSchema.Role.assistant,
          ).filter { it.first.isNotBlank() }), response
      )
    } catch (e: Throwable) {
      log.error("Execution failed", e)
      task.error(e)
      val transcript = task.transcript()
      try {
        transcript?.write("## Error\n\n```\n${e.stackTraceToString()}\n```".toByteArray())
      } finally {
        transcript?.close()
      }

      val message = when {
        e is ValidatedObject.ValidationError -> e.message ?: "".renderMarkdown(true)
        e is FailedToImplementException -> "**Failed to Implement** \n\n${e.message}\n\n".renderMarkdown(true)
        else -> "**Error `${e.javaClass.name}`**\n\n```text\n${e.stackTraceToString()}\n```\n".renderMarkdown(true)
      }
      displayCode(
        task, CodeRequest(
          messages = request.messages + listOf(
            response.code to ModelSchema.Role.assistant,
            message to ModelSchema.Role.system,
          ).filter { it.first.isNotBlank() })
      )
    }
  }

  protected open fun execute(
    task: SessionTask, response: CodeAgent.CodeResult
  ): String {
    val transcript = task.transcript()

    try {
      val resultValue = response.result.resultValue
      val resultOutput = response.result.resultOutput
      transcript?.write(
        """
                # Execution Result
                <details><summary>Output & Value</summary>
                
                ## Output
                ```text
                $resultOutput
                ```
                ## Value
                ```text
                $resultValue
                ```
                </details>
                """.trimIndent().toByteArray()
      )
      val tabs = TabbedDisplay(task)
      tabs["Result"] = "```text\n$resultValue\n```".renderMarkdown()
      tabs["Output"] = "```text\n$resultOutput\n```".renderMarkdown()
      return when {
        resultValue.isBlank() || resultValue.trim().lowercase() == "null" -> "# Output\n```text\n$resultOutput\n```"
        else -> "# Result\n```\n$resultValue\n```\n\n# Output\n```text\n$resultOutput\n```"
      }
    } finally {
      transcript?.close()
    }

  }

  companion object {
    private val log = getLogger(CodeAgent::class.java)
  }
}