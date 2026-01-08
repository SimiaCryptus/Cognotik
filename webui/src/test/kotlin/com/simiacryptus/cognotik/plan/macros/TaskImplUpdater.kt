package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.ParallelModeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask.Companion.FileModification
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.platform.model.asApiChatModel
import com.simiacryptus.cognotik.util.PlanHarness
import java.io.File

object TaskImplUpdater {
    val testName = javaClass.simpleName
    @JvmStatic fun main(args: Array<String>) {
        PlanHarness.configurePlatform()
        object : PlanHarness(
            prompt = "Update the implementations of various task types according to `agentic_io_best_practices.md`, `task_type_best_practices.md`, and `user_interface.md`.",
            cognitiveSettings = ParallelModeConfig(),
            workspace = File(".").absoluteFile,
        ) {
            override fun newConfig(session: Session, tempDir: File) = super.newConfig(session, tempDir).apply {
                //this.defaultSmartModel = GeminiModels.GeminiPro_30_Preview.asApiChatModel()
                this.temperature = 0.0
                taskSettings[FileModification.name] = TaskTypeConfig(
                  task_type = FileModification.name,
                  model = GeminiModels.GeminiPro_30_Preview.asApiChatModel()
                )
            }
            override fun createWorkspace() = File(".").resolve("workspaces/$testName/test-${now()}").apply { mkdirs() }
        }.run()
    }
}