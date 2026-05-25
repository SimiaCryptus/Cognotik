package com.simiacryptus.cognotik.platform.hsql

import com.simiacryptus.cognotik.platform.model.MetadataStorageInterface
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.User
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

class MetadataStorageDB : MetadataStorageInterface {

    object MetadataTable : Table("metadata") {
        val sessionId: Column<String> = varchar("session_id", 255)
        val userEmail: Column<String> = varchar("user_email", 255)
        val key: Column<String> = varchar("meta_key", 255)
        val value: Column<String?> = text("value").nullable()
        val timestamp: Column<Instant> = timestamp("timestamp")
        override val primaryKey = PrimaryKey(sessionId, userEmail, key)
    }

    override fun getSessionName(user: User?, session: Session): String {
        log.debug("Fetching session name for session: {}, user: {}", session, user?.email)
        return tx {
            MetadataTable
                .selectAll()
                .where {
                    (MetadataTable.sessionId eq session.sessionId) and
                            (MetadataTable.userEmail eq (user?.email ?: "")) and
                            (MetadataTable.key eq "name")
                }
                .limit(1)
                .map { it[MetadataTable.value] ?: session.sessionId }
                .firstOrNull() ?: session.sessionId
        }
    }

    override fun setSessionName(user: User?, session: Session, name: String) {
        log.debug("Setting session name for session: {}, user: {} to '{}'", session, user?.email, name)
        upsertMetadata(session.sessionId, user?.email ?: "", "name", name)
        log.debug("Session name set successfully for session: {}", session)
    }

    override fun getMessageIds(user: User?, session: Session): List<String> {
        log.debug("Fetching message IDs for session: {}, user: {}", session, user?.email)
        return tx {
            MetadataTable
                .selectAll()
                .where {
                    (MetadataTable.sessionId eq session.sessionId) and
                            (MetadataTable.userEmail eq (user?.email ?: "")) and
                            (MetadataTable.key eq "message_ids")
                }
                .limit(1)
                .map { row ->
                    val raw = row[MetadataTable.value]
                    if (raw.isNullOrEmpty()) emptyList()
                    else raw.split(",").filter { it.isNotEmpty() }
                }
                .firstOrNull() ?: emptyList()
        }
    }

    override fun setMessageIds(user: User?, session: Session, ids: List<String>) {
        log.debug("Setting {} message IDs for session: {}, user: {}", ids.size, session, user?.email)
        upsertMetadata(session.sessionId, user?.email ?: "", "message_ids", ids.joinToString(","))
    }

    override fun getSessionTime(user: User?, session: Session): Date? {
        log.debug("Fetching session time for session: {}, user: {}", session, user?.email)
        return tx {
            MetadataTable
                .selectAll()
                .where {
                    (MetadataTable.sessionId eq session.sessionId) and
                            (MetadataTable.userEmail eq (user?.email ?: "")) and
                            (MetadataTable.key eq "session_time")
                }
                .limit(1)
                .map { row ->
                    val time = row[MetadataTable.value]
                    try {
                        if (time != null) Date(time.toLong())
                        else Date.from(row[MetadataTable.timestamp])
                    } catch (e: NumberFormatException) {
                        log.warn(
                            "Invalid session time value '{}' for session: {}, user: {}; falling back to row timestamp",
                            time, session, user?.email, e
                        )
                        Date.from(row[MetadataTable.timestamp])
                    }
                }
                .firstOrNull()
        }
    }

    override fun setSessionTime(user: User?, session: Session, time: Date) {
        log.debug("Setting session time for session: {}, user: {} to {}", session, user?.email, time)
        upsertMetadata(
            session.sessionId,
            user?.email ?: "",
            "session_time",
            time.time.toString(),
            time.toInstant()
        )
    }

    override fun listSessions(path: String): List<String> {
        log.debug("Listing sessions for path: {}", path)
        return tx {
            MetadataTable
                .select(MetadataTable.sessionId)
                .where {
                    (MetadataTable.value eq path) and (MetadataTable.key eq "path")
                }
                .withDistinct()
                .map { it[MetadataTable.sessionId] }
        }.also { log.debug("Found {} sessions for path: {}", it.size, path) }
    }

    override fun listSessions(user: User): List<String> {
        log.debug("Listing sessions for user: {}", user.email)
        return tx {
            MetadataTable
                .select(MetadataTable.sessionId)
                .where { MetadataTable.userEmail eq user.email }
                .withDistinct()
                .map { it[MetadataTable.sessionId] }
        }.also { log.debug("Found {} sessions for user: {}", it.size, user.email) }
    }

