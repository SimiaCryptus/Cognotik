package com.simiacryptus.cognotik.plan.tools.session

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.session.JdbcSessionTask.JdbcSessionTaskExecutionConfigData
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object JdbcSessionTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

   //@org.junit.jupiter.api.Test
    @Timeout(5, unit = TimeUnit.MINUTES)
    fun testJdbcSession() {
        TaskHarness(
            taskType = JdbcSessionTask.JdbcSession,
            typeConfig = TaskTypeConfig(
                task_type = JdbcSessionTask.JdbcSession.name
            ),
            executionConfig = JdbcSessionTaskExecutionConfigData(
                // Use HSQLDB in-memory database for testing
                url = "jdbc:hsqldb:mem:testdb;shutdown=true",
                user = "SA",
                password = "",
                driver = "org.hsqldb.jdbc.JDBCDriver",
                sql = listOf(
                    "CREATE TABLE test_table (id INTEGER PRIMARY KEY, val VARCHAR(255))",
                    "INSERT INTO test_table (id, val) VALUES (1, 'Hello')",
                    "INSERT INTO test_table (id, val) VALUES (2, 'World')",
                    "SELECT * FROM test_table ORDER BY id ASC"
                ),
                task_description = "Create a test table, insert data, and query it.",
                closeSession = true
            ),
            timeoutMinutes = 5,
        ).run()
    }

    //@org.junit.jupiter.api.Test
    @Timeout(5, unit = TimeUnit.MINUTES)
    fun testSessionPersistence() {
        val sessionId = "test-session-${System.currentTimeMillis()}"
        val dbUrl = "jdbc:hsqldb:mem:persistent_db;shutdown=false"

        // First task: Create table and insert data without closing session
        TaskHarness(
            taskType = JdbcSessionTask.JdbcSession,
            typeConfig = TaskTypeConfig(
                task_type = JdbcSessionTask.JdbcSession.name
            ),
            executionConfig = JdbcSessionTaskExecutionConfigData(
                url = dbUrl,
                user = "SA",
                password = "",
                sessionId = sessionId,
                sql = listOf(
                    "CREATE TABLE session_test (id INTEGER)",
                    "INSERT INTO session_test VALUES (100)"
                ),
                closeSession = false
            ),
            timeoutMinutes = 2
        ).run()

        // Second task: Query the data using the same sessionId
        TaskHarness(
            taskType = JdbcSessionTask.JdbcSession,
            typeConfig = TaskTypeConfig(
                task_type = JdbcSessionTask.JdbcSession.name
            ),
            executionConfig = JdbcSessionTaskExecutionConfigData(
                url = dbUrl,
                user = "SA",
                password = "",
                sessionId = sessionId,
                sql = listOf("SELECT * FROM session_test"),
                closeSession = true
            ),
            timeoutMinutes = 2
        ).run()
    }
}