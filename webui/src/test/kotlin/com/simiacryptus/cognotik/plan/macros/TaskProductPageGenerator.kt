package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.plan.tools.file.ReadDocumentsTask.Companion.getAvailableFiles
import java.io.File

object TaskProductPageGenerator : com.simiacryptus.cognotik.util.FileGenerator() {
  @JvmStatic fun main(args: Array<String>) {
    com.simiacryptus.cognotik.util.UnifiedHarness.configurePlatform()
    run(
      root = File("."),
      folder = File("webui/src/main/kotlin/com/simiacryptus/cognotik/plan/tools"),
      targetFile = { file -> File("site/cognotik.com").resolve(file.nameWithoutExtension + ".html") },
      overwriteMode = OverwriteModes.PatchExisting,
      relatedFiles = {
        listOf(
          it.toString(),
          it.toString().replace("/main/", "/test/"),
          "docs/embedding.md",
          "docs/task_product_page.md",
          "site/cognotik.com/task_product_page_template.html",
        )
      },
      generationPrompt = { source, target ->
        "Update the product page HTML file ($target) to reflect the latest implementation in ${source.name}.\n" +
            "In particular: \n" +
            "* add the browser feature for the test workspace: \n" + getAvailableFiles(File("webui/workspaces/${source.nameWithoutExtension}").toPath()).joinToString("\n") { "      - $it" } + "\n" +
            "* Add/update details on how to invoke using the embedding doc and test case example.\n"
      }
    )
  }
}


