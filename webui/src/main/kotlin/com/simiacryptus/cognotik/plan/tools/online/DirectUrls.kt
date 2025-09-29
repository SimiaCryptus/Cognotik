package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.platform.model.User
import java.net.URI

class DirectUrls : SeedMethodFactory {
    override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy = object : SeedStrategy {
        override fun getSeedItems(
            taskConfig: CrawlerAgentTask.CrawlerTaskConfigData?,
            orchestrationConfig: OrchestrationConfig
        ): List<SeedItem>? {
            SeedMethod.Companion.log.info("Starting DirectUrls seed method")
            if (taskConfig?.direct_urls.isNullOrEmpty()) {
                SeedMethod.Companion.log.error("Direct URLs are missing for DirectUrls seed method")
                return emptyList()
            }
            SeedMethod.Companion.log.debug("Processing direct URLs: ${taskConfig.direct_urls}")
            return taskConfig.direct_urls.split(",").map { it.trim() }.filter { it.isNotBlank() }
                .filter { url ->
                    try {
                        URI.create(url)
                        url.startsWith("http://") || url.startsWith("https://")
                    } catch (e: Exception) {
                        SeedMethod.Companion.log.warn("Invalid URL format: $url")
                        false
                    }
                }
                .mapIndexed { index, url ->
                    SeedMethod.Companion.log.debug("Adding direct URL: $url")
                    SeedItem(
                        link = url,
                        title = "Direct URL ${index + 1}",
                        additionalData = mapOf("index" to index)
                    )
                }.also {
                    SeedMethod.Companion.log.info("Successfully processed ${it.size} direct URLs")
                }
        }
    }
}