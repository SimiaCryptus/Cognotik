package com.simiacryptus.cognotik.plan.tools.online.seed

import com.simiacryptus.cognotik.models.APIProvider
import com.simiacryptus.cognotik.plan.OrchestrationConfig
import com.simiacryptus.cognotik.plan.tools.online.CrawlerAgentTask
import com.simiacryptus.cognotik.platform.ApplicationServices
import com.simiacryptus.cognotik.platform.file.UserSettingsManager
import com.simiacryptus.cognotik.platform.model.User
import com.simiacryptus.cognotik.util.JsonUtil
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

open class SearchAPISearch(
    val engine: String,
    private val mainResultField: String
) : SeedMethodFactory {

    override fun createStrategy(
        task: CrawlerAgentTask,
        user: User?,
    ): SeedStrategy = object : SeedStrategy {
        override fun getSeedItems(
            taskConfig: CrawlerAgentTask.CrawlerTaskExecutionConfigData?,
            orchestrationConfig: OrchestrationConfig,
        ): List<SeedItem> {
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
                ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(
                    user ?: UserSettingsManager.defaultUser
                )
            val apiKey = userSettings
                .apis.firstOrNull { it.provider == APIProvider.SearchAPI }?.key?.decrypt?.trim()
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
            val body = response.body()
            if (statusCode != 200) {
                SeedMethod.Companion.log.error("SearchAPI.io request failed with status $statusCode: $body")
                throw RuntimeException("SearchAPI.io request failed with status $statusCode: $body")
            }
            SeedMethod.Companion.log.debug("Parsing SearchAPI.io response")
            var results = handleResult(body, query)
            SeedMethod.Companion.log.info(
                "Successfully retrieved ${results.size} search results, returning ${
                    results.size.coerceAtMost(searchLimit)
                } items"
            )
            results = results.take(searchLimit)
            return results.mapNotNull { result ->
                val link = (result["link"]
                    ?: result["url"]
                    ?: result["website"]
                    ?: result["pdf"]
                    ?: result["apply_link"]
                        ) as? String
                val title = (result["title"]) as? String
                if (link?.isNotBlank() == true && title?.isNotBlank() == true) {
                    SeedItem(
                        link = link,
                        title = title,
                        additionalData = result.filterKeys {
                            it != "link" &&
                                    it != "url" &&
                                    it != "title" &&
                                    it != "website" &&
                                    it != "pdf" &&
                                    it != "apply_link"
                        }
                    )
                } else {
                    SeedMethod.Companion.log.warn("Skipping invalid search result missing link or title: $result")
                    null
                }
            }
        }

        override fun isEnabled() = user?.let {
            ApplicationServices.fileApplicationServices().userSettingsManager.getUserSettings(it)
                .apis.any { api -> api.provider == APIProvider.SearchAPI && api.key != null }
        } ?: false
    }

    open fun handleResult(
        body: String,
        query: String
    ) = try {
        JsonUtil.fromJson<Map<String, Any>>(
            body,
            Map::class.java
        ).let { rawData ->
            try {
                if (!rawData.containsKey(mainResultField)) {
                    SeedMethod.Companion.log.warn("Expected field '$mainResultField' not found in SearchAPI.io response for query: $query")
                    listOf(rawData)
                } else {
                    val list = (rawData[mainResultField] as List<Map<String, Any>>)
                    if (list.isEmpty()) {
                        SeedMethod.Companion.log.warn("No search results found for query: $query")
                        listOf(rawData)
                    } else {
                        SeedMethod.Companion.log.debug("Parsed ${list.size} results from SearchAPI.io response")
                        list
                    }
                }
            } catch (e: Exception) {
                SeedMethod.Companion.log.debug("Failed to parse SearchAPI.io response", e)
                listOf(rawData)
            }
        }
    } catch (e: Exception) {
        SeedMethod.Companion.log.debug("Failed to parse SearchAPI.io response", e)
        listOf(JsonUtil.fromJson(body, Map::class.java))
    }
}