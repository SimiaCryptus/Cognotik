package com.simiacryptus.cognotik.platform.model

import java.util.Date

/**
 * Listing-page optimized projection containing only the fields displayed in
 * a session list row. Implementations may project fewer columns from
 * storage and avoid loading large/unused fields (e.g. message IDs) to
 * accelerate listings.
 */
data class SessionListEntry(
  val id: Session,
  val name: String?,
  val sessionTime: Date?,
  val ownerId: String?,
  val workerId: String?,
  val path: String?,
)