    override fun getSessionOwner(session: Session): String? {
        log.debug("Fetching session owner for session: {}", session)
        return tx {
            MetadataTable
                .selectAll()
                .where {
                    (MetadataTable.sessionId eq session.sessionId) and
                            (MetadataTable.key eq "owner_id") and
                            (MetadataTable.userEmail eq "")
                }
                .limit(1)
                .map { it[MetadataTable.value] }
                .firstOrNull()
        }
    }

    override fun setSessionOwner(session: Session, ownerId: String?) {
        log.debug("Setting session owner for session: {} to {}", session, ownerId)
        upsertMetadata(session.sessionId, "", "owner_id", ownerId)
    }

    override fun deleteSession(user: User?, session: Session) {
        log.info("Deleting session: {}, user: {}", session, user?.email)
        try {
            val deleted = tx {
                MetadataTable.deleteWhere {
                    (MetadataTable.sessionId eq session.sessionId) and
                            (MetadataTable.userEmail eq (user?.email ?: ""))
                }
            }
            log.info(
                "Deleted {} metadata row(s) for session: {} user: {}",
                deleted, session, user?.email ?: "anonymous"
            )
        } catch (e: Exception) {
            log.error("Failed to delete session: {} for user: {}", session, user?.email ?: "anonymous", e)
            throw e
        }
    }

    override fun getSessionMetadata(user: User?, session: Session): SessionMetadata {
        log.debug("Fetching unified session metadata for session: {}, user: {}", session, user?.email)
        return tx {
            val userEmail = user?.email ?: ""
            val rows: List<ResultRow> = MetadataTable
                .selectAll()
                .where {
                    (MetadataTable.sessionId eq session.sessionId) and
                            ((MetadataTable.userEmail eq userEmail) or (MetadataTable.userEmail eq ""))
                }
                .toList()

            var name: String? = null
            var messageIds: List<String> = emptyList()
            var sessionTime: Date? = null
            var ownerId: String? = null
            var path: String? = null
            for (row in rows) {
                val k = row[MetadataTable.key]
                val v = row[MetadataTable.value]
                when (k) {
                    "name" -> name = v
                    "message_ids" -> messageIds =
                        if (v.isNullOrEmpty()) emptyList()
                        else v.split(",").filter { it.isNotEmpty() }

                    "session_time" -> sessionTime = try {
                        if (v != null) Date(v.toLong())
                        else Date.from(row[MetadataTable.timestamp])
                    } catch (e: Exception) {
                        log.warn(
                            "Invalid session_time value '{}' for session: {}, user: {}; falling back to row timestamp",
                            v, session, user?.email, e
                        )
                        Date.from(row[MetadataTable.timestamp])
                    }

                    "owner_id" -> ownerId = v
                    "path" -> path = v
                }
            }
            SessionMetadata(
                id = session,
                name = name,
                messageIds = messageIds,
                sessionTime = sessionTime,
                ownerId = ownerId,
                path = path,
            )
        }
    }

    override fun setSessionMetadata(user: User?, session: Session, metadata: SessionMetadata) {
        log.debug("Setting unified session metadata for session: {}, user: {}", session, user?.email)
        val userEmail = user?.email ?: ""
        val now = Instant.now()
        metadata.name?.let { upsertMetadata(session.sessionId, userEmail, "name", it, now) }
        if (metadata.messageIds.isNotEmpty()) {
            upsertMetadata(session.sessionId, userEmail, "message_ids", metadata.messageIds.joinToString(","), now)
        }
        metadata.sessionTime?.let {
            upsertMetadata(session.sessionId, userEmail, "session_time", it.time.toString(), it.toInstant())
        }
        metadata.ownerId?.let { upsertMetadata(session.sessionId, "", "owner_id", it, now) }
        metadata.path?.let { upsertMetadata(session.sessionId, userEmail, "path", it, now) }
        log.debug("Unified session metadata set successfully for session: {}", session)
    }

