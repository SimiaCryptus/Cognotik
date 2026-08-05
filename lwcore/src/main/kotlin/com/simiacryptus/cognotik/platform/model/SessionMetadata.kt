package com.simiacryptus.cognotik.platform.model

import java.util.Date

/**
 * Unified data structure representing all metadata associated with a session.
 *
 * This class aggregates the various metadata fields that can be stored for a session,
 * allowing them to be retrieved or updated in a single call. Fields are nullable to
 * support partial updates and to represent unset values.
 *
 * @property id The session this metadata describes
 * @property name The display name of the session, or null if not set
 * @property messageIds The list of message IDs associated with the session
 * @property sessionTime The timestamp associated with the session, or null if not set
 * @property ownerId The owner `User.id` for the session, or null if not set
 * @property workerId The worker `ip:port` currently serving the session, or null if not set
 * @property path The path associated with the session, or null if not set
 */
@Suppress("DEPRECATION")
data class SessionMetadata(
  /**
   * Defaults to the [Session.NULL] sentinel for backwards compatibility only;
   * always pass an explicit session (REVIEW.md §3.11).
   */
  override val id: Session = Session.NULL,
  override val name: String? = null,
  val messageIds: List<String> = emptyList(),
  override val sessionTime: Date? = null,
  override val ownerId: String? = null,
  override val workerId: String? = null,
  override val path: String? = null,
) : SessionSummary {

  /** Projects this aggregate onto the listing read-model. */
  fun toEntry(): SessionListEntry = SessionListEntry(
    id = id,
    name = name,
    sessionTime = sessionTime,
    ownerId = ownerId,
    workerId = workerId,
    path = path,
  )
}