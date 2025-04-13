package com.simiacryptus.skyenet.apps.plan.tools.online

import com.simiacryptus.skyenet.apps.plan.PlanSettings
import com.simiacryptus.skyenet.util.HtmlSimplifier
import com.simiacryptus.skyenet.util.Selenium2S3
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
      override fun fetch(url: String, webSearchDir: File, index: Int, pool: ExecutorService, planSettings: PlanSettings): String {
        val client = java.net.http.HttpClient.newBuilder().build()
        val request = HttpRequest.newBuilder().uri(URI.create(url))
          .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36").GET()
          .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        val contentType = response.headers().firstValue("Content-Type").orElse("")
        if (contentType.isNotEmpty() && !contentType.startsWith("text/")) {
          return ""
        }
        val body = response.body()
        if (body.isBlank()) {
          return ""
        }
        task.saveRawContent(webSearchDir.resolve("raw_pages"), url, body)
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
        task.saveRawContent(webSearchDir.resolve("reduced_pages"), url, content)
        // Cache the processed content
        task.urlContentCache[url] = content
        return content
      }
    }
  },

  Selenium {
    override fun createStrategy(task: CrawlerAgentTask): FetchStrategy = object : FetchStrategy {
      override fun fetch(url: String, webSearchDir: File, index: Int, pool: ExecutorService, planSettings: PlanSettings): String {
        if (task.selenium == null) {
          task.selenium = Selenium2S3(
            pool = pool, cookies = null, driver = planSettings.driver()
          )
        }
        try {
          task.selenium?.navigate(url)
          return task.selenium?.getPageSource() ?: ""
        } finally {
          task.selenium?.let {
            it.quit()
            task.selenium = null
          }
        }
      }
    }
  };

  abstract fun createStrategy(task: CrawlerAgentTask): FetchStrategy
}