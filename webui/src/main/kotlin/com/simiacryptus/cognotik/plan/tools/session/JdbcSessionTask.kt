package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.util.renderMarkdown
import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.*
import com.simiacryptus.cognotik.util.LoggerFactory
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
        @Description("JDBC Connection URL (e.g., jdbc:postgresql://localhost:5432/mydb)") val url: String? = null,
        @Description("Database User") val user: String? = null,
        @Description("Database Password") val password: String? = null,
        @Description("JDBC Driver Class Name (optional, e.g., org.postgresql.Driver)") val driver: String? = null,
        @Description("SQL statements to execute") val sql: List<String> = listOf(),
        @Description("Session ID for reusing existing connections") val sessionId: String? = null,
        @Description("Close the session after execution") val closeSession: Boolean = false,
        task_description: String? = null,
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
           JdbcSession - Execute SQL queries via JDBC in a stateful session.
           ** Specify the `url`, `user`, and `password` to start a new session.
           ** Use `sessionId` to reuse an existing connection for transactions or subsequent queries.
           ** Provide a list of `sql` statements to execute.
           
           Active Sessions:
           ${if (activeSessionsInfo.isBlank()) "None" else activeSessionsInfo}
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
                task.header("JDBC Session Results")
                val transcript = task.transcript()
                
                try {
                    // 1. Session Management
                    val sessionState = getOrCreateSession(task)
                    val connection = sessionState.connection

                    // 2. Execution
                    executionConfig.sql.forEachIndexed { index, sql ->
                        val inputHeader = "\n### Query ${index + 1}"
                        val inputBlock = "```sql\n$sql\n```"
                        transcript?.write("$inputHeader\n$inputBlock\n".toByteArray())
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

                            transcript?.write("Result:\n$outputText\n".toByteArray())
                            task.add(outputText.renderMarkdown())
                            resultBuffer.appendLine("Result:").appendLine(outputText)

                        } catch (e: SQLException) {
                            val errorMsg = "SQL Error: ${e.message}"
                            log.error(errorMsg, e)
                            transcript?.write("$errorMsg\n".toByteArray())
                            task.error(e)
                            resultBuffer.appendLine(errorMsg)
                        }
                    }

                    // 3. Cleanup
                    if (executionConfig.closeSession) {
                        connection.close()
                        executionConfig.sessionId?.let { activeSessions.remove(it) }
                        task.add("Session closed.")
                    }

                    if (shouldComplete) {
                        task.complete("SQL execution finished.")
                        resultFn(resultBuffer.toString())
                    } else {
                        task.add("Execution Complete.")
                    }

                } catch (e: Exception) {
                    val errorResult = "Error in JdbcSessionTask: ${e.message}"
                    resultBuffer.appendLine(errorResult)
                    log.error("Error in JdbcSessionTask", e)
                    task.error(e)
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

            task.add(task.ui.hrefLink("Run SQL", "btn btn-primary") {
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
                task.add("Reusing session: $sessionId")
                return existing
            } else if (existing != null) {
                activeSessions.remove(sessionId)
                task.add("Previous session $sessionId was invalid/closed. Creating new.")
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
            task.add("Created new session: $sessionId")
        } else {
            task.add("Created transient connection to $url")
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
        val JdbcSession = TaskType(
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