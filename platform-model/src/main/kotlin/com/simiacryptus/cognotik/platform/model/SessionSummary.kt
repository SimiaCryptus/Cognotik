package com.simiacryptus.cognotik.platform.model

import java.time.Instant
import java.util.Date

/**
 * Shared read-model for session listing rows, extracted so that
 * [SessionMetadata] and [SessionListEntry] cannot drift apart (REVIEW.md §3.11).
 */
interface SessionSummary {
  val id: Session
  val name: String?
  val sessionTime: Date?
  val ownerId: String?
  val workerId: String?
  val path: String?

  /** [sessionTime] as a `java.time` value; prefer this over the mutable [Date]. */
  val sessionInstant: Instant? get() = sessionTime?.toInstant()
}