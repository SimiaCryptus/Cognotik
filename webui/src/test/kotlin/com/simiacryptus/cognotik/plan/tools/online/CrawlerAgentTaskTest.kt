package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.apps.general.TaskTestHarness
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskExecutionConfigData
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask.CrawlerTaskTypeConfig
import com.simiacryptus.cognotik.plan.tools.online.processing.ProcessingStrategyType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

object CrawlerAgentTaskTest {

    @JvmStatic
    @BeforeAll
    fun setup() {
        com.simiacryptus.cognotik.apps.general.PlanTestHarness.Companion.configurePlatform()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun testCrawler() {
        TaskTestHarness(
            taskType = CrawlerAgentTask.CrawlerAgent,
            typeConfig = CrawlerTaskTypeConfig(
                task_type = CrawlerAgentTask.CrawlerAgent.name,
                max_pages_per_task = 2,
                processing_strategy = ProcessingStrategyType.DefaultSummarizer,
                generate_transcript = true
            ),
            executionConfig = CrawlerTaskExecutionConfigData(
                search_query = "Kotlin programming language features",
                content_queries = "Identify the top 5 features of Kotlin and its primary use cases.",
                task_description = "Research and summarize Kotlin language features"
            ),
            timeoutMinutes = 10,
        ).run()
    }

    //@Test
    @Timeout(10, unit = TimeUnit.MINUTES)
    fun testDirectUrlCrawler() {
        TaskTestHarness(
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