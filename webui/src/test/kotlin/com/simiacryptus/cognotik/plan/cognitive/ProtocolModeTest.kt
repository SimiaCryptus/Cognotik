package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.apps.general.PlanTestHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.platform.Session
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

object ProtocolModeTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

   //@Test
    fun test() {
        object : PlanTestHarness(
            prompt = "Create a simple python script that prints 'Hello from ProtocolMode'",
            cognitiveSettings = CognitiveModeType.Protocol.newSettings(),
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