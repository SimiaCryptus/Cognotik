package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import java.io.File

object WaterfallModeTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

   //@org.junit.jupiter.api.Test
    fun test() {
        object : PlanHarness(
            prompt = "Create a simple python script that prints 'Hello from WaterfallMode'",
            cognitiveSettings = WaterfallMode.WaterfallModeConfig(),
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