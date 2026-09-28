package com.simiacryptus.cognotik.platform.service

import com.simiacryptus.cognotik.platform.model.Page
import com.simiacryptus.cognotik.platform.model.PageResult
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.platform.model.paginate
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Interface defining storage operations for managing sessions, messages, and associated data.
 *
 * As of the REVIEW.md refactor this interface is a *composition* of narrower ports:
 * - [SessionFileStore] — legacy filesystem view (deprecated)
 * - [SessionContentStore] — backend-agnostic content access
 * - [MessageStore] — message persistence
 * - [JsonStore] — JSON blob persistence
 *
 * It retains session listing/deletion plus the deprecated metadata accessors,
 * which now default to delegating to [metadataStorage].
 *
 * Implementations should handle both global sessions (accessible to all users) and
 * user-specific sessions with appropriate access controls. Implementations must be
 * thread-safe and may block.
 */
interface StorageInterface : SessionFileStore, SessionContentStore, MessageStore, JsonStore {

  /**
   * Optional metadata backend used by the deprecated metadata accessors below.
   *
   * Implementations that still override those accessors need not provide this.
   */
  val metadataStorage: SessionMetadataInterface?
    get() = null

  /**
   * Lists all sessions accessible to a user at a given path.
   *
   * This includes both global sessions and user-specific sessions.
   * Invalid session IDs are filtered out.
   *
   * @param user The user requesting the list, or null to list only global sessions
   * @param path The path filter for sessions (implementation-specific)
   * @return A list of Session objects
   */
  @Suppress("DEPRECATION")
  fun listSessionsForUser(user: User?, path: String): List<Session> = listSessionsForUser(user, path)

  /**
   * Paged variant of [listSessionsForUser].
   *
   * The default implementation pages in memory; backends should override.
   */
  fun listSessionsForUser(user: User?, path: String, page: Page): PageResult<Session> =
    listSessionsForUser(user, path).paginate(page)

  /**
   * Deletes a session and all its associated data.
   *
   * This includes removing metadata and recursively deleting the session directory.
   *
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier to delete
   * @throws IllegalArgumentException if the session ID is invalid
   */
  fun deleteSessionData(user: User?, session: Session)

  /**
   * Deletes a session, reporting whether anything was removed.
   *
   * @return true if the session existed and was deleted, false if it did not exist
   */
  fun deleteSessionIfExists(user: User?, session: Session): Boolean {
    deleteSessionData(user, session)
    return true
  }

  @Suppress("DEPRECATION")
  override fun openRead(user: User?, session: Session, path: String): InputStream =
    FileInputStream(resolveSessionFile(getUserDir(user, session), path))

  @Suppress("DEPRECATION")
  override fun openWrite(user: User?, session: Session, path: String): OutputStream {
    val file = resolveSessionFile(getUserDir(user, session), path)
    file.parentFile?.mkdirs()
    return FileOutputStream(file)
  }

  @Suppress("DEPRECATION")
  override fun list(user: User?, session: Session, prefix: String): List<String> {
    val root = getUserDir(user, session)
    if (!root.exists()) return emptyList()
    val base = root.canonicalFile.toPath()
    return root.canonicalFile.walkTopDown()
      .filter { it.isFile }
      .map { base.relativize(it.toPath()).toString().replace(File.separatorChar, '/') }
      .filter { it.startsWith(prefix) }
      .toList()
  }

  @Suppress("DEPRECATION")
  override fun exists(user: User?, session: Session, path: String): Boolean =
    resolveSessionFile(getUserDir(user, session), path).exists()

  @Suppress("DEPRECATION")
  override fun delete(user: User?, session: Session, path: String): Boolean {
    val file = resolveSessionFile(getUserDir(user, session), path)
    return file.exists() && if (file.isDirectory) file.deleteRecursively() else file.delete()
  }
}

/**
 * JSON blob persistence scoped to a session.
 *
 * Extracted from `StorageInterface`; adds the missing read side so callers no
 * longer need raw filesystem access to read what [setJson] wrote (REVIEW.md §3.3).
 */
interface JsonStore {

  /**
   * Saves an object as JSON to a named slot within a session's storage.
   *
   * @param T The type of the object to save
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @param filename The name of the slot to save (relative to session storage)
   * @param settings The object to serialize and save
   * @return The same settings object that was saved
   */
  fun <T : Any> setJson(
    user: User?,
    session: Session,
    filename: String,
    settings: T
  ): T

