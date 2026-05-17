package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.model.MetadataStorageInterface
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.User
import org.slf4j.LoggerFactory
import java.sql.Timestamp
import java.sql.Types
import java.util.*

class MetadataStorageDB : MetadataStorageInterface {

    override fun getSessionName(user: User?, session: Session): String {
        log.debug("Fetching session name for session: {}, user: {}", session, user?.email)
        return facet.withConnection() { conn ->
            conn.prepareStatement(
                "SELECT value FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'name'"
            ).use { stmt ->
                stmt.setString(1, session.sessionId)
                stmt.setString(2, user?.email ?: "")
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("value") else session.sessionId
                }
            }
        }
    }

    override fun setSessionName(user: User?, session: Session, name: String) {
        log.debug("Setting session name for session: {}, user: {} to {}", session, user?.email, name)
        upsertMetadata(session.sessionId, user?.email ?: "", "name", name)
        log.info("Session name set successfully for session: {}", session)
    }

    override fun getMessageIds(user: User?, session: Session): List<String> {
        log.debug("Fetching message IDs for session: {}, user: {}", session, user?.email)
        return facet.withConnection() { conn ->
            conn.prepareStatement(
                "SELECT value FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'message_ids'"
            ).use { stmt ->
                stmt.setString(1, session.sessionId)
                stmt.setString(2, user?.email ?: "")
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val raw = rs.getString("value")
                        if (raw.isNullOrEmpty()) emptyList()
                        else raw.split(",").filter { it.isNotEmpty() }
                    } else emptyList()
                }
            }
        }
    }

    override fun setMessageIds(user: User?, session: Session, ids: List<String>) {
        log.debug("Setting {} message IDs for session: {}, user: {}", ids.size, session, user?.email)
        upsertMetadata(session.sessionId, user?.email ?: "", "message_ids", ids.joinToString(","))
    }

    override fun getSessionTime(user: User?, session: Session): Date? {
        log.debug("Fetching session time for session: {}, user: {}", session, user?.email)
        return facet.withConnection() { conn ->
            conn.prepareStatement(
                "SELECT value, timestamp FROM metadata WHERE session_id = ? AND user_email = ? AND key = 'session_time'"
            ).use { stmt ->
                stmt.setString(1, session.sessionId)
                stmt.setString(2, user?.email ?: "")
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        val time = rs.getString("value")
                        try {
                            Date(time.toLong())
                        } catch (e: NumberFormatException) {
                            log.warn("Invalid session time value: {} for session: {}", time, session)
                            rs.getTimestamp("timestamp")
                        }
                    } else null
                }
            }
        }
    }

    override fun setSessionTime(user: User?, session: Session, time: Date) {
        log.debug("Setting session time for session: {}, user: {} to {}", session, user?.email, time)
        upsertMetadata(
            session.sessionId,
            user?.email ?: "",
            "session_time",
            time.time.toString(),
            Timestamp(time.time)
        )
    }

    override fun listSessions(path: String): List<String> {
        log.debug("Listing sessions for path: {}", path)
        return facet.withConnection() { conn ->
            conn.prepareStatement(
                "SELECT DISTINCT session_id FROM metadata WHERE value = ? AND key = 'path'"
            ).use { stmt ->
                stmt.setString(1, path)
                stmt.executeQuery().use { rs ->
                    val sessions = mutableListOf<String>()
                    while (rs.next()) sessions.add(rs.getString("session_id"))
                    sessions
                }
            }
        }.also { log.info("Found {} sessions for path: {}", it.size, path) }
    }
    override fun listSessions(user: User): List<String> {
        log.debug("Listing sessions for user: {}", user.email)
        return facet.withConnection { conn ->
            conn.prepareStatement(
                "SELECT DISTINCT session_id FROM metadata WHERE user_email = ?"
            ).use { stmt ->
                stmt.setString(1, user.email)
                stmt.executeQuery().use { rs ->
                    val sessions = mutableListOf<String>()
                    while (rs.next()) sessions.add(rs.getString("session_id"))
                    sessions
                }
            }
        }.also { log.info("Found {} sessions for user: {}", it.size, user.email) }
    }


    override fun getSessionOwner(session: Session): String? {
        log.debug("Fetching session owner for session: {}", session)
        return facet.withConnection() { conn ->
            conn.prepareStatement(
                "SELECT value FROM metadata WHERE session_id = ? AND key = 'owner_id'"
            ).use { stmt ->
                stmt.setString(1, session.sessionId)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("value") else null
                }
            }
        }
    }

    override fun setSessionOwner(session: Session, ownerId: String?) {
        log.debug("Setting session owner for session: {} to {}", session, ownerId)
        upsertMetadata(session.sessionId, "", "owner_id", ownerId)
    }

    override fun deleteSession(user: User?, session: Session) {
        log.debug("Deleting session: {}, user: {}", session, user?.email)
        facet.withConnection() { conn ->
            conn.prepareStatement(
                "DELETE FROM metadata WHERE session_id = ? AND user_email = ?"
            ).use { stmt ->
                stmt.setString(1, session.sessionId)
                stmt.setString(2, user?.email ?: "")
                stmt.executeUpdate()
            }
        }
        log.info("Deleted session: {} for user: {}", session, user?.email ?: "anonymous")
    }

    /**
     * Retrieves all metadata for a session in a single database query, returning
     * a unified [SessionMetadata] object. This is more efficient than calling
     * individual getters as it requires only one round-trip to the database.
     */
    override fun getSessionMetadata(user: User?, session: Session): SessionMetadata {
        log.debug("Fetching unified session metadata for session: {}, user: {}", session, user?.email)
        return facet.withConnection { conn ->
            conn.prepareStatement(
                "SELECT key, value, timestamp FROM metadata WHERE session_id = ? AND (user_email = ? OR user_email = '')"
            ).use { stmt ->
                stmt.setString(1, session.sessionId)
                stmt.setString(2, user?.email ?: "")
                stmt.executeQuery().use { rs ->
                    var name: String? = null
                    var messageIds: List<String> = emptyList()
                    var sessionTime: Date? = null
                    var ownerId: String? = null
                    var path: String? = null
                    val additional = mutableMapOf<String, String>()
                    while (rs.next()) {
                        val key = rs.getString("key")
                        val value = rs.getString("value")
                        when (key) {
                            "name" -> name = value
                            "message_ids" -> messageIds =
                                if (value.isNullOrEmpty()) emptyList()
                                else value.split(",").filter { it.isNotEmpty() }

                            "session_time" -> sessionTime = try {
                                Date(value.toLong())
                            } catch (e: Exception) {
                                rs.getTimestamp("timestamp")
                            }

                            "owner_id" -> ownerId = value
                            "path" -> path = value
                            else -> if (value != null) additional[key] = value
                        }
                    }
                    SessionMetadata(
                        id = session,
                        name = name,
                        messageIds = messageIds,
                        sessionTime = sessionTime,
                        ownerId = ownerId,
                        path = path,
                        additional = additional
                    )
                }
            }
        }
    }

    /**
     * Sets multiple metadata fields for a session in a single transactional batch.
     * Only non-null fields in [metadata] are written. Additional key/value pairs
     * from [SessionMetadata.additional] are also persisted.
     */
    override fun setSessionMetadata(user: User?, session: Session, metadata: SessionMetadata) {
        log.debug("Setting unified session metadata for session: {}, user: {}", session, user?.email)
        val userEmail = user?.email ?: ""
        val now = Timestamp(System.currentTimeMillis())
        metadata.name?.let { upsertMetadata(session.sessionId, userEmail, "name", it, now) }
        if (metadata.messageIds.isNotEmpty()) {
            upsertMetadata(session.sessionId, userEmail, "message_ids", metadata.messageIds.joinToString(","), now)
        }
        metadata.sessionTime?.let {
            upsertMetadata(session.sessionId, userEmail, "session_time", it.time.toString(), Timestamp(it.time))
        }
        metadata.ownerId?.let { upsertMetadata(session.sessionId, "", "owner_id", it, now) }
        metadata.path?.let { upsertMetadata(session.sessionId, userEmail, "path", it, now) }
        metadata.additional.forEach { (k, v) -> upsertMetadata(session.sessionId, userEmail, k, v, now) }
        log.info("Unified session metadata set successfully for session: {}", session)
    }

    private fun upsertMetadata(
        sessionId: String,
        userEmail: String,
        key: String,
        value: String?,
        timestamp: Timestamp = Timestamp(System.currentTimeMillis())
    ) {
        facet.withConnection() { conn ->
             val sql = when (facet.dbProvider) {
                 "postgresql" -> """
                     INSERT INTO metadata (session_id, user_email, key, value, timestamp)
                     VALUES (?, ?, ?, ?, ?)
                     ON CONFLICT (session_id, user_email, key)
                     DO UPDATE SET value = EXCLUDED.value, timestamp = EXCLUDED.timestamp
                     """.trimIndent()
                 else -> """
                     MERGE INTO metadata USING (VALUES(?, ?, ?, ?, ?)) AS vals(session_id, user_email, key, value, timestamp)
                     ON metadata.session_id = vals.session_id AND metadata.user_email = vals.user_email AND metadata.key = vals.key
                     WHEN MATCHED THEN UPDATE SET metadata.value = vals.value, metadata.timestamp = vals.timestamp
                     WHEN NOT MATCHED THEN INSERT VALUES vals.session_id, vals.user_email, vals.key, vals.value, vals.timestamp
                     """.trimIndent()
             }
             conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, sessionId)
                stmt.setString(2, userEmail)
                stmt.setString(3, key)
                 if (value == null) stmt.setNull(4, Types.VARCHAR) else stmt.setString(4, value)
                stmt.setTimestamp(5, timestamp)
                stmt.executeUpdate()
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(MetadataStorageDB::class.java)

        internal val facet = DatabaseFacet(
            name = "metadata",
             schema = { provider ->
                 val valueType = when (provider) {
                     "postgresql" -> "TEXT"
                     else -> "LONGVARCHAR"
                 }
                 listOf(
                     """
                     CREATE TABLE IF NOT EXISTS metadata (
                         session_id VARCHAR(255),
                         user_email VARCHAR(255),
                         key VARCHAR(255),
                         value $valueType,
                         timestamp TIMESTAMP,
                         PRIMARY KEY (session_id, user_email, key)
                     )
                     """.trimIndent()
                 )
             }
        )

    }
}