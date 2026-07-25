package com.simiacryptus.cognotik.docops

import com.simiacryptus.cognotik.docops.model.DocSpec
import java.io.File
import java.time.Duration

/** Everything that used to be a constructor parameter / open val on `DocProcessorBase`. */
class DocOpsConfig(
  val root: File,
  val docsFolder: File = root,
  val updateMode: UpdateMode = UpdateModes.PatchToUpdate,
  /** Host hook for computed context paths per (spec, target) pair. */
  val additionalContext: (DocSpec, File) -> List<String> = { _, _ -> emptyList() },
  val urlCacheDir: File = File(root, ".doc-processor-cache/url-cache"),
  val urlCacheTtl: Duration = Duration.ofHours(1),
  val templateVarOverrides: Map<String, String> = emptyMap(),
  val taskTimeoutMinutes: Int = 30,
  val overallTimeoutMinutes: Long = 90,
  val maxPlanningDepth: Int = 10,
  val markdownExtensions: Set<String> = setOf("md", "markdown"),
)