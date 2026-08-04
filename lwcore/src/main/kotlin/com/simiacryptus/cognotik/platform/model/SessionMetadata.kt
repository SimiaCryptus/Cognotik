package com.simiacryptus.cognotik.platform.model

import java.util.Date

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
 * @property ownerId The worker ip:port for the session, or null if not set
 * @property ownerId The owner user.id for the session, or null if not set
 * @property path The path associated with the session, or null if not set
 */
data class SessionMetadata(
  val id: Session = Session.NULL,
  val name: String? = null,
  val messageIds: List<String> = emptyList(),
  val sessionTime: Date? = null,
  val ownerId: String? = null,
  val workerId: String? = null,
  val path: String? = null,
)