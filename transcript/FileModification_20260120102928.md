# File Modification Task Transcript


## Context Data
<details>
<summary>Input Files & Dependencies</summary>

### Dependencies
None

### File Context
# /home/andrew/code/Cognotik/docs/index_docs.md

```
---
transforms: ../(.+/src/main/kotlin/.+/)([^\./]+)\.kt -> ../$1/README.md
---


```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/session/CommandSessionTask.kt

```
package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.concurrent.ConcurrentHashMap

class CommandSessionTask(
    orchestrationConfig: OrchestrationConfig, planTask: CommandSessionTaskExecutionConfigData?
) : AbstractTask<CommandSessionTask.CommandSessionTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    class SessionState(val process: Process) {
        val outputBuffer = StringBuffer()
        val monitorThread = Thread {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val buffer = CharArray(1024)
                while (!Thread.currentThread().isInterrupted && process.isAlive) {
                    if (reader.ready()) {
                        val read = reader.read(buffer)
                        if (read > 0) {
                            synchronized(outputBuffer) {
                                outputBuffer.append(buffer, 0, read)
                            }
                        }
                    } else {
                        Thread.sleep(100)
                    }
                }
            } catch (_: InterruptedException) {
                // Expected
            } catch (e: Exception) {
                log.warn("Error monitoring session output", e)
            } finally {
                if (process.isAlive) {
                    process.destroyForcibly()
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        fun read(
            configData: CommandSessionTaskExecutionConfigData,
        ): String {
            val outputBuffer = StringBuilder()
            val endTime = System.currentTimeMillis() + configData.timeout
            var lastReadTime = System.currentTimeMillis()
            while (System.currentTimeMillis() < endTime && (System.currentTimeMillis() - lastReadTime) < configData.idle_timeout) {
                var appended = false
                synchronized(this.outputBuffer) {
                    if (this.outputBuffer.isNotEmpty()) {
                        outputBuffer.append(this.outputBuffer)
                        this.outputBuffer.setLength(0)
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

    private val activeSessions: ConcurrentHashMap<String, SessionState>
        get() = _activeSessions.getOrPut(orchestrationConfig.sessionId) { ConcurrentHashMap() }


    class CommandSessionTaskExecutionConfigData(
      @Description("The command to start the interactive session (e.g. ['bash', '-i'] or ['python3', '-i'])") var command: List<String> = listOf(
        "bash",
        "-i"
      ),
      @Description("List of strings to send to the session's standard input") var inputs: List<String> = listOf(),
      @Description("Optional ID to reuse an existing session across multiple tasks") var sessionId: String? = null,
      @Description("Maximum time in milliseconds to wait for all commands to complete") var timeout: Long = TIMEOUT_MS,
      @Description("Maximum time in milliseconds to wait for output after a command is sent") var idle_timeout: Long = 2000,
      @Description("Whether to allocate a pseudo-terminal (useful for interactive tools or colored output)") var tty: Boolean = false,
      @Description("A description of what this specific task instance is intended to achieve") task_description: String? = null,
      @Description("IDs of tasks that must complete before this one") task_dependencies: List<String>? = null,
      @Description("Internal state tracking for the task") state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = CommandSession.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        val executables: List<String>? =
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings()
                .tools.flatMap { it.component1()?.getExecutables() ?: emptyList() }.distinct().sorted()
        val activeSessionsInfo = activeSessions.entries.joinToString("\n") { (id, state) ->
            val pendingBytes = state.outputBuffer.length
            val alive = state.process.isAlive
            "  ** Session $id ($pendingBytes bytes pending output, alive=$alive)"
        }
        return """
           CommandSession - Create and manage a stateful interactive terminal session.
           - Use this for running shell commands, interactive scripts (Python, Node), or managing long-running processes.
           - Specify 'command' to start a new session (e.g., ["python3", "-i"]).
           - Provide 'inputs' as a list of strings to send to the session's stdin.
           - Use 'sessionId' to maintain state (variables, directory) across multiple task calls.
           
           System Information:
           - OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})
           - Working Directory: ${System.getProperty("user.dir")}
           - Available Tools: ${executables?.joinToString(", ") ?: "None"}

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
      val executionConfig = executionConfig ?: throw IllegalStateException("Execution config is null")


        val resultBuffer = StringBuffer()
      val executeAction: (Boolean) -> Unit = { shouldComplete ->
            task.ui.pool.submit {
                task.header("Command Session Results")
                val initialText = buildString {
                    appendLine("Command: `${executionConfig.command.joinToString(" ")}`")
                    appendLine("Session ID: `${executionConfig.sessionId ?: "new"}`")
                    appendLine("Timeout per command: ${executionConfig.timeout}ms")
                }
                task.add(initialText.renderMarkdown())
                resultBuffer.append("## Command Session Results\n$initialText")
                val transcript = task.transcript()
                var sessionState: SessionState? = null
                try {
                    cleanupInactiveSessions()
                    if (activeSessions.size >= MAX_SESSIONS && executionConfig.sessionId == null) {
                        throw IllegalStateException("Maximum number of concurrent sessions ($MAX_SESSIONS) reached")
                    }

                    sessionState = executionConfig.sessionId?.let { id -> activeSessions[id] } ?: run {
                      val command = executionConfig.command
                        val executable = command.firstOrNull()
                        val resolvedCommand = if (executable != null) {
                            val tools =
                                ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings().tools
                            val resolvedExecutable =
                                tools.find { it.provider?.getExecutables()?.contains(executable) == true }
                                    ?.resolve(executable)
                            if (resolvedExecutable != null) {
                                listOf(resolvedExecutable) + command.drop(1)
                            } else {
                                command
                            }
                        } else {
                            command
                        }
                        val process = if (executionConfig.tty) {
                            try {
                                com.pty4j.PtyProcessBuilder()
                                    .setCommand(resolvedCommand.toTypedArray())
                                    .setEnvironment(System.getenv())
                                    .setDirectory(task.resolveUserFile(".")?.absolutePath)
                                    .start()
                            } catch (e: Throwable) {
                                log.warn("Failed to start PTY process, falling back to ProcessBuilder", e)
                                ProcessBuilder(resolvedCommand).directory(task.resolveUserFile("."))
                                    .redirectErrorStream(true).start()
                            }
                        } else {
                            ProcessBuilder(resolvedCommand).directory(task.resolveUserFile("."))
                                .redirectErrorStream(true).start()
                        }

                        log.info("Started new process for command: ${resolvedCommand.joinToString(" ")}")
                        val state = SessionState(process)
                        executionConfig.sessionId?.let { id -> activeSessions[id] = state }
                        state
                    }

                    val writer = PrintWriter(sessionState.process.outputStream, true)

                    executionConfig.inputs.forEachIndexed { index, input ->
                        val inputHeader = "\n### Input ${index + 1}"
                        val inputBlock = "```\n$input\n```"
                      val inputText = "$inputHeader\n$inputBlock"
                      transcript?.write("## Executing Command\n$inputText\n".toByteArray())
                      task.add(inputText.renderMarkdown())
                      resultBuffer.appendLine(inputText)
                        val output = try {
                            writer.println(input)
                            writer.flush()
                            sessionState.read(executionConfig)
                        } catch (e: Exception) {
                            log.error("Error executing command: $input", e)
                            "Error: ${e.message}"
                        } finally {
                            log.info("Completed input: $input")
                        }
                        val outputContent = output.take(10000)
                        val outputBlock = "```\n$outputContent\n```"

                      val transcriptOutput = """
                            <details>
                            <summary>Output for Input ${index + 1}</summary>
                            
                            $outputBlock
                            </details>
                        """.trimIndent()
                      transcript?.write(transcriptOutput.toByteArray())

                      val uiOutput = "\n\nOutput:\n$outputBlock"
                      task.add(uiOutput.renderMarkdown())
                      resultBuffer.appendLine(uiOutput)
                    }
                    if (shouldComplete) {
                        task.complete("Command session finished successfully.")
                        resultFn(resultBuffer.toString())
                    } else {
                        task.add("Execution Complete.")
                    }
                } catch (e: Exception) {
                    val errorResult = "Error in CommandSessionTask: ${e.message}"
                    resultBuffer.appendLine(errorResult)
                    log.error("Error in CommandSessionTask", e)
                    task.error(e)
                  transcript?.write("\n## Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
                    if (shouldComplete) {
                        resultFn(resultBuffer.toString())
                    }
                } finally {
                  transcript?.close()
                }
            }
        }

        if (orchestrationConfig.autoFix) {
          executeAction(true)
        } else {
            task.header("Command Session Plan")
            val plan = buildString {
                appendLine("Command: `${executionConfig.command.joinToString(" ")}`")
                if (executionConfig.sessionId != null) appendLine("Session ID: `${executionConfig.sessionId}`")
                appendLine("Inputs:")
                executionConfig.inputs.forEach { appendLine("- `$it`") }
            }
            task.add(plan.renderMarkdown())

            task.add(task.ui.hrefLink("Run Commands", "btn btn-primary") {
              executeAction(false)
            })

            task.add(acceptButtonFooter(task.ui) {
                task.complete()
                resultFn(resultBuffer.toString())
            })
        }
    }

    companion object {
        @JvmStatic val CommandSession = TaskType(
          name = "CommandSession",
          category = "Session",
          taskClass = CommandSessionTask::class.java,
          executionConfigClass = CommandSessionTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Execute commands in a stateful, interactive session",
          tooltipHtml = """
                          Creates and manages a persistent command-line session (e.g., bash, python).
                          This allows for stateful interactions where commands can build on the results of previous ones.
                          <ul>
                              <li><b>Start any interactive process:</b> Specify the command to run</li>
                              <li><b>Send inputs:</b> Provide a list of commands to be executed sequentially in the session.</li>
                              <li><b>Stateful Sessions:</b> Reuse sessions by providing a `sessionId`. The environment (variables, current directory) persists between tasks using the same ID.</li>
                              <li><b>Manage Session Lifecycle:</b> Sessions can be explicitly closed or will be cleaned up automatically.</li>
                              <li><b>TTY Support:</b> Set `tty` to true to allocate a pseudo-terminal (requires pty4j), enabling UI applications and TTY-dependent tools.</li>
                          </ul>
                      """,
        )
        private val log = LoggerFactory.getLogger(CommandSessionTask::class.java)
        private val _activeSessions = ConcurrentHashMap<String, ConcurrentHashMap<String, SessionState>>()
        private const val TIMEOUT_MS = 30000L

        private const val MAX_SESSIONS = 10


        private fun cleanupInactiveSessions() {
            _activeSessions.values.forEach { activeSessions ->
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

    }

}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/session/JdbcSessionTask.kt

```
package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.LoggerFactory
import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.webui.session.SessionTask
import java.sql.Connection
import java.sql.Driver
import java.sql.ResultSet
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

class JdbcSessionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: JdbcSessionTaskExecutionConfigData?
) : AbstractTask<JdbcSessionTask.JdbcSessionTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {

    init {
        @Suppress("SENSELESS_COMPARISON") require(org.hsqldb.jdbc.JDBCDriver::class.java != null)
    }

    class SessionState(val connection: Connection) {
        fun isValid(): Boolean {
            return try {
                !connection.isClosed && connection.isValid(5)
            } catch (e: SQLException) {
                false
            }
        }
    }

    private val activeSessions: ConcurrentHashMap<String, SessionState>
        get() = _activeSessions.getOrPut(orchestrationConfig.sessionId) { ConcurrentHashMap() }

    class JdbcSessionTaskExecutionConfigData(
      @Description("JDBC Connection URL (e.g., jdbc:postgresql://localhost:5432/mydb)") var url: String? = null,
      @Description("Database User") var user: String? = null,
      @Description("Database Password") var password: String? = null,
      @Description("JDBC Driver Class Name (optional, e.g., org.postgresql.Driver)") var driver: String? = null,
      @Description("SQL statements to execute") var sql: List<String> = listOf(),
      @Description("Session ID for reusing existing connections") var sessionId: String? = null,
      @Description("Close the session after execution") var closeSession: Boolean = false,
      @Description("Description of the task") task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
    ) : TaskExecutionConfig(
        task_type = JdbcSession.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    )

    override fun promptSegment(): String {
        val activeSessionsInfo = activeSessions.entries.joinToString("\n") { (id, state) ->
            val valid = state.isValid()
            "  ** Session $id (valid=$valid)"
        }
        return """
           JdbcSession - Stateful SQL execution via JDBC.
           * Use `url`, `user`, and `password` for new connections.
           * Use `sessionId` to maintain transactions or temporary state across tasks.
           * Accepts a list of `sql` statements.
           
          ${if (activeSessionsInfo.isBlank()) "None" else "Active Sessions:\n$activeSessionsInfo"}
           """.trimIndent()
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        executionConfig ?: throw IllegalStateException("Execution config is null")

        val resultBuffer = StringBuffer()

        val execute: (Boolean) -> Unit = { shouldComplete ->
            task.ui.pool.submit {
                val transcript = task.transcript()
                
                try {
                  log.info("Starting JDBC execution for session: ${executionConfig.sessionId ?: "transient"}")
                  task.header("JDBC Session Results")
                    // 1. Session Management
                    val sessionState = getOrCreateSession(task)
                    val connection = sessionState.connection

                    // 2. Execution
                    executionConfig.sql.forEachIndexed { index, sql ->
                        val inputHeader = "\n### Query ${index + 1}"
                        val inputBlock = "```sql\n$sql\n```"
                      transcript?.write(
                        """
                            $inputHeader
                            <details>
                            <summary>SQL Statement</summary>
                            
                            $inputBlock
                            </details>
                        """.trimIndent().toByteArray()
                      )
                        task.add(inputBlock.renderMarkdown())
                        resultBuffer.appendLine(inputHeader).appendLine(inputBlock)

                        try {
                            val statement = connection.createStatement()
                            val hasResultSet = statement.execute(sql)
                            
                            val outputText: String
                            if (hasResultSet) {
                                val rs = statement.resultSet
                                outputText = resultSetToMarkdown(rs)
                                rs.close()
                            } else {
                                outputText = "Update Count: ${statement.updateCount}"
                            }
                            statement.close()

                          transcript?.write(
                            """
                                #### Result
                                <details>
                                <summary>Query Output</summary>
                                
                                $outputText
                                </details>
                            """.trimIndent().toByteArray()
                          )
                            task.add(outputText.renderMarkdown())
                            resultBuffer.appendLine("Result:").appendLine(outputText)

                        } catch (e: SQLException) {
                            val errorMsg = "SQL Error: ${e.message}"
                          log.error("SQL execution failed: ${e.message}")
                            task.error(e)
                            resultBuffer.appendLine(errorMsg)
                          transcript?.write(
                            """
                                #### SQL Error
                                <details>
                                <summary>Stack Trace</summary>
                                ```
                                ${e.stackTraceToString()}
                                ```
                                </details>
                            """.trimIndent().toByteArray()
                          )
                        }
                    }

                    // 3. Cleanup
                    if (executionConfig.closeSession) {
                        connection.close()
                        executionConfig.sessionId?.let { activeSessions.remove(it) }
                      task.add("Session closed.".renderMarkdown())
                    }

                    if (shouldComplete) {
                      task.complete("SQL execution finished.".renderMarkdown())
                        resultFn(resultBuffer.toString())
                    } else {
                      task.add("Execution Complete.".renderMarkdown())
                    }

                } catch (e: Exception) {
                    val errorResult = "Error in JdbcSessionTask: ${e.message}"
                    resultBuffer.appendLine(errorResult)
                  log.error("JDBC Task failed: ${e.message}")
                    task.error(e)
                  transcript?.write(
                    """
                        ### Task Failure
                        <details>
                        <summary>Stack Trace</summary>
                        ```
                        ${e.stackTraceToString()}
                        ```
                        </details>
                    """.trimIndent().toByteArray()
                  )
                    if (shouldComplete) {
                        resultFn(resultBuffer.toString())
                    }
                } finally {
                    transcript?.close()
                }
            }
        }

        if (orchestrationConfig.autoFix) {
            execute(true)
        } else {
            task.header("JDBC Execution Plan")
            val plan = buildString {
                if (executionConfig.url != null) appendLine("Connect to: `${executionConfig.url}`")
                if (executionConfig.sessionId != null) appendLine("Session ID: `${executionConfig.sessionId}`")
                appendLine("SQL Statements:")
                executionConfig.sql.forEach { appendLine("```sql\n$it\n```") }
            }
            task.add(plan.renderMarkdown())

          task.add(task.ui.hrefLink("▶ Run SQL", "btn btn-primary") {
                execute(false)
            })

            task.add(acceptButtonFooter(task.ui) {
                task.complete()
                resultFn(resultBuffer.toString())
            })
        }
    }

    private fun getOrCreateSession(task: SessionTask): SessionState {
        val sessionId = executionConfig?.sessionId
        
        // Try to retrieve existing session
        if (sessionId != null) {
            val existing = activeSessions[sessionId]
            if (existing != null && existing.isValid()) {
              task.add("Reusing session: `$sessionId`".renderMarkdown())
                return existing
            } else if (existing != null) {
                activeSessions.remove(sessionId)
              task.add("Previous session `$sessionId` was invalid/closed. Creating new.".renderMarkdown())
            }
        }

        // Create new session
        val url = executionConfig?.url ?: throw IllegalArgumentException("URL required for new session")
        val user = executionConfig.user
        val password = executionConfig.password
        val driver = executionConfig.driver

        val driverClass = (if (driver != null && driver.isNotBlank()) {
            try {
                Class.forName(driver)
            } catch (e: ClassNotFoundException) {
                log.warn("Could not load driver class: $driver", e)
                null
            }
        } else {
            null
        } ?: try {
            when {
                url.startsWith("jdbc:h2:") -> Class.forName("org.h2.Driver")
                url.startsWith("jdbc:mysql:") -> Class.forName("com.mysql.cj.jdbc.Driver")
                url.startsWith("jdbc:postgresql:") -> Class.forName("org.postgresql.Driver")
                else -> null
            }
        } catch (e: ClassNotFoundException) {
            log.warn("Could not load driver class", e)
            null
        } ?: throw IllegalArgumentException("JDBC Driver class not found for URL: $url")) as Class<Driver>

        val connection = driverClass.newInstance().connect(url, java.util.Properties().apply {
            if(!user.isNullOrBlank()) put("user", user)
            if(!password.isNullOrBlank()) put("password", password)
        })

        val newState = SessionState(connection)
        if (sessionId != null) {
            activeSessions[sessionId] = newState
          task.add("Created new session: `$sessionId`".renderMarkdown())
        } else {
          task.add("Created transient connection to `$url`".renderMarkdown())
        }
        
        return newState
    }

    private fun resultSetToMarkdown(rs: ResultSet): String {
        val md = rs.metaData
        val colCount = md.columnCount
        val sb = StringBuilder()
        
        // Header
        sb.append("| ")
        for (i in 1..colCount) sb.append(md.getColumnLabel(i)).append(" | ")
        sb.append("\n| ")
        for (i in 1..colCount) sb.append("--- | ")
        sb.append("\n")
        
        // Rows (Limit to prevent UI flooding)
        var rowCount = 0
        val maxRows = 100
        while (rs.next()) {
            if (rowCount >= maxRows) {
                sb.append("| ... | ".repeat(colCount)).append("\n")
                sb.append("\n*(Output truncated after $maxRows rows)*")
                break
            }
            sb.append("| ")
            for (i in 1..colCount) {
                val valStr = rs.getString(i)?.replace("\n", " ")?.replace("|", "\\|") ?: "NULL"
                sb.append(valStr).append(" | ")
            }
            sb.append("\n")
            rowCount++
        }
        
        if (rowCount == 0) return "*(No rows returned)*"
        return sb.toString()
    }

    companion object {
        @JvmStatic val JdbcSession = TaskType(
          name = "JdbcSession",
          category = "Session",
          taskClass = JdbcSessionTask::class.java,
          executionConfigClass = JdbcSessionTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Execute SQL queries via JDBC",
          tooltipHtml = """
                          Executes SQL statements against a database using JDBC.
                          <ul>
                              <li><b>Connection:</b> Requires `url`. Optional `user`, `password`, and `driver`.</li>
                              <li><b>Stateful:</b> Use `sessionId` to keep connections open across multiple tasks (useful for transactions or temp tables).</li>
                              <li><b>Output:</b> Returns results as Markdown tables.</li>
                          </ul>
                      """,
        )
        private val log = LoggerFactory.getLogger(JdbcSessionTask::class.java)
        private val _activeSessions = ConcurrentHashMap<String, ConcurrentHashMap<String, SessionState>>()
    }
}
```

# /home/andrew/code/Cognotik/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/session/SeleniumSessionTask.kt

```
package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.plan.tools.AbstractTask
import com.simiacryptus.cognotik.plan.tools.TaskExecutionConfig
import com.simiacryptus.cognotik.plan.tools.TaskType
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.util.*
import com.simiacryptus.cognotik.webui.session.SessionTask
import io.github.bonigarcia.wdm.WebDriverManager
import jakarta.servlet.http.Cookie
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.devtools.v143.log.Log
import org.openqa.selenium.devtools.v143.network.Network
import org.openqa.selenium.remote.RemoteWebDriver
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService

class SeleniumSessionTask(
    orchestrationConfig: OrchestrationConfig,
    planTask: SeleniumSessionTaskExecutionConfigData?
) : AbstractTask<SeleniumSessionTask.SeleniumSessionTaskExecutionConfigData, TaskTypeConfig>(
    orchestrationConfig,
    planTask
) {
    companion object {
        private val log = LoggerFactory.getLogger(SeleniumSessionTask::class.java)
        private val activeSessions = ConcurrentHashMap<String, Selenium>()
        private const val TIMEOUT_MS = 30000L

        private const val MAX_SESSIONS = 10

        @JvmStatic val SeleniumSession = TaskType(
          name = "SeleniumSession",
          category = "Session",
          taskClass = SeleniumSessionTask::class.java,
          executionConfigClass = SeleniumSessionTaskExecutionConfigData::class.java,
          taskSettingsClass = TaskTypeConfig::class.java,
          description = "Automate browser interactions with Selenium",
          tooltipHtml = """
                    Automates browser interactions using Selenium WebDriver.
                    <ul>
                      <li>Headless Chrome browser automation</li>
                      <li>JavaScript command execution</li>
                      <li>Session management capabilities</li>
                      <li>Configurable timeouts</li>
                      <li>Detailed execution results</li>
                    </ul>
                  """,
        )


    }

    private fun cleanupInactiveSessions() {
        activeSessions.entries.removeIf { (id, session) ->
            try {
                if (!session.isAlive()) {
                    log.info("Removing inactive session $id")
                    session.quit()
                    true
                } else false
            } catch (e: Exception) {
                log.warn("Error checking session $id, removing", e)
                try {
                    session.forceQuit()
                } catch (e2: Exception) {
                    log.error("Failed to force quit session $id", e2)
                }
                true
            }
        }
    }

    class SeleniumSessionTaskExecutionConfigData(
        @Description("The URL to navigate to (optional if reusing existing session)")
        val url: String = "",
        @Description("JavaScript commands to execute")
        val commands: List<String> = listOf(),
        @Description("Session ID for reusing existing sessions")
        val sessionId: String? = null,
        @Description("Timeout in milliseconds for commands")
        val timeout: Long = TIMEOUT_MS,
        @Description("Whether to close the session after execution")
        val closeSession: Boolean = false,
        task_description: String? = null,
        task_dependencies: List<String>? = null,
        state: TaskState? = null,
        @Description("Include CSS data in page source: styles, classes, etc.")
        val includeCssData: Boolean? = null,
        @Description("Whether to simplify the HTML structure by combining nested elements")
        val simplifyStructure: Boolean = true,
        @Description("Whether to keep object IDs in the HTML output")
        val keepObjectIds: Boolean = false,
        @Description("Whether to preserve whitespace in text nodes")
        val preserveWhitespace: Boolean = false,
        @Description("Whether to create a transcript file of the session")
        val createTranscript: Boolean = false,
    ) : TaskExecutionConfig(
        task_type = SeleniumSession.name,
        task_description = task_description,
        task_dependencies = task_dependencies?.toMutableList(),
        state = state
    ), ValidatedObject {
        override fun validate(): String? {
            if (url.isBlank() && sessionId == null) {
                return "Either 'url' must be provided or 'sessionId' must be specified to reuse an existing session"
            }
            if (timeout <= 0) {
                return "Timeout must be greater than 0"
            }
            if (commands.isEmpty() && url.isBlank() && sessionId == null) {
                return "At least one command must be provided, or a URL/sessionId must be specified"
            }
            return ValidatedObject.validateFields(this)
        }
    }

    override fun promptSegment() = """
      SeleniumSession - Create and manage a stateful Selenium browser session
        * Specify the URL to navigate to
        * Provide JavaScript commands to execute in sequence through Selenium's executeScript method
        * Can be used for web scraping, testing, or automation
        * Session persists between commands for stateful interactions
        * Optionally specify sessionId to reuse an existing session
        * Set closeSession=true to close the session after execution
      Example JavaScript Commands:
        * "return document.title;" - Get page title
        * "return document.querySelector('.my-class').textContent;" - Get element text
        * "return Array.from(document.querySelectorAll('a')).map(a => a.href);" - Get all links
        * "document.querySelector('#my-button').click();" - Click an element
        * "window.scrollTo(0, document.body.scrollHeight);" - Scroll to bottom
        * "return document.documentElement.outerHTML;" - Get entire page HTML
        * "return new Promise(r => setTimeout(() => r(document.title), 1000));" - Async operation
      Note: Commands are executed in the browser context and must be valid JavaScript.
            Use proper error handling and waits for dynamic content.

      Active Sessions:
      """.trimIndent() + activeSessions.entries.joinToString("\n") { (id, session: Selenium) ->
        buildString {
            append("  ** Session $id:\n")
            append("     URL: ${session.getCurrentUrl()}\n")
            try {
                append("     Title: ${session.executeScript("return document.title;")}\n")
                val logs = session.getLogs()
                if (logs.isNotEmpty()) {
                    append("     Recent Logs:\n")
                    logs.takeLast(3).forEach { log ->
                        append("       - $log\n")
                    }
                }
            } catch (e: Exception) {
                append("     Error getting session details: ${e.message}\n")
            }
        }
    }

    override fun run(
        agent: TaskOrchestrator,
        messages: List<String>,
        task: SessionTask,
        resultFn: (String) -> Unit,
        orchestrationConfig: OrchestrationConfig
    ) {
        val seleniumFactory: (pool: ExecutorService, cookies: Array<out Cookie>?) -> Selenium =
            { pool, cookies ->
                try {
                    Selenium2S3(
                        pool = pool,
                        cookies = cookies,
                        driver = driver()
                    )
                } catch (e: Exception) {
                    throw IllegalStateException("Failed to initialize Selenium", e)
                }
            }
        requireNotNull(executionConfig) { "SeleniumSessionTaskData is required" }







      task.ui.pool.submit {
        var selenium: Selenium? = null
        var transcriptStream: FileOutputStream? = null
        try {
          task.header("Selenium Session Execution")
          val statusBuffer = task.add("Initializing browser session...".renderMarkdown())

          cleanupInactiveSessions()

          if (activeSessions.size >= MAX_SESSIONS && executionConfig.sessionId == null) {
            throw IllegalStateException("Maximum number of concurrent sessions ($MAX_SESSIONS) reached")
          }

          selenium = executionConfig.sessionId?.let { id -> activeSessions[id] }
            ?: seleniumFactory(agent.pool, null).also { newSession ->
              executionConfig.sessionId?.let { id -> activeSessions[id] = newSession }
            }

          statusBuffer?.setLength(0)
          statusBuffer?.append("Session active. ID: `${executionConfig.sessionId ?: "temporary"}`".renderMarkdown())
          task.update()

          if (executionConfig.createTranscript) {
            transcriptStream = task.transcript("Selenium Session")
            transcriptStream?.write("# Selenium Session Transcript\n\n".toByteArray())
          }

          log.info("Starting Selenium session ${executionConfig.sessionId ?: "temporary"}")
          selenium.setScriptTimeout(executionConfig.timeout)

          val runLogic = {
            if (executionConfig.url.isNotBlank()) {
              statusBuffer?.setLength(0)
              statusBuffer?.append("Navigating to: `${executionConfig.url}`".renderMarkdown())
              task.update()
              selenium.navigate(executionConfig.url)
              transcriptStream?.write("## Navigation\nNavigated to: ${executionConfig.url}\n\n".toByteArray())
            }

            val results = executionConfig.commands.mapIndexed { index, command ->
              try {
                log.debug("Executing command: $command")
                task.add("Executing command ${index + 1}...".renderMarkdown())
                transcriptStream?.write("### Command ${index + 1}\n```javascript\n$command\n```\n\n".toByteArray())

                val startTime = System.currentTimeMillis()
                val result = selenium.executeScript(command)?.toString() ?: "null"
                val duration = System.currentTimeMillis() - startTime

                task.expandable("Result (${duration}ms)", "<pre><code>$result</code></pre>")
                transcriptStream?.write("<details><summary>Result (${duration}ms)</summary>\n\n```\n$result\n```\n</details>\n\n".toByteArray())
                result
              } catch (e: Exception) {
                task.error(e)
                log.error("Command failed: $command", e)
                transcriptStream?.write("#### Error\n```\n${e.message}\n```\n\n".toByteArray())
                "Error: ${e.message}"
              }
            }

            val tabs = TabbedDisplay(task)
            val pageSource = try {
              HtmlSimplifier.scrubHtml(
                str = selenium.getPageSource(),
                baseUrl = selenium.getCurrentUrl(),
                includeCssData = executionConfig.includeCssData ?: false,
                simplifyStructure = executionConfig.simplifyStructure,
                keepObjectIds = executionConfig.keepObjectIds,
                preserveWhitespace = executionConfig.preserveWhitespace
              )
            } catch (e: Exception) {
              "Error: ${e.message}"
            }

            tabs["Summary"] = """
                        ### Session Summary
                        * **Final URL:** [${selenium.getCurrentUrl()}](${selenium.getCurrentUrl()})
                        * **Browser:** ${selenium.getBrowserInfo()}
                        * **Commands Executed:** ${results.size}
                    """.trimIndent().renderMarkdown()

            tabs["Page Source"] = "<details open><summary>Scrubbed HTML</summary><pre><code class=\"language-html\">${
              pageSource.take(50000)
            }</code></pre></details>"

            transcriptStream?.write("## Final State\n**URL:** ${selenium.getCurrentUrl()}\n\n".toByteArray())
            transcriptStream?.write("<details><summary>Final Page Source</summary>\n\n```html\n$pageSource\n```\n</details>\n".toByteArray())

            resultFn(formatResults(executionConfig, selenium, results))
            task.complete()
          }

          if (orchestrationConfig.autoFix || (executionConfig.commands.isEmpty() && executionConfig.url.isBlank())) {
            runLogic()
          } else {
            val proposal = buildString {
              append("### Proposed Browser Actions\n")
              if (executionConfig.url.isNotBlank()) append("* Navigate to: `${executionConfig.url}`\n")
              executionConfig.commands.forEach { append("* Execute: `$it`\n") }
            }
            task.add(proposal.renderMarkdown())
            task.add(acceptButtonFooter(task.ui) {
              runLogic()
            })
          }

        } catch (e: Exception) {
          task.error(e)
          log.error("Selenium task failed: ${e.message}")
          try {


            transcriptStream?.write("\n## Critical Error\n<details><summary>Stack Trace</summary>\n\n```\n${e.stackTraceToString()}\n```\n</details>".toByteArray())
          } catch (_: Exception) {
          }
          throw e
        } finally {
          transcriptStream?.flush()
          transcriptStream?.close()
          if ((executionConfig.sessionId == null || executionConfig.closeSession) && selenium != null) {
            log.info("Closing Selenium session")
            try {
              selenium.quit()
              if (executionConfig.sessionId != null) activeSessions.remove(executionConfig.sessionId)
            } catch (e: Exception) {
              log.error("Error closing session", e)
              selenium.forceQuit()
              if (executionConfig.sessionId != null) activeSessions.remove(executionConfig.sessionId)
                    }
                }
            }
        }
    }

    val chromeDriver: WebDriverManager by lazy { WebDriverManager.chromedriver().apply { setup() } }
    fun driver(): RemoteWebDriver {
        requireNotNull(chromeDriver)
        val driver = ChromeDriver(ChromeOptions().apply {
            addArguments("--headless")
            addArguments("--disable-gpu")
            addArguments("--no-sandbox")
            addArguments("--disable-dev-shm-usage")
        })

        val devTools = driver.devTools
        devTools.createSession()

        devTools.send(Network.enable(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        ))
        devTools.addListener(Network.requestWillBeSent()) { request ->
          log.debug("Request URL: ${request.request.url}")
        }

        devTools.send(Log.enable())
        devTools.addListener(Log.entryAdded()) { logEntry ->
          log.debug("Browser Console: ${logEntry.text}")
        }
        return driver
    }

    private fun formatResults(
        planTask: SeleniumSessionTaskExecutionConfigData,
        selenium: Selenium,
        results: List<String>
    ): String = buildString(capacity = 163840) {

        appendLine("## Selenium Session Results")
        if (planTask.url.isNotBlank()) {
            appendLine("Initial URL: ${planTask.url}")
        }
        appendLine("Session ID: ${planTask.sessionId ?: "temporary"}")
        appendLine("Final URL: ${selenium.getCurrentUrl()}")
        appendLine("Timeout: ${planTask.timeout}ms")
        appendLine("Browser Info: ${selenium.getBrowserInfo()}")
        appendLine("\nCommand Results:")
        results.forEachIndexed { index, result ->
            appendLine("### Command ${index + 1}")
            appendLine("```javascript")
            appendLine(planTask.commands[index])
            appendLine("```")
            if (result != "null") {
                appendLine("Result:")
                appendLine("```")
                appendLine(result.take(5000))

                appendLine("```")
            }
        }
        try {
            appendLine("\nFinal Page Source:")
            appendLine("```html")
            appendLine(
                HtmlSimplifier.scrubHtml(
                    str = selenium.getPageSource(),
                    baseUrl = selenium.getCurrentUrl(),
                    includeCssData = executionConfig?.includeCssData ?: false,
                    simplifyStructure = executionConfig?.simplifyStructure ?: true,
                    keepObjectIds = executionConfig?.keepObjectIds ?: false,
                    preserveWhitespace = executionConfig?.preserveWhitespace ?: false
                ).take(10000) // Truncate for LLM context
            )
          if (selenium.getPageSource().length > 10000) appendLine("... (truncated)")

            appendLine("```")
        } catch (e: Exception) {
          appendLine("\n*Error retrieving page source: ${e.message}*")
        }
    }

}
```

### Task Description
Update the file README.md based on the included documentation and specifications.
Ensure the file conforms to all the patterns, standards, and requirements described.
If the file already exists, update it to match the specifications while preserving existing functionality where appropriate.
</details>

                
## AI Response
<details>
<summary>Raw Output</summary>

Here is the updated `README.md` for the session tools package, generated based on the provided source code and documentation requirements.

### webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/session/README.md
```markdown
# Session Tools

