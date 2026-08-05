package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.Page
import com.simiacryptus.cognotik.platform.model.PageResult
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.SessionListEntry
import com.simiacryptus.cognotik.platform.model.SessionMetadata
import com.simiacryptus.cognotik.platform.model.SessionMetadataPatch
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.asPatch
import com.simiacryptus.cognotik.platform.model.ifSet
import com.simiacryptus.cognotik.platform.model.paginate
import java.time.Instant
import java.util.*


/**
 * Interface for managing session metadata storage operations.
 *
 * This interface provides methods for storing and retrieving session-related metadata
 * such as session names, message IDs, and timestamps. Implementations of this interface
 * can use different storage backends (e.g., database, file system, memory) to persist
 * session information.
 *
 * Implementations must be thread-safe and may block. Read-modify-write helpers
 * ([setSessionMetadata] / [updateSessionMetadata]) are *not* guaranteed atomic unless
 * an implementation says so.
 *
 * Bulk/listing default implementations here are deliberately naive (N+1). See
 * [AbstractMetadataStorage] for the same fallbacks in an explicitly opt-in base class;
 * DB-backed implementations should override them.
 */

interface MetadataStorageInterface {
  /**
   * Retrieves the display name for a session.
   *
   * @param user The user associated with the session, or null for anonymous sessions
   * @param session The session object containing the session ID
   * @return The session name if set, otherwise returns the session ID as default
   */
  fun getSessionName(user: User?, session: Session): String

  /**
   * Sets or updates the display name for a session.
   *
   * @param user The user associated with the session, or null for anonymous sessions
   * @param session The session object containing the session ID
   * @param name The new name to assign to the session
   */
  fun setSessionName(user: User?, session: Session, name: String)

  /**
   * Retrieves the list of message IDs associated with a session.
   *
   * Message IDs typically represent individual messages or interactions within a session,
   * allowing for message history tracking and retrieval.
   *
   * @param user The user associated with the session, or null for anonymous sessions
   * @param session The session object containing the session ID
   * @return A list of message IDs in the order they were stored, or an empty list if none exist
   */
  fun getMessageIds(user: User?, session: Session): List<String>

  /**
   * Sets or updates the list of message IDs for a session.
   *
   * This method replaces any existing message IDs with the provided list.
   *
   * @param user The user associated with the session, or null for anonymous sessions
   * @param session The session object containing the session ID
   * @param ids The list of message IDs to store for this session
   */
  fun setMessageIds(user: User?, session: Session, ids: List<String>)

  /**
   * Retrieves the timestamp associated with a session.
   *
   * This typically represents when the session was created or last modified.
   *
   * @param user The user associated with the session, or null for anonymous sessions
   * @param session The session object containing the session ID
   * @return The session timestamp, or null if not set. Implementations may return
   *         a default value (e.g., current time) instead of null.
   */
  @Deprecated(
    "java.util.Date is mutable and inconsistent with the rest of the platform; use getSessionTimestamp.",
    ReplaceWith("getSessionTimestamp(user, session)")
  )
  fun getSessionTime(user: User?, session: Session): Instant? = getSessionTimestamp(user, session)

  /**
   * Sets or updates the timestamp for a session.
   *
   * @param user The user associated with the session, or null for anonymous sessions
   * @param session The session object containing the session ID
   * @param time The timestamp to associate with the session
   */
  @Deprecated(
    "java.util.Date is mutable and inconsistent with the rest of the platform; use setSessionTimestamp.",
    ReplaceWith("setSessionTimestamp(user, session, time)")
  )
  fun setSessionTime(user: User?, session: Session, time: Instant) : Unit = setSessionTimestamp(user, session, time)

  /** `java.time` accessor for the session timestamp. */
  @Suppress("DEPRECATION")
  fun getSessionTimestamp(user: User?, session: Session): Instant? =
    getSessionTime(user, session)

  /** `java.time` mutator for the session timestamp. */
  @Suppress("DEPRECATION")
  fun setSessionTimestamp(user: User?, session: Session, time: Instant) =
    setSessionTime(user, session, time)

  /**
   * Lists all session IDs associated with a specific path.
   *
   * @param path The path to search for associated sessions
   * @return A list of session IDs that are associated with the given path
   */
  @Deprecated(
    "Ambiguous overload; use listSessionsByPath.",
    ReplaceWith("listSessionsByPath(path)")
  )
  fun listSessions(path: String): List<String> = listSessionsByPath(path)