  /**
   * Reads and deserializes a JSON slot previously written with [setJson].
   *
   * @return the deserialized value, or null if the slot does not exist
   * @throws UnsupportedOperationException if the implementation does not support reads yet
   */
  fun <T : Any> getJson(
    user: User?,
    session: Session,
    filename: String,
    type: Class<T>
  ): T? = throw UnsupportedOperationException("getJson is not implemented by ${this.javaClass.name}")
}

/**
 * Message persistence for a session.
 *
 * Extracted from `StorageInterface` (REVIEW.md §3.3), which owned four unrelated
 * responsibilities.
 *
 * Implementations must be thread-safe and may block. `updateMessage` is a
 * read-modify-write operation; implementations are expected to apply it atomically
 * with respect to concurrent calls for the same session.
 */
interface MessageStore {

  /**
   * Retrieves all messages for a session, in insertion order.
   *
   * @return an immutable-by-contract map of message id to content
   */
  @Suppress("DEPRECATION")
  fun getMessageMap(user: User?, session: Session): Map<String, String> =
    getMessageMap(user, session).toMap(LinkedHashMap())

  /**
   * Retrieves a single message.
   *
   * @return the message content, or null if no such message exists
   */
  fun getMessage(user: User?, session: Session, messageId: String): String? =
    getMessageMap(user, session)[messageId]

  /**
   * Updates or creates a message in the session's message store.
   *
   * If the message doesn't exist, it will be created and added to the message ID list.
   *
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @param messageId The unique identifier for the message
   * @param value The message content to store
   * @throws IllegalArgumentException if the session ID is invalid
   */
  fun updateMessage(
    user: User?,
    session: Session,
    messageId: String,
    value: String
  )
}

/**
 * Narrow, backend-agnostic content API for session data.
 *
 * This is the replacement for handing out `java.io.File` handles: it is
 * implementable over a local disk, S3/GCS, or a database blob table, and it
 * scopes callers to a single session (REVIEW.md §3.3, Phase 2 item 10).
 *
 * Paths are `/`-separated, relative to the session root, and must not escape it.
 */
interface SessionContentStore {

  /**
   * Opens a session-relative path for reading.
   *
   * @throws java.io.FileNotFoundException if the path does not exist
   * @throws IllegalArgumentException if [path] escapes the session root
   */
  fun openRead(user: User?, session: Session, path: String): InputStream

  /**
   * Opens a session-relative path for writing, creating parents as needed.
   *
   * @throws IllegalArgumentException if [path] escapes the session root
   */
  fun openWrite(user: User?, session: Session, path: String): OutputStream

  /**
   * Lists session-relative paths beginning with [prefix].
   */
  fun list(user: User?, session: Session, prefix: String = ""): List<String>

  /** @return true if the path exists within the session. */
  fun exists(user: User?, session: Session, path: String): Boolean

  /** @return true if something was deleted, false if the path did not exist. */
  fun delete(user: User?, session: Session, path: String): Boolean
}

/**
 * Local-filesystem view of session storage.
 *
 * Every member here leaks `java.io.File` into the port, which is what prevents an
 * object-store-backed implementation (REVIEW.md §3.3). They are retained for
 * compatibility; new code should use [SessionContentStore].
 */
interface SessionFileStore {

  /**
   * Gets the directory path for a specific session.
   *
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @return The File object representing the session directory
   */
  @Deprecated(
    "Exposes the local filesystem and grants callers unrestricted authority over the " +
        "directory; use SessionContentStore (openRead/openWrite/list/delete)."
  )
  fun getUserDir(
    user: User?,
    session: Session
  ): File

  /**
   * Gets the system/data directory for a specific session.
   *
   * The directory structure is determined by the session ID format:
   * - "G-{date}-{id}" for global sessions
   * - "U-{date}-{id}" for user sessions
   *
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @return The File object representing the system directory
   * @throws IllegalArgumentException if the session ID format is invalid
   */
  @Deprecated(
    "Exposes the local filesystem; use SessionContentStore for content access."
  )
  fun getSystemDir(
    user: User?,
    session: Session
  ): File

  /**
   * Gets the root directory for a user's data, with null-safety enforced by the type system.
   */
  @Suppress("DEPRECATION")
  fun userRootFor(user: User): File = userRootFor(user)
}
/**
 * Resolves [path] beneath [root], rejecting traversal outside the session root.
 */
private fun resolveSessionFile(root: File, path: String): File {
  val base = root.canonicalFile
  val target = File(base, path).canonicalFile
  require(target.toPath().startsWith(base.toPath())) { "Path escapes session directory: $path" }
  return target
}