This package provides a set of stateful task implementations designed for persistent interactions. Unlike standard tasks, session-based tasks can maintain state (such as environment variables, database transactions, or browser contexts) across multiple execution steps by using a `sessionId`.

## Tools Overview

### CommandSession
Creates and manages a persistent command-line session (e.g., `bash`, `python`). This allows for stateful interactions where commands can build on the results of previous ones.

**Key Features:**
- **Interactive Processes:** Start any interactive process by specifying the base command.
- **Sequential Inputs:** Send a list of commands to be executed sequentially in the session's standard input.
- **State Persistence:** The environment (variables, current directory) persists between tasks using the same `sessionId`.
- **TTY Support:** Optional pseudo-terminal allocation (via `pty4j`) for tools requiring a TTY or providing colored output.

**Configuration Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `command` | The command to start the interactive session (e.g. `["bash", "-i"]`). | `["bash", "-i"]` |
| `inputs` | List of strings to send to the session's standard input. | `[]` |
| `sessionId` | Optional ID to reuse an existing session across multiple tasks. | `null` |
| `timeout` | Maximum time in milliseconds to wait for all commands to complete. | `30000` |
| `idle_timeout`| Maximum time in milliseconds to wait for output after a command is sent. | `2000` |
| `tty` | Whether to allocate a pseudo-terminal. | `false` |

