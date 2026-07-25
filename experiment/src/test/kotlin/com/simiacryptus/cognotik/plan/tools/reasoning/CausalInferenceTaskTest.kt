package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.CausalInferenceTask.CausalInferenceTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object CausalInferenceTaskTest {

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
      taskType = CausalInferenceTask.CausalInference,
      typeConfig = TaskTypeConfig(
        task_type = CausalInferenceTask.CausalInference.name
      ),
      executionConfig = CausalInferenceTaskExecutionConfigData(
        observed_effect = "The application experiences a significant latency spike every 60 seconds.",
        potential_causes = listOf(
          "Scheduled background maintenance tasks",
          "JVM Garbage Collection (Stop-the-world events)",
          "External API rate limiting or periodic syncs",
          "Log rotation overhead"
        ),
        build_causal_graph = true,
        identify_confounders = true,
        evidence_sources = listOf("logs/app.log", "metrics/cpu.csv"),
        related_files = listOf("src/main/kotlin/com/simiacryptus/cognotik/util/*.kt"),
        task_description = "Investigate the root cause of periodic latency spikes in the system.",
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