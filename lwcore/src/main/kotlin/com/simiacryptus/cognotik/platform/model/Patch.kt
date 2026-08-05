package com.simiacryptus.cognotik.platform.model

import java.time.Instant

/**
 * Three-state update value: absent ([Unchanged]) is distinguishable from
 * "explicitly set to null" ([Set] with a null value).
 *
 * This resolves the "null means skip, so you can never clear a field" problem
 * described in REVIEW.md §3.4.
 */
sealed interface Patch<out T> {
  /** Leave the existing stored value untouched. */
  object Unchanged : Patch<Nothing>

  /** Write [value] (which may be null, meaning "clear"). */
  data class Set<out T>(val value: T) : Patch<T>
}

/** Invokes [block] with the new value only when this patch is a [Patch.Set]. */
inline fun <T> Patch<T>.ifSet(block: (T) -> Unit) {
  val self = this
  if (self is Patch.Set) block(self.value)
}

/** Convenience constructor: `"name".asPatch()`. */
fun <T> T.asPatchValue(): Patch<T> = Patch.Set(this)

/**
 * Explicit, field-wise patch for session metadata.
 *
 * @property name new session display name, or `Set(null)` to clear
 * @property messageIds new message id list (`Set(emptyList())` clears)
 * @property sessionTime new session timestamp
 * @property ownerId new owner id, or `Set(null)` to clear
 * @property workerId new worker id, or `Set(null)` to clear
 * @property path new session path, or `Set(null)` to clear
 */
data class SessionMetadataPatch(
  val name: Patch<String?> = Patch.Unchanged,
  val messageIds: Patch<List<String>> = Patch.Unchanged,
  val sessionTime: Patch<Instant?> = Patch.Unchanged,
  val ownerId: Patch<String?> = Patch.Unchanged,
  val workerId: Patch<String?> = Patch.Unchanged,
  val path: Patch<String?> = Patch.Unchanged,
)

/**
 * Converts a [SessionMetadata] snapshot into a patch using the historical
 * "null means unchanged, empty message list means unchanged" convention, so
 * that the deprecated `setSessionMetadata` can delegate without behaviour change.
 */
fun SessionMetadata.asPatch(): SessionMetadataPatch = SessionMetadataPatch(
  name = name?.let { Patch.Set(it) } ?: Patch.Unchanged,
  messageIds = if (messageIds.isNotEmpty()) Patch.Set(messageIds) else Patch.Unchanged,
  sessionTime = sessionTime?.let { Patch.Set(it.toInstant()) } ?: Patch.Unchanged,
  ownerId = ownerId?.let { Patch.Set(it) } ?: Patch.Unchanged,
  workerId = workerId?.let { Patch.Set(it) } ?: Patch.Unchanged,
  path = path?.let { Patch.Set(it) } ?: Patch.Unchanged,
)