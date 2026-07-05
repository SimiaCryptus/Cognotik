package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.social.PersuasiveEssayTask.PersuasiveEssayTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object PersuasiveEssayTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  //@org.junit.jupiter.api.Test
  @Timeout(15, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = PersuasiveEssayTask.PersuasiveEssay,
      typeConfig = PersuasiveEssayTask.PersuasiveEssayTaskTypeConfig(
        generate_images = false,
        generate_cover_image = false
      ),
      executionConfig = PersuasiveEssayTaskExecutionConfigData(
        thesis = "Remote work significantly improves software developer productivity and well-being.",
        target_audience = "tech company executives",
        tone = "analytical",
        target_word_count = 500,
        num_arguments = 2,
        include_counterarguments = true,
        use_rhetorical_devices = true,
        include_evidence = true,
        use_analogies = true,
        call_to_action = "moderate",
        revision_passes = 0
      ),
      timeoutMinutes = 15,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}