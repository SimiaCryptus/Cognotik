package com.simiacryptus.cognotik.ui.patch

import java.nio.file.Path

/** Whether a change creates a file or modifies an existing one. */
enum class ChangeType { NEW_FILE, MODIFIED }

/** One row of the file-change summary rendered at the end of an instrumented response. */
data class FileChangeSummary(
  val path: Path,
  val relativePath: Path,
  val changeType: ChangeType,
  val linesAdded: Int = 0,
  val linesRemoved: Int = 0,
  val isValid: Boolean = true,
  val applied: Boolean = false,
)

/**
 * A [FileChangeSummary] paired with the deferred action that applies it.
 * [apply] is null when the change has already been applied (e.g. auto-applied).
 */
data class PendingChange(
  val summary: FileChangeSummary,
  val apply: (() -> Unit)? = null,
)

/** Cheap +/- line counting for unified-diff bodies. */
object DiffStats {
  fun linesAdded(diff: String): Int =
    diff.lines().count { it.startsWith("+") && !it.startsWith("+++") }

  fun linesRemoved(diff: String): Int =
    diff.lines().count { it.startsWith("-") && !it.startsWith("---") }
}