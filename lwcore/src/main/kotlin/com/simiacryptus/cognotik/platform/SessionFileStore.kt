package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User
import java.io.File

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