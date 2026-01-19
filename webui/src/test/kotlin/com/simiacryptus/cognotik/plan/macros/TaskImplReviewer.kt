package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.util.FileGenerator
import com.simiacryptus.cognotik.util.OverwriteModes
import com.simiacryptus.cognotik.util.UnifiedHarness
import java.io.File

object TaskImplReviewer : FileGenerator() {
  @JvmStatic fun main(args: Array<String>) {
    UnifiedHarness.configurePlatform()
    run(
      root = File("."),
      folder = File("webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools"),
      overwriteMode = OverwriteModes.PatchExisting,
      relatedFiles = {
        listOf(
          "docs/task_type_best_practices.md",
          "docs/user_interface.md",
          "docs/agentic_io_best_practices.md",
        )
      },
      generationPrompt = { source, target ->
        "Update implementation file ($target) according to the standards documents"
      }
    )
  }
}
