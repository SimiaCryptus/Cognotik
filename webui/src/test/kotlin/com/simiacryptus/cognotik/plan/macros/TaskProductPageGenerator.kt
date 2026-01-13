package com.simiacryptus.cognotik.plan.macros

import java.io.File

object TaskProductPageGenerator : com.simiacryptus.cognotik.util.FileGenerator() {
  @JvmStatic fun main(args: Array<String>) {
    com.simiacryptus.cognotik.util.UnifiedHarness.configurePlatform()
    run(
      root = File("."),
      folder = File("webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools"),
      targetFile = { File("site/cognotik.com").resolve(it.nameWithoutExtension + ".html") },
      overwriteMode = OverwriteModes.SkipExisting,
      relatedFiles = {
        listOf(
          it.toString(),
          "docs/task_product_page.md",
          "site/cognotik.com/task_product_page_template.html",
        )
      },
      generationPrompt = { source, target ->
        "Update the product page HTML file ($target) to reflect the latest implementation in ${source.name}"
      }
    )
  }
}


