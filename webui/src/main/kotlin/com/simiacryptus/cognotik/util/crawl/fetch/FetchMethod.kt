package com.simiacryptus.cognotik.util.crawl.fetch

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.util.EnabledStrategy
import com.simiacryptus.cognotik.util.LoggerFactory
import java.io.File
import java.util.concurrent.ExecutorService

interface FetchStrategy : EnabledStrategy {
    fun fetch(
        url: String,
        webSearchDir: File,
        index: Int,
        pool: ExecutorService,
        orchestrationConfig: OrchestrationConfig
    ): String
}

object FetchConfig {
    var isSeleniumEnabled: Boolean = false
}

interface FetchMethodFactory {
    fun createStrategy(task: CrawlerAgentTask): FetchStrategy
}

@Suppress("unused")
enum class FetchMethod : FetchMethodFactory {
    Selenium {
        override fun createStrategy(task: CrawlerAgentTask) = Selenium().createStrategy(task)
    },
    HttpClient {
        override fun createStrategy(task: CrawlerAgentTask) = HttpClientFetch().createStrategy(task)
    };

    companion object {
        val log = LoggerFactory.getLogger(FetchMethod::class.java)
    }
}