package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.chat.model.GeminiModels
import com.simiacryptus.cognotik.crawl.CrawlerAgentTask.CrawlerTaskExecutionConfigData
import com.simiacryptus.cognotik.crawl.CrawlerAgentTask.CrawlerTaskTypeConfig
import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.util.UnifiedHarness
import com.simiacryptus.cognotik.crawl.CrawlerAgentTask
import com.simiacryptus.cognotik.crawl.processing.DefaultSummarizerStrategy
import com.simiacryptus.cognotik.crawl.processing.ProcessingStrategyType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object CrawlerAgentTaskTest {

  @JvmStatic
  @BeforeAll
  fun setup() {
    UnifiedHarness.configurePlatform(com.simiacryptus.cognotik.platform.model.defaultUser)
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun testCrawler() {
    TaskHarness(
      taskType = CrawlerAgentTask.CrawlerAgent,
      typeConfig = CrawlerTaskTypeConfig(
        task_type = CrawlerAgentTask.CrawlerAgent.name,
        max_pages_per_task = 2,
        processing_strategy = ProcessingStrategyType("DefaultSummarizer") { DefaultSummarizerStrategy.instance },
      ),
      executionConfig = CrawlerTaskExecutionConfigData(
        search_query = listOf("Kotlin programming language features"),
        content_queries = "Identify the top 5 features of Kotlin and its primary use cases.",
        task_description = "Research and summarize Kotlin language features"
      ),
      timeoutMinutes = 10,
      user = com.simiacryptus.cognotik.platform.model.defaultUser,
      smartModel = GeminiModels.GeminiFlash_30_Preview,
      fastModel = GeminiModels.GeminiFlash_30_Preview,
      imageModel = GeminiModels.GeminiFlash_31_Image_Preview,
      audioModel = GeminiModels.GeminiFlash_30_Preview,
    ).run()
  }

  @org.junit.jupiter.api.Tag("Integration")
  //@org.junit.jupiter.api.Test
  @Timeout(10, unit = TimeUnit.MINUTES)
  fun testDirectUrlCrawler() {
    TaskHarness(
      taskType = CrawlerAgentTask.CrawlerAgent,
      typeConfig = CrawlerTaskTypeConfig(
        task_type = CrawlerAgentTask.CrawlerAgent.name,
        max_pages_per_task = 1,
        processing_strategy = ProcessingStrategyType("DefaultSummarizer") { DefaultSummarizerStrategy.instance }
      ),
      executionConfig = CrawlerTaskExecutionConfigData(
        direct_urls = listOf("https://kotlinlang.org/"),
        content_queries = "What is the latest version of Kotlin mentioned on the homepage?",
        task_description = "Check Kotlin homepage for version info"
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