---

### JdbcSession
Executes SQL statements against a database using JDBC. It supports maintaining connections for transactions or temporary state.

**Key Features:**
- **Flexible Connectivity:** Supports various databases via JDBC URL, user, and password.
- **Transaction Support:** Use `sessionId` to keep connections open across multiple tasks, enabling multi-step transactions or use of temporary tables.
- **Markdown Output:** Query results are automatically formatted as Markdown tables for easy reading and processing.

**Configuration Parameters:**
| Parameter | Description |
|-----------|-------------|
| `url` | JDBC Connection URL (e.g., `jdbc:postgresql://localhost:5432/mydb`). |
| `user` | Database User. |
| `password` | Database Password. |
| `driver` | Optional JDBC Driver Class Name (e.g., `org.postgresql.Driver`). |
| `sql` | List of SQL statements to execute. |
| `sessionId` | Session ID for reusing existing connections. |
| `closeSession`| Whether to close the session after execution. |

---

### SeleniumSession
Automates browser interactions using Selenium WebDriver. This tool is optimized for web scraping, testing, and automation tasks that require a real browser environment.

**Key Features:**
- **Headless Automation:** Uses headless Chrome for efficient background execution.
- **JavaScript Execution:** Execute arbitrary JS commands in the browser context.
- **HTML Optimization:** Includes advanced HTML scrubbing and simplification (via `HtmlSimplifier`) to reduce token usage when passing page content to LLMs.
- **Transcripts:** Can generate detailed session transcripts for debugging and auditing.