  /**
   * Lists all session IDs associated with a specific user.
   *
   * @param user The user whose sessions should be listed
   * @return A list of session IDs associated with the given user
   */
  @Deprecated(
    "Ambiguous overload; use listSessionsForUser.",
    ReplaceWith("listSessionsForUser(user)")
  )
  fun listSessions(user: User): List<String> = listSessionsForUser(user)

  /** Lists all session IDs associated with [path]. */
  @Suppress("DEPRECATION")
  fun listSessionsByPath(path: String): List<String> = listSessions(path)

  /** Lists all session IDs associated with [user]. */
  @Suppress("DEPRECATION")
  fun listSessionsForUser(user: User): List<String> = listSessions(user)

  /**
   * Retrieves the owner ID associated with a session.
   *
   * @param session The session object containing the session ID
   * @return The owner ID if set, or null if the session has no recorded owner
   */
  fun getSessionOwner(session: Session): String?

  /**
   * Sets or updates the owner ID for a session.
   *
   * @param session The session object containing the session ID
   * @param ownerId The owner identifier to associate with the session, or null to clear it
   */
  fun setSessionOwner(session: Session, ownerId: String?)

  /** User-scoped overload, for signature consistency with the rest of the interface. */
  fun getSessionOwner(user: User?, session: Session): String? = getSessionOwner(session)

  /** User-scoped overload, for signature consistency with the rest of the interface. */
  fun setSessionOwner(user: User?, session: Session, ownerId: String?) = setSessionOwner(session, ownerId)

  /**
   * Retrieves the worker (`ip:port`) currently serving a session.
   *
   * @return the worker identifier, or null if the session is not assigned to a worker
   */
  fun getSessionWorker(session: Session): String?

  /**
   * Assigns (or clears) the worker currently serving a session.
   *
   * @param ownerId the worker identifier (`ip:port`), or null to clear the assignment
   */
  fun setSessionWorker(session: Session, ownerId: String?)

  /** User-scoped overload of [getSessionWorker]. */
  fun getSessionWorker(user: User?, session: Session): String? = getSessionWorker(session)

  /** User-scoped overload of [setSessionWorker]. */
  fun setSessionWorker(user: User?, session: Session, workerId: String?) = setSessionWorker(session, workerId)

  /**
   * Retrieves the application path associated with a session.
   *
   * Default returns null so existing implementations remain source-compatible;
   * implementations that persist a path MUST override this, otherwise
   * [SessionMetadata.path] is write-only (the bug reported in REVIEW.md §3.4).
   */
  fun getSessionPath(user: User?, session: Session): String? = null

  /**
   * Sets or clears the application path associated with a session.
   *
   * Default is a no-op for source compatibility; implementations that support
   * paths MUST override this.
   */
  fun setSessionPath(user: User?, session: Session, path: String?) {
    // no-op by default
  }

  /**
   * @return true if any metadata is recorded for [session].
   *
   * The default heuristic (a recorded timestamp) exists only for compatibility;
   * implementations should override with a real existence check so that callers
   * can distinguish "absent" from "default".
   */
  fun exists(user: User?, session: Session): Boolean = getSessionTimestamp(user, session) != null

  /**
   * Deletes all metadata associated with a session.
   *
   * @param user The user associated with the session, or null for anonymous sessions
   * @param session The session object containing the session ID to delete
   */
  fun deleteSession(user: User?, session: Session)

  /**
   * Deletes metadata for every session belonging to [user].
   *
   * @return the number of sessions deleted
   */
  fun deleteAllForUser(user: User): Int {
    val ids = listSessionsForUser(user)
    ids.forEach { deleteSession(user, Session(it)) }
    return ids.size
  }

  /**
   * Retrieves all metadata associated with a session as a unified data structure.
   *
   * @param user The user associated with the session, or null for anonymous sessions
   * @param session The session object containing the session ID
   * @return A [SessionMetadata] object containing all known metadata for the session
   */
  fun getSessionMetadata(user: User?, session: Session): SessionMetadata {
    return SessionMetadata(
      id = session,
      name = getSessionName(user, session),
      messageIds = getMessageIds(user, session),
      sessionTime = getSessionTimestamp(user, session)?.let { Date.from(it) },
      ownerId = getSessionOwner(session),
      workerId = getSessionWorker(session),
      path = getSessionPath(user, session),
    )
  }

  /**
   * Sets multiple metadata fields for a session in a single call.
   *
   * Only non-null fields in the provided [SessionMetadata] will be updated, and
   * [SessionMetadata.messageIds] is written only when non-empty — which means this
   * method cannot clear a value.
   *
   * @deprecated Use [updateSessionMetadata] with an explicit [SessionMetadataPatch]
   */
  @Deprecated(
    "Null-means-skip cannot express 'clear this field'; use updateSessionMetadata with a patch.",
    ReplaceWith("updateSessionMetadata(user, session, metadata.asPatch())")
  )
  fun setSessionMetadata(user: User?, session: Session, metadata: SessionMetadata) =
    updateSessionMetadata(user, session, metadata.asPatch())

