package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CodingMode
import com.simiacryptus.cognotik.plan.cognitive.CodingMode.CodingModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeConfig
import com.simiacryptus.cognotik.plan.cognitive.CognitiveModeType
import com.simiacryptus.cognotik.plan.cognitive.ParallelModeConfig
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode.WaterfallModeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.platform.Session
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
                taskSettings[FileModificationTask.FileModification.name] = TaskTypeConfig(
                    task_type = FileModificationTask.FileModification.name,
                )
            }
            override fun createWorkspace() = File(".").resolve("workspaces/$testName/test-${now()}").apply { mkdirs() }
        }.run()
    }
}