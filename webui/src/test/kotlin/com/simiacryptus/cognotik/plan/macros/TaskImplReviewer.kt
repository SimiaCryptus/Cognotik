package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.util.FileGenerator
import com.simiacryptus.cognotik.util.UpdateModes
import com.simiacryptus.cognotik.util.UnifiedHarness
import java.io.File

object TaskImplReviewer : FileGenerator() {
  @JvmStatic fun main(args: Array<String>) {
    UnifiedHarness.configurePlatform()
    run(
      root = File("."),
      folder = File("webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools"),
      updateMode = UpdateModes.PatchExisting,
      generationPrompt = { source, target ->
        "Update implementation file ($target) according to the standards documents"
      }
    )
  }
}
