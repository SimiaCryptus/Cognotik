package com.simiacryptus.cognotik.util.crawl.seed

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.platform.model.User
import java.net.URI

class DirectUrls : SeedMethodFactory {
  override fun createStrategy(task: CrawlerAgentTask, user: User): SeedStrategy = object : SeedStrategy {
    override fun getSeedItems(
      taskConfig: CrawlerAgentTask.CrawlerTaskExecutionConfigData?,
      orchestrationConfig: OrchestrationConfig
    ): List<SeedItem> {
      SeedMethod.log.info("Starting DirectUrls seed method")
      if (taskConfig?.direct_urls.isNullOrEmpty()) {
        SeedMethod.log.error("Direct URLs are missing for DirectUrls seed method")
        return emptyList()
      }
      SeedMethod.log.debug("Processing direct URLs: ${taskConfig.direct_urls?.joinToString(", ")}")
      return taskConfig.direct_urls?.map { it.trim() }?.filter { it.isNotBlank() }
        ?.filter { url ->
          try {
            URI.create(url)
            url.startsWith("http://") || url.startsWith("https://")
          } catch (e: Exception) {
            SeedMethod.log.warn("Invalid URL format: $url")
            false
          }
        }
        ?.mapIndexed { index, url ->
          SeedMethod.log.debug("Adding direct URL: $url")
          SeedItem(
            link = url,
            title = "Direct URL ${index + 1}",
            additionalData = mapOf("index" to index)
          )
        }?.also {
          SeedMethod.log.info("Successfully processed ${it.size} direct URLs")
        }!!
    }
  }
}