    /**
     * Upsert implemented with Exposed DSL: try UPDATE first, then INSERT if no
     * row was affected. This works portably across HSQL and PostgreSQL without
     * dialect-specific SQL.
     */
    private fun upsertMetadata(
        sessionId: String,
        userEmail: String,
        keyName: String,
        value: String?,
        timestamp: Instant = Instant.now()
    ) {
        try {
            tx {
                val updated = MetadataTable.update({
                    (MetadataTable.sessionId eq sessionId) and
                            (MetadataTable.userEmail eq userEmail) and
                            (MetadataTable.key eq keyName)
                }) {
                    it[MetadataTable.value] = value
                    it[MetadataTable.timestamp] = timestamp
                }
                if (updated == 0) {
                    try {
                        MetadataTable.insert {
                            it[MetadataTable.sessionId] = sessionId
                            it[MetadataTable.userEmail] = userEmail
                            it[MetadataTable.key] = keyName
                            it[MetadataTable.value] = value
                            it[MetadataTable.timestamp] = timestamp
                        }
                    } catch (e: Exception) {
                        // Race with a concurrent insert: retry the update.
                        log.debug(
                            "Insert race detected for metadata (session={}, user={}, key={}); retrying update: {}",
                            sessionId, userEmail, keyName, e.message
                        )
                        val retried = MetadataTable.update({
                            (MetadataTable.sessionId eq sessionId) and
                                    (MetadataTable.userEmail eq userEmail) and
                                    (MetadataTable.key eq keyName)
                        }) {
                            it[MetadataTable.value] = value
                            it[MetadataTable.timestamp] = timestamp
                        }
                        if (retried == 0) {
                            log.error(
                                "Failed to upsert metadata after insert race (session={}, user={}, key={})",
                                sessionId, userEmail, keyName, e
                            )
                            throw e
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error(
                "Error upserting metadata (session={}, user={}, key={}): {}",
                sessionId, userEmail, keyName, e.message, e
            )
            throw e
        }
    }

    private fun <T> tx(block: () -> T): T = transaction(ExposedDatabase.get(facet)) { block() }

    companion object {
        private val log = LoggerFactory.getLogger(MetadataStorageDB::class.java)

        internal val facet = DatabaseFacet(
            name = "metadata",
            schema = { provider ->
                // Portable DDL kept inline to avoid invoking Exposed's
                // schema generator before a Database is connected. The
                // shape mirrors MetadataTable exactly.
                val textType = if (provider == "postgresql") "TEXT" else "CLOB"
                listOf(
                    // Migration: rename legacy "key" column to "meta_key" if present.
                    // This must run BEFORE the CREATE TABLE IF NOT EXISTS so that
                    // a legacy table with a "key" column is migrated rather than
                    // left alongside a new table definition. If the table doesn't
                    // exist yet, this is a no-op.
                    if (provider == "postgresql") {
                        """
                        DO ${'$'}${'$'}
                        BEGIN
                            IF EXISTS (
                                SELECT 1 FROM information_schema.tables
                                WHERE table_name = 'metadata'
                            ) AND EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_name = 'metadata' AND column_name = 'key'
                            ) AND NOT EXISTS (
                                SELECT 1 FROM information_schema.columns
                                WHERE table_name = 'metadata' AND column_name = 'meta_key'
                            ) THEN
                                ALTER TABLE metadata RENAME COLUMN "key" TO meta_key;
                            END IF;
                        END
                        ${'$'}${'$'};
                        """
                    } else {
                        // doesn't already exist). H2 doesn't support
                        // "ALTER COLUMN IF EXISTS ... RENAME TO" syntax, so
                        // we guard with information_schema checks via a
                        // conditional approach using separate statements.
                        // H2: rename legacy "key" column to meta_key if both
                        // the table and the legacy column exist (and meta_key
                        // doesn't already exist). Use ALTER TABLE ... ALTER COLUMN
                        // ... RENAME TO with proper H2 syntax. The "IF EXISTS"
                        // on ALTER COLUMN is supported in H2 2.x.
                        """
                        ALTER TABLE IF EXISTS metadata ALTER COLUMN IF EXISTS "key" RENAME TO meta_key
                        """
                    },
                    // Also handle the case where the legacy column exists without quotes
                    // (some older H2 versions stored it unquoted, treating it as identifier).
                    if (provider == "postgresql") {
                        "SELECT 1"
                    } else {
                        """
                        ALTER TABLE IF EXISTS metadata ALTER COLUMN IF EXISTS key RENAME TO meta_key
                        """
                    },
                    """
                 CREATE TABLE IF NOT EXISTS metadata (
                     session_id VARCHAR(255) NOT NULL,
                     user_email VARCHAR(255) NOT NULL,
                      meta_key VARCHAR(255) NOT NULL,
                     value $textType,
                     timestamp TIMESTAMP NOT NULL,
                      PRIMARY KEY (session_id, user_email, meta_key)
                 )
                 """,
                    "CREATE INDEX IF NOT EXISTS idx_metadata_user ON metadata(user_email)",
                    "CREATE INDEX IF NOT EXISTS idx_metadata_key_value ON metadata(meta_key, value)",
                )
            },
            tables = listOf(MetadataTable),
        )
    }
}