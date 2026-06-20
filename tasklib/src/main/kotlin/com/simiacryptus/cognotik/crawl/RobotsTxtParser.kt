package com.simiacryptus.cognotik.crawl

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Parser and cache for robots.txt files
 */
class RobotsTxtParser {
  private val cache = ConcurrentHashMap<String, RobotsTxt>()
  private val client = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

  data class RobotsTxt(
    val disallowedPaths: List<String> = emptyList(),
    val allowedPaths: List<String> = emptyList(),
    val crawlDelay: Long? = null,
    val sitemaps: List<String> = emptyList()
  )

  /**
   * Check if a URL is allowed by robots.txt
   */
  fun isAllowed(url: String, userAgent: String = "*"): Boolean {
    try {
      val uri = URI.create(url)
      val baseUrl =
        "${uri.scheme}://${uri.host}${if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
      val robotsTxt = getRobotsTxt(baseUrl)

      val path = uri.path + (if (uri.query != null) "?${uri.query}" else "")

      // Check allowed paths first (they take precedence)
      if (robotsTxt.allowedPaths.any { matchesPattern(path, it) }) {
        log.debug("URL allowed by robots.txt allow rule: $url")
        return true
      }

      // Check disallowed paths
      if (robotsTxt.disallowedPaths.any { matchesPattern(path, it) }) {
        log.debug("URL disallowed by robots.txt: $url")
        return false
      }

      log.debug("URL allowed by robots.txt (no matching rules): $url")
      return true
    } catch (e: Exception) {
      log.warn("Error checking robots.txt for $url, allowing by default", e)
      return true
    }
  }

  /**
   * Get crawl delay in milliseconds for a domain
   */
  fun getCrawlDelay(url: String): Long? {
    try {
      val uri = URI.create(url)
      val baseUrl =
        "${uri.scheme}://${uri.host}${if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else ""}"
      return getRobotsTxt(baseUrl).crawlDelay
    } catch (e: Exception) {
      log.warn("Error getting crawl delay for $url", e)
      return null
    }
  }

  /**
   * Fetch and parse robots.txt for a domain
   */
  private fun getRobotsTxt(baseUrl: String): RobotsTxt {
    return cache.getOrPut(baseUrl) {
      try {
        val robotsUrl = "$baseUrl/robots.txt"
        log.debug("Fetching robots.txt from: $robotsUrl")

        val request = HttpRequest.newBuilder()
          .uri(URI.create(robotsUrl))
          .timeout(Duration.ofSeconds(10))
          .header("User-Agent", "Mozilla/5.0 (compatible; CognotikBot/1.0)")
          .GET()
          .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() == 200) {
          parseRobotsTxt(response.body())
        } else {
          log.debug("No robots.txt found at $robotsUrl (status: ${response.statusCode()})")
          RobotsTxt() // Empty rules = allow all
        }
      } catch (e: Exception) {
        log.warn("Failed to fetch robots.txt from $baseUrl, allowing all", e)
        RobotsTxt() // On error, allow all
      }
    }
  }

  /**
   * Parse robots.txt content
   */
  private fun parseRobotsTxt(content: String): RobotsTxt {
    val disallowedPaths = mutableListOf<String>()
    val allowedPaths = mutableListOf<String>()
    val sitemaps = mutableListOf<String>()
    var crawlDelay: Long? = null
    var isRelevantUserAgent = false

    content.lines().forEach { line ->
      val trimmed = line.trim()

      // Skip comments and empty lines
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        return@forEach
      }

      val parts = trimmed.split(":", limit = 2)
      if (parts.size != 2) return@forEach

      val directive = parts[0].trim().lowercase()
      val value = parts[1].trim()

      when (directive) {
        "user-agent" -> {
          // Match * or specific bot names
          isRelevantUserAgent = value == "*" || value.lowercase().contains("cognotik")
        }

        "disallow" -> {
          if (isRelevantUserAgent && value.isNotEmpty()) {
            disallowedPaths.add(value)
          }
        }

        "allow" -> {
          if (isRelevantUserAgent && value.isNotEmpty()) {
            allowedPaths.add(value)
          }
        }

        "crawl-delay" -> {
          if (isRelevantUserAgent) {
            crawlDelay = value.toDoubleOrNull()?.let { (it * 1000).toLong() }
          }
        }

        "sitemap" -> {
          sitemaps.add(value)
        }
      }
    }

    log.debug("Parsed robots.txt: ${disallowedPaths.size} disallow rules, ${allowedPaths.size} allow rules, crawl-delay: $crawlDelay")
    return RobotsTxt(disallowedPaths, allowedPaths, crawlDelay, sitemaps)
  }

  /**
   * Check if a path matches a robots.txt pattern
   */
  private fun matchesPattern(path: String, pattern: String): Boolean {
    // Convert robots.txt pattern to regex
    // * matches any sequence of characters
    // $ at end means end of URL
    val regexPattern = pattern
      .replace(".", "\\.")
      .replace("*", ".*")
      .replace("?", "\\?")
      .let { if (it.endsWith("$")) it else "$it.*" }

    return try {
      Regex("^$regexPattern").matches(path)
    } catch (e: Exception) {
      log.info("Invalid robots.txt pattern: $pattern", e)
      false
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(RobotsTxtParser::class.java)
  }
}