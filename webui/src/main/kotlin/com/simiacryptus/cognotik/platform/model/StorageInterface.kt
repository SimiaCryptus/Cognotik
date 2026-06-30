package com.simiacryptus.cognotik.platform.model

import com.simiacryptus.cognotik.platform.model.Session
import java.io.File
import java.util.*

/**
 * Interface defining storage operations for managing sessions, messages, and associated data.
 *
 * This interface provides methods for:
 * - Session management (creation, listing, deletion)
 * - Message storage and retrieval
 * - File system operations for session data
 * - User-specific data management
 *
 * Implementations should handle both global sessions (accessible to all users) and
 * user-specific sessions with appropriate access controls.
 */

interface StorageInterface {
  /**
   * Retrieves all messages for a given session.
   *
   * @param user The user requesting the messages, or null for global sessions
   * @param session The session identifier for which to retrieve messages
   * @return A LinkedHashMap of message IDs to message content, preserving insertion order
   * @throws IllegalArgumentException if the session ID is invalid
   */

  fun getMessages(
    user: User?,
    session: Session
  ): LinkedHashMap<String, String>

  /**
   * Gets the directory path for a specific session.
   *
   * This method may return a cached path if available, otherwise delegates to getDataDir.
   *
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @return The File object representing the session directory
   */

  fun getUserDir(
    user: User?,
    session: Session
  ): File

  /**
   * Gets the data directory for a specific session.
   *
   * The directory structure is determined by the session ID format:
   * - "G-{date}-{id}" for global sessions
   * - "U-{date}-{id}" for user sessions
   *
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @return The File object representing the data directory
   * @throws IllegalArgumentException if the session ID format is invalid
   */

  fun getSystemDir(
    user: User?,
    session: Session
  ): File

  /**
   * Gets the display name for a session.
   *
   * @deprecated Use metadataStorage instead for metadata operations
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @return The session name as a String
   */

  @Deprecated("Use metadataStorage instead")
  fun getSessionName(
    user: User?,
    session: Session
  ): String

  /**
   * Gets the creation or last modification time of a session.
   *
   * @deprecated Use metadataStorage instead for metadata operations
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @return The session timestamp, or null if not available
   */

  @Deprecated("Use metadataStorage instead")
  fun getSessionTime(
    user: User?,
    session: Session
  ): Date?

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

  fun listSessions(
    user: User?,
    path: String,
  ): List<Session>

  /**
   * Saves an object as JSON to a file within a session's directory.
   *
   * @param T The type of the object to save
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @param filename The name of the file to save (relative to session directory)
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

  /**
   * Lists sessions in a specific directory.
   *
   * @deprecated Use metadataStorage instead for listing operations
   * @param dir The directory to search for sessions
   * @param path The path filter for sessions
   * @return A list of session identifiers as strings
   */

  @Deprecated("Use metadataStorage instead")
  fun listSessions(dir: File, path: String): List<String>

  /**
   * Gets the root directory for a user's data.
   *
   * @param user The user whose root directory to retrieve
   * @return The File object representing the user's root directory
   * @throws IllegalArgumentException if user is null or has no email
   */
  fun userRoot(user: User?): File

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
   * Gets the list of message IDs for a session.
   *
   * @deprecated Use metadataStorage instead for metadata operations
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @return A list of message IDs in order
   */

  @Deprecated("Use metadataStorage instead")
  fun getMessageIds(
    user: User?,
    session: Session
  ): List<String>

  /**
   * Sets the list of message IDs for a session.
   *
   * @deprecated Use metadataStorage instead for metadata operations
   * @param user The user owning the session, or null for global sessions
   * @param session The session identifier
   * @param ids The ordered list of message IDs to set
   */

  @Deprecated("Use metadataStorage instead")
  fun setMessageIds(
    user: User?,
    session: Session,
    ids: List<String>
  )

  /**
   * Companion object containing deprecated utility methods.
   *
   * These methods have been moved to the Session class and are maintained
   * here only for backward compatibility.
   */

  companion object {
    /**
     * @deprecated Use Session.long64() instead
     */
    @Deprecated("Use Session.long64() instead", ReplaceWith("Session.long64()"))
    fun long64() = Session.long64()

    /**
     * @deprecated Use Session.validateSessionId(session) instead
     */

    @Deprecated("Use Session.validateSessionId(session) instead", ReplaceWith("Session.validateSessionId(session)"))
    fun validateSessionId(session: Session) = Session.validateSessionId(session)

    /**
     * @deprecated Use Session.newGlobalID() instead
     */

    @Deprecated("Use Session.newGlobalID() instead", ReplaceWith("Session.newGlobalID()"))
    fun newGlobalID(): Session = Session.newGlobalID()

    /**
     * @deprecated Use Session.newUserID() instead
     */

    @Deprecated("Use Session.newUserID() instead", ReplaceWith("Session.newUserID()"))
    fun newUserID(): Session = Session.newUserID()

    /**
     * @deprecated Use Session.parseSessionID(sessionID) instead
     */

    @Deprecated("Use Session.parseSessionID(sessionID) instead", ReplaceWith("Session.parseSessionID(sessionID)"))
    fun parseSessionID(sessionID: String): Session = Session.parseSessionID(sessionID)

    /**
     * @deprecated Use Session.id2() instead
     */

    @Deprecated("Use Session.id2() instead")
    private fun id2() = Session.long64().filter {
      it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9'
    }.take(4)

  }
}