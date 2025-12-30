package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.apps.general.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.BufferedReader
import java.io.OutputStream
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
    class SessionState(val process: Process, var transcript: OutputStream? = null) {
        val outputBuffer = StringBuffer()
        val monitorThread = Thread {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val buffer = CharArray(1024)
                while (!Thread.currentThread().isInterrupted && process.isAlive) {
                    if (reader.ready()) {
                        val read = reader.read(buffer)
                        if (read > 0) {
                            val text = String(buffer, 0, read)
                            synchronized(outputBuffer) {
                                outputBuffer.append(buffer, 0, read)
                            }
                            try {
                                transcript?.write(text.toByteArray())
                                transcript?.flush()
                            } catch (e: Exception) {
                                // Ignore
                            }
                        }
                    } else {
                        Thread.sleep(100)
                    }
                }
            } catch (e: InterruptedException) {
                // Expected
            } catch (e: Exception) {
                log.warn("Error monitoring session output", e)
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

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
        private val activeSessions = ConcurrentHashMap<String, SessionState>()
        private const val TIMEOUT_MS = 30000L

        private const val MAX_SESSIONS = 10


        private fun cleanupInactiveSessions() {
            activeSessions.entries.removeIf { (id, state) ->
                try {
                    if (!state.process.isAlive) {
                        log.info("Removing inactive session $id")
                        state.monitorThread.interrupt()
                        true
                    } else false
                } catch (e: Exception) {
                    log.warn("Error checking session $id, removing", e)
                    state.process.destroyForcibly()
                    state.monitorThread.interrupt()
                    true
                }
            }
        }

    }

    class CommandSessionTaskExecutionConfigData(
        @Description("The command to start the interactive session") val command: List<String> = listOf("bash", "-i"),
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
        val activeSessionsInfo = activeSessions.entries.joinToString("\n") { (id, state) ->
            val pendingBytes = state.outputBuffer.length
            val alive = state.process.isAlive
            "  ** Session $id ($pendingBytes bytes pending output, alive=$alive)"
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
        executionConfig ?: throw IllegalStateException("Execution config is null")
        task.ui.pool.submit {
            val initialText = buildString {
                appendLine("## Command Session Results")
                appendLine("Command: `${executionConfig.command.joinToString(" ")}`")
                appendLine("Session ID: `${executionConfig.sessionId ?: "new"}`")
                appendLine("Timeout per command: ${executionConfig.timeout}ms")
            }
            val uiOutput = task.add(initialText.renderMarkdown())!!
            val resultBuffer = StringBuilder(initialText)
            task.update()
            val transcript = task.transcript()
            var sessionState: SessionState? = null
            try {
                cleanupInactiveSessions()
                if (activeSessions.size >= MAX_SESSIONS && executionConfig.sessionId == null) {
                    throw IllegalStateException("Maximum number of concurrent sessions ($MAX_SESSIONS) reached")
                }


                sessionState = executionConfig.sessionId?.let { id -> activeSessions[id] }
                    ?: run {
                        val process = ProcessBuilder(executionConfig!!.command).redirectErrorStream(true).start()
                        log.info("Started new process for command: ${executionConfig.command.joinToString(" ")}")
                        val state = SessionState(process, transcript)
                        executionConfig.sessionId?.let { id -> activeSessions[id] = state }
                        state
                    }
                sessionState?.transcript = transcript

                val writer = PrintWriter(sessionState.process.outputStream, true)

                executionConfig.inputs.forEachIndexed { index, input ->
                    val text = buildString {
                        appendLine("\n### Input ${index + 1}")
                        appendLine("```")
                        appendLine(input)
                        appendLine("```")
                    }
                    transcript?.write(text.toByteArray())
                    uiOutput.appendLine(text.renderMarkdown())
                    resultBuffer.appendLine(text)
                    task.update()
                    val output = try {
                        writer.println(input)
                        writer.flush()
                        read(sessionState, executionConfig, 5000000L)
                    } catch (e: Exception) {
                        log.error("Error executing command: $input", e)
                        "Error: ${e.message}"
                    } finally {
                        log.info("Completed input: $input")
                    }
                    val value = buildString {
                        appendLine("Output:")
                        appendLine("```")
                        appendLine(output.take(10000))
                        appendLine("```")
                    }
                    transcript?.write(value.toByteArray())
                    uiOutput.appendLine(value.renderMarkdown())
                    resultBuffer.appendLine(value)
                    task.update()
                }
                task.complete("Command session finished successfully.")
                resultFn(resultBuffer.toString())
            } catch (e: Exception) {
                val errorResult = "Error in CommandSessionTask: ${e.message}"
                resultBuffer.appendLine(errorResult)
                resultFn(resultBuffer.toString())
                log.error("Error in CommandSessionTask", e)
                task.error(e)
            } finally {
                sessionState?.transcript = null
                transcript?.close()
                if ((executionConfig.sessionId == null || executionConfig.closeSession) && sessionState != null) {
                    try {
                        sessionState.monitorThread.interrupt()
                        val process = sessionState.process
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
    }

    private fun read(
        sessionState: SessionState,
        executionConfig: CommandSessionTaskExecutionConfigData,
        idleTimeout: Long
    ): String {
        val outputBuffer = StringBuilder()
        val endTime = System.currentTimeMillis() + executionConfig.timeout
        var lastReadTime = System.currentTimeMillis()
        while (System.currentTimeMillis() < endTime && (System.currentTimeMillis() - lastReadTime) < idleTimeout) {
            var appended = false
            synchronized(sessionState.outputBuffer) {
                if (sessionState.outputBuffer.isNotEmpty()) {
                    outputBuffer.append(sessionState.outputBuffer)
                    sessionState.outputBuffer.setLength(0)
                    appended = true
                }
            }
            if (appended) {
                lastReadTime = System.currentTimeMillis()
            } else {
                Thread.sleep(100)
            }
        }
        return outputBuffer.toString()
    }

}