  /**
   * Applies an explicit, field-wise patch to a session's metadata.
   *
   * Unlike [setSessionMetadata], "absent" and "set to null" are distinguishable,
   * so fields can be cleared.
   */
  fun updateSessionMetadata(user: User?, session: Session, patch: SessionMetadataPatch) {
    patch.name.ifSet { setSessionName(user, session, it ?: session.sessionId) }
    patch.messageIds.ifSet { setMessageIds(user, session, it) }
    patch.sessionTime.ifSet { if (it != null) setSessionTimestamp(user, session, it) }
    patch.ownerId.ifSet { setSessionOwner(session, it) }
    patch.workerId.ifSet { setSessionWorker(session, it) }
    patch.path.ifSet { setSessionPath(user, session, it) }
  }

  /**
   * Bulk-fetch metadata for all sessions belonging to a user.
   *
   * The default implementation performs per-session retrieval (N+1); DB-backed
   * implementations should override this for efficiency.
   *
   * @param user The user whose sessions should be listed
   * @return A list of [SessionMetadata] objects, one per session
   */
  fun listSessionMetadata(user: User): List<SessionMetadata> {
    return listSessionsForUser(user).map { sessionId ->
      getSessionMetadata(user, Session(sessionId))
    }
  }

  /**
   * Bulk-fetch metadata for all sessions associated with a path.
   *
   * The default implementation performs per-session retrieval (N+1); DB-backed
   * implementations should override this for efficiency.
   *
   * @param path The path to search for associated sessions
   * @return A list of [SessionMetadata] objects, one per session
   */
  fun listSessionMetadata(path: String): List<SessionMetadata> {
    return listSessionsByPath(path).map { sessionId ->
      getSessionMetadata(null, Session(sessionId))
    }
  }

  /**
   * Bulk-fetch metadata for an explicit set of session IDs.
   *
   * @param user The user associated with the sessions, or null for anonymous sessions
   * @param sessionIds The session IDs to fetch metadata for
   * @return A list of [SessionMetadata] objects in the same order as [sessionIds];
   *         sessions with no recorded metadata are returned with default field values
   */
  @Deprecated(
    "Cannot express 'no such session'; use getSessionMetadataMap.",
    ReplaceWith("getSessionMetadataMap(user, sessionIds).values.toList()")
  )
  fun getSessionMetadataBulk(user: User?, sessionIds: Collection<String>): List<SessionMetadata> =
    sessionIds.map { getSessionMetadata(user, Session(it)) }

  /**
   * Bulk-fetch metadata for an explicit set of session IDs.
   *
   * @return a map keyed by session id; ids with no recorded metadata are omitted,
   *         so callers can distinguish "absent" from "default"
   */
  fun getSessionMetadataMap(user: User?, sessionIds: Collection<String>): Map<String, SessionMetadata> {
    return sessionIds.distinct().mapNotNull { sessionId ->
      val session = Session(sessionId)
      if (!exists(user, session)) null else sessionId to getSessionMetadata(user, session)
    }.toMap()
  }

  /**
   * Bulk-fetch only the metadata fields needed to render a sessions listing page.
   *
   * The default implementation falls back to [listSessionMetadata]; DB-backed
   * implementations should override this to project only the columns needed.
   *
   * @param user The user whose sessions should be listed
   * @return Lightweight list of session entries
   */
  fun listSessionEntries(user: User): List<SessionListEntry> {
    return listSessionMetadata(user).map { it.toEntry() }
  }

  /**
   * Bulk-fetch only the metadata fields needed to render a sessions listing
   * page filtered by path. The default implementation falls back to
   * [listSessionMetadata]; DB-backed implementations should override this
   * to project only the columns actually needed.
   */
  fun listSessionEntries(path: String): List<SessionListEntry> {
    return listSessionMetadata(path).map { it.toEntry() }
  }

  /** Paged variant of [listSessionEntries]; default pages in memory. */
  fun listSessionEntries(user: User, page: Page): PageResult<SessionListEntry> =
    listSessionEntries(user).paginate(page)

  /** Paged variant of [listSessionEntries]; default pages in memory. */
  fun listSessionEntries(path: String, page: Page): PageResult<SessionListEntry> =
    listSessionEntries(path).paginate(page)
}