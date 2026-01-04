package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.agents.CodeAgent
import com.simiacryptus.cognotik.apps.renderMarkdown
import com.simiacryptus.cognotik.describe.MethodTypeDescriber
import com.simiacryptus.cognotik.interpreter.CodeRuntimes
import com.simiacryptus.cognotik.models.ModelSchema
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.TaskType.Companion.getImpl
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.TabbedDisplay
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Type
import java.util.concurrent.Semaphore

class CodingModeConfig(
    var codeRuntime: CodeRuntimes = CodeRuntimes.GroovyRuntime
) : CognitiveModeConfig(type = CognitiveModeType.Coding)

class CodingMode(
    orchestrationConfig: OrchestrationConfig,
    session: Session,
    user: User
) : CognitiveMode<CodingModeConfig>(orchestrationConfig, session, user) {

    private val history = mutableListOf<Pair<String, ModelSchema.Role>>()

    private fun <T : TaskExecutionConfig, U : TaskTypeConfig> TaskTypeConfig.toCallable(
        task: SessionTask
    ): TaskFunction<T> {
        val taskType: TaskType<*, *>? = task_type?.let { TaskType.valueOf(it) }
        val returnFunction = object : TaskFunction<T>(
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
        return returnFunction
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

    private fun output(
        executionResult: CodeAgent.ExecutionResult,
        tabs: TabbedDisplay,
        transcript: FileOutputStream?,
        response: CodeAgent.CodeResult
    ) {
        val output = executionResult.resultOutput
        val value = executionResult.resultValue
        if (output.isNotBlank()) {
            tabs.set("Output", "```text\n$output\n```".renderMarkdown())
        }
        if (value.isNotBlank() && value != "null") {
            tabs.set("Result", "```text\n$value\n```".renderMarkdown())
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

    private fun plan(task: SessionTask): CodeAgent.CodeResult {
        val symbols = orchestrationConfig.taskSettings.map { (name, taskTypeConfig) ->
            Pair(
                name.replace("[^a-zA-Z01-9_]".toRegex(), "_"),
                taskTypeConfig.toCallable<TaskExecutionConfig, TaskTypeConfig>(task)
            )
        }.toMap() + mapOf(
            "env" to (orchestrationConfig.env ?: emptyMap()),
            "workingDir" to (orchestrationConfig.absoluteWorkingDir?.let { File(it).absolutePath } ?: ".")
        )
        return CodeAgent(
            codeRuntime = CodeRuntimes.getRuntime(config.codeRuntime, symbols),
            model = orchestrationConfig.defaultSmart,
            fallbackModel = orchestrationConfig.defaultSmart,
            details = "You are in an interactive coding session. Execute code to answer the user.",
            temperature = orchestrationConfig.temperature,
            symbols = symbols
        ).respond(
            CodeAgent.CodeRequest(
                messages = history
            )
        )
    }

    override fun contextData(): List<String> = emptyList()
}