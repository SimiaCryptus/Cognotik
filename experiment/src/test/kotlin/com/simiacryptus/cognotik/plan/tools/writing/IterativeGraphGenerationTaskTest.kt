package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.IterativeGraphGenerationTask.IterativeGraphGenerationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout

@Suppress("unused")
object IterativeGraphGenerationTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = java.util.concurrent.TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = IterativeGraphGenerationTask.IterativeGraphGeneration,
      typeConfig = TaskTypeConfig(
        task_type = IterativeGraphGenerationTask.IterativeGraphGeneration.name
      ),
      executionConfig = IterativeGraphGenerationTaskExecutionConfigData(
        goal_prompt = "Map the relationships between characters in a small story.",
        context_data = """
                    Alice is Bob's sister. 
                    Bob works for Charlie. 
                    Charlie is friends with Alice.
                """.trimIndent(),
        node_types = listOf("Person"),
        edge_types = listOf("SISTER_OF", "WORKS_FOR", "FRIEND_OF"),
        max_iterations = 2
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