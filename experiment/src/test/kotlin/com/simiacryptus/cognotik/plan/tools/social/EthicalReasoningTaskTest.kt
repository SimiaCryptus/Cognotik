package com.simiacryptus.cognotik.plan.tools.social

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.social.EthicalReasoningTask.EthicalReasoningTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object EthicalReasoningTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = EthicalReasoningTask.EthicalReasoning,
      typeConfig = TaskTypeConfig(
        task_type = EthicalReasoningTask.EthicalReasoning.name
      ),
      executionConfig = EthicalReasoningTaskExecutionConfigData(
        ethical_dilemma = "Should an autonomous vehicle prioritize the safety of its passengers over pedestrians in an unavoidable accident scenario?",
        stakeholders = listOf("Passengers", "Pedestrians", "Vehicle Manufacturer", "Insurance Companies", "Society"),
        ethical_frameworks = listOf("utilitarianism", "deontology", "virtue_ethics"),
        context = "The vehicle is operating in a high-density urban environment with established traffic laws."
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}