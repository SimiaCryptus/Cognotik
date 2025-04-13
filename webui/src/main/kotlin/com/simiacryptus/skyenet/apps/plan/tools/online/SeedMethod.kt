package com.simiacryptus.skyenet.apps.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.skyenet.apps.plan.PlanSettings
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


interface SeedStrategy {
  fun getSeedItems(taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?, planSettings: PlanSettings): List<Map<String, Any>>?
}

enum class SeedMethod {
  GoogleSearch {
    override fun createStrategy(task: CrawlerAgentTask): SeedStrategy = object : SeedStrategy {
      override fun getSeedItems(taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?, planSettings: PlanSettings): List<Map<String, Any>>? {
        
        
        if (taskConfig?.search_query.isNullOrBlank()) {
          throw IllegalArgumentException("Search query is required when using Google Search seed method")
        }
        val client = HttpClient.newBuilder().build()
        // Use taskConfig parameter consistently
        val query = taskConfig.search_query.trim()
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // Define constants to avoid magic numbers
        val resultCount = 10
        val searchLimit = 20
        val uriBuilder = "https://www.googleapis.com/customsearch/v1?key=${planSettings.googleApiKey}" +
            "&cx=${planSettings.googleSearchEngineId}&q=$encodedQuery&num=$resultCount"
        
        val request = HttpRequest.newBuilder().uri(URI.create(uriBuilder)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val statusCode = response.statusCode()
        
        if (statusCode != 200) {
          throw RuntimeException("Google API request failed with status $statusCode: ${response.body()}")
        }
        
        val searchData: Map<String, Any> = ObjectMapper().readValue(response.body())
        return (searchData["items"] as? List<Map<String, Any>>)?.take(searchLimit)
      }

    }
  },
  DirectUrls {
    override fun createStrategy(task: CrawlerAgentTask): SeedStrategy = object : SeedStrategy {
      override fun getSeedItems(taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?, planSettings: PlanSettings): List<Map<String, Any>>? {
        if (taskConfig?.direct_urls.isNullOrEmpty()) {
          throw RuntimeException("Direct URLs are required when using Direct URLs seed method")
        }
        return taskConfig?.direct_urls?.let { url -> listOf(url.trim()) }?.filter { url -> url.isNotBlank() }?.mapIndexed { index, url ->
          mapOf(
            "link" to url,
            "title" to "Direct URL ${index + 1}"
          )
        }
      }
    }
  };

  abstract fun createStrategy(task: CrawlerAgentTask): SeedStrategy
}