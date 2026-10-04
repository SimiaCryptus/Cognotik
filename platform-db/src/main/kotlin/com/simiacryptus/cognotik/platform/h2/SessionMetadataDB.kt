package com.simiacryptus.cognotik.platform.h2

import com.simiacryptus.cognotik.platform.service.SessionMetadataInterface
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionListEntry
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.SessionMetadataPatch
import com.simiacryptus.cognotik.platform.model.SessionQuery
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.ifSet
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.javatime.timestamp
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

class SessionMetadataDB : SessionMetadataInterface {

  object MetadataTable : Table("metadata") {
    val sessionId: Column<String> = varchar("session_id", 255)
    val userEmail: Column<String> = varchar("user_email", 255)
    val key: Column<String> = varchar("meta_key", 255)
    val value: Column<String?> = text("value").nullable()
    val timestamp: Column<Instant> = timestamp("timestamp")
    override val primaryKey = PrimaryKey(sessionId, userEmail, key)
  }

  init {
    ensureIndexes()
  }

  override fun getSessionName(user: User, session: Session): String {
    log.debug("Fetching session name for session: {}, user: {}", session, user.email)
    return tx {
      MetadataTable
        .selectAll()
        .where {
          (MetadataTable.sessionId eq session.sessionId) and
              (MetadataTable.userEmail eq user.email) and
              (MetadataTable.key eq "name")
        }
        .limit(1)
        .map { it[MetadataTable.value] ?: session.sessionId }
        .firstOrNull() ?: session.sessionId
    }
  }

  override fun setSessionName(user: User, session: Session, name: String) {
    log.info("Setting session name for session: {}, user: {} to '{}'", session, user.email, name)
    upsertMetadata(session.sessionId, user.email, "name", name)
    log.debug("Session name set successfully for session: {}", session)
  }

  override fun getMessageIds(user: User, session: Session): List<String> {
    log.debug("Fetching message IDs for session: {}, user: {}", session, user.email)
    return tx {
      MetadataTable
        .selectAll()
        .where {
          (MetadataTable.sessionId eq session.sessionId) and
              (MetadataTable.userEmail eq user.email) and
              (MetadataTable.key eq "message_ids")
        }
        .limit(1)
        .map { row -> parseMessageIds(row[MetadataTable.value]) }
        .firstOrNull() ?: emptyList()
    }
  }

  override fun setMessageIds(user: User, session: Session, ids: List<String>) {
    log.debug("Setting {} message IDs for session: {}, user: {}", ids.size, session, user.email)
    upsertMetadata(session.sessionId, user.email, "message_ids", ids.joinToString(","))
  }

  override fun setSessionTimestamp(
    user: User,
    session: Session,
    time: Instant
  ) {
    log.debug("Setting session timestamp for session: {}, user: {} to {}", session, user.email, time)
    upsertMetadata(
      session.sessionId,
      user.email,
      "session_time",
      time.toEpochMilli().toString(),
      time
    )
  }

  override fun getSessionTimestamp(user: User, session: Session): Instant? {
    log.debug("Fetching session time for session: {}, user: {}", session, user.email)
    return tx {
      MetadataTable
        .selectAll()
        .where {
          (MetadataTable.sessionId eq session.sessionId) and
              (MetadataTable.userEmail eq user.email) and
              (MetadataTable.key eq "session_time")
        }
        .limit(1)
        .map { row -> parseSessionTime(row, session.sessionId).toInstant() }
        .firstOrNull()
    }
  }

  @Deprecated("Use getSessionTimestamp", ReplaceWith("getSessionTimestamp(user, session)"))
  fun getSessionTime(user: User, session: Session): Instant? = getSessionTimestamp(user, session)

  override fun getSessionPath(user: User, session: Session): String? {
    log.debug("Fetching session path for session: {}, user: {}", session, user.email)
    return tx {
      MetadataTable
        .selectAll()
        .where {
          (MetadataTable.sessionId eq session.sessionId) and
              (MetadataTable.userEmail eq user.email) and
              (MetadataTable.key eq "path")
        }
        .limit(1)
        .map { it[MetadataTable.value] }
        .firstOrNull()
    }
  }

  override fun setSessionPath(user: User, session: Session, path: String?) {
    log.info("Setting session path for session: {}, user: {} to {}", session, user.email, path)
    upsertMetadata(session.sessionId, user.email, "path", path)
  }

