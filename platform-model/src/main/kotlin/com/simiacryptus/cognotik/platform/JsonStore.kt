package com.simiacryptus.cognotik.platform

import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.platform.model.User

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