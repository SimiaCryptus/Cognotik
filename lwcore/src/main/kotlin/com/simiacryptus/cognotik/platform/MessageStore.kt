package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User

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