package com.simiacryptus.cognotik.ui.patch

import java.nio.file.Path

data class InstrumentationResult(
  val renderedMarkdown: String,
  val appliedPatches: List<AppliedPatch>,
  val newFiles: List<CreatedFile>,
  val errors: List<InstrumentationError>
)

data class CreatedFile(
  val path: Path,
  val content: String,
  val autoCreated: Boolean
)

data class InstrumentationError(
  val filename: String?,
  val message: String,
  val exception: Throwable? = null
)