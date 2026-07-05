package com.simiacryptus.cognotik.ui.patch

import java.nio.file.Path

interface DiffUIRenderer {
  fun renderSaveButton(filepath: Path, code: String, lang: String, onSave: () -> Unit): String
  fun renderApplyDiffButton(filepath: Path, diff: String, onApply: () -> Unit, onRevert: () -> Unit): String
  fun renderAutoApplied(filepath: Path, revertHtml: String): String
  fun renderWarning(message: String): String
  fun recordPatch(data: Map<String, Any?>): String
}