  override fun exists(user: User, session: Session): Boolean = tx {
    MetadataTable
      .selectAll()
      .where {
        (MetadataTable.sessionId eq session.sessionId) and
            ((MetadataTable.userEmail eq user.email) or (MetadataTable.userEmail eq ""))
      }
      .limit(1)
      .any()
  }

  /** Sessions of [user] tagged with [path] (previously leaked other users' sessions). */
  override fun listSessionsByPath(user: User, path: String): List<String> {
    log.debug("Listing sessions for path: {}, user: {}", path, user.email)
    return tx {
      pathSessionIdQuery(user.email, path)
        .withDistinct()
        .map { it[MetadataTable.sessionId] }
    }.also { log.debug("Found {} sessions for path: {}", it.size, path) }
  }

  override fun listSessionsForUser(user: User): List<String> {
    log.debug("Listing sessions for user: {}", user.email)
    return tx { sessionIdsForUser(user.email).toList() }
      .also { log.debug("Found {} sessions for user: {}", it.size, user.email) }
  }

  @Deprecated("Use listSessionsByPath", ReplaceWith("listSessionsByPath(path)"))
  fun listSessions(user: User, path: String): List<String> = listSessionsByPath(user = user, path = path)

  @Deprecated("Use listSessionsForUser", ReplaceWith("listSessionsForUser(user)"))
  fun listSessions(user: User): List<String> = listSessionsForUser(user)


