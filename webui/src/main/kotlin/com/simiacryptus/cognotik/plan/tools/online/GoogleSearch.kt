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
import java.time.Duration
import kotlin.math.min

class GoogleSearch : SeedMethodFactory {
    override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy = object : SeedStrategy {
        override fun getSeedItems(
            taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?,
            planSettings: PlanSettings
        ): List<SeedItem>? {
            SeedMethod.Companion.log.info("Starting Google Search seed method with query: ${taskConfig?.search_query}")
            if (taskConfig?.search_query.isNullOrBlank()) {
                SeedMethod.Companion.log.error("Search query is missing for Google Search seed method")
                throw IllegalArgumentException("Search query is required when using Google Search seed method")
            }
            val client = HttpClient.newBuilder().build()

            val query = taskConfig?.search_query?.trim()
            SeedMethod.Companion.log.debug("Using search query: $query")
            val encodedQuery = URLEncoder.encode(query, "UTF-8")

            val resultCount = min(10, 20) // Ensure we don't exceed API limits
            val searchLimit = 15 // Reduced from 20 to be more conservative
            SeedMethod.Companion.log.debug("Fetching user settings for Google Search API")
            val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(
                user ?: UserSettingsManager.Companion.defaultUser
            )
            val key = userSettings
                .apis.firstOrNull { it.provider == APIProvider.Companion.Google }?.key?.trim()
                ?: throw IllegalStateException("Google API token is required but not configured")
            val engineId = userSettings.apiBase[APIProvider.Companion.Google]?.trim()
                ?: throw IllegalStateException("Search engine ID is required but not configured")
            SeedMethod.Companion.log.debug("Preparing Google Search API request with engine ID: $engineId")
            val uriBuilder =
                "https://www.googleapis.com/customsearch/v1?key=${key}&cx=${engineId}&q=$encodedQuery&num=$resultCount"
            val request = HttpRequest.newBuilder()
                .uri(URI.create(uriBuilder))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "CognoTik-Crawler/1.0")
                .GET()
                .build()
            SeedMethod.Companion.log.info("Sending request to Google Search API")
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (e: Exception) {
                SeedMethod.Companion.log.error("Failed to connect to Google Search API", e)
                throw RuntimeException("Failed to connect to Google Search API: ${e.message}", e)
            }
            val statusCode = response.statusCode()

            if (statusCode != 200) {
                SeedMethod.Companion.log.error("Google API request failed with status $statusCode: ${response.body()}")
                val errorMsg = when (statusCode) {
                    401 -> "Invalid API key"
                    403 -> "API quota exceeded or access forbidden"
                    429 -> "Rate limit exceeded"
                    else -> "HTTP $statusCode"
                }
                throw RuntimeException("Google API error: $errorMsg")
            }
            SeedMethod.Companion.log.debug("Parsing Google Search API response")

            val searchData: Map<String, Any> = try {
                ObjectMapper().readValue(response.body())
            } catch (e: Exception) {
                SeedMethod.Companion.log.error("Failed to parse Google Search API response", e)
                throw RuntimeException("Invalid response from Google Search API", e)
            }
            val items = searchData["items"] as? List<Map<String, Any>>
            if (items.isNullOrEmpty()) {
                SeedMethod.Companion.log.warn("No search results found for query: $query")
                return emptyList()
            }
            SeedMethod.Companion.log.info(
                "Successfully retrieved ${items.size} search results, returning ${
                    min(
                        items.size,
                        searchLimit
                    )
                } items"
            )
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
                    SeedMethod.Companion.log.warn("Skipping invalid search result: $item")
                    null
                }
            }
        }

        override fun isEnabled(): Boolean {
            return user?.let {
                val userSettings =
                    ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(it)
                userSettings.apis.any { api -> api.provider == APIProvider.Companion.Google && api.key?.isNotBlank() == true } &&
                        userSettings.apiBase[APIProvider.Companion.Google]?.isNotBlank() == true
            } ?: false
        }
    }
}