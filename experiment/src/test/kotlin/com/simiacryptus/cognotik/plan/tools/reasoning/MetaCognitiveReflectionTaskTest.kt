package com.simiacryptus.cognotik.plan.tools.reasoning

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.reasoning.MetaCognitiveReflectionTask.MetaCognitiveReflectionTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Suppress("unused")
object MetaCognitiveReflectionTaskTest {

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
      taskType = MetaCognitiveReflectionTask.MetaCognitiveReflection,
      typeConfig = TaskTypeConfig(
        task_type = MetaCognitiveReflectionTask.MetaCognitiveReflection.name
      ),
      executionConfig = MetaCognitiveReflectionTaskExecutionConfigData(
        subject_task_id = "previous_task_id",
        reflection_aspects = listOf("assumptions", "logic", "biases", "alternatives"),
        task_description = "Reflect on the reasoning process of the previous analysis task",
        reflection_questions = listOf(
          "Are there any unstated assumptions in the architectural choice?",
          "Did we overlook any security implications?"
        ),
        suggest_improvements = true,
        identify_gaps = true,
        evaluate_confidence = true,
        include_file_context = false
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