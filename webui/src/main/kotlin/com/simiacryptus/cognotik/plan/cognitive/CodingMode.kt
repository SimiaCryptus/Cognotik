package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.MethodTypeDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.Discussable
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.util.jsonCast
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

open class CodingMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User
) : CognitiveMode<CodingMode.CodingModeConfig>(orchestrationConfig, session, user) {

    class CodingModeConfig(
        var codeRuntime: CodeRuntimes = CodeRuntimes.GroovyRuntime
    ) : CognitiveModeConfig(type = CognitiveModeType.Coding)

    protected val history = mutableListOf<Pair<String, ModelSchema.Role>>()

    inner class TaskFunctionImpl<T : TaskExecutionConfig, U : TaskTypeConfig>(
      private val taskType: TaskType<*, *>?,
      private val task: SessionTask
    ) : TaskFunction<T>(
        executionConfigClass = taskType?.executionConfigClass as Class<out T>
    ) {
        override fun call(executionConfig: Any, message: String): String {
            var result = ""
            val onComplete = Semaphore(0)
            val resultFn: (String) -> Unit = {
                result = it
                onComplete.release()
            }
            try {
            try {
                orchestrationConfig.getImpl(taskType as TaskType<T, U>, (executionConfig.jsonCast<Map<String,Any>>()+mapOf(
                    "task_type" to taskType.name
                )).jsonCast()).run(
                    agent = TaskOrchestrator(
                      user = user,
                      session = session,
                      dataStorage = task.ui.dataStorage,
                      root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                          ?: task.ui.dataStorage.getSessionDir(user, session).toPath()
                          ?: File(".").toPath()
                    ),
                    messages = listOf(message),
                    task = task,
                    resultFn = resultFn,
                    orchestrationConfig = orchestrationConfig
                )
            } catch (e: Throwable) {
                result = "Error initiating task: ${e.message}"
                onComplete.release()
            }
                if (!onComplete.tryAcquire(1, TimeUnit.HOURS)) {
                    throw RuntimeException("Task execution timed out")
                }
            } catch (e: Exception) {
                throw RuntimeException("Failed to execute task", e)
            }
            return result
        }
    }

    abstract class TaskFunction<T : TaskExecutionConfig>(
        val executionConfigClass: Class<out T>,
    ) : MethodTypeDescriber {
        override fun getMethodTypes(methodName: String): List<Type> {
            return if (methodName == "call") listOf(executionConfigClass) else emptyList()
        }
        abstract fun call(executionConfig: Any, messages: String): String
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
        val transcript = task.transcript()
        try {
            transcript?.write("User: $userMessage\n".toByteArray())
            history.add(userMessage to ModelSchema.Role.user)
            val response = if (orchestrationConfig.autoFix) {
                plan(task)
            } else {
                val baseHistory = history.dropLast(1)
                Discussable(
                    task = task,
                    heading = "Code Plan",
                    userMessage = { userMessage },
                    initialResponse = { _ -> plan(task) },
                    outputFn = { result ->
                        ("```" + config.codeRuntime.name.lowercase()
                            .replace("runtime", "") + "\n" + result.code + "\n```").renderMarkdown()
                    },
                    reviseResponse = { discussionHistory ->
                        generateCode(task, baseHistory + discussionHistory)
                    }
                ).call() ?: throw IllegalStateException("Discussion failed to produce a result")
            }
            val tabs = TabbedDisplay(task)
            tabs["Code"] = ("```" + config.codeRuntime.name.lowercase().replace("runtime", "") + "\n" + response.code + "\n```").renderMarkdown()
            transcript?.write("Code:\n${response.code}\n".toByteArray())
            task.resolveUserFile(".logs/code_${now()}.${config.codeRuntime.extension}")?.writeBytes(response.code.toByteArray())
            val executionResult = response.result // execute code
            output(executionResult, tabs, transcript, response)
        } catch (e: Throwable) {
            log.error("Error during code execution", e)
            task.error(e)
            history.add("Error: ${e.message}" to ModelSchema.Role.system)
            transcript?.write("Error: ${e.message}\n".toByteArray())
        }
    }

    private fun now(): String {
        val fmt = java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS")
        return fmt.format(java.util.Date())
    }

    open fun output(
        executionResult: CodeAgent.ExecutionResult,
        tabs: TabbedDisplay,
        transcript: FileOutputStream?,
        response: CodeAgent.CodeResult
    ) {
        val output = executionResult.resultOutput
        val value = executionResult.resultValue
        if (output.isNotBlank()) {
            tabs["Output"] = "```text\n$output\n```".renderMarkdown()
        }
        if (value.isNotBlank() && value != "null") {
            tabs["Result"] = "```text\n$value\n```".renderMarkdown()
        }
        transcript?.write("Output:\n$output\nValue:\n$value\n".toByteArray())

        history.add(response.code to ModelSchema.Role.assistant)
        val resultMsg = listOfNotNull(
            if (output.isNotBlank()) "Output:\n$output" else null,
            if (value.isNotBlank() && value != "null") "Result:\n$value" else null
        ).joinToString("\n")
        if (resultMsg.isNotBlank()) {
            history.add(resultMsg to ModelSchema.Role.system)
        }
    }

    open fun plan(task: SessionTask) = generateCode(task, history)

    private fun generateCode(
        task: SessionTask,
        messages: List<Pair<String, ModelSchema.Role>>
    ): CodeAgent.CodeResult {
        val config = config ?: throw IllegalStateException("CognitiveModeConfig is null")
        return symbols(task).let { symbols ->
            CodeAgent(
                codeRuntime = CodeRuntimes.getRuntime(config.codeRuntime, symbols),
                model = orchestrationConfig.defaultSmart.getChildClient(task),
                details = "You are in an interactive coding session. Execute code to answer the user.",
                temperature = orchestrationConfig.temperature,
                symbols = symbols,
                describer = describer,
            ).respond(
                CodeAgent.CodeRequest(
                    messages = messages
                )
            )
        }

    }

    open val describer: TypeDescriber = AbbrevWhitelistYamlDescriber("com.simiacryptus")

    open fun symbols(task: SessionTask): Map<String, Any> =
        orchestrationConfig.taskSettings.map { (name, taskTypeConfig) ->
            Pair(
                name.replace("[^a-zA-Z01-9_]".toRegex(), "_"),
                TaskFunctionImpl<com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig, TaskTypeConfig>(taskTypeConfig.task_type?.let {
                    TaskType.valueOf(
                        it
                    )
                }, task)
            )
        }.toMap() + mapOf(
            "workingDir" to (orchestrationConfig.absoluteWorkingDir?.let { File(it).absoluteFile } ?: "."),
            "smartModel" to orchestrationConfig.defaultSmart.getChildClient(task),
            "fastModel" to orchestrationConfig.defaultFast.getChildClient(task),
            "task" to task,
        )

    override fun contextData(): List<String> = emptyList()

    companion object {
        private val log = com.simiacryptus.cognotik.util.LoggerFactory.getLogger(CodingMode::class.java)
    }
}