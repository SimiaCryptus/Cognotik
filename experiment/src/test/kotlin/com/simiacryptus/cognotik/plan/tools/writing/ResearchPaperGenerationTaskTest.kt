package com.simiacryptus.cognotik.plan.tools.writing

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.plan.tools.TaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.writing.ResearchPaperGenerationTask.ResearchPaperGenerationTaskExecutionConfigData
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object ResearchPaperGenerationTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(20, unit = TimeUnit.MINUTES)
  fun test() {
    TaskHarness(
      taskType = ResearchPaperGenerationTask.ResearchPaperGeneration,
      typeConfig = TaskTypeConfig(
        task_type = ResearchPaperGenerationTask.ResearchPaperGeneration.name
      ),
      executionConfig = ResearchPaperGenerationTaskExecutionConfigData(
        research_topic = "The Impact of Large Language Models on Modern Software Development Workflows",
        paper_type = "review",
        academic_level = "masters",
        target_word_count = 1500,
        citation_style = "apa",
        include_literature_review = true,
        include_methodology = false,
        include_statistical_analysis = false,
        include_peer_review = true,
        number_of_sections = 4,
        revision_passes = 1
      ),
      timeoutMinutes = 20,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }
}