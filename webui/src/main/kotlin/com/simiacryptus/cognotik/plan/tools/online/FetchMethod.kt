package com.simiacryptus.cognotik.plan.tools.online

import com.simiacryptus.cognotik.plan.PlanSettings
import com.simiacryptus.cognotik.util.HtmlSimplifier
import com.simiacryptus.cognotik.util.Selenium2S3
import java.io.File
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ExecutorService

interface FetchStrategy {
    fun fetch(url: String, webSearchDir: File, index: Int, pool: ExecutorService, planSettings: PlanSettings): String
}

enum class FetchMethod {
    HttpClient {
        override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = object : FetchStrategy {
            override fun fetch(
                url: String,
                webSearchDir: File,
                index: Int,
                pool: ExecutorService,
                planSettings: PlanSettings
            ): String {
                log.info("HttpClient fetching URL: $url (index: $index)")
                val client = java.net.http.HttpClient.newBuilder().build()
                val request = HttpRequest.newBuilder().uri(URI.create(url))
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                    ).GET()
                    .build()
                log.debug("Sending HTTP request to: $url")
                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                val contentType = response.headers().firstValue("Content-Type").orElse("")
                log.debug("Received response from $url with status: ${response.statusCode()}, Content-Type: $contentType")
                if (contentType.isNotEmpty() && !contentType.startsWith("text/")) {
                    log.warn("Skipping non-text content type: $contentType for URL: $url")
                    return ""
                }
                val body = response.body()
                if (body.isBlank()) {
                    log.warn("Received empty body from URL: $url")
                    return ""
                }
                log.debug("Saving raw content for URL: $url")
                task.saveRawContent(webSearchDir.resolve("raw_pages"), url, body)
                log.debug("Simplifying HTML content for URL: $url")
                val content = HtmlSimplifier.scrubHtml(
                    str = body,
                    baseUrl = url,
                    includeCssData = false,
                    simplifyStructure = true,
                    keepObjectIds = false,
                    preserveWhitespace = false,
                    keepScriptElements = false,
                    keepInteractiveElements = false,
                    keepMediaElements = false,
                    keepEventHandlers = false
                )
                log.debug("Saving simplified content for URL: $url")
                task.saveRawContent(webSearchDir.resolve("reduced_pages"), url, content)

                task.urlContentCache[url] = content
                log.info("Successfully processed URL: $url, content length: ${content.length}")
                return content
            }
        }
    },

    Selenium {
        override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = object : FetchStrategy {
            override fun fetch(
                url: String,
                webSearchDir: File,
                index: Int,
                pool: ExecutorService,
                planSettings: PlanSettings
            ): String {
                log.info("Selenium fetching URL: $url (index: $index)")
                return try {
                    if (task.selenium == null) {
                        log.debug("Initializing Selenium driver")
                        task.selenium = Selenium2S3(
                            pool = pool, cookies = null, driver = planSettings.driver()
                        )
                    }
                    try {
                        log.debug("Navigating to URL with Selenium: $url")
                        task.selenium?.navigate(url)
                        val pageSource = task.selenium?.getPageSource() ?: ""
                        log.debug("Retrieved page source with Selenium, length: ${pageSource.length}")
                        pageSource
                    } finally {
                        task.selenium?.let {
                            log.debug("Quitting Selenium driver")
                            it.quit()
                            task.selenium = null
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Selenium fetch failed for URL: $url, falling back to HttpClient. Error: ${e.message}", e)
                    HttpClient.createStrategy(task).fetch(url, webSearchDir, index, pool, planSettings)
                }
            }
        }
    };

    abstract fun createStrategy(task: CrawlerAgentTask): FetchStrategy

    companion object {
        val log = org.slf4j.LoggerFactory.getLogger(FetchMethod::class.java)
    }
}