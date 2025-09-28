package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.User
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class SearchAPISearch(
    val engine: String
) : SeedMethodFactory {
    override fun createStrategy(
        task: CrawlerAgentTask,
        user: User?,
    ): SeedStrategy = object : SeedStrategy {
        override fun getSeedItems(
            taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?,
            planSettings: PlanSettings,
        ): List<Map<String, Any>>? {
            SeedMethod.Companion.log.info("Starting SearchAPI.io seed method with query: ${taskConfig?.search_query}")
            if (taskConfig?.search_query.isNullOrBlank()) {
                SeedMethod.Companion.log.error("Search query is missing for SearchAPI.io seed method")
                throw IllegalArgumentException("Search query is required when using SearchAPI.io seed method")
            }
            val client = HttpClient.newBuilder().build()
            val query = taskConfig.search_query.trim()
            SeedMethod.Companion.log.debug("Using search query: $query")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val resultCount = 10
            val searchLimit = 20
            SeedMethod.Companion.log.debug("Fetching user settings for SearchAPI.io")
            val userSettings =
                ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(user ?: UserSettingsManager.Companion.defaultUser)
            val apiKey = userSettings
                .apis.firstOrNull { it.provider == APIProvider.Companion.SearchAPI }?.key?.trim()
                ?: throw RuntimeException("SearchAPI.io API key is required")
            SeedMethod.Companion.log.debug("Preparing SearchAPI.io request")
            val uriBuilder =
                "https://www.searchapi.io/api/v1/search?engine=$engine&q=$encodedQuery&num=$resultCount&api_key=$apiKey"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(uriBuilder))
                .header("User-Agent", "CognoTik-Crawler/1.0")
                .GET()
                .build()
            SeedMethod.Companion.log.info("Sending request to SearchAPI.io")
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            val statusCode = response.statusCode()
            if (statusCode != 200) {
                SeedMethod.Companion.log.error("SearchAPI.io request failed with status $statusCode: ${response.body()}")
                throw RuntimeException("SearchAPI.io request failed with status $statusCode: ${response.body()}")
            }
            SeedMethod.Companion.log.debug("Parsing SearchAPI.io response")
            val searchData: Map<String, Any> = ObjectMapper().readValue(response.body())
            val organicResults = searchData["organic_results"] as? List<Map<String, Any>>
            if (organicResults.isNullOrEmpty()) {
                SeedMethod.Companion.log.warn("No search results found for query: $query")
                throw RuntimeException("No search results found for query: $query")
            }
            val results = organicResults.map { result ->
                mapOf(
                    "link" to (result["link"] as? String ?: ""),
                    "title" to (result["title"] as? String ?: ""),
                    "snippet" to (result["snippet"] as? String ?: "")
                )
            }.filter { it["link"] != "" }
            SeedMethod.Companion.log.info(
                "Successfully retrieved ${results.size} search results, returning ${
                    results.size.coerceAtMost(searchLimit)
                } items"
            )
            return results.take(searchLimit)
        }

        override fun isEnabled() = user?.let {
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(it)
                .apis.any { api -> api.provider == APIProvider.Companion.SearchAPI && api.key != null }
        } ?: false
    }
}