package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.util.TaskHarness
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskTypeConfig
import com.simiacryptus.cognotik.util.UnifiedHarness
import com.simiacryptus.cognotik.util.crawl.processing.ProcessingStrategyType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object CrawlerAgentTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
      UnifiedHarness.configurePlatform()
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
                processing_strategy = ProcessingStrategyType.DefaultSummarizer,
            ),
            executionConfig = CrawlerTaskExecutionConfigData(
                search_query = "Kotlin programming language features",
                content_queries = "Identify the top 5 features of Kotlin and its primary use cases.",
                task_description = "Research and summarize Kotlin language features"
            ),
            timeoutMinutes = 10,
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
                processing_strategy = ProcessingStrategyType.DefaultSummarizer
            ),
            executionConfig = CrawlerTaskExecutionConfigData(
                direct_urls = listOf("https://kotlinlang.org/"),
                content_queries = "What is the latest version of Kotlin mentioned on the homepage?",
                task_description = "Check Kotlin homepage for version info"
            ),
            timeoutMinutes = 10,
        ).run()
    }
}