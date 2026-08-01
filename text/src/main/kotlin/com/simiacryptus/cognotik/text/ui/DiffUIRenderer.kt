package com.simiacryptus.cognotik.text.ui

import java.nio.file.Path

interface DiffUIRenderer {
  fun renderSaveButton(filepath: Path, code: String, lang: String, onSave: () -> Unit): String

  /**
   * Renders the apply/revert control group for a diff. When [onForceApply] is non-null an
   * additional control is rendered which applies the patch while ignoring validation failures.
   * Implementations must keep the apply controls usable after a revert so the patch can be
   * re-applied.
   */
  fun renderApplyDiffButton(
    filepath: Path,
    diff: String,
    onApply: () -> Unit,
    onRevert: () -> Unit,
    onForceApply: (() -> Unit)? = null
  ): String

  fun renderAutoApplied(filepath: Path, revertHtml: String): String
  fun renderWarning(message: String): String
  fun recordPatch(data: Map<String, Any?>): String

  /**
   * Renders a summary of every file touched by a response. When [onApplyAll] is non-null an
   * "Apply All" control is rendered which applies all still-pending changes.
   */
  fun renderChangeSummary(changes: List<FileChangeSummary>, onApplyAll: (() -> Unit)? = null): String {
    if (changes.isEmpty()) return ""
    return changes.joinToString("\n", "\n\n### Change Summary\n\n", "\n\n") { c ->
      val kind = if (c.changeType == ChangeType.NEW_FILE) "new file" else "modified"
      "- ${c.relativePath} ($kind, +${c.linesAdded}/-${c.linesRemoved}${if (c.applied) ", applied" else ""})"
    }
  }
}