package com.simiacryptus.cognotik.plan.tools.online

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager.Companion.defaultUser
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.EnabledStrategy
import com.simiacryptus.cognotik.util.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.min

interface SeedStrategy : EnabledStrategy {
    fun getSeedItems(
        taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?,
        planSettings: PlanSettings
    ): List<Map<String, Any>>?
}

interface SeedMethodFactory {
    fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy
}

enum class SeedMethod : SeedMethodFactory {
    GoogleSearch {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy = object : SeedStrategy {
            override fun getSeedItems(
                taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?,
                planSettings: PlanSettings
            ): List<Map<String, Any>>? {
                log.info("Starting Google Search seed method with query: ${taskConfig?.search_query}")
                if (taskConfig?.search_query.isNullOrBlank()) {
                    log.error("Search query is missing for Google Search seed method")
                    throw IllegalArgumentException("Search query is required when using Google Search seed method")
                }
                val client = HttpClient.newBuilder().build()

                val query = taskConfig?.search_query?.trim()
                log.debug("Using search query: $query")
                val encodedQuery = URLEncoder.encode(query, "UTF-8")

                val resultCount = min(10, 20) // Ensure we don't exceed API limits
                val searchLimit = 15 // Reduced from 20 to be more conservative
                log.debug("Fetching user settings for Google Search API")
                val userSettings = ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(
                    user ?: defaultUser
                )
                val key = userSettings
                    .apis.firstOrNull { it.provider == APIProvider.Google }?.key?.trim()
                    ?: throw IllegalStateException("Google API token is required but not configured")
                val engineId = userSettings.apiBase[APIProvider.Google]?.trim()
                    ?: throw IllegalStateException("Search engine ID is required but not configured")
                log.debug("Preparing Google Search API request with engine ID: $engineId")
                val uriBuilder =
                    "https://www.googleapis.com/customsearch/v1?key=${key}&cx=${engineId}&q=$encodedQuery&num=$resultCount"
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(uriBuilder))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "CognoTik-Crawler/1.0")
                    .GET()
                    .build()
                log.info("Sending request to Google Search API")
                val response = try {
                    client.send(request, HttpResponse.BodyHandlers.ofString())
                } catch (e: Exception) {
                    log.error("Failed to connect to Google Search API", e)
                    throw RuntimeException("Failed to connect to Google Search API: ${e.message}", e)
                }
                val statusCode = response.statusCode()

                if (statusCode != 200) {
                    log.error("Google API request failed with status $statusCode: ${response.body()}")
                    val errorMsg = when (statusCode) {
                        401 -> "Invalid API key"
                        403 -> "API quota exceeded or access forbidden"
                        429 -> "Rate limit exceeded"
                        else -> "HTTP $statusCode"
                    }
                    throw RuntimeException("Google API error: $errorMsg")
                }
                log.debug("Parsing Google Search API response")

                val searchData: Map<String, Any> = try {
                    ObjectMapper().readValue(response.body())
                } catch (e: Exception) {
                    log.error("Failed to parse Google Search API response", e)
                    throw RuntimeException("Invalid response from Google Search API", e)
                }
                val items = searchData["items"] as? List<Map<String, Any>>
                if (items.isNullOrEmpty()) {
                    log.warn("No search results found for query: $query")
                    return emptyList()
                }
                log.info(
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
                    if (link?.isNotBlank() == true && title?.isNotBlank() == true) {
                        mapOf(
                            "link" to link,
                            "title" to title,
                            "snippet" to (item["snippet"] as? String ?: "")
                        )
                    } else {
                        log.warn("Skipping invalid search result: $item")
                        null
                    }
                }
            }

            override fun isEnabled(): Boolean {
                return user?.let {
                    val userSettings =
                        ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(it)
                    userSettings.apis.any { api -> api.provider == APIProvider.Google && api.key?.isNotBlank() == true } &&
                            userSettings.apiBase[APIProvider.Google]?.isNotBlank() == true
                } ?: false
            }
        }
    },
    SearchIO_Google_Search {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google").createStrategy(task, user)
    },
    SearchIO_Google_Maps {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_maps").createStrategy(task, user)
    },
    SearchIO_Google_Trends {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_trends").createStrategy(task, user)
    },
    SearchIO_Google_Scholar {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_scholar").createStrategy(task, user)
    },
    SearchIO_Google_Patents {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_patents").createStrategy(task, user)
    },
    SearchIO_Google_Finance {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_finance").createStrategy(task, user)
    },
    SearchIO_Google_News {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy =
            SearchAPISearch("google_news").createStrategy(task, user)
    },
    DirectUrls {
        override fun createStrategy(task: CrawlerAgentTask, user: User?): SeedStrategy = object : SeedStrategy {
            override fun getSeedItems(
                taskConfig: CrawlerAgentTask.SearchAndAnalyzeTaskConfigData?,
                planSettings: PlanSettings
            ): List<Map<String, Any>>? {
                log.info("Starting DirectUrls seed method")
                if (taskConfig?.direct_urls.isNullOrEmpty()) {
                    log.error("Direct URLs are missing for DirectUrls seed method")
                    return emptyList()
                }
                log.debug("Processing direct URLs: ${taskConfig?.direct_urls}")
                return taskConfig?.direct_urls?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
                    ?.filter { url ->
                        try {
                            URI.create(url)
                            url.startsWith("http://") || url.startsWith("https://")
                        } catch (e: Exception) {
                            log.warn("Invalid URL format: $url")
                            false
                        }
                    }
                    ?.mapIndexed { index, url ->
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

    companion object {
        val log = LoggerFactory.getLogger(SeedMethod::class.java)
    }
}

