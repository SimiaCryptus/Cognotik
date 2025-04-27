package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.MetadataStorageInterface
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.util.*

class HSQLMetadataStorage(private val dbFile: File) : MetadataStorageInterface {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.info("Initializing HSQL Metadata Storage with ${dbFile.absolutePath}", RuntimeException("Stack trace"))
    }

    private val connection: Connection by lazy {
        require(dbFile.absoluteFile.exists() || dbFile.absoluteFile.mkdirs()) { "Unable to create database directory: ${dbFile.absolutePath}" }
        log.info(
            "Initializing HSQLMetadataStorage with database file: ${dbFile.absolutePath}",
            RuntimeException("Stack trace")
        )
        Class.forName("org.hsqldb.jdbc.JDBCDriver")
        val connection =
            DriverManager.getConnection("jdbc:hsqldb:file:${dbFile.absolutePath}/metadata;shutdown=true", "SA", "")
        createSchema(connection)
        connection
    }

    private fun createSchema(connection: Connection) {
        log.debug("Attempting to create database schema if not exists")
        connection.createStatement().executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS metadata (
                session_id VARCHAR(255),
                user_email VARCHAR(255),
                key VARCHAR(255),
                value LONGVARCHAR,
                timestamp TIMESTAMP,
                PRIMARY KEY (session_id, user_email, key)
            )
            """
        )
    }

    override fun getSessionName(user: User?, session: Session): String {
        log.debug("Fetching session name for session: ${session.sessionId}, user: ${user?.email}")
        val statement = connection.prepareStatement(
            "SELECT value FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'name'"
        )
        statement.setString(1, session.sessionId)
        statement.setString(2, user?.email ?: "")
        val resultSet = statement.executeQuery()
        return if (resultSet.next()) {
            val name = resultSet.getString("value")
            log.debug("Retrieved session name: $name for session: ${session.sessionId}")
            name
        } else {
            session.sessionId
        }
    }

    override fun setSessionName(user: User?, session: Session, name: String) {
        log.debug("Setting session name for session: ${session.sessionId}, user: ${user?.email} to $name")
        val statement = connection.prepareStatement(
            """
            MERGE INTO metadata USING (VALUES(?, ?, ?, ?, ?)) AS vals(session_id, user_email, key, value, timestamp)
            ON metadata.session_id = vals.session_id AND metadata.user_email = vals.user_email AND metadata.key = vals.key
            WHEN MATCHED THEN UPDATE SET metadata.value = vals.value, metadata.timestamp = vals.timestamp
            WHEN NOT MATCHED THEN INSERT VALUES vals.session_id, vals.user_email, vals.key, vals.value, vals.timestamp
            """
        )
        statement.setString(1, session.sessionId)
        statement.setString(2, user?.email ?: "")
        statement.setString(3, "name")
        statement.setString(4, name)
        statement.setTimestamp(5, Timestamp(System.currentTimeMillis()))
        statement.executeUpdate()
        log.info("Session name set successfully for session: ${session.sessionId}")
    }

    override fun getMessageIds(user: User?, session: Session): List<String> {
        log.debug("Fetching message IDs for session: ${session.sessionId}, user: ${user?.email}")
        val statement = connection.prepareStatement(
            "SELECT value FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'message_ids'"
        )
        statement.setString(1, session.sessionId)
        statement.setString(2, user?.email ?: "")
        val resultSet = statement.executeQuery()
        return if (resultSet.next()) {
            val ids = resultSet.getString("value").split(",")
            log.debug("Retrieved ${ids.size} message IDs for session: ${session.sessionId}")
            ids
        } else {
            log.debug("No message IDs found for session: ${session.sessionId}")
            emptyList()
        }
    }

    override fun setMessageIds(user: User?, session: Session, ids: List<String>) {
        log.debug("Setting message IDs for session: ${session.sessionId}, user: ${user?.email} to $ids")
        val statement = connection.prepareStatement(
            """
            MERGE INTO metadata USING (VALUES(?, ?, ?, ?, ?)) AS vals(session_id, user_email, key, value, timestamp)
            ON metadata.session_id = vals.session_id AND metadata.user_email = vals.user_email AND metadata.key = vals.key
            WHEN MATCHED THEN UPDATE SET metadata.value = vals.value, metadata.timestamp = vals.timestamp
            WHEN NOT MATCHED THEN INSERT VALUES vals.session_id, vals.user_email, vals.key, vals.value, vals.timestamp
            """
        )
        statement.setString(1, session.sessionId)
        statement.setString(2, user?.email ?: "")
        statement.setString(3, "message_ids")
        statement.setString(4, ids.joinToString(","))
        statement.setTimestamp(5, Timestamp(System.currentTimeMillis()))
        statement.executeUpdate()
        log.debug("Set ${ids.size} message IDs for session: ${session.sessionId}")
    }

    override fun getSessionTime(user: User?, session: Session): Date? {
        log.debug("Fetching session time for session: ${session.sessionId}, user: ${user?.email}")
        val statement = connection.prepareStatement(
            "SELECT value, timestamp FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'session_time'"
        )
        statement.setString(1, session.sessionId)
        statement.setString(2, user?.email ?: "")
        val resultSet = statement.executeQuery()
        return if (resultSet.next()) {
            val time = resultSet.getString("value")
            try {
                Date(time.toLong()).also {
                    log.debug("Retrieved session time: $it for session: ${session.sessionId}")
                }
            } catch (e: NumberFormatException) {
                log.warn("Invalid session time value: $time, falling back to timestamp for session: ${session.sessionId}")
                resultSet.getTimestamp("timestamp")
            }
        } else {
            Date()
        }
    }

    override fun setSessionTime(user: User?, session: Session, time: Date) {
        log.debug("Setting session time for session: ${session.sessionId}, user: ${user?.email} to $time")
        val statement = connection.prepareStatement(
            """
            MERGE INTO metadata USING (VALUES(?, ?, ?, ?, ?)) AS vals(session_id, user_email, key, value, timestamp)
            ON metadata.session_id = vals.session_id AND metadata.user_email = vals.user_email AND metadata.key = vals.key
            WHEN MATCHED THEN UPDATE SET metadata.value = vals.value, metadata.timestamp = vals.timestamp
            WHEN NOT MATCHED THEN INSERT VALUES vals.session_id, vals.user_email, vals.key, vals.value, vals.timestamp
            """
        )
        statement.setString(1, session.sessionId)
        statement.setString(2, user?.email ?: "")
        statement.setString(3, "session_time")
        statement.setString(4, time.time.toString())
        statement.setTimestamp(5, Timestamp(time.time))
        statement.executeUpdate()
        log.info("Session time set to $time for session: ${session.sessionId}")
    }

    override fun listSessions(path: String): List<String> {
        log.debug("Listing sessions for path: $path")
        val statement = connection.prepareStatement(
            "SELECT DISTINCT session_id FROM metadata WHERE value = ? AND key = 'path'"
        )
        statement.setString(1, path)
        val resultSet = statement.executeQuery()
        val sessions = mutableListOf<String>()
        while (resultSet.next()) {
            sessions.add(resultSet.getString("session_id"))
        }
        log.info("Found ${sessions.size} sessions for path: $path")
        return sessions
    }

    override fun deleteSession(user: User?, session: Session) {
        log.debug("Deleting session: ${session.sessionId}, user: ${user?.email}")
        val statement = connection.prepareStatement(
            "DELETE FROM metadata WHERE session_id = ? AND user_email = ?"
        )
        statement.setString(1, session.sessionId)
        statement.setString(2, user?.email ?: "")
        statement.executeUpdate()
        log.info("Deleted session: ${session.sessionId} for user: ${user?.email ?: "anonymous"}")
    }

}