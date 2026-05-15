package com.simiacryptus.cognotik.platform.model

import java.util.*

/**
 * Unified data structure representing all metadata associated with a session.
 *
 * This class aggregates the various metadata fields that can be stored for a session,
 * allowing them to be retrieved or updated in a single call. Fields are nullable to
 * support partial updates and to represent unset values.
 *
 * @property name The display name of the session, or null if not set
 * @property messageIds The list of message IDs associated with the session
 * @property sessionTime The timestamp associated with the session, or null if not set
 * @property ownerId The owner identifier for the session, or null if not set
 * @property path The path associated with the session, or null if not set
 * @property additional Any additional key-value metadata associated with the session
 */
data class SessionMetadata(
    val id: Session = Session.NULL,
    val name: String? = null,
    val messageIds: List<String> = emptyList(),
    val sessionTime: Date? = null,
    val ownerId: String? = null,
    val path: String? = null,
    val additional: Map<String, String> = emptyMap()
)


/**
 * Interface for managing session metadata storage operations.
 *
 * This interface provides methods for storing and retrieving session-related metadata
 * such as session names, message IDs, and timestamps. Implementations of this interface
 * can use different storage backends (e.g., database, file system, memory) to persist
 * session information.
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
    fun getSessionTime(user: User?, session: Session): Date?

    /**
     * Sets or updates the timestamp for a session.
     *
     * @param user The user associated with the session, or null for anonymous sessions
     * @param session The session object containing the session ID
     * @param time The timestamp to associate with the session
     */
    fun setSessionTime(user: User?, session: Session, time: Date)

    /**
     * Lists all session IDs associated with a specific path.
     *
     * This method is useful for finding all sessions that belong to a particular
     * application path or context.
     *
     * @param path The path to search for associated sessions
     * @return A list of session IDs that are associated with the given path
     */
    fun listSessions(path: String): List<String>

    /**
     * Lists all session IDs associated with a specific user.
     *
     * This method retrieves all sessions that the given user has interacted with,
     * based on stored metadata entries linked to the user's email.
     *
     * @param user The user whose sessions should be listed
     * @return A list of session IDs associated with the given user
     */
    fun listSessions(user: User): List<String>


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
     * This method records which entity owns or is responsible for the given session.
     *
     * @param session The session object containing the session ID
     * @param ownerId The owner identifier to associate with the session
     */
    fun setSessionOwner(session: Session, ownerId: String?)

    /**
     * Deletes all metadata associated with a session.
     *
     * This method removes all stored information for the specified session,
     * including name, message IDs, timestamps, and any other associated metadata.
     *
     * @param user The user associated with the session, or null for anonymous sessions
     * @param session The session object containing the session ID to delete
     */
    fun deleteSession(user: User?, session: Session)

    /**
     * Retrieves all metadata associated with a session as a unified data structure.
     *
     * This is a convenience method that aggregates multiple metadata fields into a single
     * [SessionMetadata] object, reducing the number of storage calls required to obtain
     * a complete view of a session's metadata.
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
            sessionTime = getSessionTime(user, session),
            ownerId = getSessionOwner(session)
        )
    }

    /**
     * Sets multiple metadata fields for a session in a single call.
     *
     * Only non-null fields in the provided [SessionMetadata] will be updated. This allows
     * for partial updates without overwriting existing values with null. The [SessionMetadata.messageIds]
     * field is always written if non-empty; pass an explicit empty list via [setMessageIds] to clear.
     *
     * @param user The user associated with the session, or null for anonymous sessions
     * @param session The session object containing the session ID
     * @param metadata The metadata fields to update; null fields are ignored
     */
    fun setSessionMetadata(user: User?, session: Session, metadata: SessionMetadata) {
        metadata.name?.let { setSessionName(user, session, it) }
        if (metadata.messageIds.isNotEmpty()) setMessageIds(user, session, metadata.messageIds)
        metadata.sessionTime?.let { setSessionTime(user, session, it) }
        metadata.ownerId?.let { setSessionOwner(session, it) }
    }
}