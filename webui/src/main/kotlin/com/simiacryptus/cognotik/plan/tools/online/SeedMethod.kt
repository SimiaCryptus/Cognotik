package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.describe.Description
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.EnabledStrategy
import com.simiacryptus.cognotik.util.LoggerFactory

data class SeedItem(
    val link: String,
    val title: String,
    val tags: List<String>? = null,
    @Description("1-100") val relevance_score: Double = 100.0,
    val additionalData: Map<String, Any> = emptyMap()
)

interface SeedStrategy : EnabledStrategy {
    fun getSeedItems(
        taskConfig: CrawlerAgentTask.CrawlerTaskConfigData?,
        orchestrationConfig: OrchestrationConfig
    ): List<SeedItem>?
}

interface SeedMethodFactory {
    fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy
}

enum class SeedMethod : SeedMethodFactory {
    GoogleSearch {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            GoogleSearch().createStrategy(task, user)
    },
    SearchIO_Google_Search {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google", "organic_results").createStrategy(task, user)
    },
    SearchIO_Google_Maps {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_maps", "local_results").createStrategy(task, user)
    },
    SearchIO_Google_Scholar {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_scholar", "organic_results").createStrategy(task, user)
    },
    SearchIO_Google_Patents {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_patents", "organic_results").createStrategy(task, user)
    },
    SearchIO_Google_News {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_news", "organic_results").createStrategy(task, user)
    },
    SearchIO_Amazon {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("amazon-search", "organic_results").createStrategy(task, user)
    },
    SearchIO_Bing {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("bing", "organic_results").createStrategy(task, user)
    },
    SearchIO_DuckDuckGo {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("duckduckgo", "organic_results").createStrategy(task, user)
    },
    SearchIO_EBay {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("ebay-search-api", "organic_results").createStrategy(task, user)
    },
    DirectUrls {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            DirectUrls().createStrategy(task, user)
    };

    companion object {
        val log = LoggerFactory.getLogger(SeedMethod::class.java)
    }
}