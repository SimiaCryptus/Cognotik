package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.jopenai.models.APIProvider
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
      override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy = object : SeedStrategy {
      override fun getSeedItems(taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?, planSettings: PlanSettings): List<Map<String, Any>>? {
          log.info("Starting Google Search seed method with query: ${taskConfig?.search_query}")
        if (taskConfig?.search_query.isNullOrBlank()) {
            log.error("Search query is missing for Google Search seed method")
          throw IllegalArgumentException("Search query is required when using Google Search seed method")
        }
        val client = HttpClient.newBuilder().build()
        // Use taskConfig parameter consistently
        val query = taskConfig?.search_query?.trim()
          log.debug("Using search query: $query")
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        // Define constants to avoid magic numbers
        val resultCount = 10
        val searchLimit = 20
          log.debug("Fetching user settings for Google Search API")
          val userSettings = user?.let {
              ApplicationServices.userSettingsManager.getUserSettings(it)
          }
          val key = userSettings?.apiKeys?.get(APIProvider.GoogleSearch)?.let { it.trim() }
              ?: throw RuntimeException("Google API token is required")
          val engineId = userSettings?.apiBase?.get(APIProvider.GoogleSearch)?.let { it.trim() }
              ?: throw RuntimeException("Search engine id is required")
          log.debug("Preparing Google Search API request with engine ID: $engineId")
          val uriBuilder =
              "https://www.googleapis.com/customsearch/v1?key=${key}&cx=${engineId}&q=$encodedQuery&num=$resultCount"
        val request = HttpRequest.newBuilder().uri(URI.create(uriBuilder)).GET().build()
          log.info("Sending request to Google Search API")
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val statusCode = response.statusCode()
        
        if (statusCode != 200) {
            log.error("Google API request failed with status $statusCode: ${response.body()}")
          throw RuntimeException("Google API request failed with status $statusCode: ${response.body()}")
        }
          log.debug("Parsing Google Search API response")
        
        val searchData: Map<String, Any> = ObjectMapper().readValue(response.body())
        val items = searchData["items"] as? List<Map<String, Any>>
        if (items.isNullOrEmpty()) {
            log.warn("No search results found for query: $query")
          throw RuntimeException("No search results found for query: $query")
        }
          log.info(
              "Successfully retrieved ${items.size} search results, returning ${
                  Math.min(
                      items.size,
                      searchLimit
                  )
              } items"
          )
        return items.take(searchLimit)
      }

    }
  },
  DirectUrls {
      override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy = object : SeedStrategy {
      override fun getSeedItems(taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?, planSettings: PlanSettings): List<Map<String, Any>>? {
          log.info("Starting DirectUrls seed method")
        if (taskConfig?.direct_urls.isNullOrEmpty()) {
            log.error("Direct URLs are missing for DirectUrls seed method")
          throw RuntimeException("Direct URLs are required when using Direct URLs seed method")
        }
          log.debug("Processing direct URLs: ${taskConfig?.direct_urls}")
        return taskConfig?.direct_urls?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.mapIndexed { index, url ->
            log.debug("Adding direct URL: $url")
          mapOf(
            "link" to url,
            "title" to "Direct URL ${index + 1}"
          )
        }.also {
            log.info("Successfully processed ${it?.size ?: 0} direct URLs")
        }
      }
    }
  };

    abstract fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(SeedMethod::class.java)
    }
}