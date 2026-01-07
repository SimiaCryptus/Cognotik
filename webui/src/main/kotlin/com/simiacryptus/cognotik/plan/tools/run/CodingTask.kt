package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.agents.CodeAgent.CodeRequest
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale.getDefault

open class CodingTask<T : CodeRuntime>(
    val dataStorage: StorageInterface,
    val session: Session,
    val user: User?,
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
            this::class.java, user, AuthorizationInterface.OperationType.Execute
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
                    log.warn("Error", e)
                    innerTask.error(e)
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
                    log.warn("Error", e)
                    subTask.error(e)
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
        complete(
            "Writing $name to <a href='$link' target='_blank'>$link</a> <a href='${link.removeSuffix(".md")}.html' target='_blank'>html</a> <a href='${
                link.removeSuffix(
                    ".md"
                )
            }.pdf' target='_blank'>pdf</a>",
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
            log.warn("Error", e)
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
            task.expanded("Code", string.renderMarkdown)
            task.transcript()?.write("# Generated Code\n$string\n".toByteArray())
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
            task.error(e)
            log.warn("Error", e)
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
            task.echo(feedback.renderMarkdown)
            start(
                codeRequest = codeRequest(
                    messages = request.messages + listOf(
                        response.code to ModelSchema.Role.assistant,
                        feedback to ModelSchema.Role.user,
                    ).filter { it.first.isNotBlank() }.map { it.first to it.second }), task = task
            )
        } catch (e: Throwable) {
            log.warn("Error", e)
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
            val message = when {
                e is ValidatedObject.ValidationError -> e.message ?: "".renderMarkdown
                e is FailedToImplementException -> "**Failed to Implement** \n\n${e.message}\n\n".renderMarkdown
                else -> "**Error `${e.javaClass.name}`**\n\n```text\n${e.stackTraceToString()}\n```\n".renderMarkdown
            }
            task.add(message, true, "div", "error")
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
        val resultValue = response.result.resultValue
        val resultOutput = response.result.resultOutput
        transcript?.write(
            """
            # Execution Result
            ## Output
            ```text
            $resultOutput
            ```
            ## Value
            ```text
            $resultValue
            ```
            """.trimIndent().toByteArray()
        )
        val tabs = TabbedDisplay(task)
        tabs["Result"] = "```text\n$resultValue\n```".renderMarkdown()
        tabs["Output"] = "```text\n$resultOutput\n```".renderMarkdown()
        return when {
            resultValue.isBlank() || resultValue.trim().lowercase() == "null" -> "# Output\n```text\n$resultOutput\n```"
            else -> "# Result\n```\n$resultValue\n```\n\n# Output\n```text\n$resultOutput\n```"
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(CodeAgent::class.java)
    }
}