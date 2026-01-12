package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.plan.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.tools.file.ImageGenerationTask.Companion.GenerateImage
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import java.io.File

object WaterfallImagesTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
    }

   //@org.junit.jupiter.api.Test
    fun test() {
        object : PlanHarness(
            prompt = "Create a family tree of monsters; each image is an individual monster.",
            cognitiveSettings = WaterfallMode.WaterfallModeConfig(),
        ) {
            override fun newConfig(session: Session, tempDir: File) = super.newConfig(session, tempDir).apply {
                taskSettings[GenerateImage.name] = TaskTypeConfig(task_type = GenerateImage.name,)
            }
        }.run()
    }

}