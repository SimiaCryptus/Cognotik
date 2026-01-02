package com.simiacryptus.cognotik.plan.tools.run

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.apps.renderMarkdown
import com.simiacryptus.cognotik.chat.model.ChatInterface
import com.simiacryptus.cognotik.interpreter.CodeRuntime
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.transcript
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.AuthorizationInterface
import com.simiacryptus.cognotik.platform.model.StorageInterface
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.util.Retryable
import com.simiacryptus.cognotik.util.Retryable.Companion.async
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.SocketManager
import java.util.*
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
    val autoFix: Boolean = false,
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
            this::class.java, user, AuthorizationInterface.OperationType.Execute
        )
    }

    fun start(
        codeRequest: CodeAgent.CodeRequest,
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

    open fun codeRequest(messages: List<Pair<String, ModelSchema.Role>>) = CodeAgent.CodeRequest(messages)

    fun displayCode(
        task: SessionTask,
        codeRequest: CodeAgent.CodeRequest,
    ) {
        try {
            val lastUserMessage = codeRequest.messages.last { it.second == ModelSchema.Role.user }.first.trim()
            val codeResponse: CodeAgent.CodeResult = if (lastUserMessage.startsWith("```")) {
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
        response: CodeAgent.CodeResult,
    ) {
        try {
            displayCode(task, response)
            if (autoFix && canPlay) {
                execute(task, response, codeRequest)
            } else {
                displayFeedback(task, append(codeRequest, response), response)
            }
        } catch (e: Throwable) {
            task.error(e)
            log.warn("Error", e)
        }
    }

    fun append(
        codeRequest: CodeAgent.CodeRequest, response: CodeAgent.CodeResult
    ) = codeRequest(
        messages = codeRequest.messages + listOf(
            response.code to ModelSchema.Role.assistant,
        ).filter { it.first.isNotBlank() })

    fun displayCode(
        task: SessionTask, response: CodeAgent.CodeResult
    ) {
        val string = response.renderedResponse
            ?: "\n```${codeAgent.language.lowercase(Locale.getDefault())}\n${response.code.trim()}\n```\n"
        task.expanded("Code", string.renderMarkdown)
        task.transcript()?.write("# Generated Code\n$string\n".toByteArray())
    }

    open fun displayFeedback(
        task: SessionTask, request: CodeAgent.CodeRequest, response: CodeAgent.CodeResult
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
        request: CodeAgent.CodeRequest,
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
        task: SessionTask, feedback: String, request: CodeAgent.CodeRequest, response: CodeAgent.CodeResult
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
        e: Throwable, task: SessionTask, request: CodeAgent.CodeRequest, response: CodeAgent.CodeResult
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