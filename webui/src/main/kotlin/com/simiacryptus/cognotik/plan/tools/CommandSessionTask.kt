package com.simiacryptus.cognotik.plan.tools

import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import com.simiacryptus.jopenai.ChatClient
import com.simiacryptus.jopenai.OpenAIClient
import com.simiacryptus.jopenai.describe.Description
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CommandSessionTask(
    planSettings: PlanSettings,
    planTask: CommandSessionTaskConfigData?
) : AbstractTask<CommandSessionTask.CommandSessionTaskConfigData>(planSettings, planTask) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandSessionTask::class.java)
        private val activeSessions = ConcurrentHashMap<String, Process>()
        private const val TIMEOUT_MS = 30000L // 30 second timeout
        private const val MAX_SESSIONS = 10 // Maximum number of concurrent sessions

        private fun cleanupInactiveSessions() {
            activeSessions.entries.removeIf { (id, process) ->
                try {
                    if (!process.isAlive) {
                        log.info("Removing inactive session $id")
                        true
                    } else false
                } catch (e: Exception) {
                    log.warn("Error checking session $id, removing", e)
                    process.destroyForcibly()
                    true
                }
            }
        }

    }

    class CommandSessionTaskConfigData(
        @Description("The command to start the interactive session")
        val command: List<String>,
        @Description("Commands to send to the interactive session")
        val inputs: List<String> = listOf(),
        @Description("Session ID for reusing existing sessions")
        val sessionId: String? = null,
        @Description("Timeout in milliseconds for commands")
        val timeout: Long = TIMEOUT_MS,
        @Description("Whether to close the session after execution")
        val closeSession: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskConfigBase(
        task_type = TaskType.CommandSessionTask.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        val activeSessionsInfo = activeSessions.keys.joinToString("\n") { id ->
            "  ** Session $id"
        }
        return """
           CommandSessionTask - Create and manage a stateful interactive command session
           ** Specify the command to start an interactive session
           ** Provide inputs to send to the session
           ** Session persists between commands for stateful interactions
           ** Optionally specify sessionId to reuse an existing session
           ** Set closeSession=true to close the session after execution
           Active Sessions:
           """.trimIndent() + "\n" + activeSessionsInfo
    }

    override fun run(
        agent: PlanCoordinator,
        messages: List<String>,
        task: SessionTask,
        api: ChatClient,
        resultFn: (String) -> Unit,
        api2: OpenAIClient,
        planSettings: PlanSettings
    ) {
        requireNotNull(taskConfig) { "CommandSessionTaskData is required" }
        var process: Process? = null
        try {
            cleanupInactiveSessions()
            if (activeSessions.size >= MAX_SESSIONS && taskConfig.sessionId == null) {
                throw IllegalStateException("Maximum number of concurrent sessions ($MAX_SESSIONS) reached")
            }

            process = taskConfig.sessionId?.let { id -> activeSessions[id] }
                ?: ProcessBuilder(taskConfig.command)
                    .redirectErrorStream(true)
                    .start()
                    .also { newProcess ->
                        log.info("Started new process for command: ${taskConfig.command.joinToString(" ")}")
                        taskConfig.sessionId?.let { id -> activeSessions[id] = newProcess }
                    }

            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            val writer = PrintWriter(process.outputStream, true)

            val results = taskConfig.inputs.map { input ->
                try {
                    writer.println(input)
                    writer.flush()
                    val output = StringBuilder()
                    val endTime = System.currentTimeMillis() + taskConfig.timeout
                    while (System.currentTimeMillis() < endTime) {
                        if (reader.ready()) {
                            val line = reader.readLine()
                            if (line != null) output.append(line).append("\n")
                        } else {
                            Thread.sleep(100)
                        }
                    }
                    output.toString()
                } catch (e: Exception) {
                    log.error("Error executing command: $input", e)
                    "Error: ${e.message}"
                }
            }

            val result = formatResults(taskConfig, results)
            task.add(result)
            resultFn(result)

        } finally {
            if ((taskConfig.sessionId == null || taskConfig.closeSession) && process != null) {
                try {
                    process.destroy()
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                    if (taskConfig.sessionId != null) {
                        activeSessions.remove(taskConfig.sessionId)
                    }
                } catch (e: Exception) {
                    log.error("Error closing process", e)
                }
            }
        }
    }

    private fun formatResults(
        planTask: CommandSessionTaskConfigData,
        results: List<String>
    ): String = buildString {
        appendLine("## Command Session Results")
        appendLine("Command: ${planTask.command.joinToString(" ")}")
        appendLine("Session ID: ${planTask.sessionId ?: "temporary"}")
        appendLine("Timeout: ${planTask.timeout}ms")
        appendLine("\nCommand Results:")
        results.forEachIndexed { index, result ->
            appendLine("### Input ${index + 1}")
            appendLine("```")
            appendLine(planTask.inputs[index])
            appendLine("```")
            appendLine("Output:")
            appendLine("```")
            appendLine(result.take(5000)) // Limit result size
            appendLine("```")
        }
    }
}