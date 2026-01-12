package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.ParallelModeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileAppendTask
import com.simiacryptus.cognotik.plan.tools.file.FileAppendTask.Companion.FileAppend
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.PlanHarness
import java.io.File

object TaskImplDocumenter {
    val testName = javaClass.simpleName
    @JvmStatic fun main(args: Array<String>) {
        PlanHarness.configurePlatform()
        object : PlanHarness(
            prompt = "Document all cognitive modes and task types according to coded implementations - produce a `task_docs.md` and a `cognitive_modes.md` document.",
            cognitiveSettings = ParallelModeConfig(),
            workspace = File(".").absoluteFile,
        ) {
            override fun newConfig(session: Session, tempDir: File) =
                super.newConfig(session, tempDir).apply {
                    //this.defaultSmartModel = GeminiModels.GeminiPro_30_Preview.asApiChatModel()
                    this.temperature = 0.0
                    taskSettings[FileAppend.name] = TaskTypeConfig(
                      task_type = FileAppend.name,
                      //model = GeminiModels.GeminiPro_30_Preview.asApiChatModel()
                    )
                }
            override fun createWorkspace() = File(".").resolve("workspaces/$testName/test-${now()}").apply { mkdirs() }
        }.run()
    }
}