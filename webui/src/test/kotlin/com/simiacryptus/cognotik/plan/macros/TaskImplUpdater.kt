package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.util.PlanHarness
import java.io.File

object TaskImplUpdater {
    val testName = javaClass.simpleName
    @JvmStatic fun main(args: Array<String>) {
        PlanHarness.configurePlatform()
        object : PlanHarness(
            prompt = "Update the implementations of various task types according to `agentic_io_best_practices.md`, `task_type_best_practices.md`, and `user_interface.md`.",
            cognitiveSettings = com.simiacryptus.cognotik.plan.cognitive.ParallelModeConfig(),
            workspace = File(".").absoluteFile,
        ) {
            override fun newConfig(session: com.simiacryptus.cognotik.platform.Session, tempDir: File) =
                super.newConfig(session, tempDir).apply {
                    //this.defaultSmartModel = GeminiModels.GeminiPro_30_Preview.asApiChatModel()
                    this.temperature = 0.0
                    taskSettings[FileModification.name] = com.simiacryptus.cognotik.plan.TaskTypeConfig(
                      task_type = FileModification.name,
                      //model = GeminiModels.GeminiPro_30_Preview.asApiChatModel()
                    )
                }
            override fun createWorkspace() = File(".").resolve("workspaces/$testName/test-${now()}").apply { mkdirs() }
        }.run()
    }
}