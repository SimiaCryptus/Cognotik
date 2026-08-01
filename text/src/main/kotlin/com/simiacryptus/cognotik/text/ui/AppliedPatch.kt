package com.simiacryptus.cognotik.text.ui

import java.nio.file.Path

data class AppliedPatch(
  val path: Path,
  val originalContent: String,
  val newContent: String,
  val autoApplied: Boolean
)