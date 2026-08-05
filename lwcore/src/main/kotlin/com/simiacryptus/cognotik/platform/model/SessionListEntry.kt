package com.simiacryptus.cognotik.platform.model

import java.util.Date

/**
 * Listing-page optimized projection containing only the fields displayed in
 * a session list row. Implementations may project fewer columns from
 * storage and avoid loading large/unused fields (e.g. message IDs) to
 * accelerate listings.
 */
data class SessionListEntry(
  override val id: Session,
  override val name: String?,
  override val sessionTime: Date?,
  override val ownerId: String?,
  override val workerId: String?,
  override val path: String?,
) : SessionSummary