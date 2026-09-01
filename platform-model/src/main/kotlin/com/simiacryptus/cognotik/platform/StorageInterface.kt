package com.simiacryptus.cognotik.platform

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
  val metadataStorage: MetadataStorageInterface?
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
  fun deleteSession(user: User?, session: Session)

  /**
   * Deletes a session, reporting whether anything was removed.
   *
   * @return true if the session existed and was deleted, false if it did not exist
   */
  fun deleteSessionIfExists(user: User?, session: Session): Boolean {
    deleteSession(user, session)
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
 * Resolves [path] beneath [root], rejecting traversal outside the session root.
 */
private fun resolveSessionFile(root: File, path: String): File {
  val base = root.canonicalFile
  val target = File(base, path).canonicalFile
  require(target.toPath().startsWith(base.toPath())) { "Path escapes session directory: $path" }
  return target
}