package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.platform.model.defaultUser
import com.simiacryptus.cognotik.util.FileGenerator
import com.simiacryptus.cognotik.util.UnifiedHarness
import com.simiacryptus.cognotik.docops.UpdateModes
import java.io.File

object TaskImplReviewer : FileGenerator() {
  @JvmStatic
  fun main(args: Array<String>) {
    UnifiedHarness.configurePlatform(defaultUser)
    run(
      root = File("."),
      folder = File("webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools"),
      updateMode = UpdateModes.PatchExisting,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    )
  }
}
