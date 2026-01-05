package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.apps.renderMarkdown
import com.simiacryptus.cognotik.describe.AbbrevWhitelistYamlDescriber
import com.simiacryptus.cognotik.describe.MethodTypeDescriber
import com.simiacryptus.cognotik.describe.TypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.cognotik.webui.session.getChildClient
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.concurrent.Semaphore

class CodingModeConfig(
    var codeRuntime: CodeRuntimes = CodeRuntimes.GroovyRuntime
) : CognitiveModeConfig(type = CognitiveModeType.Coding)

open class CodingMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User
) : CognitiveMode<CodingModeConfig>(orchestrationConfig, session, user) {

    protected val history = mutableListOf<Pair<String, ModelSchema.Role>>()

    inner class TaskFunctionImpl<T : TaskExecutionConfig, U : TaskTypeConfig>(
        private val taskType: TaskType<*, *>?,
        private val task: SessionTask
    ) : TaskFunction<T>(
        executionConfigClass = taskType?.executionConfigClass as Class<out T>
    ) {
        override fun call(executionConfig: T, vararg messages: String): String {
            var result = ""
            val onComplete = Semaphore(0)
            val resultFn: (String) -> Unit = {
                result = it
                onComplete.release()
            }
            orchestrationConfig.getImpl(taskType as TaskType<T, U>, executionConfig).run(
                agent = TaskOrchestrator(
                    user = user,
                    session = session,
                    dataStorage = task.ui.dataStorage,
                    root = orchestrationConfig.absoluteWorkingDir?.let { File(it).toPath() }
                        ?: task.ui.dataStorage.getSessionDir(user, session).toPath()
                        ?: File(".").toPath()
                ),
                messages = messages.toList(),
                task = task,
                resultFn = resultFn,
                orchestrationConfig = orchestrationConfig
            )
            onComplete.acquire()
            return result
        }
    }

    abstract class TaskFunction<T : TaskExecutionConfig>(
        val executionConfigClass: Class<out T>,
    ) : MethodTypeDescriber {
        override fun getMethodTypes(methodName: String): List<Type> {
            return if (methodName == "call") listOf(executionConfigClass) else emptyList()
        }
        abstract fun call(executionConfig: T, vararg messages: String): String
    }

    override fun handleUserMessage(userMessage: String, task: SessionTask) {
        val transcript = task.transcript()
        try {
            transcript?.write("User: $userMessage\n".toByteArray())
            history.add(userMessage to ModelSchema.Role.user)
            val response = plan(task)
            val tabs = TabbedDisplay(task)
            tabs["Code"] = ("```" + config.codeRuntime.name.lowercase().replace("runtime", "") + "\n" + response.code + "\n```").renderMarkdown()
            transcript?.write("Code:\n${response.code}\n".toByteArray())
            val executionResult = response.result // execute code
            output(executionResult, tabs, transcript, response)
        } catch (e: Throwable) {
            task.error(e)
            history.add("Error: ${e.message}" to ModelSchema.Role.system)
            transcript?.write("Error: ${e.message}\n".toByteArray())
        }
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

    open fun plan(task: SessionTask) = symbols(task).let { symbols ->
        CodeAgent(
            codeRuntime = CodeRuntimes.getRuntime(config.codeRuntime, symbols),
            model = orchestrationConfig.defaultSmart.getChildClient(task),
            details = "You are in an interactive coding session. Execute code to answer the user.",
            temperature = orchestrationConfig.temperature,
            symbols = symbols,
            describer = describer,
        ).respond(
            CodeAgent.CodeRequest(
                messages = history
            )
        )

    }

    open val describer: TypeDescriber
        get() {
//        return AbbrevWhitelistTSDescriber("com.simiacryptus")
            return AbbrevWhitelistYamlDescriber("com.simiacryptus")
        }

    open fun symbols(task: SessionTask): Map<String, Any> =
        orchestrationConfig.taskSettings.map { (name, taskTypeConfig) ->
            Pair(
                name.replace("[^a-zA-Z01-9_]".toRegex(), "_"),
                TaskFunctionImpl<TaskExecutionConfig, TaskTypeConfig>(taskTypeConfig.task_type?.let {
                    TaskType.valueOf(
                        it
                    )
                }, task)
            )
        }.toMap() + mapOf(
            "env" to (orchestrationConfig.env ?: emptyMap()),
            "workingDir" to (orchestrationConfig.absoluteWorkingDir?.let { File(it).absolutePath } ?: ".")
        )

    override fun contextData(): List<String> = emptyList()
}