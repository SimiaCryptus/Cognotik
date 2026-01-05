package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.platform.Session
import org.junit.jupiter.api.BeforeAll
import java.io.File

object AdaptivePlanningModeTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

   //@Test
    fun test() {
        object : PlanHarness(
            prompt = "Create a simple python script that prints 'Hello from AdaptivePlanningMode'",
            cognitiveSettings = CognitiveModeType.Adaptive.newSettings(),
        ) {
            override fun newConfig(session: Session, tempDir: File) = super.newConfig(session, tempDir).apply {
                taskSettings[FileModificationTask.FileModification.name] = TaskTypeConfig(
                    task_type = FileModificationTask.FileModification.name,
                    name = FileModificationTask.FileModification.name,
                )
            }
        }.run()
    }

}