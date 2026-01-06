package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.CodingModeConfig
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode.WaterfallModeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.reasoning.BrainstormingTask
import com.simiacryptus.cognotik.plan.tools.run.SubPlanTask
import com.simiacryptus.cognotik.platform.Session
import java.io.File

object SoftwareProjectGenerator {
    @JvmStatic
    fun main(args: Array<String>) {
        PlanHarness.configurePlatform()
        object : PlanHarness(
            prompt = "Build a fun and unique browser-based game.",
            cognitiveSettings = WaterfallModeConfig(),
        ) {
            override fun newConfig(session: Session, tempDir: File) = super.newConfig(session, tempDir).apply {
                taskSettings[BrainstormingTask.Brainstorming.name] = TaskTypeConfig(
                    task_type = BrainstormingTask.Brainstorming.name,
                    name = BrainstormingTask.Brainstorming.name,
                )
                taskSettings[SubPlanTask.SubPlan.name] = SubPlanTask.SubPlanTaskTypeConfig(
                    name = SubPlanTask.SubPlan.name,
//                    cognitiveSettings = WaterfallModeConfig(),
                    cognitiveSettings = CodingModeConfig(),
                    taskSettings = mutableMapOf(
                        FileModificationTask.FileModification.name to TaskTypeConfig(
                            task_type = FileModificationTask.FileModification.name,
                            name = FileModificationTask.FileModification.name,
                        )
                    )
                )
            }
        }.run()
    }
}