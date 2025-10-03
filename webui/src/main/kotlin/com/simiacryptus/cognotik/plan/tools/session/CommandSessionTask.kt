package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class CommandSessionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: CommandSessionTaskExecutionConfigData?
) : AbstractTask<CommandSessionTask.CommandSessionTaskExecutionConfigData, TaskTypeConfig>(orchestrationConfig, planTask) {
    companion object {
        private val log = LoggerFactory.getLogger(CommandSessionTask::class.java)
        private val activeSessions = ConcurrentHashMap<String, Process>()
        private const val TIMEOUT_MS = 30000L

        private const val MAX_SESSIONS = 10


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

    class CommandSessionTaskExecutionConfigData(
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
    ) : TaskExecutionConfig(
        task_type = TaskType.CommandSession.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        val activeSessionsInfo = activeSessions.keys.joinToString("\n") { id ->
            "  ** Session $id"
        }
        return """
           CommandSession - Create and manage a stateful interactive command session
           ** Specify the command to start an interactive session
           ** Provide inputs to send to the session
           ** Session persists between commands for stateful interactions
           ** Optionally specify sessionId to reuse an existing session
           ** Set closeSession=true to close the session after execution
           Active Sessions:
           """.trimIndent() + "\n" + activeSessionsInfo
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        requireNotNull(executionConfig) { "CommandSessionTaskData is required" }
        var process: Process? = null
        try {
            cleanupInactiveSessions()
            if (activeSessions.size >= MAX_SESSIONS && executionConfig.sessionId == null) {
                throw IllegalStateException("Maximum number of concurrent sessions ($MAX_SESSIONS) reached")
            }

            process = executionConfig.sessionId?.let { id -> activeSessions[id] }
                ?: ProcessBuilder(executionConfig.command)
                    .redirectErrorStream(true)
                    .start()
                    .also { newProcess ->
                        log.info("Started new process for command: ${executionConfig.command.joinToString(" ")}")
                        executionConfig.sessionId?.let { id -> activeSessions[id] = newProcess }
                    }

            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            val writer = PrintWriter(process.outputStream, true)

            val results = executionConfig.inputs.map { input ->
                try {
                    writer.println(input)
                    writer.flush()
                    val output = StringBuilder()
                    val endTime = System.currentTimeMillis() + executionConfig.timeout
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

            val result = formatResults(executionConfig, results)
            task.add(result)
            resultFn(result)

        } finally {
            if ((executionConfig.sessionId == null || executionConfig.closeSession) && process != null) {
                try {
                    process.destroy()
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                    if (executionConfig.sessionId != null) {
                        activeSessions.remove(executionConfig.sessionId)
                    }
                } catch (e: Exception) {
                    log.error("Error closing process", e)
                }
            }
        }
    }

    private fun formatResults(
        planTask: CommandSessionTaskExecutionConfigData,
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
            appendLine(result.take(5000))

            appendLine("```")
        }
    }
}