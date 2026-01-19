package com.simiacryptus.cognotik.plan.macros

import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.cognitive.WaterfallMode
import com.simiacryptus.cognotik.plan.tools.file.ImageGenerationTask.Companion.GenerateImage
import com.simiacryptus.cognotik.plan.tools.file.ImageVariationTask.Companion.ImageVariation
import com.simiacryptus.cognotik.platform.Session
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import java.io.File

object ImageGameTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        UnifiedHarness.configurePlatform()
    }

  //@org.junit.jupiter.api.Test
   @Tag("Demo")
    fun test() {
        object : PlanHarness(
            prompt = "Create a complex image featuring a number of different elements, then compile into a game featuring a wide variety of variants." +
                " The image should feature various cats sleeping in unusual places - on bookshelves and in teacups etc. Main colors: pink, yellow, purple. Style: surrealism.",
            cognitiveSettings = WaterfallMode.WaterfallModeConfig(),
        ) {
            override fun newConfig(session: Session, tempDir: File) = super.newConfig(session, tempDir).apply {
                taskSettings[GenerateImage.name] = TaskTypeConfig(task_type = GenerateImage.name,)
                taskSettings[ImageVariation.name] = TaskTypeConfig(task_type = ImageVariation.name,)
            }
        }.run()
    }

}