**Configuration Parameters:**
| Parameter | Description | Default |
|-----------|-------------|---------|
| `url` | The URL to navigate to (optional if reusing session). | `""` |
| `commands` | JavaScript commands to execute in sequence. | `[]` |
| `sessionId` | Session ID for reusing existing sessions. | `null` |
| `timeout` | Timeout in milliseconds for commands. | `30000` |
| `closeSession`| Whether to close the session after execution. | `false` |
| `simplifyStructure`| Whether to simplify the HTML structure by combining nested elements. | `true` |
| `createTranscript`| Whether to create a transcript file of the session. | `false` |

## Session Management
- **Concurrency:** Most session types limit the number of concurrent active sessions (typically 10) to manage system resources.
- **Cleanup:** Inactive or dead processes/connections are automatically cleaned up during new task initialization.
- **Persistence:** Sessions are stored in-memory and are tied to the lifecycle of the application server.
```

### Summary of Changes
- Created a new `README.md` in the `com.simiacryptus.cognotik.plan.tools.session` package directory.
- Extracted tool descriptions and configuration details from `CommandSessionTask.kt`, `JdbcSessionTask.kt`, and `SeleniumSessionTask.kt`.
- Organized the documentation by tool, highlighting key features and configuration parameters using tables for clarity.
- Included general information about session management and persistence based on the implementation details found in the source code (e.g., `MAX_SESSIONS`, `cleanupInactiveSessions`).
- Followed the transformation pattern specified in `index_docs.md`.
</details>

                - <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/session/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/session/README.md</a> Updated

**Auto-applying changes...**

## Completion
### Modifications Applied
* <a href='fileIndex/G-20260120-RPFC/webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/session/README.md'>webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools/session/README.md</a> Updated
