package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.cognitive.ParallelModeConfig
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileAppendTask.Companion.FileAppend
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import java.io.File

object TaskImplDocumenter {
  val testName = javaClass.simpleName

  @JvmStatic
  fun main(args: Array<String>) {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
    object : PlanHarness(
      prompt = "Document all cognitive modes and task types according to coded implementations - produce a `task_docs.md` and a `cognitive_modes.md` document.",
      cognitiveSettings = ParallelModeConfig(),
      workspace = File(".").absoluteFile,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
    ) {
      override fun newConfig(session: Session, tempDir: File) =
        super.newConfig(session, tempDir).apply {
          this.temperature = 0.0
          taskSettings[FileAppend.name] = TaskTypeConfig(task_type = FileAppend.name)
        }

      override fun createTempDirectory() = File(".").resolve("workspaces/$testName/test-${now()}").apply { mkdirs() }
    }.run()
  }
}