package com.simiacryptus.cognotik.plan.cognitive

import com.simiacryptus.cognotik.CoreTasks
import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.file.FileModificationTask
import com.simiacryptus.cognotik.platform.model.ApplicationServicesConfig
import com.simiacryptus.cognotik.platform.model.Session
import com.simiacryptus.cognotik.util.PlanHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import java.io.File

@Suppress("unused")
object AdaptivePlanningModeTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(ApplicationServicesConfig.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  fun test() {
    object : PlanHarness(
      prompt = "Create a simple python script that prints 'Hello from AdaptivePlanningMode'",
      cognitiveSettings = CoreTasks.Adaptive.newSettings(),
      user = ApplicationServicesConfig.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
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