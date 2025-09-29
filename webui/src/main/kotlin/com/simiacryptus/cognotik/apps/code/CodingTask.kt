package com.simiacryptus.cognotik.apps.code

import com.simiacryptus.cognotik.actors.CodeAgent
import com.simiacryptus.cognotik.actors.CodeAgent.CodeResult
import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface.OperationType
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

open class CodingTask<T : CodeRuntime>(
    val dataStorage: StorageInterface,
    val session: Session,
    val user: User?,
    val ui: SocketManager,
    val interpreter: KClass<T>,
    val symbols: Map<String, Any>,
    val temperature: Double = 0.1,
    val details: String? = null,
    val model: ChatInterface,
    private val mainTask: SessionTask,
    val retryable: Boolean = true,
) {

    open val codeAgent by lazy {
        CodeAgent(
            interpreter,
            symbols = symbols,
            temperature = temperature,
            details = details,
            model = model,
            fallbackModel = model
        )
    }

    open val canPlay by lazy {
        ApplicationServices.authorizationManager.isAuthorized(
            this::class.java, user, OperationType.Execute
        )
    }

    fun start(
        codeRequest: CodeAgent.CodeRequest,
        task: SessionTask = mainTask,
    ) {
        val task = ui.newTask(root = false).apply { task.complete(placeholder) }
        if (retryable) {
            Retryable(task) {
                val task = ui.newTask(root = false)
                ui.scheduledThreadPoolExecutor.schedule({
                    ui.pool.submit {
                        try {
                            val statusSB = task.add("Running...")
                            displayCode(task, codeRequest)
                            statusSB?.clear()
                        } catch (e: Throwable) {
                            log.warn("Error", e)
                            task.error(e)
                        } finally {
                            task.complete()
                        }
                    }
                }, 100, TimeUnit.MILLISECONDS)
                task.placeholder
            }
        } else {
            try {
                val statusSB = task.add("Running...")
                displayCode(task, codeRequest)
                statusSB?.clear()
            } catch (e: Throwable) {
                log.warn("Error", e)
                task.error(e)
            } finally {
                task.complete()
            }
        }
    }

    open fun codeRequest(messages: List<Pair<String, ModelSchema.Role>>) = CodeAgent.CodeRequest(messages)

    fun displayCode(
        task: SessionTask,
        codeRequest: CodeAgent.CodeRequest,
    ) {
        try {
            val lastUserMessage = codeRequest.messages.last { it.second == ModelSchema.Role.user }.first.trim()
            val codeResponse: CodeResult = if (lastUserMessage.startsWith("```")) {
                codeAgent.CodeResultImpl(
                    messages = codeAgent.chatMessages(codeRequest),
                    input = codeRequest,
                    givenCode = lastUserMessage.removePrefix("```").removeSuffix("```")
                )
            } else {
                codeAgent.answer(codeRequest)
            }
            displayCodeAndFeedback(task, codeRequest, codeResponse)
        } catch (e: Throwable) {
            log.warn("Error", e)
        }
    }

    protected fun displayCodeAndFeedback(
        task: SessionTask,
        codeRequest: CodeAgent.CodeRequest,
        response: CodeResult,
    ) {
        try {
            displayCode(task, response)
            displayFeedback(task, append(codeRequest, response), response)
        } catch (e: Throwable) {
            task.error(e)
            log.warn("Error", e)
        }
    }

    fun append(
        codeRequest: CodeAgent.CodeRequest, response: CodeResult
    ) = codeRequest(
        messages = codeRequest.messages + listOf(
            response.code to ModelSchema.Role.assistant,
        ).filter { it.first.isNotBlank() })

    fun displayCode(
        task: SessionTask, response: CodeResult
    ) {
        val string = response.renderedResponse
            ?: "\n```${codeAgent.language.lowercase(Locale.getDefault())}\n${response.code.trim()}\n```\n"
        task.expanded("Code", string.renderMarkdown)
    }

    open fun displayFeedback(
        task: SessionTask, request: CodeAgent.CodeRequest, response: CodeResult
    ) {
        val formText = StringBuilder()
        var formHandle: StringBuilder? = null
        formHandle = task.add(
            "<div>\n${
                if (!canPlay) "" else playButton(
                    task, request, response, formText
                ) { formHandle!! }
            }\n</div>\n${
                ui.textInput { feedback ->
                    responseAction(task, "Revising...", formHandle!!, formText) {
                        feedback(task, feedback, request, response)
                    }
                }
            }",
            additionalClasses = "reply-message"
        )
        formText.append(formHandle.toString())
        formHandle.toString()
        task.complete()
    }

    protected fun playButton(
        task: SessionTask,
        request: CodeAgent.CodeRequest,
        response: CodeResult,
        formText: StringBuilder,
        formHandle: () -> StringBuilder
    ) = if (!canPlay) "" else ui.hrefLink("▶ Run", "href-link play-button") {
        responseAction(task, "Running...", formHandle(), formText) {
            execute(task, response, request)
        }
    }.replace("<a class", """<a style="font-size: xxx-large;" class""")

    protected open fun responseAction(
        task: SessionTask, message: String, formHandle: StringBuilder?, formText: StringBuilder, fn: () -> Unit = {}
    ) {
        formHandle?.clear()
        val header = task.header(message, 2)
        try {
            fn()
        } finally {
            header?.clear()
            val revertButton: StringBuilder? = null
            task.complete(ui.hrefLink("↩", "href-link regen-button") {
                revertButton?.clear()
                formHandle?.append(formText)
                task.complete()
            })
        }
    }

    protected open fun feedback(
        task: SessionTask, feedback: String, request: CodeAgent.CodeRequest, response: CodeResult
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
        response: CodeResult,
        request: CodeAgent.CodeRequest,
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
            handleExecutionError(e, task, request, response)
        }
    }

    protected open fun handleExecutionError(
        e: Throwable, task: SessionTask, request: CodeAgent.CodeRequest, response: CodeResult
    ) {
        val message = when {
            e is ValidatedObject.ValidationError -> e.message ?: "".renderMarkdown
            e is FailedToImplementException -> "**Failed to Implement** \n\n${e.message}\n\n".renderMarkdown
            else -> "**Error `${e.javaClass.name}`**\n\n```text\n${e.stackTraceToString()}\n```\n".renderMarkdown
        }
        task.add(message, true, "div", "error")
        displayCode(
            task, CodeAgent.CodeRequest(
                messages = request.messages + listOf(
                    response.code to ModelSchema.Role.assistant,
                    message to ModelSchema.Role.system,
                ).filter { it.first.isNotBlank() })
        )
    }

    protected open fun execute(
        task: SessionTask, response: CodeResult
    ): String {
        val resultValue = response.result.resultValue
        val resultOutput = response.result.resultOutput
        val tabs = TabbedDisplay(task)
        tabs["Result"] = "```text\n$resultValue\n```".renderMarkdown()
        tabs["Output"] = "```text\n$resultOutput\n```".renderMarkdown()
        task.update()
        return when {
            resultValue.isBlank() || resultValue.trim().lowercase() == "null" -> "# Output\n```text\n$resultOutput\n```"
            else -> "# Result\n```\n$resultValue\n```\n\n# Output\n```text\n$resultOutput\n```"
        }

    }

    companion object {
        private val log = LoggerFactory.getLogger(CodeAgent::class.java)
    }
}
