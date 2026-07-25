package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.docops.UpdateModes
import java.io.File

object TaskProductPageGenerator : com.simiacryptus.cognotik.util.FileGenerator() {
  @JvmStatic
  fun main(args: Array<String>) {
    com.simiacryptus.cognotik.util.UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
    run(
      root = File("."),
      folder = File("webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools"),
      targetFile = { file -> File("site/cognotik.com").resolve(file.nameWithoutExtension + ".html") },
      updateMode = UpdateModes.PatchExisting,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    )
  }
}


