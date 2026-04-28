package com.simiacryptus.cognotik.util.crawl.fetch

import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask

/**
  * Factory for creating an HTTP client fetch strategy.
  * Can be used to register additional [FetchMethod] instances backed by [BasicHttpClientStrategy].
  */
class HttpClientFetch : FetchMethodFactory {
   override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = BasicHttpClientStrategy(task)
}