package com.simiacryptus.cognotik.platform.model

import java.time.Instant

/**
 * Backend-agnostic filter for session listings.
 *
 * All criteria are conjunctive; null means "no constraint". The in-memory
 * [matches] predicate defines the reference semantics that DB-backed
 * implementations of `SessionMetadataInterface.querySessions` should reproduce.
 *
 * @property path exact application path match
 * @property nameContains case-insensitive substring match on the display name (or the id when unnamed)
 * @property ownerId exact owner match
 * @property workerId exact worker match
 * @property from inclusive lower bound on the session timestamp
 * @property to exclusive upper bound on the session timestamp
 * @property sessionIds restrict results to these ids
 * @property sort result ordering
 */
data class SessionQuery(
  val path: String? = null,
  val nameContains: String? = null,
  val ownerId: String? = null,
  val workerId: String? = null,
  val from: Instant? = null,
  val to: Instant? = null,
  val sessionIds: Set<String>? = null,
  val sort: SessionSort = SessionSort.TIME_DESC,
) {
  fun matches(entry: SessionSummary): Boolean {
    if (path != null && entry.path != path) return false
    if (!nameContains.isNullOrBlank() &&
      !(entry.name ?: entry.id.sessionId).contains(nameContains, ignoreCase = true)
    ) return false
    if (ownerId != null && entry.ownerId != ownerId) return false
    if (workerId != null && entry.workerId != workerId) return false
    val time = entry.sessionInstant
    if (from != null && (time == null || time.isBefore(from))) return false
    if (to != null && (time == null || !time.isBefore(to))) return false
    if (sessionIds != null && entry.id.sessionId !in sessionIds) return false
    return true
  }
}

/** Ordering for session listings. Sessions without a timestamp sort last for time orderings. */
enum class SessionSort {
  TIME_DESC, TIME_ASC, NAME_ASC, NAME_DESC;

  val comparator: Comparator<SessionSummary>
    get() = when (this) {
      TIME_DESC -> compareBy<SessionSummary, Instant?>(nullsLast(reverseOrder())) { it.sessionInstant }
        .thenBy { it.id.sessionId }

      TIME_ASC -> compareBy<SessionSummary, Instant?>(nullsLast()) { it.sessionInstant }
        .thenBy { it.id.sessionId }

      NAME_ASC -> compareBy<SessionSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name ?: it.id.sessionId }

      NAME_DESC -> compareBy<SessionSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name ?: it.id.sessionId }
        .reversed()
    }
}