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
    orchestrationConfig: OrchestrationConfig, planTask: CommandSessionTaskExecutionConfigData?
) : AbstractTask<CommandSessionTask.CommandSessionTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        val CommandSession = TaskType(
            "CommandSession",
            "Session",
            CommandSessionTaskExecutionConfigData::class.java,
            TaskTypeConfig::class.java,
            "Execute commands in a stateful, interactive session",
            """
                Creates and manages a persistent command-line session (e.g., bash, python).
                This allows for stateful interactions where commands can build on the results of previous ones.
                <ul>
                    <li><b>Start any interactive process:</b> Specify the command to run (e.g., `listOf("bash", "-i")`).</li>
                    <li><b>Send inputs:</b> Provide a list of commands to be executed sequentially in the session.</li>
                    <li><b>Stateful Sessions:</b> Reuse sessions by providing a `sessionId`. The environment (variables, current directory) persists between tasks using the same ID.</li>
                    <li><b>Manage Session Lifecycle:</b> Sessions can be explicitly closed or will be cleaned up automatically.</li>
                </ul>
            """
        )
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
        @Description("The command to start the interactive session") val command: List<String>,
        @Description("Commands to send to the interactive session") val inputs: List<String> = listOf(),
        @Description("Session ID for reusing existing sessions") val sessionId: String? = null,
        @Description("Timeout in milliseconds for commands") val timeout: Long = TIMEOUT_MS,
        @Description("Whether to close the session after execution") val closeSession: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = CommandSession.name,
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
           
           System Information:
           - OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})
           - Working Directory: ${System.getProperty("user.dir")}

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
        val uiOutput = StringBuilder().apply {
            appendLine("## Command Session Results")
            appendLine("Command: `${executionConfig.command.joinToString(" ")}`")
            appendLine("Session ID: `${executionConfig.sessionId ?: "new"}`")
            appendLine("Timeout per command: ${executionConfig.timeout}ms")
        }
        task.add(uiOutput.toString())
        task.update()
        val transcript = task.transcript()
        var process: Process? = null
        try {
            cleanupInactiveSessions()
            if (activeSessions.size >= MAX_SESSIONS && executionConfig.sessionId == null) {
                throw IllegalStateException("Maximum number of concurrent sessions ($MAX_SESSIONS) reached")
            }

            process = executionConfig.sessionId?.let { id -> activeSessions[id] }
                ?: ProcessBuilder(executionConfig.command).redirectErrorStream(true).start()
                    .also { newProcess ->
                        log.info("Started new process for command: ${executionConfig.command.joinToString(" ")}")
                        executionConfig.sessionId?.let { id -> activeSessions[id] = newProcess }
                    }

            val reader = BufferedReader(InputStreamReader(process!!.inputStream))
            val writer = PrintWriter(process.outputStream, true)

            executionConfig.inputs.forEachIndexed { index, input ->
                uiOutput.appendLine("\n### Input ${index + 1}")
                uiOutput.appendLine("```")
                uiOutput.appendLine(input)
                uiOutput.appendLine("```")
                transcript?.write(uiOutput.toString().toByteArray())
                task.add(uiOutput.toString())
                task.update()
                val output = try {
                    writer.println(input)
                    writer.flush()
                    read(executionConfig, reader)
                } catch (e: Exception) {
                    log.error("Error executing command: $input", e)
                    "Error: ${e.message}"
                }
                uiOutput.appendLine("Output:")
                uiOutput.appendLine("```")
                uiOutput.appendLine(output.take(10000))
                uiOutput.appendLine("```")
                transcript?.write(uiOutput.toString().toByteArray())
                task.add(uiOutput.toString())
                task.update()
            }
            task.complete("Command session finished successfully.")
            resultFn(uiOutput.toString())
        } catch (e: Exception) {
            log.error("Error in CommandSessionTask", e)
            task.error(e)
            val errorResult = "Error in CommandSessionTask: ${e.message}"
            task.add(uiOutput.append("\n\n**ERROR:** $errorResult").toString())
            transcript?.write(uiOutput.toString().toByteArray())
            resultFn(errorResult)
        } finally {
            transcript?.close()
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

    private fun read(
        executionConfig: CommandSessionTaskExecutionConfigData,
        reader: BufferedReader
    ): String {
        val outputBuffer = StringBuilder()
        val endTime = System.currentTimeMillis() + executionConfig.timeout
        var lastReadTime = System.currentTimeMillis()
        val idleTimeout = 5000000L // Stop if no output for 500ms
        while (System.currentTimeMillis() < endTime && System.currentTimeMillis() - lastReadTime > idleTimeout) {
            if (reader.ready()) {
                val buffer = CharArray(4096)
                val charsRead = reader.read(buffer)
                if (charsRead > 0) {
                    outputBuffer.append(buffer, 0, charsRead)
                    lastReadTime = System.currentTimeMillis()
                } else {
                    Thread.sleep(100)
                }
            } else {
                Thread.sleep(100)
            }
        }
        return outputBuffer.toString()
    }

}