package com.simiacryptus.cognotik.util.crawl.seed

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.platform.model.User
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class GoogleProxy : SeedMethodFactory {
  companion object {
    private val PROXY_ENDPOINT = System.getenv("GOOGLE_SEARCH_PROXY_ENDPOINT")
      ?: "https://1lrgx057rh.execute-api.us-east-1.amazonaws.com/search"
  }

  override fun createStrategy(task: CrawlerAgentTask, user: User): SeedStrategy = object : SeedStrategy {
    override fun getSeedItems(
      taskConfig: CrawlerAgentTask.CrawlerTaskExecutionConfigData?,
      orchestrationConfig: OrchestrationConfig
    ): List<SeedItem> {
      SeedMethod.log.info("Starting Google Search via proxy with query: ${taskConfig?.search_query}")
      val searchLimit = 20

      if (taskConfig?.search_query.isNullOrEmpty()) {
        SeedMethod.log.error("Search query is missing")
        throw IllegalArgumentException("Search query is required")
      }

      val client = HttpClient.newBuilder().build()
      val items = taskConfig.search_query?.flatMap {
        val query = it.trim()


        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        SeedMethod.log.debug("Using proxy endpoint: $PROXY_ENDPOINT")

        val uri = "$PROXY_ENDPOINT?q=$encodedQuery&num=$searchLimit"
        val request = HttpRequest.newBuilder()
          .uri(URI.create(uri))
          .timeout(Duration.ofSeconds(30))
          .header("User-Agent", "CognoTik-Crawler/1.0")
          .GET()
          .build()

        SeedMethod.log.info("Sending request to Google Search proxy")

        val response = try {
          client.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: Exception) {
          SeedMethod.log.error("Failed to connect to proxy", e)
          throw RuntimeException("Failed to connect to search proxy: ${e.message}", e)
        }

        val statusCode = response.statusCode()
        if (statusCode != 200) {
          SeedMethod.log.error("Proxy request failed with status $statusCode: ${response.body()}")
          throw RuntimeException("Search proxy error: HTTP $statusCode")
        }

        val searchData: Map<String, Any> = try {
          ObjectMapper().readValue(response.body())
        } catch (e: Exception) {
          SeedMethod.log.error("Failed to parse proxy response", e)
          throw RuntimeException("Invalid response from search proxy", e)
        }

        searchData["items"] as? List<Map<String, Any>> ?: run {
          SeedMethod.log.warn("No 'items' field in proxy response for query: $query")
          emptyList()
        }
      }
      if (items.isNullOrEmpty()) {
        SeedMethod.log.warn("No search results found for query: ${taskConfig.search_query?.toList()}")
        return emptyList()
      }

      SeedMethod.log.info("Successfully retrieved ${items.size} results")

      return items.take(searchLimit).mapNotNull { item ->
        val link = item["link"] as? String
        val title = item["title"] as? String
        val snippet = item["snippet"] as? String

        if (link?.isNotBlank() == true && title?.isNotBlank() == true) {
          SeedItem(
            link = link,
            title = title,
            additionalData = buildMap {
              snippet?.let { put("snippet", it) }
              item["pagemap"]?.let { put("pagemap", it) }
              item["displayLink"]?.let { put("displayLink", it) }
            }
          )
        } else {
          SeedMethod.log.warn("Skipping invalid result: $item")
          null
        }
      }
    }

    override fun isEnabled(): Boolean {
      return true
    }
  }
}