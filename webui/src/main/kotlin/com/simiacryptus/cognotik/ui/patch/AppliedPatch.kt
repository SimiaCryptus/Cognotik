package com.simiacryptus.cognotik.ui.patch

import java.nio.file.Path

data class AppliedPatch(
  val path: Path,
  val originalContent: String,
  val newContent: String,
  val autoApplied: Boolean
)