  override fun getSessionOwner(user: User, session: Session): String? {
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

  override fun setSessionOwner(session: Session, user: User, ownerId: String?) {
    log.info("setSessionOwner for session: {} to {}", session, ownerId)
    upsertMetadata(session.sessionId, "", "owner_id", ownerId)
  }

  override fun getSessionWorker(user: User, session: Session): String? {
    log.debug("Fetching session worker for session: {}", session)
    return tx {
      MetadataTable
        .selectAll()
        .where {
          (MetadataTable.sessionId eq session.sessionId) and
              (MetadataTable.key eq KEY_WORKER_ID) and
              (MetadataTable.userEmail eq "")
        }
        .limit(1)
        .map { it[MetadataTable.value] }
        .firstOrNull()
    }
  }

  override fun setSessionWorker(session: Session, user: User, workerId: String?) {
    log.info("setSessionWorker for session: {} to {}", session, workerId)
    // Worker assignment is user-agnostic, mirroring owner_id storage.
    upsertMetadata(session.sessionId, "", KEY_WORKER_ID, workerId)
  }

  override fun deleteSession(user: User, session: Session) {
    log.info("Deleting session: {}, user: {}", session, user.email)
    try {
      val deleted = tx {
        MetadataTable.deleteWhere {
          (MetadataTable.sessionId eq session.sessionId) and
              (MetadataTable.userEmail eq user.email)
        }
      }
      log.info("Deleted {} metadata row(s) for session: {} user: {}", deleted, session, user.email)
    } catch (e: Exception) {
      log.error("Failed to delete session: {} for user: {}", session, user.email, e)
      throw e
    }
  }

  override fun deleteAllForUser(user: User): Int {
    log.info("Deleting all sessions for user: {}", user.email)
    return tx {
      val count = sessionIdsForUser(user.email).size
      // "session in user's sessions AND user_email = user" is just "user_email = user".
      if (count > 0) MetadataTable.deleteWhere { MetadataTable.userEmail eq user.email }
      count
    }.also { log.info("Deleted {} session(s) for user: {}", it, user.email) }
  }

  /**
   * Field-wise update. Unlike the snapshot-style writer, `Patch.Set(null)`
   * clears a field rather than being indistinguishable from "unchanged"
   * (REVIEW.md §3.4).
   */
  override fun updateSessionMetadata(user: User, session: Session, patch: SessionMetadataPatch) {
    log.info("Patching session metadata for session: {}, user: {}", session, user.email)
    val userEmail = user.email
    val now = Instant.now()
    tx {
      patch.name.ifSet { upsertMetadata(session.sessionId, userEmail, "name", it, now) }
      patch.messageIds.ifSet {
        upsertMetadata(session.sessionId, userEmail, "message_ids", it.joinToString(","), now)
      }
      patch.sessionTime.ifSet { t ->
        upsertMetadata(session.sessionId, userEmail, "session_time", t?.toEpochMilli()?.toString(), t ?: now)
      }
      patch.ownerId.ifSet { upsertMetadata(session.sessionId, "", "owner_id", it, now) }
      patch.workerId.ifSet { upsertMetadata(session.sessionId, "", KEY_WORKER_ID, it, now) }
      patch.path.ifSet { upsertMetadata(session.sessionId, userEmail, "path", it, now) }
    }
  }


  override fun getSessionMetadata(user: User, session: Session): SessionMetadata {
    log.debug("Fetching unified session metadata for session: {}, user: {}", session, user.email)
    val rows = tx {
      MetadataTable
        .selectAll()
        .where {
          (MetadataTable.sessionId eq session.sessionId) and
              ((MetadataTable.userEmail eq user.email) or (MetadataTable.userEmail eq ""))
        }
        .toList()
    }
    return buildSessionMetadataMap(rows)[session.sessionId]?.copy(id = session)
      ?: SessionMetadata(id = session, workerId = null)
  }

  override fun listSessionMetadata(user: User): List<SessionMetadata> {
    log.debug("Bulk listing session metadata for user: {}", user.email)
    return tx {
      val rows = MetadataTable.selectAll().where { userScope(user.email) }.toList()
      buildSessionMetadataMap(rows).values.toList()
    }.also { log.debug("Loaded metadata for {} session(s) for user: {}", it.size, user.email) }
  }

  override fun listSessionMetadata(user: User, path: String): List<SessionMetadata> {
    log.debug("Bulk listing session metadata for path: {}, user: {}", path, user.email)
    return tx {
      val rows = MetadataTable
        .selectAll()
        .where {
          userScope(user.email) and
              (MetadataTable.sessionId inSubQuery pathSessionIdQuery(user.email, path))
        }
        .toList()
      buildSessionMetadataMap(rows).values.toList()
    }.also { log.debug("Loaded metadata for {} session(s) on path: {}", it.size, path) }
  }

  /**
   * Listing-page optimized: project only the columns required by the
   * sessions list and skip "message_ids" entirely (which can be large).
   */
  override fun listSessionEntries(user: User): List<SessionListEntry> {
    log.debug("Listing session entries (projection) for user: {}", user.email)
    return tx { loadEntries(userScope(user.email)) }
      .also { log.debug("Loaded {} session entries for user: {}", it.size, user.email) }
  }

  override fun listSessionEntries(user: User, path: String): List<SessionListEntry> {
    log.debug("Listing session entries (projection) for path: {}, user: {}", path, user.email)
    return tx {
      loadEntries(
        userScope(user.email) and
            (MetadataTable.sessionId inSubQuery pathSessionIdQuery(user.email, path))
      )
    }.also { log.debug("Loaded {} session entries for path: {}", it.size, path) }
  }

  /**
   * Single round trip: path / id restrictions are pushed into SQL; the
   * remaining (cheap) predicates and the sort run over the projected rows.
   */
  override fun querySessions(user: User, query: SessionQuery): List<SessionListEntry> {
    val email = user.email
    val ids = query.sessionIds
    val path = query.path
    val entries = if (ids != null) {
      getSessionEntries(user, ids).values.toList()
    } else tx {
      var scope: Op<Boolean> = userScope(email)
      if (path != null) {
        scope = scope and (MetadataTable.sessionId inSubQuery pathSessionIdQuery(email, path))
      }
      loadEntries(scope)
    }
    return entries.filter { query.matches(it) }.sortedWith(query.sort.comparator)
  }
   /** Counts matches without paying for a sort of the full result set. */
   override fun countSessions(user: User, query: SessionQuery): Int {
     val email = user.email
     val ids = query.sessionIds
     val path = query.path
     val entries = if (ids != null) {
       getSessionEntries(user, ids).values.toList()
     } else tx {
       var scope: Op<Boolean> = userScope(email)
       if (path != null) {
         scope = scope and (MetadataTable.sessionId inSubQuery pathSessionIdQuery(email, path))
       }
       loadEntries(scope)
     }
     return entries.count { query.matches(it) }
   }


  override fun getSessionEntries(user: User, sessionIds: Collection<String>): Map<String, SessionListEntry> {
    val ids = sessionIds.toSet()
    if (ids.isEmpty()) return emptyMap()
    val email = user.email
    return tx {
      ids.chunked(IN_LIST_CHUNK).flatMap { chunk ->
        loadEntries(
          (MetadataTable.sessionId inList chunk) and
              ((MetadataTable.userEmail eq email) or (MetadataTable.userEmail eq ""))
        )
      }
    }.associateBy { it.id.sessionId }
  }

  override fun listSessionPaths(user: User): List<String> = tx {
    MetadataTable
      .select(MetadataTable.value)
      .where { (MetadataTable.userEmail eq user.email) and (MetadataTable.key eq "path") }
      .mapNotNull { it[MetadataTable.value]?.takeIf { v -> v.isNotBlank() } }
  }.distinct().sorted()

  /**
   * Single-round-trip override of the interface's N+1 default. Session IDs with
   * no recorded metadata are intentionally omitted from the result.
   */
  override fun getSessionMetadataMap(
    user: User,
    sessionIds: Collection<String>
  ): Map<String, SessionMetadata> {
    val ids = sessionIds.toSet()
    if (ids.isEmpty()) return emptyMap()
    val userEmail = user.email
    log.debug("Bulk fetching session metadata map for {} session(s), user: {}", ids.size, userEmail)
    return tx {
      val rows = ids.chunked(IN_LIST_CHUNK).flatMap { chunk ->
        MetadataTable
          .selectAll()
          .where {
            (MetadataTable.sessionId inList chunk) and
                ((MetadataTable.userEmail eq userEmail) or (MetadataTable.userEmail eq ""))
          }
          .toList()
      }
      buildSessionMetadataMap(rows)
    }
  }

  // ---- Query building blocks ----

  /** Sub-select of the session ids [userEmail] has authored rows for. */
  private fun userSessionIdQuery(userEmail: String) =
    MetadataTable.select(MetadataTable.sessionId).where { MetadataTable.userEmail eq userEmail }

  /** Sub-select of the user's session ids tagged with [path]. */
  private fun pathSessionIdQuery(userEmail: String, path: String) =
    MetadataTable.select(MetadataTable.sessionId).where {
      (MetadataTable.userEmail eq userEmail) and
          (MetadataTable.key eq "path") and
          (MetadataTable.value eq path)
    }

  /**
   * Rows visible to the user: their own rows, plus user-agnostic rows
   * (owner/worker) of sessions they own. Uses a sub-select rather than
   * materializing every id into an IN list (which also has a hard
   * bind-parameter limit on PostgreSQL).
   */
  private fun userScope(userEmail: String): Op<Boolean> =
    (MetadataTable.userEmail eq userEmail) or
        ((MetadataTable.userEmail eq "") and (MetadataTable.sessionId inSubQuery userSessionIdQuery(userEmail)))

  /** Load listing projections matching [scope]. Must be called inside a transaction. */
  private fun loadEntries(scope: Op<Boolean>): List<SessionListEntry> {
    val rows = MetadataTable
      .select(
        MetadataTable.sessionId,
        MetadataTable.key,
        MetadataTable.value,
        MetadataTable.timestamp,
      )
      .where { scope and (MetadataTable.key inList LIST_PROJECTION_KEYS) }
      .toList()
    return buildSessionListEntries(rows)
  }

  /** Must be called inside a transaction. */
  private fun sessionIdsForUser(userEmail: String): Set<String> =
    userSessionIdQuery(userEmail)
      .withDistinct()
      .map { it[MetadataTable.sessionId] }
      .toSet()

  private fun parseMessageIds(v: String?): List<String> =
    if (v.isNullOrEmpty()) emptyList() else v.split(",").filter { it.isNotEmpty() }

  private fun parseSessionTime(row: ResultRow, sid: String): Date {
    val v = row[MetadataTable.value]
    return try {
      if (v != null) Date(v.toLong()) else Date.from(row[MetadataTable.timestamp])
    } catch (e: NumberFormatException) {
      log.warn("Invalid session_time value '{}' for session: {}; falling back to row timestamp", v, sid)
      Date.from(row[MetadataTable.timestamp])
    }
  }

  private fun buildSessionMetadataMap(rows: List<ResultRow>): Map<String, SessionMetadata> {
    if (rows.isEmpty()) return emptyMap()
    data class Accum(
      var name: String? = null,
      var messageIds: List<String> = emptyList(),
      var sessionTime: Date? = null,
      var ownerId: String? = null,
      var workerId: String? = null,
      var path: String? = null,
    )

    val grouped = LinkedHashMap<String, Accum>()
    for (row in rows) {
      val sid = row[MetadataTable.sessionId]
      val acc = grouped.getOrPut(sid) { Accum() }
      val v = row[MetadataTable.value]
      when (row[MetadataTable.key]) {
        "name" -> acc.name = v
        "message_ids" -> acc.messageIds = parseMessageIds(v)
        "session_time" -> acc.sessionTime = parseSessionTime(row, sid)
        "owner_id" -> acc.ownerId = v
        KEY_WORKER_ID -> acc.workerId = v
        "path" -> acc.path = v
      }
    }
    val out = LinkedHashMap<String, SessionMetadata>()
    for ((sid, acc) in grouped) {
      // A single malformed id must not break the whole listing.
      val session = Session.tryParse(sid) ?: continue
      out[sid] = SessionMetadata(
        id = session,
        name = acc.name,
        messageIds = acc.messageIds,
        sessionTime = acc.sessionTime,
        ownerId = acc.ownerId,
        workerId = acc.workerId,
        path = acc.path,
      )
    }
    return out
  }

  /** Lightweight equivalent of [buildSessionMetadataMap]; skips message_ids entirely. */
  private fun buildSessionListEntries(rows: List<ResultRow>): List<SessionListEntry> {
    if (rows.isEmpty()) return emptyList()
    data class Accum(
      var name: String? = null,
      var sessionTime: Date? = null,
      var ownerId: String? = null,
      var workerId: String? = null,
      var path: String? = null,
    )

    val grouped = LinkedHashMap<String, Accum>()
    for (row in rows) {
      val sid = row[MetadataTable.sessionId]
      val acc = grouped.getOrPut(sid) { Accum() }
      val v = row[MetadataTable.value]
      when (row[MetadataTable.key]) {
        "name" -> acc.name = v
        "session_time" -> acc.sessionTime = parseSessionTime(row, sid)
        "owner_id" -> acc.ownerId = v
        KEY_WORKER_ID -> acc.workerId = v
        "path" -> acc.path = v
      }
    }
    return grouped.mapNotNull { (sid, acc) ->
      val session = Session.tryParse(sid) ?: return@mapNotNull null
      SessionListEntry(
        id = session,
        name = acc.name,
        sessionTime = acc.sessionTime,
        ownerId = acc.ownerId,
        workerId = acc.workerId,
        path = acc.path,
      )
    }
  }

  /**
   * Upsert implemented with Exposed DSL: try UPDATE first, then INSERT if no
   * row was affected. Portable across H2 and PostgreSQL.
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
      log.info(
        "Error upserting metadata (session={}, user={}, key={}): {}",
        sessionId, userEmail, keyName, e.message, e
      )
      throw e
    }
  }

  private fun <T> tx(block: () -> T): T = transaction(ExposedDatabase.get(facet)) { block() }

  companion object {
    private val log = LoggerFactory.getLogger(SessionMetadataDB::class.java)

    /** Metadata key holding the worker/agent currently assigned to a session. */
    internal const val KEY_WORKER_ID = "worker_id"

    /** Max ids per IN (...) list; keeps well below driver bind-parameter limits. */
    private const val IN_LIST_CHUNK = 1000

    /** Keys required by the sessions-list projection (message_ids deliberately excluded). */
    private val LIST_PROJECTION_KEYS =
      listOf("name", "session_time", "owner_id", KEY_WORKER_ID, "path")

    /**
     * Secondary indexes. Created after the table exists (ExposedDatabase.get runs
     * SchemaUtils.create) and each in its own transaction, so a failure can never
     * abort schema creation on PostgreSQL. `value` is deliberately not indexed:
     * it holds large message-id lists that exceed btree row limits.
     */
    private val INDEX_DDL = listOf(
      "CREATE INDEX IF NOT EXISTS idx_metadata_user_session ON metadata(user_email, session_id)",
      "CREATE INDEX IF NOT EXISTS idx_metadata_key_user ON metadata(meta_key, user_email)",
    )
    private val indexesEnsured = AtomicBoolean(false)

    private fun ensureIndexes() {
      if (!indexesEnsured.compareAndSet(false, true)) return
      for (ddl in INDEX_DDL) {
        try {
          transaction(ExposedDatabase.get(facet)) { exec(ddl) }
        } catch (e: Exception) {
          log.warn("Failed to create metadata index [{}]: {}", ddl, e.message, e)
        }
      }
    }

    internal val facet by lazy {
      DatabaseFacet(
        name = "metadata",
        tables = listOf(MetadataTable),
      )
    }
  }
}