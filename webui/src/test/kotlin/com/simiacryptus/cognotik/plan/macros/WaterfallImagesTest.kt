package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.plan.tools.file.GenerateImageTask
import com.simiacryptus.cognotik.plan.tools.file.GenerateImageTask.Companion.GenerateImage
import com.simiacryptus.cognotik.platform.Session
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.io.File

object WaterfallImagesTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        PlanHarness.configurePlatform()
    }

    @Test
    fun test() {
        object : PlanHarness(
            prompt = "Create a family tree of monsters (cartoon style, family friendly)",
            cognitiveSettings = WaterfallMode.WaterfallModeConfig(),
        ) {
            override fun newConfig(session: Session, tempDir: File) = super.newConfig(session, tempDir).apply {
                taskSettings[GenerateImage.name] = TaskTypeConfig(task_type = GenerateImage.name,)
            }
        }.run()
    }

}