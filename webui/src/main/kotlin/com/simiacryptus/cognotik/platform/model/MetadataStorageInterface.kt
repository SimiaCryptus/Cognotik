package com.simiacryptus.cognotik.platform.model

 import com.simiacryptus.cognotik.platform.Session
 import java.util.*
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
     * Deletes all metadata associated with a session.
     * 
     * This method removes all stored information for the specified session,
     * including name, message IDs, timestamps, and any other associated metadata.
     * 
     * @param user The user associated with the session, or null for anonymous sessions
     * @param session The session object containing the session ID to delete
     */
    fun deleteSession(user: User?, session: Session)
}